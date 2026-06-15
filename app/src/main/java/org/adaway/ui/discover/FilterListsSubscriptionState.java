package org.adaway.ui.discover;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.adaway.model.source.FilterListCompatibility;
import org.adaway.model.source.FilterListsDirectoryApi;

import java.util.List;
import java.util.Set;

final class FilterListsSubscriptionState {

    static final int NONE = 0;
    static final int PARTIAL = 1;
    static final int ALL = 2;

    private FilterListsSubscriptionState() {
    }

    static int resolve(
            @NonNull List<FilterListsDirectoryApi.ListSummary> summaries,
            @NonNull UrlResolver urlResolver,
            @NonNull Set<String> existingUrls) {
        boolean hasCompatible = false;
        boolean hasSubscribed = false;
        boolean allSubscribed = true;

        for (FilterListsDirectoryApi.ListSummary summary : summaries) {
            if (!isCompatible(summary.syntaxIds)) {
                continue;
            }
            hasCompatible = true;

            String url = urlResolver.getUrl(summary.id);
            boolean subscribed = url != null && !url.isEmpty() && existingUrls.contains(url);
            hasSubscribed |= subscribed;
            allSubscribed &= subscribed;
        }

        if (!hasCompatible || !hasSubscribed) {
            return NONE;
        }
        return allSubscribed ? ALL : PARTIAL;
    }

    static boolean isCompatible(@Nullable int[] syntaxIds) {
        return FilterListCompatibility.isBulkSafe(syntaxIds);
    }

    interface UrlResolver {
        @Nullable
        String getUrl(int id);
    }
}
