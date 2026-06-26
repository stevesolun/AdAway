package org.adaway.ui.home;

import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.os.SystemClock;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostEntry;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.source.SourceModel;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class HomeUpdateActionsInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;
    private static final int TEST_SOURCE_ID = 1311;
    private static final String TEST_SOURCE_URL = "content://org.adaway.test.hosts/success.txt";
    private static final String FIRST_HOST = "fresh-success.example";
    private static final String SECOND_HOST = "second-success.example";

    private Context context;
    private AdAwayApplication application;
    private RecordingAdBlockModel recordingAdBlockModel;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        this.application = (AdAwayApplication) this.context.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up Home update actions");
        AppDatabase.getInstance(this.context).getOpenHelper().getWritableDatabase();
        waitForDiskIoIdle();
        resetFixture(this.context);
        seedFileSource(this.context);
        PreferenceHelper.setAbBlockMethod(this.context, ROOT);
        this.recordingAdBlockModel = new RecordingAdBlockModel(this.context);
        injectAdBlockModel(this.application, this.recordingAdBlockModel);
        resetSourceUpdateState(this.application.getSourceModel());
    }

    @After
    public void tearDown() throws Exception {
        if (this.application != null) {
            injectAdBlockModel(this.application, null);
            resetSourceUpdateState(this.application.getSourceModel());
        }
        if (this.context != null) {
            resetFixture(this.context);
            InstrumentedTestState.resetForPassiveRootUi(
                    this.context,
                    "tear down Home update actions");
        }
    }

    @Test
    public void checkForUpdateButtonRunsLocalSourcePipelineBeforeApply() {
        clickHomeActionAndAssertApplied(R.id.checkForUpdateImageView);
    }

    @Test
    public void updateApplyButtonRunsLocalSourcePipelineBeforeApply() {
        clickHomeActionAndAssertApplied(R.id.updateImageView);
    }

    private void clickHomeActionAndAssertApplied(int actionViewId) {
        try (ActivityScenario<HomeActivity> scenario =
                     ActivityScenario.launch(HomeActivity.class)) {
            waitForShownAndEnabled(scenario, actionViewId);

            clickView(scenario, actionViewId);

            waitForApplyCount(1);
            assertRuntimePipelineState();
        }
    }

    private void assertRuntimePipelineState() {
        AppDatabase database = AppDatabase.getInstance(this.context);
        int activeGeneration = database.hostEntryDao().getActiveGeneration();
        HostsSource source = database.hostsSourceDao().getById(TEST_SOURCE_ID).orElseThrow();

        assertTrue("The update must activate a new source generation.",
                activeGeneration > 0);
        assertNull("A successful file-source update must clear the last download error.",
                source.getLastDownloadError());
        assertNotNull("A successful update must record the local source freshness.",
                source.getLocalModificationDate());
        assertNotNull("A successful update must record the online source freshness.",
                source.getOnlineModificationDate());
        assertEquals(2, source.getSize());
        assertEquals(2, source.getActiveRuleCount());
        assertEquals(2, source.getBlockedCount());
        assertEquals(2, source.getBlockedExactCount());
        assertEquals(0, source.getAllowedCount());
        assertEquals(0, source.getRedirectedCount());
        assertEquals(2, database.hostsListItemDao()
                .countSourceHostsForGeneration(TEST_SOURCE_ID, activeGeneration));
        assertEquals(2, countSourceRows(database, TEST_SOURCE_ID));
        assertEquals(2, database.hostEntryDao().countActiveRuntimeRuleRows());
        assertEquals(2, database.hostEntryDao().getBlockedEntryCountNow());
        assertEquals(2, database.hostEntryDao().getBlockedExactEntryCountNow());
        assertEquals(BLOCKED, database.hostEntryDao().resolveEntry(FIRST_HOST).getType());
        assertEquals(BLOCKED, database.hostEntryDao().resolveEntry(SECOND_HOST).getType());
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
        throw new AssertionError("Home action did not reach AdBlockModel.apply().");
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
        throw new AssertionError("Home action " + viewId + " did not become shown and enabled.");
    }

    private static void clickView(ActivityScenario<HomeActivity> scenario, int viewId) {
        scenario.onActivity(activity -> {
            View view = activity.findViewById(viewId);
            assertNotNull(view);
            view.performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void seedFileSource(Context context) {
        runDatabaseWork(context, database -> {
            HostsSource source = new HostsSource();
            source.setId(TEST_SOURCE_ID);
            source.setLabel("Home action source");
            source.setUrl(TEST_SOURCE_URL);
            source.setEnabled(true);
            database.hostsSourceDao().insert(source);
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

    private static int countSourceRows(AppDatabase database, int sourceId) {
        try (Cursor cursor = database.getOpenHelper().getWritableDatabase().query(
                "SELECT COUNT(*) FROM hosts_lists WHERE source_id = ?",
                new Object[]{sourceId})) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
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

    private interface DatabaseWork {
        void run(AppDatabase database);
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
                    .resolveEntry(FIRST_HOST);
            if (entry.getType() != BLOCKED) {
                this.failure.compareAndSet(null,
                        "Home action applied before runtime truth was updated for " + FIRST_HOST);
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
