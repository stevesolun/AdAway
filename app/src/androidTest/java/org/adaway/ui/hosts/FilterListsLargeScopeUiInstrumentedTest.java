package org.adaway.ui.hosts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Data;
import androidx.work.WorkManager;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.NotificationHelper;
import org.adaway.model.source.FilterListsDirectoryApi;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.discover.DiscoverFilterListsFragment;
import org.adaway.ui.home.HomeActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real WorkManager enqueue boundary for large recovery and checked selections. */
@RunWith(AndroidJUnit4.class)
public class FilterListsLargeScopeUiInstrumentedTest {
    private static final int COUNT = 6000;
    private Context context;
    private SharedPreferences prefs;
    private FakeDependencies dependencies;
    private AppDatabase recorderDatabase;
    private HostsSourceDao hostsSourceDao;

    @Before
    public void setUpBulkUi() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(context, "set up large recovery scopes");
        // Keep the recorder isolated without DbTest's global synchronous LiveData executor:
        // the real Activity still observes its own Room database on background threads.
        recorderDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        hostsSourceDao = recorderDatabase.hostsSourceDao();
        HostsSource user = new HostsSource();
        user.setId(HostsSource.USER_SOURCE_ID);
        user.setLabel("User");
        user.setUrl(HostsSource.USER_SOURCE_URL);
        hostsSourceDao.insert(user);
        NotificationHelper.createNotificationChannels(context);
        prefs = context.getSharedPreferences("filterlists_cache", Context.MODE_PRIVATE);
        dependencies = new FakeDependencies();
        FilterListsSubscribeAllWorker.setDependenciesForTest(dependencies);
    }

    @After
    public void tearDownBulkUi() throws Exception {
        dependencies.release.countDown();
        WorkManager.getInstance(context)
                .cancelUniqueWork(FilterListsSubscribeAllWorker.UNIQUE_WORK_NAME)
                .getResult().get(10, TimeUnit.SECONDS);
        FilterListsSubscribeAllWorker.resetDependenciesForTest();
        prefs.edit().clear().commit();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(42);
        InstrumentedTestState.resetForPassiveRootUi(context, "tear down large recovery scopes");
        recorderDatabase.close();
    }

    @Test
    public void retrySixThousandFailuresEnqueuesExactScopeAndCanBeCancelled() throws Exception {
        exerciseLargeCommand(true);
    }

    @Test
    public void sixThousandCheckedIdsEnqueueExactScopeAndCanBeCancelled() throws Exception {
        exerciseLargeCommand(false);
    }

    private void exerciseLargeCommand(boolean retry) throws Exception {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            seedDirectory();
            DiscoverFilterListsFragment fragment = new DiscoverFilterListsFragment();
            scenario.onActivity(activity -> activity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_fragment_container, fragment).commitNow());
            waitForUi(scenario, activity -> {
                RecyclerView rows = activity.findViewById(R.id.filterlistsRecyclerView);
                return rows != null && rows.getAdapter() != null
                        && rows.getAdapter().getItemCount() == COUNT;
            });
            scenario.onActivity(activity -> {
                if (retry) {
                    View button = activity.findViewById(R.id.filterlistsRetryLastRunButton);
                    assertNotNull(button);
                    assertEquals(View.VISIBLE, button.getVisibility());
                    button.performClick();
                } else {
                    try {
                        Field selection = DiscoverFilterListsFragment.class
                                .getDeclaredField("selectedListIds");
                        selection.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        Set<Integer> ids = (Set<Integer>) selection.get(fragment);
                        for (int id = 1; id <= COUNT; id++) ids.add(id);
                        Method subscribe = DiscoverFilterListsFragment.class
                                .getDeclaredMethod("subscribeAll", boolean.class);
                        subscribe.setAccessible(true);
                        // Exercise the confirmed command with the exact checked-row state.
                        subscribe.invoke(fragment, false);
                    } catch (ReflectiveOperationException e) {
                        throw new AssertionError(e);
                    }
                }
            });
            assertTrue("Large command did not reach WorkManager",
                    dependencies.started.await(10, TimeUnit.SECONDS));
            String token = prefs.getString("subscribeAllScopeToken", null);
            assertNotNull(token);
            Data input = new Data.Builder()
                    .putString(FilterListsSubscribeAllWorker.INPUT_SCOPE_TOKEN, token).build();
            int[] savedIds = FilterListsSubscribeAllWorker.readScopeIds(prefs, input);
            assertEquals(COUNT, savedIds.length);
            for (int i = 0; i < COUNT; i++) assertEquals(i + 1, savedIds[i]);
            waitForUi(scenario, activity -> {
                View cancel = activity.findViewById(R.id.filterlistsCancelBulkButton);
                return cancel != null && cancel.getVisibility() == View.VISIBLE && cancel.isEnabled();
            });
            scenario.onActivity(activity -> activity.findViewById(
                    R.id.filterlistsCancelBulkButton).performClick());
            dependencies.release.countDown();
            long deadline = SystemClock.elapsedRealtime() + 10000;
            while (!prefs.getBoolean(FilterListsSubscribeAllWorker.KEY_LAST_RUN_CANCELLED, false)
                    && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(25);
            }
            assertTrue("Worker must finish cancellation before fixture teardown",
                    prefs.getBoolean(FilterListsSubscribeAllWorker.KEY_LAST_RUN_CANCELLED, false));
            assertEquals(COUNT, FilterListsSubscribeAllWorker.readScopeIds(prefs, input).length);
            if (retry) {
                assertEquals(COUNT, FilterListsSubscribeAllWorker.readStoredIds(prefs,
                        FilterListsSubscribeAllWorker.KEY_LAST_RUN_NO_URL_IDS).length);
            }
            // getAll deliberately excludes the built-in user source.
            assertTrue("Cancellation must not add subscriptions", hostsSourceDao.getAll().isEmpty());
            assertTrue("Cancellation must preserve user rules",
                    hostsSourceDao.getById(HostsSource.USER_SOURCE_ID).isPresent());
        }
    }

    private void seedDirectory() throws Exception {
        JSONArray lists = new JSONArray();
        Set<String> retryIds = new HashSet<>();
        for (int id = 1; id <= COUNT; id++) {
            lists.put(new JSONObject().put("id", id).put("name", "Safe " + id)
                    .put("syntaxIds", new JSONArray().put(1))
                    .put("tagIds", new JSONArray()).put("languageIds", new JSONArray()));
            dependencies.summaries.add(new FilterListsDirectoryApi.ListSummary(id, "Safe " + id,
                    null, new int[]{1}, new int[0], new int[0]));
            retryIds.add(Integer.toString(id));
        }
        long now = System.currentTimeMillis();
        assertTrue(prefs.edit().clear().putString("listsJson", lists.toString())
                .putString("syntaxesJson", "[{\"id\":1,\"name\":\"Hosts\"}]")
                .putString("tagsJson", "[]").putString("languagesJson", "[]")
                .putLong("cachedAt", now).putLong("tagsCachedAt", now)
                .putString(FilterListsSubscribeAllWorker.KEY_LAST_RUN_OUTCOMES,
                        "SUBSCRIBED\t99999\tOnly display history\t")
                .putInt(FilterListsSubscribeAllWorker.KEY_LAST_RUN_REVIEW_COUNT, COUNT)
                .putStringSet(FilterListsSubscribeAllWorker.KEY_LAST_RUN_NO_URL_IDS, retryIds)
                .commit());
    }

    private static void waitForUi(ActivityScenario<HomeActivity> scenario, UiCondition condition) {
        AtomicBoolean ready = new AtomicBoolean();
        long deadline = SystemClock.elapsedRealtime() + 15000;
        while (!ready.get() && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity(activity -> ready.set(condition.matches(activity)));
            if (!ready.get()) SystemClock.sleep(25);
        }
        assertTrue("Timed out waiting for bulk UI", ready.get());
    }

    private interface UiCondition {
        boolean matches(HomeActivity activity);
    }

    private final class FakeDependencies implements FilterListsSubscribeAllWorker.Dependencies,
            FilterListsSubscribeAllWorker.DirectoryClient {
        final List<FilterListsDirectoryApi.ListSummary> summaries = new ArrayList<>();
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override
        public FilterListsSubscribeAllWorker.SubscribeAllRecorder createRecorder(Context context) {
            return FilterListsSubscribeAllWorker.SubscribeAllRecorder.create(hostsSourceDao);
        }

        @Override
        public FilterListsSubscribeAllWorker.DirectoryClient createDirectoryClient(Context context) {
            return this;
        }

        @Override
        public SharedPreferences getCachePreferences(Context context) {
            return prefs;
        }

        @Override
        public void enqueueUpdateNow(Context context) {
            throw new AssertionError("Cancelled/no-URL work must not request source updates");
        }

        @Override
        public List<FilterListsDirectoryApi.ListSummary> getLists() {
            return summaries;
        }

        @Override
        public FilterListsDirectoryApi.ListDetails getListDetails(int id) throws IOException {
            started.countDown();
            try {
                if (!release.await(20, TimeUnit.SECONDS)) throw new IOException("Test gate timeout");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
            return new FilterListsDirectoryApi.ListDetails(id, "Safe " + id, null,
                    new int[]{1}, Collections.emptyList());
        }
    }
}
