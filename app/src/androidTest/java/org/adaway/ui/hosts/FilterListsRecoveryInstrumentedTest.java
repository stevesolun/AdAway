package org.adaway.ui.hosts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
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
import org.adaway.helper.NotificationHelper;
import org.adaway.model.source.FilterListsDirectoryApi;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Behavioral coverage for AUD-SRC-008 and AUD-SRC-011. No live directory or source updates. */
@RunWith(AndroidJUnit4.class)
public class FilterListsRecoveryInstrumentedTest extends DbTest {
    private Context context;
    private SharedPreferences prefs;
    private FakeDependencies dependencies;

    @Before
    public void setUpRecovery() {
        context = ApplicationProvider.getApplicationContext();
        prefs = context.getSharedPreferences("filterlists_recovery_test", Context.MODE_PRIVATE);
        assertTrue(prefs.edit().clear().commit());
        NotificationHelper.createNotificationChannels(context);
        dependencies = new FakeDependencies();
        FilterListsSubscribeAllWorker.setDependenciesForTest(dependencies);
    }

    @After
    public void tearDownRecovery() {
        dependencies.releaseDetails.countDown();
        FilterListsSubscribeAllWorker.resetDependenciesForTest();
        prefs.edit().clear().commit();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(42);
    }

    @Test
    public void lateFailuresAfterFiveHundredSuccessesRemainRecoverable() {
        FilterListsSubscribeAllWorker.SubscribeAllRecorder recorder = recorder();
        for (int id = 1; id <= 500; id++) {
            recorder.accept(id, "Safe " + id, new int[]{1},
                    "https://recovery.test/" + id + ".txt");
        }
        recorder.accept(501, "Late no URL", new int[]{1}, null);
        recorder.accept(502, "Late unsupported", new int[]{3}, null);
        recorder.flush();
        FilterListsSubscribeAllWorker.persistLastRunOutcomes(prefs, recorder, false);

        assertEquals(500, recorder.getSubscribed());
        assertEquals(502, recorder.getOutcomeCount());
        assertEquals(500, recorder.getOutcomeLedger().split("\n").length);
        assertFalse(recorder.getOutcomeLedger().contains("Late"));
        assertArrayEquals(new int[]{501}, recoveryIds(false));
        assertArrayEquals(new int[]{502}, recoveryIds(true));
    }

    @Test
    public void thousandsOfFailuresAndUnsupportedIdsSurviveCancellation() {
        FilterListsSubscribeAllWorker.SubscribeAllRecorder recorder = recorder();
        for (int id = 1; id <= 1200; id++) {
            recorder.accept(id, "Missing " + id, new int[]{1}, null);
            recorder.accept(id + 1200, "Browser " + id, new int[]{3}, null);
        }
        recorder.accept(3000, "Pending insert", new int[]{1},
                "https://recovery.test/pending.txt");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicBoolean notified = new AtomicBoolean();
        try {
            Data output = FilterListsSubscribeAllWorker.finalizeCancelledRun(pool, prefs, recorder,
                    () -> notified.set(true));
            assertTrue(pool.isShutdown());
            assertTrue(notified.get());
            assertTrue(output.getBoolean(FilterListsSubscribeAllWorker.OUTPUT_CANCELLED, false));
            assertEquals(2400, output.getInt(FilterListsSubscribeAllWorker.OUTPUT_REVIEW_COUNT, 0));
            assertTrue(output.toByteArray().length < Data.MAX_DATA_BYTES);
            assertArrayEquals(ids(1, 1200), recoveryIds(false));
            assertArrayEquals(ids(1201, 1200), recoveryIds(true));
            assertTrue(hostsSourceDao.getByUrl("https://recovery.test/pending.txt").isPresent());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void largeScopeIsSmallImmutableInputIndependentOfLastRunHistory() throws Exception {
        int[] original = ids(1, 10000);
        assertThrows(IllegalStateException.class, () -> new Data.Builder()
                .putIntArray(FilterListsSubscribeAllWorker.INPUT_LIST_IDS, original).build());
        Data input = FilterListsSubscribeAllWorker.persistScopeInput(prefs, true, original);
        original[0] = 999999;
        assertNull(input.getIntArray(FilterListsSubscribeAllWorker.INPUT_LIST_IDS));
        assertTrue(input.toByteArray().length < 512);
        assertTrue(input.getBoolean(FilterListsSubscribeAllWorker.INPUT_COMPATIBLE_ONLY, false));
        FilterListsSubscribeAllWorker.persistLastRunOutcomes(prefs, recorder(), true);
        assertArrayEquals(ids(1, 10000), FilterListsSubscribeAllWorker.readScopeIds(prefs,
                Data.fromByteArray(input.toByteArray())));
        assertEquals(0, recoveryIds(false).length);
    }

    @Test
    public void replacedOrMissingScopeFailsClosedAndStorageDoesNotAccumulate() throws Exception {
        Data first = FilterListsSubscribeAllWorker.persistScopeInput(prefs, false, ids(1, 10000));
        Data second = FilterListsSubscribeAllWorker.persistScopeInput(prefs, false, ids(20001, 6000));
        assertEquals(2, prefs.getAll().size());
        assertArrayEquals(ids(20001, 6000), FilterListsSubscribeAllWorker.readScopeIds(prefs, second));
        assertThrows(IllegalStateException.class,
                () -> FilterListsSubscribeAllWorker.readScopeIds(prefs, first));
        assertTrue(worker(first).doWork() instanceof ListenableWorker.Result.Failure);
        assertEquals(0, dependencies.listRequests);
        assertTrue(prefs.edit().clear().commit());
        assertTrue(worker(second).doWork() instanceof ListenableWorker.Result.Failure);
        assertEquals(0, dependencies.listRequests);
    }

    @Test
    public void emptyScopeStaysEmptyAndInlineBoundaryFits() throws Exception {
        Data inline = FilterListsSubscribeAllWorker.buildScopeInput(null, 0, 0, false,
                ids(1, FilterListsSubscribeAllWorker.MAX_INLINE_SCOPE_IDS));
        assertTrue(inline.toByteArray().length < Data.MAX_DATA_BYTES);
        assertThrows(IllegalArgumentException.class,
                () -> FilterListsSubscribeAllWorker.buildScopeInput(null, 0, 0, false,
                        ids(1, FilterListsSubscribeAllWorker.MAX_INLINE_SCOPE_IDS + 1)));
        Data empty = FilterListsSubscribeAllWorker.persistScopeInput(prefs, false, new int[0]);
        assertTrue(FilterListsSubscribeAllWorker.filterListsForScope(
                Collections.singletonList(summary(1, 1)), null, 0, 0, false,
                FilterListsSubscribeAllWorker.readScopeIds(prefs, empty)).isEmpty());
    }

    @Test
    public void largeSelectedScopeProcessesEveryIdAndExcludesUnselectedRows() throws Exception {
        SharedPreferences.Editor editor = prefs.edit();
        for (int id = 1; id <= 6001; id++) {
            dependencies.summaries.add(summary(id, 1));
            editor.putString("listUrl_" + id, "");
        }
        assertTrue(editor.commit());
        Data input = FilterListsSubscribeAllWorker.persistScopeInput(prefs, false, ids(1, 6000));
        ListenableWorker.Result result = worker(input).doWork();
        assertTrue(result instanceof ListenableWorker.Result.Success);
        Data output = ((ListenableWorker.Result.Success) result).getOutputData();
        assertEquals(6000, output.getInt(FilterListsSubscribeAllWorker.OUTPUT_SKIPPED_NO_URL, 0));
        assertArrayEquals(ids(1, 6000), recoveryIds(false));
        assertEquals(0, dependencies.detailRequests);
        assertFalse(dependencies.updateEnqueued);
    }

    @Test
    public void directoryRetryKeepsExactLargeScope() throws Exception {
        Data input = FilterListsSubscribeAllWorker.persistScopeInput(prefs, false, ids(1, 6000));
        dependencies.failLists = true;
        assertTrue(worker(input).doWork() instanceof ListenableWorker.Result.Retry);
        assertArrayEquals(ids(1, 6000), FilterListsSubscribeAllWorker.readScopeIds(prefs, input));
        dependencies.failLists = false;
        dependencies.summaries.add(summary(6000, 3));
        dependencies.summaries.add(summary(6001, 3));
        assertTrue(worker(input).doWork() instanceof ListenableWorker.Result.Success);
        assertArrayEquals(new int[]{6000}, recoveryIds(true));
    }

    @Test
    public void cancellingRetryKeepsUnprocessedFailuresButDropsSuccessfulRetryIds() throws Exception {
        Data scope = FilterListsSubscribeAllWorker.persistScopeInput(prefs, false, ids(1, 6000));
        Data input = new Data.Builder().putAll(scope)
                .putBoolean(FilterListsSubscribeAllWorker.INPUT_RETRY_NO_URL, true).build();
        dependencies.summaries.add(summary(1, 1));
        dependencies.summaries.add(summary(2, 3));
        dependencies.summaries.add(summary(3, 1));
        dependencies.blockDetails = true;
        dependencies.cancelAfterOutcomes = 2;
        assertTrue(prefs.edit().putString("listUrl_1", "https://adaway.org/hosts.txt").commit());

        assertTrue(worker(input).doWork() instanceof ListenableWorker.Result.Failure);

        assertArrayEquals(ids(3, 5998), recoveryIds(false));
        assertArrayEquals(new int[]{2}, recoveryIds(true));
        assertArrayEquals(ids(1, 6000), FilterListsSubscribeAllWorker.readScopeIds(prefs, input));
    }

    @Test
    public void cancellingRetryBeforeFirstCandidateKeepsEveryFailure() throws Exception {
        Data scope = FilterListsSubscribeAllWorker.persistScopeInput(prefs, false, ids(1, 6000));
        Data input = new Data.Builder().putAll(scope)
                .putBoolean(FilterListsSubscribeAllWorker.INPUT_RETRY_NO_URL, true).build();
        assertTrue(FilterListsSubscribeAllWorker.requestCancel(context));

        assertTrue(worker(input).doWork() instanceof ListenableWorker.Result.Failure);

        assertEquals(0, dependencies.listRequests);
        assertArrayEquals(ids(1, 6000), recoveryIds(false));
    }

    @Test
    public void failedDetailFutureRetainsOriginalRecoveryId() {
        dependencies.summaries.add(summary(9001, 1));
        dependencies.failDetails = true;
        assertTrue(worker(Data.EMPTY).doWork() instanceof ListenableWorker.Result.Success);
        assertArrayEquals(new int[]{9001}, recoveryIds(false));
    }

    @Test
    public void cancellationDuringLargeScopeKeepsLateFailuresAndSnapshot() throws Exception {
        SharedPreferences.Editor editor = prefs.edit();
        for (int id = 1; id <= 500; id++) {
            dependencies.summaries.add(summary(id, 1));
            editor.putString("listUrl_" + id, "https://adaway.org/hosts.txt");
        }
        dependencies.summaries.add(summary(501, 1));
        dependencies.summaries.add(summary(502, 3));
        dependencies.summaries.add(summary(503, 1));
        assertTrue(editor.putString("listUrl_501", "").commit());
        dependencies.blockDetails = true;
        dependencies.cancelAfterOutcomes = 502;
        Data input = FilterListsSubscribeAllWorker.persistScopeInput(prefs, false, ids(1, 6000));
        FilterListsSubscribeAllWorker worker = worker(input);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<ListenableWorker.Result> future = executor.submit(worker::doWork);
        try {
            ListenableWorker.Result result = future.get(10, TimeUnit.SECONDS);
            assertTrue(result instanceof ListenableWorker.Result.Failure);
            assertEquals(502, dependencies.currentRecorder.getOutcomeCount());
            assertTrue(((ListenableWorker.Result.Failure) result).getOutputData()
                    .getBoolean(FilterListsSubscribeAllWorker.OUTPUT_CANCELLED, false));
            assertArrayEquals(new int[]{501}, recoveryIds(false));
            assertArrayEquals(new int[]{502}, recoveryIds(true));
            assertArrayEquals(ids(1, 6000), FilterListsSubscribeAllWorker.readScopeIds(prefs, input));
            assertFalse(dependencies.updateEnqueued);
        } finally {
            dependencies.releaseDetails.countDown();
            executor.shutdownNow();
        }
    }

    private FilterListsSubscribeAllWorker.SubscribeAllRecorder recorder() {
        return FilterListsSubscribeAllWorker.SubscribeAllRecorder.create(hostsSourceDao);
    }

    private FilterListsSubscribeAllWorker worker(Data input) {
        return TestListenableWorkerBuilder.from(context, FilterListsSubscribeAllWorker.class)
                .setInputData(input).build();
    }

    private int[] recoveryIds(boolean unsupported) {
        return FilterListsSubscribeAllWorker.readStoredIds(prefs, unsupported
                ? FilterListsSubscribeAllWorker.KEY_LAST_RUN_UNSUPPORTED_IDS
                : FilterListsSubscribeAllWorker.KEY_LAST_RUN_NO_URL_IDS);
    }

    private static int[] ids(int start, int count) {
        int[] result = new int[count];
        for (int i = 0; i < count; i++) result[i] = start + i;
        return result;
    }

    private static FilterListsDirectoryApi.ListSummary summary(int id, int syntax) {
        return new FilterListsDirectoryApi.ListSummary(id, "List " + id, null,
                new int[]{syntax}, new int[0], new int[0]);
    }

    private final class FakeDependencies implements FilterListsSubscribeAllWorker.Dependencies,
            FilterListsSubscribeAllWorker.DirectoryClient {
        final List<FilterListsDirectoryApi.ListSummary> summaries = new ArrayList<>();
        final CountDownLatch detailStarted = new CountDownLatch(1);
        final CountDownLatch releaseDetails = new CountDownLatch(1);
        volatile FilterListsSubscribeAllWorker.SubscribeAllRecorder currentRecorder;
        int listRequests;
        int detailRequests;
        boolean failLists;
        boolean failDetails;
        boolean blockDetails;
        boolean updateEnqueued;
        int cancelAfterOutcomes;

        @Override
        public FilterListsSubscribeAllWorker.SubscribeAllRecorder createRecorder(Context context) {
            currentRecorder = recorder();
            return currentRecorder;
        }

        @Override
        public FilterListsSubscribeAllWorker.DirectoryClient createDirectoryClient(Context context) {
            return this;
        }

        @Override
        public SharedPreferences getCachePreferences(Context context) {
            if (cancelAfterOutcomes == 0) return prefs;
            // Trigger cancellation on the worker's own thread at an exact accepted-outcome count.
            return (SharedPreferences) Proxy.newProxyInstance(
                    SharedPreferences.class.getClassLoader(),
                    new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                        if ("getBoolean".equals(method.getName())
                                && "subscribeAllCancelRequested".equals(args[0])
                                && currentRecorder.getOutcomeCount() >= cancelAfterOutcomes) {
                            return true;
                        }
                        return method.invoke(prefs, args);
                    });
        }

        @Override
        public void enqueueUpdateNow(Context context) {
            updateEnqueued = true;
        }

        @Override
        public List<FilterListsDirectoryApi.ListSummary> getLists() throws IOException {
            listRequests++;
            if (failLists) throw new IOException("Simulated directory failure");
            return summaries;
        }

        @Override
        public FilterListsDirectoryApi.ListDetails getListDetails(int id) throws IOException {
            detailRequests++;
            detailStarted.countDown();
            if (failDetails) throw new IllegalStateException("Simulated detail future failure");
            if (blockDetails) {
                try {
                    if (!releaseDetails.await(15, TimeUnit.SECONDS)) {
                        throw new IOException("Timed out waiting for test cancellation");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
            return new FilterListsDirectoryApi.ListDetails(id, "List " + id, null,
                    new int[]{1}, Collections.emptyList());
        }
    }
}
