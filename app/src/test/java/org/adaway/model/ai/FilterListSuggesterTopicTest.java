package org.adaway.model.ai;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link FilterListSuggester#isAdAwayTopicQuery(String)}.
 *
 * <p>Ensures off-topic queries (weather, sports, general chat) are rejected locally
 * without an API call, while legitimate AdAway queries pass through.
 */
public class FilterListSuggesterTopicTest {

    // ── Legitimate AdAway queries — must pass ──────────────────────────────────

    @Test
    public void legitimate_blockAds_passes() {
        assertTrue(FilterListSuggester.isAdAwayTopicQuery("block ads"));
    }

    @Test
    public void legitimate_subscribePrivacy_passes() {
        assertTrue(FilterListSuggester.isAdAwayTopicQuery("subscribe to privacy filters"));
    }

    @Test
    public void legitimate_updateSources_passes() {
        assertTrue(FilterListSuggester.isAdAwayTopicQuery("update all my filter lists"));
    }

    @Test
    public void legitimate_checkDomain_passes() {
        assertTrue(FilterListSuggester.isAdAwayTopicQuery("is whatsapp blocked"));
    }

    @Test
    public void legitimate_allowDomain_passes() {
        assertTrue(FilterListSuggester.isAdAwayTopicQuery("allow youtube.com"));
    }

    @Test
    public void legitimate_enableMalware_passes() {
        assertTrue(FilterListSuggester.isAdAwayTopicQuery("enable malware protection"));
    }

    @Test
    public void legitimate_disableSocial_passes() {
        assertTrue(FilterListSuggester.isAdAwayTopicQuery("disable social category"));
    }

    @Test
    public void legitimate_blockTracker_passes() {
        assertTrue(FilterListSuggester.isAdAwayTopicQuery("block all trackers"));
    }

    @Test
    public void legitimate_unblockDomain_passes() {
        assertTrue(FilterListSuggester.isAdAwayTopicQuery("unblock facebook.com"));
    }

    @Test
    public void legitimate_addToBlocklist_passes() {
        assertTrue(FilterListSuggester.isAdAwayTopicQuery("add ads.example.com to blocklist"));
    }

    @Test
    public void legitimate_vpn_passes() {
        assertTrue(FilterListSuggester.isAdAwayTopicQuery("how does the vpn mode work"));
    }

    @Test
    public void legitimate_dns_passes() {
        assertTrue(FilterListSuggester.isAdAwayTopicQuery("what dns server is used"));
    }

    @Test
    public void legitimate_adaway_passes() {
        assertTrue(FilterListSuggester.isAdAwayTopicQuery("configure adaway"));
    }

    @Test
    public void legitimate_phishing_passes() {
        assertTrue(FilterListSuggester.isAdAwayTopicQuery("block phishing sites"));
    }

    @Test
    public void legitimate_gambling_passes() {
        assertTrue(FilterListSuggester.isAdAwayTopicQuery("enable gambling filter"));
    }

    @Test
    public void legitimate_caseInsensitive_passes() {
        assertTrue(FilterListSuggester.isAdAwayTopicQuery("BLOCK ADS AND TRACKERS"));
    }

    // ── Off-topic queries — must be rejected ───────────────────────────────────

    @Test
    public void offTopic_weather_rejected() {
        assertFalse(FilterListSuggester.isAdAwayTopicQuery("what is the weather today"));
    }

    @Test
    public void offTopic_recipe_rejected() {
        assertFalse(FilterListSuggester.isAdAwayTopicQuery("how do I make pasta"));
    }

    @Test
    public void offTopic_sports_rejected() {
        assertFalse(FilterListSuggester.isAdAwayTopicQuery("who won the championship last night"));
    }

    @Test
    public void offTopic_generalChat_rejected() {
        assertFalse(FilterListSuggester.isAdAwayTopicQuery("tell me a joke"));
    }

    @Test
    public void offTopic_translate_rejected() {
        assertFalse(FilterListSuggester.isAdAwayTopicQuery("translate this sentence to Spanish"));
    }

    @Test
    public void offTopic_math_rejected() {
        assertFalse(FilterListSuggester.isAdAwayTopicQuery("what is 2 plus 2"));
    }

    @Test
    public void offTopic_emptyString_rejected() {
        assertFalse(FilterListSuggester.isAdAwayTopicQuery(""));
    }

    @Test
    public void offTopic_whitespaceOnly_rejected() {
        // sanitizeQuery trims whitespace; test the already-sanitized form
        assertFalse(FilterListSuggester.isAdAwayTopicQuery("   "));
    }

    @Test
    public void offTopic_genericQuestion_rejected() {
        assertFalse(FilterListSuggester.isAdAwayTopicQuery("how are you doing today"));
    }
}
