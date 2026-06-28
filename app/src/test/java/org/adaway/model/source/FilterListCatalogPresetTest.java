package org.adaway.model.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class FilterListCatalogPresetTest {
    private static final String HEBREW_HOSTS_URL =
            "https://raw.githubusercontent.com/easylist/EasyListHebrew/master/hosts.txt";
    private static final String HEBREW_BROWSER_URL =
            "https://raw.githubusercontent.com/easylist/EasyListHebrew/master/EasyListHebrew.txt";
    private static final String ADGUARD_ISRAELI_URL =
            "https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/"
                    + "filter_18_Israeli/filter.txt";

    @Test
    public void defaults_excludeBroadOisdFeed() {
        List<FilterListCatalog.CatalogEntry> defaults = FilterListCatalog.getDefaults();

        assertFalse("OISD Full is too broad for first-run defaults",
                containsLabel(defaults, "OISD Full"));
        assertTrue("AdAway Official remains a safe default",
                containsLabel(defaults, "AdAway Official"));
        assertTrue("StevenBlack Unified remains a safe default",
                containsLabel(defaults, "StevenBlack Unified"));
        assertTrue("URLhaus Malware remains a security default",
                containsLabel(defaults, "URLhaus Malware"));
    }

    @Test
    public void balancedPreset_excludesHeavyOrRiskyFeeds() {
        List<FilterListCatalog.CatalogEntry> balanced = FilterListCatalog.getBalancedPreset();

        assertFalse("Balanced must not silently enable OISD Full",
                containsLabel(balanced, "OISD Full"));
        assertFalse("Balanced must not enable the aggressive HaGeZi tier",
                containsLabel(balanced, "HaGeZi Ultimate (Aggressive)"));
        assertFalse("Balanced must not enable broad 1Hosts Pro",
                containsLabel(balanced, "1Hosts Pro"));
        assertFalse("Balanced must keep social breakage opt-in",
                containsLabel(balanced, "StevenBlack Social"));
        assertFalse("Balanced must keep YouTube same-domain experiments opt-in",
                containsLabel(balanced, "GoodbyeAds YouTube"));
    }

    @Test
    public void balancedPreset_keepsModerateCurrentFeeds() {
        List<FilterListCatalog.CatalogEntry> balanced = FilterListCatalog.getBalancedPreset();

        assertTrue("Balanced starts from first-run defaults",
                containsLabel(balanced, "AdAway Official"));
        assertTrue("Balanced adds a moderate ad list",
                containsLabel(balanced, "HaGeZi Light"));
        assertTrue("Balanced adds privacy basics",
                containsLabel(balanced, "EasyPrivacy via Firebog"));
        assertTrue("Balanced adds tracking basics",
                containsLabel(balanced, "Disconnect Tracking"));
        assertTrue("Balanced adds one crypto-miner list",
                containsLabel(balanced, "NoCoin Crypto Miners"));
    }

    @Test
    public void aggressivePreset_isCuratedNotEverything() {
        List<FilterListCatalog.CatalogEntry> aggressive = FilterListCatalog.getAggressivePreset();

        assertTrue("Aggressive keeps balanced coverage",
                containsLabel(aggressive, "HaGeZi Light"));
        assertTrue("Aggressive adds stronger ad coverage",
                containsLabel(aggressive, "HaGeZi Multi PRO (Recommended)"));
        assertTrue("Aggressive adds threat-intelligence coverage",
                containsLabel(aggressive, "HaGeZi TIF (Threat Intelligence)"));
        assertTrue("Aggressive adds phishing coverage",
                containsLabel(aggressive, "Phishing Army Extended"));
        assertTrue("Aggressive adds malware coverage",
                containsLabel(aggressive, "DandelionSprout Anti-Malware"));
        assertTrue("Aggressive adds stronger crypto coverage",
                containsLabel(aggressive, "CoinBlocker Lists"));
        assertTrue("Aggressive adds Firebog crypto coverage",
                containsLabel(aggressive, "Prigent Crypto via Firebog"));

        assertFalse("Aggressive must still keep device-specific lists opt-in",
                containsLabel(aggressive, "GoodbyeAds Samsung"));
        assertFalse("Aggressive must still keep app-specific service lists opt-in",
                containsLabel(aggressive, "GoodbyeAds Spotify"));
        assertFalse("Aggressive must still keep regional lists opt-in",
                containsLabel(aggressive, "EasyList Hebrew (hosts)"));
        assertFalse("Aggressive must still keep social breakage opt-in",
                containsLabel(aggressive, "StevenBlack Social"));
        assertFalse("Aggressive must still keep YouTube experiments opt-in",
                containsLabel(aggressive, "GoodbyeAds YouTube"));
        assertFalse("Aggressive must not mean every heavy ad list",
                containsLabel(aggressive, "HaGeZi Ultimate (Aggressive)"));
    }

    @Test
    public void regionalCatalog_usesHostsCompatibleHebrewSourceOnly() {
        List<FilterListCatalog.CatalogEntry> all = FilterListCatalog.getAll();

        assertTrue("Hebrew coverage should use the hosts-format source",
                containsLabel(all, "EasyList Hebrew (hosts)"));
        assertTrue("Hebrew hosts source should point at hosts.txt",
                containsUrl(all, HEBREW_HOSTS_URL));
        assertFalse("Browser-syntax EasyList Hebrew should not be in the static catalog",
                containsLabel(all, "EasyList Hebrew"));
        assertFalse("Stale AdGuard Israeli path should not be in the static catalog",
                containsLabel(all, "AdGuard Israeli Filter"));
        assertFalse("Removed browser-syntax URL should stay absent",
                containsUrl(all, HEBREW_BROWSER_URL));
        assertFalse("Removed AdGuard Israeli URL should stay absent",
                containsUrl(all, ADGUARD_ISRAELI_URL));
    }

    @Test
    public void catalogAndPresetUrlsAreParseableHttpsSources() {
        assertValidSourceUrls("catalog", FilterListCatalog.getAll());
        assertValidSourceUrls("defaults", FilterListCatalog.getDefaults());
        assertValidSourceUrls("balanced preset", FilterListCatalog.getBalancedPreset());
        assertValidSourceUrls("aggressive preset", FilterListCatalog.getAggressivePreset());
    }

    private static void assertValidSourceUrls(String source,
            List<FilterListCatalog.CatalogEntry> entries) {
        for (FilterListCatalog.CatalogEntry entry : entries) {
            URI uri = parseUri(source, entry);

            assertEquals(source + " entry should use HTTPS: " + entry.label,
                    "https", uri.getScheme());
            assertFalse(source + " entry should include a host: " + entry.label,
                    uri.getHost() == null || uri.getHost().isEmpty());
            assertFalse(source + " entry should not contain spaces: " + entry.label,
                    entry.url.contains(" "));
        }
    }

    private static URI parseUri(String source, FilterListCatalog.CatalogEntry entry) {
        try {
            return new URI(entry.url);
        } catch (URISyntaxException exception) {
            throw new AssertionError(source + " entry has invalid URL syntax: " +
                    entry.label + " -> " + entry.url, exception);
        }
    }

    private static boolean containsLabel(List<FilterListCatalog.CatalogEntry> entries,
            String label) {
        for (FilterListCatalog.CatalogEntry entry : entries) {
            if (entry.label.equals(label)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsUrl(List<FilterListCatalog.CatalogEntry> entries, String url) {
        for (FilterListCatalog.CatalogEntry entry : entries) {
            if (entry.url.equals(url)) {
                return true;
            }
        }
        return false;
    }
}
