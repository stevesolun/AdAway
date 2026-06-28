package org.adaway.model.source;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FilterListCompatibilityTest {

    @Test
    public void isBulkSafe_acceptsExactDnsSyntaxesOnly() {
        assertTrue(FilterListCompatibility.isBulkSafe(new int[]{1}));
        assertTrue(FilterListCompatibility.isBulkSafe(new int[]{2}));
        assertTrue(FilterListCompatibility.isBulkSafe(new int[]{14}));
        assertTrue(FilterListCompatibility.isBulkSafe(new int[]{1, 2, 14}));
    }

    @Test
    public void isBulkSafe_rejectsUnknownAndBrowserRuleSyntaxes() {
        assertFalse(FilterListCompatibility.isBulkSafe(null));
        assertFalse(FilterListCompatibility.isBulkSafe(new int[0]));
        assertFalse(FilterListCompatibility.isBulkSafe(new int[]{3}));
        assertFalse(FilterListCompatibility.isBulkSafe(new int[]{4}));
        assertFalse(FilterListCompatibility.isBulkSafe(new int[]{6}));
        assertFalse(FilterListCompatibility.isBulkSafe(new int[]{20}));
        assertFalse(FilterListCompatibility.isBulkSafe(new int[]{29}));
        assertFalse(FilterListCompatibility.isBulkSafe(new int[]{1, 3}));
    }

    @Test
    public void isUsableDownloadUrl_acceptsHttpsTextListUrls() {
        assertTrue(FilterListCompatibility.isUsableDownloadUrl("https://adaway.org/hosts.txt"));
        assertTrue(FilterListCompatibility.isUsableDownloadUrl(
                "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts"));
    }

    @Test
    public void isUsableDownloadUrl_rejectsNonRawOrBinaryUrls() {
        assertFalse(FilterListCompatibility.isUsableDownloadUrl(null));
        assertFalse(FilterListCompatibility.isUsableDownloadUrl("http://example.com/hosts.txt"));
        assertFalse(FilterListCompatibility.isUsableDownloadUrl(
                "https://github.com/example/repo/blob/main/hosts.txt"));
        assertFalse(FilterListCompatibility.isUsableDownloadUrl("https://example.com/list.zip"));
        assertFalse(FilterListCompatibility.isUsableDownloadUrl("https://example.com/readme.html"));
    }

    @Test
    public void capabilitySummaryDisclosesSafeAndSkippedSemantics() {
        assertEquals("DNS-safe: exact hosts and plain domains",
                FilterListCompatibility.rowSummary(new int[]{1, 2}));
        assertTrue(FilterListCompatibility.capabilitySummary(new int[]{1, 2})
                .contains("Supported: exact hosts and plain domain entries"));
        assertTrue(FilterListCompatibility.capabilitySummary(new int[]{1, 2})
                .contains("not claimed"));

        assertEquals("Limited support: browser semantics skipped",
                FilterListCompatibility.rowSummary(new int[]{3}));
        assertTrue(FilterListCompatibility.capabilitySummary(new int[]{3})
                .contains("Domain extraction only"));
        assertTrue(FilterListCompatibility.capabilitySummary(new int[]{3})
                .contains("Exceptions"));
        assertTrue(FilterListCompatibility.capabilitySummary(new int[]{3})
                .contains("unsafe-to-flatten"));

        assertEquals("Limited support: unknown syntax",
                FilterListCompatibility.rowSummary(null));
        assertTrue(FilterListCompatibility.capabilitySummary(null)
                .contains("Unknown syntax"));
    }

    @Test
    public void decodeSyntaxIdsReturnsValidIdsOrNullForUnknownMetadata() {
        assertEquals("1,2,14", FilterListCompatibility.encodeSyntaxIds(
                FilterListCompatibility.decodeSyntaxIds("1, 2,14")));

        assertFalse(FilterListCompatibility.isBulkSafe(
                FilterListCompatibility.decodeSyntaxIds("3")));
        assertEquals("Limited support: unknown syntax",
                FilterListCompatibility.rowSummary(
                        FilterListCompatibility.decodeSyntaxIds("1,,2")));
        assertEquals("Limited support: unknown syntax",
                FilterListCompatibility.rowSummary(
                        FilterListCompatibility.decodeSyntaxIds("not-a-syntax")));
        assertEquals("Limited support: unknown syntax",
                FilterListCompatibility.rowSummary(
                        FilterListCompatibility.decodeSyntaxIds("0")));
    }
}
