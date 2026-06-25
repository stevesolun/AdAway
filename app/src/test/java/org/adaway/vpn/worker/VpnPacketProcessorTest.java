/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.vpn.worker;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class VpnPacketProcessorTest {
    @Test
    public void readPacketFromDeviceForwardsOnlyReadBytesToDnsHandlerAndMonitor()
            throws Exception {
        byte[] packetData = new byte[]{1, 2, 3, 4};
        byte[] packetBuffer = new byte[16];
        RecordingDnsHandler dnsHandler = new RecordingDnsHandler();
        RecordingPacketMonitor packetMonitor = new RecordingPacketMonitor();
        VpnPacketProcessor processor = new VpnPacketProcessor(
                new ConcurrentLinkedQueue<>(),
                dnsHandler,
                packetMonitor);

        int length = processor.readPacketFromDevice(
                new ByteArrayInputStream(packetData),
                packetBuffer);

        assertEquals(packetData.length, length);
        assertArrayEquals(packetData, dnsHandler.packetData);
        assertArrayEquals(packetData, packetMonitor.packetData);
    }

    @Test
    public void readPacketFromClosedDeviceDoesNotForwardToDnsHandler() throws Exception {
        RecordingDnsHandler dnsHandler = new RecordingDnsHandler();
        RecordingPacketMonitor packetMonitor = new RecordingPacketMonitor();
        VpnPacketProcessor processor = new VpnPacketProcessor(
                new ConcurrentLinkedQueue<>(),
                dnsHandler,
                packetMonitor);

        int length = processor.readPacketFromDevice(
                new ByteArrayInputStream(new byte[0]),
                new byte[16]);

        assertEquals(-1, length);
        assertEquals(0, dnsHandler.calls);
        assertEquals(0, packetMonitor.calls);
    }

    @Test
    public void writeToDeviceDrainsQueuedPacketsInOrder() throws Exception {
        Queue<byte[]> deviceWrites = new ConcurrentLinkedQueue<>();
        deviceWrites.add(new byte[]{1, 2});
        deviceWrites.add(new byte[]{3, 4, 5});
        VpnPacketProcessor processor = new VpnPacketProcessor(
                deviceWrites,
                new RecordingDnsHandler(),
                new RecordingPacketMonitor());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        processor.writeToDevice(outputStream);

        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, outputStream.toByteArray());
        assertEquals(0, deviceWrites.size());
    }

    private static final class RecordingDnsHandler implements VpnPacketProcessor.DnsHandler {
        private int calls;
        private byte[] packetData;

        @Override
        public void handleDnsRequest(byte[] packetData) throws IOException {
            this.calls++;
            this.packetData = Arrays.copyOf(packetData, packetData.length);
        }
    }

    private static final class RecordingPacketMonitor
            implements VpnPacketProcessor.PacketMonitor {
        private int calls;
        private byte[] packetData;

        @Override
        public void handlePacket(byte[] packetData) {
            this.calls++;
            this.packetData = Arrays.copyOf(packetData, packetData.length);
        }
    }
}
