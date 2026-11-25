# Independent Home Patch Review

Date: 2026-11-25. Initial review: working-tree diffs against `d51eb7aea06871c2c6ad2bbedb9ddbaaaca48d77` for the eight files in the original snapshot below. Follow-up: main approved implementation of the three findings, including the bounded shared-operation wrappers. No builds, tests, emulator operations, adb, commits, or pipeline actions were performed by this lane.

## Accepted Fix Handoff

Main accepted all three findings. Canonical merge IDs are `HOME-PATCH001`, `HOME-PATCH002`, and `HOME-PATCH003`; the original headings below retain their review aliases with the extra separator. See `home-patch-bugs.csv` for central-schema records.

**Implementation and regression sources are complete and stable for main's combined build. Status for all three: `implemented-awaiting-test`.** Compilation and regression execution remain pending; no existing or new Home changes are marked verified by this lane. The earlier pause ended when main approved the exact external-file scope.

Implemented:

1. `HOME-PATCH001`: `HomeViewModel` retains authorized enable intent in `SavedStateHandle` until lease acquisition succeeds. LiveData notifies the current view; shared-operation/progress completion retries deferred work. Cancellation and method change clear the request. An external apply already reflected in model state consumes the request without another apply.
2. `HOME-PATCH002`: `InitialSourcesViewModel` start/finish callbacks span preparation through the update callback. Home disables protection across that span, coalesces the bootstrap update, and waits for a held lease instead of losing the one-shot callback. No-download and exceptional callback paths release preparation once. The existing initialization API remains available.
3. `HOME-PATCH003`: application-owned `SourceOperationCoordinator` provides a nonblocking, non-reentrant lease plus observable busy state. Home acquires before dispatch. Approved update/apply callers hold the lease through final apply; try-with-resources or finally releases on success/failure/early return. Dispatch rejection also releases UI leases. Busy workers retry; interactive Sources/snackbar paths use existing failed/retry feedback.
4. Wrapped callers: both `SourceUpdateService` workers, global/per-source `FilterSetUpdateWorker` paths, all/single-source `HostsSourcesViewModel` paths, and standalone `ApplyConfigurationSnackbar`. The snackbar's delegated ViewModel branch does not acquire a second lease. No code wraps or changes `SourceModel.runSourceMutation`; SourceModel's data-agent output remains untouched by this lane.
5. Added eight Home connected-test methods and four JVM test methods. Updated one existing onboarding source-contract assertion for the new callbacks. No Gradle/dependency/build changes.

Cross-file ownership was explicitly approved before implementation. Product files and tests were declared stable to main; remaining edits were report/CSV status only. `git diff --check` passed after implementation. This is source-level validation, not compilation, runtime, or visual QA.

## Regression Handoff

All tests below are **added, not run**:

| Test class | Added coverage |
| --- | --- |
| `HomeLaunchNavigationStateInstrumentedTest` | `authorizedEnableWaitsForExternalCompletionAcrossRecreation`, `authorizedEnableWaitsForStoppedImportAcrossRecreation`, `authorizedEnableWaitsForFailedImportAcrossRecreation`: held external lease, controlled terminal progress, one retained enable after release. |
| `HomeLaunchNavigationStateInstrumentedTest` | `cancelledConsentAndMethodChangeDoNotReplayEnable`: cancellation, retry, method change, recreation, no stale apply. |
| `HomeLaunchNavigationStateInstrumentedTest` | `bootstrapHandoffCoalescesBehindExternalOperationAndBlocksToggle`, `preparationWithoutDownloadUnlocksRecreatedControl`: coalesced handoff, blocked toggle, held final apply, no duplicate retrieval, no-download unlock. |
| `HomeLaunchNavigationStateInstrumentedTest` | `externalWorkerOwnsLeaseThroughFinalApplyAndRecreatedHome`, `externalWorkerApplyFailureReleasesRecreatedHome`: production immediate-worker wrapper with controlled source/model and apply latches; COMPLETE cannot unlock Home; success/failure release; off works afterward. |
| `SourceOperationCoordinatorTest` | `leaseIsNonblockingNonNestedAndStaleCloseCannotReleaseNextOwner`, `leaseIncludesFinalApplyAndReleasesOnFailure`: competing-thread acquisition, non-reentrancy, stale close, observable busy lifetime, exception release. |
| `DefaultListsSubscriberTest` | `preparationRemainsBusyThroughUpdateHandoffAndRunsOnce`, `noDownloadPreparationAndCallbackFailuresReleaseBusyExactlyOnce`: queued handoff, repeated initialization, no-op, preparation exception, callback exception. |

Consent tests enter the validated-result ViewModel boundary; they do not display Android's permission dialog or establish a real VPN. Terminal import states in those tests are fixtures, not actual HTTP failures/stops. Worker tests run production immediate-worker orchestration against test models. Main should rerun real cancellation -> retry -> rotation during consent -> grant after its new build. Process-death/system permission-revocation behavior and visual QA are not claimed by these tests.

Implementation files: `HomeActivity`, `HomeViewModel`, `HomeFragment`, `InitialSourcesViewModel`, `AdAwayApplication`, new `SourceOperationCoordinator`, `SourceUpdateService`, `FilterSetUpdateWorker`, `HostsSourcesViewModel`, `ApplyConfigurationSnackbar`. Test files: `HomeLaunchNavigationStateInstrumentedTest`, new `SourceOperationCoordinatorTest`, `DefaultListsSubscriberTest`, `OnboardingFirstRunContractTest`.

## Findings (Original Review)

The evidence, line references, and hashes below describe the pre-fix snapshot. Current implementation status is above and in `home-patch-bugs.csv`; no finding was runtime-reproduced by this lane.

### HOME-PATCH-001 - P2 - A successful consent result can discard the pending enable request

Status: **source-confirmed control-flow gap; runtime reproduction pending**.

- Changed location: [HomeActivity.java:80](/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway/app/src/main/java/org/adaway/ui/home/HomeActivity.java:80), lines 80-85. Related guard: [HomeViewModel.java:174](/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway/app/src/main/java/org/adaway/ui/home/HomeViewModel.java:174), lines 174-186.
- Evidence: the result callback clears both `vpnEnableRequested` and `vpnConsentPending` before calling `setProtectionEnabled(true)`. That method silently returns if a Home operation or external source import is now busy. Neither the result nor the desired enabled state is retained for completion of that work. An update can begin after the original idle check while the system consent activity is open; `update()` does not exclude consent-pending state.
- Reproduction to execute: with VPN permission absent and no operation active, tap the switch to open consent. Start and hold a background source import in CHECK/DOWNLOAD before granting consent; optionally recreate Home while consent is open. Grant permission, then stop or fail the import so its own successful-apply path cannot mask the dropped request.
- Expected: an accepted enable request either runs once when safe or gives an explicit deferred/failure result. It must not silently disappear after successful authorization.
- Static result: the callback consumes the request, the busy guard rejects enable, and import termination has no continuation to enable protection. This interleaving was not executed here.
- Fix direction: retain an authorized desired-state request independently of the permission-dialog flag and consume it only when the protection operation is accepted. Clear it on explicit cancellation or method change; do not bypass import exclusion.
- Regression: `grantedConsentDuringImportEnablesAfterImportTerminates`, with COMPLETE, STOPPED and failure paths, with/without recreation. Assert one accepted enable, no second prompt, and no stale request after cancellation/method change. A successful import alone is insufficient coverage because its own apply can hide this defect.

### HOME-PATCH-002 - P2 - A protection operation can swallow the one-shot onboarding download

Status: **source-confirmed control-flow gap; runtime reproduction pending**.

- Changed location: [HomeViewModel.java:204](/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway/app/src/main/java/org/adaway/ui/home/HomeViewModel.java:204), lines 204-206, together with the synchronous claim at lines 184-188 and 250-257.
- Supporting chain: [HomeActivity.java:92](/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway/app/src/main/java/org/adaway/ui/home/HomeActivity.java:92), lines 92-96, passes `homeViewModel::update` to [InitialSourcesViewModel.java:28](/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway/app/src/main/java/org/adaway/ui/onboarding/InitialSourcesViewModel.java:28), lines 28-35. The latter marks the initialization attempt started before asynchronously preparing sources and posting that callback. Preparation inserts defaults; it does not itself download them.
- Reproduction to execute: launch Home with `EXTRA_ONBOARDING_COMPLETE`, sources requiring initial download, and an initialized applied-state value. Hold source preparation on disk IO. Tap the new protection switch so `operationPending` is claimed, release preparation, and hold the protection operation until the posted onboarding `update()` callback has run. Then release the protection operation and recreate Home.
- Expected: the initial source retrieval remains scheduled exactly once after the protection operation, without overriding a later explicit off request.
- Static result: `update()` returns because the switch owns `operationPending`; `InitialSourcesViewModel.mStarted` prevents a retry on recreation in the same activity/ViewModel lifetime. The initial download is lost until an independent manual/background update or a later fresh initialization. This interleaving was not executed here.
- Fix direction: preserve/coalesce the bootstrap update until accepted, or keep the protection control unavailable through the preparation-to-download handoff. Preserve the synchronous atomic claim that fixes repeated taps.
- Regression: `firstRunBootstrapSurvivesConcurrentProtectionToggle`, using latches around source preparation and protection apply/revert. Assert retrieval occurs once after release, including recreation and both requested switch states; do not count a later unrelated scheduled update as success.

### HOME-PATCH-003 - P2 - External import completion unlocks the switch before the worker finishes applying

Status: **source-confirmed synchronization gap; runtime reproduction pending**.

- Changed location: [HomeViewModel.java:174](/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway/app/src/main/java/org/adaway/ui/home/HomeViewModel.java:174), lines 174-181. `COMPLETE` is treated as safe, while `operationPending` tracks only operations started by this Home ViewModel.
- Supporting chain: [SourceModel.java:1002](/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway/app/src/main/java/org/adaway/model/source/SourceModel.java:1002), lines 1002-1006, publishes COMPLETE before returning; the no-change path also publishes COMPLETE before returning. [SourceUpdateService.java:222](/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway/app/src/main/java/org/adaway/model/source/SourceUpdateService.java:222), lines 222-226, then calls `adBlockModel.apply()` outside that progress lifetime. The scheduled worker has the same ordering at lines 193-196.
- Reproduction to execute: start an external immediate source update while protection is on. Hold its execution after retrieval publishes COMPLETE but before its final `apply()`. Let Home process COMPLETE, turn protection off, and wait for revert to finish. Release the worker's final apply.
- Expected: the switch stays busy until the complete update/apply operation finishes, or the user's later off request wins.
- Static result: Home can accept and complete off, then the older worker applies protection again. There is no shared exclusion or desired-state ordering across these two callers. The worker behavior predates this patch; the new actionable switch and its import guard expose this interaction. This interleaving was not executed here.
- Fix direction: include final apply in shared operation state/exclusion, or serialize protection intent across Home and external apply callers. Merely extending Home's local atomic flag cannot cover an external worker.
- Regression: `externalUpdateFinalApplyCannotOverrideNewerOff`, with a latch at the retrieval/apply boundary, both changed and no-change imports, and a recreated Home view. Assert either the off control remains disabled until final apply or final state honors the newer off request.

## Consent And Pending-State Assessment

- Normal permission cancellation, retry, rotation during consent, successful grant, and launch failure have explicit paths. Registration in `onCreate`, saved `vpnEnableRequested`, resetting consent-pending state, and checking the current method plus `VpnService.prepare()` on return are consistent with the reported successful rotation exercise. No separate ordinary-rotation defect was found.
- The synchronous atomic operation claim prevents two same-frame taps from scheduling duplicate Home operations. Releasing it on the main executor and observing pending state allows a recreated view to unlock. Keep this improvement when addressing the findings.
- The switch displays actual applied state rather than optimistically retaining its clicked state. It is disabled when applied state is unknown, while consent is pending, and during recognized active import phases. The findings concern work crossing those ownership boundaries, not the ordinary single-operation path.
- The new switch has a 48dp minimum height and constrained placement above Sources in scrollable content. No additional static layout defect was found. This is not a visual, accessibility, translation, or device-size QA pass.

## Catalog Assessment

No actionable regression found in the catalog/preset diff. URLhaus is removed from the built-in catalog, and defaults/balanced/aggressive selections derive from that catalog. The new test checks its absence from all four collections. The other default selections remain present.

There is no database deletion or migration in this patch, so an existing user's persisted URLhaus subscription is not silently removed. A legacy catalog-only URL can now classify as CUSTOM through the fallback lookup; that does not delete its subscription. Continuing failures for a user who already has the retired source are not represented as fixed by this change. The reported HTTP 410 was supplied by main and was not independently rechecked in this review.

## Evidence And Coverage

- **User-reported executed proof, not independently rerun or logs re-audited here:** 624 JVM tests with 3 skipped; lint with 33 warnings; 11 connected tests green; real emulator cancellation -> retry -> rotation during consent -> grant -> `tun0` active.
- **Independently inspected static evidence:** the named diffs and supporting call chains described above. The new Home instrumentation exercises a ROOT-mode test model, normal enable/disable with recreation, minimum switch height/order, and repeated taps with a held apply. Those are useful checks but do not exercise a real consent result competing with an import, the bootstrap callback handoff, or an external worker's final apply.
- **Not executed here:** all three proposed reproductions/regressions; any UI/visual QA; any JVM, lint, connected, build or emulator command. None of these findings is marked runtime-verified.

## Reviewed Diff Snapshot

Hashes were recomputed after tracing the findings and matched the initially inspected contents. Paths below are relative to the repository root. These are snapshot identifiers, not test evidence.

| File | SHA256 | Assessment |
| --- | --- | --- |
| `app/src/main/java/org/adaway/ui/home/HomeActivity.java` | `93d3eaebc5e980055abbdf1403eec4f1ae4b4f6e98817618229525413a31ccd1` | Consent lifecycle wired; HOME-PATCH-001; bootstrap caller for HOME-PATCH-002. |
| `app/src/main/java/org/adaway/ui/home/HomeFragment.java` | `0cc7cd237590e810a5ff1b31f19a63424081ea2ea97b29da48ec14ae2f3f5c71` | Switch rendering/click/pending observers traced; no separate defect beyond shared operation findings. |
| `app/src/main/java/org/adaway/ui/home/HomeViewModel.java` | `8443660bc74591d53b37292f19c98d7e3e956adf20eea464ff0d3a9e7fc20579` | Atomic tap exclusion improves correctness; HOME-PATCH-001/002/003 at asynchronous handoffs. |
| `app/src/main/res/layout/home_content.xml` | `0d78223ff69b6cd72d507a1d9f780eb1168b8e661084f363622f7c97578985e3` | Switch constraints and minimum touch height inspected; visual QA not performed. |
| `app/src/main/res/values/strings_home.xml` | `c43f5082714518dd6c86deda5e9a681b1423236c42344ffe255ef6b4042e83d7` | Two added strings match the new control/cancel path; no independent defect identified. |
| `app/src/androidTest/java/org/adaway/ui/home/HomeLaunchNavigationStateInstrumentedTest.java` | `5b9b45f4027474e1e5c6a170491f64fd3feb63ba2f52d90bfb41ba1690e6c4f2` | New ROOT-model tests cover toggle/recreation and repeated taps, not the three interleavings. |
| `app/src/main/java/org/adaway/model/source/FilterListCatalog.java` | `cef49788882d6beece11de1214dab549d4785b33e236e83f13ead67e8bd5e422` | Catalog-only removal preserves persisted subscriptions; no actionable regression found. |
| `app/src/test/java/org/adaway/model/source/FilterListCatalogPresetTest.java` | `7b7edd6fb57fe63682561f789b2166260837ca601d2e9c13c802adb8b3e14398` | Removal asserted across catalog/defaults/balanced/aggressive; replacement default assertion is consistent. |

The original review changed only this report. The approved follow-up changed the implementation/test files listed above plus this report and `home-patch-bugs.csv`. Earlier sources audit artifacts and the central bug ledger were not edited.
