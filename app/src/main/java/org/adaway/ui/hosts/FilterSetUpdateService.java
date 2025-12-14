package org.adaway.ui.hosts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.adaway.helper.PreferenceHelper;

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
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(FilterSetUpdateWorker.class, 6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req);
    }

    public static void disable(@NonNull Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }
}
