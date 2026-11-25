package org.adaway.model.update;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Source guards complement the receiver's Android behavioral tests. */
public class ApkDownloadReceiverContractTest {
    @Test
    public void completionRegistrationAcceptsProviderUidAndCancelsOldReceiver() throws IOException {
        String model = source("UpdateModel.java");
        assertTrue(model.contains("ContextCompat.RECEIVER_EXPORTED"));
        assertFalse(model.contains("ContextCompat.RECEIVER_NOT_EXPORTED"));
        assertTrue(model.contains("new IntentFilter(ACTION_DOWNLOAD_COMPLETE)"));
        assertTrue(model.contains("receiver.handleTerminalDownload(this.context)"));
        String unregister = model.substring(model.indexOf("private void unregisterReceiver()"));
        assertTrue(unregister.indexOf("this.receiver.cancel()")
                < unregister.indexOf("this.context.unregisterReceiver(this.receiver)"));
        assertTrue(model.contains("this.pendingInstaller.cancel()"));
    }

    @Test
    public void exportedHintsStillRequireOwnedSuccessfulDownloadAndBothIntegrityChecks()
            throws IOException {
        String receiver = source("ApkDownloadReceiver.java");
        assertTrue(receiver.contains("ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())"));
        assertTrue(receiver.contains("this.downloadId == intent.getLongExtra("));
        assertTrue(receiver.contains("new DownloadManager.Query().setFilterById(this.downloadId)"));
        assertTrue(receiver.contains("cursor.getLong(idColumn) != this.downloadId"));
        String handler = receiver.substring(receiver.indexOf("synchronized boolean handleTerminalDownload"),
                receiver.indexOf("Cursor queryDownload"));
        assertTrue(handler.indexOf("getDownloadStatus(context)")
                < handler.indexOf("this.terminalHandled = true"));
        assertTrue(handler.contains("status != STATUS_SUCCESSFUL && status != STATUS_FAILED"));
        assertTrue(handler.indexOf("if (status == STATUS_SUCCESSFUL)")
                < handler.indexOf("getDownloadedApkUri(context)"));
        assertTrue(handler.indexOf("isVerifiedApk(context, apkUri)")
                < handler.indexOf("installApk(context, apkUri)"));
        assertTrue(receiver.contains("ApkIntegrityVerifier.matchesSha256(context, apkUri, this.expectedSha256)"));
        assertTrue(receiver.contains("&& ApkIntegrityVerifier.matchesSigningCertificateSha256("));
        assertTrue(receiver.contains("context, apkUri, this.expectedSigningCertificateSha256)"));
    }

    private static String source(String name) throws IOException {
        Path app = Paths.get("").toAbsolutePath();
        if (Files.isDirectory(app.resolve("app"))) {
            app = app.resolve("app");
        }
        return new String(Files.readAllBytes(app.resolve(
                "src/main/java/org/adaway/model/update/" + name)), StandardCharsets.UTF_8);
    }
}
