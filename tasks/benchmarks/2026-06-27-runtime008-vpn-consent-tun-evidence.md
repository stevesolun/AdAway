# RUNTIME-008 Fresh VPN Consent/TUN Smoke - 2026-06-27

PR base at start of slice: `b132bf71 perf: stage root export during source import`

Environment:
- JDK: Temurin 21
- SDK: `/Users/steves/.local/android-sdk`
- Device: `adaway-api34-16g(AVD) - 14`
- Flow reset: `adb uninstall org.adaway`, then fresh debug install

Commands:

```bash
./gradlew --no-daemon --no-build-cache :app:testDebugUnitTest \
  --tests org.adaway.ui.onboarding.OnboardingFirstRunContractTest \
  --tests org.adaway.ui.home.HomeNavigationSourcesContractTest \
  --dependency-verification=strict --stacktrace

./gradlew --no-daemon --no-build-cache :app:installDebug \
  --dependency-verification=strict --stacktrace

adb shell am start -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER \
  -n org.adaway/.ui.home.HomeActivity
adb shell input tap 540 1754
adb shell input tap 894 1520
adb shell dumpsys vpn_management
adb shell dumpsys connectivity
adb logcat -d -v time
```

Raw artifacts:
- `tasks/benchmarks/2026-06-27-runtime008-vpn-smoke-final-postfix-install.out.log`
- `tasks/benchmarks/2026-06-27-runtime008-vpn-smoke-final-postfix-install.err.log`
- `tasks/benchmarks/2026-06-27-runtime008-vpn-smoke-final-postfix-onboarding-before.xml`
- `tasks/benchmarks/2026-06-27-runtime008-vpn-smoke-final-postfix-consent.xml`
- `tasks/benchmarks/2026-06-27-runtime008-vpn-smoke-final-postfix-home-after.xml`
- `tasks/benchmarks/2026-06-27-runtime008-vpn-smoke-final-postfix-dumpsys-vpn.txt`
- `tasks/benchmarks/2026-06-27-runtime008-vpn-smoke-final-postfix-dumpsys-connectivity.txt`
- `tasks/benchmarks/2026-06-27-runtime008-vpn-smoke-final-postfix-logcat.txt`

Result:

```text
Focused JVM contracts: BUILD SUCCESSFUL in 5s
Install: BUILD SUCCESSFUL
Consent dialog: "Connection request", "AdAway wants", OK button present
Home after consent: "Protection active"
Leak status after refresh: "Protection method: VPN running"
dumpsys vpn_management: Active package name: org.adaway
dumpsys connectivity: NetworkAgentInfo ... VPN CONNECTED ... InterfaceName: tun0
dumpsys connectivity: VpnTransportInfo{type=1, sessionId=AdAway, bypassable=false}
logcat: VpnServiceControls.start(), Processing START, VPN established, status RUNNING
```

The smoke originally exposed a first-run defect: accepted Android VPN consent saved VPN mode and
launched Home without applying the VPN model, leaving the tunnel stopped and/or Home status stale.
The verified fix applies VPN protection through the shared `AdBlockModel` after accepted consent and
resamples Home leak status during the post-establish window. Fresh API 34 evidence now proves the
consent path creates a real AdAway-owned `tun0` VPN and Home reports active protection.

This closes the emulator-backed RUNTIME-008 consent/TUN proof. Physical-device VPN smoke remains a
release-smoke concern under REL-003.
