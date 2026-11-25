# Source Recovery Fixes: Evidence and Limits

Date: 2026-11-25. Shared branch: `codex/full-codebase-audit`.
Status: implemented, compile-ready, Android execution pending main.
Findings: AUD-SRC-008 and separate preexisting payload defect AUD-SRC-011.

## Hashes and Ownership

Exact final SHA256 values are in `sources-recovery-fixes-files.csv`.
Only two production files were edited: `FilterListsSubscribeAllWorker.java` and
`DiscoverFilterListsFragment.java`. Four new regression files were added.
Existing dirty Discover restoration/subscription-observer changes were preserved.
The previously reported nine connected passes are historical input, not rerun proof.

No SourceEditor, Home, Schedules, native, update, existing test, XML, dependency,
base audit ledger, task ledger, commit, push, Gradle, emulator or adb changes/actions
were performed by this lane. Main's concurrent layout and existing-test edits are
outside these hashes. No audit import/update command was run.

## Executed Checks

- Targeted production and new-test compilation passed using JDK 21 `javac`,
  `-source 17 -target 17 -proc:none`, Android API 36's cached `jdkImage` via
  `--system`, Android API 36 `android.jar`, cached dependencies and existing
  debug app/test classes. Outputs are isolated in `/tmp/adaway-sources-recovery-classes`.
  This is not Gradle, annotation processing, APK assembly or a clean full build.
- Host JUnit 4.13.2 passed 36 tests: 5 new `FilterListsRecoveryContractTest`,
  7 existing `FilterListsSubscribeAllWorkerTest`, and 24 existing
  `DiscoverPresetSubscriptionTest`. Source contracts and pure helpers are not
  scheduler, SharedPreferences, Room or UI runtime proof.
- Scoped `git diff --check` passed.
- Eighteen new Android cases compiled but were NOT RUN. Each exact method and
  its limitations are listed in `sources-recovery-fixes-tests.csv`.

Main's connected-test selections:

```text
org.adaway.ui.hosts.FilterListsRecoveryInstrumentedTest
org.adaway.ui.hosts.FilterListsLargeScopeUiInstrumentedTest
org.adaway.ui.discover.DiscoverRecoveryInstrumentedTest
```

Also rerun the existing SubscribeAllWorker DoWork/Room, FilterListsBulkUi,
DiscoverUnsupportedReview, and DiscoverStateRegression suites after integration.
The new UI cases rely on existing IDs and adapter counts, not layout parent types.
They directly host the child Discover fragment; the choice-121 test uses real
accessibility scrolling/clicking. Large checked selection is seeded reflectively
and calls the confirmed command; it is not a physical checkbox/confirmation test.

## Storage and Recovery Limits

- Display history remains first 500 outcomes, summary dialog first 80 with an
  explicit `+N more`, and notification preview first 3. None bounds recovery IDs.
  No-URL and unsupported IDs are stored independently with the history metadata.
- Every loaded unsupported choice is included in the scrollable native dialog.
  Recovery IDs absent from the current directory remain stored but require that
  directory metadata to load before they can be reviewed. No missing-directory
  fallback or offline detail UI is claimed.
- Up to 500 explicit IDs remain inline. Larger retry/checked selections use one
  UUID-token-bound committed snapshot plus tiny input flags, not ID arrays in
  WorkManager Data. No truncation, batching or prefix-only processing is used.
  The 10000-ID real Data overflow/under-512-byte token assertions are authored
  Android tests, not claimed measured host results.
- The single snapshot bounds retained scope history to one. Its contents are
  immutable for its token and independent of the mutable last-run recovery sets.
  It is retained after success/cancel for retry/restart safety; a new large
  UNIQUE_WORK_NAME/REPLACE request replaces it. Old/missing tokens fail closed
  before a directory request and never become an all-directory scope. This is
  intentionally not a store for multiple independent/concurrent named jobs.
- Cancellation persists completed outcomes and keeps the full saved input scope.
  A retry also seeds its pending failure IDs before any work, removing them only
  when their new result is known; cancelling before or during retry therefore
  does not erase the unattempted failures. A first-time cancelled selection does
  not label unprocessed candidates as failures. No resume-all-selection UI added.
- Scope commit failure aborts enqueue with existing failure feedback. Persistence
  still uses the repo's SharedPreferences/commit pattern, including synchronous
  preparation in the UI and existing last-run commit handling. Disk-full,
  abrupt-process-death, corrupt preferences and storage-latency/ANR cases were
  not injected. Arbitrarily long directory search/name text and unrelated
  WorkManager output-size risks are not addressed by this ID-payload fix.
- Existing legacy history has no way to recover IDs already discarded by the
  old cap. Legacy parsing remains until new recovery keys have been written.
- Fake directory clients prevent live subscriptions/source updates in new tests.
  No real FilterLists availability, API-level matrix, physical-device layout,
  source download/apply, VPN or root behavior was validated here.
