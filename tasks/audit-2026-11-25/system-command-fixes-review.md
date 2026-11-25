# SYS-AUD-003 Command Ownership Follow-up

## Scope and Status

Implemented in the shared `codex/full-codebase-audit` checkout. Compile-ready
handback: isolated Android-API type-check passed; three host security contracts
passed. Android execution is pending main's run. This is not runtime proof of a
scheduled worker, SystemUI, notification delivery, VPN, or root behavior.

Only two production files changed: `tile/AdBlockingTileService.java` and
`broadcast/CommandReceiver.java`. Two focused Android fixtures were added; the
existing tile lifecycle fixture and command source-order contract were updated.
No strings were added. No Home, VPN, manifest, backup, list, domain, boot, or
notification fixture edits. In particular, main's notification STOP callback
barrier was left untouched. No central/base ledgers were edited by this lane.
No Gradle, emulator, adb, device settings, commits, staging, or push actions.
Root runtime remains excluded.

## Source-confirmed Bug

SYS-AUD-003: a scheduled update holds the application operation lease through
`FilterSetUpdateWorker.java:133-154`, including its final `adBlockModel.apply()`.
Previously, `CommandReceiver.java:28-31,35-48` and
`AdBlockingTileService.java:63-85` dispatched model changes without that lease.
With an update paused after retrieval but before final apply, notification STOP
or a tile toggle could revert protection and appear accepted; the older worker
could subsequently enable protection again. The tile's prior local guard was
inside its queued task, allowing rapid clicks to queue multiple later toggles.

Intake SHA-256:

- Tile: `f39565f033e96ca0c67bcfe001e776bfecb8e5c9bfd863a8ecfed930c9d2ee14`
- Receiver: `8c2126f1771170382da5e37960ae4c52c75010826c0d48d24a3cc9c8062072df`
- Existing tile test: `6029db503b311558c04bb05be5b570a59974f4ebe6dc3a7d1aa60ff8850b9463`
- Existing command security contract: `61128a3b85fff1ec6e1456ad359a09fc659ddc95e919fdc6d8186452a5839cc9`

## Changes

- Tile lines 71-85: synchronously claim the shared nonblocking lease before
  dispatch. Busy clicks return without queuing or mutating a model. A successful
  claim immediately renders unavailable, closing the same-frame click window.
- Tile lines 29-67 and 92-105: observe both applied and global busy state; publish
  `STATE_UNAVAILABLE` while any coordinated operation owns execution. Remove both
  observers on stop/destroy and reattach without duplicates. State reads use the
  synchronous coordinator truth, not delayed `LiveData.postValue` delivery.
- Tile lines 108-120: retain the lease through apply/revert, including checked or
  unchecked failure. Dispatch rejection closes it and refreshes tile state.
- Receiver lines 32-59: reject null intents, wrong actions, and unknown commands
  before work acquisition. Acquire before model lookup or dispatch. Busy returns
  call the existing `HostError.UPDATE_IN_PROGRESS` toast at lines 75-77. Accepted
  work holds its lease through terminal execution; dispatch failures also release.
- Both production dependency accessors return the application-owned coordinator
  and existing disk executor. Package-local overrides permit deterministic tests
  without replacing global application models or running VPN/root operations.
- No waiting command queue, replay after release, consent launch, activity launch,
  permission grant, or new exported identity was introduced. A rejected STOP must
  be retried after the update finishes; it is not presented as accepted.

## Verification

1. Host JUnit: all three `CommandReceiverSecurityContractTest` methods passed.
   Signature permission, exported receiver identity, sole canonical action, and
   guarded off-thread dispatch assertions remain. The source-order guard now also
   checks malformed-command return and busy feedback before model lookup.
2. Isolated `javac` type-check: passed for the two changed production classes,
   current `Command` and `SourceOperationCoordinator`, four Android test classes,
   and the host security contract. Used JDK 21 with `--system` pointing to the
   cached Android Java image, API 36 `android.jar`, existing compiled dependencies,
   and existing R bindings; `-proc:none -implicit:none -sourcepath ""`. Output was
   in a temporary directory and removed. This is not an APK/resource/Gradle build.
3. `git diff --check`: passed. Sidecar CSV shape and SHA-256 validation are recorded
   separately by the handback check.

New deterministic Android cases, type-checked but NOT executed here:

- `org.adaway.broadcast.CommandReceiverOperationInstrumentedTest` (7): held
  external lease rejects START/STOP with feedback and no dispatch; retry after
  release; rapid opposite command rejected while first is queued; null/wrong
  action/missing/null/unknown/invalid/wrong-type command ignored; dispatch rejection
  release and retry; checked and unchecked apply/revert failure release.
- `org.adaway.tile.AdBlockingTileOperationInstrumentedTest` (7): held lease blocks
  both toggle directions and renders unavailable; rapid clicks queue only one
  toggle; external lease acquisition/release refreshes a listening tile; rejection
  restores state and allows retry; checked and unchecked apply/revert release.
- Existing `AdBlockingTileServiceInstrumentedTest` (4) type-checked, not run;
  observer lifecycle assertions now include global busy state. Its SystemUI shell
  case remains conditional and was not invoked.
- Existing `CommandReceiverSecurityInstrumentedTest` (4) unchanged and
  type-checked, not run; actual sender permission enforcement is not proved by
  directly calling `onReceive` in the new deterministic fixture.

The new fixtures use a real isolated `SourceOperationCoordinator`, a deliberately
paused injected executor, and recording models. They assert ownership during
model calls and never alter app-wide permissions, channels, preferences, or the
live application model. Captured tile states and busy callbacks are app-owned
behavior checks, not a claim of actual SystemUI or toast visibility.

## Evidence Boundaries

Both owned source files and corresponding test files were fully read. The
coordinator, its existing tests, Command reader, base model, executor, and error
mapping were read as dependencies, not newly discovered full-codebase issues.
`strings_errors.xml` was searched for the existing busy text only. Home operation
code (235-365), application coordinator accessors, and scheduled/immediate worker
lease/final-apply blocks were inspected as targeted context, not full-file audits.
The current manifest was parsed by the host security contracts, not edited or
claimed as a new full-file review. No additional bug is claimed from these reads.

Final byte hashes and assessment granularity are in
`system-command-fixes-files.csv`; the canonical 11-column bug row is in
`system-command-fixes-bugs.csv`; per-method evidence is in
`system-command-fixes-tests.csv`. Main owns reconciliation and device validation.
