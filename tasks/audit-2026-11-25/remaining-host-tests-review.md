# Remaining Host Tests Source Review

Date: 2026-11-25
Checkout: `codex/full-codebase-audit`, HEAD `f7dbbb5e7a8eca6983135f04adc945bab6518308`.
Evidence is the shared working tree, including existing staged changes, not HEAD alone.

## Scope and Result

- Selected every `app/src/test/` row marked `pending` in `tasks/audit-2026-11-25/files.csv` at this lane's start: **46 files, 7,866 lines, 377,950 bytes** (not approximately 47).
- **All 46 target files were read in full. Zero target files are partial or unread.** Long files were read in contiguous chunks. The files sidecar records each current SHA-256, exact read extent and individual assessment.
- All 46 file hashes were compared again after reading and matched the start-of-read snapshots. No assigned test-file drift was observed.
- **One source-confirmed test assertion defect: JVM-AUD-001 (P3).** No new confirmed product defect or destructive test helper was found in this bounded pass. This is not a whole-product correctness claim.
- Context-only production reads are listed separately below; partial reads do not become full-file coverage. Files merely named/read by a test are not automatically counted as inspected by this audit.
- No app/test writes, dependency work, Gradle, JVM test invocation, test helper execution, emulator, adb, staging or commits. No test results or build success were inferred from source or imported from other lanes.
- Only the three `remaining-host-tests-*` sidecars were written. Central inventory/todo and frozen `domain-fixes-*` sidecars were not edited.

## Confirmed Finding

### JVM-AUD-001: Backup exclusion assertion accepts inclusion rules (P3)

Location: `app/src/test/java/org/adaway/model/backup/AppBackupAgentContractTest.java:28-34`.

The assertion says cloud and device-transfer backups must not include arbitrary root files, but tests only for the two parent tag strings and for `domain="root"` / `path="."` anywhere in the resources. Neither the element name `exclude` nor its association with each parent is checked.

A precise static counterexample is replacing every `exclude` element with `include` while keeping all attributes. Every checked substring remains present. Another is removing only the device-transfer exclusion while retaining its non-self-closing parent and the cloud exclusion: the aggregate substring checks still hold. These counterexamples were reasoned from the predicate; neither XML nor test was mutated or executed.

The current two XML resources do contain the intended exclusions. This is a defective regression guard, **not evidence of a current production backup leak**. The bounded correction is a parsed-XML assertion for each required parent/child/attribute relationship, with negative include and missing-section fixtures. No correction was made during this freeze.

Existing bug sidecars were searched for this assertion and backup exclusion issue; no duplicate finding was identified. Bug CSV uses the existing exact 11-column schema.

## Assertion and Evidence Boundaries

These are source-review limitations, not additional bugs or a request to expand the current implementation:

- `Generation304MigrationTest.java:565-746`: generation arithmetic, array migration/cleanup/copy and the failed-never-synced Boolean are implemented inside the test. They cannot establish the behavior of the actual DAO or SourceModel. The earlier reflection/source checks do not turn these simulations into a Room/HTTP-304 regression. The phrase "core behavioural regression" at lines 628-629 exceeds that evidence. Current production carry-forward uses `copySourceGenerationReplacingTarget` and preserves old rows; the retag example is not the current full-update path.
- `DownloadErrorTrackingTest.java:8-15,44-49`: comments claim DownloadResult overload verification and reflection, but every case only sets/gets the HostsSource error field. There is no DownloadResult or reflective call in the test. It establishes entity-field behavior only.
- `DiscoverPresetSubscriptionTest` and `HomeNavigationSourcesContractTest`: most checks are token presence/order across source/XML/docs, including transaction, cancellation, durability, lifecycle and visual claims. They do not execute those flows. Discover does directly call the ledger formatter and retry/unsupported-ID parsers; that narrower behavior is real product logic. Home fallback PNG checks establish existence and nonzero length, not rendered asset correctness.
- `SourceModelCarryForwardConcurrencyTest` checks a collection declaration and worker-add spelling; it does not run concurrent workers. `ApplyConfigurationSnackbarContractTest`, `HostsSourcesActionsContractTest`, `LogSortActionContractTest` and onboarding/adware intent contracts similarly do not demonstrate callback delivery, UI actions or platform behavior.
- `UserStoryStatusTrackerTest` validates TSV fields and specific historical evidence/status strings, paths and exit-code contents. It neither reruns those workflows nor ties a claimed historical pass to the current app binary.
- `DomainCheckerViewModelAdviceTest` calls the actual helper for all four Boolean combinations, but nonempty/non-null assertions and one `allow` substring cannot establish most named advice semantics. The current helper was read and no current contradiction was found. No retained failure/retry claim is made from this file.
- `FilterListsSourceMetadataTest`, `FilterListsSubscribeAllWorkerTest.applyCandidatePersistsFilterListsProvenanceWhenListIdIsKnown` and `FilterSetStoreTest` exercise entity fields or pure helpers, not database/preferences durability.
- `DefaultListsSubscriberTest` really invokes production preparation/initializer logic with a fail-closed DAO proxy and manually held queues. ViewModelStore reuse is a retention model, not Activity recreation. The offline downloader is a supplied callback that writes error/date fields, so the test does not prove real download failure or retry behavior.
- `SourceApplyStateTest`, parser/extractor tests, backup format/reader tests, state resolvers, profile diff and comparator tests call real product logic with independent expected outputs. This audit checked their actual assertions and fixture boundaries; it did not run them.

## Helper Safety and Product Checks

- `RootModelShellSafetyTest.java:17-39` is the notable external-process helper: it creates a fresh temporary directory, uses exact quoted filenames, invokes an unprivileged shell command with a missing source, bounds the process wait to five seconds, and deletes only its exact target and directory. Current `ShellUtils.hostsCopyCommand` chains ownership/permissions after copy success. No broad deletion, repository write or system-hosts target was found. The test and shell were not invoked.
- `FilterListsDirectoryApiTest` uses interceptors that supply responses/throw without `chain.proceed`; the active tests do not request real directory servers. `GitHostsSourceTest.testLastUpdateFetch` is explicitly ignored across its three parameterizations; its live fetch is not counted as covered.
- Other inspected helpers read files, build bounded in-memory fixtures, use finite Reader input, or mutate in-memory collections/queues. `AiSurfaceContractTest` closes its read-only `Files.walk` stream; its negative file-existence checks do not delete files.
- The actual backup agent/export-restore ordering and XML exclusions were read, not inferred from its contract test. Production profile diff agrees with the asserted disable-all/missing-only counts. Production onboarding preparation/initializer matches the exercised in-memory path.
- `RedirectionAddressValidator` uses literal-only `InetAddresses.forString` and family checks. Its accepted loopback defaults are preference configuration, not the separate public-IP custom redirect-rule policy; the tests do not contradict that distinction.
- `LeakStatusTest` asserts configuration-derived risk flags. Production `LeakStatus` confirms that running-VPN coverage is a state classification; these tests do not observe real DNS traffic or establish leak-free routing.

## Context-Only Reads

These are not extra pending-test coverage rows. Hashes identify entire current files; "Partial" means only the stated lines were reviewed in this lane, despite an entire-file hash.

| Path | Read Extent | Context Reviewed | SHA-256 |
|---|---|---|---|
| `app/src/main/java/org/adaway/model/backup/AppBackupAgent.java` | Full (1-82) | Entire file; backup/export/restore ordering and error handling; source only | `7a0e5eb724e9b3c552cf4be0646560c931844012937d6b7a75eb362690cc41de` |
| `app/src/main/res/xml/data_extraction_rules.xml` | Full (1-13) | Entire file; cloud-backup and device-transfer root exclusion elements | `2d9361dacc7911f63fe96cf891b1fe6f36343dad2df35e281764d02560b3b801` |
| `app/src/main/res/xml/full_backup_content.xml` | Full (1-6) | Entire file; legacy root exclusion element | `c4a4d5ad5fb91fb839f02854272e8ebc22ce5ddbb041315fc9853d7146322380` |
| `app/src/main/java/org/adaway/ui/prefs/RedirectionAddressValidator.java` | Full (1-23) | Entire file; literal-only family validation via InetAddresses.forString | `64273bec2974f23d4894b01d9dd0bb351e039fd150e3a177c43ecfd38d81322a` |
| `app/src/main/java/org/adaway/ui/onboarding/DefaultListsSubscriber.java` | Full (1-68) | Entire file; actual preparation ordering and eligibility | `0658b15c75ee4f2f3e4ceef7b35dfbe2b42046017fb24c993e561ea5a763bc33` |
| `app/src/main/java/org/adaway/ui/onboarding/InitialSourcesViewModel.java` | Full (1-73) | Entire file; attempt guard and executor handoff/finally | `538dda874a3e221bcc4c294a97246582b808b6b497de72802d388310dab4ce6f` |
| `app/src/main/java/org/adaway/ui/discover/FilterListsSubscriptionState.java` | Full (1-55) | Entire file; compatible subscription classification | `b15d3b9a33fd1a16d205239ed9b4eb9c79649adea62408f09d51e3c9884e5149` |
| `app/src/main/java/org/adaway/ui/discover/FilterListsUiState.java` | Full (1-27) | Entire file; loading/empty/error/filter classification | `caff1f644e79f92c5cea49f0d47cdc533732932157daebd772cb896310cd5be0` |
| `app/src/main/java/org/adaway/ui/hosts/FilterProfileDiff.java` | Full (1-101) | Entire file; missing-only and disable-all counts | `0dd500aed9cad4322315fbdf1a130b10c4411c43512512df8592ac08e5a563eb` |
| `app/src/main/java/org/adaway/ui/log/LogEntrySort.java` | Full (1-70) | Entire file; comparator definitions | `18c1baa37cd01cb41848b697201d1d18c6c9eceeb8467f5ade138bc7d4733128` |
| `app/src/main/java/org/adaway/ui/lists/ListsFilter.java` | Full (1-41) | Entire file; wildcard conversion/isEmpty | `f8afc8d8ed0f596e50b885f4d0d98694ad2466d627e1a15aa62ca2954e254bcd` |
| `app/src/main/java/org/adaway/ui/home/LeakStatus.java` | Full (1-158) | Entire file; risk classification vs live system inputs | `25906fad06f6a7d201fe803c96f1dfbc8bf936c9db92899b4a42ddc30d989581` |
| `app/src/main/java/org/adaway/model/root/ShellUtils.java` | Full (1-114) | Entire file; hostsCopyCommand uses escaped arguments and conditional ownership/mode commands | `0b3a490c055627f772c92f8833e6eec450e06fbb430806f14c19a96b2e432b6d` |
| `app/src/main/java/org/adaway/model/root/RootModel.java` | Partial | 205-232 only; sourceHeader and surrounding hosts output | `0b648dea499625f3cf92fa4c8c3a84c8b0b93384c72db37c8806326a008790a4` |
| `app/src/main/java/org/adaway/ui/domainchecker/DomainCheckerViewModel.java` | Partial | 235-264 only; buildAdvice and adjacent result/source-name/status logic | `47e5cd8980a3328dc8120a890f7cc0b9aa02b42ed7aa5e24613b6d82b3989f91` |
| `app/src/main/java/org/adaway/db/dao/HostListItemDao.java` | Partial | 65-125 only; generation count/clear/copy/migrate/transaction queries | `7ae035425795221394ba3a7208cb7f23bd0235e5b574a14bf33527f6fc0b8819` |
| `app/src/main/java/org/adaway/model/source/SourceModel.java` | Partial | 1390-1455 only; carry-forward and generation promotion; plus targeted symbol search | `b20a872ce49db903ec318e3eaf451e3edbdd93e812e1f4f78f4a7e0dc19f29db` |

## Handoff

The files sidecar has exactly 46 target rows with schema `path,sha256,assessment,bug_ids`. The bugs sidecar has one row with schema `bug_id,priority,area,title,status,files,reproduction,expected,observed,fix,test_evidence`.

Read-only metadata validation confirmed both CSV headers and row widths, unique target paths, exact agreement with the 46 currently pending JVM paths, and matching hashes for all target and contextual files. This validation did not invoke app code or tests. All three sidecars are untracked and unstaged.

All conclusions above are source review. No runtime result, mutation-test result or build compatibility is claimed. App/test inputs remain frozen by this lane; main retains ownership of integration and any later test-guard correction.
