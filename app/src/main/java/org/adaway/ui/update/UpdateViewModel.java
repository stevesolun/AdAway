package org.adaway.ui.update;

import static android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR;
import static android.app.DownloadManager.COLUMN_ID;
import static android.app.DownloadManager.COLUMN_STATUS;
import static android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES;
import static android.app.DownloadManager.STATUS_FAILED;
import static android.app.DownloadManager.STATUS_RUNNING;
import static android.app.DownloadManager.STATUS_SUCCESSFUL;

import android.app.Application;
import android.app.DownloadManager;
import android.database.Cursor;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.MediatorLiveData;

import org.adaway.AdAwayApplication;
import org.adaway.model.update.Manifest;
import org.adaway.model.update.UpdateModel;
import org.adaway.model.update.UpdateModel.InstallState;
import org.adaway.ui.adware.AdwareViewModel;
import org.adaway.util.AppExecutors;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import timber.log.Timber;

/**
 * This class is an {@link AndroidViewModel} for the {@link UpdateActivity} cards.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UpdateViewModel extends AdwareViewModel {
    private static final long TRACKING_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(15);
    private final UpdateModel updateModel;
    private final boolean canCheckForUpdates;
    private final MediatorLiveData<DownloadStatus> downloadProgress;
    private final AtomicLong trackedDownload = new AtomicLong(-1);

    public UpdateViewModel(@NonNull Application application) {
        super(application);
        this.updateModel = ((AdAwayApplication) application).getUpdateModel();
        this.canCheckForUpdates = this.updateModel.canCheckForUpdates();
        this.downloadProgress = new MediatorLiveData<>();
        this.downloadProgress.addSource(this.updateModel.getInstallState(), state ->
                this.downloadProgress.setValue(state == InstallState.IDLE ? null
                        : new InstallDownloadStatus(state)));
        startTracking(this.updateModel.getActiveDownloadId());
    }

    public LiveData<Manifest> getAppManifest() {
        return this.updateModel.getManifest();
    }

    boolean canCheckForUpdates() {
        return this.canCheckForUpdates;
    }

    public void update() {
        InstallState state = this.updateModel.getCurrentInstallState();
        if (state == InstallState.CHECKING || state == InstallState.DOWNLOADING
                || state == InstallState.VERIFYING) {
            return;
        }
        Manifest manifest = this.updateModel.getManifest().getValue();
        if (manifest == null || manifest.isExpired() || state == InstallState.CHECK_FAILED
                || state == InstallState.METADATA_EXPIRED) {
            this.trackedDownload.set(-1);
            this.updateModel.prepareManifestRefresh();
            AppExecutors.getInstance().networkIO().execute(this.updateModel::refreshManifest);
            return;
        }
        long downloadId = this.updateModel.update();
        startTracking(downloadId);
        if (downloadId == -1 && this.updateModel.getCurrentInstallState() == InstallState.IDLE) {
            this.downloadProgress.setValue(null);
        }
    }

    public MutableLiveData<DownloadStatus> getDownloadProgress() {
        return this.downloadProgress;
    }

    public void resumePendingInstall() {
        if (!this.updateModel.hasPendingInstall()) {
            return;
        }
        AppExecutors.getInstance().networkIO().execute(this.updateModel::resumePendingInstall);
    }

    private void startTracking(long downloadId) {
        if (this.trackedDownload.getAndSet(downloadId) != downloadId && downloadId != -1) {
            long startedAt = this.updateModel.getDownloadStartedAt();
            AppExecutors.getInstance().networkIO().execute(() -> trackProgress(downloadId, startedAt));
        }
    }

    private void trackProgress(long downloadId, long startedAt) {
        DownloadManager downloadManager = getApplication().getSystemService(DownloadManager.class);
        if (downloadManager == null) {
            Timber.w("Download service unavailable; stopping progress tracking.");
            trackingFailed(downloadId);
            return;
        }
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        int missingCount = 0;
        AtomicBoolean completionPending = new AtomicBoolean();
        try {
            while (this.trackedDownload.get() == downloadId
                    && this.updateModel.getCurrentInstallState() == InstallState.DOWNLOADING) {
                // Hard timeout: avoid infinite background loops.
                if (SystemClock.elapsedRealtime() - startedAt > TRACKING_TIMEOUT_MS) {
                    Timber.w("Stopping download progress tracking (timeout).");
                    trackingFailed(downloadId);
                    return;
                }

                // Add wait before querying download manager
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Timber.d(e, "Failed to wait before querying download manager.");
                    Thread.currentThread().interrupt();
                    trackingFailed(downloadId);
                    return;
                }

                // Query download manager
                try (Cursor cursor = queryDownload(downloadManager, query)) {
                    if (cursor == null || !cursor.moveToFirst()) {
                        // Download entry may not be immediately available; don't spin forever if it never shows up.
                        missingCount++;
                        if (missingCount > 20) { // ~10s with 500ms sleep
                            Timber.w("Download item was not found after repeated queries; stopping tracking.");
                            trackingFailed(downloadId);
                            return;
                        }
                        continue;
                    }
                    missingCount = 0;
                    int idColumnIndex = cursor.getColumnIndex(COLUMN_ID);
                    if (idColumnIndex < 0 || cursor.getLong(idColumnIndex) != downloadId) {
                        trackingFailed(downloadId);
                        return;
                    }

                    // Check download status
                    int statusColumnIndex = cursor.getColumnIndex(COLUMN_STATUS);
                    if (statusColumnIndex < 0) {
                        Timber.w("Download status column missing; stopping progress tracking.");
                        trackingFailed(downloadId);
                        return;
                    }
                    int status = cursor.getInt(statusColumnIndex);
                    switch (status) {
                        case STATUS_FAILED:
                        case STATUS_SUCCESSFUL:
                            if (completionPending.compareAndSet(false, true)) {
                                AppExecutors.getInstance().mainThread().execute(() -> {
                                    try {
                                        if (this.trackedDownload.get() == downloadId
                                                && this.updateModel.getCurrentInstallState()
                                                == InstallState.DOWNLOADING) {
                                            this.updateModel.handleDownloadCompletion(downloadId);
                                        }
                                    } finally {
                                        completionPending.set(false);
                                    }
                                });
                            }
                            break;
                        case STATUS_RUNNING:
                            int totalSizeColumnIndex = cursor.getColumnIndex(COLUMN_TOTAL_SIZE_BYTES);
                            int bytesDownloadedColumnIndex =
                                    cursor.getColumnIndex(COLUMN_BYTES_DOWNLOADED_SO_FAR);
                            if (totalSizeColumnIndex < 0 || bytesDownloadedColumnIndex < 0) {
                                Timber.w("Download progress columns missing; stopping progress tracking.");
                                trackingFailed(downloadId);
                                return;
                            }
                            long total = cursor.getLong(totalSizeColumnIndex);
                            if (total > 0) {
                                long downloaded = cursor.getLong(bytesDownloadedColumnIndex);
                                postProgress(downloadId, new PendingDownloadStatus(downloaded, total));
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        } catch (RuntimeException exception) {
            Timber.w(exception, "Unable to track APK download.");
            trackingFailed(downloadId);
        }
    }

    @VisibleForTesting
    Cursor queryDownload(DownloadManager downloadManager, DownloadManager.Query query) {
        return downloadManager.query(query);
    }

    private void postProgress(long downloadId, DownloadStatus progress) {
        AppExecutors.getInstance().mainThread().execute(() -> {
            // A queued progress read must not overwrite verification/rejection or a newer retry.
            if (this.trackedDownload.get() == downloadId
                    && this.updateModel.getCurrentInstallState() == InstallState.DOWNLOADING) {
                this.downloadProgress.setValue(progress);
            }
        });
    }

    private void trackingFailed(long downloadId) {
        AppExecutors.getInstance().mainThread().execute(() -> {
            if (this.trackedDownload.get() == downloadId) {
                this.updateModel.downloadTrackingFailed(downloadId);
            }
        });
    }

    @Override
    protected void onCleared() {
        this.trackedDownload.set(-1);
        super.onCleared();
    }
}
