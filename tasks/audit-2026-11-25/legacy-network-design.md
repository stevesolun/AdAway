# NOV-VPN-04: Legacy Network Selection Design

Date: 2026-11-25. Branch: `codex/full-codebase-audit`.
Inspected HEAD: `f7dbbb5e7a8eca6983135f04adc945bab6518308`.

Architecture sidecar only. Read `CLAUDE.md`, `tasks/lessons.md`, the current
monitor, network review and relevant tests. Only this document was written.
No production/test edits, Gradle, adb, device operations, staging or integration.
Existing concurrent changes were preserved. Main owns implementation and runtime
proof. NOV-VPN-04 remains partial; this investigation does not close it.

## Recommendation

Use a platform-selected non-VPN upstream, not an inferred physical default:

- **API26-27:** retain `registerDefaultNetworkCallback`. The inspected Android
  8.0/8.1 implementations track the system default, excluding VPN networks.
  The existing direct-default identity path should handle the overlapping-network
  switch on these releases; it still needs a real test.
- **API28-30:** add one outstanding
  `requestNetwork(INTERNET + NOT_VPN, callback, serviceHandler)` while VPN service
  operation is requested. Use its current winner and callback payloads as the
  authoritative DNS upstream. Do not fall back to transport scoring in this mode.
- **API31+:** retain the existing passive best-matching registration. No legacy
  active request is needed there; this sidecar does not redesign that path.

`requestNetwork` is public throughout API26-30. Using it on all five releases is
a supported, slightly simpler branch, but redundant on stock API26-27 and adds
active-request effects there. Prefer the narrower API28-30 branch. The precise
historical distinction and sources are below.

The supported product contract is: **when Android changes the best non-VPN
network satisfying AdAway's request, adopt that Network and its DNS coherently,
even while the previous network remains connected and has the same transport.**
It is not: **always discover the exact system default beneath every active VPN.**
If that latter contract is non-negotiable on API28-30, retain an explicit platform
limitation. An active request is not a passive exact-default query.

## Current Evidence

The following references are relative to the repository root; line numbers and
hashes describe the inspected working files, not an executed test result.

| Location | Observation |
| --- | --- |
| `app/src/main/java/org/adaway/vpn/VpnNetworkMonitor.java:89` | Registers all matching networks and the default callback, but best-matching only on API31+. |
| `app/src/main/java/org/adaway/vpn/VpnNetworkMonitor.java:182` | Selects usable map entries through scores; a tied previous selection wins. |
| `app/src/main/java/org/adaway/vpn/VpnNetworkMonitor.java:206` | Best identity outranks direct default, then validation and VPN transport hints. The comment treating all pre31 releases alike is too broad. |
| `app/src/androidTest/java/org/adaway/vpn/VpnNetworkMonitorInstrumentedTest.java:152` | The known-gap test supplies a VPN default and two synthetic Wi-Fi networks, but no real platform selection event. It demonstrates missing information in that model, not that every API26-30 implementation hides the default. |
| `app/src/androidTest/java/org/adaway/vpn/VpnServiceNetworkCallbackInstrumentedTest.java:30` | Tests handler coalescing and stop guards using a recording service, not registration or forwarding. |
| `app/src/test/java/org/adaway/vpn/worker/VpnWorkerNetworkBindingTest.java:21` | Tests protect/bind/connect ordering with a fake binder. It does not establish kernel routing. |
| `app/src/main/java/org/adaway/vpn/VpnService.java:104` | Registers on service creation; unregisters on destruction. STOP sets desired state false and calls stopSelf, which is not necessarily immediate destruction of a bound service. |
| `app/src/main/AndroidManifest.xml:31` | Declares INTERNET and ACCESS_NETWORK_STATE, not CHANGE_NETWORK_STATE. |

The current tests use immutable capability fixtures and fabricated Network handles,
not the unavailable framework capability builder described in older CSV entries.
The latest `network-fixes-review.md` correctly records that fixture correction.

### Explanations Tested

| Candidate | Evidence and conclusion |
| --- | --- |
| Passive availability/transport hints suffice | Rejected for a VPN-covered UID on API28-30: A and B can both match, while the VPN's transport payload remains unchanged. Callback arrival order is not preference. |
| A public best-winner mechanism is unavailable before31 | Rejected: requestNetwork already exists and rematches requests to a better satisfying network. It is active, not passive. |
| The same request always equals the system default | Rejected as a universal contract: request eligibility is UID/policy dependent; it is not the system's default request. A concrete metered-policy divergence exists below. |

Confidence is high in the source/API distinction, not in unexecuted OEM routing
behavior. The decisive remaining probe is a real API28-30 request handoff with the
VPN still active, A still alive, and externally distinguishable DNS egress on B.

## Verified API26-30 Semantics

Primary sources were inspected at release tags, not inferred solely from today's
reference page. Android Gitiles was unavailable through the browser reader; the
AOSP-maintained `aosp-mirror/platform_frameworks_base` release-tag sources were
read instead. These are AOSP implementation observations, not certification of
every OEM image or backport.

### Version Boundary

| API / release source | Default callback with an active VPN | Active request evidence |
| --- | --- | --- |
| 26 / Android 8.0 | `registerDefaultNetworkCallback` passes null capabilities; ConnectivityService copies `mDefaultRequest.networkCapabilities`, which retain NOT_VPN. | Handler request overload exists; REQUEST and LISTEN use distinct registrations; request rematching can move to a higher-scored satisfier. [Manager](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-8.0.0_r1/core/java/android/net/ConnectivityManager.java), [service](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-8.0.0_r1/services/core/java/com/android/server/ConnectivityService.java). |
| 27 / Android 8.1 | Same system-default capability-copy path, including NOT_VPN. | Same Handler overload and score-based request migration. [Manager](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-8.1.0_r1/core/java/android/net/ConnectivityManager.java), [service](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-8.1.0_r1/services/core/java/com/android/server/ConnectivityService.java). |
| 28 / Android 9 | TRACK_DEFAULT instead uses `createDefaultNetworkCapabilitiesForUid`, removes NOT_VPN and sets the caller UID. A VPN applying to that UID can satisfy it. | Explicit NOT_VPN requests keep that capability and use best-request rematching. [Manager](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-9.0.0_r1/core/java/android/net/ConnectivityManager.java), [service](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-9.0.0_r1/services/core/java/com/android/server/ConnectivityService.java). |
| 29 / Android 10 | Same UID-scoped, VPN-eligible default path. | Handler overload and strict higher-score rematching remain. [Manager](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-10.0.0_r1/core/java/android/net/ConnectivityManager.java), [service](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-10.0.0_r1/services/core/java/com/android/server/ConnectivityService.java). |
| 30 / Android 11 | Same UID-scoped, VPN-eligible default path. | `computeNetworkReassignment` calls NetworkRanker for each non-LISTEN request; reassignment notifies the new winner. [Manager](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-11.0.0_r1/core/java/android/net/ConnectivityManager.java), [service](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-11.0.0_r1/services/core/java/com/android/server/ConnectivityService.java), [ranker](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-11.0.0_r1/services/core/java/com/android/server/connectivity/NetworkRanker.java). |

Useful source anchors in the downloaded text: API26 manager 3250/service 4041;
API27 manager 3297/service 4084; API28 service 934 and 4345; API29 service 1064
and 5108; API30 service 1171, 5510, 6854 and 6934. Some later manager comments
still say system default; the service's UID-scoped implementation is decisive.

### Request Versus Registration

`registerNetworkCallback` observes all matching networks; it supplies no ordering
of their desirability. `requestNetwork` selects a best match, can bring one up,
and maintains the request until release or process exit. A better match produces
`onAvailable(B)` without requiring A to disconnect. The Handler overload was
added in API26. API31 introduced `registerBestMatchingNetworkCallback` for observing
the best match without making this active request.
[ConnectivityManager public contracts](https://developer.android.com/reference/android/net/ConnectivityManager).

Do not wait for `onLost(A)` before accepting B. After replacement, a request
callback follows B, not every previously delivered network. Loss from an all-match
listener means leaving that listener's match set, not necessarily physical
disconnection. Capability/link payloads follow availability on API26+; consume
them instead of synchronous getters. `onBlockedStatusChanged` is public only from
API29, despite the modern overview grouping it with the initial callback sequence.
[NetworkCallback reference](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback).

The request should preserve Builder defaults, add INTERNET and explicitly retain
NOT_VPN, with no transport, network specifier, validation or metering constraint.
Defaults include NOT_RESTRICTED and TRUSTED as well as NOT_VPN; do not clear them.
In particular, removing NOT_RESTRICTED changes the permission requirements.
[API26 capabilities](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-8.0.0_r1/core/java/android/net/NetworkCapabilities.java),
[API30 capabilities](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-11.0.0_r1/core/java/android/net/NetworkCapabilities.java),
[API30 request builder](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-11.0.0_r1/core/java/android/net/NetworkRequest.java).

INTERNET does not mean validated connectivity or a working resolver. VALIDATED is
an observed state, not a supported mutable capability to put in this active
request. The Android 11 request documentation explicitly describes best-match
replacement and active network maintenance.
[Android 11 request contract](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-11.0.0_r1/core/java/android/net/ConnectivityManager.java).

### Permission And Power

Add `android.permission.CHANGE_NETWORK_STATE`, preferably with `maxSdkVersion=30`
if this remains its only use. It is a normal manifest permission, not a runtime
consent dialog, signature permission, root requirement or WRITE_SETTINGS grant.
Keep ACCESS_NETWORK_STATE and INTERNET. No location permission is needed for this
identity/capability/DNS design. Confirm the merged manifest when main integrates.
[Permission reference](https://developer.android.com/reference/android/Manifest.permission#CHANGE_NETWORK_STATE),
[Android 8 manifest](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-8.0.0_r1/core/res/AndroidManifest.xml),
[Android 11 manifest](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-11.0.0_r1/core/res/AndroidManifest.xml).

The permission also changes background-network eligibility. AOSP's
`listenForNetwork` adds FOREGROUND for callers lacking CHANGE_NETWORK_STATE;
adding the permission can expose background matches to the existing listener.
Do not interpret those additional callbacks as new defaults. Binding to background
networks is also permission-sensitive.
[API26 listen implementation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-8.0.0_r1/services/core/java/com/android/server/ConnectivityService.java),
[Android network-state guide](https://developer.android.com/develop/connectivity/network-ops/reading-network-state).

An active request can retain or bring up connectivity and therefore affect radio
power and metered use. A transport-neutral single request does not deliberately
pin both Wi-Fi and cellular, but zero incremental power cost cannot be promised.
Do not create requests per observed network or periodically request/unregister to
poll. Release on intentional stop, failure and destruction; keep it during genuine
WAITING_FOR_NETWORK while protection is requested, or automatic recovery is lost.
This lifetime tradeoff is intentional, and must be measured by main.
[Android 11 request lifetime and power contract](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-11.0.0_r1/core/java/android/net/ConnectivityManager.java).

### Exact-Default Limit

There is a concrete counterexample to treating the request as the system default:
`enforceMeteredApnPolicy` can add NOT_METERED to an ordinary UID's active request
when that UID is restricted on metered networks. The system default request is
not the same UID request. Thus a metered system-default B can be ineligible for
AdAway's request, even though both A and B satisfy the application's original
unconstrained INTERNET/NOT_VPN expression. API28-30 also constrain request UIDs.
[API28 policy](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-9.0.0_r1/services/core/java/com/android/server/ConnectivityService.java),
[API29 policy](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-10.0.0_r1/services/core/java/com/android/server/ConnectivityService.java),
[API30 policy](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-11.0.0_r1/services/core/java/com/android/server/ConnectivityService.java).

Inference from those sources: ordinary unrestricted requests often agree with the
system default, but agreement is conditional, not an API guarantee. Equal-score
incumbency on API26-29 and OEM policy are further reasons not to equate independently
matched requests. An unchanged request winner is not evidence of a missed callback
merely because some other notion of default changed.

For a VPN-covered UID on API28-30, the inspected public APIs offer no passive,
general exact-system-default identity observer that also excludes its VPN.
Do not substitute reflection, protected APIs, netId ordering, transport priority,
polling, VPN link-property similarity or application-wide self-exclusion. Excluding
AdAway from its VPN changes its traffic policy and is not a transparent monitor fix.

## Minimal Integration After Freeze

This is a specification for main, not an implementation performed here.

1. **Manifest:** add the normal permission above. Retain minSdk26 and current
   compile/public SDK boundaries. Do not introduce hidden capability mutation.
2. **Monitor registration:** on API28-30, start one no-timeout request using the
   same INTERNET/NOT_VPN request shape and existing service Handler. API26-27
   continue using direct-default identity; API31+ keep passive best matching.
3. **Authoritative request state:** give the legacy request callback its own
   current Network and reusable `State` shape. `onAvailable(B)` replaces identity
   immediately, resets readiness for a new identity, and does not await A loss.
   Consume capabilities, DNS and API29+ blocked status from this same callback.
   Publish only a ready, eligible current winner. Duplicate availability for the
   same current identity should not erase valid state. Ignore payloads/loss for
   other identities and callbacks belonging to a retired request generation.
4. **No heuristic fallback in request mode:** while the winner has not arrived,
   lacks DNS, is blocked or is lost, selection is unavailable rather than a
   transport-ranked alternate. The all-network map may remain for existing paths,
   but must neither override nor repopulate this request-owned selection. This
   avoids depending on ordering between independent callback registrations.
5. **Lifecycle:** acquire after START has set desired state and entered the existing
   foreground path, not merely in `onCreate`. Release explicitly on STOP, on
   startup/registration failure and in destruction. Make acquire/release idempotent;
   use a fresh callback generation on a later START. Do not tie release to worker
   restarts: the request must survive a DNS rebuild or temporary network loss.
   Preserve guarded 100 ms coalescing and current-worker checks. Clean up every
   successful registration even if a later registration/unregistration fails.
6. **Failure handling:** registration denial/resource failure must be visible to
   main's existing service error path and must not silently enable the old scoring
   algorithm. A no-timeout request waits for recovery; do not add short polling
   timeouts. API26-28 cannot rely on blocked callbacks to prove access. On API29-30,
   keep initial blocked readiness explicit rather than assuming false before the
   initial callback sequence completes.
7. **Worker boundary:** preserve the immutable Network/DNS snapshot and existing
   protect -> bind -> connect path for both DNS forwarding overloads and watchdog.
   No process-wide binding and no default-route fallback after binding failure.
   Confirm main's runtime handling when no winner is usable: today's
   `waitForNetVpn()` stops the worker. This note does not claim that state is a
   fail-closed VPN or redesign the critical tunnel lifecycle.

Expected production touch points: `AndroidManifest.xml`, `VpnNetworkMonitor.java`,
and narrow START/STOP registration hooks in `VpnService.java`. No DNS parser,
provider-route, native, database or build-system change is required for selection.
This is more than an `else requestNetwork(...)` insertion because lifetime and
selection authority differ from passive observation.

### Bound Upstream Metadata

Main should reconcile VPN underlying-network reporting with the actual bound
upstream. The inspected `worker/VpnBuilder.java` does not set underlying networks.
Android asks VPNs binding their upstream sockets to report those Networks through
Builder.setUnderlyingNetworks before establishment or VpnService.setUnderlyingNetworks
afterward. That is metadata about channels actually used, not a discovery API or
a substitute for binding. Leaving null declares use of the system default, which
must not be asserted merely because the chosen request resembles that default.
An empty array means no underlying connectivity, not automatic selection.
[VpnService underlying-network contract](https://developer.android.com/reference/android/net/VpnService#setUnderlyingNetworks(android.net.Network%5B%5D)).

Keep that runtime integration main-owned and snapshot/worker-generation consistent.
Do not use VPN transport callbacks derived from app-supplied metadata as independent
confirmation of the choice: that would be circular. Real binding is still the
per-socket operation before connect.
[Network.bindSocket](https://developer.android.com/reference/android/net/Network#bindSocket(java.net.DatagramSocket)).

## Test Matrix Needed

None of these tests was run here. Existing tests are source evidence only.

### Deterministic App Tests

| Test | Required assertion |
| --- | --- |
| Registration choice at 26, 27, 28, 29, 30, 31 | Existing default path at 26/27, one request at 28/29/30, passive best at31. Use a narrow registration seam or observable real registration; manual bestCallback calls cannot prove which API production used. |
| Same-transport A -> B, A remains | New request-winner event plus complete B payload selects B, with both different DNS and identical DNS address. No loss(A) prerequisite. |
| Ordering and duplicate events | Winner-before-observer, observer-before-winner, late loss(A), duplicate availability, A -> B -> A and out-of-date payloads cannot clear/revive the wrong winner. |
| Readiness and no guessing | No winner, empty DNS, VPN candidate and blocked winner do not select a scored alternate. API29/30 initial blocked state gates publication. No dependency on blocked callbacks below29. |
| Same-Network DNS refresh | Immutable DNS replacement triggers one coalesced rebuild; equivalent payloads and irrelevant changes do not. |
| Request lifetime | Repeated START does not duplicate; WAIT/reconnect keeps request; STOP releases before destruction; STOP -> START rejects old-generation callbacks; destruction and partial-registration failure release remaining registrations. |
| Service and worker guards | Preserve queued/late stop guards, old-worker rejection, per-worker snapshots, queue retirement, protect/bind/connect ordering and bind failure behavior. |

Keep the old known-gap fixture as a clearly named **passive information-limit**
test, or replace its legacy-production interpretation. Do not simply inject
`bestCallback.onAvailable(B)` and rename the test to say the platform bug is fixed.
The current fabricated handles and immutable capability snapshots remain useful
for state-machine tests on API26+; they cannot support traffic claims.

### Platform And Traffic Tests

Run registration/callback smoke separately on **26, 27, 28, 29 and 30**. Run the
full handoff/traffic matrix at least on 28 and30, plus representative older/OEM
devices; include API31+ as a regression control. Record any untested release
explicitly rather than extrapolating a newer emulator result.

| Scenario | Evidence required |
| --- | --- |
| Active VPN, same-transport overlap | Two real routable Networks A/B; establish A selection, keep A alive, make B win, retain the VPN, and record real request callback B. On26/27 record the real system-default callback instead. |
| Resolver/network coherence | Make resolver A silent and B answer uniquely, watchdog off. Query through the actual AdAway TUN using a fresh uncached name; capture replies and independently attribute upstream packets to B. Test same resolver IP on isolated A/B paths too. |
| Request versus default | Record both separately using independent privileged lab observation or a controlled VPN-excluded observer. Their equality is a measured fact for that run, not the pass condition for all policies. |
| Physical/default visibility | Check VPN covering AdAway's UID versus explicitly excluded UID, API26/27 versus28-30. Verify NOT_VPN request never selects the tunnel. Do not auto-change exclusion to make a test pass. |
| Policies | Data Saver/background restrictions at registration and afterward, metered networks, captive/unvalidated networks, VPN lockdown and ordinary bypass settings. Confirm denial/blocking and recovery without unbound forwarding. |
| Transports and loss | Wi-Fi/cellular and Ethernet controls, simultaneous same-transport links where supported, final loss/recovery, radios disabled, and network loss during bind/connect. |
| Lifetime and power | Repeated start/stop, service still bound after STOP, process death and long WAIT state. Observe outstanding requests, network teardown/retention, and incremental radio activity relative to passive baseline. |
| Metadata | Reported VPN underlying Network agrees with the current worker's actual bindings; stale workers cannot overwrite it. This agreement alone is not traffic proof. |

A roam that retains the same Network handle does not exercise the A/B identity
case. Many legacy devices cannot sustain two Wi-Fi networks; use a capable lab
setup (for example two real Ethernet-backed networks with controlled preference),
or leave that gate unproved. Do not manufacture success by assigning callbacks
to virtual/synthetic Networks and labeling them physical routes.

Separate evidence levels explicitly: **synthetic state-machine event**, **real
framework request selection**, **kernel-bound socket egress**, and **end-to-end
DNS through the VPN**. A virtual test Network can demonstrate framework matching
only to the extent actually exercised. No callback, fabricated handle, successful
bind call, interface listing or underlying-network declaration alone proves the
real routing path or DNS success.

## Handoff And Closure

Recommended next action after freeze: main implements the small permission,
request-state and request-lifetime changes, then runs the real API28/30
same-transport discriminator before expanding the runtime matrix. If the active
request cost is rejected, document the API28-30 passive-observation limitation;
do not replace it with a guessed identity or drop minSdk26.

Close the app-owned handoff defect only against the stated platform-best-request
contract after implementation and executable evidence. Keep any literal
exact-system-default requirement separately limited, and keep unavailable physical
handoff gates open. No bug ledger or other review file was changed by this lane.

Inspected SHA256 values:

- `VpnNetworkMonitor.java`: `14bd2142cd132a705e65383798a5de400fbcdcc6c27417c374a098bb908bc641`
- `VpnNetworkMonitorInstrumentedTest.java`: `986e3c689e52e4164be041e7b6e5a43e5c9e20d71a8ed241d4892ff1aae676f5`
- `network-fixes-review.md`: `225611728f09850bcb18a61bdfe621e8fa32f6ee989c52cf2521997ab81e0d0c`
