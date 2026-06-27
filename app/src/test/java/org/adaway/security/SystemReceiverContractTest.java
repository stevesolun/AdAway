package org.adaway.security;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class SystemReceiverContractTest {

    @Test
    public void packageReplaceReceiverRepairsScheduledWorkFromPreferences() throws IOException {
        String receiver = read("app/src/main/java/org/adaway/broadcast/UpdateReceiver.java");
        String sourceUpdate = read(
                "app/src/main/java/org/adaway/model/source/SourceUpdateService.java");
        String apkUpdate = read("app/src/main/java/org/adaway/model/update/ApkUpdateService.java");
        String filterSetUpdate = read(
                "app/src/main/java/org/adaway/ui/hosts/FilterSetUpdateService.java");

        assertTrue("Package replace receiver must guard on MY_PACKAGE_REPLACED.",
                receiver.contains("ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())"));
        assertTrue("Package replace receiver must repair hosts update work.",
                receiver.contains("SourceUpdateService.syncPreferences(context)"));
        assertTrue("Package replace receiver must repair APK update work.",
                receiver.contains("ApkUpdateService.syncPreferences(context)"));
        assertTrue("Package replace receiver must repair filter-set update work.",
                receiver.contains("FilterSetUpdateService.syncPreferences(context)"));

        assertTrue("Hosts update preference repair must be public for receivers.",
                sourceUpdate.contains("public static void syncPreferences(Context context)"));
        assertTrue("APK update preference repair must be public for receivers.",
                apkUpdate.contains("public static void syncPreferences(Context context)"));
        boolean hasFilterSetSync = filterSetUpdate.contains(
                "public static void syncPreferences(@NonNull Context context)")
                && filterSetUpdate.contains("FilterSetStore.ensureGlobalDefaults(context)")
                && filterSetUpdate.contains("FilterSetStore.isGlobalScheduleEnabled(context)")
                && filterSetUpdate.contains("enable(context)")
                && filterSetUpdate.contains("disable(context)");
        assertTrue("Filter-set repair must restore defaults before deciding work state.",
                hasFilterSetSync);
    }

    @Test
    public void launcherShortcutsUseGeneratedApplicationIdResource() throws IOException {
        String build = read("app/build.gradle");
        String shortcuts = read("app/src/main/res/xml/shortcuts.xml");

        assertTrue("Application id should be explicit so launcher shortcut package is auditable.",
                build.contains("applicationId = 'org.adaway'"));
        assertTrue("Shortcut target package must be generated from the application id.",
                build.contains("resValue \"string\", \"shortcut_target_package\", applicationId"));
        assertTrue("Static shortcuts must not bake a literal package that can drift by build.",
                !shortcuts.contains("android:targetPackage=\"org.adaway\""));
        assertTrue("All three static shortcut intents should use the generated package resource.",
                count(shortcuts, "android:targetPackage=\"@string/shortcut_target_package\"") == 3);
        assertTrue("Preferences shortcut target must remain explicit.",
                shortcuts.contains("android:targetClass=\"org.adaway.ui.prefs.PrefsActivity\""));
        assertTrue("DNS requests shortcut target must remain explicit.",
                shortcuts.contains("android:targetClass=\"org.adaway.ui.log.LogActivity\""));
        assertTrue("Your lists shortcut target must remain explicit.",
                shortcuts.contains("android:targetClass=\"org.adaway.ui.lists.ListsActivity\""));
    }

    private static int count(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static String read(String relativePath) throws IOException {
        Path path = repoDir().resolve(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path repoDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        if (Files.isDirectory(cwd.resolve("src/main"))) {
            Path parent = cwd.getParent();
            return parent != null && cwd.getFileName().toString().equals("app") ? parent : cwd;
        }
        return cwd;
    }
}
