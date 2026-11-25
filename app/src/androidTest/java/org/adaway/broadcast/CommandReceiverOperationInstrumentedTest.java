package org.adaway.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostError;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.SourceOperationCoordinator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** Exercises receiver dispatch, not Android sender permissions or a real VPN/root model. */
@RunWith(AndroidJUnit4.class)
public class CommandReceiverOperationInstrumentedTest {
    private Context context;
    private SourceOperationCoordinator operations;
    private RecordingModel model;
    private RecordingReceiver receiver;
    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private Executor executor = this.tasks::add;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        this.operations = new SourceOperationCoordinator();
        this.model = new RecordingModel(this.context);
        this.receiver = new RecordingReceiver();
    }

    @Test
    public void heldExternalLeaseRejectsStartWithBusyFeedback() {
        assertExternalLeaseRejects(Command.START);
    }

    @Test
    public void heldExternalLeaseRejectsStopWithBusyFeedback() {
        assertExternalLeaseRejects(Command.STOP);
    }

    private void assertExternalLeaseRejects(Command command) {
        onMain(() -> {
            try (SourceOperationCoordinator.Lease lease = this.operations.tryAcquire()) {
                assertNotNull(lease);
                receive(command);
                assertTrue(this.operations.isBusy());
                assertEquals(1, this.receiver.busyMessages);
                assertEquals(0, this.receiver.modelLookups);
                assertTrue(this.tasks.isEmpty());
                assertEquals(0, this.model.applies + this.model.reverts);
            }
            assertFalse(this.operations.isBusy());
            receive(command);
            assertTrue(this.operations.isBusy());
            assertEquals(1, this.tasks.size());
            this.tasks.remove().run();
            assertFalse(this.operations.isBusy());
            assertEquals(command == Command.START ? 1 : 0, this.model.applies);
            assertEquals(command == Command.STOP ? 1 : 0, this.model.reverts);
        });
    }

    @Test
    public void queuedCommandOwnsLeaseAndRapidOppositeCommandIsNotQueued() {
        onMain(() -> {
            receive(Command.START);
            assertTrue(this.operations.isBusy());
            receive(Command.STOP);
            assertEquals(1, this.tasks.size());
            assertEquals(1, this.receiver.busyMessages);
            this.tasks.remove().run();
            assertEquals(1, this.model.applies);
            assertEquals(0, this.model.reverts);
            assertFalse(this.operations.isBusy());
            receive(Command.STOP);
            this.tasks.remove().run();
            assertEquals(1, this.model.reverts);
            assertFalse(this.operations.isBusy());
        });
    }

    @Test
    public void nullWrongActionAndMalformedCommandsDoNotClaimOrDispatch() {
        onMain(() -> {
            Intent[] ignored = {
                    null,
                    new Intent("unrelated").putExtra("COMMAND", "START"),
                    new Intent(),
                    new Intent(CommandReceiver.SEND_COMMAND_ACTION),
                    new Intent(CommandReceiver.SEND_COMMAND_ACTION).putExtra("COMMAND", "UNKNOWN"),
                    new Intent(CommandReceiver.SEND_COMMAND_ACTION).putExtra("COMMAND", "invalid"),
                    new Intent(CommandReceiver.SEND_COMMAND_ACTION).putExtra("COMMAND", (String) null),
                    new Intent(CommandReceiver.SEND_COMMAND_ACTION).putExtra("COMMAND", 7)
            };
            for (Intent intent : ignored) {
                this.receiver.onReceive(this.context, intent);
            }
            assertEquals(0, this.receiver.coordinatorLookups);
            assertEquals(0, this.receiver.modelLookups);
            assertEquals(0, this.receiver.busyMessages);
            assertTrue(this.tasks.isEmpty());
            assertFalse(this.operations.isBusy());
        });
    }

    @Test
    public void rejectedDispatchReleasesLeaseAndAllowsRetry() {
        onMain(() -> {
            this.executor = task -> { throw new RejectedExecutionException("test rejection"); };
            try {
                receive(Command.STOP);
                fail("Expected executor rejection.");
            } catch (RejectedExecutionException expected) {
                assertFalse(this.operations.isBusy());
                assertEquals(0, this.model.applies + this.model.reverts);
            }
            this.executor = this.tasks::add;
            receive(Command.STOP);
            this.tasks.remove().run();
            assertEquals(1, this.model.reverts);
            assertFalse(this.operations.isBusy());
        });
    }

    @Test
    public void checkedModelFailuresReleaseStartAndStopLeases() {
        onMain(() -> {
            this.model.checkedFailure = true;
            for (Command command : new Command[]{Command.START, Command.STOP}) {
                receive(command);
                this.tasks.remove().run();
                assertFalse(this.operations.isBusy());
            }
        });
    }

    @Test
    public void uncheckedModelFailuresReleaseStartAndStopLeases() {
        onMain(() -> {
            this.model.uncheckedFailure = true;
            for (Command command : new Command[]{Command.START, Command.STOP}) {
                receive(command);
                try {
                    this.tasks.remove().run();
                    fail("Expected model failure.");
                } catch (IllegalStateException expected) {
                    assertFalse(this.operations.isBusy());
                }
            }
        });
    }

    private void receive(Command command) {
        Intent intent = new Intent(CommandReceiver.SEND_COMMAND_ACTION);
        command.appendToIntent(intent);
        this.receiver.onReceive(this.context, intent);
    }

    private static void onMain(Runnable task) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task);
    }

    private final class RecordingReceiver extends CommandReceiver {
        int coordinatorLookups;
        int modelLookups;
        int busyMessages;

        @Override SourceOperationCoordinator getSourceOperations(Context context) {
            this.coordinatorLookups++;
            return operations;
        }
        @Override AdBlockModel getModel(Context context) {
            this.modelLookups++;
            return model;
        }
        @Override Executor getCommandExecutor() { return executor; }
        @Override void showBusy(Context context) { this.busyMessages++; }
    }

    private final class RecordingModel extends AdBlockModel {
        int applies;
        int reverts;
        boolean checkedFailure;
        boolean uncheckedFailure;

        RecordingModel(Context context) { super(context); }
        @Override public AdBlockMethod getMethod() { return AdBlockMethod.VPN; }
        @Override public void apply() throws HostErrorException {
            this.applies++;
            checkOwnershipAndFailure();
        }
        @Override public void revert() throws HostErrorException {
            this.reverts++;
            checkOwnershipAndFailure();
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
