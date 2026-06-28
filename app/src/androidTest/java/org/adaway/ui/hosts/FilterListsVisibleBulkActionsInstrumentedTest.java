package org.adaway.ui.hosts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.NotificationManager;
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
import androidx.work.WorkManager;

import com.google.android.material.button.MaterialButton;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.NotificationHelper;
import org.adaway.model.source.FilterListsDirectoryApi;
import org.adaway.model.source.FilterListsSourceMetadata;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class FilterListsVisibleBulkActionsInstrumentedTest {
    private static final int TIMEOUT_SECONDS = 10;
    private static final String PREFS = "filterlists_cache";
    private static final int VISIBLE_SAFE_ID = 901;
    private static final int VISIBLE_UNSUPPORTED_ID = 902;
    private static final int HIDDEN_SAFE_ID = 903;
    private static final String SEARCH_QUERY = "visible bulk";
    private static final String VISIBLE_SAFE_NAME = "Visible Bulk Safe";
    private static final String VISIBLE_UNSUPPORTED_NAME = "Visible Bulk Browser";
    private static final String HIDDEN_SAFE_NAME = "Hidden Bulk Safe";
    private static final String VISIBLE_SAFE_URL = "https://visible-bulk.test/hosts.txt";
    private static final String HIDDEN_SAFE_URL = "https://hidden-bulk.test/hosts.txt";

    private Context context;
    private SharedPreferences prefs;
    private ImmediateDependencies dependencies;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(context, "set up FilterLists visible bulk");
        NotificationHelper.createNotificationChannels(context);
        AppDatabase.getInstance(context).hostsSourceDao().deleteAll();
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        seedCachedDirectory();
        seedHiddenSubscribedSource();
        dependencies = new ImmediateDependencies(context, prefs);
        FilterListsSubscribeAllWorker.setDependenciesForTest(dependencies);
    }

    @After
    public void tearDown() throws Exception {
        if (context != null) {
            WorkManager.getInstance(context)
                    .cancelUniqueWork(FilterListsSubscribeAllWorker.UNIQUE_WORK_NAME)
                    .getResult()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            FilterListsSubscribeAllWorker.resetDependenciesForTest();
            if (prefs != null) {
                prefs.edit().clear().commit();
            }
            AppDatabase.getInstance(context).hostsSourceDao().deleteAll();
            NotificationManager notificationManager =
                    context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.cancelAll();
            }
            InstrumentedTestState.resetForPassiveRootUi(context, "tear down FilterLists visible bulk");
        }
    }

    @Test
    public void visibleSubscribeAndRemoveOnlyAffectCurrentVisibleFilterListsScope()
            throws Exception {
        try (ActivityScenario<HomeActivity> scenario =
                     ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> activity.navigateTo(R.id.nav_discover));
            waitForCondition("Discover FilterLists view", scenario,
                    activity -> activity.findViewById(R.id.filterlistsRecyclerView) != null);
            reloadSeededDirectory(scenario);
            setSearchQuery(scenario, SEARCH_QUERY);
            waitForRecyclerRowText(scenario, R.id.filterlistsItemName, VISIBLE_SAFE_NAME);
            waitForRecyclerRowText(scenario, R.id.filterlistsItemName, VISIBLE_UNSUPPORTED_NAME);
            waitForViewEnabled(scenario, R.id.filterlistsSubscribeVisibleButton, true);
            waitForViewEnabled(scenario, R.id.filterlistsRemoveVisibleButton, false);

            clickButton(scenario, R.id.filterlistsSubscribeVisibleButton);
            waitForAccessibilityText("AdAway will add 1 DNS-safe lists from the current view");
            clickExactAccessibilityText("Subscribe");

            HostsSource subscribed = waitForSource(VISIBLE_SAFE_URL, true)
                    .orElseThrow(() -> new AssertionError("Missing visible source"));
            assertEquals(VISIBLE_SAFE_ID, subscribed.getFilterListId().intValue());
            assertEquals(VISIBLE_SAFE_NAME, subscribed.getFilterListName());
            assertEquals(VISIBLE_SAFE_URL, subscribed.getFilterListSelectedUrl());
            waitForText(scenario, R.id.filterlistsSubscribeAllStatus,
                    "Added 1 | Already 0 | No URL 0 | Unsupported 1");
            waitForRecyclerRowText(scenario, R.id.filterlistsItemStatus, "Subscribed");

            assertEquals(1, dependencies.updateEnqueued.get());
            assertEquals(Collections.singletonList(VISIBLE_SAFE_ID),
                    dependencies.directoryClient.detailRequests);
            assertFalse(AppDatabase.getInstance(context).hostsSourceDao()
                    .getByUrl("https://unsupported-bulk.test/browser.txt")
                    .isPresent());
            assertTrue(AppDatabase.getInstance(context).hostsSourceDao()
                    .getByUrl(HIDDEN_SAFE_URL)
                    .isPresent());

            clickButton(scenario, R.id.filterlistsRemoveVisibleButton);
            waitForAccessibilityText("This removes FilterLists.com sources that match the current view");
            clickExactAccessibilityText("Unsubscribe selected");

            waitForSource(VISIBLE_SAFE_URL, false);
            assertTrue("Hidden FilterLists source must survive visible remove",
                    AppDatabase.getInstance(context).hostsSourceDao()
                            .getByUrl(HIDDEN_SAFE_URL)
                            .isPresent());
            waitForViewEnabled(scenario, R.id.filterlistsSubscribeVisibleButton, true);
            waitForViewEnabled(scenario, R.id.filterlistsRemoveVisibleButton, false);
        }
    }

    private void seedCachedDirectory() {
        long now = System.currentTimeMillis();
        SharedPreferences.Editor editor = prefs.edit()
                .clear()
                .putString("listsJson", listsJson())
                .putString("syntaxesJson", "["
                        + "{\"id\":1,\"name\":\"Hosts\"},"
                        + "{\"id\":3,\"name\":\"Browser rules\"}"
                        + "]")
                .putString("tagsJson", "["
                        + "{\"id\":9,\"name\":\"Visible\",\"description\":\"Visible test lists\"},"
                        + "{\"id\":10,\"name\":\"Hidden\",\"description\":\"Hidden test lists\"}"
                        + "]")
                .putString("languagesJson", "[{\"id\":2,\"name\":\"English\",\"iso6391\":\"en\"}]")
                .putLong("cachedAt", now)
                .putLong("tagsCachedAt", now);
        assertTrue(editor.commit());
    }

    private void seedHiddenSubscribedSource() {
        HostsSource hidden = new HostsSource();
        hidden.setLabel(HIDDEN_SAFE_NAME);
        hidden.setUrl(HIDDEN_SAFE_URL);
        hidden.setEnabled(true);
        hidden.setAllowEnabled(false);
        hidden.setRedirectEnabled(false);
        FilterListsSourceMetadata.apply(hidden, HIDDEN_SAFE_ID, HIDDEN_SAFE_NAME, new int[]{1},
                new int[]{10}, new int[]{2}, HIDDEN_SAFE_URL);
        AppDatabase.getInstance(context).hostsSourceDao().insert(hidden);
    }

    private void reloadSeededDirectory(ActivityScenario<HomeActivity> scenario) throws Exception {
        seedCachedDirectory();
        scenario.onActivity(activity -> {
            View retry = activity.findViewById(R.id.filterlistsStateRetryButton);
            assertNotNull(retry);
            retry.performClick();
        });
    }

    private static String listsJson() {
        return "["
                + "{\"id\":" + VISIBLE_SAFE_ID + ",\"name\":\"" + VISIBLE_SAFE_NAME + "\","
                + "\"description\":\"Visible compatible list\",\"syntaxIds\":[1],"
                + "\"tagIds\":[9],\"languageIds\":[2]},"
                + "{\"id\":" + VISIBLE_UNSUPPORTED_ID + ",\"name\":\""
                + VISIBLE_UNSUPPORTED_NAME + "\","
                + "\"description\":\"Visible browser list\",\"syntaxIds\":[3],"
                + "\"tagIds\":[9],\"languageIds\":[2]},"
                + "{\"id\":" + HIDDEN_SAFE_ID + ",\"name\":\"" + HIDDEN_SAFE_NAME + "\","
                + "\"description\":\"Hidden compatible list\",\"syntaxIds\":[1],"
                + "\"tagIds\":[10],\"languageIds\":[2]}"
                + "]";
    }

    private static void setSearchQuery(ActivityScenario<HomeActivity> scenario, String query)
            throws Exception {
        scenario.onActivity(activity -> {
            EditText search = activity.findViewById(R.id.filterlistsSearchEditText);
            assertNotNull(search);
            search.setText(query);
        });
    }

    private Optional<HostsSource> waitForSource(String url, boolean expectedPresent)
            throws Exception {
        long deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
        Optional<HostsSource> last = Optional.empty();
        while (SystemClock.elapsedRealtime() < deadline) {
            last = AppDatabase.getInstance(context).hostsSourceDao().getByUrl(url);
            if (last.isPresent() == expectedPresent) {
                return last;
            }
            SystemClock.sleep(100);
        }
        assertEquals("Timed out waiting for source " + url,
                expectedPresent, last.isPresent());
        return last;
    }

    private static void clickButton(ActivityScenario<HomeActivity> scenario, int viewId)
            throws Exception {
        scenario.onActivity(activity -> {
            MaterialButton button = activity.findViewById(viewId);
            assertNotNull(button);
            assertTrue(button.performClick());
        });
    }

    private static void waitForViewEnabled(
            ActivityScenario<HomeActivity> scenario, int viewId, boolean expectedEnabled)
            throws Exception {
        waitForCondition("view " + viewId + " enabled " + expectedEnabled, scenario,
                activity -> {
                    View view = activity.findViewById(viewId);
                    return view != null && view.isEnabled() == expectedEnabled;
                });
    }

    private static void waitForText(
            ActivityScenario<HomeActivity> scenario, int viewId, String expectedText)
            throws Exception {
        waitForCondition("text " + expectedText, scenario, activity -> {
            TextView view = activity.findViewById(viewId);
            return view != null && view.getText() != null
                    && view.getText().toString().contains(expectedText);
        });
    }

    private static void waitForRecyclerRowText(ActivityScenario<HomeActivity> scenario, int viewId,
            String expectedText) throws Exception {
        waitForCondition("row text " + expectedText, scenario, activity -> {
            RecyclerView recycler = activity.findViewById(R.id.filterlistsRecyclerView);
            if (recycler == null) {
                return false;
            }
            for (int i = 0; i < recycler.getChildCount(); i++) {
                View child = recycler.getChildAt(i);
                TextView text = child.findViewById(viewId);
                if (text != null && text.getText() != null
                        && text.getText().toString().contains(expectedText)) {
                    return true;
                }
            }
            return false;
        });
    }

    private static void waitForCondition(String description,
            ActivityScenario<HomeActivity> scenario, UiCondition condition) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
        while (SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AtomicBoolean matched = new AtomicBoolean(false);
            scenario.onActivity(activity -> matched.set(condition.matches(activity)));
            if (matched.get()) {
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

    private static void clickExactAccessibilityText(String expectedText) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
        while (SystemClock.elapsedRealtime() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            try {
                AccessibilityNodeInfo node = root == null ? null : findExactText(root, expectedText);
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
        throw new AssertionError("Timed out clicking exact accessibility text " + expectedText);
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

    private static AccessibilityNodeInfo findExactText(
            AccessibilityNodeInfo node, String expectedText) {
        if (nodeTextEquals(node, expectedText)) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            try {
                AccessibilityNodeInfo result = findExactText(child, expectedText);
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

    private static boolean nodeTextEquals(AccessibilityNodeInfo node, String expectedText) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        String expected = expectedText.trim();
        return (text != null && text.toString().trim().equalsIgnoreCase(expected))
                || (description != null
                && description.toString().trim().equalsIgnoreCase(expected));
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

    private interface UiCondition {
        boolean matches(HomeActivity activity);
    }

    private static final class ImmediateDependencies
            implements FilterListsSubscribeAllWorker.Dependencies {
        final ImmediateDirectoryClient directoryClient = new ImmediateDirectoryClient();
        final AtomicInteger updateEnqueued = new AtomicInteger(0);
        private final Context context;
        private final SharedPreferences prefs;

        ImmediateDependencies(Context context, SharedPreferences prefs) {
            this.context = context.getApplicationContext();
            this.prefs = prefs;
        }

        @Override
        public FilterListsSubscribeAllWorker.SubscribeAllRecorder createRecorder(Context ignored) {
            return FilterListsSubscribeAllWorker.SubscribeAllRecorder.create(
                    AppDatabase.getInstance(context).hostsSourceDao());
        }

        @Override
        public FilterListsSubscribeAllWorker.DirectoryClient createDirectoryClient(
                Context ignored) {
            return directoryClient;
        }

        @Override
        public SharedPreferences getCachePreferences(Context ignored) {
            return prefs;
        }

        @Override
        public void enqueueUpdateNow(Context ignored) {
            updateEnqueued.incrementAndGet();
        }
    }

    private static final class ImmediateDirectoryClient
            implements FilterListsSubscribeAllWorker.DirectoryClient {
        final List<Integer> detailRequests = Collections.synchronizedList(new ArrayList<>());

        @Override
        public List<FilterListsDirectoryApi.ListSummary> getLists() {
            List<FilterListsDirectoryApi.ListSummary> summaries = new ArrayList<>();
            summaries.add(new FilterListsDirectoryApi.ListSummary(VISIBLE_SAFE_ID,
                    VISIBLE_SAFE_NAME, "Visible compatible list", new int[]{1}, new int[]{9},
                    new int[]{2}));
            summaries.add(new FilterListsDirectoryApi.ListSummary(VISIBLE_UNSUPPORTED_ID,
                    VISIBLE_UNSUPPORTED_NAME, "Visible browser list", new int[]{3}, new int[]{9},
                    new int[]{2}));
            summaries.add(new FilterListsDirectoryApi.ListSummary(HIDDEN_SAFE_ID,
                    HIDDEN_SAFE_NAME, "Hidden compatible list", new int[]{1}, new int[]{10},
                    new int[]{2}));
            return summaries;
        }

        @Override
        public FilterListsDirectoryApi.ListDetails getListDetails(int id) throws IOException {
            detailRequests.add(id);
            if (id == VISIBLE_SAFE_ID) {
                return details(id, VISIBLE_SAFE_NAME, VISIBLE_SAFE_URL);
            }
            if (id == HIDDEN_SAFE_ID) {
                return details(id, HIDDEN_SAFE_NAME, HIDDEN_SAFE_URL);
            }
            return new FilterListsDirectoryApi.ListDetails(id, VISIBLE_UNSUPPORTED_NAME,
                    "Visible browser list", new int[]{3}, Collections.emptyList());
        }

        private static FilterListsDirectoryApi.ListDetails details(int id, String name, String url) {
            return new FilterListsDirectoryApi.ListDetails(id, name, null, new int[]{1},
                    Collections.singletonList(
                            new FilterListsDirectoryApi.ViewUrl(0, 10, url)));
        }
    }
}
