# Support Fixes Handoff

Date: 2026-11-25. Branch: `codex/full-codebase-audit`.
Original audit baseline: `d51eb7aea06871c2c6ad2bbedb9ddbaaaca48d77`.
Main reports phase-one commit `f7dbbb5e7` excludes these unstaged changes.
Repository: `/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway`.

## Ready and Frozen

Production/test inputs are ready and frozen for main's next integrated build.
No further production or test edits are planned by this lane. The earlier freeze
was respected: no production/test writes before explicit GO. All lane changes
remain unstaged; no stage, commit, push or central-ledger operation was performed.

Implemented all three approved P2 findings. Bug sidecar statuses intentionally
remain `source-confirmed` pending Android execution; implementation and host
evidence are recorded in separate columns, not mislabeled verified runtime fixes.
Only these approved changes, five new test classes, one narrow existing test
assertion update, one standalone runner and support-fixes sidecars were authored.

## SUP-AUD-001: Uninstall Permission

`app/src/main/AndroidManifest.xml:38` adds exactly one normal
REQUEST_DELETE_PACKAGES permission. No DELETE_PACKAGES, SDK cap, exported-component
change or variant-specific override was added. Minimum API remains 26, which
supports the normal permission; host XML checks cover debug/directRelease overlays.

`AdwareFragment.java:154-157` retains ACTION_DELETE and the detected package-name
URI. It still hands off to the system confirmation. No privileged delete API,
shell path, immediate row removal or success assumption was introduced. Existing
intent-contract tests pass. The source/AOSP permission rationale remains in
`remaining-support-review.md`; this patch does not claim an observed system dialog.

## SUP-AUD-002: Refresh Ordering

`AdwareFragment.java:95-105` refreshes on resume and clears view/model references
when its view is destroyed. Initial activation, return and scanner reopen use
the same activity-owned ViewModel. `AdwareViewModel.java:25-36` delegates refresh
and has package-private data injection for deterministic lifecycle tests.

`AdwareLiveData.java:60-95` separates construction from scanning. Each refresh
captures a new generation and queues package I/O on the dedicated executor. Its
completion is posted to the main executor, where the generation is checked
immediately before setValue. Both refresh calls and publication are main-thread
operations. There is no check-before-postValue race. Old visible data is retained
while refreshing; unchanged package state after cancellation keeps the row.

Scanning/signatures/sorting are otherwise unchanged. A package-private executor
constructor and overridable package-private scan method permit controlled test
snapshots without a global hook or real package mutations.

The only existing-test adjustment is
`app/src/test/java/org/adaway/ui/domainchecker/DomainCheckerTest.java:169-175`:
replace the old exact method-reference assertion with the dedicated injected
executor wiring assertion and forbid direct diskIO wiring. All 15 cases pass.
This compatibility change was identified during the freeze; no production code
was distorted to retain the old string.

## SUP-AUD-003: Accessible Actions

`LogAdapter.java:79-102` now derives descriptions from the same ListType and
selected comparison used for listeners. Selected icons announce removal; other
icons announce the appropriate add operation. Rebinding still resets tint and
listeners. Callback API, host browser/copy actions and log activity/model are
unchanged. The XML initial Allow/Redirect descriptions are corrected, and exactly
three removal strings are added to `strings_log.xml`. Existing translated add
strings and native-unavailable wording are preserved.

## Executed Host Checks

Exact reproducible command from the repository:

```sh
ruby scripts/tests/support-fixes.Tests.rb
```

Final runner execution exited 0 with:

- 32 current host cases passed; 0 failed; 0 skipped.
- 11 new cases: AdwareRefreshTest 6, AdwarePermissionContractTest 2,
  LogActionDescriptionTest 3.
- 21 existing cases: AdwareLiveDataMatcherTest 4,
  AdwareUninstallIntentContractTest 2, DomainCheckerTest 15.
- Baseline-resource control: 5 cases executed, exactly 2 expected failures
  (missing permission and swapped initial label).
- Missing-generation-guard control: 6 cases executed, exactly 3 expected failures
  (late worker, stale queued main delivery, late main overwrite).
- Neither negative control modifies the worktree; both operate only on temporary
  copies. These are intentional control failures, not 11 additional passing tests.

Every exact method, result and boundary is recorded in `support-fixes-tests.csv`.

## Compile-Readiness Check

Passed standalone javac compilation of the changed production Java files,
supporting classes, all six host test classes and both new instrumentation
classes. Android API compilation uses source/target 17, Android 36 android.jar
and the cached Android Java API image via --system, not only desktop Java APIs.

The runner reads cached libraries and existing app/test class outputs, writes
only a disposable temporary directory, and downloads nothing. Production classes
owned by this lane are compiled from copied current sources, not taken from
stale app output. Existing unrelated classes serve only as compile dependencies.

Because main owns resource generation, the runner supplies temporary R symbols
after verifying every referenced old symbol against cached R and each new string
against current XML. This proves Java/API compatibility, not AAPT linking,
manifest merging into a new APK, resource inflation, minification or installation.
No Gradle, lint, emulator, adb, package uninstall or other Android runtime was run.
Main's earlier 665-case/lint gate applies to its frozen phase-one APK, not this patch.

## Main-Owned Android Gates

Six new methods compile but have NOT run:

- AdwareRefreshInstrumentedTest: return refresh/empty state; reopen with the same
  model after an empty result; intercepted package intent and simulated canceled
  return retaining the row; installed normal permission without privileged delete.
- LogAdapterActionsInstrumentedTest: neutral-row names/callbacks; recycled-row
  add/remove descriptions, actual callbacks, tint and current host across all types.

The scanner test uses synthetic package snapshots and intercepts startActivity.
It intentionally never launches the uninstaller. Its existing passive-UI setup
resets preferences and WorkManager state, so run only in main's disposable fixture.
This is not proof of actual package deletion or a real system cancellation.
No privileged root-runtime claim is made.

After main builds/links the patch, run those six methods plus existing
AdwareScannerInstrumentedTest and the host suites above. Still needed separately:
real row-to-system confirmation and Cancel with a disposable fixture, affirmative
fixture uninstall/return refresh, recreation and a TalkBack check. Do not delete
the currently running instrumentation package or a user's real app.

## Scope and Integrity

`support-fixes-files.csv` contains 14 final full-read/hash-bound changed or new
files: seven production/resource files, six test files and the standalone runner.
The six test files comprise five new classes and the existing DomainChecker test.
Other supporting reads/compiled dependencies do not count as new audit coverage.

`LogActivity.java` and `LogViewModel.java` were neither edited nor used as review
targets. Central ledgers and the previous remaining-support report were unchanged.
The manifest diff is one permission line; no unrelated XML declarations changed.
Final report checks validate CSV schemas, hashes, bug references, exact method
counts, whitespace and that these inputs remain unstaged. Main owns import and
runtime closure of SUP-AUD-001/002/003.
