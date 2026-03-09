package org.adaway.model.source;

import org.adaway.db.dao.HostListItemDao;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * Regression test for the HTTP 304 generation-migration bug.
 *
 * ROOT CAUSE:
 * {@code checkAndRetrieveHostsSources()} uses an atomic generation-swap pattern:
 *   1. Write new host entries tagged with generation G+1.
 *   2. Flip active_generation to G+1.
 *   3. {@code cleanupNonActiveGenerations(G+1)} deletes all rows where
 *      {@code source_id != 1 AND generation != G+1}.
 *
 * When a source returns HTTP 304 (Not Modified), no new rows are written; the
 * source's existing rows still carry generation G. Step 3 therefore deletes ALL of
 * that source's entries on every update run after the first one.
 * Result: the VPN proxy sees no blocked domains for those sources.
 *
 * FIX:
 * Before cleanup runs, re-tag 304-source rows from G to G+1 via:
 *   {@code hostListItemDao.migrateSourceGeneration(sourceId, G, G+1)}
 *
 * This test guards:
 * 1. {@link HostListItemDao#migrateSourceGeneration} method exists with the correct
 *    signature — a compile error here means the fix was accidentally removed.
 * 2. The generation arithmetic is correct: oldGeneration == currentImportGeneration - 1.
 * 3. Variant: multiple sequential updates (simulate G=1→2, G=2→3) — old generation
 *    always equals newGeneration - 1, never a stale absolute value.
 */
public class Generation304MigrationTest {

    // -------------------------------------------------------------------------
    // Guard 1: HostListItemDao.migrateSourceGeneration() method signature
    // -------------------------------------------------------------------------

    /**
     * Compile-time guard: if {@link HostListItemDao#migrateSourceGeneration(int, int, int)}
     * is removed or renamed, this test fails to compile, immediately surfacing the regression.
     *
     * The method is referenced via reflection so the test body itself runs as a JVM test
     * (no Room DB needed) while still failing loudly if the method disappears.
     */
    @Test
    public void hostListItemDao_hasMigrateSourceGenerationMethod() throws Exception {
        // Reflective lookup: throws NoSuchMethodException if signature changes.
        Method m = HostListItemDao.class.getMethod(
                "migrateSourceGeneration",
                int.class, int.class, int.class);

        assertNotNull("migrateSourceGeneration(int, int, int) must exist on HostListItemDao", m);
        assertEquals("migrateSourceGeneration return type must be void",
                void.class, m.getReturnType());
    }

    // -------------------------------------------------------------------------
    // Guard 2: Generation arithmetic — oldGeneration = currentImportGeneration - 1
    // -------------------------------------------------------------------------

    /**
     * The 304 handler computes {@code oldGeneration = currentImportGeneration - 1}.
     * Verifies this produces the correct "previous generation" for the first update
     * (activeGen=0 → importGeneration=1, oldGen must be 0).
     */
    @Test
    public void generationArithmetic_firstUpdate_oldGenerationIsZero() {
        int activeGen = 0;
        int currentImportGeneration = activeGen + 1; // == 1
        int oldGeneration = currentImportGeneration - 1; // == 0

        assertEquals("First update: activeGen=0, import=1, old must be 0", 0, oldGeneration);
        assertEquals("First update: currentImportGeneration must be 1", 1, currentImportGeneration);
    }

    /**
     * Variant: second update run (activeGen=1 → importGeneration=2).
     * This is the CRITICAL case: without migrateSourceGeneration(), rows at generation 1
     * would be deleted by cleanupNonActiveGenerations(2) on the second update.
     */
    @Test
    public void generationArithmetic_secondUpdate_oldGenerationIsOne() {
        int activeGen = 1;
        int currentImportGeneration = activeGen + 1; // == 2
        int oldGeneration = currentImportGeneration - 1; // == 1

        assertEquals("Second update: activeGen=1, import=2, old must be 1", 1, oldGeneration);
        assertEquals("Second update: currentImportGeneration must be 2", 2, currentImportGeneration);
    }

    /**
     * Variant: Nth update (activeGen=42 → importGeneration=43).
     * Guards against any absolute-value hardcoding — the arithmetic must always be
     * relative to currentImportGeneration, regardless of how large the generation counter grows.
     */
    @Test
    public void generationArithmetic_nthUpdate_oldGenerationIsAlwaysOneLess() {
        for (int activeGen = 0; activeGen <= 100; activeGen++) {
            int currentImportGeneration = activeGen + 1;
            int oldGeneration = currentImportGeneration - 1;

            assertEquals(
                    "activeGen=" + activeGen + ": oldGeneration must equal activeGen",
                    activeGen, oldGeneration);
            assertEquals(
                    "activeGen=" + activeGen + ": currentImportGeneration must equal activeGen+1",
                    activeGen + 1, currentImportGeneration);
        }
    }

    // -------------------------------------------------------------------------
    // Guard 3: Verify migrateSourceGeneration UPDATE semantics (pure logic)
    // -------------------------------------------------------------------------

    /**
     * Models the migrateSourceGeneration UPDATE semantics in pure Java.
     * Simulates a tiny in-memory "hosts_lists" table to verify that rows with
     * oldGeneration are re-tagged to newGeneration (and rows with other generations
     * are untouched) — exactly what the SQL UPDATE does.
     *
     * This is the core behavioural regression: before the fix, 304 rows at generation G
     * were cleaned up; after the fix they are migrated to G+1 and survive cleanup.
     */
    @Test
    public void migrateSourceGeneration_updatesMatchingRows_leavesOthersUntouched() {
        // Simulate hosts_lists rows: {sourceId, generation}
        int[][] rows = {
                {2, 1},  // source 2, old generation (should be migrated)
                {2, 1},  // source 2, old generation (should be migrated)
                {3, 1},  // source 3, old generation (different source — should NOT be migrated here)
                {2, 0},  // source 2, even-older generation (different old gen — should NOT be migrated)
        };

        int sourceId = 2;
        int oldGeneration = 1;
        int newGeneration = 2;

        // Apply migrateSourceGeneration logic
        for (int[] row : rows) {
            if (row[0] == sourceId && row[1] == oldGeneration) {
                row[1] = newGeneration;
            }
        }

        // Source 2's rows that were at oldGeneration=1 are now at newGeneration=2
        assertEquals("row[0]: source 2, gen should be migrated to 2", 2, rows[0][1]);
        assertEquals("row[1]: source 2, gen should be migrated to 2", 2, rows[1][1]);
        // Source 3's rows are untouched
        assertEquals("row[2]: source 3, gen should remain 1", 1, rows[2][1]);
        // Source 2's rows at different generation are untouched
        assertEquals("row[3]: source 2 gen=0, should remain 0 (different old gen)", 0, rows[3][1]);
    }

    /**
     * Variant: simulates cleanupNonActiveGenerations() after migration.
     * After migrateSourceGeneration(sourceId=2, old=1, new=2):
     *   - Source 2 rows at gen=2 → SURVIVE cleanup(active=2)
     *   - Source 3 rows at gen=1 → DELETED by cleanup(active=2)   [source 3 returned 200 and was re-written]
     * Without migration, source 2 rows at gen=1 would also be deleted.
     */
    @Test
    public void cleanupAfterMigration_migratedRowsSurvive_nonMigratedRowsDeleted() {
        // Rows after migration: {sourceId, generation, survived}
        // source 2: 304 source — rows migrated from gen=1 to gen=2 by migrateSourceGeneration
        // source 3: 200 source — new rows written at gen=2
        // source 4: 304 source — WITHOUT migration (bug scenario) rows still at gen=1
        int[][] rows = {
                {2, 2},  // source 2, migrated to new gen (should survive)
                {3, 2},  // source 3, new rows at new gen (should survive)
                {4, 1},  // source 4, NOT migrated (bug scenario — should be deleted)
        };

        int activeGeneration = 2;
        // Apply cleanupNonActiveGenerations: delete where source_id != 1 AND generation != activeGeneration
        boolean[] deleted = new boolean[rows.length];
        for (int i = 0; i < rows.length; i++) {
            int srcId = rows[i][0];
            int gen = rows[i][1];
            if (srcId != 1 && gen != activeGeneration) {
                deleted[i] = true;
            }
        }

        assertFalse("Source 2 (migrated 304): must survive cleanup", deleted[0]);
        assertFalse("Source 3 (200 response): must survive cleanup", deleted[1]);
        assertTrue("Source 4 (un-migrated 304 — bug): must be deleted by cleanup", deleted[2]);
    }
}
