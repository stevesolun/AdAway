package org.adaway.model.source;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;

import org.adaway.model.error.HostError;
import org.adaway.model.error.HostErrorException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class SourceOperationCoordinatorTest {
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

    @Test(timeout = 5000)
    public void leaseIsNonblockingNonNestedAndStaleCloseCannotReleaseNextOwner() throws Exception {
        SourceOperationCoordinator operations = new SourceOperationCoordinator();
        SourceOperationCoordinator.Lease first = operations.acquire();
        ExecutorService competingCaller = Executors.newSingleThreadExecutor();
        try {
            assertNull("Nested acquisition must fail rather than deadlock.", operations.tryAcquire());
            assertNull(competingCaller.submit(operations::tryAcquire).get(1, TimeUnit.SECONDS));
            try {
                operations.acquire();
                fail("Busy acquisition must report UPDATE_IN_PROGRESS.");
            } catch (HostErrorException expected) {
                assertEquals(HostError.UPDATE_IN_PROGRESS, expected.getError());
            }
            first.close();
            try (SourceOperationCoordinator.Lease second = operations.acquire()) {
                first.close();
                assertTrue("A stale close must not release the new owner.", operations.isBusy());
            }
            assertFalse(operations.isBusy());
        } finally {
            first.close();
            competingCaller.shutdownNow();
        }
    }

    @Test
    public void leaseIncludesFinalApplyAndReleasesOnFailure() throws Exception {
        SourceOperationCoordinator operations = new SourceOperationCoordinator();
        List<Boolean> busy = new ArrayList<>();
        operations.getBusy().observeForever(busy::add);
        try {
            try (SourceOperationCoordinator.Lease ignored = operations.acquire()) {
                assertTrue("Download has finished, but final apply still owns execution.",
                        operations.isBusy());
                throw new IllegalStateException("apply failed");
            }
        } catch (IllegalStateException expected) {
            assertEquals("apply failed", expected.getMessage());
        }
        assertFalse(operations.isBusy());
        assertEquals(Arrays.asList(false, true, false), busy);
        try (SourceOperationCoordinator.Lease ignored = operations.acquire()) {
            assertTrue("A failure must permit the next operation.", operations.isBusy());
        }
    }
}
