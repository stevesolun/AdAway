package org.adaway.util;

import com.google.common.net.InetAddresses;

import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.SourceType;
import org.adaway.model.source.FilterListCompatibility;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.SocketFactory;

import okhttp3.Dns;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.*;

/** Only synthetic DNS and transport doubles; no live endpoints, servers or outbound connections. */
public class SourceHttpPolicyTest {
    private static final InetAddress V4 = InetAddresses.forString("93.184.216.34");
    private static final InetAddress V6 = InetAddresses.forString("2606:4700::1111");
    private static final String START = "https://source.test/list";

    @Test
    public void pickerDocumentsAreLocalAndRemoteAdmissionIsConsistent() {
        HostsSource local = new HostsSource();
        local.setUrl("content://documents.test/document/selected");
        assertTrue(HostsSource.isValidUrl(local.getUrl()));
        assertEquals(SourceType.FILE, local.getType());
        assertFalse(FilterListCompatibility.isUsableDownloadUrl(local.getUrl()));
        for (String url : Arrays.asList(START, "https://[2606:4700::1111]/list")) {
            assertTrue(HostsSource.isValidUrl(url));
            assertTrue(FilterListCompatibility.isUsableDownloadUrl(url));
        }
        for (String url : Arrays.asList("http://source.test/list", "https://192.0.2.1/list",
                "https://[2001:db8::1]/list", "https://localhost./list", "https://source.local/list",
                "https://source.home.arpa/list", "file:///list", "https://")) {
            assertFalse(HostsSource.isValidUrl(url));
            assertFalse(FilterListCompatibility.isUsableDownloadUrl(url));
        }
    }

    @Test
    public void addressesAreClassifiedWithoutResolvingNames() {
        for (String literal : Arrays.asList("0.0.0.0", "10.0.0.1", "100.64.0.1", "127.0.0.1",
                "169.254.0.1", "172.16.0.1", "192.0.0.1", "192.168.1.1", "198.18.1.1",
                "198.51.100.1", "203.0.113.1", "224.0.0.1", "255.255.255.255", "::", "::1",
                "fc00::1", "fe80::1", "ff02::1", "2001:db8::1", "3fff::1", "2002::1",
                "64:ff9b::c000:0201", "::ffff:192.0.2.1")) {
            assertFalse(literal, SourceHttpPolicy.isPublic(InetAddresses.forString(literal).getAddress()));
        }
        for (String literal : Arrays.asList("93.184.216.34", "2606:4700::1111", "192.0.0.9",
                "64:ff9b::5db8:d822")) {
            assertTrue(literal, SourceHttpPolicy.isPublic(InetAddresses.forString(literal).getAddress()));
        }
    }

    @Test
    public void routeDnsReturnsOnlyCheckedAnswersAndRejectsMixedOrChangedAnswers() throws Exception {
        List<InetAddress> good = Arrays.asList(V4, V6);
        List<InetAddress> mixed = Arrays.asList(V4, InetAddresses.forString("fd00::1"));
        AtomicInteger calls = new AtomicInteger();
        Dns delegate = host -> {
            int i = calls.getAndIncrement();
            return i == 0 ? good : i == 1 ? mixed : Collections.emptyList();
        };
        OkHttpClient client = SourceHttpPolicy.newClientBuilder(delegate, () -> null).build();
        assertEquals(Proxy.NO_PROXY, client.proxy());
        assertFalse(client.followRedirects());
        assertFalse(client.followSslRedirects());
        assertEquals(good, client.dns().lookup("source.test"));
        assertThrows(UnknownHostException.class, () -> client.dns().lookup("source.test"));
        assertThrows(UnknownHostException.class, () -> client.dns().lookup("source.test"));
        assertEquals(3, calls.get());
    }

    @Test
    public void realRoutePlannerConsumesGuardedDnsOnTheRedirectedHop() {
        InetAddress denied = InetAddresses.forString("192.0.2.17");
        for (InetAddress answer : Arrays.asList(denied, V4, V6)) {
            RecordingSocketFactory sockets = new RecordingSocketFactory();
            List<String> lookups = new ArrayList<>();
            OkHttpClient client = SourceHttpPolicy.newClientBuilder(host -> {
                lookups.add(host);
                return Collections.singletonList(answer);
            }, () -> null).socketFactory(sockets).retryOnConnectionFailure(false)
                    .addInterceptor(chain -> chain.request().url().host().equals("source.test")
                            ? response(chain.request(), 302, "https://next.test/list")
                            : chain.proceed(chain.request())).build();
            assertThrows(IOException.class, () -> client.newCall(new Request.Builder().url(START).build()).execute());
            assertEquals(Collections.singletonList("next.test"), lookups);
            if (answer.equals(denied)) {
                assertEquals(0, sockets.creations);
                assertTrue(sockets.destinations.isEmpty());
            } else {
                assertEquals(1, sockets.creations);
                assertEquals(Collections.singletonList(new InetSocketAddress(answer, 443)), sockets.destinations);
            }
        }
    }

    @Test
    public void publicRedirectsPreserveGetHeadAndConditionalHeaders() throws Exception {
        for (String method : Arrays.asList("GET", "HEAD")) {
            List<Request> seen = new ArrayList<>();
            OkHttpClient client = fakeClient(null, chain -> {
                Request r = chain.request();
                seen.add(r);
                if (seen.size() == 1) return response(r, 301, "/next");
                if (seen.size() == 2) return response(r, 307, "https://cdn.test/final");
                return response(r, 304, null);
            });
            Request request = new Request.Builder().url(START).method(method, null)
                    .header("If-None-Match", "revision-a").header("If-Modified-Since", "date")
                    .header("Authorization", "fixture").build();
            try (Response result = client.newCall(request).execute()) {
                assertEquals(304, result.code());
            }
            assertEquals(3, seen.size());
            assertEquals("https://source.test/next", seen.get(1).url().toString());
            assertEquals("https://cdn.test/final", seen.get(2).url().toString());
            assertNull(seen.get(2).header("Authorization"));
            for (Request r : seen) {
                assertEquals(method, r.method());
                assertEquals("revision-a", r.header("If-None-Match"));
                assertEquals("date", r.header("If-Modified-Since"));
            }
        }
    }

    @Test
    public void deniedInitialUrlOrRedirectNeverReachesTheFakeTransport() {
        for (String method : Arrays.asList("GET", "HEAD")) {
            for (String target : Arrays.asList("http://other.test/list", "https://192.0.2.1/list",
                    "https://[2001:db8::1]/list")) {
                AtomicInteger sends = new AtomicInteger();
                OkHttpClient client = fakeClient(null, chain -> {
                    sends.incrementAndGet();
                    return response(chain.request(), 302, target);
                });
                Request direct = new Request.Builder().url(target).method(method, null).build();
                assertThrows(IOException.class, () -> client.newCall(direct).execute());
                assertEquals(0, sends.get());
                Request redirected = direct.newBuilder().url(START).build();
                assertThrows(IOException.class, () -> client.newCall(redirected).execute());
                assertEquals(1, sends.get());
            }
        }
    }

    @Test
    public void redirectCyclesAreBounded() {
        AtomicInteger sends = new AtomicInteger();
        OkHttpClient client = fakeClient(null, chain -> {
            sends.incrementAndGet();
            return response(chain.request(), 302, START);
        });
        assertThrows(IOException.class, () -> client.newCall(new Request.Builder().url(START).build()).execute());
        assertEquals(21, sends.get());
    }

    @Test
    public void proxyRequirementOnRedirectFailsWithoutSendingOrSilentlyGoingDirect() {
        ProxySelector proxy = new ProxySelector() {
            public List<Proxy> select(URI uri) {
                if (uri.getHost().equals("source.test")) return Collections.singletonList(Proxy.NO_PROXY);
                return Arrays.asList(new Proxy(Proxy.Type.HTTP,
                        InetSocketAddress.createUnresolved("proxy.test", 8080)), Proxy.NO_PROXY);
            }
            public void connectFailed(URI uri, SocketAddress sa, IOException e) { throw new AssertionError(); }
        };
        AtomicInteger sends = new AtomicInteger();
        OkHttpClient client = fakeClient(proxy, chain -> {
            sends.incrementAndGet();
            return response(chain.request(), 302, "https://cdn.test/list");
        });
        assertThrows(IOException.class, () -> client.newCall(new Request.Builder().url(START).build()).execute());
        assertEquals(1, sends.get());
    }

    @Test
    public void socksRequiredAtFirstHopDoesNotContactProxyOrOrigin() {
        ProxySelector socks = new ProxySelector() {
            public List<Proxy> select(URI uri) {
                return Collections.singletonList(new Proxy(Proxy.Type.SOCKS,
                        InetSocketAddress.createUnresolved("proxy.test", 1080)));
            }
            public void connectFailed(URI uri, SocketAddress sa, IOException e) { throw new AssertionError(); }
        };
        OkHttpClient client = fakeClient(socks, chain -> { throw new AssertionError("Unexpected transport"); });
        assertThrows(IOException.class, () -> client.newCall(new Request.Builder().url(START).build()).execute());
    }

    private static OkHttpClient fakeClient(ProxySelector proxy, Interceptor terminal) {
        return SourceHttpPolicy.newClientBuilder(host -> { throw new AssertionError("Unexpected DNS"); },
                () -> proxy).addInterceptor(terminal).build();
    }

    private static Response response(Request r, int code, String location) {
        Response.Builder response = new Response.Builder().request(r).code(code).message("fixture")
                .protocol(Protocol.HTTP_1_1).body(ResponseBody.create("", null));
        if (location != null) response.header("Location", location);
        return response.build();
    }

    private static final class RecordingSocketFactory extends SocketFactory {
        final List<SocketAddress> destinations = new ArrayList<>();
        int creations;

        @Override public Socket createSocket() {
            creations++;
            return new Socket(Proxy.NO_PROXY) {
                @Override public void connect(SocketAddress address, int timeout) throws IOException {
                    destinations.add(address);
                    throw new IOException("Stopped by transport double before any network I/O");
                }
            };
        }

        @Override public Socket createSocket(String h, int p) { throw new AssertionError(); }
        @Override public Socket createSocket(String h, int p, InetAddress l, int lp) { throw new AssertionError(); }
        @Override public Socket createSocket(InetAddress a, int p) { throw new AssertionError(); }
        @Override public Socket createSocket(InetAddress a, int p, InetAddress l, int lp) { throw new AssertionError(); }
    }
}
