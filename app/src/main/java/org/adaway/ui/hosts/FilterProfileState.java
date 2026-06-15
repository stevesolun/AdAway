package org.adaway.ui.hosts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;

/**
 * Relationship between an active profile's baseline URLs and currently enabled sources.
 */
public enum FilterProfileState {
    NONE,
    EXACT,
    EXTENDED,
    PARTIAL;

    @NonNull
    public static FilterProfileState resolve(
            @Nullable Set<String> profileUrls,
            @Nullable Set<String> enabledUrls) {
        if (profileUrls == null || profileUrls.isEmpty()) {
            return NONE;
        }
        if (enabledUrls == null || enabledUrls.isEmpty()) {
            return PARTIAL;
        }
        if (enabledUrls.equals(profileUrls)) {
            return EXACT;
        }
        if (enabledUrls.containsAll(profileUrls)) {
            return EXTENDED;
        }
        return PARTIAL;
    }
}
