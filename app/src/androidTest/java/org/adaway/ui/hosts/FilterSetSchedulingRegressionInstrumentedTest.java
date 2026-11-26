package org.adaway.ui.hosts;

import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.clickPickerRow;
import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.clickText;
import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.drainDiskAndMain;
import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.hasText;
import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.openToolbarPicker;
import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.waitFor;
import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.waitForPickerRows;
import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.withDialogView;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ListView;
import android.widget.TimePicker;

import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostsSource;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class FilterSetSchedulingRegressionInstrumentedTest {
    private static final String URL = "https://selection-schedule.test/enabled.txt";
    private static final String DISABLED_URL = "https://selection-schedule.test/disabled.txt";
    private static final String ACTIVE = "Existing active profile";
    private Context mContext;
    private SharedPreferences mPrefs;
    private Set<String> mEnabledUrls;
    private HomeActivity mActivity;

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(mContext, "selection schedule setup");
        mPrefs = mContext.getSharedPreferences("filter_sets", Context.MODE_PRIVATE);
        assertTrue(mPrefs.edit().clear().commit());
        assertTrue(mContext.getSharedPreferences("source_schedules", Context.MODE_PRIVATE)
                .edit().clear().commit());
        FilterSetStore.setGlobalSchedule(mContext, FilterSetStore.SCHEDULE_OFF, 1, 3, 0);
        AppDatabase.getInstance(mContext).getOpenHelper().getWritableDatabase();
        drainDiskAndMain();
        seedSource(URL, true);
        seedSource(DISABLED_URL, false);
        mEnabledUrls = new HashSet<>();
        for (HostsSource source : AppDatabase.getInstance(mContext).hostsSourceDao().getAll()) {
            if (source.getId() != HostsSource.USER_SOURCE_ID && source.isEnabled()) {
                mEnabledUrls.add(source.getUrl());
            }
        }
        FilterSetStore.saveSet(mContext, ACTIVE, mEnabledUrls);
        FilterSetStore.setActiveProfile(mContext, ACTIVE);
    }

    @After
    public void tearDown() throws Exception {
        if (mContext != null) {
            drainDiskAndMain();
            InstrumentedTestState.resetForPassiveRootUi(mContext, "selection schedule teardown");
            for (String url : new String[]{URL, DISABLED_URL}) {
                AppDatabase.getInstance(mContext).hostsSourceDao().getByUrl(url).ifPresent(
                        source -> AppDatabase.getInstance(mContext).hostsSourceDao().delete(source));
            }
            assertTrue(mPrefs.edit().clear().commit());
            assertTrue(mContext.getSharedPreferences("source_schedules", Context.MODE_PRIVATE)
                    .edit().clear().commit());
            FilterSetStore.setGlobalSchedule(mContext, FilterSetStore.SCHEDULE_OFF, 1, 3, 0);
        }
    }

    @Test(timeout = 120_000)
    public void cancelAtModeDayAndEitherTimePickerLeavesProfilesAndIdentityUnchanged()
            throws Exception {
        assertAbandonedChains(false);
    }

    @Test(timeout = 120_000)
    public void backAtModeDayAndEitherTimePickerLeavesProfilesAndIdentityUnchanged()
            throws Exception {
        assertAbandonedChains(true);
    }

    @Test(timeout = 60_000)
    public void offForCurrentSelectionCreatesNoProfile() throws Exception {
        try (ActivityScenario<HomeActivity> scenario = launchSources()) {
            Map<String, ?> before = new HashMap<>(mPrefs.getAll());
            openCurrentSelection(scenario);
            clickPickerRow(scenario, mContext.getString(R.string.filter_set_schedule_off));
            waitForNoDialog();
            assertEquals(before, mPrefs.getAll());
        }
    }

    @Test(timeout = 60_000)
    public void offForExistingSetKeepsProfileAndDisablesOnlyItsSchedule() throws Exception {
        FilterSetStore.setSchedule(mContext, ACTIVE, FilterSetStore.SCHEDULE_WEEKLY, 4, 6, 15);
        FilterSetStore.setLastRun(mContext, ACTIVE, System.currentTimeMillis());
        try (ActivityScenario<HomeActivity> scenario = launchSources()) {
            Set<String> names = FilterSetStore.getSetNames(mContext);
            openToolbarPicker(scenario);
            clickPickerRow(scenario, ACTIVE);
            clickPickerRow(scenario, mContext.getString(R.string.filter_set_schedule_off));
            waitForNoDialog();
            assertEquals(names, FilterSetStore.getSetNames(mContext));
            assertEquals(ACTIVE, FilterSetStore.getActiveProfile(mContext));
            assertEquals(mEnabledUrls, FilterSetStore.getSetUrls(mContext, ACTIVE));
            assertEquals(FilterSetStore.SCHEDULE_OFF, FilterSetStore.getSchedule(mContext, ACTIVE));
            assertEquals(4, FilterSetStore.getScheduleWeekdayIso(mContext, ACTIVE));
            assertEquals(6, FilterSetStore.getScheduleHour(mContext, ACTIVE));
            assertEquals(15, FilterSetStore.getScheduleMinute(mContext, ACTIVE));
        }
    }

    @Test(timeout = 60_000)
    public void confirmDailySelectionUsesPendingSnapshotAndAvoidsNewNameCollision()
            throws Exception {
        assertConfirmedSelection(false);
    }

    @Test(timeout = 60_000)
    public void confirmWeeklySelectionPersistsWeekdayWithoutChangingActiveIdentity()
            throws Exception {
        assertConfirmedSelection(true);
    }

    @Test(timeout = 60_000)
    public void confirmedStoreWritePublishesCompleteScheduleWithoutChangingActiveIdentity() {
        String name = "Confirmed selection";
        Set<String> snapshot = new HashSet<>(Collections.singleton(URL));
        String identity = mPrefs.getString("active_profile_id", null);
        AtomicInteger observations = new AtomicInteger();
        SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
            if (!FilterSetStore.hasSet(mContext, name)) return;
            observations.incrementAndGet();
            assertEquals(Collections.singleton(URL), FilterSetStore.getSetUrls(mContext, name));
            assertEquals(FilterSetStore.SCHEDULE_WEEKLY, FilterSetStore.getSchedule(mContext, name));
            assertEquals(5, FilterSetStore.getScheduleWeekdayIso(mContext, name));
            assertEquals(7, FilterSetStore.getScheduleHour(mContext, name));
            assertEquals(25, FilterSetStore.getScheduleMinute(mContext, name));
            assertEquals(identity, prefs.getString("active_profile_id", null));
        };
        mPrefs.registerOnSharedPreferenceChangeListener(listener);
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    FilterSetStore.saveScheduledSet(mContext, name, snapshot,
                            FilterSetStore.SCHEDULE_WEEKLY, 5, 7, 25));
            snapshot.clear();
            assertTrue("Preference observers saw the complete write", observations.get() > 0);
            assertEquals(Collections.singleton(URL), FilterSetStore.getSetUrls(mContext, name));
            assertEquals(ACTIVE, FilterSetStore.getActiveProfile(mContext));
            assertEquals(identity, mPrefs.getString("active_profile_id", null));
        } finally {
            mPrefs.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }

    private void assertAbandonedChains(boolean back) throws Exception {
        try (ActivityScenario<HomeActivity> scenario = launchSources()) {
            Map<String, ?> before = new HashMap<>(mPrefs.getAll());
            // Mode, weekday, daily time, weekly time. Repetition must not create numbered sets.
            for (int step = 0; step < 4; step++) {
                openCurrentSelection(scenario);
                if (step == 1 || step == 3) {
                    clickPickerRow(scenario, mContext.getString(R.string.filter_set_schedule_weekly));
                    waitForPickerRows(scenario, mContext.getString(R.string.filter_set_schedule_pick_day));
                    if (step == 3) {
                        clickPickerRow(scenario, DayOfWeek.WEDNESDAY.getDisplayName(
                                TextStyle.FULL, Locale.getDefault()));
                    }
                } else if (step == 2) {
                    clickPickerRow(scenario, mContext.getString(R.string.filter_set_schedule_daily));
                }
                if (back && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    backFromFocusedPicker(scenario, step);
                } else {
                    if (step >= 2) {
                        waitFor("time picker", () -> hasText(mContext.getString(android.R.string.ok)));
                    }
                    if (back) {
                        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                    } else {
                        clickText(mContext.getString(step >= 2
                                ? android.R.string.cancel : R.string.button_cancel));
                    }
                    waitForNoDialog();
                }
                assertEquals("Cancelled step " + step + " changed persisted state",
                        before, mPrefs.getAll());
                assertEquals(ACTIVE, FilterSetStore.getActiveProfile(mContext));
            }
        }
    }

    private void assertConfirmedSelection(boolean weekly) throws Exception {
        String baseName = mContext.getString(R.string.filter_set_schedule_current_selection_saved_name);
        String name = baseName + " 2";
        String identity = mPrefs.getString("active_profile_id", null);
        // These tests inspect scheduling, not downloads/apply. Stamp the new schedule before
        // the confirmation callback enqueues work so the worker has no immediately due set.
        SharedPreferences.OnSharedPreferenceChangeListener preventImmediateRun = (prefs, key) -> {
            if (FilterSetStore.hasSet(mContext, name)
                    && FilterSetStore.getLastRun(mContext, name) == 0) {
                FilterSetStore.setLastRun(mContext, name, System.currentTimeMillis());
            }
        };
        mPrefs.registerOnSharedPreferenceChangeListener(preventImmediateRun);
        try (ActivityScenario<HomeActivity> scenario = launchSources()) {
            try {
                openCurrentSelection(scenario);
                // Simulate a profile saved while the chain is open; confirmation must not overwrite it.
                FilterSetStore.saveSet(mContext, baseName, Collections.singleton(DISABLED_URL));
                AppDatabase.getInstance(mContext).hostsSourceDao().getByUrl(URL).ifPresent(source -> {
                    source.setEnabled(false);
                    AppDatabase.getInstance(mContext).hostsSourceDao().update(source);
                });
                waitForSourceEnabled(scenario, false);
                clickPickerRow(scenario, mContext.getString(weekly ? R.string.filter_set_schedule_weekly
                        : R.string.filter_set_schedule_daily));
                if (weekly) {
                    clickPickerRow(scenario, DayOfWeek.WEDNESDAY.getDisplayName(TextStyle.FULL, Locale.getDefault()));
                }
                clickText(mContext.getString(android.R.string.ok));
                waitForNoDialog();
                assertEquals(mEnabledUrls, FilterSetStore.getSetUrls(mContext, name));
                assertEquals(Collections.singleton(DISABLED_URL),
                        FilterSetStore.getSetUrls(mContext, baseName));
                assertEquals(weekly ? FilterSetStore.SCHEDULE_WEEKLY : FilterSetStore.SCHEDULE_DAILY,
                        FilterSetStore.getSchedule(mContext, name));
                assertEquals(weekly ? 3 : 1, FilterSetStore.getScheduleWeekdayIso(mContext, name));
                assertEquals(ACTIVE, FilterSetStore.getActiveProfile(mContext));
                assertEquals(identity, mPrefs.getString("active_profile_id", null));
                WorkManager manager = WorkManager.getInstance(mContext);
                manager.pruneWork().getResult().get(10, TimeUnit.SECONDS);
                assertEquals(1, manager.getWorkInfosForUniqueWork(FilterSetUpdateService.WORK_NAME)
                        .get(10, TimeUnit.SECONDS).stream()
                        .filter(info -> !info.getState().isFinished()).count());
            } finally {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(mActivity::finish);
            }
        } finally {
            mPrefs.unregisterOnSharedPreferenceChangeListener(preventImmediateRun);
        }
    }

    private void waitForSourceEnabled(ActivityScenario<HomeActivity> scenario, boolean enabled)
            throws Exception {
        Field sourcesField = HostsSourcesFragment.class.getDeclaredField("lastSources");
        sourcesField.setAccessible(true);
        waitFor("updated source observation", () -> {
            AtomicBoolean observed = new AtomicBoolean();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                Fragment parent = mActivity.getSupportFragmentManager()
                        .findFragmentById(R.id.nav_fragment_container);
                assertNotNull(parent);
                Fragment child = parent.getChildFragmentManager()
                        .findFragmentById(R.id.hosts_sources_child_container);
                try {
                    @SuppressWarnings("unchecked")
                    List<HostsSource> sources = (List<HostsSource>) sourcesField.get(child);
                    observed.set(sources.stream().anyMatch(source -> URL.equals(source.getUrl())
                            && source.isEnabled() == enabled));
                } catch (IllegalAccessException exception) {
                    throw new AssertionError(exception);
                }
            });
            return observed.get();
        });
    }

    private ActivityScenario<HomeActivity> launchSources() throws Exception {
        ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class);
        try {
            scenario.onActivity(activity -> {
                mActivity = activity;
                activity.navigateTo(R.id.nav_sources);
            });
            Field sourcesField = HostsSourcesFragment.class.getDeclaredField("lastSources");
            sourcesField.setAccessible(true);
            waitFor("source observer delivery", () -> {
                AtomicBoolean loaded = new AtomicBoolean();
                scenario.onActivity(activity -> {
                    Fragment parent = activity.getSupportFragmentManager()
                            .findFragmentById(R.id.nav_fragment_container);
                    if (parent == null) return;
                    Fragment child = parent.getChildFragmentManager()
                            .findFragmentById(R.id.hosts_sources_child_container);
                    if (!(child instanceof HostsSourcesFragment)) return;
                    try {
                        @SuppressWarnings("unchecked")
                        List<HostsSource> sources = (List<HostsSource>) sourcesField.get(child);
                        loaded.set(sources.stream().anyMatch(source -> URL.equals(source.getUrl()))
                                && sources.stream().anyMatch(
                                        source -> DISABLED_URL.equals(source.getUrl())));
                    } catch (IllegalAccessException exception) {
                        throw new AssertionError(exception);
                    }
                });
                return loaded.get();
            });
            return scenario;
        } catch (Exception | AssertionError failure) {
            scenario.close();
            throw failure;
        }
    }

    private void openCurrentSelection(ActivityScenario<HomeActivity> scenario) {
        openToolbarPicker(scenario);
        clickPickerRow(scenario, mContext.getString(R.string.filter_set_schedule_current_selection));
        waitForPickerRows(scenario, mContext.getString(R.string.filter_set_schedule_daily));
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private void backFromFocusedPicker(ActivityScenario<HomeActivity> scenario, int step) {
        Class<? extends View> type = step < 2 ? ListView.class : TimePicker.class;
        String label = step == 0 ? mContext.getString(R.string.filter_set_schedule_daily)
                : step == 1 ? mContext.getString(R.string.filter_set_schedule_pick_day) : null;
        View[] target = new View[1];
        withDialogView(scenario, type, label, picker -> {
            View window = picker.getRootView();
            target[0] = window;
            long downTime = SystemClock.uptimeMillis();
            // A global key can reach Home while a new dialog is taking focus.
            assertTrue("Picker must handle Back down at step " + step, window.dispatchKeyEvent(
                    new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0)));
            assertTrue("Picker must handle Back up at step " + step, window.dispatchKeyEvent(
                    new KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
                            KeyEvent.KEYCODE_BACK, 0)));
        });
        waitFor("step " + step + " picker detached and Sources focused", () -> {
            AtomicBoolean restored = new AtomicBoolean();
            scenario.onActivity(activity -> {
                View toolbar = activity.findViewById(R.id.hosts_sources_toolbar);
                restored.set(activity == mActivity && !target[0].isAttachedToWindow()
                        && toolbar != null && toolbar.isShown() && toolbar.hasWindowFocus());
            });
            return restored.get();
        });
    }

    private void waitForNoDialog() {
        waitFor("dialog dismissal", () -> hasText(mContext.getString(R.string.nav_sources))
                && !hasText(mContext.getString(R.string.button_cancel))
                && !hasText(mContext.getString(android.R.string.cancel)));
    }

    private void seedSource(String url, boolean enabled) {
        HostsSource source = new HostsSource();
        source.setId(enabled ? 93202 : 93203);
        source.setLabel(enabled ? "Schedule enabled fixture" : "Schedule disabled fixture");
        source.setUrl(url);
        source.setEnabled(enabled);
        AppDatabase.getInstance(mContext).hostsSourceDao().insert(source);
    }
}
