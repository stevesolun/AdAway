package org.adaway.ui.prefs;

import static android.widget.Toast.LENGTH_SHORT;
import static org.adaway.model.root.MountType.READ_ONLY;
import static org.adaway.model.root.MountType.READ_WRITE;
import static org.adaway.model.root.ShellUtils.isWritable;
import static org.adaway.model.root.ShellUtils.remountPartition;
import static org.adaway.ui.prefs.PrefsActivity.PREFERENCE_NOT_FOUND;
import static org.adaway.util.Constants.ANDROID_SYSTEM_ETC_HOSTS;
import static org.adaway.util.Constants.PREFS_NAME;
import static org.adaway.util.WebServerUtils.TEST_URL;
import static org.adaway.util.WebServerUtils.getWebServerState;
import static org.adaway.util.WebServerUtils.isWebServerAvailable;
import static org.adaway.util.WebServerUtils.isWebServerRunning;
import static org.adaway.util.WebServerUtils.startWebServer;
import static org.adaway.util.WebServerUtils.stopWebServer;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.ui.dialog.MissingAppDialog;
import org.adaway.util.AppExecutors;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

import timber.log.Timber;

/**
 * This fragment is the preferences fragment for root ad blocker.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class PrefsRootFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    /**
     * The launcher to start open hosts file activity.
     */
    private ActivityResultLauncher<Intent> openHostsFileLauncher;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Configure preferences
        getPreferenceManager().setSharedPreferencesName(PREFS_NAME);
        addPreferencesFromResource(R.xml.preferences_root);
        // Register for activities
        registerForOpenHostActivity();
        // Bind pref actions
        bindOpenHostsFile();
        bindRedirection();
        configureWebServerAvailability();
        bindWebServerPrefAction();
        bindWebServerTest();
        // Update current state
        updateWebServerState();
        // Register as listener
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, R.string.pref_root_title);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Update current state
        updateWebServerState();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Unregister as listener
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        // Restart web server on icon change
        if (context.getString(R.string.pref_webserver_icon_key).equals(key) && isWebServerRunning()) {
            stopWebServer();
            startWebServer(context);
            updateWebServerState();
        }
    }

    private void registerForOpenHostActivity() {
        this.openHostsFileLauncher = registerForActivityResult(new StartActivityForResult(), result -> {
            try {
                File hostFile = new File(ANDROID_SYSTEM_ETC_HOSTS).getCanonicalFile();
                remountPartition(hostFile, READ_ONLY);
            } catch (IOException e) {
                Timber.e(e, "Failed to get hosts canonical file.");
            }
        });
    }

    private void bindOpenHostsFile() {
        Preference openHostsFilePreference = findPreference(getString(R.string.pref_open_hosts_key));
        assert openHostsFilePreference != null : PREFERENCE_NOT_FOUND;
        openHostsFilePreference.setOnPreferenceClickListener(this::openHostsFile);
    }

    private boolean openHostsFile(Preference preference) {
        try {
            File hostFile = new File(ANDROID_SYSTEM_ETC_HOSTS).getCanonicalFile();
            boolean remount = !isWritable(hostFile) && remountPartition(hostFile, READ_WRITE);
            Intent intent = new Intent()
                    .setAction(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse("file://" + hostFile.getAbsolutePath()), "text/plain");
            if (remount) {
                this.openHostsFileLauncher.launch(intent);
            } else {
                startActivity(intent);
            }
            return true;
        } catch (IOException e) {
            Timber.e(e, "Failed to get hosts canonical file.");
        } catch (ActivityNotFoundException e) {
            MissingAppDialog.showTextEditorMissingDialog(getContext());
            return false;
        }
        return false;
    }

    private void bindRedirection() {
        Context context = requireContext();
        boolean ipv6Enabled = PreferenceHelper.getEnableIpv6(context);
        Preference ipv4RedirectionPreference = findPreference(getString(R.string.pref_redirection_ipv4_key));
        assert ipv4RedirectionPreference != null : PREFERENCE_NOT_FOUND;
        ipv4RedirectionPreference.setOnPreferenceChangeListener(
                (preference, newValue) -> validateRedirection(Inet4Address.class, (String) newValue)
        );
        Preference ipv6RedirectionPreference = findPreference(getString(R.string.pref_redirection_ipv6_key));
        assert ipv6RedirectionPreference != null : PREFERENCE_NOT_FOUND;
        ipv6RedirectionPreference.setEnabled(ipv6Enabled);
        ipv6RedirectionPreference.setOnPreferenceChangeListener(
                (preference, newValue) -> validateRedirection(Inet6Address.class, (String) newValue)
        );
    }

    private boolean validateRedirection(Class<? extends InetAddress> addressType, String redirection) {
        boolean valid = RedirectionAddressValidator.isValid(addressType, redirection);
        if (!valid) {
            Toast.makeText(requireContext(), R.string.pref_redirection_invalid, LENGTH_SHORT).show();
        }
        return valid;
    }

    private void bindWebServerPrefAction() {
        Context context = requireContext();
        // Start web server when preference is enabled
        SwitchPreferenceCompat webServerEnabledPref = findPreference(getString(R.string.pref_webserver_enabled_key));
        assert webServerEnabledPref != null : PREFERENCE_NOT_FOUND;
        webServerEnabledPref.setOnPreferenceChangeListener((preference, newValue) -> {
            if (!isWebServerAvailable(context)) {
                Toast.makeText(context, R.string.pref_webserver_unavailable, LENGTH_SHORT).show();
                return false;
            }
            if (newValue.equals(true)) {
                // Start web server
                startWebServer(context);
                updateWebServerState();
                return isWebServerRunning();
            } else {
                // Stop web server
                stopWebServer();
                updateWebServerState();
                return !isWebServerRunning();
            }
        });
    }

    private void bindWebServerTest() {
        Preference webServerTest = findPreference(getString(R.string.pref_webserver_test_key));
        assert webServerTest != null : PREFERENCE_NOT_FOUND;
        webServerTest.setOnPreferenceClickListener(preference -> {
            if (!isWebServerAvailable(requireContext())) {
                Toast.makeText(requireContext(), R.string.pref_webserver_unavailable, LENGTH_SHORT)
                        .show();
                return false;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(TEST_URL));
            startActivity(intent);
            return true;
        });
    }

    private void configureWebServerAvailability() {
        if (isWebServerAvailable(requireContext())) {
            return;
        }

        stopWebServer();

        SwitchPreferenceCompat webServerEnabledPref =
                findPreference(getString(R.string.pref_webserver_enabled_key));
        Preference webServerTest = findPreference(getString(R.string.pref_webserver_test_key));
        SwitchPreferenceCompat webServerIcon =
                findPreference(getString(R.string.pref_webserver_icon_key));

        assert webServerEnabledPref != null : PREFERENCE_NOT_FOUND;
        assert webServerTest != null : PREFERENCE_NOT_FOUND;
        assert webServerIcon != null : PREFERENCE_NOT_FOUND;

        webServerEnabledPref.setChecked(false);
        webServerEnabledPref.setEnabled(false);
        webServerEnabledPref.setSummary(R.string.pref_webserver_unavailable);
        webServerTest.setEnabled(false);
        webServerTest.setSummary(R.string.pref_webserver_state_unavailable);
        webServerIcon.setEnabled(false);
        webServerIcon.setChecked(false);
    }

    private void updateWebServerState() {
        Preference webServerTest = findPreference(getString(R.string.pref_webserver_test_key));
        assert webServerTest != null : PREFERENCE_NOT_FOUND;
        if (!isWebServerAvailable(requireContext())) {
            webServerTest.setSummary(R.string.pref_webserver_state_unavailable);
            return;
        }
        webServerTest.setSummary(R.string.pref_webserver_state_checking);
        AppExecutors executors = AppExecutors.getInstance();
        executors.networkIO().execute(() -> {
                    // Wait for server to start
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    int summaryResId = getWebServerState();
                    executors.mainThread().execute(
                            () -> webServerTest.setSummary(summaryResId)
                    );
                }
        );
    }
}
