# Rules Fixes

## Ownership and Freeze

- Approved scope: RR-001, RR-002, RR-005, RR-006, RR-007 only.
- RR-003/RR-004 and every Domain Checker Java file remain owned by main.
- Integration freeze received before any production or test edit. As of this planning entry, no production/test file has been written by this lane. Only this new sidecar is being written during the freeze.
- Explicit GO was received after main saved immutable phase-one APKs. All production/test writes occurred after GO. Main subsequently committed phase one as `f7dbbb5e7` without this lane's unstaged implementation.
- Implementation is now **ready/frozen for main's next integrated build**. No further production/test writes are planned. No Gradle, adb, emulator, central ledger edits, staging, commit, or push by this lane.

## Confirmed Contracts

- `HostListItemDao.loadList` returns a Room PagingSource over hosts_lists and hosts_meta. Mutations invalidate its tables; changing the user filter is unnecessary to refresh the rows.
- `getHostId` is scoped to source_id=1; upserting an exception does not overwrite a downloaded row.
- `HostEntryDao.resolveEntry` and `resolveRootEntry` check an enabled exact redirect BEFORE checking active allow rules. An allow entry alone cannot neutralize an enabled redirect.
- Redirect disable will therefore atomically add/update one exact user ALLOWED entry and disable only the selected downloaded row. Other redirect rows remain untouched; the dialog must state that they can still apply. Reimport can restore a downloaded redirect; this lane does not change global precedence or source import policy.
- `HostListItem.setHost` lowercases/reverses labels but does not collapse repeated wildcards. Reject overlength raw patterns consistently rather than inventing a normalization contract.
- Existing AppExecutors.diskIO is a single-thread executor. New tests can hold its queue and main delivery without changing global executor ownership.

## Implemented Changes

1. `setItemEnabled(item, requestedState)` replaces inversion. Mutations copy complete entities instead of changing Paging snapshots. The adapter restores its bound state before confirmation, so No, Back, outside touch, and dismissal need no synthetic refresh. Diff identity now uses row ID, distinguishing equal hosts from different sources.
2. Block/allow moves remain supported; downloaded redirects create exact ALLOWED entries with no redirection value. User redirects have no meaningful Move and hide that menu item. Override, selected-source disable, and source statistics share one Room transaction. A redirect-specific confirmation warns that other redirects can still apply.
3. Shared query LiveData updates retained editors. Binding occurs after hierarchy restoration, ignores model-originated changes, and disables independent editor text saving. The three Paging streams use the existing library's ViewModel-scoped `cachedIn` overload so a recreated adapter can consume the retained generation without forcing a filter reset.
4. Removed all mutation-triggered filter replacement. Room invalidation refreshes rows; search/source commands remain user-owned. Identical queries do not regenerate the filter.
5. Wildcard validation rejects null, empty, or raw input over 253 characters before wildcard removal. Add and edit share write-boundary validation and return acceptance explicitly. Allowed add/edit show a length error and disable submission for overlength patterns; existing invalid edit values are checked immediately.

## Regression Intent

- Actual downloaded redirect checkbox: confirmation inserts exact ALLOWED, disables selected redirect, and live DAO resolution becomes ALLOWED with an isolated fixture.
- Duplicate-redirect control: a second source's redirect remains enabled and wins; no broad cross-source mutation.
- Context Move availability: user redirect hidden; supported block/allow and downloaded-redirect operations remain meaningful.
- Actual checkbox and row cancellation: No, Back, and outside touch leave DAO/visual state unchanged; later confirmed off/on requests agree with the requested state.
- Repeated explicit same-state calls are idempotent and do not mutate the Paging snapshot object.
- Retained adjacent tabs synchronize query text and matching rows; view/activity recreation cannot restore an obsolete tab-local query over the ViewModel.
- Deterministic mutation/search interleaving: hold main delivery until a disk mutation has finished, then set a newer query/source filter before draining queued delivery; latest selection survives and Room refresh still publishes data.
- Long wildcard at add/edit boundaries: 255-character input rejected with visible error and disabled button; short controls and valid multiline entries remain accepted; no partial accepted batch.
- Standalone host tests for null/empty and wildcard length boundaries, including the previously accepted repeated-star case. Android tests and compilation are main-owned unless a standalone isolated compiler check can use existing artifacts without invoking Gradle.

## Exact New Cases

`app/src/androidTest/java/org/adaway/ui/lists/ListsRuleFixesInstrumentedTest.java`: 13 authored and Android API-image compiled cases, **none executed here**:

1. `downloadedRedirectCheckboxCreatesExactAllowAndDisablesOnlySelectedRow`: real checkbox/Yes, exact user entry, disabled selected row, DAO ALLOWED result, explicit re-enable and REDIRECTED result; downloaded Move is visible because it is supported.
2. `redirectOverrideDoesNotDisableAnotherSourcesRedirect`: second redirect unchanged, original Paging snapshot unchanged, existing redirect-first runtime precedence retained.
3. `contextMoveIsHiddenForUserRedirectAndVisibleForUserBlock`: actual contextual menu visibility plus successful block-to-allow command.
4. `noButtonRestoresDownloadedCheckboxBeforeNextConfirmedChange`: actual switch/No/confirmed off/on and persisted row checks.
5. `backRestoresDownloadedCheckboxBeforeNextConfirmedChange`: same sequence with a real Back event.
6. `outsideTouchRestoresDownloadedCheckboxBeforeNextConfirmedChange`: injected outside-screen-coordinate touch, dismissal assertion, and off/on checks.
7. `rowClickCancellationAlsoKeepsAuthoritativeState`: adjacent row-click path with Back cancellation.
8. `repeatedRequestedStateIsIdempotentAndDoesNotMutatePagingSnapshot`: repeated false writes and explicit true using the original snapshot.
9. `retainedTabsShareQueryAndKeepNewestQueryAfterRecreation`: retained Blocked/Allowed editors, alpha/beta rows, actual activity recreation, clear-search control.
10. `completedMutationCannotReplayAnOlderFilterAndRoomStillRefreshesRows`: hold main while disk mutation completes; publish newer query/source selection before posted delivery drains; check scope and subsequent same-filter Room refresh.
11. `allowAddRejectsLongLineVisiblyWithoutSubmittingTheOtherLine`: visible error/disabled Add, neither line written, corrected two-line batch persists.
12. `allowEditRejectsLongInputAndKeepsExistingRuleUntilValidSave`: visible error/disabled Save, original retained, valid wildcard rename succeeds.
13. `writeBoundaryRejectsSameOverlengthPatternForAddAndEdit`: both reject, no original-snapshot mutation, no invalid row.

The suite uses an isolated in-memory Room database and restores the application database singleton. UI fixture cleanup drains diskIO before closing the database. API 29+ is required for WindowInspector; main's API34 fixture is suitable. No root write, VPN enablement, network request, or real-user rule deletion is required.

`app/src/test/java/org/adaway/util/RegexUtilsRuleLengthTest.java`: four new host cases, **all passed**:

1. `wildcardRejectsNullEmptyAndOverlengthRawInput`
2. `wildcardAcceptsRawLengthBoundaryAndNormalPatterns`
3. `wildcardStillRejectsMalformedShortPatterns`
4. `exactHostnameKeepsItsOwnLengthAndWildcardRules`

## Host Checks

- OpenJDK21 `javac --release 17`, cached JUnit4.13.2/Hamcrest1.3/Guava33.6.0-android and Timber dependencies; no Gradle.
- JUnitCore ran `RegexUtilsRuleLengthTest` (4), existing `ListsSearchBarTest` (10), and existing `ListsUiStateTest` (7): **OK (21 tests), zero failures** on both runs. Final run: 0.037 seconds.
- Final generated host classes: `/var/folders/cj/j956f9v920b8wk3wvd8ms2nh0000gn/T/adaway-rules-host-final-20261125-25173-1u8ys8c`.
- This is green-on-current-source evidence. No baseline Android red/green or connected-test pass is claimed; existing assertions were not weakened or edited.

## Compile Check

- Final isolated compile passed, exit 0, using OpenJDK21 `javac -source 21 -target 21 -proc:none -implicit:none`, cached Android Java system image, API36 `android.jar`, cached dependencies, and main's preexisting compiled app/test classes.
- Compiled every list Java file, changed RegexUtils, and the complete new instrumentation class to `/tmp/adaway-rules-compile.DXWCWu/classes`. Only an existing deprecated-API note was emitted.
- Because main's existing R artifacts predate the two new strings, this isolated check used a temporary compile-only R symbol overlay. Every referenced symbol was checked against the existing symbol list or the parsed real `strings_lists.xml`. No overlay or compiled output was placed in app/build or the repository.
- Full Android resource merging, annotation processing, app/test APK construction, lint, and runtime execution are **main-owned and pending**. The symbol-overlay compile is not a substitute for those integration gates.

## Limits and Handoff

- Seven production/resource files changed and two new test files added. No Domain Checker file, DAO, SourceModel, existing test, central audit ledger, or Git index was edited by this lane.
- `rules-fixes-files.csv` records the nine implementation/test hashes. `rules-fixes-bugs.csv` preserves exactly RR-001/002/005/006/007 in the central 11-column schema with status `implemented-awaiting-android`; it must not mark them runtime verified.
- Redirect-first precedence is intentionally unchanged. An exact allow entry does not suppress another enabled redirect, and source reimport can restore a disabled downloaded redirect. No broad source disabling or persisted import override policy was added.
- Re-enabling a downloaded block row does not delete a separately stored user allow entry; this patch fixes requested checkbox/row state, not global override-removal policy.
- Existing mutation exception reporting and process-death search persistence are outside this approved slice. Query restoration coverage is for retained ViewModel/activity/view recreation.
- The pre-GO planning sidecar was included in main's phase-one staging/commit. Its final working-tree update and all implementation/test changes remain unstaged. This lane did not stage, unstage, commit, or modify main's index.

## Status

- [x] DAO/runtime precedence and current list contracts read.
- [x] Initial freeze respected; no production/test writes before GO.
- [x] Implementation and regression design prepared.
- [x] Explicit GO received.
- [x] Scoped implementation and new focused tests.
- [x] Standalone host validation and source/compile review.
- [x] Exact case inventory and ready/frozen handoff to main.
- [x] Final validation passed: nine frozen hashes; five preserved IDs; exact CSV schemas; 13 Android case declarations; XML parsing and whitespace; no staged lane changes.

Main owns the remaining integrated build and all Android execution. No more production/test writes are planned.

## Phase-Two Outside-Touch Follow-Up

This section supersedes the original pending-runtime statements for the cases main executed. Main's `/tmp/adaway-audit-phase-two-focused.log` contains 13 terminal rules results: 12 passed, and only `outsideTouchRestoresDownloadedCheckboxBeforeNextConfirmedChange` failed. Its failure at log lines 550-578 and 1667-1673 is `Timed out: confirmation dismissed`; it never reached the post-cancel state assertions. No/Back/row cancellation, requested-state idempotence, redirects, query/recreation, invalidation, and validation cases all passed in main's run.

**Diagnosis: fixture error, not evidence of a product cancellation defect.** The injected point was `bounds.top - 2`. Android14's [Window outside-boundary predicate](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-14.0.0_r1/core/java/android/view/Window.java) requires the release point to exceed the configured window-touch tolerance, with a strict comparison. [ViewConfiguration](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-14.0.0_r1/core/java/android/view/ViewConfiguration.java) scales the 16dp base tolerance by screen configuration. Thus a two-pixel near-edge release is not an outside cancellation. Cached AppCompat1.7.0 bytecode also confirms AlertParams defaults to cancelable and Builder.create enables outside cancellation when cancelable; the product does not override those defaults.

Ranked hypotheses were: near-edge tolerance, bounds/animation drift, or disabled outside cancellation. The framework predicate and AppCompat bytecode support the first and reject the need to change product cancelability. No exact failed-device slop value was captured, and no device rerun was performed by this lane.

**Minimal change:** only `ListsRuleFixesInstrumentedTest.java` changed relative to the phase-two index. Read `ViewConfiguration.get(dialogContext).getScaledWindowTouchSlop()` on main and inject at one pixel beyond it. Strengthen the existing available-space guard to require that clearance above the dialog. Real DOWN/UP injection remains; there is no direct `cancel()`, dismiss fallback, retry, added sleep, or timeout increase.

**Proof retained:** a source comparison against the staged test confirms the complete block from `await("confirmation dismissed", ...)` through DAO enabled/absence checks, checked-row checks, subsequent confirmed disable, and re-enable is byte-for-byte unchanged. All 13 test declarations remain. This keeps outside-dismissal and checkbox/database agreement as required assertions, not optional or skipped checks.

**Checks performed:**

- Replayed the framework's strict above-window predicate for slop 12/16/24/32/48/64px: old offset2 was inside tolerance for all six; new slop+1 was outside for all six. This is arithmetic/source evidence, not Android execution.
- Isolated OpenJDK21/Android API36-image javac passed with the real phase-two app/test classes and R.jar, no resource-symbol overlay. Output: `/tmp/adaway-rules-outside-compile-20261125-42939-1i1b2va`.
- Source-diff guard passed: all post-cancellation proof assertions unchanged; actual touch injection retained; 13 cases retained.
- Production/resource/host-test files have no unstaged differences from main's phase-two index. No Gradle, adb, emulator, staging, or commit was performed. The staged test blob remains `e949dbaaa631d23190275dafd0baa110f89be2e7`; the fixture delta is unstaged.
- Updated test SHA256 in `rules-fixes-files.csv` and RR-002 evidence in `rules-fixes-bugs.csv`. Central ledgers and previously staged changes remain untouched.

**Ready/frozen again.** Main should rerun `org.adaway.ui.lists.ListsRuleFixesInstrumentedTest#outsideTouchRestoresDownloadedCheckboxBeforeNextConfirmedChange`. Corrected-device success is pending; do not close outside-touch runtime coverage from this compile/source check alone.

## Phase-Three Back Readiness Follow-Up (2026-11-25)

**Latest bounded scope:** only `app/src/androidTest/java/org/adaway/ui/lists/ListsRuleFixesInstrumentedTest.java` and these three rules-fixes sidecars. No production, resources, other tests, central ledger, or index edits. Native-printer review remains partial/pending as previously reported.

**Observed runtime evidence supplied by main:** `/tmp/adaway-audit-phase-three-api34.log` contains 94 passing and 2 failing cases out of 96. The rules class has 12 passing and 1 failing case. The earlier outside-touch correction passes (lines185-198). `rowClickCancellationAlsoKeepsAuthoritativeState` fails at the confirmation-dismissal wait, old test line407 (log lines297-325); the checkbox Back case passes immediately afterward (lines326-339). The unrelated HomeFlow failure is outside this lane. No new device execution was performed here.

**Source-confirmed fixture gap, not a confirmed product regression:** `awaitDialog()` only finds a shown global window containing the positive button. It does not establish window input focus or layout completion before Back is injected. Row and checkbox actions reach the same default-cancelable confirmation in `AbstractListFragment`; there is no row-specific Back handler. Android14 `Instrumentation.sendKeyDownUpSync()` targets the currently focused window, and `sendKeySync()` injects through InputManager without checking the returned success value or synchronizing window transactions. A shown dialog is not a focus/readiness barrier. Early or unsuccessful Back delivery is therefore the leading fixture explanation, but the failed run did not record focus or injection outcome: its exact event destination is not proven.

Primary framework references: [Instrumentation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-14.0.0_r1/core/java/android/app/Instrumentation.java), [UiAutomation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-14.0.0_r1/core/java/android/app/UiAutomation.java), [UiAutomationConnection](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-14.0.0_r1/core/java/android/app/UiAutomationConnection.java), and [View](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-14.0.0_r1/core/java/android/view/View.java). UiAutomation's two-argument injection waits for animations; its service synchronizes input transactions before key DOWN and after key UP. `View.isShown()` only checks visibility along the parent chain.

**Minimal fixture correction:** the two Back cancellation cases now share `pressBackOnDialog()` (current lines643-659). It waits on the main thread for a non-null, attached, laid-out, focused confirmation, then injects exactly one real Back DOWN/UP pair through UiAutomation with explicit keyboard source, shared downTime, and an assertion on each injection result. This adds a deterministic readiness barrier and reports injection failure at its source. There is no fallback dismissal, direct dialog cancellation, retry, arbitrary sleep, longer timeout, or product change.

**Proof retained:** the complete post-cancel block is byte-identical to the staged version: confirmation disappears; source row remains enabled; no user exception exists; checkbox remains checked; the next confirmed change disables both persisted row and checkbox; the following click re-enables both. The outside-touch slop correction and No-button path are unchanged. All 13 test methods, annotations, and existing timeout values remain unchanged. Only Back setup changes, affecting `rowClickCancellationAlsoKeepsAuthoritativeState` and `backRestoresDownloadedCheckboxBeforeNextConfirmedChange`.

**Checks performed:**

- Standalone OpenJDK21 javac passed against the cached Android API36 system image, real app/test classes, and real R.jar files. Output: `/var/folders/cj/j956f9v920b8wk3wvd8ms2nh0000gn/T/adaway-rules-back-compile-20261125-20261125-86949-187l0jt`. This is a compile check, not Gradle or Android runtime evidence.
- Source guards passed: exact post-cancel proof block and outside-touch block unchanged; 13-case inventory unchanged; only the Back import/call/helper differ from the index.
- Current test SHA256: `6675682dae6982016a80b76e453f2e6279dabd42cab4fbb2700143e4bb9436c6`. Staged test blob remains `924edc33af5d34ae86a18bc889ed54f9fbd124de`; the new correction is unstaged.
- No Gradle, adb, emulator, build gate, staging, or commit was run by this lane. No corrected-device success is claimed.

**Ready/frozen for main:** rerun the two Back cases on API34/API30, with the full 13-case rules class retaining No/outside-touch coverage. A corrected row-case pass is still required before considering this failure resolved at runtime. Sidecars record the unchanged cancellation proof and the source/runtime distinction; central statuses remain untouched.
