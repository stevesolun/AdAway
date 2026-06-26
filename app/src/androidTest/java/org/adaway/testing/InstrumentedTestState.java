package org.adaway.testing;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.source.SourceModel;
import org.adaway.model.source.SourceUpdateService;
import org.adaway.model.update.ApkUpdateService;
import org.adaway.ui.hosts.FilterListsSubscribeAllWorker;
import org.adaway.ui.hosts.FilterSetStore;
import org.adaway.ui.hosts.FilterSetUpdateService;
import org.adaway.util.Constants;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class InstrumentedTestState {
    private static final int WORK_MANAGER_RESET_TIMEOUT_SECONDS = 30;
    private static final int WORK_MANAGER_DRAIN_POLL_MS = 100;
    private static final String SOURCE_PERIODIC_WORK_NAME = "HostsUpdateWork";
    private static final String APK_PERIODIC_WORK_NAME = "ApkUpdateWork";
    private static final String[] KNOWN_WORK_NAMES = {
            SOURCE_PERIODIC_WORK_NAME,
            SourceUpdateService.WORK_NAME_NOW,
            APK_PERIODIC_WORK_NAME,
            FilterSetUpdateService.WORK_NAME,
            FilterListsSubscribeAllWorker.UNIQUE_WORK_NAME
    };

    private InstrumentedTestState() {
    }

    public static void resetForPassiveRootUi(@NonNull Context context, @NonNull String phase) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit().clear();
        putBackgroundUpdatePrefs(context, editor, false);
        if (!editor.commit()) {
            throw new AssertionError("Failed to reset app preferences during " + phase);
        }

        PreferenceHelper.setAbBlockMethod(context, AdBlockMethod.ROOT);
        FilterSetStore.setGlobalSchedule(context, FilterSetStore.SCHEDULE_OFF, 1, 3, 0);
        FilterSetStore.setGlobalEnabled(context, false);
        SourceUpdateService.disable(context);
        ApkUpdateService.disable(context);
        FilterSetUpdateService.disable(context);
        resetSourceModel(context, phase + " before WorkManager reset");
        resetWorkManager(context, phase);
        resetSourceModel(context, phase + " after WorkManager reset");
    }

    public static void resetWorkManager(@NonNull Context context, @NonNull String phase) {
        try {
            WorkManager workManager = WorkManager.getInstance(context);
            workManager.cancelAllWork().getResult()
                    .get(WORK_MANAGER_RESET_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            waitForKnownWorkToDrain(workManager);
            workManager.pruneWork().getResult()
                    .get(WORK_MANAGER_RESET_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Failed to reset WorkManager during " + phase, exception);
        }
    }

    private static void waitForKnownWorkToDrain(@NonNull WorkManager workManager)
            throws Exception {
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(WORK_MANAGER_RESET_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            String activeWorkName = firstActiveWorkName(workManager);
            if (activeWorkName == null) {
                return;
            }
            Thread.sleep(WORK_MANAGER_DRAIN_POLL_MS);
        }

        throw new AssertionError("Timed out waiting for WorkManager jobs to stop. Active job: "
                + firstActiveWorkName(workManager));
    }

    private static String firstActiveWorkName(@NonNull WorkManager workManager)
            throws Exception {
        for (String workName : KNOWN_WORK_NAMES) {
            List<WorkInfo> workInfos = workManager.getWorkInfosForUniqueWork(workName)
                    .get(WORK_MANAGER_RESET_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            for (WorkInfo workInfo : workInfos) {
                if (!workInfo.getState().isFinished()) {
                    return workName;
                }
            }
        }
        return null;
    }

    private static void resetSourceModel(@NonNull Context context, @NonNull String phase) {
        try {
            SourceModel sourceModel =
                    ((AdAwayApplication) context.getApplicationContext()).getSourceModel();
            sourceModel.requestStop();
            shutdownSourcePool(sourceModel, "currentDownloadPool");
            shutdownSourcePool(sourceModel, "currentParsePool");
            waitForSourceModelIdle(sourceModel);
            forceSourceModelIdle(sourceModel);
        } catch (Exception exception) {
            throw new AssertionError("Failed to reset SourceModel during " + phase, exception);
        }
    }

    private static void shutdownSourcePool(
            @NonNull SourceModel sourceModel,
            @NonNull String fieldName) throws Exception {
        Field field = SourceModel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        ExecutorService pool = (ExecutorService) field.get(sourceModel);
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    private static void waitForSourceModelIdle(@NonNull SourceModel sourceModel)
            throws Exception {
        AtomicBoolean updateInProgress = getUpdateInProgress(sourceModel);
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(WORK_MANAGER_RESET_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (!updateInProgress.get()) {
                return;
            }
            Thread.sleep(WORK_MANAGER_DRAIN_POLL_MS);
        }

        throw new AssertionError("Timed out waiting for SourceModel update to stop.");
    }

    private static void forceSourceModelIdle(@NonNull SourceModel sourceModel) throws Exception {
        Field progressBuilderField = SourceModel.class.getDeclaredField("progressBuilder");
        progressBuilderField.setAccessible(true);
        SourceModel.MultiPhaseProgressBuilder progressBuilder =
                (SourceModel.MultiPhaseProgressBuilder) progressBuilderField.get(sourceModel);
        progressBuilder.reset();
        getUpdateInProgress(sourceModel).set(false);

        Method method = SourceModel.class.getDeclaredMethod(
                "postMultiPhaseProgress",
                SourceModel.MultiPhaseProgress.class,
                boolean.class);
        method.setAccessible(true);
        method.invoke(sourceModel, SourceModel.MultiPhaseProgress.idle(), true);
        drainSourceModelMainHandler(sourceModel);
    }

    private static void drainSourceModelMainHandler(@NonNull SourceModel sourceModel)
            throws Exception {
        Field mainHandlerField = SourceModel.class.getDeclaredField("mainHandler");
        mainHandlerField.setAccessible(true);
        Handler mainHandler = (Handler) mainHandlerField.get(sourceModel);
        CountDownLatch latch = new CountDownLatch(1);
        if (!mainHandler.post(latch::countDown)) {
            throw new AssertionError("Failed to post SourceModel main-handler drain.");
        }
        if (!latch.await(WORK_MANAGER_RESET_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out draining SourceModel main handler.");
        }
    }

    private static AtomicBoolean getUpdateInProgress(@NonNull SourceModel sourceModel)
            throws Exception {
        Field field = SourceModel.class.getDeclaredField("updateInProgress");
        field.setAccessible(true);
        return (AtomicBoolean) field.get(sourceModel);
    }

    private static void putBackgroundUpdatePrefs(
            @NonNull Context context,
            @NonNull SharedPreferences.Editor editor,
            boolean enabled) {
        editor.putBoolean(context.getString(R.string.pref_update_check_key), enabled)
                .putBoolean(context.getString(R.string.pref_update_check_app_startup_key), enabled)
                .putBoolean(context.getString(R.string.pref_update_check_app_daily_key), enabled)
                .putBoolean(context.getString(R.string.pref_update_check_hosts_daily_key), enabled)
                .putBoolean(context.getString(R.string.pref_automatic_update_daily_key), enabled);
    }
}
