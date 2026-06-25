package org.adaway.ui.home;

import android.content.Context;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.vpn.VpnServiceControls;

import java.util.Set;

final class LeakStatus {
    static final String PRIVATE_DNS_MODE_OFF = "off";
    static final String PRIVATE_DNS_MODE_OPPORTUNISTIC = "opportunistic";
    static final String PRIVATE_DNS_MODE_HOSTNAME = "hostname";
    static final String VPN_EXCLUDED_SYSTEM_NONE = "none";

    @NonNull
    final AdBlockMethod method;
    final boolean vpnRunning;
    @Nullable
    final String privateDnsMode;
    @Nullable
    final String privateDnsSpecifier;
    final boolean vpnBypassAllowed;
    final int excludedUserAppCount;
    @NonNull
    final String excludedSystemApps;

    private LeakStatus(
            @NonNull AdBlockMethod method,
            boolean vpnRunning,
            @Nullable String privateDnsMode,
            @Nullable String privateDnsSpecifier,
            boolean vpnBypassAllowed,
            int excludedUserAppCount,
            @NonNull String excludedSystemApps) {
        this.method = method;
        this.vpnRunning = vpnRunning;
        this.privateDnsMode = privateDnsMode;
        this.privateDnsSpecifier = privateDnsSpecifier;
        this.vpnBypassAllowed = vpnBypassAllowed;
        this.excludedUserAppCount = Math.max(0, excludedUserAppCount);
        this.excludedSystemApps = excludedSystemApps;
    }

    @NonNull
    static LeakStatus from(@NonNull Context context) {
        AdBlockMethod method = PreferenceHelper.getAdBlockMethod(context);
        boolean vpnRunning = method == AdBlockMethod.VPN && VpnServiceControls.isRunning(context);
        Set<String> excludedApps = PreferenceHelper.getVpnExcludedApps(context);
        return create(
                method,
                vpnRunning,
                getGlobalSetting(context, "private_dns_mode"),
                getGlobalSetting(context, "private_dns_specifier"),
                PreferenceHelper.getVpnAllowAppBypass(context),
                excludedApps == null ? 0 : excludedApps.size(),
                PreferenceHelper.getVpnExcludedSystemApps(context));
    }

    @NonNull
    static LeakStatus create(
            @NonNull AdBlockMethod method,
            boolean vpnRunning,
            @Nullable String privateDnsMode,
            @Nullable String privateDnsSpecifier,
            boolean vpnBypassAllowed,
            int excludedUserAppCount,
            @Nullable String excludedSystemApps) {
        return new LeakStatus(
                method,
                vpnRunning,
                normalize(privateDnsMode),
                normalize(privateDnsSpecifier),
                vpnBypassAllowed,
                excludedUserAppCount,
                normalize(excludedSystemApps, VPN_EXCLUDED_SYSTEM_NONE));
    }

    boolean hasPrivateDnsRisk() {
        return this.method == AdBlockMethod.VPN
                && (isPrivateDnsUnknown() || isPrivateDnsActive());
    }

    boolean hasDohRisk() {
        return !hasCommonDohRouteCoverage();
    }

    boolean hasVpnStoppedRisk() {
        return this.method == AdBlockMethod.VPN && !this.vpnRunning;
    }

    boolean hasVpnBypassRisk() {
        return this.method == AdBlockMethod.VPN
                && (this.vpnBypassAllowed
                || this.excludedUserAppCount > 0
                || !VPN_EXCLUDED_SYSTEM_NONE.equals(this.excludedSystemApps));
    }

    boolean hasProtectionModeRisk() {
        return this.method == AdBlockMethod.UNDEFINED;
    }

    int riskCount() {
        int count = 0;
        if (hasProtectionModeRisk()) count++;
        if (hasPrivateDnsRisk()) count++;
        if (hasDohRisk()) count++;
        if (hasVpnStoppedRisk()) count++;
        if (hasVpnBypassRisk()) count++;
        return count;
    }

    boolean hasRisks() {
        return riskCount() > 0;
    }

    boolean isPrivateDnsActive() {
        return this.privateDnsMode != null
                && !this.privateDnsMode.isEmpty()
                && !PRIVATE_DNS_MODE_OFF.equals(this.privateDnsMode);
    }

    boolean isPrivateDnsUnknown() {
        return this.privateDnsMode == null;
    }

    boolean hasCommonDohRouteCoverage() {
        return this.method == AdBlockMethod.VPN && this.vpnRunning;
    }

    @NonNull
    private static String normalize(@Nullable String value, @NonNull String fallback) {
        String normalized = normalize(value);
        return normalized == null || normalized.isEmpty() ? fallback : normalized;
    }

    @Nullable
    private static String normalize(@Nullable String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }

    @Nullable
    private static String getGlobalSetting(@NonNull Context context, @NonNull String key) {
        try {
            return Settings.Global.getString(context.getContentResolver(), key);
        } catch (SecurityException exception) {
            return null;
        }
    }
}
