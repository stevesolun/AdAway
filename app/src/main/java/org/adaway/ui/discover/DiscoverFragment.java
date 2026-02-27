package org.adaway.ui.discover;

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
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.google.android.material.tabs.TabLayoutMediator;

import org.adaway.databinding.FragmentDiscoverBinding;

/**
 * Discover tab: two sub-tabs — Curated filters and FilterLists.com.
 *
 * Phase 2: sub-tabs use embedded Fragments (DiscoverCatalogFragment + DiscoverFilterListsFragment).
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
        // Shift content below the status bar dynamically
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, insets.top, 0, 0);
            return windowInsets;
        });
        setupTabs();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        this.binding = null;
    }

    private void setupTabs() {
        DiscoverPagerAdapter adapter = new DiscoverPagerAdapter(this);
        this.binding.discoverViewPager.setAdapter(adapter);

        new TabLayoutMediator(this.binding.discoverTabLayout, this.binding.discoverViewPager,
                (tab, position) -> {
                    if (position == 0) {
                        tab.setText(org.adaway.R.string.discover_tab_curated);
                    } else {
                        tab.setText(org.adaway.R.string.discover_tab_filterlists);
                    }
                }).attach();
    }

    // -----------------------------------------------------------------------------------------
    // Inner adapter
    // -----------------------------------------------------------------------------------------

    private static class DiscoverPagerAdapter extends FragmentStateAdapter {

        DiscoverPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) return new DiscoverCatalogFragment();
            return new DiscoverFilterListsFragment();
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}
