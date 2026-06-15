package org.adaway.model.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.adaway.R;
import org.adaway.util.Constants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class ApkUpdateServiceWorkManagerTest {
    private Context context;
    private WorkManager workManager;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        workManager = WorkManager.getInstance(context);
        resetWorkManager();
    }

    @After
    public void tearDown() throws Exception {
        resetWorkManager();
    }

    @Test
    public void syncPreferences_enqueuesAndCancelsApkUpdateWork() throws Exception {
        setBoolean(R.string.pref_update_check_app_daily_key, true);

        ApkUpdateService.syncPreferences(context);

        assertSingleActiveWork(ApkUpdateService.WORK_NAME);

        setBoolean(R.string.pref_update_check_app_daily_key, false);
        ApkUpdateService.syncPreferences(context);

        assertNoActiveWork(ApkUpdateService.WORK_NAME);
    }

    @Test
    public void enable_replacesExistingApkUpdateWorkWithoutDuplicates() throws Exception {
        ApkUpdateService.enable(context);
        ApkUpdateService.enable(context);

        assertSingleActiveWork(ApkUpdateService.WORK_NAME);
    }

    @Test
    public void syncPreferences_updatesExistingApkUpdatePeriod() throws Exception {
        enqueueStaleApkUpdateWork(2);
        setBoolean(R.string.pref_update_check_app_daily_key, true);

        assertEquals(TimeUnit.DAYS.toMillis(2), singleActiveWork(ApkUpdateService.WORK_NAME)
                .getPeriodicityInfo().getRepeatIntervalMillis());

        ApkUpdateService.syncPreferences(context);

        assertEquals(TimeUnit.DAYS.toMillis(1), singleActiveWork(ApkUpdateService.WORK_NAME)
                .getPeriodicityInfo().getRepeatIntervalMillis());
    }

    private void resetWorkManager() throws Exception {
        workManager.cancelAllWork().getResult().get(5, TimeUnit.SECONDS);
        workManager.pruneWork().getResult().get(5, TimeUnit.SECONDS);
    }

    private void setBoolean(int keyResId, boolean value) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        assertTrue(prefs.edit().putBoolean(context.getString(keyResId), value).commit());
    }

    private void enqueueStaleApkUpdateWork(int repeatDays) throws Exception {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                ApkUpdateService.ApkUpdateWorker.class, repeatDays, TimeUnit.DAYS)
                .build();
        workManager.enqueueUniquePeriodicWork(
                ApkUpdateService.WORK_NAME, ExistingPeriodicWorkPolicy.REPLACE, request)
                .getResult()
                .get(5, TimeUnit.SECONDS);
    }

    private void assertSingleActiveWork(String workName) throws Exception {
        assertEquals(1, activeWork(workName).size());
    }

    private void assertNoActiveWork(String workName) throws Exception {
        assertEquals(0, activeWork(workName).size());
    }

    private List<WorkInfo> activeWork(String workName) throws Exception {
        return workManager.getWorkInfosForUniqueWork(workName).get(5, TimeUnit.SECONDS)
                .stream()
                .filter(info -> !info.getState().isFinished())
                .collect(Collectors.toList());
    }

    private WorkInfo singleActiveWork(String workName) throws Exception {
        List<WorkInfo> active = activeWork(workName);
        assertEquals(1, active.size());
        return active.get(0);
    }
}
