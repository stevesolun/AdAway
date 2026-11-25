package org.adaway.ui.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.AdAwayApplication;
import org.adaway.model.update.UpdateModel;
import org.adaway.model.update.UpdateModel.InstallState;
import org.adaway.testing.InstrumentedTestState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class UpdateProgressTrackingInstrumentedTest {
    private static final long DOWNLOAD_ID = 42L;
    private AdAwayApplication application;
    private UpdateModel previousModel;
    private RecordingModel model;
    private UpdateViewModel viewModel;
    private Field modelField;

    @Before
    public void setUp() throws Exception {
        application = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(application, "set up download tracking");
        modelField = AdAwayApplication.class.getDeclaredField("updateModel");
        modelField.setAccessible(true);
        previousModel = application.getUpdateModel();
        onMain(() -> model = new RecordingModel(application));
        modelField.set(application, model);
    }

    @After
    public void tearDown() throws Exception {
        onMain(() -> {
            if (viewModel != null) {
                viewModel.onCleared();
            }
        });
        if (modelField != null && previousModel != null) {
            modelField.set(application, previousModel);
        }
        InstrumentedTestState.resetForPassiveRootUi(application, "tear down download tracking");
    }

    @Test(timeout = 30_000)
    public void reopeningTracksRemovedDownloadAndRecoversRetry() throws Exception {
        beginDownload(SystemClock.elapsedRealtime());
        onMain(() -> {
            viewModel = new MissingDownloadViewModel(application);
            viewModel.onCleared();
            viewModel = new MissingDownloadViewModel(application);
        });

        assertTrue(model.trackingFailed.await(15, TimeUnit.SECONDS));
        assertEquals(InstallState.DOWNLOAD_FAILED, model.getCurrentInstallState());
        assertEquals(-1L, model.getActiveDownloadId());
    }

    @Test(timeout = 10_000)
    public void reopeningPreservesOriginalTimeoutForPausedDownload() throws Exception {
        beginDownload(SystemClock.elapsedRealtime() - TimeUnit.MINUTES.toMillis(15) - 1);
        onMain(() -> viewModel = new PausedDownloadViewModel(application));

        assertTrue(model.trackingFailed.await(5, TimeUnit.SECONDS));
        assertEquals(InstallState.DOWNLOAD_FAILED, model.getCurrentInstallState());
    }

    @Test(timeout = 10_000)
    public void terminalPollingRetriesUntilModelAcknowledgesCompletion() throws Exception {
        beginDownload(SystemClock.elapsedRealtime());
        onMain(() -> viewModel = new TerminalDownloadViewModel(application));

        assertTrue(model.completionHandled.await(5, TimeUnit.SECONDS));
        assertEquals(2, model.completionAttempts.get());
        assertEquals(InstallState.DOWNLOAD_FAILED, model.getCurrentInstallState());
    }

    private void beginDownload(long startedAt) throws Exception {
        Field downloadId = UpdateModel.class.getDeclaredField("activeDownloadId");
        Field start = UpdateModel.class.getDeclaredField("downloadStartedAt");
        downloadId.setAccessible(true);
        start.setAccessible(true);
        downloadId.setLong(model, DOWNLOAD_ID);
        start.setLong(model, startedAt);
        onMain(() -> setState(model, InstallState.DOWNLOADING));
    }

    private static void setState(UpdateModel model, InstallState state) {
        try {
            Method setter = UpdateModel.class.getDeclaredMethod("setInstallState", InstallState.class);
            setter.setAccessible(true);
            setter.invoke(model, state);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private static class TerminalDownloadViewModel extends UpdateViewModel {
        TerminalDownloadViewModel(Application application) {
            super(application);
        }

        @Override
        Cursor queryDownload(DownloadManager downloadManager, DownloadManager.Query query) {
            return row(DownloadManager.STATUS_SUCCESSFUL);
        }
    }

    private static final class MissingDownloadViewModel extends UpdateViewModel {
        MissingDownloadViewModel(Application application) {
            super(application);
        }

        @Override
        Cursor queryDownload(DownloadManager downloadManager, DownloadManager.Query query) {
            return null;
        }
    }

    private static final class PausedDownloadViewModel extends UpdateViewModel {
        PausedDownloadViewModel(Application application) {
            super(application);
        }

        @Override
        Cursor queryDownload(DownloadManager downloadManager, DownloadManager.Query query) {
            return row(DownloadManager.STATUS_PAUSED);
        }
    }

    private static Cursor row(int status) {
        MatrixCursor cursor = new MatrixCursor(new String[]{
                DownloadManager.COLUMN_ID, DownloadManager.COLUMN_STATUS});
        cursor.addRow(new Object[]{DOWNLOAD_ID, status});
        return cursor;
    }

    private static final class RecordingModel extends UpdateModel {
        final CountDownLatch trackingFailed = new CountDownLatch(1);
        final CountDownLatch completionHandled = new CountDownLatch(1);
        final AtomicInteger completionAttempts = new AtomicInteger();

        RecordingModel(Context context) {
            super(context);
        }

        @Override
        public void handleDownloadCompletion(long downloadId) {
            assertEquals(DOWNLOAD_ID, downloadId);
            if (completionAttempts.incrementAndGet() == 2) {
                setState(this, InstallState.DOWNLOAD_FAILED);
                completionHandled.countDown();
            }
        }

        @Override
        public void downloadTrackingFailed(long downloadId) {
            super.downloadTrackingFailed(downloadId);
            trackingFailed.countDown();
        }
    }
}
