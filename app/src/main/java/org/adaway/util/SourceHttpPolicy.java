package org.adaway.util;

import com.google.common.net.InetAddresses;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Network boundary for remote filter sources. Content-provider documents never use this client. */
public final class SourceHttpPolicy {
    private static final int MAX_REDIRECTS = 20;
    private static final Pattern NUMERIC_HOST = Pattern.compile("[0-9.]+");
    // Special-use destinations are not general Internet source hosts. Keep public anycast
    // exceptions explicit, and classify the embedded IPv4 destination on standard DNS64.
    private static final Prefix[] NON_PUBLIC_V4 = prefixes("0.0.0.0/8", "10.0.0.0/8",
            "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16", "172.16.0.0/12",
            "192.0.0.0/24", "192.0.2.0/24", "192.88.99.0/24", "192.168.0.0/16",
            "198.18.0.0/15", "198.51.100.0/24", "203.0.113.0/24", "224.0.0.0/3");
    private static final Prefix GLOBAL_V6 = new Prefix("2000::/3");
    private static final Prefix NAT64 = new Prefix("64:ff9b::/96");
    private static final Prefix[] NON_PUBLIC_V6 = prefixes("2001::/23", "2001:db8::/32",
            "2002::/16", "3fff::/20");
    private static final Prefix[] PUBLIC_SPECIAL = prefixes("192.0.0.9/32", "192.0.0.10/32",
            "2001:1::1/128", "2001:1::2/128", "2001:1::3/128", "2001:3::/32", "2001:4:112::/48");

    private SourceHttpPolicy() {}

    public static OkHttpClient.Builder newClientBuilder() {
        return newClientBuilder(Dns.SYSTEM, ProxySelector::getDefault);
    }

    static OkHttpClient.Builder newClientBuilder(Dns resolver, Supplier<ProxySelector> proxies) {
        return new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .proxy(Proxy.NO_PROXY)
                .dns(host -> {
                    List<InetAddress> answers = new ArrayList<>(resolver.lookup(host));
                    if (answers.isEmpty() || answers.stream().anyMatch(a -> !isPublic(a.getAddress()))) {
                        throw new UnknownHostException("Source destination is not public");
                    }
                    // These are the route addresses, not a separate DNS preflight.
                    return Collections.unmodifiableList(answers);
                })
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    for (int redirects = 0; ; redirects++) {
                        HttpUrl url = request.url();
                        if (!allowedUrl(url)) throw new IOException("A source requires public HTTPS");
                        requireDirectRoute(url, proxies);
                        Response response = chain.proceed(request);
                        String location = response.header("Location");
                        if (!response.isRedirect() || location == null) return response;
                        response.close();
                        HttpUrl next = url.resolve(location);
                        if (next == null || redirects >= MAX_REDIRECTS) {
                            throw new IOException("Invalid or excessive source redirects");
                        }
                        Request.Builder follow = request.newBuilder().url(next);
                        if (!url.host().equals(next.host()) || url.port() != next.port()) {
                            follow.removeHeader("Authorization").removeHeader("Proxy-Authorization")
                                    .removeHeader("Cookie").removeHeader("Host");
                        }
                        // The source clients issue only GET and HEAD. Keep method and validators.
                        request = follow.build();
                    }
                });
    }

    private static void requireDirectRoute(HttpUrl url, Supplier<ProxySelector> proxies) throws IOException {
        try {
            ProxySelector selector = proxies.get();
            if (selector == null) return;
            // CONNECT/SOCKS can resolve the origin remotely. Do not override a proxy-first route.
            List<Proxy> routes = selector.select(url.uri());
            if (routes == null || routes.isEmpty() || !Proxy.NO_PROXY.equals(routes.get(0))) {
                throw new IOException("Cannot verify the source destination through this proxy");
            }
        } catch (RuntimeException e) {
            throw new IOException("Cannot determine the proxy policy for this source", e);
        }
    }

    /** Syntactic admission only; a hostname must still pass the client's route DNS check. */
    public static boolean isRemoteUrl(String value) {
        if (value == null || !value.regionMatches(true, 0, "https://", 0, 8)) return false;
        HttpUrl url = HttpUrl.parse(value);
        return url != null && allowedUrl(url);
    }

    private static boolean allowedUrl(HttpUrl url) {
        if (!url.isHttps()) return false;
        String host = url.host();
        String name = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        if (name.isEmpty() || name.equals("localhost") || name.endsWith(".localhost")
                || name.equals("local") || name.endsWith(".local")
                || name.equals("home.arpa") || name.endsWith(".home.arpa")) return false;
        // OkHttp can route literals without invoking its Dns. Reject ambiguous numeric forms.
        if (InetAddresses.isInetAddress(host)) return isPublic(InetAddresses.forString(host).getAddress());
        return host.indexOf(':') < 0 && !NUMERIC_HOST.matcher(host).matches();
    }

    static boolean isPublic(byte[] address) {
        if (matches(PUBLIC_SPECIAL, address)) return true;
        if (address.length == 4) return !matches(NON_PUBLIC_V4, address);
        if (address.length != 16) return false;
        if (NAT64.contains(address)) return isPublic(Arrays.copyOfRange(address, 12, 16));
        return GLOBAL_V6.contains(address) && !matches(NON_PUBLIC_V6, address);
    }

    private static boolean matches(Prefix[] prefixes, byte[] address) {
        for (Prefix prefix : prefixes) if (prefix.contains(address)) return true;
        return false;
    }

    private static Prefix[] prefixes(String... values) {
        return Arrays.stream(values).map(Prefix::new).toArray(Prefix[]::new);
    }

    private static final class Prefix {
        final byte[] network;
        final int bits;

        Prefix(String cidr) {
            int slash = cidr.indexOf('/');
            network = InetAddresses.forString(cidr.substring(0, slash)).getAddress();
            bits = Integer.parseInt(cidr.substring(slash + 1));
        }

        boolean contains(byte[] address) {
            if (address.length != network.length) return false;
            for (int i = 0; i * 8 < bits; i++) {
                int mask = 0xff << Math.max(0, (i + 1) * 8 - bits);
                if (((address[i] ^ network[i]) & mask) != 0) return false;
            }
            return true;
        }
    }
}
