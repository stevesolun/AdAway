# Non-root Android release verification

The reviewed base [PR #15](https://github.com/stevesolun/AdAway/pull/15)
merged with all six required checks passing. The following local evidence is
for the subsequent deep-QA changes. It is not yet evidence of public delivery.

**At review submission (18:46 UTC):** [DEEP-STRESS-001](../deep-stress-bugs.csv),
[GitHub issue #17](https://github.com/stevesolun/AdAway/issues/17). The fresh 5M
benchmark and a first real import succeeded, but a repeated production refresh
on the 3 GiB Android 14 emulator ended in an OS low-memory kill. Stored rules
and the prior active generation survived. A bounded-copy change is being
tested against the retained fixture; the initial benchmark does not close this
gate. The linked issue records the terminal matched-scale outcome and release
decision; do not infer either from a snapshot taken while the test was running.

**Terminal matched result:** [issue #17 retest](https://github.com/stevesolun/AdAway/issues/17#issuecomment-7176713232).
The patched foreground process survived both real 5M refreshes (18m30s and
17m30s). Both generations kept the full 5M rules and 75 unchanged user
entries. Each refresh overlapped with 250 menu/Back cycles, 500 swipes and
50 requested rotations. That closes the local repeat gate, not public delivery.
At the 30-second sampling cadence, maximum app PSS was 232,540 KiB
on the first patched pass (38 samples) and 235,384 KiB on the warm pass
(36 samples). These are sampled values, not guaranteed peak allocations.

Against the earlier APK, the writer regression failed at 18,000 pending rows.
On the bounded build both the injected-failure rollback and repeated-revision
regressions passed (two tests, no skips, 6.772 s). The 32,000-row file
had a duplicate across batches and an unsupported line; both repeats retained
31,999 distinct rules and preserved the user's exception. A changed third
revision replaced the old rules. See `deep-general/bounded-red.txt` and
`deep-general/bounded-green.txt`.

The full Android 14 suite on the bounded build recorded **636 passes, 19
assumption skips, zero failures or errors** (655 cases, 400.756 s). The debug
APK digest is in the stress register; the test APK SHA-256 is
`2b7cc4e0407a4fa614fb1a6f76e8fab5992ca65a7f35f9ee52a37c6236503ad4`.
The earlier integrated run recorded 633 passes, 19 skips and zero failures.
The final host suite recorded 737 passes, three skips and no failures (740
cases across 95 suites); Android lint reported 35 warnings and no errors.
See `deep-general/bounded-full-connected.txt` and `bounded-final-host.log`.
All 88 repository Python checks and 26 matrix safety fixtures
passed. These runs overlap the targeted tests; their counts are not additive.

In the subsequent Log and rule-scope batch, a forced concurrent read failed
on the old VPN logger and passed with the fix (all five cache tests passed).
One older UI refresh can no longer restore the pre-Clear list; the Android
regression holds the actual classified rows until Clear completes. With
recording paused, live DNS still filtered a listed host and another pull to
refresh kept the cleared screen empty. With both browser handlers temporarily
disabled, tapping a valid recorded hostname did not exit the Log screen.
The portable VPN ownership checks passed 11/11 and the disabled-native-feature
checks passed 17/17. The release APK was exercised separately from Android
instrumentation: see [signed candidate evidence](signed-upgrade.md).

Two older host invocations collided while writing the same test-result file.
An isolated host run on the updated source completed 740 cases (737 passes,
3 skips, no failures) and the subsequent complete host run also passed.
Lint reported 0 errors and 35 warnings. Local raw outputs include
`q12-isolated-host.log`, `q14-final-host.log` and `q14-packages.log`.

The first integrated Log run had 636 passes, 19 skips and two failing
search/Paging assertions (657 total). A wanted row already present in
the old page was not a valid indication that an excluded row had left.
The corrected two checks require the expected adapter count, the visible
wanted row and no attached excluded row. Both passed on the original
3 GiB emulator; all 11 targeted Log, Rules and exclusion cases passed.

A second emulator ran 660 cases against the final app code: 640 passes,
19 skips and one timeout after recreating Home and tapping its next counter.
Android showed the first Lists launch, return and Home relaunch. No second
launch was recorded. After requiring a focused stable target in screen
coordinates, the empty and populated routes each passed three times on
that emulator, without repeating taps or changing the timeout (six cases).
Exact cause of the original lost tap is not conclusively established.
Raw evidence: `q06-full-connected.txt`, `q15-full-connected.txt`,
`q15-system-test-activity.txt`, `q16-focused.txt` and `q17-home-*.txt`.
The final public-source limits, startup batch and narrow layouts also have
targeted regression evidence. Protected CI must validate the submitted head.

**Final complete local Android 14 run:** 660 started, **641 passes, 19
assumption skips, zero failures or errors**, 646.919 seconds. Counts are
from the per-case terminal statuses in `q16-final-suite.txt`, not the
runner's total. The actual installed debug app and test APKs were verified
against `q16-debug-inputs.txt`: hashes started `4d27deeb` and `34ea92fd`.
This run included the corrected Paging checks and the original Home test.
The six later Home executions used the test-only update (`q17-test-sha256.txt`),
not a different release APK. The 19 skips comprised two channel/permission
cases, two platform backup phases, one root apply, three opt-in benchmarks,
one system download, and ten opt-in or unprepared live VPN/network cases.
The separate signed VPN journey and 5M runs are not counted as suite passes.

Android 11: three independent cold-boot, real-consent sequences on the same
APK pair each passed the first blocked DNS query without retry (14, 31 and
7 ms from send to response recording). That did not reproduce the prior
timeout or establish a fix. The complete three sequences were **23 passed,
one failed, none skipped**: the third run observed a third service start
where the IPv6 recreation test expected two. The later DNS case still passed.
Local evidence: `api30-first-dns/report.md` and `run-3/instrumentation.txt`.

Follow-up: timestamps distinguished **two actual service launches** from
three worker starts. A network handoff preceded the old Activity pausing.
The corrected test checks launches across recreation and samples the current
tunnel immediately before the next toggle. On a fresh Android 11 emulator
all eight cases passed without skips, including the first blocked DNS request.
This fixes a test attribution error, not the unreproduced DNS timeout.
See [issue #18](https://github.com/stevesolun/AdAway/issues/18).

| User question | Evidence | Boundary |
| --- | --- | --- |
| Can I enter my own filter URL and apply it? | [Source editor and Apply](../deep-source-add-journeys.csv); [unrelated live filter](../deep-general-journeys.csv) | HTTP success, contention, a genuine error and stored source counts are distinguished. |
| Can I subscribe to and remove the intended lists? | [Discover and Sources](../deep-discover-journeys.csv) | Includes selection, mixed types, confirmation, cancel, rendering and database outcomes; not every directory entry. |
| Does the phone really filter DNS? | [Rule provenance and traffic](../deep-rules-journeys.csv); [browser controls](../deep-general-journeys.csv) | A DNS blocker cannot remove every banner or distinguish paths on a shared domain. |
| Can I stop, recover and understand protection? | [VPN permission and system surfaces](../deep-onboarding-journeys.csv) | Emulated Android, not OEM behavior or physical hardware. |
| Will an upgrade keep my chosen list? | [Public 13.5.6b to signed candidate, then browser and VPN tests](signed-upgrade.md) | Real in-place Android 14 upgrade. The VPN stopped on package replacement and was restored by the Home switch. |
| Do preferences and backups actually work? | [Settings and real picker](../deep-settings-journeys.csv); [startup preference](../deep-startup-journeys.csv) | Manual file restore is not platform backup transport restore. |
| Does large text and RTL still allow interaction? | [Matched before/after and screenshot hashes](visual-review.md); [Discover row corrections](../deep-disc-bugs.csv) | Forty populated snapshots at 393 dp across five settings; English and Hebrew only; partial translations remain. |
| What input does a remote list get to use? | [Input review](input-safety.md); [public URL and wire-DNS check](public-url-policy-smoke.md) | Public HTTPS filters are supported. The reviewed download routes, metadata limits and unsupported rule handling are not a general security certification. |
| What happens under load? | [Local-scale and rough-input results](../deep-stress-journeys.csv) | Generated loads are local. Third-party sites receive ordinary page visits only. |

Raw, build-specific local evidence is under
`app/build/reports/audit-2026-11-25/`. The per-lane ledgers identify the tested
binary or commit, initial failures, retests and limitations. Root installation
is out of scope. Physical-phone testing was deferred by the user, and no test
bypassed third-party human verification, Android consent or the package installer.
