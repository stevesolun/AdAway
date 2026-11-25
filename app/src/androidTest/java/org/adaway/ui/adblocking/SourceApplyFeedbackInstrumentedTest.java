package org.adaway.ui.adblocking;

import android.app.Application;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.R;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.source.SourceApplyState;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.hosts.HostsSourcesViewModel;
import org.adaway.ui.lists.ListsActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class SourceApplyFeedbackInstrumentedTest {
    private Application application;

    @Before
    public void setUp() {
        application = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(application, "set up apply prompt timing");
    }

    @After
    public void tearDown() {
        InstrumentedTestState.resetForPassiveRootUi(application, "tear down apply prompt timing");
    }

    @Test(timeout = 30_000)
    public void pendingPromptReturnsAfterSameFrameLoadingAndCompletion() {
        assertPromptReturns(true);
    }

    @Test(timeout = 30_000)
    public void pendingPromptReturnsAfterSameFrameSelectionReversal() {
        assertPromptReturns(false);
    }

    private void assertPromptReturns(boolean completedUpdate) {
        AtomicReference<ApplyConfigurationSnackbar> feedback = new AtomicReference<>();
        AtomicReference<FeedbackModel> model = new AtomicReference<>();
        AtomicReference<View> decor = new AtomicReference<>();
        ActivityScenario<ListsActivity> scenario = ActivityScenario.launch(ListsActivity.class);
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> scenario.onActivity(activity -> {
                decor.set(activity.getWindow().getDecorView());
                FeedbackModel state = new FeedbackModel(application);
                state.selection(true);
                state.selection(false);
                model.set(state);
                ApplyConfigurationSnackbar bar = new ApplyConfigurationSnackbar(
                        activity.findViewById(R.id.coordinator), state);
                feedback.set(bar);
                bar.refreshSourceState();
            }));
            awaitPrompt(decor.get());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                if (completedUpdate) {
                    model.get().status.setValue(HostsSourcesViewModel.ApplyStatus.RUNNING);
                    feedback.get().refreshSourceState();
                    model.get().status.setValue(HostsSourcesViewModel.ApplyStatus.SUCCEEDED);
                } else {
                    model.get().selection(true);
                    feedback.get().refreshSourceState();
                    model.get().selection(false);
                }
                assertTrue("Presentation must not acknowledge pending selections",
                        model.get().state.isPending());
                feedback.get().refreshSourceState();
            });
            // Let queued dismiss callbacks and animations settle before checking visibility.
            SystemClock.sleep(1_000);
            awaitPrompt(decor.get());
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    () -> scenario.onActivity(ListsActivity::finish));
            scenario.close();
        }
    }

    private void awaitPrompt(View decor) {
        String text = application.getString(R.string.notification_configuration_changed);
        long deadline = SystemClock.uptimeMillis() + 5_000;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicBoolean visible = new AtomicBoolean();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    () -> visible.set(hasText(decor, text)));
            if (visible.get()) return;
            SystemClock.sleep(25);
        }
        throw new AssertionError("Pending Apply prompt disappeared after a same-frame transition");
    }

    private static boolean hasText(View view, String text) {
        if (view instanceof TextView && view.isShown()
                && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasText(group.getChildAt(i), text)) return true;
            }
        }
        return false;
    }

    private static final class FeedbackModel extends HostsSourcesViewModel {
        final SourceApplyState state = new SourceApplyState();
        final MutableLiveData<ApplyStatus> status = new MutableLiveData<>(ApplyStatus.IDLE);

        FeedbackModel(Application application) { super(application); }

        void selection(boolean enabled) {
            HostsSource source = new HostsSource();
            source.setId(2026);
            source.setUrl("https://example.invalid/feedback-test.txt");
            source.setEnabled(enabled);
            state.updateSources(Collections.singletonList(source));
        }

        @Override public SourceApplyState getApplyState() { return state; }
        @Override public LiveData<ApplyStatus> getApplyStatus() { return status; }
    }
}
