# Discover and Sources Static Review

Date: 2026-11-25. Branch: `codex/full-codebase-audit`.
Baseline and inspected HEAD: `d51eb7aea06871c2c6ad2bbedb9ddbaaaca48d77`.
Repository: `/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway`.

## Scope and Evidence

- Individually read all 20 Java files in `app/src/main/java/org/adaway/ui/discover/` and `ui/hosts/`, plus all 12 layouts and the menu they inflate. The 33 exact paths, SHA256 values, specific assessments, and finding IDs are in `sources-files.csv`.
- `sources-workflows.csv` is the actionable control/chain inventory. Every row is **pending**. Source tracing, existing test names, and October results are not current executed visual QA.
- Read `CLAUDE.md`, `tasks/lessons.md`, the October Discover/app reports, relevant consolidated October findings, and the current audit README. Inspected targeted test methods and cross-boundary callers to validate findings. Partial supporting reads are not claimed as whole-file reviews in the file ledger.
- No product edits, Gradle, adb, emulator, builds, commits, branches, push, or pipeline operations. Only this report and the three requested CSV sidecars were written.
- Main owns Home navigation implementation, emulator execution, and the shared bug/file ledgers. Source editor, database, SourceModel, notification permission, and Home internals remain handoff boundaries; this lane inventories entry/return contracts without claiming complete review of those packages.
- All reproductions below are proposed deterministic replay instructions, **not executed failures**. Suggested new test method names are proposals, not tests already added or run.

## Findings

### AUD-SRC-001 - P2 - Source schedule picker can open against a destroyed activity

Evidence: `app/src/main/java/org/adaway/ui/hosts/SchedulesActivity.java:211-227`.
The disk task captures the activity and unconditionally posts an AlertDialog with that activity as its window owner. Neither the query completion nor dialog creation checks finishing/destroyed/lifecycle state. Back/Up or rotation while diskIO is queued can deliver the callback after the old activity's window token is invalid, producing a BadTokenException; before destruction it can also open a dialog over a screen being left.

Repro: block diskIO with a latch, tap the per-source Edit control with at least one source, finish or recreate the activity, then release the latch. Expected: no old-window dialog and no crash. Source-predicted actual: old activity calls `show()`.

Fix direction: retain application context for work; deliver results only to the current live screen, preferably via lifecycle-owned state. Also guard the summary callback at lines 105-120. Regression: proposed `SchedulesLifecycleInstrumentedTest.sourcePickerCompletionAfterFinishOrRecreationDoesNotShowOldDialog`; assert old activity destroyed and no uncaught exception. Workflows SCH-013, SCH-016.

### AUD-SRC-002 - P2 - App update cancels independent set/source schedules when global updates are off

Evidence: `app/src/main/java/org/adaway/ui/hosts/FilterSetUpdateService.java:44-50`; `FilterSetUpdateWorker.java:62-100,116-118`.
The same periodic worker services global, named-set, and per-source schedules, but `syncPreferences()` cancels it solely because the global schedule is disabled. Read-only caller trace: `app/src/main/java/org/adaway/broadcast/UpdateReceiver.java:31` calls this on package replacement. The application startup snippet only enables this worker when global scheduling is enabled, so it does not repair this off-global case.

Repro: disable the global schedule; enable a daily per-source schedule or a named-set schedule; verify periodic work exists; deliver the package-replaced reconciliation. Expected: independent schedules remain scheduled. Actual source path: unique work is cancelled while the schedule manager still reports scheduled items.

Fix direction: make worker lifetime depend on any enabled schedule, or keep a harmless periodic dispatcher alive independently of the global setting. Regression: extend `FilterSetUpdateServiceWorkManagerTest` with `syncPreferencesKeepsSourceAndSetSchedulesWhenGlobalIsOff` and add package-replacement integration coverage. Workflows SCH-018, SCH-019. This is not a reboot-cancellation claim: the demonstrated caller is app-update reconciliation.

### AUD-SRC-003 - P2 - Late directory refresh overwrites newer subscription truth

Evidence: `app/src/main/java/org/adaway/ui/discover/DiscoverFilterListsFragment.java:323-329,414-427`; live observer at 169-195.
`load()` snapshots database subscriptions before network requests, then overwrites both subscription caches with that old snapshot when the network completes. Cached rows remain actionable during the refresh. An intervening subscribe/unsubscribe can be correctly delivered by Room and then undone visually by the late snapshot; no subsequent database change is required to correct it.

Repro: seed expired valid directory cache and a subscribed list; hold the network refresh; confirm unsubscribe from the cached row; wait for DAO removal and observer delivery; release refresh. Expected: switch remains off and Subscribed-only excludes it. Source-predicted actual: old URL returns to `existingUrls`, making the removed row appear subscribed. Repeat with a newly subscribed row.

Fix direction: keep subscription truth solely driven by current DAO observations, or version/re-read the snapshot before publication. Regression: extend `DiscoverCacheRecoveryInstrumentedTest` with `lateRefreshPreservesSubscriptionChangesMadeWhileCachedRowsAreVisible`. Workflows DISC-008, DISC-009.

### AUD-SRC-004 - P2 - Background refresh resets the language filter

Evidence: `app/src/main/java/org/adaway/ui/discover/DiscoverFilterListsFragment.java:425-427,483-513`.
Both cached and network results replace the spinner adapter. The new adapter is never restored to `selectedLanguageId`; selection of position zero sets that ID to zero and reruns filtering.

Repro: hold refresh of expired valid cache; select a non-default language; release identical languages/directory data. Expected: same language and same filtered scope. Source-predicted actual: All languages is selected and unrelated rows return. Also test reordered language metadata so position-based restoration cannot silently choose another language.

Fix direction: preserve the semantic language ID and select its new position before accepting callbacks. Regression: proposed `DiscoverFilterListsFiltersInstrumentedTest.refreshRetainsLanguageIdAcrossMetadataReordering`. Workflow DISC-017.

### AUD-SRC-005 - P2 - Recreation discards checked lists and the tag filter

Evidence: `app/src/main/java/org/adaway/ui/discover/DiscoverFilterListsFragment.java:92-100,129-150,448-479,592-609`.
Selected list IDs and selected tag ID exist only in instance fields. There is no saved-state or retained-state restoration. Dynamic chips are rebuilt from the default tag state. Restoring a RecyclerView view hierarchy does not restore this adapter's selection set; asynchronous initial empty filtering would also prune prematurely restored IDs unless ordering is handled.

Repro: select a tag and check several rows, then recreate/rotate. Expected: same tag, checked IDs, and selected actions (pruned only against an authoritative loaded result). Source-predicted actual: selection is empty and tag becomes All. WorkManager progress re-observation is separate and does not restore selection.

Fix direction: persist semantic filter/selection state and defer pruning until a directory snapshot is loaded. Regression: proposed `DiscoverFilterListsFiltersInstrumentedTest.recreationPreservesCheckedIdsAndTagScope`; also cover process-state recreation, not only same-instance view recreation. Workflow DISC-021.

### AUD-SRC-006 - P2 - Profile status stays stale during in-place directory changes

Evidence: `app/src/main/java/org/adaway/ui/discover/DiscoverFragment.java:69-75,134-136,154-170`.
The parent updates profile status only on initial view/resume and its own preset completion. Child subscription changes and bulk-worker database emissions never refresh the parent label. The child has its own DAO observer, which does not notify the parent.

Repro: seed a saved exact profile whose URL corresponds to a directory row; remain on Discover and unsubscribe that row. Expected: status becomes partial/custom as appropriate without navigation. Source-predicted actual: it remains exact until a resume or preset action. Reverse test: add a non-profile source and expect extended.

Fix direction: observe enabled-source state with the parent's view lifecycle. Regression: extend `DiscoverProfileStatusInstrumentedTest` with `profileStatusUpdatesAfterChildSubscriptionWithoutLeavingDiscover`. Existing `profileStatusReflectsExactExtendedPartialAndCustomState` explicitly navigates away/back after each mutation; it does not cover this case. Workflow DISC-005.

### AUD-SRC-007 - P2 - Cancelling a new current-selection schedule still saves and activates a profile

Evidence: `app/src/main/java/org/adaway/ui/hosts/HostsSourcesFragment.java:350-362,534-540,563-595`.
Choosing Current selection immediately calls `saveFilterSet()`, which writes a named set and changes active profile, before the Off/Daily/Weekly or time/day choice. Cancelling any subsequent step does not roll that back.

Repro: record current active profile and saved names; choose Schedule filter set > Current selection; cancel at mode, weekday, or time picker. Expected: no new profile or active identity change when the unfinished scheduling chain is cancelled. Actual source path: Scheduled selection (and numbered duplicates on repeats) remains saved and active.

Fix direction: retain a pending snapshot until schedule confirmation; commit profile and schedule together without changing active identity merely to configure scheduling. Regression: proposed `FilterSetSaveApplyInstrumentedTest.cancelCurrentSelectionSchedulingLeavesProfilesAndIdentityUnchanged`. Workflows SRC-042, SRC-043.

### AUD-SRC-008 - P2 - Bulk history truncation removes recovery actions for later failures

Evidence: `app/src/main/java/org/adaway/ui/hosts/FilterListsSubscribeAllWorker.java:66,669-681`; `DiscoverFilterListsFragment.java:1227-1240,1269-1275,1309-1320`.
The worker stores only the first 500 outcomes, including successes, but counts every skipped outcome. Retry and unsupported-review IDs are parsed only from that truncated history. A failure after 500 successful outcomes increments the summary/review count but cannot appear in Retry or unsupported review.

Repro: deterministic 501-list run with cached URLs for the first 500 successes and a final safe list with no URL; finish and inspect review controls. Expected: final skipped ID can be retried. Source-predicted actual: Review is offered but Retry has no IDs. Repeat with a final unsupported item; its review choice is lost.

Fix direction: persist recovery IDs separately from a bounded display history, or retain all actionable failures with explicit paging/truncation semantics. Regression: extend `FilterListsSubscribeAllWorkerRoomTest` and `FilterListsBulkUiInstrumentedTest` with `lateSkippedOutcomeRemainsActionableBeyondHistoryLimit`. Workflows DISC-053, DISC-054.

### AUD-SRC-009 - P3 - Schedule toolbar has a back listener but no navigation icon

Evidence: `app/src/main/res/layout/schedules_activity.xml:7-12`; `app/src/main/java/org/adaway/ui/hosts/SchedulesActivity.java:54-55`.
The toolbar sets only a title; Java installs a navigation listener but never sets an icon or makes this toolbar the support action bar. Its intended in-app Up target is absent from the explicit toolbar configuration.

Repro: open Manage schedules and attempt to tap its toolbar Back/Up affordance. Expected: visible, labelled navigation action calls finish. Source-predicted actual: title-only toolbar; system Back is the available escape. Device/theme inspection remains pending before visual closure.

Fix direction: set the navigation icon and accessible description explicitly. Regression: proposed `SchedulesLifecycleInstrumentedTest.toolbarUpIsVisibleLabelledAndFinishes`. Workflow SCH-002.

### AUD-SRC-010 - P2 - Non-scrolling Discover controls can consume all list space

Evidence: `app/src/main/res/layout/fragment_discover.xml:8-88`; `fragment_discover_filterlists.xml:17-207,250-256`.
This is a **source-supported layout risk, pending visual reproduction**, not an executed screenshot defect. Quick-start/status content and the entire search/tag/language/switch/bulk/history stack are fixed siblings above a weight-1 list; only tags scroll horizontally. The last-run actions add three further vertical buttons. There is no enclosing vertical scrolling/collapsing route to recover list height when these controls exceed available height.

Repro: 640x360dp landscape and a short split window; seed a run exposing Review, Retry, and Unsupported; repeat at 1.3x/2.0x font scale and with keyboard open. Expected: rows, Cancel, retry/error text, and all controls remain reachable. Inspect actual bounds, clipping, scrolling, and physical tap reachability, not adapter counts alone.

Fix direction after reproduction: move controls into a shared scrolling/collapsing surface or provide an adaptive compact filter/actions arrangement. Preserve the October 48dp touch-target fix. Regression: extend `UxDeviceMatrixTest` with the history-actions and keyboard states and screenshot/bounds assertions. Workflow DISC-060.

## Latent and Secondary Notes

- `DiscoverCatalogFragment` has no production construction/reference outside its own declaration in the inspected main-source search. Discover embeds only `DiscoverFilterListsFragment`; Sources' Browse catalog also routes to Discover. Catalog actions are inventoried as legacy/direct-host fixture paths, not presented as reachable current app UI.
- AUD-SRC-L01 (P3, latent): catalog deselecting one item after Select all calls `catalogSelectAllSwitch.setChecked(false)` at lines 454-455; its listener at 84-98 clears the entire selection. Filtering while Select all is on does the same. Individually chosen hidden entries, conversely, are not pruned by `filterEntries()`. Proposed direct-host regression: select all, deselect one, assert N-1; select one, hide it, verify explicit hidden-selection policy before Add. Do not promote this as an active-route regression without demonstrating an entry point.
- Catalog Custom chip only checks itself (line 113), without clearing `selectedPresetProfile`; existing URLs are loaded only once, not refreshed after returning from SourceEdit. These are additional latent state risks. Catalog add hides its button but keeps selection until async completion; failure has no local catch. Badge recycling resets background but not the risky-row white text color.
- `HostsSourcesAdapter` is also unreferenced by production callers; the active Sources fragment uses `CategorizedSourcesAdapter`. Its legacy checkbox, row editor, date/count/error rendering are inventoried separately.
- `HostsSourcesActivity` is manifest-declared and a parent for source/schedule activities. It unconditionally replaces its fragment on recreation (lines 24-28), unlike the tab wrapper's restored-child lookup. Keep dedicated parent-stack/recreation replay pending; do not assume it is the primary tab implementation.
- Other pending probes: category expansion/scroll restoration; stale view references retained by HostsSourcesFragment; repeated source switches while requests are in flight; unhandled DAO exceptions in fragment mutation tasks; bulk FAILED/no-output presentation; source schedule Edit with no pickable sources silently returns; English-only outcome/provenance text; schedule time display ignores 12-hour preference. These are recorded as assessment limits/workflows rather than inflated into independently reproduced bugs.

## October Non-Duplication

- OCT-DISC-01: fresh corrupt cache recovery is present at DiscoverFilterListsFragment 344-391. Keep corrupt lists/syntaxes/tags/languages and offline Retry checks; no new cache-TTL finding.
- OCT-DISC-02: single/catalog/bulk removals call `syncHostEntries()` and offer local-only Apply configuration. Keep VPN cache/materialized-rule/root apply regressions; do not re-report DAO-only deletion.
- OCT-DISC-03: all four directory bulk action buttons now specify 48dp minimum height. The short-height risk above is distinct; do not shrink targets as its fix.
- October SourceEdit duplicate/empty-label/URL-placeholder/import/runtime fixes are outside this lane and not duplicated.
- Preserve intentional unsupported single-switch subscription, explicit checked-row bulk scope, unsupported-only manual review, responsive no-op selected actions, and cached-row bulk availability documented in lessons. They are not defects.
- Existing source update/apply failure/retry/recreation tests and the October teardown fix remain regression references only. No historical pass count is reused as current QA.

## Handoff and Verification

- File ledger: 33 scope files, each completely read and hashed. Test/caller snippets are supporting evidence only.
- Workflow ledger: 145 rows, all pending; test references prefixed `existing:` indicate located candidate coverage, not execution or complete visual proof; `proposed:` identifies missing coverage.
- Mergeable bug ledger: `sources-bugs.csv` uses the central schema and preserves the report IDs: eight source-confirmed findings plus three needs-reproduction entries (toolbar visibility, short-height layout, and latent catalog selection). None is reproduced, fixed, or verified in this lane.
- Re-run the listed focused instrumentation with deterministic cache/network/disk fixtures, then physical control interaction and screenshots. Use bounded lifecycle waits where the existing October lessons warn about global-idle starvation.
- Give first replay priority to AUD-SRC-001/002/003, then state restoration/profile truth, cancellation, and history overflow. Main owns deciding changes and running the device/build gates.
- Audit-artifact checks only: validate CSV column counts/unique IDs/all-pending status, exact 33-file scope and SHA256 consistency, and scoped whitespace checks. These checks do not exercise AdAway UI.
