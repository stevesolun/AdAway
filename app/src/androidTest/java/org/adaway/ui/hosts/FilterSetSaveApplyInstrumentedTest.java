package org.adaway.ui.hosts;

import static org.adaway.db.entity.ListType.BLOCKED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.appbar.MaterialToolbar;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.RuleKind;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class FilterSetSaveApplyInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;
    private static final int SOURCE_A_ID = 2011;
    private static final int SOURCE_B_ID = 2012;
    private static final int SOURCE_C_ID = 2013;
    private static final String SOURCE_A_URL = "https://filter-set.test/a.txt";
    private static final String SOURCE_B_URL = "https://filter-set.test/b.txt";
    private static final String SOURCE_C_URL = "https://filter-set.test/c.txt";
    private static final String SAVED_SET_NAME = "Travel Pack";
    private static final String RENAMED_SET_NAME = "Weekend Pack";
    private static final String FILTER_SETS_PREFS = "filter_sets";

    private Context context;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up filter sets");
        clearFilterSets(this.context);
        resetWorkManager(this.context);
        AppDatabase.getInstance(this.context).getOpenHelper().getWritableDatabase();
        waitForDiskIoIdle();
        resetSources(this.context);
        seedSources(this.context);
    }

    @After
    public void tearDown() {
        finishResumedActivities();
        if (this.context != null) {
            resetSources(this.context);
            clearFilterSets(this.context);
            resetWorkManager(this.context);
            InstrumentedTestState.resetForPassiveRootUi(this.context, "tear down filter sets");
        }
    }

    @Test(timeout = 120_000)
    public void saveFilterSetValidationThenApplyRestoresSavedSelection() throws Exception {
        ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class);
        navigateToSources(scenario);
        HomeActivity homeActivity = waitForHomeActivity();
        waitForSourceRowsLoaded();

        clickToolbarAction(homeActivity, R.id.action_hosts_save_filter_set);
        clickAccessibilityText(this.context.getString(R.string.button_save));
        assertAccessibilityText(this.context.getString(R.string.filter_set_name_empty));
        assertFalse(FilterSetStore.hasSet(this.context, SAVED_SET_NAME));

        setEditableAccessibilityText(SAVED_SET_NAME);
        clickAccessibilityText(this.context.getString(R.string.button_save));
        waitForSavedSet(SAVED_SET_NAME, setOf(SOURCE_A_URL, SOURCE_B_URL));
        assertEquals(SAVED_SET_NAME, FilterSetStore.getActiveProfile(this.context));

        applySelectionDirectly(setOf(SOURCE_C_URL));
        waitForSourceEnabled(SOURCE_A_ID, false);
        waitForSourceEnabled(SOURCE_B_ID, false);
        waitForSourceEnabled(SOURCE_C_ID, true);

        clickToolbarAction(homeActivity, R.id.action_hosts_apply_filter_set);
        clickAccessibilityText(SAVED_SET_NAME);
        assertAccessibilityText(
                this.context.getString(R.string.filter_set_apply_preview_title, SAVED_SET_NAME));
        assertAccessibilityText(this.context.getString(
                R.string.filter_set_apply_preview_message, 2, 1, 0));
        assertAccessibilityText(
                this.context.getString(R.string.filter_set_apply_preview_disable_warning));

        clickAccessibilityText(this.context.getString(R.string.checkbox_list_context_apply));
        waitForSourceEnabled(SOURCE_A_ID, true);
        waitForSourceEnabled(SOURCE_B_ID, true);
        waitForSourceEnabled(SOURCE_C_ID, false);
        waitForListRowsEnabled(SOURCE_A_ID, true);
        waitForListRowsEnabled(SOURCE_B_ID, true);
        waitForListRowsEnabled(SOURCE_C_ID, false);
        assertEquals(SAVED_SET_NAME, FilterSetStore.getActiveProfile(this.context));
    }

    @Test(timeout = 120_000)
    public void scheduleSavedFilterSetDailyPersistsScheduleAndEnqueuesWorker() throws Exception {
        FilterSetStore.saveSet(this.context, SAVED_SET_NAME, setOf(SOURCE_A_URL, SOURCE_B_URL));

        ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class);
        navigateToSources(scenario);
        HomeActivity homeActivity = waitForHomeActivity();
        waitForSourceRowsLoaded();

        clickToolbarAction(homeActivity, R.id.action_hosts_schedule_filter_set);
        clickAccessibilityText(SAVED_SET_NAME);
        clickAccessibilityText(this.context.getString(R.string.filter_set_schedule_daily));
        clickAccessibilityText(this.context.getString(android.R.string.ok));

        waitForSchedule(SAVED_SET_NAME, FilterSetStore.SCHEDULE_DAILY);
        assertEquals(1, activeWork(this.context, FilterSetUpdateService.WORK_NAME).size());
    }

    @Test(timeout = 120_000)
    public void manageFilterSetsRenameValidationPersistsThenDeleteRemovesSet()
            throws Exception {
        Set<String> savedUrls = setOf(SOURCE_A_URL, SOURCE_B_URL);
        FilterSetStore.savePresetProfile(this.context,
                FilterSetStore.PROFILE_SAFE, setOf(SOURCE_C_URL));
        FilterSetStore.saveSet(this.context, SAVED_SET_NAME, savedUrls);
        FilterSetStore.setSchedule(this.context,
                SAVED_SET_NAME, FilterSetStore.SCHEDULE_WEEKLY, 2, 4, 30);
        FilterSetStore.setActiveProfile(this.context, SAVED_SET_NAME);

        ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class);
        navigateToSources(scenario);
        HomeActivity homeActivity = waitForHomeActivity();
        waitForSourceRowsLoaded();

        clickToolbarAction(homeActivity, R.id.action_hosts_manage_filter_sets);
        assertAccessibilityText(this.context.getString(R.string.filter_set_manage_title));
        assertAccessibilityText(SAVED_SET_NAME);
        assertAccessibilityTextNotVisible(FilterSetStore.PROFILE_SAFE);

        clickAccessibilityText(SAVED_SET_NAME);
        assertAccessibilityText(
                this.context.getString(R.string.filter_set_manage_actions_title, SAVED_SET_NAME));
        clickAccessibilityText(this.context.getString(R.string.filter_set_rename));
        assertAccessibilityText(this.context.getString(R.string.filter_set_rename_title));

        setEditableAccessibilityText(FilterSetStore.PROFILE_SAFE);
        clickAccessibilityText(this.context.getString(R.string.filter_set_rename));
        assertAccessibilityText(this.context.getString(R.string.filter_set_name_reserved));
        assertTrue(FilterSetStore.hasSet(this.context, SAVED_SET_NAME));
        assertFalse(FilterSetStore.hasSet(this.context, RENAMED_SET_NAME));

        setEditableAccessibilityText(RENAMED_SET_NAME);
        clickAccessibilityText(this.context.getString(R.string.filter_set_rename));
        waitForRenamedSet(SAVED_SET_NAME, RENAMED_SET_NAME, savedUrls);
        assertEquals(RENAMED_SET_NAME, FilterSetStore.getActiveProfile(this.context));
        assertEquals(FilterSetStore.SCHEDULE_WEEKLY,
                FilterSetStore.getSchedule(this.context, RENAMED_SET_NAME));

        clickToolbarAction(homeActivity, R.id.action_hosts_manage_filter_sets);
        assertAccessibilityText(this.context.getString(R.string.filter_set_manage_title));
        assertAccessibilityText(RENAMED_SET_NAME);
        assertAccessibilityTextNotVisible(SAVED_SET_NAME);

        clickAccessibilityText(RENAMED_SET_NAME);
        clickAccessibilityText(this.context.getString(R.string.filter_set_delete));
        assertAccessibilityText(this.context.getString(R.string.filter_set_delete_title));
        assertAccessibilityText(
                this.context.getString(R.string.filter_set_delete_message, RENAMED_SET_NAME));

        clickAccessibilityText(this.context.getString(R.string.filter_set_delete));
        waitForDeletedSet(RENAMED_SET_NAME);
        assertEquals(FilterSetStore.PROFILE_CUSTOM, FilterSetStore.getActiveProfile(this.context));
        assertTrue(FilterSetStore.hasSet(this.context, FilterSetStore.PROFILE_SAFE));

        clickToolbarAction(homeActivity, R.id.action_hosts_manage_filter_sets);
        assertAccessibilityText(this.context.getString(R.string.filter_set_manage_none));
    }

    private static void navigateToSources(ActivityScenario<HomeActivity> scenario) {
        scenario.onActivity(activity -> activity.navigateTo(R.id.nav_sources));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void clickToolbarAction(Activity activity, int menuItemId) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MaterialToolbar toolbar = activity.findViewById(R.id.hosts_sources_toolbar);
            assertNotNull("Expected Sources toolbar.", toolbar);
            assertTrue("Expected Sources toolbar action to be handled.",
                    toolbar.getMenu().performIdentifierAction(menuItemId, 0));
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

    private void waitForSourceRowsLoaded() {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            Optional<HostsSource> source = AppDatabase.getInstance(this.context)
                    .hostsSourceDao()
                    .getByUrl(SOURCE_A_URL);
            if (source.isPresent()) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Seeded source rows were not available to the Sources screen.");
    }

    private void waitForSavedSet(String name, Set<String> expectedUrls) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (FilterSetStore.hasSet(this.context, name)
                    && FilterSetStore.getSetUrls(this.context, name).equals(expectedUrls)) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Saved filter set did not contain expected URLs.");
    }

    private void waitForRenamedSet(String oldName, String newName, Set<String> expectedUrls) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (!FilterSetStore.hasSet(this.context, oldName)
                    && FilterSetStore.hasSet(this.context, newName)
                    && FilterSetStore.getSetUrls(this.context, newName).equals(expectedUrls)) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Filter set rename did not persist expected URLs.");
    }

    private void waitForDeletedSet(String name) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (!FilterSetStore.hasSet(this.context, name)) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Filter set was not deleted: " + name);
    }

    private void waitForSchedule(String name, int expectedSchedule) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (FilterSetStore.getSchedule(this.context, name) == expectedSchedule) {
                return;
            }
            SystemClock.sleep(100);
        }
        assertEquals(expectedSchedule, FilterSetStore.getSchedule(this.context, name));
    }

    private void waitForSourceEnabled(int sourceId, boolean expectedEnabled) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            HostsSource source = AppDatabase.getInstance(this.context)
                    .hostsSourceDao()
                    .getById(sourceId)
                    .orElseThrow();
            if (source.isEnabled() == expectedEnabled) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Source " + sourceId
                + " did not become enabled=" + expectedEnabled);
    }

    private void waitForListRowsEnabled(int sourceId, boolean expectedEnabled) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            int mismatch = countListRowsWithEnabled(sourceId, !expectedEnabled);
            if (mismatch == 0 && countListRowsWithEnabled(sourceId, expectedEnabled) > 0) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("List rows for source " + sourceId
                + " did not become enabled=" + expectedEnabled);
    }

    private int countListRowsWithEnabled(int sourceId, boolean enabled) {
        try (android.database.Cursor cursor = AppDatabase.getInstance(this.context)
                .getOpenHelper()
                .getReadableDatabase()
                .query("SELECT COUNT(*) FROM hosts_lists WHERE source_id = ? AND enabled = ?",
                        new Object[]{sourceId, enabled ? 1 : 0})) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private void applySelectionDirectly(Set<String> enabledUrls) {
        runDatabaseWork(this.context, database ->
                database.hostsSourceDao().applySourceSelections(enabledUrls));
    }

    private static void assertAccessibilityText(String expectedText) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            try {
                if (root != null && containsText(root, expectedText)) {
                    return;
                }
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Text was not visible: " + expectedText);
    }

    private static void assertAccessibilityTextNotVisible(String unexpectedText) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .getRootInActiveWindow();
        try {
            AccessibilityNodeInfo node = root == null ? null : findText(root, unexpectedText);
            if (node != null) {
                node.recycle();
                throw new AssertionError("Text was unexpectedly visible: " + unexpectedText);
            }
        } finally {
            if (root != null) {
                root.recycle();
            }
        }
    }

    private static void clickAccessibilityText(String expectedText) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            try {
                AccessibilityNodeInfo node = root == null ? null : findText(root, expectedText);
                if (node != null) {
                    try {
                        if (clickNodeOrParent(node)) {
                            return;
                        }
                    } finally {
                        node.recycle();
                    }
                }
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Text was not clickable: " + expectedText);
    }

    private static void setEditableAccessibilityText(String value) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            try {
                AccessibilityNodeInfo node = root == null ? null : findEditable(root);
                if (node != null) {
                    try {
                        Bundle arguments = new Bundle();
                        arguments.putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                value);
                        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                                arguments)) {
                            return;
                        }
                    } finally {
                        node.recycle();
                    }
                }
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Editable dialog field was not available.");
    }

    private static boolean containsText(AccessibilityNodeInfo node, String expectedText) {
        if (nodeTextContains(node, expectedText)) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            try {
                if (containsText(child, expectedText)) {
                    return true;
                }
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    private static AccessibilityNodeInfo findText(
            AccessibilityNodeInfo node,
            String expectedText) {
        if (nodeTextMatches(node, expectedText)) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            try {
                AccessibilityNodeInfo result = findText(child, expectedText);
                if (result != null) {
                    return result;
                }
            } finally {
                child.recycle();
            }
        }
        return null;
    }

    private static AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node) {
        if (node.isEditable()) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            try {
                AccessibilityNodeInfo result = findEditable(child);
                if (result != null) {
                    return result;
                }
            } finally {
                child.recycle();
            }
        }
        return null;
    }

    private static boolean nodeTextMatches(AccessibilityNodeInfo node, String expectedText) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        return (text != null && expectedText.contentEquals(text))
                || (description != null && expectedText.contentEquals(description));
    }

    private static boolean nodeTextContains(AccessibilityNodeInfo node, String expectedText) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        return (text != null && text.toString().contains(expectedText))
                || (description != null && description.toString().contains(expectedText));
    }

    private static boolean clickNodeOrParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
        try {
            while (current != null) {
                if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        || (current.isClickable()
                        && current.performAction(AccessibilityNodeInfo.ACTION_CLICK))) {
                    return true;
                }
                if (tapNodeCenter(current)) {
                    return true;
                }
                AccessibilityNodeInfo parent = current.getParent();
                current.recycle();
                current = parent;
            }
            return false;
        } finally {
            if (current != null) {
                current.recycle();
            }
        }
    }

    private static boolean tapNodeCenter(AccessibilityNodeInfo node) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.isEmpty()) {
            return false;
        }
        long downTime = SystemClock.uptimeMillis();
        float x = bounds.centerX();
        float y = bounds.centerY();
        InstrumentationRegistry.getInstrumentation().sendPointerSync(MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0));
        InstrumentationRegistry.getInstrumentation().sendPointerSync(MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0));
        return true;
    }

    private static void seedSources(Context context) {
        runDatabaseWork(context, database -> {
            database.hostsSourceDao().insert(seedSource(
                    SOURCE_A_ID, "Filter set proof Alpha", SOURCE_A_URL, true));
            database.hostsSourceDao().insert(seedSource(
                    SOURCE_B_ID, "Filter set proof Beta", SOURCE_B_URL, true));
            database.hostsSourceDao().insert(seedSource(
                    SOURCE_C_ID, "Filter set proof Gamma", SOURCE_C_URL, false));
            database.hostsListItemDao().insert(seedItem(
                    SOURCE_A_ID, "filter-set-alpha.example", true));
            database.hostsListItemDao().insert(seedItem(
                    SOURCE_B_ID, "filter-set-beta.example", true));
            database.hostsListItemDao().insert(seedItem(
                    SOURCE_C_ID, "filter-set-gamma.example", false));
        });
    }

    private static HostsSource seedSource(int id, String label, String url, boolean enabled) {
        HostsSource source = new HostsSource();
        source.setId(id);
        source.setLabel(label);
        source.setUrl(url);
        source.setEnabled(enabled);
        source.setSize(1);
        source.setActiveRuleCount(enabled ? 1 : 0);
        source.setBlockedCount(enabled ? 1 : 0);
        source.setBlockedExactCount(enabled ? 1 : 0);
        return source;
    }

    private static HostListItem seedItem(int sourceId, String host, boolean enabled) {
        HostListItem item = new HostListItem();
        item.setHost(host);
        item.setType(BLOCKED);
        item.setKind(RuleKind.EXACT);
        item.setEnabled(enabled);
        item.setSourceId(sourceId);
        item.setGeneration(0);
        return item;
    }

    private static void resetSources(Context context) {
        runDatabaseWork(context, database -> {
            database.getOpenHelper().getWritableDatabase().execSQL("DELETE FROM hosts_lists");
            database.hostEntryDao().clear();
            database.hostEntryDao().clearRootExport();
            database.hostsSourceDao().deleteAll();
            database.getOpenHelper().getWritableDatabase().execSQL(
                    "INSERT OR REPLACE INTO hosts_meta (id, active_generation) VALUES (0, 0)");
            database.getOpenHelper().getWritableDatabase().execSQL(
                    "INSERT OR REPLACE INTO hosts_stats "
                            + "(id, blocked_count, blocked_exact_count, allowed_count, "
                            + "redirected_count, active_rule_count) VALUES (0, 0, 0, 0, 0, 0)");
        });
    }

    private static void clearFilterSets(Context context) {
        if (!context.getSharedPreferences(FILTER_SETS_PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()) {
            throw new AssertionError("Failed to clear filter set preferences.");
        }
    }

    private static void resetWorkManager(Context context) {
        try {
            WorkManager workManager = WorkManager.getInstance(context);
            workManager.cancelAllWork().getResult().get(5, TimeUnit.SECONDS);
            workManager.pruneWork().getResult().get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Failed to reset WorkManager.", exception);
        }
    }

    private static List<WorkInfo> activeWork(Context context, String workName) throws Exception {
        return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(workName)
                .get(5, TimeUnit.SECONDS)
                .stream()
                .filter(info -> !info.getState().isFinished())
                .collect(Collectors.toList());
    }

    private static void runDatabaseWork(Context context, DatabaseWork work) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> work.run(AppDatabase.getInstance(context)));
        try {
            future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Database fixture work failed.", exception);
        } finally {
            executor.shutdownNow();
        }
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

    private static void finishResumedActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                activity.finish();
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private interface DatabaseWork {
        void run(AppDatabase database);
    }
}
