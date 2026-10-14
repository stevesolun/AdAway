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
        state.observeAutomaticWork("import", SourceApplyState.fingerprint(sources));
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
        state.observeAutomaticWork("import", imported);
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
        state.observeAutomaticWork("historical", historical);
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
