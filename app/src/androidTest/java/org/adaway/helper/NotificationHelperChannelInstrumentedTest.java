package org.adaway.helper;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_NO_CREATE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;

import org.adaway.R;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.ui.home.HomeActivity;
import org.adaway.vpn.VpnService;
import org.adaway.vpn.VpnServiceControls;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class NotificationHelperChannelInstrumentedTest {
    private static final String PROBE_TAG = "NotificationHelperChannelInstrumentedTest";
    private static final int VISIBLE_PROBE_ID = 1;
    private static final int BLOCKED_UPDATE_PROBE_ID = 2;
    private static final int BLOCKED_VPN_PROBE_ID = 3;

    private Context context;
    private NotificationManager notificationManager;
    private final List<String> probeChannels = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        this.notificationManager = this.context.getSystemService(NotificationManager.class);
        assertNotNull("NotificationManager must be available on device.", this.notificationManager);
        stopVpnBeforeChannelReset();
        clearTestNotifications();
    }

    @After
    public void tearDown() throws Exception {
        stopVpnBeforeChannelReset();
        clearTestNotifications();
        for (String channelId : this.probeChannels) {
            this.notificationManager.deleteNotificationChannel(channelId);
        }
        NotificationHelper.createNotificationChannels(this.context);
    }

    @Test
    public void createChannelsUpdatesMetadataAndPreservesExistingSettings() {
        NotificationChannel updateBefore = this.notificationManager.getNotificationChannel(
                NotificationHelper.UPDATE_NOTIFICATION_CHANNEL);
        NotificationChannel filterListsBefore = this.notificationManager.getNotificationChannel(
                NotificationHelper.FILTERLISTS_NOTIFICATION_CHANNEL);
        NotificationChannel vpnBefore = this.notificationManager.getNotificationChannel(
                NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL);
        NotificationHelper.createNotificationChannels(this.context);

        assertChannel(
                NotificationHelper.UPDATE_NOTIFICATION_CHANNEL,
                R.string.notification_update_channel_name,
                R.string.notification_update_channel_description,
                NotificationManager.IMPORTANCE_LOW,
                updateBefore
        );
        assertChannel(
                NotificationHelper.FILTERLISTS_NOTIFICATION_CHANNEL,
                R.string.notification_filterlists_channel_name,
                R.string.notification_filterlists_channel_description,
                NotificationManager.IMPORTANCE_DEFAULT,
                filterListsBefore
        );
        assertChannel(
                NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL,
                R.string.notification_vpn_channel_name,
                R.string.notification_vpn_channel_description,
                NotificationManager.IMPORTANCE_LOW,
                vpnBefore
        );
    }

    @Test
    public void existingFilterListsStyleChannelDoesNotUpgradeFromLowToDefaultImportance() {
        String channelId = createProbeChannel(NotificationManager.IMPORTANCE_LOW);

        NotificationChannel upgradedChannel = new NotificationChannel(
                channelId,
                this.context.getString(R.string.notification_filterlists_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        this.notificationManager.createNotificationChannel(upgradedChannel);

        assertChannelImportance(
                channelId,
                NotificationManager.IMPORTANCE_LOW
        );
    }

    @Test
    public void updateAlertsBuildDistinctActionableNotificationsWithoutPermissionMutation() {
        NotificationHelper.createNotificationChannels(this.context);

        Notification hosts = NotificationHelper.buildUpdateHostsNotification(this.context);
        Notification app = NotificationHelper.buildUpdateApplicationNotification(this.context);

        assertUpdateAlertNotification(
                hosts,
                R.string.notification_update_host_available_title,
                R.string.notification_update_host_available_text
        );
        assertUpdateAlertNotification(
                app,
                R.string.notification_update_app_available_title,
                R.string.notification_update_app_available_text
        );
        assertNotEquals(
                "Hosts and app update alerts must open distinct pending intents.",
                hosts.contentIntent,
                app.contentIntent
        );
    }

    @Test
    public void updateAlertPostingMatchesCurrentNotificationPermissionState() {
        NotificationHelper.createNotificationChannels(this.context);
        NotificationHelper.clearUpdateNotifications(this.context);
        waitForNoNotifications(NotificationHelper.UPDATE_HOSTS_NOTIFICATION_ID,
                NotificationHelper.UPDATE_APP_NOTIFICATION_ID);

        boolean alertsEnabled = isChannelEnabled(NotificationHelper.UPDATE_NOTIFICATION_CHANNEL);

        NotificationHelper.showUpdateHostsNotification(this.context);
        NotificationHelper.showUpdateApplicationNotification(this.context);

        if (alertsEnabled) {
            waitForNotification(null, NotificationHelper.UPDATE_HOSTS_NOTIFICATION_ID);
            waitForNotification(null, NotificationHelper.UPDATE_APP_NOTIFICATION_ID);
        } else {
            assertNoNotificationsFor(1_000, null, NotificationHelper.UPDATE_HOSTS_NOTIFICATION_ID,
                    NotificationHelper.UPDATE_APP_NOTIFICATION_ID);
        }
    }

    @Test
    public void bootPermissionNotificationOpensHomeWithoutAnAutomaticStartAction() {
        Notification notification = NotificationHelper.buildVpnPermissionNotification(this.context);
        assertEquals(NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL, notification.getChannelId());
        assertEquals(this.context.getString(R.string.notification_vpn_permission_title),
                notification.extras.getCharSequence(Notification.EXTRA_TITLE));
        assertEquals(this.context.getString(R.string.notification_vpn_permission_text),
                notification.extras.getCharSequence(Notification.EXTRA_TEXT));
        assertTrue((notification.flags & Notification.FLAG_AUTO_CANCEL) != 0);
        assertTrue(notification.actions == null || notification.actions.length == 0);
        Intent homeIntent = new Intent(this.context, HomeActivity.class)
                .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent expected = PendingIntent.getActivity(this.context,
                NotificationHelper.VPN_RESUME_SERVICE_NOTIFICATION_ID,
                homeIntent, FLAG_NO_CREATE | FLAG_IMMUTABLE);
        assertNotNull(expected);
        assertEquals(expected, notification.contentIntent);
        // Do not send the intent: Home Resume and real consent are separate live UI gates.
    }

    @Test
    public void bootPermissionAlertPostingRespectsCurrentAppAndVpnChannelSettings() {
        NotificationHelper.createNotificationChannels(this.context);
        this.notificationManager.cancel(NotificationHelper.VPN_RESUME_SERVICE_NOTIFICATION_ID);
        waitForNoNotifications(NotificationHelper.VPN_RESUME_SERVICE_NOTIFICATION_ID);
        boolean alertsEnabled = isChannelEnabled(NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL);

        NotificationHelper.showVpnPermissionNotification(this.context);

        if (alertsEnabled) {
            waitForNotification(null, NotificationHelper.VPN_RESUME_SERVICE_NOTIFICATION_ID);
        } else {
            assertNoNotificationsFor(1_000, null, NotificationHelper.VPN_RESUME_SERVICE_NOTIFICATION_ID);
        }
    }

    @Test
    public void postingGuardRejectsMissingManagerOrChannel() {
        assertFalse(NotificationHelper.canPostNotification(null, "missing"));
        assertFalse(NotificationHelper.canPostNotification(this.notificationManager,
                "missing-" + UUID.randomUUID()));
    }

    @Test
    public void blockedChannelSuppressesAlertsWhileAppNotificationsAreEnabled() {
        assumeTrue("Channel-only suppression needs already-granted app notification permission.",
                this.notificationManager.areNotificationsEnabled());
        String blockedChannel = createProbeChannel(NotificationManager.IMPORTANCE_NONE);
        String visibleChannel = createProbeChannel(NotificationManager.IMPORTANCE_LOW);
        assertFalse(NotificationHelper.canPostNotification(this.notificationManager, blockedChannel));
        assertTrue(NotificationHelper.canPostNotification(this.notificationManager, visibleChannel));

        Notification update = NotificationHelper.buildUpdateHostsNotification(this.context);
        Notification boot = NotificationHelper.buildVpnPermissionNotification(this.context);
        postProbe(BLOCKED_UPDATE_PROBE_ID, update, blockedChannel);
        postProbe(BLOCKED_VPN_PROBE_ID, boot, blockedChannel);
        postProbe(VISIBLE_PROBE_ID, update, visibleChannel);
        waitForNotification(PROBE_TAG, VISIBLE_PROBE_ID);
        assertNoNotificationsFor(1_000, PROBE_TAG, BLOCKED_UPDATE_PROBE_ID, BLOCKED_VPN_PROBE_ID);

        // Same-id recreation must not be mistaken for a reset of a blocked channel.
        this.notificationManager.deleteNotificationChannel(blockedChannel);
        this.notificationManager.createNotificationChannel(new NotificationChannel(
                blockedChannel, "Restored probe", NotificationManager.IMPORTANCE_LOW));
        assertChannelImportance(blockedChannel, NotificationManager.IMPORTANCE_NONE);
        assertFalse(NotificationHelper.canPostNotification(this.notificationManager, blockedChannel));
        postProbe(BLOCKED_UPDATE_PROBE_ID, update, blockedChannel);
        postProbe(BLOCKED_VPN_PROBE_ID, boot, blockedChannel);
        assertNoNotificationsFor(1_000, PROBE_TAG, BLOCKED_UPDATE_PROBE_ID, BLOCKED_VPN_PROBE_ID);
    }

    @Test(timeout = 45_000)
    public void channelResetStopsPreparedForegroundVpnBeforeDeletion() throws Exception {
        assumeTrue("VPN consent is required for the foreground-channel cleanup regression.",
                android.net.VpnService.prepare(this.context) == null);
        AdBlockMethod originalMethod = PreferenceHelper.getAdBlockMethod(this.context);
        PreferenceHelper.setAbBlockMethod(this.context, AdBlockMethod.VPN);
        try {
            NotificationHelper.createNotificationChannels(this.context);
            assertTrue(VpnServiceControls.start(this.context));
            long deadline = SystemClock.elapsedRealtime() + 20_000;
            while (!VpnServiceControls.isTunnelEstablished(this.context)
                    && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(100);
            }
            assertTrue("The regression requires an actual running VPN.",
                    VpnServiceControls.isTunnelEstablished(this.context));
            NotificationChannel before = this.notificationManager.getNotificationChannel(
                    NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL);
            stopVpnBeforeChannelReset();
            this.notificationManager.deleteNotificationChannel(
                    NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL);
            assertEquals(null, this.notificationManager.getNotificationChannel(
                    NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL));
            NotificationHelper.createNotificationChannels(this.context);
            assertSettingsPreserved(before, this.notificationManager.getNotificationChannel(
                    NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL));
        } finally {
            try {
                stopVpnBeforeChannelReset();
            } finally {
                PreferenceHelper.setAbBlockMethod(this.context, originalMethod);
            }
        }
    }

    private void assertUpdateAlertNotification(
            Notification notification,
            int expectedTitle,
            int expectedText) {
        assertEquals(NotificationHelper.UPDATE_NOTIFICATION_CHANNEL, notification.getChannelId());
        assertEquals(Notification.PRIORITY_LOW, notification.priority);
        assertEquals(this.context.getColor(R.color.notification), notification.color);
        assertEquals(
                this.context.getString(expectedTitle),
                notification.extras.getCharSequence(Notification.EXTRA_TITLE)
        );
        assertEquals(
                this.context.getString(expectedText),
                notification.extras.getCharSequence(Notification.EXTRA_TEXT)
        );
        assertNotNull("Update alert must have an actionable content intent.",
                notification.contentIntent);
        assertTrue("Update alert must clear after tap.",
                (notification.flags & Notification.FLAG_AUTO_CANCEL) != 0);
    }

    private void assertChannel(
            String channelId,
            int expectedName,
            int expectedDescription,
            int defaultImportance,
            NotificationChannel previous) {
        NotificationChannel channel = this.notificationManager.getNotificationChannel(channelId);
        assertNotNull("Missing installed notification channel: " + channelId, channel);
        assertEquals(this.context.getString(expectedName), channel.getName().toString());
        assertEquals(this.context.getString(expectedDescription), channel.getDescription());
        if (previous == null) {
            assertEquals(defaultImportance, channel.getImportance());
        } else {
            assertSettingsPreserved(previous, channel);
        }
    }

    private static void assertSettingsPreserved(NotificationChannel previous,
            NotificationChannel current) {
        assertNotNull(previous);
        assertNotNull(current);
        assertEquals(previous.getImportance(), current.getImportance());
        assertEquals(previous.getSound(), current.getSound());
        assertEquals(previous.getAudioAttributes(), current.getAudioAttributes());
        assertEquals(previous.shouldVibrate(), current.shouldVibrate());
        assertArrayEquals(previous.getVibrationPattern(), current.getVibrationPattern());
        assertEquals(previous.shouldShowLights(), current.shouldShowLights());
        assertEquals(previous.getLightColor(), current.getLightColor());
        assertEquals(previous.canShowBadge(), current.canShowBadge());
        assertEquals(previous.canBypassDnd(), current.canBypassDnd());
        assertEquals(previous.getLockscreenVisibility(), current.getLockscreenVisibility());
        assertEquals(previous.getGroup(), current.getGroup());
    }

    private boolean isChannelEnabled(String channelId) {
        NotificationChannel channel = this.notificationManager.getNotificationChannel(channelId);
        return this.notificationManager.areNotificationsEnabled()
                && channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    private void assertChannelImportance(String channelId, int expectedImportance) {
        NotificationChannel channel = this.notificationManager.getNotificationChannel(channelId);
        assertNotNull("Missing installed notification channel: " + channelId, channel);
        assertEquals(expectedImportance, channel.getImportance());
    }

    private String createProbeChannel(int importance) {
        String channelId = PROBE_TAG + "." + UUID.randomUUID();
        this.probeChannels.add(channelId);
        NotificationChannel channel = new NotificationChannel(
                channelId,
                "Existing " + channelId,
                importance
        );
        channel.setDescription("Existing description for " + channelId);
        this.notificationManager.createNotificationChannel(channel);
        return channelId;
    }

    private void postProbe(int id, Notification notification, String channelId) {
        Notification probe = Notification.Builder.recoverBuilder(this.context, notification)
                .setChannelId(channelId)
                .build();
        this.notificationManager.notify(PROBE_TAG, id, probe);
    }

    private void clearTestNotifications() {
        NotificationHelper.clearUpdateNotifications(this.context);
        this.notificationManager.cancel(NotificationHelper.VPN_RESUME_SERVICE_NOTIFICATION_ID);
        this.notificationManager.cancel(PROBE_TAG, VISIBLE_PROBE_ID);
        this.notificationManager.cancel(PROBE_TAG, BLOCKED_UPDATE_PROBE_ID);
        this.notificationManager.cancel(PROBE_TAG, BLOCKED_VPN_PROBE_ID);
        waitForNoNotifications(NotificationHelper.UPDATE_HOSTS_NOTIFICATION_ID,
                NotificationHelper.UPDATE_APP_NOTIFICATION_ID,
                NotificationHelper.VPN_RESUME_SERVICE_NOTIFICATION_ID);
    }

    private void stopVpnBeforeChannelReset() throws Exception {
        // Earlier receiver tests can start the real service when VPN consent is prepared.
        WorkManager.getInstance(this.context).cancelUniqueWork("vpnHeartbeat")
                .getResult().get(5, TimeUnit.SECONDS);
        ActivityManager manager = this.context.getSystemService(ActivityManager.class);
        assertNotNull(manager);
        if (!isVpnServicePresent(manager)) {
            drainStopCallback();
            return;
        }
        VpnServiceControls.stop(this.context);
        long deadline = SystemClock.elapsedRealtime() + 5_000;
        do {
            // STOP is asynchronous. Wait for destruction, not only foreground removal,
            // so the next test cannot START an instance with stopSelf already queued.
            if (!isVpnServicePresent(manager)) {
                drainStopCallback();
                return;
            }
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("VPN service must be destroyed before deleting its channel.");
    }

    private static void drainStopCallback() {
        // stopSelf can remove the service record before the main-thread STOP callback
        // finishes posting its resume notification. Cancel alerts only after that callback.
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { });
    }

    private static boolean isVpnServicePresent(ActivityManager manager) {
        return manager.getRunningServices(Integer.MAX_VALUE).stream()
                .anyMatch(service -> VpnService.class.getName()
                        .equals(service.service.getClassName()));
    }

    private void waitForNotification(String tag, int notificationId) {
        long deadline = SystemClock.elapsedRealtime() + 5000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasNotification(tag, notificationId)) {
                return;
            }
            sleep();
        }
        assertTrue("Expected active notification id " + notificationId,
                hasNotification(tag, notificationId));
    }

    private void waitForNoNotifications(int... notificationIds) {
        long deadline = SystemClock.elapsedRealtime() + 5000;
        while (SystemClock.elapsedRealtime() < deadline) {
            boolean anyActive = false;
            for (int notificationId : notificationIds) {
                anyActive |= hasNotification(null, notificationId);
            }
            if (!anyActive) {
                return;
            }
            sleep();
        }
        for (int notificationId : notificationIds) {
            assertFalse("Notification should not be active: " + notificationId,
                    hasNotification(null, notificationId));
        }
    }

    private void assertNoNotificationsFor(long durationMs, String tag, int... notificationIds) {
        long deadline = SystemClock.elapsedRealtime() + durationMs;
        do {
            for (int notificationId : notificationIds) {
                assertFalse("Unexpected active notification id " + notificationId,
                        hasNotification(tag, notificationId));
            }
            sleep();
        } while (SystemClock.elapsedRealtime() < deadline);
    }

    private boolean hasNotification(String tag, int notificationId) {
        for (StatusBarNotification notification :
                this.notificationManager.getActiveNotifications()) {
            if (notification.getPackageName().equals(this.context.getPackageName()) &&
                    Objects.equals(tag, notification.getTag()) &&
                    notification.getId() == notificationId) {
                return true;
            }
        }
        return false;
    }

    private static void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(
                    "Interrupted while waiting for notification state.",
                    exception
            );
        }
    }
}
