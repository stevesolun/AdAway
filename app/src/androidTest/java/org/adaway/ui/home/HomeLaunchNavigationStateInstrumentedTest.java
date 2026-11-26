/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.ui.home;

import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestListenableWorkerBuilder;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.RuleKind;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.error.HostError;
import org.adaway.model.source.FilterOperationState;
import org.adaway.model.source.SourceModel;
import org.adaway.model.source.SourceOperationCoordinator;
import org.adaway.model.source.SourceUpdateService;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.discover.DiscoverFragment;
import org.adaway.ui.hosts.HostsSourcesTabFragment;
import org.adaway.ui.more.MoreFragment;
import org.adaway.vpn.VpnStatus;
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
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class HomeLaunchNavigationStateInstrumentedTest {
    private static final long TIMEOUT_MS = 10_000L;

    private Context context;
    private AdAwayApplication application;
    private TestAdBlockModel testAdBlockModel;
    private SourceModel originalSourceModel;
    private int blockingFixtureSourceId;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        this.application = (AdAwayApplication) this.context.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up Home shell");
        PreferenceHelper.setAbBlockMethod(this.context, ROOT);
        this.testAdBlockModel = new TestAdBlockModel(this.context);
        injectAdBlockModel(this.application, this.testAdBlockModel);
        seedBlockingFixture();
        publishApplied(false);
    }

    @After
    public void tearDown() throws Exception {
        try {
            finishResumedActivities();
            if (this.blockingFixtureSourceId != 0) {
                AppDatabase database = AppDatabase.getInstance(this.context);
                database.hostsSourceDao().getById(this.blockingFixtureSourceId)
                        .ifPresent(database.hostsSourceDao()::delete);
                database.hostEntryDao().sync();
                this.blockingFixtureSourceId = 0;
            }
            if (this.application != null) {
                injectAdBlockModel(this.application, null);
                if (this.originalSourceModel != null) {
                    injectSourceModel(this.originalSourceModel);
                }
            }
        } finally {
            if (this.context != null) {
                InstrumentedTestState.resetForPassiveRootUi(this.context, "tear down Home shell");
            }
        }
    }

    private void seedBlockingFixture() {
        AppDatabase database = AppDatabase.getInstance(this.context);
        String host = "home-control-" + System.nanoTime() + ".invalid";
        HostsSource source = new HostsSource();
        source.setLabel("Home loaded-rules fixture");
        source.setUrl("https://example.invalid/" + host + ".txt");
        source.setEnabled(true);
        database.hostsSourceDao().insert(source);
        this.blockingFixtureSourceId = database.hostsSourceDao()
                .getByUrl(source.getUrl()).orElseThrow().getId();
        HostListItem item = new HostListItem();
        item.setHost(host);
        item.setType(ListType.BLOCKED);
        item.setKind(RuleKind.EXACT);
        item.setEnabled(true);
        item.setSourceId(this.blockingFixtureSourceId);
        item.setGeneration(database.hostEntryDao().getActiveGeneration());
        database.hostsListItemDao().insert(item);
        database.hostEntryDao().sync();
    }

    @Test(timeout = 90_000)
    public void passiveLaunchDefaultsToHomeAndRoutesEveryBottomNavDestination() {
        ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class);
        waitForDestination(scenario, R.id.nav_home, "home", HomeFragment.class);
        assertBottomNavigationContainsAllPrimaryDestinations(scenario);
        assertProtectionStatus(scenario, R.string.home_protection_status_inactive);

        selectDestination(scenario, R.id.nav_discover);
        waitForDestination(scenario, R.id.nav_discover, "discover", DiscoverFragment.class);

        selectDestination(scenario, R.id.nav_sources);
        waitForDestination(
                scenario,
                R.id.nav_sources,
                "sources",
                HostsSourcesTabFragment.class);

        selectDestination(scenario, R.id.nav_more);
        waitForDestination(scenario, R.id.nav_more, "more", MoreFragment.class);

        selectDestination(scenario, R.id.nav_home);
        waitForDestination(scenario, R.id.nav_home, "home", HomeFragment.class);
        assertProtectionStatus(scenario, R.string.home_protection_status_inactive);
    }

    @Test(timeout = 60_000)
    public void singleTopDiscoverIntentUpdatesSelectedTabAndStoredIntent() {
        ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class);
        waitForDestination(scenario, R.id.nav_home, "home", HomeFragment.class);

        scenario.onActivity(activity -> {
            Intent intent = new Intent(activity, HomeActivity.class)
                    .putExtra(HomeActivity.EXTRA_NAV_DISCOVER, true);
            activity.onNewIntent(intent);
        });

        waitForDestination(scenario, R.id.nav_discover, "discover", DiscoverFragment.class);
        scenario.onActivity(activity -> assertTrue(
                "HomeActivity must store the latest singleTop intent before routing.",
                activity.getIntent().getBooleanExtra(HomeActivity.EXTRA_NAV_DISCOVER, false)));
    }

    @Test(timeout = 60_000)
    public void protectionStatusRendersOffAndActiveModelStates() throws Exception {
        ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class);
        waitForDestination(scenario, R.id.nav_home, "home", HomeFragment.class);
        assertProtectionStatus(scenario, R.string.home_protection_status_inactive);

        publishApplied(true);
        assertProtectionStatus(scenario, R.string.home_protection_status_active);

        publishApplied(false);
        assertProtectionStatus(scenario, R.string.home_protection_status_inactive);
    }

    @Test(timeout = 60_000)
    public void returningHomeWithoutVpnConsentClearsStaleAppliedSwitch() throws Exception {
        org.junit.Assume.assumeNotNull(android.net.VpnService.prepare(this.context));
        PreferenceHelper.setAbBlockMethod(this.context, VPN);
        PreferenceHelper.setVpnServiceStatus(this.context, VpnStatus.STOPPED);
        injectAdBlockModel(this.application, null);
        AdBlockModel model = this.application.getAdBlockModel();
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            waitForDestination(scenario, R.id.nav_home, "home", HomeFragment.class);
            scenario.onActivity(activity -> {
                ((MutableLiveData<Boolean>) model.isApplied()).setValue(true);
                assertTrue(protectionControl(activity).isChecked());
            });
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> assertFalse(protectionControl(activity).isChecked()));
            assertProtectionStatus(scenario, R.string.home_protection_status_inactive);
        }
    }

    @Test(timeout = 60_000)
    public void inlineProtectionControlResumesPausesAndSurvivesRecreation() {
        ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class);
        waitForDestination(scenario, R.id.nav_home, "home", HomeFragment.class);
        scenario.onActivity(activity -> {
            CompoundButton control = protectionControl(activity);
            assertTrue("Protection control must be visible and enabled.",
                    control.isShown() && control.isEnabled());
            assertTrue("Protection control must meet the 48dp touch target.",
                    control.getHeight() >= 48 * activity.getResources().getDisplayMetrics().density);
            assertTrue("Protection control must not overlap the sources card.",
                    control.getBottom() <= activity.findViewById(R.id.sourcesCardView).getTop());
            assertTrue(control.performClick());
        });
        assertProtectionStatus(scenario, R.string.home_protection_status_active);
        scenario.recreate();
        waitForDestination(scenario, R.id.nav_home, "home", HomeFragment.class);
        scenario.onActivity(activity -> {
            CompoundButton control = protectionControl(activity);
            assertTrue("Recreation must preserve the actual protection state.", control.isChecked());
            assertTrue(control.performClick());
        });
        assertProtectionStatus(scenario, R.string.home_protection_status_inactive);
        scenario.onActivity(activity -> assertTrue(
                "Switch must reflect disabled protection.", !protectionControl(activity).isChecked()));
    }

    private static CompoundButton protectionControl(HomeActivity activity) {
        int id = activity.getResources().getIdentifier(
                "protectionSwitch", "id", activity.getPackageName());
        View view = activity.findViewById(id);
        assertNotNull("Home must offer an inline protection control.", view);
        assertTrue("Protection is a binary switch, not a floating action.",
                view instanceof CompoundButton);
        return (CompoundButton) view;
    }

    @Test(timeout = 60_000)
    public void repeatedTapsQueueOneOperationAndRecreatedViewUnlocks() throws Exception {
        this.testAdBlockModel.applyEntered = new CountDownLatch(1);
        this.testAdBlockModel.applyRelease = new CountDownLatch(1);
        ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class);
        waitForDestination(scenario, R.id.nav_home, "home", HomeFragment.class);
        try {
            scenario.onActivity(activity -> {
                protectionControl(activity).performClick();
                protectionControl(activity).performClick();
            });
            assertTrue("Apply must start.", this.testAdBlockModel.applyEntered.await(5, TimeUnit.SECONDS));
            scenario.recreate();
            waitForDestination(scenario, R.id.nav_home, "home", HomeFragment.class);
            scenario.onActivity(activity -> assertTrue("In-flight operation must disable the switch.",
                    !protectionControl(activity).isEnabled()));
        } finally {
            this.testAdBlockModel.applyRelease.countDown();
        }
        assertProtectionStatus(scenario, R.string.home_protection_status_active);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity(activity -> assertTrue("Current view must unlock after completion.",
                protectionControl(activity).isEnabled()));
        assertEquals("Same-frame taps must not enqueue duplicate operations.",
                1, this.testAdBlockModel.applyCount.get());
    }

    @Test(timeout = 60_000)
    public void authorizedEnableWaitsForExternalCompletionAcrossRecreation() throws Exception {
        assertAuthorizedEnableAfterTerminal(FilterOperationState.Phase.COMPLETE);
    }

    @Test(timeout = 60_000)
    public void authorizedEnableWaitsForStoppedImportAcrossRecreation() throws Exception {
        assertAuthorizedEnableAfterTerminal(FilterOperationState.Phase.STOPPED);
    }

    @Test(timeout = 60_000)
    public void authorizedEnableWaitsForFailedImportAcrossRecreation() throws Exception {
        assertAuthorizedEnableAfterTerminal(FilterOperationState.Phase.IDLE);
    }

    private void assertAuthorizedEnableAfterTerminal(FilterOperationState.Phase terminal)
            throws Exception {
        TestSourceModel sources = installTestSourceModel();
        this.testAdBlockModel.method = VPN;
        PreferenceHelper.setAbBlockMethod(this.context, VPN);
        SourceOperationCoordinator.Lease external = this.application.getSourceOperations().acquire();
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> {
                HomeViewModel model = homeModel(activity);
                model.setVpnConsentPending(true);
                // Exercise the validated-result boundary, without showing Android's consent UI.
                model.onVpnConsentResult(true);
                assertTrue(model.hasAuthorizedVpnEnableRequest());
                assertFalse(protectionControl(activity).isEnabled());
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertTrue(homeModel(activity).hasAuthorizedVpnEnableRequest());
                assertFalse(protectionControl(activity).isEnabled());
                sources.operationState.setValue(terminalState(terminal));
            });
            assertEquals("A terminal parser state must not consume the authorized request.",
                    0, this.testAdBlockModel.applyCount.get());
            external.close();
            // The fake accepts the enable but never establishes a platform tunnel.
            assertProtectionStatus(scenario, R.string.home_protection_status_vpn_disconnected);
            waitForProtectionControl(scenario, true);
            scenario.onActivity(activity ->
                    assertFalse(homeModel(activity).hasAuthorizedVpnEnableRequest()));
            assertEquals("Authorized enable must run once after acquisition succeeds.",
                    1, this.testAdBlockModel.applyCount.get());
        } finally {
            external.close();
        }
    }

    @Test(timeout = 60_000)
    public void cancelledConsentAndMethodChangeDoNotReplayEnable() throws Exception {
        installTestSourceModel();
        this.testAdBlockModel.method = VPN;
        PreferenceHelper.setAbBlockMethod(this.context, VPN);
        SourceOperationCoordinator.Lease external = this.application.getSourceOperations().acquire();
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> {
                homeModel(activity).setVpnConsentPending(true);
                homeModel(activity).onVpnConsentResult(false);
                assertFalse(homeModel(activity).hasAuthorizedVpnEnableRequest());
            });
            external.close();
            waitForProtectionControl(scenario, true);
            assertEquals(0, this.testAdBlockModel.applyCount.get());

            external = this.application.getSourceOperations().acquire();
            scenario.onActivity(activity -> {
                homeModel(activity).onVpnConsentResult(true);
                assertTrue(homeModel(activity).hasAuthorizedVpnEnableRequest());
                PreferenceHelper.setAbBlockMethod(this.context, ROOT);
                this.testAdBlockModel.method = ROOT;
            });
            scenario.recreate();
            external.close();
            waitForProtectionControl(scenario, true);
            scenario.onActivity(activity ->
                    assertFalse(homeModel(activity).hasAuthorizedVpnEnableRequest()));
            assertEquals(0, this.testAdBlockModel.applyCount.get());
        } finally {
            external.close();
        }
    }

    @Test(timeout = 60_000)
    public void bootstrapHandoffCoalescesBehindExternalOperationAndBlocksToggle() throws Exception {
        TestSourceModel sources = installTestSourceModel();
        this.testAdBlockModel.applyEntered = new CountDownLatch(1);
        this.testAdBlockModel.applyRelease = new CountDownLatch(1);
        SourceOperationCoordinator.Lease external = this.application.getSourceOperations().acquire();
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> {
                HomeViewModel model = homeModel(activity);
                model.beginInitialSources();
                activity.setProtectionEnabled(true);
                model.update();
                model.update();
                assertFalse(protectionControl(activity).isEnabled());
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                homeModel(activity).endInitialSources();
                assertFalse(protectionControl(activity).isEnabled());
            });
            assertEquals(0, sources.retrievalCount.get());
            assertEquals(0, this.testAdBlockModel.applyCount.get());
            external.close();
            assertTrue(this.testAdBlockModel.applyEntered.await(5, TimeUnit.SECONDS));
            assertEquals(1, sources.retrievalCount.get());
            scenario.onActivity(activity -> assertFalse(protectionControl(activity).isEnabled()));
            this.testAdBlockModel.applyRelease.countDown();
            assertProtectionStatus(scenario, R.string.home_protection_status_active);
            waitForProtectionControl(scenario, true);
            scenario.recreate();
            assertEquals("Recreation must not repeat the bootstrap retrieval.",
                    1, sources.retrievalCount.get());
            assertEquals(1, this.testAdBlockModel.applyCount.get());
        } finally {
            external.close();
            this.testAdBlockModel.applyRelease.countDown();
        }
    }

    @Test(timeout = 60_000)
    public void preparationWithoutDownloadUnlocksRecreatedControl() throws Exception {
        TestSourceModel sources = installTestSourceModel();
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> {
                homeModel(activity).beginInitialSources();
                assertFalse(protectionControl(activity).isEnabled());
                activity.setProtectionEnabled(true);
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertFalse(protectionControl(activity).isEnabled());
                homeModel(activity).endInitialSources();
                assertTrue(protectionControl(activity).isEnabled());
            });
            assertEquals(0, sources.retrievalCount.get());
            assertEquals(0, this.testAdBlockModel.applyCount.get());
        }
    }

    @Test(timeout = 60_000)
    public void externalWorkerOwnsLeaseThroughFinalApplyAndRecreatedHome() throws Exception {
        assertExternalWorkerFinalApply(false);
    }

    @Test(timeout = 60_000)
    public void externalWorkerApplyFailureReleasesRecreatedHome() throws Exception {
        assertExternalWorkerFinalApply(true);
    }

    private void assertExternalWorkerFinalApply(boolean failApply) throws Exception {
        installTestSourceModel();
        publishApplied(true);
        this.testAdBlockModel.applyEntered = new CountDownLatch(1);
        this.testAdBlockModel.applyRelease = new CountDownLatch(1);
        this.testAdBlockModel.failApply = failApply;
        ExecutorService workerExecutor = Executors.newSingleThreadExecutor();
        Future<ListenableWorker.Result> result = null;
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            SourceUpdateService.HostsSourcesImmediateUpdateWorker worker =
                    TestListenableWorkerBuilder.from(this.context,
                            SourceUpdateService.HostsSourcesImmediateUpdateWorker.class).build();
            result = workerExecutor.submit(worker::doWork);
            assertTrue("Worker must reach final apply.",
                    this.testAdBlockModel.applyEntered.await(5, TimeUnit.SECONDS));
            scenario.onActivity(activity -> {
                assertEquals(FilterOperationState.Phase.COMPLETE,
                        homeModel(activity).getFilterOperationState().getValue().phase);
                assertFalse(protectionControl(activity).isEnabled());
                activity.setProtectionEnabled(false);
            });
            scenario.recreate();
            scenario.onActivity(activity -> assertFalse(protectionControl(activity).isEnabled()));
            assertEquals("Off must not race an external final apply.",
                    0, this.testAdBlockModel.revertCount.get());
            this.testAdBlockModel.applyRelease.countDown();
            ListenableWorker.Result completed = result.get(5, TimeUnit.SECONDS);
            if (failApply) {
                assertEquals(ListenableWorker.Result.retry(), completed);
            } else {
                assertTrue(completed instanceof ListenableWorker.Result.Success);
            }
            waitForProtectionControl(scenario, true);
            scenario.onActivity(activity -> protectionControl(activity).performClick());
            assertProtectionStatus(scenario, R.string.home_protection_status_inactive);
            waitForProtectionControl(scenario, true);
            assertEquals(1, this.testAdBlockModel.revertCount.get());
            assertEquals(1, this.testAdBlockModel.applyCount.get());
        } finally {
            this.testAdBlockModel.applyRelease.countDown();
            try {
                if (result != null) result.get(5, TimeUnit.SECONDS);
            } finally {
                workerExecutor.shutdownNow();
            }
        }
    }

    private static HomeViewModel homeModel(HomeActivity activity) {
        return new ViewModelProvider(activity).get(HomeViewModel.class);
    }

    private static void waitForProtectionControl(ActivityScenario<HomeActivity> scenario,
            boolean enabled) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            boolean[] ready = new boolean[1];
            scenario.onActivity(activity -> ready[0] = protectionControl(activity).isEnabled() == enabled);
            if (ready[0]) return;
            SystemClock.sleep(20);
        }
        throw new AssertionError("Protection control enabled did not become " + enabled);
    }

    private TestSourceModel installTestSourceModel() throws Exception {
        if (this.originalSourceModel == null) {
            this.originalSourceModel = this.application.getSourceModel();
        }
        TestSourceModel model = new TestSourceModel(this.context);
        injectSourceModel(model);
        return model;
    }

    private void injectSourceModel(SourceModel model) throws Exception {
        Field field = AdAwayApplication.class.getDeclaredField("sourceModel");
        field.setAccessible(true);
        field.set(this.application, model);
    }

    private static FilterOperationState terminalState(FilterOperationState.Phase phase) {
        if (phase == FilterOperationState.Phase.IDLE) return FilterOperationState.idle();
        SourceModel.MultiPhaseProgressBuilder progress = new SourceModel.MultiPhaseProgressBuilder();
        progress.setTotalToCheck(1);
        progress.incrementChecked();
        progress.incrementTotalToDownload();
        progress.incrementDownloaded();
        progress.incrementTotalToParse();
        progress.incrementParsed();
        progress.setComplete(true);
        progress.setStopped(phase == FilterOperationState.Phase.STOPPED);
        try {
            Method factory = FilterOperationState.class.getDeclaredMethod("fromMultiPhaseProgress",
                    SourceModel.MultiPhaseProgress.class, long.class, long.class);
            factory.setAccessible(true);
            return (FilterOperationState) factory.invoke(null, progress.build(), 1L, 2L);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class TestSourceModel extends SourceModel {
        private final AtomicInteger retrievalCount = new AtomicInteger();
        private final MutableLiveData<FilterOperationState> operationState =
                new MutableLiveData<>(FilterOperationState.idle());

        private TestSourceModel(Context context) {
            super(context);
        }

        @Override
        public LiveData<FilterOperationState> getFilterOperationState() {
            return this.operationState;
        }

        @Override
        public boolean checkAndRetrieveHostsSources() {
            this.retrievalCount.incrementAndGet();
            this.operationState.postValue(terminalState(FilterOperationState.Phase.COMPLETE));
            return true;
        }
    }

    private static void waitForDestination(
            ActivityScenario<HomeActivity> scenario,
            int navItemId,
            @NonNull String tag,
            @NonNull Class<? extends Fragment> expectedFragmentClass) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            final boolean[] ready = new boolean[1];
            scenario.onActivity(activity -> {
                activity.getSupportFragmentManager().executePendingTransactions();
                BottomNavigationView nav = activity.findViewById(R.id.bottom_navigation);
                Fragment fragment = activity.getSupportFragmentManager().findFragmentByTag(tag);
                ready[0] = nav != null
                        && nav.getSelectedItemId() == navItemId
                        && expectedFragmentClass.isInstance(fragment)
                        && fragment.isAdded()
                        && fragment.getView() != null
                        && fragment.getView().isShown();
            });
            if (ready[0]) {
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError("Home destination was not ready: " + tag);
    }

    private static void selectDestination(
            ActivityScenario<HomeActivity> scenario,
            int navItemId) {
        scenario.onActivity(activity -> {
            BottomNavigationView nav = activity.findViewById(R.id.bottom_navigation);
            assertNotNull(nav);
            assertTrue("Bottom navigation item click failed for " + navItemId,
                    nav.findViewById(navItemId).performClick());
        });
    }

    private static void assertBottomNavigationContainsAllPrimaryDestinations(
            ActivityScenario<HomeActivity> scenario) {
        scenario.onActivity(activity -> {
            BottomNavigationView nav = activity.findViewById(R.id.bottom_navigation);
            assertNotNull(nav);
            assertNotNull(nav.getMenu().findItem(R.id.nav_home));
            assertNotNull(nav.getMenu().findItem(R.id.nav_discover));
            assertNotNull(nav.getMenu().findItem(R.id.nav_sources));
            assertNotNull(nav.getMenu().findItem(R.id.nav_more));

            View container = activity.findViewById(R.id.nav_fragment_container);
            assertNotNull(container);
            assertTrue("Home content container must be shown.", container.isShown());
            assertTrue("Bottom navigation must be shown.", nav.isShown());
            assertTrue("Home content must stay above the bottom navigation.",
                    container.getBottom() <= nav.getTop());
        });
    }

    private void assertProtectionStatus(
            ActivityScenario<HomeActivity> scenario,
            int expectedStringResId) {
        String expected = this.context.getString(expectedStringResId);
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            final String[] actual = new String[1];
            final boolean[] shown = new boolean[1];
            scenario.onActivity(activity -> {
                TextView view = activity.findViewById(R.id.protectionStatusTextView);
                actual[0] = view == null ? "" : view.getText().toString();
                shown[0] = view != null && view.isShown();
            });
            if (shown[0] && expected.equals(actual[0])) {
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError("Protection status did not show \"" + expected + "\".");
    }

    private void publishApplied(boolean applied) {
        MutableLiveData<Boolean> liveData =
                (MutableLiveData<Boolean>) this.testAdBlockModel.isApplied();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> liveData.setValue(applied));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void injectAdBlockModel(
            @NonNull AdAwayApplication application,
            AdBlockModel adBlockModel) throws Exception {
        Field field = AdAwayApplication.class.getDeclaredField("adBlockModel");
        field.setAccessible(true);
        field.set(application, adBlockModel);
    }

    private static void finishResumedActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                activity.finish();
            }
        });
    }

    private static final class TestAdBlockModel extends AdBlockModel {
        private final AtomicInteger applyCount = new AtomicInteger();
        private final AtomicInteger revertCount = new AtomicInteger();
        private volatile AdBlockMethod method = ROOT;
        private volatile boolean failApply;
        private volatile CountDownLatch applyEntered;
        private volatile CountDownLatch applyRelease;

        private TestAdBlockModel(Context context) {
            super(context);
        }

        @Override
        public AdBlockMethod getMethod() {
            return this.method;
        }

        @Override
        public void apply() throws HostErrorException {
            this.applyCount.incrementAndGet();
            if (this.applyEntered != null) {
                this.applyEntered.countDown();
                try {
                    if (!this.applyRelease.await(15, TimeUnit.SECONDS)) {
                        throw new AssertionError("Test did not release apply.");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
            }
            if (this.failApply) throw new HostErrorException(HostError.COPY_FAIL);
            this.applied.postValue(true);
        }

        @Override
        public void revert() throws HostErrorException {
            this.revertCount.incrementAndGet();
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
