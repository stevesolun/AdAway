package org.adaway.model.backup;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AppBackupAgentContractTest {
    @Test
    public void manifestKeepsBackupAgentEligibleAndConstrained() throws Exception {
        String manifest = readRepoFile("app/src/main/AndroidManifest.xml");
        String dataExtractionRules = readRepoFile(
                "app/src/main/res/xml/data_extraction_rules.xml");
        String fullBackupContent = readRepoFile("app/src/main/res/xml/full_backup_content.xml");

        assertTrue("Declared BackupAgent must be eligible for platform backup.",
                manifest.contains("android:allowBackup=\"true\"") &&
                        manifest.contains(
                                "android:backupAgent=\".model.backup.AppBackupAgent\"") &&
                        manifest.contains("android:fullBackupOnly=\"false\""));
        assertTrue("Manifest must keep platform backup constrained by explicit XML rules.",
                manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\"") &&
                        manifest.contains("android:fullBackupContent=\"@xml/full_backup_content\""));
        assertTrue("Cloud and device-transfer backup must not include arbitrary root files.",
                dataExtractionRules.contains("<cloud-backup>") &&
                        dataExtractionRules.contains("<device-transfer>") &&
                        dataExtractionRules.contains("domain=\"root\"") &&
                        dataExtractionRules.contains("path=\".\"") &&
                        fullBackupContent.contains("domain=\"root\"") &&
                        fullBackupContent.contains("path=\".\""));
    }

    @Test
    public void backupAgentRegistersPreferencesAndRulesHelpers() throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/model/backup/AppBackupAgent.java");

        assertTrue("BackupAgent must register the app SharedPreferences helper.",
                source.contains("addHelper(PREFS_BACKUP_KEY, " +
                        "new SharedPreferencesBackupHelper(this, PREFS_NAME))"));
        assertTrue("BackupAgent must register the user-rules file helper.",
                source.contains("addHelper(RULES_BACKUP_KEY, new SourceBackupHelper(this))") &&
                        source.contains("private static class SourceBackupHelper " +
                                "extends FileBackupHelper"));
        assertTrue("Rules helper must use a stable private JSON filename.",
                source.contains("private static final String RULES_FILE_NAME = " +
                        "\"rules-backup.json\"") &&
                        source.contains("new File(this.context.getFilesDir(), RULES_FILE_NAME)"));
    }

    @Test
    public void rulesBackupOrdersExportAndRestoreSideEffects() throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/model/backup/AppBackupAgent.java");

        int exportIndex = source.indexOf("BackupExporter.exportBackup(this.context, " +
                "getRulesFileUri())");
        int performBackupIndex = source.indexOf("super.performBackup(oldState, data, newState)");
        int restoreFileIndex = source.indexOf("super.restoreEntity(data)");
        int importIndex = source.indexOf("BackupImporter.importBackup(this.context, " +
                "getRulesFileUri())");

        assertTrue("Rules export must refresh the JSON file before FileBackupHelper backs it up.",
                exportIndex >= 0 && performBackupIndex > exportIndex);
        assertTrue("Rules restore must let FileBackupHelper materialize the file before import.",
                restoreFileIndex >= 0 && importIndex > restoreFileIndex);
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
