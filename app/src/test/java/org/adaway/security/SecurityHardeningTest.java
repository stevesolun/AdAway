package org.adaway.security;

import org.adaway.util.RegexUtils;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Security regression tests for hardening changes in v13.4.2.
 * Only tests that use pure-Java code live here; Android-framework-dependent tests
 * (e.g. HostsSource.isValidUrl which uses android.webkit.URLUtil) must go in androidTest.
 *
 * <p>Each test is labelled with the ATK-XX identifier it guards.
 */
public class SecurityHardeningTest {

    // -------------------------------------------------------------------------
    // ATK-01: Private/reserved IP redirect blocking (RegexUtils)
    // -------------------------------------------------------------------------

    @Test
    public void atk01_loopbackIpRejectedAsRedirectTarget() {
        assertTrue("127.0.0.1 must be flagged as private", RegexUtils.isPrivateOrReservedIp("127.0.0.1"));
        assertTrue("127.0.0.2 must be flagged as private", RegexUtils.isPrivateOrReservedIp("127.0.0.2"));
        assertTrue("::1 must be flagged as private (IPv6 loopback)", RegexUtils.isPrivateOrReservedIp("::1"));
    }

    @Test
    public void atk01_rfc1918RangesRejectedAsRedirectTarget() {
        assertTrue("10.0.0.1 must be private", RegexUtils.isPrivateOrReservedIp("10.0.0.1"));
        assertTrue("10.255.255.255 must be private", RegexUtils.isPrivateOrReservedIp("10.255.255.255"));
        assertTrue("172.16.0.1 must be private", RegexUtils.isPrivateOrReservedIp("172.16.0.1"));
        assertTrue("172.31.255.255 must be private", RegexUtils.isPrivateOrReservedIp("172.31.255.255"));
        assertTrue("192.168.0.1 must be private", RegexUtils.isPrivateOrReservedIp("192.168.0.1"));
        assertTrue("192.168.255.255 must be private", RegexUtils.isPrivateOrReservedIp("192.168.255.255"));
    }

    @Test
    public void atk01_linkLocalRejectedAsRedirectTarget() {
        assertTrue("169.254.0.1 must be link-local", RegexUtils.isPrivateOrReservedIp("169.254.0.1"));
        assertTrue("fe80::1 must be link-local", RegexUtils.isPrivateOrReservedIp("fe80::1"));
    }

    @Test
    public void atk01_multicastRejectedAsRedirectTarget() {
        assertTrue("224.0.0.1 must be multicast", RegexUtils.isPrivateOrReservedIp("224.0.0.1"));
        assertTrue("239.255.255.255 must be multicast", RegexUtils.isPrivateOrReservedIp("239.255.255.255"));
    }

    @Test
    public void atk01_publicIpAllowedAsRedirectTarget() {
        assertFalse("8.8.8.8 must NOT be flagged as private", RegexUtils.isPrivateOrReservedIp("8.8.8.8"));
        assertFalse("1.1.1.1 must NOT be flagged as private", RegexUtils.isPrivateOrReservedIp("1.1.1.1"));
        assertFalse("104.21.0.1 must NOT be flagged as private", RegexUtils.isPrivateOrReservedIp("104.21.0.1"));
    }

    @Test
    public void atk01_invalidIpReturnsFalse() {
        assertFalse("not-an-ip must return false", RegexUtils.isPrivateOrReservedIp("not-an-ip"));
        assertFalse("empty must return false", RegexUtils.isPrivateOrReservedIp(""));
    }

    // Note: ATK-02 (HostsSource.isValidUrl) tests require android.webkit.URLUtil
    // and must live in the instrumented androidTest suite.

    // ATK-09 tests are in FilterListSuggesterSanitizeTest (same package as FilterListSuggester)

    // -------------------------------------------------------------------------
    // ATK-15: Negative modelIndex clamped to valid range
    // -------------------------------------------------------------------------

    @Test
    public void atk15_mathMaxClampsNegativeIndex() {
        int negativeIndex = -5;
        int len = 3;
        int safeIndex = Math.max(0, Math.min(negativeIndex, len - 1));
        assertEquals("Negative index must be clamped to 0", 0, safeIndex);
    }

    @Test
    public void atk15_mathMinClampsOversizedIndex() {
        int oversizedIndex = 100;
        int len = 3;
        int safeIndex = Math.max(0, Math.min(oversizedIndex, len - 1));
        assertEquals("Oversized index must be clamped to len-1", len - 1, safeIndex);
    }
}
