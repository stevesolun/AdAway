package org.adaway.model.source;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.adaway.db.entity.HostsSource;

/**
 * Applies durable FilterLists.com provenance to source rows.
 */
public final class FilterListsSourceMetadata {
    private FilterListsSourceMetadata() {
    }

    public static void apply(
            @NonNull HostsSource source,
            int filterListId,
            @Nullable int[] syntaxIds,
            @Nullable String selectedUrl) {
        apply(source, filterListId, null, syntaxIds, null, null, selectedUrl);
    }

    public static void apply(
            @NonNull HostsSource source,
            int filterListId,
            @Nullable String name,
            @Nullable int[] syntaxIds,
            @Nullable int[] tagIds,
            @Nullable int[] languageIds,
            @Nullable String selectedUrl) {
        source.setFilterListId(filterListId > 0 ? filterListId : null);
        source.setFilterListSyntaxIds(FilterListCompatibility.encodeSyntaxIds(syntaxIds));
        source.setFilterListCompatibility(FilterListCompatibility.describe(syntaxIds));
        source.setFilterListCompatibilityScore(FilterListCompatibility.score(syntaxIds));
        source.setFilterListSelectedUrl(selectedUrl);
        source.setFilterListName(name);
        source.setFilterListTagIds(encodeIds(tagIds));
        source.setFilterListLanguageIds(encodeIds(languageIds));
    }

    public static void copy(@NonNull HostsSource from, @NonNull HostsSource to) {
        to.setFilterListId(from.getFilterListId());
        to.setFilterListSyntaxIds(from.getFilterListSyntaxIds());
        to.setFilterListCompatibility(from.getFilterListCompatibility());
        to.setFilterListCompatibilityScore(from.getFilterListCompatibilityScore());
        to.setFilterListSelectedUrl(from.getFilterListSelectedUrl());
        to.setFilterListName(from.getFilterListName());
        to.setFilterListTagIds(from.getFilterListTagIds());
        to.setFilterListLanguageIds(from.getFilterListLanguageIds());
    }

    @Nullable
    private static String encodeIds(@Nullable int[] ids) {
        if (ids == null || ids.length == 0) {
            return null;
        }

        StringBuilder encoded = new StringBuilder();
        for (int id : ids) {
            if (encoded.length() > 0) {
                encoded.append(',');
            }
            encoded.append(id);
        }
        return encoded.toString();
    }
}
