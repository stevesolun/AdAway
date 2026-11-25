# Post-beta Android resource audit

Initial scope: exactly the existing XML paths under `app/src/main/res/` marked
`pending` or `stale` in the November 25 inventory: 1 pending Home layout and
20 stale values files. All 21 matched their inventory hashes on entry. Each
was read in full; 15 changed in the initial audit and 6 did not. The current
[per-file evidence](post-beta-res-files.csv) also includes the subsequently read
allowed-host layout, Hebrew backup preferences, and new focused test. The 43
key-only locale edits are **not** whole-file review rows. Prior bug IDs are
retained, not re-certified.
The ten PB-RES IDs have matching [register-format rows](post-beta-res-bugs.csv);
no central records were edited. Reproduction instructions in those rows are
source traces unless explicitly identified as executed host checks.

## Findings fixed

| ID | User-visible problem and evidence | Change |
| --- | --- | --- |
| PB-RES-001 | Both allowed-host dialogs validate hostnames/wildcards, but 43 overrides asked for a link or source address. The visible localized label was not associated with the EditText. | Link that label to the field with `android:labelFor`. Use one nontranslatable `example.org` hint for every locale; remove only the obsolete locale entries. |
| PB-RES-002 | Hide downloaded sources and open a tab with no user rules. `AbstractListFragment` shows the empty state for the filtered tab even when subscriptions or rules elsewhere exist. Subscribing alone will not populate a source-hidden tab. | Describe this view, and mention the source visibility filter. The no-search-matches and load-error states remain separate. |
| PB-RES-003 | Delete a rule: the source surrounded the hostname with straight quotes, but the compiled default was `Delete %1$s from your rules?`. | Use literal typographic quotes. The linked resource formats as `Delete “*.example.org” from your rules?`. |
| PB-RES-004 | Backup preferences claimed only block rules and external storage. `PrefsBackupRestoreFragment` launches CreateDocument/OpenDocument; `BackupExporter.makeBackup` and `BackupImporter` handle source definitions and personal blocked, allowed and redirected rules. | Both default and Hebrew summaries describe a chosen backup file and the rule/source scope. Remaining locale overrides are tracked below; no global closure claim. |
| PB-RES-005 | English and Hebrew root errors prescribed Magisk and reboot for insufficient space. `RootModel.copyHostsFile` raises NOT_ENOUGH_SPACE before the write/remount path; COPY_FAIL can also occur without Magisk. | Give size-specific recovery for the former and root/write guidance conditional on Magisk for the latter. No unconditional reboot. |
| PB-RES-006 | INSTALL_FAILED could name an installer/settings launch that was never attempted. `ApkDownloadReceiver.handleTerminalDownload` and `resumePendingInstall` also catch processing exceptions before launch. | Say installation could not start and offer retry; keep verification failure, permission required and installer opened distinct. |
| PB-RES-007 | The support buttons open different destinations in `UpdateActivity.bindListeners`. Some localized labels named a sponsor/person or left an English noun beside a translated donation action. The button's text is its accessible name. | Distinguish the actions in Greek, Spanish, Persian, Italian, Polish, Brazilian Portuguese, Slovak and both Chinese variants. No target or icon changed. |
| PB-RES-008 | The Dutch system download notification began with the English word “Downloading” followed by Dutch text. `UpdateModel` passes this string to DownloadManager. | Use a Dutch download-in-progress sentence, not a completion claim. |
| PB-RES-009 | The Brazilian Portuguese up-to-date heading used unaccented `esta` in place of the verb `está` and described the user as updated. | Identify AdAway as the up-to-date object and correct the verb. |
| PB-RES-010 | A restore, protection change or rule mutation can own the same `SourceOperationCoordinator` lease as an update. A second action could therefore report a running filter update when there was none. | Identify contention without guessing its owner. Provide both the busy title and wait/retry details in English and Hebrew. |

## Visibility, direction and accessibility

- **Home:** the included content is the sole child of a weighted, fill-viewport scroll view. The labeled Discover button is a separate wrap-content sibling. It starts GONE (preview only: VISIBLE); the lifecycle observer shows it for null/empty sources, hides it otherwise, and its listener selects Discover. The activity places the fragment above bottom navigation. Centered placement and symmetric margins do not select a physical edge. No change justified to the layout. Day/night on-primary versus primary color pairs calculate to 6.57:1 and 5.62:1; these are palette checks, not measured pixels.
- **Lists and Sources:** the 96dp reserves are consumed by bottom and **end** padding in the two lists, matching bottom/end FABs with 16dp margins. The reserve also reduces row width; tight-width truncation or actual tap bounds were not measured. The hosts preview's status is a polite live region. Its six strings distinguish loading, a completed read, truncation after 64 KiB, and failure without implying write access.
- **Locales:** the scoped updater translations omit some retry/permission/verification/installer states. Missing overrides are not empty translations or proof of installation; the [unqualified updater strings](../../app/src/main/res/values/strings_update.xml) remain available. Hebrew now covers all 18 default error keys. No resource qualifier was renamed or physical direction forced.

## Verification

- Parsed each selected XML tree and checked duplicate string names in its values configuration, nonempty scoped values, and localized placeholder position/type against the defaults. Compiled all 21 separately with SDK build-tools 36.0.0 `aapt2 compile`. Linked the 20 values outputs against `platforms/android-36/android.jar` with an isolated manifest, then inspected `aapt2 dump resources`. This is not an application build or layout inflation test.
- On the linked table, Java Formatter passed 15 checks: default plus 12 localized progress strings with distinguishable downloaded/total sizes, a wildcard delete prompt, and the fixed 253-character limit. Italian and default Chinese keep their compact slash; both operands remain present. The before/after resource dumps confirmed the delete delimiters changed in the packaged string, not just in source markup.
- Four JVM bidi probes passed: the three Latin terms in the Hebrew write-error sentence (`root`, `Magisk`, `Systemless hosts`) retain even embedding levels in an RTL paragraph, and a Persian progress sample with Persian digits/units has RTL base direction and even digit levels. This does not establish Android glyph layout, numeric localization by every device, or TalkBack speech order.
- Parsed structural assertions checked Home's visibility namespaces, scroll/button sibling flow, non-directional centering, and both list consumers' logical-end and bottom dimensions. The evidence CSV was checked for exact scope equality, unique paths, nonempty assessments and hashes recomputed from the final bytes.

### Allowed-host and Hebrew follow-up

- `python3 -B scripts/tests/test_allowed_host_resources.py`: both host tests failed before the edits (43 extra definitions; missing label association) and both passed after. The tests scan all main values configurations, including string aliases, and all allowed-host layout variants.
- Isolated SDK 36 resource link using the app's minimum API 26: all 65 qualified list XML files plus the default list, allowed layout, dimensions and both backup preference values files (70 inputs). The packaged resource table contains only the default `example.org`; the compiled label references the localized hostname string and its `labelFor` equals the EditText ID. Both packaged Hebrew summaries matched their XML. This does not test TalkBack speech, rendering or document picker behavior.
- For each of the 43 [localized hint removals](post-beta-res-hint-removals.csv), `previous_sha256` is the pre-removal hash; `hint_only_sha256` was verified against the same bytes minus just the obsolete declaration line. `sha256` is the final, CI-normalized file. The latter two hashes agree for 30 files; only the XML declaration changed in the other 13. These are targeted delta checks, not whole-file review credit.

### Protected CI formatting

- Ran the workflow's Java source launcher on the 59 changed values XMLs in the resource bug rows. The first run reported `14 file(s) patched.` and `patch_applied=true`; the second reported `No file patched.` and `patch_applied=false`.
- Before running the checker, all 59 inputs matched their recorded hashes. Compared every final file byte-for-byte with the expected checker transformation of that input. The only differences were 14 canonical UTF-8 declaration lines; no ellipsis, translated text, whitespace elsewhere, or line ending changed.
- The fourteenth header was Hebrew `values-iw/strings_prefs_backup_restore.xml`: pre-format `cb0f1f4bd0a5438de589f998c54d3aaf99017f7e0eb0ee46af49bab585d72868`, final `66b35e3daff7ecf2283fbbd70525c9f1bbd6d6d9c47bf4e2865516d14b6e4e33`. Its review row uses the final hash; the checked backup/restore wording is unchanged.

## Handoff

- **Updater cold-start header:** current status is tracked as POST-BETA-UPDATE-001 in the [bug register](bugs.csv), with [integrated Android evidence](validation.md#post-beta-review).
- The [current localized override inventory](post-beta-res-localized-overrides.csv) lists all 143 remaining explicit localized declarations of the five tracked keys across 72 files. **Zero** hostname hint overrides remain. Of the other declarations, 135 contain misleading guidance: **35** backup and **32** restore summaries; **33** low-space and **35** generic root write details. These include the four remaining US-English overrides. These are source declarations, not observed device failures.
- Eight declarations are not counted as misleading: Arabic's neutral restore-from-file summary; Bulgarian and German restore summaries that say rules/lists without claiming block-only restore; Vietnamese low-space detail that reports a copy failure but omits capacity guidance; and the four corrected Hebrew backup/restore and root details. No other backup/restore translation or root message was changed in this follow-up. Mixed-language fallback remains possible for untranslated updater strings.
- No Gradle, emulator, Git, instrumented tests, physical device or screen reader were used. The next device checks are Home with zero/nonzero sources at large font, adjacent updater buttons at narrow widths, and Hebrew/Persian visual and spoken order. No visual, runtime or native-speaker sign-off is implied.
