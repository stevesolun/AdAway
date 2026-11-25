# Remaining Documentation/Data Review

Date: 2026-11-25. Lane: remaining-docs. Read-only review; no implementation.

## Scope and Coverage

- Pending central-inventory paths only under `metadata/**`, `Resources/tasker/**`, `.claude/**`, and `docs/**`: 255 candidates.
- Fully reviewed and SHA256-bound in `remaining-docs-files.csv`: **254 files**, comprising **229 text files and 25 genuinely viewed images**.
- Text coverage: 207 metadata files, 13 command/hook documents, and 9 documentation/data files.
- Visual coverage: 7 metadata images, 7 Tasker images, 10 documentation screenshots, and the WA/TG final-state image.
- **One binary remains not visually reviewed:** `docs/screenshots/import_final.png`. Decoder failure is DOC-AUD-002. It is intentionally absent from the full-review CSV; do not merge it as visually reviewed.
- Seven already-reviewed inventory paths were not re-counted: the two .claude settings JSON files; dependency-verification, filter-rule-capabilities and MIT-relicensing documents; fetch_and_classify.py; scan_lists.py.
- `docs/wa-tg-audit/scan_lists.py` and all tooling tests remain with Kant. No source, tests, central ledgers, staging, or release artifacts were changed.
- Fully read duplicate metadata files individually, not inferred from identical hashes. Every fully reviewed path matched its central inventory hash at the closing check; the only scoped hash difference was the excluded scan_lists.py.
- The 259 KB catalog was displayed losslessly as all root fields plus complete records 0-736 in order; only JSON whitespace was compacted. Every displayed record was read. This is distinct from the parser-only assertions below.

## Findings by Severity

### DOC-AUD-001 - P2: Store descriptions promise an unavailable local server

Evidence: `metadata/en-US/full_description.txt:9` offers a local web server; the same capability promise remains in **all 70** long descriptions, including translated final paragraphs. These are store descriptions, not a dated historical report.

Current implementation: `WebServerUtils.java:50` sets `WEB_SERVER_ENABLED = false`; `isWebServerAvailable()` returns that flag. `PrefsRootFragment.java:185-191` unchecks/disables the server controls. `settings.gradle:29` includes only app and sentrystub; README's current module section explicitly says the historical webserver is absent.

Reproduction: read the listing's final paragraph and inspect the current availability/control branches. A user following that capability claim cannot enable the advertised server in this build.

Likely fix: remove or explicitly qualify the disabled-server claim across the base and localized long descriptions. Preserve the separately supported custom host/IP redirection feature; do not re-enable native code to satisfy stale copy.

Smallest regression: English/fallback capability-claim guard plus review of the 70 localized final paragraphs against the disabled feature. Source-confirmed; no new Android execution.

### DOC-AUD-002 - P3: Import screenshot has invalid PNG bytes

Evidence: opening `docs/screenshots/import_final.png` with the image viewer failed with `invalid or unsupported image data`. The host `file` utility classifies it as `data`. A PNG parser rejects its first bytes: `ff fe eb 00 50 00 4e 00 47 00`, including a UTF-16LE BOM, rather than the PNG signature. The complete file is 111342 bytes.

Reproduction: open this path in an image decoder or assert the eight-byte PNG signature. This is a present artifact-integrity failure, not a complaint that a historical screenshot looks old. No current APK impact or active README reference to this particular image was established.

Likely fix: recover a known-good original or replace through binary-safe capture/transfer. Removing an unused damaged historical artifact is an owner decision. Do not claim visual coverage until the original/replacement genuinely renders.

Smallest regression: signature plus real decode check for every committed documentation PNG. Host decoder/parser failure executed; no Android runtime claim.

## Existing Findings and Non-Findings

- The dated domain-scan JSON and summary repeat the scanner's exact-to-suffix inference and advice to allow domains they themselves label ads/telemetry. Cross-referenced **TOOL-AUD-001 and TOOL-AUD-003** in coverage only; no duplicate bug rows.
- March audit/fix/recon/priority records and the dated February modernization plan are historical material. Old test totals, paths, permissions, proposed code, and plain assembleRelease examples are not promoted to current recommendations or fresh defects merely because implementation changed.
- The Tasker pictures actually target `org.adaway.automation` and use `SEND_COMMAND_INSECURE` with START/STOP extras. They are classified as historical screenshots in `THIRD_PARTY_LICENSES.md:63`; no current markdown walkthrough referencing them was found. Current app receiver requires the signature-level SEND_COMMAND permission and accepts SEND_COMMAND, not the pictured companion action. No compatibility/runtime success is claimed, and weakening that permission is not a proposed fix.
- Command/hook files were read as review guidance, not executed or treated as authorization. Generic app compilation is not native-runtime proof.
- The saved catalog contains third-party descriptions of browser, DNS, proxy, and IP lists. These are historical heuristic classifications, not proof that every format works in this app. No external feed or DNS service was queried.
- No new actionable claim that disabled AI features are available was found in these pending paths.
- Historical screenshots were inspected for visible private data and misleading proof. Splash-only images and the Android-launcher final-state image do not demonstrate completed app behavior. No private message/account contents or credentials were observed. Artwork/trademark clearance was not performed.

## Executed Checks

These checks were read-only and are **not substitutes for full text or visual review**:

1. Four documentation JSON files parsed successfully.
2. Catalog assertions: 737 records, 737 distinct IDs, 648 score-3 and 89 score-2 records, consistent with saved totals. All viewUrl/homeUrl fields are empty; no current fetch success inferred.
3. All 19 scoped PNG files underwent signature, chunk-boundary, CRC and IDAT zlib checks: **18 passed, 1 failed** (DOC-AUD-002). Seven JPEGs were genuinely viewed; not included in the PNG parser check.
4. Coverage/bug CSV schemas, uniqueness, current hashes, exclusions and central bug-ID collisions checked before handoff.
5. No Gradle, emulator, adb, privileged-root operation, network modification, stage, or commit. No new main-owned test result is claimed.

## Handoff

- `remaining-docs-files.csv`: exact `path,sha256,assessment,bug_ids`; 254 full-review rows.
- `remaining-docs-bugs.csv`: exact central 11-column schema; 2 new source-confirmed findings.
- This report records the sole decode-blocked coverage gap explicitly. Production inputs remain untouched.
