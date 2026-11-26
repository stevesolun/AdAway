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
