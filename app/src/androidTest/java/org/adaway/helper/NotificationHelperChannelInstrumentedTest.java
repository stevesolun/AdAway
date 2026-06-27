package org.adaway.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

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
        this.notificationManager.deleteNotificationChannel(
                NotificationHelper.UPDATE_NOTIFICATION_CHANNEL);
        this.notificationManager.deleteNotificationChannel(
                NotificationHelper.FILTERLISTS_NOTIFICATION_CHANNEL);
        this.notificationManager.deleteNotificationChannel(
                NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL);
        this.notificationManager.deleteNotificationChannel(FILTERLISTS_UPGRADE_PROBE_CHANNEL);
    }
}
