/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of AdAway.
 *
 * AdAway is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdAway is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.adaway.ui.hosts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.snackbar.Snackbar;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.SourceModel;
import org.adaway.ui.adblocking.ApplyConfigurationSnackbar;
import org.adaway.ui.home.HomeActivity;
import org.adaway.ui.source.SourceEditActivity;
import org.adaway.util.AppExecutors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.adaway.ui.source.SourceEditActivity.SOURCE_ID;

/**
 * This class is a {@link Fragment} to display and manage hosts sources.
 * <p>
 * Uses a categorized view inspired by uBlock Origin, organizing filter lists
 * into expandable categories (Ads, Privacy, Malware, Social, etc.).
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HostsSourcesFragment extends Fragment implements HostsSourcesViewCallback {
    /**
     * The view model (<code>null</code> if view is not created).
     */
    private HostsSourcesViewModel mViewModel;
    private CategorizedSourcesAdapter mAdapter;
    private CoordinatorLayout coordinatorLayout;
    private List<HostsSource> lastSources = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Get activity
        Activity activity = requireActivity();
        // Initialize view model
        this.mViewModel = new ViewModelProvider(this).get(HostsSourcesViewModel.class);
        LifecycleOwner lifecycleOwner = getViewLifecycleOwner();
        // Create fragment view
        View view = inflater.inflate(R.layout.hosts_sources_fragment, container, false);
        setHasOptionsMenu(true);
        
        /*
         * Configure snackbar.
         */
        // Get lists layout to attached snackbar to
        coordinatorLayout = view.findViewById(R.id.coordinator);
        // Create apply snackbar
        ApplyConfigurationSnackbar applySnackbar = new ApplyConfigurationSnackbar(coordinatorLayout, true, true);
        // Bind snackbar to view models
        this.mViewModel.getHostsSources().observe(lifecycleOwner, applySnackbar.createObserver());
        
        /*
         * Configure recycler view with categorized adapter.
         */
        RecyclerView recyclerView = view.findViewById(R.id.hosts_sources_list);
        recyclerView.setHasFixedSize(false); // Categories expand/collapse
        
        // Use LinearLayoutManager
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(activity);
        recyclerView.setLayoutManager(linearLayoutManager);
        
        // Create the new categorized adapter (uBlock-style)
        mAdapter = new CategorizedSourcesAdapter(this);
        recyclerView.setAdapter(mAdapter);
        
        // Bind adapter to view model
        this.mViewModel.getHostsSources().observe(lifecycleOwner, sources -> {
            lastSources = sources != null ? new ArrayList<>(sources) : new ArrayList<>();
            mAdapter.updateSources(sources);
        });
        
        /*
         * Add floating action button for adding sources.
         * Shows a bottom sheet with options to browse catalog or add custom.
         */
        FloatingActionButton button = view.findViewById(R.id.hosts_sources_add);
        button.setOnClickListener(actionButton -> showAddSourceOptions());
        
        // Return fragment view
        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.hosts_sources_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_hosts_check_updates) {
            runCheckUpdates();
            return true;
        } else if (id == R.id.action_hosts_update_all) {
            updateAllSources();
            return true;
        } else if (id == R.id.action_hosts_save_filter_set) {
            promptSaveFilterSet();
            return true;
        } else if (id == R.id.action_hosts_apply_filter_set) {
            promptApplyFilterSet();
            return true;
        } else if (id == R.id.action_hosts_schedule_filter_set) {
            promptScheduleFilterSet();
            return true;
        } else if (id == R.id.action_hosts_manage_schedules) {
            startActivity(new Intent(requireContext(), SchedulesActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void requestAddCustomSource() {
        showAddSourceOptions();
    }

    private void promptSaveFilterSet() {
        TextInputLayout layout = new TextInputLayout(requireContext());
        layout.setHint(getString(R.string.filter_set_name_hint));
        TextInputEditText editText = new TextInputEditText(requireContext());
        layout.addView(editText);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.menu_save_filter_set)
                .setView(layout)
                .setPositiveButton(R.string.button_save, (d, which) -> {
                    String name = editText.getText() != null ? editText.getText().toString().trim() : "";
                    if (name.isEmpty()) return;
                    saveFilterSet(name);
                    Snackbar.make(coordinatorLayout, R.string.filter_set_saved, Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void saveFilterSet(String name) {
        Set<String> enabledUrls = new HashSet<>();
        for (HostsSource s : lastSources) {
            if (s.getId() != HostsSource.USER_SOURCE_ID && s.isEnabled()) {
                enabledUrls.add(s.getUrl());
            }
        }
        FilterSetStore.saveSet(requireContext(), name, enabledUrls);
    }

    private void promptApplyFilterSet() {
        Set<String> names = FilterSetStore.getSetNames(requireContext());
        if (names == null || names.isEmpty()) {
            Snackbar.make(coordinatorLayout, R.string.filter_set_none, Snackbar.LENGTH_SHORT).show();
            return;
        }
        String[] arr = names.toArray(new String[0]);
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.menu_apply_filter_set)
                .setItems(arr, (d, which) -> applyFilterSet(arr[which]))
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void applyFilterSet(String name) {
        Set<String> enabledUrls = FilterSetStore.getSetUrls(requireContext(), name);
        HostsSourceDao dao = AppDatabase.getInstance(requireContext()).hostsSourceDao();

        Snackbar waitSnackbar = Snackbar.make(coordinatorLayout, R.string.notification_configuration_installing, Snackbar.LENGTH_INDEFINITE);
        waitSnackbar.show();

        AppExecutors.getInstance().diskIO().execute(() -> {
            for (HostsSource s : lastSources) {
                if (s.getId() == HostsSource.USER_SOURCE_ID) continue;
                boolean shouldEnable = enabledUrls.contains(s.getUrl());
                if (s.isEnabled() == shouldEnable) continue;
                dao.setSourceEnabled(s.getId(), shouldEnable);
                dao.setSourceItemsEnabled(s.getId(), shouldEnable);
            }
            FilterSetUpdateService.enable(requireContext());
            AppExecutors.getInstance().mainThread().execute(() -> {
                waitSnackbar.dismiss();
                Snackbar.make(coordinatorLayout, R.string.filter_set_applied, Snackbar.LENGTH_SHORT).show();
            });
        });
    }

    private void promptScheduleFilterSet() {
        // Let user schedule current selection or any saved set.
        Set<String> names = new HashSet<>(FilterSetStore.getSetNames(requireContext()));
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.filter_set_schedule_current_selection));
        options.addAll(names);
        String[] arr = options.toArray(new String[0]);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.menu_schedule_filter_set)
                .setItems(arr, (d, which) -> {
                    String choice = arr[which];
                    if (choice.equals(getString(R.string.filter_set_schedule_current_selection))) {
                        // Save current selection as a real set so it can be scheduled.
                        String name = getString(R.string.filter_set_schedule_current_selection);
                        saveFilterSet(name);
                        promptScheduleForOne(name);
                    } else {
                        promptScheduleForOne(choice);
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void promptScheduleForOne(String name) {
        CharSequence[] scheduleOptions = new CharSequence[]{
                getString(R.string.filter_set_schedule_off),
                getString(R.string.filter_set_schedule_daily),
                getString(R.string.filter_set_schedule_weekly)
        };
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(name)
                .setItems(scheduleOptions, (d, which) -> {
                    if (which == 0) {
                        FilterSetStore.setSchedule(requireContext(), name, FilterSetStore.SCHEDULE_OFF);
                        FilterSetUpdateService.enable(requireContext());
                        Snackbar.make(coordinatorLayout, "Scheduled: " + scheduleOptions[which], Snackbar.LENGTH_SHORT).show();
                        return;
                    }
                    if (which == 1) {
                        // Daily -> pick time
                        pickTime((hour, minute) -> {
                            FilterSetStore.setSchedule(requireContext(), name, FilterSetStore.SCHEDULE_DAILY, 1, hour, minute);
                            FilterSetUpdateService.enable(requireContext());
                            Snackbar.make(coordinatorLayout, "Scheduled: " + scheduleOptions[which], Snackbar.LENGTH_SHORT).show();
                        });
                        return;
                    }
                    // Weekly -> pick day then time
                    pickDayOfWeek(dowIso -> pickTime((hour, minute) -> {
                        FilterSetStore.setSchedule(requireContext(), name, FilterSetStore.SCHEDULE_WEEKLY, dowIso, hour, minute);
                        FilterSetUpdateService.enable(requireContext());
                        Snackbar.make(coordinatorLayout, "Scheduled: " + scheduleOptions[which], Snackbar.LENGTH_SHORT).show();
                    }));
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private interface TimePicked {
        void onPicked(int hour24, int minute);
    }

    private void pickTime(@NonNull TimePicked picked) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        int hour = c.get(java.util.Calendar.HOUR_OF_DAY);
        int minute = c.get(java.util.Calendar.MINUTE);
        new android.app.TimePickerDialog(
                requireContext(),
                (view, hourOfDay, minuteOfHour) -> picked.onPicked(hourOfDay, minuteOfHour),
                hour,
                minute,
                DateFormat.is24HourFormat(requireContext())
        ).show();
    }

    private interface DayPicked {
        void onPicked(int isoDay);
    }

    private void pickDayOfWeek(@NonNull DayPicked picked) {
        String[] days = new String[]{
                "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
        };
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.filter_set_schedule_pick_day)
                .setItems(days, (d, which) -> picked.onPicked(which + 1))
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    /**
     * Shows a bottom sheet with options to add filter lists.
     * - Browse catalog: Open curated filter list catalog
     * - Add custom: Add a custom URL source
     */
    private void showAddSourceOptions() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheetView = getLayoutInflater().inflate(R.layout.hosts_add_options_sheet, null);
        
        // Browse catalog option — opens Discover tab in HomeActivity (catalog is embedded there)
        View catalogOption = sheetView.findViewById(R.id.browseCatalogOption);
        catalogOption.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(requireContext(), HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra(HomeActivity.EXTRA_NAV_DISCOVER, true);
            startActivity(intent);
        });
        
        // Add custom source option
        View customOption = sheetView.findViewById(R.id.addCustomOption);
        customOption.setOnClickListener(v -> {
            dialog.dismiss();
            startSourceEdition(null);
        });

        // Manage schedules option
        View schedulesOption = sheetView.findViewById(R.id.manageSchedulesOption);
        schedulesOption.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(requireContext(), SchedulesActivity.class));
        });
        
        dialog.setContentView(sheetView);
        dialog.show();
    }

    @Override
    public void setEnabled(HostsSource source, boolean enabled) {
        this.mViewModel.setSourceEnabled(source, enabled);
    }

    @Override
    public void edit(HostsSource source) {
        startSourceEdition(source);
    }

    @Override
    public void updateSource(HostsSource source) {
        runUpdateSources(source);
    }

    @Override
    public void updateAllSources() {
        runUpdateSources(null);
    }

    private void startSourceEdition(@Nullable HostsSource source) {
        Intent intent = new Intent(requireContext(), SourceEditActivity.class);
        if (source != null) {
            intent.putExtra(SOURCE_ID, source.getId());
        }
        startActivity(intent);
    }

    private void runCheckUpdates() {
        Snackbar info = Snackbar.make(coordinatorLayout, R.string.status_check, Snackbar.LENGTH_LONG);
        info.show();
        AppExecutors.getInstance().diskIO().execute(() -> {
            AdAwayApplication application = (AdAwayApplication) requireContext().getApplicationContext();
            SourceModel sourceModel = application.getSourceModel();
            try {
                sourceModel.checkForUpdate();
            } catch (HostErrorException ignored) {
                // Keep it simple: failures are handled elsewhere in the app
            }
        });
    }

    /**
     * Update sources (download + parse) then apply the adblocking config.
     * If {@code source} is null, updates all enabled sources.
     */
    private void runUpdateSources(@Nullable HostsSource source) {
        Snackbar waitSnackbar = Snackbar.make(
                coordinatorLayout,
                R.string.notification_configuration_installing,
                Snackbar.LENGTH_INDEFINITE
        );
        waitSnackbar.show();

        AppExecutors.getInstance().diskIO().execute(() -> {
            AdAwayApplication application = (AdAwayApplication) requireContext().getApplicationContext();
            SourceModel sourceModel = application.getSourceModel();
            AdBlockModel adBlockModel = application.getAdBlockModel();
            boolean ok = true;
            try {
                if (source == null) {
                    sourceModel.checkAndRetrieveHostsSources();
                } else {
                    sourceModel.retrieveHostsSource(source.getId());
                }
                adBlockModel.apply();
            } catch (HostErrorException e) {
                ok = false;
            }

            boolean finalOk = ok;
            AppExecutors.getInstance().mainThread().execute(() -> {
                waitSnackbar.dismiss();
                if (!finalOk) {
                    Snackbar.make(coordinatorLayout, R.string.notification_configuration_failed, Snackbar.LENGTH_LONG).show();
                } else {
                    Snackbar.make(coordinatorLayout, R.string.notification_configuration_changed, Snackbar.LENGTH_LONG).show();
                }
            });
        });
    }
}
