package org.adaway.ui.home;

import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;

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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class HomeUpdateControlsInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;

    private Context context;
    private SourceModel sourceModel;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up Home update controls");
        this.sourceModel =
                ((AdAwayApplication) this.context.getApplicationContext()).getSourceModel();
        resetSourceUpdateState(this.sourceModel);
    }

    @After
    public void tearDown() throws Exception {
        if (this.sourceModel != null) {
            resetSourceUpdateState(this.sourceModel);
        }
        if (this.context != null) {
            InstrumentedTestState.resetForPassiveRootUi(
                    this.context,
                    "tear down Home update controls");
        }
    }

    @Test
    public void pauseResumeButtonControlsActiveSourceUpdate() throws Exception {
        primeActiveSourceUpdate(this.sourceModel);

        try (ActivityScenario<HomeActivity> scenario =
                     ActivityScenario.launch(HomeActivity.class)) {
            waitForVisibility(scenario, R.id.multiPhaseProgressContainer, View.VISIBLE);
            waitForShownAndEnabled(scenario, R.id.pauseResumeButton);
            waitForContentDescription(
                    scenario,
                    R.id.pauseResumeButton,
                    this.context.getString(R.string.pause_update));
            waitForPausedState(false);

            clickView(scenario, R.id.pauseResumeButton);
            waitForPausedState(true);
            waitForContentDescription(
                    scenario,
                    R.id.pauseResumeButton,
                    this.context.getString(R.string.resume_update));

            clickView(scenario, R.id.pauseResumeButton);
            waitForPausedState(false);
            waitForContentDescription(
                    scenario,
                    R.id.pauseResumeButton,
                    this.context.getString(R.string.pause_update));
        }
    }

    @Test
    public void stopButtonStopsActiveSourceUpdateAndDisablesControls() throws Exception {
        primeActiveSourceUpdate(this.sourceModel);

        try (ActivityScenario<HomeActivity> scenario =
                     ActivityScenario.launch(HomeActivity.class)) {
            waitForVisibility(scenario, R.id.multiPhaseProgressContainer, View.VISIBLE);
            waitForShownAndEnabled(scenario, R.id.stopButton);

            clickView(scenario, R.id.stopButton);

            waitForStoppedState();
            waitForEnabled(scenario, R.id.pauseResumeButton, false);
            waitForEnabled(scenario, R.id.stopButton, false);
        }
    }

    private void waitForPausedState(boolean expectedPaused) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            FilterOperationState state = this.sourceModel.getFilterOperationState().getValue();
            if (state != null
                    && state.kind == FilterOperationState.Kind.SOURCE_UPDATE
                    && state.paused == expectedPaused) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Source update paused state did not become " + expectedPaused);
    }

    private void waitForStoppedState() {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            FilterOperationState state = this.sourceModel.getFilterOperationState().getValue();
            if (state != null
                    && state.kind == FilterOperationState.Kind.SOURCE_UPDATE
                    && state.stopped
                    && state.phase == FilterOperationState.Phase.STOPPED) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Source update stopped state was not published.");
    }

    private static void clickView(ActivityScenario<HomeActivity> scenario, int viewId) {
        scenario.onActivity(activity -> {
            View view = activity.findViewById(viewId);
            assertNotNull(view);
            view.performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
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

    private static void waitForShownAndEnabled(
            ActivityScenario<HomeActivity> scenario,
            int viewId) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AtomicReference<Boolean> shownAndEnabled = new AtomicReference<>(false);
            scenario.onActivity(activity -> {
                View view = activity.findViewById(viewId);
                shownAndEnabled.set(view != null && view.isShown() && view.isEnabled());
            });
            if (shownAndEnabled.get()) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("View " + viewId + " did not become shown and enabled.");
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

    private static void waitForContentDescription(
            ActivityScenario<HomeActivity> scenario,
            int viewId,
            String expectedDescription) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AtomicReference<String> actualDescription = new AtomicReference<>("");
            scenario.onActivity(activity -> {
                View view = activity.findViewById(viewId);
                CharSequence description = view == null ? null : view.getContentDescription();
                actualDescription.set(description == null ? "" : description.toString());
            });
            if (expectedDescription.equals(actualDescription.get())) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("View " + viewId + " did not show content description \""
                + expectedDescription + "\".");
    }

    private static void primeActiveSourceUpdate(SourceModel sourceModel) throws Exception {
        SourceModel.MultiPhaseProgressBuilder builder = getProgressBuilder(sourceModel);
        builder.reset();
        builder.setTotalToCheck(4);
        builder.incrementChecked();
        builder.incrementChecked();
        builder.setCurrentLabel("Progress controls");
        setUpdateInProgress(sourceModel, true);
        postMultiPhaseProgress(sourceModel, builder.build(), true);
    }

    private static void resetSourceUpdateState(SourceModel sourceModel) throws Exception {
        SourceModel.MultiPhaseProgressBuilder builder = getProgressBuilder(sourceModel);
        builder.reset();
        setUpdateInProgress(sourceModel, false);
        postMultiPhaseProgress(sourceModel, SourceModel.MultiPhaseProgress.idle(), true);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static SourceModel.MultiPhaseProgressBuilder getProgressBuilder(SourceModel sourceModel)
            throws Exception {
        Field field = SourceModel.class.getDeclaredField("progressBuilder");
        field.setAccessible(true);
        return (SourceModel.MultiPhaseProgressBuilder) field.get(sourceModel);
    }

    private static void setUpdateInProgress(SourceModel sourceModel, boolean inProgress)
            throws Exception {
        Field field = SourceModel.class.getDeclaredField("updateInProgress");
        field.setAccessible(true);
        AtomicBoolean updateInProgress = (AtomicBoolean) field.get(sourceModel);
        updateInProgress.set(inProgress);
    }

    private static void postMultiPhaseProgress(
            SourceModel sourceModel,
            SourceModel.MultiPhaseProgress progress,
            boolean force) throws Exception {
        Method method = SourceModel.class.getDeclaredMethod(
                "postMultiPhaseProgress",
                SourceModel.MultiPhaseProgress.class,
                boolean.class);
        method.setAccessible(true);
        method.invoke(sourceModel, progress, force);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
