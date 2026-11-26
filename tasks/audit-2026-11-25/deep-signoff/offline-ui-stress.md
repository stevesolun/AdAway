# Offline UI stress: signed Android 14 builds

**Result (2026-11-26):** No AdAway crash or ANR observed on either binary. On the consolidated APK, the Safe Mode profile, source enablement, DNS recording setting and two update preferences matched their expected states after navigation. One intermediate Home-to-Discover checkpoint did not match; it is not counted as a pass. This is a bounded offline UI result, not network or VPN filtering signoff.

## Binary and device

Both APKs came from `app/build/outputs/apk/directRelease/app-directRelease.apk` at different times. Each was pinned outside the checkout, verified with APK Signature Schemes v2/v3, and independently matched against the SHA-256 of the **installed** `base.apk` read from the path returned by Android. No build, signing credentials or root access were used.

| Run | Installed SHA-256 | Installation |
|---|---|---|
| Intermediate | `c0d6af7ef3e820e2f6c0adb7e42e36fbd2109180a4961d94bbf92236e842c8f5` | Clean install; non-root VPN onboarding and Android consent. |
| Consolidated | `41698caa22f43daca8fde1cccbfa03016d98f02fcaafa589ce91930df74ac3dd` | In-place upgrade; installed digest matched before and after stress. |

One fresh Pixel 2 AVD, Google APIs API 34 ARM64, 1080 x 1920 at 420 dpi. Ports 5580/5581 were checked locally before launch. Every device command explicitly targeted `emulator-5580`; no other serial was used. Airplane mode was enabled and Wi-Fi and cellular data disabled **before installing or launching AdAway**. At preflight, the upgrade boundary and the end: no active default network, Wi-Fi disabled, no cellular data connection, and both IPv4/IPv6 route lookups to documentation addresses failed as unreachable. These were local route lookups, not packets sent to a site. The end snapshot also shows wlan0 and eth0 down. The generic mobile-data preference still read 1 at preflight; it was not used as evidence of connectivity.

AdAway's protection switch was on after consent. The upgrade stopped the prior process; on consolidated Home the switch initially showed off and was explicitly re-enabled. Both builds then showed "VPN enabled; no blocking rules loaded" and had a foreground VPN service. Android showed no VPN tunnel/default network and Home also reported "VPN selected but not running." Active tunnel operation, DNS traffic and filtering were **not** demonstrated. No live catalog or source download was exercised successfully.

## Runs and oracles

Use the English UI in portrait. D/S/M/H below mean the real Discover/Sources/More/Home bottom-tab centers, x = 405/675/945/135 respectively at y = 1752. Taps were at least 270 ms apart, with an AdAway window-focus check before and after each seeded input. Tab selection was checked from the UI tree every eighth tap and at the end, not inferred from the package name. The literal traces below are sufficient to replay the inputs without depending on a random-number implementation. No Monkey, system keys, notification shade, edge gestures or external launches in the bursts.

| Binary / seed | Actual inputs | Observed result |
|---|---|---|
| Intermediate / 3401 | With AdAway Official disabled in Sources, sent `DSMDSMHD`; stopped at input 8. | **Inconclusive navigation.** Expected Discover; screenshot/tree showed Home selected. The last tap had a different app-owned focused window from the normal Home window. The exact overlay/cause was not established. Package foreground held; no crash or ANR. |
| Intermediate / 3411 | Start Sources; `DMDMDMSDMSDSDMDMDS` (18 taps). | Checkpoints selected D/M/S. AdAway Official remained unchecked and Ads showed 2 of 3 enabled, 0 hosts. The consolidated build still showed both values immediately after upgrade. |
| Consolidated / 3412 | In Discover, confirm Custom + catalog unavailable, then tap Safe Mode. From both filters off, tap `UCCUUCUCUCCC` (U = Show subscribed, C = DNS-safe only). Then `MDSMSDMDMDSDMSMDSMD` (19 tabs). | Both switches ended on as predicted. Safe Mode survived leaving and returning; the offline catalog stayed unavailable. On Sources, AdAway Official was checked again and Ads showed 3 of 3 enabled, 0 hosts. Filter-switch persistence across tabs was not asserted. |
| Consolidated / 3413 | More > DNS Log, initially not recording. `RTRRRTTTRSTSTST`: R = center pull-to-refresh (525,520 to 525,1250 in 350 ms), T = recording (964,1741), S = sort (889,146). | Recording ended on. Used the in-app Up arrow, reopened DNS Log and verified "Pause DNS recording" again; toggled off and verified "Start recording DNS requests". The first assertion expected different wording; the saved tree and a corrected assertion verified the actual control without replaying the burst. Empty-log sort order and capture of live requests were not asserted. |
| Consolidated / 3414 | More > Preferences > Automatic updates. `BBABBAAAAB`: A = **Application updates** startup check (960,681), B = include beta releases (960,963). | Startup check changed on to off and beta releases off to on. Both exact switches still matched after navigating Up and reopening Automatic updates. No update delivery or scheduling claim. |

The four completed seeds cover 74 stress inputs; the stopped eight-input prefix is separate. Including setup, verification and navigation, the trace contains 101 taps (including the explicit VPN consent) and five interior swipes. There are 30 paired screenshots and UI trees. Foreground checks apply to sampled inputs, not to every millisecond between them.

## Diagnostics and handoff

Android's crash buffer was empty at the intermediate and consolidated endpoints. The events buffer still covered boot through the last pass, with no `am_crash` or `am_anr`. The intermediate package had no historical exits before replacement. The final package exit history contained only the intermediate PID (3338), reason 16, **PACKAGE UPDATED**, at 03:41:41 local time. The consolidated PID (5823) remained alive with Preferences in front. No app crash, native-crash or ANR DropBox entry was found. These are observations for this device and interval, not evidence about longer soaks, other APIs, system UI, or online traffic.

- Ignored evidence: `app/build/offline-ui-stress/5580-AT07EDSy/`. See `events.jsonl` for timestamped inputs and assertions; `seed-3401-step-08-D.{png,xml}` records the expected-D/actual-Home mismatch. Numbered `100-` through `114-` captures belong to the consolidated APK. Crash buffers, event logs, exit histories, installed hashes and offline state snapshots are stored alongside them.
- Left running and offline for coordinated cleanup: **emulator-5580**, AVD `offline_ui_5580`, launchd job `local.adaway.offline-ui-5580.AT07EDSy` (PID 31153 at handoff).
- Unique `ANDROID_AVD_HOME`: `/var/folders/cj/j956f9v920b8wk3wvd8ms2nh0000gn/T/adaway-offline-ui-5580.AT07EDSy`. This directory contains the disposable AVD and both pinned APKs. No emulator shutdown, staging or commit.

Subsequent cleanup: after collecting the final results, the parent stopped
the emulator, removed its launch job, AVD, temporary APK copies and directory.
