package org.adaway.ui.discover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.model.source.SourceUpdateService;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.adaway.ui.hosts.FilterSetStore;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class DiscoverQuickPresetInstrumentedTest {
    private static final int TIMEOUT_SECONDS = 10;

    private Context context;
    private AppDatabase database;
    private HostsSourceDao hostsSourceDao;
    private WorkManager workManager;

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
    }

    @After
    public void tearDown() throws Exception {
        if (context != null) {
            resetImmediateUpdateWork();
            if (hostsSourceDao != null) {
                hostsSourceDao.deleteAll();
            }
            InstrumentedTestState.resetForPassiveRootUi(context, "tear down quick preset");
        }
    }

    @Test
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
}
