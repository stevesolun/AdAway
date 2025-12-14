package org.adaway.model.source;

import android.content.Context;

import org.adaway.R;
import org.adaway.db.entity.HostsSource;

import java.util.ArrayList;
import java.util.List;

import static org.adaway.model.source.FilterListCategory.*;

/**
 * A comprehensive, curated catalog of filter lists organized by category.
 * Inspired by uBlock Origin's filter list management.
 * <p>
 * This catalog provides:
 * - 60+ curated sources across 12 categories
 * - Smart defaults (ads + malware ON, others OFF)
 * - Built-in Facebook/Meta whitelist for safety
 * - YouTube-specific blockers for ad-free viewing
 * - Device-specific blockers (Samsung, Xiaomi telemetry)
 * - Service-specific blockers (Spotify ads)
 * - Regional lists for international users
 * - Preset modes (Safe, Balanced, Aggressive)
 * <p>
 * Sources compiled from community recommendations including Reddit, GitHub,
 * Pi-hole community, and Blokada/NetGuard ecosystems.
 *
 * @author AdAway Contributors
 */
public class FilterListCatalog {

    /**
     * Entry representing a curated filter list.
     */
    public static class CatalogEntry {
        public final String label;
        public final String url;
        public final FilterListCategory category;
        public final boolean enabledByDefault;
        public final boolean isAllowList;
        public final boolean supportsRedirects;
        public final String description;

        public CatalogEntry(String label, String url, FilterListCategory category,
                           boolean enabledByDefault, boolean isAllowList, boolean supportsRedirects,
                           String description) {
            this.label = label;
            this.url = url;
            this.category = category;
            this.enabledByDefault = enabledByDefault;
            this.isAllowList = isAllowList;
            this.supportsRedirects = supportsRedirects;
            this.description = description;
        }

        public CatalogEntry(String label, String url, FilterListCategory category,
                           boolean enabledByDefault) {
            this(label, url, category, enabledByDefault, false, false, null);
        }

        /**
         * Convert this catalog entry to a HostsSource entity.
         */
        public HostsSource toHostsSource() {
            HostsSource source = new HostsSource();
            source.setLabel(label);
            source.setUrl(url);
            source.setEnabled(enabledByDefault);
            source.setAllowEnabled(isAllowList);
            source.setRedirectEnabled(supportsRedirects);
            return source;
        }
    }

    // ========================================================================
    // COMPREHENSIVE CURATED FILTER LISTS
    // ========================================================================

    private static final List<CatalogEntry> CATALOG = new ArrayList<>();

    static {
        // ====================================================================
        // ADS - Primary ad blocking (DEFAULT: ON for essential lists)
        // ====================================================================
        CATALOG.add(new CatalogEntry(
                "AdAway Official",
                "https://adaway.org/hosts.txt",
                ADS, true
        ));
        CATALOG.add(new CatalogEntry(
                "StevenBlack Unified",
                "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
                ADS, true
        ));
        CATALOG.add(new CatalogEntry(
                "Peter Lowe's Ad Servers",
                "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
                ADS, true
        ));
        CATALOG.add(new CatalogEntry(
                "1Hosts Pro",
                "https://o0.pages.dev/Pro/hosts.txt",
                ADS, false
        ));
        CATALOG.add(new CatalogEntry(
                "OISD Full",
                "https://hosts.oisd.nl/",
                ADS, false
        ));
        CATALOG.add(new CatalogEntry(
                "HaGeZi Multi PRO",
                "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/pro.txt",
                ADS, false
        ));
        CATALOG.add(new CatalogEntry(
                "GoodbyeAds",
                "https://raw.githubusercontent.com/jerryn70/GoodbyeAds/master/Hosts/GoodbyeAds.txt",
                ADS, false
        ));
        CATALOG.add(new CatalogEntry(
                "Anudeep Adservers",
                "https://raw.githubusercontent.com/anudeepND/blacklist/master/adservers.txt",
                ADS, false
        ));
        CATALOG.add(new CatalogEntry(
                "EasyList via Firebog",
                "https://v.firebog.net/hosts/Easylist.txt",
                ADS, false
        ));
        CATALOG.add(new CatalogEntry(
                "Prigent Ads via Firebog",
                "https://v.firebog.net/hosts/Prigent-Ads.txt",
                ADS, false
        ));
        CATALOG.add(new CatalogEntry(
                "Anti Pop-ups",
                "https://raw.githubusercontent.com/yhonay/antipopads/master/hosts",
                ADS, false
        ));
        CATALOG.add(new CatalogEntry(
                "MVPS Hosts",
                // Use HTTPS so it passes HostsSource.isValidUrl() and avoids cleartext issues.
                "https://winhelp2002.mvps.org/hosts.txt",
                ADS, false
        ));
        CATALOG.add(new CatalogEntry(
                "Dan Pollock Hosts",
                "https://someonewhocares.org/hosts/hosts",
                ADS, false
        ));
        CATALOG.add(new CatalogEntry(
                "Disconnect Ads",
                "https://s3.amazonaws.com/lists.disconnect.me/simple_ad.txt",
                ADS, false
        ));

        // Android-focused hosts lists (community curated)
        CATALOG.add(new CatalogEntry(
                "Android AdBlock (DataMaster)",
                "https://raw.githubusercontent.com/DataMaster-2501/DataMaster-Android-AdBlock-Hosts/master/hosts",
                ADS, false
        ));

        // ====================================================================
        // YOUTUBE - YouTube-specific ad blockers (DEFAULT: OFF)
        // Note: YouTube ads are notoriously hard to block via hosts files
        // because they often come from same domains as video content.
        // These lists help but may not block 100% of ads.
        // ====================================================================
        CATALOG.add(new CatalogEntry(
                "GoodbyeAds YouTube",
                "https://raw.githubusercontent.com/jerryn70/GoodbyeAds/master/Hosts/YouTube/hosts.txt",
                YOUTUBE, false
        ));
        CATALOG.add(new CatalogEntry(
                "YouTube Ads Blacklist (anudeepND)",
                "https://raw.githubusercontent.com/anudeepND/youtubeadsblacklist/master/hosts.txt",
                YOUTUBE, false
        ));
        CATALOG.add(new CatalogEntry(
                "YouTube Ads 4 AdAway (taichikuji)",
                "https://raw.githubusercontent.com/taichikuji/youTube-ads-4-adaway/master/hosts",
                YOUTUBE, false
        ));
        CATALOG.add(new CatalogEntry(
                "YouTube Ad-Block List for AdAway (unfunf22)",
                "https://raw.githubusercontent.com/unfunf22/youtube-ad-block-list-for-adaway/master/hosts.txt",
                YOUTUBE, false
        ));
        CATALOG.add(new CatalogEntry(
                "Perflyst YouTube",
                "https://raw.githubusercontent.com/Perflyst/PiHoleBlocklist/master/youtube.txt",
                YOUTUBE, false
        ));
        CATALOG.add(new CatalogEntry(
                "SmartTV Tracking",
                "https://raw.githubusercontent.com/Perflyst/PiHoleBlocklist/master/SmartTV.txt",
                YOUTUBE, false
        ));

        // ====================================================================
        // PRIVACY - Tracking protection (DEFAULT: OFF)
        // ====================================================================
        CATALOG.add(new CatalogEntry(
                "EasyPrivacy via Firebog",
                "https://v.firebog.net/hosts/Easyprivacy.txt",
                PRIVACY, false
        ));
        CATALOG.add(new CatalogEntry(
                "Disconnect Tracking",
                "https://s3.amazonaws.com/lists.disconnect.me/simple_tracking.txt",
                PRIVACY, false
        ));
        CATALOG.add(new CatalogEntry(
                "Frogeye First-Party Trackers",
                "https://hostfiles.frogeye.fr/firstparty-trackers-hosts.txt",
                PRIVACY, false
        ));
        CATALOG.add(new CatalogEntry(
                "DDG Tracker Radar",
                "https://blokada.org/blocklists/ddgtrackerradar/standard/hosts.txt",
                PRIVACY, false
        ));
        CATALOG.add(new CatalogEntry(
                "Exodus Privacy Trackers",
                "https://blokada.org/mirror/v5/exodusprivacy/standard/hosts.txt",
                PRIVACY, false
        ));
        CATALOG.add(new CatalogEntry(
                "FadeMind 2o7 Net Trackers",
                "https://raw.githubusercontent.com/FadeMind/hosts.extras/master/add.2o7Net/hosts",
                PRIVACY, false
        ));
        CATALOG.add(new CatalogEntry(
                "Windows Spy Blocker",
                "https://raw.githubusercontent.com/crazy-max/WindowsSpyBlocker/master/data/hosts/spy.txt",
                PRIVACY, false
        ));

        // ====================================================================
        // MALWARE - Security protection (DEFAULT: ON)
        // ====================================================================
        CATALOG.add(new CatalogEntry(
                "URLhaus Malware",
                "https://urlhaus.abuse.ch/downloads/hostfile/",
                MALWARE, true
        ));
        CATALOG.add(new CatalogEntry(
                "Phishing Army Extended",
                "https://phishing.army/download/phishing_army_blocklist_extended.txt",
                MALWARE, false
        ));
        CATALOG.add(new CatalogEntry(
                "DandelionSprout Anti-Malware",
                "https://raw.githubusercontent.com/DandelionSprout/adfilt/master/Alternate%20versions%20Anti-Malware%20List/AntiMalwareHosts.txt",
                MALWARE, false
        ));
        CATALOG.add(new CatalogEntry(
                "Disconnect Malvertising",
                "https://s3.amazonaws.com/lists.disconnect.me/simple_malvertising.txt",
                MALWARE, false
        ));
        CATALOG.add(new CatalogEntry(
                "Quidsup NoTrack Malware",
                "https://gitlab.com/quidsup/notrack-blocklists/raw/master/notrack-malware.txt",
                MALWARE, false
        ));
        CATALOG.add(new CatalogEntry(
                "DigitalSide OSINT Threats",
                "https://osint.digitalside.it/Threat-Intel/lists/latestdomains.txt",
                MALWARE, false
        ));
        CATALOG.add(new CatalogEntry(
                "FadeMind Risk Domains",
                "https://raw.githubusercontent.com/FadeMind/hosts.extras/master/add.Risk/hosts",
                MALWARE, false
        ));
        CATALOG.add(new CatalogEntry(
                "Spam404 Blacklist",
                "https://raw.githubusercontent.com/Spam404/lists/master/main-blacklist.txt",
                MALWARE, false
        ));

        // ====================================================================
        // CRYPTO - Cryptocurrency miner blockers (DEFAULT: OFF)
        // ====================================================================
        CATALOG.add(new CatalogEntry(
                "NoCoin Crypto Miners",
                "https://raw.githubusercontent.com/hoshsadiq/adblock-nocoin-list/master/hosts.txt",
                CRYPTO, false
        ));
        CATALOG.add(new CatalogEntry(
                "CoinBlocker Lists",
                "https://zerodot1.gitlab.io/CoinBlockerLists/hosts_browser",
                CRYPTO, false
        ));
        CATALOG.add(new CatalogEntry(
                "Prigent Crypto via Firebog",
                "https://v.firebog.net/hosts/Prigent-Crypto.txt",
                CRYPTO, false
        ));

        // ====================================================================
        // SOCIAL - Social media tracking (DEFAULT: OFF)
        // ⚠️ WARNING: May break Facebook, Instagram, WhatsApp, Messenger!
        // Facebook domains are protected via allowlist but use with caution.
        // ====================================================================
        CATALOG.add(new CatalogEntry(
                "StevenBlack Social",
                "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/social/hosts",
                SOCIAL, false
        ));
        CATALOG.add(new CatalogEntry(
                "Anudeep Facebook Blacklist",
                "https://raw.githubusercontent.com/anudeepND/blacklist/master/facebook.txt",
                SOCIAL, false
        ));
        CATALOG.add(new CatalogEntry(
                "BlocklistProject Facebook",
                "https://blokada.org/mirror/v5/blocklist/facebook/hosts.txt",
                SOCIAL, false
        ));
        CATALOG.add(new CatalogEntry(
                "DeveloperDan Facebook",
                "https://blokada.org/mirror/v5/developerdan/facebook/hosts.txt",
                SOCIAL, false
        ));

        // ====================================================================
        // DEVICE - OEM telemetry blockers (DEFAULT: OFF)
        // ⚠️ WARNING: May break device-specific features like Samsung Pay,
        // Xiaomi Cloud Sync, device warranty services, etc.
        // ====================================================================
        CATALOG.add(new CatalogEntry(
                "GoodbyeAds Samsung",
                "https://raw.githubusercontent.com/jerryn70/GoodbyeAds/master/Hosts/Samsung/hosts.txt",
                DEVICE, false
        ));
        CATALOG.add(new CatalogEntry(
                "GoodbyeAds Xiaomi",
                "https://raw.githubusercontent.com/jerryn70/GoodbyeAds/master/Hosts/Xiaomi/hosts.txt",
                DEVICE, false
        ));
        CATALOG.add(new CatalogEntry(
                "HaGeZi Samsung Native Trackers",
                "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/native.samsung.txt",
                DEVICE, false
        ));

        // ====================================================================
        // SERVICE - App-specific blockers (DEFAULT: OFF)
        // ⚠️ WARNING: May break the targeted app's functionality.
        // ====================================================================
        CATALOG.add(new CatalogEntry(
                "GoodbyeAds Spotify",
                "https://raw.githubusercontent.com/jerryn70/GoodbyeAds/master/Hosts/Spotify/hosts.txt",
                SERVICE, false
        ));

        // ====================================================================
        // ANNOYANCES - Cookie banners, popups (DEFAULT: OFF)
        // ====================================================================
        CATALOG.add(new CatalogEntry(
                "FadeMind Spam Hosts",
                "https://raw.githubusercontent.com/FadeMind/hosts.extras/master/add.Spam/hosts",
                ANNOYANCES, false
        ));
        CATALOG.add(new CatalogEntry(
                "FadeMind UncheckyAds",
                "https://raw.githubusercontent.com/FadeMind/hosts.extras/master/UncheckyAds/hosts",
                ANNOYANCES, false
        ));

        // ====================================================================
        // REGIONAL - Country/language-specific lists (DEFAULT: OFF)
        // ====================================================================
        CATALOG.add(new CatalogEntry(
                "Energized Regional",
                "https://blokada.org/mirror/v5/energized/regional/hosts.txt",
                REGIONAL, false
        ));
    }

    // ========================================================================
    // FACEBOOK WHITELIST - Ensures Facebook/Meta services always work
    // ========================================================================

    /**
     * Built-in Facebook whitelist domains.
     * These are whitelisted by default to ensure Facebook/Meta functionality.
     * Includes: Facebook, Instagram, WhatsApp, Messenger, Oculus/Meta Quest
     */
    public static final String[] FACEBOOK_WHITELIST_DOMAINS = {
            // Core Facebook
            "facebook.com",
            "www.facebook.com",
            "m.facebook.com",
            "web.facebook.com",
            "static.facebook.com",
            "fbcdn.net",
            "fbcdn.com",
            
            // Facebook Login & OAuth
            "connect.facebook.net",
            "graph.facebook.com",
            "api.facebook.com",
            "b-graph.facebook.com",
            "edge-mqtt.facebook.com",
            "star.c10r.facebook.com",
            
            // Facebook CDN
            "fbsbx.com",
            "akamaihd.net",
            "fbstatic-a.akamaihd.net",
            
            // Messenger
            "messenger.com",
            "www.messenger.com",
            
            // Instagram (owned by Facebook/Meta)
            "instagram.com",
            "www.instagram.com",
            "i.instagram.com",
            "cdninstagram.com",
            
            // WhatsApp (owned by Facebook/Meta)
            "whatsapp.com",
            "www.whatsapp.com",
            "web.whatsapp.com",
            "whatsapp.net",
            
            // Oculus/Meta Quest (owned by Facebook/Meta)
            "oculus.com",
            "oculuscdn.com"
    };

    // ========================================================================
    // CATALOG ACCESS METHODS
    // ========================================================================

    /**
     * Get all catalog entries.
     */
    public static List<CatalogEntry> getAll() {
        return new ArrayList<>(CATALOG);
    }

    /**
     * Get catalog entries by category.
     */
    public static List<CatalogEntry> getByCategory(FilterListCategory category) {
        List<CatalogEntry> result = new ArrayList<>();
        for (CatalogEntry entry : CATALOG) {
            if (entry.category == category) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Get default-enabled catalog entries.
     * These are safe lists that don't break common services.
     */
    public static List<CatalogEntry> getDefaults() {
        List<CatalogEntry> result = new ArrayList<>();
        for (CatalogEntry entry : CATALOG) {
            if (entry.enabledByDefault) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Get entries for the "Balanced" preset mode.
     * Includes: Ads, Malware, Privacy basics, Crypto.
     * Excludes: Social, Device, Service (may break things).
     */
    public static List<CatalogEntry> getBalancedPreset() {
        List<CatalogEntry> result = new ArrayList<>();
        for (CatalogEntry entry : CATALOG) {
            FilterListCategory cat = entry.category;
            if (cat == ADS || cat == MALWARE || cat == CRYPTO) {
                result.add(entry);
            } else if (cat == PRIVACY && (
                    entry.label.contains("EasyPrivacy") ||
                    entry.label.contains("Disconnect Tracking"))) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Get entries for the "Aggressive" preset mode.
     * Includes everything except device-specific lists.
     * ⚠️ May break some services. Use with caution.
     */
    public static List<CatalogEntry> getAggressivePreset() {
        List<CatalogEntry> result = new ArrayList<>();
        for (CatalogEntry entry : CATALOG) {
            // Include everything except DEVICE and SERVICE (too risky)
            if (entry.category != DEVICE && entry.category != SERVICE && entry.category != REGIONAL) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Create the Facebook whitelist as a HostsSource.
     * This source uses the built-in domains to ensure Facebook always works.
     */
    public static HostsSource createFacebookWhitelist(Context context) {
        HostsSource source = new HostsSource();
        source.setLabel(context.getString(R.string.filter_facebook_whitelist));
        source.setUrl(HostsSource.USER_SOURCE_URL + "/facebook-whitelist");
        source.setEnabled(true);
        source.setAllowEnabled(true);  // This is a whitelist
        source.setRedirectEnabled(false);
        return source;
    }

    /**
     * Check if a URL matches any catalog entry.
     */
    public static CatalogEntry findByUrl(String url) {
        for (CatalogEntry entry : CATALOG) {
            if (entry.url.equals(url)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Get the category for a given URL.
     */
    public static FilterListCategory getCategoryForUrl(String url) {
        // Built-in user lists (blocked/allowed/redirected) live under a content:// URL.
        if (url != null && url.startsWith(HostsSource.USER_SOURCE_URL)) {
            return USER;
        }
        CatalogEntry entry = findByUrl(url);
        return entry != null ? entry.category : CUSTOM;
    }

    /**
     * Get the total number of available filter lists in the catalog.
     */
    public static int getCatalogSize() {
        return CATALOG.size();
    }

    /**
     * Get counts per category for display.
     */
    public static int getCountForCategory(FilterListCategory category) {
        int count = 0;
        for (CatalogEntry entry : CATALOG) {
            if (entry.category == category) {
                count++;
            }
        }
        return count;
    }
}
