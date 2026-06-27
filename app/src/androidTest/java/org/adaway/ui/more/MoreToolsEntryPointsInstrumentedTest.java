package org.adaway.ui.more;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.adaway.R;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.about.AboutActivity;
import org.adaway.ui.adware.AdwareFragment;
import org.adaway.ui.domainchecker.DomainCheckerFragment;
import org.adaway.ui.home.HomeActivity;
import org.adaway.ui.hosts.HostsSourcesTabFragment;
import org.adaway.ui.lists.ListsActivity;
import org.adaway.ui.log.LogActivity;
import org.adaway.ui.prefs.PrefsActivity;
import org.adaway.ui.prefs.PrefsBackupRestoreFragment;
import org.adaway.ui.prefs.PrefsMainFragment;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collection;

@RunWith(AndroidJUnit4.class)
public class MoreToolsEntryPointsInstrumentedTest {
    private static final long TIMEOUT_MS = 10_000L;
    private static final Uri GITHUB_URI = Uri.parse("https://github.com/AdAway/AdAway");

    private Context context;
    private Instrumentation instrumentation;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        this.instrumentation = InstrumentationRegistry.getInstrumentation();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up More tools");
    }

    @After
    public void tearDown() {
        finishResumedActivities();
        if (this.context != null) {
            InstrumentedTestState.resetForPassiveRootUi(this.context, "tear down More tools");
        }
    }

    @Test(timeout = 60_000)
    public void domainCheckerRowOpensDomainCheckerFragment() {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            clickMoreRow(scenario, R.id.domainCheckerCard);
            waitForContainerFragment(scenario, DomainCheckerFragment.class, "Domain Checker");
        }
    }

    @Test(timeout = 60_000)
    public void dnsLogRowOpensLogActivity() {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            clickMoreRow(scenario, R.id.more_row_dns_log);
            waitForResumedActivity(LogActivity.class, "DNS Log");
        }
    }

    @Test(timeout = 60_000)
    public void customRulesRowOpensListsActivity() {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            clickMoreRow(scenario, R.id.more_row_custom_rules);
            waitForResumedActivity(ListsActivity.class, "Custom Rules");
        }
    }

    @Test(timeout = 60_000)
    public void filterSourcesRowSelectsSourcesTab() {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            clickMoreRow(scenario, R.id.more_row_filter_sources);
            waitForSourcesDestination(scenario);
        }
    }

    @Test(timeout = 60_000)
    public void adwareScannerRowOpensAdwareFragment() {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            clickMoreRow(scenario, R.id.more_row_adware_scanner);
            waitForContainerFragment(scenario, AdwareFragment.class, "Adware Scanner");
        }
    }

    @Test(timeout = 60_000)
    public void preferencesRowOpensMainPreferences() {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            clickMoreRow(scenario, R.id.more_row_preferences);
            waitForPrefsFragment(PrefsMainFragment.class, "Preferences");
        }
    }

    @Test(timeout = 60_000)
    public void backupRowOpensBackupRestorePreferences() {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            clickMoreRow(scenario, R.id.more_row_backup);
            waitForPrefsFragment(PrefsBackupRestoreFragment.class, "Backup and Restore");
        }
    }

    @Test(timeout = 60_000)
    public void aboutRowOpensAboutActivity() {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            clickMoreRow(scenario, R.id.more_row_about);
            waitForResumedActivity(AboutActivity.class, "About");
        }
    }

    @Test(timeout = 60_000)
    public void githubRowUsesGuardedExternalIntent() throws Exception {
        Intent viewIntent = new Intent(Intent.ACTION_VIEW, GITHUB_URI);
        boolean hasExternalHandler =
                viewIntent.resolveActivity(this.context.getPackageManager()) != null;
        Instrumentation.ActivityMonitor monitor = addHttpsViewMonitor();
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            clickMoreRow(scenario, R.id.more_row_github);
            if (hasExternalHandler) {
                waitForMonitorHit(monitor, "GitHub external URL");
            } else {
                waitForHomeResumed();
            }
        } finally {
            this.instrumentation.removeMonitor(monitor);
        }
    }

    private static void clickMoreRow(ActivityScenario<HomeActivity> scenario, int rowId) {
        scenario.onActivity(activity -> {
            activity.navigateTo(R.id.nav_more);
            activity.getSupportFragmentManager().executePendingTransactions();
            Fragment moreFragment = activity.getSupportFragmentManager().findFragmentByTag("more");
            assertTrue("More fragment should be active before row click.",
                    moreFragment instanceof MoreFragment);
            View row = activity.findViewById(rowId);
            assertNotNull("Missing More row: " + rowId, row);
            assertTrue("More row is not shown: " + rowId, row.isShown());
            assertTrue("More row click failed: " + rowId, row.performClick());
            activity.getSupportFragmentManager().executePendingTransactions();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void waitForContainerFragment(
            ActivityScenario<HomeActivity> scenario,
            @NonNull Class<? extends Fragment> fragmentClass,
            @NonNull String description) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            final boolean[] ready = new boolean[1];
            scenario.onActivity(activity -> {
                activity.getSupportFragmentManager().executePendingTransactions();
                Fragment fragment = activity.getSupportFragmentManager()
                        .findFragmentById(R.id.nav_fragment_container);
                ready[0] = fragmentClass.isInstance(fragment)
                        && fragment.isAdded()
                        && fragment.getView() != null
                        && fragment.getView().isShown();
            });
            if (ready[0]) {
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError(description + " fragment did not become visible.");
    }

    private static void waitForSourcesDestination(ActivityScenario<HomeActivity> scenario) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            final boolean[] ready = new boolean[1];
            scenario.onActivity(activity -> {
                activity.getSupportFragmentManager().executePendingTransactions();
                BottomNavigationView nav = activity.findViewById(R.id.bottom_navigation);
                Fragment fragment = activity.getSupportFragmentManager().findFragmentByTag("sources");
                ready[0] = nav != null
                        && nav.getSelectedItemId() == R.id.nav_sources
                        && fragment instanceof HostsSourcesTabFragment
                        && fragment.isAdded()
                        && fragment.getView() != null
                        && fragment.getView().isShown();
            });
            if (ready[0]) {
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError("Filter Sources row did not select the Sources tab.");
    }

    private static <T extends Activity> T waitForResumedActivity(
            @NonNull Class<T> activityClass,
            @NonNull String description) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            final Activity[] match = new Activity[1];
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    match[0] = findResumedActivity(activityClass));
            if (activityClass.isInstance(match[0])) {
                return activityClass.cast(match[0]);
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError(description + " activity did not resume.");
    }

    private static void waitForPrefsFragment(
            @NonNull Class<? extends Fragment> fragmentClass,
            @NonNull String description) {
        PrefsActivity activity = waitForResumedActivity(PrefsActivity.class, description);
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            final boolean[] ready = new boolean[1];
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                activity.getSupportFragmentManager().executePendingTransactions();
                Fragment fragment = activity.getSupportFragmentManager()
                        .findFragmentById(android.R.id.content);
                ready[0] = fragmentClass.isInstance(fragment)
                        && fragment.isAdded()
                        && fragment.getView() != null
                        && fragment.getView().isShown();
            });
            if (ready[0]) {
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError(description + " preferences fragment did not become visible.");
    }

    private Instrumentation.ActivityMonitor addHttpsViewMonitor() throws Exception {
        IntentFilter filter = new IntentFilter(Intent.ACTION_VIEW);
        filter.addDataScheme("https");
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(
                filter,
                new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null),
                true
        );
        this.instrumentation.addMonitor(monitor);
        return monitor;
    }

    private static void waitForMonitorHit(
            @NonNull Instrumentation.ActivityMonitor monitor,
            @NonNull String description) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (monitor.getHits() > 0) {
                assertEquals(1, monitor.getHits());
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError(description + " intent was not launched.");
    }

    private static void waitForHomeResumed() {
        waitForResumedActivity(HomeActivity.class, "Home after guarded GitHub click");
    }

    private static <T extends Activity> Activity findResumedActivity(
            @NonNull Class<T> activityClass) {
        Collection<Activity> activities = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED);
        for (Activity activity : activities) {
            if (activityClass.isInstance(activity) && !activity.isFinishing()) {
                return activity;
            }
        }
        return null;
    }

    private static void finishResumedActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Collection<Activity> activities = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED);
            for (Activity activity : activities) {
                activity.finish();
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
