# Remaining Schema and Backup Test Audit

Date: 2026-11-25. Read-only lane on the shared `codex/full-codebase-audit` worktree.

## Result

**One new P2 finding: REM-SCHEMA-001.** A database with the table-form version-4 schema cannot traverse the registered upgrade path because 4->5 tries to drop its table as a view. Source-confirmed and reproduced on host SQLite; no Android/device migration was run. No additional actionable bug was found in the two backup test files.

Coverage: **32 whole-file structured JSON/SQLite semantic reviews, not 32 full assistant text reads; two newly completed full assistant test-file reads; three full supporting Java rereads.** The 37 paths and current SHA-256 values are in `remaining-schema-files.csv`. No schema, production, test, central ledger, index/staging, or commit was changed. Only these three new audit sidecars were added in the repository; the executable probe and generated results live under `/tmp/adaway-schema-audit`.

## Finding by Severity

### P2 - REM-SCHEMA-001: Table-form version 4 cannot upgrade

- **Evidence:** `app/schemas/org.adaway.db.AppDatabase/4.json:140` declares `host_entries` as a table, with a unique host index at line 170 and no views at line 181. `app/src/main/java/org/adaway/db/Migrations.java:195` unconditionally runs `DROP VIEW host_entries`. `AppDatabase.java:132` includes 4->5 in its contiguous route to version 32.
- **Reproduction:** Create an isolated database from unchanged schema 4, seed three user rules (blocked, allowed, redirected) and an HTTPS source, enable foreign keys, and invoke the actual current `MIGRATION_4_5`. Repeat with the registered 4->32 chain. Both throw `use DROP TABLE to delete table host_entries` before final schema validation. The same SQL failure was independently reproduced using Ruby SQLite 3.54.0.
- **Control:** Starting from schema 3 and executing 3->4 produces the older view-form version 4. Its subsequent 4->5 and full upgrade to 32 succeed. Full chains starting at 1, 2, 3, and 5 through 31 preserve the bounded user-rule/source fixtures and reach the version-32 target schema.
- **Provenance:** Local history commit `db36c4b317852fe30264d908e75a7c4306e01002` changed the application entity from a view to a table while `@Database(version = 4)` remained unchanged; it also rewrote schema 4 to the current table form. Commit `987dba5f062352529055dd5daf8f8d084babec6f` later bumped to 5 and added the unconditional drop-view migration. This establishes a real historical source shape, not a guessed malformed input. Whether that intermediate build was distributed is unknown.
- **Impact:** A version-4 database matching the checked-in table form cannot open via the current registered upgrade path. This is not evidence that modern installations fail, nor that persisted rules were deleted: the failure prevents upgrade completion.
- **Likely fix:** Preserve historical JSON. In 4->5, inspect the existing object type; replace the view when present, but retain an already compatible table and ensure the canonical index exists. Do not use a destructive database fallback or blindly drop the table.
- **Smallest regression:** A Room test created from the unmodified schema-4 export, seeded with user rules and source identity/selection, must complete 4->32 with those values intact. Add a companion old view-form version-4 fixture reached through 3->4. Both should pass; this lane did not edit or run those Android cases.

The new ID was checked against the central bugs ledger and all 35 existing bug sidecars. No duplicate migration finding was present. `remaining-schema-bugs.csv` uses the exact central 11-column header.

## Structured Schema Review

All 32 JSON files were parsed completely with structured JSON APIs. For each export, the probe created its entities, declared indices, views, and setup queries in an isolated host SQLite database, then compared the resulting schema with its metadata:

- Exactly versions 1 through 32; filename version equals `database.version`; `formatVersion` is 1; unique table/view/field names.
- `identityHash` equals the value inserted into `room_master_table` by that export's setup queries. This checks internal consistency, not a fresh Room compiler identity-hash calculation.
- Table/view object kinds, field affinities, nullability, declared defaults, ordered primary keys, and AUTOINCREMENT declarations.
- Foreign-key targets/columns and update/delete cascade actions; all declared foreign keys are single-column and foreign-key checks remain clean for the fixture data.
- Declared index names, uniqueness, ordered columns, and sort directions. The current version has eight entity tables and 19 declared indices.
- All 31 consecutive migrations are registered by AppDatabase; the current application annotation and final export both select version 32.

All 32 exports are internally self-consistent under those checks. That alone does not establish congruence with the preceding migration. Identity equality between versions 16/17 and 24/25 is not a bug: their migrations change physical WITHOUT ROWID storage without changing Room's exported logical entity metadata. Versions 4/5 also share an identity, but the historical version-4 rewrite is implicated in the failure above.

### Executed Host Checks

The actual unmodified `Migrations.java` was compiled under JDK 21 with minimal Android/Room API adapters. Its migration bodies ran against SQLite JDBC 3.41.2.2 with foreign keys enabled and each migration route enclosed in a transaction. The adapters execute SQL and supply only the cursor operations used by this class; they do not simulate Room lifecycle, Android storage, or Android's SQLite build.

| Check | Count | Outcome |
| --- | ---: | --- |
| Export creation and metadata self-check | 32 | All pass |
| Each adjacent migration against its target export | 31 | 28 match; two intermediate artifact differences; one execution failure |
| Each starting version 1..31 through current version 32 | 31 | 30 pass; version 4 fails at 4->5 |
| Independent minimal schema-4 drop-view probe | 1 | Same error on Ruby SQLite 3.54.0 |

The two intermediate differences are:

1. **2->3:** Migration 2->3 creates only the view and retains the unique `hosts_lists(host)` index from version 2. Schema 3 instead declares that index nonunique. Migration 3->4 subsequently recreates it as nonunique; 2->32 passes. This is a concrete historical artifact/one-step-validation discrepancy, not a separate demonstrated current-upgrade or data-loss bug.
2. **3->4:** The migration creates a view while schema 4 declares a table. This is the same historical dual-form issue behind REM-SCHEMA-001, not a second bug ID.

The successful chains preserve the fixture's three host/type/enabled/redirection/source-ID tuples, exact versus suffix kind where the version supports it, and external source ID/URL/selection. The fixture is deliberately small. It is not exhaustive proof for every old datum, corrupted database, very large migration, interruption, or storage-pressure case. Defaults absent from exported metadata are not compared as if explicitly declared. Physical rowid layout is not treated as a logical-schema mismatch.

Local probe artifacts:

- `/tmp/adaway-schema-audit/src/org/adaway/db/SchemaAudit.java`, SHA-256 `fdb24a035e3dbed7199e2bef4a5df9a8fb2f7ee26eb38435166030f0c32351d5`.
- `/tmp/adaway-schema-audit/results.json`, SHA-256 `300c819a2d4e933cd9314c013a902fba48aa7a9509f8258c1af2d6065ee79758`.
- The results contain all 94 structured self-check/one-step/chain outcomes, including exact failing SQL and the preserved fixture rows. Production migration source identity is recorded in the files CSV.

## MigrationTest Coverage

`MigrationTest.java` was read completely (658 lines), including fixture setup, assertions, and query helpers. Its 21 methods cover exactly 11->12 through 31->32, each via `runMigrationsAndValidate(..., true, oneMigration)`. The tests assert selected data/default/index/physical-layout behavior in addition to helper validation.

Missing from this class: 1->2 through 10->11, every multistep start-to-current route, and the two historical version-4 shapes. The modern tests therefore cannot detect the confirmed early upgrade failure. This is a coverage limitation, not a claim that the existing tests fail. None of these Android migration tests was run by this lane.

## Backup Test Review

### AppBackupAgentPlatformInstrumentedTest

All 195 lines were read. Its two methods are opt-in phases, gated by `platformBackupPhase=seed` or `assert`. They seed/check a named preference, source label, intentional redirect-trust revocation, and blocked-suffix/allowed-exact/redirected-exact user rules. Cleanup touches only the named probe records. The assert phase waits up to 20 seconds for restored state.

The file supplies assertions for an external platform-backup workflow; it does not itself invoke backup, clear application data, choose a transport, or prove that a real restore occurred. Ordinary unparameterized execution skips both phases. The negative trust assertion helps distinguish untouched seed state from restored state. Enabled/allow source flags, source ID preservation, runtime cache behavior, and transport failures are not comprehensively asserted here. No actionable new bug was confirmed in this file, and no transport run was performed.

### BackupImportAtomicityTest

All 100 lines were read. Four cases cover malformed-later-list rejection before writes, transaction rollback after a trigger-induced SQLite failure, exact/suffix coexistence after repeated restore, and the shared 50,001-rule rejection across two arrays. The assertions exercise real database transactions when run on Android, using an isolated test database.

These cases call the database/JSONObject overload directly. They intentionally do not establish coordinator-lease behavior, URI permissions, application-cache invalidation, or platform transport restoration. The separate prior settings runtime tests address some of those paths. No actionable new bug was confirmed in this file; no Android backup test was run in this lane.

## Handoff and Limits

Only `remaining-schema-files.csv`, `remaining-schema-bugs.csv`, and this report were added. Historical schemas and all build inputs remain untouched; implementation remains frozen. No Gradle, ADB, emulator, device, privileged root runtime, staging, commit, or central-ledger update occurred.

Main reports seven live-preferences cases passed with zero skips on saved phase-two APK `30f86`, including IPv6, watchdog restart, and stopped controls. That is recorded as main-supplied settings evidence only; it is not migration or platform-backup proof. Prior settings sidecars were not changed during this bounded audit.
