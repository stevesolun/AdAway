# Signed upgrade: Android 14, non-root VPN

The [final signed phone candidate](#final-signed-phone-candidate) has the
current checksum and its own test results. Earlier builds below are retained
to distinguish the sources, Android sessions and fixes each one exercised.

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
| Ordinary DNS control `www.wikipedia.org` | One alias (CNAME) and one IPv4 address (A) from the same VPN DNS endpoint. |

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
advertising.microsoft.com or media.fastclick.net, and a CNAME plus one IPv4 address for Wikipedia.
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
VPN queries gave zero A answers for media.fastclick.net and a CNAME plus one IPv4 address for Wikipedia.
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
for that host, three for Debian and a CNAME plus one IPv4 address for Wikipedia. See `q14-sites.json`,
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

## Filter editor recovery candidate

Signed APK from `fb11c0976`, SHA-256
`88247c4b1b01eb0cec4251db77f1a67934d2bc20ad110d809217bd4f92321e65`.
On the signed public beta the real Documents picker followed by Cancel and
switching back to URL cleared the displayed address. The public beta also
opened the scheduling dialog against an unsaved location. Installed the
candidate directly over 13.5.6b: app ID 10194, first installation time, the
enabled Dan Pollock source, 13k rules and 25 skipped lines were retained.
On the new version the same real picker round trip kept the address. The
editor displayed “Save the new location before changing its schedule”
instead of opening the schedule dialog. The debug regression independently
verified both preferences and the database after Back and after a saved rename.

With VPN re-enabled from Home, Chrome (cache disabled) rendered Linux Kernel
Archives (HTTP 200; 104 links) and IANA Reserved Domains (HTTP 200; 60 links).
The listed media.fastclick.net failed DNS in Chrome and had zero direct VPN
answers. Direct queries returned one IPv4 address for Kernel and two for IANA.
Evidence: `q18-public-*`, `q18-candidate-*`, `q18-signer-comparison.txt`,
`q18-editor-dns.json`, `q18-editor-sites.json` and `q18-editor-green.txt`.

## Conditional filters on the phone candidate

Superseded local signed `13.5.7b/130507` from app commit `1097353af`:
`45467825b03f832f4c0d2ae1602f8871016804f4fcbaf78a65ac0f16cbc5e923`.
APK signature v2 and v3 verified against the public certificate.
On public 13.5.6b, entered `https://v.firebog.net/hosts/Easyprivacy.txt`
through the Add custom source dialog and applied it. The in-place update
kept the original install time, app ID 10194 and the enabled 43k privacy
filter. Home restarted protection without requiring root.

Then imported the [local fixture](conditional-filter-fixture.txt) through
Android's actual document picker. Sources showed **1 host, 2 skipped**.
These are fresh A queries sent directly to this VPN, not browser cache hits:

| Host | Before adding file | File enabled | File disabled and applied |
| --- | --- | --- | --- |
| www.example.com (unconditional) | 2 IPv4 addresses | No answers | 2 IPv4 addresses |
| www.kernel.org (conditional) | 1 IPv4 address | 1 IPv4 address | 1 IPv4 address |
| www.iana.org (else branch) | 2 IPv4 addresses | 2 IPv4 addresses | 2 IPv4 addresses |

With the file enabled, actual Chrome loads (cache disabled) rendered Kernel
(HTTP 200; 104 links) and IANA (HTTP 200; 60 links). Only www.example.com
failed with `ERR_NAME_NOT_RESOLVED`, as the local test requested.
The English category summary said “1 hosts”; that copy defect is separately
logged and does not change enforcement. The 5M matched stress results are
from the earlier staging fix, not a newly repeated 5M run on this APK.
Evidence: `q19-{before,after,restored}-dns.json`, `q19-conditional-sites.json`,
`q19-condition-visible`, `q19-retained-filter`, `q19-*-package.txt`.

## Domain Checker phone candidate

App commit `07d396dd0`, `AdAway_13.5.7b.apk`, version code 130507.
SHA-256: `0fe4ac97d2dfa993653b9cac2f6f956dcb1038993731da32389a7c0a2e6fb11c`.
APK v2/v3 verified; certificate SHA-256
`9bf3a40102638ed9271f4dea6225a2d66e5d2c5f3e5204cc125376288f7f9595`.
The release package was not debuggable. This is a local candidate, not a GitHub release.

The compiled manifest had seven explicitly exported components. Only Home
had no component permission. The VPN, tile and job services required Android's
respective bind permissions, the command receiver required the app's signature
permission (compiled protection level 0x2), and both library diagnostic
receivers required DUMP. Neither debuggable nor testOnly was enabled.
This is a packaged permission check, not an unrestricted security signoff.
Local evidence: `q23-manifest-surface.txt`.

On public 13.5.6b we entered `https://v.firebog.net/hosts/AdguardDNS.txt`
as a custom URL and applied it. Installing the candidate in place kept the
original Android app ID (10197), first-install time (02:00:53) and selection.
The selected list showed 190k hosts (a rounded badge). We restarted the VPN
from Home. No root, debug instrumentation or database edits were used for
the following rule and browser checks:

| Stage | Fresh IPv4 answer counts: example.com / kernel.org / iana.org (all www) |
| --- | --- |
| No test rule; Domain Checker says Unknown | 2 / 1 / 2 |
| Immediately after tapping Block, while still applying | 2 / 1 / 2 |
| Visible Blocked confirmation and refreshed result | 0 / 1 / 2 |
| Delete Rule confirmation and refreshed Unknown result | 2 / 1 / 2 |

Android Chrome, with HTTP cache disabled, rendered Debian (HTTP 200, 37 links)
and IANA Reserved Domains (HTTP 200, 60 links). The listed media.fastclick.net
and our deliberately blocked www.example.com each failed DNS. These are
ordinary visits; we do not attribute every blocked request to the new feed.
The Block confirmation remained visible in landscape and after returning to
portrait; the next Check cleared it. Delete Rule showed its own result.

Finally we disabled only the 190k custom source, applied, and selected the
[conditional fixture](conditional-filter-fixture.txt) via Android's real file
picker. Sources showed **1 of 2 enabled, 1 host**, with two skipped rules.
Fresh VPN answer counts were again **0 / 1 / 2**. This verifies both the
singular summary and the difference between unconditional and browser-only
rules, independently of the larger filter. Raw captures: `q23-*-package.txt`,
`q23-signed-chrome.json`, `q23-*-dns.json`, `q23-block-confirmation-*.png`,
`q23-new-check.xml`, `q23-deleted-rule.xml` and `q23-singular-host.xml`.

## Idle and locale phone candidate

Local **AdAway_13.5.7b.apk**, version code **130507**, app commit `17a7d1415`.
SHA-256: `9120fd846a12a010ceae073cb0043420b827bf46e24241587d4b041920828b4f`.
APK v2 and v3 verified with the same public certificate as above.
Not a published GitHub artifact. The private signing key is not in this repository.

On API 34 the public beta, the locale fix, then this candidate were installed
in place. The selected Dan Pollock URL survived, including its name GeneralQA.
Disabling, applying, re-enabling and applying restored its displayed 13k hosts.
After starting non-root VPN protection, Android Chrome rendered Debian and
IANA Reserved Domains (HTTP 200; 37 and 60 links), while media.fastclick.net
failed with ERR_NAME_NOT_RESOLVED. We did not attribute every block to this
single list: the three default sources were enabled, too.

The same binary handled an exact redirect and a Log-created block in both
directions. See the [signed interactions, DNS answers and idle measurements](pending-apply-and-locale.md).

## Discover order phone candidate

Local **AdAway_13.5.7b.apk**, version code **130507**, app commit `8e2eace32`.
APK SHA-256: `7237fe228acb59c7b8678f0ee9a4052d33d9f69122a276a999ebeff9ae6347be`.
The installed base APK and Downloads copy matched. Signatures v2 and v3
verified with certificate SHA-256 `9bf3a40102638ed9271f4dea6225a2d66e5d2c5f3e5204cc125376288f7f9595`.
The in-place public -> intermediate -> final chain retained the app identity
and repaired custom URL. [Discover selection, removal and DNS outcomes](discover-pick-order.md).
On a separate fresh install of these exact bytes, the app imported the custom
URL again; Android Chrome loaded kernel.org and RFC Editor and failed DNS for
doubleclick.net with VPN protection active.
This is a local phone-test build; no GitHub publication is implied.

## Final signed phone candidate

The current local 13.5.7b APK (130507), app commit `9968265d4`, is
`34765cb44edf7f1ed8daaec844364620760f9209b3a05b36a450dd3c61d06ee7`.
Its installed bytes matched on Android 11 and 14, and its v2/v3 signer
matched the public beta. [Independent filter, two web pages, on/off DNS and
saved schedule](restored-phone-release.md); [offline Home and notification,
Wi-Fi/LTE recovery, DNS and Chrome](vpn-network-recovery.md#consolidated-signed-ota).
Package replacement stopped protection; one Home tap resumed the VPN
without new consent. This private candidate is not a GitHub release.

### Previous source-selection candidate

Local **AdAway_13.5.7b.apk**, app commit `6da4194eb`, version code **130507**, SHA-256
`e08123d932628ec77d6f38ed74c3727eca3494c2d94ab68f315cb5cf8a1e3570`.
The installed base APK matched. APK schemes v2 and v3 verified with the pinned
certificate `9bf3a40102638ed9271f4dea6225a2d66e5d2c5f3e5204cc125376288f7f9595`.
See the [exact-build test](source-category.md#phone-candidate) for source and
schedule controls, restore after process eviction, and DNS and browser results.
The [earlier public-to-local chain](#discover-order-phone-candidate) is
historical evidence. This is not a newly published GitHub artifact.
