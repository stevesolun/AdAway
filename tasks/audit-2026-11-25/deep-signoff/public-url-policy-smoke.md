# Public HTTPS Admission Smoke

Result: PASS on 2026-11-25, approximately 20:05-20:14 +0200. This is fresh runtime evidence on the requested post-SourceHttpPolicy APK, not reused evidence from an earlier build.

## Build And Isolation

- APK: `app/build/reports/audit-2026-11-25/deep-general/integration-app.apk`, version 13.5.7b.
- SHA-256, verified on both the host artifact and installed `base.apk`: `36ba69c778c64b847c8d30f0f127809ff857c89b9161b190987a498c741113f2`.
- Fresh disposable Google APIs ARM64 API34 AVD `public-url-policy-api34`, only `emulator-5584` / port 5584, launched with `-feature -WiFiPacketStream,-Vulkan`, cold boot and snapshots disabled.
- Selected VPN during normal onboarding and tapped OK on Android's real VPN connection-request dialog. No root, run-as, permission grants, appops changes, database injection, Gradle, or app/test source changes.

## Manual Source And Counts

Used Sources > Add source > Add custom source. Entered `Public HTTPS QA` and [Dan Pollock's public hosts list](https://someonewhocares.org/hosts/zero/hosts), then the form's Apply action. This is neither a Hebrew nor HaGeZi filter.

| Source | Baseline accepted rows | After custom import |
| --- | ---: | --- |
| AdAway Official | 6,540 | Enabled; HTTP 304; unchanged 7k badge |
| Peter Lowe's Ad Servers | 3,558 | Enabled; HTTP 304; unchanged 4k badge |
| StevenBlack Unified | 81,677 | Enabled; HTTP 304; unchanged 82k badge |
| Public HTTPS QA | Absent | HTTP 200; 13,118 accepted; enabled 13k badge |

Baseline pipeline: three updated, zero failed, 91,775 accepted source rows. Custom-source pipeline: three up to date, one updated, zero failed; 13,118 newly parsed source rows and 25 skipped lines. XML comparison confirmed all three default switches and count badges unchanged. No default was toggled or edited.

Final Home showed **Protection active**, **4 up-to-date sources**, **0 outdated sources**, **VPN configuration successfully updated**, 81.7k blocked, 75 allowed, and zero redirected. Source rows overlap; their sum is not a unique blocked-domain count.

## Actual VPN Wire Check

An ordinary UID 2000 UDP socket bound to `192.0.2.1` sent one DNS A query per domain to `192.0.2.2:53`. OS route lookup explicitly selected `tun0`; connectivity state identified that endpoint as AdAway VPN DNS. No model, DAO, or packet-handler methods were invoked directly.

- Listed domain `media.fastclick.net` (present in the public feed): 91-byte matching DNS response, NOERROR, zero answers, SOA authority `adaway.vpn.invalid`, TTL 5. Blocking confirmed.
- Ordinary control `example.com`, through the same endpoint: 61-byte matching NOERROR response with A records `172.66.147.243` and `104.20.23.154`. Normal resolution confirmed.
- Earlier netcat attempts returned no usable response and are not counted as proof. A small unprivileged native UDP probe resolved this harness limitation; its source, decoded results, and raw replies are retained. No HTTP requests were sent to the blocked domain, no private services were probed, and traffic was limited to normal source updates and a handful of DNS attempts.

## Evidence And Cleanup

Evidence directory: `app/build/reports/audit-2026-11-25/public-url-policy-smoke/`.

Key artifacts: `03-consent-ready.png`, `08-defaults-ready.xml`, `11-source-entered.xml`, `16-home-after-source.png`, `18-custom-success.xml`, `source-comparison.json`, `app-final.log`, `installed-apk-sha256.txt`, `device-state.json`, `connectivity.txt`, `dns-wire-final.json`, and the two `dns-wire-final-*.dns` replies. Initial transitional/stale UI captures are not relied upon.

Only emulator-5584 was stopped; its emulator process exited successfully and avdmanager confirmed deletion of this lane's disposable AVD. The final device inventory no longer contained 5584. Other emulators were not operated on. No git stage, commit, or push was performed.

Limit: this proves ordinary public HTTPS admission, unchanged default source state, and live VPN DNS behavior for this build and feed. The listed domain may overlap defaults; the wire result is not claimed as exclusive attribution to the new source. Redirect, hostile-input, other-device, and other-format coverage are outside this smoke.
