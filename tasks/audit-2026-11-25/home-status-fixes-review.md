# Home Status Fixes - 2026-11-25

## Scope

Repository: `/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway`.
Branch: `codex/full-codebase-audit`. Shared dirty checkout; no commit or push.
Assignment: AUD-UI-003 and AUD-UI-004 from `main-bugs.csv`.

Read `CLAUDE.md`, `CONTRIBUTING.md`, `tasks/lessons.md`, and the audit README.
No `AGENTS.md` was found in this repository or its ancestor directories. The
explicit ownership and execution limits override the general request to update
`tasks/todo.md` and run builds. No base ledger or existing test was edited.

Owned changes: `HomeFragment.java`, four additions to `values/strings_home.xml`,
two NEW Home regression test classes, and these NEW `home-status-fixes-*` reports.
Both bugs are **implemented-awaiting-test**, not verified-fixed.

## AUD-UI-003

The scheduled observer cleared its flags on completion but never removed the
shared progress views. The subscription observer sometimes happened to remove
them, depending on delivery order. Its snackbar field was never assigned, so it
could not serve as an ownership signal for overlapping work.

Both WorkManager observers now retain their own active WorkInfo snapshot and call
one renderer. Subscription work (RUNNING, then ENQUEUED) has stable priority over
RUNNING scheduled work. When the visible task ends, the renderer immediately
shows the retained other task; when both are inactive, it clears text, click
handling, and both views. Scheduled ENQUEUED remains idle because this is periodic
work waiting for its next run, not an active import.

Monotonic progress is scoped to the work ID, attempt, generation, and total. A
replacement, retry, or changed total cannot inherit the previous progress floor.
Direct visibility changes avoid competing hide/show animation completion
callbacks. The subscription Discover action is removed on scheduled handoff.
Obsolete snackbar/flag fields are removed. Snapshots are cleared on view teardown.

## AUD-UI-004

The applied Boolean alone previously selected green `Protection active`, including
a VPN tunnel with zero blocking rules after a failed all-or-nothing import.

A distinct view-lifecycle observer now keeps the existing active-truth count
LiveData observed even when the three display-counter observers detach during
import. It reads the same tiny `hosts_stats` snapshot exposed by HomeViewModel:
VPN uses `blocked_count`; root uses `blocked_exact_count`. It never infers loaded
rules from parsed progress, frozen/abbreviated counter text, source counts, or the
cached initial count. No extra large-table query or model mutation is introduced.

- Applied false: existing `Protection off` and red color.
- Applied true, snapshot unknown: enabled with `checking blocking rules`.
- Applied true, zero blocking rules: enabled with `no blocking rules loaded`.
- Applied true, positive blocking count: existing green `Protection active`.

VPN messages explicitly say `VPN enabled`; root messages say `Ad blocking
enabled`. Unknown/empty states use normal foreground color, not success green.
The existing one-line status restriction is removed in the renderer so the
warning can wrap. Narrow-screen/large-font visual verification belongs to main.
This describes the app's current active-rule snapshot, not a new guarantee about
every request, external DNS bypass, root-file freshness, or VPN service health.

The switch continues to use the model's actual applied state and existing
can-change gate, even with zero rules. The pre-existing dirty
`bindProtectionControl` and `renderProtectionControl` bodies are byte-for-byte
preserved; all five original render call sites remain. All pre-existing string
lines are preserved. Counter observer attachment and view-local guards are reset
on teardown so reattaching the same Fragment can observe again; the ViewModel's
cached import baseline is not cleared by teardown.

## New Tests

`HomeWorkProgressRegressionInstrumentedTest`: six deterministic tests invoke the
production observer handlers against real inflated Home views, without starting
workers or downloading sources. Cases cover success/failure/cancellation after
the other observer has settled; overlap in both delivery orders and both finish
orders; periodic requeue versus queued subscription; RUNNING selection among
historical/queued rows; monotonic updates and new IDs/totals; retry/generation
reset. Assertions include retained text, both visibilities, preparing state, and
removal of the subscription click action during handoff.

`HomeProtectionStatusRegressionInstrumentedTest`: five ActivityScenario tests
cover enabled VPN zero/positive/zero snapshots and disabled state with loaded
rules; root exact-count selection with other nonblocking rule counts present;
unknown snapshot; changing snapshots while counters are detached and the parsed
count is positive; Activity recreation during import; and detach/attach of the
same Fragment view. Tests assert status text/color and the unchanged applied
switch state, and reject renderer-triggered apply/revert operations.

Status tests replay the active stats row inside Room transactions so invalidation
is delivered, restore its original contents, use a fake applied-state model,
disable background schedules using the existing fixture, and drain pending
counter snapshots before the detached-counter assertions. They are not real
imports or tunnel tests. No VPN permission or root hosts-file write is requested.

## Source Checks

Completed, without Gradle/emulator/adb or Android test execution:

- Read both original observer branches, lifecycle/counter bindings, applied-state
  switch path, active-count DAO queries, layout, and surrounding test patterns.
- JDK21 JavacTask.parse parsed HomeFragment and both new test files:
  `SOURCE_PARSE files=3 diagnostics=[]`. Syntax only, not compilation/type checking.
- `xmllint --noout app/src/main/res/values/strings_home.xml` passed.
- Scoped `git diff --check` passed; new-file whitespace checked separately.
- Inspected the installed WorkManager 2.9.0 class with `javap`; the WorkInfo
  constructors and generation/attempt accessors used by the tests are present.
- Compared dirty switch method bodies and pre-existing resource lines against
  captured pre-edit contents. No protected implementation was removed.

Current source/test SHA256 values are in `home-status-fixes-files.csv`.
The starting dirty HomeFragment hash was
`d02044b82492edd86afbabc3c14325cbad3ad335d5cabe1fa2eea486c5d1e2ec`;
the starting dirty strings hash was
`c43f5082714518dd6c86deda5e9a681b1423236c42344ffe255ef6b4042e83d7`.

Protected files still match their pre-edit hashes at handoff:

| File | SHA256 |
| --- | --- |
| HomeViewModel.java | 65afe60c8d9d6c6199a512fcdca37844e5bb7288da13ac7749b8c60e16501b15 |
| HomeActivity.java | 3519095b8824c97f3fc7cf61cd3e78cd3051e77d9bc7b51328f1993e4f01a280 |
| HomeLaunchNavigationStateInstrumentedTest.java | 4096dc170881c4cc6503d902c239f3f1596b6f786cd3e956c54a2632076d49d7 |

## Main-Owned Verification

These commands are instructions only, NOT run by this lane. Use main's configured
JDK21/Android environment from the repository root and a disposable test device.

```sh
./gradlew :app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.home.HomeWorkProgressRegressionInstrumentedTest,org.adaway.ui.home.HomeProtectionStatusRegressionInstrumentedTest
```

After the new 11-case suite, rerun existing HomeProgress, HomeCounters,
HomeUpdateControls, HomeUpdateActions, and HomeLaunchNavigationState instrumentation.
Main must reconcile the existing HomeLaunch fixtures: several assertions expect
`Protection active` after setting applied=true without seeding positive blocking
rules. Seed the appropriate active stats fixture when testing genuinely loaded
protection, or expect the truthful empty-rule state when the fixture is empty.
Do not weaken the protection-switch applied-state assertions. This lane did not
edit those tests.

Runtime gates still pending:

1. Real scheduled worker completion after the subscription observer settles,
   including periodic RUNNING-to-ENQUEUED, failure, cancellation, and overlapping
   subscription work. Confirm no stale row and no hidden remaining task.
2. Fresh onboarding import failure with VPN enabled and zero active blocking
   rules; retry to positive rules; remove the last blocking rules; disabled VPN
   with retained rules. Inspect switch state and actual tunnel state separately.
3. Navigate away/back and recreate during import/finalize/terminal states.
4. API26/28 and API34+ layout checks at narrow width and large fonts. Confirm full
   warning text and no overlap with name/description/counters. The wrap setting
   is source-reviewed, not visually verified here.

No compile, device, screenshot, network, or runtime pass is claimed. The direct
progress replays do not replace WorkManager/database delivery integration QA.
