package org.adaway.ui.adware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.R;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AdwareScannerInstrumentedTest {
    private static final long TIMEOUT_MS = 10_000L;
    private static final String FIXTURE_ACTIVITY =
            "com.airpush.fixture.AdwareFixtureActivity";
    private static final String FIXTURE_LABEL = "Airpush Test Fixture";

    private Context context;
    private String fixturePackageName;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        this.fixturePackageName = InstrumentationRegistry.getInstrumentation()
                .getContext()
                .getPackageName();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up adware scanner");
    }

    @After
    public void tearDown() {
        if (this.context != null) {
            InstrumentedTestState.resetForPassiveRootUi(
                    this.context,
                    "tear down adware scanner");
        }
    }

    @Test
    public void scannerShowsInstalledLaunchablePackageWithKnownAdwareComponent() {
        assertFixturePackageVisibleToTargetApp();

        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            openAdwareScanner(scenario);
            waitForDetectedFixtureRow(scenario);
        }
    }

    @SuppressWarnings("deprecation")
    private void assertFixturePackageVisibleToTargetApp() {
        PackageManager packageManager = this.context.getPackageManager();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(
                    this.fixturePackageName,
                    PackageManager.GET_ACTIVITIES);
            assertEquals("Fixture application label",
                    FIXTURE_LABEL,
                    packageManager.getApplicationLabel(packageInfo.applicationInfo).toString());
            assertNotNull("Fixture package must expose activities.", packageInfo.activities);
            boolean foundFixtureActivity = false;
            for (android.content.pm.ActivityInfo activityInfo : packageInfo.activities) {
                foundFixtureActivity |= FIXTURE_ACTIVITY.equals(activityInfo.name);
            }
            assertTrue("Target app must see the launchable adware fixture activity.",
                    foundFixtureActivity);
        } catch (PackageManager.NameNotFoundException exception) {
            throw new AssertionError(
                    "Target app cannot see the launchable instrumentation fixture package "
                            + this.fixturePackageName,
                    exception);
        }
    }

    private void openAdwareScanner(ActivityScenario<HomeActivity> scenario) {
        scenario.onActivity(activity -> {
            activity.navigateTo(R.id.nav_more);
            activity.getSupportFragmentManager().executePendingTransactions();
            View scannerRow = activity.findViewById(R.id.more_row_adware_scanner);
            assertNotNull("Missing More > Adware Scanner row.", scannerRow);
            assertTrue("Adware Scanner row click failed.", scannerRow.performClick());
            activity.getSupportFragmentManager().executePendingTransactions();
        });
    }

    private void waitForDetectedFixtureRow(ActivityScenario<HomeActivity> scenario) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        RowSnapshot lastSnapshot = new RowSnapshot();
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                activity.getSupportFragmentManager().executePendingTransactions();
                ListView listView = activity.findViewById(R.id.adware_list);
                if (listView == null || listView.getVisibility() != View.VISIBLE) {
                    lastSnapshot.description = "adware list not visible";
                    return;
                }
                ListAdapter adapter = listView.getAdapter();
                if (adapter == null) {
                    lastSnapshot.description = "adware list adapter missing";
                    return;
                }
                lastSnapshot.description = "adapter count=" + adapter.getCount();
                for (int index = 0; index < adapter.getCount(); index++) {
                    AdwareInstall install = (AdwareInstall) adapter.getItem(index);
                    String packageName = install.get(AdwareInstall.PACKAGE_NAME_KEY);
                    String applicationName = install.get(AdwareInstall.APPLICATION_NAME_KEY);
                    if (this.fixturePackageName.equals(packageName)
                            && FIXTURE_LABEL.equals(applicationName)) {
                        lastSnapshot.found = true;
                        return;
                    }
                }
            });
            if (lastSnapshot.found) {
                return;
            }
            SystemClock.sleep(100L);
        }

        throw new AssertionError("Adware scanner did not show fixture package "
                + this.fixturePackageName + ". Last state: " + lastSnapshot.description);
    }

    private static final class RowSnapshot {
        private boolean found;
        private String description = "";
    }
}
