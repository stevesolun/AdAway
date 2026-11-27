package org.adaway.model.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import androidx.lifecycle.Observer;

import org.adaway.R;
import org.adaway.model.error.HostError;
import org.adaway.model.error.HostErrorException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SourceUpdateCheckTest {
    @Before
    public void directLiveDataDelivery() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override public void executeOnDiskIO(Runnable task) { task.run(); }
            @Override public void postToMainThread(Runnable task) { task.run(); }
            @Override public boolean isMainThread() { return true; }
        });
    }

    @After
    public void restoreExecutor() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    @Test
    public void replacementObserversJoinQueuedCheckAndReceiveItsRetainedResult() throws Exception {
        QueuedExecutor worker = new QueuedExecutor();
        QueuedExecutor main = new QueuedExecutor();
        AtomicInteger scans = new AtomicInteger();
        SourceUpdateCheck check = new SourceUpdateCheck(() -> {
            scans.incrementAndGet();
            return true;
        }, worker, main);
        List<Integer> firstMessages = new ArrayList<>();
        Observer<Integer> first = firstMessages::add;
        List<Integer> replacementMessages = new ArrayList<>();
        Observer<Integer> replacement = replacementMessages::add;

        check.start();
        check.getMessage().observeForever(first);
        check.getMessage().removeObserver(first);
        check.getMessage().observeForever(replacement);
        check.start();
        assertEquals(1, worker.size());
        assertEquals(0, scans.get());
        assertEquals(Collections.singletonList(R.string.status_check), replacementMessages);

        worker.runNext();
        check.start();
        assertEquals(0, worker.size());
        assertEquals(1, main.size());
        assertEquals(Integer.valueOf(R.string.status_check), check.getMessage().getValue());
        main.runNext();

        assertEquals(1, scans.get());
        assertEquals(Collections.singletonList(R.string.status_check), firstMessages);
        assertEquals(Arrays.asList(R.string.status_check, R.string.status_update_available),
                replacementMessages);
        check.getMessage().removeObserver(replacement);
        List<Integer> lateMessages = new ArrayList<>();
        Observer<Integer> late = lateMessages::add;
        check.getMessage().observeForever(late);
        assertEquals(Collections.singletonList(R.string.status_update_available), lateMessages);
        check.getMessage().removeObserver(late);
    }

    @Test(timeout = 10000)
    public void repeatedStartsWhileScanRunsDoNotScheduleAnotherScan() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        QueuedExecutor main = new QueuedExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger scheduled = new AtomicInteger();
        AtomicInteger scans = new AtomicInteger();
        SourceUpdateCheck check = new SourceUpdateCheck(() -> {
            scans.incrementAndGet();
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Check was not released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return false;
        }, task -> {
            scheduled.incrementAndGet();
            worker.execute(task);
        }, main);
        try {
            check.start();
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            check.start();
            check.start();
            check.dismiss();
            assertEquals(1, scheduled.get());
            assertEquals(Integer.valueOf(R.string.status_check), check.getMessage().getValue());
            release.countDown();
            main.runNext();
            assertEquals(Integer.valueOf(R.string.status_no_update_found),
                    check.getMessage().getValue());
            assertEquals(1, scans.get());
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    public void dismissalWaitsForCompletionAndNextCheckPublishesFreshResult() throws Exception {
        QueuedExecutor worker = new QueuedExecutor();
        QueuedExecutor main = new QueuedExecutor();
        AtomicBoolean available = new AtomicBoolean(false);
        SourceUpdateCheck check = new SourceUpdateCheck(available::get, worker, main);
        check.start();
        check.dismiss();
        assertEquals(Integer.valueOf(R.string.status_check), check.getMessage().getValue());
        worker.runNext();
        check.dismiss();
        assertEquals(Integer.valueOf(R.string.status_check), check.getMessage().getValue());
        main.runNext();
        assertEquals(Integer.valueOf(R.string.status_no_update_found), check.getMessage().getValue());
        check.dismiss();
        assertNull(check.getMessage().getValue());
        available.set(true);
        check.start();
        worker.runNext();
        main.runNext();
        assertEquals(Integer.valueOf(R.string.status_update_available), check.getMessage().getValue());
    }

    @Test
    public void offlineFailureIsSharedAndCanBeRetried() throws Exception {
        AtomicInteger scans = new AtomicInteger();
        QueuedExecutor worker = new QueuedExecutor();
        QueuedExecutor main = new QueuedExecutor();
        SourceUpdateCheck check = new SourceUpdateCheck(() -> {
            if (scans.getAndIncrement() == 0) throw new HostErrorException(HostError.NO_CONNECTION);
            return true;
        }, worker, main);
        check.start();
        worker.runNext();
        main.runNext();
        assertEquals(Integer.valueOf(HostError.NO_CONNECTION.getMessageKey()),
                check.getMessage().getValue());
        check.start();
        worker.runNext();
        main.runNext();
        assertEquals(Integer.valueOf(R.string.status_update_available), check.getMessage().getValue());
        assertEquals(2, scans.get());
    }

    @Test
    public void unexpectedFailureReleasesOwnershipForRetry() throws Exception {
        AtomicInteger scans = new AtomicInteger();
        QueuedExecutor worker = new QueuedExecutor();
        QueuedExecutor main = new QueuedExecutor();
        SourceUpdateCheck check = new SourceUpdateCheck(() -> {
            if (scans.getAndIncrement() == 0) throw new IllegalStateException("Fixture failure");
            return false;
        }, worker, main);
        check.start();
        worker.runNext();
        main.runNext();
        assertEquals(Integer.valueOf(R.string.status_source_check_failed), check.getMessage().getValue());
        check.start();
        worker.runNext();
        main.runNext();
        assertEquals(Integer.valueOf(R.string.status_no_update_found), check.getMessage().getValue());
        assertEquals(2, scans.get());
    }

    @Test
    public void rejectedSchedulingReportsFailureAndPermitsRetry() throws Exception {
        AtomicBoolean reject = new AtomicBoolean(true);
        AtomicInteger scans = new AtomicInteger();
        QueuedExecutor worker = new QueuedExecutor();
        QueuedExecutor main = new QueuedExecutor();
        SourceUpdateCheck check = new SourceUpdateCheck(() -> {
            scans.incrementAndGet();
            return true;
        }, task -> {
            if (reject.get()) throw new RejectedExecutionException("Fixture rejection");
            worker.execute(task);
        }, main);
        check.start();
        assertEquals(Integer.valueOf(R.string.status_source_check_failed), check.getMessage().getValue());
        assertEquals(0, scans.get());
        reject.set(false);
        check.start();
        worker.runNext();
        main.runNext();
        assertEquals(Integer.valueOf(R.string.status_update_available), check.getMessage().getValue());
        assertEquals(1, scans.get());
    }

    private static final class QueuedExecutor implements Executor {
        private final LinkedBlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        int size() {
            return tasks.size();
        }

        void runNext() throws InterruptedException {
            Runnable task = tasks.poll(2, TimeUnit.SECONDS);
            assertNotNull("Expected a queued task", task);
            task.run();
        }
    }
}
