package org.adaway.model.source;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import org.adaway.R;

/**
 * Categories for filter lists, inspired by uBlock Origin's organization.
 * This provides a logical grouping of hosts sources for better UX.
 * <p>
 * Categories are ordered by importance/risk level:
 * - Safe categories (ADS, MALWARE) are enabled by default
 * - Moderate categories (PRIVACY, YOUTUBE) require user opt-in
 * - Aggressive categories (SOCIAL, DEVICE) may break functionality
 *
 * @author AdAway Contributors
 */
public enum FilterListCategory {
    /**
     * User's personal lists (blocked, allowed, redirected).
     */
    USER(R.string.filter_category_user, R.string.filter_category_user_desc, 
         R.drawable.ic_category_user, true, false),
    
    /**
     * Primary ad-blocking lists - blocks advertising networks.
     * SAFE: Rarely breaks anything important.
     */
    ADS(R.string.filter_category_ads, R.string.filter_category_ads_desc,
        R.drawable.ic_category_ads, true, false),
    
    /**
     * YouTube-specific ad blockers.
     * MODERATE: May not block all YouTube ads (they use same domains as video).
     */
    YOUTUBE(R.string.filter_category_youtube, R.string.filter_category_youtube_desc,
            R.drawable.ic_category_youtube, false, false),
    
    /**
     * Privacy protection - blocks trackers and analytics.
     * MODERATE: May break some analytics-dependent features.
     */
    PRIVACY(R.string.filter_category_privacy, R.string.filter_category_privacy_desc,
            R.drawable.ic_category_privacy, false, false),
    
    /**
     * Malware and phishing protection.
     * SAFE: Protects against known threats. Recommended for everyone.
     */
    MALWARE(R.string.filter_category_malware, R.string.filter_category_malware_desc,
            R.drawable.ic_category_malware, true, false),
    
    /**
     * Cryptocurrency miner blockers.
     * SAFE: Blocks browser-based crypto mining scripts.
     */
    CRYPTO(R.string.filter_category_crypto, R.string.filter_category_crypto_desc,
           R.drawable.ic_category_crypto, false, false),
    
    /**
     * Social media tracking (Facebook, Instagram, etc.).
     * AGGRESSIVE: May break Facebook/Instagram/WhatsApp login and features!
     * Facebook is protected via allowlist, but enabling these lists is risky.
     */
    SOCIAL(R.string.filter_category_social, R.string.filter_category_social_desc,
           R.drawable.ic_category_social, false, true),
    
    /**
     * Device-specific telemetry blockers (Samsung, Xiaomi, etc.).
     * AGGRESSIVE: May break OEM features like Samsung Pay, Xiaomi Cloud.
     */
    DEVICE(R.string.filter_category_device, R.string.filter_category_device_desc,
           R.drawable.ic_category_device, false, true),
    
    /**
     * Service-specific blockers (Spotify ads, etc.).
     * MODERATE: Targets specific apps. May break those apps.
     */
    SERVICE(R.string.filter_category_service, R.string.filter_category_service_desc,
            R.drawable.ic_category_service, false, true),
    
    /**
     * Annoyances - cookie notices, newsletter popups, etc.
     * MODERATE: Usually safe but may hide some legitimate UI.
     */
    ANNOYANCES(R.string.filter_category_annoyances, R.string.filter_category_annoyances_desc,
               R.drawable.ic_category_annoyances, false, false),
    
    /**
     * Regional and language-specific lists.
     * SAFE: Targets regional ad networks. Enable based on your location.
     */
    REGIONAL(R.string.filter_category_regional, R.string.filter_category_regional_desc,
             R.drawable.ic_category_regional, false, false),
    
    /**
     * Custom sources added by user.
     */
    CUSTOM(R.string.filter_category_custom, R.string.filter_category_custom_desc,
           R.drawable.ic_category_custom, false, false);

    @StringRes
    private final int labelResId;
    @StringRes
    private final int descriptionResId;
    @DrawableRes
    private final int iconResId;
    private final boolean enabledByDefault;
    private final boolean mayBreakServices;

    FilterListCategory(@StringRes int labelResId, @StringRes int descriptionResId,
                       @DrawableRes int iconResId, boolean enabledByDefault, boolean mayBreakServices) {
        this.labelResId = labelResId;
        this.descriptionResId = descriptionResId;
        this.iconResId = iconResId;
        this.enabledByDefault = enabledByDefault;
        this.mayBreakServices = mayBreakServices;
    }

    @StringRes
    public int getLabelResId() {
        return labelResId;
    }

    @StringRes
    public int getDescriptionResId() {
        return descriptionResId;
    }

    @DrawableRes
    public int getIconResId() {
        return iconResId;
    }

    public boolean isEnabledByDefault() {
        return enabledByDefault;
    }

    /**
     * Returns true if enabling lists in this category may break common services
     * like Facebook, WhatsApp, Google, Samsung Pay, etc.
     * UI should show a warning when enabling these categories.
     */
    public boolean mayBreakServices() {
        return mayBreakServices;
    }
}
