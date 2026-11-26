package org.adaway.ui.hosts;

import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.clickPickerRow;
import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.drainDiskAndMain;
import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.openToolbarPicker;
import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.waitFor;
import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.withDialogView;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TimePicker;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostsSource;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.adaway.util.Constants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 29)
public class SchedulePickerInstrumentedTest {
    private static final String SET = "TravelRestore picker fixture";
    private static final String URL = "https://schedule-picker.example/list";
    private static final String SOURCE = "! Schedule picker fixture";
    private final Context context = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() throws Exception {
        InstrumentedTestState.resetForPassiveRootUi(context, "schedule picker setup");
        assertTrue(context.getSharedPreferences(Constants.PREFS_FILTER_SETS, Context.MODE_PRIVATE)
                .edit().clear().commit());
        assertTrue(urlPrefs().edit().clear().commit());
        FilterSetStore.setGlobalSchedule(context, FilterSetStore.SCHEDULE_OFF, 1, 3, 0);
        AppDatabase.getInstance(context).getOpenHelper().getWritableDatabase();
        drainDiskAndMain();
        FilterSetStore.saveScheduledSet(context, SET, Collections.singleton(URL),
                FilterSetStore.SCHEDULE_WEEKLY, 4, 17, 23);
        FilterSetStore.setLastRun(context, SET, System.currentTimeMillis());
    }

    @After
    public void tearDown() throws Exception {
        drainDiskAndMain();
        FilterSetStore.deleteSet(context, SET);
        SharedPreferences.Editor edit = urlPrefs().edit();
        for (String prefix : new String[]{"schedule_url_", "weekday_url_", "hour_url_",
                "minute_url_", "last_run_url_"}) edit.remove(prefix + URL);
        assertTrue(edit.commit());
        var dao = AppDatabase.getInstance(context).hostsSourceDao();
        dao.getByUrl(URL).ifPresent(dao::delete);
        InstrumentedTestState.resetForPassiveRootUi(context, "schedule picker teardown");
    }

    @Test
    public void managerWeeklySetReopensOnThursdayAt1723() {
        try (ActivityScenario<SchedulesActivity> screen = ActivityScenario.launch(SchedulesActivity.class)) {
            screen.onActivity(a -> a.findViewById(R.id.editFilterSetSchedulesButton).performClick());
            clickPickerRow(screen, SET);
            chooseSavedWeekly(screen);
            assertTime(screen, 17, 23);
            clickTimeButton(screen, android.R.id.button1);
            waitForScreen(screen);
            assertEquals(4, FilterSetStore.getScheduleWeekdayIso(context, SET));
            assertEquals(17, FilterSetStore.getScheduleHour(context, SET));
            assertEquals(23, FilterSetStore.getScheduleMinute(context, SET));
        }
    }

    @Test
    public void managerGlobalAndUrlDailyKeepTheirOwnTimeAndLastRun() {
        FilterSetStore.setGlobalSchedule(context, FilterSetStore.SCHEDULE_DAILY, 1, 6, 41);
        long stamp = FilterSetStore.getGlobalLastRun(context);
        long urlStamp = stamp - 1;
        seedUrlSchedule(urlStamp);
        try (ActivityScenario<SchedulesActivity> screen = ActivityScenario.launch(SchedulesActivity.class)) {
            screen.onActivity(a -> a.findViewById(R.id.globalEditButton).performClick());
            chooseDaily(screen);
            assertTime(screen, 6, 41);
            clickTimeButton(screen, android.R.id.button1);
            waitForScreen(screen);
            assertEquals(stamp, FilterSetStore.getGlobalLastRun(context));
            FilterSetStore.setGlobalEnabled(context, false);
            screen.onActivity(a -> a.findViewById(R.id.globalEditButton).performClick());
            assertChoice(screen, 0, R.string.schedule_global_title);
            back();
            waitForScreen(screen);

            screen.onActivity(a -> a.findViewById(R.id.editSourceSchedulesButton).performClick());
            clickPickerRow(screen, SOURCE);
            chooseDaily(screen);
            assertTime(screen, 21, 8);
            assertUrlClock("before confirmation", urlStamp);
            clickTimeButton(screen, android.R.id.button1);
            waitForScreen(screen);
            assertUrlClock("after confirmation", urlStamp);
        }
    }

    @Test
    public void sourcesToolbarMarksRestoredSetAndDiscardsUnconfirmedDay() {
        try (ActivityScenario<HomeActivity> screen = ActivityScenario.launch(HomeActivity.class)) {
            screen.onActivity(a -> a.navigateTo(R.id.nav_sources));
            openToolbarPicker(screen);
            clickPickerRow(screen, SET);
            assertChoice(screen, 2, R.string.filter_set_schedule_weekly);
            clickPickerRow(screen, context.getString(R.string.filter_set_schedule_weekly));
            assertChoice(screen, 3, R.string.filter_set_schedule_pick_day);
            clickPickerRow(screen, dayName(DayOfWeek.FRIDAY));
            assertTime(screen, 17, 23);
            back();
            waitForScreen(screen);
            assertEquals(4, FilterSetStore.getScheduleWeekdayIso(context, SET));
            openToolbarPicker(screen);
            clickPickerRow(screen, SET);
            chooseSavedWeekly(screen);
            assertTime(screen, 17, 23);
            withDialogView(screen, TimePicker.class, null, picker -> picker.setMinute(24));
            clickTimeButton(screen, android.R.id.button1);
            waitForScreen(screen);
            assertEquals(24, FilterSetStore.getScheduleMinute(context, SET));
        }
    }

    @Test
    public void unsavedSelectionHasNoCheckedRowAndStillStartsAtCurrentTime() {
        try (ActivityScenario<HomeActivity> screen = ActivityScenario.launch(HomeActivity.class)) {
            screen.onActivity(a -> a.navigateTo(R.id.nav_sources));
            openToolbarPicker(screen);
            clickPickerRow(screen, context.getString(R.string.filter_set_schedule_current_selection));
            assertChoice(screen, -1, R.string.filter_set_schedule_weekly);
            clickPickerRow(screen, context.getString(R.string.filter_set_schedule_weekly));
            assertChoice(screen, -1, R.string.filter_set_schedule_pick_day);
            LocalTime before = LocalTime.now().withSecond(0).withNano(0);
            clickPickerRow(screen, dayName(DayOfWeek.MONDAY));
            withDialogView(screen, TimePicker.class, null, picker -> {
                LocalTime shown = LocalTime.of(picker.getHour(), picker.getMinute());
                assertTrue("Expected current time, got " + shown, shown.equals(before)
                        || shown.equals(LocalTime.now().withSecond(0).withNano(0)));
            });
            clickTimeButton(screen, android.R.id.button2);
            waitForScreen(screen);
        }
    }

    private SharedPreferences urlPrefs() {
        return context.getSharedPreferences(Constants.PREFS_SOURCE_SCHEDULES, Context.MODE_PRIVATE);
    }

    @Test
    public void globalOffKeepsNamedDispatcherThenToolbarOffCancelsIt() throws Exception {
        FilterSetStore.setGlobalSchedule(context, FilterSetStore.SCHEDULE_DAILY, 1, 6, 41);
        FilterSetUpdateService.syncPreferences(context);
        assertDispatchers(1);
        try (ActivityScenario<SchedulesActivity> screen = ActivityScenario.launch(SchedulesActivity.class)) {
            screen.onActivity(a -> a.findViewById(R.id.globalEditButton).performClick());
            clickPickerRow(screen, context.getString(R.string.schedule_off));
            waitForScreen(screen);
            assertEquals(FilterSetStore.SCHEDULE_OFF, FilterSetStore.getGlobalSchedule(context));
            assertDispatchers(1);
        }
        try (ActivityScenario<HomeActivity> screen = ActivityScenario.launch(HomeActivity.class)) {
            screen.onActivity(a -> a.navigateTo(R.id.nav_sources));
            openToolbarPicker(screen);
            clickPickerRow(screen, SET);
            clickPickerRow(screen, context.getString(R.string.filter_set_schedule_off));
            waitForScreen(screen);
            assertEquals(FilterSetStore.SCHEDULE_OFF, FilterSetStore.getSchedule(context, SET));
            assertDispatchers(0);
        }
    }

    @Test
    public void managerSetOffKeepsUrlDispatcherThenUrlOffCancelsIt() throws Exception {
        seedUrlSchedule(System.currentTimeMillis());
        FilterSetUpdateService.syncPreferences(context);
        assertDispatchers(1);
        try (ActivityScenario<SchedulesActivity> screen = ActivityScenario.launch(SchedulesActivity.class)) {
            screen.onActivity(a -> a.findViewById(R.id.editFilterSetSchedulesButton).performClick());
            clickPickerRow(screen, SET);
            clickPickerRow(screen, context.getString(R.string.filter_set_schedule_off));
            waitForScreen(screen);
            assertEquals(FilterSetStore.SCHEDULE_OFF, FilterSetStore.getSchedule(context, SET));
            assertDispatchers(1);
            screen.onActivity(a -> a.findViewById(R.id.editSourceSchedulesButton).performClick());
            clickPickerRow(screen, SOURCE);
            clickPickerRow(screen, context.getString(R.string.filter_set_schedule_off));
            waitForScreen(screen);
            assertEquals(FilterSetStore.SCHEDULE_OFF, urlPrefs().getInt("schedule_url_" + URL, -1));
            assertDispatchers(0);
        }
    }

    private void seedUrlSchedule(long stamp) {
        HostsSource source = new HostsSource();
        source.setLabel(SOURCE);
        source.setUrl(URL);
        source.setEnabled(false);
        AppDatabase.getInstance(context).hostsSourceDao().insert(source);
        assertTrue(urlPrefs().edit().putInt("schedule_url_" + URL, FilterSetStore.SCHEDULE_DAILY)
                .putInt("hour_url_" + URL, 21).putInt("minute_url_" + URL, 8)
                .putLong("last_run_url_" + URL, stamp).commit());
    }

    private void assertUrlClock(String stage, long expected) {
        assertEquals(stage + ": " + urlPrefs().getAll(), expected,
                urlPrefs().getLong("last_run_url_" + URL, -1));
    }

    private void assertDispatchers(int count) throws Exception {
        WorkManager manager = WorkManager.getInstance(context);
        manager.pruneWork().getResult().get(10, TimeUnit.SECONDS);
        assertEquals(count, manager.getWorkInfosForUniqueWork(FilterSetUpdateService.WORK_NAME)
                .get(10, TimeUnit.SECONDS).stream().filter(info -> !info.getState().isFinished()).count());
    }

    private void chooseSavedWeekly(ActivityScenario<?> screen) {
        assertChoice(screen, 2, R.string.filter_set_schedule_weekly);
        clickPickerRow(screen, context.getString(R.string.filter_set_schedule_weekly));
        assertChoice(screen, 3, R.string.filter_set_schedule_pick_day);
        clickPickerRow(screen, dayName(DayOfWeek.THURSDAY));
    }

    private void chooseDaily(ActivityScenario<?> screen) {
        assertChoice(screen, 1, R.string.filter_set_schedule_daily);
        clickPickerRow(screen, context.getString(R.string.filter_set_schedule_daily));
    }

    private static String dayName(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, Locale.getDefault());
    }

    private void assertChoice(ActivityScenario<?> screen, int expected, int visibleLabel) {
        withDialogView(screen, ListView.class, context.getString(visibleLabel),
                list -> assertEquals(expected, list.getCheckedItemPosition()));
    }

    private void assertTime(ActivityScenario<?> screen, int hour, int minute) {
        withDialogView(screen, TimePicker.class, null, picker -> {
            assertEquals(hour, picker.getHour());
            assertEquals(minute, picker.getMinute());
        });
    }

    private static void back() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
    }

    private static void waitForScreen(ActivityScenario<? extends Activity> screen) {
        waitFor("dialog dismissed to original screen", () -> {
            AtomicBoolean focused = new AtomicBoolean();
            screen.onActivity(a -> focused.set(a.hasWindowFocus()));
            return focused.get();
        });
    }

    private static void clickTimeButton(ActivityScenario<?> screen, int id) {
        withDialogView(screen, TimePicker.class, null, picker -> {
            Button button = picker.getRootView().findViewById(id);
            assertTrue("Missing time-picker button", button != null && button.isShown() && button.isEnabled());
            assertTrue(button.performClick());
        });
    }
}
