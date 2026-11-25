# November Audit Validation

This is evidence for the current audit batch, not whole-repository or release
completion. Emulator runs use disposable API 34 `adaway-review-ci-api34` at
`emulator-5556` and, where explicitly identified, API 30
`adaway-review-ci-api30` at `emulator-5558`, JDK 21 and strict Gradle dependency
verification. No personal phone or privileged root apply was used.

## Candidate 13.5.5b

- Final local direct-release SHA-256:
  `2a25db354c6bf4f5c8346c1579349fc0d31099bfa70e3038e9fd89213150fece`.
  The signed APK uses the public beta certificate below. An in-place install
  retained the four enabled lists and 81.9k blocked rules. Turned protection
  on via Home after package replacement: Android reported a connected VPN,
  example.com resolved, while the Hebrew totalmedia2.ynet.co.il and default
  pagead2.googlesyndication.com probes failed DNS. Android Chrome loaded
  Ynet news content and 323 images, Walla news content and 208 images, and
  KSP's mobile home with 64 headings and 16 images. KSP initially presented
  a challenge on the preceding build; no challenge was bypassed, no checkout
  was attempted and no claim is made about shopping beyond the home page.
  Do not interpret these probes as a controlled ad-rate or speed benchmark.
- Older subscription recovery: freshly installed public 13.5.4b retained
  URLhaus after upgrade (HTTP 410). Removing it from the defaults only fixes
  fresh candidate installs. A separate HTTP 404 was caused by an incomplete
  URL entered by the test driver. Verified the full Hebrew URL on screen,
  disabled only the retired source and applied. All four selected sources
  then showed up to date. No silent subscription removal or replacement.
- Phase-five full API34: 602 cases, 584 passed, 18 skipped, no failures or errors;
  see the retained `phase-five-connected-full.xml`. The final two edits came
  after that run. All four focused final API34 checks passed, covering the
  Room recount and download-error recovery, existing Help and Update actions.
  Final host tests: 718 cases, three skipped, no failures or errors. Lint:
  37 warnings, zero errors. Strict debug, Android-test and signed builds passed.
- The Android 11 planner also rejected the forced index in the current-schema
  runtime recount (REM-SCHEMA-003). With the index hint removed, the same
  four-row Android SQLite fixture returned 1 blocked, 1 exact, 1 allowed,
  0 redirected, 2 active. No link to the intermittent DNS timeout is inferred.
- Phase four debug pair: app SHA-256
  `cbc4d94237a0c5d884bb0c4592691bc9f0d55d8dd5c65e841b3e070f8d626376`,
  tests `2de33ded07e1092d0cdb76d69e6f41982c7b57f912239a87cd32f0f341c393bc`.
  The integrated host run recorded 718 cases, 3 skipped, no failures or errors.
  Lint: 37 warnings, no errors. API34 focused: 87 passed without skips.
- Full phase-four API34 XML: 601 cases, 583 passed, 15 skipped, 3 failed
  (external caller identity, rapid rule toggle, unscrolled disabled preference).
  Retained XML: `app/build/reports/audit-2026-11-25/phase-four-connected-full.xml`.
- API30 bound-service lifecycle: 3 passed, 0 skipped; the running service retained
  its request ID on repeated start, released it on stop while bound and acquired a
  new ID on restart. Additional API30 UI/migration run: 18 passed, two v4-to-current
  upgrade paths failed in 18-to-19. Removing the forced index from that migration
  allowed both paths and the seeded stats check to pass on Android 11.
- A separate API30 live DNS query timed out after the lifecycle cases. An isolated
  rerun passed; this is not yet a root cause or a claim that the timeout is fixed.
- Phase five: the combined UI/migration checks passed apart from the recording
  model's newly added positive delivery control. Matching the recorder to the actual
  configured blocking method fixed that fixture; API30 then passed all five cases
  including the same-frame double-tap regression and real external denial.
- First signed local direct-release APK: version 13.5.5b, code 130505, non-debuggable.
  APK SHA-256 `7a32cdabc0d2caa0370bb5e2eaef0f528fb4c6895cef20945d5cbb0617b03f6a`.
  v2/v3 signer SHA-256 matches the public beta certificate:
  `9bf3a40102638ed9271f4dea6225a2d66e5d2c5f3e5204cc125376288f7f9595`.
  Downloaded the actual public 13.5.4b APK (SHA-256
  `d33d02aabf4d502626fc7ae08c509ff28e3977712fb8ecdb5659cfc6f335ab63`),
  installed it on disposable API30, then installed 13.5.5b over it. Same UID
  and first-install time; signed Home launched and visibly showed 13.5.5b. This
  is not physical-device or public CI-artifact attestation.

## Phase Four Checkpoint

- Frozen app input hashes: local `app/build/reports/audit-2026-11-25/phase-four-build-inputs.csv`.
  Integrated host tests, lint and both APK builds are running; no pass claimed yet.
- Host tooling and documentation: 73 cases, zero failures/skips; main replay log
  `/tmp/adaway-audit-phase-four-tooling.log`. This includes real APK-tooling fixtures,
  isolated mock command tests, all retained documentation PNG decoding and metadata
  checks. It does not verify a newly published artifact or store upload.
- Main reran actual migration code with host SQLite: all six seeded table/view/index
  cases and all 31 upgrade chains passed; 32 schema self-checks passed. Two historical
  adjacent-export differences remain recorded, not relabeled as exact matches.
  Log: `/tmp/adaway-audit-phase-four-schema-host.log`. Android Room tests still pending.
- Main replayed all 13 root-fixture safety controls through an in-memory Shell only:
  `/tmp/adaway-audit-phase-four-root-fixture-host.log`. No root or system-hosts access.
- REM-EVID-001/003 historical summaries now distinguish mixed commits and unpaired
  invocation/results without changing raw logs. REM-EVID-002 applied only guarded
  OCT-005 status/retest cells: historical beta publication is not current audit delivery.
- Current ledger: 111 findings, 79 scoped verified fixes, eight implemented awaiting
  tests, three host/source-only verified, one partially verified and 20 dormant-native
  source findings. Native repair has not executed; retirement remains a user decision.

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
- Phase-three configured Python discovery: 63 passed, zero skips/failures
  (33 tooling fixes, 21 real-APK identity fixtures, nine inventory cases). Log:
  `/tmp/adaway-audit-tooling-followup-configured.log`. The first invocation lacked
  ANDROID_HOME: 42 cases passed but release-artifact setup errored before its
  cases ran. Supplying the installed SDK/JDK fixed that environment issue; no
  assertion or safety guard was removed. Certificate ShellCheck and bash -n pass.

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
- The first Android batch is committed as `f7dbbb5e7`. A further direct run of the
  four live VPN/DNS cases passed without skips on the saved phase-one APK; log:
  `/tmp/adaway-audit-phase-one-live-vpn.log`.

## Actual Boot Consent Recovery

On the saved phase-one APK above, the disposable emulator's Enable at startup
preference was on and notification permission was granted. ACTIVATE_VPN was
revoked as a test precondition, then Android was actually rebooted. This was not
a synthetic boot broadcast or injected controller callback.

- Android posted "VPN permission needed" / "Open AdAway to resume VPN protection."
  Screenshot: `app/build/reports/audit-2026-11-25/boot-permission-reminder.png`.
- Tapping that notification opened Home showing Protection off. Android's service
  inventory contained no AdAway service: opening the reminder did not enable VPN.
- Tapping the actual protection switch showed Android's Connection request dialog.
  Cancel returned without a service. Retrying and pressing Back also left no service.
- Retried again, rotated the consent dialog to landscape, and tapped its normal OK
  control. Returning to portrait showed the switch checked and "VPN enabled; no
  blocking rules loaded". Android confirmed foreground VpnService, notification20,
  and its framework VPN binding. No rule-blocking claim follows from this empty
  database fixture. The Home switch then stopped protection for subsequent tests.
- This proves API34 boot-reminder-to-real-consent continuation and denial/retry plus
  rotation. App-denied and blocked-channel notification branches retain their
  separate helper-level tests; this reboot did not exercise those two conditions.
- The screen capture exposed HOME-AUD-005: the empty-source Discover command
  overlapped leak status. It is logged with a layout correction and pending
  geometry test, not silently folded into the prior pass count.

## Phase-Two Validation

- First full host run: 706 cases, three skipped, one failed. The failure was a
  source-contract test searching for the removed external hosts-editor method.
  Its delimiter was migrated to the internal viewer binding, preserving the
  safety assertions. Log: `/tmp/adaway-audit-phase-two-host-build.log`.
- Focused corrected source-contract run: four cases passed; lint 37 warnings,
  zero errors; app and instrumentation APK builds succeeded. Log:
  `/tmp/adaway-audit-phase-two-followup-build.log`. The full 706 XML was overwritten
  before preservation. `phase-two-host-guard-four/` contains only the four-case
  follow-up, not the full host suite.
- Saved app SHA256:
  `30f86e8f8c90d2520e068956a0e6e97a19d14466e967fd6d87e4079f3bff6c92`.
  Saved test SHA256:
  `671f74de1a81d59da1ce4b5c871c28966d95040cf5c3ce25c22833a979134675`.
  Both are under `app/build/reports/audit-2026-11-25/phase-two-apks/`.
- Focused API34 run: 87 cases, 74 passed, two opt-in live-VPN cases skipped,
  eleven failed. Log: `/tmp/adaway-audit-phase-two-focused.log`. Failures are one
  retained domain-error feedback case, one outside-touch confirmation case, one
  oversized backup fixture, four legacy catalog selection cases, and four loaded
  filter-label layout fixtures. These are under diagnosis, not a green run.
- Separate prepared-consent live run: seven passed, zero skipped/failed. Log:
  `/tmp/adaway-audit-phase-two-live-vpn.log`. Actual preference switches replaced
  the tunnel/worker in both directions, including rotation; stopped/root-mode
  switches did not start protection. Existing lifecycle and UDP tunnel cases also
  passed. This run does not prove the newer unstaged network changes.
- Home empty-state geometry and navigation passed on the saved APK. Four internal
  hosts-viewer fixtures passed without privileged system reads or writes. All
  fourteen command/tile operation-ownership cases passed. These are scoped fixes,
  not root-runtime, system tile placement, or whole-workflow completion claims.

### Actual Scanner Uninstall

Only the harmless `org.adaway.audit.adwarefixture` package was installed for this
check. It has no ad SDK, code, or network permission; a manifest component name
matches the scanner's static signature. The instrumentation package was untouched.

- Saved phase-one APK reproduced Android's refusal: PackageInstaller logged
  missing REQUEST_DELETE_PACKAGES and returned without confirmation. The filtered
  log is `adware-uninstall-baseline.txt` in the local audit reports directory.
- Saved phase-two APK opened Android's normal uninstall confirmation from the
  scanner row. Cancel returned with the package still listed.
- Reopening the same row and confirming OK uninstalled only the disposable
  fixture. The surviving scanner automatically refreshed and removed its row;
  the separate instrumentation fixture remained visible. Package-manager query
  confirmed the disposable package was absent.
- Screenshots: `adware-uninstall-confirmation.png` and
  `adware-uninstalled-refresh.png`. No privileged deletion was used. Automated
  refresh and DNS-log action-label/callback cases also passed in the 87-case run;
  no TalkBack interaction claim follows from accessibility-property assertions.

## Phase-Three Validation

- Consolidated full JVM run: 711 cases, three skipped, zero failures/errors;
  lint 37 warnings, zero errors. Production and Android-test APK builds passed.
  Logs: `/tmp/adaway-audit-phase-three-host-build.log` and
  `/tmp/adaway-audit-phase-three-test-build.log`. Full JVM XML was preserved before
  another invocation in `app/build/reports/audit-2026-11-25/phase-three-host-full/`.
- Saved app SHA256:
  `e25c957e6a9c57d5e957147f5f1c8f439cc8e057382dfb96ec9463aff0509a4b`.
  Saved test SHA256:
  `9f1c191963a3cfa35cc32f2d672183ba9f88246d393c759aa299d3a19392ce02`.
  Both are under `app/build/reports/audit-2026-11-25/phase-three-apks/`.
- API30 emulator `adaway-review-ci-api30`, serial `emulator-5558`: 55 passed,
  zero skips/failures. Log: `/tmp/adaway-audit-phase-three-api30.log`.
  47 deterministic network/mapper/service cases, one actual platform-registration
  callback case, three preference cases, three live lifecycle cases and one live
  UDP DNS tunnel case. The fixture used real Android consent granted through its
  Connection request dialog and began with AdAway stopped, not a substituted
  permission callback. The app runs without root; no privileged root apply occurred.
- Actual API30 callbacks now yield a usable non-VPN selection through the legacy
  active request. Live tests exercise that path through real tunnel creation and
  traffic. They do not reproduce overlapping same-transport A-to-B routing with
  independently captured egress. Retained-bind STOP request release still needs
  separate framework evidence. No actual API26/27/28/29 result is implied.
- API34 focused replay: 96 cases, 94 passed, two failed, zero skipped. Log:
  `/tmp/adaway-audit-phase-three-api34.log`. Ten domain operations, four backup
  capacity, six legacy selection, four loaded-label geometry, eight updater
  geometry and all 47 deterministic network cases passed. Rules were 12/13;
  Home round trips 3/4. Remaining failures: row-dialog Back before confirmed
  focus, and the Private DNS fixture requiring a direct settings handler.
- Main's actual API34 package resolver reports no PRIVATE_DNS_SETTINGS activity,
  while WIRELESS_SETTINGS resolves to Android's NetworkDashboardActivity. The
  product already falls back to that route; the fixture must verify it rather
  than require an unavailable direct handler. Both Home counter-card cases and
  the real VPN-settings round trip passed. Fixture corrections await rerun.
- API34 separate live replay: eight passed, zero skipped/failed. Log:
  `/tmp/adaway-audit-phase-three-api34-live.log`. The real registration callback,
  all three preference controls, three lifecycle cases and UDP DNS tunnel passed
  on the same immutable phase-three APK pair. A full connected rerun is still open.
- All eight updater geometry cases passed, including actual measured pre-patch
  red controls and normal/2x font scenarios. The filtered platform geometry log
  is retained as `phase-three-updater-geometry.log` in the local audit reports.

## Additional Review Findings

The continued file review found a table-form historical version-4 migration
failure (host reproduced), one backup XML guard weakness, six connected-fixture
blind spots/safety issues, obsolete contributor toolchain instructions, stale
store descriptions, and one corrupt historical screenshot. These are logged
separately and assigned bounded fixes. No modern-install migration failure,
actual privileged test mutation, or physical-device incident is claimed.

Seventeen further source-confirmed issues are in dormant tcpdump printers. They
are neither compiled into the APK nor reachable through the disabled capture
feature. The user has been asked whether to retire that obsolete module from
the active tree, retaining Git history, or continue maintaining it. No deletion
or native-runtime success is implied. One other native-printer review agent
failed before producing coverage; its assigned files remain pending.

## Remaining Evidence Boundaries

- Existing 145 Sources and nine Home workflow rows now carry method-level
  evidence from the retained phase-one artifacts: 88 partial and 66 pending.
  No row was promoted to passed. The join and limits are preserved in
  `workflow-evidence-refresh.csv` and `workflow-evidence-review.md`; this does
  not fold current phase-two/three results into the older APK's proof.
- Live DownloadProvider delivery passed on API 34 using a real download of a
  pinned public repository file. No synthetic completion broadcast or polling
  fallback drove the receiver. The actual verifier rejected the untrusted bytes;
  this does not prove successful APK installation or installer-cancel UX.
- Real VPN consent and Hebrew-site browsing now passed on the intermediate APK
  identified above, but final network changes and a signed release need retesting.
- Earlier KSP attempts displayed a human-verification challenge. The final
  candidate loaded its mobile home; deeper shopping flows were not repeated.
  DNS filtering is not browser cosmetic filtering.
- Root runtime, live backup-transport restore, physical devices, every workflow
  variant, and still-pending file reviews are not implied by suite results.
- The beta version bump and application fixes were committed locally. Public
  delivery requires separate protected CI, merge, signed tag and release verification.
