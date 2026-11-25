# Legacy Artwork Review: Bounded Read-Only Closure

## Result and Import Boundary

Scope selection: 2026-11-25, branch codex/full-codebase-audit, HEAD f7dbbb5e7a8eca6983135f04adc945bab6518308. Central files.csv snapshot SHA-256: 8e3d0713ab231be4a70e0cfbfdfa229cad3a4ef7dd00b2b17a64f726a4c631d8. Selected exactly the 27 Resources/ rows whose review_status was pending, checked every selected hash against current bytes, and checked git diff/status. None of the 27 targets was staged or unstaged; the already-reviewed, actively modified Resources/certificate/generate.sh was excluded.

- 23 files have completed bounded review: 19 PNGs, one GIF and three SVGs.
- Three XCFs remain PARTIAL / NOT FULLY REVIEWED: header/hash only; no layers or pixels decoded.
- The ZIP remains PARTIAL / NOT FULLY REVIEWED: archive integrity and full text script reviewed; embedded native executable not fully reviewed.
- No confirmed image/SVG defect. One confirmed source-level historical-helper issue, LEGACY-ART-001, is recorded separately. No current app impact is asserted.

legacy-artwork-files.csv contains ONLY the 23 fully covered image/SVG hashes. The four partial hashes are in the table below and are intentionally absent from that importable CSV. scripts/audit_inventory.py --record unconditionally marks every imported row reviewed: importing partial rows would overstate coverage. Do not close those four central rows from this pass. The requested sidecar schemas are exactly path,sha256,assessment,bug_ids and the existing 11-column bugs schema.

Read CLAUDE.md, tasks/lessons.md, the active tasks/todo.md audit checkpoint and the inventory script. No central files, existing sidecars, original artwork, app sources, index or commits were changed by this lane. No Gradle, adb, app build, recovery installer execution, asset installation or deletion occurred. Only these three legacy-artwork sidecars were added to the repository. Temporary decoder source and derived previews are outside it.

## Actual Bitmap Inspection

The assistant opened all three contact sheets below with the local image-view tool and inspected every labeled image, then opened Resources/adaway_screenshot.png and Resources/screenshot6.png directly. This is actual visual inspection, not a claim based on metadata. Those views cover all 20 bitmap files.

- Marks contact: ten source/download/social/logo images, including the sole GIF frame. Text is readable; historical AdAway silhouettes, red octagons, F-Droid mark, Google Play badge, XDA wordmark and Donate button are recognizable and complete.
- Device contact: all three 2013 portrait captures, showing enabled home with/without its overflow menu and preferences.
- Screenshot contact: screenshot1 through screenshot5, showing disabled/downloading home, preferences, example list and DNS-log screen.
- Direct views: adaway_screenshot is a phone-frame composition of the legacy overflow-menu capture; screenshot6 is a landscape tablet home/hosts-source view.
- Menu occlusion and partially visible scrolling preference rows are pictured UI states, not missing-image defects. Old UI wording, HTTP source URLs, disabled controls and legacy brand treatments are not evidence of a defect in the current app. No pictured domain was fetched or endorsed.
- Foreground/background assets are intentionally separate transparent components; their individual absence of the other component is not a bug. Current and historical brand ownership/permission is not established by visual similarity.

Contact sheets use Java2D, checkerboard alpha backgrounds, aspect-preserving fit and nearest-neighbor scaling (enlargement capped at 3x). Marks use 420x330 tiles, device captures 400x700, screenshots 500x866. Full source extents were used, with no crop, recoloring or source rewriting. Scaling artifacts in these derived views are not source defects.

## Decode and Structural Checks

Existing /opt/homebrew/opt/openjdk@21/bin/java ran a temporary inspection program using JDK ImageIO readers. getNumImages(true) found one frame in each file, including the GIF. Every frame was decoded and every pixel read through getRGB; alpha counts and nonzero bounds were calculated across the full image. All 20 decoded successfully. This is decoder evidence, not a security audit of the decoders or original editing projects.

An independent Ruby PNG chunk parser checked the signature, bounded chunk lengths, every chunk CRC32, IHDR-first/IEND-last and exact EOF on all 19 PNGs. All passed. Every PNG is 8-bit RGB or RGBA and noninterlaced. The GIF is GIF89a, has one successfully decoded frame, and has no partial-alpha pixels; no separate complete GIF-format validator was used.

Alpha counts below are zero / partial / fully opaque; bounds are [minX,minY,maxX,maxY) in source pixels.

| Bitmap | Dimensions | Alpha Counts | Nonzero Bounds |
| --- | --- | --- | --- |
| XDADevelopers.png | 512x72 | 28207 / 3455 / 5202 | 0:0:512:72 |
| adaway_screenshot.png | 1195x1994 | 948966 / 54363 / 1379501 | 105:163:1091:1842 |
| device-2013-06-19-105150.png | 768x1280 | 0 / 0 / 983040 | 0:0:768:1280 |
| device-2013-06-19-105158.png | 768x1280 | 0 / 0 / 983040 | 0:0:768:1280 |
| device-2013-06-19-105215.png | 768x1280 | 0 / 0 / 983040 | 0:0:768:1280 |
| download_fdroid.png | 72x72 | 917 / 731 / 3536 | 0:0:72:70 |
| download_google_play.png | 172x60 | 76 / 248 / 9996 | 0:0:172:60 |
| get-it-on-adaway.png | 646x250 | 67088 / 160 / 94252 | 41:41:605:209 |
| get-it-on-fdroid.png | 646x250 | 67088 / 160 / 94252 | 41:41:605:209 |
| icon_background.png | 512x512 | 85150 / 0 / 176994 | 25:25:487:487 |
| icon_foreground.png | 512x512 | 226883 / 197 / 35064 | 114:146:403:415 |
| logo_googlecode.png | 55x55 | 755 / 289 / 1981 | 1:1:54:54 |
| logo_market.png | 512x512 | 68555 / 14521 / 179068 | 15:16:499:500 |
| paypal_donate_button.gif | 92x26 | 136 / 0 / 2256 | 0:0:92:26 |
| screenshot1.png | 480x800 | 0 / 0 / 384000 | 0:0:480:800 |
| screenshot2.png | 480x800 | 0 / 0 / 384000 | 0:0:480:800 |
| screenshot3.png | 480x800 | 0 / 0 / 384000 | 0:0:480:800 |
| screenshot4.png | 480x800 | 0 / 0 / 384000 | 0:0:480:800 |
| screenshot5.png | 480x800 | 0 / 0 / 384000 | 0:0:480:800 |
| screenshot6.png | 1280x800 | 0 / 0 / 1024000 | 0:0:1280:800 |

## SVG Full Reads and Previews

Read all bytes as text in icon.svg (4,837 bytes), icon_old.svg (14,378 bytes), and status_bar_icon.svg (11,684 bytes), including definitions, paths, transforms, paint/filter attributes, metadata and closing elements. This was assistant full-source reading plus structured parser checks, not parser-only credit.

REXML walked every element (17, 75 and 60 respectively), checked unique IDs and all local href and url(#id) references, and checked for DTDs, script, event attributes, foreignObject, image, animation and external rendering references. No duplicate ID, unresolved reference or active/external render content was found. Namespace/metadata URLs and obsolete Inkscape export-filename attributes are not live render fetches.

macOS Quick Look generated previews with qlmanage -t -s 512, and the assistant actually opened all three output PNGs. All show recognizable AdAway artwork: a simple red octagon/cutout, the older outlined/shaded mark, and a gray status mark. Quick Look left intrinsic-sized artwork on a larger preview canvas; blank surrounding preview space is not treated as an SVG defect. No Android, browser, editing-application, high-zoom or cross-renderer fidelity claim is made.

icon_old.svg explicitly includes creator and CC BY-SA 3.0 metadata; the other two lack equivalent license declarations. THIRD_PARTY_LICENSES.md already records those historical-source limitations. This pass neither changes that status nor claims license/trademark clearance. .gitattributes exports ignore Resources/**; repository references found only historical notices and a test fixture, not a current app use established by this pass. No new APK was inspected.

## Partial Binary Coverage

These exact current hashes are recorded for accountability, NOT as completed binary review and NOT in legacy-artwork-files.csv.

| File | SHA-256 | Actual Coverage and Remaining Limit |
| --- | --- | --- |
| Resources/icon.xcf | 69817d26a4161f40d245455aa09dacbb98be9d45aa65f3e93ea78f9517a05897 | file plus exact 26-byte header: GIMP XCF v003, 512x512, RGB type 0. sips reported nil dimensions; GIMP/xcf2png/ImageMagick were unavailable. No layer table, pixel decode, composite render or comparison with companion PNG; NOT FULLY REVIEWED. |
| Resources/icon_background.xcf | e0734a5ad2dd7040c61fd5832d887d57ebaf28ead2f98bcd93f9e4fe0ef888a9 | file plus exact 26-byte header: GIMP XCF v003, 512x512, RGB type 0. sips reported nil dimensions; GIMP/xcf2png/ImageMagick were unavailable. No layer table, pixel decode, composite render or comparison with companion PNG; NOT FULLY REVIEWED. |
| Resources/icon_foreground.xcf | 904cfab9181c4cae468505145f70c74125752d37834bb7e85066b1f81b142fca | file plus exact 26-byte header: GIMP XCF v003, 512x512, RGB type 0. sips reported nil dimensions; GIMP/xcf2png/ImageMagick were unavailable. No layer table, pixel decode, composite render or comparison with companion PNG; NOT FULLY REVIEWED. |
| Resources/symlink_hosts_to_data.zip | a903bd5bc39e37454761377d9357be58b6e0f55759c1deccd5020eeeca02322c | All six directory entries and member CRC/decompression checked; full 26-line updater-script read. Native executable has only header/hash/selected diagnostic-string inspection; NOT FULLY REVIEWED. |

No XCF renderer or package was installed and no hand-written XCF pixel decoder was substituted. Viewing a similarly named PNG does not prove the XCF contains that composite or that hidden layers are safe/intact.

## Archive Review and Confirmed Finding

Resources/symlink_hosts_to_data.zip has four directory entries and two regular members; no absolute, parent-traversal, duplicate or symlink names were present in the listing. unzip -t validated all six entries; unzip -Z -v exposed directory/member metadata. Nothing was extracted into the checkout or executed.

- META-INF/com/google/android/updater-script: 1,042 bytes, SHA-256 b9e55d4f6d2eda9baaa1c2cf146247e63a4ed343ba398eefff6dbe9bb385dd23. Entire 26-line text reviewed.
- META-INF/com/google/android/update-binary: 221,856 bytes, SHA-256 4bfade3dc04d7dd72c98cfc5a4ee28f430888d2f54b2764e38d9af4b5b089381. ELF header identifies a 32-bit little-endian ARM executable. Selected strings include run_program child failure diagnostics. No disassembly, native code audit, execution, reproducible-source match or safety approval was performed.

**LEGACY-ART-001 (P2, source-confirmed-unfixed): ignored command failure followed by unconditional SUCCESS.** updater-script lines 11 and 13 invoke mount; line 15 moves the hosts file; line 17 creates the replacement symlink. All four run_program values are discarded. Line 24 prints SUCCESS without checking the relocation. AOSP's historical RunProgramFn logs a nonzero child result and returns its status string rather than aborting solely on that failure. This establishes the unchecked-result defect under standard Edify semantics; it is not a test of the embedded executable. [AOSP RunProgramFn source](https://android.googlesource.com/platform/bootable/recovery/+/7d0542f28045640dfab6a259ae7bd796e653d66f/updater/install.c).

A failed move/link therefore has no script-level failure branch. In the move-success/link-failure case, the script has no recovery step before its success message. No live filesystem/recovery reproduction was attempted, and exact provenance of the packaged binary is unknown. The finding is restricted to this archived support utility, documented as source-only in THIRD_PARTY_LICENSES.md; no current app security or install failure is inferred. No repair, feature task or repackaging is authorized or performed.

## Viewed Evidence

All derived images reside in /tmp/adaway-legacy-artwork-review-20261125/ and were actually opened. Their hashes bind the viewed output, not a claim that temporary paths will persist indefinitely.

| Derived Image | SHA-256 |
| --- | --- |
| legacy-marks-contact.png | 9bc76132a7715fe46865016125382b7b92aa0ed8d7a05f1c0261f714b8b51bdf |
| legacy-device-contact.png | 3b1ac4aeaee7880fe2dd676e00780948301c1e059aa099400c8b79507062b2f2 |
| legacy-screenshot-contact.png | 58062cd7108cd9bf37265b2b5031fd40ae866e4223f14c7947c7516bc659f678 |
| icon.svg.png | 2f0834de70cf07c5c514f506ef32c7e5f6206cefa30a64d1588c7ca18f0bbded |
| icon_old.svg.png | 791448f4d33118a4bacb393b168e922f176c1cf37abb8e4d9cc3a328a8a426ea |
| status_bar_icon.svg.png | b2ec4ee09178a57b854b28ff76278119e8452e12f9a05eef05ca476b5619c580 |

## Bounded Closure

The 27 selected original hashes were rechecked against the final sidecar/report evidence; the 23-row files CSV and one-row bugs CSV were validated for exact schema, duplicate paths/IDs and hash consistency. The partial four remain explicitly outside completed coverage. Main owns central integration and all builds. This assigned read-only pass is complete at the stated limits; no follow-up scope is assumed.
