# Data-Layer Full File Review - 2026-11-25

## Scope and Evidence Standard

- Repository: `/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway`.
- Branch: `codex/full-codebase-audit`; baseline and initial HEAD: `d51eb7aea` (`d51eb7a`).
- Read `CLAUDE.md`, `tasks/lessons.md`, and the historical `tasks/review-2026-10-12-data.md`.
- Individually read the complete contents of all **33 files / 10,296 lines** in `app/src/main/java/org/adaway/db/**` and `app/src/main/java/org/adaway/model/source/**`. Chunked reads covered every line, including catalog rows and every migration. Truncated bulk output was reread where necessary.
- The 33-row `data-files.csv` is the assigned production-file coverage ledger. Related tests are documented separately below; symbol searches and filename enumeration are not counted as full review.
- Initial pass was review-only. The subsequent user-authorized implementation changes only `SourceLoader.java`, `SourceModel.java` and four directly related Android test/helper files, plus these three audit artifacts. No Gradle, adb, builds, test executions, branches, commits, pushes, releases or pipelines were run by this owner.
- All six original defects were **source-confirmed**, not hypothetical. `AUD-DATA-003` through `006` are now **implemented-awaiting-test**; 16 Android regression methods were added but not compiled/executed by this owner. `001`/`002` remain source-confirmed and **deferred: root-only scope excluded by user**. None is claimed verified-fixed. Main owns combined Gradle/device validation.
- No hypothetical-only candidate is submitted as a reproduced bug. The CSV `fix`/`test_evidence` columns distinguish implemented changes from deferred suggestions and unexecuted tests.
- Initial review covered baseline contents. Hashes below now include this owner's two product changes and main's catalog removal/final header change, reread before hash capture. Main's updated `HostsSourceFreshnessTest` was also reread and its evidence hash refreshed. Other owners' files were not edited.

## Findings

| Bug ID | Priority | Finding | Status |
| --- | --- | --- | --- |
| AUD-DATA-001 | P1 | Stage-backed root export includes disabled or deleted sources | source-confirmed |
| AUD-DATA-002 | P2 | Copied staged export chooses redirect by import ID instead of source priority | source-confirmed |
| AUD-DATA-003 | P2 | SAF modification milliseconds are interpreted as epoch seconds | implemented-awaiting-test |
| AUD-DATA-004 | P2 | Targeted local-file updates still fail while offline | implemented-awaiting-test |
| AUD-DATA-005 | P2 | Periodic check never retries a never-downloaded source without Last-Modified | implemented-awaiting-test |
| AUD-DATA-006 | P2 | Hosts-format aliases after the first hostname are silently discarded | implemented-awaiting-test |

**Reading the original findings:** Locations and present-tense defect descriptions in the six detailed findings below describe the audited baseline `d51eb7a`, before the implementation round. Current fix locations/status and exact test names follow under Implementation Handoff. Root-only findings are retained, not being implemented.

### AUD-DATA-001 - Disabled sources leak into stage-backed root export

**Location:** `app/src/main/java/org/adaway/db/dao/HostEntryDao.java:82-83`; consumers at 1475-1479, 1484-1488, 1498-1508, 1520-1533 and 1607-1610. Stage selection is at 657-660; completeness checks at 976-1021.

The materialized-stage predicate omits the enabled/existing-source test present in `ROOT_STAGE_ACTIVE_SOURCE_WHERE`. Completeness checks count only enabled sources, but the underlying stage retains other sources' rows. Allow, redirect and duplicate skip preparation also processes only active sources, so it does not exclude disabled rows.

**Reproduction:** Import an enabled source A with 500,001 unique blocked candidates and source B with a distinct blocked host in the same generation. There must be no enabled user block/redirect rule and no active wildcard exact allow rule, so the stage-backed path is selected. Disable B through `applySourceSelection`, call `syncHostEntries`, and drain the root export. B's row is still emitted although active rule resolution allows it. A DAO deletion leaving orphan stage rows exposes the same predicate defect; no UI-delete behavior is claimed here.

**Observed host-side proof:** An in-memory SQLite fixture with A's 500,001 rows and B's disabled `disabled.example` produced:
```text
complete_enabled_stage_count = 500001
materialized_export_rows = 500002
leaked_disabled_host = disabled.example
```
The probe used the current enabled-source completeness predicate, duplicate-skip SQL and materialized export predicate; it did not execute Room or Android. The absence of wildcard allow rules is a real precondition, not the fresh-install default with safety wildcards enabled.

**Fix direction:** Filter every stage-backed list, cursor, chunk and count query by live enabled-source membership, or materialize disabled/orphan IDs into the skip set. Keep user semantics consistent. Fixing only the plain cursor would leave chunk export/counts wrong.

**Strongest regression:** Extend `SourceDbTest.testCompleteRootExportStageIgnoresDisabledSources` with actual >500,000 enabled stage rows, toggle and delete cases, then compare list/cursor/IPv4 chunk/IPv6 chunk/count output against active truth. Assert `hasStageMaterializedRootExportRows()` so the test cannot silently exercise the copied-table path. The existing test at 779-801 has one enabled stage row and cannot catch this. No root privileges are needed.

### AUD-DATA-002 - Preserved stage IDs override redirect precedence

**Location:** `app/src/main/java/org/adaway/db/dao/HostEntryDao.java:1440-1446` and 1392-1408; calling path 630-635.

The copied-stage path orders redirects by source precedence but explicitly preserves `entry.id`. Later deduplication chooses `MIN(id)`, which is import order, not the insertion order specified by that sort. Downloads finish in different orders, so a numerically higher source ID can win against a lower source ID. Active `resolveEntry` uses the lower source ID.

**Reproduction:** Stage source 3's `redirect.example -> 1.1.1.1` first, then source 2's same-host `9.9.9.9`. Use a complete large stage and an unrelated wildcard allow rule, forcing the copied-stage branch. Expect source 2's redirect; observe source 3's earlier stage ID. The wildcard path matters: the separate stage-backed redirect-winner implementation correctly compares source priority.

**Observed host-side proof:** Executing the current staged redirect insertion and deduplication SQL with stage IDs 10/source 3 and 20/source 2 retained `10|redirect.example|1.1.1.1`; source-priority ordering selected `9.9.9.9`. This is a reduced SQL proof of the ranking error, not an Android pipeline run.

**Fix direction:** Select canonical redirect winners using source priority, source ID, redirection and stable tie-break before inserting, or otherwise remove the assumption that preserved stage IDs encode priority.

**Strongest regression:** Room fixture with >500,000 active/staged rows and `*.unrelated.example` allow, source 3 imported first, source 2 second. Compare `resolveEntry`, root list/cursor/chunks; reverse import order and repeat with conflicting addresses within one source. Existing `SourceDbTest` tests at 642-663 and 666-724 cover direct active-rule materialization or stage-backed winners, not this copied-stage branch.

### AUD-DATA-003 - SAF milliseconds enter the seconds converter

**Location:** `app/src/main/java/org/adaway/model/source/SourceModel.java:535-540`; dates committed at 680-686 and 1618-1626. `db/converter/ZonedDateTimeConverter.java:22-23` correctly expects seconds and is not itself defective.

Android specifies `COLUMN_LAST_MODIFIED` in milliseconds and permits null for unknown dates. [Android API contract](https://developer.android.com/reference/android/provider/DocumentsContract.Document#COLUMN_LAST_MODIFIED).
The source reader passes `cursor.getLong(...)` directly into the epoch-seconds converter. It then sets the local date to the maximum of now and this converted online date, persisting the erroneous future timestamp in both fields.

**Reproduction:** Provider returns `1700000000000`; import a FILE source and reload its dates. Expected online time is `2023-11-14T22:13:20Z`, but interpreting the value as seconds yields `+55840-11-08T22:13:20Z`. A host-side date calculation confirmed this arithmetic. The existing test provider already returns `System.currentTimeMillis()`; its consumers do not assert persisted dates. If later metadata is unavailable, the weekly age fallback cannot expire this far-future local date.

**Fix direction:** Check `cursor.isNull` and convert milliseconds at the provider boundary, e.g. `Instant.ofEpochMilli(...).atZone(UTC)`. Keep Room's seconds representation unchanged. Plan a refresh/repair for previously malformed FILE metadata.

**Strongest regression:** Real test provider + in-memory Room; expose a fixed millisecond timestamp and a null timestamp, import through full and targeted APIs, reload source records and assert expected UTC instant/local-fetch range. Check an unknown metadata result after a prior valid import. No Android execution claimed.

### AUD-DATA-004 - Offline fix does not cover targeted or check-only operations

**Location:** `app/src/main/java/org/adaway/model/source/SourceModel.java:1535-1543`; related check-only guard at 382-390.

The targeted helper checks network connectivity before it even reads the source, so a local FILE source, disabled URL cleanup, user-source no-op or missing target can all fail with `NO_CONNECTION`. Both public targeted APIs reach it. Check-only likewise rejects before enumerating local sources.

**Reproduction:** Reuse `SourceModelOfflineFileTest`'s ContextWrapper returning null for `CONNECTIVITY_SERVICE` and its `content://org.adaway.test.hosts/success.txt` source. Call `retrieveHostsSource(id)` or `retrieveHostsSources(singletonList(id))`. Expected local import; the first guard deterministically throws. `checkForUpdate` with a local-only selection has the same unnecessary requirement. Source trace only; Android calls not executed.

**October distinction:** Historical DATA-13 fixed `checkAndRetrieveHostsSources` only, and that fix remains present at 583-618. This is a remaining entry-point gap, not a claim that the full-update fix regressed.

**Fix direction:** Read/classify the targeted source after acquiring the existing operation gate; require connectivity only for an enabled URL that will actually be fetched. Categorize enabled sources before check-only connectivity validation too.

**Strongest regression:** Extend the existing offline class with single/multi-target FILE imports and check-only, plus disabled URL cleanup, missing/user target no-ops and an enabled URL rejection control. Verify runtime rows, unchanged active generation for targeted work, no scratch tables and released gate.

### AUD-DATA-005 - Never-downloaded sources can be ignored forever by periodic checks

**Location:** `app/src/main/java/org/adaway/model/source/SourceModel.java:412-417`; periodic consumer `SourceUpdateService.java:160-179`.

When the remote modification date is unknown, the check only sets update-needed if the local date is non-null and old. A never-downloaded source has a null local date forever, so it never qualifies. For the only enabled source, periodic work reports success without attempting retrieval. The DAO freshness query correctly considers this source outdated; this finding is about the separate check/worker path.

**Reproduction:** After an initial failed download leave one enabled HTTPS source with null local date. Its subsequent successful HEAD responses have no `Last-Modified` header. Invoke check/periodic work repeatedly. Expected a first-body download attempt; observed by control-flow analysis: `checkForUpdate()` returns false on every run, so no GET/apply is attempted. No server/device reproduction was run.

**Fix direction:** Make absence of any successful local download sufficient for update-needed before applying the unknown-remote-date weekly fallback. Preserve the existing successful-empty-source behavior.

**Strongest regression:** Adapt the HTTPS MockWebServer setup from `SourceModelHttpConditionalTest:75-118`, inject the same TLS client into the HEAD client as well, and serve HEAD 200 without a date to a source with null local date/error metadata. Assert check returns true, then test the real periodic worker reaches retrieval. Controls: recently downloaded empty source, disabled source, old successful source. DAO-only freshness tests do not cover this.

### AUD-DATA-006 - Hosts aliases are silently lost

**Location:** `app/src/main/java/org/adaway/model/source/SourceLoader.java:70-71,593-594,638-646`.

The hosts regex captures address and one hostname and consumes the remainder with `.*`. The parser creates exactly one item. A standard hosts record's additional names are therefore dropped, although the import succeeds and reports no skipped line.

**Reproduction:** Import `0.0.0.0 ads.one.example ads.two.example # shared block`. Expected both names blocked; only `ads.one.example` reaches the item queue, leaving the second name allowed absent another rule. A host-side equivalent-regex probe captured only the first name. No Java/Android parser test was executed.

**Fix direction:** Parse the address and all hostname/alias tokens before the comment, validating/enqueuing each name while retaining redirect-IP checks and source-scoped deduplication.

**Strongest regression:** Extend `SourceLoaderDatabaseSemanticsTest` to import multi-alias rows through raw SQLite with deduper and through the Room insertion path. Assert every valid alias in `hosts_lists`, staging and runtime resolution; add tabs, duplicate aliases, comment tokens and public-IP redirects. `SourceLoaderTest.testHostParser` currently checks only single-name lines plus comments.

## Implementation Handoff

### Completed Edits, Awaiting Main Validation

- AUD-DATA-003: `SourceModel.java:538-546` checks nullable provider dates and converts SAF epoch milliseconds to UTC. Room storage still uses seconds. Both full and targeted imports use the corrected reader. A successful re-import repairs old future dates; no automatic metadata migration was added. Unknown metadata still falls back to fetch time on successful import, as before; check-only records unknown as null.
- AUD-DATA-004: `SourceModel.java:384-396,1541-1563` checks connectivity only when an enabled URL operation needs it. Targeted reads remain inside the existing mutation gate; disabled cleanup/missing/user no-ops work offline. Mixed-batch rollback and full-generation publication policy are unchanged.
- AUD-DATA-005: `SourceModel.java:413-425` treats a missing successful local download as update-needed even without remote Last-Modified. Successfully downloaded empty sources still use weekly refresh when the remote date is unknown.
- AUD-DATA-006: `SourceLoader.java:553-562,575-621` iterates hosts aliases before hash comments and validates each through existing host/redirect rules. Invalid aliases do not discard valid siblings; partially malformed records count as one skipped line. Non-host formats and allowlist syntax retain separate parsing; SQL deduplication is unchanged.

### Exact Added Tests

All methods below are Android instrumentation tests, **added but not compiled or run by this owner**.

`app/src/androidTest/java/org/adaway/model/source/SourceLoaderDatabaseSemanticsTest.java` (4):
- `hostsAliasesUseFastPathDeduplicationAndIgnoreComments`
- `hostsAliasesUseRoomPathAndCountMalformedLinesOnce`
- `redirectAliasesPreservePublicAddressAndRejectPrivateAddresses`
- `hostsAliasesDoNotEnableRedirectsOrBroadenAllowListSyntax`

`app/src/androidTest/java/org/adaway/model/source/SourceModelOfflineFileTest.java` (9):
- `targetedLocalFileImportsOfflineAndRepairsOldMillisecondMetadata`
- `fullLocalFileImportUsesMillisecondsForOnlineDate`
- `targetedMultipleLocalFilesImportOfflineWithoutChangingGeneration`
- `targetedDisabledUrlCleansUpOfflineWithoutTouchingUserRules`
- `targetedEnabledUrlStillRejectsOfflineAndReleasesGate`
- `targetedMixedOfflineFailureRollsBackEarlierLocalImport`
- `offlineChecksHandleNoSourcesAndLocalSourcesWithUnknownDates`
- `offlineCheckStillRejectsEnabledUrlBeforeUpdatingLocalMetadata`
- `targetedLocalUpdatesStillRespectMutationGateOffline`

`app/src/androidTest/java/org/adaway/model/source/SourceModelHttpConditionalTest.java` (3):
- `failedFirstDownloadWithoutLastModifiedRemainsUpdateNeeded`
- `successfulEmptySourceWithoutLastModifiedUsesWeeklyRefresh`
- `freshGoneSourceAbortsPartialGenerationWithoutDisablingSelection`

Main should run these complete three classes, not just the additions, to cover existing parser cancellation/RPZ and HTTP 304/carry-forward contracts. Also retain existing `SourceModelGenerationFailureTest` and `SourceLoaderTest` in combined validation. The provider supports per-URI fixed/unknown dates without global fixture state; HTTP tests inject the same local TLS client for GET and HEAD and drain queued cache work before teardown.

### Exact Files Changed by This Owner

1. `app/src/main/java/org/adaway/model/source/SourceLoader.java`
2. `app/src/main/java/org/adaway/model/source/SourceModel.java`
3. `app/src/androidTest/java/org/adaway/model/source/SourceLoaderDatabaseSemanticsTest.java`
4. `app/src/androidTest/java/org/adaway/model/source/SourceModelOfflineFileTest.java`
5. `app/src/androidTest/java/org/adaway/model/source/SourceModelHttpConditionalTest.java`
6. `app/src/androidTest/java/org/adaway/model/source/TestHostsContentProvider.java`
7. `tasks/audit-2026-11-25/data-review.md`
8. `tasks/audit-2026-11-25/data-files.csv`
9. `tasks/audit-2026-11-25/data-bugs.csv`

Catalog, defaults, migrations, Home, database DAOs and other owners' tests were not edited by this owner. No further product edits will be made after this handoff without coordination.

## Fresh Onboarding and Existing-User Boundary

- **Main-provided live evidence:** default URLhaus `https://urlhaus.abuse.ch/downloads/hostfile/` returns HTTP 410 Gone, three other lists parse 91,774 accepted rows, full update fails, runtime blocked count remains zero, and Home says protection active. Main confirmed the official [URLhaus API documentation](https://urlhaus.abuse.ch/api/) requires an authentication key for hostfile feeds. This owner did not rerun the live endpoint or device workflow. Main tracks the default-feed defect as **AUD-ONB-001**, not a duplicate data finding.
- **Source-confirmed explanation:** accepted rows are staging/import progress, not published runtime coverage. `SourceModel.java:1400-1413` refuses carry-forward when a selected failed source has neither prior active rows nor a successful local-fetch timestamp. That makes `generationUnsafe` true; `SourceModel.java:961-997` aborts and clears the new generation even when other imports succeeded. This is the intentional October preservation policy. With previous coverage the failed source can instead carry forward, allowing successful peers to publish; the policy is not simply "any failure aborts".
- **No policy change:** this patch does not activate incomplete first-run generations, disable failing selections, replace provider URLs, modify defaults or change Home's protection label. The new local HTTPS 410 regression preserves that boundary; it does not claim to reproduce the real provider/device incident.
- **Fresh installs:** `AppDatabase.java:109-120,177-196` seeds catalog defaults from the database-creation callback only. Main's catalog fix removes the retired public endpoint from future defaults/presets. The final catalog, including conservative-public-feed header wording, was reread and its current SHA256 is in the ledger.
- **Existing installs, including already-created failed onboarding DBs:** startup does not reseed or reconcile catalog entries. There is no URLhaus-specific migration in the reviewed migration/source code. Removing a catalog row does not delete, disable or rewrite a saved `hosts_sources` row. A preexisting selected retired URL with no prior successful coverage can still block a full update. Existing saved sources and user selections remain unchanged; any remediation needs an explicit user-visible choice or separately authorized migration, not silent disablement. Catalog-only removal is not an existing-user repair.
- Existing retired sources no longer matching catalog URL metadata can fall back to the Custom category unless durable directory metadata applies. This does not change enablement. Home's active-tunnel versus loaded-rules wording and user-facing remediation stay with main.

## Reviewed Production Files

All paths in this ledger are repository-relative. Each entry represents a full-content read, not an `rg` hit. The matching CSV uses exactly `path,sha256,assessment,bug_ids`.

| Path | SHA256 | Specific assessment | Bug IDs |
| --- | --- | --- | --- |
| `app/src/main/java/org/adaway/db/ActiveRootHostsCursor.java` | `325d36026617c37ddc9af06b5851a48b55fb25a01a3b17783ead6e7fb01fa7df` | Reviewed forward-only traversal, cursor closure, redirect precedence and exact/suffix/wildcard allow filtering; no new confirmed defect. | - |
| `app/src/main/java/org/adaway/db/AppDatabase.java` | `a4ea6f3e7781995f2c912da0c6b89e1789c7e26686b0cb14ed0c0fb8a6d15e25` | Reviewed WAL singleton, schema-32 migration registration, storage optimization and asynchronous default/user seeding; cold-start ordering not executed. | - |
| `app/src/main/java/org/adaway/db/Migrations.java` | `c5f74d272065386b37d5a55d912e55ce91ef51cffd9a80ba2b78e0a6a85ab5d6` | Read all 31 incremental migrations plus reverse-key backfills and table rebuild helpers; no confirmed migration bug; schema validation not run. | - |
| `app/src/main/java/org/adaway/db/converter/ListTypeConverter.java` | `cfb421a300f59a7f8bd226b4ba1359fdb92fa098a40b4c6c7bfa72e49d8d371e` | Nullable integer/ListType round trip preserves enum values and delegates invalid-value rejection. | - |
| `app/src/main/java/org/adaway/db/converter/RuleKindConverter.java` | `b4c5b517b3bd7e0e927000a6511c57029c2e499605da7cb99afee8b7eada6113` | Nullable integer/RuleKind round trip preserves EXACT/SUFFIX values; no new confirmed defect. | - |
| `app/src/main/java/org/adaway/db/converter/ZonedDateTimeConverter.java` | `68151f7fbf46c7674bac8bc2c8cc347655bc44ebf956787d7a077ad8e9805688` | Correct epoch-seconds Room converter remains unchanged; SAF unit conversion fixed at SourceModel boundary under AUD-DATA-003; Android verification pending. | - |
| `app/src/main/java/org/adaway/db/dao/HostEntryDao.java` | `b0c99d896154bf29cd47b25d911b14c4f92fb587c8def2fd2fbd30c3dbe74128` | Confirmed disabled/deleted-source leakage in stage-backed export and staged redirect precedence chosen by import ID; reviewed all runtime/export paths. | AUD-DATA-001;AUD-DATA-002 |
| `app/src/main/java/org/adaway/db/dao/HostListItemDao.java` | `7ae035425795221394ba3a7208cb7f23bd0235e5b574a14bf33527f6fc0b8819` | Reviewed active-generation filtering, user-rule lookups and transactional copy/promotion helpers; old generation survives copy-forward. | - |
| `app/src/main/java/org/adaway/db/dao/HostsSourceDao.java` | `2e5ab8a8a7db450c0e7f35d521f2b5f310753620d0aa434582a3ba33bf9dd972` | Reviewed transactional selection propagation and generation-aware statistics; never-downloaded freshness SQL is fixed; no new DAO defect. | - |
| `app/src/main/java/org/adaway/db/entity/HostEntry.java` | `f5a687d64e953069845f89782de8af7fd0a0d0336cf74369bb7cb1cd9a917cc1` | Reviewed composite host/kind identity, host normalization and reverse-label maintenance; schema matches current migration helpers. | - |
| `app/src/main/java/org/adaway/db/entity/HostListItem.java` | `aef67763f97a4195f3d2f4800c493005a54224a2d37bb1ba97ff6191a5df2c19` | Reviewed generation/source FK cascade, covering indexes, normalized host/reverse keys and equality; no new confirmed defect. | - |
| `app/src/main/java/org/adaway/db/entity/HostsMeta.java` | `3d48a836261bdb6d2798a7372333282ef6495abc2902932dd20aa01acdfc908c` | Single-row active-generation entity and zero default match generation publication queries. | - |
| `app/src/main/java/org/adaway/db/entity/HostsSource.java` | `09327ad9a895bff635d280b69922ef18f8de2a06745f470a8c79754d52dc584f` | Reviewed provenance/error fields, URL/type validation and equality; omitted presentation fields need caller-level UI verification, not a confirmed data bug. | - |
| `app/src/main/java/org/adaway/db/entity/HostsStats.java` | `ee0db33c11cd016b38bb76bc668d42880d489bafab703181a3b91046bc75d9fd` | Reviewed aggregate counters and independent materialized/stage-export flags against DAO usage and migrations. | - |
| `app/src/main/java/org/adaway/db/entity/ListType.java` | `ba59310323329b5319185237b17fbe326ff6e7341fba658c76d89b2804422d67` | Stable BLOCKED=0, ALLOWED=1, REDIRECTED=2 mapping and invalid-value rejection match SQL literals. | - |
| `app/src/main/java/org/adaway/db/entity/RootHostEntry.java` | `90f6f0c0dc3776aad6dd770cdb2bd4a5f2fe3c18d8ac2e47e4e593f871f376b8` | Reviewed append-ID schema, root-export projection and lowercase setters; redirect bug is DAO ranking, not entity mapping. | - |
| `app/src/main/java/org/adaway/db/entity/RootHostSkipEntry.java` | `3aca434a00a61595874f42c567e70f604d315460e8136f5cda80767ce04b505b` | Reviewed stage-ID exclusion primary key; schema matches skip-table creation and lookup. | - |
| `app/src/main/java/org/adaway/db/entity/RootHostStageEntry.java` | `ed7b396572eb59c4c25299a5d3c83465f79fe1689bbdd9e646ccbb1b99ec91f6` | Reviewed staging ID, source/generation indexes and normalized reverse keys; no source FK by design, so exports must filter live sources. | - |
| `app/src/main/java/org/adaway/db/entity/RuleKind.java` | `0ede98f4cfc8484d929afc8998a923347a4da0360e805962baa8dd98b365ad0c` | Stable EXACT=0 and SUFFIX=1 mapping and invalid-value rejection match parser/DAO SQL. | - |
| `app/src/main/java/org/adaway/db/entity/SourceType.java` | `3440388ac6368cb93e3a8d0b32a994d190b242a955fdcf3ae33f5d96f1d0288a` | URL/FILE/UNSUPPORTED enum inspected against dispatch; no behavior in enum itself. | - |
| `app/src/main/java/org/adaway/model/source/FilterListCatalog.java` | `7435dcfdd64a6f9424c2f5ab16ee993520849db877cf72121b5631b3e8573d26` | Reread complete main-owned catalog and final header diff after URLhaus removal; three public defaults remain; saved source rows are not migrated; AUD-ONB-001 owned by main. | - |
| `app/src/main/java/org/adaway/model/source/FilterListCategory.java` | `8e592f602c6294284b8b60b88abb5343749094aef0595842b64972706544b556` | Reviewed every category resource binding, default flag and service-breakage flag; resources/UI outside this scope. | - |
| `app/src/main/java/org/adaway/model/source/FilterListCompatibility.java` | `7e3f2b5bbabb6cfbb592b6f856d378cb9fa390e3627da71731536bf37b578027` | Reviewed conservative syntax gate, URL heuristic, labels and ID codecs; URL heuristic is not a security validator; no proven caller-level defect. | - |
| `app/src/main/java/org/adaway/model/source/FilterListsDirectoryApi.java` | `1f8141dff2a3f261384e375eea70adccb22942bdde9a926ce6a624ab18a6710a` | Reviewed every DTO, JSON parser, request and best-URL selector; live API contract and segmented lists not verified. | - |
| `app/src/main/java/org/adaway/model/source/FilterListsSourceMetadata.java` | `4ffb29b323b11aeabbe2407630e1aefbcece7a7c6d045b17fb02c9c542b15eae` | Reviewed apply/copy of all eight provenance fields and optional-ID serialization; no missing copy field found. | - |
| `app/src/main/java/org/adaway/model/source/FilterOperationState.java` | `ac7a82d7adbbdbede31a08a914e9150f5c0f9da4a264825779973cbccf2e7d29` | Reviewed idle/check/download/parse/finalize/stopped/complete mapping and count bounds; no new confirmed defect. | - |
| `app/src/main/java/org/adaway/model/source/SiteCompatibilityAllowlist.java` | `f3215d5d44fe8c5d95fa8f90a23fe659b6d56f5d2ba5d8e5ed762c952a1df0d9` | Reviewed all exact domains, user-source repair and preserve-existing-host behavior; no new confirmed defect. | - |
| `app/src/main/java/org/adaway/model/source/SourceApplyState.java` | `b180cce362889e42b27f93b97e5ff95ec891fc074a59039ef68fef870a405491` | Reviewed stable configuration fingerprint, partial acknowledgements and historical-worker guard; lifecycle persistence requires UI owner verification. | - |
| `app/src/main/java/org/adaway/model/source/SourceLoader.java` | `99666e177cc0106a9927e5b07334d003b9048e828d0bfc66f74ea9866fdd13b9` | Reviewed complete parser and insert paths; implemented validated multi-alias hosts ingestion with per-line skipped accounting; 4 focused Android regressions added, awaiting main execution. | AUD-DATA-006 |
| `app/src/main/java/org/adaway/model/source/SourceModel.java` | `b20a872ce49db903ec318e3eaf451e3edbdd93e812e1f4f78f4a7e0dc19f29db` | Reviewed full/targeted updates; implemented SAF millisecond/null handling, enabled-URL-only offline guards and first-download freshness check; safety publication policy unchanged; Android verification pending. | AUD-DATA-003;AUD-DATA-004;AUD-DATA-005 |
| `app/src/main/java/org/adaway/model/source/SourceUpdateService.java` | `0cbcbe709c7f66785fbd85a12b54234a65f3bbfcdb8249e14d9dbad2a4d72505` | Reviewed constraints, replacement policy and both worker bodies; immediate retry fix remains present; periodic worker is affected by AUD-DATA-005 in SourceModel. | - |
| `app/src/main/java/org/adaway/model/source/SqlUpdateDeduper.java` | `dace21f426fefbe92c325f1eb0d0a5d3cc1b902f37891dabf2aaf53eff64ee0f` | Reviewed per-source rule identity, pending flush/stage writes, transactional carry-forward and scratch cleanup; no new confirmed deduplication defect. | - |
| `app/src/main/java/org/adaway/model/source/WaTgSafetyAllowlist.java` | `acc343817fb2d470efba2ffb96c0e80d84be4217a791d3e2a430b2c768aad97c` | Reviewed all required entries and both ensure paths; existing user choices preserved by host presence; no new confirmed defect. | - |

## Related Test Evidence

These **12 complete test/helper reads** are separate from the 33 assigned production ledger rows. Reading evidence is not a claim that tests ran or passed.

| Path | SHA256 | Evidence and gap |
| --- | --- | --- |
| `app/src/androidTest/java/org/adaway/db/SourceDbTest.java` | `1bfde29cb057d8c2e3de24892ec5c81cc8710bda204431da8067d46862f02352` | Entire file read. Small/large runtime, allow, redirect, stage-completeness and cursor tests. Disabled-stage test at 779-801 uses one enabled candidate, so it does not exercise stage-backed export. Stage priority test at 666-724 has no wildcard allow and tests a different winner path. |
| `app/src/androidTest/java/org/adaway/db/HostsSourceFreshnessTest.java` | `7d988d48873454d60c9b82d8f25a43ab9209811f0c4e0c6cf4f77e13b7584b98` | Entire updated main-owned file reread. Fresh default count now derives from catalog size. DAO checks cover first-download and valid empty sources; SourceModel checks covered separately. Not executed here. |
| `app/src/androidTest/java/org/adaway/model/source/SourceModelOfflineFileTest.java` | `bae2c9bb9a2cd06f82a6b78bf40fa3dbcab41dede8ad8d4757f94ab5c1743e0a` | Entire file reviewed; original six full-update tests plus nine new local/targeted/check/freshness controls. Runtime rows, metadata, gate release and staging cleanup asserted. Not executed. |
| `app/src/androidTest/java/org/adaway/model/source/TestHostsContentProvider.java` | `13481f424efb0d700aa5cc878d90aa13de036d508ac909103541bd7621909d0f` | Entire helper reviewed. Added per-URI modified=milliseconds/unknown for deterministic SAF date tests; default current-time and backup behavior unchanged. |
| `app/src/androidTest/java/org/adaway/model/source/SourceModelRuntimeTruthMutationTest.java` | `306e2636257ba8ac7379fa26514df0effbceebb106353cd238d11365860ae542` | Entire file read. Real toggle/user mutation plus syncHostEntries asserts small active runtime truth; no staged >500000-row mutation fixture. |
| `app/src/androidTest/java/org/adaway/model/source/SourceUpdateServiceWorkManagerTest.java` | `d9000972f6f175f95f6671fb1f4470d439760022fa69a14366b9b5eff376ecce` | Entire file read. Covers immediate false/busy retries and schedule constraints; not periodic HEAD-without-date first-download behavior. |
| `app/src/androidTest/java/org/adaway/model/source/SourceLoaderDatabaseSemanticsTest.java` | `076efb00a22416529edd4014b1c9d93af0d57b91a313db07fb23502d1de70f2c` | Entire file reviewed; original six tests plus four alias regressions across SQL/deduper and Room paths, runtime resolution, comments, invalid aliases and redirect/allowlist boundaries. Not executed. |
| `app/src/test/java/org/adaway/db/entity/HostsSourceTest.java` | `0ae6d385789ceb269e1a7fd482d7ae8fd687c7e3e5b04999569144e147fcc6a0` | Entire file read. Equality/error redraw contract only; does not establish live UI behavior for label/provenance-only changes. |
| `app/src/test/java/org/adaway/ui/hosts/CategorizedSourcesAdapterTest.java` | `99677e81a2e7a8f3a3e76379411b769be46c4656a8a008d6aa02ae4c5e4fbb59` | Entire file read as related evidence only. Category/provenance formatting tested; no DiffUtil update integration. UI product files not reviewed. |
| `app/src/test/java/org/adaway/model/source/SourceLoaderTest.java` | `4b165e96617e414b82b12a2ca811e0e6e571705c3b91ecfb17c7e1884eba8395` | Entire file read. Host regex test uses one hostname plus comments; remaining tests validate hostname/wildcard sets, not alias ingestion. |

| `app/src/androidTest/java/org/adaway/model/source/SourceModelHttpConditionalTest.java` | `8bfb54e012a035da08dc23785dd9c7e2a19e2c4641a3da19374a4c018f31cf8a` | Entire file now reviewed. Added failed-first-download HEAD, successful-empty weekly-refresh and fresh-410 abort controls; existing conditional/cancellation tests retained. Not executed. |
| `app/src/androidTest/java/org/adaway/model/source/SourceModelGenerationFailureTest.java` | `1c0f482b0f6935433980ee74f3c0ef7f72dc3dc520208230ede13e3ddd157722` | Entire file now read, unchanged. Existing generation failure/carry-forward, mutation gate and finalizer coverage supports unchanged safety contract. Not executed. |

### Partial Reads and Scans Only

- `app/src/androidTest/java/org/adaway/db/HostDbTest.java`: focused stage/redirect/disable search output only. Not fully reviewed.
- `app/src/androidTest/java/org/adaway/db/MigrationTest.java`: migration test-name scan only. Do not mark migration tests reviewed/passing from this report.
- `app/src/test/java/org/adaway/model/source/SourceLoaderParserPatternsTest.java` and `Generation304MigrationTest.java`: test-name/symbol scans only. Not fully reviewed.
- Initial `rg --files` enumeration of other tests/tasks only located potential evidence; it confers no review coverage. No other production packages, UI/home implementation or inventory scripts were audited here.

## October Findings Rechecked

Current assigned production code retains the October fixes for parser-worker shutdown before cleanup, identity-only reader sentinel, rejection of ABP path/caret overblocking, current enabled selection inside the writer transaction, FILE parse-failure handling, all-unrecognized nonempty rejection, byte/line bounds, weak/cleared ETags, cache-expiry network validation, unsolicited 304 rejection, synchronized HTTP-client initialization, guarded source mutations, immediate-worker retries, unsafe carry-forward abort and local-only full updates.

These are not relogged. Historical DATA-08 was in `model/git` and is outside this ownership; no current Git-provider review claim is made. The remaining offline targeted/check-only issue is explicitly distinguished in AUD-DATA-004. Existing worker/sentinel tests were read, but their execution history in October is not represented as a new verification result.

## Coverage Gaps and Main Handoff

1. **Main: compile/run the three modified instrumentation classes and existing generation/loader controls listed above.** These 16 regressions are awaiting combined validation, not verified fixes. Root-only AUD-DATA-001/002 remain logged/deferred; do not schedule rooted scope from this handoff.
2. No Room annotation processing, migration validation, supported-API matrix, installed app, root hosts write, VPN, WorkManager scheduler execution or device storage-pressure test was run. Main retains shared Android build ownership.
3. Host-side SQLite probes validate SQL predicates/ranking in isolation, not Android transaction scheduling, Room invalidation or live root apply. Timestamp/regex probes likewise demonstrate arithmetic/capture loss only. The two root-only bugs remain `source-confirmed`; the four implemented fixes are `implemented-awaiting-test`.
4. All migration bodies were read; production-to-schema32 and very old schema paths still need `MigrationTest` and schema validation. No migration bug was inferred from index complexity alone.
5. No catalog/filter endpoint availability, live FilterLists API/schema, segmented-list behavior, body size across all selected sources, adversarial provider blocking, process-death recovery or full concurrent mutation schedule was tested.
6. `HostsSource.equals` omits label/format/provenance/size fields. The related tests do not prove a current adapter redraw failure; main/UI owner must trace the actual comparison path before filing one. Similarly, source apply acknowledgement across screen recreation needs its UI owner. Neither is submitted as a confirmed bug.
7. Root export can deliberately retain duplicate blocked lines on some large fallback paths; this was not relogged as lost protection. Allowlist preservation of existing user rules and conservative rejection of browser-only syntax were treated as intentional policies, not defects.
8. Merge the updated `data-bugs.csv` using the six preserved IDs. Do not overwrite the central ledger. Existing-user URLhaus remediation, automatic repair of previously corrupt FILE timestamps, and actual periodic-worker integration remain open follow-up boundaries, not extra implemented fixes.

## Local Verification Performed

- Complete assigned-file reads and SHA256 capture: 33/33.
- Initial baseline comparison had no assigned production differences. Post-implementation ledger refresh covers this owner's SourceLoader/SourceModel and main's final catalog; other product hashes remain unchanged.
- Isolated in-memory SQL: disabled stage row emitted (500002 actual versus 500001 enabled); copied-stage redirect chose earlier ID (1.1.1.1 actual versus 9.9.9.9 source-priority winner).
- Host-side fixed-date calculation: milliseconds interpreted as seconds moved 2023 to year 55840.
- Host-side equivalent hosts regex: first alias captured, second alias discarded.
- Scoped `git diff --check` passed after final product/test edits. CSV schema/IDs, all 33 production hashes, and complete-review evidence hashes checked during artifact finalization.
- No Gradle, adb, Java/JUnit, Android instrumentation, builds or shared pipeline execution. Source/model behavior tests remain pending with main; no red/green Android claim.
