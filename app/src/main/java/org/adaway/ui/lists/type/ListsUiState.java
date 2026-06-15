package org.adaway.ui.lists.type;

final class ListsUiState {
    static final int HIDDEN = 0;
    static final int LOAD_FAILED = 1;
    static final int NO_RULES = 2;
    static final int NO_MATCHES = 3;

    private ListsUiState() {
    }

    static int resolve(boolean loading, boolean loadFailed, int itemCount, boolean searching) {
        if (loading) {
            return HIDDEN;
        }
        if (loadFailed) {
            return LOAD_FAILED;
        }
        if (itemCount > 0) {
            return HIDDEN;
        }
        return searching ? NO_MATCHES : NO_RULES;
    }
}
