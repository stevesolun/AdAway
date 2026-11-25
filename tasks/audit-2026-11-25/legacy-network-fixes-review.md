# Legacy Network Repair Handoff

Date: 2026-11-25. Branch: `codex/full-codebase-audit`.
Status: **source frozen; implemented-awaiting-main-build-and-runtime**.
NOV-VPN-04 is not closed by source inspection or synthetic callbacks.

## Scope And Freeze

The approved design is `legacy-network-design.md`. This lane changed exactly five
production files and five network test files, all unstaged, plus these four
`legacy-network-fixes-*` sidecars. No other application files were edited.
No Gradle, adb, compilation, test execution, staging, commit or device operation
was performed here. Main owns the combined APK and API30/API34 execution.

The first freeze was briefly reopened at main's explicit request to handle the
API34 foreground failure. The final source state includes that correction and the
updated source guard. No further source edits are planned by this lane.

Staged binary-diff SHA256 at entry and after implementation:
`d476c380cb1cbca45e03d8398c7464bef5186e646b859904328e296b34244a7a`.
The index still matched this baseline at the initial source freeze. The only
unstaged Manifest addition is
CHANGE_NETWORK_STATE with maxSdkVersion30. Existing REQUEST_DELETE_PACKAGES stays.
Other concurrently edited files were left alone.

At final handoff the global staged diff advanced concurrently to
`4592c15aab1f132d95b2bb0fde6d751ed221dd0b3aef955b3f6ed09f143a1b62`.
This lane issued no staging/index-changing operation. The scoped cached diff
still contains only the prior one-line Manifest addition; all this lane's changes
remain unstaged. All ten frozen working-file hashes still match the file ledger.

Main reported the previous staged APK passed seven phase2 live preference,
lifecycle and DNS tests. Those passes do not validate these phase3 edits.
Main reported API30 `emulator-5558` and API34 `emulator-5556` available; this lane
has not contacted either device. API30 VPN consent was not yet granted at handoff.

## Implementation

- API26/27 retain the existing all-network observer and direct-default identity
  callback. API31+ retain the existing passive best-matching path.
- API28-30 register exactly one no-timeout INTERNET/NOT_VPN request. There is no
  passive observer/default registration in this mode and no scoring fallback.
  Its callback owns winner identity, immutable DNS inputs, capabilities and
  blocked readiness. API29/30 wait for the initial blocked payload; API28 does not.
- Replacement availability switches identity without waiting for old-network loss.
  A new identity needs fresh payloads. Duplicates preserve readiness; stale
  payload/loss events cannot overwrite a different winner. Empty DNS, loss,
  blocked status or unavailable request selects no upstream, not a guessed one.
- The service registers only after START requests operation and foreground entry
  succeeds. Creation alone registers nothing. Repeated START reuses the current
  registration; reconnect and WAIT retain it. STOP closes and unregisters before
  stopSelf, including while bound. A later START receives a new monitor generation.
- Partial registration failure retires the monitor and attempts release for every
  attempted callback. Unregistration continues after individual release failures,
  aggregates exceptions, and leaves every local callback retired. The original
  registration error remains primary. Cleanup failures are logged by the service.
- Current-worker status/recovery guards, desired-state dispatch guard, 100 ms
  coalescing and protected/bound DNS and watchdog paths remain intact.
- Builder underlying-network metadata uses the same constructor Network as the
  worker's DNS mapper. It is attached before establishing that worker's tunnel;
  no asynchronous/global setUnderlyingNetworks call can separately stamp a newer
  worker's tunnel. Null input is rejected instead of declaring system-default use.

### Foreground Failure Check

Main noticed the existing API34 foreground catch swallowed failure. The private
status helper now returns success/failure; START explicitly checks it before
registration. A false result throws into startVpn's existing catch, which calls
stopVpn and returns START_NOT_STICKY. API28-30 platform exceptions already reach
that catch. Other notification/status paths were not broadly refactored.
The source-level test checks this wiring; forced platform failure remains a
main-owned runtime test, not a pass claimed here.

## Exact Focused Tests

The owned five test files now contain **41 methods**: 24 added, 17 existing
methods retained/adapted. Of the 24 added methods, 22 are deterministic/source
checks and two are opt-in live callback fixtures. None was executed here.

### JVM

- `org.adaway.vpn.LegacyVpnNetworkContractTest` (5 new): parsed permission scope,
  create/start ordering and foreground failure gate, STOP/destroy cleanup wiring,
  fresh generations/current-worker guards, immutable underlying-network wiring.
- Rerun existing `org.adaway.vpn.worker.VpnWorkerNetworkBindingTest`,
  `org.adaway.vpn.worker.VpnWorkerSocketTest`, and
  `org.adaway.vpn.worker.VpnConnectionMonitorTest` for unchanged binding/stop paths.

### Deterministic Android

Run these classes on both API30 and API34 after compiling production and Android
test sources against the combined APK:

1. `org.adaway.vpn.LegacyVpnNetworkMonitorInstrumentedTest` (15 new).
2. `org.adaway.vpn.VpnNetworkMonitorInstrumentedTest` (14 adapted existing).
3. `org.adaway.vpn.VpnServiceNetworkCallbackInstrumentedTest` (5; 2 added).
4. `org.adaway.vpn.VpnServiceRuntimeStateTest` (3 existing, untouched).
5. `org.adaway.vpn.dns.DnsServerMapperConfigurationInstrumentedTest` (10 existing,
   untouched).

That is 47 deterministic Android methods per device. No live opt-in arguments,
VPN consent or radio manipulation are needed for these five classes. Registration
tests use the actual monitor's registration boundary and a recording adapter to
exercise API26/27/28/29/30/31/34 decisions. This is not real framework behavior on
each simulated release. Old passive tests now choose their intended mode explicitly
instead of accidentally depending on the host device API. The old gap test remains
an information-limit fixture, not a claim that the repaired production path fails.

### Real Callback Smoke

Class: `org.adaway.vpn.LegacyVpnNetworkCallbackLiveInstrumentedTest`.

- `realRegistrationDeliversAReadyNonVpnSelection`: set
  **adawayLiveNetworkCallbacks=true**. Requires a real usable internet network
  with DNS and the newly merged normal permission on API28-30. VPN consent is
  unnecessary. Uses production registration with real framework payloads; allows
  20 seconds for readiness. It does not start a VPN or transmit test traffic.
- `realSameTransportReplacementWithOldNetworkStillReportedAndVpnUnchanged`:
  additionally set **adawayLiveNetworkHandoff=true**. Prepare an active VPN
  covering the test application's UID externally, preferably AdAway for this
  regression. After the initial selection log, the lab must produce a distinct
  same-transport Network winner within 120 seconds while keeping A and the VPN
  alive. A roam keeping the same handle does not qualify. The test observes real
  callbacks and old-network presence; it does not manufacture Networks or scores.

The smoke method requires only the first flag. With only that flag the handoff
method skips. With no flags both skip. The handoff fixture does not grant consent,
toggle radios, replace another VPN or start/stop AdAway. Both unregister their own
callbacks on completion. On API28-30 they temporarily create an additional real
active request: do not run them concurrently with production STOP/power-retention
measurements, because the fixture's own request changes those measurements.

### Production Lifecycle And Traffic

After preparing API30 consent, rerun the applicable existing
`org.adaway.vpn.VpnLifecycleInstrumentedTest` and
`org.adaway.vpn.VpnDnsTunnelInstrumentedTest` live gates with
**adawayLiveVpnSmoke=true**. Start from AdAway stopped, no unrelated VPN, consent
pre-granted, usable network/DNS, and initialized app data/USER source for the DNS
test. Preserve main's existing preference restoration/cleanup procedure.

Also prove intentional STOP while the service remains bound releases the actual
request; a create/bind-only service never requests a network; failed foreground
entry never registers/starts; and an old callback/worker cannot revive a stopped
or replacement run. Source guards and fake registrations do not prove those
framework service conditions.

The decisive routing gate remains a real A -> B same-transport handoff with A
alive, watchdog off, A resolver silent and B resolver uniquely answering. Send
fresh DNS through the actual AdAway TUN and independently capture B egress;
include identical resolver IPs on isolated A/B paths. Underlying metadata and
callback selection alone are not proof of packet routing or resolver success.

## Checks And Limits

Performed here: focused git diff --check, owned-file trailing-whitespace checks,
parsed manifest validation, API call-site/source review, test-method counts and
staged-content hash comparison. These are static checks, not Java compilation,
lint, test passes, installation or runtime results. Source hashes are in the file
sidecar and identify the final frozen bytes.

No exact-system-default guarantee or zero-power-cost claim is made. Request
eligibility is UID/policy-sensitive; Data Saver and OEM behavior remain runtime
gates. API26/27 and API28/29 source paths are retained but need actual-device proof;
API30/34 results cannot be automatically assigned to those releases.

An IPC failure during unregister cannot prove the system released its resource;
the implementation guarantees all local callbacks are retired and every release
is attempted, not success after arbitrary platform failure. Loss/no usable winner
uses the existing WAIT behavior, which stops the worker; this is not a new
fail-closed VPN guarantee. Existing late tunnel-establishment/worker lifecycle
risks remain main-owned; no shared controller redesign was attempted.

No central bug ledger was changed. Keep NOV-VPN-04 implemented-awaiting-test until
main's build, framework callback, service lifetime and actual traffic evidence is
recorded separately. Review the sidecar limits before changing closure language.
