package org.adaway.vpn.dns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.VpnService;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class DnsServerMapperConfigurationInstrumentedTest {
    private static final String IPV4_DNS = "192.0.2.53";
    private static final String IPV6_DNS = "2001:db8::53";
    private static final String[] PROVIDERS = {
            "1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4",
            "9.9.9.9", "149.112.112.112", "208.67.222.222", "208.67.220.220",
            "2606:4700:4700::1111", "2606:4700:4700::1001",
            "2001:4860:4860::8888", "2001:4860:4860::8844",
            "2620:fe::fe", "2620:fe::9", "2620:119:35::35", "2620:119:53::53"
    };

    @Test
    public void ipv4DnsWithIpv6EnabledStillRoutesIpv6Providers() throws Exception {
        assertConfiguration(true, addresses(IPV4_DNS), addresses(IPV4_DNS));
    }

    @Test
    public void ipv4DnsWithIpv6DisabledStillRoutesIpv6Providers() throws Exception {
        assertConfiguration(false, addresses(IPV4_DNS), addresses(IPV4_DNS));
    }

    @Test
    public void mixedDnsWithIpv6EnabledMapsBothFamilies() throws Exception {
        assertConfiguration(true, addresses(IPV4_DNS, IPV6_DNS),
                addresses(IPV4_DNS, IPV6_DNS));
    }

    @Test
    public void mixedDnsWithIpv6DisabledOmitsOnlyIpv6Aliases() throws Exception {
        assertConfiguration(false, addresses(IPV4_DNS, IPV6_DNS), addresses(IPV4_DNS));
    }

    @Test
    public void ipv6OnlyDnsWithIpv6EnabledMapsEveryResolver() throws Exception {
        List<InetAddress> servers = addresses(IPV6_DNS, "2001:db8::54");
        assertConfiguration(true, servers, servers);
    }

    @Test
    public void ipv6OnlyDnsWithIpv6DisabledRetainsEveryResolver() throws Exception {
        List<InetAddress> servers = addresses(IPV6_DNS, "2001:db8::54");
        assertConfiguration(false, servers, servers);
    }

    @Test(expected = IllegalStateException.class)
    public void noResolverStillRejectsTunnelConfiguration() {
        new DnsServerMapper().configureVpn(new RecordingBuilder(), Collections.emptyList(), false);
    }

    @Test
    public void reconfigurationReplacesMappingsAndRemovesOldFamilyAliases() throws Exception {
        DnsServerMapper mapper = new DnsServerMapper();
        RecordingBuilder original = new RecordingBuilder();
        mapper.configureVpn(original, addresses(IPV4_DNS, IPV6_DNS), true);
        InetAddress retiredAlias = original.mDns.get(1);

        RecordingBuilder replacement = new RecordingBuilder();
        InetAddress newServer = address("198.51.100.53");
        mapper.configureVpn(replacement, Collections.singletonList(newServer), false);

        assertEquals(1, replacement.mDns.size());
        assertEquals(newServer, mapper.getDnsServerFromFakeAddress(replacement.mDns.get(0)).get());
        assertEquals(newServer, mapper.getDefaultDnsServerAddress());
        assertFalse(mapper.getDnsServerFromFakeAddress(retiredAlias).isPresent());
        assertProviderRoutes(replacement);
    }

    @Test
    public void publicConfigurationUsesDefensiveDnsSnapshotInsteadOfRediscovery() throws Exception {
        List<InetAddress> servers = addresses(IPV4_DNS);
        DnsServerMapper mapper = new DnsServerMapper(null, servers);
        servers.clear();
        servers.add(address("198.51.100.53"));
        RecordingBuilder builder = new RecordingBuilder();

        mapper.configureVpn(InstrumentationRegistry.getInstrumentation().getTargetContext(), builder);

        assertEquals(1, builder.mDns.size());
        assertEquals(address(IPV4_DNS), mapper.getDnsServerFromFakeAddress(builder.mDns.get(0)).get());
        assertProviderRoutes(builder);
    }

    @Test(expected = IOException.class)
    public void missingSelectedNetworkCannotFallBackToUnboundSocket() throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            new DnsServerMapper().bindSocket(socket);
        }
    }

    private static void assertConfiguration(boolean ipv6Enabled, List<InetAddress> servers,
                                            List<InetAddress> expectedServers) throws Exception {
        DnsServerMapper mapper = new DnsServerMapper();
        RecordingBuilder builder = new RecordingBuilder();
        mapper.configureVpn(builder, servers, ipv6Enabled);

        assertEquals(expectedServers.size(), builder.mDns.size());
        for (int index = 0; index < expectedServers.size(); index++) {
            InetAddress alias = builder.mDns.get(index);
            InetAddress upstream = expectedServers.get(index);
            assertEquals(upstream, mapper.getDnsServerFromFakeAddress(alias).get());
            assertEquals(upstream instanceof Inet4Address, alias instanceof Inet4Address);
            assertEquals(Integer.valueOf(alias instanceof Inet4Address ? 32 : 128),
                    builder.mRoutes.get(alias));
        }
        assertEquals(expectedServers.get(expectedServers.size() - 1),
                mapper.getDefaultDnsServerAddress());
        boolean ipv6Alias = expectedServers.stream().anyMatch(Inet6Address.class::isInstance);
        assertEquals(ipv6Alias,
                builder.mAddresses.stream().anyMatch(Inet6Address.class::isInstance));
        assertEquals(16 + expectedServers.size(), builder.mRoutes.size());
        assertProviderRoutes(builder);
    }

    private static void assertProviderRoutes(RecordingBuilder builder) throws Exception {
        for (String provider : PROVIDERS) {
            InetAddress destination = address(provider);
            assertEquals(provider, Integer.valueOf(destination instanceof Inet4Address ? 32 : 128),
                    builder.mRoutes.get(destination));
        }
        // Only host routes: unrelated IPv6 traffic must keep its underlying-network path.
        assertTrue(builder.mRoutes.entrySet().stream().allMatch(route -> route.getValue()
                == (route.getKey() instanceof Inet4Address ? 32 : 128)));
        InetAddress ordinaryIpv6 = address("2001:db8:1234::1");
        assertFalse(builder.mRoutes.containsKey(ordinaryIpv6));
    }

    private static List<InetAddress> addresses(String... values) throws UnknownHostException {
        List<InetAddress> result = new ArrayList<>();
        for (String value : values) {
            result.add(address(value));
        }
        return result;
    }

    private static InetAddress address(String value) throws UnknownHostException {
        return InetAddress.getByName(value);
    }

    private static final class RecordingBuilder extends VpnService.Builder {
        private final List<InetAddress> mAddresses = new ArrayList<>();
        private final List<InetAddress> mDns = new ArrayList<>();
        private final Map<InetAddress, Integer> mRoutes = new HashMap<>();

        RecordingBuilder() {
            this(new VpnService());
        }

        private RecordingBuilder(VpnService service) {
            service.super();
        }

        @Override
        public VpnService.Builder addAddress(InetAddress address, int prefixLength) {
            mAddresses.add(address);
            return super.addAddress(address, prefixLength);
        }

        @Override
        public VpnService.Builder addDnsServer(InetAddress address) {
            mDns.add(address);
            return super.addDnsServer(address);
        }

        @Override
        public VpnService.Builder addRoute(InetAddress address, int prefixLength) {
            mRoutes.put(address, prefixLength);
            return super.addRoute(address, prefixLength);
        }
    }
}
