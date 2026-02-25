package org.adaway.ui.onboarding;

import static android.app.Activity.RESULT_OK;
import static java.lang.Boolean.TRUE;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.topjohnwu.superuser.Shell;

import org.adaway.R;
import org.adaway.databinding.ActivityOnboardingBinding;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.ThemeHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.ui.home.HomeActivity;
import org.adaway.util.log.SentryLog;

/**
 * Single-screen onboarding: choose VPN or Root protection method.
 *
 * Replaces the 3-screen WelcomeActivity wizard with a single card-based chooser.
 * Auto-selects VPN when device does not have root.
 *
 * The launcher intent is set to this Activity in AndroidManifest.xml. HomeActivity
 * checks if the method is UNDEFINED and redirects here on its own if somehow reached
 * before onboarding completes.
 */
public class OnboardingActivity extends AppCompatActivity {

    private ActivityOnboardingBinding binding;
    private ActivityResultLauncher<Intent> prepareVpnLauncher;

    // Track which method the user has chosen
    private AdBlockMethod selectedMethod = AdBlockMethod.UNDEFINED;

    // Card color resources
    private int cardNormalColor;
    private int cardSelectedColor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);

        this.binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(this.binding.getRoot());

        this.cardNormalColor = getResources().getColor(R.color.cardBackground, null);
        this.cardSelectedColor = getResources().getColor(R.color.cardEnabledBackground, null);

        this.prepareVpnLauncher = registerForActivityResult(new StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                onVpnSelected();
            } else {
                onVpnDenied();
            }
        });

        this.binding.onboardingVpnCard.setOnClickListener(v -> trySelectVpn());
        this.binding.onboardingRootCard.setOnClickListener(v -> trySelectRoot());
        this.binding.onboardingStartButton.setOnClickListener(v -> startProtecting());

        // Auto-detect: if root is not available, pre-select VPN
        autoDetectAndPreselectMethod();
    }

    // -----------------------------------------------------------------------------------------
    // Method selection
    // -----------------------------------------------------------------------------------------

    private void autoDetectAndPreselectMethod() {
        // Check root availability without blocking the main thread
        new Thread(() -> {
            boolean hasRoot = TRUE.equals(Shell.isAppGrantedRoot());
            runOnUiThread(() -> {
                if (!hasRoot) {
                    // No root — pre-select VPN
                    trySelectVpn();
                }
                // If root is available, let the user choose explicitly
            });
        }).start();
    }

    private void trySelectVpn() {
        Intent prepareIntent = VpnService.prepare(this);
        if (prepareIntent == null) {
            // VPN already authorized
            onVpnSelected();
        } else {
            this.prepareVpnLauncher.launch(prepareIntent);
        }
    }

    private void trySelectRoot() {
        new Thread(() -> {
            Shell.getShell();
            boolean hasRoot = TRUE.equals(Shell.isAppGrantedRoot());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (hasRoot) {
                    onRootSelected();
                } else {
                    onRootDenied();
                }
            });
        }).start();
    }

    private void onVpnSelected() {
        SentryLog.recordBreadcrumb("Onboarding: VPN selected");
        this.selectedMethod = AdBlockMethod.VPN;
        this.binding.onboardingVpnCard.setCardBackgroundColor(this.cardSelectedColor);
        this.binding.onboardingRootCard.setCardBackgroundColor(this.cardNormalColor);
        this.binding.onboardingStartButton.setEnabled(true);
    }

    private void onVpnDenied() {
        // VPN permission denied — check if always-on is blocking
        this.selectedMethod = AdBlockMethod.UNDEFINED;
        this.binding.onboardingVpnCard.setCardBackgroundColor(this.cardNormalColor);
        this.binding.onboardingStartButton.setEnabled(false);
        checkAlwaysOnVpn();
    }

    private void onRootSelected() {
        SentryLog.recordBreadcrumb("Onboarding: Root selected");
        this.selectedMethod = AdBlockMethod.ROOT;
        this.binding.onboardingRootCard.setCardBackgroundColor(this.cardSelectedColor);
        this.binding.onboardingVpnCard.setCardBackgroundColor(this.cardNormalColor);
        this.binding.onboardingStartButton.setEnabled(true);
    }

    private void onRootDenied() {
        this.selectedMethod = AdBlockMethod.UNDEFINED;
        this.binding.onboardingRootCard.setCardBackgroundColor(this.cardNormalColor);
        this.binding.onboardingStartButton.setEnabled(false);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.welcome_root_missing_title)
                .setMessage(R.string.welcome_root_missile_description)
                .setPositiveButton(R.string.button_close, null)
                .show();
    }

    private void checkAlwaysOnVpn() {
        int message = R.string.welcome_vpn_alwayson_description;
        try {
            String alwaysOn = Settings.Secure.getString(
                    getContentResolver(), "always_on_vpn_app");
            if (alwaysOn == null) return;
        } catch (SecurityException e) {
            message = R.string.welcome_vpn_alwayson_blocked_description;
        }
        final int finalMessage = message;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.welcome_vpn_alwayson_title)
                .setMessage(finalMessage)
                .setNegativeButton(R.string.button_close, null)
                .setPositiveButton(R.string.welcome_vpn_alwayson_settings_action,
                        (dialog, which) -> startActivity(
                                new Intent(Settings.ACTION_VPN_SETTINGS)))
                .show();
    }

    // -----------------------------------------------------------------------------------------
    // Finish onboarding
    // -----------------------------------------------------------------------------------------

    private void startProtecting() {
        if (this.selectedMethod == AdBlockMethod.UNDEFINED) return;
        PreferenceHelper.setAbBlockMethod(this, this.selectedMethod);
        Intent intent = new Intent(this, HomeActivity.class);
        intent.putExtra(HomeActivity.EXTRA_ONBOARDING_COMPLETE, true);
        startActivity(intent);
        finish();
    }
}
