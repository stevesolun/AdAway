package org.adaway.ui.hosts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;

import org.adaway.R;

public class HostsSourcesTabFragment extends Fragment {
    private static final String CHILD_TAG = "sources_child";

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_hosts_sources_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialToolbar toolbar = view.findViewById(R.id.hosts_sources_toolbar);
        toolbar.inflateMenu(R.menu.hosts_sources_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            Fragment child = getChildFragmentManager()
                    .findFragmentById(R.id.hosts_sources_child_container);
            return child instanceof HostsSourcesFragment
                    && ((HostsSourcesFragment) child).handleMenuItem(item);
        });

        if (getChildFragmentManager().findFragmentByTag(CHILD_TAG) == null) {
            getChildFragmentManager()
                    .beginTransaction()
                    .replace(R.id.hosts_sources_child_container,
                            new HostsSourcesFragment(), CHILD_TAG)
                    .commit();
        }
    }
}
