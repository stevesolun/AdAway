package org.adaway.vpn.dns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.List;

public class DnsServerMapperDohRoutesTest {
    @Test
    public void commonDohBlockRoutes_ipv4OnlyContainsCommonProviderHostRoutes() {
        List<DnsServerMapper.DohRoute> routes = DnsServerMapper.commonDohBlockRoutes(false);

        assertEquals(8, routes.size());
        assertRoute(routes, "1.1.1.1", 32);
        assertRoute(routes, "1.0.0.1", 32);
        assertRoute(routes, "8.8.8.8", 32);
        assertRoute(routes, "8.8.4.4", 32);
        assertRoute(routes, "9.9.9.9", 32);
        assertRoute(routes, "149.112.112.112", 32);
        assertRoute(routes, "208.67.222.222", 32);
        assertRoute(routes, "208.67.220.220", 32);
        assertTrue(routes.stream().allMatch(route -> route.address instanceof Inet4Address));
        assertFalse(routes.stream().anyMatch(route -> route.address instanceof Inet6Address));
    }

    @Test
    public void commonDohBlockRoutes_withIpv6AddsCommonIpv6HostRoutes() {
        List<DnsServerMapper.DohRoute> routes = DnsServerMapper.commonDohBlockRoutes(true);

        assertEquals(16, routes.size());
        assertRoute(routes, "2606:4700:4700:0:0:0:0:1111", 128);
        assertRoute(routes, "2606:4700:4700:0:0:0:0:1001", 128);
        assertRoute(routes, "2001:4860:4860:0:0:0:0:8888", 128);
        assertRoute(routes, "2001:4860:4860:0:0:0:0:8844", 128);
        assertRoute(routes, "2620:fe:0:0:0:0:0:fe", 128);
        assertRoute(routes, "2620:fe:0:0:0:0:0:9", 128);
        assertRoute(routes, "2620:119:35:0:0:0:0:35", 128);
        assertRoute(routes, "2620:119:53:0:0:0:0:53", 128);
        assertEquals(8, routes.stream()
                .filter(route -> route.address instanceof Inet6Address)
                .count());
    }

    private static void assertRoute(
            List<DnsServerMapper.DohRoute> routes,
            String hostAddress,
            int prefixLength) {
        assertTrue(hostAddress + "/" + prefixLength,
                routes.stream().anyMatch(route ->
                        hostAddress.equals(route.address.getHostAddress())
                                && route.prefixLength == prefixLength));
    }
}
