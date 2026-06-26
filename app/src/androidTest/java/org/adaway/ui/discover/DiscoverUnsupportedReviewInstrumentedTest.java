package org.adaway.ui.discover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.model.source.SourceModel;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.adaway.ui.source.SourceEditActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@RunWith(AndroidJUnit4.class)
public class DiscoverUnsupportedReviewInstrumentedTest {
    private static final int TIMEOUT_SECONDS = 10;
    private static final String PREFS = "filterlists_cache";
    private static final int UNSUPPORTED_ID = 801;
    private static final String UNSUPPORTED_NAME = "Browser Rules Manual";
    private static final String DETAILS_NAME = "Manual Browser Rules";
    private static final String DETAILS_URL = "https://manual.test/browser-rules.txt";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private Context context;
    private SourceModel sourceModel;
    private OkHttpClient previousHttpClient;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(context, "set up unsupported review");
        AppDatabase.getInstance(context).hostsSourceDao().deleteAll();
        seedFilterListsCache(context);
        sourceModel = ((AdAwayApplication) context).getSourceModel();
        previousHttpClient = getCachedHttpClient(sourceModel);
        injectHttpClient(sourceModel, buildFilterListsClient());
    }

    @After
    public void tearDown() throws Exception {
        finishResumedActivities();
        if (sourceModel != null) {
            injectHttpClient(sourceModel, previousHttpClient);
        }
        if (context != null) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit();
            AppDatabase.getInstance(context).hostsSourceDao().deleteAll();
            InstrumentedTestState.resetForPassiveRootUi(context, "tear down unsupported review");
        }
    }

    @Test
    public void unsupportedRowReviewShowsDisclosureAndManualAddPrefillsSourceEditor()
            throws Exception {
        try (ActivityScenario<HomeActivity> scenario =
                     ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> activity.navigateTo(R.id.nav_discover));
            waitForScenarioCondition("Discover FilterLists view", scenario,
                    activity -> activity.findViewById(R.id.filterlistsRecyclerView) != null);
            reloadSeededDirectory(scenario);
            scenario.onActivity(activity -> {
                EditText search = activity.findViewById(R.id.filterlistsSearchEditText);
                assertNotNull(search);
                search.setText(UNSUPPORTED_NAME);
            });
            waitForRowText(scenario, R.id.filterlistsItemName, UNSUPPORTED_NAME);
            waitForRowText(scenario, R.id.filterlistsItemStatus,
                    "Manual review: browser semantics skipped");

            clickRowByName(scenario, UNSUPPORTED_NAME);

            waitForAccessibilityText("AdAway does not subscribe it automatically");
            waitForAccessibilityText("Domain extraction only");
            waitForAccessibilityText("Exceptions, redirects, path/options rules");
            waitForAccessibilityText(DETAILS_URL);
            clickAccessibilityText("Add manually");

            SourceEditActivity editActivity = waitForActivity(SourceEditActivity.class);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                TextView label = editActivity.findViewById(R.id.labelEditText);
                TextView location = editActivity.findViewById(R.id.location_edit_text);
                assertNotNull(label);
                assertNotNull(location);
                assertEquals(DETAILS_NAME, label.getText().toString());
                assertEquals(DETAILS_URL, location.getText().toString());
                assertEquals(UNSUPPORTED_ID, editActivity.getIntent()
                        .getIntExtra(SourceEditActivity.EXTRA_FILTER_LIST_ID, 0));
                assertEquals(UNSUPPORTED_NAME, editActivity.getIntent()
                        .getStringExtra(SourceEditActivity.EXTRA_FILTER_LIST_NAME));
                assertEquals(DETAILS_URL, editActivity.getIntent()
                        .getStringExtra(SourceEditActivity.EXTRA_FILTER_LIST_SELECTED_URL));
            });
        }
    }

    private static void seedFilterListsCache(Context context) {
        long now = System.currentTimeMillis();
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .putString("listsJson", listsJson())
                .putString("syntaxesJson", syntaxesJson())
                .putString("tagsJson", "[]")
                .putString("languagesJson", "[]")
                .putLong("cachedAt", now)
                .putLong("tagsCachedAt", now);
        assertTrue(editor.commit());
    }

    private static void reloadSeededDirectory(ActivityScenario<HomeActivity> scenario)
            throws Exception {
        seedFilterListsCache(ApplicationProvider.getApplicationContext());
        scenario.onActivity(activity -> {
            View retry = activity.findViewById(R.id.filterlistsStateRetryButton);
            assertNotNull(retry);
            retry.performClick();
        });
    }

    private static OkHttpClient buildFilterListsClient() {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    String path = request.url().encodedPath();
                    String body;
                    if ("/lists".equals(path)) {
                        body = listsJson();
                    } else if ("/syntaxes".equals(path)) {
                        body = syntaxesJson();
                    } else if ("/tags".equals(path) || "/languages".equals(path)) {
                        body = "[]";
                    } else if (("/lists/" + UNSUPPORTED_ID).equals(path)) {
                        body = detailsJson();
                    } else {
                        return jsonResponse(request, 404, "{}");
                    }
                    return jsonResponse(request, 200, body);
                })
                .build();
    }

    private static Response jsonResponse(Request request, int code, String body) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(code == 200 ? "OK" : "Not Found")
                .body(ResponseBody.create(body, JSON))
                .build();
    }

    private static String listsJson() {
        return "["
                + "{\"id\":" + UNSUPPORTED_ID + ",\"name\":\"" + UNSUPPORTED_NAME + "\","
                + "\"description\":\"Browser-only rules that need review\","
                + "\"syntaxIds\":[3],\"tagIds\":[],\"languageIds\":[]}"
                + "]";
    }

    private static String syntaxesJson() {
        return "[{\"id\":3,\"name\":\"Browser rules\"}]";
    }

    private static String detailsJson() {
        return "{"
                + "\"id\":" + UNSUPPORTED_ID + ","
                + "\"name\":\"" + DETAILS_NAME + "\","
                + "\"description\":\"Resolved unsupported list\","
                + "\"syntaxIds\":[3],"
                + "\"viewUrls\":[{\"segmentNumber\":0,\"primariness\":10,"
                + "\"url\":\"" + DETAILS_URL + "\"}]"
                + "}";
    }

    private static void waitForRowText(ActivityScenario<HomeActivity> scenario, int viewId,
            String expectedText) throws Exception {
        waitForScenarioCondition("row text " + expectedText, scenario, activity -> {
            TextView view = activity.findViewById(viewId);
            return view != null && view.getText() != null
                    && view.getText().toString().contains(expectedText);
        });
    }

    private static void clickRowByName(ActivityScenario<HomeActivity> scenario, String rowName)
            throws Exception {
        waitForScenarioCondition("click row " + rowName, scenario, activity -> {
            RecyclerView recycler = activity.findViewById(R.id.filterlistsRecyclerView);
            if (recycler == null) {
                return false;
            }
            for (int i = 0; i < recycler.getChildCount(); i++) {
                View child = recycler.getChildAt(i);
                TextView name = child.findViewById(R.id.filterlistsItemName);
                if (name != null && name.getText() != null
                        && name.getText().toString().contains(rowName)) {
                    return child.performClick();
                }
            }
            return false;
        });
    }

    private static void waitForScenarioCondition(String description,
            ActivityScenario<HomeActivity> scenario, ScenarioCondition condition) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
        while (SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AtomicInteger matched = new AtomicInteger(0);
            scenario.onActivity(activity -> {
                if (condition.matches(activity)) {
                    matched.set(1);
                }
            });
            if (matched.get() == 1) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    private static void waitForAccessibilityText(String expectedText) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
        while (SystemClock.elapsedRealtime() < deadline) {
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
        throw new AssertionError("Timed out waiting for accessibility text " + expectedText);
    }

    private static void clickAccessibilityText(String expectedText) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
        while (SystemClock.elapsedRealtime() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            try {
                AccessibilityNodeInfo node = root == null ? null : findText(root, expectedText);
                if (node != null && clickNodeOrParent(node)) {
                    return;
                }
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out clicking accessibility text " + expectedText);
    }

    private static boolean containsText(AccessibilityNodeInfo node, String expectedText) {
        if (nodeTextMatches(node, expectedText)) {
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

    private static AccessibilityNodeInfo findText(AccessibilityNodeInfo node, String expectedText) {
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
        String expected = expectedText.toLowerCase();
        return (text != null && text.toString().toLowerCase().contains(expected))
                || (description != null
                && description.toString().toLowerCase().contains(expected));
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

    private static SourceEditActivity waitForActivity(Class<SourceEditActivity> activityClass)
            throws Exception {
        long deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
        while (SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
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

    private static void finishResumedActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                activity.finish();
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static OkHttpClient getCachedHttpClient(SourceModel sourceModel) throws Exception {
        Field field = SourceModel.class.getDeclaredField("cachedHttpClient");
        field.setAccessible(true);
        return (OkHttpClient) field.get(sourceModel);
    }

    private static void injectHttpClient(SourceModel sourceModel, OkHttpClient client)
            throws Exception {
        Field field = SourceModel.class.getDeclaredField("cachedHttpClient");
        field.setAccessible(true);
        field.set(sourceModel, client);
    }

    private interface ScenarioCondition {
        boolean matches(HomeActivity activity);
    }
}
