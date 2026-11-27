# Non-root Android VPN: 13.5.8b candidate

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
- **Selection changed during import (POST-VPN-004):** Two deterministic Android
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

The final local app at `64cbc505b`, SHA-256
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
