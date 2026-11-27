package org.adaway.ui.hosts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.source.SourceApplyState;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.util.AppExecutors;
import org.adaway.util.Constants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class SourceCheckpointOwnershipInstrumentedTest {
    private final String name = "Checkpoint " + UUID.randomUUID();
    private final String url = "https://checkpoint.invalid/" + UUID.randomUUID();
    private AdAwayApplication app;
    private HostsSourceDao dao;
    private HostsSource source;
    private HostsSourcesViewModel owner;
    private String previousProfile;
    private SharedPreferences checkpoint;
    private boolean tunnel;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(app, "prepare source checkpoints");
        previousProfile = FilterSetStore.getActiveProfile(app);
        checkpoint = app.getSharedPreferences(Constants.PREFS_PENDING_SOURCE_APPLY, Context.MODE_PRIVATE);
        dao = AppDatabase.getInstance(app).hostsSourceDao();
        HostsSource row = new HostsSource();
        row.setLabel(name);
        row.setUrl(url);
        row.setEnabled(false);
        dao.insert(row);
        source = dao.getByUrl(url).orElseThrow();
        List<HostsSource> baseline = dao.getAll();
        main(() -> {
            owner = new HostsSourcesViewModel(app) {
                @Override boolean isVpnTunnelEstablished() { return tunnel; }
            };
            owner.onSourcesChanged(baseline);
            owner.observeAutomaticWork(null, false, null);
            assertFalse(owner.getApplyState().isPending());
        });
    }

    @After
    public void tearDown() throws Exception {
        if (owner != null) main(owner::onCleared);
        drainVerification();
        if (source != null) dao.delete(source);
        if (app != null) {
            FilterSetStore.deleteSet(app, name);
            if (previousProfile != null) FilterSetStore.setActiveProfile(app, previousProfile);
            InstrumentedTestState.resetForPassiveRootUi(app, "finish source checkpoints");
        }
    }

    @Test
    public void coalescedAutomaticReceiptRetiresObsoleteTarget() throws Exception {
        List<HostsSource> edited = coalescedRows();
        String receipt = SourceApplyState.fingerprint(edited);
        CountDownLatch release = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Disk gate timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        });
        try {
            main(() -> {
                owner.observeAutomaticWork("new", true, receipt);
                owner.onSourcesChanged(edited);
                assertFalse(owner.getApplyState().isPending());
                owner.onCleared();
                assertFalse(freshPending(edited));
            });
        } finally {
            release.countDown();
        }
        drainVerification();
        assertNull(checkpoint.getString("selection", null));
        assertEquals(receipt, checkpoint.getString("fingerprint", null));
        main(() -> assertFalse(freshPending(edited)));
        assertTrue(checkpoint.getAll().isEmpty());
    }

    @Test
    public void staleRoomAndReceiptCannotEraseUnobservedCommittedWrite() throws Exception {
        List<HostsSource> stale = dao.getAll();
        commitSelectionWithoutRoom();
        List<HostsSource> committed = dao.getAll();
        String target = SourceApplyState.fingerprint(committed);
        // WorkManager skipped RUNNING; old rows and the late receipt agree with each other.
        main(() -> owner.observeAutomaticWork("late", true, SourceApplyState.fingerprint(stale)));
        drainVerification();
        assertEquals(target, checkpoint.getString("selection", null));
        main(() -> assertTrue(freshPending(committed)));
    }

    @Test
    public void delayedTunnelCannotRetargetANewerSelection() throws Exception {
        List<HostsSource> edited = coalescedRows();
        String receipt = SourceApplyState.fingerprint(edited);
        main(() -> {
            owner.onSourcesChanged(edited);
            owner.observeAutomaticWork("download", true, null, receipt);
            assertTrue(owner.getApplyState().isPending());
            owner.beginSavedSetSelection();
        });
        try {
            source.setEnabled(false);
            dao.update(source);
        } finally {
            owner.finishSavedSetSelection(true);
        }
        List<HostsSource> newer = dao.getAll();
        main(() -> {
            tunnel = true;
            owner.onVpnStateChanged();
        });
        drainVerification();
        assertEquals(SourceApplyState.fingerprint(newer), checkpoint.getString("selection", null));
        main(() -> assertTrue(freshPending(newer)));
    }

    @Test
    public void queuedSavedSetSurvivesOwnerRemovalBeforeRoomDelivery() throws Exception {
        Set<String> urls = dao.getEnabled().stream().map(HostsSource::getUrl).collect(Collectors.toSet());
        urls.add(url);
        FilterSetStore.saveSet(app, name, urls);
        String id = FilterSetStore.getSetIdForDisplayName(app, name);
        ArrayDeque<Runnable> disk = new ArrayDeque<>();
        List<HostsSource> stale = dao.getAll();
        main(() -> {
            HostsSourcesFragment.submitConfirmedFilterSet(app, owner, name, urls, id, disk::add);
            owner.onCleared();
            assertFalse(freshPending(stale));
        });
        disk.remove().run();
        assertTrue(dao.getById(source.getId()).orElseThrow().isEnabled());
        assertEquals(0, dao.getById(source.getId()).orElseThrow().getSize());
        assertEquals(name, FilterSetStore.getActiveProfile(app));
        List<HostsSource> committed = dao.getAll();
        main(() -> assertTrue(freshPending(committed)));
    }

    @Test
    public void refusedAndRejectedSavedSetsReleaseOnlyTheirOwnWrite() {
        commitSelectionWithoutRoom();
        String target = SourceApplyState.fingerprint(dao.getAll());
        main(() -> {
            CompletableFuture<Integer> refused = HostsSourcesFragment.submitConfirmedFilterSet(
                    app, owner, name, java.util.Collections.emptySet(), null, Runnable::run);
            assertEquals(Integer.valueOf(R.string.filter_set_missing), refused.join());
            CompletableFuture<Integer> rejected = HostsSourcesFragment.submitConfirmedFilterSet(
                    app, owner, name, java.util.Collections.emptySet(), null,
                    task -> { throw new RejectedExecutionException(); });
            assertTrue(rejected.isCompletedExceptionally());
            owner.savedSetSelectionFinished();
        });
        assertEquals(target, checkpoint.getString("selection", null));
        assertTrue(app.getPendingSourceApply().settledSelectionRevision() >= 0);
    }

    private List<HostsSource> coalescedRows() {
        commitSelectionWithoutRoom();
        source.setAllowEnabled(true);
        dao.update(source);
        return dao.getAll();
    }

    private void commitSelectionWithoutRoom() {
        main(owner::beginSavedSetSelection);
        boolean committed = false;
        try {
            source.setEnabled(true);
            dao.update(source);
            committed = true;
        } finally {
            owner.finishSavedSetSelection(committed);
        }
    }

    private boolean freshPending(List<HostsSource> rows) {
        HostsSourcesViewModel fresh = new HostsSourcesViewModel(app);
        try {
            fresh.onSourcesChanged(rows);
            return fresh.getApplyState().isPending();
        } finally {
            fresh.onCleared();
        }
    }

    private static void main(Runnable task) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task);
    }

    private static void drainVerification() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(done::countDown);
        assertTrue(done.await(10, TimeUnit.SECONDS));
        main(() -> {});
    }
}
