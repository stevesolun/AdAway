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

    @Test
    public void acceptsNormalDomains() {
        assertEquals("example.com", DomainNormalizer.normalize(
                "https://Example.COM:443/path?q=1#frag"));
        assertEquals("example.com", DomainNormalizer.normalize("example.com."));
        assertEquals("sub.example.co.uk", DomainNormalizer.normalize("sub.example.co.uk"));
    }

    @Test
    public void acceptsIdnaDomains() {
        assertEquals("xn--bcher-kva.example",
                DomainNormalizer.normalize("xn--bcher-kva.example"));
        assertEquals("xn--bcher-kva.example",
                DomainNormalizer.normalize("b\u00fccher.example"));
        assertEquals("xn--r8jz45g.xn--zckzah",
                DomainNormalizer.normalize("\u4f8b\u3048.\u30c6\u30b9\u30c8"));
    }

    @Test
    public void rejectsIpv4Addresses() {
        assertNull(DomainNormalizer.normalize("127.0.0.1"));
        assertNull(DomainNormalizer.normalize("127.0.0.1:8080"));
        assertNull(DomainNormalizer.normalize("http://127.0.0.1"));
        assertNull(DomainNormalizer.normalize("8.8.8.8"));
    }

    @Test
    public void rejectsIpv6AndBracketedIpv6() {
        assertNull(DomainNormalizer.normalize("::1"));
        assertNull(DomainNormalizer.normalize("http://[::1]/"));
    }

    @Test
    public void rejectsInvalidHostnames() {
        assertNull(DomainNormalizer.normalize("localhost"));
        assertNull(DomainNormalizer.normalize("localhost:8080"));
        assertNull(DomainNormalizer.normalize("ads"));
        assertNull(DomainNormalizer.normalize("bad..example"));
        assertNull(DomainNormalizer.normalize(".example.com"));
        assertNull(DomainNormalizer.normalize("example .com"));
        assertNull(DomainNormalizer.normalize("this is not a domain"));
        assertNull(DomainNormalizer.normalize("*.example.com"));
        assertNull(DomainNormalizer.normalize("javascript:alert(1)"));
    }

    @Test
    public void rejectsSingleLabelIdna() {
        assertNull(DomainNormalizer.normalize("b\u00fccher"));
    }
}
