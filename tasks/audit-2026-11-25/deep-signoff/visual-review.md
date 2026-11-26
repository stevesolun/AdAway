# Integrated visual review: API 34

**Final 393 dp verdict:** 40/40 passed in one run on API 34. All eight screens were captured and visually reviewed in each rendered configuration: en-US at 1.0x, 1.3x and 1.6x; he-IL (RTL) at 1.3x and 1.6x. No wider captures were substituted. DEEP-VIS-006, DEEP-VIS-007 and DEEP-VIS-008 are verified on this viewport.

## Final acceptance

- **Home source counts (DEEP-VIS-007):** In [Hebrew 1.6x](../../../app/build/reports/audit-2026-11-25/deep-visual-integrated/final-393dp/font-1.6-rtl/ux-matrix/home.png), the up-to-date count fits on one line and the outdated count now shows both lines, including the previously missing word. The two action icons and the sync status remain separate. Both count rows use the same growing layout; the counter labels and badge clearance remain intact.
- **Discover selector (DEEP-VIS-008):** Compare the [old RTL crop](../../../app/build/reports/audit-2026-11-25/deep-visual-integrated/review/rtl13-language-control.png) and [final RTL crop](../../../app/build/reports/audit-2026-11-25/deep-visual-integrated/final-393dp/rtl13-language-control.png). At 1.3x there are 54 blank pixels between arrow and text in Hebrew, and 624 in English. Measured on the original screenshots across y=965..1064 using foreground columns with red channel >100. All five Discover images were checked; the recent RTL result row still keeps its title and syntax clear of its controls. No translation or selector behavior changed.
- **Provenance:** API 34 debug 13.5.7b/130507, 1080x2340 at 440 dpi, emulator-5564. Installed SHA-256: app `a46591f52389564b64f7cc1ace10c00beda4c6f135b6a21f1d99bc1283de19e0`; test `9c37b0377c1ab63fa8cc6b8cceb4f96b10b84f1ae98ea11aefdf602bdc0d712a` (unchanged from the counter-only build). [Final run log](../../../app/build/reports/audit-2026-11-25/deep-visual-integrated/final-393dp/runner.log), [40 screenshot hashes](../../../app/build/reports/audit-2026-11-25/deep-visual-integrated/final-393dp/screenshots.sha256), [bug ledger](../deep-visual-extra-bugs.csv).

## Earlier matched narrow runs (before and after the counter-only fix)

Disposable Pixel 5, emulator-5564, 1080x2340 at 440 dpi (393 dp wide), debug 13.5.7b/130507. Both runs used `scripts/run-ux-matrix.ps1 -DeviceSerial emulator-5564 -AllowDestructiveCapture`. Before the fix, the runner stopped on English 1.6x; the two Hebrew variants used the same script's setup, capture, assertions and restore functions on the same installation. After the fix all five variants ran in the normal script order. The instrumentation checks the actual activity language, layout direction and font scale (tolerance 0.02) on each capture, plus VPN mode selection and populated Discover/Sources lists. It does not establish an active VPN.

| Actual configuration | Before | After |
| --- | --- | --- |
| English 1.0x | 8/8 passed | 8/8 passed |
| English 1.3x | 8/8 passed | 8/8 passed |
| English 1.6x | Home failed: `Redirected` lost its final letter; 7 not reached | 8/8 passed; all three counter labels readable |
| Hebrew RTL 1.3x | 8/8 passed assertions; selector overlap visible | 8/8 passed assertions; selector overlap visible |
| Hebrew RTL 1.6x | Home failed: outdated-source text; 7 not reached | Same Home failure; 7 not reached |

**Separate width control (before only):** At 420 dpi (411 dp), English and Hebrew 1.6x each passed all eight assertions. Both sets were visually inspected. The counters fit on the old build at this width; the Hebrew selector still overlapped. These 16 screens do not fill gaps in the narrow matrix.

## Earlier visual findings

- **Home:** The [old counter](../../../app/build/reports/audit-2026-11-25/deep-visual-integrated/font-1.6/ux-matrix/home.png) needed 111 px with 71 px available. The [fixed counter](../../../app/build/reports/audit-2026-11-25/deep-visual-integrated/after-counter/font-1.6/ux-matrix/home.png) shows the full label without reducing text or disturbing the status/badges. Only the three cards' side padding changed. In [Hebrew at 1.6x](../../../app/build/reports/audit-2026-11-25/deep-visual-integrated/after-counter/font-1.6-rtl/ux-matrix/home.png), the counters fit but the outdated-source message loses its last word (170 px needed; 88 px available), before and after.
- **Discover and Sources:** In Hebrew the populated Discover row's title and `Hosts` stay between the switch and checkbox, consistent with the packaged DEEP-DISC-002 layout. The separate [language selector](../../../app/build/reports/audit-2026-11-25/deep-visual-integrated/review/rtl13-language-control.png) draws its arrow over the first letter of `All languages`; the automated check misses this. Sources shows populated rows and a mirrored add button. Some Discover and Custom Rules copy falls back to English.
- **Other screens:** Reviewed the More list, Domain Checker field and action, onboarding choices and action, Custom Rules empty state, and Update failure state and support actions. The captures show rendered app content, not live update success, keyboard behavior, or a tap-through of each action.

## Earlier builds and evidence

| Installed APK | Before SHA-256 | After SHA-256 |
| --- | --- | --- |
| App | `36ba69c778c64b847c8d30f0f127809ff857c89b9161b190987a498c741113f2` | `1ae05190074fa15ef2e5ee1a9958bb8b7c1d3f235c0dad4eeca09cb71839b0ea` |
| Instrumentation | `f886e76fe284930e3536931bbb857e5af39b08f53ed32cfb424c047c080bc0cd` | `9c37b0377c1ab63fa8cc6b8cceb4f96b10b84f1ae98ea11aefdf602bdc0d712a` |

The runner and matrix test source hashes matched across builds. [Evidence directory](../../../app/build/reports/audit-2026-11-25/deep-visual-integrated/): `runner.log`, `rtl-continuation.log`, `after-counter-runner.log`, and `width-411dp.log` are separate. The [screenshot manifest](../../../app/build/reports/audit-2026-11-25/deep-visual-integrated/screenshots.sha256) covers 26 narrow before, 33 narrow after and 16 wider before, including failed Home captures. [Bug ledger](../deep-visual-extra-bugs.csv).
