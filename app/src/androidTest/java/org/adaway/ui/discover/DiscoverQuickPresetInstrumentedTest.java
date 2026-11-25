package org.adaway.ui.discover;

import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

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
import org.adaway.testing.UiEvidence;
import org.adaway.ui.home.HomeActivity;
import org.adaway.ui.hosts.FilterSetStore;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
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
        clearProfiles();
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
            clearProfiles();
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

    @Test(timeout = 60_000)
    public void balancedUpdatesKnownOutdatedSourceAndPreservesUnrelatedSource() throws Exception {
        List<FilterListCatalog.CatalogEntry> entries = FilterListCatalog.getBalancedPreset();
        seedDownloaded(entries, true);
        String extra = addUnrelatedSource();
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> activity.navigateTo(R.id.nav_discover));
            tapPreset(scenario, R.id.chipDiscoverBalanced);
            waitForMessage(scenario, context.getResources().getQuantityString(
                    R.plurals.filter_preset_existing_updating, 1, 1));
            UiEvidence.capture(context, "discover-balanced-known-newer-update");
            scenario.onActivity(activity -> activity.navigateTo(R.id.nav_home));
        }
        drainDiskIo();
        assertPreset(FilterSetStore.PROFILE_BALANCED, entries);
        assertTrue(hostsSourceDao.getByUrl(extra).get().isEnabled());
        waitForSuccessfulImmediateWork();
        assertEquals(1, sourceModel.updateCount.get());
        assertEquals(1, adBlockModel.applyCount.get());
        System.out.println("Balanced preset recording-model evidence: activeProfile="
                + FilterSetStore.getActiveProfile(context) + ", unrelatedSourceEnabled="
                + hostsSourceDao.getByUrl(extra).get().isEnabled() + ", updateCalls="
                + sourceModel.updateCount.get() + ", applyCalls=" + adBlockModel.applyCount.get());
    }

    @Test(timeout = 60_000)
    public void balancedAddsMissingReenablesDisabledAndKeepsCustomSource() throws Exception {
        List<FilterListCatalog.CatalogEntry> entries = FilterListCatalog.getBalancedPreset();
        HostsSource disabled = entries.get(0).toHostsSource();
        disabled.setEnabled(false);
        hostsSourceDao.insert(disabled);
        String extra = addUnrelatedSource();
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> activity.navigateTo(R.id.nav_discover));
            tapPreset(scenario, R.id.chipDiscoverBalanced);
            int added = entries.size() - 1;
            waitForMessage(scenario, context.getResources().getQuantityString(
                    R.plurals.filter_preset_added_updating, added, added));
            assertPreset(FilterSetStore.PROFILE_BALANCED, entries);
            assertTrue(hostsSourceDao.getByUrl(extra).get().isEnabled());
            waitForSuccessfulImmediateWork();
        }
    }

    @Test(timeout = 60_000)
    public void currentAggressiveThenSafePreservesExtrasWithoutQueuingAnotherUpdate()
            throws Exception {
        List<FilterListCatalog.CatalogEntry> aggressive = FilterListCatalog.getAggressivePreset();
        seedDownloaded(aggressive, false);
        String extra = addUnrelatedSource();
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> activity.navigateTo(R.id.nav_discover));
            tapPreset(scenario, R.id.chipDiscoverAggressive);
            waitForMessage(scenario, context.getString(R.string.filter_preset_already_subscribed));
            assertPreset(FilterSetStore.PROFILE_AGGRESSIVE, aggressive);
            tapPreset(scenario, R.id.chipDiscoverAggressive);
            tapPreset(scenario, R.id.chipDiscoverSafe);
            drainDiskIo();
            assertPreset(FilterSetStore.PROFILE_SAFE, FilterListCatalog.getDefaults());
        }
        assertEquals(aggressive.size() + 1, hostsSourceDao.getEnabled().size());
        assertTrue(hostsSourceDao.getByUrl(extra).get().isEnabled());
        assertTrue(getImmediateUpdateWork().isEmpty());
        assertEquals(0, sourceModel.updateCount.get());
    }

    private void seedDownloaded(List<FilterListCatalog.CatalogEntry> entries, boolean outdated) {
        ZonedDateTime local = ZonedDateTime.parse("2026-01-10T12:00:00Z");
        for (FilterListCatalog.CatalogEntry entry : entries) {
            HostsSource source = entry.toHostsSource();
            source.setEnabled(true);
            source.setSize(20);
            source.setLocalModificationDate(local);
            source.setOnlineModificationDate(outdated ? local.plusDays(1) : local);
            outdated = false;
            hostsSourceDao.insert(source);
        }
    }

    private void clearProfiles() {
        assertTrue(context.getSharedPreferences("filter_sets", Context.MODE_PRIVATE)
                .edit().clear().commit());
    }

    private void waitForMessage(ActivityScenario<HomeActivity> scenario, String expected) {
        long deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
        while (SystemClock.elapsedRealtime() < deadline) {
            boolean[] found = {false};
            scenario.onActivity(activity -> {
                TextView message = activity.findViewById(com.google.android.material.R.id.snackbar_text);
                found[0] = message != null && message.isShown() && expected.contentEquals(message.getText());
            });
            if (found[0]) return;
            SystemClock.sleep(30);
        }
        throw new AssertionError("Did not see preset feedback: " + expected);
    }

    private String addUnrelatedSource() {
        HostsSource source = new HostsSource();
        source.setLabel("Unrelated hosts");
        source.setUrl("https://lists.example.org/extra");
        source.setEnabled(true);
        hostsSourceDao.insert(source);
        return source.getUrl();
    }

    private void assertPreset(String profile, List<FilterListCatalog.CatalogEntry> entries) {
        assertEquals(profile, FilterSetStore.getActiveProfile(context));
        Set<String> urls = new HashSet<>();
        for (FilterListCatalog.CatalogEntry entry : entries) {
            urls.add(entry.url);
            assertTrue(hostsSourceDao.getByUrl(entry.url).get().isEnabled());
        }
        assertEquals(urls, FilterSetStore.getSetUrls(context, profile));
    }

    private void tapPreset(ActivityScenario<HomeActivity> scenario, int id) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Rect target = new Rect();
        scenario.onActivity(activity -> {
            View chip = activity.findViewById(id);
            assertNotNull(chip);
            assertTrue("Preset chip must be on screen", chip.getGlobalVisibleRect(target));
        });
        long down = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                    target.centerX(), target.centerY(), 0);
            try {
                InstrumentationRegistry.getInstrumentation().sendPointerSync(event);
            } finally {
                event.recycle();
            }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
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
