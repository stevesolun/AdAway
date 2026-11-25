package org.adaway.ui.discover;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.model.source.SourceModel;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.adaway.ui.hosts.FilterListsSubscribeAllWorker;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Recovery actions must not derive their scope from the capped display history. */
@RunWith(AndroidJUnit4.class)
public class DiscoverRecoveryInstrumentedTest {
    private Context context;
    private SharedPreferences prefs;
    private SourceModel sourceModel;
    private OkHttpClient previousClient;
    private final AtomicInteger requestedId = new AtomicInteger();

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(context, "set up Discover recovery");
        prefs = context.getSharedPreferences("filterlists_cache", Context.MODE_PRIVATE);
        sourceModel = ((AdAwayApplication) context).getSourceModel();
        Field client = SourceModel.class.getDeclaredField("cachedHttpClient");
        client.setAccessible(true);
        previousClient = (OkHttpClient) client.get(sourceModel);
        client.set(sourceModel, new OkHttpClient.Builder().addInterceptor(chain -> {
            String path = chain.request().url().encodedPath();
            String body = "[]";
            if (path.startsWith("/lists/")) {
                int id = Integer.parseInt(path.substring("/lists/".length()));
                requestedId.set(id);
                body = "{\"id\":" + id + ",\"name\":\"Browser " + id
                        + "\",\"syntaxIds\":[3],\"viewUrls\":[]}";
            }
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(ResponseBody.create(body,
                            MediaType.get("application/json; charset=utf-8"))).build();
        }).build());
    }

    @After
    public void tearDown() throws Exception {
        Field client = SourceModel.class.getDeclaredField("cachedHttpClient");
        client.setAccessible(true);
        client.set(sourceModel, previousClient);
        prefs.edit().clear().commit();
        InstrumentedTestState.resetForPassiveRootUi(context, "tear down Discover recovery");
    }

    @Test
    public void newRecoverySetsOverrideTruncatedOrStaleLedger() {
        Set<String> failures = new HashSet<>();
        Set<String> unsupported = new HashSet<>();
        for (int id = 1; id <= 6000; id++) {
            failures.add(Integer.toString(id));
            unsupported.add(Integer.toString(id + 10000));
        }
        assertTrue(prefs.edit().clear()
                .putString(FilterListsSubscribeAllWorker.KEY_LAST_RUN_OUTCOMES,
                        "SUBSCRIBED\t1\tOld success\t")
                .putStringSet(FilterListsSubscribeAllWorker.KEY_LAST_RUN_NO_URL_IDS, failures)
                .putStringSet(FilterListsSubscribeAllWorker.KEY_LAST_RUN_UNSUPPORTED_IDS, unsupported)
                .commit());
        int[] retryIds = DiscoverFilterListsFragment.readLastRunNoUrlIds(prefs);
        int[] reviewIds = DiscoverFilterListsFragment.readLastRunUnsupportedIds(prefs);
        assertEquals(6000, retryIds.length);
        assertEquals(6000, retryIds[5999]);
        assertEquals(6000, reviewIds.length);
        assertEquals(16000, reviewIds[5999]);
        assertTrue(prefs.edit()
                .putStringSet(FilterListsSubscribeAllWorker.KEY_LAST_RUN_NO_URL_IDS, new HashSet<>())
                .putStringSet(FilterListsSubscribeAllWorker.KEY_LAST_RUN_UNSUPPORTED_IDS, new HashSet<>())
                .putString(FilterListsSubscribeAllWorker.KEY_LAST_RUN_OUTCOMES,
                        "SKIPPED_NO_URL\t99\tOld failure\t\nSKIPPED_UNSUPPORTED\t100\tOld\t")
                .commit());
        assertEquals(0, DiscoverFilterListsFragment.readLastRunNoUrlIds(prefs).length);
        assertEquals(0, DiscoverFilterListsFragment.readLastRunUnsupportedIds(prefs).length);
    }

    @Test
    public void legacyLedgerStillOffersRecoveryUntilNextRun() {
        assertTrue(prefs.edit().clear()
                .putString(FilterListsSubscribeAllWorker.KEY_LAST_RUN_OUTCOMES,
                        "SKIPPED_NO_URL\t51\tMissing\t\nSKIPPED_UNSUPPORTED\t52\tBrowser\t")
                .commit());
        assertArrayEquals(new int[]{51}, DiscoverFilterListsFragment.readLastRunNoUrlIds(prefs));
        assertArrayEquals(new int[]{52}, DiscoverFilterListsFragment.readLastRunUnsupportedIds(prefs));
    }

    @Test
    public void reviewTextKeepsEightyRowLimitAndExplicitRemainingCount() {
        StringBuilder ledger = new StringBuilder();
        for (int id = 1; id <= 500; id++) {
            if (id > 1) ledger.append('\n');
            ledger.append("SUBSCRIBED\t").append(id).append("\tSafe ").append(id).append('\t');
        }
        String message = DiscoverFilterListsFragment.formatLastRunReviewMessage(
                ledger.toString(), 502, true);
        assertTrue(message.startsWith("Cancelled"));
        assertTrue(message.contains("Safe 80"));
        assertFalse(message.contains("Safe 81"));
        assertTrue(message.endsWith("+422 more"));
    }

    @Test
    public void unsupportedChoiceBeyondEightyCanBeScrolledToAndOpened() throws Exception {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            openRecovery(scenario);
            scenario.onActivity(activity -> activity.findViewById(
                    R.id.filterlistsReviewUnsupportedButton).performClick());
            assertTrue("Choice 121 must be reachable", findAndClickChoice("Browser 121"));
            long deadline = SystemClock.elapsedRealtime() + 10000;
            while (requestedId.get() != 121 && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(25);
            }
            assertEquals(121, requestedId.get());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        }
    }

    @Test
    public void closingUnsupportedChooserDoesNotResolveOrDiscardChoices() throws Exception {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            openRecovery(scenario);
            scenario.onActivity(activity -> activity.findViewById(
                    R.id.filterlistsReviewUnsupportedButton).performClick());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            assertEquals(0, requestedId.get());
            assertEquals(121, DiscoverFilterListsFragment.readLastRunUnsupportedIds(prefs).length);
        }
    }

    private void openRecovery(ActivityScenario<HomeActivity> scenario) throws Exception {
        JSONArray lists = new JSONArray();
        Set<String> unsupported = new HashSet<>();
        for (int id = 1; id <= 121; id++) {
            lists.put(new JSONObject().put("id", id).put("name", "Browser " + id)
                    .put("syntaxIds", new JSONArray().put(3))
                    .put("tagIds", new JSONArray()).put("languageIds", new JSONArray()));
            unsupported.add(Integer.toString(id));
        }
        long now = System.currentTimeMillis();
        assertTrue(prefs.edit().clear().putString("listsJson", lists.toString())
                .putString("syntaxesJson", "[{\"id\":3,\"name\":\"Browser\"}]")
                .putString("tagsJson", "[]").putString("languagesJson", "[]")
                .putLong("cachedAt", now).putLong("tagsCachedAt", now)
                .putString(FilterListsSubscribeAllWorker.KEY_LAST_RUN_OUTCOMES,
                        "SUBSCRIBED\t999\tCapped history\t")
                .putInt(FilterListsSubscribeAllWorker.KEY_LAST_RUN_REVIEW_COUNT, 121)
                .putStringSet(FilterListsSubscribeAllWorker.KEY_LAST_RUN_UNSUPPORTED_IDS, unsupported)
                .commit());
        scenario.onActivity(activity -> activity.getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_fragment_container, new DiscoverFilterListsFragment())
                .commitNow());
        AtomicBoolean ready = new AtomicBoolean();
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (!ready.get() && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity(activity -> {
                RecyclerView rows = activity.findViewById(R.id.filterlistsRecyclerView);
                View review = activity.findViewById(R.id.filterlistsReviewUnsupportedButton);
                ready.set(rows != null && rows.getAdapter() != null
                        && rows.getAdapter().getItemCount() == 121 && review != null
                        && review.getVisibility() == View.VISIBLE);
            });
            if (!ready.get()) SystemClock.sleep(25);
        }
        assertTrue("Recovery directory did not load", ready.get());
    }

    private static boolean findAndClickChoice(String label) {
        long deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(15);
        while (SystemClock.elapsedRealtime() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                try {
                    if (clickText(root, label)) return true;
                    scrollForward(root);
                } finally {
                    root.recycle();
                }
            }
            SystemClock.sleep(100);
        }
        return false;
    }

    private static boolean clickText(AccessibilityNodeInfo node, String label) {
        if (node.getText() != null && label.contentEquals(node.getText())) {
            AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
            while (current != null) {
                boolean clicked = current.isClickable()
                        && current.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                AccessibilityNodeInfo parent = clicked ? null : current.getParent();
                current.recycle();
                if (clicked) return true;
                current = parent;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                if (clickText(child, label)) return true;
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    private static boolean scrollForward(AccessibilityNodeInfo node) {
        if (node.isScrollable()
                && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                if (scrollForward(child)) return true;
            } finally {
                child.recycle();
            }
        }
        return false;
    }
}
