package org.adaway.ui.hosts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Impact summary for applying a saved filter profile to the currently available sources.
 */
public final class FilterProfileDiff {
    private final int enableCount;
    private final int disableCount;
    private final int keepEnabledCount;
    private final int missingCount;
    private final int targetCount;

    private FilterProfileDiff(int enableCount, int disableCount, int keepEnabledCount,
            int missingCount, int targetCount) {
        this.enableCount = enableCount;
        this.disableCount = disableCount;
        this.keepEnabledCount = keepEnabledCount;
        this.missingCount = missingCount;
        this.targetCount = targetCount;
    }

    @NonNull
    public static FilterProfileDiff resolve(@Nullable Set<String> currentEnabledUrls,
            @Nullable Set<String> targetProfileUrls, @Nullable Set<String> availableUrls) {
        Set<String> current = copyOf(currentEnabledUrls);
        Set<String> target = copyOf(targetProfileUrls);
        Set<String> available = copyOf(availableUrls);

        int enable = 0;
        int disable = 0;
        int keepEnabled = 0;
        for (String url : available) {
            boolean enabled = current.contains(url);
            boolean targetEnabled = target.contains(url);
            if (!enabled && targetEnabled) {
                enable++;
            } else if (enabled && !targetEnabled) {
                disable++;
            } else if (enabled) {
                keepEnabled++;
            }
        }

        Set<String> missing = new HashSet<>(target);
        missing.removeAll(available);
        return new FilterProfileDiff(enable, disable, keepEnabled, missing.size(),
                target.size());
    }

    public int getEnableCount() {
        return this.enableCount;
    }

    public int getDisableCount() {
        return this.disableCount;
    }

    public int getUnchangedCount() {
        return this.keepEnabledCount;
    }

    public int getKeepEnabledCount() {
        return this.keepEnabledCount;
    }

    public int getMissingCount() {
        return this.missingCount;
    }

    public boolean isEmptyProfile() {
        return this.targetCount == 0;
    }

    public boolean isMissingOnlyProfile() {
        return this.targetCount > 0 && this.missingCount == this.targetCount;
    }

    public boolean hasChanges() {
        return this.enableCount > 0 || this.disableCount > 0;
    }

    public boolean weakensProtection() {
        return this.disableCount > 0;
    }

    public boolean disablesAllEnabledSources() {
        return this.disableCount > 0 && this.enableCount == 0 && this.keepEnabledCount == 0;
    }

    @NonNull
    private static Set<String> copyOf(@Nullable Set<String> urls) {
        return urls == null ? Collections.emptySet() : new HashSet<>(urls);
    }
}
