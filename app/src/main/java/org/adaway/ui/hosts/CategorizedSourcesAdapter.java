package org.adaway.ui.hosts;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.adaway.R;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.source.FilterListCompatibility;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.model.source.FilterListCategory;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * RecyclerView adapter that displays filter sources organized by category,
 * similar to uBlock Origin's filter list management.
 * <p>
 * Features:
 * - Expandable category headers with icons and summaries
 * - Individual source items with toggle switches
 * - Host count badges
 * - Update status indicators
 *
 * @author AdAway Contributors
 */
public class CategorizedSourcesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_CATEGORY = 0;
    private static final int VIEW_TYPE_SOURCE = 1;
    private static final String[] QUANTITY_PREFIXES = new String[] { "k", "M", "G" };
    private static final String SOURCE_DETAIL_SEPARATOR = " \u2022 ";

    private final HostsSourcesViewCallback callback;
    private final List<FilterListItem> items = new ArrayList<>();
    private final Set<FilterListCategory> expandedCategories = new HashSet<>();

    // Default expanded categories for better UX
    {
        expandedCategories.add(FilterListCategory.ADS);
        expandedCategories.add(FilterListCategory.MALWARE);
        expandedCategories.add(FilterListCategory.FILTERLISTS);
        // Keep Custom expanded so user can always see their custom lists.
        expandedCategories.add(FilterListCategory.CUSTOM);
    }

    public CategorizedSourcesAdapter(@NonNull HostsSourcesViewCallback callback) {
        this.callback = callback;
    }

    /**
     * Submit a new list of sources. The adapter will categorize them automatically.
     */
    public void submitList(List<HostsSource> sources) {
        if (sources == null) {
            sources = new ArrayList<>();
        }

        // Group sources by category
        Map<FilterListCategory, List<HostsSource>> grouped = new EnumMap<>(FilterListCategory.class);
        for (FilterListCategory category : FilterListCategory.values()) {
            grouped.put(category, new ArrayList<>());
        }

        for (HostsSource source : sources) {
            FilterListCategory category = FilterListCatalog.getCategoryForSource(source);
            grouped.get(category).add(source);
        }

        // Build the flat item list with headers and sources
        List<FilterListItem> nextItems = new ArrayList<>();

        // Define display order (most important/safest first, risky last)
        FilterListCategory[] displayOrder = {
                FilterListCategory.USER,
                FilterListCategory.ADS,
                FilterListCategory.YOUTUBE,
                FilterListCategory.MALWARE,
                FilterListCategory.PRIVACY,
                FilterListCategory.CRYPTO,
                FilterListCategory.ANNOYANCES,
                FilterListCategory.REGIONAL,
                FilterListCategory.SOCIAL, // ⚠️ May break Facebook
                FilterListCategory.DEVICE, // ⚠️ May break OEM features
                FilterListCategory.SERVICE, // ⚠️ May break apps
                FilterListCategory.FILTERLISTS,
                FilterListCategory.CUSTOM
        };

        for (FilterListCategory category : displayOrder) {
            List<HostsSource> categorySources = grouped.get(category);

            // Skip empty categories (except CUSTOM which should always show for adding)
            if (categorySources.isEmpty() && category != FilterListCategory.CUSTOM) {
                continue;
            }

            // Calculate stats
            int enabledCount = 0;
            long totalHosts = 0;
            for (HostsSource source : categorySources) {
                if (source.isEnabled()) {
                    enabledCount++;
                    totalHosts += source.getSize();
                }
            }

            boolean isExpanded = expandedCategories.contains(category);

            // Add category header
            FilterListItem.CategoryHeader header = new FilterListItem.CategoryHeader(
                    category, enabledCount, categorySources.size(), totalHosts, isExpanded);
            nextItems.add(header);

            // Add source items if expanded
            if (isExpanded) {
                for (HostsSource source : categorySources) {
                    boolean hasUpdate = hasUpdateAvailable(source);
                    nextItems.add(new FilterListItem.SourceItem(source, hasUpdate));
                }
            }
        }

        updateItems(nextItems);
    }

    private void updateItems(@NonNull List<FilterListItem> nextItems) {
        List<FilterListItem> previousItems = new ArrayList<>(items);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return previousItems.size();
            }

            @Override
            public int getNewListSize() {
                return nextItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return isSameItem(previousItems.get(oldItemPosition), nextItems.get(newItemPosition));
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return hasSameContent(previousItems.get(oldItemPosition), nextItems.get(newItemPosition));
            }
        });

        items.clear();
        items.addAll(nextItems);
        diff.dispatchUpdatesTo(this);
    }

    private static boolean isSameItem(@NonNull FilterListItem oldItem,
                                      @NonNull FilterListItem newItem) {
        if (oldItem.getItemType() != newItem.getItemType()) {
            return false;
        }
        if (oldItem instanceof FilterListItem.CategoryHeader
                && newItem instanceof FilterListItem.CategoryHeader) {
            return ((FilterListItem.CategoryHeader) oldItem).getCategory()
                    == ((FilterListItem.CategoryHeader) newItem).getCategory();
        }
        if (oldItem instanceof FilterListItem.SourceItem
                && newItem instanceof FilterListItem.SourceItem) {
            FilterListItem.SourceItem oldSource = (FilterListItem.SourceItem) oldItem;
            FilterListItem.SourceItem newSource = (FilterListItem.SourceItem) newItem;
            return oldSource.getId() == newSource.getId()
                    && Objects.equals(oldSource.getUrl(), newSource.getUrl());
        }
        return false;
    }

    private static boolean hasSameContent(@NonNull FilterListItem oldItem,
                                          @NonNull FilterListItem newItem) {
        if (!isSameItem(oldItem, newItem)) {
            return false;
        }
        if (oldItem instanceof FilterListItem.CategoryHeader
                && newItem instanceof FilterListItem.CategoryHeader) {
            FilterListItem.CategoryHeader oldHeader = (FilterListItem.CategoryHeader) oldItem;
            FilterListItem.CategoryHeader newHeader = (FilterListItem.CategoryHeader) newItem;
            return oldHeader.getEnabledCount() == newHeader.getEnabledCount()
                    && oldHeader.getTotalCount() == newHeader.getTotalCount()
                    && oldHeader.getTotalHosts() == newHeader.getTotalHosts()
                    && oldHeader.isExpanded() == newHeader.isExpanded();
        }
        FilterListItem.SourceItem oldSourceItem = (FilterListItem.SourceItem) oldItem;
        FilterListItem.SourceItem newSourceItem = (FilterListItem.SourceItem) newItem;
        return oldSourceItem.isEnabled() == newSourceItem.isEnabled()
                && oldSourceItem.getSize() == newSourceItem.getSize()
                && oldSourceItem.isUpdateAvailable() == newSourceItem.isUpdateAvailable()
                && Objects.equals(oldSourceItem.getLabel(), newSourceItem.getLabel())
                && Objects.equals(oldSourceItem.getUrl(), newSourceItem.getUrl())
                && Objects.equals(oldSourceItem.getLocalModificationDate(),
                        newSourceItem.getLocalModificationDate())
                && Objects.equals(oldSourceItem.getOnlineModificationDate(),
                        newSourceItem.getOnlineModificationDate())
                && Objects.equals(oldSourceItem.getLastDownloadError(),
                        newSourceItem.getLastDownloadError())
                && oldSourceItem.getSkippedCount() == newSourceItem.getSkippedCount()
                && Objects.equals(oldSourceItem.getFilterListId(),
                        newSourceItem.getFilterListId())
                && Objects.equals(oldSourceItem.getFilterListName(),
                        newSourceItem.getFilterListName())
                && Objects.equals(oldSourceItem.getFilterListSyntaxIds(),
                        newSourceItem.getFilterListSyntaxIds())
                && Objects.equals(oldSourceItem.getFilterListCompatibility(),
                        newSourceItem.getFilterListCompatibility())
                && oldSourceItem.getFilterListCompatibilityScore()
                        == newSourceItem.getFilterListCompatibilityScore()
                && Objects.equals(oldSourceItem.getFilterListTagIds(),
                        newSourceItem.getFilterListTagIds())
                && Objects.equals(oldSourceItem.getFilterListLanguageIds(),
                        newSourceItem.getFilterListLanguageIds());
    }

    private boolean hasUpdateAvailable(HostsSource source) {
        if (source.getOnlineModificationDate() == null || source.getLocalModificationDate() == null) {
            return false;
        }
        return source.getOnlineModificationDate().isAfter(source.getLocalModificationDate());
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getItemType() == FilterListItem.TYPE_CATEGORY_HEADER
                ? VIEW_TYPE_CATEGORY
                : VIEW_TYPE_SOURCE;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_CATEGORY) {
            View view = inflater.inflate(R.layout.filter_category_header, parent, false);
            return new CategoryViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.filter_source_item, parent, false);
            return new SourceViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        FilterListItem item = items.get(position);
        if (holder instanceof CategoryViewHolder && item instanceof FilterListItem.CategoryHeader) {
            bindCategory((CategoryViewHolder) holder, (FilterListItem.CategoryHeader) item);
        } else if (holder instanceof SourceViewHolder && item instanceof FilterListItem.SourceItem) {
            bindSource((SourceViewHolder) holder, (FilterListItem.SourceItem) item);
        }
    }

    private void bindCategory(CategoryViewHolder holder, FilterListItem.CategoryHeader header) {
        Context context = holder.itemView.getContext();
        Resources res = context.getResources();

        // Icon
        holder.icon.setImageResource(header.getCategoryIcon());

        // Title
        String categoryLabel = context.getString(header.getCategory().getLabelResId());
        holder.title.setText(categoryLabel);
        holder.toggle.setContentDescription(
                context.getString(R.string.filter_category_toggle_description, categoryLabel));

        // Summary
        if (header.isAnyEnabled()) {
            String hostCount = formatHostCount(header.getTotalHosts());
            holder.summary.setText(res.getQuantityString(
                    R.plurals.filter_category_summary_enabled,
                    header.getEnabledCount(),
                    header.getEnabledCount(),
                    header.getTotalCount(),
                    hostCount));
        } else {
            holder.summary.setText(R.string.filter_category_summary_disabled);
        }

        // Make CUSTOM useful even when empty: tapping it opens the add-options sheet.
        if (header.getCategory() == FilterListCategory.CUSTOM && header.getTotalCount() == 0) {
            holder.summary.setText(context.getString(R.string.filter_catalog_subtitle));
            holder.card.setOnClickListener(v -> callback.requestAddCustomSource());
            holder.toggle.setOnCheckedChangeListener(null);
            holder.toggle.setOnClickListener(null);
            holder.toggle.setChecked(false);
            holder.toggle.setEnabled(false);
            ViewCompat.setStateDescription(holder.toggle,
                    context.getString(R.string.filter_category_state_empty));
            // Still show an indicator so it looks interactive.
            holder.expandIndicator.setVisibility(View.VISIBLE);
            return;
        }

        // Binary toggle (all / not all)
        holder.toggle.setOnClickListener(null);
        int enabledCount = header.getEnabledCount();
        int totalCount = header.getTotalCount();

        holder.toggle.setEnabled(totalCount > 0);

        // If ALL enabled, check it. Otherwise uncheck.
        // This simplifies the tri-state "square" to a simple switch.
        boolean allEnabled = totalCount > 0 && enabledCount >= totalCount;
        holder.toggle.setOnCheckedChangeListener(null); // prevent trigger during bind
        holder.toggle.setChecked(allEnabled);
        ViewCompat.setStateDescription(holder.toggle,
                getCategoryStateDescription(context, enabledCount, totalCount));
        holder.toggle.setOnClickListener(v -> {
            boolean isChecked = holder.toggle.isChecked();
            // If user turned it ON, enable all. If OFF, disable all.
            setAllInCategory(header.getCategory(), isChecked);
        });

        // Expand indicator rotation
        float rotation = header.isExpanded() ? 180f : 0f;
        holder.expandIndicator.setRotation(rotation);

        // Click to expand/collapse
        holder.card.setOnClickListener(v -> {
            boolean wasExpanded = header.isExpanded();
            if (wasExpanded) {
                expandedCategories.remove(header.getCategory());
            } else {
                expandedCategories.add(header.getCategory());
            }

            // Animate the indicator
            float fromRotation = wasExpanded ? 180f : 0f;
            float toRotation = wasExpanded ? 0f : 180f;
            RotateAnimation anim = new RotateAnimation(
                    fromRotation, toRotation,
                    RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                    RotateAnimation.RELATIVE_TO_SELF, 0.5f);
            anim.setDuration(200);
            anim.setFillAfter(true);
            holder.expandIndicator.startAnimation(anim);

            // Rebuild list (will handle expand/collapse)
            rebuildFromCurrentSources();
        });

        // Hide expand indicator if no sources
        holder.expandIndicator.setVisibility(
                header.getTotalCount() > 0 ? View.VISIBLE : View.INVISIBLE);
    }

    private void bindSource(SourceViewHolder holder, FilterListItem.SourceItem item) {
        Context context = holder.itemView.getContext();
        HostsSource source = item.getSource();

        // Label
        holder.label.setText(item.getLabel());

        // Status text
        String statusText = getStatusText(context, item);
        String provenanceText = buildSourceProvenanceSummary(item);
        holder.status.setText(provenanceText.isEmpty()
                ? statusText
                : statusText + "\n" + provenanceText);

        String error = item.getLastDownloadError();
        if (error != null && !error.trim().isEmpty()) {
            holder.error.setText(context.getString(
                    R.string.filter_source_download_error, truncateError(error)));
            holder.error.setVisibility(View.VISIBLE);
        } else {
            holder.error.setVisibility(View.GONE);
        }

        // Host count badge
        String hostCount = formatHostCount(item.getSize());
        holder.hostCountBadge.setText(hostCount);
        holder.hostCountBadge.setVisibility(
                item.getSize() > 0 && item.isEnabled() ? View.VISIBLE : View.GONE);

        // Show update button for sources that have an update available OR have never been
        // downloaded yet (localModificationDate == null). Without this, newly added custom
        // sources show "Never updated" with no way to trigger a download from the per-source UI.
        boolean showUpdateButton = item.isUpdateAvailable()
                || (item.isEnabled() && item.getLocalModificationDate() == null);
        holder.updateIndicator.setVisibility(showUpdateButton ? View.VISIBLE : View.GONE);
        holder.updateIndicator.setContentDescription(
                context.getString(R.string.filter_source_update_action, item.getLabel()));

        // Switch (per-list enable/disable)
        holder.toggle.setOnCheckedChangeListener(null);
        holder.toggle.setChecked(item.isEnabled());
        holder.toggle.setContentDescription(context.getString(
                R.string.filter_source_toggle_description, item.getLabel()));
        holder.toggle.setOnCheckedChangeListener(
                (buttonView, isChecked) -> callback.setEnabled(source, isChecked));

        // Card click to edit
        holder.card.setOnClickListener(v -> callback.edit(source));

        // Per-list update action (when an update is available or never downloaded)
        holder.updateIndicator.setOnClickListener(null);
        if (showUpdateButton) {
            holder.updateIndicator.setClickable(true);
            holder.updateIndicator.setOnClickListener(v -> callback.updateSource(source));
        } else {
            holder.updateIndicator.setClickable(false);
        }
    }

    private String getCategoryStateDescription(Context context, int enabledCount, int totalCount) {
        if (totalCount <= 0) {
            return context.getString(R.string.filter_category_state_empty);
        }
        if (enabledCount >= totalCount) {
            return context.getString(R.string.filter_category_state_all_enabled);
        }
        if (enabledCount > 0) {
            return context.getResources().getQuantityString(
                    R.plurals.filter_category_state_some_enabled,
                    enabledCount,
                    enabledCount,
                    totalCount);
        }
        return context.getString(R.string.filter_category_state_disabled);
    }

    private String getStatusText(Context context, FilterListItem.SourceItem source) {
        if (!source.isEnabled()) {
            return context.getString(R.string.filter_disabled);
        }

        ZonedDateTime lastUpdate = source.getLocalModificationDate();
        if (lastUpdate == null) {
            return context.getString(R.string.filter_never_updated);
        }

        // Check if update available
        if (source.isUpdateAvailable()) {
            return context.getString(R.string.filter_update_available);
        }

        String delay = getApproximateDelay(context, lastUpdate);
        return context.getString(R.string.filter_last_updated, delay);
    }

    static String buildSourceProvenanceSummary(@NonNull FilterListItem.SourceItem source) {
        List<String> parts = new ArrayList<>();
        if (source.getFilterListId() != null || notBlank(source.getFilterListName())
                || notBlank(source.getFilterListSyntaxIds())
                || notBlank(source.getFilterListCompatibility())) {
            parts.add("FilterLists.com");
            if (source.getFilterListId() != null || notBlank(source.getFilterListName())
                    || notBlank(source.getFilterListSyntaxIds())) {
                parts.add(FilterListCompatibility.rowSummary(
                        FilterListCompatibility.decodeSyntaxIds(source.getFilterListSyntaxIds())));
            } else if (notBlank(source.getFilterListCompatibility())) {
                parts.add(source.getFilterListCompatibility().trim());
            }
        }
        if (source.getSkippedCount() > 0) {
            parts.add(source.getSkippedCount() + " skipped");
        }
        return String.join(SOURCE_DETAIL_SEPARATOR, parts);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String truncateError(@NonNull String error) {
        return error.length() > 160 ? error.substring(0, 157) + "..." : error;
    }

    private String getApproximateDelay(Context context, ZonedDateTime from) {
        Resources resources = context.getResources();
        ZonedDateTime now = ZonedDateTime.now();
        long delay = Duration.between(from, now).toMinutes();

        if (delay < 60) {
            return resources.getString(R.string.hosts_source_few_minutes);
        }
        delay /= 60;
        if (delay < 24) {
            int hours = (int) delay;
            return resources.getQuantityString(R.plurals.hosts_source_hours, hours, hours);
        }
        delay /= 24;
        if (delay < 30) {
            int days = (int) delay;
            return resources.getQuantityString(R.plurals.hosts_source_days, days, days);
        }
        int months = (int) delay / 30;
        return resources.getQuantityString(R.plurals.hosts_source_months, months, months);
    }

    private String formatHostCount(long size) {
        if (size <= 0)
            return "0";

        int length = 1;
        long temp = size;
        while (temp >= 10) {
            temp /= 10;
            length++;
        }

        int prefixIndex = (length - 1) / 3 - 1;
        if (prefixIndex < 0) {
            return String.valueOf(size);
        }
        if (prefixIndex >= QUANTITY_PREFIXES.length) {
            prefixIndex = QUANTITY_PREFIXES.length - 1;
        }

        long rounded = Math.round(size / Math.pow(10, (prefixIndex + 1) * 3D));
        return rounded + QUANTITY_PREFIXES[prefixIndex];
    }

    private void setAllInCategory(FilterListCategory category, boolean enabled) {
        for (HostsSource source : currentSources) {
            if (FilterListCatalog.getCategoryForSource(source) == category
                    && source.isEnabled() != enabled) {
                // Optimistic UI update; DB update happens via callback.
                source.setEnabled(enabled);
                callback.setEnabled(source, enabled);
            }
        }
        // Force immediate UI refresh of switches + header tri-state.
        rebuildFromCurrentSources();
    }

    // Keep track of current sources for rebuild
    private List<HostsSource> currentSources = new ArrayList<>();

    public void updateSources(List<HostsSource> sources) {
        this.currentSources = sources != null ? new ArrayList<>(sources) : new ArrayList<>();
        submitList(currentSources);
    }

    private void rebuildFromCurrentSources() {
        submitList(currentSources);
    }

    /**
     * ViewHolder for category headers.
     */
    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView icon;
        final TextView title;
        final TextView summary;
        final MaterialSwitch toggle;
        final ImageView expandIndicator;

        CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.categoryCard);
            icon = itemView.findViewById(R.id.categoryIcon);
            title = itemView.findViewById(R.id.categoryTitle);
            summary = itemView.findViewById(R.id.categorySummary);
            toggle = itemView.findViewById(R.id.categorySwitch);
            expandIndicator = itemView.findViewById(R.id.expandIndicator);
        }
    }

    /**
     * ViewHolder for source items.
     */
    static class SourceViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final SwitchMaterial toggle;
        final TextView label;
        final TextView status;
        final TextView error;
        final TextView hostCountBadge;
        final ImageView updateIndicator;

        SourceViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.sourceCard);
            toggle = itemView.findViewById(R.id.sourceSwitch);
            label = itemView.findViewById(R.id.sourceLabel);
            status = itemView.findViewById(R.id.sourceStatus);
            error = itemView.findViewById(R.id.sourceDownloadError);
            hostCountBadge = itemView.findViewById(R.id.hostCountBadge);
            updateIndicator = itemView.findViewById(R.id.updateIndicator);
        }
    }
}
