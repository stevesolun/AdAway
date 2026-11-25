# RUNTIME-009 VPN Lifecycle Boundary - 2026-06-27

Scope:
- Story: `RUNTIME-009`
- Device: `adaway-api34-16g`
- Boundary: automated monitor/watchdog proof is local; the full live tunnel start/stop/resume path
  needs AdAway VPN consent already granted and no other active VPN.

Automated proof already landed:

```bash
./gradlew --no-daemon --no-build-cache :app:testDebugUnitTest \
  --tests org.adaway.vpn.worker.VpnConnectionMonitorTest \
  --tests org.adaway.vpn.worker.VpnWatchdogTest \
  --tests org.adaway.vpn.worker.VpnWorkerIdleTimeoutTest \
  --dependency-verification=strict --stacktrace
```

Result:
- `VpnConnectionMonitor.reset()` re-arms the monitor after recovery stop.
- Watchdog restart behavior remains covered.
- Idle timeout behavior remains covered.

Recorded connected live-method invocation (June 27 outcome not established here):

```bash
./gradlew --no-daemon --no-build-cache :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.vpn.VpnLifecycleInstrumentedTest#startStopResumeEstablishesTunnelStatusTunAndHeartbeatWhenVpnConsentExists \
  --dependency-verification=strict --stacktrace
```

Historical result attribution, corrected 2026-11-25 (REM-EVID-003):
- The original narrative reports that the lifecycle class passed its heartbeat start/stop
  proof on `adaway-api34-16g`. Its exact heartbeat invocation and matching result artifact
  are not recorded in this document. The command above selects the live method, so it
  must not be used as the invocation for that separate heartbeat claim.
- This June 27 record does not establish the displayed live invocation's pass, fail or
  skip outcome. Its precondition description is not an observed skip count.
- The live method is intentionally precondition-gated: absent prepared consent or an
  already active VPN can cause a skip. No Android consent UI execution is claimed here.

Subsequent historical proof:
- The [June 28 prepared-device follow-up](2026-06-28-runtime009-prepared-vpn-lifecycle-evidence.md)
  records the exact direct live-method invocation, instrumentation status `0`, `OK (1 test)`,
  start/stop/resume tunnel logs and post-run cleanup. That is a separate prepared-device
  result, not a retroactive outcome for the command above or proof of the current revision.

Release interpretation:
- This document retains the June 27 evidence boundary and points to the later prepared-device proof.
- It does not close physical/manual VPN lifecycle smoke.
- Current release decisions still need evidence tied to the intended artifact and device;
  the historical emulator result does not replace physical-device release smoke under `REL-003`.
