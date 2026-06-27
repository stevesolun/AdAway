package org.adaway.ui.log;

import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.testing.InstrumentedTestState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class LogRootExpectationInstrumentedTest {
    private static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(context, "set up root log expectation");
        PreferenceHelper.setAbBlockMethod(context, ROOT);
    }

    @After
    public void tearDown() {
        finishResumedActivities();
        if (context != null) {
            InstrumentedTestState.resetForPassiveRootUi(context, "tear down root log expectation");
        }
    }

    @Test(timeout = 60_000)
    public void rootModeExplainsDnsRequestLoggingUnavailable() {
        String expected = context.getString(R.string.log_root_recording_unavailable);

        try (ActivityScenario<LogActivity> scenario = ActivityScenario.launch(LogActivity.class)) {
            waitForEmptyText(scenario, expected);
        }
    }

    private static void waitForEmptyText(
            @NonNull ActivityScenario<LogActivity> scenario,
            @NonNull String expectedText) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        AtomicReference<String> actual = new AtomicReference<>("");
        AtomicReference<Boolean> shown = new AtomicReference<>(false);
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                TextView empty = activity.findViewById(R.id.emptyTextView);
                shown.set(empty != null && empty.isShown());
                actual.set(empty != null && empty.getText() != null
                        ? empty.getText().toString()
                        : "");
            });
            if (Boolean.TRUE.equals(shown.get()) && expectedText.equals(actual.get())) {
                return;
            }
            SystemClock.sleep(100);
        }
        assertTrue("Expected root log empty text to be visible.", Boolean.TRUE.equals(shown.get()));
        assertEquals(expectedText, actual.get());
    }

    private static void finishResumedActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                activity.finish();
            }
        });
    }
}
