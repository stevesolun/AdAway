# Boot Recovery and Notification Fixes

Date: 2026-11-25. Branch: `codex/full-codebase-audit`.
HEAD: `d51eb7aea06871c2c6ad2bbedb9ddbaaaca48d77`.
Repository: `/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway`.
Status: **compile-ready handback; implemented-awaiting-test**, not runtime-verified.

## Scope

- [x] Re-read guidance, lessons, prior system audit and current owned source/tests.
- [x] Confirm all six edited paths were clean at intake; preserve concurrent changes.
- [x] Replace receiver-side consent launch with a permission-respecting Home notification.
- [x] Update deterministic boot tests and notification channel/state tests.
- [x] Run focused host source-contract red/green, isolated Android-API type-check,
  XML validation and scoped whitespace checks.
- [x] Record hashes, bug status, exact test methods and remaining live gates separately.

Modified only two production Java files, three previously clean test files and two
strings in the existing base notification resource. Exact paths/current hashes are
in `system-fixes-files.csv`. No Home, VPN, manifest, other-agent, central/base-ledger,
existing audit-evidence or task-tracker changes. No Gradle, emulator, adb, test-device
settings, installation, app execution, commit or push actions. Privileged root
runtime remains excluded. Main's integrated UI run was not touched.

Canonical IDs remain `SYS-AUD-001` and `SYS-AUD-002`. The original
`system-audit-*` sidecars retain baseline evidence; these follow-up sidecars do not
silently mark those older hashes as current or merge central ledgers.

## Implemented Behavior

### SYS-AUD-001

`BootRestoreController.java:45-57` retains selected-VPN and boot-enabled guards.
When `prepare()` returns a consent Intent, it calls the injectable
`VpnGateway.requestPermissionNotification` and returns. It never launches that
Intent. Already-prepared consent still invokes the existing gateway start;
boot-disabled and ROOT paths still do nothing. The platform gateway delegates the
notification request to `NotificationHelper` at lines 76-78.

`NotificationHelper.java:178-219` adds the boot reminder and a shared posting guard.
The guard rejects a missing manager, app-wide disabled alerts, missing channel or
IMPORTANCE_NONE. Existing app/hosts alerts also use this guard. No permissions are
requested or altered. OS notification suppression remains authoritative.

The new reminder uses the existing VPN channel and `VPN_RESUME_SERVICE_NOTIFICATION_ID`
(21), so the existing VPN STARTING/RUNNING path clears it without VPN edits. It has
an explicit immutable Home Activity PendingIntent, no extras, no automatic START
command and no broadcast/service action. Tapping only opens Home. The user then
chooses the actual Home Resume switch and its existing ActivityResult consent flow.
The notification auto-cancels on tap; app/channel denial leaves ordinary Home resume
available, with no attempted background Activity fallback.

Added base strings: `notification_vpn_permission_title` and
`notification_vpn_permission_text`. Other locales use the normal base-resource
fallback until translated; no translated files or app identity assets changed.

### SYS-AUD-002

`NotificationHelperChannelInstrumentedTest` no longer deletes production channels
in normal setup/teardown. Metadata checks snapshot existing importance, sound,
audio attributes, vibration, lights, badge, DND, lockscreen and group settings, then
assert they survive channel registration. Default importance is asserted only when
there was no previous channel. No user setting is reset to make a test pass.

Update/boot posting expectations independently read app-wide permission and the
actual relevant channel importance. Cancellation is awaited before posting checks;
negative assertions observe a bounded interval rather than pass on an immediate
empty notification list. Probe notifications use a test-specific tag.

The independent channel-only suppression case requires app-wide permission to be
already enabled. It creates UUID-scoped disposable NONE and LOW channels, requires
the LOW positive-control notification to arrive, then asserts that both update and
boot alert payloads remain absent on the NONE channel. It also verifies suppression
after deleting/recreating that same disposable channel. This exercises the real OS
notification manager when main runs it; no mock result is called live proof.

The existing prepared-foreground-VPN cleanup regression remains. It waits for actual
service destruction before deleting the production VPN channel, recreates the
channel and checks preservation. Probe cleanup never changes app-wide permission.
No grant/revoke/adopt-shell-permission operation was introduced. On app-denied
fixtures, the channel-only test explicitly skips; that is not a channel-only pass.

## Verification Performed

### Host Source Contracts

With OpenJDK 21 and cached JUnit 4.13.2/Hamcrest, compiled only
`NotificationHelperContractTest.java` into a disposable directory and ran
`org.junit.runner.JUnitCore org.adaway.helper.NotificationHelperContractTest`.

- Before production changes: 8 tests, 4 expected failures (background consent
  launch, changed update guard contract, missing channel guard, missing boot alert).
- After production changes: **8 tests, 0 failures**. These are source-contract
  tests, not execution of Android notification or VPN behavior.

No Gradle or dependency download was involved. Output/classes were outside the
checkout and no project build outputs were changed by this lane.

### Isolated Android-API Type-Check

Passed `javac` with OpenJDK 21, `-source 21 -target 21 -proc:none -implicit:none`,
the cached Android Java system image, API36 `android.jar`, cached AndroidX/JUnit
dependencies, and existing app dependency class files. Explicit source inputs were
both changed production files, all three changed tests and unchanged BootReceiver.
Only existing deprecated-API notes were emitted; exit 0.

New resource symbols were supplied through a temporary compile-only `R` binding;
their XML names/content were checked separately. This validates Java signatures,
imports and Android API types, including the changed gateway implementation. It is
**not** AAPT/resource linking, a full fresh dependency build, APK packaging, Gradle
validation or executed instrumentation. Main must perform the real integrated
build. The temporary bindings/classes are never packaged or used as runtime proof.

### Static Checks

- Notification XML parses; the two added string names are unique and nonempty.
  Every temporary compile-only R field resolves to a current base resource or
  drawable file; this is still not resource linking.
- Scoped `git diff --check` is clean for all six changed files.
- The only other implementation of `BootRestoreController.VpnGateway` is the
  updated recording test gateway; no old test-method selectors remain in app,
  scripts or workflow source.
- CSV validation passed: six matching current hashes; the exact central 11-column
  schema with two implemented-awaiting-test rows; 22 unique methods matching all
  three test classes; source/report whitespace checks clean. The final host rerun
  again passed all eight cases. No central inventory import was performed.

## Compile-Ready Handoff

No known Java/API type errors remain in the changed files. Main's next gate is a
fresh Android resources/production/instrumentation compile, followed by these
classes after the current integrated run is finished:

```text
org.adaway.helper.NotificationHelperContractTest                 (8 host cases)
org.adaway.broadcast.BootReceiverBehaviorTest                    (5 Android cases)
org.adaway.helper.NotificationHelperChannelInstrumentedTest      (9 Android cases)
```

`system-fixes-tests.csv` records all 22 exact methods and their separate evidence.
Four notification cases and three host source-contract cases are new; the old
boot missing-consent case is updated and renamed. The Android tests have passed
only the isolated type-check, not device execution. Boot's recording gateway
proves routing/no automatic start; it does not grant real VPN consent.

The notification class's existing setup stops the app VPN and cancels heartbeat;
its live cleanup case requires prepared consent and connectivity. Run on the
controlled test fixture, not against an ongoing/personal VPN session. The
channel-only test requires app notification permission already granted and will
skip otherwise. Use an already-denied fixture for the denial branch; do not revoke
app-wide notification permission from inside the instrumentation process.

### Remaining Live Gates

1. VPN mode, boot enabled, missing real consent, no Android always-on override:
   boot should produce only a reminder when alerts are permitted. Tap it and
   confirm Home opens without automatically presenting consent or starting VPN.
2. Tap the real Home Resume switch; test grant, deny, Back, retry and recreation.
   Grant must establish the actual VPN; denied/cancelled consent must not start it.
3. App alerts denied and VPN channel blocked: no reminder, no background consent
   Activity, and the normal launcher -> Home Resume flow remains available.
4. Already-prepared boot starts normally. Boot-disabled and non-VPN guards remain
   inert. The source/fake ROOT branch check is not privileged root runtime proof.
5. Run notification tests on existing custom channel settings and on the disposable
   channel positive/negative fixtures; verify settings remain unchanged afterward.

These live gates and final integrated compilation remain main-owned. Both bug rows
stay `implemented-awaiting-test`; host checks alone do not close either issue.

## Intake Hashes

All six paths were clean against HEAD when claimed:

| Path | Intake SHA256 |
| --- | --- |
| app/src/main/java/org/adaway/broadcast/BootRestoreController.java | 831c16ef5e3560a5c00a268beff55490705c2b513412933345d829bffbdbbf32 |
| app/src/main/java/org/adaway/helper/NotificationHelper.java | 3f36990b379912466fb9afafeefcae76868c30cdc68f3fced6c6b9adcc17903a |
| app/src/androidTest/java/org/adaway/broadcast/BootReceiverBehaviorTest.java | e09c2e47e83db3f3643f9fca8dc9f1e7ad400c6b96e706f1758bb2274ea7114d |
| app/src/androidTest/java/org/adaway/helper/NotificationHelperChannelInstrumentedTest.java | 0340f2889d6a95dc9984ce1d739de122618bae58e4c1e0cdacf5ba8a8869f80a |
| app/src/test/java/org/adaway/helper/NotificationHelperContractTest.java | 39362d82bb154c532b4d6d85eacd5c0b1858a3a5c127a794dd1ad5910a24fe8a |
| app/src/main/res/values/strings_notification.xml | 67713d0625e129793bfbdee5548d2aedaaeb37512ff4bd140497c9cc10b3d6c4 |
