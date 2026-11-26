# Pending Apply, rule enforcement and language changes

Non-root Android 14, signed release package. These checks exercised UI controls
and the live VPN, not just counts in a database. Raw capture names below are in
`app/build/reports/audit-2026-11-25/deep-geometry-final/` on the QA machine.
The [phone APK](signed-upgrade.md#final-signed-phone-candidate) is the build
from `17a7d1415`; its predecessor with identical locale changes was `26e890f20`.

## When Apply is pending

On the same 1080x1920 emulator at 420 dpi, portrait, Hebrew and 150% font,
we sampled the cumulative rendered-frame counter every two seconds with
no input after animations settled. This is not a battery measurement.

| Screen / state | Before (three samples) | After (three samples) | Button exercised above prompt |
| --- | --- | --- | --- |
| Sources, change not applied | 7767 / 7892 / 8017 | 260 / 260 / 260 | Open add sheet, Back, Apply |
| Custom Rules, change not applied | 5894 / 6018 / 6125 | 1206 / 1206 / 1206 | Open add dialog, Cancel, Apply |
| Log, change not applied | Not measured | 2029 / 2029 / 2029 | Pause recording, Apply |

Before the change, Sources with no prompt held at 7586, and Rules after Apply
at 7309. The old pending screens also prevented the Android UI inspector
from reaching idle; manual input still worked. Removing a full-screen anchor
while keeping the normal bottom/end gravity resolved the measured redraw.
The regression checks the three actual layout files. See `*-frames.txt`,
`fab-red.log`, `fab-green.log`, and UI captures 44-47, 52-55 and 68-72.

## What the rule actually did

| Action on the signed APK | Direct VPN DNS result |
| --- | --- |
| Apply redirect for release-qa.invalid | Exactly 192.0.2.45 |
| Cancel deletion, then confirm deletion and Apply | Cancel kept the row; after confirm and Apply the name returned NXDOMAIN |
| Record www.example.com, Block and Apply, remove block and Apply | Two A answers before, zero blocked, two restored |
| Enter/retain a general-purpose hosts list | With defaults also enabled: zero A answers for media.fastclick.net, one for www.kernel.org, two for www.iana.org |

Chrome ran separately with HTTP cache disabled. Debian and IANA returned
HTTP 200 and rendered; the advertising host returned no main document and a
DNS error. See `final-browser.json`, `signed-after-dns.json`,
`{redirect,deleted-redirect}-dns.json` and `log-{control,blocked,restored}.json`.
These tests distinguish DNS blocking from cosmetic browser filters; they
are not an assertion that arbitrary filter syntax will work.

The [complete debug suite](idle-fix-android14-methods.csv) separately tested
what happens when a provider or user changes course: an oversized response
and an HTTP 200 containing an error page both preserved working rules. Disabling
a source mid-download did not resurrect it; stopping an update kept active
protection intact (cases 175, 181, 182, 185). Selected unsubscribe kept an
unselected source blocking, a non-DNS-listed directory row could be subscribed
without a manual detour, and visible bulk actions stayed within their displayed
scope (319, 320, 415). Those are controlled data and UI tests, not claims about
the availability or syntax of every public provider.

## Readability and recovery

- On the earlier signed APK, switching language left a Hebrew completion on
  English Home. The locale-fix APK displayed the same VPN result in Hebrew,
  English and French (captures 16-18). A connected regression checks a retained
  source name containing a percent sign across a French-to-English switch.
- The final APK's Hebrew Custom Rules search, empty state, target, delete and
  cancel controls were checked in 49 and 56-62. Domain Checker showed its
  integrated Hebrew title, input and action in 65.
- After resetting **only the disposable app data**, first-run Hebrew Home at
  150% showed the actual long VPN-on/no-rules state on a throttled network.
  In `77-final-no-rules.xml` the version ends at y=273 and the description
  starts at 400; the status ends at 654 and counter icon images start at 696.
  This confirms the reported overlap is fixed in this geometry, not every
  possible screen dimension. This reset retained Android's prior VPN approval.
- In airplane mode the updater visibly explained the failed check in Hebrew;
  tapping its retry action again showed failure feedback and left retry
  available (78-79). Network access was restored afterwards.
- On the debug emulator with the app locale set to he-IL before and after
  instrumentation, a real database insert failure was forced while the Checker
  was stopped. Its recreated screen displayed the failure message, not a success
  and not a stored rule. After restoring writes, retry showed success and
  persisted the block (one test, 2.213 s). The installed resource for the error
  was verified in Hebrew, separately from the signed visible Checker title.

## Rapid input

On the signed APK, Android's package-targeted Monkey runner completed **5,000
events in 73.882 seconds**, seed 1350701, 20 ms throttle, with stop-on-crash
enabled. Mix: touch 45%, motion 28%, rotation 8%, navigation 12%, major
navigation 7%. The log shows Home, Custom Rules, Log, Source Editor,
Preferences, Onboarding and Updater activity starts. Zero AdAway crashes
or ANRs reported; no such process exit recorded. After dismissing Android's
notification shade, Custom Rules still opened its Add dialog and accepted Cancel.

Two earlier runs stopped at events 1,838 and 2,463 with native crashes in
the emulator's Bluetooth process, not AdAway. The final run disabled **that
system package on this disposable image only**. The runner reported 209
dropped pointer injections and the system notification shade received some
gestures. 5,000 is not a count of successful in-app taps or covered journeys.
See `monkey-5000{,-isolated,-final}.txt`, `post-stress-exits.txt`, and
captures 82-83. No arbitrary traffic was sent to external sites as a load test.
