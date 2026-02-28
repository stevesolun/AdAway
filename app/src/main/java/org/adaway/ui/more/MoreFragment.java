package org.adaway.ui.more;

import android.content.Intent;
import android.net.Uri;
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
import androidx.fragment.app.FragmentTransaction;

import org.adaway.R;
import org.adaway.databinding.FragmentMoreBinding;
import org.adaway.ui.adware.AdwareFragment;
import org.adaway.ui.about.AboutActivity;
import org.adaway.ui.hosts.HostsSourcesActivity;
import org.adaway.ui.lists.ListsActivity;
import org.adaway.ui.log.LogActivity;
import org.adaway.ui.prefs.PrefsActivity;

/**
 * More tab: list of tools and settings entries.
 * Each row opens the existing Activity for the feature.
 */
public class MoreFragment extends Fragment {

    /** URL opened by the GitHub / Help row. */
    private static final String GITHUB_URL = "https://github.com/AdAway/AdAway";

    private FragmentMoreBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        this.binding = FragmentMoreBinding.inflate(inflater, container, false);
        return this.binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindRows();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        this.binding = null;
    }

    private void bindRows() {
        // DNS Log
        this.binding.moreRowDnsLog.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), LogActivity.class)));

        // Custom Host Rules
        this.binding.moreRowCustomRules.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), ListsActivity.class);
            startActivity(intent);
        });

        // Filter Sources
        this.binding.moreRowFilterSources.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), HostsSourcesActivity.class)));

        // Adware Scanner — AdwareFragment is a Fragment, so show in a dialog-style transaction.
        this.binding.moreRowAdwareScanner.setOnClickListener(v -> showAdwareScanner());

        // Preferences
        this.binding.moreRowPreferences.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), PrefsActivity.class)));

        // Backup & Restore — currently inside PrefsActivity
        this.binding.moreRowBackup.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), PrefsActivity.class)));

        // About
        this.binding.moreRowAbout.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AboutActivity.class)));

        // GitHub / Help
        this.binding.moreRowGithub.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))));
    }

    private void showAdwareScanner() {
        // AdwareFragment is a standalone Fragment; show it by navigating to a dedicated
        // container inside the activity. If the adware fragment is already on the back
        // stack we don't push it again.
        if (getParentFragmentManager().findFragmentByTag("adware") != null) return;

        AdwareFragment fragment = new AdwareFragment();
        getParentFragmentManager().beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(R.id.nav_fragment_container, fragment, "adware")
                .commit();
    }
}
