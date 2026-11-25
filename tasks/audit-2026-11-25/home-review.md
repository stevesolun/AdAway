# Home Controls and Initial Download

## Scope and Findings

The missing Home protection control was reproduced by a new connected test before
the fix. The replacement is an inline switch, not a floating button. Enabling uses
the platform consent flow when required; completion is an explicit enable request,
not a toggle of potentially changed state. An atomic operation claim protects
against repeated taps before a background task begins.

`main-bugs.csv` records closure and remaining Home findings separately. The leak
status card does have a settings click handler; it is not an inert-control bug.
The scheduled progress terminal branch and misleading empty-rule protection status
remain open. This is not full Home workflow sign-off.

## Executed Checks

- Baseline: `inlineProtectionControlResumesPausesAndSurvivesRecreation` failed with
  "Home must offer an inline protection control."
- Patched Home class: four tests passed, then the expanded Home regression group
  passed 11 connected tests, zero skipped/failures.
- Full JVM suite at the Home-only patch: 624 tests, three skipped, zero failures
  or errors. Android lint: zero errors and 33 warnings. Later agent/catalog changes
  need a new combined run; these counts do not validate edits made afterward.
- Inventory helper: five Python tests passed.
- API 34 emulator `adaway-review-ci-api34`, serial `emulator-5556`: normal
  onboarding consent accepted. After explicitly revoking VPN authorization with
  appops, Home switch-off showed Protection off. Switch-on opened the real system
  request. Cancel kept the switch off and showed the denial explanation. Retry,
  rotate the system prompt to landscape, then OK produced a live `tun0` interface
  and active status. Landscape scrolling exposed the checked switch. Original
  rotation settings were restored (accelerometer 1, user rotation 0).
- This consent check did not prove ad blocking: onboarding had failed at a retired
  provider endpoint, leaving zero active block entries.

Local generated evidence: `app/build/reports/audit-2026-11-25/`, including
`adaway-audit-home-red.log`, `adaway-audit-home-green.log`,
`adaway-audit-home-regression.log`, `vpn-consent-denied.xml`,
`vpn-consent-rotation-granted.xml/png`, `urlhaus-http410.png`, and
`onboarding-download.log`. Generated screenshots/logs are not committed.

## Retired Default Source

Fresh onboarding on 2026-11-25 downloaded three lists (91,774 accepted rows), but
URLhaus returned HTTP 410. With no prior URLhaus rows to carry forward, the source
transaction correctly preserved the previous empty active dataset. The generic
connectivity dialog obscured the provider-specific cause; Sources showed HTTP 410.
Manually disabling that failed source and pressing Apply successfully loaded the
other lists. The code change removes the dead URL from built-in choices only;
existing users' selections and custom URLs are not silently edited.

External evidence: [URLhaus API](https://urlhaus.abuse.ch/api/) requires an Auth-Key
for hostfile exports; its old public hostfile endpoint returned 410 during this
audit. Fresh-install verification of the changed catalog is still pending.

Platform references: [VPN preparation](https://developer.android.com/develop/connectivity/vpn)
and [activity-result restoration](https://developer.android.com/training/basics/intents/result).

## Browsing and URL Replay

After explicitly disabling the broken source in the emulator, Apply loaded about
81.7k distinct blocked hosts. Ynet and Walla rendered news content with VPN active;
DNS proxy logs include both blocked and allowed queries. These observations do not
prove all ads are removed or attribute each logged query to a particular site.
KSP reached a Cloudflare human-verification page; its store workflow remains open.

The actual Sources > Add custom source flow accepted an empty optional name and
`https://raw.githubusercontent.com/easylist/EasyListHebrew/master/hosts.txt`.
Saving automatically downloaded it: HTTP 200, 182 accepted rows, one skipped line,
three retained sources returning 304, no failed sources. Ynet and Walla content
remained reachable afterward. See `site-workflows.csv`; these runs used the
Home-patch debug APK before the later data/VPN/updater changes were compiled.

Independent review subsequently found three cross-operation timing gaps in the
new Home control. `home-patch-review.md` describes them; fixes and combined retests
are required before this control is considered ready to merge.
