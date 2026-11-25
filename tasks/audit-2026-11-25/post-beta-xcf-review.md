# Historical XCF artwork review | 2026-11-25

All three previously header-only XCFs are fully reviewed for historical artwork content at the source hashes in `post-beta-xcf-files.csv`. No artwork defect was confirmed. The strongest export evidence is that the **stored group pixels** reproduce the companion PNGs exactly, including transparency. Rebuilding the dove from its two paint layers gives a slightly different numerical result at the eye, explained below.

## Results by source

Layer order is top to bottom. Bounds are source pixels, written as `[left, top, right, bottom)` with exclusive right and bottom edges.

| Source | Saved groups and their paint layers | Visual and pixel comparison |
| --- | --- | --- |
| `Resources/icon.xcf` | `dove` (eye above silhouette), `red area #1` (inner octagon), `border #1` (outer octagon). 3 groups, 4 paint layers. | Complete pale dove facing right with black eye on a flat red octagon; transparent corners. No `Resources/icon.png`. Stacking its stored groups exactly reproduces `icon_foreground.png` over `icon_background.png`. Rebuilding from its four paint layers exactly reproduces the two independently rebuilt component XCFs stacked together. |
| `Resources/icon_background.xcf` | `red area #1` contains `red area`; `border #1` contains `border`. 2 groups, 2 paint layers. | Two solid octagons, not a red center with a contrasting outline. The smaller [52, 52, 460, 460) sits inside the larger [25, 25, 487, 487); both are RGB (183, 28, 28). Stored groups and rebuilt composite each match `icon_background.png`: 0 differing RGBA pixels out of 262,144. |
| `Resources/icon_foreground.xcf` | `dove` contains `dove - eye` above the silhouette named `dove` with a trailing space. 1 group, 2 paint layers. | Light silhouette [114, 146, 403, 415) and a small black eye [365, 186, 374, 195) on transparency. Stored group versus `icon_foreground.png`: 0 differing RGBA pixels. Rebuilt paint layers versus PNG: 24, all at the eye edge. |

The background has 85,150 transparent, 0 partial-alpha and 176,994 opaque pixels; the full icon has the same alpha plane. The foreground has 226,883 transparent, 197 partial-alpha and 35,064 opaque pixels. Every stored group and paint layer in the split XCFs also matches its counterpart in the full XCF over the entire RGBA canvas.

**Eye-edge rounding.** All 24 differing pixels are exactly the partially transparent pixels of the black *eye layer*, over an opaque silhouette. The PNG and stored group use the integer floor of the blended RGB values at those positions; the independent paint-layer renders round to nearest. No channel differs by more than 1 on the 0-255 scale, and alpha is identical everywhere. This accounts for all 24 differences between the rebuilt full icon and the two PNGs stacked together. Enlarged views show the same eye shape. The shaded, outlined `Resources/logo_market.png` was also viewed: it is a distinct treatment, not an exact export reference for the flat icon.

## How coverage was verified

- Used GimpFormats 2025 (`GimpDocument.raw_layers`, each layer's `image`, and the document renderer) on the original XCF bytes. Forced a full 512x512 RGBA read for each of the 14 entries, including the six stored group projections. Each entry has 64 full-resolution RLE tile pointers; the three smaller hierarchy levels have none. All entries are visible, at (0, 0), normal legacy blend, full opacity; no raster masks or separate channels are present.
- The reader exposes legacy integer opacity as 255; its blender expects 1.0 for full opacity. The unadjusted renderer produced a cast warning and was not used as evidence. Normalized opacity **in memory only** before rendering. Independently composited the decoded children bottom to top within each group and then the groups bottom to top using Pillow 13.0.0 alpha-over. All three results agreed byte for byte with the normalized reader render. Stored group pixels were decoded and compared separately, never painted a second time on top of the children.
- Visually inspected every isolated group and paint layer, all three rebuilt composites, both component PNGs, their overlay, and the market variant on checkerboard previews. Compared unscaled straight RGBA pixels at all 262,144 positions, including RGB under zero alpha; display scaling was not used to measure equality.
- Each XCF also carries the same three **hidden editor paths**, not hidden raster layers. The reader's vector list did not expose them. Separately parsed the complete legacy property using the [GIMP XCF specification](https://developer.gimp.org/core/standards/xcf/), checked stroke types and finite coordinates, and viewed the outlines. `Unnamed #2` and `Unnamed #1` are closed (18 and 51 point records); `Unnamed` has two open strokes (9 and 3 records). The guide at 252 and stored grid settings are editing aids, not painted content.

## Evidence fingerprints

These are SHA-256 hashes of **decoded pixels** (row-major 512x512 straight RGBA8, no header), not source file hashes. The CSV uses SHA-256 of each original XCF file.

| Image | Rebuilt from paint layers | Composed from stored groups; also equals PNG reference |
| --- | --- | --- |
| Full icon | `370e922b7e2e47c306262dcd2518ab95e60106d8b5a772d7d28934412003721e` | `7688c953f8545d3845b8a764a90c32fe2ceb71c58a1acd0bc06783cd22ecebdc` (PNG pair) |
| Background | `79915b12577f8bbf1270e85e8e3df7f636f281751871ffe7d0267c3ade632b28` | Same (background PNG) |
| Foreground | `9dfd3e83f6106a11c4d1ccd14a678ecd216b01210427a5dc2e7e05c7cb2c6f87` | `717ae5ad75e6fb3b5ac6d2f132373982cc21d45bd537f8e2939e852d9b435690` (foreground PNG) |

This closes the prior decode and visual-inspection gap, not questions of export provenance, licensing, GIMP editing round trips or device rendering. Previews existed only in memory. The three-row sidecar was validated without importing it; no image, app, device, archive, Git state or central ledger was operated on or changed by this review other than reading the named artwork and existing audit evidence.
