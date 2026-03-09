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
}
