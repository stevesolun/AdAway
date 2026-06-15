package org.adaway.ui.discover;

import static org.junit.Assert.assertEquals;

import org.adaway.model.source.FilterListsDirectoryApi;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FilterListsSubscriptionStateTest {

    @Test
    public void resolve_noSubscribedCompatibleRows_returnsNone() {
        Map<Integer, String> urls = urls(
                1, "https://example.test/one.txt",
                2, "https://example.test/two.txt");

        assertEquals(FilterListsSubscriptionState.NONE,
                FilterListsSubscriptionState.resolve(
                        lists(safe(1), safe(2)), urls::get, set()));
    }

    @Test
    public void resolve_someSubscribedCompatibleRows_returnsPartial() {
        Map<Integer, String> urls = urls(
                1, "https://example.test/one.txt",
                2, "https://example.test/two.txt");

        assertEquals(FilterListsSubscriptionState.PARTIAL,
                FilterListsSubscriptionState.resolve(
                        lists(safe(1), safe(2)), urls::get,
                        set("https://example.test/one.txt")));
    }

    @Test
    public void resolve_allSubscribedCompatibleRows_returnsAll() {
        Map<Integer, String> urls = urls(
                1, "https://example.test/one.txt",
                2, "https://example.test/two.txt");

        assertEquals(FilterListsSubscriptionState.ALL,
                FilterListsSubscriptionState.resolve(
                        lists(safe(1), safe(2)), urls::get,
                        set("https://example.test/one.txt", "https://example.test/two.txt")));
    }

    @Test
    public void resolve_unsupportedRowsDoNotBlockAllState() {
        Map<Integer, String> urls = urls(
                1, "https://example.test/one.txt",
                2, "https://example.test/browser-rules.txt");

        assertEquals(FilterListsSubscriptionState.ALL,
                FilterListsSubscriptionState.resolve(
                        lists(safe(1), unsupported(2)), urls::get,
                        set("https://example.test/one.txt")));
    }

    @Test
    public void resolve_missingOrEmptyCachedUrlsAreNotSubscribed() {
        Map<Integer, String> urls = urls(
                1, "",
                2, "https://example.test/two.txt");

        assertEquals(FilterListsSubscriptionState.PARTIAL,
                FilterListsSubscriptionState.resolve(
                        lists(safe(1), safe(2), safe(3)), urls::get,
                        set("https://example.test/two.txt")));
    }

    private static FilterListsDirectoryApi.ListSummary safe(int id) {
        return new FilterListsDirectoryApi.ListSummary(
                id, "Safe " + id, null, new int[]{1}, new int[0], new int[0]);
    }

    private static FilterListsDirectoryApi.ListSummary unsupported(int id) {
        return new FilterListsDirectoryApi.ListSummary(
                id, "Unsupported " + id, null, new int[]{3}, new int[0], new int[0]);
    }

    private static List<FilterListsDirectoryApi.ListSummary> lists(
            FilterListsDirectoryApi.ListSummary... summaries) {
        return Arrays.asList(summaries);
    }

    private static Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private static Map<Integer, String> urls(Object... pairs) {
        Map<Integer, String> result = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((Integer) pairs[i], (String) pairs[i + 1]);
        }
        return result;
    }
}
