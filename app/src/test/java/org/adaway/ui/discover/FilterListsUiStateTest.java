package org.adaway.ui.discover;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FilterListsUiStateTest {

    @Test
    public void resolve_loadingWithNoRows_showsLoadingState() {
        assertEquals(FilterListsUiState.LOADING,
                FilterListsUiState.resolve(true, true, 0, 0, true));
    }

    @Test
    public void resolve_loadingWithVisibleRows_hidesInlineState() {
        assertEquals(FilterListsUiState.EMPTY_HIDDEN,
                FilterListsUiState.resolve(true, false, 10, 3, false));
    }

    @Test
    public void resolve_failedWithNoRows_showsLoadFailed() {
        assertEquals(FilterListsUiState.LOAD_FAILED,
                FilterListsUiState.resolve(false, true, 0, 0, false));
    }

    @Test
    public void resolve_emptyDirectory_showsNoLists() {
        assertEquals(FilterListsUiState.NO_LISTS,
                FilterListsUiState.resolve(false, false, 0, 0, false));
    }

    @Test
    public void resolve_filteredOutRows_showsNoMatches() {
        assertEquals(FilterListsUiState.NO_MATCHES,
                FilterListsUiState.resolve(false, false, 10, 0, true));
    }

    @Test
    public void resolve_visibleRows_hidesInlineState() {
        assertEquals(FilterListsUiState.EMPTY_HIDDEN,
                FilterListsUiState.resolve(false, true, 10, 3, true));
    }
}
