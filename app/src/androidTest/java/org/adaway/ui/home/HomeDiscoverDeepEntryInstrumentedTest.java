package org.adaway.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.discover.DiscoverFragment;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class HomeDiscoverDeepEntryInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;

    private Context context;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up Discover deep entry");
    }

    @After
    public void tearDown() {
        if (this.context != null) {
            AppDatabase.getInstance(this.context).hostsSourceDao().deleteAll();
            InstrumentedTestState.resetForPassiveRootUi(
                    this.context, "tear down Discover deep entry");
        }
    }

    @Test
    public void discoverLaunchExtraOpensDiscoverAsInitialTab() {
        Intent intent = new Intent(this.context, HomeActivity.class)
                .putExtra(HomeActivity.EXTRA_NAV_DISCOVER, true);

        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(intent)) {
            assertDiscoverSelected(scenario);
        }
    }

    @Test
    public void homeNoSourceDiscoverCtaNavigatesToDiscover() {
        try (ActivityScenario<HomeActivity> scenario =
                     ActivityScenario.launch(HomeActivity.class)) {
            clearExternalSources(scenario);
            waitForVisibleView(scenario, R.id.discoverCta, "Home Discover CTA");

            scenario.onActivity(activity -> {
                View cta = activity.findViewById(R.id.discoverCta);
                assertNotNull(cta);
                cta.performClick();
            });

            assertDiscoverSelected(scenario);
        }
    }

    private static void assertDiscoverSelected(ActivityScenario<HomeActivity> scenario) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        scenario.onActivity(activity -> {
            activity.getSupportFragmentManager().executePendingTransactions();

            BottomNavigationView nav = activity.findViewById(R.id.bottom_navigation);
            assertEquals(R.id.nav_discover, nav.getSelectedItemId());

            Fragment selected = activity.getSupportFragmentManager().findFragmentByTag("discover");
            assertTrue("Discover fragment must be loaded for Discover deep entry.",
                    selected instanceof DiscoverFragment);
            assertTrue(selected.isAdded());
            assertNotNull(activity.findViewById(R.id.discoverProfileStatus));
        });
    }

    private static void waitForVisibleView(
            ActivityScenario<HomeActivity> scenario,
            int viewId,
            String description) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AtomicBoolean visible = new AtomicBoolean(false);
            scenario.onActivity(activity -> {
                View view = activity.findViewById(viewId);
                visible.set(view != null && view.getVisibility() == View.VISIBLE);
            });
            if (visible.get()) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError(description + " did not become visible. "
                + describeCurrentUi(scenario));
    }

    private static void clearExternalSources(ActivityScenario<HomeActivity> scenario) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        scenario.onActivity(activity -> AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                AppDatabase.getInstance(activity).hostsSourceDao().deleteAll();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        }));
        try {
            assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while clearing sources.", interruptedException);
        }
        if (failure.get() != null) {
            throw new AssertionError("Failed to clear sources.", failure.get());
        }
    }

    private static String describeCurrentUi(ActivityScenario<HomeActivity> scenario) {
        AtomicReference<String> detail = new AtomicReference<>("");
        scenario.onActivity(activity -> {
            BottomNavigationView nav = activity.findViewById(R.id.bottom_navigation);
            View cta = activity.findViewById(R.id.discoverCta);
            Fragment home = activity.getSupportFragmentManager().findFragmentByTag("home");
            Fragment discover = activity.getSupportFragmentManager().findFragmentByTag("discover");
            detail.set("selectedTab=" + (nav == null ? "missing" : nav.getSelectedItemId())
                    + ", cta=" + describeVisibility(cta)
                    + ", homeFragment=" + describeFragment(home)
                    + ", discoverFragment=" + describeFragment(discover));
        });
        int sourceCount = AppDatabase.getInstance(ApplicationProvider.getApplicationContext())
                .hostsSourceDao()
                .getAll()
                .size();
        return detail.get() + ", externalSourceCount=" + sourceCount;
    }

    private static String describeVisibility(View view) {
        if (view == null) {
            return "missing";
        }
        return "visibility=" + view.getVisibility();
    }

    private static String describeFragment(Fragment fragment) {
        if (fragment == null) {
            return "missing";
        }
        return fragment.getClass().getSimpleName() + ":added=" + fragment.isAdded();
    }

}
