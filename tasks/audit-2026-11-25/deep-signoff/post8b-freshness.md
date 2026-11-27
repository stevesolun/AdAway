# Sources: check for updates, after public 13.5.8b

This is branch evidence for WF:SRC-009 and the Check updates route of
WF:SRC-001. These changes are not in the published 13.5.8b APK.

## Phone journey on an Android 14 emulator

The installed debug APK was
`1d7ed6d1d7c99fee60201e8b5d27827588936c7ae81e53ba464001c541ec8253`.
Started fresh, selected VPN mode, approved Android's consent screen and opened
Sources. The three default sources were enabled. With the emulator's actual
airplane mode enabled, tapped **More options > Check updates**. Sources showed
**No Internet connection**. Rotating to landscape retained the message. Tapping
Close removed it. Disabled airplane mode and waited for a validated network.
The same menu action returned **No source update found**. The test did not
equate checking source dates with applying filters or blocking a DNS request.

Raw local captures are under `app/build/reports/audit-2026-11-25/post8b-freshness/`.

| Capture | SHA-256 |
| --- | --- |
| os-offline.png | a7be4647cdddecf547d900f655fddc53a857ac8b5e039db9df6e2f00d8e7447b |
| os-offline-landscape.png | 9c299932c8428608c3ee7128f14bd3d8e608398841c00d09eb8e4b71d45a95db |
| os-online.png | 847ecd2217ded7a4ec3591e376f0cf381d148dea9cc066273a3de179cafedb26 |

## Regression evidence

POST8-UX-001: before the product change, the new offline test selected the
menu item but failed at the visible error assertion (`red-popup.txt`). The
original menu handler caught and ignored the connectivity error. The fixed
test requires feedback after activity recreation, then reconnects the
controlled source, checks again, verifies the online date changed, and asserts
one HEAD, zero body requests and zero protection applies. Dismiss survives a
second recreation. The unchanged-source case verifies that a check finishes
while Home is visible. The duplicate-tap case holds the first response open.
The eight tests in `HostsStartupCheckInstrumentedTest` passed together in
17.761 seconds (`actual-touch.txt`).

A separate fixture failure occurred when selecting a toolbar popup by
accessibility focus. An intermediate Android 14 fixture waited for the Sources child and
toolbar, locates the named visible row in the focused popup, and sends a
touch at its screen coordinates. A missing popup or a wrong database outcome
still fails the test. This explains failures recorded as *no clickable visible
label* here; it does not retroactively establish the cause of the older
[issue #28](https://github.com/stevesolun/AdAway/issues/28) date mismatch.
The controlled slow startup remains held until all nine manual dates have been
checked.

The same slow-startup test also passed in three fresh instrumentation runs,
portrait / landscape / portrait (2.038 s, 2.057 s, 2.321 s). In the separate
device-settings journey above, airplane mode and orientation were restored.

## Post-release integration

This batch was recorded at `165f2ad28`, before `a40205704` moved manual-check
ownership to the application model and added cross-activity coalescing coverage.
The APK hashes and execution counts below belong to that earlier batch, not the
later source revision. The later changes have separate hash-bound source reviews
in [post8b-source-review.csv](../post8b-source-review.csv) and
[post8b-integration-review.csv](../post8b-integration-review.csv); those reviews
are not runtime results.

The first full run of this branch completed 729 Android cases: 707 passed,
21 skipped, one failed. The duplicate-check test released the first response
before the second popup action was known to have run and counted two requests.
An isolated retry with a popup-dismissal wait passed once, then missed the
first action: no source check started and the popup remained until teardown.
See `first-full-device.xml`, `duplicate-first-full.txt`, and `dispatch-2.txt`.

On Android 14 the corrected automated fixture dispatches a click to the
enabled, visible, named row in the focused popup, then observes its dismissal
before releasing the first request. This tests menu dispatch and business
logic, not touchscreen hit testing. The physical input path above was checked
separately on the emulator. Five independent repetitions passed, followed by
23/23 in a combined run (eight startup/menu, five source-checkpoint and ten
Apply feedback cases). The tests still assert exactly one request and no import.
See `row-dispatch-{1..5}.txt` and `final-ownership-and-menu.txt`.

Debug APK SHA-256: `6a5c57138433b6b334e7b6432b814a9264c1f7463d8d71fa942416ec7871d7b9`.
Test APK SHA-256: `c8a73b9930f49ccd68b17efa27612bcb5ab7568aebfb786ead651a54ebaf45e2`.
These are development builds, not replacements for the signed public beta.

The final full Android 14 result XML for this batch has 729 unique cases: **708 passed,
21 skipped, zero failures or errors** (`final-full-device.xml`). The JVM
suite recorded 774 passed, 3 skipped and no failures; Android lint reported
0 errors and 32 warnings. The separate host-tool suite passed all 136 tests.

Two opt-in VPN cases skipped in the default run also passed separately on
the same build, with test VPN consent pre-authorized on the private emulator.
Through the real tunnel, one generated DNS rule blocked, another redirected to
the expected address, and an upstream query for `example.com` resolved.
Android's service-start route established a foreground tunnel before and after
a stop. Both cases returned individual pass results
(`live-vpn-data-plane.txt`); they are **not** added to the default suite totals.
This is not a claim about cosmetic filtering in a browser.

No physical-device, OEM, root, all-screen or public-release claim.
