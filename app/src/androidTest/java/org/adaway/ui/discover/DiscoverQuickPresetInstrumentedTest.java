package org.adaway.ui.discover;

import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.model.source.SourceModel;
import org.adaway.model.source.SourceOperationCoordinator;
import org.adaway.model.source.SourceUpdateService;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.adaway.ui.hosts.FilterSetStore;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class DiscoverQuickPresetInstrumentedTest {
    private static final int TIMEOUT_SECONDS = 10;

    private Context context;
    private AppDatabase database;
    private HostsSourceDao hostsSourceDao;
    private WorkManager workManager;
    private AdAwayApplication application;
    private SourceModel originalSourceModel;
    private AdBlockModel originalAdBlockModel;
    private RecordingSourceModel sourceModel;
    private RecordingAdBlockModel adBlockModel;
    private boolean modelsInjected;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(context, "set up quick preset");
        database = AppDatabase.getInstance(context);
        drainDiskIo();
        hostsSourceDao = database.hostsSourceDao();
        hostsSourceDao.deleteAll();
        workManager = WorkManager.getInstance(context);
        resetImmediateUpdateWork();
        application = (AdAwayApplication) context.getApplicationContext();
        Field sourceField = modelField("sourceModel");
        Field adBlockField = modelField("adBlockModel");
        originalSourceModel = (SourceModel) sourceField.get(application);
        originalAdBlockModel = (AdBlockModel) adBlockField.get(application);
        sourceModel = new RecordingSourceModel(context);
        adBlockModel = new RecordingAdBlockModel(context);
        adBlockField.set(application, adBlockModel);
        sourceField.set(application, sourceModel);
        modelsInjected = true;
    }

    @After
    public void tearDown() throws Exception {
        if (context != null) {
            drainDiskIo();
            if (modelsInjected) {
                // Do not restore real models while an update/apply still owns the operation.
                try (SourceOperationCoordinator.Lease ignored = awaitIdleOperation()) {
                    resetImmediateUpdateWork();
                    modelField("sourceModel").set(application, originalSourceModel);
                    modelField("adBlockModel").set(application, originalAdBlockModel);
                    modelsInjected = false;
                }
            } else if (workManager != null) {
                resetImmediateUpdateWork();
            }
            if (hostsSourceDao != null) {
                hostsSourceDao.deleteAll();
            }
            InstrumentedTestState.resetForPassiveRootUi(context, "tear down quick preset");
        }
    }

    @Test(timeout = 60_000)
    public void safePresetChipAddsPresetSourcesPersistsProfileAndQueuesImmediateUpdate()
            throws Exception {
        List<FilterListCatalog.CatalogEntry> safePreset = FilterListCatalog.getDefaults();
        assertFalse(safePreset.isEmpty());

        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> activity.navigateTo(R.id.nav_discover));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                View chip = activity.findViewById(R.id.chipDiscoverSafe);
                assertNotNull(chip);
                chip.performClick();
            });
        }

        drainDiskIo();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        Set<String> expectedUrls = new HashSet<>();
        for (FilterListCatalog.CatalogEntry entry : safePreset) {
            expectedUrls.add(entry.url);
            HostsSource source = hostsSourceDao.getByUrl(entry.url).orElse(null);
            assertNotNull("Missing preset source " + entry.url, source);
            assertTrue("Preset source must be enabled " + entry.url, source.isEnabled());
        }

        assertEquals(FilterSetStore.PROFILE_SAFE, FilterSetStore.getActiveProfile(context));
        assertEquals(expectedUrls, FilterSetStore.getSetUrls(context, FilterSetStore.PROFILE_SAFE));
        assertFalse(getImmediateUpdateWork().isEmpty());
        waitForSuccessfulImmediateWork();
        assertSame(sourceModel, application.getSourceModel());
        assertSame(adBlockModel, application.getAdBlockModel());
        assertEquals(1, sourceModel.updateCount.get());
        assertEquals(1, adBlockModel.applyCount.get());
    }

    private void waitForSuccessfulImmediateWork() throws Exception {
        long deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
        while (SystemClock.elapsedRealtime() < deadline) {
            List<WorkInfo> work = getImmediateUpdateWork();
            if (!work.isEmpty() && work.get(0).getState().isFinished()) {
                assertEquals(1, work.size());
                assertEquals(WorkInfo.State.SUCCEEDED, work.get(0).getState());
                return;
            }
            SystemClock.sleep(50);
        }
        throw new AssertionError("Immediate preset work did not finish with the inert models.");
    }

    private SourceOperationCoordinator.Lease awaitIdleOperation() {
        long deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
        while (SystemClock.elapsedRealtime() < deadline) {
            SourceOperationCoordinator.Lease lease = application.getSourceOperations().tryAcquire();
            if (lease != null) {
                return lease;
            }
            SystemClock.sleep(50);
        }
        throw new AssertionError("Refusing to restore real models while preset work is active.");
    }

    private static Field modelField(String name) throws Exception {
        Field field = AdAwayApplication.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private void resetImmediateUpdateWork() throws Exception {
        workManager.cancelUniqueWork(SourceUpdateService.WORK_NAME_NOW)
                .getResult()
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        workManager.pruneWork()
                .getResult()
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private List<WorkInfo> getImmediateUpdateWork() throws Exception {
        return workManager.getWorkInfosForUniqueWork(SourceUpdateService.WORK_NAME_NOW)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static void drainDiskIo() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(latch::countDown);
        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    private static final class RecordingSourceModel extends SourceModel {
        final AtomicInteger updateCount = new AtomicInteger();

        RecordingSourceModel(Context context) {
            super(context);
        }

        @Override
        public boolean checkAndRetrieveHostsSources() {
            updateCount.incrementAndGet();
            return true;
        }
    }

    private static final class RecordingAdBlockModel extends AdBlockModel {
        final AtomicInteger applyCount = new AtomicInteger();

        RecordingAdBlockModel(Context context) {
            super(context);
        }

        @Override public AdBlockMethod getMethod() { return ROOT; }
        @Override public void apply() { applyCount.incrementAndGet(); }
        @Override public void revert() { throw new AssertionError("Unexpected preset revert."); }
        @Override public boolean isRecordingLogs() { return false; }
        @Override public void setRecordingLogs(boolean recording) { }
        @Override public List<String> getLogs() { return Collections.emptyList(); }
        @Override public void clearLogs() { }
    }
}
