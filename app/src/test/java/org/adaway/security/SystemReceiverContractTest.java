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
