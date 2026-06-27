package org.adaway.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.adaway.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NotificationHelperChannelInstrumentedTest {
    private Context context;
    private NotificationManager notificationManager;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        this.notificationManager = this.context.getSystemService(NotificationManager.class);
        assertNotNull("NotificationManager must be available on device.", this.notificationManager);
        NotificationHelper.createNotificationChannels(this.context);
    }

    @Test
    public void installedChannelsHaveExpectedUserVisibleMetadata() {
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
}
