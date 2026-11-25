package org.adaway.vpn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilderFactory;

/** Source/manifest wiring guards, not substitutes for service or network runtime tests. */
public class LegacyVpnNetworkContractTest {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String SERVICE = "app/src/main/java/org/adaway/vpn/VpnService.java";

    @Test
    public void changeNetworkPermissionIsLegacyBoundedAndExistingDeletePermissionRemains()
            throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        NodeList permissions = factory.newDocumentBuilder().parse(new InputSource(new StringReader(
                read("app/src/main/AndroidManifest.xml")))).getElementsByTagName("uses-permission");
        int changeCount = 0;
        Element delete = null;
        for (int index = 0; index < permissions.getLength(); index++) {
            Element permission = (Element) permissions.item(index);
            String name = permission.getAttributeNS(ANDROID_NS, "name");
            if ("android.permission.CHANGE_NETWORK_STATE".equals(name)) {
                changeCount++;
                assertEquals("30", permission.getAttributeNS(ANDROID_NS, "maxSdkVersion"));
            }
            if ("android.permission.REQUEST_DELETE_PACKAGES".equals(name)) delete = permission;
        }
        assertEquals(1, changeCount);
        assertNotNull(delete);
        assertTrue(read("app/build.gradle").contains("minSdk = 26"));
    }

    @Test
    public void creationIsPassiveAndRequestedStartEntersForegroundBeforeRegistration()
            throws Exception {
        String source = read(SERVICE);
        String create = between(source, "public void onCreate()", "public int onStartCommand");
        assertFalse(create.contains("registerNetworkCallback()"));
        String start = between(source, "private void startVpn()", "private void stopVpn()");
        assertOrdered(start, "this.vpnRequested = true", "updateVpnStatus(STARTING)",
                "registerNetworkCallback()", "startWorker()");
        assertOrdered(start, "if (!updateVpnStatus(STARTING))",
                "throw new IllegalStateException", "registerNetworkCallback()");
        String status = between(source, "private boolean updateVpnStatus(VpnStatus status)",
                "private Notification getNotification");
        assertOrdered(status, "catch (Exception e)", "return false;", "return true;");
        assertTrue(start.contains("catch (RuntimeException exception)"));
        assertTrue(start.contains("stopVpn();"));
    }

    @Test
    public void explicitStopAndDestructionRetireCallbacksBeforeOtherTeardown() throws Exception {
        String source = read(SERVICE);
        String stop = between(source, "private void stopVpn()", "void waitForNetVpn()");
        assertOrdered(stop, "this.vpnRequested = false", "unregisterNetworkCallback()",
                "stopWorker()", "stopSelf()");
        String destroy = between(source, "public void onDestroy()", "public void onRevoke()");
        assertOrdered(destroy, "this.vpnRequested = false", "unregisterNetworkCallback()",
                "stopWorker()", "super.onDestroy()");
        String wait = between(source, "void waitForNetVpn()", "private void startWorker()");
        assertFalse("WAIT/reconnect must not release the request.",
                wait.contains("unregisterNetworkCallback"));
    }

    @Test
    public void freshMonitorPerStartAndAllWorkerIdentityGuardsRemain() throws Exception {
        String source = read(SERVICE);
        String register = between(source, "private void registerNetworkCallback()",
                "private void unregisterNetworkCallback()");
        assertOrdered(register, "if (this.networkCallbacksRegistered) return",
                "this.networkMonitor.close()", "new VpnNetworkMonitor(",
                "this.networkMonitor.register(", "this.networkCallbacksRegistered = true");
        assertTrue(source.contains("&& this.vpnRequested && message.obj == this.vpnWorker"));
        String dispatch = between(source, "void applyNetworkChange()", "private static class MyHandler");
        assertOrdered(dispatch, "if (!this.vpnRequested)", "return;", "getSelection()");
    }

    @Test
    public void underlyingMetadataAndDnsBindingUseTheSameWorkerSnapshot() throws Exception {
        String worker = read("app/src/main/java/org/adaway/vpn/worker/VpnWorker.java");
        assertTrue(worker.contains("private final Network upstreamNetwork;"));
        assertTrue(worker.contains("this.upstreamNetwork = network;"));
        assertTrue(worker.contains("new DnsServerMapper(network, dnsServers)"));
        assertTrue(worker.contains("this.vpnService, this.dnsServerMapper, this.upstreamNetwork"));
        assertFalse(worker.contains("this.vpnService.setUnderlyingNetworks("));
        String builder = read("app/src/main/java/org/adaway/vpn/worker/VpnBuilder.java");
        assertOrdered(builder, "builder.setUnderlyingNetworks(",
                "new Network[]{requireNonNull(upstreamNetwork)}", ".establish()");
    }

    private static void assertOrdered(String source, String... tokens) {
        int previous = -1;
        for (String token : tokens) {
            int position = source.indexOf(token, previous + 1);
            assertTrue("Missing/out-of-order token: " + token, position > previous);
            previous = position;
        }
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + 1);
        assertTrue("Missing source boundaries", from >= 0 && to > from);
        return source.substring(from, to);
    }

    private static String read(String relativePath) throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        if (root.getFileName().toString().equals("app")) root = root.getParent();
        return new String(Files.readAllBytes(root.resolve(relativePath)), StandardCharsets.UTF_8);
    }
}
