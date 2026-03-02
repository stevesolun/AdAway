package org.adaway.ui.lists;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the search bar wiring contract.
 *
 * Covers:
 *  1. ListsFilter core behaviour (query round-trip, sqlQuery conversion)
 *  2. ListsFilter.isEmpty() — new helper method used by the search-bar
 *     view-restoration path in AbstractListFragment.onViewCreated()
 */
public class ListsSearchBarTest {

    // --- Core ListsFilter behaviour (must stay green) ---

    @Test
    public void emptyQueryProducesMatchAllSqlPattern() {
        ListsFilter filter = new ListsFilter(true, "");
        assertEquals("%%", filter.sqlQuery);
    }

    @Test
    public void nonEmptyQueryIsPreservedOnFilter() {
        ListsFilter filter = new ListsFilter(true, "example.com");
        assertEquals("example.com", filter.query);
    }

    @Test
    public void nonEmptyQueryIsWrappedWithWildcardsInSqlQuery() {
        ListsFilter filter = new ListsFilter(true, "example.com");
        assertEquals("%example.com%", filter.sqlQuery);
    }

    @Test
    public void wildcardStarIsConvertedToSqlPercent() {
        ListsFilter filter = new ListsFilter(true, "*.com");
        assertEquals("%%.com%", filter.sqlQuery);
    }

    @Test
    public void wildcardQuestionMarkIsConvertedToSqlUnderscore() {
        ListsFilter filter = new ListsFilter(true, "ad?.net");
        assertEquals("%ad_.net%", filter.sqlQuery);
    }

    @Test
    public void searchQueryRoundTripsViaNewFilter() {
        String query = "tracker";
        ListsFilter previous = ListsFilter.ALL;
        ListsFilter newFilter = new ListsFilter(previous.sourcesIncluded, query);
        assertEquals(query, newFilter.query);
    }

    @Test
    public void clearSearchProducesEmptyQuery() {
        ListsFilter active = new ListsFilter(true, "tracker");
        ListsFilter cleared = new ListsFilter(active.sourcesIncluded, "");
        assertTrue(cleared.query.isEmpty());
        assertEquals("%%", cleared.sqlQuery);
    }

    // --- NEW: ListsFilter.isEmpty() — drives the search-bar restore condition ---
    // The search bar's onViewCreated calls:
    //   if (!currentQuery.isEmpty()) { searchEditText.setText(currentQuery); }
    // We expose isEmpty() on ListsFilter itself so callers don't reach into
    // the raw query field.  These tests are RED until isEmpty() is added.

    @Test
    public void isEmptyReturnsTrueForBlankFilter() {
        ListsFilter filter = new ListsFilter(true, "");
        assertTrue("ALL/blank filter must report isEmpty()", filter.isEmpty());
    }

    @Test
    public void isEmptyReturnsFalseWhenQueryPresent() {
        ListsFilter filter = new ListsFilter(true, "ads");
        assertFalse("filter with query must not report isEmpty()", filter.isEmpty());
    }

    @Test
    public void allConstantIsEmpty() {
        assertTrue("ListsFilter.ALL must report isEmpty()", ListsFilter.ALL.isEmpty());
    }
}
