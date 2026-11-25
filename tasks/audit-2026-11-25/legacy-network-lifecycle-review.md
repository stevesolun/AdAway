# Bound Legacy Request Lifecycle Handoff

Date: 2026-11-25. Branch: `codex/full-codebase-audit`.
Status: **final freeze; actual API30 three passed, zero skips; scoped lifecycle evidence observed**.

## Scope And Freeze

This follow-up adds only
`app/src/androidTest/java/org/adaway/vpn/LegacyVpnBoundRequestLifecycleInstrumentedTest.java`
and these three `legacy-network-lifecycle-*` sidecars. One live method plus two
parser methods. No production edits, Gradle, adb, compilation, staging, commit,
or device operations by this lane. The five frozen production hashes still match
`legacy-network-fixes-files.csv`. The new source SHA256 is
`966575b4768f2de96402400836a0b35507d6b9b6dd35a9464a514c85636aa82d`.
Tracked `git diff --check` passed at source freeze; compilation and execution are
main-owned. This result update changes only the three lifecycle sidecars. The
test source and all five production hashes were rechecked and remain unchanged.

## Exact Run Contract

- Class: `org.adaway.vpn.LegacyVpnBoundRequestLifecycleInstrumentedTest`.
- Argument: `adawayLiveBoundNetworkLifecycle=true`.
- Live method:
  `bindStartRepeatStopWhileBoundRestartAndUnbindOwnExactlyOneRequest`.
- The live method requires actual API30/Android11; absent opt-in or another API
  skips it. Run the class alone, without another network fixture or UI operation.
- Start with STOPPED preferences, no AdAway service, no VPN of any owner,
  pre-granted AdAway VPN consent, and no unfinished `vpnHeartbeat` work. These
  safety preconditions skip rather than disturb another run. A skipped live method
  is not lifecycle proof; every run must confirm three passes and zero skips.
- Supply usable non-VPN INTERNET connectivity and writable target external files.
  The current own-UID REQUEST baseline must be zero; an unrelated active request
  is a failing isolation condition, not a value silently subtracted away.
- The instrumented process must share the target application's UID. The test
  temporarily selects VPN mode and restores the prior method in cleanup.

The held connection uses an explicit component plus public `SERVICE_INTERFACE`
and `BIND_AUTO_CREATE`. It does not transact the framework binder or reach into
the monitor. Owned `VpnServiceControls` deliver START and STOP; repeated START
uses `restart()` so the existing-running short circuit cannot manufacture a pass.
The existing Timber start message acknowledges delivery only, not system ownership.

## Evidence And Assertions

The sampled own-UID REQUEST counts enforced by the passing fixture and recorded
in the retained phase summary are:

| Phase | Count | Additional assertion |
| --- | --- | --- |
| Baseline | 0 | Service absent; no VPN |
| Bind/create only | 0 | Bound, not started or foreground |
| START | 1 | Started, foreground, RUNNING and VPN visible |
| Repeated START | 1 | Same request ID; second START acknowledged |
| STOP while bound | 0 | Same service creation time; still bound; no VPN |
| Restart while bound | 1 | Fresh request ID; same service creation time |
| Final STOP while bound | 0 | Same service remains bound; no VPN |
| Final unbind | 0 | Service absent; no VPN |

Each phase needs matching samples spanning at least one second, with a 20-second
convergence deadline. The read command is `dumpsys -t 5 connectivity requests`,
executed through public `UiAutomation.executeShellCommand`, not adb or hidden API
reflection. Reads have an eight-second deadline and a one-MiB cap. This is a
test-only privileged diagnostic surface, not an application API dependency.

Android11 AOSP's `requests` dump selects the current request table, whose rows
include owner UID/PID; this avoids per-network copies and historical events.
The parser accepts only recognized complete rows, rejects duplicate IDs and
another process's own-UID REQUEST, and requires a system-owned REQUEST positive
control. It counts only exact REQUEST type owned by the target UID; LISTEN,
TRACK_DEFAULT, BACKGROUND_REQUEST and other owners are excluded. The sole running
request must include INTERNET, NOT_VPN and the target requestor package.
[ConnectivityService dump and request owner format](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-11.0.0_r1/services/core/java/com/android/server/ConnectivityService.java),
[NetworkRequest type and text format](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-11.0.0_r1/core/java/android/net/NetworkRequest.java).

The public running-service information supplies started/foreground, UID/PID and
creation time; the held binder must remain alive without reconnection.
[RunningServiceInfo](https://developer.android.com/reference/android/app/ActivityManager.RunningServiceInfo),
[UiAutomation shell execution](https://developer.android.com/reference/android/app/UiAutomation#executeShellCommand(java.lang.String)).

Log tag `LegacyVpnBoundLifecycle` gives the evidence directory under the target
app's external files directory: `legacy-bound-network-<timestamp>`. `summary.txt`
records every phase sample; `<phase>.txt` retains the most recent raw request dump
for each phase. Unknown or incomplete dump text fails with that path, never an
empty count. If the device's dump differs, main can inspect these artifacts; no
automatic alternative parser or heuristic fallback is provided.

Cleanup attempts STOP for this run's surviving started service, always releases
its own binding, cancels owned heartbeat work, waits for destruction, restores the
prior method, removes its Timber tree and closes the shell reader. Pre-existing
service/VPN state is never intentionally stopped.

## Results And Limits

Main reported the previous combined build: 711 host tests, three skipped, zero
failures; lint 37 warnings, zero errors. Main reported actual API30: 55 passed,
zero skips (47 deterministic, one real registration, three live preferences,
three lifecycle, one DNS). The existing log
`/tmp/adaway-audit-phase-three-api30.log` ends `OK (55 tests)`.

Those results predate this new fixture. Saved app SHA256:
`e25c957e6a9c57d5e957147f5f1c8f439cc8e057382dfb96ec9463aff0509a4b`;
saved test APK SHA256:
`9f1c191963a3cfa35cc32f2d672183ba9f88246d393c759aa299d3a19392ce02`.
Main subsequently reported phase3 actual API34 callback/preferences/lifecycle/DNS:
eight passed, zero skips. That separate result is not API34 execution of this
API30-only fixture.

For phase4, main ran the new class on actual API30. This lane inspected
`/tmp/adaway-audit-phase-four-api30-bound.log`: all three named methods finish with
status code zero, no skips, and `OK (3 tests)` in 12.685 seconds. This covers one
real framework lifecycle method and two synthetic parser methods. Main supplied
the phase4 saved APK hashes:

- App: `cbc4d94237a0c5d884bb0c4592691bc9f0d55d8dd5c65e841b3e070f8d626376`.
- Test: `2de33ded07e1092d0cdb76d69e6f41982c7b57f912239a87cd32f0f341c393bc`.

This lane also inspected the retained system-log summary at
`app/build/reports/audit-2026-11-25/phase-four-api30-bound-summary.log`.
It records UID10166, the sampled sequence `0/0/1/1/0/1/0/0`, request ID56 across
START and repeated START, and fresh ID58 after STOP/restart. Service creation
time `1659045` remains unchanged throughout the bound sequence. STOP phases show
not-started/not-foreground with the binding retained; final unbind shows the
service absent. The summary includes the fixture's final PASS line. These are
fixture log summaries of parsed framework dumps, not archived raw dump rows.

The test recorded raw dump files on the emulator under
`/storage/emulated/0/Android/data/org.adaway/files/legacy-bound-network-1795607139162`.
Main reports shell and run-as export were both denied by scoped storage, with
failed-export text preserved. No host raw-dump archive or independent inspection
of those raw files is claimed. This lane did not attempt export or bypass. The
successful assertions plus retained phase summary support only the scoped
sampled lifecycle result, with no broader claim based on unavailable raw exports.

This is bounded sampled registration-lifetime evidence, not a continuous trace
that excludes every transient request between samples. Each phase passes only
after matching samples span at least one second; it does not establish that no
brief request appeared earlier or between observations. The dump text contract
is API30-specific and may fail on a divergent device. This passing result does
not prove packet routing, DNS destinations, same-transport winner changes or
same-transport routing handoff, exact system-default identity, zero power cost,
or forced foreground-failure cleanup. No API26-29 runtime proof is supplied:
API26/27 direct-default behavior and API28/29 request behavior remain outside
this actual API30 run. The result must not be extrapolated to those releases.
Synthetic parser inputs and the older virtual-network callback tests are not
real routing evidence. NOV-VPN-04's remaining real handoff boundary stays explicit.
