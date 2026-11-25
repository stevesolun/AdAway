package org.adaway.vpn.worker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class VpnWorkerNetworkBindingTest {
    @Test
    public void socketIsProtectedThenBoundBeforeConnectingToResolver() throws Exception {
        List<String> steps = new ArrayList<>();
        DatagramPacket packet = packet();
        try (DatagramSocket socket = VpnWorker.openDnsSocket(packet, candidate -> {
            assertFalse(candidate.isConnected());
            steps.add("protect");
            return true;
        }, candidate -> {
            assertFalse(candidate.isConnected());
            steps.add("bind");
        })) {
            assertEquals(Arrays.asList("protect", "bind"), steps);
            assertTrue(socket.isConnected());
            assertEquals(packet.getAddress(), socket.getInetAddress());
            assertEquals(packet.getPort(), socket.getPort());
        }
    }

    @Test
    public void bindingFailureClosesSocketWithoutConnectingOrFallingBack() throws Exception {
        AtomicReference<DatagramSocket> captured = new AtomicReference<>();
        try {
            VpnWorker.openDnsSocket(packet(), socket -> true, socket -> {
                captured.set(socket);
                assertFalse(socket.isConnected());
                throw new IOException("Network disappeared");
            });
            fail("Binding failure must fail closed.");
        } catch (IOException expected) {
            assertEquals("Network disappeared", expected.getMessage());
            assertTrue(captured.get().isClosed());
            assertFalse(captured.get().isConnected());
        }
    }

    @Test
    public void protectionFailureNeverAttemptsBinding() throws Exception {
        AtomicReference<DatagramSocket> captured = new AtomicReference<>();
        try {
            VpnWorker.openDnsSocket(packet(), socket -> {
                captured.set(socket);
                return false;
            }, socket -> fail("Unprotected socket must not reach binding."));
            fail("Protection failure must fail closed.");
        } catch (IOException expected) {
            assertTrue(captured.get().isClosed());
        }
    }

    @Test
    public void runtimeBindingFailureAlsoClosesSocket() throws Exception {
        AtomicReference<DatagramSocket> captured = new AtomicReference<>();
        try {
            VpnWorker.openDnsSocket(packet(), socket -> true, socket -> {
                captured.set(socket);
                throw new SecurityException("Network inaccessible");
            });
            fail("Runtime binding failure must fail closed.");
        } catch (SecurityException expected) {
            assertTrue(captured.get().isClosed());
        }
    }

    private static DatagramPacket packet() throws Exception {
        return new DatagramPacket(new byte[0], 0, InetAddress.getByName("127.0.0.1"), 53);
    }
}
