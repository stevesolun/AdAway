package org.adaway.ui.hosts;

import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.android.material.appbar.MaterialToolbar;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostEntry;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.RuleKind;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.SourceModel;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class SourcesUpdateAllInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;
    private static final int TEST_SOURCE_ID = 1811;
    private static final String TEST_SOURCE_URL =
            "content://org.adaway.test.hosts/sources-update-all.txt";
    private static final String TEST_HOST = "sources-update-all.example";

    private Context context;
    private AdAwayApplication application;
    private SourceModel originalSourceModel;
    private RecordingSourceModel recordingSourceModel;
    private RecordingAdBlockModel recordingAdBlockModel;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        this.application = (AdAwayApplication) this.context.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up Sources update all");
        AppDatabase.getInstance(this.context).getOpenHelper().getWritableDatabase();
        waitForDiskIoIdle();
        resetFixture(this.context);
        seedEnabledSourceWithActiveItem(this.context);
        PreferenceHelper.setAbBlockMethod(this.context, ROOT);
        this.originalSourceModel = this.application.getSourceModel();
        this.recordingSourceModel = new RecordingSourceModel(this.context);
        injectSourceModel(this.application, this.recordingSourceModel);
        this.recordingAdBlockModel = new RecordingAdBlockModel(this.context);
        injectAdBlockModel(this.application, this.recordingAdBlockModel);
    }

    @After
    public void tearDown() throws Exception {
        if (this.recordingSourceModel != null) {
            this.recordingSourceModel.releaseUpdate();
        }
        finishResumedActivities();
        if (this.application != null) {
            injectAdBlockModel(this.application, null);
            injectSourceModel(this.application, this.originalSourceModel);
        }
        if (this.context != null) {
            resetFixture(this.context);
            InstrumentedTestState.resetForPassiveRootUi(this.context, "tear down Sources update all");
        }
    }

    @Test(timeout = 60_000)
    public void updateAllToolbarActionSyncsSourcesThenAppliesProtection() {
        ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class);
        navigateToSources(scenario);
        HomeActivity activity = waitForHomeActivity();
        waitForToolbarAction(activity, R.id.action_hosts_update_all);

        clickToolbarAction(activity, R.id.action_hosts_update_all);

        waitForSourceUpdateStarted(1);
        waitForText(activity, this.context.getString(R.string.sources_apply_installing));

        this.recordingSourceModel.releaseUpdate();

        waitForApplyCount(1);
        assertEquals(BLOCKED,
                AppDatabase.getInstance(this.context)
                        .hostEntryDao()
                        .resolveEntry(TEST_HOST)
                        .getType());
    }

    private static void navigateToSources(ActivityScenario<HomeActivity> scenario) {
        scenario.onActivity(activity -> activity.navigateTo(R.id.nav_sources));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static HomeActivity waitForHomeActivity() {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<Activity> resumed = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (activity instanceof HomeActivity) {
                        resumed.set(activity);
                        return;
                    }
                }
            });
            Activity activity = resumed.get();
            if (activity instanceof HomeActivity) {
                return (HomeActivity) activity;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out waiting for HomeActivity.");
    }

    private static void waitForToolbarAction(
            Activity activity,
            int menuItemId) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<Boolean> found = new AtomicReference<>(false);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                MaterialToolbar toolbar = activity.findViewById(R.id.hosts_sources_toolbar);
                found.set(toolbar != null && toolbar.getMenu().findItem(menuItemId) != null);
            });
            if (found.get()) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Sources toolbar action did not become available: " + menuItemId);
    }

    private static void clickToolbarAction(
            Activity activity,
            int menuItemId) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MaterialToolbar toolbar = activity.findViewById(R.id.hosts_sources_toolbar);
            assertNotNull("Expected Sources toolbar.", toolbar);
            assertTrue("Expected Sources toolbar action to be handled.",
                    toolbar.getMenu().performIdentifierAction(menuItemId, 0));
        });
    }

    private void waitForSourceUpdateStarted(int expectedCount) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (this.recordingSourceModel.updateAllCount.get() >= expectedCount) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Sources update-all action did not reach SourceModel.");
    }

    private void waitForText(HomeActivity activity, String expectedText) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<Boolean> found = new AtomicReference<>(false);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    found.set(hasVisibleText(activity.getWindow().getDecorView(), expectedText)));
            if (found.get()) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Text did not appear: " + expectedText);
    }

    private void waitForApplyCount(int expectedCount) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            String failure = this.recordingAdBlockModel.failure.get();
            if (failure != null) {
                throw new AssertionError(failure);
            }
            if (this.recordingAdBlockModel.applyCount.get() >= expectedCount) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Sources update-all action did not reach AdBlockModel.apply().");
    }

    private static void finishResumedActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                activity.finish();
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static boolean hasVisibleText(View view, String expectedText) {
        if (view instanceof TextView
                && view.isShown()
                && expectedText.contentEquals(((TextView) view).getText())) {
            return true;
        }
        if (!(view instanceof ViewGroup)) {
            return false;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (hasVisibleText(group.getChildAt(i), expectedText)) {
                return true;
            }
        }
        return false;
    }

    private static void seedEnabledSourceWithActiveItem(Context context) {
        runDatabaseWork(context, database -> {
            HostsSource source = new HostsSource();
            source.setId(TEST_SOURCE_ID);
            source.setLabel("Sources update-all proof source");
            source.setUrl(TEST_SOURCE_URL);
            source.setEnabled(true);
            source.setLocalModificationDate(ZonedDateTime.now());
            source.setOnlineModificationDate(source.getLocalModificationDate());
            source.setSize(1);
            source.setActiveRuleCount(1);
            source.setBlockedCount(1);
            source.setBlockedExactCount(1);
            database.hostsSourceDao().insert(source);

            HostListItem item = new HostListItem();
            item.setHost(TEST_HOST);
            item.setType(BLOCKED);
            item.setKind(RuleKind.EXACT);
            item.setEnabled(true);
            item.setSourceId(TEST_SOURCE_ID);
            item.setGeneration(0);
            database.hostsListItemDao().insert(item);
        });
    }

    private static void resetFixture(Context context) {
        runDatabaseWork(context, database -> {
            database.getOpenHelper().getWritableDatabase().execSQL("DELETE FROM hosts_lists");
            database.hostEntryDao().clear();
            database.hostEntryDao().clearRootExport();
            database.hostsSourceDao().deleteAll();
            database.getOpenHelper().getWritableDatabase().execSQL(
                    "INSERT OR REPLACE INTO hosts_meta (id, active_generation) VALUES (0, 0)");
            database.getOpenHelper().getWritableDatabase().execSQL(
                    "INSERT OR REPLACE INTO hosts_stats "
                            + "(id, blocked_count, blocked_exact_count, allowed_count, "
                            + "redirected_count, active_rule_count) VALUES (0, 0, 0, 0, 0, 0)");
        });
    }

    private static void runDatabaseWork(Context context, DatabaseWork work) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> work.run(AppDatabase.getInstance(context)));
        try {
            future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Database fixture work failed.", exception);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void waitForDiskIoIdle() {
        CountDownLatch latch = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(latch::countDown);
        try {
            if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("Timed out waiting for app disk executor to become idle.");
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for app disk executor.",
                    interruptedException);
        }
    }

    private static void injectAdBlockModel(
            AdAwayApplication application,
            AdBlockModel adBlockModel) throws Exception {
        Field field = AdAwayApplication.class.getDeclaredField("adBlockModel");
        field.setAccessible(true);
        field.set(application, adBlockModel);
    }

    private static void injectSourceModel(
            AdAwayApplication application,
            SourceModel sourceModel) throws Exception {
        Field field = AdAwayApplication.class.getDeclaredField("sourceModel");
        field.setAccessible(true);
        field.set(application, sourceModel);
    }

    private interface DatabaseWork {
        void run(AppDatabase database);
    }

    private static final class RecordingSourceModel extends SourceModel {
        private final Context context;
        private final AtomicInteger updateAllCount = new AtomicInteger();
        private final CountDownLatch allowCompletion = new CountDownLatch(1);

        private RecordingSourceModel(Context context) {
            super(context);
            this.context = context;
        }

        @Override
        public boolean checkAndRetrieveHostsSources() throws HostErrorException {
            this.updateAllCount.incrementAndGet();
            try {
                if (!this.allowCompletion.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    throw new AssertionError("Timed out waiting to complete test update.");
                }
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting to complete test update.",
                        interruptedException);
            }
            AppDatabase.getInstance(this.context).hostEntryDao().sync();
            return true;
        }

        private void releaseUpdate() {
            this.allowCompletion.countDown();
        }
    }

    private static final class RecordingAdBlockModel extends AdBlockModel {
        private final AtomicInteger applyCount = new AtomicInteger();
        private final AtomicReference<String> failure = new AtomicReference<>();

        private RecordingAdBlockModel(Context context) {
            super(context);
        }

        @Override
        public AdBlockMethod getMethod() {
            return ROOT;
        }

        @Override
        public void apply() {
            HostEntry entry = AppDatabase.getInstance(this.context)
                    .hostEntryDao()
                    .resolveEntry(TEST_HOST);
            if (entry.getType() != BLOCKED) {
                this.failure.compareAndSet(null,
                        "Sources update-all applied before runtime truth was synced.");
            }
            this.applyCount.incrementAndGet();
            this.applied.postValue(true);
        }

        @Override
        public void revert() {
            this.applied.postValue(false);
        }

        @Override
        public boolean isRecordingLogs() {
            return false;
        }

        @Override
        public void setRecordingLogs(boolean recording) {
            // No-op for the test model.
        }

        @Override
        public List<String> getLogs() {
            return Collections.emptyList();
        }

        @Override
        public void clearLogs() {
            // No-op for the test model.
        }
    }
}
