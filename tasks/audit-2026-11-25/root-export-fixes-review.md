# Root Export Fixes: AUD-DATA-001/002

## Scope and Status

- Shared checkout: `codex/full-codebase-audit`; baseline `d51eb7aea06871c2c6ad2bbedb9ddbaaaca48d77`.
- Read repository guidance, lessons, audit README, data findings and relevant October data history.
- Production edit: `HostEntryDao.java` only. New tests: `RootExportStagingTest.java` and `scripts/tests/test_root_export_sql.py`.
- Both findings are **implemented-awaiting-android**, not verified-fixed. App edits are complete and handed to main for integrated compilation/execution.
- New `root-export-fixes-{files,bugs,tests}.csv` records contain this lane's hashes, scoped assessments and exact test evidence. Base audit ledgers were not edited.

## AUD-DATA-001

`ROOT_STAGE_MATERIALIZED_SOURCE_WHERE` now aliases the existing active-stage predicate. All five consumers (list, ordinary cursor, IPv4 chunk, IPv6 chunk, count) require external sources to exist, be enabled and belong to the active generation. User-source semantics remain independent of generation/source metadata.

Source selection already updates `hosts_sources.enabled` and the associated `hosts_lists.enabled` transactionally. Source deletion cascades active rules but deliberately leaves stage rows without a foreign key. Filtering retained stage rows matches those boundaries without changing staging or import publication.

The existing skip-table preparation already filters active membership. Rebuild after a selection change remains necessary to recalculate duplicate/redirect winners and allow skips. This patch does not promise a coherent multi-page snapshot across arbitrary concurrent direct DAO mutations without the existing rebuild/gating discipline.

## AUD-DATA-002

Copied redirects no longer preserve import-time stage IDs. The existing ordered insert now generates export IDs in source priority/address order, so the existing `MIN(id)` dedupe agrees with `getActiveExactRedirectEntry`. Stage ID is the final stable tie-break for identical addresses. User-shadow filtering remains unchanged.

Blocked copies still retain stage IDs: exact/suffix allow processing deletes them by stage skip ID before redirects are inserted. Removing IDs from blocked copies would break that filtering, so the edit is deliberately limited to redirects. The current root table is already an append-friendly integer-primary-key table; no migration or entity change is needed.

## Executed Host Checks

Exact command from repository root:

```sh
python3 scripts/tests/test_root_export_sql.py -v
```

Environment: Python 3.12.13, SQLite 3.53.4.

- Before production edit, all three test methods failed: disabled/deleted source fixture returned 5 rows instead of 2; copied redirects selected wrong source and same-source address; chunk plans had no source membership lookup.
- After edit, all three pass (`Ran 3 tests in 0.013s`, `OK`). Full method names and coverage are in `root-export-fixes-tests.csv`.
- Test script extracts Java string constants, query annotations and the actual redirect insert/dedupe SQL. It does not reimplement their ranking expressions. Its reduced fixture is not Android/Room execution.
- Query plans retain `SEARCH entry USING INTEGER PRIMARY KEY (rowid>?)` and add `SEARCH source USING INTEGER PRIMARY KEY (rowid=?)` in both chunk variants. No chunk temp sort is introduced. This establishes query shape, not a timing guarantee.
- Scoped production `git diff --check` passed. Default `java --version` could not locate a runtime; no Java syntax/compilation claim is made and no build setup was changed.
- Final structured CSV validation passed: 2 bug rows, 10 file rows, 8 test rows; every row matches its header width and all 10 recorded SHA-256 hashes match disk. New test/audit files have no trailing whitespace. No-index whitespace checks on the two new test files emitted no diagnostics (exit 1 denotes the added-file difference, not a test pass).

## Exact Android Handoff

All three methods below are **added, source-reviewed, not compiled or executed by this lane**:

```text
org.adaway.db.RootExportStagingTest#stageBackedExportTracksSelectionDeletionAndCopiedFallback
org.adaway.db.RootExportStagingTest#copiedStageRedirectsUseCanonicalPriorityInBothImportOrders
org.adaway.db.RootExportStagingTest#stageReadersKeepUserRowsIndependentOfGenerationAndSourceSelection
```

The first creates one real 500,001-row filler dataset in both active rules and stage, with additional control rows. It asserts the streaming branch, disable/re-enable/delete behavior, orphan exclusion, duplicate handling and all output readers. It then reuses the same dataset for actual copied-stage dispatch with wildcard allow and inverted redirect import priority. No fabricated source-count threshold is used.

The second uses a small staged materializer fixture for both import orders, same-source canonical address, duplicate ties, user override, disabled/stale rows, all three allow forms and redirect precedence. One-row chunk pages expose ID holes and pagination mistakes. The third isolates the user-source exemption in stage readers. These latter two deliberately bypass full completeness/dispatch and are not presented as end-to-end import coverage.

Large-output assertions use bitsets for filler identity and small sets for controls, not additional full output maps. The list API necessarily allocates its returned list. All three tests use inherited in-memory Room setup and never construct RootModel, fetch a URL or write privileged hosts files.

## Preservation and Limits

- SourceModel before/after SHA-256: `b20a872ce49db903ec318e3eaf451e3edbdd93e812e1f4f78f4a7e0dc19f29db`.
- SourceLoader before/after SHA-256: `99666e177cc0106a9927e5b07334d003b9048e828d0bfc66f74ea9866fdd13b9`.
- Existing dirty SAF/offline fixes and alias parsing are unchanged byte-for-byte. SourceModel review was scoped to the dirty diff and staging/finalization boundaries; SourceLoader was a preservation boundary, not a fresh full-file review.
- Original HostEntryDao SHA-256: `b0c99d896154bf29cd47b25d911b14c4f92fb587c8def2fd2fbd30c3dbe74128`; reviewed patched hashes are in the file CSV.
- No schema, dependency, source selection API or threading changes. No Gradle, emulator, adb, privileged runtime, new heavy5M benchmark, commit or push.
- Full Android compilation, Room-generated query validation, actual fixture runtime/memory and integrated regression execution remain main-owned. Desktop SQL success does not close those gates.
