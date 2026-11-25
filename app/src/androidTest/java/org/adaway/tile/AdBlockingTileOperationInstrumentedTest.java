package org.adaway.tile;

import static android.service.quicksettings.Tile.STATE_ACTIVE;
import static android.service.quicksettings.Tile.STATE_INACTIVE;
import static android.service.quicksettings.Tile.STATE_UNAVAILABLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostError;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.SourceOperationCoordinator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** Deterministic tile callbacks; does not bind SystemUI or start a real VPN/root model. */
@RunWith(AndroidJUnit4.class)
public class AdBlockingTileOperationInstrumentedTest {
    private SourceOperationCoordinator operations;
    private RecordingModel model;
    private RecordingTile service;
    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private Executor executor = this.tasks::add;

    @Before
    public void setUp() {
        onMain(() -> {
            this.operations = new SourceOperationCoordinator();
            this.model = new RecordingModel(ApplicationProvider.getApplicationContext());
            this.model.setApplied(false);
            this.service = new RecordingTile();
            this.service.onStartListening();
        });
    }

    @After
    public void tearDown() {
        onMain(() -> this.service.onDestroy());
    }

    @Test
    public void heldExternalLeaseRejectsEnableAndShowsUnavailable() {
        assertExternalLeaseRejects(false);
    }

    @Test
    public void heldExternalLeaseRejectsStopAndShowsUnavailable() {
        assertExternalLeaseRejects(true);
    }

    private void assertExternalLeaseRejects(boolean applied) {
        onMain(() -> {
            this.model.setApplied(applied);
            try (SourceOperationCoordinator.Lease lease = this.operations.tryAcquire()) {
                assertNotNull(lease);
                this.service.onClick();
                assertEquals(STATE_UNAVAILABLE, this.service.publishedState);
                assertTrue(this.operations.isBusy());
                assertTrue(this.tasks.isEmpty());
                assertEquals(0, this.model.applies + this.model.reverts);
            }
            assertFalse(this.operations.isBusy());
            assertEquals(applied ? STATE_ACTIVE : STATE_INACTIVE, this.service.getTileState());
            this.service.onClick();
            assertEquals(1, this.tasks.size());
            this.tasks.remove().run();
            assertFalse(this.operations.isBusy());
            assertEquals(applied ? 0 : 1, this.model.applies);
            assertEquals(applied ? 1 : 0, this.model.reverts);
        });
    }

    @Test
    public void rapidClicksClaimBeforeDispatchAndOnlyQueueOneToggle() {
        onMain(() -> {
            this.service.onClick();
            assertTrue(this.operations.isBusy());
            assertEquals(STATE_UNAVAILABLE, this.service.publishedState);
            this.service.onClick();
            this.service.onClick();
            assertEquals(1, this.tasks.size());
            assertEquals(0, this.model.applies + this.model.reverts);
            this.tasks.remove().run();
            assertEquals(1, this.model.applies);
            assertEquals(0, this.model.reverts);
            assertFalse(this.operations.isBusy());
            this.service.onClick();
            this.tasks.remove().run();
            assertEquals(1, this.model.reverts);
            assertFalse(this.operations.isBusy());
        });
    }

    @Test
    public void externalLeaseChangesRefreshListeningTileWithoutAClick() {
        SourceOperationCoordinator.Lease[] lease = new SourceOperationCoordinator.Lease[1];
        try {
            onMain(() -> {
                this.model.setApplied(true);
                lease[0] = this.operations.tryAcquire();
                assertNotNull(lease[0]);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            onMain(() -> assertEquals(STATE_UNAVAILABLE, this.service.publishedState));
        } finally {
            if (lease[0] != null) lease[0].close();
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        onMain(() -> assertEquals(STATE_ACTIVE, this.service.publishedState));
    }

    @Test
    public void rejectedDispatchReleasesLeaseRestoresStateAndAllowsRetry() {
        onMain(() -> {
            this.executor = task -> { throw new RejectedExecutionException("test rejection"); };
            try {
                this.service.onClick();
                fail("Expected executor rejection.");
            } catch (RejectedExecutionException expected) {
                assertFalse(this.operations.isBusy());
                assertEquals(STATE_INACTIVE, this.service.publishedState);
                assertEquals(0, this.model.applies + this.model.reverts);
            }
            this.executor = this.tasks::add;
            this.service.onClick();
            this.tasks.remove().run();
            assertFalse(this.operations.isBusy());
            assertEquals(1, this.model.applies);
        });
    }

    @Test
    public void checkedApplyAndRevertFailuresReleaseLease() {
        onMain(() -> {
            this.model.checkedFailure = true;
            for (boolean applied : new boolean[]{false, true}) {
                this.model.setApplied(applied);
                this.service.onClick();
                this.tasks.remove().run();
                assertFalse(this.operations.isBusy());
                assertEquals(applied ? STATE_ACTIVE : STATE_INACTIVE, this.service.getTileState());
            }
        });
    }

    @Test
    public void uncheckedApplyAndRevertFailuresReleaseLease() {
        onMain(() -> {
            this.model.uncheckedFailure = true;
            for (boolean applied : new boolean[]{false, true}) {
                this.model.setApplied(applied);
                this.service.onClick();
                try {
                    this.tasks.remove().run();
                    fail("Expected model failure.");
                } catch (IllegalStateException expected) {
                    assertFalse(this.operations.isBusy());
                    assertEquals(applied ? STATE_ACTIVE : STATE_INACTIVE,
                            this.service.getTileState());
                }
            }
        });
    }

    private static void onMain(Runnable task) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task);
    }

    private final class RecordingTile extends AdBlockingTileService {
        int publishedState = -1;

        @Override AdBlockModel getModel() { return model; }
        @Override SourceOperationCoordinator getSourceOperations() { return operations; }
        @Override Executor getCommandExecutor() { return executor; }
        @Override void publishTileState(int state) { this.publishedState = state; }
    }

    private final class RecordingModel extends AdBlockModel {
        int applies;
        int reverts;
        boolean checkedFailure;
        boolean uncheckedFailure;

        RecordingModel(Context context) { super(context); }
        void setApplied(boolean applied) { this.applied.setValue(applied); }
        @Override public AdBlockMethod getMethod() { return AdBlockMethod.VPN; }
        @Override public void apply() throws HostErrorException {
            this.applies++;
            checkOwnershipAndFailure();
            setApplied(true);
        }
        @Override public void revert() throws HostErrorException {
            this.reverts++;
            checkOwnershipAndFailure();
            setApplied(false);
        }
        private void checkOwnershipAndFailure() throws HostErrorException {
            assertTrue("Model mutation must hold the shared lease.", operations.isBusy());
            if (this.checkedFailure) throw new HostErrorException(HostError.ENABLE_VPN_FAIL);
            if (this.uncheckedFailure) throw new IllegalStateException("test model failure");
        }
        @Override public boolean isRecordingLogs() { return false; }
        @Override public void setRecordingLogs(boolean recording) { }
        @Override public List<String> getLogs() { return Collections.emptyList(); }
        @Override public void clearLogs() { }
    }
}
