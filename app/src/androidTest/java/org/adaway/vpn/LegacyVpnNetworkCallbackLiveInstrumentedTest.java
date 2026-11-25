package org.adaway.vpn;

import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Opt-in real framework callbacks only. Does not establish a VPN or prove packet routing. */
@RunWith(AndroidJUnit4.class)
public class LegacyVpnNetworkCallbackLiveInstrumentedTest {
    @Test(timeout = 30_000)
    public void realRegistrationDeliversAReadyNonVpnSelection() throws Exception {
        requireOptIn("adawayLiveNetworkCallbacks");
        try (LiveMonitor live = new LiveMonitor()) {
            VpnNetworkMonitor.Selection selection = live.awaitSelection(null, 20_000);
            assertUsable(live.manager, selection);
            Log.i("LegacyNetworkCallback", "Real callback selection on API "
                    + Build.VERSION.SDK_INT + ": " + selection.network
                    + "; not packet-routing proof");
        }
    }

    @Test(timeout = 150_000)
    public void realSameTransportReplacementWithOldNetworkStillReportedAndVpnUnchanged()
            throws Exception {
        requireOptIn("adawayLiveNetworkCallbacks");
        requireOptIn("adawayLiveNetworkHandoff");
        Context context = ApplicationProvider.getApplicationContext();
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        assertNotNull(manager);
        Network vpn = manager.getActiveNetwork();
        NetworkCapabilities vpnCaps = vpn == null ? null : manager.getNetworkCapabilities(vpn);
        assumeTrue("Prepare the VPN externally; this fixture does not start or replace it.",
                vpnCaps != null && vpnCaps.hasTransport(TRANSPORT_VPN));
        AtomicBoolean vpnLost = new AtomicBoolean();
        ConnectivityManager.NetworkCallback vpnObserver = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onLost(Network network) {
                if (vpn.equals(network)) vpnLost.set(true);
            }
        };
        Handler handler = new Handler(Looper.getMainLooper());
        NetworkRequest vpnRequest = new NetworkRequest.Builder()
                .removeCapability(NET_CAPABILITY_NOT_VPN)
                .addTransportType(TRANSPORT_VPN).build();
        manager.registerNetworkCallback(vpnRequest, vpnObserver, handler);
        try (LiveMonitor live = new LiveMonitor()) {
            VpnNetworkMonitor.Selection first = live.awaitSelection(null, 20_000);
            NetworkCapabilities firstCaps = assertUsable(manager, first);
            Log.i("LegacyNetworkCallback", "Ready for externally controlled handoff from "
                    + first.network + "; keep this Network and the VPN alive");
            VpnNetworkMonitor.Selection second = live.awaitSelection(first.network, 120_000);
            NetworkCapabilities secondCaps = assertUsable(manager, second);
            boolean sameTransport = false;
            for (int transport : new int[]{NetworkCapabilities.TRANSPORT_WIFI,
                    NetworkCapabilities.TRANSPORT_CELLULAR,
                    NetworkCapabilities.TRANSPORT_ETHERNET,
                    NetworkCapabilities.TRANSPORT_BLUETOOTH}) {
                sameTransport |= firstCaps.hasTransport(transport)
                        && secondCaps.hasTransport(transport);
            }
            assertTrue("The replacement must exercise same-transport identity, not roaming.",
                    sameTransport);
            assertNotNull("Old Network must still be reported by the framework.",
                    manager.getNetworkCapabilities(first.network));
            assertNotNull(manager.getLinkProperties(first.network));
            assertEquals(vpn, manager.getActiveNetwork());
            assertFalse("The observed VPN must not be lost during handoff.", vpnLost.get());
            Log.i("LegacyNetworkCallback", "Real callback handoff " + first.network + " -> "
                    + second.network + "; egress and DNS-through-TUN proof remain separate");
        } finally {
            manager.unregisterNetworkCallback(vpnObserver);
        }
    }

    private static void requireOptIn(String name) {
        assumeTrue("Opt-in fixture: pass " + name + "=true.", "true".equals(
                InstrumentationRegistry.getArguments().getString(name)));
    }

    private static NetworkCapabilities assertUsable(ConnectivityManager manager,
                                                   VpnNetworkMonitor.Selection selection) {
        assertNotNull(selection);
        assertFalse(selection.dnsServers.isEmpty());
        // These synchronous checks run on the test thread, never inside a network callback.
        NetworkCapabilities caps = manager.getNetworkCapabilities(selection.network);
        assertNotNull("Selected real Network disappeared before inspection.", caps);
        assertTrue(caps.hasCapability(NET_CAPABILITY_INTERNET));
        assertTrue(caps.hasCapability(NET_CAPABILITY_NOT_VPN));
        assertFalse(caps.hasTransport(TRANSPORT_VPN));
        return caps;
    }

    private static final class LiveMonitor implements AutoCloseable {
        final ConnectivityManager manager;
        private final AtomicReference<VpnNetworkMonitor> monitor = new AtomicReference<>();
        private final LinkedBlockingQueue<VpnNetworkMonitor.Selection> selections =
                new LinkedBlockingQueue<>();

        LiveMonitor() {
            Context context = ApplicationProvider.getApplicationContext();
            manager = context.getSystemService(ConnectivityManager.class);
            assertNotNull(manager);
            onMain(() -> {
                VpnNetworkMonitor candidate = new VpnNetworkMonitor(() -> {
                    VpnNetworkMonitor.Selection selection = monitor.get().getSelection();
                    if (selection != null) selections.add(selection);
                });
                monitor.set(candidate);
                candidate.register(manager, new Handler(Looper.getMainLooper()));
            });
        }

        VpnNetworkMonitor.Selection awaitSelection(Network previous, long timeoutMs)
                throws InterruptedException {
            long deadline = SystemClock.elapsedRealtime() + timeoutMs;
            while (SystemClock.elapsedRealtime() < deadline) {
                VpnNetworkMonitor.Selection next = selections.poll(
                        Math.max(1, deadline - SystemClock.elapsedRealtime()), TimeUnit.MILLISECONDS);
                if (next == null || next.network.equals(previous)) continue;
                AtomicReference<VpnNetworkMonitor.Selection> current = new AtomicReference<>();
                onMain(() -> current.set(monitor.get().getSelection()));
                if (next.equals(current.get())) return next;
            }
            throw new AssertionError("No real ready replacement callback within " + timeoutMs
                    + " ms; do not replace this gate with synthetic Network callbacks.");
        }

        @Override
        public void close() {
            onMain(() -> monitor.get().unregister(manager));
        }
    }

    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }
}
