package org.adaway.ui.hosts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkInfo;
import androidx.work.testing.TestListenableWorkerBuilder;

import org.adaway.AdAwayApplication;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.SourceModel;
import org.adaway.model.vpn.VpnModel;
import org.adaway.testing.InstrumentedTestState;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class FilterSetUpdateWorkerDueTest {
    private static final String SET = "Worker due fixture";
    private static final String ENABLED = "content://org.adaway.test.hosts/success.txt?modified=123";
    private static final String DISABLED = "content://org.adaway.test.hosts/disabled-due.txt";
    private static final long OVERDUE = 1L;

    @Test
    public void connectivityStopDuringRetrievalLeavesDueSchedulesForNextWorker() throws Exception {
        AdAwayApplication app = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(app, "due worker setup");
        PreferenceHelper.setAbBlockMethod(app, AdBlockMethod.VPN);
        SharedPreferences urls = app.getSharedPreferences("source_schedules", Context.MODE_PRIVATE);
        SharedPreferences sets = app.getSharedPreferences("filter_sets", Context.MODE_PRIVATE);
        HostsSourceDao dao = AppDatabase.getInstance(app).hostsSourceDao();
        AppDatabase.getInstance(app).getOpenHelper().getWritableDatabase();
        ScheduleRegressionTestSupport.drainDiskAndMain();
        Field sources = modelField("sourceModel");
        Field blocker = modelField("adBlockModel");
        Object previousSources = sources.get(app);
        Object previousBlocker = blocker.get(app);
        try {
            assertTrue(sets.edit().clear().commit());
            FilterSetStore.setGlobalSchedule(app, FilterSetStore.SCHEDULE_OFF, 1, 0, 0);
            FilterSetStore.setGlobalEnabled(app, false);
            seed(dao, ENABLED, true);
            seed(dao, DISABLED, false);
            int enabledId = dao.getByUrl(ENABLED).orElseThrow().getId();
            FilterSetStore.saveScheduledSet(app, SET, Collections.singleton(ENABLED),
                    FilterSetStore.SCHEDULE_DAILY, 1, 0, 0);
            FilterSetStore.setLastRun(app, SET, OVERDUE);
            assertTrue(urls.edit().clear()
                    .putInt("schedule_url_" + ENABLED, FilterSetStore.SCHEDULE_DAILY)
                    .putLong("last_run_url_" + ENABLED, OVERDUE)
                    .putInt("schedule_url_" + DISABLED, FilterSetStore.SCHEDULE_DAILY)
                    .putLong("last_run_url_" + DISABLED, OVERDUE).commit());

            FilterSetUpdateWorker first = worker(app);
            int[] fetches = {0};
            int[] applies = {0};
            sources.set(app, new SourceModel(app) {
                @Override public boolean checkAndRetrieveHostsSources() {
                    throw new AssertionError("A scoped schedule must not fetch all sources");
                }

                @Override public void retrieveHostsSources(List<Integer> ids) throws HostErrorException {
                    assertEquals(Collections.singletonList(enabledId), ids);
                    super.retrieveHostsSources(ids);
                    if (++fetches[0] == 1) first.stop(WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY);
                }
            });
            blocker.set(app, new VpnModel(app) {
                @Override public void apply() { applies[0]++; }
            });

            Result stopped = first.doWork();
            assertTrue(first.isStopped());
            assertEquals("Stopped retrieval must not start VPN apply", 0, applies[0]);
            assertEquals(Result.retry(), stopped);
            assertEquals(OVERDUE, FilterSetStore.getLastRun(app, SET));
            assertEquals(OVERDUE, urls.getLong("last_run_url_" + ENABLED, -1));
            assertEquals(OVERDUE, urls.getLong("last_run_url_" + DISABLED, -1));

            long beforeRetry = System.currentTimeMillis();
            assertEquals(Result.success(), worker(app).doWork());
            assertEquals(2, fetches[0]);
            assertEquals(1, applies[0]);
            long completed = FilterSetStore.getLastRun(app, SET);
            assertTrue(completed >= beforeRetry && completed <= System.currentTimeMillis());
            assertEquals(completed, urls.getLong("last_run_url_" + ENABLED, -1));
            assertEquals(2, dao.getByUrl(ENABLED).orElseThrow().getSize());
            HostsSource disabled = dao.getByUrl(DISABLED).orElseThrow();
            assertFalse(disabled.isEnabled());
            assertNull(disabled.getLocalModificationDate());
            assertEquals(OVERDUE, urls.getLong("last_run_url_" + DISABLED, -1));
        } finally {
            ScheduleRegressionTestSupport.drainDiskAndMain();
            sources.set(app, previousSources);
            blocker.set(app, previousBlocker);
            dao.getByUrl(ENABLED).ifPresent(dao::delete);
            dao.getByUrl(DISABLED).ifPresent(dao::delete);
            AppDatabase.getInstance(app).hostEntryDao().sync();
            assertTrue(urls.edit().clear().commit());
            assertTrue(sets.edit().clear().commit());
            InstrumentedTestState.resetForPassiveRootUi(app, "due worker teardown");
        }
    }

    private static Field modelField(String name) throws ReflectiveOperationException {
        Field field = AdAwayApplication.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static FilterSetUpdateWorker worker(Context context) {
        return TestListenableWorkerBuilder.from(context, FilterSetUpdateWorker.class).build();
    }

    private static void seed(HostsSourceDao dao, String url, boolean enabled) {
        HostsSource source = new HostsSource();
        source.setLabel(url);
        source.setUrl(url);
        source.setEnabled(enabled);
        dao.insert(source);
    }
}
