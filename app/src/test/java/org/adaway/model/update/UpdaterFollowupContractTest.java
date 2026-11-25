package org.adaway.model.update;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

/** Host-only wiring guards, not a substitute for Android behavioral execution. */
public class UpdaterFollowupContractTest {
    @Test
    public void productionReceiverCapturesExpiryFromSameVerifiedManifestAsHashes() throws Exception {
        String receiver = source("model/update/ApkDownloadReceiver.java");
        assertTrue(receiver.contains("this(downloadId, manifest.apkSha256, manifest.signingCertificateSha256,"));
        assertTrue(receiver.contains("manifest.expiresAt, Clock.systemUTC(), onTerminal, onState"));
        assertTrue(receiver.contains("private final Instant expiresAt;"));
        assertTrue(receiver.contains("java.util.Objects.requireNonNull(expiresAt)"));
        assertFalse(receiver.contains("public ApkDownloadReceiver("));
        String model = source("model/update/UpdateModel.java");
        assertTrue(model.contains("new ApkDownloadReceiver(\n                downloadId,\n                manifest,"));
        assertTrue(model.contains("manifest == null || !manifest.updateAvailable"));
    }

    @Test
    public void expiryIsInclusiveAndDropsPendingContinuation() throws Exception {
        String receiver = source("model/update/ApkDownloadReceiver.java");
        String check = receiver.substring(receiver.indexOf("private boolean requireFreshAuthorization()"),
                receiver.indexOf("boolean canRequestPackageInstalls(Context context)"));
        assertTrue(check.contains("!this.expiresAt.isAfter(this.clock.instant())"));
        assertTrue(check.contains("this.pendingInstallUri = null"));
        assertTrue(check.contains("InstallState.METADATA_EXPIRED"));
        String resume = receiver.substring(receiver.indexOf("synchronized void resumePendingInstall"),
                receiver.indexOf("private boolean requireFreshAuthorization()"));
        assertTrue(resume.indexOf("!requireFreshAuthorization()")
                < resume.indexOf("isVerifiedApk(context, apkUri)"));
        assertTrue(resume.contains("installApk(context, apkUri)"));
        String install = receiver.substring(receiver.indexOf("synchronized void installApk"),
                receiver.indexOf("private void reportLaunchFailure()"));
        assertTrue(install.indexOf("!requireFreshAuthorization()")
                < install.indexOf("new Intent(ACTION_INSTALL_PACKAGE)"));
    }

    @Test
    public void finalHandoffChecksExpiryAfterActivityResolution() throws Exception {
        String receiver = source("model/update/ApkDownloadReceiver.java");
        String launch = receiver.substring(receiver.indexOf("boolean startActivityIfResolvable"));
        assertTrue(launch.indexOf("resolveActivity(context.getPackageManager())")
                < launch.indexOf("!requireFreshAuthorization()"));
        assertTrue(launch.indexOf("!requireFreshAuthorization()")
                < launch.indexOf("context.startActivity(intent)"));
    }

    @Test
    public void pollingCannotClaimInstallSuccessOrClobberReceiverOutcome() throws Exception {
        String viewModel = source("ui/update/UpdateViewModel.java");
        assertFalse(viewModel.contains("new CompleteDownloadStatus()"));
        assertTrue(viewModel.contains("this.updateModel.handleDownloadCompletion(downloadId)"));
        String publish = viewModel.substring(viewModel.indexOf("private void postProgress"));
        assertTrue(publish.contains("this.trackedDownload.get() == downloadId"));
        assertTrue(publish.contains("getCurrentInstallState() == InstallState.DOWNLOADING"));
        assertTrue(viewModel.contains("this.updateModel.getInstallState()"));
    }

    @Test
    public void retryAndRefetchAreExplicitAndAllNewStatusStringsExist() throws Exception {
        String activity = source("ui/update/UpdateActivity.java");
        assertTrue(activity.contains("retry ? VISIBLE : INVISIBLE"));
        assertTrue(activity.contains("retry ? GONE : VISIBLE"));
        String status = source("ui/update/InstallDownloadStatus.java");
        assertTrue(status.contains("R.string.update_check_again_button"));
        String viewModel = source("ui/update/UpdateViewModel.java");
        assertTrue(viewModel.contains("this.updateModel.prepareManifestRefresh()"));
        assertTrue(viewModel.contains("this.updateModel::refreshManifest"));
        Path strings = app().resolve("src/main/res/values/strings_update.xml");
        org.w3c.dom.Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(strings.toFile());
        java.util.Set<String> names = new java.util.HashSet<>();
        org.w3c.dom.NodeList nodes = document.getElementsByTagName("string");
        for (int i = 0; i < nodes.getLength(); i++) {
            names.add(((org.w3c.dom.Element) nodes.item(i)).getAttribute("name"));
        }
        Matcher references = Pattern.compile("R\\.string\\.(update_[a-z_]+)").matcher(status);
        while (references.find()) {
            assertTrue("Missing string: " + references.group(1), names.contains(references.group(1)));
        }
    }

    @Test
    public void installerOpenedRetainsRetryWithoutAutomaticRelaunch() throws Exception {
        String receiver = source("model/update/ApkDownloadReceiver.java");
        assertTrue(receiver.contains("this.onState.accept(InstallState.INSTALLER_OPENED)"));
        assertFalse(receiver.contains("InstallState.INSTALLED"));
        String install = receiver.substring(receiver.indexOf("Intent install = new Intent"),
                receiver.indexOf("private void reportLaunchFailure()"));
        assertTrue(install.indexOf("this.pendingInstallUri = null")
                < install.indexOf("startActivityIfResolvable"));
        String status = source("ui/update/InstallDownloadStatus.java");
        String retry = status.substring(status.indexOf("boolean canRetry()"), status.indexOf("int actionLabel()"));
        assertFalse(retry.contains("INSTALLER_OPENED"));
    }

    @Test
    public void retryUiTestUsesActualLayoutIdsNotBindingPropertyNames() throws Exception {
        org.w3c.dom.Document layout = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(app().resolve("src/main/res/layout/update_actity.xml").toFile());
        java.util.Set<String> ids = new java.util.HashSet<>();
        org.w3c.dom.NodeList nodes = layout.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            String id = ((org.w3c.dom.Element) nodes.item(i)).getAttribute("android:id");
            if (id.startsWith("@+id/")) {
                ids.add(id.substring(5));
            }
        }
        String uiTest = new String(Files.readAllBytes(app().resolve(
                "src/androidTest/java/org/adaway/ui/update/UpdateRetryUiInstrumentedTest.java")),
                StandardCharsets.UTF_8);
        Matcher references = Pattern.compile("R\\.id\\.([a-zA-Z0-9_]+)").matcher(uiTest);
        while (references.find()) {
            assertTrue("Missing layout ID: " + references.group(1), ids.contains(references.group(1)));
        }
    }

    private static String source(String name) throws IOException {
        return new String(Files.readAllBytes(app().resolve("src/main/java/org/adaway/" + name)),
                StandardCharsets.UTF_8);
    }

    private static Path app() {
        Path path = Paths.get("").toAbsolutePath();
        return Files.isDirectory(path.resolve("app")) ? path.resolve("app") : path;
    }
}
