package org.adaway.vpn.worker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class VpnWatchdogTest {
    @Test
    public void sendPacketProtectsSocketBeforeSendingProbe() throws Exception {
        TestableVpnWatchdog watchdog = new TestableVpnWatchdog(socket -> {
            ((RecordingDatagramSocket) socket).protectedByVpnService = true;
            return true;
        });
        watchdog.initialize(true);
        watchdog.setTarget(InetAddress.getLoopbackAddress());

        watchdog.sendPacket();

        assertTrue(watchdog.socket.protectedByVpnService);
        assertTrue(watchdog.socket.sent);
        assertFalse(watchdog.socket.sentBeforeProtection);
    }

    @Test
    public void sendPacketFailsClosedWhenSocketProtectionFails() throws Exception {
        TestableVpnWatchdog watchdog = new TestableVpnWatchdog(socket -> false);
        watchdog.initialize(true);
        watchdog.setTarget(InetAddress.getLoopbackAddress());

        try {
            watchdog.sendPacket();
            fail("Expected watchdog probe to fail when VPN socket protection fails.");
        } catch (VpnNetworkException expected) {
            assertFalse(watchdog.socket.sent);
        }
    }

    private static final class TestableVpnWatchdog extends VpnWatchdog {
        private final RecordingDatagramSocket socket;

        private TestableVpnWatchdog(SocketProtector socketProtector) throws SocketException {
            super(socketProtector);
            this.socket = new RecordingDatagramSocket();
        }

        @Override
        DatagramSocket newDatagramSocket() {
            return this.socket;
        }
    }

    private static final class RecordingDatagramSocket extends DatagramSocket {
        private boolean protectedByVpnService;
        private boolean sent;
        private boolean sentBeforeProtection;

        private RecordingDatagramSocket() throws SocketException {
            super();
        }

        @Override
        public void send(DatagramPacket packet) throws IOException {
            this.sent = true;
            this.sentBeforeProtection = !this.protectedByVpnService;
        }
    }
}
