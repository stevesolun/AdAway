package org.adaway.vpn;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.adaway.vpn.VpnStatus.RUNNING;
import static org.adaway.vpn.VpnStatus.STOPPED;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.adaway.R;
import org.adaway.helper.NotificationHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.ui.home.HomeActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

@RunWith(AndroidJUnit4.class)
public class VpnLifecycleInstrumentedTest {
    private static final String HEARTBEAT_WORK_NAME = "vpnHeartbeat";
    private static final long LIFECYCLE_TIMEOUT_MS = 20_000L;
    private static final long HEARTBEAT_TIMEOUT_MS = 5_000L;
    private static final long POLL_INTERVAL_MS = 250L;

    private Context context;
    private RecordingTree recordingTree;
    private VpnStatus originalStatus;
    private AdBlockMethod originalMethod;
    private boolean heartbeatTouched;
    private boolean lifecycleStarted;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        this.originalStatus = PreferenceHelper.getVpnServiceStatus(this.context);
        this.recordingTree = new RecordingTree();
        Timber.plant(this.recordingTree);
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (this.lifecycleStarted) {
                ActivityManager manager = this.context.getSystemService(ActivityManager.class);
                assertNotNull(manager);
                // A failed start may already have destroyed the service; do not recreate it.
                if (manager.getRunningServices(Integer.MAX_VALUE).stream().anyMatch(service ->
                        VpnService.class.getName().equals(service.service.getClassName()))) {
                    VpnServiceControls.stop(this.context);
                }
                waitUntilStopped();
            }
        } finally {
            try {
                if (this.heartbeatTouched || this.lifecycleStarted) {
                    cancelHeartbeat();
                }
            } finally {
                PreferenceHelper.setVpnServiceStatus(this.context,
                        this.lifecycleStarted ? STOPPED : this.originalStatus);
                if (this.originalMethod != null) {
                    PreferenceHelper.setAbBlockMethod(this.context, this.originalMethod);
                }
                Timber.uproot(this.recordingTree);
            }
        }
    }

    @Test
    public void readingTunnelStateCannotOverwriteServiceStatus() {
        assumeFalse("Do not modify a live VPN fixture.", hasAnyVpnNetwork());
        assumeTrue(this.originalStatus == STOPPED);
        PreferenceHelper.setVpnServiceStatus(this.context, RUNNING);
        assertFalse("A RUNNING value alone is not an established tunnel.",
                VpnServiceControls.isTunnelEstablished(this.context));
        assertTrue("Only the service should change its published status.",
                PreferenceHelper.getVpnServiceStatus(this.context) == RUNNING);
    }

    @Test(timeout = 90_000)
    public void visibleLeakCardTracksEstablishedAndLostTunnelWithoutReopeningHome() throws Exception {
        assumeTrue("Live VPN smoke is opt-in.", "true".equals(
                InstrumentationRegistry.getArguments().getString("adawayLiveVpnSmoke")));
        assumeFalse("Do not interrupt another VPN.", hasAnyVpnNetwork());
        assumeTrue(this.originalStatus == STOPPED);
        assumeTrue("Requires prior user approval.", android.net.VpnService.prepare(this.context) == null);
        this.originalMethod = PreferenceHelper.getAdBlockMethod(this.context);
        PreferenceHelper.setAbBlockMethod(this.context, VPN);
        try (ActivityScenario<HomeActivity> home = ActivityScenario.launch(HomeActivity.class)) {
            waitForLeakText(home, R.string.leak_status_mode_vpn_stopped);
            this.lifecycleStarted = true;
            assertTrue(VpnServiceControls.start(this.context));
            waitForForegroundTunnel(this.context.getSystemService(ActivityManager.class));
            waitForLeakText(home, R.string.leak_status_mode_vpn_running);
            VpnServiceControls.stop(this.context);
            waitUntilStopped();
            waitForLeakText(home, R.string.leak_status_mode_vpn_stopped);
        }
    }

    private void waitForLeakText(ActivityScenario<HomeActivity> home, int expected) {
        String want = this.context.getString(expected);
        String[] actual = {""};
        long end = SystemClock.elapsedRealtime() + 8_000;
        do {
            home.onActivity(a -> actual[0] = ((TextView) a.findViewById(
                    R.id.leakStatusDetailTextView)).getText().toString());
            if (actual[0].contains(want)) return;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < end);
        throw new AssertionError("Expected " + want + "; visible card: " + actual[0]);
    }

    @Test
    public void offlineRequestedServiceUpdatesItsForegroundNotification() throws Exception {
        assumeTrue("Requires an explicitly offline disposable device.", "true".equals(
                InstrumentationRegistry.getArguments().getString("adawayOfflineVpnNotification")));
        assumeTrue(this.originalStatus == STOPPED && !hasAnyVpnNetwork());
        assumeTrue(this.context.getSystemService(ConnectivityManager.class).getActiveNetwork() == null);
        NotificationManager manager = this.context.getSystemService(NotificationManager.class);
        assumeTrue(manager.areNotificationsEnabled());
        this.lifecycleStarted = true;
        assertTrue(VpnServiceControls.start(this.context));
        waitUntilStatus(VpnStatus.WAITING_FOR_NETWORK, LIFECYCLE_TIMEOUT_MS);
        assertTrue("Keep the user's request while waiting", VpnServiceControls.isStarted(this.context));
        String expected = this.context.getString(R.string.vpn_notification_title,
                this.context.getString(R.string.vpn_notification_waiting_for_net));
        String actual = null;
        long deadline = SystemClock.elapsedRealtime() + 8_000;
        do {
            boolean duplicate = false;
            boolean waitingForeground = false;
            for (StatusBarNotification item : manager.getActiveNotifications()) {
                if (item.getId() == NotificationHelper.VPN_RESUME_SERVICE_NOTIFICATION_ID) {
                    duplicate = true;
                }
                if (item.getId() == NotificationHelper.VPN_RUNNING_SERVICE_NOTIFICATION_ID) {
                    Notification n = item.getNotification();
                    actual = n.extras.getString(Notification.EXTRA_TITLE);
                    waitingForeground = expected.equals(actual)
                            && (n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0
                            && n.actions != null && n.actions.length == 1
                            && this.context.getString(R.string.vpn_notification_action_pause)
                                    .contentEquals(n.actions[0].title);
                }
            }
            if (waitingForeground && !duplicate) return;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Expected one foreground notification: " + expected + "; got " + actual);
    }

    @Test
    public void heartbeatStartAndStopAreLocallyObservable() throws Exception {
        this.heartbeatTouched = true;
        cancelHeartbeat();

        VpnServiceHeartbeat.start(this.context);

        assertTrue("Starting the VPN heartbeat must enqueue unique WorkManager work.",
                waitForHeartbeatActive());

        VpnServiceHeartbeat.stop(this.context);

        assertTrue("Stopping the VPN heartbeat must cancel unique WorkManager work.",
                waitForHeartbeatStopped());
    }

    @Test(timeout = 90_000)
    public void frameworkStartIntentEstablishesForegroundTunnelAndResumesAfterStop()
            throws Exception {
        assumeTrue("Live VPN smoke is opt-in.", "true".equals(
                InstrumentationRegistry.getArguments().getString("adawayLiveVpnSmoke")));
        assumeFalse("Do not interrupt an existing VPN.", hasAnyVpnNetwork());
        ActivityManager manager = this.context.getSystemService(ActivityManager.class);
        assertNotNull(manager);
        assumeFalse("Do not interrupt an existing VPN service.",
                manager.getRunningServices(Integer.MAX_VALUE).stream().anyMatch(service ->
                        VpnService.class.getName().equals(service.service.getClassName())));
        assumeTrue("AdAway must be stopped before the framework-start test.",
                this.originalStatus == STOPPED);
        assumeTrue("VPN consent must already be prepared.",
                android.net.VpnService.prepare(this.context) == null);
        this.originalMethod = PreferenceHelper.getAdBlockMethod(this.context);
        PreferenceHelper.setAbBlockMethod(this.context, VPN);

        // Same action/package and absent private extra as Android's always-on start.
        // The test uses foreground start to satisfy app background-start restrictions.
        Intent frameworkStart = new Intent(android.net.VpnService.SERVICE_INTERFACE)
                .setPackage(this.context.getPackageName());
        this.lifecycleStarted = true;
        assertNotNull(this.context.startForegroundService(frameworkStart));
        waitForForegroundTunnel(manager);

        VpnServiceControls.stop(this.context);
        waitUntilStopped();
        assertNotNull(this.context.startForegroundService(frameworkStart));
        waitForForegroundTunnel(manager);
    }

    private void waitForForegroundTunnel(ActivityManager manager) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LIFECYCLE_TIMEOUT_MS);
        do {
            boolean foreground = manager.getRunningServices(Integer.MAX_VALUE).stream()
                    .anyMatch(service -> VpnService.class.getName()
                            .equals(service.service.getClassName()) && service.foreground);
            if (foreground && hasAnyVpnNetwork()
                    && PreferenceHelper.getVpnServiceStatus(this.context) == RUNNING
                    && VpnServiceControls.isTunnelEstablished(this.context)
                    && findUpTunnelInterface() != null) {
                return;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Framework start must establish a foreground VPN tunnel.");
    }

    @Test
    public void startStopResumeEstablishesTunnelStatusTunAndHeartbeatWhenVpnConsentExists()
            throws Exception {
        assumeFalse("Skipping full VPN lifecycle proof: device already has an active VPN.",
                hasAnyVpnNetwork());
        Intent prepareIntent = android.net.VpnService.prepare(this.context);
        assumeTrue("Skipping full VPN lifecycle proof: AdAway VPN consent is not pre-granted.",
                prepareIntent == null);
        this.originalMethod = PreferenceHelper.getAdBlockMethod(this.context);
        PreferenceHelper.setAbBlockMethod(this.context, VPN);
        this.heartbeatTouched = true;
        cancelHeartbeat();
        PreferenceHelper.setVpnServiceStatus(this.context, STOPPED);

        this.lifecycleStarted = true;
        assertTrue("VPN service start command must be accepted.",
                VpnServiceControls.start(this.context));
        waitUntilStatus(RUNNING, LIFECYCLE_TIMEOUT_MS);
        assertTrue("RUNNING must mean Android reports AdAway's VPN network.",
                VpnServiceControls.isTunnelEstablished(this.context));
        assertNotNull("An established VPN should expose an up tun* interface.",
                findUpTunnelInterface());
        assertTrue("Starting the VPN must schedule the heartbeat.",
                waitForHeartbeatActive());
        assertTrue("Lifecycle logs must include a start signal.",
                waitForLog("VPN service started."));

        VpnServiceControls.stop(this.context);
        waitUntilStopped();
        assertFalse("STOPPED must clear established tunnel status.",
                VpnServiceControls.isTunnelEstablished(this.context));
        assertTrue("Stopping the VPN must cancel the heartbeat.",
                waitForHeartbeatStopped());
        assertTrue("Lifecycle logs must include a stop signal.",
                waitForLog("VPN service stopped."));

        assertTrue("VPN service resume command must be accepted.",
                VpnServiceControls.start(this.context));
        waitUntilStatus(RUNNING, LIFECYCLE_TIMEOUT_MS);
        assertTrue("Resumed VPN must re-establish the tunnel.",
                VpnServiceControls.isTunnelEstablished(this.context));
        assertNotNull("A resumed VPN should expose an up tun* interface.",
                findUpTunnelInterface());
    }

    private void waitUntilStopped() throws Exception {
        ActivityManager manager = this.context.getSystemService(ActivityManager.class);
        assertNotNull(manager);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LIFECYCLE_TIMEOUT_MS);
        do {
            boolean servicePresent = manager.getRunningServices(Integer.MAX_VALUE).stream()
                    .anyMatch(service -> VpnService.class.getName()
                            .equals(service.service.getClassName()));
            if (!servicePresent && !hasAnyVpnNetwork()
                    && PreferenceHelper.getVpnServiceStatus(this.context) == STOPPED) {
                return;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Stopped VPN must release both its service and network.");
    }

    private void waitUntilStatus(VpnStatus expected, long timeoutMs) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        VpnStatus lastStatus;
        do {
            lastStatus = PreferenceHelper.getVpnServiceStatus(this.context);
            if (lastStatus == expected) {
                return;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        } while (System.nanoTime() < deadline);

        throw new AssertionError("Timed out waiting for VPN status " + expected
                + "; last status was " + lastStatus);
    }

    private boolean waitForHeartbeatActive() throws Exception {
        return waitForHeartbeatState(false);
    }

    private boolean waitForHeartbeatStopped() throws Exception {
        return waitForHeartbeatState(true);
    }

    private boolean waitForLog(String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LIFECYCLE_TIMEOUT_MS);
        do {
            if (this.recordingTree.contains(expected)) {
                return true;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        } while (System.nanoTime() < deadline);
        return false;
    }

    private boolean waitForHeartbeatState(boolean stopped) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(HEARTBEAT_TIMEOUT_MS);
        do {
            List<WorkInfo> workInfos = heartbeatWorkInfos();
            boolean hasActiveWork = false;
            for (WorkInfo workInfo : workInfos) {
                if (!workInfo.getState().isFinished()) {
                    hasActiveWork = true;
                    break;
                }
            }
            if (stopped != hasActiveWork) {
                return true;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        } while (System.nanoTime() < deadline);
        return false;
    }

    private List<WorkInfo> heartbeatWorkInfos() throws Exception {
        return WorkManager.getInstance(this.context)
                .getWorkInfosForUniqueWork(HEARTBEAT_WORK_NAME)
                .get(HEARTBEAT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void cancelHeartbeat() throws Exception {
        WorkManager.getInstance(this.context)
                .cancelUniqueWork(HEARTBEAT_WORK_NAME)
                .getResult()
                .get(HEARTBEAT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private boolean hasAnyVpnNetwork() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) this.context.getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        for (Network network : connectivityManager.getAllNetworks()) {
            NetworkCapabilities capabilities =
                    connectivityManager.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(TRANSPORT_VPN)) {
                return true;
            }
        }
        return false;
    }

    private static NetworkInterface findUpTunnelInterface() throws Exception {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (networkInterface.getName().matches("tun[0-9]+") && networkInterface.isUp()) {
                return networkInterface;
            }
        }
        return null;
    }

    private static final class RecordingTree extends Timber.Tree {
        private final List<String> messages = Collections.synchronizedList(new ArrayList<>());

        @Override
        protected void log(int priority, String tag, String message, Throwable throwable) {
            this.messages.add(message);
        }

        private boolean contains(String expected) {
            synchronized (this.messages) {
                for (String message : this.messages) {
                    if (message.contains(expected)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
