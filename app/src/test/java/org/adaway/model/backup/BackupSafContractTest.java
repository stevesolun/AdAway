package org.adaway.model.backup;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BackupSafContractTest {
    @Test
    public void prefsBackupRestoreUsesSafJsonDocumentContracts() throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/prefs/PrefsBackupRestoreFragment.java");

        assertTrue("Backup export must create a JSON document through SAF.",
                source.contains("new CreateDocument(JSON_MIME_TYPE)") &&
                        source.contains("private static final String JSON_MIME_TYPE = " +
                                "\"application/json\""));
        assertTrue("Backup export must suggest the stable AdAway backup filename.",
                source.contains("private static final String BACKUP_FILE_NAME = " +
                        "\"adaway-backup.json\"") &&
                        source.contains("this.exportActivityLauncher.launch(BACKUP_FILE_NAME)"));
        assertTrue("Backup restore must use SAF open-document and mark it openable.",
                source.contains("new OpenDocument()") &&
                        source.contains(".addCategory(CATEGORY_OPENABLE)"));
        assertTrue("Backup restore must prefer JSON on modern Android.",
                source.contains("mimeTypes = new String[]{JSON_MIME_TYPE};"));
        assertTrue("Backup restore must keep compatibility fallbacks for older pickers.",
                source.contains("mimeTypes = new String[]{\"*/*\"};") &&
                        source.contains("mimeTypes = new String[]{JSON_MIME_TYPE, " +
                                "\"application/octet-stream\"};"));
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
