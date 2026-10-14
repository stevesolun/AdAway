package org.adaway.model.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.adaway.db.entity.HostsSource;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SourceApplyStateTest {
    @Test
    public void importCompletionClearsPendingAndMetadataDoesNotReopenIt() {
        SourceApplyState state = observingEmptySources();
        HostsSource source = source(2);
        List<HostsSource> sources = Collections.singletonList(source);
        state.updateSources(sources);
        assertTrue(state.isPending());
        assertTrue(state.observeAutomaticWork("import", SourceApplyState.fingerprint(sources)));
        assertFalse(state.isPending());

        source.setSize(181);
        source.setSkippedCount(1);
        source.setLabel("Renamed source");
        source.setEntityTag("new-etag");
        source.setLocalModificationDate(ZonedDateTime.now());
        source.setOnlineModificationDate(ZonedDateTime.now());
        source.setLastDownloadError("download failed");
        state.updateSources(sources);
        assertFalse(state.isPending());
    }

    @Test
    public void completionBeforeRoomEmissionDoesNotCreatePendingPrompt() {
        SourceApplyState state = observingEmptySources();
        List<HostsSource> sources = Collections.singletonList(source(2));
        state.observeAutomaticWork("import", SourceApplyState.fingerprint(sources));
        state.updateSources(sources);
        assertFalse(state.isPending());
    }

    @Test
    public void selectionChangedDuringOrAfterImportStillNeedsApply() {
        SourceApplyState state = observingEmptySources();
        HostsSource source = source(2);
        List<HostsSource> sources = Collections.singletonList(source);
        String imported = SourceApplyState.fingerprint(sources);
        source.setEnabled(false);
        state.updateSources(sources);
        state.observeAutomaticWork("import", imported);
        assertTrue(state.isPending());
        state.applied(SourceApplyState.fingerprint(sources));
        assertFalse(state.isPending());
        source.setEnabled(true);
        state.updateSources(sources);
        assertTrue(state.isPending());
        assertFalse(state.observeAutomaticWork("import", imported));
        assertTrue("Replayed success must not swallow the next selection", state.isPending());
    }

    @Test
    public void failedCancelledOrRetryingWorkDoesNotAcknowledgeSelection() {
        SourceApplyState state = observingEmptySources();
        List<HostsSource> sources = Collections.singletonList(source(2));
        state.updateSources(sources);
        state.observeAutomaticWork("retry", null);
        assertTrue(state.isPending());
        state.applied(null);
        assertTrue(state.isPending());
        state.observeAutomaticWork("retry", SourceApplyState.fingerprint(sources));
        assertFalse(state.isPending());
    }

    @Test
    public void newScreenIgnoresHistoricalWorkerAfterLaterManualApply() {
        HostsSource source = source(2);
        List<HostsSource> sources = Collections.singletonList(source);
        String historical = SourceApplyState.fingerprint(sources);
        source.setEnabled(false);

        SourceApplyState state = new SourceApplyState();
        state.updateSources(sources);
        assertFalse(state.observeAutomaticWork("historical", historical));
        assertFalse(state.isPending());

        source.setEnabled(true);
        state.updateSources(sources);
        state.observeAutomaticWork("historical", historical);
        assertTrue(state.isPending());
    }

    @Test
    public void historicalWorkerBeforeInitialRowsDoesNotReplaceBaseline() {
        SourceApplyState state = new SourceApplyState();
        state.observeAutomaticWork("historical", "old-selection");
        state.updateSources(Collections.singletonList(source(2)));
        assertFalse(state.isPending());
    }

    @Test
    public void runningWorkerOnNewScreenCanStillAcknowledgeCompletion() {
        SourceApplyState state = new SourceApplyState();
        state.updateSources(Collections.emptyList());
        state.observeAutomaticWork("running", null);
        List<HostsSource> sources = Collections.singletonList(source(2));
        state.updateSources(sources);
        state.observeAutomaticWork("running", SourceApplyState.fingerprint(sources));
        assertFalse(state.isPending());
    }

    @Test
    public void fingerprintIsOrderIndependentAndIncludesAllRuleSelectionFields() {
        HostsSource a = source(2);
        HostsSource b = source(3);
        List<HostsSource> sources = Arrays.asList(a, b);
        String original = SourceApplyState.fingerprint(sources);
        assertEquals(64, original.length());
        assertEquals(original, SourceApplyState.fingerprint(Arrays.asList(b, a)));
        b.setAllowEnabled(true);
        assertNotEquals(original, SourceApplyState.fingerprint(sources));
        b.setAllowEnabled(false);
        b.setRedirectEnabled(true);
        assertNotEquals(original, SourceApplyState.fingerprint(sources));
        b.setRedirectEnabled(false);
        b.setUrl("https://changed.example/hosts");
        assertNotEquals(original, SourceApplyState.fingerprint(sources));
        assertNotEquals(original, SourceApplyState.fingerprint(Collections.singletonList(a)));
    }

    @Test
    public void targetedUpdateRetainsUnrelatedReenabledSelection() {
        SourceApplyState state = new SourceApplyState();
        HostsSource a = source(2);
        HostsSource b = source(3);
        List<HostsSource> sources = Arrays.asList(a, b);
        state.updateSources(sources);
        b.setEnabled(false);
        state.updateSources(sources);
        state.appliedSources(sources, null);
        assertFalse(state.isPending());

        b.setEnabled(true);
        state.updateSources(sources);
        state.appliedSources(sources, a.getId());
        assertTrue(state.isPending());

        state.appliedSources(sources, b.getId());
        assertFalse(state.isPending());
    }

    @Test
    public void targetedUpdateAcknowledgesOnlyItsOwnPendingSelection() {
        SourceApplyState state = new SourceApplyState();
        HostsSource a = source(2);
        HostsSource b = source(3);
        List<HostsSource> sources = Arrays.asList(a, b);
        state.updateSources(sources);
        a.setAllowEnabled(true);
        b.setRedirectEnabled(true);
        state.updateSources(sources);

        state.appliedSources(sources, a.getId());
        assertTrue(state.isPending());
        b.setRedirectEnabled(false);
        state.updateSources(sources);
        assertFalse(state.isPending());
    }

    @Test
    public void targetedUpdateRetainsUnrelatedRemovedSource() {
        SourceApplyState state = new SourceApplyState();
        HostsSource a = source(2);
        HostsSource b = source(3);
        state.updateSources(Arrays.asList(a, b));
        List<HostsSource> sources = Collections.singletonList(a);
        state.updateSources(sources);
        state.appliedSources(sources, a.getId());
        assertTrue(state.isPending());
        state.appliedSources(sources, null);
        assertFalse(state.isPending());
    }

    @Test
    public void targetedCompletionBeforeRoomKeepsLaterSelectionPending() {
        SourceApplyState state = new SourceApplyState();
        HostsSource a = source(2);
        List<HostsSource> sources = Collections.singletonList(a);
        state.updateSources(sources);
        a.setAllowEnabled(true);
        state.appliedSources(sources, a.getId());
        a.setRedirectEnabled(true);
        state.updateSources(sources);
        assertTrue(state.isPending());
        a.setRedirectEnabled(false);
        state.updateSources(sources);
        assertFalse(state.isPending());
    }

    @Test
    public void targetedCompletionDoesNotAcknowledgeNewerObservedSelection() {
        SourceApplyState state = new SourceApplyState();
        state.updateSources(Collections.singletonList(source(2)));
        HostsSource completed = source(2);
        completed.setAllowEnabled(true);
        HostsSource current = source(2);
        current.setAllowEnabled(true);
        current.setRedirectEnabled(true);
        state.updateSources(Collections.singletonList(current));

        state.appliedSources(Collections.singletonList(completed), completed.getId());
        assertTrue(state.isPending());
        current.setRedirectEnabled(false);
        state.updateSources(Collections.singletonList(current));
        assertFalse(state.isPending());
    }

    @Test
    public void fullCompletionBeforeInitialRoomSnapshotKeepsCapturedBaseline() {
        SourceApplyState state = new SourceApplyState();
        HostsSource a = source(2);
        List<HostsSource> sources = Collections.singletonList(a);
        state.appliedSources(sources, null);
        a.setEnabled(false);
        state.updateSources(sources);
        assertTrue(state.isPending());
        a.setEnabled(true);
        state.updateSources(sources);
        assertFalse(state.isPending());
    }

    @Test
    public void targetedUpdateCannotReplaceUnknownAutomaticCompletionBaseline() {
        SourceApplyState state = observingEmptySources();
        HostsSource a = source(2);
        HostsSource b = source(3);
        List<HostsSource> sources = Arrays.asList(a, b);
        state.observeAutomaticWork("import", SourceApplyState.fingerprint(sources));
        b.setEnabled(false);
        state.updateSources(sources);
        state.appliedSources(sources, a.getId());
        assertTrue(state.isPending());

        b.setEnabled(true);
        state.updateSources(sources);
        assertFalse(state.isPending());
        b.setEnabled(false);
        state.updateSources(sources);
        state.appliedSources(sources, b.getId());
        assertFalse(state.isPending());
    }

    @Test
    public void uncertainCompletionStaysPendingAfterSelectionReturnsToBaseline() {
        SourceApplyState state = new SourceApplyState();
        HostsSource a = source(2);
        HostsSource b = source(3);
        List<HostsSource> sources = Arrays.asList(a, b);
        state.updateSources(sources);
        b.setEnabled(false);
        state.updateSources(sources);
        b.setEnabled(true);
        state.updateSources(sources);
        state.invalidate();
        assertTrue(state.isPending());

        b.setSize(0);
        state.updateSources(sources);
        state.applied(null);
        assertTrue(state.isPending());
        state.appliedSources(sources, a.getId());
        state.appliedSources(sources, b.getId());
        assertTrue(state.isPending());

        state.appliedSources(sources, null);
        assertFalse(state.isPending());
    }

    @Test
    public void freshAutomaticCompletionResolvesUncertaintyButReplayDoesNot() {
        SourceApplyState state = observingEmptySources();
        List<HostsSource> sources = Collections.singletonList(source(2));
        state.updateSources(sources);
        state.appliedSources(sources, null);
        state.invalidate();
        assertTrue(state.isPending());

        String configuration = SourceApplyState.fingerprint(sources);
        assertTrue(state.observeAutomaticWork("completed", configuration));
        assertFalse(state.isPending());
        state.invalidate();
        assertFalse(state.observeAutomaticWork("completed", configuration));
        assertTrue(state.isPending());
        assertTrue(state.observeAutomaticWork("next-completion", configuration));
        assertFalse(state.isPending());
    }

    private static SourceApplyState observingEmptySources() {
        SourceApplyState state = new SourceApplyState();
        state.updateSources(Collections.emptyList());
        state.observeAutomaticWork(null, null);
        return state;
    }

    private static HostsSource source(int id) {
        HostsSource source = new HostsSource();
        source.setId(id);
        source.setLabel("Source " + id);
        source.setUrl("https://source.example/" + id);
        return source;
    }
}
