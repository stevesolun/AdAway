package org.adaway.vpn.worker;

import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.RuleKind.EXACT;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.adaway.AdAwayApplication;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostEntry;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.vpn.VpnModel;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.log.LogActivity;
import org.adaway.util.AppExecutors;
import org.adaway.vpn.dns.DnsPacketProxy;
import org.adaway.vpn.dns.DnsPacketTestFixtures;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.pcap4j.packet.IpPacket;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class VpnPacketProcessorRuntimeTruthTest {
    private static final int TIMEOUT_MS = 10_000;
    private static final int TEST_SOURCE_ID = 979797;
    private static final String LOG_HOST = "vpn-packet-log-runtime-ci.invalid";

    private Context context;
    private AdAwayApplication application;
    private AppDatabase database;
    private HostListItemDao hostListItemDao;
    private HostEntryDao hostEntryDao;
    private HostsSourceDao hostsSourceDao;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(
                context,
                "set up VPN packet processor runtime truth");
        waitForDiskIoIdle();
        application = (AdAwayApplication) context.getApplicationContext();
        PreferenceHelper.setAbBlockMethod(context, VPN);
        database = AppDatabase.getInstance(application);
        hostListItemDao = database.hostsListItemDao();
        hostEntryDao = database.hostEntryDao();
        hostsSourceDao = database.hostsSourceDao();
        cleanup();
        insertSource();
        insertBlockedHost();
        hostsSourceDao.updateActiveRuleStats(TEST_SOURCE_ID);
        hostEntryDao.sync();
        application.invalidateVpnRulesCache();
    }

    @After
    public void tearDown() {
        finishResumedActivities();
        if (application != null) {
            AdBlockModel model = application.getAdBlockModel();
            model.setRecordingLogs(false);
            model.clearLogs();
        }
        cleanup();
        if (hostEntryDao != null) {
            hostEntryDao.sync();
        }
        if (context != null) {
            InstrumentedTestState.resetForPassiveRootUi(
                    context,
                    "tear down VPN packet processor runtime truth");
        }
    }

    @Test(timeout = 120_000)
    public void dnsPacketThroughProcessorRecordsVpnLogAndAppearsInLogUi()
            throws Exception {
        AdBlockModel model = application.getAdBlockModel();
        assertTrue("Expected VPN model after selecting VPN mode.", model instanceof VpnModel);
        assertFixtureHostIsBlocked();
        model.clearLogs();
        model.setRecordingLogs(true);

        Queue<byte[]> deviceWrites = new ConcurrentLinkedQueue<>();
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        DnsPacketProxy proxy = DnsPacketTestFixtures.newFixedServerProxy(
                eventLoop,
                InetAddress.getByName("8.8.8.8"));
        RecordingPacketMonitor packetMonitor = new RecordingPacketMonitor();
        VpnPacketProcessor processor = new VpnPacketProcessor(
                deviceWrites,
                proxy::handleDnsRequest,
                packetMonitor);
        eventLoop.attachProcessor(processor);
        proxy.initialize(application);
        byte[] dnsQuery = DnsPacketTestFixtures.buildIpv4DnsQueryPacket(LOG_HOST);

        int length = processor.readPacketFromDevice(
                new ByteArrayInputStream(dnsQuery),
                new byte[dnsQuery.length + 32]);

        model.setRecordingLogs(false);
        assertEquals(dnsQuery.length, length);
        assertArrayEquals(dnsQuery, packetMonitor.packetData);
        assertEquals(0, eventLoop.forwardedPackets);
        assertNotNull("Blocked DNS query should queue a response to the device.",
                eventLoop.devicePacketData);
        assertTrue("Blocked DNS response should be available through VpnPacketProcessor.",
                processor.hasDeviceWrites());
        ByteArrayOutputStream tunnelOutput = new ByteArrayOutputStream();
        processor.writeToDevice(tunnelOutput);
        assertArrayEquals(eventLoop.devicePacketData, tunnelOutput.toByteArray());
        assertTrue("VPN model must record the host observed by the packet/proxy boundary.",
                model.getLogs().contains(LOG_HOST));

        try (ActivityScenario<LogActivity> scenario = ActivityScenario.launch(LogActivity.class)) {
            LogActivity activity = waitForActivity(LogActivity.class);
            waitForVisibleText(activity, LOG_HOST);
        }
    }

    private void assertFixtureHostIsBlocked() {
        HostEntry entry = hostEntryDao.resolveEntry(LOG_HOST);
        assertEquals("Fixture host must be blocked before exercising the packet bridge.",
                BLOCKED, entry.getType());
    }

    private void insertSource() {
        HostsSource source = new HostsSource();
        source.setId(TEST_SOURCE_ID);
        source.setLabel("VPN packet processor runtime truth test");
        source.setUrl("https://example.invalid/vpn-packet-runtime-truth.txt");
        source.setEnabled(true);
        hostsSourceDao.insert(source);
    }

    private void insertBlockedHost() {
        HostListItem item = new HostListItem();
        item.setHost(LOG_HOST);
        item.setType(BLOCKED);
        item.setKind(EXACT);
        item.setEnabled(true);
        item.setSourceId(TEST_SOURCE_ID);
        item.setGeneration(hostEntryDao.getActiveGeneration());
        hostListItemDao.insert(item);
    }

    private void cleanup() {
        if (database == null) {
            return;
        }
        database.getOpenHelper().getWritableDatabase().execSQL(
                "DELETE FROM hosts_lists WHERE host = ?",
                new Object[]{LOG_HOST});
        hostListItemDao.clearSourceHosts(TEST_SOURCE_ID);
        hostsSourceDao.getById(TEST_SOURCE_ID).ifPresent(hostsSourceDao::delete);
        hostEntryDao.invalidateMaterializedRuntimeCaches();
        application.invalidateVpnRulesCache();
    }

    private static <T extends Activity> T waitForActivity(@NonNull Class<T> activityClass) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<T> resumed = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (activityClass.isInstance(activity)) {
                        resumed.set(activityClass.cast(activity));
                        return;
                    }
                }
            });
            T activity = resumed.get();
            if (activity != null) {
                return activity;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out waiting for " + activityClass.getSimpleName() + ".");
    }

    private static void waitForVisibleText(Activity activity, String expectedText) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<Boolean> found = new AtomicReference<>(false);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    found.set(hasVisibleText(activity.getWindow().getDecorView(), expectedText)));
            if (Boolean.TRUE.equals(found.get())) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Text did not appear: " + expectedText);
    }

    private static boolean hasVisibleText(@Nullable View view, String expectedText) {
        if (view instanceof TextView
                && view.isShown()
                && expectedText.contentEquals(((TextView) view).getText())) {
            return true;
        }
        if (!(view instanceof ViewGroup)) {
            return false;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (hasVisibleText(group.getChildAt(i), expectedText)) {
                return true;
            }
        }
        return false;
    }

    private static void finishResumedActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                activity.finish();
            }
        });
    }

    private static void waitForDiskIoIdle() {
        CountDownLatch latch = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(latch::countDown);
        try {
            if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("Timed out waiting for app disk executor to become idle.");
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for app disk executor.",
                    interruptedException);
        }
    }

    private static final class RecordingEventLoop implements DnsPacketProxy.EventLoop {
        private int forwardedPackets;
        private byte[] devicePacketData;
        private VpnPacketProcessor processor;

        private void attachProcessor(VpnPacketProcessor processor) {
            this.processor = processor;
        }

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
            if (this.processor == null) {
                throw new AssertionError("Packet processor was not attached.");
            }
            this.processor.queueDeviceWrite(packet);
            this.devicePacketData = packet.getRawData();
        }
    }

    private static final class RecordingPacketMonitor
            implements VpnPacketProcessor.PacketMonitor {
        private byte[] packetData;

        @Override
        public void handlePacket(byte[] packetData) {
            this.packetData = Arrays.copyOf(packetData, packetData.length);
        }
    }
}
