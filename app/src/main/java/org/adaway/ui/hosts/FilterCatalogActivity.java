package org.adaway.ui.hosts;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.ThemeHelper;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.model.source.FilterListCategory;
import org.adaway.ui.home.HomeActivity;
import org.adaway.ui.source.SourceEditActivity;
import org.adaway.util.AppExecutors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Activity for browsing and adding filter lists from the curated catalog.
 * Inspired by uBlock Origin's filter list selection experience.
 * <p>
 * Features:
 * - Search across all filter lists
 * - Preset modes (Safe, Balanced, Aggressive)
 * - Category-based browsing
 * - Multi-select for batch adding
 * - Shows which lists are already added
 */
public class FilterCatalogActivity extends AppCompatActivity {

    private static final Executor EXECUTOR = AppExecutors.getInstance().diskIO();

    private RecyclerView recyclerView;
    private MaterialButton addButton;
    private MaterialButton importFromFilterListsButton;
    private TextView subtitleView;
    private EditText searchEditText;
    private ChipGroup presetChipGroup;
    private Chip chipSafe, chipBalanced, chipAggressive, chipCustom;
    private MaterialSwitch selectAllSwitch;

    private CatalogAdapter adapter;
    private HostsSourceDao hostsSourceDao;
    private Set<String> existingUrls = new HashSet<>();
    private Set<FilterListCatalog.CatalogEntry> selectedEntries = new HashSet<>();

    // All entries and filtered entries
    private List<FilterListCatalog.CatalogEntry> allEntries;
    private List<FilterListCatalog.CatalogEntry> filteredEntries;
    private String currentSearchQuery = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.filter_catalog_dialog);

        hostsSourceDao = AppDatabase.getInstance(this).hostsSourceDao();

        // Setup toolbar
        MaterialToolbar toolbar = findViewById(R.id.catalogToolbar);
        // Use this toolbar as the Activity ActionBar to avoid the default ActionBar
        // being shown above it
        // (which caused the "double back arrows / double titles" UI).
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
        // Standard "Up"/Back affordance
        toolbar.setNavigationIcon(R.drawable.baseline_arrow_back_24);

        // Setup views
        recyclerView = findViewById(R.id.catalogRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        addButton = findViewById(R.id.addSelectedButton);
        addButton.setOnClickListener(v -> addSelectedSources());

        importFromFilterListsButton = findViewById(R.id.importFromFilterListsButton);
        importFromFilterListsButton
                .setOnClickListener(v -> startActivity(new Intent(this, FilterListsImportActivity.class)));

        subtitleView = findViewById(R.id.catalogSubtitle);

        // Setup "Select All" switch
        selectAllSwitch = findViewById(R.id.catalogSelectAllSwitch);
        selectAllSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (filteredEntries == null)
                return;

            selectedEntries.clear();
            if (isChecked) {
                for (FilterListCatalog.CatalogEntry entry : filteredEntries) {
                    if (!existingUrls.contains(entry.url)) {
                        selectedEntries.add(entry);
                    }
                }
            }

            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            updateAddButton();
            // Automatically select "Custom" preset when using Select All
            chipCustom.setChecked(true);
        });

        // Setup search
        searchEditText = findViewById(R.id.searchEditText);
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString();
                filterEntries();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Setup preset chips
        presetChipGroup = findViewById(R.id.presetChipGroup);
        chipSafe = findViewById(R.id.chipSafe);
        chipBalanced = findViewById(R.id.chipBalanced);
        chipAggressive = findViewById(R.id.chipAggressive);
        chipCustom = findViewById(R.id.chipCustom);

        chipSafe.setOnClickListener(v -> applyPreset("safe"));
        chipBalanced.setOnClickListener(v -> applyPreset("balanced"));
        chipAggressive.setOnClickListener(v -> applyPreset("aggressive"));
        // "Custom" means manual selection mode (uBlock-style). Do NOT clear selection.
        chipCustom.setOnClickListener(v -> {
            // no-op; the chip is set automatically when user manually selects entries
            chipCustom.setChecked(true);
        });

        // Load data
        loadData();
    }

    private boolean onToolbarMenuItemClicked(MenuItem item) {
        if (item.getItemId() == R.id.action_go_home) {
            // Jump straight back to the app home screen (fast escape hatch).
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_filter_add_custom) {
            Intent intent = new Intent(this, SourceEditActivity.class);
            intent.putExtra(SourceEditActivity.EXTRA_INITIAL_LABEL, getString(R.string.filter_add_custom));
            intent.putExtra(SourceEditActivity.EXTRA_INITIAL_URL, "https://");
            intent.putExtra(SourceEditActivity.EXTRA_INITIAL_ALLOW, false);
            intent.putExtra(SourceEditActivity.EXTRA_INITIAL_REDIRECT, false);
            startActivity(intent);
            return true;
        } else if (item.getItemId() == R.id.action_filterlists_import) {
            startActivity(new Intent(this, FilterListsImportActivity.class));
            return true;
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.filter_catalog_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (onToolbarMenuItemClicked(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadData() {
        EXECUTOR.execute(() -> {
            // Get existing source URLs
            List<HostsSource> existing = hostsSourceDao.getAll();
            existingUrls.clear();
            for (HostsSource source : existing) {
                existingUrls.add(source.getUrl());
            }

            // Get all catalog entries
            allEntries = FilterListCatalog.getAll();
            filteredEntries = new ArrayList<>(allEntries);

            runOnUiThread(() -> {
                adapter = new CatalogAdapter();
                recyclerView.setAdapter(adapter);
                updateSubtitle();
            });
        });
    }

    private void filterEntries() {
        if (allEntries == null)
            return;

        filteredEntries = new ArrayList<>();
        String query = currentSearchQuery.toLowerCase(Locale.ROOT).trim();

        for (FilterListCatalog.CatalogEntry entry : allEntries) {
            if (query.isEmpty()) {
                filteredEntries.add(entry);
            } else {
                // Search in label, URL, and category name
                boolean matchesLabel = entry.label.toLowerCase(Locale.ROOT).contains(query);
                boolean matchesUrl = entry.url.toLowerCase(Locale.ROOT).contains(query);
                boolean matchesCategory = entry.category.name().toLowerCase(Locale.ROOT).contains(query);

                if (matchesLabel || matchesUrl || matchesCategory) {
                    filteredEntries.add(entry);
                }
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateSubtitle();

        // Uncheck "Select All" when filter changes because the visible set changes
        if (selectAllSwitch != null && selectAllSwitch.isChecked()) {
            // Avoid triggering listener loops if possible, or just accept it clears
            // selection
            selectAllSwitch.setChecked(false);
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

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateAddButton();

        // Show info about what was selected
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

        if (!message.isEmpty()) {
            Snackbar.make(recyclerView, message, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void clearSelection() {
        selectedEntries.clear();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateAddButton();
    }

    private void updateSubtitle() {
        int total = allEntries != null ? allEntries.size() : 0;
        int showing = filteredEntries != null ? filteredEntries.size() : 0;

        if (currentSearchQuery.isEmpty()) {
            subtitleView.setText(getString(R.string.filter_catalog_count, total));
        } else {
            subtitleView.setText(String.format(Locale.ROOT, "Showing %d of %d", showing, total));
        }
    }

    private void updateAddButton() {
        int count = selectedEntries.size();
        addButton.setEnabled(count > 0);
        if (count > 0) {
            addButton.setText(getString(R.string.filter_catalog_add_selected) + " (" + count + ")");
        } else {
            addButton.setText(R.string.filter_catalog_add_selected);
        }
    }

    private void addSelectedSources() {
        if (selectedEntries.isEmpty())
            return;

        addButton.setEnabled(false);
        addButton.setText(R.string.filter_update_started);

        EXECUTOR.execute(() -> {
            int added = 0;
            for (FilterListCatalog.CatalogEntry entry : selectedEntries) {
                if (!existingUrls.contains(entry.url)) {
                    HostsSource source = entry.toHostsSource();
                    // If the user selected it, enable it (uBlock-style behavior).
                    source.setEnabled(true);
                    hostsSourceDao.insert(source);
                    existingUrls.add(entry.url);
                    added++;
                }
            }

            final int finalAdded = added;
            runOnUiThread(() -> {
                selectedEntries.clear();
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                updateAddButton();

                Snackbar.make(
                        recyclerView,
                        getString(R.string.filter_added_success) + " (" + finalAdded + ")",
                        Snackbar.LENGTH_SHORT).show();

                // Finish after brief delay to show snackbar
                recyclerView.postDelayed(this::finish, 1500);
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
            runOnUiThread(() -> {
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                Snackbar.make(recyclerView, R.string.filter_removed_success, Snackbar.LENGTH_SHORT).show();
                updateSubtitle();
            });
        });
    }

    /**
     * Adapter for the catalog list with search support.
     */
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

            // Color code the badge based on category risk level
            if (entry.category.mayBreakServices()) {
                // Warning color for risky categories
                holder.badge.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                getResources().getColor(R.color.blocked, getTheme())));
                holder.badge.setTextColor(getResources().getColor(android.R.color.white, getTheme()));
            } else {
                // Normal color for safe categories
                holder.badge.setBackgroundTintList(null);
            }

            // Show URL as description
            String urlPreview = entry.url;
            if (urlPreview.length() > 55) {
                urlPreview = urlPreview.substring(0, 52) + "...";
            }
            holder.description.setText(urlPreview);

            // Already added state
            holder.alreadyAdded.setVisibility(alreadyAdded ? View.VISIBLE : View.GONE);
            holder.toggle.setVisibility(alreadyAdded ? View.GONE : View.VISIBLE);

            // Avoid triggering listener during bind
            holder.toggle.setOnCheckedChangeListener(null);
            holder.toggle.setChecked(isSelected);

            holder.toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedEntries.add(entry);
                } else {
                    selectedEntries.remove(entry);
                    if (selectAllSwitch.isChecked()) {
                        selectAllSwitch.setChecked(false);
                    }
                }
                updateAddButton();
                chipCustom.setChecked(true);
            });

            // Card checked state
            holder.card.setChecked(isSelected);

            // Disable selection for already-added items
            if (alreadyAdded) {
                holder.card.setAlpha(0.7f);
                holder.card.setOnClickListener(v -> {
                    // Open editor for already-added sources
                    EXECUTOR.execute(() -> {
                        HostsSource existing = hostsSourceDao.getByUrl(entry.url).orElse(null);
                        if (existing == null)
                            return;
                        runOnUiThread(() -> {
                            Intent intent = new Intent(FilterCatalogActivity.this,
                                    org.adaway.ui.source.SourceEditActivity.class);
                            intent.putExtra(org.adaway.ui.source.SourceEditActivity.SOURCE_ID, existing.getId());
                            startActivity(intent);
                        });
                    });
                });
                holder.card.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(FilterCatalogActivity.this)
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
                    if (selectedEntries.contains(entry)) {
                        selectedEntries.remove(entry);
                    } else {
                        selectedEntries.add(entry);
                    }

                    // Update switch visual state
                    holder.toggle.setChecked(!selectedEntries.contains(entry)); // Toggle was already clicked, so state
                                                                                // is opposite? No, click listener is on
                                                                                // card.
                    // Actually, if we click card, we toggle the state.
                    boolean newState = selectedEntries.contains(entry);
                    holder.toggle.setChecked(newState);

                    notifyItemChanged(position);
                    updateAddButton();
                });
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
