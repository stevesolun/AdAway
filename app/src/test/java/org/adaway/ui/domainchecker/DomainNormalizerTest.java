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
    public void rejectsCredentialsInsteadOfCheckingUsername() {
        assertNull(DomainNormalizer.normalize(
                "https://account.example:secret@target.example/path"));
        assertNull(DomainNormalizer.normalize("http://account.example@target.example"));
        assertNull(DomainNormalizer.normalize("https://user:secret@b\u00fccher.example/path"));
        assertNull(DomainNormalizer.normalize("account.example:secret@target.example/path"));
    }

    @Test
    public void preservesBareHostPathQueryPortAndIdnContracts() {
        assertEquals("target.example", DomainNormalizer.normalize("TARGET.example:8080/path"));
        assertEquals("target.example", DomainNormalizer.normalize("target.example?q=1/path#x"));
        assertEquals("target.example", DomainNormalizer.normalize("target.example#x/path"));
        assertEquals("target.example", DomainNormalizer.normalize(
                " HTTPS://TARGET.EXAMPLE.:443/path?q=1#x "));
        assertEquals("xn--bcher-kva.example", DomainNormalizer.normalize(
                "https://b\u00fccher.example:443/path?q=1#x"));
        assertEquals("xn--bcher-kva.example", DomainNormalizer.normalize(
                "b\u00fccher.example:8080/path"));
    }

    @Test
    public void bareHostQueryMayContainAnotherUrl() {
        assertEquals("target.example", DomainNormalizer.normalize(
                "target.example/path?next=https://other.example/path"));
        assertEquals("target.example", DomainNormalizer.normalize(
                "target.example?next=http://other.example/path"));
    }

    @Test
    public void rejectsMalformedOrUnsupportedAuthorities() {
        for (String input : new String[]{
                "https://account.example%40target.example/path",
                "https://account.example\\@target.example/path",
                "https://target.example:secret/path", "https://target.example:65536/path",
                "https://target.example:/path", "https:///target.example/path",
                "ftp://target.example/path", "target.example:secret@other.example",
                "https://[::1]/", "https://127.0.0.1/path"}) {
            assertNull(input, DomainNormalizer.normalize(input));
        }
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
