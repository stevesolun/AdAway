package org.adaway.model.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.adaway.db.entity.HostsSource;
import org.junit.Test;

public class FilterListsSourceMetadataTest {
    @Test
    public void applyStoresDurableFilterListsProvenance() {
        HostsSource source = new HostsSource();

        FilterListsSourceMetadata.apply(source, 42, "Example List", new int[]{1, 2},
                new int[]{7, 8}, new int[]{3},
                "https://example.test/hosts.txt");

        assertEquals(Integer.valueOf(42), source.getFilterListId());
        assertEquals("Example List", source.getFilterListName());
        assertEquals("1,2", source.getFilterListSyntaxIds());
        assertEquals("7,8", source.getFilterListTagIds());
        assertEquals("3", source.getFilterListLanguageIds());
        assertEquals("DNS-safe", source.getFilterListCompatibility());
        assertEquals(100, source.getFilterListCompatibilityScore());
        assertEquals("https://example.test/hosts.txt", source.getFilterListSelectedUrl());
    }

    @Test
    public void copyPreservesFilterListsProvenanceAcrossSourceEdit() {
        HostsSource original = new HostsSource();
        FilterListsSourceMetadata.apply(original, 77, "Original", new int[]{14},
                new int[]{10}, new int[]{2, 3},
                "https://example.test/non-localhost.txt");
        HostsSource edited = new HostsSource();

        FilterListsSourceMetadata.copy(original, edited);

        assertEquals(Integer.valueOf(77), edited.getFilterListId());
        assertEquals("Original", edited.getFilterListName());
        assertEquals("14", edited.getFilterListSyntaxIds());
        assertEquals("10", edited.getFilterListTagIds());
        assertEquals("2,3", edited.getFilterListLanguageIds());
        assertEquals("DNS-safe", edited.getFilterListCompatibility());
        assertEquals(100, edited.getFilterListCompatibilityScore());
        assertEquals("https://example.test/non-localhost.txt",
                edited.getFilterListSelectedUrl());
    }

    @Test
    public void applyClearsUnknownFilterListIdAndSyntaxes() {
        HostsSource source = new HostsSource();

        FilterListsSourceMetadata.apply(source, 0, null, null);

        assertNull(source.getFilterListId());
        assertNull(source.getFilterListSyntaxIds());
        assertNull(source.getFilterListName());
        assertNull(source.getFilterListTagIds());
        assertNull(source.getFilterListLanguageIds());
        assertEquals("Unknown syntax", source.getFilterListCompatibility());
        assertEquals(0, source.getFilterListCompatibilityScore());
        assertNull(source.getFilterListSelectedUrl());
    }
}
