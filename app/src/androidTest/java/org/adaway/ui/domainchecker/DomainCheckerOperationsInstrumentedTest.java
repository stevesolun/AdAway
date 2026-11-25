package org.adaway.ui.domainchecker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.database.sqlite.SQLiteException;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.model.source.SourceOperationCoordinator;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises real fragment commands and Room writes without starting protection. */
@RunWith(AndroidJUnit4.class)
public class DomainCheckerOperationsInstrumentedTest {
    private static final String HOST = "domain-operation-ci.invalid";
    private static final String OTHER = "other-domain-operation-ci.invalid";
    private static final long TIMEOUT_MS = 10000;
    private AdAwayApplication application;
    private AppDatabase database;
    private CountDownLatch release;

    @Before
    public void setUp() throws Exception {
        application = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(application, "set up domain operations");
        drainDisk();
        database = AppDatabase.getInstance(application);
        cleanup();
    }

    @After
    public void tearDown() throws Exception {
        if (release != null) release.countDown();
        drainDisk();
        if (database != null) cleanup();
        InstrumentedTestState.resetForPassiveRootUi(application, "tear down domain operations");
    }

    @Test(timeout = 120000)
    public void heldBlockHasNoSuccessAndCompletesOnRecreatedView() throws Exception {
        try (ActivityScenario<HomeActivity> scenario = openChecker()) {
            check(scenario, HOST);
            holdDisk();
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.blockButton).performClick();
                assertFalse(activity.findViewById(R.id.checkButton).isEnabled());
                assertFalse(activity.findViewById(R.id.blockButton).isEnabled());
                assertFalse(hasText(activity.getWindow().getDecorView(),
                        application.getString(R.string.domain_checker_blocked_snack, HOST)));
                model(activity).unblockDomain(HOST);
            });
            assertTrue(database.hostsListItemDao().getExactEntriesForHost(HOST).isEmpty());
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertFalse(activity.findViewById(R.id.checkButton).isEnabled());
                assertFalse(activity.findViewById(R.id.blockButton).isEnabled());
            });
            release.countDown();
            awaitIdle(scenario);
            awaitText(scenario, application.getString(R.string.domain_checker_blocked_snack, HOST));
            scenario.onActivity(activity -> assertEquals(DomainCheckResult.Status.BLOCKED,
                    model(activity).checkResult.getValue().status));
            assertEquals(1, database.hostsListItemDao().getExactEntriesForHost(HOST).size());
        }
    }

    @Test(timeout = 120000)
    public void heldAllowRemoveAndDeleteDoNotAnnounceSuccessEarly() throws Exception {
        seedRule(ListType.BLOCKED);
        try (ActivityScenario<HomeActivity> scenario = openChecker()) {
            check(scenario, HOST);
            heldClick(scenario, R.id.unblockButton, R.string.domain_checker_allowed_snack);
            assertEquals(ListType.ALLOWED, database.hostEntryDao().getRootTypeForHost(HOST));
            heldClick(scenario, R.id.removeAllowButton, R.string.domain_checker_allow_removed);
            assertEquals("Removing an exception must restore the underlying user block",
                    ListType.BLOCKED, database.hostEntryDao().getRootTypeForHost(HOST));
            heldClick(scenario, 0, R.string.domain_checker_rule_deleted);
            assertNull(userRule());
        }
    }

    @Test(timeout = 120000)
    public void insertFailureIsDeliveredAfterStoppedOwnerRecreationAndCanRetry() throws Exception {
        failInsert("hosts_lists");
        try (ActivityScenario<HomeActivity> scenario = openChecker()) {
            check(scenario, HOST);
            AtomicReference<DomainCheckerViewModel> retained = new AtomicReference<>();
            AtomicReference<Fragment> original = new AtomicReference<>();
            AtomicReference<DomainCheckerViewModel.OperationResult> failure = new AtomicReference<>();
            holdDisk();
            scenario.onActivity(activity -> {
                retained.set(model(activity));
                original.set(activity.getSupportFragmentManager()
                        .findFragmentById(R.id.nav_fragment_container));
                activity.findViewById(R.id.blockButton).performClick();
                // recreate() resumes the old Activity first. Keep its Fragment below STARTED
                // so only the recreated view can consume the retained failure.
                activity.getSupportFragmentManager().beginTransaction()
                        .setMaxLifecycle(original.get(), Lifecycle.State.CREATED).commitNow();
            });
            scenario.moveToState(Lifecycle.State.CREATED);
            release.countDown();
            drainDisk();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                assertFalse(retained.get().loading.getValue());
                assertNotNull(retained.get().getOperationResult().getValue());
                assertEquals(R.string.domain_checker_change_failed,
                        retained.get().getOperationResult().getValue().error);
                failure.set(retained.get().getOperationResult().getValue());
            });
            scenario.recreate();
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> {
                Fragment recreated = activity.getSupportFragmentManager()
                        .findFragmentById(R.id.nav_fragment_container);
                assertNotSame(original.get(), recreated);
                assertSame(retained.get(), model(activity));
                assertSame("Recreation must retain the undelivered failure", failure.get(),
                        model(activity).getOperationResult().getValue());
                assertEquals(Lifecycle.State.CREATED, recreated.getLifecycle().getCurrentState());
                activity.getSupportFragmentManager().beginTransaction()
                        .setMaxLifecycle(recreated, Lifecycle.State.RESUMED).commitNow();
            });
            awaitText(scenario, application.getString(R.string.domain_checker_change_failed));
            scenario.onActivity(activity -> {
                assertTrue(activity.findViewById(R.id.checkButton).isEnabled());
                assertEquals(View.GONE, activity.findViewById(R.id.resultCard).getVisibility());
                assertFalse(hasText(activity.getWindow().getDecorView(),
                        application.getString(R.string.domain_checker_blocked_snack, HOST)));
            });
            assertNull(userRule());
            dropFailure();
            check(scenario, HOST);
            scenario.onActivity(activity -> activity.findViewById(R.id.blockButton).performClick());
            awaitIdle(scenario);
            awaitText(scenario, application.getString(R.string.domain_checker_blocked_snack, HOST));
            assertEquals(ListType.BLOCKED, userRule().getType());
        }
    }

    @Test(timeout = 120000)
    public void failedReplacementRollsBackPreviousAllowRuleThenRetries() throws Exception {
        seedRule(ListType.ALLOWED);
        int previousId = userRule().getId();
        failInsert("hosts_lists");
        try (ActivityScenario<HomeActivity> scenario = openChecker()) {
            check(scenario, HOST);
            scenario.onActivity(activity -> model(activity).blockDomain(HOST));
            awaitIdle(scenario);
            awaitText(scenario, application.getString(R.string.domain_checker_change_failed));
            assertEquals(previousId, userRule().getId());
            assertEquals(ListType.ALLOWED, userRule().getType());
            dropFailure();
            scenario.onActivity(activity -> model(activity).blockDomain(HOST));
            awaitIdle(scenario);
            assertEquals(ListType.BLOCKED, userRule().getType());
        }
    }

    @Test(timeout = 120000)
    public void runtimeSyncFailureDoesNotClaimSuccessAndRetrySynchronizes() throws Exception {
        failInsert("host_entries");
        try (ActivityScenario<HomeActivity> scenario = openChecker()) {
            check(scenario, HOST);
            scenario.onActivity(activity -> activity.findViewById(R.id.blockButton).performClick());
            awaitIdle(scenario);
            awaitText(scenario, application.getString(R.string.domain_checker_change_failed));
            assertEquals("The rule write committed before runtime sync failed",
                    ListType.BLOCKED, userRule().getType());
            scenario.onActivity(activity -> {
                assertTrue(activity.findViewById(R.id.checkButton).isEnabled());
                assertEquals(View.GONE, activity.findViewById(R.id.resultCard).getVisibility());
                assertFalse(hasText(activity.getWindow().getDecorView(),
                        application.getString(R.string.domain_checker_blocked_snack, HOST)));
            });
            dropFailure();
            scenario.onActivity(activity -> model(activity).blockDomain(HOST));
            awaitIdle(scenario);
            awaitText(scenario, application.getString(R.string.domain_checker_blocked_snack, HOST));
            assertEquals(ListType.BLOCKED, database.hostEntryDao().getRootTypeForHost(HOST));
            assertEquals(1, database.hostsListItemDao().getExactEntriesForHost(HOST).size());
        }
    }

    @Test(timeout = 120000)
    public void readFailureClearsBusyAndStaleResultThenCheckRetries() throws Exception {
        try (ActivityScenario<HomeActivity> scenario = openChecker()) {
            check(scenario, HOST);
            AtomicReference<DomainCheckerViewModel> owner = new AtomicReference<>();
            scenario.onActivity(activity -> owner.set(model(activity)));
            Field field = DomainCheckerViewModel.class.getDeclaredField("mHostsSourceDao");
            field.setAccessible(true);
            Object original = field.get(owner.get());
            Object failing = Proxy.newProxyInstance(HostsSourceDao.class.getClassLoader(),
                    new Class<?>[]{HostsSourceDao.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getAll")) throw new SQLiteException("test read");
                        try {
                            return method.invoke(original, args);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
            try {
                field.set(owner.get(), failing);
                check(scenario, OTHER);
                awaitText(scenario, application.getString(R.string.domain_checker_check_failed));
                scenario.onActivity(activity -> {
                    assertTrue(activity.findViewById(R.id.checkButton).isEnabled());
                    assertEquals(View.GONE, activity.findViewById(R.id.resultCard).getVisibility());
                    assertNull(model(activity).checkResult.getValue());
                });
            } finally {
                field.set(owner.get(), original);
            }
            check(scenario, OTHER);
            scenario.onActivity(activity -> assertEquals(OTHER,
                    model(activity).checkResult.getValue().domain));
        }
    }

    @Test(timeout = 120000)
    public void failedAllowRefreshRetriesWithoutDuplicatesOrLosingUnderlyingBlock() throws Exception {
        seedRule(ListType.BLOCKED);
        try (ActivityScenario<HomeActivity> scenario = openChecker()) {
            check(scenario, HOST);
            AtomicReference<DomainCheckerViewModel> owner = new AtomicReference<>();
            scenario.onActivity(activity -> owner.set(model(activity)));
            Field field = DomainCheckerViewModel.class.getDeclaredField("mHostsSourceDao");
            field.setAccessible(true);
            Object original = field.get(owner.get());
            Object failing = Proxy.newProxyInstance(HostsSourceDao.class.getClassLoader(),
                    new Class<?>[]{HostsSourceDao.class}, (proxy, method, args) -> {
                        throw new SQLiteException("test refresh");
                    });
            try {
                field.set(owner.get(), failing);
                scenario.onActivity(activity -> activity.findViewById(R.id.unblockButton)
                        .performClick());
                awaitIdle(scenario);
                awaitText(scenario, application.getString(R.string.domain_checker_change_failed));
                scenario.onActivity(activity -> assertFalse(hasText(
                        activity.getWindow().getDecorView(),
                        application.getString(R.string.domain_checker_allowed_snack, HOST))));
                assertEquals(ListType.ALLOWED, database.hostEntryDao().getRootTypeForHost(HOST));
            } finally {
                field.set(owner.get(), original);
            }
            scenario.onActivity(activity -> model(activity).unblockDomain(HOST));
            awaitIdle(scenario);
            awaitText(scenario, application.getString(R.string.domain_checker_allowed_snack, HOST));
            assertEquals("One underlying block and one allow exception after retry", 2,
                    database.hostsListItemDao().getExactEntriesForHost(HOST).size());
            scenario.onActivity(activity -> activity.findViewById(R.id.removeAllowButton)
                    .performClick());
            awaitIdle(scenario);
            assertEquals(ListType.BLOCKED, database.hostEntryDao().getRootTypeForHost(HOST));
        }
    }

    @Test(timeout = 120000)
    public void newerCheckWinsOverMutationRefreshWithNoIdleGap() throws Exception {
        try (ActivityScenario<HomeActivity> scenario = openChecker()) {
            check(scenario, HOST);
            List<Boolean> busyStates = new ArrayList<>();
            List<String> renderedDomains = new ArrayList<>();
            Observer<Boolean> busy = busyStates::add;
            Observer<DomainCheckResult> result = value -> {
                if (value != null) renderedDomains.add(value.domain);
            };
            AtomicReference<DomainCheckerViewModel> owner = new AtomicReference<>();
            holdDisk();
            scenario.onActivity(activity -> {
                owner.set(model(activity));
                owner.get().loading.observeForever(busy);
                owner.get().checkResult.observeForever(result);
                busyStates.clear();
                renderedDomains.clear();
                owner.get().blockDomain(HOST);
                owner.get().checkDomain(OTHER);
                owner.get().unblockDomain(OTHER);
            });
            try {
                release.countDown();
                awaitIdle(scenario);
                scenario.onActivity(activity -> {
                    assertEquals(OTHER, model(activity).checkResult.getValue().domain);
                    assertEquals(1, renderedDomains.size());
                    assertEquals(OTHER, renderedDomains.get(0));
                    assertFalse(busyStates.get(busyStates.size() - 1));
                    for (int i = 0; i < busyStates.size() - 1; i++) assertTrue(busyStates.get(i));
                });
                assertEquals(ListType.BLOCKED, userRule().getType());
                assertTrue(database.hostsListItemDao().getExactEntriesForHost(OTHER).isEmpty());
            } finally {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    owner.get().loading.removeObserver(busy);
                    owner.get().checkResult.removeObserver(result);
                });
            }
        }
    }

    @Test(timeout = 120000)
    public void sharedSourceOperationRejectsMutationWithoutWritingAndAllowsRetry() throws Exception {
        try (ActivityScenario<HomeActivity> scenario = openChecker()) {
            check(scenario, HOST);
            try (SourceOperationCoordinator.Lease ignored = application.getSourceOperations().acquire()) {
                scenario.onActivity(activity -> activity.findViewById(R.id.blockButton).performClick());
                awaitIdle(scenario);
                awaitText(scenario, application.getString(R.string.error_update_in_progress_message));
                assertNull(userRule());
            }
            scenario.onActivity(activity -> model(activity).blockDomain(HOST));
            awaitIdle(scenario);
            assertEquals(ListType.BLOCKED, userRule().getType());
        }
    }

    @Test(timeout = 120000)
    public void credentialsCannotSelectOrMutateUsernameHost() throws Exception {
        String input = "https://" + HOST + ":secret@" + OTHER + "/path";
        try (ActivityScenario<HomeActivity> scenario = openChecker()) {
            check(scenario, HOST);
            check(scenario, input);
            scenario.onActivity(activity -> {
                assertNull(model(activity).checkResult.getValue());
                assertEquals(View.GONE, activity.findViewById(R.id.resultCard).getVisibility());
                model(activity).blockDomain(input);
                model(activity).unblockDomain(input);
                model(activity).removeUserAllowRule(input);
                model(activity).deleteRule(1, input);
                assertFalse(model(activity).loading.getValue());
            });
            drainDisk();
            assertNull(userRule());
            assertTrue(database.hostsListItemDao().getExactEntriesForHost(OTHER).isEmpty());
        }
    }

    private void heldClick(ActivityScenario<HomeActivity> scenario, int id, int message)
            throws Exception {
        String expected = application.getString(message, HOST);
        holdDisk();
        scenario.onActivity(activity -> {
            View control = id == 0 ? findText(activity.findViewById(R.id.sourcesContainer),
                    application.getString(R.string.domain_checker_delete_rule))
                    : activity.findViewById(id);
            assertNotNull(control);
            assertTrue(control.isEnabled());
            control.performClick();
            assertFalse(control.isEnabled());
            assertFalse(activity.findViewById(R.id.checkButton).isEnabled());
            assertFalse(hasText(activity.getWindow().getDecorView(), expected));
        });
        release.countDown();
        awaitIdle(scenario);
        awaitText(scenario, expected);
    }

    private void seedRule(ListType type) {
        HostListItem item = new HostListItem();
        item.setSourceId(HostsSource.USER_SOURCE_ID);
        item.setHost(HOST);
        item.setType(type);
        item.setEnabled(true);
        database.hostsListItemDao().insert(item);
        database.hostEntryDao().sync();
        application.invalidateVpnRulesCache();
    }

    private HostListItem userRule() {
        for (HostListItem item : database.hostsListItemDao().getExactEntriesForHost(HOST)) {
            if (item.getSourceId() == HostsSource.USER_SOURCE_ID) return item;
        }
        return null;
    }

    private void failInsert(String table) {
        database.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER domain_operation_failure BEFORE INSERT ON " + table
                        + " WHEN NEW.host = '" + HOST + "'"
                        + " BEGIN SELECT RAISE(ABORT, 'domain operation test'); END");
    }

    private void dropFailure() {
        database.getOpenHelper().getWritableDatabase().execSQL(
                "DROP TRIGGER IF EXISTS domain_operation_failure");
    }

    private static View findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private ActivityScenario<HomeActivity> openChecker() {
        ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class);
        scenario.onActivity(activity -> activity.getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_fragment_container, new DomainCheckerFragment()).commitNow());
        return scenario;
    }

    private void check(ActivityScenario<HomeActivity> scenario, String input) throws Exception {
        scenario.onActivity(activity -> {
            ((TextView) activity.findViewById(R.id.domainEditText)).setText(input);
            activity.findViewById(R.id.checkButton).performClick();
        });
        awaitIdle(scenario);
    }

    private static DomainCheckerViewModel model(HomeActivity activity) {
        return new ViewModelProvider(activity.getSupportFragmentManager()
                .findFragmentById(R.id.nav_fragment_container)).get(DomainCheckerViewModel.class);
    }

    private void holdDisk() throws Exception {
        release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(() -> {
            entered.countDown();
            try {
                if (!release.await(30, TimeUnit.SECONDS)) {
                    throw new AssertionError("Held domain operation was not released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        });
        assertTrue(entered.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    private static void drainDisk() throws Exception {
        CountDownLatch idle = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(idle::countDown);
        assertTrue("Disk executor did not drain", idle.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void awaitIdle(ActivityScenario<HomeActivity> scenario) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        AtomicBoolean idle = new AtomicBoolean();
        do {
            drainDisk();
            scenario.onActivity(activity -> idle.set(!Boolean.TRUE.equals(
                    model(activity).loading.getValue())));
            if (idle.get()) return;
            SystemClock.sleep(25);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Domain operation remained busy");
    }

    private static void awaitText(ActivityScenario<HomeActivity> scenario, String text) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        AtomicBoolean found = new AtomicBoolean();
        do {
            scenario.onActivity(activity -> found.set(hasText(
                    activity.getWindow().getDecorView(), text)));
            if (found.get()) return;
            SystemClock.sleep(25);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Missing feedback: " + text);
    }

    private static boolean hasText(View view, String text) {
        if (view instanceof TextView && view.isShown()
                && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasText(group.getChildAt(i), text)) return true;
            }
        }
        return false;
    }

    private void cleanup() {
        dropFailure();
        database.getOpenHelper().getWritableDatabase().execSQL(
                "DELETE FROM hosts_lists WHERE source_id = ? AND host IN (?, ?)",
                new Object[]{HostsSource.USER_SOURCE_ID, HOST, OTHER});
        database.hostEntryDao().sync();
        application.invalidateVpnRulesCache();
    }
}
