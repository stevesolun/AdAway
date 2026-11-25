package org.adaway.model.update;

import static android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE;
import static android.app.DownloadManager.COLUMN_ID;
import static android.app.DownloadManager.COLUMN_STATUS;
import static android.app.DownloadManager.EXTRA_DOWNLOAD_ID;
import static android.app.DownloadManager.STATUS_FAILED;
import static android.app.DownloadManager.STATUS_PAUSED;
import static android.app.DownloadManager.STATUS_PENDING;
import static android.app.DownloadManager.STATUS_RUNNING;
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

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ApkDownloadReceiverInstrumentedTest {
    private static final long DOWNLOAD_ID = 42L;
    private static final Uri APK = Uri.parse("content://downloads/my_downloads/42");

    @Test
    public void ignoresNullWrongActionMissingIdAndOtherDownloads() {
        Fixture fixture = new Fixture();
        fixture.receiver.onReceive(fixture.context, null);
        fixture.receiver.onReceive(fixture.context,
                new Intent("untrusted.action").putExtra(EXTRA_DOWNLOAD_ID, DOWNLOAD_ID));
        fixture.receiver.onReceive(fixture.context, new Intent(ACTION_DOWNLOAD_COMPLETE));
        fixture.receiver.onReceive(fixture.context,
                new Intent(ACTION_DOWNLOAD_COMPLETE).putExtra(EXTRA_DOWNLOAD_ID, 7L));
        assertEquals(0, fixture.receiver.queryCount);
        fixture.assertNoInstallOrCleanup();
    }

    @Test
    public void earlyForgedHintsKeepReceiverUntilProviderReportsSuccess() {
        Fixture fixture = new Fixture();
        for (int status : new int[]{STATUS_PENDING, STATUS_RUNNING, STATUS_PAUSED, -1}) {
            fixture.receiver.status = status;
            fixture.broadcast();
            fixture.assertNoInstallOrCleanup();
        }
        fixture.receiver.status = STATUS_SUCCESSFUL;
        fixture.broadcast();
        fixture.broadcast();
        assertFalse(fixture.receiver.handleTerminalDownload(fixture.context));
        assertEquals(5, fixture.receiver.queryCount);
        fixture.assertSingleInstall();
    }

    @Test
    public void immediateFallbackAndLaterBroadcastInstallOnlyOnce() {
        Fixture fixture = new Fixture();
        assertTrue(fixture.receiver.handleTerminalDownload(fixture.context));
        fixture.broadcast();
        assertEquals(1, fixture.receiver.queryCount);
        fixture.assertSingleInstall();
    }

    @Test
    public void failedDownloadCleansUpWithoutReadingOrInstallingApk() {
        Fixture fixture = new Fixture();
        fixture.receiver.status = STATUS_FAILED;
        fixture.broadcast();
        fixture.broadcast();
        assertEquals(0, fixture.receiver.uriCount);
        assertEquals(0, fixture.receiver.verificationCount);
        assertEquals(0, fixture.receiver.installCount);
        fixture.assertSingleCleanup();
    }

    @Test
    public void missingMalformedAndMismatchedRowsDoNotConsumeReceiver() {
        for (String row : new String[]{"null", "empty", "missing-id", "missing-status", "other"}) {
            Fixture fixture = new Fixture();
            fixture.receiver.row = row;
            fixture.broadcast();
            fixture.assertNoInstallOrCleanup();
            fixture.receiver.row = "valid";
            fixture.broadcast();
            fixture.assertSingleInstall();
        }
    }

    @Test
    public void providerQueryFailureIsFailClosedAndCanRetry() {
        Fixture fixture = new Fixture();
        fixture.receiver.queryThrows = true;
        fixture.broadcast();
        fixture.assertNoInstallOrCleanup();
        fixture.receiver.queryThrows = false;
        fixture.broadcast();
        fixture.assertSingleInstall();
    }

    @Test
    public void successfulRowStillRequiresVerifiedApk() {
        Fixture fixture = new Fixture();
        fixture.receiver.verified = false;
        fixture.broadcast();
        assertEquals(1, fixture.receiver.verificationCount);
        assertEquals(0, fixture.receiver.installCount);
        fixture.assertSingleCleanup();
    }

    @Test
    public void missingApkCannotInstall() {
        Fixture fixture = new Fixture();
        fixture.receiver.apkUri = null;
        fixture.broadcast();
        assertEquals(0, fixture.receiver.verificationCount);
        assertEquals(0, fixture.receiver.installCount);
        fixture.assertSingleCleanup();
    }

    @Test
    public void providerUriFailureCannotInstallOrRepeatCleanup() {
        Fixture fixture = new Fixture();
        fixture.receiver.uriThrows = true;
        fixture.broadcast();
        fixture.broadcast();
        assertEquals(1, fixture.receiver.uriCount);
        assertEquals(0, fixture.receiver.installCount);
        fixture.assertSingleCleanup();
    }

    @Test
    public void cancelledReceiverIgnoresQueuedCompletionAndFallback() {
        Fixture fixture = new Fixture();
        fixture.receiver.cancel();
        fixture.broadcast();
        assertFalse(fixture.receiver.handleTerminalDownload(fixture.context));
        assertEquals(0, fixture.receiver.queryCount);
        fixture.assertNoInstallOrCleanup();
    }

    private static final class Fixture {
        int cleanupCount;
        int terminalCount;
        final Context context = new ContextWrapper(null) {
            @Override
            public void unregisterReceiver(BroadcastReceiver receiver) {
                cleanupCount++;
            }
        };
        final RecordingReceiver receiver = new RecordingReceiver(() -> terminalCount++);

        void broadcast() {
            receiver.onReceive(context,
                    new Intent(ACTION_DOWNLOAD_COMPLETE).putExtra(EXTRA_DOWNLOAD_ID, DOWNLOAD_ID));
        }

        void assertNoInstallOrCleanup() {
            assertEquals(0, receiver.uriCount);
            assertEquals(0, receiver.verificationCount);
            assertEquals(0, receiver.installCount);
            assertEquals(0, cleanupCount);
            assertEquals(0, terminalCount);
        }

        void assertSingleInstall() {
            assertEquals(1, receiver.uriCount);
            assertEquals(1, receiver.verificationCount);
            assertEquals(1, receiver.installCount);
            assertSingleCleanup();
        }

        void assertSingleCleanup() {
            assertEquals(1, cleanupCount);
            assertEquals(1, terminalCount);
        }
    }

    private static final class RecordingReceiver extends ApkDownloadReceiver {
        int status = STATUS_SUCCESSFUL;
        String row = "valid";
        boolean queryThrows;
        boolean uriThrows;
        boolean verified = true;
        Uri apkUri = APK;
        int queryCount;
        int uriCount;
        int verificationCount;
        int installCount;

        RecordingReceiver(Runnable onTerminal) {
            super(DOWNLOAD_ID, "expected-sha256", "expected-certificate", onTerminal);
        }

        @Override
        Cursor queryDownload(Context context) {
            queryCount++;
            if (queryThrows) {
                throw new SecurityException("Provider unavailable");
            }
            switch (row) {
                case "null":
                    return null;
                case "empty":
                    return new MatrixCursor(new String[]{COLUMN_ID, COLUMN_STATUS});
                case "missing-id":
                    MatrixCursor noId = new MatrixCursor(new String[]{COLUMN_STATUS});
                    noId.addRow(new Object[]{status});
                    return noId;
                case "missing-status":
                    MatrixCursor noStatus = new MatrixCursor(new String[]{COLUMN_ID});
                    noStatus.addRow(new Object[]{DOWNLOAD_ID});
                    return noStatus;
                default:
                    MatrixCursor cursor = new MatrixCursor(new String[]{COLUMN_ID, COLUMN_STATUS});
                    cursor.addRow(new Object[]{row.equals("other") ? 7L : DOWNLOAD_ID, status});
                    return cursor;
            }
        }

        @Override
        Uri getDownloadedApkUri(Context context) {
            uriCount++;
            if (uriThrows) {
                throw new SecurityException("APK unavailable");
            }
            return apkUri;
        }

        @Override
        boolean isVerifiedApk(Context context, Uri uri) {
            assertEquals(APK, uri);
            verificationCount++;
            return verified;
        }

        @Override
        void installApk(Context context, Uri uri) {
            assertEquals(APK, uri);
            installCount++;
        }
    }
}
