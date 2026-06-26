package org.adaway.ui.prefs;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartFragmentCallback;

import android.net.Uri;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.ui.log.LogActivity;
import org.adaway.ui.about.AboutActivity;
import org.adaway.ui.onboarding.OnboardingActivity;
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
        bindLanguagePref();
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
        // Both preferences are always enabled so the user can switch modes
        rootPreference.setEnabled(true);
        vpnPreference.setEnabled(true);
        // Show which mode is currently active
        rootPreference.setSummary(adBlockMethod == ROOT ? R.string.pref_ad_block_method_active : R.string.pref_ad_block_method_switch);
        vpnPreference.setSummary(adBlockMethod == VPN ? R.string.pref_ad_block_method_active : R.string.pref_ad_block_method_switch);
        // Tap the active mode to configure it; tap the other mode to launch onboarding.
        rootPreference.setOnPreferenceClickListener(preference -> {
            if (PreferenceHelper.getAdBlockMethod(requireContext()) == ROOT) {
                openConfiguration(preference);
            } else {
                launchOnboarding();
            }
            return true;
        });
        vpnPreference.setOnPreferenceClickListener(preference -> {
            if (PreferenceHelper.getAdBlockMethod(requireContext()) == VPN) {
                openConfiguration(preference);
            } else {
                launchOnboarding();
            }
            return true;
        });
    }

    private void openConfiguration(Preference preference) {
        if (requireActivity() instanceof OnPreferenceStartFragmentCallback) {
            ((OnPreferenceStartFragmentCallback) requireActivity())
                    .onPreferenceStartFragment(this, preference);
        } else {
            Toast.makeText(requireContext(), R.string.pref_ad_block_method_already_active,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void launchOnboarding() {
        Intent intent = new Intent(requireContext(), OnboardingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
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

    private void bindLanguagePref() {
        Preference langPref = findPreference(getString(R.string.pref_force_english_key));
        assert langPref != null : PREFERENCE_NOT_FOUND;
        langPref.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean forceEnglish = (boolean) newValue;
            LocaleListCompat locales = forceEnglish
                    ? LocaleListCompat.forLanguageTags("en")
                    : LocaleListCompat.getEmptyLocaleList();
            AppCompatDelegate.setApplicationLocales(locales);
            return true;
        });
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
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/AdAway/AdAway/wiki")));
                return true;
            });
        }

        Preference donatePref = findPreference("pref_donate");
        if (donatePref != null) {
            donatePref.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://paypal.me/BruceBUJON")));
                return true;
            });
        }

        Preference aboutPref = findPreference("pref_about");
        if (aboutPref != null) {
            aboutPref.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), AboutActivity.class));
                return true;
            });
        }
    }
}
