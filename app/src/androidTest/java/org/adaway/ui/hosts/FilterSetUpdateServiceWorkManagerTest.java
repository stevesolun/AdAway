package org.adaway.ui.hosts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
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
public class FilterSetUpdateServiceWorkManagerTest {
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
    public void enable_replacesExistingFilterSetUpdateWorkWithoutDuplicates() throws Exception {
        FilterSetUpdateService.enable(context);
        FilterSetUpdateService.enable(context);

        assertSingleActiveWork(FilterSetUpdateService.WORK_NAME);
    }

    @Test
    public void disable_cancelsFilterSetUpdateWork() throws Exception {
        FilterSetUpdateService.enable(context);
        assertSingleActiveWork(FilterSetUpdateService.WORK_NAME);

        FilterSetUpdateService.disable(context);

        assertNoActiveWork(FilterSetUpdateService.WORK_NAME);
    }

    @Test
    public void enable_updatesExistingFilterSetUpdateConstraints() throws Exception {
        enqueueStaleFilterSetUpdateWork(NetworkType.CONNECTED);
        setBoolean(R.string.pref_update_only_on_wifi_key, true);

        assertEquals(NetworkType.CONNECTED, singleActiveWork(FilterSetUpdateService.WORK_NAME)
                .getConstraints().getRequiredNetworkType());

        FilterSetUpdateService.enable(context);

        WorkInfo updated = singleActiveWork(FilterSetUpdateService.WORK_NAME);
        assertEquals(NetworkType.UNMETERED, updated.getConstraints().getRequiredNetworkType());
        assertTrue(updated.getConstraints().requiresStorageNotLow());
    }

    private void resetWorkManager() throws Exception {
        workManager.cancelAllWork().getResult().get(5, TimeUnit.SECONDS);
        workManager.pruneWork().getResult().get(5, TimeUnit.SECONDS);
    }

    private void setBoolean(int keyResId, boolean value) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        assertTrue(prefs.edit().putBoolean(context.getString(keyResId), value).commit());
    }

    private void enqueueStaleFilterSetUpdateWork(NetworkType networkType) throws Exception {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                FilterSetUpdateWorker.class, 1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();

        workManager.enqueueUniquePeriodicWork(
                FilterSetUpdateService.WORK_NAME, ExistingPeriodicWorkPolicy.REPLACE, request)
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
