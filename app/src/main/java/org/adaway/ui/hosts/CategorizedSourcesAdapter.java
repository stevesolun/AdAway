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
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.adaway.R;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.model.source.FilterListCategory;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private static final String[] QUANTITY_PREFIXES = new String[]{"k", "M", "G"};

    private final HostsSourcesViewCallback callback;
    private final List<FilterListItem> items = new ArrayList<>();
    private final Set<FilterListCategory> expandedCategories = new HashSet<>();

    // Default expanded categories for better UX
    {
        expandedCategories.add(FilterListCategory.ADS);
        expandedCategories.add(FilterListCategory.MALWARE);
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
            FilterListCategory category = FilterListCatalog.getCategoryForUrl(source.getUrl());
            grouped.get(category).add(source);
        }

        // Build the flat item list with headers and sources
        items.clear();

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
                FilterListCategory.SOCIAL,      // ⚠️ May break Facebook
                FilterListCategory.DEVICE,      // ⚠️ May break OEM features
                FilterListCategory.SERVICE,     // ⚠️ May break apps
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
                    category, enabledCount, categorySources.size(), totalHosts, isExpanded
            );
            items.add(header);

            // Add source items if expanded
            if (isExpanded) {
                for (HostsSource source : categorySources) {
                    boolean hasUpdate = hasUpdateAvailable(source);
                    items.add(new FilterListItem.SourceItem(source, hasUpdate));
                }
            }
        }

        notifyDataSetChanged();
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
                ? VIEW_TYPE_CATEGORY : VIEW_TYPE_SOURCE;
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
        holder.title.setText(header.getCategory().getLabelResId());

        // Summary
        if (header.isAnyEnabled()) {
            String hostCount = formatHostCount(header.getTotalHosts());
            holder.summary.setText(res.getString(
                    R.string.filter_category_summary_enabled,
                    header.getEnabledCount(),
                    header.getTotalCount(),
                    hostCount
            ));
        } else {
            holder.summary.setText(R.string.filter_category_summary_disabled);
        }

        // Make CUSTOM useful even when empty: tapping it opens the add-options sheet.
        if (header.getCategory() == FilterListCategory.CUSTOM && header.getTotalCount() == 0) {
            holder.summary.setText(context.getString(R.string.filter_catalog_subtitle));
            holder.card.setOnClickListener(v -> callback.requestAddCustomSource());
            // Still show an indicator so it looks interactive.
            holder.expandIndicator.setVisibility(View.VISIBLE);
            return;
        }

        // Tri-state toggle (none / some / all)
        holder.toggle.setOnClickListener(null);
        int enabledCount = header.getEnabledCount();
        int totalCount = header.getTotalCount();
        if (totalCount <= 0) {
            holder.toggle.setEnabled(false);
            holder.toggle.setCheckedState(MaterialCheckBox.STATE_UNCHECKED);
        } else if (enabledCount <= 0) {
            holder.toggle.setEnabled(true);
            holder.toggle.setCheckedState(MaterialCheckBox.STATE_UNCHECKED);
        } else if (enabledCount >= totalCount) {
            holder.toggle.setEnabled(true);
            holder.toggle.setCheckedState(MaterialCheckBox.STATE_CHECKED);
        } else {
            holder.toggle.setEnabled(true);
            holder.toggle.setCheckedState(MaterialCheckBox.STATE_INDETERMINATE);
        }
        holder.toggle.setOnClickListener(v -> {
            // Decide based on current category state (not on the checkbox's post-click visual state).
            // If any list is disabled -> enable all, else disable all.
            boolean shouldEnableAll = header.getEnabledCount() < header.getTotalCount();
            setAllInCategory(header.getCategory(), shouldEnableAll);
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
                    RotateAnimation.RELATIVE_TO_SELF, 0.5f
            );
            anim.setDuration(200);
            anim.setFillAfter(true);
            holder.expandIndicator.startAnimation(anim);

            // Rebuild list (will handle expand/collapse)
            rebuildFromCurrentSources();
        });

        // Hide expand indicator if no sources
        holder.expandIndicator.setVisibility(
                header.getTotalCount() > 0 ? View.VISIBLE : View.INVISIBLE
        );
    }

    private void bindSource(SourceViewHolder holder, FilterListItem.SourceItem item) {
        Context context = holder.itemView.getContext();
        HostsSource source = item.getSource();

        // Label
        holder.label.setText(source.getLabel());

        // Status text
        holder.status.setText(getStatusText(context, source));

        // Host count badge
        String hostCount = formatHostCount(source.getSize());
        holder.hostCountBadge.setText(hostCount);
        holder.hostCountBadge.setVisibility(
                source.getSize() > 0 && source.isEnabled() ? View.VISIBLE : View.GONE
        );

        // Update indicator
        holder.updateIndicator.setVisibility(
                item.isUpdateAvailable() ? View.VISIBLE : View.GONE
        );

        // Switch (per-list enable/disable)
        holder.toggle.setOnCheckedChangeListener(null);
        holder.toggle.setChecked(source.isEnabled());
        holder.toggle.setOnCheckedChangeListener((buttonView, isChecked) -> callback.setEnabled(source, isChecked));

        // Card click to edit
        holder.card.setOnClickListener(v -> callback.edit(source));

        // Per-list update action (when an update is available)
        holder.updateIndicator.setOnClickListener(null);
        if (item.isUpdateAvailable()) {
            holder.updateIndicator.setClickable(true);
            holder.updateIndicator.setOnClickListener(v -> callback.updateSource(source));
        } else {
            holder.updateIndicator.setClickable(false);
        }
    }

    private String getStatusText(Context context, HostsSource source) {
        if (!source.isEnabled()) {
            return context.getString(R.string.filter_disabled);
        }

        ZonedDateTime lastUpdate = source.getLocalModificationDate();
        if (lastUpdate == null) {
            return context.getString(R.string.filter_never_updated);
        }

        // Check if update available
        if (hasUpdateAvailable(source)) {
            return context.getString(R.string.filter_update_available);
        }

        String delay = getApproximateDelay(context, lastUpdate);
        return context.getString(R.string.filter_last_updated, delay);
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
        if (size <= 0) return "0";

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
            if (FilterListCatalog.getCategoryForUrl(source.getUrl()) == category && source.isEnabled() != enabled) {
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
        final MaterialCheckBox toggle;
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
        final TextView hostCountBadge;
        final ImageView updateIndicator;

        SourceViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.sourceCard);
            toggle = itemView.findViewById(R.id.sourceSwitch);
            label = itemView.findViewById(R.id.sourceLabel);
            status = itemView.findViewById(R.id.sourceStatus);
            hostCountBadge = itemView.findViewById(R.id.hostCountBadge);
            updateIndicator = itemView.findViewById(R.id.updateIndicator);
        }
    }
}

