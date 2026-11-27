# Non-root Android VPN: 13.5.8b candidate

**Handoff, November 27:** At the time of this check the public beta was 13.5.7b.
The locally built 13.5.8b (130508) integration APK passed
the [new-task DNS and browsing checks](#signed-new-task-checks); the combined
709-case Android run finished with 688 passed, 21 skipped and no failures.
This is local emulator evidence, not publication or stable sign-off.

On public 13.5.7b, merely returning to Home disconnected another authorized
VPN on stock Android 14. Home was asking Android to prepare a VPN to find out
whether it was available. Scheduled filter refreshes also used the operation
that enables protection. The fix at `c6f24f438` separates passive status and
background refresh from a user's request to turn protection on.

## Competing VPN

Using two real, separately authorized apps on a private API 34 emulator, the
published app displaced the second VPN on two Home returns. On the patched
debug APK, the other network stayed connected on first launch, warm return,
and navigating from Sources to Home (103/103 samples). A background refresh
imported two local-file rules without changing that network (91/91 samples).
A deliberate enable tap switched ownership to AdAway. After forgetting
AdAway in Android Settings, cancelling Android's consent dialog left the other
network intact (69/69 samples). These are sampled observations, not continuous
packet captures. The successful immediate refresh used a local file and a
disposable emulator's validated network; a time-of-day scheduled run was not
observed in this journey.

The integrated local JVM run completed 746 cases (743 passed, 3 skipped, zero
failures); all 124 host checks passed. These are distinct from the Android
device checks below.

The debug APK SHA-256 was
`9259ae41bf1349baab779ca77fa3074c2b478604035225700486c40acf6ff1c9`.
The 38 focused Android tests separately covered the worker, offline waiting,
consent, pending selections and the system service start action.

## Signed Upgrade

The **locally signed release candidate**, version code 130508,
had SHA-256 `4ed8ddacaa3c0892e76d9c9ef4cebed0ae805400ce545a091efa2a18e62baf69`
and the same v2/v3 signer as the public beta. Two Android 14 emulators used
the system installer to update from public 13.5.7b without clearing data.
Both kept the Firebog AdGuard DNS custom URL as their sole enabled source,
the beta preference, and their original install time.

| Before the update | Actual outcome |
| --- | --- |
| Protection on; doubleclick.net blocked | The installer stopped the tunnel. Home accurately showed off. One explicit enable tap blocked doubleclick.net again; example.org resolved and Chrome securely rendered RFC 2606 and About Debian. Turning it off made both domains resolve. |
| Protection deliberately off after running; consent retained | Stayed off across installation and two Home relaunches. No VPN service start or tunnel establishment observed. |

The ordinary VPN does not auto-resume after package replacement. Check Home
after installation. No physical/OEM, rooted device, lockdown, universal
filter-syntax or every-click-sequence claim. The candidate is not asserted to
have the same bytes as a future public build. The local raw evidence is under
`app/build/reports/audit-2026-11-25/{passive-vpn-qa,upgrade-13.5.8b}/RESULT.md`.

## Refresh feedback follow-up

- **Sources Apply (POST-VPN-002):** A completed download is acknowledged only
  after a matching selection and established tunnel. A returning lifecycle
  observer rechecked this without a new database or worker event in the Android
  regression, which controlled tunnel state. The earlier signed upgrade did not
  test this prompt.
- **Worker selection changed during import (POST-VPN-004):** Two deterministic Android
  tests disabled a source for the real file import and restored the switch before
  completion. Room showed an enabled source with zero rules. Before the fix both
  cases incorrectly produced a success receipt (active and stopped). Afterward
  neither receipt was emitted. The two cases and then all 48 related device tests
  passed. See `final-refresh-feedback/race-{red,green}.txt` and `final-focused.txt`.
- **Home status (P3, POST-VPN-003): verified in a focused API 34 debug test.**
  The earlier immediate worker succeeded and imported two rules, but Home still
  showed "Syncing rule database" with protection off. At `405e73004`, the
  focused test saw "Filter rules updated" after passive finalization and again
  after Home recreation. It failed before the fix (1/1) and passed after (1/1):
  `app/build/reports/audit-2026-11-25/final-refresh-feedback/{red,green}.txt`.
  This follow-up was not run on the signed upgrade APK above.

## Signed filter and navigation stress, November 27

The local app for this stress run at `64cbc505b`, SHA-256
`ede3279300efb442f7dd7733dd87f0ba84039fc343730aeee57a7fe09c012233`,
matched the installed package bytes on non-root Android 14. Version 130508;
APK Signature Schemes v2 and v3 matched the pinned public signer. This was
an in-place install over the earlier **same-version candidate**, separate
from the public-to-candidate upgrade described above. Home showed off after
replacement; protection was explicitly re-enabled.

The only enabled list was a custom one-rule hosts file selected through the
Android picker, not a regional list. Turning it off and tapping Apply gave
www.iana.org two IPv4 addresses. Re-enabling it and tapping Apply gave zero
answers with the AdAway blocking authority. The control www.ietf.org returned
two addresses throughout. These were matching-ID DNS responses through the
emulator's AdAway tunnel, not cached browser results. A system lookup directly
after re-enabling on the earlier candidate had briefly returned a cached
address; we do not promise that all client caches clear instantly.

Five rounds of 40 rapid Home, More and Sources taps in portrait and landscape
kept the exact custom source enabled. The target still returned zero addresses
after the final round; no AdAway crash or ANR was recorded for the interval.
Chrome rendered the Python documentation and About SQLite pages with protection
on. Project Gutenberg returned a maintenance page on the earlier candidate;
its normal content was not counted as a pass. One earlier landscape harness
assertion mistakenly required an off-screen row; the final run scrolled to it.

Raw UI trees, screenshots, wire answers and process history:
`app/build/reports/audit-2026-11-25/custom-filter-stress-8b/`. The final
local JVM suite passed 747, skipped three, and had no failures; lint found
zero errors and 32 warnings. All 124 host checks passed. No public-artifact,
physical phone, unlimited stress or all-controls claim follows from this test.

## Protected refresh fixes

| Change | Confirmed defect and scope of proof |
| --- | --- |
| `dbb0e249` (POST-VPN-005) | Manual refresh could report success when the VPN start request returned but the tunnel was not established. The full and single-source Android regressions control tunnel state. After establishment, a single-source receipt clears only that source; unrelated edits still need Apply. |
| `dbb0e249` (POST-VPN-006) | Passive refresh could reuse an old VPN DNS decision when every feed returned HTTP 304. It now discards the VPN rule cache even without new downloads. The host regression checks a primed lookup across six service/ownership combinations without preparing or starting a VPN; this is not a signed all-304 wire capture. |
| `82937ddc` (POST-VPN-007, [#27](https://github.com/stevesolun/AdAway/issues/27)) | Manual Update All twice acknowledged an enabled source with zero imported rules when it was disabled during import and restored before completion. It now checks the actual importer's receipt. The regression uses a controlled tunnel. POST-VPN-004 above was the worker, not this manual path. |
| `aa04d13b` (POST-VPN-008) | A successful Apply could lose its queued confirmation while the installing banner was dismissing. Forced garbage collection reproduced the missing banner; retaining it passed five consecutive runs of the exact regression. This was lost feedback, not a failed import. |

## Earlier signed controls

The `64cbc505b` candidate also exercised a user-entered Disconnect simple_ad
URL with all defaults off. Two catalog checks survived rotation; browser-format
lists opened compatibility review. For MoaAB and Hosts Blocking, cancel then
confirm reported Added 2, Already 0, No URL 0, Unsupported 0. Show subscribed
listed both. Removing just those two kept Disconnect enabled: doubleclick.net
had zero address answers and example.org had two. This bulk run was not
repeated on the combined APK.

On the signed `82937ddc` APK (SHA-256
`a86f95c0aad1c3d57e57a6e5d98ba9d96989d3473ef7dc9d4f69aebf33f77828`),
the same sole URL survived the same-version upgrade. After explicit VPN
consent, filter on/off/on gave 0/1/0 target answers and 2/2/2 control answers.
Chrome rendered Ynet (1,195 nonempty text nodes) and About SQLite (104).
Ordinary taps passed here, but this APK **lost Apply** when the user removed
the app task with Disconnect checked, never updated and showing zero hosts.
The target still resolved; manual Update All recovered blocking.

## Signed new-task checks

[#29](https://github.com/stevesolun/AdAway/issues/29) (POST-VPN-009) concerns
a *new* task with no saved activity state, not the earlier same-task return
(DEEP-SRC-GROUP-006). The local fix keeps a fingerprint of the last confirmed
selection across task removal. Two distinct local signed APKs exercised it:

| APK (SHA-256) | Fresh task before Apply | After Apply and in a second fresh task |
| --- | --- | --- |
| Isolated #29 fix on `82937ddc`: `25165b9bb14a39545f2adb8244b4999f050ac35b3e82fe478963a4ccd5313939` | Disconnect checked, 0 hosts, Apply visible. Target/control address answers: 1/2. | About 3k hosts; 0/2 after Apply and again on reopen, without a new reminder. |
| Combined `aa04d13b` + local #29 + test-only #26: `8b0d39a3d05439ed85ed16b35024b87022e9077ad2da1ff998c5f8511d6ea150` | Applied empty baseline: 1/2. Re-enabled without applying, removed task and reopened: checked, 0 hosts, Apply visible, still 1/2. | About 3k hosts; 0/2 after Apply and 0/2 on reopen with no reminder. Chrome rendered Ynet (1,210 nonempty text nodes) and About SQLite (104) with protection active. |

Target = doubleclick.net; independent control = example.org. Both blocked
captures after Apply and after reopen on each APK returned AdAway's DNS
authority. These were fresh VPN queries, not an inference from the checkbox
or the browser. The two page loads do not measure cosmetic ad removal.

Raw evidence is local under `app/build/reports/audit-2026-11-25/`:
`final-public-filter/RESULT.md` describes the earlier runs.
`final-public-filter/fixed-reopen/{cold,reapply,settled}.txt` and its
`wire-*.json` record the first fix. Separately,
`final-refresh-feedback/fixed-reopen/regression-class.txt` passed 20/20
on private API 34 instrumentation. For the combined APK, see
`final-public-filter/combined-aa04/{check-cold-enabled,apply-after-reopen,check-cold-settled}.txt`,
the adjacent wire JSON and `browser-results.json`, and the corresponding
`final-refresh-feedback/combined-aa04/` build and test logs.

## Test limits

- [#26](https://github.com/stevesolun/AdAway/issues/26) (POST-QA-DISC-001)
  is test-only. A 128-pixel landscape window offset made the injected tap
  miss the checkbox. It failed in the earlier 705-case run and three replays;
  correcting screen coordinates passed the exact check on the unchanged app
  and the 15-case Discover class, including contrast checks. See
  `final-refresh-feedback/{landscape-issue.md,tap-diagnostic-result.txt,tap-scope-suite.txt}`.
- [#28](https://github.com/stevesolun/AdAway/issues/28) remains unexplained.
  Home's slow-startup/manual-check case failed once in the earlier full run;
  Home passed in each of three paired replays, and its whole class passed 5/5. No
  confirmed cause, fix or new defect count. See
  `final-refresh-feedback/{complete-device-final-stable,control-replay-1,control-replay-2,control-replay-3,startup-whole-class-control}.txt`.
- The combined full run finished in 320.586 seconds: 688 passed, 21 assumption
  skips, no failures, including the previously intermittent Home case passing
  this time. See `final-refresh-feedback/combined-aa04/all-device-tests.txt`.
  In a disposable Git snapshot with the same changes, the complete JVM run
  reported 753 passed, 3 skipped and no failures; all 124 host checks passed.
  Android lint reported 24 warnings and no errors. See `combined-aa04/`
  logs `jvm-lint-indexed.txt` and `host-repository.txt`. The Git metadata was
  needed by checks which inspect the source archive and tracked files.
  Skips are not passes and this run does not establish the cause of #28. Neither
  local signed APK is hosted or proves future release bytes. Stable release,
  physical/OEM and root behavior are not signed off. The full audit stays open.
