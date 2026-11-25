# Discover State Fix Handoff

Date: 2026-11-25. Branch: `codex/full-codebase-audit`.
Workspace: `/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway`.
Scope: AUD-SRC-003, AUD-SRC-004, AUD-SRC-005, AUD-SRC-006 only.
Status for all four: **implemented-awaiting-test**.

## Stable-Ready Boundary

Product and test files were declared stable-ready before documentation work. Main owns the
consolidated JVM, lint, build, and connected-device run. No Gradle or adb commands, tests, app
launches, commits, pushes, or library/version changes were performed in this lane. Initial
instrumentation replays were authored before their corresponding fixes; the removed-ID pruning
case was added during review. Neither pre-fix failures nor post-fix passes have been executed
here. Do not treat static inspection as reproduced or verified.

Owned code changes:

- `app/src/main/java/org/adaway/ui/discover/DiscoverFilterListsFragment.java`
- `app/src/main/java/org/adaway/ui/discover/DiscoverFragment.java`
- `app/src/androidTest/java/org/adaway/ui/discover/DiscoverStateRegressionInstrumentedTest.java`

No edits to Home, Sources ViewModels, the operation coordinator, other agents' tests, shared
`sources-bugs.csv`, `tasks/todo.md`, layouts, resources, build files, or dependencies.

## Root Causes And Fixes

### AUD-SRC-003: Late Refresh Subscription Truth

The old directory loader queried sources before HTTP requests and published that old source
snapshot with both cached and refreshed metadata. A Room notification arriving during HTTP could
therefore be overwritten without another database change to repair the screen.

Removed the loader's source query and both source-cache publications. Directory requests now
publish directory metadata only; the existing DAO observer and explicit mutation callbacks retain
subscription ownership. Existing single-toggle, unsubscribe synchronization, and bulk semantics
are unchanged. Observer and directory-result callbacks additionally capture their view binding
so results for a destroyed view cannot publish into a replacement view on the same fragment.

Two deterministic tests hold `/lists` after expired cached rows are displayed, mutate the DAO to
model a concurrent worker/editor add or removal, and wait for Subscribed-only scope to reflect the
change. A changed list title proves that the late HTTP response was applied before checking the
row switch and Subscribed-only scope again. These tests intentionally use DAO writes so the
pre-release UI change proves observer delivery rather than an optimistic row-action callback.

### AUD-SRC-004: Semantic Language Selection

The spinner adapter replacement previously selected position zero and its callback reset the
language filter. Adapter population now finds the selected language ID's new position, detaches
the listener during replacement, restores the position, and only then attaches the listener.
An absent language falls back to All. A transient `onNothingSelected` does not clear semantic
state. Spinner hierarchy position saving is disabled because the fragment saves the language ID.

Tests hold a refresh while English is selected, then return identical metadata or German/English
in reversed order. Both assert English and the expected three-row scope after refresh publication.

### AUD-SRC-005: Saved Filters And Checked IDs

The fragment previously kept tag and checked IDs only in instance fields. Its early empty filtering
could also prune restored IDs before asynchronous directory or subscription data arrived.

Fragment saved state now stores checked list IDs, tag ID, language ID, search text, and both filter
switches. Controls are initialized before listeners, and dynamic chip hierarchy saving is disabled
in favor of semantic tag state. Pruning waits for a directory snapshot and restored view state;
Subscribed-only pruning also waits for the first source observation. Once these are ready, the
existing visible-selection pruning and explicit checked-row bulk scope remain in force. A valid
cached directory is an available snapshot; an empty initial adapter is not one.

Four tests cover activity recreation; parcelled saved state restored into a new fragment while the
cold directory request is held; a held initial source observation under Subscribed-only; and a
loaded snapshot removing a checked ID. The removed ID is reintroduced in a later snapshot to
confirm it does not become checked again. The recreation test also hides checked rows with the
tag filter and confirms returning to them does not resurrect selection. Supported and unsupported
rows are both checked, preserving the selected-command/no-op/manual-review safety contract.

The parcel tests exercise a serialized process-restoration state boundary without retaining the
old fragment instance. They do not claim an OS-killed-process end-to-end replay.

### AUD-SRC-006: Live Parent Profile Header

The parent previously refreshed its profile label only on initial display/resume and preset
completion. It now observes sources with its view lifecycle and reuses the existing enabled-URL
query and profile reconciliation. Result delivery is tied to the originating view and latest
request generation. Preset behavior and source mutation orchestration are unchanged.

The regression opens the real Discover parent/child, confirms a subscribed child switch's
unsubscribe dialog, and waits for exact -> partial without leaving Discover. Subsequent DAO
re-add, extra-source insertion, and disabling that extra source assert exact -> extended -> exact
without navigation, also checking that enabled state rather than mere row existence drives status.

## Main Run

New instrumentation class: `org.adaway.ui.discover.DiscoverStateRegressionInstrumentedTest`.

| Finding | New test methods |
| --- | --- |
| AUD-SRC-003 | `lateRefreshPreservesUnsubscribeObservedWhileCachedRowsAreVisible`; `lateRefreshPreservesSubscribeObservedWhileCachedRowsAreVisible` |
| AUD-SRC-004 | `refreshRetainsLanguageWithUnchangedMetadata`; `refreshRetainsLanguageIdAcrossMetadataReordering` |
| AUD-SRC-005 | `recreationPreservesCheckedIdsAndTagScope`; `parcelledStateKeepsCheckedIdsUntilColdDirectorySnapshotArrives`; `restoredSubscribedSelectionWaitsForInitialSourceObservation`; `authoritativeSnapshotPrunesRemovedCheckedIds` |
| AUD-SRC-006 | `profileStatusUpdatesAfterChildSubscriptionWithoutLeavingDiscover` |

Optional focused selector for main, not executed here:

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.discover.DiscoverStateRegressionInstrumentedTest
```

Retain these existing regressions in the consolidated run:

- Instrumentation: `DiscoverCacheRecoveryInstrumentedTest`,
  `DiscoverFilterListsFiltersInstrumentedTest`, `DiscoverProfileStatusInstrumentedTest`,
  `DiscoverUnsubscribeRuntimeInstrumentedTest`, `DiscoverUnsupportedReviewInstrumentedTest`,
  `DiscoverQuickPresetInstrumentedTest`, and `org.adaway.ui.hosts.FilterListsBulkUiInstrumentedTest`.
- JVM: `org.adaway.ui.discover.DiscoverPresetSubscriptionTest`,
  `DiscoverCatalogDetachSafetyTest`, and existing Discover state/selection tests in the full batch.
- Lint/build: main's planned full gates; Android API compilation and runtime behavior remain
  unverified until those gates finish.

Fixtures use intercepted directory HTTP, Room, real fragment lifecycles, bounded polling, and
latches. They do not depend on public network access or arbitrary response delays. Test teardown
releases held refreshes, restores the HTTP client, removes the named test profile, clears fixture
cache/sources, and resets background work. Run instrumentation serially against these shared
application fixtures, as with the existing Discover instrumentation suite.

## Local Evidence And Ledgers

- Read `CLAUDE.md`, `tasks/lessons.md`, `sources-review.md`, and `sources-bugs.csv` before editing.
- Completely reviewed the two owned production files and the new regression test. Supporting
  DAO/store/executor/layout/existing-test reads are context, not an expanded codebase audit claim.
- Scoped `git diff --check` passed for the two tracked production files. The new test's
  `git diff --no-index --check /dev/null ...` produced no whitespace diagnostics (exit 1 denotes
  the expected new-file difference, not an executed test failure).
- `discover-fixes-files.csv` contains the three frozen code/test SHA256 values and scoped
  assessments. Documentation files are not recursively included in that implementation ledger.
- `discover-fixes-bugs.csv` preserves the canonical 11 columns and the four existing finding IDs.
  All four remain `implemented-awaiting-test`; main should attach actual test evidence before
  promoting status or merging into the shared bug index.
- Structured CSV validation passed: canonical header equality, four unique finding IDs, all four
  pending-test statuses, three file-ledger rows, and exact current code/test hash matches.
