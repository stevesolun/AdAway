package org.adaway.ui.domainchecker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DomainNormalizerTest {

    @Test
    public void testSimpleDomain() {
        assertEquals("example.com", DomainNormalizer.normalize("example.com"));
    }

    @Test
    public void testHttpsUrl() {
        assertEquals("example.com", DomainNormalizer.normalize("https://example.com/path?q=1#frag"));
    }

    @Test
    public void testHttpUrl() {
        assertEquals("example.com", DomainNormalizer.normalize("http://example.com/path"));
    }

    @Test
    public void testPortIsStripped() {
        assertEquals("example.com", DomainNormalizer.normalize("example.com:8080"));
    }

    @Test
    public void testPortInUrl() {
        assertEquals("example.com", DomainNormalizer.normalize("https://example.com:443/path"));
    }

    @Test
    public void testUpperCase() {
        assertEquals("example.com", DomainNormalizer.normalize("EXAMPLE.COM"));
    }

    @Test
    public void testLeadingTrailingWhitespace() {
        assertEquals("example.com", DomainNormalizer.normalize("  example.com  "));
    }

    @Test
    public void testNullInput() {
        assertNull(DomainNormalizer.normalize(null));
    }

    @Test
    public void testEmptyInput() {
        assertNull(DomainNormalizer.normalize(""));
    }

    @Test
    public void testWhitespaceOnlyInput() {
        assertNull(DomainNormalizer.normalize("   "));
    }

    @Test
    public void testSubdomain() {
        assertEquals("sub.example.com", DomainNormalizer.normalize("sub.example.com"));
    }
}
