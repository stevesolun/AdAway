# Source Scheduling Fixes

Date: 2026-11-25. Branch: `codex/full-codebase-audit`.
Baseline HEAD: `d51eb7aea06871c2c6ad2bbedb9ddbaaaca48d77`.

## Scope and Plan

- [x] Read repository guidance, lessons, audit README, sources review and bug rows.
- [x] Confirm AUD-SRC-001/002/007 against current code and check ownership boundaries.
- [x] Guard source picker and summary deliveries against activity lifecycle changes.
- [x] Reconcile periodic work against global, named-set and per-source schedules.
- [x] Defer current-selection persistence until schedule confirmation; preserve active identity.
- [x] Add NEW focused regression tests; statically review them without executing Android gates.
- [x] Record exact paths, SHA256 review coverage, test methods and remaining limits.

Only scheduling changes in SchedulesActivity, FilterSetUpdateService, FilterSetStore and
HostsSourcesFragment were made. These four files were clean at intake. Existing dirty
files, including FilterSetUpdateWorker and HostsSourcesViewModel, are read-only boundaries.
No central/base audit records, existing tests, SourceModel, SourceUpdateService, Discover,
ApplyConfigurationSnackbar, or unrelated code was edited. No Gradle, emulator, adb,
commit or push; main owns compiled and executed test evidence.

## Confirmed Causes

- AUD-SRC-001: both Room result deliveries capture the activity without a lifecycle guard;
  the picker creates a window even after navigation or recreation.
- AUD-SRC-002: syncPreferences cancels the shared dispatcher solely on the global flag,
  although the worker also evaluates independently scheduled sets and source URLs.
- AUD-SRC-007: selecting Current selection calls saveFilterSet before any schedule choice,
  persisting a set and changing active profile even if the rest of the chain is cancelled.

## Implemented Behavior

- Invalidate pending screen results on pause and reject delivery to finishing/destroyed or
  non-resumed activities. Use application context for disk-side database/preferences access.
- Keep the dispatcher when any configured schedule is enabled, without querying Room from
  the package-replacement receiver. No enabled schedules means cancellation as before.
- Capture enabled URLs at Current selection choice. Cancel/Back or Off creates nothing.
  On final time confirmation, allocate a collision-free name and persist metadata, URLs and
  schedule together; never change active identity merely to configure scheduling.

## Paths and Tests

Repository: `/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway`.
All paths in the CSV sidecars are relative to this root.

- `schedule-fixes-files.csv`: eight exact Java paths and final SHA256 values with scoped
  assessments: four modified production files, three new test classes and one new test helper.
  Every listed file was read in full; review conclusions are scheduling-specific, not claims
  that every unrelated profile/source workflow is correct.
- `schedule-fixes-tests.csv`: 16 exact new test methods, coverage and individual limits.
- `schedule-fixes-bugs.csv`: the three assigned IDs, each `implemented-awaiting-test`.
  These sidecars are handoff evidence only; no central/base ledger was imported or edited.

New instrumentation class selectors for main's execution:

```text
org.adaway.ui.hosts.SchedulesLifecycleRegressionInstrumentedTest
org.adaway.ui.hosts.FilterSetScheduleReconciliationInstrumentedTest
org.adaway.ui.hosts.FilterSetSchedulingRegressionInstrumentedTest
```

Existing tests were not modified. Useful adjacent regression gates remain
`FilterSetUpdateServiceWorkManagerTest`, `FilterSetSaveApplyInstrumentedTest`,
`FilterSetStoreMigrationTest`, `UpdateReceiverPackageReplaceInstrumentedTest`,
`PrefsUpdateSchedulingInstrumentedTest` and the JVM `FilterSetStoreTest`.

## Review Coverage

Production intake hashes (all four files were clean against HEAD before editing):

| File under `app/src/main/java/org/adaway/ui/hosts/` | Intake SHA256 |
| --- | --- |
| SchedulesActivity.java | 5cc19537969bbdf77df1068f001ca6a99fd934ea3a6530cacea0709d766fd353 |
| FilterSetUpdateService.java | 83682b6ab53042cc23c55bd3d781b1860be7f433238479980ade4c675cdf68cd |
| FilterSetStore.java | 357c9797ee365c7a1bbc26883bc4c6b1c9c9ed1a606cb8ace6f6fc2bc36f3ffe |
| HostsSourcesFragment.java | 3fd385fdd8bb2bdd7b7f3aa07f36a5cf75625628a0410c1e5d482c9a4f361e28 |

The final ledger hashes supersede these intake hashes for this scheduling review. Supporting
read-only traces included FilterSetUpdateWorker, UpdateReceiver, AppExecutors, the test-state
fixture and existing scheduling tests, the Sources tab wrapper and schedule layout, plus
targeted DAO/entity/view-model methods. These supporting reads are not claimed as new full
package review coverage; the dirty worker/view-model files remain their owners' responsibility.

## Verification Limits

- No Gradle, javac, emulator, adb, instrumentation, JVM tests, lint, installation, screenshots,
  commit or push were run. No runtime reproduction or verified closure is claimed.
- All 16 tests are authored and manually reviewed, not compiled or executed. Main owns both
  test compilation and runtime/integrated gates. Accessibility lookup, lifecycle timing and
  WorkManager operation ordering still need their first device replay.
- Source picker requests are intentionally discarded after pause, including a quick return;
  fresh resume summaries still load and a new Edit request still works. Pending pickers are
  not retained across recreation. Other dialog-chain lifecycle behavior is unchanged.
- Source schedule retention is preference-based: a configured daily/weekly URL keeps the
  dispatcher even if its database source is temporarily disabled or absent. This avoids
  receiver/main-thread Room access and accidental loss on re-enable. The existing worker
  still filters enabled sources; no worker download/due/apply behavior was changed here.
- Package-replacement tests invoke the receiver directly on main. They do not install an APK,
  test reboot behavior, or prove OS periodic timing. Missing-dispatcher startup repair outside
  syncPreferences is not added; AdAwayApplication is outside this lane.
- Current-selection tests traverse the real Sources toolbar/menu handler and dialog controls;
  successful confirmation fixtures stamp the new set's last-run before enqueue to exclude
  download/apply side effects. Their focus is snapshot/name/identity and work registration.
- Store publication is atomic in one SharedPreferences editor application. Disk durability,
  process death, concurrent cross-process profile edits and storage failure are not tested.
  Existing legacy migration still runs through the existing resolver and is not redesigned.
- AUD-SRC-009, global-schedule cancellation semantics, source-editor scheduling, Discover,
  SourceModel/SourceUpdateService, ApplyConfigurationSnackbar and other audit findings remain
  out of scope. No unrelated changes were reverted or rewritten.

## Static Checks

Passed:

- `git diff --check --` with the four explicit production paths: no whitespace errors.
- `git diff --no-index --check /dev/null <new-file>` for all four new Java files and all four
  new schedule-fixes artifacts: empty diagnostics. A read-only Ruby/Open3 wrapper accepts
  no-index's expected exit 1 for new-file differences and rejects diagnostics/errors.
- Read-only Ruby CSV/Digest validation: exact headers and row widths for all three CSVs;
  eight unique file paths with matching SHA256; 16 unique test methods matching the three
  Java classes' @Test declarations; exactly the three assigned IDs and conservative statuses.
- `git diff --unified=0 -- app/src/main/java/org/adaway/ui/hosts/HostsSourcesFragment.java`:
  every changed hunk is in the scheduling block (original lines 535-597); no save/apply,
  observer, source mutation or navigation code changed.
- `git branch --show-current` and `git rev-parse HEAD`: requested branch and unchanged
  baseline HEAD. Scoped status contains exactly four modified production files, four new
  test/helper files and four new schedule-fixes artifacts attributable to this lane.

These checks validate artifacts/source structure only and are not substitutes for Android tests.
Outcome: all three assigned findings are implemented-awaiting-test, not verified/closed.
