package org.adaway.ui.prefs;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.ui.help.HelpActivity;
import org.adaway.ui.log.LogActivity;
import org.adaway.ui.support.SupportActivity;
import org.adaway.util.log.SentryLog;

import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.adaway.ui.prefs.PrefsActivity.PREFERENCE_NOT_FOUND;
import static org.adaway.util.Constants.PREFS_NAME;

/**
 * This fragment is the preferences main fragment.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class PrefsMainFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Configure preferences
        getPreferenceManager().setSharedPreferencesName(PREFS_NAME);
        addPreferencesFromResource(R.xml.preferences_main);
        // Bind pref actions
        bindThemePrefAction();
        bindAdBlockMethod();
        bindTelemetryPrefAction();
        bindToolsAndSupportActions();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, R.string.pref_main_title);
    }

    @Override
    public void onResume() {
        super.onResume();
        PrefsActivity.setAppBarTitle(this, R.string.pref_main_title);
    }

    private void bindThemePrefAction() {
        Preference darkThemePref = findPreference(getString(R.string.pref_dark_theme_mode_key));
        assert darkThemePref != null : PREFERENCE_NOT_FOUND;
        darkThemePref.setOnPreferenceChangeListener((preference, newValue) -> {
            requireActivity().recreate();
            // Allow preference change
            return true;
        });
    }

    private void bindAdBlockMethod() {
        Preference rootPreference = findPreference(getString(R.string.pref_root_ad_block_method_key));
        assert rootPreference != null : PREFERENCE_NOT_FOUND;
        Preference vpnPreference = findPreference(getString(R.string.pref_vpn_ad_block_method_key));
        assert vpnPreference != null : PREFERENCE_NOT_FOUND;
        AdBlockMethod adBlockMethod = PreferenceHelper.getAdBlockMethod(requireContext());
        rootPreference.setEnabled(adBlockMethod == ROOT);
        vpnPreference.setEnabled(adBlockMethod == VPN);
    }

    private void bindTelemetryPrefAction() {
        Preference enableTelemetryPref = findPreference(getString(R.string.pref_enable_telemetry_key));
        assert enableTelemetryPref != null : PREFERENCE_NOT_FOUND;
        enableTelemetryPref.setOnPreferenceChangeListener((preference, newValue) -> {
            SentryLog.setEnabled(requireActivity().getApplication(), (boolean) newValue);
            return true;
        });
        if (SentryLog.isStub()) {
            enableTelemetryPref.setEnabled(false);
            enableTelemetryPref.setSummary(R.string.pref_enable_telemetry_disabled_summary);
        }
    }

    private void bindToolsAndSupportActions() {
        Preference dnsLogPref = findPreference("pref_dns_log");
        if (dnsLogPref != null) {
            dnsLogPref.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), LogActivity.class));
                return true;
            });
        }

        Preference helpPref = findPreference("pref_help");
        if (helpPref != null) {
            helpPref.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), HelpActivity.class));
                return true;
            });
        }

        Preference donatePref = findPreference("pref_donate");
        if (donatePref != null) {
            donatePref.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), SupportActivity.class));
                return true;
            });
        }
    }
}
