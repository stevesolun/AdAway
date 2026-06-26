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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.discover.DiscoverFragment;
import org.adaway.ui.hosts.HostsSourcesTabFragment;
import org.adaway.ui.more.MoreFragment;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class HomeLaunchNavigationStateInstrumentedTest {
    private static final long TIMEOUT_MS = 10_000L;

    private Context context;
    private AdAwayApplication application;
    private TestAdBlockModel testAdBlockModel;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        this.application = (AdAwayApplication) this.context.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up Home shell");
        PreferenceHelper.setAbBlockMethod(this.context, ROOT);
        this.testAdBlockModel = new TestAdBlockModel(this.context);
        injectAdBlockModel(this.application, this.testAdBlockModel);
        publishApplied(false);
    }

    @After
    public void tearDown() throws Exception {
        try {
            finishResumedActivities();
            if (this.application != null) {
                injectAdBlockModel(this.application, null);
            }
        } finally {
            if (this.context != null) {
                InstrumentedTestState.resetForPassiveRootUi(this.context, "tear down Home shell");
            }
        }
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
        private TestAdBlockModel(Context context) {
            super(context);
        }

        @Override
        public AdBlockMethod getMethod() {
            return ROOT;
        }

        @Override
        public void apply() throws HostErrorException {
            this.applied.postValue(true);
        }

        @Override
        public void revert() throws HostErrorException {
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
