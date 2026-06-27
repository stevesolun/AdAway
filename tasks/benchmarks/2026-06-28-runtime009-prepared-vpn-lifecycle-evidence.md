# RUNTIME-009 Prepared VPN Lifecycle Proof - 2026-06-28

Scope:
- Story: `RUNTIME-009`
- Device: `adaway-api34-16g(AVD) - 14`
- Commit under test: `4a0a51b0 test: guard system contract tracker closure` plus the local
  `VpnLifecycleInstrumentedTest` log-wait fix in this slice.

## Preparation

The full lifecycle method requires Android VPN consent to be granted to the exact installed app UID
and no active VPN network before the test starts.

Commands:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am start -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER \
  -n org.adaway/.ui.home.HomeActivity
adb shell input tap 540 1754
adb shell input tap 894 1520
adb shell dumpsys vpn_management
adb shell dumpsys connectivity
adb shell am force-stop org.adaway
adb shell dumpsys vpn_management
adb shell dumpsys connectivity
```

Observed before force-stop:

```text
Consent dialog: "Connection request", "AdAway wants", OK button present
vpn_management: Active package name: org.adaway
vpn_management: Active vpn type: 1
vpn_management: OwnerUid: 10196
connectivity: VPN CONNECTED ... InterfaceName: tun0 ... OwnerUid: 10196
```

Observed after force-stop:

```text
vpn_management: Active package name: org.adaway
vpn_management: Active vpn type: -1
vpn_management: OwnerUid: 10196
connectivity: no VPN CONNECTED / InterfaceName: tun* network
```

This leaves the device in the required prepared state: consent granted for the current install, but
no active tunnel.

## Test

Prepared-device direct instrumentation command:

```bash
adb shell am instrument -w -r \
  -e class 'org.adaway.vpn.VpnLifecycleInstrumentedTest#startStopResumeEstablishesTunnelStatusTunAndHeartbeatWhenVpnConsentExists' \
  org.adaway.test/androidx.test.runner.AndroidJUnitRunner
```

Result:

```text
INSTRUMENTATION_STATUS: class=org.adaway.vpn.VpnLifecycleInstrumentedTest
INSTRUMENTATION_STATUS: test=startStopResumeEstablishesTunnelStatusTunAndHeartbeatWhenVpnConsentExists
INSTRUMENTATION_STATUS_CODE: 0
Time: 2.788
OK (1 test)
```

Supporting logcat from the passing direct instrumentation run:

```text
VpnServiceControls.start() called
Processing START command
VPN service started.
Established by org.adaway on tun0
VPN established.
Connection monitor initialized to watch interface tun0.
Processing STOP command
VPN service stopped.
VpnServiceControls.start() called
Processing START command
VPN service started.
Established by org.adaway on tun0
VPN established.
Connection monitor initialized to watch interface tun0.
```

Post-run cleanup evidence:

```text
vpn_management: Active package name: org.adaway
vpn_management: Active vpn type: -1
connectivity: no VPN CONNECTED / InterfaceName: tun* network
```

## Focused Hygiene

The tracker and ordinary connected lifecycle class were rerun after recording the prepared-device
proof:

```bash
./gradlew --no-daemon :app:testDebugUnitTest \
  --tests org.adaway.tasks.UserStoryStatusTrackerTest \
  --dependency-verification=strict --stacktrace

./gradlew --no-daemon :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.vpn.VpnLifecycleInstrumentedTest \
  --dependency-verification=strict --stacktrace
```

Results:

```text
UserStoryStatusTrackerTest: BUILD SUCCESSFUL
VpnLifecycleInstrumentedTest: Starting 2 tests on adaway-api34-16g(AVD) - 14
VpnLifecycleInstrumentedTest: Finished 2 tests on adaway-api34-16g(AVD) - 14
VpnLifecycleInstrumentedTest: BUILD SUCCESSFUL
```

## Fix Captured By This Slice

The first full lifecycle run did not skip, but failed on:

```text
java.lang.AssertionError: Lifecycle logs must include a stop signal.
```

The service sets persisted `STOPPED` before the asynchronous stop log is observed by the test. The
test now waits for the lifecycle log signal instead of racing it.

## Boundary

This is a connected prepared-device proof. A Gradle `connectedDebugAndroidTest` invocation may run
against an unprepared install or reinstall the APK and change the app UID after consent is granted.
When that happens, `VpnService.prepare()` is non-null and the full lifecycle method correctly skips.
In the final local hygiene pass, the prepared consent survived the Gradle class run and both
`VpnLifecycleInstrumentedTest` methods passed with no skips. Physical-device release smoke remains
tracked separately under `REL-003`.
