package org.adaway.ui.hosts;

import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.clickText;
import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.drainDiskAndMain;
import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.hasText;
import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.waitFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import androidx.appcompat.widget.Toolbar;

import org.adaway.R;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.testing.UiEvidence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class SchedulesNavigationInstrumentedTest {
    private Context context;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(context, "schedule navigation setup");
        drainDiskAndMain();
        FilterSetStore.setGlobalSchedule(context, FilterSetStore.SCHEDULE_DAILY, 3, 8, 25);
        FilterSetStore.setGlobalEnabled(context, false);
    }

    @After
    public void tearDown() throws Exception {
        drainDiskAndMain();
        InstrumentedTestState.resetForPassiveRootUi(context, "schedule navigation teardown");
    }

    @Test
    public void toolbarUpIsVisibleLabelledAndFinishes() {
        try (ActivityScenario<SchedulesActivity> scenario =
                     ActivityScenario.launch(SchedulesActivity.class)) {
            UiEvidence.capture(context, "schedule-navigation");
            AtomicReference<SchedulesActivity> owner = new AtomicReference<>();
            scenario.onActivity(activity -> {
                owner.set(activity);
                assertEquals("Schedule heading must not be duplicated", 1,
                        countHeadings(activity.getWindow().getDecorView(),
                                context.getString(R.string.menu_manage_schedules)));
                Toolbar toolbar = activity.findViewById(androidx.appcompat.R.id.action_bar);
                assertNotNull(toolbar);
                assertNotNull("Schedule toolbar needs a visible Up icon", toolbar.getNavigationIcon());
                assertNotNull("Up icon needs an accessibility label",
                        toolbar.getNavigationContentDescription());
                View navigation = null;
                for (int i = 0; i < toolbar.getChildCount(); i++) {
                    View child = toolbar.getChildAt(i);
                    if (toolbar.getNavigationContentDescription().equals(
                            child.getContentDescription())) navigation = child;
                }
                assertNotNull(navigation);
                assertTrue(navigation.isShown());
                assertTrue(navigation.performClick());
            });
            waitFor("toolbar Up finish", () -> owner.get().isFinishing());
        }
    }

    @Test
    public void cancellingDailyTimeDoesNotEnablePreviousSchedule() {
        assertCancellingPickerKeepsDisabled(R.string.filter_set_schedule_daily);
    }

    @Test
    public void cancellingWeeklyDayDoesNotEnablePreviousSchedule() {
        assertCancellingPickerKeepsDisabled(R.string.filter_set_schedule_weekly);
    }

    private void assertCancellingPickerKeepsDisabled(int choice) {
        try (ActivityScenario<SchedulesActivity> scenario =
                     ActivityScenario.launch(SchedulesActivity.class)) {
            waitFor("schedule screen before opening editor", () ->
                    hasText(context.getString(R.string.menu_manage_schedules)));
            scenario.onActivity(activity -> activity.findViewById(R.id.globalEditButton).performClick());
            clickText(context.getString(choice));
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            assertFalse("Cancelling must not re-enable the previous daily schedule",
                    FilterSetStore.isGlobalScheduleEnabled(context));
            assertEquals(FilterSetStore.SCHEDULE_DAILY, FilterSetStore.getGlobalSchedule(context));
            assertEquals(8, FilterSetStore.getGlobalHour(context));
            assertEquals(25, FilterSetStore.getGlobalMinute(context));
        }
    }

    private int countHeadings(View view, String heading) {
        int count = view instanceof TextView && heading.contentEquals(
                ((TextView) view).getText()) ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                count += countHeadings(group.getChildAt(i), heading);
            }
        }
        return count;
    }
}
