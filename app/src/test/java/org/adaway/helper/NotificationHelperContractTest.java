package org.adaway.helper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class NotificationHelperContractTest {
    @Test
    public void appAndHostsUpdateNotificationsUseDistinctIds() throws Exception {
        String source = readNotificationHelperSource();
        String hostsBlock = methodBlock(source, "showUpdateHostsNotification");
        String appBlock = methodBlock(source, "showUpdateApplicationNotification");

        assertTrue("Hosts update notification must use the hosts notification id.",
                hostsBlock.contains("notificationManager.notify(" +
                        "UPDATE_HOSTS_NOTIFICATION_ID, builder.build())"));
        assertTrue("App update notification must use the app notification id.",
                appBlock.contains("notificationManager.notify(" +
                        "UPDATE_APP_NOTIFICATION_ID, builder.build())"));
        assertFalse("App update notification must not overwrite the hosts update notification.",
                appBlock.contains("notificationManager.notify(" +
                        "UPDATE_HOSTS_NOTIFICATION_ID, builder.build())"));
    }

    @Test
    public void updateNotificationsOpenExpectedScreensAndClearOnTap() throws Exception {
        String source = readNotificationHelperSource();
        String hostsBlock = methodBlock(source, "showUpdateHostsNotification");
        String appBlock = methodBlock(source, "showUpdateApplicationNotification");

        assertActionableUpdateNotification(hostsBlock,
                "HomeActivity.class",
                "notification_update_host_available_title",
                "notification_update_host_available_text");
        assertActionableUpdateNotification(appBlock,
                "UpdateActivity.class",
                "notification_update_app_available_title",
                "notification_update_app_available_text");
    }

    @Test
    public void updateNotificationsAreSkippedWhenSystemBlocksAlerts() throws Exception {
        String source = readNotificationHelperSource();
        String hostsBlock = methodBlock(source, "showUpdateHostsNotification");
        String appBlock = methodBlock(source, "showUpdateApplicationNotification");

        assertNotificationBlockHasPermissionGuard(hostsBlock);
        assertNotificationBlockHasPermissionGuard(appBlock);
    }

    @Test
    public void vpnChannelReceivesVpnDescription() throws Exception {
        String source = readNotificationHelperSource();

        assertTrue("VPN notification channel must receive the VPN channel description.",
                source.contains("vpnServiceChannel.setDescription(context.getString(" +
                        "R.string.notification_vpn_channel_description))"));
        assertFalse("Update notification channel must not receive the VPN description.",
                source.contains("updateChannel.setDescription(context.getString(" +
                        "R.string.notification_vpn_channel_description))"));
    }

    private static void assertActionableUpdateNotification(String block, String targetActivity,
            String titleResource, String textResource) {
        assertTrue("Update notification must use the update notification channel.",
                block.contains("new NotificationCompat.Builder(context, " +
                        "UPDATE_NOTIFICATION_CHANNEL)"));
        assertTrue("Update notification must open the expected screen.",
                block.contains("new Intent(context, " + targetActivity + ")"));
        assertTrue("Update notification must reset the opened task.",
                block.contains("intent.setFlags(FLAG_ACTIVITY_NEW_TASK | " +
                        "FLAG_ACTIVITY_CLEAR_TASK)"));
        assertTrue("Update notification must use an immutable content pending intent.",
                block.contains("PendingIntent pendingIntent = getActivity(context, 0, intent, " +
                        "FLAG_IMMUTABLE)"));
        assertTrue("Update notification must use the expected title resource.",
                block.contains("String title = context.getString(R.string." +
                        titleResource + ")"));
        assertTrue("Update notification must use the expected text resource.",
                block.contains("String text = context.getString(R.string." +
                        textResource + ")"));
        assertTrue("Update notification must attach the pending intent.",
                block.contains(".setContentIntent(pendingIntent)"));
        assertTrue("Update notification must remain low priority on pre-channel devices.",
                block.contains(".setPriority(PRIORITY_LOW)"));
        assertTrue("Update notification must clear after the user taps it.",
                block.contains(".setAutoCancel(true)"));
    }

    private static void assertNotificationBlockHasPermissionGuard(String block) {
        int guard = block.indexOf("if (notificationManager == null || " +
                "!notificationManager.areNotificationsEnabled())");
        int builder = block.indexOf("new NotificationCompat.Builder");
        int notify = block.indexOf("notificationManager.notify(");

        assertTrue("Update notification must check whether alerts can be posted.", guard >= 0);
        assertTrue("Update notification must build only after the alert availability check.",
                guard < builder);
        assertTrue("Update notification must notify only after the alert availability check.",
                guard < notify);
        assertTrue("Update notification must return early when alerts are blocked.",
                block.substring(guard, builder).contains("return;"));
    }

    private static String readNotificationHelperSource() throws Exception {
        return readRepoFile("app/src/main/java/org/adaway/helper/NotificationHelper.java");
    }

    private static String methodBlock(String source, String methodName) {
        int start = source.indexOf("void " + methodName);
        if (start < 0) {
            throw new AssertionError("Missing method: " + methodName);
        }
        int end = source.indexOf("\n    /**", start + 1);
        if (end < 0) {
            end = source.length();
        }
        return source.substring(start, end);
    }

    private static String readRepoFile(String relativePath) throws Exception {
        return new String(Files.readAllBytes(resolveRepoFile(relativePath)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private static Path resolveRepoFile(String relativePath) {
        Path cwd = Paths.get("").toAbsolutePath();
        Path repo = Files.isDirectory(cwd.resolve("app")) ? cwd : cwd.getParent();
        return repo.resolve(relativePath);
    }
}
