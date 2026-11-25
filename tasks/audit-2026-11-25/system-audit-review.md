# Helper and Broadcast Source Audit

Date: 2026-11-25. Branch: `codex/full-codebase-audit`.
HEAD: `d51eb7aea06871c2c6ad2bbedb9ddbaaaca48d77`.
Repository: `/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway`.
Paths below and in the CSVs are relative to this repository unless linked otherwise.

## Scope and Evidence

- Read `CLAUDE.md`, all of `tasks/lessons.md`, the active section of `tasks/todo.md`,
  audit README, `files.csv`/`current-reviews.csv` entries, and relevant native, VPN,
  network, scheduling and release lane evidence. Compared candidate paths/hashes
  against every existing `*-files.csv` before recording new coverage.
- Fully read all eight helper/broadcast Java files. Prioritized the four pending
  files: `NotificationHelper`, `PreferenceHelper`, `ThemeHelper`, `UpdateReceiver`.
  The other four retain their existing matching-byte reviews; boundary inspection
  is not counted again as new whole-file coverage.
- `system-audit-files.csv` contains 18 newly reviewed, hash-bound files: four owned
  production files, nine corresponding tests, and five text resources. Every one
  was read in full. A full source read does not establish runtime correctness.
- Found two independent P2 issues: one production boot-consent boundary defect and
  one notification-test expectation defect. Both are `source-confirmed`, not
  reproduced, implemented, verified or closed. No duplicate of existing bug IDs
  was found. The boot finding supplements the native lane's explicitly deferred
  VPN-consent boundary, not its already-completed native boot review.
- Only the three `system-audit-*` reports were written. No production or existing
  test edits, central/base-ledger changes, builds, Gradle, Java compilation, JVM
  test execution, emulator/adb actions, installs, commits or pushes occurred.
  Privileged root runtime remains excluded; inspecting ROOT branches is not proof
  of root operation. Other agents' changes were left intact.

## Findings

### SYS-AUD-001 - P2 - Boot Consent Has No Result-Backed Continuation

Location: `app/src/main/java/org/adaway/broadcast/BootRestoreController.java:51-55`.
Related: `BootReceiver.java:49-51`; test
`app/src/androidTest/java/org/adaway/broadcast/BootReceiverBehaviorTest.java:95-110`.

Trigger: VPN method selected, start-on-boot enabled, and VPN consent no longer
prepared when BOOT_COMPLETED arrives. Use no Android always-on VPN or independent
start request to mask this branch. This is a proposed device reproduction, not an
executed one.

Source proof: a non-null `prepare()` result is launched directly with
`context.startActivity()` plus NEW_TASK, then the controller returns. The only
`mVpnGateway.start()` call is the other branch at line 58. There is no app Activity
result owner, pending enable request or subsequent consent-completion callback.
Android's [VpnService.prepare contract](https://developer.android.com/reference/android/net/VpnService#prepare(android.content.Context))
requires an Activity result flow. The
[Android 14 confirmation implementation](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/packages/VpnDialogs/src/com/android/vpndialogs/ConfirmDialog.java#L92-L101)
obtains the requesting package from `getCallingPackage()`; its approval path
authorizes and returns RESULT_OK, rather than starting AdAway's service. Direct
receiver launch provides neither the intended result caller nor a consumer.

Impact: boot recovery cannot complete reauthorization and resume AdAway protection
through this branch. Even on a platform that displays the requested screen, this
code has no transition from approval to service start. On modern Android,
[background activity restrictions](https://developer.android.com/guide/components/activities/secure-bal)
are an additional constraint; exact OEM/dialog presentation is not asserted here.

Counterchecks: already-prepared consent correctly reaches line 58. ROOT and
disabled boot preference return at lines 46-48 and must remain unchanged. The
existing test replaces the real consent activity with a recording Context and a
synthetic Intent; it proves one launch and zero immediate starts, not real consent
identity or eventual activation. Home's Activity result handler at
`ui/home/HomeActivity.java:79-94` is not entered by this receiver. This is distinct
from HOME-PATCH001's Home continuation race and NOV-VPN-01's service decoder fix.

Fix direction: route recovery through an app-owned Activity/result flow, normally
from a user-tapped notification when background launch is restricted. Reuse the
normal consent handling and start only after successful authorization and a fresh
check of selected method/requested state. Preserve cancellation and ROOT exclusion.

Main-owned regression: real missing-consent boot recovery followed by approve,
deny, Back and recreation; verify prepared state plus established VPN only after
approval. Also retain the already-prepared, boot-disabled and non-VPN branches.
Do not replace these with a test that only records `startActivity()`.

### SYS-AUD-002 - P2 - Notification Test Ignores Blocked Channel State

Location:
`app/src/androidTest/java/org/adaway/helper/NotificationHelperChannelInstrumentedTest.java:131-138`.
Related reset: lines 41-54 and 222-230; metadata assertion: lines 194-203.

Trigger: on a persistent test install, leave app-wide alerts enabled (and Android
13+ notification permission granted) but disable the Updates channel in system
settings. Then run `updateAlertPostingMatchesCurrentNotificationPermissionState`.
No settings mutation or test run was performed in this audit.

Source proof: the branch derives `alertsEnabled` solely from
`NotificationManager.areNotificationsEnabled()`, then requires both alerts to be
active. Both builders use `UpdateChannel`. Android also suppresses alerts for a
channel with IMPORTANCE_NONE. Setup deleting and recreating the same channel does
not repair this precondition: Android's
[notification-manager contract](https://developer.android.com/reference/android/app/NotificationManager#deleteNotificationChannel(java.lang.String))
restores the channel's prior settings on recreation; app-wide enablement is a
separate check. The [channel guide](https://developer.android.com/develop/ui/compose/notifications/channels#ReadChannel)
requires reading the channel importance to determine the user's channel choice.

Impact: the test times out/fails against correct production behavior on a supported
user setting. `createChannelsInstallsExpectedUserVisibleMetadata` can likewise fail
its fixed-importance assertion after the same delete/recreate cycle. This is a
test reliability defect, not a requirement to override the user's channel block.

Counterchecks: a fresh install/default channel masks the defect. The dedicated
LOW-to-DEFAULT probe already recognizes importance persistence, but the alert
expectation and default-metadata case do not account for production channel state.
The recently added foreground-service stop-before-delete protection addresses a
different failure and is not being reopened.

Fix direction: base the posting expectation on app permission and the actual
Updates channel importance. Keep a dedicated blocked-channel suppression case.
Run default-metadata assertions only with an explicitly fresh/disposable channel
fixture, or assert preservation of prior user settings when a channel exists.
Do not grant notification permission or reset user settings merely to make it pass.

Main-owned regression: app-denied, app-enabled/channel-enabled, and
app-enabled/channel-blocked fixtures, including a second run after channel
delete/recreate. No production notification fix is proposed by this finding.

## Requested Boundary Checks

### Exported Intent Trust and Null Commands

`AndroidManifest.xml:17-22,166-187` declares a signature-only SEND_COMMAND permission,
requires it on the exported CommandReceiver, and keeps BootReceiver/UpdateReceiver
non-exported. The VPN service separately requires BIND_VPN_SERVICE at lines 144-153.
CommandReceiver checks its action before lookup/dispatch. Existing immutable,
explicit VPN notification actions were checked at `VpnService.java:298-318`.
No unprivileged external-command bypass was established.

`Command.java:34-59` returns UNKNOWN for the ordinary reader's absent/null intent,
missing extra, explicit null, invalid string or wrong-type String-extra result.
The service's `readServiceCommand` at `VpnService.java:131-138` limits START fallback
to sticky null recreation or the framework service action without COMMAND; explicit
bad COMMAND does not use fallback. No duplicate NOV-VPN-01 finding is recorded.
Receiver `onReceive` methods assume a framework-supplied non-null Intent; merely
calling them directly with null is not a demonstrated platform delivery trigger.
Command security tests were read but not executed or treated as permission proof.

### Boot and Package-Replaced Scheduling

BootReceiver delegates only VPN restoration, not native services. WorkManager
periodic persistence is not invalidated merely because this receiver has no manual
schedule recreation call. Actual reboot timing was not tested.

UpdateReceiver accepts MY_PACKAGE_REPLACED and synchronizes all three scheduled
work families. Inspected source/APK enable-disable methods and the current
FilterSetUpdateService implementation: the latter now checks global, named-set and
per-source schedules and does not query Room in the receiver. This is the existing
AUD-SRC-002 fix, not a new finding. Its implementation/validation remains owned by
the scheduling/main lanes. The old package-replacement instrumentation only proves
direct callback behavior with enabled global scheduling; it is not APK upgrade or
reboot proof. Other schedule lanes' new tests were not re-reviewed here.

### Notification Permissions and Channels

NotificationHelper's three stable channel IDs have distinct metadata; minimum API
26 permits unguarded channel construction (`app/build.gradle:21`). Application
startup creates channels before ordinary helper usage. Update alerts use distinct
notification IDs, different explicit target components and immutable PendingIntents.
Both posting methods return if manager is absent or app-wide notifications are
disabled. Channel suppression by Android is valid behavior, not a production bug.

The VPN foreground path is separate from optional alert posting; current STARTING
and RUNNING use startForeground, while other alert states check POST_NOTIFICATIONS.
No runtime permission, foreground-service, OEM or channel-upgrade pass is claimed.
The notification settings UI/test boundary was read; full preferences UX and all
translated resources remain outside this lane.

### Current VPN Desired-State Guards

Inspected current `VpnService.java:112-215,229-281,324-350`: STOP clears
`vpnRequested`, removes delayed network work and stops heartbeat/worker; destroy
clears requested state and queued messages; worker messages require current-worker
identity and requested state; both queued network scheduling and application check
requested state. These are current guards, not the pre-fix October behavior.
Heartbeat was read as a dependency; its persisted-status/process check is not
equivalent to proving all background restart interleavings. No new lifecycle bug
or runtime closure is inferred from this limited boundary check.

## Coverage Accounting

The 18 new full-read rows and exact SHA256 values are in `system-audit-files.csv`.
Tests cover helper notification contracts, channel construction/posting, receiver
dispatch/security/package replacement, notification preference visibility and theme
selection. All nine are source-reviewed only; none ran. Boot tests mock consent;
package replacement directly invokes onReceive; channel tests alter real app state
when executed; theme tests assume a disposable UI fixture and English labels.
Those limitations are retained in the individual assessments.

Already-reviewed current-byte dependencies were read for the requested boundary
checks but deliberately omitted from the new-coverage CSV:

| Path | SHA256 | Existing review / supplemental result |
| --- | --- | --- |
| app/src/main/java/org/adaway/broadcast/BootReceiver.java | f5bd90be74631053b0ef82bd57a1a487d35995a77ca4f1f549ae868ca64eec97 | native-files.csv; boot dispatch connection only |
| app/src/main/java/org/adaway/broadcast/BootRestoreController.java | 831c16ef5e3560a5c00a268beff55490705c2b513412933345d829bffbdbbf32 | native-files.csv; supplemental SYS-AUD-001 in deferred VPN-consent boundary |
| app/src/main/java/org/adaway/broadcast/Command.java | 7e927870385caa6512170b96103309e8669f5774add1be893690ad048be42fc6 | vpn-files.csv; current reader/fallback compatibility retained |
| app/src/main/java/org/adaway/broadcast/CommandReceiver.java | 8c2126f1771170382da5e37960ae4c52c75010826c0d48d24a3cc9c8062072df | vpn-files.csv; trust/dispatch dependency only |
| app/src/main/AndroidManifest.xml | b917d420e6f46f83e8320113ca2052494be6ac536ecafb50287855df38942140 | release-files.csv; receiver/service permissions and notification permission |

Other supporting reads are not new whole-file audit coverage:

| Path | Examined scope |
| --- | --- |
| app/src/main/java/org/adaway/vpn/VpnService.java | Lines 100-372; commands, current-worker/requested-state checks, foreground notifications and network restart guards; hash 23b968ec286807e63d6dcd1b7284dd3943a64dd58085fa339805d745a6646d89 |
| app/src/main/java/org/adaway/vpn/VpnServiceControls.java | Fully read as existing VPN-lane dependency; consent/start/stop and persisted status usage |
| app/src/main/java/org/adaway/vpn/VpnServiceHeartbeat.java | Fully read as existing VPN-lane dependency; no new full-lifecycle audit claim |
| app/src/main/java/org/adaway/AdAwayApplication.java | Lines 40-124; channels, model initialization, global schedule startup |
| app/src/main/java/org/adaway/ui/hosts/FilterSetUpdateService.java | Fully read as scheduling-lane dependency; reconciliation fix not counted twice |
| app/src/main/java/org/adaway/model/update/ApkUpdateService.java | Fully read as release-lane dependency; periodic scheduling and notification caller |
| app/src/main/java/org/adaway/model/source/SourceUpdateService.java | Lines 1-170; schedule preferences, constraints and initial worker entry only |
| app/src/main/java/org/adaway/ui/home/HomeActivity.java | Lines 54-108; theme/clear-notification calls and Activity result ownership |
| app/src/main/java/org/adaway/ui/prefs/PrefsUpdateFragment.java | Fully read supporting caller; notification setting visibility and schedule listeners, not a new preferences-lane audit |
| app/src/main/java/org/adaway/ui/prefs/exclusion/PrefsVpnExcludedAppsActivity.java | Search-selected exclusion get/set callers only |
| app/src/main/java/org/adaway/vpn/worker/VpnBuilder.java | Search-selected exclusion consumers only |
| app/src/main/java/org/adaway/ui/home/LeakStatus.java | Search-selected exclusion consumer only |
| app/build.gradle | SDK declarations only |

Translations, drawable pixels, unexamined preference tests, other VPN tests, backup
serialization, native sources and whole-file correctness of the partial dependencies
are not claimed. CSV import and central bug-ID merging remain main-owned.

## Verification

Passed read-only Ruby CSV/Digest/Open3 verification: exact 4/11-column schemas and
row widths; 18 unique paths with matching current hashes; two unique source-confirmed
SYS-AUD IDs; no duplicate current-byte coverage in any existing lane/current ledger;
and `git diff --no-index --check` for all three new reports. Rechecked the unchanged
BootRestoreController and current VpnService hashes separately. These checks do not
execute app code. Real boot consent,
package replacement, notification denial/channel suppression, and VPN/network
interleavings remain main-owned runtime gates.
