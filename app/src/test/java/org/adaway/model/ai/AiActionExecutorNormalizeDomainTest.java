package org.adaway.model.ai;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link AiActionExecutor#normalizeDomain(String)}.
 *
 * <p>Must live in the same package to access the package-private method.
 */
public class AiActionExecutorNormalizeDomainTest {

    // -------------------------------------------------------------------------
    // Happy-path normalization
    // -------------------------------------------------------------------------

    @Test
    public void plainHostname_returnedAsIs() {
        assertEquals("example.com", AiActionExecutor.normalizeDomain("example.com"));
    }

    @Test
    public void plainHostname_lowercased() {
        assertEquals("Example.COM must be lowercased",
                "example.com", AiActionExecutor.normalizeDomain("Example.COM"));
    }

    @Test
    public void https_schemeStripped() {
        assertEquals("example.com", AiActionExecutor.normalizeDomain("https://example.com"));
    }

    @Test
    public void http_schemeStripped() {
        assertEquals("example.com", AiActionExecutor.normalizeDomain("http://example.com"));
    }

    @Test
    public void schemeAndPath_bothStripped() {
        assertEquals("example.com",
                AiActionExecutor.normalizeDomain("https://example.com/path/to/page"));
    }

    @Test
    public void pathWithQueryString_stripped() {
        assertEquals("ads.example.com",
                AiActionExecutor.normalizeDomain("ads.example.com/track?utm=1"));
    }

    @Test
    public void portStripped() {
        assertEquals("example.com", AiActionExecutor.normalizeDomain("example.com:8080"));
    }

    @Test
    public void schemeAndPort_bothStripped() {
        assertEquals("example.com",
                AiActionExecutor.normalizeDomain("https://example.com:443/path"));
    }

    @Test
    public void leadingAndTrailingWhitespace_trimmed() {
        assertEquals("example.com", AiActionExecutor.normalizeDomain("  example.com  "));
    }

    @Test
    public void subdomainHostname_accepted() {
        assertEquals("ads.tracking.example.com",
                AiActionExecutor.normalizeDomain("ads.tracking.example.com"));
    }

    // -------------------------------------------------------------------------
    // Rejection cases
    // -------------------------------------------------------------------------

    @Test
    public void emptyString_returnsNull() {
        assertNull("Empty string must return null", AiActionExecutor.normalizeDomain(""));
    }

    @Test
    public void whitespaceOnly_returnsNull() {
        assertNull("Whitespace-only must return null", AiActionExecutor.normalizeDomain("   "));
    }

    @Test
    public void notAnHostname_returnsNull() {
        assertNull("Random text must return null",
                AiActionExecutor.normalizeDomain("this is not a domain"));
    }

    @Test
    public void domainWithSpacesAfterStrip_returnsNull() {
        // LLM might return "example .com" with a space
        assertNull("Domain with embedded space must be rejected",
                AiActionExecutor.normalizeDomain("example .com"));
    }

    @Test
    public void justSlash_returnsNull() {
        assertNull("Bare slash must return null", AiActionExecutor.normalizeDomain("/"));
    }

    // -------------------------------------------------------------------------
    // ATK-01: IP address / private hostname rejection (no letter → invalid hostname)
    // -------------------------------------------------------------------------

    @Test
    public void ipv4Loopback_returnsNull() {
        // 127.0.0.1 has no letter — isValidHostname requires hasLetter=true
        assertNull("Loopback IP must be rejected", AiActionExecutor.normalizeDomain("127.0.0.1"));
    }

    @Test
    public void ipv4Loopback_withScheme_returnsNull() {
        assertNull("Loopback IP with scheme must be rejected",
                AiActionExecutor.normalizeDomain("http://127.0.0.1"));
    }

    @Test
    public void ipv4Rfc1918_classA_returnsNull() {
        assertNull("RFC-1918 class-A IP must be rejected",
                AiActionExecutor.normalizeDomain("10.0.0.1"));
    }

    @Test
    public void ipv4Rfc1918_classB_returnsNull() {
        assertNull("RFC-1918 class-B IP must be rejected",
                AiActionExecutor.normalizeDomain("192.168.1.1"));
    }

    @Test
    public void ipv4Rfc1918_classC_returnsNull() {
        assertNull("RFC-1918 class-C IP must be rejected",
                AiActionExecutor.normalizeDomain("172.16.0.1"));
    }

    @Test
    public void ipv4Public_returnsNull() {
        // All pure IPs (no letters) must be rejected regardless of whether private
        assertNull("Public IP must be rejected (no letter in hostname)",
                AiActionExecutor.normalizeDomain("8.8.8.8"));
    }

    @Test
    public void ipv4WithPort_returnsNull() {
        // After port stripping: "127.0.0.1" — still no letter
        assertNull("IP:port must be rejected after port strip",
                AiActionExecutor.normalizeDomain("127.0.0.1:8080"));
    }

    @Test
    public void localhost_returnsNull() {
        // "localhost" has no dot — must fail single-label check or isValidHostname
        assertNull("'localhost' must be rejected (no dot, no TLD)",
                AiActionExecutor.normalizeDomain("localhost"));
    }

    @Test
    public void localhostWithPort_returnsNull() {
        assertNull("'localhost:8080' must be rejected",
                AiActionExecutor.normalizeDomain("localhost:8080"));
    }

    // -------------------------------------------------------------------------
    // Edge cases: extra characters, unusual inputs
    // -------------------------------------------------------------------------

    @Test
    public void trailingDot_normalised() {
        // Trailing dot is valid DNS syntax (FQDN) but should be stripped or accepted
        // Either "example.com" or null is acceptable — just must not crash
        String result = AiActionExecutor.normalizeDomain("example.com.");
        // If not null, the trailing dot must be stripped
        if (result != null) {
            assertFalse("Trailing dot must be stripped from result", result.endsWith("."));
        }
    }

    @Test
    public void singleLabel_noTld_returnsNull() {
        // "ads" alone — no TLD, should be rejected
        assertNull("Single-label domain without TLD must be rejected",
                AiActionExecutor.normalizeDomain("ads"));
    }

    @Test
    public void justNumbers_returnsNull() {
        // "12345" — no dot, no letter → invalid
        assertNull("Pure numbers must be rejected", AiActionExecutor.normalizeDomain("12345"));
    }

    @Test
    public void veryLongDomain_returnsNullOrTruncated() {
        // 300-char label — violates RFC 1035 max 253 chars
        String longDomain = "a".repeat(260) + ".com";
        // Must not crash; null or some rejection is acceptable
        String result = AiActionExecutor.normalizeDomain(longDomain);
        // If accepted, length must be ≤ 253
        if (result != null) {
            assertTrue("Accepted domain must be ≤ 253 chars", result.length() <= 253);
        }
    }
}
