package org.adaway.vpn;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.adaway.vpn.VpnStatus.RUNNING;
import static org.adaway.vpn.VpnStatus.STOPPED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;

import org.adaway.AdAwayApplication;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.SOARecord;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

import java.io.File;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Opt-in connected proof: adawayLiveVpnSmoke=true and pre-granted VPN consent are required.
 * Add adawayLiveNetworkSmoke=true to also exercise upstream example.com resolution.
 * Add adawayLiveHebrewSmoke=true for the enabled Hebrew feed and site DNS probes.
 * An optional evidenceDir captures actual UDP responses in app external files.
 */
@RunWith(AndroidJUnit4.class)
public class VpnDnsTunnelInstrumentedTest {
    private static final String CANARY_ADDRESS = "203.0.113.42";
    private static final String HEBREW_SOURCE_URL =
            "https://raw.githubusercontent.com/easylist/EasyListHebrew/master/hosts.txt";
    private static final long LIFECYCLE_TIMEOUT_MS = 20_000L;
    private static final int SOCKET_TIMEOUT_MS = 5_000;
    private static final long POLL_INTERVAL_MS = 100L;

    private AdAwayApplication mApplication;
    private ConnectivityManager mConnectivityManager;
    private AppDatabase mDatabase;
    private AdBlockMethod mOriginalMethod;
    private HostListItem mBlockedRule;
    private HostListItem mRedirectedRule;
    private boolean mRulesInserted;
    private boolean mStartAttempted;
    private ConnectivityManager.NetworkCallback mDefaultNetworkCallback;
    private volatile Network mDefaultNetworkWithLinkProperties;

    @Test(timeout = 90_000L)
    public void udpDnsTraversesPreparedVpnTunnel() throws Exception {
        assumeTrue("Live VPN smoke is opt-in: pass adawayLiveVpnSmoke=true.",
                argumentEnabled("adawayLiveVpnSmoke"));
        mApplication = ApplicationProvider.getApplicationContext();
        mConnectivityManager =
                (ConnectivityManager) mApplication.getSystemService(CONNECTIVITY_SERVICE);
        assertNotNull(mConnectivityManager);
        assumeFalse("Do not take ownership of an existing VPN.", hasAnyVpnNetwork());
        assumeTrue("Do not interrupt an existing AdAway VPN lifecycle.",
                PreferenceHelper.getVpnServiceStatus(mApplication) == STOPPED);
        assumeTrue("AdAway VPN consent must already be prepared on this device.",
                android.net.VpnService.prepare(mApplication) == null);

        mDatabase = AppDatabase.getInstance(mApplication);
        assumeTrue("The app's USER source must already exist.",
                mDatabase.hostsSourceDao().getById(USER_SOURCE_ID).isPresent());
        if (argumentEnabled("adawayLiveHebrewSmoke")) {
            assertTrue("Hebrew site probes require explicit adawayLiveNetworkSmoke=true.",
                    argumentEnabled("adawayLiveNetworkSmoke"));
            assertTrue("The real Hebrew hosts feed must already be enabled.",
                    mDatabase.hostsSourceDao().getEnabled().stream()
                            .anyMatch(source -> HEBREW_SOURCE_URL.equals(source.getUrl())));
            assertSelectedSourceBlocks("totalmedia2.ynet.co.il");
            assertSelectedSourceBlocks("pagead2.googlesyndication.com");
        }
        mOriginalMethod = PreferenceHelper.getAdBlockMethod(mApplication);
        PreferenceHelper.setAbBlockMethod(mApplication, VPN);

        String suffix = UUID.randomUUID() + ".vpn-smoke.invalid";
        mBlockedRule = rule("blocked." + suffix, BLOCKED, null);
        mRedirectedRule = rule("canary." + suffix, REDIRECTED, CANARY_ADDRESS);
        mDatabase.runInTransaction(() -> {
            insertRule(mBlockedRule);
            insertRule(mRedirectedRule);
        });
        mRulesInserted = true;
        mApplication.getSourceModel().syncHostEntries();

        observeDefaultNetwork();
        // Claim cleanup before starting: even a failed launch may have started the service.
        mStartAttempted = true;
        assertTrue("VPN service start command must be accepted.",
                VpnServiceControls.start(mApplication));
        InetAddress alias = waitForTunnelDnsAlias();

        Message blocked = query(alias, mBlockedRule.getHost());
        assertBlockedResponse(blocked);

        Message redirected = query(alias, mRedirectedRule.getHost());
        assertEquals(Rcode.NOERROR, redirected.getRcode());
        Record[] answers = redirected.getSectionArray(Section.ANSWER);
        assertEquals(1, answers.length);
        assertTrue(answers[0] instanceof ARecord);
        assertEquals(redirected.getQuestion().getName(), answers[0].getName());
        assertEquals(InetAddress.getByName(CANARY_ADDRESS),
                ((ARecord) answers[0]).getAddress());

        if (argumentEnabled("adawayLiveNetworkSmoke")) {
            Message resolved = query(alias, "example.com");
            assertEquals(Rcode.NOERROR, resolved.getRcode());
            assertTrue("Upstream example.com resolution must return an IPv4 answer.",
                    Arrays.stream(resolved.getSectionArray(Section.ANSWER))
                            .anyMatch(answer -> answer instanceof ARecord));
        }
        if (argumentEnabled("adawayLiveHebrewSmoke")) {
            for (String host : new String[]{"totalmedia2.ynet.co.il",
                    "pagead2.googlesyndication.com"}) {
                assertBlockedResponse(query(alias, host));
            }
            for (String host : new String[]{"www.ynet.co.il", "www.walla.co.il", "ksp.co.il"}) {
                Message resolved = query(alias, host);
                assertEquals("Upstream DNS response for " + host,
                        Rcode.NOERROR, resolved.getRcode());
                assertTrue("Upstream " + host + " resolution must return an IPv4 answer.",
                        Arrays.stream(resolved.getSectionArray(Section.ANSWER))
                                .anyMatch(answer -> answer instanceof ARecord));
            }
        }
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (mStartAttempted) {
                try {
                    VpnServiceControls.stop(mApplication);
                    waitForStopped();
                } finally {
                    WorkManager.getInstance(mApplication).cancelUniqueWork("vpnHeartbeat")
                            .getResult().get(5, TimeUnit.SECONDS);
                }
            }
        } finally {
            try {
                if (mRulesInserted) {
                    mDatabase.runInTransaction(() -> {
                        deleteInsertedRule(mBlockedRule);
                        deleteInsertedRule(mRedirectedRule);
                    });
                    mApplication.getSourceModel().syncHostEntries();
                }
            } finally {
                try {
                    if (mOriginalMethod != null) {
                        PreferenceHelper.setAbBlockMethod(mApplication, mOriginalMethod);
                    }
                } finally {
                    if (mDefaultNetworkCallback != null) {
                        mConnectivityManager.unregisterNetworkCallback(mDefaultNetworkCallback);
                    }
                }
            }
        }
    }

    private static boolean argumentEnabled(String name) {
        return "true".equals(InstrumentationRegistry.getArguments().getString(name));
    }

    private static HostListItem rule(String host, ListType type, String redirection) {
        HostListItem item = new HostListItem();
        item.setHost(host);
        item.setType(type);
        item.setEnabled(true);
        item.setRedirection(redirection);
        item.setSourceId(USER_SOURCE_ID);
        return item;
    }

    private void insertRule(HostListItem item) {
        assertFalse("A smoke rule must not replace an existing USER rule.",
                mDatabase.hostsListItemDao().getHostId(item.getHost()).isPresent());
        mDatabase.hostsListItemDao().insert(item);
        item.setId(mDatabase.hostsListItemDao().getHostId(item.getHost()).get());
    }

    private void deleteInsertedRule(HostListItem item) {
        // Match both identity and unique hostname; never clear the USER source or other rules.
        mDatabase.getOpenHelper().getWritableDatabase().execSQL(
                "DELETE FROM hosts_lists WHERE id = ? AND source_id = ? AND host = ?",
                new Object[]{item.getId(), USER_SOURCE_ID, item.getHost()});
    }

    private InetAddress waitForTunnelDnsAlias() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LIFECYCLE_TIMEOUT_MS);
        do {
            if (PreferenceHelper.getVpnServiceStatus(mApplication) == RUNNING) {
                for (Network network : mConnectivityManager.getAllNetworks()) {
                    NetworkCapabilities capabilities =
                            mConnectivityManager.getNetworkCapabilities(network);
                    if (capabilities == null || !capabilities.hasTransport(TRANSPORT_VPN)) {
                        continue;
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            && capabilities.getOwnerUid() != mApplication.getApplicationInfo().uid) {
                        continue;
                    }
                    // Synchronous network snapshots can precede Android's route installation.
                    // Wait for the default network's link callback before opening a socket,
                    // otherwise it can retain the physical source address and miss TUN.
                    if (!network.equals(mDefaultNetworkWithLinkProperties)
                            || !network.equals(mConnectivityManager.getActiveNetwork())) {
                        continue;
                    }
                    LinkProperties link = mConnectivityManager.getLinkProperties(network);
                    if (link == null || link.getInterfaceName() == null
                            || !link.getInterfaceName().matches("tun[0-9]+")
                            || link.getDnsServers().isEmpty()) {
                        continue;
                    }
                    NetworkInterface tun = NetworkInterface.getByName(link.getInterfaceName());
                    if (tun == null || !tun.isUp()) {
                        continue;
                    }
                    InetAddress alias = link.getDnsServers().get(0);
                    assertTrue("VPN DNS alias must have a route through the tunnel.",
                            link.getRoutes().stream()
                                    .anyMatch(route -> route.getDestination().contains(alias)));
                    assertTrue(VpnServiceControls.isTunnelEstablished(mApplication));
                    return alias;
                }
            }
            Thread.sleep(POLL_INTERVAL_MS);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Timed out waiting for RUNNING and tun DNS link properties; "
                + "last status: " + PreferenceHelper.getVpnServiceStatus(mApplication));
    }

    private void observeDefaultNetwork() {
        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                mDefaultNetworkWithLinkProperties = null;
                recordNetworkEvent("default available=" + network);
            }

            @Override
            public void onLinkPropertiesChanged(Network network, LinkProperties link) {
                mDefaultNetworkWithLinkProperties = network;
                recordNetworkEvent("default link=" + network + " " + link);
            }

            @Override
            public void onLost(Network network) {
                if (network.equals(mDefaultNetworkWithLinkProperties)) {
                    mDefaultNetworkWithLinkProperties = null;
                }
                recordNetworkEvent("default lost=" + network);
            }
        };
        mConnectivityManager.registerDefaultNetworkCallback(callback);
        mDefaultNetworkCallback = callback;
    }

    private static void recordNetworkEvent(String event) {
        System.out.println("VPN DNS elapsedMs=" + SystemClock.elapsedRealtime() + " " + event);
    }

    private void assertSelectedSourceBlocks(String host) {
        try (Cursor rows = mDatabase.getOpenHelper().getReadableDatabase().query(
                "SELECT 1 FROM hosts_lists AS rules JOIN hosts_sources AS source "
                        + "ON source.id = rules.source_id WHERE rules.host = ? "
                        + "AND rules.type = 0 AND rules.enabled = 1 AND source.enabled = 1 "
                        + "AND source.id != 1 AND rules.generation = "
                        + "(SELECT active_generation FROM hosts_meta WHERE id = 0) LIMIT 1",
                new Object[]{host})) {
            assertTrue("A real selected source must supply the blocked rule for " + host,
                    rows.moveToFirst());
        }
    }

    private static void assertBlockedResponse(Message blocked) throws Exception {
        assertEquals("Blocked DNS must be NODATA, not NXDOMAIN: " + blocked.getQuestion(),
                Rcode.NOERROR, blocked.getRcode());
        assertEquals(0, blocked.getSectionArray(Section.ANSWER).length);
        Record[] authority = blocked.getSectionArray(Section.AUTHORITY);
        assertEquals(1, authority.length);
        assertTrue("Blocked DNS must carry the negative-cache SOA.",
                authority[0] instanceof SOARecord);
        assertEquals(Name.fromString("adaway.vpn.invalid."), authority[0].getName());
    }

    private Message query(InetAddress alias, String host) throws Exception {
        Message request = Message.newQuery(
                Record.newRecord(Name.fromString(host + "."), Type.A, DClass.IN));
        byte[] wire = request.toWire();
        // An ordinary app socket follows Android's VPN routes; no proxy/worker invocation.
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            socket.connect(alias, 53);
            recordNetworkEvent("probe host=" + host + " local=" + socket.getLocalSocketAddress()
                    + " remote=" + socket.getRemoteSocketAddress()
                    + " active=" + mConnectivityManager.getActiveNetwork()
                    + " callbackReady=" + mDefaultNetworkWithLinkProperties);
            socket.send(new DatagramPacket(wire, wire.length));
            recordNetworkEvent("sent host=" + host);
            byte[] buffer = new byte[4096];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            Message response = new Message(Arrays.copyOfRange(packet.getData(),
                    packet.getOffset(), packet.getOffset() + packet.getLength()));
            recordDnsResponse(alias, host, response);
            assertTrue(response.getHeader().getFlag(Flags.QR));
            assertFalse(response.getHeader().getFlag(Flags.TC));
            assertEquals(request.getHeader().getID(), response.getHeader().getID());
            assertEquals(request.getHeader().getOpcode(), response.getHeader().getOpcode());
            assertEquals(1, response.getHeader().getCount(Section.QUESTION));
            assertEquals(request.getQuestion(), response.getQuestion());
            return response;
        }
    }

    private void recordDnsResponse(InetAddress alias, String host, Message response) throws Exception {
        String directoryName = InstrumentationRegistry.getArguments().getString("evidenceDir");
        if (directoryName == null || directoryName.isEmpty()) return;
        assertTrue("evidenceDir must be a single directory name",
                directoryName.matches("[A-Za-z0-9_-][A-Za-z0-9._-]*"));
        File externalFiles = mApplication.getExternalFilesDir(null);
        assertNotNull("External files directory is required for DNS evidence", externalFiles);
        File directory = new File(externalFiles, directoryName);
        assertTrue("Cannot create DNS evidence directory",
                directory.isDirectory() || directory.mkdirs());
        File target = new File(directory, "dns-" + host + ".txt");
        String transcript = "Actual UDP A response through the AdAway VPN tunnel\n"
                + "DNS alias: " + alias.getHostAddress() + ":53\n"
                + "Host: " + host + "\n"
                + "Probe: " + (host.endsWith(".vpn-smoke.invalid")
                ? "temporary USER canary" : "public domain with current selected sources") + "\n\n"
                + response + "\n";
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(transcript.getBytes(StandardCharsets.UTF_8));
        }
        System.out.println("DNS evidence: " + target.getAbsolutePath());
    }

    private boolean hasAnyVpnNetwork() {
        for (Network network : mConnectivityManager.getAllNetworks()) {
            NetworkCapabilities capabilities =
                    mConnectivityManager.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(TRANSPORT_VPN)) {
                return true;
            }
        }
        return false;
    }

    private void waitForStopped() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LIFECYCLE_TIMEOUT_MS);
        do {
            if (PreferenceHelper.getVpnServiceStatus(mApplication) == STOPPED
                    && !hasAnyVpnNetwork()) {
                return;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Timed out stopping the smoke-test VPN and removing its tunnel.");
    }
}
