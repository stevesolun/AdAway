package org.adaway.ui.hosts;

import static org.adaway.ui.hosts.FilterListsSubscribeAllWorker.CandidateOutcome.ALREADY;
import static org.adaway.ui.hosts.FilterListsSubscribeAllWorker.CandidateOutcome.SKIPPED_NO_URL;
import static org.adaway.ui.hosts.FilterListsSubscribeAllWorker.CandidateOutcome.SKIPPED_UNSUPPORTED;
import static org.adaway.ui.hosts.FilterListsSubscribeAllWorker.CandidateOutcome.SUBSCRIBED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.adaway.db.entity.HostsSource;
import org.adaway.model.source.FilterListsDirectoryApi;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FilterListsSubscribeAllWorkerTest {
    @Test
    public void progressUpdatesAreThrottledButKeepInitialMilestonesAndFinalState() {
        assertTrue(FilterListsSubscribeAllWorker.shouldEmitProgressUpdate(1, 100));
        assertFalse(FilterListsSubscribeAllWorker.shouldEmitProgressUpdate(2, 100));
        assertFalse(FilterListsSubscribeAllWorker.shouldEmitProgressUpdate(24, 100));
        assertTrue(FilterListsSubscribeAllWorker.shouldEmitProgressUpdate(25, 100));
        assertFalse(FilterListsSubscribeAllWorker.shouldEmitProgressUpdate(26, 100));
        assertTrue(FilterListsSubscribeAllWorker.shouldEmitProgressUpdate(100, 100));
    }

    @Test
    public void progressUpdatesAlwaysEmitFinalStateEvenBelowThrottleWindow() {
        assertFalse(FilterListsSubscribeAllWorker.shouldEmitProgressUpdate(2, 10));
        assertTrue(FilterListsSubscribeAllWorker.shouldEmitProgressUpdate(10, 10));
    }

    @Test
    public void applyCandidateClassifiesCompatibilityUrlQualityAndDuplicates() {
        Set<String> existingUrls = new HashSet<>();
        List<HostsSource> pendingInsert = new ArrayList<>();

        assertEquals(SUBSCRIBED, FilterListsSubscribeAllWorker.applyCandidate(existingUrls,
                pendingInsert, "Good hosts", new int[]{1}, " https://good.test/hosts.txt "));
        assertEquals(1, pendingInsert.size());
        assertEquals("Good hosts", pendingInsert.get(0).getLabel());
        assertEquals("https://good.test/hosts.txt", pendingInsert.get(0).getUrl());
        assertTrue(pendingInsert.get(0).isEnabled());
        assertFalse(pendingInsert.get(0).isAllowEnabled());
        assertFalse(pendingInsert.get(0).isRedirectEnabled());
        assertEquals("1", pendingInsert.get(0).getFilterListSyntaxIds());
        assertEquals("DNS-safe", pendingInsert.get(0).getFilterListCompatibility());
        assertEquals(100, pendingInsert.get(0).getFilterListCompatibilityScore());
        assertEquals("https://good.test/hosts.txt",
                pendingInsert.get(0).getFilterListSelectedUrl());

        assertEquals(ALREADY, FilterListsSubscribeAllWorker.applyCandidate(existingUrls,
                pendingInsert, "Duplicate", new int[]{1}, "https://good.test/hosts.txt"));
        assertEquals(SKIPPED_UNSUPPORTED, FilterListsSubscribeAllWorker.applyCandidate(existingUrls,
                pendingInsert, "ABP", new int[]{3}, "https://abp.test/list.txt"));
        assertEquals(SKIPPED_NO_URL, FilterListsSubscribeAllWorker.applyCandidate(existingUrls,
                pendingInsert, "No URL", new int[]{14}, null));
        assertEquals(SKIPPED_NO_URL, FilterListsSubscribeAllWorker.applyCandidate(existingUrls,
                pendingInsert, "Archive", new int[]{2}, "https://bad.test/list.zip"));
        assertEquals(1, pendingInsert.size());
        assertTrue(existingUrls.contains("https://good.test/hosts.txt"));
    }

    @Test
    public void applyCandidatePersistsFilterListsProvenanceWhenListIdIsKnown() {
        Set<String> existingUrls = new HashSet<>();
        List<HostsSource> pendingInsert = new ArrayList<>();

        assertEquals(SUBSCRIBED, FilterListsSubscribeAllWorker.applyCandidate(existingUrls,
                pendingInsert, 42, "Known list", new int[]{1, 2}, new int[]{7, 8},
                new int[]{3},
                "https://known.test/hosts.txt"));

        HostsSource source = pendingInsert.get(0);
        assertEquals(Integer.valueOf(42), source.getFilterListId());
        assertEquals("Known list", source.getFilterListName());
        assertEquals("1,2", source.getFilterListSyntaxIds());
        assertEquals("7,8", source.getFilterListTagIds());
        assertEquals("3", source.getFilterListLanguageIds());
        assertEquals("DNS-safe", source.getFilterListCompatibility());
        assertEquals(100, source.getFilterListCompatibilityScore());
        assertEquals("https://known.test/hosts.txt", source.getFilterListSelectedUrl());
    }

    @Test
    public void applyCandidateReportsAlreadyForPreexistingUrlWithoutInsert() {
        Set<String> existingUrls = new HashSet<>();
        existingUrls.add("https://existing.test/hosts.txt");
        List<HostsSource> pendingInsert = new ArrayList<>();

        assertEquals(ALREADY, FilterListsSubscribeAllWorker.applyCandidate(existingUrls,
                pendingInsert, "Existing", new int[]{1}, "https://existing.test/hosts.txt"));

        assertTrue(pendingInsert.isEmpty());
        assertEquals(1, existingUrls.size());
    }

    @Test
    public void filterListsForScopeMatchesVisibleSearchTagLanguageAndCompatibility() {
        FilterListsDirectoryApi.ListSummary exactVisible = summary(10, "Regional ads",
                "Blocks regional tracking", new int[]{1}, new int[]{7}, new int[]{2});
        FilterListsDirectoryApi.ListSummary unsupportedVisible = summary(11, "Regional browser",
                "Browser-only regional rules", new int[]{3}, new int[]{7}, new int[]{2});
        FilterListsDirectoryApi.ListSummary wrongTag = summary(12, "Regional family",
                "Blocks regional tracking", new int[]{1}, new int[]{8}, new int[]{2});
        FilterListsDirectoryApi.ListSummary wrongLanguage = summary(13, "Regional malware",
                "Blocks regional tracking", new int[]{1}, new int[]{7}, new int[]{3});
        FilterListsDirectoryApi.ListSummary wrongQuery = summary(14, "Social ads",
                "Blocks social tracking", new int[]{1}, new int[]{7}, new int[]{2});
        List<FilterListsDirectoryApi.ListSummary> lists = Arrays.asList(
                exactVisible, unsupportedVisible, wrongTag, wrongLanguage, wrongQuery);

        assertEquals(Arrays.asList(exactVisible, unsupportedVisible),
                FilterListsSubscribeAllWorker.filterListsForScope(lists,
                        " regional ", 7, 2, false));
        assertEquals(Arrays.asList(exactVisible),
                FilterListsSubscribeAllWorker.filterListsForScope(lists,
                        "REGIONAL", 7, 2, true));
    }

    @Test
    public void filterListsForScopeUsesExplicitVisibleIdsWhenProvided() {
        FilterListsDirectoryApi.ListSummary selected = summary(10, "Selected",
                "Does not match query", new int[]{1}, new int[]{1}, new int[]{1});
        FilterListsDirectoryApi.ListSummary hidden = summary(11, "Regional hidden",
                "Would match query without the id scope", new int[]{1}, new int[]{7}, new int[]{2});
        List<FilterListsDirectoryApi.ListSummary> lists = Arrays.asList(selected, hidden);

        assertEquals(Arrays.asList(selected),
                FilterListsSubscribeAllWorker.filterListsForScope(lists,
                        "regional", 7, 2, false, new int[]{10}));
    }

    private static FilterListsDirectoryApi.ListSummary summary(int id, String name,
            String description, int[] syntaxIds, int[] tagIds, int[] languageIds) {
        return new FilterListsDirectoryApi.ListSummary(id, name, description, syntaxIds, tagIds,
                languageIds);
    }
}
