package org.adaway.model.update;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Consumer;

import org.adaway.model.update.UpdateModel.InstallState;

import static android.content.Intent.ACTION_INSTALL_PACKAGE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.O;
import static android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE;
import static android.app.DownloadManager.COLUMN_ID;
import static android.app.DownloadManager.COLUMN_STATUS;
import static android.app.DownloadManager.STATUS_FAILED;
import static android.app.DownloadManager.STATUS_SUCCESSFUL;

import timber.log.Timber;

/**
 * This class is a {@link BroadcastReceiver} to install downloaded application updates.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class ApkDownloadReceiver extends BroadcastReceiver {
    private final long downloadId;
    private final String expectedSha256;
    private final String expectedSigningCertificateSha256;
    private final Instant expiresAt;
    private final Clock clock;
    private final Consumer<InstallState> onState;
    @Nullable
    private final Runnable onTerminal;
    @Nullable
    private volatile Uri pendingInstallUri;
    private boolean terminalHandled;
    private boolean cancelled;

    // Legacy isolated receiver fixtures do not construct signed metadata. Production must use
    // the Manifest constructor, which binds the expiry to the same signed payload as the hashes.
    @VisibleForTesting
    ApkDownloadReceiver(
            long downloadId,
            String expectedSha256,
            String expectedSigningCertificateSha256) {
        this(downloadId, expectedSha256, expectedSigningCertificateSha256, null);
    }

    @VisibleForTesting
    ApkDownloadReceiver(
            long downloadId,
            String expectedSha256,
            String expectedSigningCertificateSha256,
            @Nullable Runnable onTerminal) {
        this(downloadId, expectedSha256, expectedSigningCertificateSha256,
                Instant.MAX, Clock.systemUTC(), onTerminal, state -> { });
    }

    ApkDownloadReceiver(long downloadId, Manifest manifest, @Nullable Runnable onTerminal,
            Consumer<InstallState> onState) {
        this(downloadId, manifest.apkSha256, manifest.signingCertificateSha256,
                manifest.expiresAt, Clock.systemUTC(), onTerminal, onState);
    }

    @VisibleForTesting
    ApkDownloadReceiver(long downloadId, String expectedSha256,
            String expectedSigningCertificateSha256, Instant expiresAt, Clock clock,
            @Nullable Runnable onTerminal, Consumer<InstallState> onState) {
        this.downloadId = downloadId;
        this.expectedSha256 = expectedSha256;
        this.expectedSigningCertificateSha256 = expectedSigningCertificateSha256;
        this.onTerminal = onTerminal;
        this.expiresAt = java.util.Objects.requireNonNull(expiresAt);
        this.clock = clock;
        this.onState = onState;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())
                && this.downloadId > 0
                && this.downloadId == intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)) {
            // An exported broadcast is only a hint; the provider owns completion state.
            handleTerminalDownload(context);
        }
    }

    synchronized boolean handleTerminalDownload(Context context) {
        if (this.terminalHandled || this.downloadId <= 0) {
            return false;
        }
        int status = getDownloadStatus(context);
        if (status != STATUS_SUCCESSFUL && status != STATUS_FAILED) {
            return false;
        }
        this.terminalHandled = true;
        try {
            if (status == STATUS_SUCCESSFUL) {
                if (!requireFreshAuthorization()) {
                    return true;
                }
                this.onState.accept(InstallState.VERIFYING);
                Uri apkUri = getDownloadedApkUri(context);
                if (apkUri != null && isVerifiedApk(context, apkUri)) {
                    installApk(context, apkUri);
                } else {
                    Timber.w("Downloaded APK missing or rejected by integrity verification.");
                    this.onState.accept(InstallState.VERIFICATION_FAILED);
                }
            } else {
                Timber.w("Failed to download id: %s.", this.downloadId);
                this.onState.accept(InstallState.DOWNLOAD_FAILED);
            }
        } catch (RuntimeException exception) {
            Timber.w(exception, "Unable to process completed download: %s.", this.downloadId);
            this.pendingInstallUri = null;
            this.onState.accept(InstallState.INSTALL_FAILED);
        } finally {
            unregister(context);
        }
        return true;
    }

    @Nullable
    Cursor queryDownload(Context context) {
        DownloadManager downloadManager = context.getSystemService(DownloadManager.class);
        if (downloadManager == null) {
            Timber.w("Download service unavailable for id: %s.", this.downloadId);
            return null;
        }
        return downloadManager.query(new DownloadManager.Query().setFilterById(this.downloadId));
    }

    private int getDownloadStatus(Context context) {
        try (Cursor cursor = queryDownload(context)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return -1;
            }
            int idColumn = cursor.getColumnIndex(COLUMN_ID);
            int statusColumn = cursor.getColumnIndex(COLUMN_STATUS);
            if (idColumn < 0 || statusColumn < 0 || cursor.getLong(idColumn) != this.downloadId) {
                Timber.w("Download row does not match id: %s.", this.downloadId);
                return -1;
            }
            return cursor.getInt(statusColumn);
        } catch (RuntimeException exception) {
            Timber.w(exception, "Unable to query download: %s.", this.downloadId);
            return -1;
        }
    }

    @Nullable
    Uri getDownloadedApkUri(Context context) {
        DownloadManager downloadManager = context.getSystemService(DownloadManager.class);
        return downloadManager == null ? null
                : downloadManager.getUriForDownloadedFile(this.downloadId);
    }

    synchronized void cancel() {
        this.cancelled = true;
        this.terminalHandled = true;
        this.pendingInstallUri = null;
    }

    private void unregister(Context context) {
        try {
            context.unregisterReceiver(this);
        } catch (IllegalArgumentException exception) {
            Timber.d(exception, "APK download receiver was already unregistered.");
        }
        if (this.onTerminal != null) {
            this.onTerminal.run();
        }
    }

    synchronized void installApk(Context context, Uri apkUri) {
        if (this.cancelled || !requireFreshAuthorization()) {
            return;
        }
        if (!canRequestPackageInstalls(context)) {
            this.pendingInstallUri = apkUri;
            this.onState.accept(InstallState.PERMISSION_REQUIRED);
            Intent settings = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (!startActivityIfResolvable(context, settings, "install-permission settings")) {
                this.pendingInstallUri = null;
                reportLaunchFailure();
                return;
            }
            Timber.w("APK install permission is disabled; opened install-permission settings.");
            return;
        }

        Intent install = new Intent(ACTION_INSTALL_PACKAGE);
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        install.setData(apkUri);
        this.pendingInstallUri = null;
        if (startActivityIfResolvable(context, install, "APK installer")) {
            // Opening another app is not evidence that the update was installed.
            this.onState.accept(InstallState.INSTALLER_OPENED);
        } else {
            reportLaunchFailure();
        }
    }

    private void reportLaunchFailure() {
        if (requireFreshAuthorization()) {
            this.onState.accept(InstallState.INSTALL_FAILED);
        }
    }

    boolean hasPendingInstall() {
        return this.pendingInstallUri != null;
    }

    synchronized void resumePendingInstall(Context context) {
        Uri apkUri = this.pendingInstallUri;
        if (this.cancelled || apkUri == null || !requireFreshAuthorization()) {
            return;
        }
        try {
            if (!canRequestPackageInstalls(context)) {
                this.onState.accept(InstallState.PERMISSION_REQUIRED);
                return;
            }
            this.pendingInstallUri = null;
            // The download may have changed or disappeared while settings was open.
            this.onState.accept(InstallState.VERIFYING);
            if (isVerifiedApk(context, apkUri)) {
                installApk(context, apkUri);
            } else {
                Timber.w("Pending APK rejected after returning from install-permission settings.");
                this.onState.accept(InstallState.VERIFICATION_FAILED);
            }
        } catch (RuntimeException exception) {
            Timber.w(exception, "Unable to resume APK installation.");
            this.pendingInstallUri = null;
            this.onState.accept(InstallState.INSTALL_FAILED);
        }
    }

    private boolean requireFreshAuthorization() {
        if (!this.expiresAt.isAfter(this.clock.instant())) {
            this.pendingInstallUri = null;
            this.onState.accept(InstallState.METADATA_EXPIRED);
            return false;
        }
        return true;
    }

    boolean canRequestPackageInstalls(Context context) {
        return SDK_INT < O || context.getPackageManager().canRequestPackageInstalls();
    }

    boolean isVerifiedApk(Context context, Uri apkUri) {
        return ApkIntegrityVerifier.matchesSha256(context, apkUri, this.expectedSha256)
                && ApkIntegrityVerifier.matchesSigningCertificateSha256(
                        context, apkUri, this.expectedSigningCertificateSha256);
    }

    boolean startActivityIfResolvable(Context context, Intent intent, String target) {
        if (intent.resolveActivity(context.getPackageManager()) == null) {
            Timber.w("No activity available to open %s.", target);
            return false;
        }
        try {
            // Resolution and APK verification can take time. Check again at the handoff.
            if (!requireFreshAuthorization()) {
                return false;
            }
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException exception) {
            Timber.w(exception, "Failed to open %s.", target);
            return false;
        }
    }
}
