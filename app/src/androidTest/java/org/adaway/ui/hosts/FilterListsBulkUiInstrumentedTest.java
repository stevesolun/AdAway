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
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;

import com.google.android.material.button.MaterialButton;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.helper.NotificationHelper;
import org.adaway.model.source.FilterListsDirectoryApi;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class FilterListsBulkUiInstrumentedTest {
    private static final int TIMEOUT_SECONDS = 10;
    private static final String PREFS = "filterlists_cache";
    private static final int RETRY_LIST_ID = 701;
    private static final int UNSUPPORTED_LIST_ID = 702;

    private Context context;
    private SharedPreferences prefs;
    private SlowDependencies dependencies;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(context, "set up FilterLists bulk UI");
        NotificationHelper.createNotificationChannels(context);
        AppDatabase.getInstance(context).hostsSourceDao().deleteAll();
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        seedCachedDirectoryAndLastRun();
        dependencies = new SlowDependencies(context, prefs);
        FilterListsSubscribeAllWorker.setDependenciesForTest(dependencies);
    }

    @After
    public void tearDown() throws Exception {
        if (context != null) {
            if (dependencies != null) {
                dependencies.directoryClient.release();
            }
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
            InstrumentedTestState.resetForPassiveRootUi(context, "tear down FilterLists bulk UI");
        }
    }

    @Test
    public void lastRunRetryShowsProgressAndCanBeCancelledFromDiscover()
            throws Exception {
        try (ActivityScenario<HomeActivity> scenario =
                     ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> activity.navigateTo(R.id.nav_discover));
            waitForViewVisibility(scenario, R.id.filterlistsLastRunActions, View.VISIBLE);
            waitForViewVisibility(scenario, R.id.filterlistsReviewLastRunButton, View.VISIBLE);
            waitForViewVisibility(scenario, R.id.filterlistsRetryLastRunButton, View.VISIBLE);
            waitForViewVisibility(scenario, R.id.filterlistsReviewUnsupportedButton, View.VISIBLE);

            scenario.onActivity(activity -> {
                MaterialButton retry = activity.findViewById(R.id.filterlistsRetryLastRunButton);
                assertNotNull(retry);
                retry.performClick();
            });

            assertTrue("Retry worker did not start resolving details",
                    dependencies.directoryClient.detailRequestStarted.await(
                            TIMEOUT_SECONDS, TimeUnit.SECONDS));
            waitForViewVisibility(scenario, R.id.filterlistsCancelBulkButton, View.VISIBLE);
            waitForViewEnabled(scenario, R.id.filterlistsCancelBulkButton, true);
            waitForTextMatching(scenario, R.id.filterlistsSubscribeAllStatus,
                    text -> text.contains("Preparing") || text.contains("/"),
                    "preparing or numeric progress");

            scenario.onActivity(activity -> {
                MaterialButton cancel = activity.findViewById(R.id.filterlistsCancelBulkButton);
                assertNotNull(cancel);
                cancel.performClick();
                assertFalse(cancel.isEnabled());
                TextView status = activity.findViewById(R.id.filterlistsSubscribeAllStatus);
                assertNotNull(status);
                assertTrue(status.getText().toString().contains("Stopping"));
            });

            dependencies.directoryClient.release();
            waitForText(scenario, R.id.filterlistsSubscribeAllStatus,
                    "FilterLists subscription cancelled");
        }

        assertFalse("Cancelled retry must not enqueue a source update",
                dependencies.updateEnqueued.get());
        assertFalse("Cancelled retry must not insert the retried source",
                AppDatabase.getInstance(context)
                        .hostsSourceDao()
                        .getByUrl("https://retry.test/hosts.txt")
                        .isPresent());
    }

    private void seedCachedDirectoryAndLastRun() {
        long now = System.currentTimeMillis();
        String ledger = "SKIPPED_NO_URL\t" + RETRY_LIST_ID + "\tSlow retry\t\n"
                + "SKIPPED_UNSUPPORTED\t" + UNSUPPORTED_LIST_ID + "\tBrowser rules\t";
        SharedPreferences.Editor editor = prefs.edit()
                .clear()
                .putString("listsJson", "["
                        + "{\"id\":" + RETRY_LIST_ID + ",\"name\":\"Slow retry\","
                        + "\"description\":\"Retryable DNS-safe list\","
                        + "\"syntaxIds\":[1],\"tagIds\":[7],\"languageIds\":[2]},"
                        + "{\"id\":" + UNSUPPORTED_LIST_ID + ",\"name\":\"Browser rules\","
                        + "\"description\":\"Unsupported browser rules\","
                        + "\"syntaxIds\":[3],\"tagIds\":[7],\"languageIds\":[2]}"
                        + "]")
                .putString("syntaxesJson", "["
                        + "{\"id\":1,\"name\":\"Hosts\"},"
                        + "{\"id\":3,\"name\":\"Browser rules\"}"
                        + "]")
                .putString("tagsJson", "["
                        + "{\"id\":7,\"name\":\"Regional\",\"description\":\"Regional lists\"}"
                        + "]")
                .putString("languagesJson", "["
                        + "{\"id\":2,\"name\":\"English\",\"iso6391\":\"en\"}"
                        + "]")
                .putLong("cachedAt", now)
                .putLong("tagsCachedAt", now)
                .putString(FilterListsSubscribeAllWorker.KEY_LAST_RUN_OUTCOMES, ledger)
                .putInt(FilterListsSubscribeAllWorker.KEY_LAST_RUN_OUTCOME_COUNT, 2)
                .putInt(FilterListsSubscribeAllWorker.KEY_LAST_RUN_REVIEW_COUNT, 2)
                .putString(FilterListsSubscribeAllWorker.KEY_LAST_RUN_REVIEW_PREVIEW,
                        "No URL: Slow retry; Unsupported: Browser rules")
                .putBoolean(FilterListsSubscribeAllWorker.KEY_LAST_RUN_CANCELLED, false)
                .putLong(FilterListsSubscribeAllWorker.KEY_LAST_RUN_FINISHED_AT, now)
                .putString("listUrl_" + RETRY_LIST_ID, "");
        assertTrue(editor.commit());
    }

    private static void waitForViewVisibility(
            ActivityScenario<HomeActivity> scenario, int viewId, int expectedVisibility)
            throws Exception {
        waitForCondition("view " + viewId + " visibility " + expectedVisibility, scenario,
                activity -> {
                    View view = activity.findViewById(viewId);
                    return view != null && view.getVisibility() == expectedVisibility;
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
        waitForTextMatching(scenario, viewId, text -> text.contains(expectedText), expectedText);
    }

    private static void waitForTextMatching(ActivityScenario<HomeActivity> scenario, int viewId,
            TextCondition condition, String description) throws Exception {
        AtomicReference<String> lastText = new AtomicReference<>("");
        waitForCondition("text matching " + description, scenario, activity -> {
            TextView view = activity.findViewById(viewId);
            if (view == null || view.getText() == null) {
                lastText.set("");
                return false;
            }
            lastText.set(view.getText().toString());
            return condition.matches(lastText.get());
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

    private interface UiCondition {
        boolean matches(HomeActivity activity);
    }

    private interface TextCondition {
        boolean matches(String text);
    }

    private static final class SlowDependencies
            implements FilterListsSubscribeAllWorker.Dependencies {
        final SlowDirectoryClient directoryClient = new SlowDirectoryClient();
        final AtomicBoolean updateEnqueued = new AtomicBoolean(false);
        private final Context context;
        private final SharedPreferences prefs;

        SlowDependencies(Context context, SharedPreferences prefs) {
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
            updateEnqueued.set(true);
        }
    }

    private static final class SlowDirectoryClient
            implements FilterListsSubscribeAllWorker.DirectoryClient {
        final CountDownLatch detailRequestStarted = new CountDownLatch(1);
        private final CountDownLatch releaseDetailRequest = new CountDownLatch(1);

        @Override
        public List<FilterListsDirectoryApi.ListSummary> getLists() {
            List<FilterListsDirectoryApi.ListSummary> summaries = new ArrayList<>();
            summaries.add(new FilterListsDirectoryApi.ListSummary(RETRY_LIST_ID, "Slow retry",
                    "Retryable DNS-safe list", new int[]{1}, new int[]{7}, new int[]{2}));
            summaries.add(new FilterListsDirectoryApi.ListSummary(UNSUPPORTED_LIST_ID,
                    "Browser rules", "Unsupported browser rules", new int[]{3}, new int[]{7},
                    new int[]{2}));
            return summaries;
        }

        @Override
        public FilterListsDirectoryApi.ListDetails getListDetails(int id) throws IOException {
            detailRequestStarted.countDown();
            try {
                releaseDetailRequest.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while resolving " + id, exception);
            }
            return new FilterListsDirectoryApi.ListDetails(id, "Slow retry",
                    "Retryable DNS-safe list", new int[]{1},
                    Collections.singletonList(new FilterListsDirectoryApi.ViewUrl(
                            0, 10, "https://retry.test/hosts.txt")));
        }

        void release() {
            releaseDetailRequest.countDown();
        }
    }
}
