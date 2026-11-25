# VPN Network Fixes - 2026-11-25

## Status And Ownership

Branch: `codex/full-codebase-audit`; HEAD `d51eb7aea06871c2c6ad2bbedb9ddbaaaca48d77`.
Read CLAUDE.md, tasks/lessons.md, vpn-bugs.csv and the relevant vpn-review.md
findings. No repository/ancestor AGENTS.md was found. The bounded assignment
overrides general requests to edit tasks/todo.md or run builds. All other dirty
work was preserved. No Gradle, emulator, adb, compilation, product test execution,
git staging, commit, push, reset, stash or checkout by this lane.

Main subsequently authorized VpnService callback/network tracking, VpnWorker
integration and VpnServiceRuntimeStateTest. The pre-existing service command
decoder/start dispatch was preserved. Command.java, VpnModel.java and the
main-owned NOV-VPN-06 cleanup in DnsPacketProxyRuntimeTruthTest were not edited.
VpnConnectionMonitor's current-worker-tagged recovery path is unchanged.

- [x] Confirm NOV-VPN-03/04/05 against current source and platform contracts.
- [x] Implement identity tracking, resolver snapshots and socket binding.
- [x] Decouple provider IPv6 routes from upstream resolver family.
- [x] Add bounded tests, including an explicit unresolved pre31 handoff gap test.
- [x] Static diff/whitespace checks and SHA256 review handoff.
- [ ] Main-owned final compile, executable tests and device gates.

NOV-VPN-03 and NOV-VPN-05: **implemented-awaiting-test**.
NOV-VPN-04: **partially-implemented**, with pre-API31 same-transport default
handoff still open when the application's default remains a VPN. No universal
closure. Source/test ownership returned to main after the public API fixture fix.

## NOV-VPN-03 - Network Identity

Confirmed baseline: callbacks collapsed Network identities into transport types.
WiFi A available, B available, A lost removed WIFI and stopped filtering despite B.

The new VpnNetworkMonitor keeps each Network independently and consumes ordered
capability/link-property payloads on the service handler. Its request observes
INTERNET and NOT_VPN networks, including Ethernet. A usable selection needs
non-VPN internet capabilities, DNS, and no reported blocked state. Duplicate
availability preserves state; unknown/duplicate loss is a no-op; post-loss
property callbacks cannot recreate a removed entry. Only final usable loss
selects null. A network with no DNS is not a usable upstream.

Startup waits for callback data instead of seeding an identity-free transport.
START enters the existing foreground path before waiting. Eligible callback data
then starts the worker. Registrations are released on destruction; a closed
monitor ignores all callback paths. Android specifies per-Network loss and
ordered capability/link callbacks on API26+:
[NetworkCallback contract](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback).
No synchronous ConnectivityManager property reads occur inside callbacks.

## NOV-VPN-04 - DNS Refresh And Binding

Confirmed baseline: resolver snapshot established once; no DNS/default callbacks;
protected sockets not bound to their resolver's Network.

The monitor emits only changes to selected Network or ordered DNS list.
Equivalent payloads do not rebuild. The service coalesces selection bursts for
100 ms and checks vpnRequested again on dispatch. STOP/destruction remove pending
work; surviving callbacks remain guarded. Existing worker identity checks for
reconnect/status messages are preserved.

Each worker owns an immutable Network/DNS snapshot. The mapper no longer
independently discovers a potentially different upstream during establishment.
Both forwarding overloads protect, bind, connect to the resolver, then send.
Binding failure closes the socket, never falls back to an unbound socket.
Watchdog sockets use the same protected, bound network. Existing worker teardown
retires query queues. Android requires binding before connect:
[Network.bindSocket](https://developer.android.com/reference/android/net/Network#bindSocket(java.net.DatagramSocket)).

API31+ uses the passive
[best-matching callback](https://developer.android.com/reference/android/net/ConnectivityManager#registerBestMatchingNetworkCallback(android.net.NetworkRequest,%20android.net.ConnectivityManager.NetworkCallback,%20android.os.Handler))
for the non-VPN internet request. No network is actively requested/brought up.
A directly exposed physical app-default Network is also honored.
[Default callback documentation](https://developer.android.com/reference/android/net/ConnectivityManager#registerDefaultNetworkCallback(android.net.ConnectivityManager.NetworkCallback,%20android.os.Handler))
explicitly allows the app's default to be a VPN instead.

### Pre-API31 Same-Transport Gap

Before API31 there is no best-matching callback here. If app-default remains a
VPN, a WiFi transport hint cannot distinguish overlapping WiFi A and B after an
underlying-default handoff. The fallback uses matching transport/validation and
retains the existing candidate on a tie. Availability order is not treated as
evidence of a new default identity. Selection may stay A even after the platform
moves its physical default to B. Binding keeps resolver traffic coherent with A,
but cannot prove A is preferred or still answers DNS.

The test `legacyVpnTransportHintCannotResolveSameTransportDefaultHandoff`
asserts this unchanged-A outcome. It is a **known-gap test**, not successful
handoff evidence. It simulates the legacy path by never supplying a best-network
event. NOV-VPN-04 remains partial. Do not extrapolate API34 best-matching results
to API26-30. Different-transport hints, directly exposed physical defaults,
same-Network DNS changes and loss of A with B retained have separate coverage.
No additional identity heuristic was introduced to pretend this gap is closed.

## NOV-VPN-05 - IPv6 Family And Preference Check

Confirmed baseline: IPv6 provider routes were gated on an IPv6 DNS alias subnet.
IPv4-only DNS therefore omitted all eight listed provider /128 routes. The mapper
now always installs both provider families. Alias selection remains unchanged:
preference false omits IPv6 aliases for mixed DNS, but retains IPv6-only resolvers.
No IPv6 default route or extra IPv6 alias/interface is introduced for IPv4 DNS.

Android normally blocks a family if the VPN has no address, route or DNS for it.
Adding a route implicitly permits that family, even without an interface address.
But this app's VpnBuilder **already unconditionally calls allowFamily(AF_INET6)**,
including enableIpv6=false. Thus the baseline did not rely on Android's implicit
IPv6-family block. This change does not newly enable ordinary IPv6 fall-through;
it was already expressly allowed. Only the listed provider /128 destinations
gain capture routes; ordinary IPv6 remains outside those exact routes. See
[addRoute](https://developer.android.com/reference/android/net/VpnService.Builder#addRoute(java.net.InetAddress,%20int))
and [allowFamily](https://developer.android.com/reference/android/net/VpnService.Builder#allowFamily(int)).
The preference remains an alias/upstream-family preference in this VPN path, not
a global IPv6 kill switch. Device forwarding/fallback still requires proof.

Main reported fresh onboarding on its 12:08 APK (earlier mapper-only revision):
tun0 CONNECTED/VALIDATED, IPv4 alias 192.0.2.2 and all eight provider IPv6 /128
routes with IPv4-only DNS. Main owns sanitized proof in its records. This is
main-reported route-installation evidence, not this lane's test execution, not
proof for the later callback edits, and not IPv6 reachability or DoH fallback.

## Exact Changed Paths

Root: `/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway`.
The following root-relative paths are this lane's exact file set:

- `app/src/main/java/org/adaway/vpn/VpnService.java` (network integration only).
- `app/src/main/java/org/adaway/vpn/VpnNetworkMonitor.java` (NEW).
- `app/src/main/java/org/adaway/vpn/dns/DnsServerMapper.java`.
- `app/src/main/java/org/adaway/vpn/worker/VpnWorker.java`.
- `app/src/androidTest/java/org/adaway/vpn/VpnServiceRuntimeStateTest.java` (one removed-helper reflection call replaced).
- `app/src/androidTest/java/org/adaway/vpn/VpnNetworkMonitorInstrumentedTest.java` (NEW).
- `app/src/androidTest/java/org/adaway/vpn/VpnServiceNetworkCallbackInstrumentedTest.java` (NEW).
- `app/src/androidTest/java/org/adaway/vpn/dns/DnsServerMapperConfigurationInstrumentedTest.java` (NEW).
- `app/src/test/java/org/adaway/vpn/worker/VpnWorkerNetworkBindingTest.java` (NEW).
- `tasks/audit-2026-11-25/network-fixes-review.md` (NEW).
- `tasks/audit-2026-11-25/network-fixes-files.csv` (NEW).
- `tasks/audit-2026-11-25/network-fixes-bugs.csv` (NEW).

No central/base CSV/docs or other existing modified tests were edited.
Preference searches are partial context, not full-file review coverage.

## Tests And Main Handoff

Thirty-one NEW methods after main's integration correction, none executed by this lane:

- VpnNetworkMonitorInstrumentedTest (14): overlap/final loss, duplicates,
  unselected loss, defensive DNS replacement, equivalent callbacks, physical
  default with identical DNS IP, legacy different-transport switch, best-network
  switch, explicit same-transport legacy gap, late properties, close, readiness/
  VPN exclusion/Ethernet, blocked/unblocked fallback, and public empty framework payload mapping.
- VpnServiceNetworkCallbackInstrumentedTest (3): real-handler burst coalescing,
  queued/late desired-state stop guard, and overlapping loss/final wait.
  A recording subclass intercepts reconnect/wait instead of establishing a TUN.
- DnsServerMapperConfigurationInstrumentedTest (10): six resolver/preference
  combinations, empty DNS, replacement cleanup, public entry point's defensive
  snapshot, and absent selected Network rejects binding. Builder calls retain
  actual Android validation; no preferences are mutated or tunnel established.
- VpnWorkerNetworkBindingTest (4): protect-bind-connect order, binding failure
  closes, protection failure skips binding, runtime binding failure closes.
  The binder is a test double, not kernel routing proof.

Main's Android compilation disproved the proposed fixture API: neither mutation
methods nor NetworkCapabilities.Builder is in the public compileSdk36 image.
The monitor now extracts an immutable capability snapshot through public
hasCapability/hasTransport methods. Synthetic selection and service-handler tests
feed that snapshot, not fabricated framework capability objects. An additional
test sends a real empty NetworkCapabilities through both callback paths and checks
mapping/eligibility. There is no SDK suppression and no hidden-API reflection.
Positive framework callback delivery remains a live-service gate, not proven by
synthetic snapshots. Synthetic Network handles test identity only and are never
used to send traffic. Run API30 for legacy selection and API34+ for best-matching
selection; separate real network/device coverage is still needed on API26/28.

Suggested commands for main, NOT executed here:

```sh
./gradlew :app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
./gradlew :app:testDebugUnitTest --tests org.adaway.vpn.worker.VpnWorkerNetworkBindingTest --tests org.adaway.vpn.worker.VpnWorkerSocketTest --tests org.adaway.vpn.worker.VpnConnectionMonitorTest --tests org.adaway.vpn.dns.DnsServerMapperTest --tests org.adaway.vpn.dns.DnsServerMapperDohRoutesTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.vpn.VpnNetworkMonitorInstrumentedTest,org.adaway.vpn.VpnServiceNetworkCallbackInstrumentedTest,org.adaway.vpn.VpnServiceRuntimeStateTest,org.adaway.vpn.dns.DnsServerMapperConfigurationInstrumentedTest
```

Also rerun existing VpnLifecycleInstrumentedTest, VpnServiceCommandInstrumentedTest,
VpnDnsTunnelInstrumentedTest and preference-restart live gates with main's consent
and opt-in setup. Mapper/runtime-state suites retain lower-API coverage.

Live gates: same-Network DNS A-to-B with A silently dropping and watchdog off;
cellular/WiFi and same-transport overlap; final loss/recovery/intentional stop;
resolver IP unreachable on the other network; actual socket binding and queue
cleanup; provider /128 capture plus ordinary IPv6 with preference off and on.
Browser fallback and finite-provider-list coverage remain separate limitations.
Changes to ignored IPv6 DNS entries can cause an extra rebuild because raw ordered
resolver lists, not preference-filtered lists, are compared.

## Verification Record

Scoped git diff --check and new-file whitespace checks passed. The SHA256 ledger
contains 19 fully read files: 9 production, 9 tests, 1 build boundary. Hashes identify
reviewed bytes, not runtime success; main's later edits may make a row stale.

Main reported production compilation passed after replacement of unsupported
getTransportTypes with finite hasTransport checks. Two fixture revisions failed:
mutation methods and then the unavailable Builder. Main applied the immutable
snapshot correction above. Original lane hashes for the monitor and two callback
test files predate this correction and must not be imported as current review.
Final Android-test compilation and execution remain main-owned.
No compile/test pass is claimed from this lane. Blockers should be communicated
directly to main rather than only placed in audit prose.
