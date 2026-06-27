package org.adaway.ui.prefs;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION_CODES.S;
import static android.os.Build.VERSION_CODES.TIRAMISU;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PrefsUpdateNotificationPermissionContractTest {
    @Test
    public void notificationSettingsAffordanceIsOnlyNeededForDeniedAndroid13PlusPermission() {
        assertFalse(PrefsUpdateFragment.shouldShowNotificationPreferences(S, PERMISSION_DENIED));
        assertFalse(PrefsUpdateFragment.shouldShowNotificationPreferences(
                TIRAMISU,
                PERMISSION_GRANTED));
        assertTrue(PrefsUpdateFragment.shouldShowNotificationPreferences(
                TIRAMISU,
                PERMISSION_DENIED));
        assertTrue(PrefsUpdateFragment.shouldShowNotificationPreferences(
                TIRAMISU + 1,
                PERMISSION_DENIED));
    }

    @Test
    public void notificationSettingsRowOpensAppNotificationSettingsWithoutPermissionMutation()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/prefs/PrefsUpdateFragment.java");
        String preferences = readRepoFile("app/src/main/res/xml/preferences_update.xml");
        String strings = readRepoFile("app/src/main/res/values/strings_prefs_update.xml");

        assertTrue("Update preferences must include the notification-settings row.",
                preferences.contains("pref_update_open_notification_preferences_key"));
        assertTrue("Notification row title must explain the disabled-alert state.",
                strings.contains("Notifications are disabled"));
        assertTrue("Notification row summary must tell users to enable notifications.",
                strings.contains("Tap to enable them"));
        assertTrue("Notification row must open Android app notification settings.",
                source.contains("new Intent(ACTION_APP_NOTIFICATION_SETTINGS)") &&
                        source.contains(".putExtra(EXTRA_APP_PACKAGE, context.getPackageName())"));
        assertTrue("Notification row must not mutate POST_NOTIFICATIONS during instrumentation.",
                !source.contains("requestPermissions(") &&
                        !source.contains("revokeRuntimePermission") &&
                        !source.contains("grantRuntimePermission"));
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
