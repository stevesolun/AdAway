package org.adaway.vpn;

import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static org.adaway.vpn.VpnNetworkMonitorInstrumentedTest.available;
import static org.adaway.vpn.VpnNetworkMonitorInstrumentedTest.capabilities;
import static org.adaway.vpn.VpnNetworkMonitorInstrumentedTest.link;
import static org.adaway.vpn.VpnNetworkMonitorInstrumentedTest.network;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Synthetic identities prove selection/registration contracts, never real routing. */
@RunWith(AndroidJUnit4.class)
public class LegacyVpnNetworkMonitorInstrumentedTest {
    @Test
    public void registrationUsesDirectDefaultRequestOrPassiveBestAtExactApiBoundaries() {
        Handler handler = new Handler(Looper.getMainLooper());
        NetworkRequest expected = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_VPN).build();
        for (int api : new int[]{26, 27, 28, 29, 30, 31, 34}) {
            VpnNetworkMonitor monitor = monitor(api);
            RecordingRegistration registration = new RecordingRegistration();
            assertNull(monitor.getSelection());
            monitor.register(registration, handler);
            assertEquals("API " + api, api < 28 ? Arrays.asList("observe", "default")
                    : api < 31 ? Collections.singletonList("request")
                    : Arrays.asList("observe", "default", "best"), registration.operations);
            for (NetworkRequest request : registration.requests) assertEquals(expected, request);
            for (Handler actual : registration.handlers) assertSame(handler, actual);
            monitor.unregister(registration);
            assertEquals(registration.callbacks, registration.released);
        }
    }

    @Test
    public void repeatedRegistrationIsIdempotentAndStoppedGenerationCannotBeReused() {
        VpnNetworkMonitor monitor = monitor(30);
        RecordingRegistration registration = new RecordingRegistration();
        monitor.register(registration, null);
        monitor.register(registration, null);
        assertEquals(Collections.singletonList("request"), registration.operations);
        monitor.unregister(registration);
        monitor.unregister(registration);
        assertEquals(1, registration.released.size());
        try {
            monitor.register(registration, null);
            fail("A stopped generation must not be reactivated.");
        } catch (IllegalStateException expected) {
            assertEquals(1, registration.operations.size());
        }
        VpnNetworkMonitor replacement = monitor(30);
        replacement.register(registration, null);
        assertEquals(2, registration.operations.size());
        replacement.unregister(registration);
    }

    @Test
    public void partialRegistrationFailureReleasesAllAttemptsAndPreservesOriginalFailure()
            throws Exception {
        VpnNetworkMonitor monitor = monitor(31);
        RecordingRegistration registration = new RecordingRegistration();
        registration.failAt = 3;
        registration.failRelease = true;
        try {
            monitor.register(registration, null);
            fail("Expected registration failure.");
        } catch (SecurityException expected) {
            assertSame(registration.registrationFailure, expected);
            assertEquals(1, expected.getSuppressed().length);
        }
        assertEquals(registration.callbacks, registration.released);
        deliverEveryPassivePath(monitor);
        assertNull(monitor.getSelection());
    }

    @Test
    public void failedActiveRegistrationRetiresItsCallbackWithoutHeuristicFallback()
            throws Exception {
        VpnNetworkMonitor monitor = monitor(30);
        RecordingRegistration registration = new RecordingRegistration();
        registration.failAt = 1;
        try {
            monitor.register(registration, null);
            fail("Expected request failure.");
        } catch (SecurityException expected) {
            assertSame(registration.registrationFailure, expected);
        }
        assertEquals(Collections.singletonList(monitor.requestCallback), registration.released);
        requested(monitor, network(100), "192.0.2.53");
        deliverEveryPassivePath(monitor);
        assertNull(monitor.getSelection());
    }

    @Test
    public void unregisterFailureStillAttemptsEveryCallbackAndRetiresTheirState() throws Exception {
        VpnNetworkMonitor monitor = monitor(31);
        RecordingRegistration registration = new RecordingRegistration();
        monitor.register(registration, null);
        registration.failRelease = true;
        try {
            monitor.unregister(registration);
            fail("Expected release failure.");
        } catch (IllegalStateException expected) {
            assertEquals(2, expected.getSuppressed().length);
        }
        assertEquals(registration.callbacks, registration.released);
        deliverEveryPassivePath(monitor);
        assertNull(monitor.getSelection());
    }

    @Test
    public void api26And27DirectDefaultSwitchesBetweenOverlappingSameTransportNetworks()
            throws Exception {
        for (int api : new int[]{26, 27}) {
            VpnNetworkMonitor monitor = monitor(api);
            Network first = network(100);
            Network second = network(101);
            monitor.defaultCallback.onAvailable(first);
            available(monitor, first, TRANSPORT_WIFI, "192.0.2.53");
            available(monitor, second, TRANSPORT_WIFI, "192.0.2.53");
            assertEquals(first, monitor.getSelection().network);
            monitor.defaultCallback.onAvailable(second);
            monitor.defaultCallback.onLost(first);
            assertEquals(second, monitor.getSelection().network);
        }
    }

    @Test
    public void requestWinnerSwitchesSameTransportWithoutOldLossEvenWithIdenticalDns()
            throws Exception {
        for (int api : new int[]{28, 29, 30}) {
            for (String dns : new String[]{"192.0.2.53", "198.51.100.53"}) {
                VpnNetworkMonitor monitor = monitor(api);
                Network first = network(100);
                Network second = network(101);
                requested(monitor, first, "192.0.2.53");
                requested(monitor, second, dns);
                monitor.requestCallback.onLost(first);
                assertEquals(second, monitor.getSelection().network);
                assertEquals(InetAddress.getByName(dns), monitor.getSelection().dnsServers.get(0));
            }
        }
    }

    @Test
    public void api29And30WaitForInitialBlockedStateAndRecoverWithoutGuessing() throws Exception {
        for (int api : new int[]{29, 30}) {
            VpnNetworkMonitor monitor = monitor(api);
            Network winner = network(100);
            available(monitor, network(101), TRANSPORT_WIFI, "198.51.100.53");
            monitor.requestCallback.onAvailable(winner);
            monitor.requestCallback.updateCapabilities(winner, capabilities(TRANSPORT_WIFI));
            monitor.requestCallback.onLinkPropertiesChanged(winner, link("192.0.2.53"));
            assertNull(monitor.getSelection());
            monitor.requestCallback.onBlockedStatusChanged(winner, true);
            assertNull(monitor.getSelection());
            monitor.requestCallback.onBlockedStatusChanged(winner, false);
            assertEquals(winner, monitor.getSelection().network);
            monitor.requestCallback.onBlockedStatusChanged(winner, true);
            assertNull(monitor.getSelection());
        }
    }

    @Test
    public void api28DoesNotWaitForAnUnavailableBlockedCallback() throws Exception {
        VpnNetworkMonitor monitor = monitor(28);
        Network winner = network(100);
        monitor.requestCallback.onAvailable(winner);
        monitor.requestCallback.onLinkPropertiesChanged(winner, link("192.0.2.53"));
        assertNull(monitor.getSelection());
        monitor.requestCallback.updateCapabilities(winner, capabilities(TRANSPORT_WIFI));
        assertEquals(winner, monitor.getSelection().network);
    }

    @Test
    public void passiveCallbacksCannotSupplyOrOverrideRequestOwnedPayloads() throws Exception {
        VpnNetworkMonitor monitor = monitor(30);
        Network winner = network(100);
        available(monitor, winner, TRANSPORT_WIFI, "192.0.2.53");
        monitor.defaultCallback.onAvailable(winner);
        monitor.bestCallback.onAvailable(winner);
        assertNull(monitor.getSelection());
        monitor.requestCallback.onAvailable(winner);
        monitor.requestCallback.onBlockedStatusChanged(winner, false);
        assertNull(monitor.getSelection());
        requested(monitor, winner, "198.51.100.53");
        VpnNetworkMonitor.Selection original = monitor.getSelection();
        monitor.onLost(winner);
        monitor.defaultCallback.onLost(winner);
        monitor.bestCallback.onLost(winner);
        available(monitor, network(101), TRANSPORT_WIFI, "203.0.113.53");
        assertSame(original, monitor.getSelection());
    }

    @Test
    public void duplicateAndStaleEventsPreserveWinnerAndReturningIdentityNeedsFreshPayloads()
            throws Exception {
        VpnNetworkMonitor monitor = monitor(30);
        Network first = network(100);
        Network second = network(101);
        requested(monitor, first, "192.0.2.53");
        VpnNetworkMonitor.Selection original = monitor.getSelection();
        monitor.requestCallback.onAvailable(first);
        assertSame(original, monitor.getSelection());
        requested(monitor, second, "198.51.100.53");
        original = monitor.getSelection();
        monitor.requestCallback.updateCapabilities(first, capabilities(TRANSPORT_VPN));
        monitor.requestCallback.onLinkPropertiesChanged(first, link("203.0.113.53"));
        monitor.requestCallback.onBlockedStatusChanged(first, true);
        monitor.requestCallback.onLost(first);
        assertSame(original, monitor.getSelection());
        monitor.requestCallback.onAvailable(first);
        assertNull(monitor.getSelection());
        requested(monitor, first, "203.0.113.53");
        assertEquals(first, monitor.getSelection().network);
        assertEquals(InetAddress.getByName("203.0.113.53"),
                monitor.getSelection().dnsServers.get(0));
    }

    @Test
    public void requestDnsSnapshotsAreDefensiveAndEquivalentEventsDoNotRebuild() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        VpnNetworkMonitor monitor = new VpnNetworkMonitor(changes::incrementAndGet, 30);
        Network winner = network(100);
        requested(monitor, winner, "192.0.2.53");
        VpnNetworkMonitor.Selection original = monitor.getSelection();
        requested(monitor, winner, "192.0.2.53");
        assertEquals(1, changes.get());
        LinkProperties properties = link("198.51.100.53");
        monitor.requestCallback.onLinkPropertiesChanged(winner, properties);
        properties.setDnsServers(Collections.emptyList());
        assertEquals(2, changes.get());
        assertEquals(InetAddress.getByName("192.0.2.53"), original.dnsServers.get(0));
        assertEquals(InetAddress.getByName("198.51.100.53"),
                monitor.getSelection().dnsServers.get(0));
    }

    @Test
    public void emptyDnsVpnLossAndUnavailableNeverUseAnotherObservedNetwork() throws Exception {
        VpnNetworkMonitor monitor = monitor(30);
        Network winner = network(100);
        available(monitor, network(101), TRANSPORT_WIFI, "198.51.100.53");
        requested(monitor, winner, "192.0.2.53");
        monitor.requestCallback.onLinkPropertiesChanged(winner, new LinkProperties());
        assertNull(monitor.getSelection());
        requested(monitor, winner, "192.0.2.53");
        monitor.requestCallback.updateCapabilities(winner, capabilities(TRANSPORT_VPN));
        assertNull(monitor.getSelection());
        requested(monitor, winner, "192.0.2.53");
        monitor.requestCallback.onLost(winner);
        monitor.requestCallback.onLinkPropertiesChanged(winner, link("203.0.113.53"));
        assertNull(monitor.getSelection());
        requested(monitor, winner, "192.0.2.53");
        monitor.requestCallback.onUnavailable();
        assertNull(monitor.getSelection());
    }

    @Test
    public void stoppedRequestCannotPublishIntoReplacementGeneration() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        VpnNetworkMonitor old = new VpnNetworkMonitor(changes::incrementAndGet, 30);
        RecordingRegistration registration = new RecordingRegistration();
        old.register(registration, null);
        requested(old, network(100), "192.0.2.53");
        old.unregister(registration);
        VpnNetworkMonitor replacement = new VpnNetworkMonitor(changes::incrementAndGet, 30);
        replacement.register(registration, null);
        requested(replacement, network(101), "198.51.100.53");
        requested(old, network(102), "203.0.113.53");
        old.requestCallback.onCapabilitiesChanged(network(102), new NetworkCapabilities());
        old.requestCallback.onLost(network(102));
        old.requestCallback.onUnavailable();
        assertEquals(2, changes.get());
        assertNull(old.getSelection());
        assertEquals(network(101), replacement.getSelection().network);
        replacement.unregister(registration);
    }

    @Test
    public void realEmptyFrameworkCapabilitiesCannotMakeRequestWinnerUsable() throws Exception {
        VpnNetworkMonitor monitor = monitor(30);
        Network winner = network(100);
        monitor.requestCallback.onAvailable(winner);
        monitor.requestCallback.onCapabilitiesChanged(winner, new NetworkCapabilities());
        monitor.requestCallback.onLinkPropertiesChanged(winner, link("192.0.2.53"));
        monitor.requestCallback.onBlockedStatusChanged(winner, false);
        assertNull(monitor.getSelection());
    }

    static void requested(VpnNetworkMonitor monitor, Network network, String dns) throws Exception {
        monitor.requestCallback.onAvailable(network);
        monitor.requestCallback.updateCapabilities(network, capabilities(TRANSPORT_WIFI));
        monitor.requestCallback.onLinkPropertiesChanged(network, link(dns));
        monitor.requestCallback.onBlockedStatusChanged(network, false);
    }

    private static VpnNetworkMonitor monitor(int api) {
        return new VpnNetworkMonitor(() -> { }, api);
    }

    private static void deliverEveryPassivePath(VpnNetworkMonitor monitor) throws Exception {
        Network network = network(100);
        available(monitor, network, TRANSPORT_WIFI, "192.0.2.53");
        monitor.defaultCallback.onAvailable(network);
        monitor.updateDefaultCapabilities(network, capabilities(TRANSPORT_WIFI));
        monitor.bestCallback.onAvailable(network);
        monitor.onBlockedStatusChanged(network, false);
        monitor.onLost(network);
        monitor.defaultCallback.onLost(network);
        monitor.bestCallback.onLost(network);
    }

    static final class RecordingRegistration implements VpnNetworkMonitor.Registration {
        final List<String> operations = new ArrayList<>();
        final List<NetworkRequest> requests = new ArrayList<>();
        final List<Handler> handlers = new ArrayList<>();
        final List<ConnectivityManager.NetworkCallback> callbacks = new ArrayList<>();
        final List<ConnectivityManager.NetworkCallback> released = new ArrayList<>();
        final SecurityException registrationFailure = new SecurityException("Request denied");
        int failAt;
        boolean failRelease;

        private void record(String operation, NetworkRequest request,
                            ConnectivityManager.NetworkCallback callback, Handler handler) {
            operations.add(operation);
            if (request != null) requests.add(request);
            handlers.add(handler);
            callbacks.add(callback);
            if (operations.size() == failAt) throw registrationFailure;
        }

        @Override
        public void observe(NetworkRequest request, ConnectivityManager.NetworkCallback callback,
                            Handler handler) {
            record("observe", request, callback, handler);
        }

        @Override
        public void observeDefault(ConnectivityManager.NetworkCallback callback, Handler handler) {
            record("default", null, callback, handler);
        }

        @Override
        public void observeBest(NetworkRequest request, ConnectivityManager.NetworkCallback callback,
                                Handler handler) {
            record("best", request, callback, handler);
        }

        @Override
        public void request(NetworkRequest request, ConnectivityManager.NetworkCallback callback,
                            Handler handler) {
            record("request", request, callback, handler);
        }

        @Override
        public void unregister(ConnectivityManager.NetworkCallback callback) {
            assertNotNull(callback);
            released.add(callback);
            if (failRelease) throw new IllegalStateException("Release failed");
        }
    }
}
