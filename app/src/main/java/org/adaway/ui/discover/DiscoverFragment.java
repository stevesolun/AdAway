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
import org.adaway.db.entity.HostsSource;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.util.AppExecutors;

import java.util.List;

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

        // Shift content below the status bar
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, insets.top, 0, 0);
            return windowInsets;
        });

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
            int added = 0;
            for (FilterListCatalog.CatalogEntry entry : entries) {
                if (!db.hostsSourceDao().getByUrl(entry.url).isPresent()) {
                    HostsSource source = entry.toHostsSource();
                    source.setEnabled(true);
                    db.hostsSourceDao().insert(source);
                    added++;
                }
            }

            final int finalAdded = added;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (this.binding == null) return;
                String msg = finalAdded > 0
                        ? "Added " + finalAdded + " filter lists"
                        : "Already subscribed to these lists";
                Snackbar.make(this.binding.getRoot(), msg, Snackbar.LENGTH_SHORT).show();
            });
        });
    }
}
