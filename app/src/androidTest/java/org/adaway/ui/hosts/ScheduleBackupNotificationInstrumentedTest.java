package org.adaway.ui.hosts;

import static org.adaway.ui.hosts.FilterSetStore.SCHEDULE_DAILY;
import static org.adaway.ui.hosts.FilterSetStore.SCHEDULE_OFF;
import static org.adaway.ui.hosts.FilterSetStore.SCHEDULE_WEEKLY;
import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.clickPickerRow;
import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.drainDiskAndMain;
import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.waitForPickerRows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.R;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.testing.UiEvidence;
import org.adaway.ui.source.SourceScheduleStore;
import org.adaway.util.Constants;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Opt-in persisted actions for an external Android backup-queue check. Run one case with
 * backupNotificationPhase=seed, drain its pending backup externally, then run phase=act and
 * inspect Android's pending queue. The test never changes transport settings or clears the
 * queue itself. Ordinary connected runs skip it before changing application preferences.
 */
@RunWith(AndroidJUnit4.class)
public class ScheduleBackupNotificationInstrumentedTest {
    private static final String SET = "Backup notification travel";
    private static final String CREATED_SET = "Backup notification new";
    private static final String RENAMED_SET = "Backup notification renamed";
    private static final String RESTORED_SET = "Backup notification restored";
    private static final String URL = "https://backup-notification.example/hosts.txt";
    private static final String SECOND_URL = "https://backup-notification.example/privacy.txt";
    // A future marker prevents startup reconciliation from running this fixture's schedules.
    private static final long LAST_RUN = 4_000_000_000_000L;
    private static final Set<String> CASES = new HashSet<>(Arrays.asList(
            "create", "setTime", "global", "unchanged", "housekeeping", "restoreInput",
            "cancel", "managerUrl", "editorUrl", "editorNoop", "globalDisable", "setOff",
            "membership", "rename", "delete"));

    @Test(timeout = 120_000)
    public void exerciseBackupNotificationCase() throws Exception {
        Bundle arguments = InstrumentationRegistry.getArguments();
        String phase = arguments.getString("backupNotificationPhase", "");
        String testCase = arguments.getString("backupNotificationCase", "");
        assumeTrue("Run only with the external backup queue controller.",
                !phase.isEmpty() || !testCase.isEmpty());
        assertTrue("Expected backupNotificationPhase=seed|act", "seed".equals(phase)
                || "act".equals(phase));
        assertTrue("Unknown backupNotificationCase: " + testCase, CASES.contains(testCase));
        Context context = ApplicationProvider.getApplicationContext();
        if ("seed".equals(phase)) {
            seed(context);
        } else {
            act(context, testCase);
        }
        flush(context);
        System.out.println("Backup notification probe: case=" + testCase + ", phase=" + phase
                + ", persisted assertions complete; expected pending after act="
                + (expectsPending(testCase) ? "1" : "0"));
    }

    private static void seed(Context context) throws Exception {
        InstrumentedTestState.resetForPassiveRootUi(context, "backup notification seed");
        assertTrue(setPrefs(context).edit().clear().commit());
        assertTrue(urlPrefs(context).edit().clear().commit());
        FilterSetStore.setGlobalSchedule(context, SCHEDULE_DAILY, 1, 7, 5);
        FilterSetStore.setGlobalLastRun(context, LAST_RUN);
        FilterSetStore.saveScheduledSet(context, SET, Collections.singleton(URL),
                SCHEDULE_WEEKLY, 4, 17, 23);
        FilterSetStore.setLastRun(context, SET, LAST_RUN);
        SourceScheduleStore.save(context, URL, SCHEDULE_DAILY, 1, 6, 30);
        assertTrue(urlPrefs(context).edit().putLong("last_run_url_" + URL, LAST_RUN).commit());
        // The external controller drains seed notifications after this phase returns.
        InstrumentedTestState.resetWorkManager(context, "backup notification seed complete");
    }

    private static void act(Context context, String testCase) throws Exception {
        assertEquals("Run the seed phase before each act phase", LAST_RUN,
                FilterSetStore.getLastRun(context, SET));
        switch (testCase) {
            case "create":
                assertFalse(FilterSetStore.hasSet(context, CREATED_SET));
                FilterSetStore.saveScheduledSet(context, CREATED_SET, Collections.singleton(URL),
                        SCHEDULE_WEEKLY, 4, 17, 23);
                assertSet(context, CREATED_SET, SCHEDULE_WEEKLY, 4, 17, 23);
                break;
            case "setTime":
                FilterSetStore.setSchedule(context, SET, SCHEDULE_WEEKLY, 4, 17, 24);
                assertSet(context, SET, SCHEDULE_WEEKLY, 4, 17, 24);
                break;
            case "global":
                FilterSetStore.setGlobalSchedule(context, SCHEDULE_WEEKLY, 5, 8, 16);
                assertTrue(FilterSetStore.isGlobalScheduleEnabled(context));
                assertEquals(SCHEDULE_WEEKLY, FilterSetStore.getGlobalSchedule(context));
                assertEquals(5, FilterSetStore.getGlobalWeekdayIso(context));
                assertEquals(8, FilterSetStore.getGlobalHour(context));
                assertEquals(16, FilterSetStore.getGlobalMinute(context));
                break;
            case "unchanged":
                Map<String, ?> before = setPrefs(context).getAll();
                FilterSetStore.saveSet(context, SET, Collections.singleton(URL));
                FilterSetStore.saveScheduledSet(context, SET, Collections.singleton(URL),
                        SCHEDULE_WEEKLY, 4, 17, 23);
                FilterSetStore.setSchedule(context, SET, SCHEDULE_WEEKLY);
                FilterSetStore.setSchedule(context, SET, SCHEDULE_WEEKLY, 4, 17, 23);
                FilterSetStore.setGlobalSchedule(context, SCHEDULE_DAILY, 6, 7, 5);
                FilterSetStore.setGlobalEnabled(context, true);
                assertTrue(FilterSetStore.renameSet(context, SET, SET));
                assertFalse(FilterSetStore.deleteSet(context, "Missing backup notification set"));
                assertEquals(before, setPrefs(context).getAll());
                assertEquals(LAST_RUN, FilterSetStore.getLastRun(context, SET));
                assertEquals(LAST_RUN, FilterSetStore.getGlobalLastRun(context));
                break;
            case "housekeeping":
                FilterSetStore.setLastRun(context, SET, LAST_RUN + 1);
                FilterSetStore.setGlobalLastRun(context, LAST_RUN + 1);
                assertTrue(urlPrefs(context).edit().putLong("last_run_url_" + URL,
                        LAST_RUN + 1).commit());
                assertEquals(LAST_RUN + 1, FilterSetStore.getLastRun(context, SET));
                assertEquals(LAST_RUN + 1, FilterSetStore.getGlobalLastRun(context));
                assertEquals(LAST_RUN + 1, urlPrefs(context).getLong("last_run_url_" + URL, -1));
                break;
            case "restoreInput":
                // Android restores preferences directly. Reading/migrating that input is not
                // a user edit and must not request another backup of the same restoration.
                assertTrue(setPrefs(context).edit()
                        .putStringSet("names", Collections.singleton(RESTORED_SET))
                        .putStringSet("set_" + RESTORED_SET, Collections.singleton(SECOND_URL))
                        .putInt("schedule_" + RESTORED_SET, SCHEDULE_WEEKLY)
                        .putInt("schedule_weekday_" + RESTORED_SET, 2)
                        .putInt("schedule_hour_" + RESTORED_SET, 9)
                        .putInt("schedule_minute_" + RESTORED_SET, 12)
                        .putLong("last_run_" + RESTORED_SET, LAST_RUN).commit());
                assertTrue(FilterSetStore.getSetNames(context).contains(RESTORED_SET));
                assertEquals(Collections.singleton(SECOND_URL),
                        FilterSetStore.getSetUrls(context, RESTORED_SET));
                assertEquals(SCHEDULE_WEEKLY, FilterSetStore.getSchedule(context, RESTORED_SET));
                assertEquals(2, FilterSetStore.getScheduleWeekdayIso(context, RESTORED_SET));
                assertEquals(9, FilterSetStore.getScheduleHour(context, RESTORED_SET));
                assertEquals(12, FilterSetStore.getScheduleMinute(context, RESTORED_SET));
                assertEquals(LAST_RUN, FilterSetStore.getLastRun(context, RESTORED_SET));
                FilterSetUpdateService.syncPreferences(context);
                break;
            case "cancel":
                cancelPicker(context);
                break;
            case "managerUrl":
                SourceScheduleStore.save(context, URL, SCHEDULE_WEEKLY, 3, 10, 11);
                assertUrl(context, SCHEDULE_WEEKLY, 3, 10, 11);
                assertFalse(urlPrefs(context).contains("last_run_url_" + URL));
                break;
            case "editorUrl":
                SourceScheduleStore.save(context, URL, SCHEDULE_DAILY, 1, 6, 31);
                assertUrl(context, SCHEDULE_DAILY, 1, 6, 31);
                assertFalse(urlPrefs(context).contains("last_run_url_" + URL));
                break;
            case "editorNoop":
                Map<String, ?> urlBefore = urlPrefs(context).getAll();
                SourceScheduleStore.save(context, URL, SCHEDULE_DAILY, 6, 6, 30);
                assertEquals(urlBefore, urlPrefs(context).getAll());
                assertEquals(LAST_RUN, urlPrefs(context).getLong("last_run_url_" + URL, -1));
                break;
            case "globalDisable":
                FilterSetStore.setGlobalEnabled(context, false);
                assertFalse(FilterSetStore.isGlobalScheduleEnabled(context));
                assertEquals(SCHEDULE_DAILY, FilterSetStore.getGlobalSchedule(context));
                break;
            case "setOff":
                FilterSetStore.setSchedule(context, SET, SCHEDULE_OFF);
                assertSet(context, SET, SCHEDULE_OFF, 4, 17, 23);
                break;
            case "membership":
                Set<String> urls = new HashSet<>(Arrays.asList(URL, SECOND_URL));
                FilterSetStore.saveSet(context, SET, urls);
                assertEquals(urls, FilterSetStore.getSetUrls(context, SET));
                break;
            case "rename":
                String id = FilterSetStore.getSetIdForDisplayName(context, SET);
                assertTrue(FilterSetStore.renameSet(context, SET, RENAMED_SET));
                assertFalse(FilterSetStore.hasSet(context, SET));
                assertSet(context, RENAMED_SET, SCHEDULE_WEEKLY, 4, 17, 23);
                assertEquals(id, FilterSetStore.getSetIdForDisplayName(context, RENAMED_SET));
                break;
            case "delete":
                assertTrue(FilterSetStore.deleteSet(context, SET));
                assertFalse(FilterSetStore.hasSet(context, SET));
                assertTrue(FilterSetStore.getSetUrls(context, SET).isEmpty());
                break;
            default:
                throw new AssertionError("Unhandled backup notification case: " + testCase);
        }
    }

    private static void cancelPicker(Context context) {
        Map<String, ?> before = setPrefs(context).getAll();
        try (ActivityScenario<SchedulesActivity> screen = ActivityScenario.launch(SchedulesActivity.class)) {
            screen.onActivity(activity -> activity.findViewById(R.id.globalEditButton).performClick());
            clickPickerRow(screen, context.getString(R.string.filter_set_schedule_weekly));
            waitForPickerRows(screen, context.getString(R.string.filter_set_schedule_pick_day));
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(before, setPrefs(context).getAll());
            UiEvidence.capture(context, "backup-notification-cancelled");
        }
    }

    private static void assertSet(Context context, String name, int cadence, int weekday, int hour,
            int minute) {
        assertTrue(FilterSetStore.hasSet(context, name));
        assertEquals(Collections.singleton(URL), FilterSetStore.getSetUrls(context, name));
        assertEquals(cadence, FilterSetStore.getSchedule(context, name));
        assertEquals(weekday, FilterSetStore.getScheduleWeekdayIso(context, name));
        assertEquals(hour, FilterSetStore.getScheduleHour(context, name));
        assertEquals(minute, FilterSetStore.getScheduleMinute(context, name));
    }

    private static void assertUrl(Context context, int cadence, int weekday, int hour, int minute) {
        SharedPreferences prefs = urlPrefs(context);
        assertEquals(cadence, prefs.getInt("schedule_url_" + URL, -1));
        assertEquals(weekday, prefs.getInt("weekday_url_" + URL, -1));
        assertEquals(hour, prefs.getInt("hour_url_" + URL, -1));
        assertEquals(minute, prefs.getInt("minute_url_" + URL, -1));
    }

    private static boolean expectsPending(String testCase) {
        return !Arrays.asList("unchanged", "housekeeping", "restoreInput", "cancel", "editorNoop")
                .contains(testCase);
    }

    private static SharedPreferences setPrefs(Context context) {
        return context.getSharedPreferences(Constants.PREFS_FILTER_SETS, Context.MODE_PRIVATE);
    }

    private static SharedPreferences urlPrefs(Context context) {
        return context.getSharedPreferences(Constants.PREFS_SOURCE_SCHEDULES, Context.MODE_PRIVATE);
    }

    private static void flush(Context context) throws Exception {
        drainDiskAndMain();
        // commit waits for preceding apply writes to reach disk without changing preferences.
        assertTrue(setPrefs(context).edit().commit());
        assertTrue(urlPrefs(context).edit().commit());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
