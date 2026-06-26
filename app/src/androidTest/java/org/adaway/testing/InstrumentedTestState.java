package org.adaway.testing;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.source.SourceUpdateService;
import org.adaway.model.update.ApkUpdateService;
import org.adaway.ui.hosts.FilterListsSubscribeAllWorker;
import org.adaway.ui.hosts.FilterSetStore;
import org.adaway.ui.hosts.FilterSetUpdateService;
import org.adaway.util.Constants;

import java.util.List;
import java.util.concurrent.TimeUnit;

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
        resetWorkManager(context, phase);
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
