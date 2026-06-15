package org.adaway.ui.home;

import org.adaway.model.adblocking.AdBlockMethod;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LeakStatusTest {
    @Test
    public void runningVpn_reportsEncryptedDnsCoverageLimit() {
        LeakStatus status = LeakStatus.create(
                AdBlockMethod.VPN,
                true,
                LeakStatus.PRIVATE_DNS_MODE_OFF,
                null,
                false,
                0,
                LeakStatus.VPN_EXCLUDED_SYSTEM_NONE);

        assertTrue(status.hasCommonDohRouteCoverage());
        assertTrue(status.hasDohRisk());
        assertTrue(status.hasRisks());
        assertEquals(1, status.riskCount());
    }

    @Test
    public void vpnWithPrivateDnsAndExcludedApps_reportsThreeRisks() {
        LeakStatus status = LeakStatus.create(
                AdBlockMethod.VPN,
                true,
                LeakStatus.PRIVATE_DNS_MODE_HOSTNAME,
                "dns.example",
                false,
                2,
                "allExceptBrowsers");

        assertTrue(status.hasPrivateDnsRisk());
        assertTrue(status.hasVpnBypassRisk());
        assertTrue(status.hasDohRisk());
        assertEquals(3, status.riskCount());
    }

    @Test
    public void rootMode_reportsBrowserDohRisk() {
        LeakStatus status = LeakStatus.create(
                AdBlockMethod.ROOT,
                false,
                null,
                null,
                false,
                0,
                LeakStatus.VPN_EXCLUDED_SYSTEM_NONE);

        assertTrue(status.hasDohRisk());
        assertEquals(1, status.riskCount());
    }

    @Test
    public void stoppedVpn_reportsVpnAndDohRisks() {
        LeakStatus status = LeakStatus.create(
                AdBlockMethod.VPN,
                false,
                LeakStatus.PRIVATE_DNS_MODE_OFF,
                null,
                false,
                0,
                LeakStatus.VPN_EXCLUDED_SYSTEM_NONE);

        assertTrue(status.hasVpnStoppedRisk());
        assertTrue(status.hasDohRisk());
        assertEquals(2, status.riskCount());
    }

    @Test
    public void vpnWithAppManagedBypassAllowed_reportsBypassRisk() {
        LeakStatus status = LeakStatus.create(
                AdBlockMethod.VPN,
                true,
                LeakStatus.PRIVATE_DNS_MODE_OFF,
                null,
                true,
                0,
                LeakStatus.VPN_EXCLUDED_SYSTEM_NONE);

        assertTrue(status.hasVpnBypassRisk());
        assertEquals(2, status.riskCount());
    }

    @Test
    public void vpnWithUnknownPrivateDns_reportsUnknownPrivateDnsRisk() {
        LeakStatus status = LeakStatus.create(
                AdBlockMethod.VPN,
                true,
                null,
                null,
                false,
                0,
                LeakStatus.VPN_EXCLUDED_SYSTEM_NONE);

        assertTrue(status.isPrivateDnsUnknown());
        assertTrue(status.hasPrivateDnsRisk());
        assertEquals(2, status.riskCount());
    }
}
