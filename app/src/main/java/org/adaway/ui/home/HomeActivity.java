package org.adaway.ui.home;

import static org.adaway.model.adblocking.AdBlockMethod.UNDEFINED;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.adaway.R;
import org.adaway.databinding.ActivityHomeNavBinding;
import org.adaway.helper.NotificationHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.ThemeHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.ui.discover.DiscoverFragment;
import org.adaway.ui.more.MoreFragment;
import org.adaway.ui.onboarding.DefaultListsSubscriber;
import org.adaway.ui.onboarding.OnboardingActivity;
import org.adaway.util.AppExecutors;

import timber.log.Timber;

/**
 * Navigation shell activity.
 *
 * Hosts three tabs via BottomNavigationView:
 *   - Home     → {@link HomeFragment}
 *   - Discover → {@link DiscoverFragment}
 *   - More     → {@link MoreFragment}
 *
 * All heavy home-screen logic (stats, progress, FAB) lives in {@link HomeFragment}.
 */
public class HomeActivity extends AppCompatActivity {

    public static final String EXTRA_ONBOARDING_COMPLETE = "onboarding_complete";
    /** When set to {@code true} on the launch intent, the Discover tab is shown on open. */
    public static final String EXTRA_NAV_DISCOVER = "nav_discover";
    private static final String KEY_SELECTED_TAB = "selected_tab";

    private ActivityHomeNavBinding binding;
    private int currentSelectedId = R.id.nav_home;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        NotificationHelper.clearUpdateNotifications(this);
        Timber.i("HomeActivity: starting nav shell");

        this.binding = ActivityHomeNavBinding.inflate(getLayoutInflater());
        setContentView(this.binding.getRoot());

        if (getIntent().getBooleanExtra(EXTRA_ONBOARDING_COMPLETE, false)) {
            AppExecutors.getInstance().diskIO().execute(() -> {
                boolean added = DefaultListsSubscriber.subscribeDefaultsIfEmpty(this);
                if (added) {
                    runOnUiThread(() -> {
                        HomeViewModel vm = new ViewModelProvider(this).get(HomeViewModel.class);
                        vm.update();
                    });
                }
            });
        }

        if (savedInstanceState != null) {
            this.currentSelectedId = savedInstanceState.getInt(KEY_SELECTED_TAB, R.id.nav_home);
        }

        setupBottomNavigation();

        // Restore or select default tab
        if (savedInstanceState == null) {
            int startTab = getIntent().getBooleanExtra(EXTRA_NAV_DISCOVER, false)
                    ? R.id.nav_discover : R.id.nav_home;
            showTab(startTab);
        } else {
            // Fragment manager restores the fragments automatically; just sync the selected item.
            this.binding.bottomNavigation.setSelectedItemId(this.currentSelectedId);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra(EXTRA_NAV_DISCOVER, false)) {
            showTab(R.id.nav_discover);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkFirstStep();
    }

    @Override
    protected void onSaveInstanceState(@Nullable Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_TAB, this.currentSelectedId);
    }

    @Override
    public void onBackPressed() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
            // Re-seat the current tab fragment since replace() without backstack
            // leaves the container empty after AdwareFragment is popped.
            showTab(this.currentSelectedId);
        } else {
            super.onBackPressed();
        }
    }

    // -----------------------------------------------------------------------------------------
    // Navigation
    // -----------------------------------------------------------------------------------------

    private void setupBottomNavigation() {
        this.binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == this.currentSelectedId) {
                // Already on this tab — do nothing (avoids unnecessary replace)
                return true;
            }
            showTab(id);
            return true;
        });
        // Reselect: no-op (don't re-inflate fragment on double-tap)
        this.binding.bottomNavigation.setOnItemReselectedListener(item -> { });
    }

    private void showTab(int menuItemId) {
        this.currentSelectedId = menuItemId;

        Fragment fragment;
        String tag;
        if (menuItemId == R.id.nav_discover) {
            fragment = new DiscoverFragment();
            tag = "discover";
        } else if (menuItemId == R.id.nav_more) {
            fragment = new MoreFragment();
            tag = "more";
        } else {
            fragment = new HomeFragment();
            tag = "home";
        }

        // Use replace() per plan — avoids fragment stack depth issues
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_fragment_container, fragment, tag)
                .commit();

        // Sync indicator (in case called programmatically without user tap)
        this.binding.bottomNavigation.setSelectedItemId(menuItemId);
    }

    // -----------------------------------------------------------------------------------------
    // First-run guard
    // -----------------------------------------------------------------------------------------

    private void checkFirstStep() {
        AdBlockMethod adBlockMethod = PreferenceHelper.getAdBlockMethod(this);
        if (adBlockMethod == UNDEFINED) {
            // Not set up yet — go to onboarding
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
        } else if (adBlockMethod == VPN) {
            // VPN permission may have been revoked; if so, let HomeFragment handle the
            // re-authorization via its FAB click handler. No redirect needed here.
            Intent prepareIntent = VpnService.prepare(this);
            if (prepareIntent != null) {
                // HomeFragment will re-ask when the user taps the FAB.
                // We don't launch it automatically here to avoid interrupting navigation.
            }
        }
    }

    /**
     * Navigate to a specific bottom-nav tab from a Fragment.
     *
     * @param navItemId One of {@code R.id.nav_home}, {@code R.id.nav_discover},
     *                  {@code R.id.nav_more}.
     */
    public void navigateTo(int navItemId) {
        showTab(navItemId);
    }
}
