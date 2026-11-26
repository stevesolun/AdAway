package org.adaway.ui.hosts;

import androidx.annotation.NonNull;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import org.adaway.model.source.FilterListCategory;

/** Activity-scoped display choices, independent of enabled sources and saved filter sets. */
public class SourceCategoryViewState extends ViewModel {
    private final SavedStateHandle state;

    public SourceCategoryViewState(@NonNull SavedStateHandle state) {
        this.state = state;
    }

    boolean isExpanded(FilterListCategory category) {
        Boolean expanded = state.get(category.name());
        if (expanded != null) return expanded;
        return category == FilterListCategory.ADS || category == FilterListCategory.MALWARE
                || category == FilterListCategory.FILTERLISTS || category == FilterListCategory.CUSTOM;
    }

    void setExpanded(FilterListCategory category, boolean expanded) {
        state.set(category.name(), expanded);
    }
}
