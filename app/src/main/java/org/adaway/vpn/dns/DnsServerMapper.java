package org.adaway.vpn.dns;

import static java.util.Collections.emptyList;

import android.content.Context;
import android.net.Network;
import android.net.VpnService;

import org.adaway.helper.PreferenceHelper;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import timber.log.Timber;

/**
 * This class is in charge of mapping DNS server addresses between network DNS and fake DNS.
 * <p>
 * Fake DNS addresses are registered as VPN interface DNS to capture DNS traffic.
 * Each original DNS server is directly mapped to one fake address.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class DnsServerMapper {
    /**
     * Known DNS-over-HTTPS (DoH) provider IPv4 addresses.
     * Routing these through the VPN forces TCP/443 connections to them to enter the tunnel
     * where they are dropped (DnsPacketProxy only handles UDP/53).
     * Clients that permit fallback can then use system UDP/53 DNS through the fake-DNS
     * mapping. Clients that require DoH may instead fail to resolve names.
     */
    private static final String[] DOH_PROVIDER_IPV4 = {
            "1.1.1.1", "1.0.0.1",              // Cloudflare
            "8.8.8.8", "8.8.4.4",              // Google
            "9.9.9.9", "149.112.112.112",       // Quad9
            "208.67.222.222", "208.67.220.220"  // OpenDNS
    };
    /**
     * Known DNS-over-HTTPS provider IPv6 addresses matching {@link #DOH_PROVIDER_IPV4}.
     */
    private static final String[] DOH_PROVIDER_IPV6 = {
            "2606:4700:4700::1111", "2606:4700:4700::1001", // Cloudflare
            "2001:4860:4860::8888", "2001:4860:4860::8844", // Google
            "2620:fe::fe", "2620:fe::9",                    // Quad9
            "2620:119:35::35", "2620:119:53::53"             // OpenDNS
    };
    /**
     * The TEST NET addresses blocks, defined in RFC5735.
     */
    private static final String[] TEST_NET_ADDRESS_BLOCKS = {
            "192.0.2.0/24", // TEST-NET-1
            "198.51.100.0/24", // TEST-NET-2
            "203.0.113.0/24" // TEST-NET-3
    };
    /**
     * This IPv6 address prefix for documentation, defined in RFC3849.
     */
    private static final String IPV6_ADDRESS_PREFIX_RESERVED_FOR_DOCUMENTATION = "2001:db8::/32";
    /**
     * VPN network IPv6 interface prefix length.
     */
    private static final int IPV6_PREFIX_LENGTH = 120;
    /**
     * The original DNS servers.
     */
    private final List<InetAddress> dnsServers;
    private final Map<InetAddress, InetAddress> dnsAliases;
    private final Network mNetwork;
    private final List<InetAddress> mNetworkDnsServers;

    /**
     * Constructor.
     */
    public DnsServerMapper() {
        this(null, emptyList());
    }

    public DnsServerMapper(Network network, List<InetAddress> dnsServers) {
        this.dnsServers = new ArrayList<>();
        this.dnsAliases = new HashMap<>();
        this.mNetwork = network;
        this.mNetworkDnsServers = Collections.unmodifiableList(new ArrayList<>(dnsServers));
    }

    /**
     * Configure the VPN.
     * <p>
     * Build fake DNS aliases from the selected upstream snapshot so tunnel routes and
     * bound DNS sockets use the same network.
     *
     * @param context The application context.
     * @param builder The builder of the VPN to configure.
     */
    public void configureVpn(Context context, VpnService.Builder builder) {
        configureVpn(builder, this.mNetworkDnsServers, PreferenceHelper.getEnableIpv6(context));
    }

    public void bindSocket(DatagramSocket socket) throws IOException {
        if (this.mNetwork == null) {
            throw new IOException("No upstream network selected for DNS.");
        }
        this.mNetwork.bindSocket(socket);
    }

    void configureVpn(VpnService.Builder builder, List<InetAddress> dnsServers,
                      boolean isIpv6Enabled) {
        // Configure tunnel network address
        Subnet ipv4Subnet = addIpv4Address(builder);
        Subnet ipv6Subnet = hasIpv6DnsServers(dnsServers, isIpv6Enabled)
                ? addIpv6Address(builder) : null;
        // Configure DNS mapping
        this.dnsServers.clear();
        this.dnsAliases.clear();
        for (InetAddress dnsServer : dnsServers) {
            Subnet subnetForDnsServer = dnsServer instanceof Inet4Address ? ipv4Subnet : ipv6Subnet;
            if (subnetForDnsServer == null) {
                continue;
            }
            this.dnsServers.add(dnsServer);
            int serverIndex = this.dnsServers.size();
            InetAddress dnsAddressAlias = subnetForDnsServer.getAddress(serverIndex);
            this.dnsAliases.put(dnsAddressAlias, dnsServer);
            Timber.i("Mapping DNS server %s as %s.", dnsServer, dnsAddressAlias);
            builder.addDnsServer(dnsAddressAlias);
            builder.addRoute(dnsAddressAlias, dnsServer instanceof Inet4Address ? 32 : 128);
        }
        if (this.dnsServers.isEmpty()) {
            throw new IllegalStateException("No usable upstream DNS servers found.");
        }
        // Provider reachability is independent of the upstream DNS address family.
        addDohBlockRoutes(builder, true);
    }

    /**
     * Route the providers documented at {@link #DOH_PROVIDER_IPV4} through the VPN tunnel.
     */
    private void addDohBlockRoutes(VpnService.Builder builder, boolean includeIpv6) {
        for (DohRoute route : commonDohBlockRoutes(includeIpv6)) {
            builder.addRoute(route.address, route.prefixLength);
            Timber.d("Added DoH block route for %s/%d.",
                    route.address.getHostAddress(), route.prefixLength);
        }
    }

    static List<DohRoute> commonDohBlockRoutes(boolean includeIpv6) {
        List<DohRoute> routes = new ArrayList<>(DOH_PROVIDER_IPV4.length
                + (includeIpv6 ? DOH_PROVIDER_IPV6.length : 0));
        addDohRoutes(routes, DOH_PROVIDER_IPV4, 32);
        if (includeIpv6) {
            addDohRoutes(routes, DOH_PROVIDER_IPV6, 128);
        }
        return routes;
    }

    private static void addDohRoutes(List<DohRoute> routes, String[] addresses, int prefixLength) {
        for (String address : addresses) {
            routes.add(DohRoute.from(address, prefixLength));
        }
    }

    static final class DohRoute {
        final InetAddress address;
        final int prefixLength;

        private DohRoute(InetAddress address, int prefixLength) {
            this.address = address;
            this.prefixLength = prefixLength;
        }

        static DohRoute from(String address, int prefixLength) {
            try {
                return new DohRoute(InetAddress.getByName(address), prefixLength);
            } catch (UnknownHostException exception) {
                throw new IllegalStateException("Invalid hardcoded DoH route: " + address,
                        exception);
            }
        }
    }

    public InetAddress getDefaultDnsServerAddress() {
        if (this.dnsServers.isEmpty()) {
            try {
                return InetAddress.getByName("1.1.1.1");
            } catch (UnknownHostException e) {
                throw new IllegalStateException("Failed to parse hardcoded DNS IP address.", e);
            }
        }
        // Return last DNS server added
        return this.dnsServers.get(this.dnsServers.size() - 1);
    }

    /**
     * Get the original DNS server address from fake DNS server address.
     *
     * @param fakeDnsAddress The fake DNS address to get the original DNS server address.
     * @return The original DNS server address, wrapped into an {@link Optional} or {@link Optional#empty()} if it does not exists.
     */
    Optional<InetAddress> getDnsServerFromFakeAddress(InetAddress fakeDnsAddress) {
        return Optional.ofNullable(this.dnsAliases.get(fakeDnsAddress));
    }

    /**
     * Add IPv4 network address to the VPN.
     *
     * @param builder The build of the VPN to configure.
     * @return The IPv4 address of the VPN network.
     */
    private Subnet addIpv4Address(VpnService.Builder builder) {
        for (String addressBlock : TEST_NET_ADDRESS_BLOCKS) {
            try {
                Subnet subnet = Subnet.parse(addressBlock);
                InetAddress address = subnet.getAddress(0);
                builder.addAddress(address, subnet.prefixLength);
                Timber.d("Set %s as IPv4 network address to tunnel interface.", address);
                return subnet;
            } catch (IllegalArgumentException e) {
                Timber.w(e, "Failed to add %s network address to tunnel interface.", addressBlock);
            }
        }
        throw new IllegalStateException("Failed to add any IPv4 address for TEST-NET to tunnel interface.");
    }

    /**
     * Add IPv6 network address to the VPN.
     *
     * @param builder The build of the VPN to configure.
     * @return The IPv4 address of the VPN network.
     */
    private Subnet addIpv6Address(VpnService.Builder builder) {
        Subnet subnet = Subnet.parse(IPV6_ADDRESS_PREFIX_RESERVED_FOR_DOCUMENTATION);
        builder.addAddress(subnet.address, IPV6_PREFIX_LENGTH);
        Timber.d("Set %s as IPv6 network address to tunnel interface.", subnet.address);
        return subnet;
    }


    private boolean hasIpv6DnsServers(Collection<InetAddress> dnsServers, boolean isIpv6Enabled) {
        boolean hasIpv6Server = dnsServers.stream()
                .anyMatch(server -> server instanceof Inet6Address);
        return shouldUseIpv6(dnsServers, isIpv6Enabled) && hasIpv6Server;
    }

    static boolean shouldUseIpv6(Collection<InetAddress> dnsServers, boolean isIpv6Enabled) {
        boolean hasIpv4Server = dnsServers.stream().anyMatch(Inet4Address.class::isInstance);
        return isIpv6Enabled || !hasIpv4Server;
    }
}
