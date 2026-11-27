# Non-root Android release verification

The reviewed base [PR #15](https://github.com/stevesolun/AdAway/pull/15)
merged with all six required checks passing. The following local evidence is
for the subsequent deep-QA changes. Public delivery was verified later in the
[13.5.7b release record](public-13.5.7b.md).

**November 26 phone candidate:** The [signed arbitrary-filter upgrade](restored-phone-release.md)
and [Android 11 offline recovery](vpn-network-recovery.md#consolidated-signed-ota)
were run on the same private APK. The Android [platform restore](platform-restore.md)
and [interrupted scheduled update](scheduled-due.md) have separate before/after
evidence. The [app-initiated installer checkpoint](app-update-journey.md)
was partial; see the [later public updater journey](public-13.5.7b.md#public-updater-on-android-14)
for its separately observed outcome. An Android package update over USB is not
proof of that path.

**Consolidated app commit `9968265d4`:** API 34 covered 698 distinct methods.
The two initial shards recorded 673 passes, 21 assumption skips and four
failed schedule-picker checks. On the identical installed debug app, the
final test-only replay passed all 19 picker/scheduler checks in 26.865 s.
After replay: **677 passed, 21 skipped, no outstanding failures**.
[Every method, including initial failures and skips](recovery-android14-methods.csv).
This was not one uninterrupted green run. Intermediate replays included
a timestamp assertion that did not reproduce and a Back action that closed
the underlying activity; the final harness targets the focused picker and
checks both detachment and restoration of the Sources toolbar focus.
The earlier 690-case run before the final UI edits passed 670 and skipped
20 in 540.995 s. Counts are not additive.

The final app's 95 JVM suites recorded 742 passes and three skips, with no
failures or errors; lint reported 35 warnings and zero errors. All 103
repository host checks passed. The APK's direct signer matched the public beta.
The debug app SHA-256 was `4ae756c123314381d3975c4fcd87b129b73c6942bb2ec69c4989449d669a9665`;
initial and final test harnesses were `3b913cc33e98356ce0246cfef42558e9fafa25108e65c68d7e7cfbdde66decaa`
and `c09fdc68ad5b0476989078d28176ebe89cbd312010390b92473fc73cba747762`.
Raw outputs: `app/build/reports/audit-2026-11-25/platform-restore/`.
Five disposable AVDs were stopped and deleted after validation; no phone
was connected. Neither these app tests nor the 3,000-event signed trace
prove every possible UI sequence or universal ad removal.

**Previous phone candidate, app commit `6da4194eb`:** [APK identity and signed results](source-category.md#phone-candidate),
[nine source findings](../deep-source-category-bugs.csv) and
[offline UI stresses](offline-ui-stress.md). The JVM result was 742 passed,
three skipped, zero failures or errors (95 suites); Android lint reported 35
warnings and no errors; all 90 repository host checks passed.
On the network-validated non-root API 34 emulator the final debug/test APK
pair completed all **687** cases in **483.82 s**: **668 passed, 19 assumption
skips, zero failures or errors**. [Per-method results and skip reasons](source-category-android14-methods.csv).
An initial run on an intentionally offline device did not complete; its
three network-constrained timeouts are recorded in the [test evidence](source-category.md#regression-controls).

**At the preceding checkpoint:** The [coverage cross-check](../deep-journey-evidence-files.csv)
reconciles 104 stories, 260 control variants and 179 historical journey records,
not 260 executed clicks or 179 passes. Prioritize Android 11/OEM network
recovery on a physical phone; interrupted or metered scheduled multi-source
updates; and Android's own backup restore and app-initiated update paths.
These are separate from the signed arbitrary-filter and local document proof.

**Prior app commit `8e2eace32`:** Android 14, 672 unique cases: **652
passed, 20 assumption skips, zero failures or errors** across two executions
of the same installed debug/test APK pair. The emulator exited after 281
terminal cases and while running case 282. After a cold boot and reinstall,
all 394 cases from the interrupted class onward finished. Three completed
cases overlapped and agreed; all expected method names are accounted for.
This is not a single uninterrupted run. [Per-method results and skip reasons](discover-order-android14-methods.csv);
[signed APK for that run](signed-upgrade.md#discover-order-phone-candidate) and [real selection checks](discover-pick-order.md).
JVM: 738 passed and three skipped (95 suites); lint: 35 warnings, no errors.
90 repository host checks passed with JDK 21 and the configured Android SDK.

**Previous local Android 14 result, app commit `17a7d1415`:** 669 started
and finished in 611.905 s: **650 passed, 19 assumption skips, zero failures
or errors**. See [each method and skip reason](idle-fix-android14-methods.csv).
All 95 JVM suites completed (738 passed, three skipped, zero failures or
errors). Android lint: 35 warnings and no errors. The non-root release APK
was tested separately: [signed APK and checksum](signed-upgrade.md#idle-and-locale-phone-candidate),
[5,000 rapid inputs, real browser, DNS and change/undo paths](pending-apply-and-locale.md).
The 5M imports below were matched on the import fix, not repeated for these
UI changes. Neither local test gate authorizes a public release.

Installed debug and test APK SHA-256 respectively:
`fae1c5e0b4588b829529b5e0ca34fd4ed1262e3b10b697911819148abfcf3ee1`,
`f22ef2b7da5481333ef8cde72fb461e4530020d27a46668322e4d18619742fe5`.
Raw `deep-geometry-final/fab-final-suite.txt`:
`73307da1197f6989ca53b51007e5984533e066c6f678bf448a943fd82d433726`.
Skipped: two notifications, two prepared platform backups, one root, three
opt-in benchmarks, one system download, ten prepared VPN/network scenarios.
The focused Home class initially encountered an Android Bluetooth crash dialog;
all seven passed unchanged after removing the overlay. An earlier partial
suite was superseded by the idle-redraw fix and is not counted here.

**Previous local Android 14 result, app commit `07d396dd0`:** 668 started and
finished: **648 passed, 20 assumption skips, zero failures or errors** in
625.588 s. See the [per-method results and skip reasons](final-android14-methods.csv)
and the separate [signed candidate for that run](signed-upgrade.md#domain-checker-phone-candidate).
The suite ran the debug app; the upgrade, Chrome, DNS, rotation and picker
checks ran the signed release app. The 5M stress runs below used the earlier
memory fix; they were not rerun on each later UI change.

The actual installed debug app/test SHA-256 values were
`f1f7c1a73f0b2c797c295aa83a9e76d20e99087dbfcf2f690fb25c6a2d1afdbe` and
`6784fe6aafbf8083af230ed68cdd7900ceb7e23f5186551f922e6dd8f9a840ea`.
Raw instrumentation output `q23-full.txt`:
`17d8461e6d4f1880829ea9b0e430aeccae1fb368b62df3b62ce036ba3dbfb5e6`.
Skips: two notification, two platform backup, one root, three opt-in benchmark,
one system download, one system tile callback and ten opt-in or unprepared
VPN/network cases. All five Home round-trip tests and all 18 Domain Checker
package tests passed in this complete run. Of 260 previously inventoried UI variants,
180 have at least one historically linked method that passed in this run.
That number is a cross-reference, **not** a count of fully signed-off journeys:
a method can test a component without exercising every click or device state.

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

**Pre-editor complete local Android 14 run:** 660 started, **641 passes, 19
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

**Later editor and filter changes:** The public 13.5.6b editor lost the draft URL
after cancelling Android's file picker. An instrumented refused document grant
also crashed it. After the fix, all 21 editor cases passed. The full suite at
`fb11c0976` finished 664 cases: 645 passed, 19 skipped, zero failures in
690.641 s. Two new regressions then exposed browser-only conditional sections
being applied as unconditional DNS rules. All 36 parser/offline cases passed
after the fix at `1097353af`. See [signed upgrade and real browser tests](signed-upgrade.md#conditional-filters-on-the-phone-candidate).
That earlier candidate APK SHA-256 was
`45467825b03f832f4c0d2ae1602f8871016804f4fcbaf78a65ac0f16cbc5e923`.
An upgrade from the public release retained the checked 43k EasyPrivacy list.
The signed file-picker journey independently enabled, disabled and deleted
a local source while the unrelated list remained selected.

**What interrupted the first two integrated attempts:** One Android 14 emulator
exited after 563 terminal outcomes; its graphics log reported an invalid color
buffer. That incomplete run is not a suite pass. On a single fresh emulator
the suite then finished 666 cases in 698.378 s: **646 passed, 19 skipped, one
failed**. The second Home counter tap did not launch Lists. At 01:40:14.653
Android's input event log identified NotificationShade as the recipient;
the adjacent touchscreen events identified the attempted point as (540, 705).
The test now waits for the intended window to own that point. It does not
disable notifications, extend the deadline or retry the tap. All five Home
cases then passed in 19.573 s, including a covering dialog and its dismissal.
Raw files: `q19-parser-full.txt`, `q20-solitary-full.txt`, `q20-events.txt`,
`q20-logcat.txt`, and `q20-home-guarded.txt`.

The host suite after aligning the two screenshot-runner contracts finished
740 cases across 95 suites: 737 passed, three skipped, zero failed, including
all 15 host matrix tests. All 30 focused capture safety scenarios also passed.
These tests check the capture process, not the usability of every screen.

**Pre-feedback-fix Android 14 result (test commit `37742e77a`):** 667 started,
**646 passed, 20 skipped, one failed**, 545.249 s. Counts are from per-case
terminal statuses in `q21-final-suite.txt`. All five Home cases passed,
including the formerly intercepted counter tap. The additional skip versus
the preceding run was the conditional Quick Settings click: SystemUI accepted
the command but did not dispatch its callback.

The failing Domain Checker case forced a write error, recreated the stopped
screen, restored writes and retried Block. The first failure was displayed as
expected, and the runtime database sync ran after retry, but the test did not
observe the visible success message before its deadline. Its subsequent
persistence assertion therefore did not run. The finding remained open at
that point: the identical method passed in the previous complete run and in
three unchanged isolated repetitions (2.607, 3.038, 3.084 s), including the
visible message and persisted rule. That does not establish the cause of
the integrated failure. See `q21-domain-logcat.txt` and
`q21-domain-unchanged-{1,2,3}.txt`. No public beta signoff from this run.

**Domain Checker follow-up:** A whole-class run missed a different success
message after Remove Allow. A deterministic regression then observed a
successful Block and its stored rule, recreated the screen and found the
confirmation gone. With the fix at `bea8c023d`, two independent full runs of
the Domain Checker package passed all 18 cases (21.630 s and 19.981 s).
The [Domain Checker signed candidate](signed-upgrade.md#domain-checker-phone-candidate)
also retained the confirmation in landscape, cleared it on a new Check and
restored DNS after Delete. We did not inject a failed write into the signed
APK. A separate source-summary fix at `07d396dd0` was verified with a real
single-rule file alongside a disabled 190k source. The final host suite
passed 737 of 740 cases, with three skips and zero failures; Android lint
reported 35 warnings and no errors.

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
| Can I stop, recover and understand protection? | [VPN permission and system surfaces](../deep-onboarding-journeys.csv); [signed network recovery](vpn-network-recovery.md#consolidated-signed-ota) | Emulated Android, not OEM behavior or physical hardware. |
| Will an upgrade keep my chosen list? | [Public 13.5.6b to signed candidate, then browser and VPN tests](signed-upgrade.md) | Real in-place Android 14 upgrade. The VPN stopped on package replacement and was restored by the Home switch. |
| Do preferences and backups actually work? | [Settings and real picker](../deep-settings-journeys.csv); [startup preference](../deep-startup-journeys.csv); [Android platform restore](platform-restore.md) | Platform restore used the local key/value transport; cloud/OEM restore and restored document grants remain unverified. |
| Does large text and RTL still allow interaction? | [Matched before/after and screenshot hashes](visual-review.md); [Discover row corrections](../deep-disc-bugs.csv) | Forty populated snapshots at 393 dp across five settings; English and Hebrew only; partial translations remain. |
| What input does a remote list get to use? | [Input review](input-safety.md); [public URL and wire-DNS check](public-url-policy-smoke.md) | Public HTTPS filters are supported. The reviewed download routes, metadata limits and unsupported rule handling are not a general security certification. |
| What happens under load? | [Local-scale and rough-input results](../deep-stress-journeys.csv) | Generated loads are local. Third-party sites receive ordinary page visits only. |

Raw, build-specific local evidence is under
`app/build/reports/audit-2026-11-25/`. The per-lane ledgers identify the tested
binary or commit, initial failures, retests and limitations. Root installation
is out of scope. Physical-phone testing was deferred by the user, and no test
bypassed third-party human verification, Android consent or the package installer.
