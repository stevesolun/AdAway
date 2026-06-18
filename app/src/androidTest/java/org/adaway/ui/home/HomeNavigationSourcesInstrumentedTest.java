package org.adaway.ui.home;

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
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.hosts.HostsSourcesFragment;
import org.adaway.ui.hosts.HostsSourcesTabFragment;
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
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up sources navigation");
    }

    @After
    public void tearDown() {
        if (this.context != null) {
            InstrumentedTestState.resetForPassiveRootUi(this.context, "tear down sources navigation");
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
