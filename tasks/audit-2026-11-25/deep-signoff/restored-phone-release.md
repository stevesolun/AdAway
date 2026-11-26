# Signed in-place upgrade with a nonregional source

**Result:** Source and rule counts survived public 13.5.6b -> private signed 13.5.7b on API 34, and the installed bytes matched the supplied artifact. With the VPN enabled, two independent HTTPS sites rendered in Chrome and a listed hostname was blocked in a repeatable on/off control. **Continuity caveat:** protection was active before installation but off on the candidate's first Home screen. A tap re-enabled it. This is not a pass for uninterrupted or automatic VPN restart.

## Binary and baseline

- Only `emulator-5576` was used. Started on public `org.adaway` 13.5.6b / 130506 (installed SHA-256 `75f064326e8e2059808ffcd656448f2deb1d2825ac0f482f09eb66913681f621`).
- Through Sources, disabled all three Ads defaults and URLhaus Malware, then used **Add custom source** to add **Dan Pollock** with the [public zero-address hosts file](https://someonewhocares.org/hosts/zero/hosts). Home reported **Protection active**, **1 up-to-date source**, **0 outdated**, **13.1k blocked**, **75 allowed**, **0 redirected**. The disabled URLhaus row retained an earlier HTTP 410; it was not the active source.
- Installed the supplied `app/build/reports/audit-2026-11-25/platform-restore/AdAway_13.5.7b.apk` in place with `adb -s emulator-5576 install --no-incremental -r`. No uninstall, data clear, rebuild, clean, signature override, or public-updater claim.
- Host artifact and independently hashed on-device `base.apk`: **`34765cb44edf7f1ed8daaec844364620760f9209b3a05b36a450dd3c61d06ee7`**. Package became 13.5.7b / 130507; original first-install timestamp remained unchanged. APK signer SHA-256: `9bf3a40102638ed9271f4dea6225a2d66e5d2c5f3e5204cc125376288f7f9595`.
- First launch went directly to Home with unchanged counts. Sources showed **Ads: All disabled**, **Custom: 1 of 2 enabled**, Dan Pollock checked with **13k** hosts and **25 skipped**, URLhaus unchecked. No resubscription, restore, or manual source update was needed. The counts above are the UI's displayed precision.

## Chrome and DNS

Android reported an active AdAway VPN on `tun0`. In the actual `com.android.chrome` browser (113.0.5672.136), without an account, opened independent sites over HTTPS:

| Site | Browser observation |
| --- | --- |
| [Debian](https://www.debian.org/) | Secure address bar and rendered “Debian -- The Universal Operating System”, community and operating-system content. |
| [Python](https://www.python.org/) | Secure address bar and rendered “Welcome to Python.org”, PSF, Docs and PyPI navigation. |
| [W3C](https://www.w3.org/) | Secure connection to the site, but the observed page was a verification challenge. No bypass attempted; not counted as a successful content render. |

The retained list explicitly contains `0.0.0.0 pagead2.googlesyndication.com`; `www.debian.org` is not listed. The device's normal hostname resolution (not an outside DNS service) was tested using `ping -c 1 -W 1`. The result of interest is name resolution, not ICMP latency:

| State | Listed hostname | Debian control |
| --- | --- | --- |
| Public 13.5.6b, protection on | Unknown host | Resolved |
| Signed candidate, first launch off | Resolved to 142.251.153.155 | Resolved |
| Candidate, switched on | Unknown host | Resolved |
| Candidate, switched off again | Resolved to 142.251.155.155 | Resolved |
| Candidate, switched back on | Unknown host | Resolved |

This demonstrates system-resolver filtering for this list and these names; it does not establish that every Chrome request uses system DNS, or cover all browsers, lists, transports, or Always-on VPN. No product edits.

## Retained evidence

Proof directory: [restored-phone-release-5576](../../../app/build/reports/audit-2026-11-25/restored-phone-release-5576/).

- Binary: `public-installed-sha256.txt`, `candidate-installed-sha256.txt`, `candidate-cert.txt`, `candidate-install.txt`, both package snapshots.
- Selection and continuity: `source-after-add.png`, `public-baseline-home.xml`, `candidate-first-home.xml`, `candidate-sources.xml`, `candidate-custom-source.xml`.
- Runtime: `chrome-*-rendered.xml`, three browser screenshots, `chrome-version.txt`, `candidate-vpn-active.txt`, paired DNS captures, and `candidate-final-on.png`. Public list snapshot: `pollock-hosts.txt`, SHA-256 `8a3738c1e233700f6f4f35564cdc0c64efdd08d1082ca92d497c293174c65dcf`.

**Handoff:** 5576 left running on the supplied 13.5.7b, Home open, protection active, Dan Pollock the only enabled subscription. No commands to 5578.

## Saved weekly schedule

On the same verified signed APK, saved the current selection as **Pollock Weekly** in Sources and set it to **Saturday, 8:45 PM**. Reopened it independently via **Manage schedules > Edit filter set schedule > Pollock Weekly**: **Weekly** was checked (Off and Daily unchecked), **Saturday** was checked, and the time picker displayed **8:45** with **PM** checked. Tapped **Cancel**, then Back; Manage schedules still showed **1 scheduled set**, and Sources still showed Ads all disabled, Dan Pollock checked and URLhaus unchecked. [UI evidence](../../../app/build/reports/audit-2026-11-25/restored-phone-release-5576/weekly-profile/) includes the three reopened picker states and membership before and after. This verifies saved values, not execution of the weekly job.

## Final offline input run

- **3,000/3,000 Monkey events injected**, zero reported drops, seed **5576130507**, **30 ms** throttle, **54.00 s** wall time (53.853 s reported disconnected). Package restricted to `org.adaway`; touch and directional-navigation events only. No default network or IPv4/IPv6 route at either endpoint. No external site loaded during the burst. App permission grants and audio stream volumes matched before/after.
- Normal completion; no app crash or ANR observed in Monkey or the Android crash/event buffers. This was one seeded input trace, not exhaustive path coverage.
- Random inputs enabled 13 extra lists. After reconnect, **Apply filter set > Pollock Weekly** offered to disable 13 and keep one. The first Apply reported an operation in progress; the retry confirmed **Protection changes applied**. Restored the active selection, not every inactive preset/catalog entry. Final Home: **Protection active**, **13.1k blocked**, **75 allowed**, **0 redirected**, **1 up-to-date**, **0 outdated**. The system resolver rejected `pagead2.googlesyndication.com`; `www.debian.org` resolved to 140.211.166.200 and replied.
- [Raw proof](../../../app/build/reports/audit-2026-11-25/restored-phone-release-5576/offline-inputs/): `monkey.log`, network and diagnostic snapshots, `restore-preview.xml`, `restore-final-apply.xml`, `final-home.png`, and paired `final-dns-*.txt`. Left 5576 running on Home, online and idle.
