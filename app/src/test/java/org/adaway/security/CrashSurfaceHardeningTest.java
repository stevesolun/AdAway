package org.adaway.security;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class CrashSurfaceHardeningTest {

    @Test
    public void bootReceiverAddsNewTaskFlagBeforeStartingVpnPermissionActivity() throws IOException {
        String source = read("app/src/main/java/org/adaway/broadcast/BootRestoreController.java");
        int permissionBranch = source.indexOf("if (prepareIntent != null)");
        int addNewTaskFlag = source.indexOf("prepareIntent.addFlags(FLAG_ACTIVITY_NEW_TASK);",
                permissionBranch);
        int startPermissionActivity = source.indexOf("context.startActivity(prepareIntent);",
                addNewTaskFlag);
        int returnAfterPermissionRequest = source.indexOf("return;", startPermissionActivity);
        int startVpnService = source.indexOf("this.mVpnGateway.start(context);",
                returnAfterPermissionRequest);

        assertTrue("Broadcast receivers must add FLAG_ACTIVITY_NEW_TASK before starting a VPN "
                        + "permission Activity.",
                addNewTaskFlag >= 0 && addNewTaskFlag < startPermissionActivity);
        assertTrue("Boot restore must not start the VPN service until VPN permission is granted.",
                startPermissionActivity < returnAfterPermissionRequest
                        && returnAfterPermissionRequest < startVpnService);
    }

    @Test
    public void apkDownloadReceiverContainsInstallerIntentExceptionGuards() throws IOException {
        String source = read("app/src/main/java/org/adaway/model/update/ApkDownloadReceiver.java");

        assertTrue("Updater must check installer/settings handlers before startActivity.",
                source.contains("resolveActivity(context.getPackageManager())"));
        assertTrue("Updater must contain missing Activity handlers.",
                source.contains("ActivityNotFoundException"));
        assertTrue("Updater must contain installer permission/security failures.",
                source.contains("SecurityException"));
    }

    @Test
    public void updateViewModelGuardsUnavailableDownloadCursorAndColumns() throws IOException {
        String source = read("app/src/main/java/org/adaway/ui/update/UpdateViewModel.java");

        assertTrue("Progress tracking must handle a missing DownloadManager service.",
                source.contains("downloadManager == null"));
        assertTrue("Progress tracking must handle a null query cursor.",
                source.contains("cursor == null"));
        assertTrue("Progress tracking must reject missing status columns.",
                source.contains("statusColumnIndex < 0"));
        assertTrue("Progress tracking must reject missing progress columns.",
                source.contains("totalSizeColumnIndex < 0")
                        && source.contains("bytesDownloadedColumnIndex < 0"));
    }

    @Test
    public void quickSettingsTileToleratesMissingTileHandle() throws IOException {
        String source = read("app/src/main/java/org/adaway/tile/AdBlockingTileService.java");

        assertTrue("Quick Settings tile updates must tolerate getQsTile() returning null.",
                source.contains("Tile tile = getQsTile();") &&
                        source.contains("if (tile == null)") &&
                        source.indexOf("if (tile == null)")
                                < source.indexOf("tile.setState"));
    }

    @Test
    public void moreGithubHelpLinkToleratesMissingExternalActivity() throws IOException {
        String source = read("app/src/main/java/org/adaway/ui/more/MoreFragment.java");

        assertTrue("More GitHub/Help row must use a crash-safe external link helper.",
                source.contains("openExternalUri(Uri.parse(GITHUB_URL))"));
        assertTrue("External links must check for an available Activity before launch.",
                source.contains("resolveActivity(requireContext().getPackageManager())"));
        assertTrue("External links must catch missing or blocked Activity launches.",
                source.contains("ActivityNotFoundException | SecurityException"));
        assertTrue("More GitHub/Help row must not directly start an unguarded ACTION_VIEW.",
                !source.contains("startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))"));
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
