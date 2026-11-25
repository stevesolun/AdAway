# Post-beta database review, 2026-11-25

Scope: the original three database classes and regressions plus the authorized Domain Checker follow-up for PB-DATA-005. Five fixes implemented. The owner reports the prior combined API 34 run passed all 80 cases, including v1->6, canonical redirects, literal underscores, the host-list snapshot, and Home. That run predates the checker patch. No schema version change, central CSV edit, Git, Gradle, device, emulator, or privileged root operation in this lane.

## Migrations.java

- Reviewed the registered 1 through 32 chain: source and rule identity, historical view/table forms, generation initialization, stats backfill, reverse-label backfills, root table copies and index transitions.
- **PB-DATA-001:** 1->2 creates reserved source 1 with the modern content URL. At 5->6, the URL-only update did not match it: the two newly added rule-type flags stayed 0. A schema-1 fixture with a saved redirect and disabled external source reproduced user flags (0,0); after using the reserved identity they are (1,1). The external flags stay (0,0), and the saved redirect and its owner survive. This corrects upgrades that still cross version 5; it does not retroactively edit existing version-32 databases.
- **PB-DATA-003:** 14->15's root export population contained the same literal-underscore mismatch as the live DAO. The extended v14 fixture checks both the allowed spelling and its blocked lookalike, for exact-pattern and suffix allows. Extracted migration SQL retained both lookalikes and excluded both actual matches.
- Prior **REM-SCHEMA-001/002**: 4->5 still distinguishes the view from the compatible table and closes its inspection cursor before DDL. 18->19 still counts enabled user/current rows without forcing the Android 11 planner's ineligible index. Recompiled the actual migrations in the existing host JDBC harness: six v4 cases passed; 32/32 schema self-checks and 31/31 chains to v32 passed. Of the adjacent comparisons, 29/31 match; the previously documented 2->3 index and 3->4 view/export differences remain. The v18 seeded stats query returned (blocked, exact, allowed, redirect, total) = (3,2,1,1,5). Prior device evidence is historical, not a run of this patch.

## HostEntryDao.java

- Traced active generation/user reads, stats fallback, exact/suffix import and deletion, stage completeness, skip IDs, redirect conflict ranking, cached and uncached dispatch, and IPv4/IPv6 chunk readers. Large block-only duplicate suppression has deliberately different cost policies; no new throughput result is claimed.
- **PB-DATA-002:** uncached root streaming visits user redirects before source redirects and keeps the first row for a hostname. Neither scan ranked same-source addresses. In descending insertion order both emitted 9.9.9.9 first where runtime chose 1.1.1.1. Added the same address tie-break as runtime, after source ID for external rows; both input orders and a competing source now agree. The old copied-stage fix alone did not cover these two scans.
- **PB-DATA-003:** SQL LIKE treated literal underscores as one-character wildcards even though the Java active cursor and indexed literal/suffix paths did not. The exact, wildcard-plus-underscore, and suffix lookalikes all reproduced false allows before the fix. SQL now escapes the literal and the escape character before translating rule wildcards; exact patterns retain legacy percent behavior, and suffix labels match literally. Checked the runtime probe, fallback candidate CTE, small-cache materialization, and direct/copied wildcard deletion call sites.
- Prior **AUD-DATA-001/002**: the five stage readers still filter missing/disabled external sources and skip IDs; copied redirects still receive canonical IDs before deduplication. The three original host SQL tests passed, including disable/re-enable/delete, competing redirects, pagination and query plans. Prior **REM-SCHEMA-003**: the v32 recount uses no forced index and reproduced the seeded (3,2,1,1,5) on host SQLite. Room processing accepted all current queries.

## HostListItemDao.java

- Traced user-only lookup/deletion, paging and counts, exact/suffix explanations, copy-forward and targeted promotion. The latter two are transactions with distinct source/target generations in current SourceModel callers. Executing the actual SQL preserved the original and replaced partial target rows on copy, and replaced only the selected source's target on promotion; user and unrelated source controls survived. The source-level Generation304MigrationTest is not claimed as a new execution.
- **PB-DATA-004:** each inner lookup rereads active_generation. A two-connection WAL fixture committed activation and stale-row cleanup between the exact and suffix SELECTs: without an enclosing transaction the result contained generations (1,2); with a single snapshot it was (1,1) in both root-base and VPN-ancestor cases. Annotated just the two composed explanation reads. Verified generated Room implementations bracket the entire default method, not the individual queries. No change to lists of eligible rules, delete scope, or runtime DNS locking.

## PB-DATA-005: Domain Checker

- Previously, a user or downloaded wildcard allowed a child at runtime but the checker searched only literal rule keys and reported UNKNOWN. The read-only query now returns distinct source ID, stored host and rule kind using the exact same active allow predicate as the runtime. Existing equality lookups, indexes, packet resolution and database version are unchanged.
- The ViewModel takes status from the actual root or VPN resolver. In the same database transaction it reads provenance to distinguish explicit ALLOWED from default UNKNOWN. Redirect precedence and the existing blocked-source and delete-by-ID path remain intact. Root still treats suffix blocks as base-only; suffix allows match descendants in both modes.
- The Fragment shows the source and rule for an explicit allow. Remove is offered only for an enabled, literal user exact allow on the checked host; its DELETE cannot touch downloaded, wildcard, or suffix rules. Source-only ALLOWED no longer offers Block. UNKNOWN still does. No subscription changes.
- Host SQLite 3.53.4 on the exported v32 indexes with 8,192 unrelated blocks: EXPLAIN reported `SEARCH allowed USING INDEX index_hosts_lists_active_allow_source_kind_host (type=? AND enabled=?)`, a primary-key lookup on hosts_meta and temporary ordering of matches; no hosts_lists scan. This still checks enabled allow candidates, not a constant-time wildcard lookup. No timing or device-planner claim.
- Two new host tests pass: mixed-case user/source exact, suffix and wildcard provenance, literal underscores, question/star/legacy percent, disabled/stale/future exclusion, exact removal and sibling retention, and query plan. The five existing root SQL tests and 19 Domain Checker JVM tests also pass. Bounded standalone Room/API 36 compilation passed, including the runtime and operations instrumentation tests. Generated packet allow SQL compared byte-for-byte equal to the previous Room output.
- Android follow-up, not run here: `DomainCheckerRuntimeTruthTest#allowProvenanceIsReadOnlyAndDoesNotOverrideRedirects` and `#domainCheckerFragmentRendersRuntimeTruthStates`. The latter asserts downloaded-wildcard ALLOWED has its source visible with neither Block nor Remove, then checks UNKNOWN still offers Block.

## Verification

1. From the repository root: `python3 -B scripts/tests/test_domain_checker_allow_sql.py -v` and `python3 -B scripts/tests/test_root_export_sql.py -v`. Two checker SQL tests and five root SQL tests pass.
2. The v1, v14 and stats fixtures above used local SQLite; the full migration chain used SQLite JDBC with host Android/Room API stubs. Standalone JDK 21 compilation against the Android API 36 image ran the Room annotation processor, generated the real DAOs, and compiled the changed Android test classes. Output was outside the checkout; schema export was intentionally disabled. This is not an installed-APK test or an Android 11 planner run.
3. For the checker follow-up only, on an authorized disposable device with **matching freshly built app and test APKs already installed**:

   ```sh
   adb shell am instrument -w -e class org.adaway.ui.domainchecker.DomainCheckerRuntimeTruthTest org.adaway.test/androidx.test.runner.AndroidJUnitRunner
   ```

4. Before importing, recheck the file report against current bytes in this shared checkout:

   ```sh
   python3 -c 'import csv,hashlib,pathlib; p=pathlib.Path("tasks/audit-2026-11-25/post-beta-data-files.csv"); rows=list(csv.DictReader(p.open())); assert len(rows)==len({r["path"] for r in rows})==12; assert all(hashlib.sha256(pathlib.Path(r["path"]).read_bytes()).hexdigest()==r["sha256"] for r in rows); print("12 hashes match")'
   ```

   Central owner only, when ready: `python3 scripts/audit_inventory.py --record tasks/audit-2026-11-25/post-beta-data-files.csv`. No import was run. The bugs CSV separates the prior API 34 evidence from the checker patch's host verification.
