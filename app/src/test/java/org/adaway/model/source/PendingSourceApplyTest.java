package org.adaway.model.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import org.adaway.db.entity.HostsSource;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PendingSourceApplyTest {
    @Test
    public void queuedSelectionSurvivesRepeatedTaskRemovalUntilMatchingApply() {
        Fixture fixture = new Fixture();
        List<HostsSource> baseline = rows(false);
        List<HostsSource> selected = rows(true);
        String fingerprint = SourceApplyState.fingerprint(baseline);
        SourceApplyState first = fixture.restore(baseline);
        fixture.pending.beginSelection(first, false);

        for (int task = 0; task < 3; task++) {
            SourceApplyState replacement = fixture.restore(baseline);
            assertEquals(fingerprint, fixture.save(replacement, baseline, false));
        }

        fixture.pending.finishSelection(SourceApplyState.fingerprint(selected));
        SourceApplyState reopened = fixture.restore(selected);
        assertTrue(reopened.isPending());
        assertEquals(fingerprint, fixture.save(reopened, selected, false));
        reopened.appliedSources(selected, null);
        assertNull(fixture.save(reopened, selected, false));
        assertTrue(fixture.values.isEmpty());
        assertFalse(fixture.restore(selected).isPending());
    }

    @Test
    public void completionCannotClearCheckpointBeforeFinalRoomSnapshot() {
        Fixture fixture = new Fixture();
        List<HostsSource> baseline = rows(false);
        List<HostsSource> selected = rows(true);
        String fingerprint = SourceApplyState.fingerprint(baseline);
        fixture.pending.beginSelection(fixture.restore(baseline), false);
        fixture.pending.finishSelection(SourceApplyState.fingerprint(selected));

        SourceApplyState stale = fixture.restore(baseline);
        assertFalse(stale.isPending());
        assertEquals(fingerprint, fixture.save(stale, baseline, false));
        assertEquals(SourceApplyState.fingerprint(selected), fixture.values.get("selection"));
        assertTrue(fixture.restore(selected).isPending());
    }

    @Test
    public void applyingOldRowsDoesNotAcknowledgeQueuedSelection() {
        Fixture fixture = new Fixture();
        List<HostsSource> baseline = rows(false);
        List<HostsSource> selected = rows(true);
        String fingerprint = SourceApplyState.fingerprint(baseline);
        fixture.pending.beginSelection(fixture.restore(baseline), false);
        SourceApplyState applying = fixture.restore(baseline);
        assertEquals("unconfirmed", fixture.save(applying, baseline, true));
        applying.appliedSources(baseline, null);
        assertEquals("unconfirmed", fixture.save(applying, baseline, false));

        fixture.pending.finishSelection(SourceApplyState.fingerprint(selected));
        assertEquals("unconfirmed", fixture.save(applying, baseline, false));
        applying.updateSources(selected);
        assertTrue(applying.isPending());
        assertEquals(fingerprint, fixture.save(applying, selected, false));
        applying.appliedSources(selected, null);
        assertNull(fixture.save(applying, selected, false));
    }

    @Test
    public void allQueuedWritesMustSettleBeforeReturningToQuietBaseline() {
        Fixture fixture = new Fixture();
        List<HostsSource> baseline = rows(false);
        String fingerprint = SourceApplyState.fingerprint(baseline);
        SourceApplyState state = fixture.restore(baseline);
        fixture.pending.beginSelection(state, false);
        fixture.pending.beginSelection(state, false);
        fixture.pending.finishSelection(SourceApplyState.fingerprint(rows(true)));
        assertEquals(fingerprint, fixture.save(state, baseline, false));
        assertEquals("unconfirmed", fixture.values.get("selection"));

        fixture.pending.finishSelection(fingerprint);
        assertNull(fixture.save(state, baseline, false));
        assertTrue(fixture.values.isEmpty());
        assertFalse(fixture.restore(baseline).isPending());
    }

    @Test
    public void rejectedWriteDoesNotReleaseOtherQueuedSelection() {
        Fixture fixture = new Fixture();
        List<HostsSource> baseline = rows(false);
        List<HostsSource> selected = rows(true);
        String fingerprint = SourceApplyState.fingerprint(baseline);
        SourceApplyState state = fixture.restore(baseline);
        fixture.pending.beginSelection(state, false);
        fixture.pending.beginSelection(state, false);
        fixture.pending.cancelSelection();
        assertEquals(fingerprint, fixture.save(state, baseline, false));

        fixture.pending.finishSelection(SourceApplyState.fingerprint(selected));
        assertEquals(fingerprint, fixture.save(state, baseline, false));
        SourceApplyState reopened = fixture.restore(selected);
        assertTrue(reopened.isPending());
    }

    @Test
    public void rejectedOnlyWriteLeavesSettledTaskQuiet() {
        Fixture fixture = new Fixture();
        List<HostsSource> baseline = rows(false);
        SourceApplyState state = fixture.restore(baseline);
        fixture.pending.beginSelection(state, false);
        fixture.pending.cancelSelection();

        assertNull(fixture.save(state, baseline, false));
        assertTrue(fixture.values.isEmpty());
        assertFalse(fixture.restore(baseline).isPending());
    }

    @Test
    public void processRestorationRequiresApplyForStrandedSelectionOwnership() {
        Fixture fixture = new Fixture();
        List<HostsSource> baseline = rows(false);
        fixture.pending.beginSelection(fixture.restore(baseline), false);
        fixture.pending = new PendingSourceApply(fixture.preferences);

        SourceApplyState restored = fixture.restore(baseline);
        assertTrue(restored.isPending());
        assertEquals("unconfirmed", fixture.save(restored, baseline, false));
        assertEquals(Collections.singletonMap("fingerprint", "unconfirmed"), fixture.values);
        restored.appliedSources(baseline, null);
        assertNull(fixture.save(restored, baseline, false));
        assertFalse(fixture.restore(baseline).isPending());
    }

    @Test
    public void processRestorationRetainsSettledTargetUntilMatchingRowsArrive() {
        Fixture fixture = new Fixture();
        List<HostsSource> baseline = rows(false);
        List<HostsSource> selected = rows(true);
        String fingerprint = SourceApplyState.fingerprint(baseline);
        fixture.pending.beginSelection(fixture.restore(baseline), false);
        fixture.pending.finishSelection(SourceApplyState.fingerprint(selected));
        fixture.pending = new PendingSourceApply(fixture.preferences);

        SourceApplyState restored = fixture.restore(baseline);
        assertEquals(fingerprint, fixture.save(restored, baseline, false));
        restored.updateSources(selected);
        assertTrue(restored.isPending());
        assertEquals(fingerprint, fixture.save(restored, selected, false));
        restored.appliedSources(selected, null);
        assertNull(fixture.save(restored, selected, false));
    }

    @Test
    public void unreadableFinalSnapshotRequiresFreshAcknowledgement() {
        Fixture fixture = new Fixture();
        List<HostsSource> baseline = rows(false);
        SourceApplyState state = fixture.restore(baseline);
        fixture.pending.beginSelection(state, false);
        fixture.pending.finishSelection(null);

        assertEquals("unconfirmed", fixture.save(state, baseline, false));
        assertTrue(state.isPending());
        assertFalse(fixture.values.containsKey("selection"));
        state.appliedSources(baseline, null);
        assertNull(fixture.save(state, baseline, false));
    }

    @Test
    public void runningApplyRemainsUnconfirmedAfterSelectionSettles() {
        Fixture fixture = new Fixture();
        List<HostsSource> baseline = rows(false);
        List<HostsSource> selected = rows(true);
        SourceApplyState state = fixture.restore(baseline);
        fixture.pending.beginSelection(state, false);
        fixture.pending.finishSelection(SourceApplyState.fingerprint(selected));
        state.updateSources(selected);

        assertEquals("unconfirmed", fixture.save(state, selected, true));
        SourceApplyState reopened = fixture.restore(selected);
        assertTrue(reopened.isPending());
        reopened.appliedSources(selected, null);
        assertNull(fixture.save(reopened, selected, false));
        assertFalse(fixture.restore(selected).isPending());
    }

    @Test
    public void verifiedFullSnapshotSupersedesCoalescedSelectionTarget() {
        Fixture fixture = new Fixture();
        List<HostsSource> baseline = rows(false);
        List<HostsSource> selected = rows(true);
        List<HostsSource> edited = rows(true);
        edited.get(0).setUrl("https://source.example/edited");
        SourceApplyState state = fixture.restore(baseline);
        fixture.pending.beginSelection(state, false);
        fixture.pending.finishSelection(SourceApplyState.fingerprint(selected));
        state.updateSources(edited);
        assertEquals(SourceApplyState.fingerprint(baseline), fixture.save(state, edited, false));

        long revision = fixture.pending.settledSelectionRevision();
        assertEquals("unconfirmed", fixture.save(state, edited, true));
        fixture.pending.confirmSelection(revision, SourceApplyState.fingerprint(edited));
        state.appliedSources(edited, null);
        assertNull(fixture.save(state, edited, false));
        assertTrue(fixture.values.isEmpty());
        assertFalse(fixture.restore(edited).isPending());
    }

    @Test
    public void receiptStartedWithQueuedWriteCannotReplaceFinalTarget() {
        Fixture fixture = new Fixture();
        List<HostsSource> baseline = rows(false);
        List<HostsSource> selected = rows(true);
        SourceApplyState state = fixture.restore(baseline);
        fixture.pending.beginSelection(state, false);
        long revision = fixture.pending.settledSelectionRevision();
        assertEquals(-1, revision);
        fixture.pending.confirmSelection(revision, SourceApplyState.fingerprint(baseline));
        assertEquals("unconfirmed", fixture.values.get("selection"));
        fixture.pending.finishSelection(SourceApplyState.fingerprint(selected));

        fixture.pending.confirmSelection(revision, SourceApplyState.fingerprint(baseline));
        assertEquals(SourceApplyState.fingerprint(selected), fixture.values.get("selection"));
        state.appliedSources(baseline, null);
        assertEquals(SourceApplyState.fingerprint(baseline), fixture.save(state, baseline, false));
        assertTrue(fixture.restore(selected).isPending());
    }

    @Test
    public void laterMutationInvalidatesEarlierFullSnapshotReceipt() {
        Fixture fixture = new Fixture();
        List<HostsSource> baseline = rows(false);
        List<HostsSource> selected = rows(true);
        SourceApplyState state = fixture.restore(baseline);
        long revision = fixture.pending.settledSelectionRevision();
        fixture.pending.beginSelection(state, false);
        fixture.pending.confirmSelection(revision, SourceApplyState.fingerprint(baseline));
        assertEquals("unconfirmed", fixture.values.get("selection"));
        fixture.pending.finishSelection(SourceApplyState.fingerprint(selected));

        fixture.pending.confirmSelection(revision, SourceApplyState.fingerprint(baseline));
        assertEquals(SourceApplyState.fingerprint(selected), fixture.values.get("selection"));
        assertEquals(SourceApplyState.fingerprint(baseline), fixture.save(state, baseline, false));
        assertTrue(fixture.restore(selected).isPending());
    }

    @Test
    public void confirmingSettledSelectionDoesNotCreateNewCheckpoint() {
        Fixture fixture = new Fixture();
        List<HostsSource> selected = rows(true);
        fixture.pending.confirmSelection(fixture.pending.settledSelectionRevision(),
                SourceApplyState.fingerprint(selected));

        assertTrue(fixture.values.isEmpty());
        assertFalse(fixture.restore(selected).isPending());
    }

    private static List<HostsSource> rows(boolean enabled) {
        HostsSource source = new HostsSource();
        source.setId(2);
        source.setUrl("https://source.example/2");
        source.setEnabled(enabled);
        return Collections.singletonList(source);
    }

    private static final class Fixture {
        private final Map<String, String> values = new HashMap<>();
        private final SharedPreferences preferences = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "getString":
                            return values.getOrDefault(arguments[0], (String) arguments[1]);
                        case "edit":
                            return editor();
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
        private PendingSourceApply pending = new PendingSourceApply(preferences);

        private SharedPreferences.Editor editor() {
            Map<String, String> changes = new HashMap<>();
            return (SharedPreferences.Editor) Proxy.newProxyInstance(
                    SharedPreferences.Editor.class.getClassLoader(),
                    new Class<?>[]{SharedPreferences.Editor.class},
                    (proxy, method, arguments) -> {
                        switch (method.getName()) {
                            case "putString":
                                changes.put((String) arguments[0], (String) arguments[1]);
                                return proxy;
                            case "remove":
                                changes.put((String) arguments[0], null);
                                return proxy;
                            case "apply":
                                changes.forEach((key, value) -> {
                                    if (value == null) values.remove(key);
                                    else values.put(key, value);
                                });
                                return null;
                            default:
                                throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }

        private SourceApplyState restore(List<HostsSource> sources) {
            SourceApplyState state = SourceApplyState.fromPendingCheckpoint(pending.read());
            state.updateSources(sources);
            return state;
        }

        private String save(SourceApplyState state, List<HostsSource> sources, boolean running) {
            return pending.save(state, SourceApplyState.fingerprint(sources), running);
        }
    }
}
