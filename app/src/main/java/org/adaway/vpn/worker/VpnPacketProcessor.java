/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.vpn.worker;

import org.pcap4j.packet.IpPacket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Queue;

import timber.log.Timber;

final class VpnPacketProcessor {
    private final Queue<byte[]> deviceWrites;
    private final DnsHandler dnsHandler;
    private final PacketMonitor packetMonitor;

    VpnPacketProcessor(
            Queue<byte[]> deviceWrites, DnsHandler dnsHandler, PacketMonitor packetMonitor) {
        this.deviceWrites = deviceWrites;
        this.dnsHandler = dnsHandler;
        this.packetMonitor = packetMonitor;
    }

    boolean hasDeviceWrites() {
        return !this.deviceWrites.isEmpty();
    }

    void writeToDevice(OutputStream outputStream) throws IOException {
        Timber.d("Write to device %d packets.", this.deviceWrites.size());
        try {
            while (!this.deviceWrites.isEmpty()) {
                byte[] ipPacketData = this.deviceWrites.poll();
                outputStream.write(ipPacketData);
            }
        } catch (IOException e) {
            throw new IOException("Failed to write to tunnel output stream.", e);
        }
    }

    int readPacketFromDevice(InputStream inputStream, byte[] packet)
            throws IOException {
        Timber.d("Read a packet from device.");
        int length = inputStream.read(packet);
        if (length < 0) {
            Timber.d("Tunnel input stream closed.");
        } else if (length == 0) {
            Timber.d("Read empty packet from tunnel.");
        } else {
            byte[] readPacket = Arrays.copyOf(packet, length);
            this.packetMonitor.handlePacket(readPacket);
            this.dnsHandler.handleDnsRequest(readPacket);
        }
        return length;
    }

    void queueDeviceWrite(IpPacket ipOutPacket) {
        byte[] rawData = ipOutPacket.getRawData();
        if (rawData != null) {
            this.deviceWrites.add(rawData);
        }
    }

    interface DnsHandler {
        void handleDnsRequest(byte[] packetData) throws IOException;
    }

    interface PacketMonitor {
        void handlePacket(byte[] packetData);
    }
}
