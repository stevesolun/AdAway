package org.adaway.ui.home;

import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.ui.hosts.HostsSourcesFragment;
import org.adaway.ui.hosts.HostsSourcesTabFragment;
import org.adaway.util.Constants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class HomeNavigationSourcesInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        this.context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        PreferenceHelper.setAbBlockMethod(this.context, ROOT);
    }

    @After
    public void tearDown() {
        if (this.context != null) {
            this.context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit();
        }
    }

    @Test
    public void sourcesTabShowsSourceManagementWithToolbarActions() {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> activity.navigateTo(R.id.nav_sources));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                BottomNavigationView nav = activity.findViewById(R.id.bottom_navigation);
                assertEquals(R.id.nav_sources, nav.getSelectedItemId());

                Fragment selected = activity.getSupportFragmentManager()
                        .findFragmentByTag("sources");
                assertTrue(selected instanceof HostsSourcesTabFragment);

                Fragment child = selected.getChildFragmentManager()
                        .findFragmentById(R.id.hosts_sources_child_container);
                assertTrue(child instanceof HostsSourcesFragment);

                MaterialToolbar toolbar = activity.findViewById(R.id.hosts_sources_toolbar);
                assertNotNull(toolbar);
                assertNotNull(toolbar.getMenu().findItem(R.id.action_hosts_update_all));
            });
        }
    }
}
