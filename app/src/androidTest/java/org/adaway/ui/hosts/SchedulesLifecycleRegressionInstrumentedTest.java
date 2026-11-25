package org.adaway.ui.hosts;

import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.drainDiskAndMain;
import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.hasText;
import static org.adaway.ui.hosts.ScheduleRegressionTestSupport.waitFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostsSource;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class SchedulesLifecycleRegressionInstrumentedTest {
    private static final String URL = "https://schedules-lifecycle.test/hosts.txt";
    private static final String LABEL = "Lifecycle schedule source";
    private static final String UNCHANGED = "Pending lifecycle result";
    private Context mContext;
    private TextView mQueuedSummary;
    private final CountDownLatch mReleaseDisk = new CountDownLatch(1);

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(mContext, "schedule lifecycle setup");
        AppDatabase.getInstance(mContext).getOpenHelper().getWritableDatabase();
        drainDiskAndMain();
        HostsSource source = new HostsSource();
        source.setId(93201);
        source.setLabel(LABEL);
        source.setUrl(URL);
        source.setEnabled(false);
        AppDatabase.getInstance(mContext).hostsSourceDao().insert(source);
    }

    @After
    public void tearDown() throws Exception {
        mReleaseDisk.countDown();
        drainDiskAndMain();
        if (mContext != null) {
            AppDatabase.getInstance(mContext).hostsSourceDao().getByUrl(URL).ifPresent(
                    source -> AppDatabase.getInstance(mContext).hostsSourceDao().delete(source));
            InstrumentedTestState.resetForPassiveRootUi(mContext, "schedule lifecycle teardown");
        }
    }

    @Test(timeout = 60_000)
    public void pickerAndSummaryCompletionAfterFinishDoNotTouchOldActivity() throws Exception {
        holdDisk();
        try (ActivityScenario<SchedulesActivity> scenario =
                ActivityScenario.launch(SchedulesActivity.class)) {
            SchedulesActivity old = queuePicker(scenario);
            scenario.onActivity(SchedulesActivity::finish);
            waitFor("activity destruction", old::isDestroyed);
            mReleaseDisk.countDown();
            drainDiskAndMain();
            assertOldSummaryUnchanged(old);
        }
    }

    @Test(timeout = 60_000)
    public void pickerAndSummaryCompletionAfterRecreationDoNotTouchOldActivity() throws Exception {
        holdDisk();
        try (ActivityScenario<SchedulesActivity> scenario =
                ActivityScenario.launch(SchedulesActivity.class)) {
            SchedulesActivity old = queuePicker(scenario);
            scenario.recreate();
            assertTrue(old.isDestroyed());
            mReleaseDisk.countDown();
            drainDiskAndMain();
            assertOldSummaryUnchanged(old);
            assertNoPicker();
            scenario.onActivity(activity -> {
                TextView summary = activity.findViewById(R.id.sourceSchedulesSummary);
                assertTrue("Replacement activity gets its own summary", summary.length() > 0);
                activity.findViewById(R.id.editSourceSchedulesButton).performClick();
            });
            drainDiskAndMain();
            waitFor("new activity picker", () -> hasText(LABEL));
            ScheduleRegressionTestSupport.clickText(mContext.getString(R.string.button_cancel));
            waitFor("picker dismissal", () -> !hasText(LABEL));
        }
    }

    @Test(timeout = 60_000)
    public void pickerQueuedBeforePauseDoesNotReopenAfterResume() throws Exception {
        holdDisk();
        try (ActivityScenario<SchedulesActivity> scenario =
                ActivityScenario.launch(SchedulesActivity.class)) {
            queuePicker(scenario);
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            mReleaseDisk.countDown();
            drainDiskAndMain();
            assertNoPicker();
        }
    }

    private void holdDisk() throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(() -> {
            entered.countDown();
            try {
                if (!mReleaseDisk.await(30, TimeUnit.SECONDS)) {
                    throw new AssertionError("Disk hold was not released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        });
        assertTrue(entered.await(10, TimeUnit.SECONDS));
    }

    private SchedulesActivity queuePicker(ActivityScenario<SchedulesActivity> scenario) {
        AtomicReference<SchedulesActivity> owner = new AtomicReference<>();
        scenario.onActivity(activity -> {
            owner.set(activity);
            mQueuedSummary = activity.findViewById(R.id.sourceSchedulesSummary);
            mQueuedSummary.setText(UNCHANGED);
            assertTrue(activity.findViewById(R.id.editSourceSchedulesButton).performClick());
        });
        return owner.get();
    }

    private void assertOldSummaryUnchanged(SchedulesActivity old) {
        assertTrue(old.isDestroyed());
        // Recreation can transfer the Window: old.findViewById may resolve the new screen.
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> assertEquals(UNCHANGED,
                mQueuedSummary.getText().toString()));
    }

    private void assertNoPicker() {
        // Result queue is drained before checking; a stale show cannot be pending behind this.
        waitFor("schedule screen", () ->
                hasText(mContext.getString(R.string.menu_manage_schedules)));
        assertTrue("Old picker must not be visible", !hasText(LABEL));
    }
}
