package org.adaway.model.source;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FilterOperationStateTest {

    @Test
    public void fromMultiPhaseProgress_mapsActiveDownloadPhase() {
        SourceModel.MultiPhaseProgress progress = new SourceModel.MultiPhaseProgress(
                2, 2,
                1, 2, 1,
                0, 2, 2,
                100L,
                "Daily update",
                false,
                false,
                false,
                false,
                "Example",
                100,
                50,
                0,
                50);

        FilterOperationState state =
                FilterOperationState.fromMultiPhaseProgress(progress, 10L, 260L);

        assertEquals(FilterOperationState.Kind.SOURCE_UPDATE, state.kind);
        assertEquals(FilterOperationState.Phase.DOWNLOAD, state.phase);
        assertEquals(2, state.totalSources);
        assertEquals(50, state.overallPercent);
        assertEquals(100L, state.parsedHostCount);
        assertEquals("Example", state.currentLabel);
        assertEquals("Daily update", state.schedulerTaskName);
        assertEquals(10L, state.startedElapsedMs);
        assertEquals(260L, state.emittedElapsedMs);
    }

    @Test
    public void fromMultiPhaseProgress_mapsCompleteBeforeIdle() {
        SourceModel.MultiPhaseProgress progress = new SourceModel.MultiPhaseProgress(
                2, 2,
                2, 2, 0,
                2, 2, 0,
                500L,
                null,
                false,
                false,
                false,
                true,
                null,
                100,
                100,
                100,
                100);

        FilterOperationState state =
                FilterOperationState.fromMultiPhaseProgress(progress, 10L, 500L);

        assertEquals(FilterOperationState.Kind.SOURCE_UPDATE, state.kind);
        assertEquals(FilterOperationState.Phase.COMPLETE, state.phase);
        assertEquals(100, state.overallPercent);
    }

    @Test
    public void fromMultiPhaseProgress_mapsFinalizingBeforeComplete() {
        SourceModel.MultiPhaseProgress progress = new SourceModel.MultiPhaseProgress(
                2, 2,
                2, 2, 0,
                2, 2, 0,
                500L,
                null,
                false,
                false,
                true,
                false,
                null,
                100,
                100,
                100,
                100);

        FilterOperationState state =
                FilterOperationState.fromMultiPhaseProgress(progress, 10L, 500L);

        assertEquals(FilterOperationState.Kind.SOURCE_UPDATE, state.kind);
        assertEquals(FilterOperationState.Phase.FINALIZE, state.phase);
        assertEquals(100, state.overallPercent);
    }

    @Test
    public void fromMultiPhaseProgress_mapsStoppedTerminalState() {
        SourceModel.MultiPhaseProgress progress = new SourceModel.MultiPhaseProgress(
                2, 2,
                1, 2, 1,
                1, 2, 1,
                100L,
                null,
                false,
                true,
                false,
                false,
                null,
                100,
                50,
                50,
                65);

        FilterOperationState state =
                FilterOperationState.fromMultiPhaseProgress(progress, 10L, 500L);

        assertEquals(FilterOperationState.Kind.SOURCE_UPDATE, state.kind);
        assertEquals(FilterOperationState.Phase.STOPPED, state.phase);
        assertEquals(65, state.overallPercent);
    }

    @Test
    public void fromMultiPhaseProgress_mapsNoWorkToIdle() {
        FilterOperationState state = FilterOperationState.fromMultiPhaseProgress(
                SourceModel.MultiPhaseProgress.idle(), 0L, 0L);

        assertEquals(FilterOperationState.Kind.IDLE, state.kind);
        assertEquals(FilterOperationState.Phase.IDLE, state.phase);
    }
}
