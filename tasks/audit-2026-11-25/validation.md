# November Audit Validation

This is evidence for the current audit batch, not whole-repository or release
completion. All emulator runs use the disposable API 34
`adaway-review-ci-api34` at `emulator-5556`, JDK 21 and strict Gradle dependency
verification. No personal phone or privileged root apply was used.

## Executed Runs

| Run | Observed result |
| --- | --- |
| Initial Home regression | 624 JVM cases, 3 skipped, no failures; lint 33 warnings and no errors; 11 connected cases passed. |
| Data/VPN/updater focused batch | 629 JVM cases, 3 skipped, no failures; 76 connected cases, 1 failed. The failure incorrectly expected disabling a source to erase a previous download error. |
| First combined compile | Production and JVM compilation passed; Android-test compilation rejected a new Discover test's unsupported Espresso imports. Replaced those with the existing accessibility API approach, without adding dependencies. |
| Full combined Android batch | 379 cases, 12 intentionally skipped, 3 failures. Two were incorrect offline-test expectations, including a correction applied to the wrong test. The third exposed the editor's initial Material button state. All other cases passed. |
| Editor initial-state red proof | Two focused cases failed on the pre-fix draft patch: existing Allow source rendered as Block; initial File source had the wrong checked appearance. Enabled controls before assigning initial values, without binding change listeners early. |
| Corrected full combined batch | 380 connected cases, 12 skipped, 1 failure: pending Apply prompt disappears after a targeted update. Source editor 12/12, Discover state 9/9, Home navigation/state 13/13 and offline source 15/15 passed. Latest JVM suite: 634 cases, 3 skipped, no failures. This is not a clean combined-suite result. |
| Live download and first prompt fixture | Real Android DownloadProvider completion delivery and rejection of untrusted downloaded bytes passed. Two prompt fixtures timed out in ActivityScenario idle waits; those timeouts are not product regressions. |
| Minimal prompt red proof | After removing the idle-wait dependency, same-frame selection reversal left pending state true but removed the visible Apply message. The fast-completion test also needed explicit Activity finish before scenario cleanup. A fresh snackbar callback on explicit dismissal is under validation. |
| Prompt and live provider green | 12 connected cases passed: two rapid-feedback transitions, all nine Sources apply-state regressions, and the real DownloadProvider case. Explicit Activity finish corrected the new test cleanup's idle wait; no state assertion was removed. |
| DNS fixture safety | All six DnsPacketProxyRuntimeTruthTest cases passed, including preservation of existing USER row identities. Runtime compatibility rules now belong to a unique test source; teardown removes only that source. This test no longer exercises compatibility-row initialization, which retains separate tests. |
| Current-version updater red/green | The new current-version JVM case failed before the fix; afterward all 31 ManifestTest cases and five connected update-model/UI cases passed. Current metadata is authenticated but cannot authorize a reinstall; downgrade, expiry and signature checks remain. |
| Fresh setup and Hebrew URL | Normal VPN consent granted through Android UI; three defaults returned HTTP200, 91,774 accepted source rows, 0 failed; Home displayed 81.7k effective blocked rules. Visible Sources > Add custom source > empty name + literal EasyListHebrew hosts URL > Apply imported 182 rows, 1 skipped, three existing sources HTTP304, 0 failed. |
| Live VPN before network callback follow-up | Direct instrumentation with pre-granted consent: all four VpnLifecycleInstrumentedTest and VpnDnsTunnelInstrumentedTest cases passed, no skips. This includes framework action without private extras, foreground tunnel start/stop/resume, heartbeat work and actual UDP DNS through the tunnel. |
| Layout red proof | Four cases failed: daily Cancel incorrectly enabled an old schedule; custom schedule toolbar lacked an icon; landscape list had zero reachable height; first weekly fixture could not find its menu. Screenshots corrected the schedule finding: the themed ActionBar already had Up, but the screen duplicated the title. Both layouts and cancellation were fixed; the test now targets the real ActionBar. |
| UI/network focused integration | 97 cases, 0 skipped, 4 failures. Editor 17, existing Home navigation 13, new Home status/progress 11, schedule navigation/cancellation 3, schedule reconciliation 6, and network monitor/service/mapper 27 all passed. Nine existing Discover cases passed. Remaining failures: checkbox performClick return assumption; old Activity lookup resolving a recreated Window; two scheduling fixtures blocked in ActivityScenario.waitForIdleSync. Fixture ownership and main-thread reads corrected without dropping product assertions. |
| Recovery/updater integration interrupted | Intended 90 cases, but only 38 executed: 37 passed and one failure/process crash. All schedule navigation 3, schedule lifecycle 3, profile scheduling 7, Discover state 10, root export 3 and core recovery 11 passed. First large-selection UI test inherited a global synchronous LiveData executor, making the real Activity database query run on the UI thread. Isolated its recorder database without that global rule. The other 52 cases did not execute. |
| Remaining recovery/updater cases | 53 cases executed: 51 passed, two large-selection assertions failed because HostsSourceDao.getAll deliberately excludes USER. All updater, passive native UI, recovery chooser, existing bulk worker/UI and unsupported-review cases passed. Corrected the assertion to require zero added subscriptions and the USER row still present. Raw run log: /tmp/adaway-audit-recovery-updater-focused-two.log; XML was overwritten before preservation, not available as retained evidence. |
| Large selections and system routing | XML reports 16 cases: 14 passed, two skipped, zero failures. Both real WorkManager 6000-ID retry/checked commands and cancellation pass; five boot cases and seven notification cases pass. Channel-only suppression needs notification permission; live channel cleanup needs prepared VPN consent, so both explicitly skipped. Preserved system-large-focused.xml. These skips require a positive permission fixture, not optimistic closure. |

Network fixture integration required two compile corrections: Android's public
SDK supplies neither mutable NetworkCapabilities setters nor its proposed Builder.
The monitor extracts an immutable snapshot with public getters. Synthetic tests
exercise that selection state; one test also checks an empty real framework payload.
There is no hidden-API reflection or lower-API suppression. Positive live callback
delivery, socket routing, and the documented pre31 same-transport gap remain separate.

Fresh setup/site/live-VPN evidence above belongs to the intermediate debug APK
SHA256 `9ee67abcb29fe24a2e0894721cdd012ab6ca15fc1e0bd823f45a1e739ba7f4f9`,
not a released or final merged artifact. Android reported VPN CONNECTED/VALIDATED,
an IPv4 alias and eight installed provider IPv6 /128 routes. Ynet and Walla
rendered news content after the Hebrew import; this is not a clean-cache ad-removal
comparison or video/cosmetic filtering claim. KSP again required human verification
and was not bypassed. The later network callback changes require another live run.

The independent editor review found the Material button issue before shipping.
It is logged as `EDITOR-PATCH001`; the two failing emulator assertions confirm
the static finding. No source data or production behavior was weakened to make
the offline assertions pass: disabled sources preserve their historical errors,
while sources with no previous error remain error-free.

Ignored local logs are under `app/build/reports/audit-2026-11-25/`, with active
run logs in `/tmp/adaway-audit-*.log`. Do not publish raw device/network captures
as release evidence without reviewing their contents.

## Tooling Checks

- `python3 -m unittest discover -s scripts/tests -p 'test_*.py'`: 6 passed.
- `bash scripts/tests/cleanup-releases-safety.Tests.sh`: 14 mocked cases passed.
- `pwsh -NoProfile -File scripts/tests/verify-ux-signoff-safety.Tests.ps1`:
  126 generator-backed cases passed.
- `pwsh -NoProfile -File scripts/tests/run-ux-matrix-safety.Tests.ps1`:
  26 mocked cases passed in the earlier batch.
- Main independently reran these tooling checks; no real releases were deleted,
  no device was cleared by a mock, and no CI protection was relaxed.

## Consolidated Phase-One Gate

- Full JVM suite: 665 cases, three skipped, zero failures/errors. Lint: 34 warnings,
  zero errors. Log: `/tmp/adaway-audit-host-final-one.log`. The preceding host
  run exposed one stale boot-launch expectation, corrected to assert notification
  routing without starting a consent Activity or VPN automatically.
- Full API34: 488 cases, 476 passed, ten skipped, two failures. Preserved XML:
  `app/build/reports/audit-2026-11-25/connected-four.xml`; log:
  `/tmp/adaway-audit-connected-four.log`. One notification fixture cancelled an
  alert before the service's STOP callback had finished. A main-thread barrier
  now drains that callback after service disappearance. The UX matrix also found
  truncated filter identity text; names and formats now wrap without ellipsis.
- Focused host verification after those fixes: 30 cases, zero skips/failures.
  Log: `/tmp/adaway-audit-phase-one-followup-build.log`.
- Focused API34 replay: all 22 passed, zero skips. Nine notification cases began
  with a running, consented VPN; one complete UX matrix, ten Discover state cases,
  and two real 6000-ID selection/retry WorkManager cases passed. Log:
  `/tmp/adaway-audit-phase-one-replay.log`. Eight UX screenshots were retrieved to
  `app/build/reports/audit-2026-11-25/phase-one-ux-replay/` before uninstall.
- The full-run skips were two platform backup transport fixtures, privileged root
  apply, three opt-in scale cases, a network-dependent DownloadProvider case,
  preference VPN restart, live DNS tunnel and framework VPN start. They are not
  pass claims. Earlier prepared-permission notification nine and live VPN/DNS four
  each passed without skips on APK `a6442448dfb19af7421bd2e3970ada2eebb864e8463a03e30ca876276264de4f`.
- The replay used saved immutable APKs while next-batch agents edited sources:
  app SHA256 `44d51782eb11f414a9a6c9a0815e3c6a34eb4cf589d995b2f6ae6caca089acd1`,
  test SHA256 `95ea2b386198132a58fec3d433492552b03ea4782cb6360ec9aea247f84f1347`.
  No full-green or current-next-batch claim follows from this targeted replay.
- Release artifact/signature and evidence protections were committed separately
  as `cce7b5b78`, with no push, merge or publication yet.

## Remaining Evidence Boundaries

- Live DownloadProvider delivery passed on API 34 using a real download of a
  pinned public repository file. No synthetic completion broadcast or polling
  fallback drove the receiver. The actual verifier rejected the untrusted bytes;
  this does not prove successful APK installation or installer-cancel UX.
- Real VPN consent and Hebrew-site browsing now passed on the intermediate APK
  identified above, but final network changes and a signed release need retesting.
- KSP displayed a human-verification challenge; successful shopping-site use is
  not claimed. DNS filtering is not browser cosmetic filtering.
- Root runtime, live backup-transport restore, physical devices, every workflow
  variant, and still-pending file reviews are not implied by suite results.
- No new release, merge, push, version bump or protected CI run has occurred for
  this audit batch yet. Local release-safety changes have been committed.
