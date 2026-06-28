package org.adaway.ui.hosts;

import static org.junit.Assert.assertEquals;

import org.adaway.db.entity.HostsSource;
import org.adaway.model.source.FilterListCategory;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.model.source.FilterListsSourceMetadata;
import org.junit.Test;

public class CategorizedSourcesAdapterTest {
    @Test
    public void sourceItemUsesFilterListsCategoryWhenDirectoryMetadataIsPresent() {
        HostsSource source = source("https://unlisted.example.test/hosts.txt");
        FilterListsSourceMetadata.apply(source, 42, "Directory list", new int[]{1},
                new int[]{7}, new int[]{2}, "https://unlisted.example.test/hosts.txt");

        assertEquals(FilterListCategory.FILTERLISTS,
                FilterListCatalog.getCategoryForSource(source));
        assertEquals(FilterListCategory.FILTERLISTS,
                new FilterListItem.SourceItem(source, false).getCategory());
    }

    @Test
    public void userSourcesKeepUserCategoryEvenWhenMetadataIsPresent() {
        HostsSource source = source(HostsSource.USER_SOURCE_URL + "/blocked");
        FilterListsSourceMetadata.apply(source, 42, "Directory list", new int[]{1},
                new int[]{7}, new int[]{2}, "https://example.test/hosts.txt");

        assertEquals(FilterListCategory.USER, FilterListCatalog.getCategoryForSource(source));
        assertEquals(FilterListCategory.USER,
                new FilterListItem.SourceItem(source, false).getCategory());
    }

    @Test
    public void catalogSourcesStillUseStaticCatalogCategoryWithoutDirectoryMetadata() {
        HostsSource source = source("https://adaway.org/hosts.txt");

        assertEquals(FilterListCategory.ADS, FilterListCatalog.getCategoryForSource(source));
        assertEquals(FilterListCategory.ADS,
                new FilterListItem.SourceItem(source, false).getCategory());
    }

    @Test
    public void provenanceSummaryShowsFilterListsCapabilitiesAndSkippedRows() {
        HostsSource source = source("https://example.test/hosts.txt");
        FilterListsSourceMetadata.apply(source, 42, "Example", new int[]{1},
                new int[]{7}, new int[]{2}, "https://example.test/hosts.txt");
        source.setSkippedCount(12);

        assertEquals("FilterLists.com \u2022 DNS-safe: exact hosts and plain domains"
                        + " \u2022 12 skipped",
                CategorizedSourcesAdapter.buildSourceProvenanceSummary(
                        new FilterListItem.SourceItem(source, false)));
    }

    @Test
    public void provenanceSummaryShowsLimitedSupportForUnsupportedFilterListsSyntax() {
        HostsSource source = source("https://browser-rules.test/easylist.txt");
        FilterListsSourceMetadata.apply(source, 42, "Browser rules", new int[]{3},
                new int[]{7}, new int[]{2}, "https://browser-rules.test/easylist.txt");

        assertEquals("FilterLists.com \u2022 Limited support: browser semantics skipped",
                CategorizedSourcesAdapter.buildSourceProvenanceSummary(
                        new FilterListItem.SourceItem(source, false)));
    }

    @Test
    public void provenanceSummaryShowsUnknownSyntaxForIncompleteFilterListsMetadata() {
        HostsSource source = source("https://unknown.test/list.txt");
        source.setFilterListId(42);
        source.setFilterListName("Unknown directory row");

        assertEquals("FilterLists.com \u2022 Limited support: unknown syntax",
                CategorizedSourcesAdapter.buildSourceProvenanceSummary(
                        new FilterListItem.SourceItem(source, false)));
    }

    @Test
    public void provenanceSummaryShowsSkippedRowsForNonDirectorySources() {
        HostsSource source = source("https://custom.test/hosts.txt");
        source.setSkippedCount(3);

        assertEquals("3 skipped",
                CategorizedSourcesAdapter.buildSourceProvenanceSummary(
                        new FilterListItem.SourceItem(source, false)));
    }

    @Test
    public void provenanceSummaryIsEmptyWhenThereIsNoExtraSourceHealth() {
        HostsSource source = source("https://custom.test/hosts.txt");

        assertEquals("",
                CategorizedSourcesAdapter.buildSourceProvenanceSummary(
                        new FilterListItem.SourceItem(source, false)));
    }

    private static HostsSource source(String url) {
        HostsSource source = new HostsSource();
        source.setLabel(url);
        source.setUrl(url);
        source.setEnabled(true);
        return source;
    }
}
