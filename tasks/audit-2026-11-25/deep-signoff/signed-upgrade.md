# Signed upgrade: Android 14, non-root VPN

Installed the verified public 13.5.6b APK on disposable API 34 emulator-5574.
In the regular UI, selected VPN, accepted Android's consent dialog, and added
`https://someonewhocares.org/hosts/zero/hosts` under the name **QA Global**.
Sources showed three enabled defaults and one enabled custom source (13k).

Installed the locally signed 13.5.7b/130507 candidate in place, without
clearing app data. Android retained UID 10195 and first-install time
2026-11-25 20:17:09 local; last-update time changed to 20:20:25. The custom
source still showed the original name, checked switch and 13k badge. No
new onboarding or VPN consent prompt appeared. Android had stopped the
tunnel during replacement; Home correctly showed protection off. One tap
restarted it. Android then reported AdAway owning the connected tun0 VPN.

| User-visible check after restarting protection | Observed |
| --- | --- |
| Wikipedia, BBC, MDN in Android Chrome, browser cache disabled | All three main documents returned HTTP 200 and rendered; 384, 233 and 152 links respectively. |
| Listed host `media.fastclick.net` | No A answers from the VPN DNS endpoint; Chrome returned `ERR_NAME_NOT_RESOLVED` with no document response. |
| Ordinary DNS control `www.wikipedia.org` | Two A answers from the same VPN DNS endpoint. |

APK SHA-256: `30633336ec23ca8d188c48b70c123f6efafda4cad1830bb7ca72a75c24ec27b4`.
The single signer matched the pinned public certificate
`9bf3a40102638ed9271f4dea6225a2d66e5d2c5f3e5204cc125376288f7f9595`;
APK signature schemes v2 and v3 verified. Local evidence under
`app/build/reports/audit-2026-11-25/deep-general/`: `r00-*` through `r07-*`,
`public-before-package.txt`, `candidate-after-package.txt`,
`candidate-upgrade-tunnel.txt`, `candidate-upgrade-dns.json`,
`candidate-upgrade-sites.json` and `signed-build-inputs.sha256`.
This is not a verification of a GitHub release artifact or an OEM phone.

## Bounded-import candidate

Repeated the public-to-local upgrade after changing the staging of large
imports. APK SHA-256:
`1ddf22500234010694c4c399ad1167a3303768fed029dbeba9aaa5d5eecdd97b`.
The signer still matched. UID 10196 and the original first-install time
survived. The enabled **Public Upgrade QA** filter still showed 13k rows;
one Home switch tap restored VPN protection. Then added HaGeZi Multi LIGHT
through the source editor. Both custom rows were checked (86k and 13k).
In Android Chrome, Python.org, NASA and Wikipedia returned HTTP 200 with
220, 284 and 384 rendered links; telemetry.mozilla.org failed DNS. Direct
VPN DNS queries returned no A answers for telemetry.mozilla.org,
advertising.microsoft.com or media.fastclick.net, and two for Wikipedia.
Evidence in the same directory: `p00-*` through `p02-*`, `b00-*`
through `b03-*`, `bounded-package.txt`, `bounded-upgrade-tunnel.txt`,
`bounded-multifilter-dns.json`, and `bounded-multifilter-sites.json`.
The 5M repeat uses a separate instrumentable build and emulator.

## VPN ownership review build

Upgraded a separately prepared public 13.5.6b install to the signed local
build from `b6c6a5a78` (SHA-256 `24512a34af3aaf8a4c66bbc0822201b507556130154af42eba98c904df18f775`).
Android verified the update in place: UID 10199 and first install time
remained unchanged. Sources still showed the checked Dan Pollock custom
list (13k; 25 skipped); a Home switch tap brought protection online. Direct
VPN queries gave zero A answers for media.fastclick.net and two for Wikipedia.
The certificate matched the public signer. Local evidence: `q00-*` to
`q03-*` in the same directory. Not a published artifact.

## Deep-QA phone candidate

Local `AdAway_13.5.7b.apk` SHA-256:
`ec78acf3baab00f7392ee317acdecfbe311155a2362ec2b41d57a3109034cfff`.
The signed update retained the same UID, first install time and selected
13k custom list through the public-to-candidate upgrade chain. Home showed
protection active after starting the VPN. The Android 14 browser, with its
cache disabled for the check, rendered Debian (37 links), NASA (284), and
Example Domain (1); all returned HTTP 200. The listed media.fastclick.net
failed with ERR_NAME_NOT_RESOLVED. Direct VPN DNS returned zero A answers
for that host, three for Debian and two for Wikipedia. See `q14-sites.json`,
`q14-dns.json`, `q14-retained`, `q14-package.txt` and `q14-signer.txt`.
This tests domain filtering, not cosmetic rules or every list format.

Signing all three variants with release configuration available confirmed
the debug app and instrumentation used the same **development** certificate
and only directRelease used the pinned public certificate. The build refused
an intentionally misconfigured debug variant before packaging. The release
APK was non-debuggable and byte-identical before and after the signing policy
fix. See `q15-actual-signers.txt` and `q15-refusal.txt`.
An additional scan checked the manifest of each APK under the local
`app/build/reports/` tree. All 21 marked debuggable verified with the
development certificate; none of those archived files used the release
certificate. This is limited to that tree, not a scan of other machines
or release history. See `q17-archived-debug-signers.txt`.
