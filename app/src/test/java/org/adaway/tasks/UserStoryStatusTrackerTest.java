package org.adaway.tasks;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class UserStoryStatusTrackerTest {
    private static final List<String> EXPECTED_HEADER = List.of(
            "story_id",
            "area",
            "feature",
            "user_story",
            "expected_behavior",
            "code_evidence",
            "existing_test_evidence",
            "status",
            "test_state",
            "priority",
            "risk_notes",
            "error_id",
            "error_notes",
            "fix_status",
            "retest_status"
    );
    private static final Pattern STORY_ID = Pattern.compile("[A-Z]+-[0-9]{3}");
    private static final Pattern BENCHMARK_EVIDENCE_PATH =
            Pattern.compile("tasks/benchmarks/[A-Za-z0-9._/-]+");
    private static final Set<String> VALID_PRIORITIES = Set.of("P0", "P1", "P2");

    @Test
    public void trackerHasStableShapeAndUniqueStoryIds() throws IOException {
        List<Row> rows = readRows();
        Set<String> seenIds = new HashSet<>();

        assertTrue("Tracker should keep broad feature coverage.", rows.size() >= 90);
        for (Row row : rows) {
            assertTrue("Story id should follow AREA-000 format at line " + row.lineNumber,
                    STORY_ID.matcher(row.storyId()).matches());
            assertTrue("Duplicate story id: " + row.storyId(), seenIds.add(row.storyId()));
            assertRequired(row, "area", row.area());
            assertRequired(row, "feature", row.feature());
            assertRequired(row, "user_story", row.userStory());
            assertRequired(row, "expected_behavior", row.expectedBehavior());
            assertRequired(row, "code_evidence", row.codeEvidence());
            assertRequired(row, "existing_test_evidence", row.existingTestEvidence());
            assertRequired(row, "status", row.status());
            assertRequired(row, "test_state", row.testState());
            assertRequired(row, "priority", row.priority());
            assertRequired(row, "risk_notes", row.riskNotes());
            assertRequired(row, "fix_status", row.fixStatus());
            assertRequired(row, "retest_status", row.retestStatus());
            assertTrue("Unknown priority at line " + row.lineNumber + ": " + row.priority(),
                    VALID_PRIORITIES.contains(row.priority()));
        }
    }

    @Test
    public void p0RowsNeedExecutableEvidenceOrExplicitOpenState() throws IOException {
        for (Row row : readRows()) {
            if (!"P0".equals(row.priority())) {
                continue;
            }

            assertRequired(row, "existing_test_evidence", row.existingTestEvidence());
            assertRequired(row, "test_state", row.testState());
            assertRequired(row, "retest_status", row.retestStatus());

            if (isFullyCovered(row)) {
                assertFalse("Fully covered P0 rows need concrete retest evidence: " +
                                row.storyId(),
                        isPlaceholderEvidence(row.retestStatus()));
                assertFalse("Fully covered P0 rows need concrete test-state evidence: " +
                                row.storyId(),
                        isPlaceholderEvidence(row.testState()));
            } else {
                assertTrue("Open P0 rows should make the remaining proof obvious: " +
                                row.storyId(),
                        containsOpenMarker(row.status()) ||
                                containsOpenMarker(row.testState()) ||
                                containsOpenMarker(row.retestStatus()) ||
                                containsOpenMarker(row.riskNotes()));
            }
        }
    }

    @Test
    public void externalReleaseGatesStayOpenUntilRealArtifactsExist() throws IOException {
        Map<String, Row> rows = rowsById();

        assertOpenGate(rows, "RUNTIME-007", "rooted");
        assertOpenGate(rows, "UPDATE-002", "device install");
        assertOpenGate(rows, "UPDATE-004", "directrelease");
        assertOpenGate(rows, "REL-001", "legal/provenance");
        assertOpenGate(rows, "REL-002", "tagged release");
        assertOpenGate(rows, "REL-003", "physical device");
        assertOpenGate(rows, "REL-004", "human review");
        assertOpenGate(rows, "REL-005", "real release artifacts");
    }

    @Test
    public void trackerBenchmarkEvidencePathsExist() throws IOException {
        Path repo = repoDir();

        for (Row row : readRows()) {
            Matcher matcher = BENCHMARK_EVIDENCE_PATH.matcher(row.allEvidence());
            while (matcher.find()) {
                String relativePath = trimTrailingPunctuation(matcher.group());
                assertTrue("Tracker evidence path should exist for " + row.storyId() +
                                ": " + relativePath,
                        Files.exists(repo.resolve(relativePath)));
            }
        }
    }

    @Test
    public void rel001StaysOpenWithFreshBoundaryReports() throws IOException {
        Row row = rowsById().get("REL-001");

        assertNotNull("REL-001 row should exist.", row);
        assertEquals("REL-001 should remain a P0 release/legal gate.", "P0", row.priority());
        assertFalse("REL-001 must not be marked covered by source/debug scans alone.",
                isFullyCovered(row));
        assertTrue("REL-001 should point at the fresh source-boundary evidence.",
                row.allEvidence().contains(
                        "tasks/benchmarks/2026-06-28-rel001-license-boundary-" +
                                "source-baseline-evidence.md"));
        assertTrue("REL-001 should point at the debug artifact-boundary evidence.",
                row.allEvidence().contains(
                        "tasks/benchmarks/2026-06-28-rel001-" +
                                "debug-artifact-boundary-evidence.md"));
        assertTrue("REL-001 should record debug APK/SBOM artifact-boundary counts.",
                row.testState().contains("development CycloneDX SBOM") &&
                        row.testState().contains("artifact-boundary checking") &&
                        row.testState().contains("1119 APK entries") &&
                        row.testState().contains("265 APK resources") &&
                        row.testState().contains("116 SBOM components") &&
                        row.testState().contains("Issues 0"));
        assertExitCode(
                "tasks/benchmarks/2026-06-28-rel001-license-boundary-gittracked.exitcode");
        assertExitCode(
                "tasks/benchmarks/2026-06-28-rel001-license-boundary-workingtree.exitcode");
        assertExitCode("tasks/benchmarks/2026-06-28-rel001-license-boundary-" +
                "gittracked-strict-source-archive.exitcode");
        assertTrue("REL-001 should keep MIT and artifact/legal boundaries open.",
                row.riskNotes().contains("MIT remains blocked") &&
                        row.riskNotes().contains("signed release APK/SBOM") &&
                        row.retestStatus().contains("legal/provenance"));
        assertTrue("REL-001 should record the debug artifact hashes.",
                row.retestStatus().contains(
                        "cc587365535bae924e7a12cd0f3c35b58fb6595320243c6f37b37580b1e26771") &&
                        row.retestStatus().contains(
                                "5aedaeef2b7137c6bce331d549a6815accb9c51b7a4bf0d758631711b3bbf8f4"));
    }

    @Test
    public void update004StaysOpenWithDirectReleaseDryRunEvidence() throws IOException {
        Row row = rowsById().get("UPDATE-004");

        assertNotNull("UPDATE-004 row should exist.", row);
        assertEquals("UPDATE-004 should remain a P0 direct release gate.", "P0",
                row.priority());
        assertFalse("UPDATE-004 must not be marked covered by an ephemeral dry run.",
                isFullyCovered(row));
        assertTrue("UPDATE-004 should point at the directRelease dry-run evidence.",
                row.allEvidence().contains(
                        "tasks/benchmarks/2026-06-28-update004-" +
                                "directrelease-dry-run-evidence.md"));
        assertTrue("UPDATE-004 should record release packaging and artifact-boundary proof.",
                row.testState().contains("assembleDirectRelease plus generateSbom") &&
                        row.testState().contains("847 APK entries") &&
                        row.testState().contains("202 APK resources") &&
                        row.testState().contains("105 SBOM components") &&
                        row.testState().contains("Issues 0"));
        assertTrue("UPDATE-004 should keep production install smoke open.",
                row.riskNotes().contains("production signed directRelease") &&
                        row.retestStatus().contains("signed directRelease install smoke remains open"));
    }

    @Test
    public void releaseHandoffRowsKeepVerifierEvidenceWithoutClosingExternalGates()
            throws IOException {
        Map<String, Row> rows = rowsById();

        assertReleaseHandoffGate(rows, "REL-002", "artifact verifier");
        assertReleaseHandoffGate(rows, "REL-003", "physical-smoke");
        assertReleaseHandoffGate(rows, "REL-004", "ux packet");
        assertReleaseHandoffGate(rows, "REL-005", "readiness aggregator");
    }

    @Test
    public void rel002StaysOpenWithDirectReleaseDryRunEvidence() throws IOException {
        Row row = rowsById().get("REL-002");

        assertNotNull("REL-002 row should exist.", row);
        assertEquals("REL-002 should remain a P0 release artifact gate.", "P0",
                row.priority());
        assertFalse("REL-002 must not be marked covered by an ephemeral dry run.",
                isFullyCovered(row));
        assertTrue("REL-002 should point at the directRelease dry-run evidence.",
                row.allEvidence().contains(
                        "tasks/benchmarks/2026-06-28-update004-" +
                                "directrelease-dry-run-evidence.md"));
        assertTrue("REL-002 should record the directRelease dry-run scope.",
                row.testState().contains("release packaging") &&
                        row.testState().contains("R8/minification") &&
                        row.testState().contains("release SBOM generation") &&
                        row.testState().contains("strict APK/SBOM artifact-boundary"));
        assertTrue("REL-002 should keep real signed artifact proof open.",
                row.riskNotes().contains("production signer certificate") &&
                        row.riskNotes().contains("signed manifest") &&
                        row.retestStatus().contains("tagged release artifact run remains open"));
    }

    @Test
    public void rel004StaysOpenWithFreshUxMatrixPacketEvidence() throws IOException {
        Row row = rowsById().get("REL-004");

        assertNotNull("REL-004 row should exist.", row);
        assertEquals("REL-004 should remain a P0 release gate.", "P0", row.priority());
        assertFalse("REL-004 must not be marked covered until human signoff exists.",
                isFullyCovered(row));
        assertTrue("REL-004 should point at the fresh UX matrix evidence.",
                row.allEvidence().contains(
                        "tasks/benchmarks/2026-06-28-rel004-ux-matrix-packet-evidence.md"));
        assertTrue("REL-004 should record the fresh packet size and hash.",
                row.testState().contains("40 screenshots") &&
                        row.testState().contains(
                                "a28fa2dc5740c603ae37fc358746a31773cb9d8384927871948de6abf311db19"));
        assertTrue("REL-004 should keep the human-review boundary explicit.",
                row.retestStatus().contains("Unchecked items 45") &&
                        row.retestStatus().contains("human UX signoff"));
    }

    @Test
    public void runtime010StaysClosedWithFreshFiveMillionScaleEvidence() throws IOException {
        Row row = rowsById().get("RUNTIME-010");

        assertNotNull("RUNTIME-010 row should exist.", row);
        assertEquals("Covered by connected benchmark", row.status());
        assertTrue("RUNTIME-010 should mention fresh 5M benchmark proof.",
                row.testState().contains("5M") &&
                        row.retestStatus().contains("5M") &&
                        row.retestStatus().contains("rootRows=4500000"));
    }

    @Test
    public void runtime009StaysClosedWithPreparedVpnLifecycleEvidence() throws IOException {
        Row row = rowsById().get("RUNTIME-009");

        assertNotNull("RUNTIME-009 row should exist.", row);
        assertEquals("RUNTIME-009 should stay a P1 lifecycle proof.", "P1", row.priority());
        assertEquals("Covered by connected prepared-device lifecycle proof", row.status());
        assertTrue("RUNTIME-009 should point at the prepared-device evidence file.",
                row.existingTestEvidence().contains(
                        "tasks/benchmarks/2026-06-28-runtime009-prepared-vpn-lifecycle-evidence.md"));
        assertTrue("RUNTIME-009 evidence file should be present.",
                Files.isRegularFile(repoDir().resolve(
                        "tasks/benchmarks/2026-06-28-runtime009-prepared-vpn-lifecycle-evidence.md")));
        assertTrue("RUNTIME-009 should name the prepared-device full lifecycle proof.",
                row.testState().contains("prepared API 34") &&
                        row.testState().contains("start stop resume") &&
                        row.retestStatus().contains("am instrument") &&
                        row.retestStatus().contains("OK (1 test)"));
        assertTrue("RUNTIME-009 should keep physical release smoke separate.",
                row.riskNotes().contains("REL-003"));
    }

    @Test
    public void systemContractRowsStayClosedWithoutOverclaimingPlatformDispatch()
            throws IOException {
        Map<String, Row> rows = rowsById();

        assertCoveredSystemContract(
                rows,
                "SYS-001",
                "Covered by connected tile contract",
                "systemui");
        assertCoveredSystemContract(
                rows,
                "SYS-004",
                "Covered by connected receiver contract",
                "release-smoke");
    }

    @Test
    public void notif002StaysClosedWithNonMutatingConnectedAlertProof() throws IOException {
        Row row = rowsById().get("NOTIF-002");

        assertNotNull("NOTIF-002 row should exist.", row);
        assertEquals("NOTIF-002 should stay a P2 notification contract.", "P2",
                row.priority());
        assertEquals("Covered by JVM and connected notification alert proof", row.status());
        assertTrue("NOTIF-002 should point at the connected notification proof.",
                row.existingTestEvidence().contains(
                        "app/src/androidTest/java/org/adaway/helper/" +
                                "NotificationHelperChannelInstrumentedTest.java") &&
                        row.existingTestEvidence().contains(
                                "tasks/benchmarks/2026-06-28-notif002-" +
                                        "nonmutating-alert-proof.md"));
        assertTrue("NOTIF-002 should name the app-owned alert contract.",
                row.testState().contains("distinct actionable host/app notification builders") &&
                        row.testState().contains("current notification-permission posting"));
        assertTrue("NOTIF-002 should keep permission UX in the safer rows.",
                row.riskNotes().contains("NOTIF-003/PREF-010") &&
                        row.riskNotes().contains("avoids permission mutation"));
        assertTrue("NOTIF-002 should record the focused JVM and connected retest.",
                row.retestStatus().contains("NotificationHelperContractTest") &&
                        row.retestStatus().contains("UserStoryStatusTrackerTest") &&
                        row.retestStatus().contains("NotificationHelperChannelInstrumentedTest") &&
                        row.retestStatus().contains("4 tests") &&
                        row.retestStatus().contains("adaway-api34-16g"));
    }

    @Test
    public void pref013StaysClosedWithPlatformBackupRestoreEvidence() throws IOException {
        Row row = rowsById().get("PREF-013");

        assertNotNull("PREF-013 row should exist.", row);
        assertEquals("PREF-013 should stay a P2 backup contract.", "P2", row.priority());
        assertEquals("Covered by connected platform backup restore smoke", row.status());
        assertTrue("PREF-013 should point at the phase-gated platform restore proof.",
                row.existingTestEvidence().contains(
                        "app/src/androidTest/java/org/adaway/model/backup/" +
                                "AppBackupAgentPlatformInstrumentedTest.java") &&
                        row.existingTestEvidence().contains(
                                "tasks/benchmarks/2026-06-28-pref013-" +
                                        "platform-backup-restore-evidence.md"));
        assertTrue("PREF-013 should record the real bmgr local transport path.",
                row.testState().contains("bmgr backupnow") &&
                        row.testState().contains("bmgr restore 1 org.adaway") &&
                        row.testState().contains("blocked/allowed/redirected user rules"));
        assertTrue("PREF-013 should keep cloud/provider availability out of the app claim.",
                row.riskNotes().contains("local transport") &&
                        row.riskNotes().contains("cloud account") &&
                        row.riskNotes().contains("OS-owned"));
        assertTrue("PREF-013 should record seed/assert instrumentation and shell restore.",
                row.retestStatus().contains("platformBackupPhase=seed") &&
                        row.retestStatus().contains("platformBackupPhase=assert") &&
                        row.retestStatus().contains("adaway-api34-16g"));
        assertTrue("PREF-013 evidence file should be present.",
                Files.isRegularFile(repoDir().resolve(
                        "tasks/benchmarks/2026-06-28-pref013-" +
                                "platform-backup-restore-evidence.md")));
    }

    private static List<Row> readRows() throws IOException {
        Path tracker = repoDir().resolve("tasks/user-story-status.tsv");
        List<String> lines = Files.readAllLines(tracker, StandardCharsets.UTF_8);
        assertFalse("Tracker must not be empty.", lines.isEmpty());
        assertEquals("Header changed unexpectedly.", EXPECTED_HEADER,
                List.of(lines.get(0).split("\t", -1)));

        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] fields = lines.get(i).split("\t", -1);
            assertEquals("Wrong field count at line " + (i + 1), EXPECTED_HEADER.size(),
                    fields.length);
            rows.add(new Row(i + 1, fields));
        }
        return rows;
    }

    private static Map<String, Row> rowsById() throws IOException {
        Map<String, Row> rows = new HashMap<>();
        for (Row row : readRows()) {
            rows.put(row.storyId(), row);
        }
        return rows;
    }

    private static void assertOpenGate(Map<String, Row> rows, String storyId, String marker) {
        Row row = rows.get(storyId);

        assertNotNull(storyId + " row should exist.", row);
        assertEquals(storyId + " should remain P0 until external proof lands.", "P0",
                row.priority());
        assertFalse(storyId + " must not be marked fully covered while external proof is open.",
                isFullyCovered(row));
        assertTrue(storyId + " should name the remaining external proof.",
                row.allEvidence().toLowerCase().contains(marker));
    }

    private static void assertReleaseHandoffGate(
            Map<String, Row> rows,
            String storyId,
            String verifierMarker) {
        Row row = rows.get(storyId);

        assertNotNull(storyId + " row should exist.", row);
        assertEquals(storyId + " should remain a P0 release gate.", "P0", row.priority());
        assertFalse(storyId + " must not be marked fully covered before upstream reports exist.",
                isFullyCovered(row));
        assertTrue(storyId + " should point at the release-gate handoff evidence.",
                row.allEvidence().contains(
                        "tasks/benchmarks/2026-06-28-release-gate-handoff-evidence.md"));
        assertTrue(storyId + " should name the verifier contract.",
                row.status().toLowerCase().contains(verifierMarker));
        boolean hasScriptRetest = row.retestStatus().contains("ReleaseReadinessScriptTest") &&
                row.retestStatus().contains("UserStoryStatusTrackerTest");
        boolean hasUxPacketRetest = row.retestStatus().contains("run-ux-matrix") &&
                row.retestStatus().contains("verify-ux-signoff");
        assertTrue(storyId + " should record the focused verifier retest.",
                hasScriptRetest || hasUxPacketRetest);
        assertFalse(storyId + " should not regress to vague retest placeholders.",
                isPlaceholderEvidence(row.retestStatus()));
    }

    private static void assertCoveredSystemContract(
            Map<String, Row> rows,
            String storyId,
            String expectedStatus,
            String externalMarker) {
        Row row = rows.get(storyId);

        assertNotNull(storyId + " row should exist.", row);
        assertEquals(storyId + " should stay a P1 system contract.", "P1", row.priority());
        assertEquals(expectedStatus, row.status());
        assertFalse(storyId + " needs concrete connected evidence.",
                isPlaceholderEvidence(row.retestStatus()));
        assertTrue(storyId + " should keep the external platform caveat.",
                row.riskNotes().toLowerCase().contains(externalMarker));
    }

    private static boolean isFullyCovered(Row row) {
        return row.status().startsWith("Covered by connected") ||
                row.status().startsWith("Covered by tests") ||
                row.status().startsWith("Covered by route-plan") ||
                row.status().startsWith("Covered by parser") ||
                row.status().startsWith("Fixed and covered");
    }

    private static boolean containsOpenMarker(String value) {
        String lower = value.toLowerCase();
        return lower.contains("partial") ||
                lower.contains("needs") ||
                lower.contains("not retested") ||
                lower.contains("not started") ||
                lower.contains("open") ||
                lower.contains("external") ||
                lower.contains("manual") ||
                lower.contains("human") ||
                lower.contains("physical") ||
                lower.contains("legal");
    }

    private static boolean isPlaceholderEvidence(String value) {
        String lower = value.toLowerCase();
        return lower.equals("not retested") ||
                lower.equals("not started") ||
                lower.startsWith("needs ") ||
                lower.contains("none found");
    }

    private static String trimTrailingPunctuation(String value) {
        String trimmed = value;
        while (trimmed.endsWith(".") || trimmed.endsWith(",") || trimmed.endsWith(";") ||
                trimmed.endsWith(":") || trimmed.endsWith(")") || trimmed.endsWith("`")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static void assertExitCode(String relativePath) throws IOException {
        Path path = repoDir().resolve(relativePath);

        assertTrue("Exitcode artifact should exist: " + relativePath,
                Files.isRegularFile(path));
        String value = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
        assertEquals("Exitcode artifact should record success: " + relativePath,
                "0", value);
    }

    private static void assertRequired(Row row, String fieldName, String value) {
        assertFalse("Missing " + fieldName + " for " + row.storyId() +
                " at line " + row.lineNumber, value.trim().isEmpty());
    }

    private static Path repoDir() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("settings.gradle")) &&
                    Files.isRegularFile(dir.resolve("tasks/user-story-status.tsv"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate repository root from " +
                System.getProperty("user.dir"));
    }

    private record Row(int lineNumber, String[] fields) {
        String storyId() {
            return fields[0];
        }

        String area() {
            return fields[1];
        }

        String feature() {
            return fields[2];
        }

        String userStory() {
            return fields[3];
        }

        String expectedBehavior() {
            return fields[4];
        }

        String codeEvidence() {
            return fields[5];
        }

        String existingTestEvidence() {
            return fields[6];
        }

        String status() {
            return fields[7];
        }

        String testState() {
            return fields[8];
        }

        String priority() {
            return fields[9];
        }

        String riskNotes() {
            return fields[10];
        }

        String fixStatus() {
            return fields[13];
        }

        String retestStatus() {
            return fields[14];
        }

        String allEvidence() {
            return String.join(" ", fields);
        }
    }
}
