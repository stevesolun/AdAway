package org.adaway.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Data;
import androidx.work.WorkInfo;

import org.adaway.R;
import org.adaway.ui.hosts.FilterListsSubscribeAllWorker;
import org.adaway.ui.hosts.FilterSetUpdateWorker;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

/** Deterministic observer-delivery replay, without starting real workers or network imports. */
@RunWith(AndroidJUnit4.class)
public class HomeWorkProgressRegressionInstrumentedTest {
    private HomeFragment fragment;
    private TextView text;
    private ProgressBar bar;

    @Before
    public void setUp() {
        onMain(() -> {
            Context context = new ContextThemeWrapper(
                    ApplicationProvider.getApplicationContext(),
                    R.style.Theme_AdAway_NoActionBar_Content);
            this.fragment = new HomeFragment();
            View view = this.fragment.onCreateView(LayoutInflater.from(context), null, null);
            this.text = view.findViewById(R.id.filterListsSubscribeProgressTextView);
            this.bar = view.findViewById(R.id.filterListsSubscribeProgressBar);
        });
    }

    @Test
    public void scheduledCompletionClearsProgressAfterSubscribeObserverHasSettled() {
        onMain(() -> {
            this.fragment.updateFilterListsSubscribeAllProgress(Collections.emptyList());
            UUID id = UUID.randomUUID();
            for (WorkInfo.State terminal : Arrays.asList(WorkInfo.State.SUCCEEDED,
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED)) {
                scheduled(work(id, WorkInfo.State.RUNNING, false, 2, 4));
                assertProgress("Scheduled update: 2/4 (50%)", false);
                scheduled(work(id, terminal, false, 4, 4));
                assertHidden();
            }
        });
    }

    @Test
    public void eitherCompletionKeepsTheOtherTaskVisibleInBothDeliveryOrders() {
        onMain(() -> {
            UUID subscribe = UUID.randomUUID();
            UUID schedule = UUID.randomUUID();
            for (boolean scheduledFirst : new boolean[] {true, false}) {
                this.fragment.updateFilterListsSubscribeAllProgress(null);
                this.fragment.updateScheduledUpdateProgress(null);
                if (scheduledFirst) {
                    scheduled(work(schedule, WorkInfo.State.RUNNING, false, 1, 4));
                }
                subscribed(work(subscribe, WorkInfo.State.RUNNING, true, 1, 2));
                scheduled(work(schedule, WorkInfo.State.RUNNING, false, 2, 4));
                assertProgress("FilterLists subscribing: 1/2 (50%)", true);

                subscribed(work(subscribe, WorkInfo.State.SUCCEEDED, true, 2, 2));
                assertProgress("Scheduled update: 2/4 (50%)", false);
                subscribed(work(subscribe, WorkInfo.State.RUNNING, true, 1, 2));
                scheduled(work(schedule, WorkInfo.State.CANCELLED, false, 2, 4));
                assertProgress("FilterLists subscribing: 1/2 (50%)", true);
                subscribed(work(subscribe, WorkInfo.State.FAILED, true, 1, 2));
                assertHidden();
            }
        });
    }

    @Test
    public void queuedPeriodicWorkIsIdleButQueuedSubscriptionIsPreparing() {
        onMain(() -> {
            UUID id = UUID.randomUUID();
            scheduled(work(id, WorkInfo.State.RUNNING, false, 1, 2));
            scheduled(work(id, WorkInfo.State.ENQUEUED, false, 0, 0));
            assertHidden();
            subscribed(work(UUID.randomUUID(), WorkInfo.State.ENQUEUED, true, 0, 0));
            assertProgress("FilterLists subscribing: Preparing\u2026", true);
            assertTrue(this.bar.isIndeterminate());
            this.fragment.updateScheduledUpdateProgress(Collections.emptyList());
            assertProgress("FilterLists subscribing: Preparing\u2026", true);
            this.fragment.updateFilterListsSubscribeAllProgress(null);
            assertHidden();
        });
    }

    @Test
    public void runningWorkWinsOverHistoricalAndQueuedRows() {
        onMain(() -> {
            WorkInfo running = work(UUID.randomUUID(), WorkInfo.State.RUNNING, true, 2, 4);
            this.fragment.updateFilterListsSubscribeAllProgress(Arrays.asList(
                    work(UUID.randomUUID(), WorkInfo.State.SUCCEEDED, true, 4, 4),
                    work(UUID.randomUUID(), WorkInfo.State.ENQUEUED, true, 0, 0), running));
            assertProgress("FilterLists subscribing: 2/4 (50%)", true);
            this.fragment.updateFilterListsSubscribeAllProgress(Collections.emptyList());
            this.fragment.updateScheduledUpdateProgress(Arrays.asList(
                    work(UUID.randomUUID(), WorkInfo.State.SUCCEEDED, false, 4, 4),
                    work(UUID.randomUUID(), WorkInfo.State.RUNNING, false, 2, 4)));
            assertProgress("Scheduled update: 2/4 (50%)", false);
        });
    }

    @Test
    public void regressionsAreIgnoredButNewJobsAndNewTotalsResetProgress() {
        onMain(() -> {
            for (boolean subscribing : new boolean[] {true, false}) {
                this.fragment.updateFilterListsSubscribeAllProgress(null);
                this.fragment.updateScheduledUpdateProgress(null);
                UUID id = UUID.randomUUID();
                deliver(work(id, WorkInfo.State.RUNNING, subscribing, 3, 4), subscribing);
                deliver(work(id, WorkInfo.State.RUNNING, subscribing, 1, 4), subscribing);
                String prefix = subscribing ? "FilterLists subscribing: " : "Scheduled update: ";
                assertProgress(prefix + "3/4 (75%)", subscribing);
                deliver(work(id, WorkInfo.State.RUNNING, subscribing, 1, 5), subscribing);
                assertProgress(prefix + "1/5 (20%)", subscribing);
                deliver(work(UUID.randomUUID(), WorkInfo.State.RUNNING, subscribing, 0, 5),
                        subscribing);
                assertProgress(prefix + "0/5 (0%)", subscribing);
            }
        });
    }

    @Test
    public void retryAttemptsAndUpdatedWorkResetProgressForTheSameId() {
        onMain(() -> {
            UUID id = UUID.randomUUID();
            scheduled(work(id, WorkInfo.State.RUNNING, false, 3, 4));
            Data restarted = work(id, WorkInfo.State.RUNNING, false, 0, 4).getProgress();
            scheduled(new WorkInfo(id, WorkInfo.State.RUNNING, Collections.emptySet(),
                    Data.EMPTY, restarted, 1, 0));
            assertProgress("Scheduled update: 0/4 (0%)", false);
            scheduled(work(id, WorkInfo.State.RUNNING, false, 3, 4));
            scheduled(new WorkInfo(id, WorkInfo.State.RUNNING, Collections.emptySet(),
                    Data.EMPTY, restarted, 0, 1));
            assertProgress("Scheduled update: 0/4 (0%)", false);
        });
    }

    private void subscribed(WorkInfo info) {
        this.fragment.updateFilterListsSubscribeAllProgress(Collections.singletonList(info));
    }

    private void scheduled(WorkInfo info) {
        this.fragment.updateScheduledUpdateProgress(Collections.singletonList(info));
    }

    private void deliver(WorkInfo info, boolean subscribing) {
        if (subscribing) subscribed(info);
        else scheduled(info);
    }

    private void assertProgress(String expected, boolean clickable) {
        assertEquals(View.VISIBLE, this.text.getVisibility());
        assertEquals(View.VISIBLE, this.bar.getVisibility());
        assertEquals(expected, this.text.getText().toString());
        assertEquals(clickable, this.text.hasOnClickListeners());
        assertEquals(clickable, this.text.isClickable());
    }

    private void assertHidden() {
        assertEquals(View.GONE, this.text.getVisibility());
        assertEquals(View.GONE, this.bar.getVisibility());
        assertEquals("", this.text.getText().toString());
        assertFalse(this.text.hasOnClickListeners());
    }

    private static WorkInfo work(UUID id, WorkInfo.State state, boolean subscribing,
            int done, int total) {
        Data progress = new Data.Builder()
                .putInt(subscribing ? FilterListsSubscribeAllWorker.PROGRESS_DONE
                        : FilterSetUpdateWorker.PROGRESS_DONE, done)
                .putInt(subscribing ? FilterListsSubscribeAllWorker.PROGRESS_TOTAL
                        : FilterSetUpdateWorker.PROGRESS_TOTAL, total)
                .build();
        return new WorkInfo(id, state, Collections.emptySet(), Data.EMPTY, progress, 0);
    }

    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }
}
