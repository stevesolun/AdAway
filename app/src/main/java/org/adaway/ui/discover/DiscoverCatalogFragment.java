package org.adaway.ui.discover;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import org.adaway.R;
import org.adaway.databinding.FragmentDiscoverCatalogBinding;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.ui.hosts.FilterSetStore;
import org.adaway.ui.source.SourceEditActivity;
import org.adaway.util.AppExecutors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Fragment for browsing and adding filter lists from the curated catalog.
 *
 * Ported from FilterCatalogActivity. Embedded into DiscoverFragment as the first sub-tab.
 */
public class DiscoverCatalogFragment extends Fragment {

    private static final Executor EXECUTOR = AppExecutors.getInstance().diskIO();

    private FragmentDiscoverCatalogBinding binding;
    private HostsSourceDao hostsSourceDao;

    private CatalogAdapter adapter;
    private Set<String> existingUrls = new HashSet<>();
    private Set<FilterListCatalog.CatalogEntry> selectedEntries = new HashSet<>();

    private List<FilterListCatalog.CatalogEntry> allEntries;
    private List<FilterListCatalog.CatalogEntry> filteredEntries;
    private String currentSearchQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        this.binding = FragmentDiscoverCatalogBinding.inflate(inflater, container, false);
        return this.binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        hostsSourceDao = AppDatabase.getInstance(requireContext()).hostsSourceDao();

        binding.catalogRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        binding.addSelectedButton.setOnClickListener(v -> addSelectedSources());

        binding.catalogSelectAllSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (this.binding == null) return;
            if (filteredEntries == null) return;
            selectedEntries.clear();
            if (isChecked) {
                for (FilterListCatalog.CatalogEntry entry : filteredEntries) {
                    if (!existingUrls.contains(entry.url)) {
                        selectedEntries.add(entry);
                    }
                }
            }
            if (adapter != null) adapter.notifyDataSetChanged();
            updateAddButton();
            binding.chipCustom.setChecked(true);
        });

        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString();
                filterEntries();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.chipSafe.setOnClickListener(v -> applyPreset("safe"));
        binding.chipBalanced.setOnClickListener(v -> applyPreset("balanced"));
        binding.chipAggressive.setOnClickListener(v -> applyPreset("aggressive"));
        binding.chipCustom.setOnClickListener(v -> binding.chipCustom.setChecked(true));

        loadData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        this.binding = null;
    }

    private void loadData() {
        EXECUTOR.execute(() -> {
            List<HostsSource> existing = hostsSourceDao.getAll();
            existingUrls.clear();
            for (HostsSource source : existing) {
                existingUrls.add(source.getUrl());
            }

            allEntries = FilterListCatalog.getAll();
            filteredEntries = new ArrayList<>(allEntries);

            requireActivity().runOnUiThread(() -> {
                if (this.binding == null) return;
                adapter = new CatalogAdapter();
                binding.catalogRecyclerView.setAdapter(adapter);
                updateSubtitle();
            });
        });
    }

    private void filterEntries() {
        if (allEntries == null) return;

        filteredEntries = new ArrayList<>();
        String query = currentSearchQuery.toLowerCase(Locale.ROOT).trim();

        for (FilterListCatalog.CatalogEntry entry : allEntries) {
            if (query.isEmpty()) {
                filteredEntries.add(entry);
            } else {
                boolean matchesLabel = entry.label.toLowerCase(Locale.ROOT).contains(query);
                boolean matchesUrl = entry.url.toLowerCase(Locale.ROOT).contains(query);
                boolean matchesCategory = entry.category.name().toLowerCase(Locale.ROOT).contains(query);
                if (matchesLabel || matchesUrl || matchesCategory) {
                    filteredEntries.add(entry);
                }
            }
        }

        if (adapter != null) adapter.notifyDataSetChanged();
        updateSubtitle();

        if (binding != null && binding.catalogSelectAllSwitch.isChecked()) {
            binding.catalogSelectAllSwitch.setChecked(false);
        }
    }

    private void applyPreset(String preset) {
        selectedEntries.clear();

        List<FilterListCatalog.CatalogEntry> presetEntries;
        switch (preset) {
            case "safe":
                presetEntries = FilterListCatalog.getDefaults();
                break;
            case "balanced":
                presetEntries = FilterListCatalog.getBalancedPreset();
                break;
            case "aggressive":
                presetEntries = FilterListCatalog.getAggressivePreset();
                break;
            default:
                presetEntries = new ArrayList<>();
        }

        for (FilterListCatalog.CatalogEntry entry : presetEntries) {
            if (!existingUrls.contains(entry.url)) {
                selectedEntries.add(entry);
            }
        }

        if (adapter != null) adapter.notifyDataSetChanged();
        updateAddButton();

        String message;
        switch (preset) {
            case "safe":
                message = getString(R.string.filter_preset_safe_desc);
                break;
            case "balanced":
                message = getString(R.string.filter_preset_balanced_desc);
                break;
            case "aggressive":
                message = getString(R.string.filter_preset_aggressive_desc);
                break;
            default:
                message = "";
        }

        if (!message.isEmpty() && binding != null) {
            Snackbar.make(binding.catalogRecyclerView, message, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void clearSelection() {
        selectedEntries.clear();
        if (adapter != null) adapter.notifyDataSetChanged();
        updateAddButton();
    }

    private void updateSubtitle() {
        if (binding == null) return;
        int total = allEntries != null ? allEntries.size() : 0;
        int showing = filteredEntries != null ? filteredEntries.size() : 0;

        if (currentSearchQuery.isEmpty()) {
            binding.catalogSubtitle.setText(getString(R.string.filter_catalog_count, total));
        } else {
            binding.catalogSubtitle.setText(String.format(Locale.ROOT, "Showing %d of %d", showing, total));
        }
    }

    private void updateAddButton() {
        if (binding == null) return;
        int count = selectedEntries.size();
        binding.addSelectedButton.setEnabled(count > 0);
        if (count > 0) {
            binding.addSelectedButton.setText(getString(R.string.filter_catalog_add_selected) + " (" + count + ")");
        } else {
            binding.addSelectedButton.setText(R.string.filter_catalog_add_selected);
        }
    }

    private void addSelectedSources() {
        if (selectedEntries.isEmpty() || binding == null) return;

        binding.addSelectedButton.setEnabled(false);
        binding.addSelectedButton.setText(R.string.filter_update_started);

        // Capture URLs before clearing selectedEntries (called on main thread)
        final Set<String> selectedUrls = new HashSet<>();
        for (FilterListCatalog.CatalogEntry e : selectedEntries) {
            selectedUrls.add(e.url);
        }
        final android.content.Context appContext = requireContext().getApplicationContext();

        EXECUTOR.execute(() -> {
            int added = 0;
            for (FilterListCatalog.CatalogEntry entry : selectedEntries) {
                if (!existingUrls.contains(entry.url)) {
                    HostsSource source = entry.toHostsSource();
                    source.setEnabled(true);
                    hostsSourceDao.insert(source);
                    existingUrls.add(entry.url);
                    added++;
                }
            }
            // Persist applied preset to FilterSetStore so scheduled updates can track it.
            if (!selectedUrls.isEmpty()) {
                FilterSetStore.saveSet(appContext, "custom", selectedUrls);
            }

            final int finalAdded = added;
            requireActivity().runOnUiThread(() -> {
                if (this.binding == null) return;
                selectedEntries.clear();
                if (adapter != null) adapter.notifyDataSetChanged();
                updateAddButton();
                Snackbar.make(
                        binding.catalogRecyclerView,
                        getString(R.string.filter_added_success) + " (" + finalAdded + ")",
                        Snackbar.LENGTH_SHORT).show();
            });
        });
    }

    private void removeExistingSourceByUrl(String url) {
        EXECUTOR.execute(() -> {
            HostsSource existing = hostsSourceDao.getByUrl(url).orElse(null);
            if (existing != null) {
                hostsSourceDao.delete(existing);
            }
            existingUrls.remove(url);
            requireActivity().runOnUiThread(() -> {
                if (this.binding == null) return;
                if (adapter != null) adapter.notifyDataSetChanged();
                Snackbar.make(binding.catalogRecyclerView, R.string.filter_removed_success, Snackbar.LENGTH_SHORT)
                        .show();
                updateSubtitle();
            });
        });
    }

    // -----------------------------------------------------------------------------------------
    // Adapter
    // -----------------------------------------------------------------------------------------

    private class CatalogAdapter extends RecyclerView.Adapter<CatalogAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.filter_catalog_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FilterListCatalog.CatalogEntry entry = filteredEntries.get(position);
            boolean alreadyAdded = existingUrls.contains(entry.url);
            boolean isSelected = selectedEntries.contains(entry);

            holder.name.setText(entry.label);
            holder.badge.setText(entry.category.getLabelResId());

            if (entry.category.mayBreakServices()) {
                holder.badge.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                requireContext().getResources().getColor(R.color.blocked,
                                        requireContext().getTheme())));
                holder.badge.setTextColor(
                        requireContext().getResources().getColor(android.R.color.white,
                                requireContext().getTheme()));
            } else {
                holder.badge.setBackgroundTintList(null);
            }

            String urlPreview = entry.url;
            if (urlPreview.length() > 55) {
                urlPreview = urlPreview.substring(0, 52) + "...";
            }
            holder.description.setText(urlPreview);

            holder.alreadyAdded.setVisibility(alreadyAdded ? View.VISIBLE : View.GONE);
            holder.toggle.setVisibility(alreadyAdded ? View.GONE : View.VISIBLE);

            holder.toggle.setOnCheckedChangeListener(null);
            holder.toggle.setChecked(isSelected);

            holder.toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedEntries.add(entry);
                } else {
                    selectedEntries.remove(entry);
                    if (binding != null && binding.catalogSelectAllSwitch.isChecked()) {
                        binding.catalogSelectAllSwitch.setChecked(false);
                    }
                }
                updateAddButton();
                if (binding != null) binding.chipCustom.setChecked(true);
            });

            holder.card.setChecked(isSelected);

            if (alreadyAdded) {
                holder.card.setAlpha(0.7f);
                holder.card.setOnClickListener(v -> {
                    EXECUTOR.execute(() -> {
                        HostsSource existing = hostsSourceDao.getByUrl(entry.url).orElse(null);
                        if (existing == null) return;
                        requireActivity().runOnUiThread(() -> {
                            if (DiscoverCatalogFragment.this.binding == null) return;
                            Intent intent = new Intent(requireContext(), SourceEditActivity.class);
                            intent.putExtra(SourceEditActivity.SOURCE_ID, existing.getId());
                            startActivity(intent);
                        });
                    });
                });
                holder.card.setOnLongClickListener(v -> {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.checkbox_list_context_delete)
                            .setMessage(entry.label)
                            .setPositiveButton(R.string.checkbox_list_context_delete,
                                    (d, which) -> removeExistingSourceByUrl(entry.url))
                            .setNegativeButton(R.string.button_cancel, null)
                            .show();
                    return true;
                });
            } else {
                holder.card.setAlpha(1.0f);
                holder.card.setOnClickListener(v -> {
                    boolean newState = !selectedEntries.contains(entry);
                    if (newState) {
                        selectedEntries.add(entry);
                    } else {
                        selectedEntries.remove(entry);
                    }
                    holder.toggle.setChecked(newState);
                    notifyItemChanged(position);
                    updateAddButton();
                });
                holder.card.setOnLongClickListener(null);
            }
        }

        @Override
        public int getItemCount() {
            return filteredEntries != null ? filteredEntries.size() : 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final MaterialSwitch toggle;
            final TextView name;
            final TextView badge;
            final TextView description;
            final TextView alreadyAdded;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.catalogItemCard);
                toggle = itemView.findViewById(R.id.catalogItemSwitch);
                name = itemView.findViewById(R.id.catalogItemName);
                badge = itemView.findViewById(R.id.catalogItemBadge);
                description = itemView.findViewById(R.id.catalogItemDescription);
                alreadyAdded = itemView.findViewById(R.id.catalogItemAlreadyAdded);
            }
        }
    }
}
