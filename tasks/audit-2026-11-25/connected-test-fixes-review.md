# Connected Test Fixes: Frozen Handback

Date: 2026-11-25
Branch: codex/full-codebase-audit
Source/test freeze: YES. No incomplete source work. No further app/ edits unless main reports a compile/test failure.

## Scope

Six approved findings corrected in exactly seven existing androidTest files. No production files, manifests, resources, existing shared helpers, previous system/legacy files, central ledgers, or unrelated tests were edited. No Gradle, adb, emulator, device settings, staging, commits or privileged root execution.

The three connected-test-fixes sidecars are the only repository writes after the freeze confirmation. Exact frozen fixture SHA-256 values are in connected-test-fixes-files.csv. Main reviewed the working diffs and owns the combined build and full targeted runtime.

## Implemented Changes

| Finding | File / Current Lines | Bounded Change |
| --- | --- | --- |
| CONN-AUD-003 | model/root/RootModelApplyInstrumentedTest.java:115-151 | UUID-named private backup directory; original/backup/restored SHA-256 checks; fail-fast copy/chmod/chown; retain backup on restoration failure; delete only after verified restoration. Existing rootHostsApplySmoke opt-in remains. |
| CONN-AUD-004 | ui/discover/DiscoverQuickPresetInstrumentedTest.java:65-105,137-175,199-228 | Save/inject/restore inert recording models; keep real unique-work enqueue; assert SUCCEEDED and exactly one retrieval/apply. Cancel/prune and restore under the operation lease after dispatch drains; refuse to restore real models while an operation remains active. |
| CONN-AUD-001 | db/SourceDbTest.java:494-519 | Keep SQL separate from rendered plan and execute both exact/suffix EXPLAIN variants. |
| CONN-AUD-002 | ui/discover/DiscoverFilterListsFiltersInstrumentedTest.java:78-115,172-218 | Clear search before tag selection; observe four exact IDs then three Regional IDs. Exact-ID assertions also cover subsequent language, compatible-only and empty scope. |
| CONN-AUD-005 | ui/prefs/PrefsAdBlockMethodSwitchInstrumentedTest.java:138-159; ui/hosts/FilterSetSaveApplyInstrumentedTest.java:379-405 | Absence requires the expected screen/dialog anchor in a non-null snapshot. Missing/wrong windows retry then fail; forbidden text is not accepted. |
| CONN-AUD-006 | ui/lists/ListsRuleActionsInstrumentedTest.java:119-152,227-243 | Capture rule ID and query persisted ID or host/source identity without enabled/type predicates. Disabling a retained row cannot satisfy deletion. |

Paths in the table are relative to app/src/androidTest/java/org/adaway/. Files/bugs CSVs contain full repository-relative paths.

## Standalone Compilation

PASS: all seven modified test sources compiled together with javac from OpenJDK 21, using the cached Android API system image and existing app/test classes, Android/AndroidX dependencies and R symbols. Annotation processing and implicit source discovery were disabled. Only existing deprecated-API notes were reported.

Output directory:
`/var/folders/cj/j956f9v920b8wk3wvd8ms2nh0000gn/T/adaway-connected-test-fixes-20261125-81129-1qh6cv6`

This is a compile-ready handback, not an APK packaging, Gradle lint, Room execution or connected-test pass. The typecheck read cached classpath inputs from /tmp/adaway-settings-fixes-checks/javac.args; it did not execute that other lane's script.

## Host Safety Controls

PASS: 13 controls using the actual updated RootModelApplyInstrumentedTest backup/restore methods with only Shell replaced by an in-memory command interpreter.

Command:
`ruby /tmp/adaway-connected-test-fixes-checks/run.rb`

Controls executed:
- Four backup failures: mkdir, cp, chmod, chown. Each assertion rejects the operation, stops at the failing step and leaves restoration disabled.
- Two independently created fixtures produce distinct backup paths.
- Corrupt backup copy and malformed original digest are rejected before privileged apply eligibility.
- Three restoration failures: cp, chmod, chown. Each rejects cleanup success and retains the recovery backup/restore-needed state.
- Tampered backup and wrong restored digest are rejected with recovery data retained.
- Successful restoration requires original digest equality, clears restore-needed state, and removes the verified backup.

The harness has no process execution or file IO for commands: path strings index a Java map only. Its getShell method throws if framework/root setup is attempted. It invokes neither the fixture's @Before setup nor RootModel.apply. Printed /system and /data paths are simulated, not accessed. Host negative controls do not prove Android shell behavior, real ownership/mode changes or real root restoration.

Temporary harness sources:
- /tmp/adaway-connected-test-fixes-checks/Shell.java
- /tmp/adaway-connected-test-fixes-checks/RootFixtureChecks.java
- /tmp/adaway-connected-test-fixes-checks/run.rb

## Remaining Runtime Limits

- All actual connected replays are pending main. No passing runtime result is inferred from source inspection or compilation.
- Root runtime stays excluded. The root smoke still skips without its explicit opt-in; do not enable it merely to close this sidecar.
- Quick-preset source/apply behavior is inert and deterministic, but the real WorkManager request retains production network/storage constraints. An unmet constraint causes the bounded success wait to fail, not silently pass. No network download or hosts installation is claimed by the recording counters.
- Exact-ID adapter checks, target-window absence checks, and deletion SQL have not executed on Android in this lane. Their existing behavioral assertions were strengthened rather than removed.
- Changes passed scoped git diff --check. Final sidecar schema/ID/hash validation also passed.
