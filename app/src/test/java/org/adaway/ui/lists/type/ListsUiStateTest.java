package org.adaway.ui.lists.type;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ListsUiStateTest {

    @Test
    public void resolve_loading_hidesInlineState() {
        assertEquals(ListsUiState.HIDDEN, ListsUiState.resolve(true, false, 0, false));
    }

    @Test
    public void resolve_error_showsLoadFailed() {
        assertEquals(ListsUiState.LOAD_FAILED, ListsUiState.resolve(false, true, 0, false));
    }

    @Test
    public void resolve_emptyWithoutSearch_showsNoRules() {
        assertEquals(ListsUiState.NO_RULES, ListsUiState.resolve(false, false, 0, false));
    }

    @Test
    public void resolve_emptyWithSearch_showsNoMatches() {
        assertEquals(ListsUiState.NO_MATCHES, ListsUiState.resolve(false, false, 0, true));
    }

    @Test
    public void resolve_rowsVisible_hidesInlineState() {
        assertEquals(ListsUiState.HIDDEN, ListsUiState.resolve(false, false, 4, true));
    }
}
