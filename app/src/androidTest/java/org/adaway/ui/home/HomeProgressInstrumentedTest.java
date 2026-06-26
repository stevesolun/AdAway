package org.adaway.ui.home;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.lifecycle.MutableLiveData;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.model.source.FilterOperationState;
import org.adaway.model.source.SourceModel;
import org.adaway.testing.InstrumentedTestState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class HomeProgressInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;

    private Context context;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up Home progress");
        publishOperationState(this.context, FilterOperationState.idle());
    }

    @After
    public void tearDown() throws Exception {
        if (this.context != null) {
            publishOperationState(this.context, FilterOperationState.idle());
            InstrumentedTestState.resetForPassiveRootUi(this.context, "tear down Home progress");
        }
    }

    @Test
    public void homeProgressRendersActiveTerminalAndIdleStates() throws Exception {
        try (ActivityScenario<HomeActivity> scenario =
                     ActivityScenario.launch(HomeActivity.class)) {
            waitForVisibility(scenario, R.id.multiPhaseProgressContainer, View.GONE);

            publishOperationState(this.context, operationState(
                    FilterOperationState.Phase.DOWNLOAD,
                    34,
                    0L,
                    false));
            waitForProgressState(scenario, "34.0% Complete", 34, true);
            waitForVisibility(scenario, R.id.checkPhaseLabel, View.GONE);
            waitForVisibility(scenario, R.id.downloadPhaseLabel, View.GONE);
            waitForVisibility(scenario, R.id.parsePhaseLabel, View.GONE);

            publishOperationState(this.context, operationState(
                    FilterOperationState.Phase.PARSE,
                    62,
                    1_234L,
                    false));
            waitForProgressState(
                    scenario,
                    "62.0% Complete - 1,234 accepted rules",
                    62,
                    true);

            publishOperationState(this.context, operationState(
                    FilterOperationState.Phase.FINALIZE,
                    96,
                    1_234L,
                    false));
            waitForProgressState(
                    scenario,
                    "Finalizing protection - 1,234 accepted rules",
                    96,
                    false);

            publishOperationState(this.context, operationState(
                    FilterOperationState.Phase.STOPPED,
                    48,
                    1_234L,
                    true));
            waitForProgressState(scenario, "Update stopped", 48, false);

            publishOperationState(this.context, operationState(
                    FilterOperationState.Phase.COMPLETE,
                    100,
                    1_234L,
                    false));
            waitForProgressState(scenario, "Protection updated", 100, false);

            publishOperationState(this.context, FilterOperationState.idle());
            waitForVisibility(scenario, R.id.multiPhaseProgressContainer, View.GONE);
        }
    }

    private static void waitForProgressState(
            ActivityScenario<HomeActivity> scenario,
            String expectedText,
            int expectedProgress,
            boolean controlsEnabled) {
        waitForVisibility(scenario, R.id.multiPhaseProgressContainer, View.VISIBLE);
        waitForVisibility(scenario, R.id.schedulerTaskContainer, View.VISIBLE);
        waitForText(scenario, R.id.overallProgressText, expectedText);
        waitForProgress(scenario, R.id.overallProgressBar, expectedProgress);
        waitForEnabled(scenario, R.id.pauseResumeButton, controlsEnabled);
        waitForEnabled(scenario, R.id.stopButton, controlsEnabled);
    }

    private static void waitForText(
            ActivityScenario<HomeActivity> scenario,
            int viewId,
            String expectedText) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AtomicReference<String> actualText = new AtomicReference<>("");
            scenario.onActivity(activity -> {
                TextView view = activity.findViewById(viewId);
                actualText.set(view == null ? "" : view.getText().toString());
            });
            if (expectedText.equals(actualText.get())) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("View " + viewId + " did not show \"" + expectedText + "\".");
    }

    private static void waitForVisibility(
            ActivityScenario<HomeActivity> scenario,
            int viewId,
            int expectedVisibility) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AtomicReference<Integer> actualVisibility = new AtomicReference<>(View.GONE);
            scenario.onActivity(activity -> {
                View view = activity.findViewById(viewId);
                actualVisibility.set(view == null ? View.GONE : view.getVisibility());
            });
            if (actualVisibility.get() == expectedVisibility) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("View " + viewId + " did not reach visibility "
                + expectedVisibility + ".");
    }

    private static void waitForProgress(
            ActivityScenario<HomeActivity> scenario,
            int viewId,
            int expectedProgress) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AtomicReference<Integer> actualProgress = new AtomicReference<>(-1);
            scenario.onActivity(activity -> {
                ProgressBar view = activity.findViewById(viewId);
                actualProgress.set(view == null ? -1 : view.getProgress());
            });
            if (actualProgress.get() == expectedProgress) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Progress view " + viewId + " did not reach "
                + expectedProgress + ".");
    }

    private static void waitForEnabled(
            ActivityScenario<HomeActivity> scenario,
            int viewId,
            boolean expectedEnabled) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AtomicReference<Boolean> actualEnabled = new AtomicReference<>(false);
            scenario.onActivity(activity -> {
                View view = activity.findViewById(viewId);
                actualEnabled.set(view != null && view.isEnabled());
            });
            if (actualEnabled.get() == expectedEnabled) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("View " + viewId + " enabled state did not become "
                + expectedEnabled + ".");
    }

    private static FilterOperationState operationState(
            FilterOperationState.Phase phase,
            int overallPercent,
            long parsedHostCount,
            boolean stopped) throws Exception {
        Constructor<FilterOperationState> constructor =
                FilterOperationState.class.getDeclaredConstructor(
                        FilterOperationState.Kind.class,
                        FilterOperationState.Phase.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        long.class,
                        String.class,
                        String.class,
                        long.class,
                        long.class,
                        boolean.class,
                        boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                FilterOperationState.Kind.SOURCE_UPDATE,
                phase,
                4,
                4,
                3,
                phase == FilterOperationState.Phase.COMPLETE ? 4 : 2,
                overallPercent,
                parsedHostCount,
                "Progress source",
                "Progress test",
                1L,
                2L,
                false,
                stopped);
    }

    @SuppressWarnings("unchecked")
    private static void publishOperationState(Context context, FilterOperationState state)
            throws Exception {
        SourceModel sourceModel =
                ((AdAwayApplication) context.getApplicationContext()).getSourceModel();
        Field field = SourceModel.class.getDeclaredField("filterOperationState");
        field.setAccessible(true);
        MutableLiveData<FilterOperationState> liveData =
                (MutableLiveData<FilterOperationState>) field.get(sourceModel);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> liveData.setValue(state));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
