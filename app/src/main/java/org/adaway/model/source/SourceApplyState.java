package org.adaway.model.source;

import org.adaway.db.entity.HostsSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Tracks source configuration, excluding download and presentation metadata. */
public final class SourceApplyState {
    private String mCurrentConfiguration;
    private String mAppliedConfiguration;
    private Map<Integer, String> mCurrentSources;
    private Map<Integer, String> mAppliedSources;
    // Partial completions while we have a fingerprint but not its matching Room rows.
    private final Map<Integer, String> mTargetedChanges = new TreeMap<>();
    private String mLastCompletedWork;
    private String mDownloadedConfiguration;
    private Map<Integer, String> mDownloadedSources;
    private Integer mDownloadedSourceId;
    private String mRestoredDownloadWork;
    private boolean mWorkObserved;
    private boolean mAcknowledgementUncertain;

    public SourceApplyState() {
        this(null, false);
    }

    public SourceApplyState(String appliedConfiguration, boolean uncertain) {
        this(appliedConfiguration, uncertain, null);
    }

    public SourceApplyState(String appliedConfiguration, boolean uncertain, String downloadedWork) {
        mAppliedConfiguration = appliedConfiguration;
        mAcknowledgementUncertain = uncertain;
        mRestoredDownloadWork = downloadedWork;
    }

    public String getAppliedConfiguration() {
        return mAppliedConfiguration;
    }

    public boolean isUncertainOnRestore() {
        return mAcknowledgementUncertain || !mTargetedChanges.isEmpty()
                || mDownloadedSources != null;
    }

    public String getDownloadedWorkToRestore() {
        // Restore from this worker's immutable output, not from arbitrary historical success.
        return mDownloadedSources != null ? null
                : mDownloadedConfiguration != null ? mLastCompletedWork : mRestoredDownloadWork;
    }

    public boolean hasMatchingDownload() {
        return mDownloadedConfiguration != null
                && mDownloadedConfiguration.equals(mCurrentConfiguration);
    }

    public boolean acknowledgeDownload(boolean tunnelEstablished) {
        if (!tunnelEstablished || !hasMatchingDownload()) return false;
        if (mDownloadedSources == null) {
            applied(mDownloadedConfiguration);
        } else {
            appliedSources(mDownloadedSources, mDownloadedSourceId);
        }
        return true;
    }

    public void downloadedSources(List<HostsSource> sources, Integer sourceId) {
        forgetDownload();
        mDownloadedSources = snapshot(sources);
        mDownloadedSourceId = sourceId;
        mDownloadedConfiguration = fingerprint(mDownloadedSources);
    }

    public void updateSources(List<HostsSource> sources) {
        if (sources == null) {
            return;
        }
        boolean matchedDownload = hasMatchingDownload();
        mCurrentSources = snapshot(sources);
        mCurrentConfiguration = fingerprint(mCurrentSources);
        if (matchedDownload && !hasMatchingDownload()) forgetDownload();
        if (mAppliedConfiguration == null) {
            mAppliedConfiguration = mCurrentConfiguration;
        }
        if (!mAcknowledgementUncertain && mCurrentConfiguration.equals(mAppliedConfiguration)) {
            recoverAppliedSources(mCurrentSources);
        }
    }

    public boolean isPending() {
        return mCurrentConfiguration != null
                && (mAcknowledgementUncertain || mDownloadedConfiguration != null
                    || !mCurrentConfiguration.equals(mAppliedConfiguration));
    }

    public void invalidate() {
        forgetDownload();
        mAcknowledgementUncertain = true;
        mAppliedSources = null;
        mTargetedChanges.clear();
    }

    public void applied(String configuration) {
        if (configuration != null) {
            forgetDownload();
            mTargetedChanges.clear();
            mAcknowledgementUncertain = false;
            mAppliedConfiguration = configuration;
            mAppliedSources = configuration.equals(mCurrentConfiguration)
                    ? new TreeMap<>(mCurrentSources) : null;
        }
    }

    public void appliedSources(List<HostsSource> sources, Integer sourceId) {
        appliedSources(snapshot(sources), sourceId);
    }

    private void appliedSources(Map<Integer, String> completedSources, Integer sourceId) {
        forgetDownload();
        if (sourceId == null) {
            mAcknowledgementUncertain = false;
            mTargetedChanges.clear();
            mAppliedSources = completedSources;
        } else {
            if (mAcknowledgementUncertain) {
                return;
            }
            if (mAppliedSources == null) {
                if (mAppliedConfiguration == null) {
                    invalidate();
                    return;
                }
                mTargetedChanges.put(sourceId, completedSources.get(sourceId));
                if (mAppliedConfiguration.equals(fingerprint(completedSources))) {
                    recoverAppliedSources(completedSources);
                }
                return;
            }
            setAppliedRow(sourceId, completedSources.get(sourceId));
        }
        mAppliedConfiguration = fingerprint(mAppliedSources);
    }

    private void recoverAppliedSources(Map<Integer, String> baseline) {
        mAppliedSources = new TreeMap<>(baseline);
        if (!mTargetedChanges.isEmpty()) {
            mTargetedChanges.forEach(this::setAppliedRow);
            mTargetedChanges.clear();
            mAppliedConfiguration = fingerprint(mAppliedSources);
        }
    }

    private void setAppliedRow(int id, String row) {
        if (row == null) mAppliedSources.remove(id);
        else mAppliedSources.put(id, row);
    }

    public boolean observeAutomaticWork(String workId, boolean successful, String configuration) {
        return observeAutomaticWork(workId, successful, configuration, null);
    }

    private void forgetDownload() {
        mDownloadedConfiguration = null;
        mDownloadedSources = null;
        mDownloadedSourceId = null;
        mRestoredDownloadWork = null;
    }

    public boolean observeAutomaticWork(String workId, boolean successful, String configuration,
                                        String downloaded) {
        // A new screen must not replay historical success over a later manual apply.
        if (!mWorkObserved) {
            mWorkObserved = true;
            boolean restoring = successful && downloaded != null && workId != null
                    && workId.equals(mRestoredDownloadWork);
            mRestoredDownloadWork = null;
            if (!restoring) {
                mLastCompletedWork = successful ? workId : null;
                return false;
            }
        }
        if (workId != null && !workId.equals(mLastCompletedWork)) forgetDownload();
        if (successful && workId != null && !workId.equals(mLastCompletedWork)) {
            mLastCompletedWork = workId;
            if (configuration == null) {
                invalidate();
                mDownloadedConfiguration = downloaded;
            } else {
                applied(configuration);
            }
            return true;
        }
        return false;
    }

    public static String fingerprint(List<HostsSource> sources) {
        return fingerprint(snapshot(sources));
    }

    private static Map<Integer, String> snapshot(List<HostsSource> sources) {
        Map<Integer, String> snapshot = new TreeMap<>();
        for (HostsSource source : sources) {
            String url = source.getUrl();
            String row = source.getId() + ":" + source.isEnabled() + ":"
                    + source.isAllowEnabled() + ":" + source.isRedirectEnabled() + ":"
                    + url.length() + ":" + url;
            snapshot.put(source.getId(), row);
        }
        return snapshot;
    }

    private static String fingerprint(Map<Integer, String> sources) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String row : sources.values()) {
                digest.update(row.getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) {
                result.append(Character.forDigit((value >>> 4) & 15, 16));
                result.append(Character.forDigit(value & 15, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
