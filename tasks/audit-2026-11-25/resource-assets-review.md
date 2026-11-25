# Resource Asset Review: Bounded Read-Only Pass

Main acceptance: main subsequently read the complete report and viewed both contact sheets, and accepted this bounded asset review. No original asset/app edit followed. The resource lane is closed with no remaining assigned scope; main owns central integration.

## Result

Completed coverage for **18 current files**: the 15 pending mipmap PNGs, app/src/main/icon-web.png, app/lint.xml and app/.gitignore. **No concrete new bug found.** resource-assets-bugs.csv intentionally contains only the central register's 11-column header.

All target hashes were pending with no matching existing review evidence and no staged/unstaged changes at scope selection. No original asset, application source, central audit file, old sidecar, index or commit was changed. Only the three resource-assets sidecars were created inside the repository. Derived inspection images were created outside it under /tmp.

## Frozen Updater Handoff

Read resources-fixes-review.md completely and the entire UpdateStatusLayoutInstrumentedTest.java, including a separate read of the middle section omitted by the first combined tool display. No changes were made. The frozen hashes remained:
- update_actity.xml: b82385e8a82af5dffc76c17de2f33838aa499c2af06a0d56f863776cad83ad4d
- UpdateStatusLayoutInstrumentedTest.java: 365da467cbd522e5c7b9fdd41719240c28aa394b2033e5afe5bad7a3cfc76895

The accepted freeze stands. Main owns the next build and actual Android test execution; this asset pass adds no updater runtime proof.

## Actual Image Inspection

The assistant actually opened both derived PNG contact sheets with the local image-view tool and inspected every labeled target tile, not just file metadata:
- mipmap-contact.png: all 15 density PNGs, arranged mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi by rows and icon/foreground/round by columns.
- reference-contact.png: web icon plus existing Resources/icon_foreground.png, Resources/icon_background.png and Resources/logo_market.png.

Additionally opened original app/src/main/icon-web.png (512x512) and mipmap-xxxhdpi/icon_foreground.png (432x432) directly at source resolution. These direct views confirm the web mark and high-density transparent foreground independently of contact-sheet scaling.

The contact sheets composite source alpha over a checkerboard, preserve aspect ratio and use nearest-neighbor resizing (208px inspection boxes for mipmaps, 288px for references). Enlarged low-density pixels are expected from that inspection transform, not evidence of a source defect. No recoloring, sharpening, cropping or original-file rewriting occurred.

### Visual Observations

- Every density icon shows the same recognizable right-facing AdAway bird silhouette with its eye and wing/tail structure; no blank, unrelated replacement, missing mark or grossly cut-off silhouette was seen.
- icon.png variants use a light bird on a red octagonal badge; icon_round.png variants use a red circular badge and edge/shadow alpha. Foreground PNGs contain only the light bird on transparency with intentional margins.
- The web image uses the same mark on a red rounded-square tile with a shadow. It is visually coherent with the existing asset family, although not an exact copy of either launcher background shape.
- Existing Resources/icon_foreground.png and icon_background.png provide directly viewed source-art context. Resources/logo_market.png is a more shaded/outlined legacy treatment of the same identity, not proof that every current asset must use its shading.
- Current adaptive-icon XML references icon_foreground_red on logo_bg_blue, with an icon_monochrome layer; the legacy PNGs use light-on-red treatments. The common mark is consistent, but palette/background treatments are not pixel-identical. No unsupported branding regression was inferred solely from that difference.
- The active red/white/monochrome vector sources and existing branding contract test were inspected as source context. Their XML was NOT rasterized in this pass; no Android-rendered vector comparison is claimed.
- Slight edge/shadow and sampling differences across density files were not promoted to speculative cosmetic defects.

## Decode and Parser Evidence

Used existing JDK21 ImageIO to decode every target PNG and iterate every pixel via getRGB. Recorded dimensions, alpha-capability, zero/partial/opaque alpha counts and the nonzero-alpha bounding box. The whole image, including transparent regions, was decoded; this did not substitute for the actual visual inspection above.

A separate Ruby structured PNG-chunk walk verified PNG signature, chunk boundaries, per-chunk CRC32, IHDR-first/IEND-last and exact end-of-file. All 16 target images passed. Every IHDR is 8-bit RGBA, noninterlaced (color type 6, compression/filter/interlace 0). All but xxhdpi/icon.png contain IHDR/IDAT/IEND only; that file also has a valid pHYs chunk. No malformed/truncated image or unexpected trailing payload was found by these checks.

Icon and round sizes follow 48/72/96/144/192 pixels; foreground sizes follow 108/162/216/324/432 pixels. These are internally consistent 1/1.5/2/3/4 density progressions. All images have both nonzero artwork and transparent pixels; none is accidentally all-transparent or fully opaque.

| File | Dimensions | Alpha 0 | Partial Alpha | Alpha 255 | Nonzero Alpha Bounds |
| --- | --- | ---: | ---: | ---: | --- |
| icon-web.png | 512x512 | 80259 | 18047 | 163838 | [42,53,470,482] |
| mipmap-hdpi/icon.png | 72x72 | 1700 | 148 | 3336 | [4,4,68,68] |
| mipmap-hdpi/icon_foreground.png | 162x162 | 23796 | 376 | 2072 | [44,53,119,122] |
| mipmap-hdpi/icon_round.png | 72x72 | 1287 | 769 | 3128 | [0,0,72,72] |
| mipmap-mdpi/icon.png | 48x48 | 804 | 100 | 1400 | [3,3,45,45] |
| mipmap-mdpi/icon_foreground.png | 108x108 | 10543 | 249 | 872 | [30,35,79,81] |
| mipmap-mdpi/icon_round.png | 48x48 | 513 | 491 | 1300 | [0,0,48,48] |
| mipmap-xhdpi/icon.png | 96x96 | 3264 | 196 | 5756 | [6,6,90,90] |
| mipmap-xhdpi/icon_foreground.png | 216x216 | 42390 | 502 | 3764 | [60,71,159,163] |
| mipmap-xhdpi/icon_round.png | 96x96 | 2336 | 1216 | 5664 | [1,1,95,96] |
| mipmap-xxhdpi/icon.png | 144x144 | 7112 | 908 | 12716 | [7,7,137,137] |
| mipmap-xxhdpi/icon_foreground.png | 324x324 | 95572 | 786 | 8618 | [90,106,237,243] |
| mipmap-xxhdpi/icon_round.png | 144x144 | 5483 | 2155 | 13098 | [3,3,141,144] |
| mipmap-xxxhdpi/icon.png | 192x192 | 13296 | 344 | 23224 | [12,12,180,180] |
| mipmap-xxxhdpi/icon_foreground.png | 432x432 | 170031 | 1082 | 15511 | [120,141,316,324] |
| mipmap-xxxhdpi/icon_round.png | 192x192 | 9874 | 3496 | 23494 | [4,5,188,192] |

Bounds are [left,top,right-exclusive,bottom-exclusive] and include shadows/antialiased pixels. A shadow reaching the image edge is not by itself proof of a clipped logo.

## Lint Configuration Check

Read all of app/lint.xml and parsed its complete XML with REXML. It contains one issue configuration, InvalidPackage, with two path-scoped ignores:
- **/dnsjava-3.0.2*.jar
- **/jna-5.3.1.jar

There is no blanket all-issues or security-category suppression in this file. The current version catalog pins dnsjava 3.6.5, so the old dnsjava filename exception does not match that artifact. This is historical/nonmatching configuration, not a demonstrated current lint or release failure. Verification metadata contains multiple historical versions; it was not treated as a resolved runtime dependency graph. JNA path relevance and actual lint behavior remain main-owned. No Gradle, lint, dependency resolution or build was run.

## Ignore Configuration Check

Read all 36 lines of app/.gitignore with root .gitignore as context. Reviewed generated Android/native directories, compiled classes/APKs, local property files, Gradle/Maven outputs and IDE state patterns.

Read-only git check-ignore --no-index --stdin probes confirmed ignores for seven representative paths: app/build/outputs/apk/debug/app-debug.apk, app/.gradle/cache, app/gradle.properties, app/local.properties, app/release.properties, app/test-artifact.apk and app/.externalNativeBuild/cache. Three negative controls (the Java application source, a mipmap PNG, and app/lint.xml) were not ignored. Probes created no files and did not change the index.

No concrete ignore-pattern defect was demonstrated. This does not imply all future artifacts or arbitrary secret filenames are protected, nor does ignoring a name remove an already tracked file.

## Evidence Artifacts

Derived files are review-only, outside the repository:
- /tmp/adaway-resource-assets-review-20261125/mipmap-contact.png
  SHA-256 21580b10dc4d08e9f5e394db1b7113126c3ae4498cb8bc4b86276af796256dfc
- /tmp/adaway-resource-assets-review-20261125/reference-contact.png
  SHA-256 e631908c2ef908d66867e57a230bb0bb5a6ad5e2f0273937146640da9d5ef3d7

These files were actually viewed. Their temporary location may not survive system cleanup; source hashes and visual observations remain recorded here.

Context assets, not newly credited review rows:
- Resources/icon_foreground.png: 893e060577ec1747e2890cce0fdd6025babbba3271484c1e88ffa5d122b8533f
- Resources/icon_background.png: fb5a7ab6f19d5e35bd710d15a1010d78a3b93ddd82df4ec07dd6a53f2bbe20b8
- Resources/logo_market.png: c4a74bfffa2982b9d08cd480041ee5b6ad60dfa22be790aee4e22cfc8ec8198c
- drawable/icon_foreground_red.xml: 32c53394361208c5625e42a2381a5b1554c8cf0e9f5278dccfad0e09c4764c99
- drawable/icon_foreground_white.xml: 50db2083be4e870ea0a0275ae2e29c8e069f421e323a3e1b87b84d4b390aa52f
- drawable/icon_monochrome.xml: 788462453887123ec791c9b8c85246962586db93e875f4d0ecd0eb265cd3a41a

## Limits and Handoff

VISUAL-IMAGE means assistant inspection of displayed bitmap pixels. It is not a human brand-owner sign-off. PIXEL/PNG means complete decoding and structured byte/pixel checks; metadata alone was never credited as image viewing. FULLTEXT-ASSISTANT applies only to the two small configuration files in this new coverage CSV.

No launcher/OEM mask, adaptive-icon animation, density selection, APK resource packaging, accessibility contrast, device screenshot, physical-device or store-upload verification was executed. The web asset's publication/use was not established. Existing legacy source art and vector files remain context rather than expanded audit scope.

resource-assets-files.csv uses path,sha256,assessment,bug_ids with 18 exact current hashes. resource-assets-bugs.csv has the standard 11 columns and zero findings. No central import was performed. Recheck hashes before integration; do not transfer this review credit to changed bytes.

Final validation passed: all 18 source hashes still match; no target differs from HEAD; exact requested file scope with no duplicate paths; 4/11-column CSV schemas match the existing convention; zero bug rows. The accepted updater layout/test hashes also remain unchanged. This completes only the bounded asset/configuration pass.
