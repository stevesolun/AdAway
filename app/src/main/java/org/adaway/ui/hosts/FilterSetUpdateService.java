package org.adaway.ui.hosts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.adaway.helper.PreferenceHelper;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Schedules background updates for filter sets.
 */
public final class FilterSetUpdateService {
    public static final String WORK_NAME = "FilterSetUpdateWork";

    private FilterSetUpdateService() {}

    public static void enable(@NonNull Context context) {
        boolean wifiOnly = PreferenceHelper.getUpdateOnlyOnWifi(context);
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(wifiOnly ? NetworkType.UNMETERED : NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build();

        // Run periodically (best effort). We check due schedules inside the worker.
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(FilterSetUpdateWorker.class, 1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req);
    }

    public static void disable(@NonNull Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }

    public static void syncPreferences(@NonNull Context context) {
        FilterSetStore.ensureGlobalDefaults(context);
        if (hasEnabledSchedule(context)) {
            enable(context);
        } else {
            disable(context);
        }
    }

    private static boolean hasEnabledSchedule(@NonNull Context context) {
        if (FilterSetStore.isGlobalScheduleEnabled(context)
                && FilterSetStore.getGlobalSchedule(context) != FilterSetStore.SCHEDULE_OFF) {
            return true;
        }
        for (String name : FilterSetStore.getSetNames(context)) {
            if (FilterSetStore.getSchedule(context, name) != FilterSetStore.SCHEDULE_OFF) {
                return true;
            }
        }
        // Reconciliation also runs in a receiver: do not query Room on the main thread.
        // Retain configured URL schedules even while their sources are disabled or absent.
        for (Map.Entry<String, ?> entry : context.getSharedPreferences(
                "source_schedules", Context.MODE_PRIVATE).getAll().entrySet()) {
            if (entry.getKey().startsWith("schedule_url_")
                    && (Integer.valueOf(FilterSetStore.SCHEDULE_DAILY).equals(entry.getValue())
                    || Integer.valueOf(FilterSetStore.SCHEDULE_WEEKLY).equals(entry.getValue()))) {
                return true;
            }
        }
        return false;
    }
}


