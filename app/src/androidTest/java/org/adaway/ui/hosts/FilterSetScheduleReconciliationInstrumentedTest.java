package org.adaway.ui.hosts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.NetworkType;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.adaway.R;
import org.adaway.broadcast.UpdateReceiver;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.util.Constants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class FilterSetScheduleReconciliationInstrumentedTest {
    private static final String URL = "https://schedule-reconciliation.test/hosts.txt";
    private static final String SET = "Independent schedule";
    private Context mContext;
    private WorkManager mWorkManager;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(mContext, "schedule reconciliation setup");
        mWorkManager = WorkManager.getInstance(mContext);
        clearSchedules();
    }

    @After
    public void tearDown() {
        if (mContext != null) {
            InstrumentedTestState.resetForPassiveRootUi(mContext, "schedule reconciliation teardown");
            clearSchedules();
        }
    }

    @Test(timeout = 60_000)
    public void globalOffKeepsDailySourceScheduleAndRefreshesConstraints() throws Exception {
        assertTrue(sourcePreferences().edit()
                .putInt("schedule_url_" + URL, FilterSetStore.SCHEDULE_DAILY)
                .putLong("last_run_url_" + URL, System.currentTimeMillis()).commit());
        FilterSetUpdateService.enable(mContext);
        awaitActiveWork(1);
        setWifiOnly(true);

        reconcile();

        WorkInfo info = awaitActiveWork(1).get(0);
        assertEquals(NetworkType.UNMETERED, info.getConstraints().getRequiredNetworkType());
        assertTrue(info.getConstraints().requiresStorageNotLow());
        assertEquals(FilterSetStore.SCHEDULE_DAILY,
                sourcePreferences().getInt("schedule_url_" + URL, -1));
    }

    @Test(timeout = 60_000)
    public void globalOffRestoresMissingWeeklySetDispatcher() throws Exception {
        configureSet();
        assertTrue(awaitActiveWork(0).isEmpty());

        reconcile();

        awaitActiveWork(1);
        assertEquals(FilterSetStore.SCHEDULE_WEEKLY, FilterSetStore.getSchedule(mContext, SET));
    }

    @Test(timeout = 60_000)
    public void packageReplacementKeepsIndependentSourceSchedule() throws Exception {
        assertTrue(sourcePreferences().edit()
                .putInt("schedule_url_" + URL, FilterSetStore.SCHEDULE_WEEKLY)
                .putLong("last_run_url_" + URL, System.currentTimeMillis()).commit());
        FilterSetUpdateService.enable(mContext);
        awaitActiveWork(1);

        replacePackage();

        awaitActiveWork(1);
        assertEquals(FilterSetStore.SCHEDULE_OFF, FilterSetStore.getGlobalSchedule(mContext));
    }

    @Test(timeout = 60_000)
    public void packageReplacementKeepsIndependentSetSchedule() throws Exception {
        configureSet();
        FilterSetUpdateService.enable(mContext);
        awaitActiveWork(1);

        replacePackage();

        awaitActiveWork(1);
    }

    @Test(timeout = 60_000)
    public void noEnabledSchedulesCancelsDispatcherDespiteSourceTimingMetadata() throws Exception {
        FilterSetStore.saveSet(mContext, SET, Collections.emptySet());
        FilterSetStore.setSchedule(mContext, SET, FilterSetStore.SCHEDULE_OFF);
        assertTrue(sourcePreferences().edit()
                .putInt("schedule_url_" + URL, FilterSetStore.SCHEDULE_OFF)
                .putInt("hour_url_" + URL, FilterSetStore.SCHEDULE_DAILY)
                .putInt("weekday_url_" + URL, FilterSetStore.SCHEDULE_WEEKLY)
                .putLong("last_run_url_" + URL, System.currentTimeMillis()).commit());
        FilterSetUpdateService.enable(mContext);
        awaitActiveWork(1);

        reconcile();

        awaitActiveWork(0);
    }

    @Test(timeout = 60_000)
    public void globalOnlyScheduleStillEnqueuesDispatcher() throws Exception {
        FilterSetStore.setGlobalSchedule(mContext, FilterSetStore.SCHEDULE_DAILY, 1, 3, 0);

        reconcile();

        awaitActiveWork(1);
    }

    private void configureSet() {
        FilterSetStore.saveSet(mContext, SET, Collections.emptySet());
        FilterSetStore.setSchedule(mContext, SET, FilterSetStore.SCHEDULE_WEEKLY, 2, 4, 30);
        FilterSetStore.setLastRun(mContext, SET, System.currentTimeMillis());
    }

    private void reconcile() throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                FilterSetUpdateService.syncPreferences(mContext));
        drainWorkOperations();
    }

    private void replacePackage() throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                new UpdateReceiver().onReceive(mContext,
                        new Intent(Intent.ACTION_MY_PACKAGE_REPLACED)));
        drainWorkOperations();
    }

    private void drainWorkOperations() throws Exception {
        // A queued operation completes after preceding enqueue/cancel preference reconciliation.
        mWorkManager.pruneWork().getResult().get(10, TimeUnit.SECONDS);
    }

    private void clearSchedules() {
        assertTrue(mContext.getSharedPreferences("filter_sets", Context.MODE_PRIVATE)
                .edit().clear().commit());
        assertTrue(sourcePreferences().edit().clear().commit());
        FilterSetStore.setGlobalSchedule(mContext, FilterSetStore.SCHEDULE_OFF, 1, 3, 0);
    }

    private SharedPreferences sourcePreferences() {
        return mContext.getSharedPreferences("source_schedules", Context.MODE_PRIVATE);
    }

    private void setWifiOnly(boolean enabled) {
        assertTrue(mContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(mContext.getString(R.string.pref_update_only_on_wifi_key), enabled)
                .commit());
    }

    private List<WorkInfo> awaitActiveWork(int expected) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        List<WorkInfo> active;
        do {
            active = mWorkManager.getWorkInfosForUniqueWork(FilterSetUpdateService.WORK_NAME)
                    .get(10, TimeUnit.SECONDS).stream()
                    .filter(info -> !info.getState().isFinished()).collect(Collectors.toList());
            if (active.size() == expected) return active;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        assertEquals(expected, active.size());
        return active;
    }
}
