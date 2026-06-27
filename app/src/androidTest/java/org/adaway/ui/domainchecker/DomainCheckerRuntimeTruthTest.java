package org.adaway.ui.domainchecker;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.db.entity.RuleKind.EXACT;
import static org.adaway.db.entity.RuleKind.SUFFIX;
import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.android.material.textfield.TextInputEditText;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.RuleKind;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class DomainCheckerRuntimeTruthTest {
    private static final int TIMEOUT_MS = 10_000;
    private static final int TEST_SOURCE_ID = 929292;
    private static final String SOURCE_LABEL = "Domain checker runtime truth test";
    private static final String SUFFIX_HOST = "domainchecker-runtime-ci.invalid";
    private static final String CHILD_HOST = "ads.domainchecker-runtime-ci.invalid";
    private static final String BLOCKED_HOST = "blocked.domainchecker-runtime-ci.invalid";
    private static final String ALLOWED_HOST = "allowed.domainchecker-runtime-ci.invalid";
    private static final String REDIRECTED_HOST = "redirected.domainchecker-runtime-ci.invalid";
    private static final String UNKNOWN_HOST = "unknown.domainchecker-runtime-ci.invalid";

    private Context context;
    private AdAwayApplication application;
    private AppDatabase database;
    private HostListItemDao hostListItemDao;
    private HostEntryDao hostEntryDao;
    private HostsSourceDao hostsSourceDao;
    private AdBlockMethod originalMethod;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(context, "set up Domain Checker runtime truth");
        waitForDiskIoIdle();
        application = (AdAwayApplication) context.getApplicationContext();
        database = AppDatabase.getInstance(application);
        hostListItemDao = database.hostsListItemDao();
        hostEntryDao = database.hostEntryDao();
        hostsSourceDao = database.hostsSourceDao();
        originalMethod = PreferenceHelper.getAdBlockMethod(application);
        cleanup();
        insertSource();
        insertRuntimeTruthRules();
        hostsSourceDao.updateActiveRuleStats(TEST_SOURCE_ID);
        hostsSourceDao.updateActiveRuleStats(USER_SOURCE_ID);
        hostEntryDao.sync();
        assertEquals("Test fixture must materialize the root-mode base suffix before checking UI.",
                BLOCKED, hostEntryDao.getRootTypeForHost(SUFFIX_HOST));
        assertEquals(BLOCKED, hostEntryDao.getRootTypeForHost(BLOCKED_HOST));
        assertEquals(ALLOWED, hostEntryDao.getRootTypeForHost(ALLOWED_HOST));
        assertEquals(REDIRECTED, hostEntryDao.getRootTypeForHost(REDIRECTED_HOST));
        application.invalidateVpnRulesCache();
    }

    @After
    public void tearDown() {
        finishResumedActivities();
        cleanup();
        PreferenceHelper.setAbBlockMethod(application, originalMethod);
        hostEntryDao.sync();
        application.invalidateVpnRulesCache();
        if (context != null) {
            InstrumentedTestState.resetForPassiveRootUi(context, "tear down Domain Checker runtime truth");
        }
    }

    @Test
    public void domainCheckerUsesRootExactTruthAndVpnSuffixTruth() throws Exception {
        PreferenceHelper.setAbBlockMethod(application, ROOT);
        DomainCheckResult rootBaseResult = check(SUFFIX_HOST);
        assertTrue("Root hosts-file mode must report suffix rules for their materialized base host.",
                rootBaseResult.blocked);
        assertEquals(DomainCheckResult.Status.BLOCKED, rootBaseResult.status);
        assertEquals(1, rootBaseResult.blockingSources.size());
        assertEquals(SOURCE_LABEL, rootBaseResult.blockingSources.get(0).name);

        DomainCheckResult rootChildResult = check(CHILD_HOST);
        assertFalse("Root hosts-file mode must not report suffix-only child matches as blocked.",
                rootChildResult.blocked);
        assertEquals(DomainCheckResult.Status.UNKNOWN, rootChildResult.status);
        assertTrue(rootChildResult.blockingSources.isEmpty());

        PreferenceHelper.setAbBlockMethod(application, VPN);
        DomainCheckResult vpnBaseResult = check(SUFFIX_HOST);
        assertTrue("VPN mode must report suffix rules for base domains.", vpnBaseResult.blocked);
        assertEquals(DomainCheckResult.Status.BLOCKED, vpnBaseResult.status);
        assertEquals(1, vpnBaseResult.blockingSources.size());
        assertEquals(SOURCE_LABEL, vpnBaseResult.blockingSources.get(0).name);

        DomainCheckResult vpnChildResult = check(CHILD_HOST);
        assertTrue("VPN mode must report suffix rules for child domains.", vpnChildResult.blocked);
        assertEquals(DomainCheckResult.Status.BLOCKED, vpnChildResult.status);
        assertEquals(1, vpnChildResult.blockingSources.size());
        assertEquals(SOURCE_LABEL, vpnChildResult.blockingSources.get(0).name);
    }

    @Test(timeout = 120_000)
    public void domainCheckerFragmentRendersRuntimeTruthStates() {
        PreferenceHelper.setAbBlockMethod(application, ROOT);
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            HomeActivity activity = showDomainChecker(scenario);

            assertDomainStatus(activity, BLOCKED_HOST,
                    context.getString(R.string.domain_checker_status_blocked),
                    context.getString(R.string.domain_checker_filter_source_format, SOURCE_LABEL));
            assertDomainStatus(activity, REDIRECTED_HOST,
                    context.getString(R.string.redirect_hosts_label),
                    context.getString(R.string.domain_checker_filter_source_format, SOURCE_LABEL));
            assertDomainStatus(activity, ALLOWED_HOST,
                    context.getString(R.string.allowed_hosts_label),
                    context.getString(R.string.domain_checker_already_allowed));
            assertDomainStatus(activity, UNKNOWN_HOST,
                    context.getString(R.string.domain_checker_status_unknown),
                    context.getString(R.string.domain_checker_not_blocked));
        }
    }

    @Test(timeout = 120_000)
    public void domainCheckerFragmentNormalizesPastedInputBeforeLookup() {
        PreferenceHelper.setAbBlockMethod(application, ROOT);
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            HomeActivity activity = showDomainChecker(scenario);
            String pastedInput = "  https://"
                    + BLOCKED_HOST.toUpperCase(Locale.ROOT)
                    + ".:443/path/to/ad.js?cache_bust=1#tracker  ";

            checkDomainInUi(activity, pastedInput);

            waitForVisibleText(activity, BLOCKED_HOST);
            waitForVisibleText(activity, context.getString(R.string.domain_checker_status_blocked));
            waitForVisibleText(activity,
                    context.getString(R.string.domain_checker_filter_source_format, SOURCE_LABEL));
        }
    }

    private DomainCheckResult check(String host) throws InterruptedException {
        DomainCheckerViewModel viewModel = new DomainCheckerViewModel(application);
        CountDownLatch latch = new CountDownLatch(1);
        final DomainCheckResult[] result = new DomainCheckResult[1];
        Observer<DomainCheckResult> observer = new Observer<DomainCheckResult>() {
            @Override
            public void onChanged(@Nullable DomainCheckResult value) {
                result[0] = value;
                latch.countDown();
                removeObserverOnMain(viewModel, this);
            }
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            viewModel.checkResult.observeForever(observer);
            viewModel.checkDomain(host);
        });
        if (!latch.await(3, TimeUnit.SECONDS)) {
            removeObserverOnMain(viewModel, observer);
            fail("Timed out waiting for domain checker result.");
        }
        return result[0];
    }

    private static void removeObserverOnMain(DomainCheckerViewModel viewModel,
            Observer<DomainCheckResult> observer) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            viewModel.checkResult.removeObserver(observer);
            return;
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> viewModel.checkResult.removeObserver(observer));
    }

    private void insertSource() {
        HostsSource source = new HostsSource();
        source.setId(TEST_SOURCE_ID);
        source.setLabel(SOURCE_LABEL);
        source.setUrl("https://example.invalid/domain-checker-runtime.txt");
        source.setEnabled(true);
        hostsSourceDao.insert(source);
    }

    private void insertRuntimeTruthRules() {
        insertItem(BLOCKED_HOST, BLOCKED, EXACT, TEST_SOURCE_ID, null);
        insertItem(ALLOWED_HOST, ALLOWED, EXACT, USER_SOURCE_ID, null);
        insertItem(REDIRECTED_HOST, REDIRECTED, EXACT, TEST_SOURCE_ID, "127.0.0.2");
        insertItem(SUFFIX_HOST, BLOCKED, SUFFIX, TEST_SOURCE_ID, null);
    }

    private void insertItem(
            String host,
            ListType type,
            RuleKind kind,
            int sourceId,
            @Nullable String redirection) {
        HostListItem item = new HostListItem();
        item.setHost(host);
        item.setType(type);
        item.setKind(kind);
        item.setEnabled(true);
        item.setSourceId(sourceId);
        item.setRedirection(redirection);
        item.setGeneration(getActiveGeneration());
        hostListItemDao.insert(item);
    }

    private int getActiveGeneration() {
        try (Cursor cursor = database.getOpenHelper().getWritableDatabase().query(
                "SELECT active_generation FROM hosts_meta WHERE id = 0 LIMIT 1")) {
            if (!cursor.moveToFirst()) {
                return 0;
            }
            return cursor.getInt(0);
        }
    }

    private void cleanup() {
        database.getOpenHelper().getWritableDatabase().execSQL(
                "DELETE FROM hosts_lists WHERE host IN (?, ?, ?, ?, ?, ?)",
                new Object[]{
                        SUFFIX_HOST,
                        CHILD_HOST,
                        BLOCKED_HOST,
                        ALLOWED_HOST,
                        REDIRECTED_HOST,
                        UNKNOWN_HOST});
        hostListItemDao.clearSourceHosts(TEST_SOURCE_ID);
        hostsSourceDao.getById(TEST_SOURCE_ID).ifPresent(hostsSourceDao::delete);
        hostEntryDao.invalidateMaterializedRuntimeCaches();
    }

    private static HomeActivity showDomainChecker(ActivityScenario<HomeActivity> scenario) {
        scenario.onActivity(activity -> {
            activity.getSupportFragmentManager().executePendingTransactions();
            activity.getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.nav_fragment_container, new DomainCheckerFragment())
                    .commitNow();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        return waitForHomeActivity();
    }

    private void assertDomainStatus(
            HomeActivity activity,
            String host,
            String expectedStatus,
            String expectedDetail) {
        checkDomainInUi(activity, host);
        waitForVisibleText(activity, host);
        waitForVisibleText(activity, expectedStatus);
        waitForVisibleText(activity, expectedDetail);
    }

    private static void checkDomainInUi(HomeActivity activity, String host) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            TextInputEditText domainEditText = activity.findViewById(R.id.domainEditText);
            View checkButton = activity.findViewById(R.id.checkButton);
            assertTrue("Expected domain field to be visible.",
                    domainEditText != null && domainEditText.isShown());
            assertTrue("Expected check button to be visible.",
                    checkButton != null && checkButton.isShown());
            domainEditText.setText(host);
            assertTrue("Expected check click to be handled.", checkButton.performClick());
        });
    }

    private static HomeActivity waitForHomeActivity() {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<Activity> resumed = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (activity instanceof HomeActivity) {
                        resumed.set(activity);
                        return;
                    }
                }
            });
            Activity activity = resumed.get();
            if (activity instanceof HomeActivity) {
                return (HomeActivity) activity;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out waiting for HomeActivity.");
    }

    private static void waitForVisibleText(Activity activity, String expectedText) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<Boolean> found = new AtomicReference<>(false);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    found.set(hasVisibleText(activity.getWindow().getDecorView(), expectedText)));
            if (Boolean.TRUE.equals(found.get())) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Text did not appear: " + expectedText);
    }

    private static boolean hasVisibleText(View view, String expectedText) {
        if (view instanceof TextView
                && view.isShown()
                && expectedText.contentEquals(((TextView) view).getText())) {
            return true;
        }
        if (!(view instanceof ViewGroup)) {
            return false;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (hasVisibleText(group.getChildAt(i), expectedText)) {
                return true;
            }
        }
        return false;
    }

    private static void finishResumedActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                activity.finish();
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
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
}
