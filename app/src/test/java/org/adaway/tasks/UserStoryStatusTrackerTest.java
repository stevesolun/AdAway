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
