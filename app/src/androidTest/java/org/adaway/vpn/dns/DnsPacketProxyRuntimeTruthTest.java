package org.adaway.vpn.dns;

import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.RuleKind.SUFFIX;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.adaway.AdAwayApplication;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.IpV4Rfc791Tos;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.UdpPacket;
import org.pcap4j.packet.UnknownPacket;
import org.pcap4j.packet.namednumber.IpNumber;
import org.pcap4j.packet.namednumber.IpV4TosPrecedence;
import org.pcap4j.packet.namednumber.IpVersion;
import org.pcap4j.packet.namednumber.UdpPort;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Optional;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class DnsPacketProxyRuntimeTruthTest {
    private static final int TEST_SOURCE_ID = 909091;
    private static final String TEST_HOST_PATTERN = "%dns-proxy-runtime%.invalid";

    private AdAwayApplication application;
    private AppDatabase database;
    private HostEntryDao hostEntryDao;
    private HostListItemDao hostListItemDao;
    private HostsSourceDao hostsSourceDao;
    private AdBlockMethod originalMethod;
    private String suffixHost;
    private String childHost;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        application = (AdAwayApplication) context.getApplicationContext();
        originalMethod = PreferenceHelper.getAdBlockMethod(application);
        PreferenceHelper.setAbBlockMethod(application, VPN);
        database = AppDatabase.getInstance(application);
        hostEntryDao = database.hostEntryDao();
        hostListItemDao = database.hostsListItemDao();
        hostsSourceDao = database.hostsSourceDao();
        long uniqueSuffix = System.nanoTime();
        suffixHost = "dns-proxy-runtime-" + uniqueSuffix + ".invalid";
        childHost = "ads." + suffixHost;
        cleanup();
        insertSource();
    }

    @After
    public void tearDown() {
        cleanup();
        PreferenceHelper.setAbBlockMethod(application, originalMethod);
    }

    @Test
    public void dnsRequestForChildDomain_isBlockedByRuntimeSuffixRule() throws Exception {
        insertSuffixBlockedHost(suffixHost);
        hostEntryDao.sync();
        application.invalidateVpnRulesCache();
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        DnsPacketProxy proxy = new DnsPacketProxy(
                eventLoop,
                new FixedDnsServerMapper(InetAddress.getByName("8.8.8.8")));
        proxy.initialize(application);

        proxy.handleDnsRequest(buildDnsQueryPacket(childHost));

        assertEquals(0, eventLoop.forwardedPackets);
        assertNotNull(eventLoop.devicePacket);
        Message response = readDnsResponse(eventLoop.devicePacket);
        assertTrue(response.getHeader().getFlag(Flags.QR));
        assertEquals(Rcode.NOERROR, response.getRcode());
        assertEquals(0, response.getSectionArray(Section.ANSWER).length);
        assertEquals(1, response.getSectionArray(Section.AUTHORITY).length);
    }

    private void insertSource() {
        HostsSource source = new HostsSource();
        source.setId(TEST_SOURCE_ID);
        source.setLabel("DNS proxy runtime truth test");
        source.setUrl("https://example.invalid/dns-proxy-runtime-hosts.txt");
        source.setEnabled(true);
        hostsSourceDao.insert(source);
    }

    private void insertSuffixBlockedHost(String host) {
        HostListItem item = new HostListItem();
        item.setHost(host);
        item.setType(BLOCKED);
        item.setKind(SUFFIX);
        item.setEnabled(true);
        item.setSourceId(TEST_SOURCE_ID);
        item.setGeneration(hostEntryDao.getActiveGeneration());
        hostListItemDao.insert(item);
    }

    private void cleanup() {
        hostListItemDao.clearSourceHosts(TEST_SOURCE_ID);
        database.getOpenHelper().getWritableDatabase().execSQL(
                "DELETE FROM hosts_lists WHERE host LIKE ?",
                new Object[]{TEST_HOST_PATTERN});
        hostsSourceDao.getById(TEST_SOURCE_ID).ifPresent(hostsSourceDao::delete);
        hostEntryDao.sync();
        application.invalidateVpnRulesCache();
    }

    private static byte[] buildDnsQueryPacket(String host) throws Exception {
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

    private static Message readDnsResponse(IpPacket packet) throws IOException {
        UdpPacket udpPacket = (UdpPacket) packet.getPayload();
        Packet payload = udpPacket.getPayload();
        assertNotNull(payload);
        return new Message(payload.getRawData());
    }

    private static final class FixedDnsServerMapper extends DnsServerMapper {
        private final InetAddress dnsServer;

        private FixedDnsServerMapper(InetAddress dnsServer) {
            this.dnsServer = dnsServer;
        }

        @Override
        Optional<InetAddress> getDnsServerFromFakeAddress(InetAddress fakeDnsAddress) {
            return Optional.of(dnsServer);
        }
    }

    private static final class RecordingEventLoop implements DnsPacketProxy.EventLoop {
        private int forwardedPackets;
        private IpPacket devicePacket;

        @Override
        public void forwardPacket(DatagramPacket packet) {
            forwardedPackets++;
        }

        @Override
        public void forwardPacket(DatagramPacket packet, Consumer<byte[]> callback) {
            forwardedPackets++;
        }

        @Override
        public void queueDeviceWrite(IpPacket packet) {
            devicePacket = packet;
        }
    }
}
