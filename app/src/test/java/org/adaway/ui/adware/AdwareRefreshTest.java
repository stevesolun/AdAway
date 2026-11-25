package org.adaway.ui.adware;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.Assert.*;

public class AdwareRefreshTest {
    private final QueueExecutor scans = new QueueExecutor();
    private final QueueExecutor main = new QueueExecutor();
    private Fixture data;

    @Before
    public void setUp() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override public void executeOnDiskIO(Runnable task) { task.run(); }
            @Override public void postToMainThread(Runnable task) { task.run(); }
            @Override public boolean isMainThread() { return true; }
        });
        this.data = new Fixture(this.scans, this.main);
    }

    @After
    public void tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    @Test
    public void refreshDefersScanningAndPublicationToTheirExecutors() {
        assertTrue(this.scans.tasks.isEmpty());
        this.data.next = installs("first");
        this.data.refresh();
        assertEquals(0, this.data.scanCount);
        assertNull(this.data.getValue());
        this.scans.run(0);
        assertEquals(1, this.data.scanCount);
        assertNull(this.data.getValue());
        this.main.run(0);
        assertEquals(installs("first"), this.data.getValue());
    }

    @Test
    public void olderWorkerCannotReplaceNewerCompletedScan() {
        this.data.refresh();
        this.data.refresh();
        this.data.next = installs("new");
        this.scans.run(1);
        this.main.run(0);
        this.data.next = installs("old");
        this.scans.run(0);
        this.main.run(0);
        assertEquals(installs("new"), this.data.getValue());
    }

    @Test
    public void newRequestInvalidatesOldMainDeliveryBeforeNewScanCompletes() {
        this.data.next = installs("old");
        this.data.refresh();
        this.scans.run(0);
        this.data.refresh();
        this.main.run(0);
        assertNull("An obsolete result must never be published.", this.data.getValue());
        this.data.next = installs("new");
        this.scans.run(0);
        this.main.run(0);
        assertEquals(installs("new"), this.data.getValue());
    }

    @Test
    public void delayedOldMainDeliveryCannotOverwritePublishedNewResult() {
        this.data.next = installs("old");
        this.data.refresh();
        this.scans.run(0);
        this.data.next = installs("new");
        this.data.refresh();
        this.scans.run(0);
        this.main.run(1);
        this.main.run(0);
        assertEquals(installs("new"), this.data.getValue());
    }

    @Test
    public void canceledUninstallKeepsRowUntilAnActualEmptySnapshotArrives() {
        publish(installs("installed"));
        this.data.refresh();
        assertEquals(installs("installed"), this.data.getValue());
        this.scans.run(0);
        this.main.run(0);
        assertEquals("Cancel did not uninstall the package.",
                installs("installed"), this.data.getValue());

        this.data.next = Collections.emptyList();
        this.data.refresh();
        this.scans.run(0);
        assertEquals(installs("installed"), this.data.getValue());
        this.main.run(0);
        assertTrue(this.data.getValue().isEmpty());
    }

    @Test
    public void refreshFindsPackageInstalledAfterAnEmptyScan() {
        publish(Collections.emptyList());
        assertTrue(this.data.getValue().isEmpty());
        publish(installs("new.install"));
        assertEquals(installs("new.install"), this.data.getValue());
    }

    private void publish(List<AdwareInstall> snapshot) {
        this.data.next = snapshot;
        this.data.refresh();
        this.scans.run(0);
        this.main.run(0);
    }

    private static List<AdwareInstall> installs(String packageName) {
        return Collections.singletonList(new AdwareInstall("Fixture", packageName));
    }

    private static final class Fixture extends AdwareLiveData {
        List<AdwareInstall> next = Collections.emptyList();
        int scanCount;

        Fixture(Executor scans, Executor main) {
            super(null, scans, main);
        }

        @Override List<AdwareInstall> loadData() {
            this.scanCount++;
            return new ArrayList<>(this.next);
        }
    }

    private static final class QueueExecutor implements Executor {
        final List<Runnable> tasks = new ArrayList<>();
        @Override public void execute(Runnable task) { this.tasks.add(task); }
        void run(int index) { this.tasks.remove(index).run(); }
    }
}
