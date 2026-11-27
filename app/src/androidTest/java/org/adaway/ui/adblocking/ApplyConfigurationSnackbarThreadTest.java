package org.adaway.ui.adblocking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostError;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.SourceModel;
import org.adaway.model.source.SourceOperationCoordinator;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.testing.UiEvidence;
import org.adaway.ui.adblocking.ApplyConfigurationSnackbar.ApplyOutcome;
import org.adaway.ui.lists.ListsActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class ApplyConfigurationSnackbarThreadTest {
    private AdAwayApplication application;
    private SourceModel originalSource;
    private AdBlockModel originalAdBlock;

    @Before
    public void setUp() {
        application = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(application, "set up apply feedback thread");
        originalSource = application.getSourceModel();
        originalAdBlock = application.getAdBlockModel();
    }

    @After
    public void tearDown() throws Exception {
        setModel("sourceModel", originalSource);
        setModel("adBlockModel", originalAdBlock);
        InstrumentedTestState.resetForPassiveRootUi(application, "tear down apply feedback thread");
    }

    @Test
    public void successfulApplyBuildsCompletionFeedbackOnMainThread() throws Exception {
        assertCompletionThread(0);
    }

    @Test
    public void hostFailureBuildsErrorFeedbackOnMainThread() throws Exception {
        assertCompletionThread(1);
    }

    @Test
    public void unexpectedDatabaseFailureEndsLoadingOnMainThread() throws Exception {
        assertCompletionThread(2);
    }

    private void assertCompletionThread(int failure) throws Exception {
        AtomicReference<Thread> worker = new AtomicReference<>();
        setModel("sourceModel", new SourceModel(application) {
            @Override
            public boolean checkAndRetrieveHostsSources() throws HostErrorException {
                worker.set(Thread.currentThread());
                if (failure == 1) throw new HostErrorException(HostError.NO_CONNECTION);
                if (failure == 2) throw new IllegalStateException("fixture database failure");
                return true;
            }
        });
        setModel("adBlockModel", new NoOpAdBlockModel(application));
        AtomicReference<RecordingSnackbar> snackbar = new AtomicReference<>();
        try (ActivityScenario<ListsActivity> scenario = ActivityScenario.launch(ListsActivity.class)) {
            scenario.onActivity(activity -> {
                RecordingSnackbar feedback = new RecordingSnackbar(
                        activity.findViewById(R.id.coordinator));
                snackbar.set(feedback);
                feedback.apply();
            });
            assertTrue("Apply must complete", snackbar.get().completed.await(10, TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertNotSame(Looper.getMainLooper().getThread(), worker.get());
            assertSame(Looper.getMainLooper().getThread(), snackbar.get().completionThread);
            assertEquals(failure == 0 ? ApplyOutcome.APPLIED : ApplyOutcome.FAILED,
                    snackbar.get().outcome);
            assertNull("Real Snackbar creation must not throw", snackbar.get().uiFailure);
        }
    }

    @Test
    public void acceptedVpnWithoutTunnelShowsRequestNotProtection() throws Exception {
        assertManualFeedback(AdBlockMethod.VPN, false, false, false,
                R.string.notification_configuration_vpn_requested);
    }

    @Test
    public void acceptedVpnWithTunnelShowsApplied() throws Exception {
        assertManualFeedback(AdBlockMethod.VPN, true, false, false,
                R.string.notification_configuration_applied);
    }

    @Test
    public void editDuringAcceptedRequestStillOffersApply() throws Exception {
        assertManualFeedback(AdBlockMethod.VPN, false, false, true,
                R.string.notification_configuration_changed);
    }

    @Test
    public void rejectedVpnWithNewerEditShowsFailureAndRetry() throws Exception {
        assertManualFeedback(AdBlockMethod.VPN, true, true, true,
                R.string.notification_configuration_failed);
    }

    @Test
    public void listsWithoutProtectionMethodDoesNotReportApplied() throws Exception {
        assertManualFeedback(AdBlockMethod.UNDEFINED, false, false, false,
                R.string.leak_status_mode_unconfigured);
    }

    @Test
    public void detachedAnchorDoesNotReceiveVpnRequestFeedback() throws Exception {
        assertManualFeedback(AdBlockMethod.VPN, false, false, false, 0);
    }

    @Test
    public void busyAttemptOffersRetryWithoutReleasingOtherOperation() throws Exception {
        try (ActivityScenario<ListsActivity> scenario = ActivityScenario.launch(ListsActivity.class);
             SourceOperationCoordinator.Lease ignored = application.getSourceOperations().acquire()) {
            AtomicReference<ViewGroup> parent = new AtomicReference<>();
            AtomicReference<RecordingSnackbar> snackbar = new AtomicReference<>();
            scenario.onActivity(activity -> {
                parent.set(activity.findViewById(R.id.coordinator));
                RecordingSnackbar feedback = new RecordingSnackbar(parent.get(), false);
                snackbar.set(feedback);
                feedback.apply();
            });
            awaitFeedback(parent.get(), R.string.error_update_in_progress_details, true, null);
            assertEquals(ApplyOutcome.BUSY, snackbar.get().outcome);
            assertNull(snackbar.get().uiFailure);
            assertTrue(application.getSourceOperations().isBusy());
        }
    }

    private void assertManualFeedback(AdBlockMethod method, boolean established, boolean reject,
            boolean editDuringApply, int expectedText) throws Exception {
        PreferenceHelper.setAbBlockMethod(application, method);
        AtomicInteger syncs = new AtomicInteger();
        AtomicInteger requests = new AtomicInteger();
        setModel("sourceModel", new SourceModel(application) {
            @Override public void syncHostEntries() { syncs.incrementAndGet(); }
        });
        setModel("adBlockModel", new NoOpAdBlockModel(application) {
            @Override public AdBlockMethod getMethod() { return method; }
            @Override public void apply() throws HostErrorException {
                if (requests.incrementAndGet() == 1 && reject) {
                    throw new HostErrorException(HostError.ENABLE_VPN_FAIL);
                }
                applied.postValue(true); // Acknowledged, as in VpnModel; not evidence of a tunnel.
            }
        });
        AtomicReference<RecordingSnackbar> snackbar = new AtomicReference<>();
        AtomicReference<View> anchor = new AtomicReference<>();
        try (ActivityScenario<ListsActivity> scenario = ActivityScenario.launch(ListsActivity.class)) {
            scenario.onActivity(activity -> {
                ViewGroup coordinator = activity.findViewById(R.id.coordinator);
                anchor.set(new View(activity));
                coordinator.addView(anchor.get(), 1, 1);
                RecordingSnackbar feedback = new RecordingSnackbar(anchor.get(), false);
                feedback.established = established;
                snackbar.set(feedback);
                feedback.notifyUpdateAvailable();
            });
            ViewGroup parent = (ViewGroup) anchor.get().getParent();
            TextView apply = awaitFeedback(parent, R.string.notification_configuration_changed, true, null);
            assertEquals("Showing a reminder must not apply anything", 0, requests.get());
            assertEquals(0, syncs.get());
            RecordingSnackbar feedback = snackbar.get();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                assertTrue(apply.performClick());
                // Same main-thread turn: completion cannot overtake the new edit or detach.
                if (editDuringApply) feedback.notifyUpdateAvailable();
                if (expectedText == 0) parent.removeView(anchor.get());
            });
            assertTrue("Apply must complete", feedback.completed.await(10, TimeUnit.SECONDS));
            assertNull("No result should be constructed against a detached anchor", feedback.uiFailure);
            assertEquals(1, syncs.get());
            assertEquals(method == AdBlockMethod.UNDEFINED ? 0 : 1, requests.get());
            assertEquals(method == AdBlockMethod.VPN && !reject ? 1 : 0, feedback.tunnelChecks);
            assertFalse("Completion releases the operation", application.getSourceOperations().isBusy());
            if (expectedText != 0) {
                TextView action = awaitFeedback(parent, expectedText, editDuringApply, apply);
                if (expectedText == R.string.notification_configuration_vpn_requested) {
                    UiEvidence.capture(application, "vpn-start-requested-protection-unconfirmed");
                }
                if (reject) {
                    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> assertTrue(action.performClick()));
                    awaitFeedback(parent, R.string.notification_configuration_applied, false, action);
                    assertEquals(2, requests.get());
                    assertEquals(2, syncs.get());
                }
            }
        }
    }

    private static TextView awaitFeedback(ViewGroup parent, int message, boolean expectApplyAction,
            TextView previousAction) {
        AtomicReference<TextView> button = new AtomicReference<>();
        long deadline = SystemClock.uptimeMillis() + 5_000;
        do {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                TextView text = parent.findViewById(com.google.android.material.R.id.snackbar_text);
                if (text == null || !text.isShown()
                        || !parent.getResources().getString(message).contentEquals(text.getText())) return;
                TextView action = parent.findViewById(com.google.android.material.R.id.snackbar_action);
                // Do not match the outgoing Apply while its dismissal animation is still running.
                if (action == null || action == previousAction) return;
                if (!expectApplyAction || (action.isShown() && action.isClickable()
                        && parent.getResources().getString(R.string.notification_configuration_changed_action)
                        .contentEquals(action.getText()))) button.set(action);
            });
            if (button.get() != null) return button.get();
            SystemClock.sleep(20);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Missing feedback: " + parent.getResources().getString(message));
    }

    private void setModel(String fieldName, Object value) throws Exception {
        Field field = AdAwayApplication.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(application, value);
    }

    private static final class RecordingSnackbar extends ApplyConfigurationSnackbar {
        final CountDownLatch completed = new CountDownLatch(1);
        Thread completionThread;
        ApplyOutcome outcome;
        boolean established;
        int tunnelChecks;
        Throwable uiFailure;

        RecordingSnackbar(View view) {
            this(view, true);
        }

        RecordingSnackbar(View view, boolean syncSources) {
            super(view, syncSources, false);
        }

        @Override
        boolean isTunnelEstablished(Context context) {
            tunnelChecks++;
            return established;
        }

        @Override
        void endLoading(ApplyOutcome result) {
            completionThread = Thread.currentThread();
            outcome = result;
            try {
                super.endLoading(result);
            } catch (Throwable failure) {
                uiFailure = failure;
            } finally {
                completed.countDown();
            }
        }
    }

    private static class NoOpAdBlockModel extends AdBlockModel {
        NoOpAdBlockModel(Context context) { super(context); }
        @Override public AdBlockMethod getMethod() { return AdBlockMethod.ROOT; }
        @Override public void apply() throws HostErrorException { }
        @Override public void revert() { }
        @Override public boolean isRecordingLogs() { return false; }
        @Override public void setRecordingLogs(boolean recording) { }
        @Override public List<String> getLogs() { return Collections.emptyList(); }
        @Override public void clearLogs() { }
    }
}
