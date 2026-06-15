package org.adaway.ui.hosts;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.adaway.R;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.source.FilterListCategory;
import org.adaway.model.source.FilterListCatalog;

import java.time.ZonedDateTime;

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
        private final int id;
        private final String label;
        private final String url;
        private final boolean enabled;
        private final int size;
        private final ZonedDateTime localModificationDate;
        private final ZonedDateTime onlineModificationDate;
        private final String lastDownloadError;
        private final int skippedCount;
        private final Integer filterListId;
        private final String filterListName;
        private final String filterListSyntaxIds;
        private final String filterListCompatibility;
        private final int filterListCompatibilityScore;
        private final String filterListTagIds;
        private final String filterListLanguageIds;
        
        public SourceItem(@NonNull HostsSource source, boolean updateAvailable) {
            this.source = source;
            this.category = FilterListCatalog.getCategoryForSource(source);
            this.updateAvailable = updateAvailable;
            this.id = source.getId();
            this.label = source.getLabel();
            this.url = source.getUrl();
            this.enabled = source.isEnabled();
            this.size = source.getSize();
            this.localModificationDate = source.getLocalModificationDate();
            this.onlineModificationDate = source.getOnlineModificationDate();
            this.lastDownloadError = source.getLastDownloadError();
            this.skippedCount = source.getSkippedCount();
            this.filterListId = source.getFilterListId();
            this.filterListName = source.getFilterListName();
            this.filterListSyntaxIds = source.getFilterListSyntaxIds();
            this.filterListCompatibility = source.getFilterListCompatibility();
            this.filterListCompatibilityScore = source.getFilterListCompatibilityScore();
            this.filterListTagIds = source.getFilterListTagIds();
            this.filterListLanguageIds = source.getFilterListLanguageIds();
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

        public int getId() {
            return id;
        }
        
        public String getLabel() {
            return label;
        }

        public String getUrl() {
            return url;
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public int getSize() {
            return size;
        }

        @Nullable
        public ZonedDateTime getLocalModificationDate() {
            return localModificationDate;
        }

        @Nullable
        public ZonedDateTime getOnlineModificationDate() {
            return onlineModificationDate;
        }

        @Nullable
        public String getLastDownloadError() {
            return lastDownloadError;
        }

        public int getSkippedCount() {
            return skippedCount;
        }

        @Nullable
        public Integer getFilterListId() {
            return filterListId;
        }

        @Nullable
        public String getFilterListName() {
            return filterListName;
        }

        @Nullable
        public String getFilterListSyntaxIds() {
            return filterListSyntaxIds;
        }

        @Nullable
        public String getFilterListCompatibility() {
            return filterListCompatibility;
        }

        public int getFilterListCompatibilityScore() {
            return filterListCompatibilityScore;
        }

        @Nullable
        public String getFilterListTagIds() {
            return filterListTagIds;
        }

        @Nullable
        public String getFilterListLanguageIds() {
            return filterListLanguageIds;
        }
    }
}

