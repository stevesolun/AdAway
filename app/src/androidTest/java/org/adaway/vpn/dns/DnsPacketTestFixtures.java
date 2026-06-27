package org.adaway.vpn.dns;

import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.IpV4Rfc791Tos;
import org.pcap4j.packet.UdpPacket;
import org.pcap4j.packet.UnknownPacket;
import org.pcap4j.packet.namednumber.IpNumber;
import org.pcap4j.packet.namednumber.IpV4TosPrecedence;
import org.pcap4j.packet.namednumber.IpVersion;
import org.pcap4j.packet.namednumber.UdpPort;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Optional;

public final class DnsPacketTestFixtures {
    private DnsPacketTestFixtures() {
    }

    public static byte[] buildIpv4DnsQueryPacket(String host) throws Exception {
        Inet4Address clientAddress = (Inet4Address) InetAddress.getByName("192.0.2.10");
        Inet4Address fakeDnsAddress = (Inet4Address) InetAddress.getByName("192.0.2.2");
        Message query = Message.newQuery(
                Record.newRecord(Name.fromString(host + "."), Type.A, DClass.IN));
        UdpPacket.Builder udpBuilder = new UdpPacket.Builder()
                .srcPort(UdpPort.getInstance((short) 43210))
                .dstPort(UdpPort.DOMAIN)
                .srcAddr(clientAddress)
                .dstAddr(fakeDnsAddress)
                .correctChecksumAtBuild(true)
                .correctLengthAtBuild(true)
                .payloadBuilder(new UnknownPacket.Builder().rawData(query.toWire()));
        return new IpV4Packet.Builder()
                .version(IpVersion.IPV4)
                .tos(new IpV4Rfc791Tos.Builder()
                        .precedence(IpV4TosPrecedence.ROUTINE)
                        .build())
                .identification((short) 100)
                .ttl((byte) 64)
                .protocol(IpNumber.UDP)
                .srcAddr(clientAddress)
                .dstAddr(fakeDnsAddress)
                .payloadBuilder(udpBuilder)
                .correctChecksumAtBuild(true)
                .correctLengthAtBuild(true)
                .build()
                .getRawData();
    }

    public static DnsPacketProxy newFixedServerProxy(
            DnsPacketProxy.EventLoop eventLoop,
            InetAddress dnsServer) {
        return new DnsPacketProxy(eventLoop, new FixedDnsServerMapper(dnsServer));
    }

    private static final class FixedDnsServerMapper extends DnsServerMapper {
        private final InetAddress dnsServer;

        private FixedDnsServerMapper(InetAddress dnsServer) {
            this.dnsServer = dnsServer;
        }

        @Override
        Optional<InetAddress> getDnsServerFromFakeAddress(InetAddress fakeDnsAddress) {
            return Optional.of(this.dnsServer);
        }
    }
}
