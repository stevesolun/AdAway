package org.adaway.model.source;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Shared operation state for filter update/import flows.
 */
public final class FilterOperationState {
    public enum Kind {
        IDLE,
        SOURCE_UPDATE
    }

    public enum Phase {
        IDLE,
        CHECK,
        DOWNLOAD,
        PARSE,
        FINALIZE,
        STOPPED,
        COMPLETE
    }

    @NonNull
    public final Kind kind;
    @NonNull
    public final Phase phase;
    public final int totalSources;
    public final int checkedCount;
    public final int downloadedCount;
    public final int parsedCount;
    public final int overallPercent;
    public final long parsedHostCount;
    @Nullable
    public final String currentLabel;
    @Nullable
    public final String schedulerTaskName;
    public final long startedElapsedMs;
    public final long emittedElapsedMs;
    public final boolean paused;
    public final boolean stopped;

    private FilterOperationState(@NonNull Kind kind, @NonNull Phase phase, int totalSources,
            int checkedCount, int downloadedCount, int parsedCount, int overallPercent,
            long parsedHostCount, @Nullable String currentLabel, @Nullable String schedulerTaskName,
            long startedElapsedMs, long emittedElapsedMs, boolean paused, boolean stopped) {
        this.kind = kind;
        this.phase = phase;
        this.totalSources = Math.max(0, totalSources);
        this.checkedCount = Math.max(0, checkedCount);
        this.downloadedCount = Math.max(0, downloadedCount);
        this.parsedCount = Math.max(0, parsedCount);
        this.overallPercent = Math.max(0, Math.min(100, overallPercent));
        this.parsedHostCount = Math.max(0L, parsedHostCount);
        this.currentLabel = currentLabel;
        this.schedulerTaskName = schedulerTaskName;
        this.startedElapsedMs = Math.max(0L, startedElapsedMs);
        this.emittedElapsedMs = Math.max(0L, emittedElapsedMs);
        this.paused = paused;
        this.stopped = stopped;
    }

    @NonNull
    public static FilterOperationState idle() {
        return new FilterOperationState(Kind.IDLE, Phase.IDLE, 0, 0, 0, 0, 0,
                0L, null, null, 0L, 0L, false, false);
    }

    @NonNull
    static FilterOperationState fromMultiPhaseProgress(
            @NonNull SourceModel.MultiPhaseProgress progress, long startedElapsedMs,
            long emittedElapsedMs) {
        if (progress.totalToCheck <= 0) {
            return idle();
        }

        Phase phase;
        if (progress.isStopped) {
            phase = Phase.STOPPED;
        } else if (progress.isFinalizing) {
            phase = Phase.FINALIZE;
        } else if (!progress.isActive()) {
            phase = Phase.COMPLETE;
        } else if (progress.getCheckPercent() < 100) {
            phase = Phase.CHECK;
        } else if (progress.getDownloadPercent() < 100) {
            phase = Phase.DOWNLOAD;
        } else if (progress.getParsePercent() < 100) {
            phase = Phase.PARSE;
        } else {
            phase = Phase.COMPLETE;
        }

        return new FilterOperationState(
                Kind.SOURCE_UPDATE,
                phase,
                progress.totalToCheck,
                progress.checkedCount,
                progress.downloadedCount,
                progress.parsedCount,
                progress.getOverallPercent(),
                progress.parsedHostCount,
                progress.currentLabel,
                progress.schedulerTaskName,
                startedElapsedMs,
                emittedElapsedMs,
                progress.isPaused,
                progress.isStopped);
    }
}
