# Remaining Connected Tests: Read-only Audit

Date: 2026-11-25
Branch: codex/full-codebase-audit
Scope owner: remaining PENDING androidTest files, excluding ui/home, model/backup, and the already-reviewed Catalog selection / long-label tests.

## Coverage And Boundaries

- Fully read all 52 initially assigned pending files (19,937 lines), then both late-added pending network fixtures (553 lines): 54 full reads and 20,490 source lines total. Intake and final SHA-256 values agree for the original 52; late additions have their exact read hashes.
- The intake had 63 pending androidTest files: the original 52 plus 11 excluded Home/backup files. During final validation, main added LegacyVpnNetworkCallbackLiveInstrumentedTest and LegacyVpnNetworkMonitorInstrumentedTest to the inventory and concurrently marked InstrumentedTestState reviewed at the same hash. Both additions were read fully; the reset helper had already been read before its new current-review row appeared. Current pending count outside exclusions is 53, all covered here. No completed current-byte review was intentionally duplicated.
- The files sidecar contains 68 rows: 54 FULL_READ_PENDING (pending when selected), 2 FULL_READ_SUPPORT, and 12 explicitly PARTIAL support/search/bytecode rows. A supporting file's whole-file hash identifies its bytes; it does not imply that unlisted lines were reviewed.
- DbTest and the test manifest were reread fully only as dependencies. Production excerpts establish the dispatch chain and rule-scope contract, not new full-file production coverage. No Home or backup androidTest file was reviewed.
- Only the three remaining-connected-tests sidecars were written. App source, tests, previous system/legacy sidecars, central ledgers, staged work, settings and device state were untouched.
- No Gradle, adb, emulator, connected tests, privileged shell, system-host write, app compilation, staging or commits. Main owns combined test runtime. Prior passing runs are not treated as source-review or runtime proof for these findings.
- Root runtime exclusion remains in force. No real root smoke is proposed as part of this audit.

## New Confirmed Findings

The six CSV findings are source-confirmed defects in fixture safety or assertion discrimination, not six new production defects. Reproduction/mutation descriptions below are proposed and were NOT RUN.

### CONN-AUD-003 / P2: Root restore can fail silently

Location: `app/src/androidTest/java/org/adaway/model/root/RootModelApplyInstrumentedTest.java:112-128`.

The opt-in is correctly enforced at line 47. Inside that guarded fixture, however, backup runs `cp` and then `sha256sum` as separate shell commands; restore similarly runs copy, chmod, chown and hash. The final successful hash masks failed earlier commands. The fixed backup path can also contain stale readable bytes after a failed backup copy. No digest equality is asserted, so cleanup can report success while leaving generated system hosts installed or restoring stale data.

Static dependency proof: the pinned libsu 6.0.0 cached binary's CommandSource writes each supplied command followed by a newline; JobTask appends an END_CMD that captures the final `$?`. Read-only javap inspection was used, not a shell/device experiment. The exact jar hash is in the files sidecar.

Narrow remedy: check every copy/metadata step, use a unique verified backup, and compare restored bytes/digest to the captured preimage. Preserve the explicit opt-in and root exclusion. Test failure handling with controlled command results before any separately authorized privileged smoke.

### CONN-AUD-004 / P2: Ordinary quick-preset test can perform a root apply

Locations:
- `app/src/androidTest/java/org/adaway/ui/discover/DiscoverQuickPresetInstrumentedTest.java:48-101`
- `app/src/androidTest/java/org/adaway/testing/InstrumentedTestState.java:47-65`
- `app/src/main/java/org/adaway/ui/discover/DiscoverFragment.java:167-169`
- `app/src/main/java/org/adaway/model/source/SourceUpdateService.java:224-236`

The ordinary fixture selects ROOT, empties sources, and clicks the real safe-preset chip. It asserts queued work but does not inject an inert apply model, deterministic source model, or blocked test scheduler. Production enqueues immediate work with network/storage constraints and no delay. Once retrieval succeeds, the worker obtains the application's ROOT model and calls apply, reaching the system-host copy. A connected root-capable test device can therefore write system hosts if the worker beats teardown. Cancellation cannot undo a completed write and this fixture has no hosts preimage/restore.

Narrow remedy: isolate execution with recording/no-op apply and deterministic source work, or a test scheduler/WorkerFactory, while preserving the real enqueue assertion. Keep privileged apply in the guarded root fixture. This is distinct from REL-002: the fixed external UX runner's emulator/consent gate does not isolate this standalone fixture's worker.

### CONN-AUD-001 / P3: Suffix plan assertion repeats the exact plan

Location: `app/src/androidTest/java/org/adaway/db/SourceDbTest.java:494-518`.

Line 515 performs SQL-string replacement on already-rendered EXPLAIN output. No suffix SQL is executed in this branch; lines 516-518 reassert the exact plan. A suffix-specific bad plan is undetectable here. The earlier separate plan test at 469-485 executes different SQL and does not make this branch valid.

Narrow remedy: retain the SQL separately and call queryPlan on both actual kind variants. Assert each returned plan independently.

### CONN-AUD-002 / P3: Tag action has no discriminating fixture

Location: `app/src/androidTest/java/org/adaway/ui/discover/DiscoverFilterListsFiltersInstrumentedTest.java:73-97,119-130`.

Search for regional already leaves IDs 101/102/103, all tagged Regional. Selecting that tag still expects three; language and compatibility reduce these to two then one even when tag selection does nothing. Removing the tag listener would preserve every asserted count.

Narrow remedy: clear search before selecting the tag and assert four-to-three plus exact IDs, or seed another-tag row matching the search. This does not claim the current tag listener is broken.

### CONN-AUD-005 / P3: Null accessibility root counts as absence

Locations:
- `app/src/androidTest/java/org/adaway/ui/prefs/PrefsAdBlockMethodSwitchInstrumentedTest.java:134-151`
- `app/src/androidTest/java/org/adaway/ui/hosts/FilterSetSaveApplyInstrumentedTest.java:377-393`

The first helper returns for a null active-window root; the second converts that state to a null search result and succeeds. A transient unavailable snapshot during a transition can pass the negative check without inspecting the screen. Earlier positive checks do not guarantee this later snapshot exists.

Narrow remedy: boundedly await a non-null expected target window, then assert absence on that snapshot; null must retry or fail. Preserve the existing positive screen checks and add a null-window control.

### CONN-AUD-006 / P3: Delete assertion permits a retained disabled row

Location: `app/src/androidTest/java/org/adaway/ui/lists/ListsRuleActionsInstrumentedTest.java:147-151,226-241`.

After confirmed deletion the test asks for zero enabled rows. The counting query filters enabled=true, so an implementation that only disables the row also passes. This is a concrete deletion-regression blind spot, not evidence that current production deletion is wrong.

Narrow remedy: capture the target identity and assert its physical absence irrespective of enabled/type. Retain the cancellation control that requires the row to survive.

## Silent Checks And Proof Limits

These are bounded evidence qualifications, not additional bug rows or a broad coverage wishlist.

| File / Check | What Can Be Skipped Or Unproven | Narrow Handling |
| --- | --- | --- |
| RootModelApplyInstrumentedTest:47 | Explicit rootHostsApplySmoke opt-in means ordinary runs skip the privileged test. | Report skipped, not root-proven; retain exclusion. CONN-AUD-003 concerns cleanup only when explicitly opted in. |
| ApkDownloadProviderInstrumentedTest:41-42 | Real DownloadManager smoke requires adawayLiveDownloadSmoke=true. | Report skipped when absent. When enabled it requires successful provider completion and tests verification failure/installer interception, not a successful real installation. |
| SourceLoaderPerformanceTest:239-242,303-308,416-419,1240-1245 | Requested large-scale cases skip without size arguments; optional zero/absent time budgets do not assert a speed bound. | Record exact size/budget arguments and report measurement-only where no budget applies. Default 10k budget checks are separate. Private hosts-file generation is not privileged installation. |
| UiEvidence:19-21 | Missing evidenceDir intentionally produces no screenshot. | Record evidence disabled. A screenshot file alone is not a rendered-content assertion. |
| UxDeviceMatrixTest:146,193-194,237-267 | Fixed 750ms wait can capture Loading; zero-size TextViews and null layouts escape text checks; last-line full consumption is not asserted. | Require seeded/loaded rows before claiming Discover label proof; assert positive geometry and final line end. Known Loading limitation and AUD-SRC-012 are not relogged; the separate frozen label fixture remains the intended focused proof. |
| PrefsLanguageTelemetryInstrumentedTest | Supported branch checks stored opt-in, unsupported branch cannot exercise SDK enablement. | Attribute proof to preference behavior only; do not report SDK transport/lifecycle proof from it. |
| PrefsBackupRestoreSafInstrumentedTest | Cancelled picker launch assertions and direct provider-URI exporter/importer roundtrip are separate. | Do not call this a successful end-to-end picker/grant flow. Pipe publication is explicitly awaited. |
| MoreToolsEntryPointsInstrumentedTest | HTTPS launch is intercepted; no-handler branch only proves Home remains resumed. | Do not infer browser rendering or exact destination/failure-feedback proof. |
| OnboardingFirstRunInstrumentedTest | Saved pending state is set reflectively; no-root auto-detect case depends on environment. | No live system-consent or rooted/non-root environment matrix claim. |
| SourceUpdateServiceWorkManagerTest | Busy retry injects UPDATE_IN_PROGRESS from the model, rather than holding the application operation lease. | Attribute that case to retry mapping, not held-external-lease exclusion. |
| SourcesUpdateAllInstrumentedTest | Resolver result is already BLOCKED from seeded active rows before the sync under test. | Counter/latch assertions prove callback wiring, not independently that this sync changed materialized truth. A distinct pre/post result would be needed for that stronger claim. |
| LegacyVpnNetworkCallbackLiveInstrumentedTest:39-60,102-105 | Both cases skip without adawayLiveNetworkCallbacks; handoff also needs adawayLiveNetworkHandoff and an externally prepared active VPN. | Report skipped conditions explicitly. Real callbacks/ready DNS selection and same-transport identity change are not packet/TUN or exact-default-route proof. On API28-30 the extra real request can affect retention; the owning lane already documents this limitation. No new defect found. |
| LegacyVpnNetworkMonitorInstrumentedTest | Synthetic handles and a recording registration adapter exercise simulated API decisions, not framework behavior on seven real OS versions. | Attribute the 15 methods to deterministic registration/state contracts only. No silent empty-loop or new confirmed fixture defect found. |

No new claim is made from empty loops or screenshots alone. The per-file CSV describes the actual assertions and fixtures for the remaining files, including database isolation, local responses, bounded waits and synthetic apply boundaries.

## Production Contradiction Check

`FilterListsSubscribeAllWorkerDoWorkTest` supplies explicit IDs alongside query/tag/language. Its negative rows are already excluded by IDs. This is not a new production bug: `FilterListsSubscribeAllWorker.java:546-548` deliberately uses explicit IDs plus compatibility when IDs are supplied. The test proves that selected-ID contract; it does not independently prove each filter. The initial candidate was rejected, not logged.

Real root/VPN installation is not inferred from recording apply models, direct resolver/log calls, reflected callbacks or private file generation. Shared fixture preference/database/work clearing requires disposable app data. Existing runner destructive-device safety work is REL-002 and was not duplicated.

## Static Checks

- Recomputed hashes of all original 52 assigned files against the intake snapshot: unchanged. Rechecked both late-added full reads and all supporting hashes.
- Reconciled the changing inventory minus explicit exclusions: all 53 currently pending paths covered, plus the already-read reset helper concurrently moved to reviewed. The initial inventory equality check correctly flagged that delta; it was resolved by reading both additions and recording the concurrent review.
- Validated sidecar CSV headers, widths, unique paths/IDs, all six bug links, full-read line counts, and current file hashes.
- The 11-column bug schema matches the central bugs.csv schema. Central ledgers were read only.
- No tests were executed. The only dependency inspection beyond source reads was read-only javap on cached libsu CommandSource/JobTask; no dependency code was invoked.
- App/test inputs remain frozen from this lane. All remedies are proposals for main to assign; none were implemented by this read-only audit.
