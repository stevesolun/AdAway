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

Connected proof already landed:

```bash
./gradlew --no-daemon --no-build-cache :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.vpn.VpnLifecycleInstrumentedTest#startStopResumeEstablishesTunnelStatusTunAndHeartbeatWhenVpnConsentExists \
  --dependency-verification=strict --stacktrace
```

Result:
- The connected lifecycle class passed its heartbeat start/stop proof on `adaway-api34-16g`.
- The full start/stop/resume live tunnel method is intentionally precondition-gated.
- When VPN consent is not already granted, or another VPN is active, the method skips instead of
  pretending that Android consent UI can be automated safely.

Release interpretation:
- This closes the local documentation slice for `RUNTIME-009`.
- It does not close physical/manual VPN lifecycle smoke.
- Final release signoff still needs a prepared device where AdAway VPN consent is granted and the
  live tunnel can be started, stopped, resumed, and observed through `tun0`/heartbeat evidence.
