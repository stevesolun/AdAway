package org.adaway.ui.home;

import static org.adaway.model.adblocking.AdBlockMethod.UNDEFINED;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;

import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.databinding.ActivityHomeNavBinding;
import org.adaway.helper.NotificationHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.ThemeHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.ui.discover.DiscoverFragment;
import org.adaway.ui.hosts.HostsSourcesTabFragment;
import org.adaway.ui.more.MoreFragment;
import org.adaway.ui.onboarding.InitialSourcesViewModel;
import org.adaway.ui.onboarding.OnboardingActivity;
import org.adaway.util.AppExecutors;
import org.adaway.util.Constants;

import java.util.concurrent.CompletableFuture;

import timber.log.Timber;

/**
 * Navigation shell activity.
 *
 * Hosts three tabs via BottomNavigationView:
 *   - Home     → {@link HomeFragment}
 *   - Discover → {@link DiscoverFragment}
 *   - Sources  → {@link HostsSourcesTabFragment}
 *   - More     → {@link MoreFragment}
 *
 * Home status, progress and inline controls live in {@link HomeFragment}.
 */
public class HomeActivity extends AppCompatActivity {

    public static final String EXTRA_ONBOARDING_COMPLETE = "onboarding_complete";
    /** When set to {@code true} on the launch intent, the Discover tab is shown on open. */
    public static final String EXTRA_NAV_DISCOVER = "nav_discover";
    private static final String KEY_SELECTED_TAB = "selected_tab";
    private static final String KEY_VPN_ENABLE_REQUESTED = "vpn_enable_requested";
    private static CompletableFuture<Void> hostsStartupCheck; // Assigned only from onCreate.
    private static final String STARTUP_SOURCE_CURSOR = "hostsStartupLastCheckedId";

    private ActivityHomeNavBinding binding;
    private int currentSelectedId = R.id.nav_home;
    private boolean vpnEnableRequested;
    private ActivityResultLauncher<Intent> prepareVpnLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.Theme_AdAway_NoActionBar_Content);
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        NotificationHelper.clearUpdateNotifications(this);
        Timber.i("HomeActivity: starting nav shell");

        this.binding = ActivityHomeNavBinding.inflate(getLayoutInflater());
        setContentView(this.binding.getRoot());

        HomeViewModel protectionModel = new ViewModelProvider(this).get(HomeViewModel.class);
        this.vpnEnableRequested = savedInstanceState != null
                && savedInstanceState.getBoolean(KEY_VPN_ENABLE_REQUESTED);
        protectionModel.setVpnConsentPending(this.vpnEnableRequested);
        this.prepareVpnLauncher = registerForActivityResult(new StartActivityForResult(), result -> {
            boolean requested = this.vpnEnableRequested;
            this.vpnEnableRequested = false;
            if (!requested) {
                protectionModel.setVpnConsentPending(false);
                return;
            }
            boolean vpnMethod = PreferenceHelper.getAdBlockMethod(this) == VPN;
            boolean authorized = vpnMethod && result.getResultCode() == RESULT_OK
                    && VpnService.prepare(this) == null;
            protectionModel.onVpnConsentResult(authorized);
            if (vpnMethod && !authorized) {
                Snackbar.make(this.binding.getRoot(), R.string.home_vpn_permission_required,
                        Snackbar.LENGTH_LONG).show();
            }
        });

        if (getIntent().getBooleanExtra(EXTRA_ONBOARDING_COMPLETE, false)) {
            ViewModelProvider provider = new ViewModelProvider(this);
            HomeViewModel homeViewModel = provider.get(HomeViewModel.class);
            provider.get(InitialSourcesViewModel.class)
                    .initialize(getApplicationContext(), homeViewModel::update,
                            homeViewModel::beginInitialSources, homeViewModel::endInitialSources);
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
        checkAppUpdateAtStartup(savedInstanceState);
        checkHostsAtStartup(savedInstanceState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_NAV_DISCOVER, false)) {
            showTab(R.id.nav_discover);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkFirstStep();
        HomeViewModel model = new ViewModelProvider(this).get(HomeViewModel.class);
        model.reconcileVpnProtection();
        model.resumeDeferredOperations();
    }

    @Override
    protected void onSaveInstanceState(@Nullable Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_TAB, this.currentSelectedId);
        outState.putBoolean(KEY_VPN_ENABLE_REQUESTED, this.vpnEnableRequested);
    }

    @Override
    public void onBackPressed() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            // popBackStackImmediate() is synchronous — the back stack entry is removed
            // before showTab() commits its replace(), preventing a transaction ordering race.
            fm.popBackStackImmediate();
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
        } else if (menuItemId == R.id.nav_sources) {
            fragment = new HostsSourcesTabFragment();
            tag = "sources";
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
            Intent onboardingIntent = new Intent(this, OnboardingActivity.class);
            onboardingIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(onboardingIntent);
            finish();
        }
    }

    public void setProtectionEnabled(boolean enabled) {
        HomeViewModel model = new ViewModelProvider(this).get(HomeViewModel.class);
        if (!model.canChangeProtection()) return;
        if (enabled && PreferenceHelper.getAdBlockMethod(this) == VPN) {
            Intent prepare = VpnService.prepare(this);
            if (prepare != null) {
                this.vpnEnableRequested = true;
                model.setVpnConsentPending(true);
                try {
                    this.prepareVpnLauncher.launch(prepare);
                } catch (ActivityNotFoundException | SecurityException exception) {
                    this.vpnEnableRequested = false;
                    model.onVpnConsentResult(false);
                    Timber.w(exception, "VPN authorization could not be opened.");
                    Snackbar.make(this.binding.getRoot(), R.string.error_enable_vpn_failed_message,
                            Snackbar.LENGTH_LONG).show();
                }
                return;
            }
        }
        model.setProtectionEnabled(enabled);
    }

    private void checkAppUpdateAtStartup(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            return;
        }
        if (!PreferenceHelper.getUpdateCheckAppStartup(this)) {
            return;
        }
        new ViewModelProvider(this).get(HomeViewModel.class).checkForAppUpdate();
    }

    private void checkHostsAtStartup(@Nullable Bundle savedInstanceState) {
        Intent intent = getIntent();
        if (savedInstanceState != null || !Intent.ACTION_MAIN.equals(intent.getAction())
                || !intent.hasCategory(Intent.CATEGORY_LAUNCHER)
                || intent.getBooleanExtra(EXTRA_ONBOARDING_COMPLETE, false)
                || intent.getBooleanExtra(EXTRA_NAV_DISCOVER, false)
                || PreferenceHelper.getAdBlockMethod(this) == UNDEFINED
                || !PreferenceHelper.getUpdateCheck(this)
                || hostsStartupCheck != null) {
            return;
        }
        AdAwayApplication app = (AdAwayApplication) getApplication();
        hostsStartupCheck = CompletableFuture.completedFuture(null);
        if (app.getSourceOperations().isBusy()) return;
        hostsStartupCheck = CompletableFuture.runAsync(() -> {
            var prefs = app.getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
            int previous = prefs.getInt(STARTUP_SOURCE_CURSOR, 0);
            int next = app.getSourceModel().checkStartupBatch(previous, () -> mayProbeHosts(app));
            if (next != previous) prefs.edit().putInt(STARTUP_SOURCE_CURSOR, next).apply();
        }, AppExecutors.getInstance().checkIO()).exceptionally(error -> {
            Timber.i(error, "Skipped startup source check.");
            return null;
        });
    }

    private static boolean mayProbeHosts(AdAwayApplication app) {
        if (app.getSourceOperations().isBusy() || !PreferenceHelper.getUpdateCheck(app)) return false;
        ConnectivityManager cm = app.getSystemService(ConnectivityManager.class);
        NetworkCapabilities network = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        return network != null && network.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && (!PreferenceHelper.getUpdateOnlyOnWifi(app) || !cm.isActiveNetworkMetered());
    }

    /**
     * Navigate to a specific bottom-nav tab from a Fragment.
     *
     * @param navItemId One of {@code R.id.nav_home}, {@code R.id.nav_discover},
     *                  {@code R.id.nav_sources}, {@code R.id.nav_more}.
     */
    public void navigateTo(int navItemId) {
        showTab(navItemId);
    }
}
