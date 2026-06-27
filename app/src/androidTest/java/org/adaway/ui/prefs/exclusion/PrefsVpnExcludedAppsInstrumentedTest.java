package org.adaway.ui.prefs.exclusion;

import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.view.View;

import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.testing.InstrumentedTestState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PrefsVpnExcludedAppsInstrumentedTest {
    private static final long TIMEOUT_MS = 10_000L;
    private static final String FIXTURE_LABEL = "Airpush Test Fixture";

    private Context context;
    private String fixturePackageName;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        this.fixturePackageName = InstrumentationRegistry.getInstrumentation()
                .getContext()
                .getPackageName();
        InstrumentedTestState.resetForPassiveRootUi(
                this.context,
                "set up VPN excluded apps");
    }

    @After
    public void tearDown() {
        if (this.context != null) {
            InstrumentedTestState.resetForPassiveRootUi(
                    this.context,
                    "tear down VPN excluded apps");
        }
    }

    @Test
    public void excludedAppsPickerListsVisibleUserAppAndPersistsRowToggle() {
        assertFixturePackageVisibleAsUserApp();

        try (ActivityScenario<PrefsVpnExcludedAppsActivity> scenario =
                     ActivityScenario.launch(PrefsVpnExcludedAppsActivity.class)) {
            waitForFixtureListed(scenario);

            toggleFixtureRow(scenario);
            waitForExcludedPreference(true);

            toggleFixtureRow(scenario);
            waitForExcludedPreference(false);
        }
    }

    @SuppressWarnings("deprecation")
    private void assertFixturePackageVisibleAsUserApp() {
        PackageManager packageManager = this.context.getPackageManager();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(
                    this.fixturePackageName,
                    0);
            assertEquals("Fixture application label",
                    FIXTURE_LABEL,
                    packageManager.getApplicationLabel(packageInfo.applicationInfo).toString());
            assertFalse("Fixture package must be treated as a user app.",
                    (packageInfo.applicationInfo.flags & FLAG_SYSTEM) != 0);
        } catch (PackageManager.NameNotFoundException exception) {
            throw new AssertionError(
                    "Target app cannot see the launchable instrumentation fixture package "
                            + this.fixturePackageName,
                    exception);
        }
    }

    private void waitForFixtureListed(ActivityScenario<PrefsVpnExcludedAppsActivity> scenario) {
        waitForActivityCondition(scenario, activity -> {
            RecyclerView recyclerView = activity.findViewById(R.id.vpn_excluded_app_list);
            assertNotNull("Missing VPN excluded app list.", recyclerView);
            assertNotNull("Missing VPN excluded app adapter.", recyclerView.getAdapter());
            int index = findFixtureIndex(activity);
            if (index < 0) {
                return false;
            }
            UserApp app = activity.getUserApplications()[index];
            assertEquals("Fixture label in excluded-apps picker", FIXTURE_LABEL, app.name);
            return true;
        }, "Fixture package was not listed in the VPN excluded-apps picker.");
    }

    private void toggleFixtureRow(ActivityScenario<PrefsVpnExcludedAppsActivity> scenario) {
        waitForActivityCondition(scenario, activity -> {
            RecyclerView recyclerView = activity.findViewById(R.id.vpn_excluded_app_list);
            assertNotNull("Missing VPN excluded app list.", recyclerView);
            int index = findFixtureIndex(activity);
            if (index < 0) {
                return false;
            }
            recyclerView.scrollToPosition(index);
            RecyclerView.ViewHolder holder =
                    recyclerView.findViewHolderForAdapterPosition(index);
            if (holder == null || holder.itemView == null || !holder.itemView.isShown()) {
                return false;
            }
            View row = holder.itemView.findViewById(R.id.rowLayout);
            SwitchCompat excludedSwitch = holder.itemView.findViewById(R.id.excludedSwitch);
            assertNotNull("Missing excluded-app row.", row);
            assertNotNull("Missing excluded-app switch.", excludedSwitch);
            boolean before = excludedSwitch.isChecked();
            assertTrue("Excluded-app row click failed.", row.performClick());
            return excludedSwitch.isChecked() != before;
        }, "Fixture excluded-app row was not toggleable.");
    }

    private void waitForExcludedPreference(boolean expectedExcluded) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            boolean actualExcluded = PreferenceHelper.getVpnExcludedApps(this.context)
                    .contains(this.fixturePackageName);
            if (actualExcluded == expectedExcluded) {
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError("Excluded-app preference for " + this.fixturePackageName
                + " did not become " + expectedExcluded);
    }

    private int findFixtureIndex(PrefsVpnExcludedAppsActivity activity) {
        UserApp[] applications = activity.getUserApplications();
        for (int index = 0; index < applications.length; index++) {
            if (this.fixturePackageName.contentEquals(applications[index].packageName)) {
                return index;
            }
        }
        return -1;
    }

    private void waitForActivityCondition(
            ActivityScenario<PrefsVpnExcludedAppsActivity> scenario,
            ActivityCondition condition,
            String failureMessage) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            boolean[] matched = new boolean[1];
            scenario.onActivity(activity -> matched[0] = condition.matches(activity));
            if (matched[0]) {
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError(failureMessage);
    }

    private interface ActivityCondition {
        boolean matches(PrefsVpnExcludedAppsActivity activity);
    }
}
