package org.adaway.ui.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.RuleKind;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class SourceEditAddEditInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;
    private static final String ADD_LABEL = "Custom add proof source";
    private static final String ADD_URL = "https://example.com/adaway-add-proof.txt";
    private static final String EDIT_LABEL = "Custom edited proof source";
    private static final String EDIT_URL = "https://example.com/adaway-edited-proof.txt";
    private static final String INVALID_URL = "http://example.com/not-secure.txt";
    private static final int DELETE_SOURCE_ID = 2210;
    private static final String DELETE_LABEL = "Custom delete proof source";
    private static final String DELETE_URL = "https://example.com/adaway-delete-proof.txt";

    private Context context;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up source add edit");
        AppDatabase.getInstance(this.context).getOpenHelper().getWritableDatabase();
        waitForDiskIoIdle();
        resetSources(this.context);
    }

    @After
    public void tearDown() {
        finishResumedActivities();
        if (this.context != null) {
            resetSources(this.context);
            InstrumentedTestState.resetForPassiveRootUi(this.context, "tear down source add edit");
        }
    }

    @Test(timeout = 120_000)
    public void customSourceAddRejectsInvalidUrlThenEditPersistsMetadata() throws Exception {
        ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class);
        navigateToSources(scenario);
        HomeActivity homeActivity = waitForHomeActivity();

        clickViewById(homeActivity, R.id.hosts_sources_add);
        clickAccessibilityText(this.context.getString(R.string.filter_add_custom));

        SourceEditActivity addActivity = waitForActivity(SourceEditActivity.class);
        saveSource(addActivity, ADD_LABEL, INVALID_URL);
        waitForSourceMissing(INVALID_URL);
        assertFieldError(addActivity, R.id.location_edit_text,
                this.context.getString(R.string.source_edit_location_invalid));

        saveSource(addActivity, ADD_LABEL, ADD_URL);
        HostsSource added = waitForSource(ADD_URL);
        assertEquals(ADD_LABEL, added.getLabel());
        assertTrue(added.isEnabled());
        assertFalse(added.isAllowEnabled());
        assertFalse(added.isRedirectEnabled());

        homeActivity = waitForHomeActivity();
        waitForSourceCard(homeActivity, ADD_LABEL);
        clickSourceCard(homeActivity, ADD_LABEL);

        SourceEditActivity editActivity = waitForActivity(SourceEditActivity.class);
        assertFieldText(editActivity, R.id.labelEditText, ADD_LABEL);
        assertFieldText(editActivity, R.id.location_edit_text, ADD_URL);
        clickViewById(editActivity, R.id.allow_format_button);

        saveSource(editActivity, EDIT_LABEL, EDIT_URL);
        HostsSource edited = waitForSource(EDIT_URL);
        assertEquals(EDIT_LABEL, edited.getLabel());
        assertTrue(edited.isEnabled());
        assertTrue(edited.isAllowEnabled());
        assertFalse(edited.isRedirectEnabled());
        waitForSourceMissing(ADD_URL);
    }

    @Test(timeout = 120_000)
    public void deleteSourceRequiresConfirmationAndRemovesDownloadedRules() throws Exception {
        seedDownloadedSource(this.context);

        ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class);
        navigateToSources(scenario);
        HomeActivity homeActivity = waitForHomeActivity();
        waitForSourceCard(homeActivity, DELETE_LABEL);
        clickSourceCard(homeActivity, DELETE_LABEL);

        SourceEditActivity editActivity = waitForActivity(SourceEditActivity.class);
        assertFieldText(editActivity, R.id.labelEditText, DELETE_LABEL);
        assertFieldText(editActivity, R.id.location_edit_text, DELETE_URL);
        assertEquals(1, countListRowsForSource(DELETE_SOURCE_ID));

        clickAccessibilityText(this.context.getString(R.string.checkbox_list_context_delete));
        assertAccessibilityText(this.context.getString(R.string.source_edit_delete_confirm_title));
        assertAccessibilityText(this.context.getString(
                R.string.source_edit_delete_confirm_message, DELETE_LABEL));
        clickAccessibilityText(this.context.getString(R.string.button_cancel));
        waitForSource(DELETE_URL);
        assertEquals(1, countListRowsForSource(DELETE_SOURCE_ID));

        clickAccessibilityText(this.context.getString(R.string.checkbox_list_context_delete));
        clickAccessibilityText(this.context.getString(
                R.string.source_edit_delete_confirm_action));

        waitForSourceMissing(DELETE_URL);
        waitForListRowsMissing(DELETE_SOURCE_ID);
        homeActivity = waitForHomeActivity();
        waitForSourceCardMissing(homeActivity, DELETE_LABEL);
    }

    private static void navigateToSources(ActivityScenario<HomeActivity> scenario) {
        scenario.onActivity(activity -> activity.navigateTo(R.id.nav_sources));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
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

    private static void saveSource(
            SourceEditActivity activity,
            String label,
            String url) throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            TextInputEditText labelField = activity.findViewById(R.id.labelEditText);
            TextInputEditText locationField = activity.findViewById(R.id.location_edit_text);
            assertNotNull(labelField);
            assertNotNull(locationField);
            labelField.setText(label);
            locationField.setText(url);
        });
        clickAccessibilityText(ApplicationProvider.getApplicationContext()
                .getString(R.string.checkbox_list_context_apply));
    }

    private static void assertFieldText(Activity activity, int viewId, String expectedText) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            TextView field = activity.findViewById(viewId);
            assertNotNull(field);
            assertEquals(expectedText, field.getText().toString());
        });
    }

    private static void assertFieldError(Activity activity, int viewId, String expectedError) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            TextInputEditText field = activity.findViewById(viewId);
            assertNotNull(field);
            assertEquals(expectedError, String.valueOf(field.getError()));
        });
    }

    private static void clickViewById(Activity activity, int viewId) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<View> viewRef = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                View view = findViewById(activity.getWindow().getDecorView(), viewId);
                if (view != null && view.isShown()) {
                    viewRef.set(view);
                }
            });
            View view = viewRef.get();
            if (view != null) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(view::performClick);
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out waiting to click view " + viewId);
    }

    private static void waitForSourceCard(Activity activity, String label) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<MaterialCardView> cardRef = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    cardRef.set(findSourceCardByLabel(
                            activity.getWindow().getDecorView(), label)));
            if (cardRef.get() != null) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out waiting for source card: " + label);
    }

    private static void waitForSourceCardMissing(Activity activity, String label) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<MaterialCardView> cardRef = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    cardRef.set(findSourceCardByLabel(
                            activity.getWindow().getDecorView(), label)));
            if (cardRef.get() == null) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Source card remained visible: " + label);
    }

    private static void clickSourceCard(Activity activity, String label) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MaterialCardView card = findSourceCardByLabel(
                    activity.getWindow().getDecorView(), label);
            assertNotNull("Expected source card for " + label, card);
            assertTrue("Expected source card click to be handled.", card.performClick());
        });
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

    private static void assertAccessibilityText(String expectedText) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            try {
                AccessibilityNodeInfo node = root == null ? null : findText(root, expectedText);
                if (node != null) {
                    node.recycle();
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

    private static boolean nodeTextMatches(AccessibilityNodeInfo node, String expectedText) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        return (text != null && expectedText.contentEquals(text))
                || (description != null && expectedText.contentEquals(description));
    }

    private static boolean clickNodeOrParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
        try {
            while (current != null) {
                if (current.isClickable()
                        && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
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

    private static View findViewById(View view, int viewId) {
        if (view.getId() == viewId) {
            return view;
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View match = findViewById(group.getChildAt(i), viewId);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static MaterialCardView findSourceCardByLabel(View view, String label) {
        if (view instanceof TextView
                && view.getId() == R.id.sourceLabel
                && view.isShown()
                && label.contentEquals(((TextView) view).getText())) {
            ViewParent parent = view.getParent();
            while (parent instanceof View) {
                View parentView = (View) parent;
                if (parentView.getId() == R.id.sourceCard
                        && parentView instanceof MaterialCardView) {
                    return (MaterialCardView) parentView;
                }
                parent = parentView.getParent();
            }
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            MaterialCardView match = findSourceCardByLabel(group.getChildAt(i), label);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static SourceEditActivity waitForActivity(
            Class<SourceEditActivity> activityClass) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<Activity> resumed = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (activityClass.isInstance(activity)) {
                        resumed.set(activity);
                        return;
                    }
                }
            });
            Activity activity = resumed.get();
            if (activityClass.isInstance(activity)) {
                return activityClass.cast(activity);
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out waiting for " + activityClass.getSimpleName());
    }

    private static HostsSource waitForSource(String url) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            Optional<HostsSource> source = AppDatabase.getInstance(
                    ApplicationProvider.getApplicationContext())
                    .hostsSourceDao()
                    .getByUrl(url);
            if (source.isPresent()) {
                return source.get();
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out waiting for source: " + url);
    }

    private static void waitForSourceMissing(String url) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            Optional<HostsSource> source = AppDatabase.getInstance(
                    ApplicationProvider.getApplicationContext())
                    .hostsSourceDao()
                    .getByUrl(url);
            if (source.isEmpty()) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Source remained present: " + url);
    }

    private static void waitForListRowsMissing(int sourceId) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (countListRowsForSource(sourceId) == 0) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Downloaded rules remained for source: " + sourceId);
    }

    private static int countListRowsForSource(int sourceId) {
        try (Cursor cursor = AppDatabase.getInstance(ApplicationProvider.getApplicationContext())
                .getOpenHelper()
                .getReadableDatabase()
                .query("SELECT COUNT(*) FROM hosts_lists WHERE source_id = ?",
                        new Object[]{sourceId})) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private static void seedDownloadedSource(Context context) {
        runDatabaseWork(context, database -> {
            HostsSource source = new HostsSource();
            source.setId(DELETE_SOURCE_ID);
            source.setLabel(DELETE_LABEL);
            source.setUrl(DELETE_URL);
            source.setEnabled(true);
            database.hostsSourceDao().insert(source);

            HostListItem item = new HostListItem();
            item.setHost("ads.delete-proof.example");
            item.setType(ListType.BLOCKED);
            item.setKind(RuleKind.EXACT);
            item.setEnabled(true);
            item.setSourceId(DELETE_SOURCE_ID);
            item.setGeneration(0);
            database.hostsListItemDao().insert(item);
        });
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
    }

    private interface DatabaseWork {
        void run(AppDatabase database);
    }
}
