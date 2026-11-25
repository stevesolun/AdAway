package org.adaway.vpn;

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_ETHERNET;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class VpnNetworkMonitorInstrumentedTest {
    @Test
    public void sameTransportOverlapLosesOnlyTheDepartingNetwork() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        VpnNetworkMonitor monitor = new VpnNetworkMonitor(changes::incrementAndGet, 31);
        Network first = network(100);
        Network second = network(101);
        available(monitor, first, TRANSPORT_WIFI, "192.0.2.53");
        available(monitor, second, TRANSPORT_WIFI, "192.0.2.54");
        assertEquals(1, changes.get());

        monitor.onLost(first);
        assertEquals(second, monitor.getSelection().network);
        assertEquals(2, changes.get());
        monitor.onLost(second);
        assertNull(monitor.getSelection());
        assertEquals(3, changes.get());
    }

    @Test
    public void duplicateAvailabilityAndUnknownLossDoNotRebuild() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        VpnNetworkMonitor monitor = new VpnNetworkMonitor(changes::incrementAndGet, 31);
        Network selected = network(100);
        available(monitor, selected, TRANSPORT_WIFI, "192.0.2.53");
        VpnNetworkMonitor.Selection original = monitor.getSelection();
        available(monitor, selected, TRANSPORT_WIFI, "192.0.2.53");
        monitor.onLost(network(999));
        assertSame(original, monitor.getSelection());
        assertEquals(1, changes.get());
        monitor.onLost(selected);
        monitor.onLost(selected);
        assertEquals(2, changes.get());
    }

    @Test
    public void lossOfUnselectedNetworkDoesNotRebuild() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        VpnNetworkMonitor monitor = new VpnNetworkMonitor(changes::incrementAndGet, 31);
        Network first = network(100);
        Network second = network(101);
        available(monitor, first, TRANSPORT_WIFI, "192.0.2.53");
        available(monitor, second, TRANSPORT_WIFI, "192.0.2.54");
        monitor.onLost(second);
        assertEquals(first, monitor.getSelection().network);
        assertEquals(1, changes.get());
    }

    @Test
    public void sameNetworkDnsChangePublishesNewImmutableSnapshot() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        VpnNetworkMonitor monitor = new VpnNetworkMonitor(changes::incrementAndGet, 31);
        Network selected = network(100);
        available(monitor, selected, TRANSPORT_WIFI, "192.0.2.53");
        VpnNetworkMonitor.Selection original = monitor.getSelection();
        LinkProperties replacement = link("198.51.100.53");
        monitor.onLinkPropertiesChanged(selected, replacement);
        replacement.setDnsServers(Collections.emptyList());
        assertEquals(InetAddress.getByName("192.0.2.53"), original.dnsServers.get(0));
        assertEquals(InetAddress.getByName("198.51.100.53"),
                monitor.getSelection().dnsServers.get(0));
        assertEquals(selected, monitor.getSelection().network);
        assertEquals(2, changes.get());
    }

    @Test
    public void equivalentCapabilitiesAndUnrelatedLinkChangesDoNotRebuild() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        VpnNetworkMonitor monitor = new VpnNetworkMonitor(changes::incrementAndGet, 31);
        Network selected = network(100);
        available(monitor, selected, TRANSPORT_WIFI, "192.0.2.53");
        LinkProperties properties = link("192.0.2.53");
        properties.setDomains("example.test");
        monitor.onLinkPropertiesChanged(selected, properties);
        monitor.updateCapabilities(selected, capabilities(TRANSPORT_WIFI));
        assertEquals(1, changes.get());
    }

    @Test
    public void defaultHandoffChangesNetworkEvenWithIdenticalResolverAddress() throws Exception {
        VpnNetworkMonitor monitor = new VpnNetworkMonitor(() -> { }, 26);
        Network cell = network(100);
        Network wifi = network(101);
        monitor.defaultCallback.onAvailable(cell);
        available(monitor, cell, TRANSPORT_CELLULAR, "192.0.2.53");
        available(monitor, wifi, TRANSPORT_WIFI, "192.0.2.53");
        assertEquals(cell, monitor.getSelection().network);
        monitor.defaultCallback.onAvailable(wifi);
        monitor.defaultCallback.onLost(cell);
        assertEquals(wifi, monitor.getSelection().network);
    }

    @Test
    public void passiveVpnTransportHintChangesFallbackSelection() throws Exception {
        VpnNetworkMonitor monitor = new VpnNetworkMonitor(() -> { }, 31);
        Network vpn = network(200);
        Network cell = network(100);
        Network wifi = network(101);
        monitor.defaultCallback.onAvailable(vpn);
        monitor.updateDefaultCapabilities(vpn,
                capabilities(TRANSPORT_CELLULAR, TRANSPORT_VPN));
        available(monitor, cell, TRANSPORT_CELLULAR, "192.0.2.53");
        available(monitor, wifi, TRANSPORT_WIFI, "198.51.100.53");
        assertEquals(cell, monitor.getSelection().network);
        monitor.updateDefaultCapabilities(vpn,
                capabilities(TRANSPORT_WIFI, TRANSPORT_VPN));
        assertEquals(wifi, monitor.getSelection().network);
    }

    @Test
    public void platformBestNetworkOverridesVpnTransportHints() throws Exception {
        VpnNetworkMonitor monitor = new VpnNetworkMonitor(() -> { }, 31);
        Network vpn = network(200);
        Network cell = network(100);
        Network wifi = network(101);
        monitor.defaultCallback.onAvailable(vpn);
        monitor.updateDefaultCapabilities(vpn,
                capabilities(TRANSPORT_CELLULAR, TRANSPORT_VPN));
        available(monitor, cell, TRANSPORT_CELLULAR, "192.0.2.53");
        available(monitor, wifi, TRANSPORT_WIFI, "198.51.100.53");
        monitor.bestCallback.onAvailable(wifi);
        monitor.bestCallback.onLost(cell);
        assertEquals(wifi, monitor.getSelection().network);
    }

    @Test
    public void passiveTransportHintAloneCannotIdentifySameTransportWinner() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        VpnNetworkMonitor monitor = new VpnNetworkMonitor(changes::incrementAndGet, 31);
        Network vpn = network(200);
        Network first = network(100);
        Network second = network(101);
        monitor.defaultCallback.onAvailable(vpn);
        monitor.updateDefaultCapabilities(vpn,
                capabilities(TRANSPORT_WIFI, TRANSPORT_VPN));
        available(monitor, first, TRANSPORT_WIFI, "192.0.2.53");
        available(monitor, second, TRANSPORT_WIFI, "198.51.100.53");
        VpnNetworkMonitor.Selection original = monitor.getSelection();

        // An information-limit fixture, not the API28-30 production request path.
        // With no best event, the unchanged transport hint cannot identify a winner.
        monitor.updateDefaultCapabilities(vpn,
                capabilities(TRANSPORT_WIFI, TRANSPORT_VPN));

        assertSame(original, monitor.getSelection());
        assertEquals(first, monitor.getSelection().network);
        assertEquals(1, changes.get());
    }

    @Test
    public void lostNetworkCannotBeResurrectedByLatePropertyCallbacks() throws Exception {
        VpnNetworkMonitor monitor = new VpnNetworkMonitor(() -> { }, 31);
        Network lost = network(100);
        available(monitor, lost, TRANSPORT_WIFI, "192.0.2.53");
        monitor.onLost(lost);
        monitor.updateCapabilities(lost, capabilities(TRANSPORT_WIFI));
        monitor.onLinkPropertiesChanged(lost, link("198.51.100.53"));
        monitor.bestCallback.onAvailable(lost);
        assertNull(monitor.getSelection());
    }

    @Test
    public void closedMonitorIgnoresEveryCallbackPath() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        VpnNetworkMonitor monitor = new VpnNetworkMonitor(changes::incrementAndGet, 31);
        Network network = network(100);
        available(monitor, network, TRANSPORT_WIFI, "192.0.2.53");
        monitor.close();
        available(monitor, network, TRANSPORT_WIFI, "198.51.100.53");
        monitor.defaultCallback.onAvailable(network);
        monitor.updateDefaultCapabilities(network, capabilities(TRANSPORT_WIFI));
        monitor.bestCallback.onAvailable(network);
        monitor.onBlockedStatusChanged(network, false);
        monitor.onLost(network);
        monitor.defaultCallback.onLost(network);
        monitor.bestCallback.onLost(network);
        assertNull(monitor.getSelection());
        assertEquals(1, changes.get());
    }

    @Test
    public void capabilitiesAndDnsAreBothRequiredAndVpnIsNeverEligible() throws Exception {
        VpnNetworkMonitor monitor = new VpnNetworkMonitor(() -> { }, 31);
        Network network = network(100);
        monitor.onAvailable(network);
        monitor.onLinkPropertiesChanged(network, link("192.0.2.53"));
        assertNull(monitor.getSelection());
        monitor.updateCapabilities(network,
                capabilities(TRANSPORT_WIFI, TRANSPORT_VPN));
        assertNull(monitor.getSelection());
        monitor.updateCapabilities(network, capabilities(TRANSPORT_ETHERNET));
        assertNotNull(monitor.getSelection());
        monitor.onLinkPropertiesChanged(network, new LinkProperties());
        assertNull(monitor.getSelection());
    }

    @Test
    public void blockedNetworkFallsBackAndRecoversWithoutLosingIdentity() throws Exception {
        VpnNetworkMonitor monitor = new VpnNetworkMonitor(() -> { }, 31);
        Network wifi = network(100);
        Network cell = network(101);
        available(monitor, wifi, TRANSPORT_WIFI, "192.0.2.53");
        available(monitor, cell, TRANSPORT_CELLULAR, "198.51.100.53");
        monitor.onBlockedStatusChanged(wifi, true);
        assertEquals(cell, monitor.getSelection().network);
        monitor.onBlockedStatusChanged(wifi, false);
        assertEquals(wifi, monitor.getSelection().network);
    }

    @Test
    public void frameworkCapabilityPayloadUsesTheSameEligibilityRules() throws Exception {
        NetworkCapabilities payload = new NetworkCapabilities();
        VpnNetworkMonitor.Capabilities snapshot = VpnNetworkMonitor.Capabilities.from(payload);
        assertEquals(payload.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                snapshot.internet);
        assertEquals(payload.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
                snapshot.notVpn);
        assertEquals(payload.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                snapshot.validated);
        for (int transport : new int[]{TRANSPORT_CELLULAR, TRANSPORT_WIFI, TRANSPORT_ETHERNET,
                TRANSPORT_VPN, NetworkCapabilities.TRANSPORT_BLUETOOTH}) {
            assertEquals(payload.hasTransport(transport), snapshot.hasTransport(transport));
        }
        VpnNetworkMonitor monitor = new VpnNetworkMonitor(() -> { }, 31);
        Network candidate = network(100);
        monitor.onAvailable(candidate);
        monitor.onLinkPropertiesChanged(candidate, link("192.0.2.53"));
        monitor.onCapabilitiesChanged(candidate, payload);
        monitor.defaultCallback.onAvailable(candidate);
        monitor.defaultCallback.onCapabilitiesChanged(candidate, payload);
        assertNull("An empty real payload has no Internet capability", monitor.getSelection());
    }

    static Network network(long id) {
        // Synthetic Android handles: no socket is ever bound to these test-only networks.
        return Network.fromNetworkHandle((id << 32) | 0xcafed00dL);
    }

    static void available(VpnNetworkMonitor monitor, Network network, int transport, String dns)
            throws Exception {
        monitor.onAvailable(network);
        monitor.updateCapabilities(network, capabilities(transport));
        monitor.onLinkPropertiesChanged(network, link(dns));
    }

    static VpnNetworkMonitor.Capabilities capabilities(int... transports) {
        int mask = 0;
        boolean notVpn = true;
        for (int transport : transports) {
            mask |= 1 << transport;
            if (transport == TRANSPORT_VPN) notVpn = false;
        }
        return new VpnNetworkMonitor.Capabilities(true, notVpn, true, mask);
    }

    static LinkProperties link(String dns) throws Exception {
        LinkProperties properties = new LinkProperties();
        properties.setDnsServers(Collections.singletonList(InetAddress.getByName(dns)));
        return properties;
    }
}
