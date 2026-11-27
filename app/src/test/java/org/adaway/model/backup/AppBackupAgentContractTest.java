package org.adaway.model.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.adaway.util.Constants;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

public class AppBackupAgentContractTest {
    private static final String ROOT_EXCLUSION = "<exclude domain=\"root\" path=\".\"/>";
    private static final String ROOT_INCLUSION = "<include domain=\"root\" path=\".\"/>";

    @Test
    public void manifestKeepsBackupAgentEligibleAndConstrained() throws Exception {
        String manifest = readRepoFile("app/src/main/AndroidManifest.xml");
        String dataExtractionRules = readRepoFile(
                "app/src/main/res/xml/data_extraction_rules.xml");
        String fullBackupContent = readRepoFile("app/src/main/res/xml/full_backup_content.xml");

        assertApplicationBackupAttributes(manifest);
        assertBackupRootExcluded(dataExtractionRules, fullBackupContent);
    }

    private static void assertApplicationBackupAttributes(String manifest) throws Exception {
        Element app = (Element) XPathFactory.newInstance().newXPath().evaluate(
                "/manifest/application", parseXml(manifest), XPathConstants.NODE);
        assertNotNull("Manifest must declare the application.", app);
        String android = "http://schemas.android.com/apk/res/android";
        assertEquals("true", app.getAttributeNS(android, "allowBackup"));
        assertEquals(".model.backup.AppBackupAgent", app.getAttributeNS(android, "backupAgent"));
        assertEquals("false", app.getAttributeNS(android, "fullBackupOnly"));
        assertEquals("@xml/data_extraction_rules", app.getAttributeNS(android, "dataExtractionRules"));
        assertEquals("@xml/full_backup_content", app.getAttributeNS(android, "fullBackupContent"));
    }

    @Test
    public void manifestBackupAttributesMustBelongToApplication() throws Exception {
        String manifest = "<manifest xmlns:a='http://schemas.android.com/apk/res/android'>"
                + "<application a:allowBackup='true' a:backupAgent='.model.backup.AppBackupAgent'"
                + " a:fullBackupOnly='false' a:dataExtractionRules='@xml/data_extraction_rules'"
                + " a:fullBackupContent='@xml/full_backup_content'/></manifest>";
        assertApplicationBackupAttributes(manifest);
        String misplaced = manifest.replace("a:allowBackup='true'", "a:allowBackup='false'")
                .replace("</manifest>", "<!-- android:allowBackup=\"true\" --> </manifest>");
        assertThrows(AssertionError.class, () -> assertApplicationBackupAttributes(misplaced));
    }

    @Test
    public void backupExclusionsRejectCloudIncludeSubstitution() {
        assertThrows(AssertionError.class, () -> assertBackupRootExcluded(
                dataExtractionRules(ROOT_INCLUSION, ROOT_EXCLUSION),
                fullBackupContent(ROOT_EXCLUSION)));
    }

    @Test
    public void backupExclusionsRejectDeviceTransferIncludeSubstitution() {
        assertThrows(AssertionError.class, () -> assertBackupRootExcluded(
                dataExtractionRules(ROOT_EXCLUSION, ROOT_INCLUSION),
                fullBackupContent(ROOT_EXCLUSION)));
    }

    @Test
    public void backupExclusionsRejectLegacyIncludeSubstitution() {
        assertThrows(AssertionError.class, () -> assertBackupRootExcluded(
                dataExtractionRules(ROOT_EXCLUSION, ROOT_EXCLUSION),
                fullBackupContent(ROOT_INCLUSION)));
    }

    @Test
    public void backupExclusionsRejectMissingCloudExclusion() {
        assertThrows(AssertionError.class, () -> assertBackupRootExcluded(
                dataExtractionRules("", ROOT_EXCLUSION), fullBackupContent(ROOT_EXCLUSION)));
    }

    @Test
    public void backupExclusionsRejectMissingDeviceTransferExclusion() {
        assertThrows(AssertionError.class, () -> assertBackupRootExcluded(
                dataExtractionRules(ROOT_EXCLUSION, ""), fullBackupContent(ROOT_EXCLUSION)));
    }

    @Test
    public void backupExclusionsRequireEachSection() {
        String withoutCloud = "<data-extraction-rules><device-transfer>" + ROOT_EXCLUSION
                + "</device-transfer></data-extraction-rules>";
        String withoutTransfer = "<data-extraction-rules><cloud-backup>" + ROOT_EXCLUSION
                + "</cloud-backup></data-extraction-rules>";

        assertThrows(AssertionError.class, () -> assertBackupRootExcluded(
                withoutCloud, fullBackupContent(ROOT_EXCLUSION)));
        assertThrows(AssertionError.class, () -> assertBackupRootExcluded(
                withoutTransfer, fullBackupContent(ROOT_EXCLUSION)));
        assertThrows(AssertionError.class, () -> assertBackupRootExcluded(
                dataExtractionRules(ROOT_EXCLUSION, ROOT_EXCLUSION), fullBackupContent("")));
    }

    @Test
    public void backupExclusionsAcceptEquivalentXmlFormatting() throws Exception {
        String exclusion = "<exclude\n path='.' domain='root' />";
        assertBackupRootExcluded(dataExtractionRules(exclusion, exclusion),
                fullBackupContent(exclusion));
    }

    @Test
    public void pendingSourceApplyCheckpointExcludedFromEveryPlatformBackupMode() throws Exception {
        Document extractionRules = parseXml(readRepoFile(
                "app/src/main/res/xml/data_extraction_rules.xml"));
        Document legacyRules = parseXml(readRepoFile(
                "app/src/main/res/xml/full_backup_content.xml"));
        String exclusion = "/exclude[@domain='sharedpref' "
                + "and @path='" + Constants.PREFS_PENDING_SOURCE_APPLY + ".xml']";
        for (String section : new String[]{"cloud-backup", "device-transfer"}) {
            assertTrue(section + " must explicitly exclude the local pending-Apply checkpoint.",
                    (Boolean) XPathFactory.newInstance().newXPath().evaluate(
                            "/data-extraction-rules/" + section + exclusion,
                            extractionRules, XPathConstants.BOOLEAN));
        }
        assertTrue("Legacy full backup must explicitly exclude the local pending-Apply checkpoint.",
                (Boolean) XPathFactory.newInstance().newXPath().evaluate(
                        "/full-backup-content" + exclusion, legacyRules, XPathConstants.BOOLEAN));
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
        assertFalse("Key-value backup must not register the local pending-Apply preferences.",
                source.contains("PREFS_PENDING_SOURCE_APPLY")
                        || source.contains("\"" + Constants.PREFS_PENDING_SOURCE_APPLY + "\""));
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

    private static void assertBackupRootExcluded(String dataExtractionRules,
            String fullBackupContent) throws Exception {
        Document extractionRules = parseXml(dataExtractionRules);
        assertRootExcluded(extractionRules, "/data-extraction-rules/cloud-backup");
        assertRootExcluded(extractionRules, "/data-extraction-rules/device-transfer");
        assertRootExcluded(parseXml(fullBackupContent), "/full-backup-content");
    }

    private static void assertRootExcluded(Document rules, String sectionPath) throws Exception {
        assertTrue(sectionPath + " must directly exclude domain=root path=.",
                (Boolean) XPathFactory.newInstance().newXPath().evaluate(
                        sectionPath + "/exclude[@domain='root' and @path='.']",
                        rules, XPathConstants.BOOLEAN));
    }

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static String dataExtractionRules(String cloudRules, String transferRules) {
        return "<data-extraction-rules><cloud-backup>" + cloudRules
                + "</cloud-backup><device-transfer>" + transferRules
                + "</device-transfer></data-extraction-rules>";
    }

    private static String fullBackupContent(String rules) {
        return "<full-backup-content>" + rules + "</full-backup-content>";
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
