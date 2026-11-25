# Legacy Version-4 Migration Fix - Ready and Frozen

Date: 2026-11-25. Finding: REM-SCHEMA-001. Production/test inputs are frozen for main's next integrated build.

## Bounded Change

Only `app/src/main/java/org/adaway/db/Migrations.java` and the new `app/src/androidTest/java/org/adaway/db/LegacyV4MigrationInstrumentedTest.java` are build-input changes in this slice. They remain unstaged. Exact SHA-256 values are in `schema-fixes-files.csv`. Existing `MigrationTest.java`, AppDatabase, all 32 historical JSON schemas, and other lanes' files were left unchanged. The historical schema hashes were rechecked against `remaining-schema-files.csv`; every schema still matches.

Migration 4->5 queries `sqlite_master` specifically for the legacy `host_entries` view. The cursor closes before any schema write. Only the view is dropped. The existing CREATE TABLE IF NOT EXISTS and CREATE UNIQUE INDEX IF NOT EXISTS statements then either create the table-form target or leave the already compatible table/index in place. There is no table drop, database reset, destructive fallback, database version bump, new schema artifact, or change to any other migration.

An incompatible/corrupt preexisting table is not silently repaired or discarded: existing SQL/schema validation still rejects unsupported shapes. This patch only addresses the two evidenced historical version-4 forms.

## Connected Cases for Main

Class: `org.adaway.db.LegacyV4MigrationInstrumentedTest`.

1. `tableForm4To32PreservesUserRulesAndSources`: create from unchanged schema 4; seed user blocked/disabled-allowed/redirected rules, a disabled external source with ID 42 and a source rule, and a cached redirect. Run the complete registered 4->32 chain with Room target validation. Check stable row/source IDs, URLs, enabled states, rule types, redirect values, final reverse keys, exact kinds, generation zero, foreign-key integrity, and retained cache content.
2. `viewForm4To32PreservesUserRulesAndSources`: create schema 3, run the real 3->4 migration inside a fixture transaction, set its user_version to 4, and assert the starting object really is a view. Run the same 28-edge route and saved-state assertions to schema 32. This avoids rewriting the historical schema-4 JSON to invent the older view fixture.
3. `tableForm4To5RetainsCachedRowsAndUniqueIndex`: validate directly against schema 5, preserving the cached redirect and original unique-index SQL, with source/rule counts intact. This catches a destructive replacement that might otherwise hide behind eventual schema-32 success.

All three cases compiled against the Android API image; **none was run on Android by this lane**. The explicit 28-edge test array was compared with AppDatabase's registered route and matches exactly. Main should run this class and the existing `MigrationTest` suite. Tests create only the dedicated `legacy-v4-migration-test` database; no singleton app database or privileged root file operation is used.

## Executed Negative/Green Proof

The prior structured audit harness was reused with a focused probe that invokes actual `Migrations.java`, not a reimplementation of its SQL. It uses SQLite JDBC 3.41.2.2, foreign keys enabled, and transaction-wrapped migrations. The same six cases were executed before and after the production edit:

| Fixture | Target | Original | Corrected |
| --- | ---: | --- | --- |
| Table with existing unique index | 5 | Expected DROP VIEW failure | Pass |
| Table with existing unique index | 32 | Expected DROP VIEW failure | Pass |
| Historical view reached through 3->4 | 5 | Pass control | Pass |
| Historical view reached through 3->4 | 32 | Pass control | Pass |
| Table with index removed in fixture | 5 | Expected DROP VIEW failure | Pass; index recreated |
| Table with index removed in fixture | 32 | Expected DROP VIEW failure | Pass |

The negative probe accepts only the exact `DROP VIEW host_entries` failure with SQLite's `use DROP TABLE` cause; unrelated exceptions fail it. It also checks that rollback leaves the seeded rules unchanged. The green probe checks target schema metadata, user rule IDs/host/type/enabled/redirection/source IDs, disabled external source identity/selection, and cached redirect preservation. For the compatible table/index 4->5 case, both original sqlite_master root pages remain unchanged, demonstrating retention rather than drop-and-recreate.

The complete reused matrix then passed all **32 schema self-checks and all 31 start-to-32 chains**. Adjacent target comparison passes **29 of 31** edges. The only differences are the already documented historical 2->3 host-index uniqueness and 3->4 view-versus-exported-table discrepancies; neither is hidden or reclassified as passing. No migration execution failures remain in the host matrix.

## Compilation and Boundaries

Standalone JDK 21 compilation of the changed production class and dedicated test class passed using Android API 36 `android.jar`, its cached `jdkImage` via `--system`, source/target 21, cached dependencies, and existing debug application classes. Outputs are isolated under `/tmp/adaway-schema-fixes/android-classes`. This establishes Java/API compatibility, not a clean Gradle build, Room annotation processing, resource linking, APK assembly, lint, or connected execution.

No Gradle, ADB, emulator, device, privileged root runtime, staging, commit, push, central-ledger update, or unrelated production/test edit was performed. Main owns the integrated build and Android proof. Sidecar status remains `implemented-host-verified-awaiting-android`, not verified-fixed.

## Reproducibility

Local scratch entry points:

- `ruby /tmp/adaway-schema-fixes/check.rb green`: recompiles actual current migrations, runs the six focused cases, then the 94-outcome matrix. The `negative` option was run before the edit; it intentionally expects the original failure and will reject corrected code. The compiled original remains isolated under `negative-classes` for replay without touching repository files.
- `ruby /tmp/adaway-schema-fixes/compile-android.rb`: standalone Android API-image compilation only.
- `/tmp/adaway-schema-fixes/LegacyV4Probe.java`: focused fixture/proof source, reusing `/tmp/adaway-schema-audit/src/org/adaway/db/SchemaAudit.java` and its minimal API adapters.

Evidence hashes:

| Artifact | SHA-256 |
| --- | --- |
| Original Migrations.java before edit | `c5f74d272065386b37d5a55d912e55ce91ef51cffd9a80ba2b78e0a6a85ab5d6` |
| `negative-focused.log` | `517274bfc192d50cfba0937a6d09cb7087eb2957fc92a100ee8dc3372ceccdad` |
| `green-focused.log` | `608f7f526684066199cf4d33944a1f97b9c6b7fd7d4d8a89c1b5878a6d3b6b7f` |
| `green-results.json` | `48a87d69d49d5a72fb7cfff7fa53ec9f145a2c362443c0b0f4dcdbcaacdf90d0` |
| `LegacyV4Probe.java` | `6bd115652caa8e9da58f42f26d4d56499f54bb2a4d0fe43ce4ee960be6957494` |

Historical deployment reach of the table-form v4 build remains unknown. The defect and the fix are demonstrated for that recorded shape; no claim is made that every old installation used it. Host SQLite is not Android SQLite or the Room lifecycle. The connected cases are the remaining integration proof, and their pending status is explicit in `schema-fixes-tests.csv`.
