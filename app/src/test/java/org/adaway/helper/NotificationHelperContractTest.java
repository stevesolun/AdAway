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
        String source = readRepoFile("app/src/main/java/org/adaway/helper/NotificationHelper.java");
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
    public void vpnChannelReceivesVpnDescription() throws Exception {
        String source = readRepoFile("app/src/main/java/org/adaway/helper/NotificationHelper.java");

        assertTrue("VPN notification channel must receive the VPN channel description.",
                source.contains("vpnServiceChannel.setDescription(context.getString(" +
                        "R.string.notification_vpn_channel_description))"));
        assertFalse("Update notification channel must not receive the VPN description.",
                source.contains("updateChannel.setDescription(context.getString(" +
                        "R.string.notification_vpn_channel_description))"));
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
