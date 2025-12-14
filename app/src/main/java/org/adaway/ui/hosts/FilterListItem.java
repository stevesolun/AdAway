package org.adaway.ui.hosts;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import org.adaway.R;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.source.FilterListCategory;
import org.adaway.model.source.FilterListCatalog;

/**
 * Represents an item in the filter list RecyclerView.
 * Can be either a category header or a source item.
 */
public abstract class FilterListItem {
    
    public static final int TYPE_CATEGORY_HEADER = 0;
    public static final int TYPE_SOURCE_ITEM = 1;
    
    public abstract int getItemType();
    
    /**
     * Category header item - expandable section header.
     */
    public static class CategoryHeader extends FilterListItem {
        private final FilterListCategory category;
        private final int enabledCount;
        private final int totalCount;
        private final long totalHosts;
        private boolean expanded;
        
        public CategoryHeader(@NonNull FilterListCategory category, int enabledCount, 
                             int totalCount, long totalHosts, boolean expanded) {
            this.category = category;
            this.enabledCount = enabledCount;
            this.totalCount = totalCount;
            this.totalHosts = totalHosts;
            this.expanded = expanded;
        }
        
        @Override
        public int getItemType() {
            return TYPE_CATEGORY_HEADER;
        }
        
        public FilterListCategory getCategory() {
            return category;
        }
        
        public int getEnabledCount() {
            return enabledCount;
        }
        
        public int getTotalCount() {
            return totalCount;
        }
        
        public long getTotalHosts() {
            return totalHosts;
        }
        
        public boolean isExpanded() {
            return expanded;
        }
        
        public void setExpanded(boolean expanded) {
            this.expanded = expanded;
        }
        
        public boolean hasAnySources() {
            return totalCount > 0;
        }
        
        public boolean isAllEnabled() {
            return enabledCount == totalCount && totalCount > 0;
        }
        
        public boolean isAnyEnabled() {
            return enabledCount > 0;
        }
        
        @DrawableRes
        public int getCategoryIcon() {
            return category.getIconResId();
        }
        
        /**
         * Returns true if this category may break common services.
         * UI should show a warning indicator.
         */
        public boolean mayBreakServices() {
            return category.mayBreakServices();
        }
    }
    
    /**
     * Source item - individual filter list source.
     */
    public static class SourceItem extends FilterListItem {
        private final HostsSource source;
        private final FilterListCategory category;
        private final boolean updateAvailable;
        
        public SourceItem(@NonNull HostsSource source, boolean updateAvailable) {
            this.source = source;
            this.category = FilterListCatalog.getCategoryForUrl(source.getUrl());
            this.updateAvailable = updateAvailable;
        }
        
        @Override
        public int getItemType() {
            return TYPE_SOURCE_ITEM;
        }
        
        public HostsSource getSource() {
            return source;
        }
        
        public FilterListCategory getCategory() {
            return category;
        }
        
        public boolean isUpdateAvailable() {
            return updateAvailable;
        }
        
        public String getLabel() {
            return source.getLabel();
        }
        
        public boolean isEnabled() {
            return source.isEnabled();
        }
        
        public int getSize() {
            return source.getSize();
        }
    }
}

