package org.adaway.ui.discover;

final class FilterListsUiState {

    static final int EMPTY_HIDDEN = 0;
    static final int LOAD_FAILED = 1;
    static final int NO_LISTS = 2;
    static final int NO_MATCHES = 3;
    static final int LOADING = 4;

    private FilterListsUiState() {
    }

    static int resolve(boolean loading, boolean loadFailed, int totalCount, int filteredCount,
                       boolean hasActiveFilters) {
        if (loading && totalCount <= 0) {
            return LOADING;
        }
        if (totalCount <= 0) {
            return loadFailed ? LOAD_FAILED : NO_LISTS;
        }
        if (filteredCount <= 0 && hasActiveFilters) {
            return NO_MATCHES;
        }
        return EMPTY_HIDDEN;
    }
}
