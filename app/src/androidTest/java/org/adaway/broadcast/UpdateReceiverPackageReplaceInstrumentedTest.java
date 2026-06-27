/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.broadcast;

import static android.content.Intent.ACTION_MY_PACKAGE_REPLACED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.NetworkType;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.adaway.R;
import org.adaway.model.source.SourceUpdateService;
import org.adaway.model.update.ApkUpdateService;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.hosts.FilterSetStore;
import org.adaway.ui.hosts.FilterSetUpdateService;
import org.adaway.util.Constants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class UpdateReceiverPackageReplaceInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;
    private static final int TIMEOUT_SECONDS = 10;

    private Context context;
    private WorkManager workManager;
    private String hostsUpdateWorkName;
    private String apkUpdateWorkName;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(
                this.context,
                "set up package replace receiver");
        this.workManager = WorkManager.getInstance(this.context);
        this.hostsUpdateWorkName = getWorkName(SourceUpdateService.class);
        this.apkUpdateWorkName = getWorkName(ApkUpdateService.class);
        resetWorkManager();
    }

    @After
    public void tearDown() throws Exception {
        if (this.context != null) {
            resetWorkManager();
            InstrumentedTestState.resetForPassiveRootUi(
                    this.context,
                    "tear down package replace receiver");
        }
    }

    @Test(timeout = 60_000)
    public void packageReplacedRepairsConfiguredUpdateSchedules() throws Exception {
        setBoolean(R.string.pref_update_check_hosts_daily_key, true);
        setBoolean(R.string.pref_update_check_app_daily_key, true);
        setBoolean(R.string.pref_update_only_on_wifi_key, true);
        FilterSetStore.setGlobalSchedule(
                this.context,
                FilterSetStore.SCHEDULE_DAILY,
                2,
                4,
                30);

        resetWorkManager();
        assertNoActiveWork(this.hostsUpdateWorkName);
        assertNoActiveWork(this.apkUpdateWorkName);
        assertNoActiveWork(FilterSetUpdateService.WORK_NAME);

        new UpdateReceiver().onReceive(this.context, new Intent(ACTION_MY_PACKAGE_REPLACED));

        assertSingleActiveWork(this.hostsUpdateWorkName, NetworkType.UNMETERED);
        assertSingleActiveApkWork();
        assertSingleActiveWork(FilterSetUpdateService.WORK_NAME, NetworkType.UNMETERED);
    }

    private void resetWorkManager() throws Exception {
        this.workManager.cancelAllWork().getResult().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        this.workManager.pruneWork().getResult().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void setBoolean(int keyResId, boolean value) {
        SharedPreferences prefs = this.context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        assertTrue(prefs.edit().putBoolean(this.context.getString(keyResId), value).commit());
    }

    private void assertSingleActiveWork(
            @NonNull String workName,
            @NonNull NetworkType expectedNetworkType)
            throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            List<WorkInfo> active = activeWork(workName);
            if (active.size() == 1
                    && active.get(0).getConstraints().getRequiredNetworkType()
                    == expectedNetworkType) {
                assertTrue(active.get(0).getConstraints().requiresStorageNotLow());
                return;
            }
            SystemClock.sleep(100L);
        }

        List<WorkInfo> active = activeWork(workName);
        assertEquals(1, active.size());
        assertEquals(expectedNetworkType, active.get(0).getConstraints().getRequiredNetworkType());
        assertTrue(active.get(0).getConstraints().requiresStorageNotLow());
    }

    private void assertSingleActiveApkWork() throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            List<WorkInfo> active = activeWork(this.apkUpdateWorkName);
            if (active.size() == 1
                    && active.get(0).getPeriodicityInfo() != null
                    && active.get(0).getPeriodicityInfo().getRepeatIntervalMillis()
                    == TimeUnit.DAYS.toMillis(1)) {
                return;
            }
            SystemClock.sleep(100L);
        }

        List<WorkInfo> active = activeWork(this.apkUpdateWorkName);
        assertEquals(1, active.size());
        assertEquals(TimeUnit.DAYS.toMillis(1),
                active.get(0).getPeriodicityInfo().getRepeatIntervalMillis());
    }

    private void assertNoActiveWork(@NonNull String workName) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (activeWork(workName).isEmpty()) {
                return;
            }
            SystemClock.sleep(100L);
        }
        assertEquals(0, activeWork(workName).size());
    }

    private List<WorkInfo> activeWork(@NonNull String workName) throws Exception {
        return this.workManager.getWorkInfosForUniqueWork(workName)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .stream()
                .filter(info -> !info.getState().isFinished())
                .collect(Collectors.toList());
    }

    private static String getWorkName(@NonNull Class<?> serviceClass) throws Exception {
        Field field = serviceClass.getDeclaredField("WORK_NAME");
        field.setAccessible(true);
        return (String) field.get(null);
    }
}
