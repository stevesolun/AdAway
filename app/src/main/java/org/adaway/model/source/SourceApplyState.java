package org.adaway.model.source;

import org.adaway.db.entity.HostsSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Tracks source configuration, excluding download and presentation metadata. */
public final class SourceApplyState {
    private String mCurrentConfiguration;
    private String mAppliedConfiguration;
    private String mLastCompletedWork;
    private boolean mWorkObserved;

    public void updateSources(List<HostsSource> sources) {
        if (sources == null) {
            return;
        }
        mCurrentConfiguration = fingerprint(sources);
        if (mAppliedConfiguration == null) {
            mAppliedConfiguration = mCurrentConfiguration;
        }
    }

    public boolean isPending() {
        return mCurrentConfiguration != null
                && !mCurrentConfiguration.equals(mAppliedConfiguration);
    }

    public void applied(String configuration) {
        if (configuration != null) {
            mAppliedConfiguration = configuration;
        }
    }

    public void observeAutomaticWork(String workId, String configuration) {
        // A new screen must not replay historical success over a later manual apply.
        if (!mWorkObserved) {
            mWorkObserved = true;
            mLastCompletedWork = configuration == null ? null : workId;
            return;
        }
        if (configuration != null && !workId.equals(mLastCompletedWork)) {
            mLastCompletedWork = workId;
            applied(configuration);
        }
    }

    public static String fingerprint(List<HostsSource> sources) {
        List<HostsSource> ordered = new ArrayList<>(sources);
        ordered.sort(Comparator.comparingInt(HostsSource::getId));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (HostsSource source : ordered) {
                // Length-prefix the URL so arbitrary source text cannot alias another row.
                String url = source.getUrl();
                String row = source.getId() + ":" + source.isEnabled() + ":"
                        + source.isAllowEnabled() + ":" + source.isRedirectEnabled() + ":"
                        + url.length() + ":" + url;
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
