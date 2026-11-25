package org.adaway.ui.prefs;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.ui.prefs.exclusion.PrefsVpnExcludedAppsActivity;
import org.adaway.vpn.VpnServiceControls;

import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.adaway.ui.prefs.PrefsActivity.PREFERENCE_NOT_FOUND;
import static org.adaway.util.Constants.PREFS_NAME;

/**
 * This fragment is the preferences fragment for VPN ad blocker.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class PrefsVpnFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private ActivityResultLauncher<Intent> startActivityLauncher;
    private SharedPreferences vpnPreferences;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Configure preferences
        getPreferenceManager().setSharedPreferencesName(PREFS_NAME);
        addPreferencesFromResource(R.xml.preferences_vpn);
        // Register for activity
        registerForStartActivity();
        // Bind pref actions
        bindExcludedUserApps();
        this.vpnPreferences = getPreferenceManager().getSharedPreferences();
        if (this.vpnPreferences != null) {
            this.vpnPreferences.registerOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onDestroy() {
        if (this.vpnPreferences != null) {
            this.vpnPreferences.unregisterOnSharedPreferenceChangeListener(this);
        }
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        Context context = getContext();
        if (context != null && (context.getString(R.string.pref_vpn_watchdog_enabled_key).equals(key)
                || context.getString(R.string.pref_vpn_allow_app_bypass_key).equals(key)
                || context.getString(R.string.pref_vpn_excluded_system_apps_key).equals(key))) {
            // SharedPreferences has already published the new value before this callback.
            restartRunningVpn(context);
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, R.string.pref_vpn_title);
    }

    private void registerForStartActivity() {
        this.startActivityLauncher = registerForActivityResult(
                new StartActivityForResult(),
                result -> {
                    Context context = getContext();
                    if (context != null) {
                        restartRunningVpn(context);
                    }
                }
        );
    }

    private void bindExcludedUserApps() {
        Context context = requireContext();
        Preference excludeUserAppsPreferences = findPreference(getString(R.string.pref_vpn_excluded_user_apps_key));
        assert excludeUserAppsPreferences != null : PREFERENCE_NOT_FOUND;
        excludeUserAppsPreferences.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(context, PrefsVpnExcludedAppsActivity.class);
            this.startActivityLauncher.launch(intent);
            return true;
        });
    }

    static void restartRunningVpn(Context context) {
        if (PreferenceHelper.getAdBlockMethod(context) == VPN
                && VpnServiceControls.isRunning(context)) {
            VpnServiceControls.restart(context);
        }
    }
}
