package org.adaway.ui.hosts;

import static org.adaway.ui.hosts.FilterListsSubscribeAllWorker.OUTPUT_ALREADY;
import static org.adaway.ui.hosts.FilterListsSubscribeAllWorker.OUTPUT_CANCELLED;
import static org.adaway.ui.hosts.FilterListsSubscribeAllWorker.OUTPUT_REVIEW_COUNT;
import static org.adaway.ui.hosts.FilterListsSubscribeAllWorker.OUTPUT_REVIEW_PREVIEW;
import static org.adaway.ui.hosts.FilterListsSubscribeAllWorker.OUTPUT_SKIPPED_NO_URL;
import static org.adaway.ui.hosts.FilterListsSubscribeAllWorker.OUTPUT_SKIPPED_UNSUPPORTED;
import static org.adaway.ui.hosts.FilterListsSubscribeAllWorker.OUTPUT_SUBSCRIBED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestListenableWorkerBuilder;

import org.adaway.db.DbTest;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.NotificationHelper;
import org.adaway.model.source.FilterListsDirectoryApi;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Connected coverage for the full FilterLists subscribe-all Worker execution path.
 */
@RunWith(AndroidJUnit4.class)
public class FilterListsSubscribeAllWorkerDoWorkTest extends DbTest {
    private static final String TEST_PREFS = "filterlists_subscribe_all_worker_test";

    private Context context;
    private SharedPreferences prefs;
    private FakeDependencies dependencies;

    @Before
    public void setUpWorker() {
        context = ApplicationProvider.getApplicationContext();
        prefs = context.getSharedPreferences(TEST_PREFS, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        NotificationHelper.createNotificationChannels(context);
        dependencies = new FakeDependencies(prefs);
        FilterListsSubscribeAllWorker.setDependenciesForTest(dependencies);
    }

    @After
    public void tearDownWorker() {
        FilterListsSubscribeAllWorker.resetDependenciesForTest();
        prefs.edit().clear().commit();
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.cancelAll();
        }
    }

    @Test
    public void doWork_subscribesAllWithFakeDirectoryClientAndEnqueuesUpdateOnce() {
        dependencies.directoryClient.summaries.add(summary(10, "New safe", "Safe hosts",
                new int[]{1}, new int[]{7}, new int[]{2}));
        dependencies.directoryClient.summaries.add(summary(11, "Existing", new int[]{1}));
        dependencies.directoryClient.summaries.add(summary(12, "Unsupported", new int[]{3}));
        dependencies.directoryClient.summaries.add(summary(13, "No URL", new int[]{14}));
        dependencies.directoryClient.details.put(10, details(10, "New safe",
                "https://new.test/hosts.txt"));
        dependencies.directoryClient.details.put(11, details(11, "Existing",
                "https://adaway.org/hosts.txt"));
        dependencies.directoryClient.details.put(13, details(13, "No URL", null));

        ListenableWorker.Result result = runWorker();

        assertTrue(result instanceof ListenableWorker.Result.Success);
        Data output = ((ListenableWorker.Result.Success) result).getOutputData();
        assertEquals(1, output.getInt(OUTPUT_SUBSCRIBED, -1));
        assertEquals(1, output.getInt(OUTPUT_ALREADY, -1));
        assertEquals(1, output.getInt(OUTPUT_SKIPPED_NO_URL, -1));
        assertEquals(1, output.getInt(OUTPUT_SKIPPED_UNSUPPORTED, -1));
        assertFalse(output.getBoolean(OUTPUT_CANCELLED, true));
        assertEquals(2, output.getInt(OUTPUT_REVIEW_COUNT, -1));
        String reviewPreview = output.getString(OUTPUT_REVIEW_PREVIEW);
        assertNotNull(reviewPreview);
        assertTrue(reviewPreview.contains("No URL"));
        assertTrue(reviewPreview.contains("Unsupported"));

        assertEquals(4, prefs.getInt(
                FilterListsSubscribeAllWorker.KEY_LAST_RUN_OUTCOME_COUNT, -1));
        assertEquals(2, prefs.getInt(
                FilterListsSubscribeAllWorker.KEY_LAST_RUN_REVIEW_COUNT, -1));
        assertFalse(prefs.getBoolean(
                FilterListsSubscribeAllWorker.KEY_LAST_RUN_CANCELLED, true));
        assertTrue(prefs.getLong(
                FilterListsSubscribeAllWorker.KEY_LAST_RUN_FINISHED_AT, 0L) > 0L);
        assertEquals(reviewPreview, prefs.getString(
                FilterListsSubscribeAllWorker.KEY_LAST_RUN_REVIEW_PREVIEW, null));
        String ledger = prefs.getString(FilterListsSubscribeAllWorker.KEY_LAST_RUN_OUTCOMES, "");
        assertTrue(ledger.contains("SUBSCRIBED\t10\tNew safe"));
        assertTrue(ledger.contains("ALREADY\t11\tExisting"));
        assertTrue(ledger.contains("SKIPPED_UNSUPPORTED\t12\tUnsupported"));
        assertTrue(ledger.contains("SKIPPED_NO_URL\t13\tNo URL"));

        HostsSource source = hostsSourceDao.getByUrl("https://new.test/hosts.txt")
                .orElse(null);
        assertNotNull(source);
        assertEquals("New safe", source.getLabel());
        assertTrue(source.isEnabled());
        assertEquals(Integer.valueOf(10), source.getFilterListId());
        assertEquals("New safe", source.getFilterListName());
        assertEquals("1", source.getFilterListSyntaxIds());
        assertEquals("7", source.getFilterListTagIds());
        assertEquals("2", source.getFilterListLanguageIds());
        assertEquals("https://new.test/hosts.txt", source.getFilterListSelectedUrl());
        assertEquals(1, dependencies.enqueueUpdateNowCount);
        assertFalse(dependencies.directoryClient.requestedDetailIds.contains(12));
    }

    @Test
    public void doWork_subscribesOnlyListsMatchingInputScope() {
        dependencies.directoryClient.summaries.add(summary(20, "Regional safe",
                "Blocks regional tracking", new int[]{1}, new int[]{7}, new int[]{2}));
        dependencies.directoryClient.summaries.add(summary(21, "Regional unsupported",
                "Browser-only regional rules", new int[]{3}, new int[]{7}, new int[]{2}));
        dependencies.directoryClient.summaries.add(summary(22, "Regional wrong tag",
                "Blocks regional tracking", new int[]{1}, new int[]{8}, new int[]{2}));
        dependencies.directoryClient.summaries.add(summary(23, "Regional wrong language",
                "Blocks regional tracking", new int[]{1}, new int[]{7}, new int[]{3}));
        dependencies.directoryClient.summaries.add(summary(24, "Social safe",
                "Blocks social tracking", new int[]{1}, new int[]{7}, new int[]{2}));
        dependencies.directoryClient.details.put(20, details(20, "Regional safe",
                "https://regional.test/hosts.txt"));

        Data input = FilterListsSubscribeAllWorker.buildScopeInput("regional", 7, 2, false,
                new int[]{20, 21});
        ListenableWorker.Result result = runWorker(input);

        assertTrue(result instanceof ListenableWorker.Result.Success);
        Data output = ((ListenableWorker.Result.Success) result).getOutputData();
        assertEquals(1, output.getInt(OUTPUT_SUBSCRIBED, -1));
        assertEquals(0, output.getInt(OUTPUT_ALREADY, -1));
        assertEquals(0, output.getInt(OUTPUT_SKIPPED_NO_URL, -1));
        assertEquals(1, output.getInt(OUTPUT_SKIPPED_UNSUPPORTED, -1));
        assertFalse(output.getBoolean(OUTPUT_CANCELLED, true));
        assertEquals(1, output.getInt(OUTPUT_REVIEW_COUNT, -1));
        assertTrue(output.getString(OUTPUT_REVIEW_PREVIEW).contains("Regional unsupported"));

        assertNotNull(hostsSourceDao.getByUrl("https://regional.test/hosts.txt")
                .orElse(null));
        assertEquals(Arrays.asList(20), dependencies.directoryClient.requestedDetailIds);
    }

    @Test
    public void doWork_returnsRetryWhenListFetchFails() {
        dependencies.directoryClient.throwOnGetLists = true;

        ListenableWorker.Result result = runWorker();

        assertTrue(result instanceof ListenableWorker.Result.Retry);
        assertEquals(0, dependencies.enqueueUpdateNowCount);
        assertFalse(hostsSourceDao.getByUrl("https://new.test/hosts.txt").isPresent());
        assertEquals(1, hostsSourceDao.getAll().size());
    }

    private ListenableWorker.Result runWorker() {
        return runWorker(Data.EMPTY);
    }

    private ListenableWorker.Result runWorker(Data inputData) {
        FilterListsSubscribeAllWorker worker = TestListenableWorkerBuilder
                .from(context, FilterListsSubscribeAllWorker.class)
                .setInputData(inputData)
                .build();
        return worker.doWork();
    }

    private static FilterListsDirectoryApi.ListSummary summary(int id, String name,
            int[] syntaxIds) {
        return summary(id, name, null, syntaxIds, new int[0], new int[0]);
    }

    private static FilterListsDirectoryApi.ListSummary summary(int id, String name,
            String description, int[] syntaxIds, int[] tagIds, int[] languageIds) {
        return new FilterListsDirectoryApi.ListSummary(id, name, description, syntaxIds,
                tagIds, languageIds);
    }

    private static FilterListsDirectoryApi.ListDetails details(int id, String name, String url) {
        List<FilterListsDirectoryApi.ViewUrl> viewUrls = new ArrayList<>();
        if (url != null) {
            viewUrls.add(new FilterListsDirectoryApi.ViewUrl(0, 1, url));
        }
        return new FilterListsDirectoryApi.ListDetails(id, name, null, new int[]{1}, viewUrls);
    }

    private final class FakeDependencies implements FilterListsSubscribeAllWorker.Dependencies {
        final FakeDirectoryClient directoryClient = new FakeDirectoryClient();
        final SharedPreferences sharedPreferences;
        int enqueueUpdateNowCount;

        FakeDependencies(SharedPreferences sharedPreferences) {
            this.sharedPreferences = sharedPreferences;
        }

        @Override
        public FilterListsSubscribeAllWorker.SubscribeAllRecorder createRecorder(Context context) {
            return FilterListsSubscribeAllWorker.SubscribeAllRecorder.create(hostsSourceDao);
        }

        @Override
        public FilterListsSubscribeAllWorker.DirectoryClient createDirectoryClient(
                Context context) {
            return directoryClient;
        }

        @Override
        public SharedPreferences getCachePreferences(Context context) {
            return sharedPreferences;
        }

        @Override
        public void enqueueUpdateNow(Context context) {
            enqueueUpdateNowCount++;
        }
    }

    private static final class FakeDirectoryClient
            implements FilterListsSubscribeAllWorker.DirectoryClient {
        final List<FilterListsDirectoryApi.ListSummary> summaries = new ArrayList<>();
        final Map<Integer, FilterListsDirectoryApi.ListDetails> details = new HashMap<>();
        final List<Integer> requestedDetailIds = new ArrayList<>();
        boolean throwOnGetLists;

        @Override
        public List<FilterListsDirectoryApi.ListSummary> getLists() throws IOException {
            if (throwOnGetLists) {
                throw new IOException("Simulated /lists failure");
            }
            return summaries;
        }

        @Override
        public FilterListsDirectoryApi.ListDetails getListDetails(int id) throws IOException {
            requestedDetailIds.add(id);
            FilterListsDirectoryApi.ListDetails result = details.get(id);
            if (result == null) {
                throw new IOException("Unexpected detail request: " + id
                        + " known=" + Arrays.toString(details.keySet().toArray()));
            }
            return result;
        }
    }
}
