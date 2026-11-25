package org.adaway.vpn;

import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static org.adaway.vpn.VpnNetworkMonitorInstrumentedTest.available;
import static org.adaway.vpn.VpnNetworkMonitorInstrumentedTest.link;
import static org.adaway.vpn.VpnNetworkMonitorInstrumentedTest.network;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.Network;
import android.os.Handler;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class VpnServiceNetworkCallbackInstrumentedTest {
    @Test
    public void callbackBurstRebuildsOnceWithLatestResolverSnapshot() throws Exception {
        RecordingService service = createService();
        try {
            onMain(() -> {
                requested(service, true);
                VpnNetworkMonitor monitor = monitor(service);
                Network wifi = network(100);
                available(monitor, wifi, TRANSPORT_WIFI, "192.0.2.53");
                monitor.onLinkPropertiesChanged(wifi, link("198.51.100.53"));
                monitor.onLinkPropertiesChanged(wifi, link("198.51.100.53"));
            });
            assertEquals("reconnect", service.mTransitions.poll(2, TimeUnit.SECONDS));
            onMain(() -> {
                assertEquals(1, service.mReconnects);
                assertEquals(0, service.mWaits);
                assertEquals(InetAddress.getByName("198.51.100.53"),
                        monitor(service).getSelection().dnsServers.get(0));
            });
        } finally {
            cleanup(service);
        }
    }

    @Test
    public void queuedAndLateCallbacksCannotRestartAfterIntentionalStop() throws Exception {
        RecordingService service = createService();
        CountDownLatch drained = new CountDownLatch(1);
        try {
            onMain(() -> {
                requested(service, true);
                VpnNetworkMonitor monitor = monitor(service);
                Network wifi = network(100);
                available(monitor, wifi, TRANSPORT_WIFI, "192.0.2.53");
                // Leave the queued runnable in place to exercise its desired-state guard.
                requested(service, false);
                monitor.onLinkPropertiesChanged(wifi, link("198.51.100.53"));
                monitor.onLost(wifi);
                available(monitor, network(101), TRANSPORT_WIFI, "203.0.113.53");
                handler(service).postDelayed(drained::countDown, 200);
            });
            assertTrue("Service handler did not drain", drained.await(2, TimeUnit.SECONDS));
            onMain(() -> {
                assertEquals(0, service.mReconnects);
                assertEquals(0, service.mWaits);
                assertNull(field("vpnWorker").get(service));
            });
        } finally {
            cleanup(service);
        }
    }

    @Test
    public void overlappingNetworkLossWaitsOnlyAfterFinalUsableNetwork() throws Exception {
        RecordingService service = createService();
        Network second = network(101);
        try {
            onMain(() -> {
                requested(service, true);
                VpnNetworkMonitor monitor = monitor(service);
                Network first = network(100);
                available(monitor, first, TRANSPORT_WIFI, "192.0.2.53");
                available(monitor, second, TRANSPORT_WIFI, "192.0.2.54");
                monitor.onLost(first);
            });
            assertEquals("reconnect", service.mTransitions.poll(2, TimeUnit.SECONDS));
            onMain(() -> {
                assertEquals(second, monitor(service).getSelection().network);
                assertEquals(0, service.mWaits);
                monitor(service).onLost(second);
                monitor(service).onLost(second);
            });
            assertEquals("wait", service.mTransitions.poll(2, TimeUnit.SECONDS));
            onMain(() -> {
                assertEquals(1, service.mReconnects);
                assertEquals(1, service.mWaits);
            });
        } finally {
            cleanup(service);
        }
    }

    private static RecordingService createService() {
        AtomicReference<RecordingService> result = new AtomicReference<>();
        onMain(() -> result.set(new RecordingService()));
        return result.get();
    }

    private static void cleanup(RecordingService service) {
        onMain(() -> {
            requested(service, false);
            monitor(service).close();
            handler(service).removeCallbacksAndMessages(null);
        });
    }

    private static void requested(VpnService service, boolean requested) throws Exception {
        field("vpnRequested").setBoolean(service, requested);
    }

    private static Handler handler(VpnService service) throws Exception {
        return (Handler) field("handler").get(service);
    }

    private static VpnNetworkMonitor monitor(VpnService service) throws Exception {
        return (VpnNetworkMonitor) field("networkMonitor").get(service);
    }

    private static Field field(String name) throws Exception {
        Field field = VpnService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void onMain(CheckedRunnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                action.run();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
    }

    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private static final class RecordingService extends VpnService {
        private final LinkedBlockingQueue<String> mTransitions = new LinkedBlockingQueue<>();
        private int mReconnects;
        private int mWaits;

        @Override
        void reconnect() {
            mReconnects++;
            mTransitions.add("reconnect");
        }

        @Override
        void waitForNetVpn() {
            mWaits++;
            mTransitions.add("wait");
        }
    }
}
