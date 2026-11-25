package org.adaway.model.update;

import static android.app.DownloadManager.COLUMN_ID;
import static android.app.DownloadManager.COLUMN_STATUS;
import static android.app.DownloadManager.STATUS_SUCCESSFUL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.adaway.model.update.UpdateModel.InstallState;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Controlled clock/provider/installer fixtures; no real APK or installer is opened. */
@RunWith(AndroidJUnit4.class)
public class ApkInstallExpiryRetryInstrumentedTest {
    private static final Instant EXPIRY = Instant.parse("2030-01-01T00:00:00Z");
    private static final Uri APK = Uri.parse("content://downloads/my_downloads/42");

    @Test
    public void completionAtOrAfterExpiryRejectsBeforeReadingApkAndCleansUpOnce() {
        for (Instant now : new Instant[]{EXPIRY, EXPIRY.plusSeconds(1)}) {
            Fixture f = new Fixture();
            f.clock.now = now;
            assertTrue(f.receiver.handleTerminalDownload(f.context));
            assertFalse(f.receiver.handleTerminalDownload(f.context));
            assertEquals(InstallState.METADATA_EXPIRED, f.lastState());
            assertEquals(0, f.receiver.uriReads);
            assertEquals(0, f.receiver.verifications);
            assertEquals(0, f.receiver.launched.size());
            assertEquals(1, f.cleanups);
        }
    }

    @Test
    public void expiryDuringVerificationCannotLaunchInstaller() {
        Fixture f = new Fixture();
        f.receiver.expireDuringVerification = true;
        f.receiver.handleTerminalDownload(f.context);
        assertEquals(1, f.receiver.verifications);
        assertEquals(InstallState.METADATA_EXPIRED, f.lastState());
        assertTrue(f.receiver.launched.isEmpty());
    }

    @Test
    public void delayedPermissionReturnExpiresAndCannotBeRevived() {
        for (boolean grant : new boolean[]{false, true}) {
            Fixture f = new Fixture();
            f.receiver.permissionGranted = false;
            f.receiver.handleTerminalDownload(f.context);
            assertTrue(f.receiver.hasPendingInstall());
            f.clock.now = EXPIRY;
            f.receiver.permissionGranted = grant;
            f.receiver.resumePendingInstall(f.context);
            assertEquals(InstallState.METADATA_EXPIRED, f.lastState());
            assertFalse(f.receiver.hasPendingInstall());
            f.clock.now = EXPIRY.minusSeconds(10);
            f.receiver.permissionGranted = true;
            f.receiver.resumePendingInstall(f.context);
            assertEquals(1, f.receiver.verifications);
            assertEquals(1, f.receiver.launched.size());
        }
    }

    @Test
    public void expiryDuringPermissionReturnReverificationCannotLaunch() {
        Fixture f = new Fixture();
        f.receiver.permissionGranted = false;
        f.receiver.handleTerminalDownload(f.context);
        f.receiver.permissionGranted = true;
        f.receiver.expireDuringVerification = true;
        f.receiver.resumePendingInstall(f.context);
        assertEquals(InstallState.METADATA_EXPIRED, f.lastState());
        assertFalse(f.receiver.hasPendingInstall());
        assertEquals(1, f.receiver.launched.size());
    }

    @Test
    public void verificationRejectionPublishesFailureNotDownloadCompletion() {
        Fixture f = new Fixture();
        f.receiver.verified = false;
        f.receiver.handleTerminalDownload(f.context);
        assertEquals(InstallState.VERIFICATION_FAILED, f.lastState());
        assertTrue(f.receiver.launched.isEmpty());
        assertFalse(f.receiver.hasPendingInstall());
    }

    @Test
    public void missingInstallerOrSettingsPublishesFailureAndDropsContinuation() {
        for (boolean permission : new boolean[]{false, true}) {
            Fixture f = new Fixture();
            f.receiver.permissionGranted = permission;
            f.receiver.resolvable = false;
            f.receiver.handleTerminalDownload(f.context);
            assertEquals(InstallState.INSTALL_FAILED, f.lastState());
            assertFalse(f.receiver.hasPendingInstall());
            f.receiver.resumePendingInstall(f.context);
            assertEquals(1, f.receiver.launchAttempts);
        }
    }

    @Test
    public void permissionDenialDoesNotLoopAndGrantReverifiesExactlyOnce() {
        Fixture f = new Fixture();
        f.receiver.permissionGranted = false;
        f.receiver.handleTerminalDownload(f.context);
        f.receiver.resumePendingInstall(f.context);
        assertEquals(InstallState.PERMISSION_REQUIRED, f.lastState());
        assertEquals(1, f.receiver.launched.size());
        f.receiver.permissionGranted = true;
        f.receiver.resumePendingInstall(f.context);
        f.receiver.resumePendingInstall(f.context);
        assertEquals(InstallState.INSTALLER_OPENED, f.lastState());
        assertEquals(2, f.receiver.verifications);
        assertEquals(2, f.receiver.launched.size());
        assertFalse(f.receiver.hasPendingInstall());
    }

    @Test
    public void returningAfterInstallerCancellationNeverAutomaticallyRelaunches() {
        Fixture f = new Fixture();
        f.receiver.handleTerminalDownload(f.context);
        f.receiver.resumePendingInstall(f.context);
        f.receiver.resumePendingInstall(f.context);
        assertEquals(InstallState.INSTALLER_OPENED, f.lastState());
        assertEquals(1, f.receiver.launched.size());
        assertFalse(f.receiver.hasPendingInstall());
    }

    @Test
    public void changedOrUnreadablePendingApkReportsFailureAndClearsContinuation() {
        for (boolean throwsOnVerify : new boolean[]{false, true}) {
            Fixture f = new Fixture();
            f.receiver.permissionGranted = false;
            f.receiver.handleTerminalDownload(f.context);
            f.receiver.permissionGranted = true;
            f.receiver.verified = false;
            f.receiver.throwsOnVerify = throwsOnVerify;
            f.receiver.resumePendingInstall(f.context);
            assertEquals(throwsOnVerify ? InstallState.INSTALL_FAILED
                    : InstallState.VERIFICATION_FAILED, f.lastState());
            assertFalse(f.receiver.hasPendingInstall());
            assertEquals(1, f.receiver.launched.size());
        }
    }

    @Test
    public void cancelledReceiverCannotResumeOrDirectlyLaunch() {
        Fixture f = new Fixture();
        f.receiver.permissionGranted = false;
        f.receiver.handleTerminalDownload(f.context);
        f.receiver.cancel();
        f.receiver.permissionGranted = true;
        f.receiver.resumePendingInstall(f.context);
        f.receiver.installApk(f.context, APK);
        assertFalse(f.receiver.handleTerminalDownload(f.context));
        assertFalse(f.receiver.hasPendingInstall());
        assertEquals(1, f.receiver.launched.size());
        assertEquals(1, f.receiver.verifications);
    }

    private static final class Fixture {
        final MutableClock clock = new MutableClock();
        final List<InstallState> states = new ArrayList<>();
        int cleanups;
        final Context context = new ContextWrapper(ApplicationProvider.getApplicationContext()) {
            @Override
            public void unregisterReceiver(BroadcastReceiver receiver) {
                cleanups++;
            }
        };
        final RecordingReceiver receiver = new RecordingReceiver(clock, states);

        InstallState lastState() {
            return states.get(states.size() - 1);
        }
    }

    private static final class MutableClock extends Clock {
        Instant now = EXPIRY.minusSeconds(10);
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static final class RecordingReceiver extends ApkDownloadReceiver {
        final MutableClock clock;
        final List<Intent> launched = new ArrayList<>();
        boolean permissionGranted = true;
        boolean resolvable = true;
        boolean verified = true;
        boolean expireDuringVerification;
        boolean throwsOnVerify;
        int uriReads;
        int verifications;
        int launchAttempts;

        RecordingReceiver(MutableClock clock, List<InstallState> states) {
            super(42L, "fixture-sha256", "fixture-certificate", EXPIRY, clock, null, states::add);
            this.clock = clock;
        }

        @Override Cursor queryDownload(Context context) {
            MatrixCursor cursor = new MatrixCursor(new String[]{COLUMN_ID, COLUMN_STATUS});
            cursor.addRow(new Object[]{42L, STATUS_SUCCESSFUL});
            return cursor;
        }

        @Override Uri getDownloadedApkUri(Context context) {
            uriReads++;
            return APK;
        }

        @Override boolean isVerifiedApk(Context context, Uri uri) {
            assertEquals(APK, uri);
            verifications++;
            if (expireDuringVerification) {
                clock.now = EXPIRY;
            }
            if (throwsOnVerify) {
                throw new SecurityException("Fixture no longer readable");
            }
            return verified;
        }

        @Override boolean canRequestPackageInstalls(Context context) {
            return permissionGranted;
        }

        @Override boolean startActivityIfResolvable(Context context, Intent intent, String target) {
            launchAttempts++;
            if (resolvable) {
                launched.add(intent);
            }
            return resolvable;
        }
    }
}
