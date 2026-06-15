package org.adaway.ui.discover;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;

import org.adaway.R;
import org.adaway.databinding.FragmentDiscoverBinding;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.model.source.SourceUpdateService;
import org.adaway.ui.hosts.FilterProfileState;
import org.adaway.ui.hosts.FilterSetStore;
import org.adaway.util.AppExecutors;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Discover tab: FilterLists.com browser is the primary content (immediately visible).
 * Quick-start preset chips at top let users subscribe a curated set in one tap.
 * No sub-tabs — zero barriers.
 */
public class DiscoverFragment extends Fragment {

    private FragmentDiscoverBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        this.binding = FragmentDiscoverBinding.inflate(inflater, container, false);
        return this.binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        // Embed the full FilterLists.com browser as child fragment
        if (savedInstanceState == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.discoverBrowserContainer, new DiscoverFilterListsFragment())
                    .commit();
        }

        // Quick-start preset chips — subscribe a curated set in one tap
        this.binding.chipDiscoverSafe.setOnClickListener(v -> applyPreset("safe"));
        this.binding.chipDiscoverBalanced.setOnClickListener(v -> applyPreset("balanced"));
        this.binding.chipDiscoverAggressive.setOnClickListener(v -> applyPreset("aggressive"));

        updateProfileStatus();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateProfileStatus();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        this.binding = null;
    }

    /**
     * Subscribe all curated lists for the given preset in one tap.
     */
    private void applyPreset(String preset) {
        final Context appContext = requireContext().getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<FilterListCatalog.CatalogEntry> entries;
            switch (preset) {
                case "safe":       entries = FilterListCatalog.getDefaults();       break;
                case "balanced":   entries = FilterListCatalog.getBalancedPreset(); break;
                case "aggressive": entries = FilterListCatalog.getAggressivePreset(); break;
                default:           return;
            }

            AppDatabase db = AppDatabase.getInstance(appContext);
            HostsSourceDao sourceDao = db.hostsSourceDao();
            int added = 0;
            int existingNeedingUpdate = 0;
            Set<String> profileUrls = new HashSet<>();
            for (FilterListCatalog.CatalogEntry entry : entries) {
                profileUrls.add(entry.url);
                Optional<HostsSource> existingSource = sourceDao.getByUrl(entry.url);
                if (existingSource.isPresent()) {
                    HostsSource source = existingSource.get();
                    boolean needsUpdate = source.getLocalModificationDate() == null
                            || source.getSize() <= 0
                            || source.getLastDownloadError() != null;
                    if (!source.isEnabled()) {
                        sourceDao.setSourceEnabled(source.getId(), true);
                        sourceDao.setSourceItemsEnabled(source.getId(), true);
                        needsUpdate = true;
                    }
                    if (needsUpdate) {
                        existingNeedingUpdate++;
                    }
                } else {
                    HostsSource source = entry.toHostsSource();
                    source.setEnabled(true);
                    sourceDao.insert(source);
                    added++;
                }
            }
            FilterSetStore.savePresetProfile(appContext, preset, profileUrls);

            final int finalAdded = added;
            final int finalExistingNeedingUpdate = existingNeedingUpdate;
            final boolean shouldUpdateNow = finalAdded > 0 || finalExistingNeedingUpdate > 0;
            if (shouldUpdateNow) {
                SourceUpdateService.enqueueUpdateNow(appContext);
            }
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (this.binding == null) return;
                updateProfileStatus();
                String msg;
                if (finalAdded > 0) {
                    msg = getResources().getQuantityString(
                            R.plurals.filter_preset_added_updating, finalAdded, finalAdded);
                } else if (finalExistingNeedingUpdate > 0) {
                    msg = getResources().getQuantityString(
                            R.plurals.filter_preset_existing_updating,
                            finalExistingNeedingUpdate,
                            finalExistingNeedingUpdate);
                } else {
                    msg = getString(R.string.filter_preset_already_subscribed);
                }
                Snackbar.make(this.binding.getRoot(), msg, Snackbar.LENGTH_SHORT).show();
            });
        });
    }

    private void updateProfileStatus() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            Set<String> enabledUrls = getEnabledSourceUrls(appContext);
            String activeProfile = FilterSetStore.reconcileActiveProfile(appContext, enabledUrls);
            Set<String> profileUrls = FilterSetStore.getSetUrls(appContext, activeProfile);
            FilterProfileState state = FilterProfileState.resolve(profileUrls, enabledUrls);
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (this.binding == null) return;
                this.binding.discoverProfileStatus.setText(
                        formatProfileStatus(activeProfile, state));
            });
        });
    }

    @NonNull
    private Set<String> getEnabledSourceUrls(@NonNull Context appContext) {
        Set<String> enabledUrls = new HashSet<>();
        HostsSourceDao sourceDao = AppDatabase.getInstance(appContext).hostsSourceDao();
        for (HostsSource source : sourceDao.getAll()) {
            if (source.getId() != HostsSource.USER_SOURCE_ID && source.isEnabled()) {
                enabledUrls.add(source.getUrl());
            }
        }
        return enabledUrls;
    }

    @NonNull
    private String formatProfileStatus(@NonNull String profile, @NonNull FilterProfileState state) {
        if (FilterSetStore.PROFILE_CUSTOM.equals(profile) || state == FilterProfileState.NONE) {
            return getString(R.string.filter_profile_status_custom);
        }
        String label = formatProfileLabel(profile);
        switch (state) {
            case EXACT:
                return getString(R.string.filter_profile_status_exact, label);
            case EXTENDED:
                return getString(R.string.filter_profile_status_extended, label);
            case PARTIAL:
            default:
                return getString(R.string.filter_profile_status_partial, label);
        }
    }

    @NonNull
    private String formatProfileLabel(@NonNull String profile) {
        switch (profile) {
            case FilterSetStore.PROFILE_SAFE:
                return getString(R.string.filter_preset_safe);
            case FilterSetStore.PROFILE_BALANCED:
                return getString(R.string.filter_preset_balanced);
            case FilterSetStore.PROFILE_AGGRESSIVE:
                return getString(R.string.filter_preset_aggressive);
            default:
                return profile;
        }
    }
}
