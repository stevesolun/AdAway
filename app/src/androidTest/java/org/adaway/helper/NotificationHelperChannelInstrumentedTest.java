package org.adaway.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.StatusBarNotification;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.adaway.R;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NotificationHelperChannelInstrumentedTest {
    private static final String FILTERLISTS_UPGRADE_PROBE_CHANNEL =
            "FilterListsChannel.UpgradeProbe";

    private Context context;
    private NotificationManager notificationManager;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        this.notificationManager = this.context.getSystemService(NotificationManager.class);
        assertNotNull("NotificationManager must be available on device.", this.notificationManager);
        deleteAppChannels();
    }

    @After
    public void tearDown() {
        deleteAppChannels();
    }

    @Test
    public void createChannelsInstallsExpectedUserVisibleMetadata() {
        NotificationHelper.createNotificationChannels(this.context);

        assertChannel(
                NotificationHelper.UPDATE_NOTIFICATION_CHANNEL,
                R.string.notification_update_channel_name,
                R.string.notification_update_channel_description,
                NotificationManager.IMPORTANCE_LOW
        );
        assertChannel(
                NotificationHelper.FILTERLISTS_NOTIFICATION_CHANNEL,
                R.string.notification_filterlists_channel_name,
                R.string.notification_filterlists_channel_description,
                NotificationManager.IMPORTANCE_DEFAULT
        );
        assertChannel(
                NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL,
                R.string.notification_vpn_channel_name,
                R.string.notification_vpn_channel_description,
                NotificationManager.IMPORTANCE_LOW
        );
    }

    @Test
    public void existingFilterListsStyleChannelDoesNotUpgradeFromLowToDefaultImportance() {
        createExistingChannel(
                FILTERLISTS_UPGRADE_PROBE_CHANNEL,
                NotificationManager.IMPORTANCE_LOW
        );

        NotificationChannel upgradedChannel = new NotificationChannel(
                FILTERLISTS_UPGRADE_PROBE_CHANNEL,
                this.context.getString(R.string.notification_filterlists_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        this.notificationManager.createNotificationChannel(upgradedChannel);

        assertChannelImportance(
                FILTERLISTS_UPGRADE_PROBE_CHANNEL,
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
        waitForNoUpdateNotifications();

        boolean alertsEnabled = this.notificationManager.areNotificationsEnabled();

        NotificationHelper.showUpdateHostsNotification(this.context);
        NotificationHelper.showUpdateApplicationNotification(this.context);

        if (alertsEnabled) {
            waitForUpdateNotification(NotificationHelper.UPDATE_HOSTS_NOTIFICATION_ID);
            waitForUpdateNotification(NotificationHelper.UPDATE_APP_NOTIFICATION_ID);
        } else {
            waitForNoUpdateNotifications();
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
            int expectedImportance) {
        NotificationChannel channel = this.notificationManager.getNotificationChannel(channelId);
        assertNotNull("Missing installed notification channel: " + channelId, channel);
        assertEquals(this.context.getString(expectedName), channel.getName().toString());
        assertEquals(this.context.getString(expectedDescription), channel.getDescription());
        assertEquals(expectedImportance, channel.getImportance());
    }

    private void assertChannelImportance(String channelId, int expectedImportance) {
        NotificationChannel channel = this.notificationManager.getNotificationChannel(channelId);
        assertNotNull("Missing installed notification channel: " + channelId, channel);
        assertEquals(expectedImportance, channel.getImportance());
    }

    private void createExistingChannel(String channelId, int importance) {
        NotificationChannel channel = new NotificationChannel(
                channelId,
                "Existing " + channelId,
                importance
        );
        channel.setDescription("Existing description for " + channelId);
        this.notificationManager.createNotificationChannel(channel);
    }

    private void deleteAppChannels() {
        NotificationHelper.clearUpdateNotifications(this.context);
        this.notificationManager.deleteNotificationChannel(
                NotificationHelper.UPDATE_NOTIFICATION_CHANNEL);
        this.notificationManager.deleteNotificationChannel(
                NotificationHelper.FILTERLISTS_NOTIFICATION_CHANNEL);
        this.notificationManager.deleteNotificationChannel(
                NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL);
        this.notificationManager.deleteNotificationChannel(FILTERLISTS_UPGRADE_PROBE_CHANNEL);
    }

    private void waitForUpdateNotification(int notificationId) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (hasUpdateNotification(notificationId)) {
                return;
            }
            sleep();
        }
        assertTrue("Expected active update notification id " + notificationId,
                hasUpdateNotification(notificationId));
    }

    private void waitForNoUpdateNotifications() {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (!hasUpdateNotification(NotificationHelper.UPDATE_HOSTS_NOTIFICATION_ID) &&
                    !hasUpdateNotification(NotificationHelper.UPDATE_APP_NOTIFICATION_ID)) {
                return;
            }
            sleep();
        }
        assertFalse("Hosts update notification should not be active.",
                hasUpdateNotification(NotificationHelper.UPDATE_HOSTS_NOTIFICATION_ID));
        assertFalse("App update notification should not be active.",
                hasUpdateNotification(NotificationHelper.UPDATE_APP_NOTIFICATION_ID));
    }

    private boolean hasUpdateNotification(int notificationId) {
        for (StatusBarNotification notification :
                this.notificationManager.getActiveNotifications()) {
            if (notification.getPackageName().equals(this.context.getPackageName()) &&
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
