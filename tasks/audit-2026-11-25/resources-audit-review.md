# Resource XML Audit: Bounded First Pass

## Result

- Scope complete for this frozen, read-only XML pass: 1,088 files, 1,158,204 bytes, 11,716 XML elements.
- One new concrete finding: RES-AUD-001 (P2, source-confirmed; not fixed or visually reproduced).
- No production files, central audit files, builds, adb sessions or commits were changed/run by this lane. Only the three resources-audit sidecars were written. No optional checker file was added.
- This is not completion of the full repository audit, native-language review, Android linking/inflation or runtime UI QA.

## Frozen Scope

- Branch: codex/full-codebase-audit.
- HEAD at selection and final scope reconstruction: f7dbbb5e7a8eca6983135f04adc945bab6518308.
- Scope derived from scripts/audit_inventory.py semantics and tasks/audit-2026-11-25/files.csv. The inventory script was read, NOT run in its default writing mode or with --record.
- Parsed CSV with Ruby CSV, enumerated git ls-files --cached --others --exclude-standard -z app/src/main/res, and hashed complete bytes with SHA-256.
- Excluded the union of git diff --name-only HEAD -z and untracked paths (including active/new resources); excluded path+hash matches with a nonempty assessment in existing *-files.csv/current-reviews.csv or review_status=reviewed in central files.csv.
- All 1,103 remaining resource entries were pending in the inventory: 1,088 XML audited and 15 bitmap files explicitly unchecked. The 50 already-reviewed matching resource hashes and 8 active resource paths receive no new review credit.
- Dirty tracked default resources were compared only from frozen HEAD for translation context; untracked resource files were not baseline definitions. These are reference context, not reviewed files in this lane. Findings tied to an active default wording change were not manufactured.
- Selection remained 1,088 XML at the bounded closing pass. A final hash/dirty/exclusion validation accompanies handoff; integration must re-check hashes before importing because the main lane remains active.

## Evidence Levels

- P0/PARSER-SEMANTIC: every XML byte was parsed by Ruby 2.6.10 REXML, then every element, attribute and ordered text node traversed. Nested markup text is flattened only for format comparison; complete trees retain names, attributes and ordered children. XML comments were not treated as runtime semantics.
- ASSISTANT-FULLREAD: 32 small/default files additionally read as complete source in this chat and reasoned through. This is assistant source review, NOT human visual proof. See the exact file list below.
- T1/TRANSLATION-STATIC: 990 XML files across 65 locale directories, including empty translation files. These were complete-tree checks, NOT fluent human proofreading. Missing translations that correctly fall back were not logged as bugs.
- 1,056 files have parser-semantic coverage without assistant fullread. No bitmap, screenshot, layout measurement, screen-reader, visual RTL or native-speaker sign-off is claimed anywhere.
- A parser pass is not Android AAPT validation. References to already-reviewed resources/libraries were inspected as context without claiming those files anew.

## Exact Checks

### P0: XML and Identity

REXML::Document parsed each complete file; recursively retained expanded tag/attribute names and every text node. Checked duplicate resource (qualifier,type,name) declarations; duplicate actual android:id attributes within pending layouts/menus; localized name/type consistency; unexpected string markup; empty translated values against nonempty defaults; Unicode escape shape; XML control characters and explicit bidi override/isolate controls U+202A-U+202E/U+2066-U+2069. No failures in these checks. No claim that this is a complete Android string-escape compiler or natural-language validator.

### T1: Translations and Plurals

Compared every localized string, array item and plural quantity with its same-type default. A plural quantity compares the same default quantity if present, otherwise default other. Required other; allowed only zero/one/two/few/many/other; rejected duplicate quantities. Array item counts compared to default.

Formatter scan recognizes percent tokens, explicit n$ indexes, implicit indexes, reuse flag <, flags/width/precision and date prefixes; skips literal %% and %n for argument consumption. Both broad conversion-category/index and exact conversion-letter/index signatures were compared, with malformed percent candidates triaged. 9,747 localized/default branch comparisons yielded zero mismatches; this is not execution of Java Formatter against every possible argument type or flag combination. Repeated uses of the same argument are compared as a set, not a required occurrence count.

Checked translatable=false overrides for changed values (none), locale directory syntax (65 legacy 2/3-letter language folders plus optional rCC region; in/iw are not rejected as malformed aliases), and technical/link text. All 24 source_edit_url_location_default definitions, including default, preserve https://. Runtime URI-bearing text and markup/attribute links were inspected as static literals; no link was fetched and no endpoint reputation/availability claim is made.

Default filter_category_youtube_desc contains the literal text 100%; this is display text, not a new formatter failure. No speculative grammatical, stylistic or missing-translation issues were logged. Android locale selection, locale-specific plural rule completeness and semantic translation accuracy remain outside static proof.

### R1: IDs and References

Collected app resource symbols by type/name, file-based resource names, generated string symbols from app/build.gradle, and @+id declarations. Walked all attribute and text nodes for typed @type/name and ?attr/name references: 601 reference occurrences in this scope. The 86 initially app-unresolved occurrences were all accounted for by cached dependency R.txt tables; no unresolved app/library typed reference remained. Framework android: references were recognized as framework-owned, not exhaustively verified against every supported SDK.

Dependency symbol context (read-only unzip -p, no extraction/build): androidx.appcompat:appcompat:1.7.0; androidx.cardview:cardview:1.0.0; androidx.constraintlayout:constraintlayout:2.2.0; com.google.android.material:material:1.12.0. Style parents were checked against app/dependency symbols, including day/night variants. This does not substitute for a merged-resource build or validate all configuration-specific theme resolutions.

### L1/X1: Layouts, Menus, Shortcuts and RTL

For all seven pending layouts, checked width/height presence, duplicate actual IDs, each direct ConstraintLayout child target against sibling IDs/parent, start/end versus absolute left/right attributes, and complete input/label/reference metadata. No missing sibling target, duplicate actual ID, missing dimension or absolute left/right layout attribute was found. String-internal bidi controls were absent. URLs/IP fields and default https:// placeholder were inspected; the source URL example is placeholderText, not prefilled editor text.

Read all three pending menu files and both pending xml configuration files fully: menu IDs/title/icon references, searchable configuration, shortcut IDs/labels/generated target package and existing Java target classes. Existence is not proof of successful activity launch. Shortcut/manifest behavior already belongs to another lane.

No fit/overflow, keyboard, font-scale, accessibility or RTL mirroring pass was run. Source editor small-window concerns and palette/contrast preferences were not promoted to unproven cosmetic findings. The complete Home tree was checked structurally but not credited as assistant fullread because its displayed dump was truncated; existing HOME-AUD-005 was not duplicated.

### V1/G1: Values and Graphics

In audited values trees: 9,054 strings, 240 plural resources, 96 arrays, 43 colors, 6 dimensions and 7 styles. 145 XML files contain empty resources roots and were explicitly parsed/recorded, not silently skipped. Arrays are the localized theme-mode and excluded-system-app display entry arrays; compared lengths, not translated-label equality.

Checked color literal/reference syntax, dimension units, style parent existence and localized type consistency. Parsed all 65 pathData attributes (vectors plus interpolator), checked command tokens, finite numeric tokens, initial moveto and command parameter arity; checked positive vector viewports and alpha ranges. No malformed path-token/arity, viewport/alpha, color or dimension candidates remained. This is not vector rasterization, full geometric validation, clipping/contrast or launcher-brand proof.

All four animator files and the interpolator were source-read for ordering, alpha/scale endpoints, duration and references; all five adaptive icons were source-read for background/foreground/monochrome links. Android animation playback, API-specific icon rendering and all 15 bitmap fallback images remain unchecked.

## Finding RES-AUD-001

P2: Retry status text overlaps the recovery button after the progress bar becomes GONE.

- Resource evidence: app/src/main/res/layout/update_actity.xml:30-81. Button top = header bottom + 16dp; progress text top = progress bar bottom + 8dp. Both are horizontally centered. Changelog is anchored only below the button.
- Reachability: UpdateActivity.java:79-101 makes the button VISIBLE and the progress bar GONE for canRetry(), and still assigns progress.format(this) to progressTextView. InstallDownloadStatus maps DOWNLOAD_FAILED, VERIFICATION_FAILED, permission and expiry states to nonempty explanations. There is no dynamic constraint rewrite.
- With header bottom H, a GONE progress bar has zero solver height and owner margin, so the message starts at H+8dp while the retry button starts at H+16dp. Cached AppCompat 1.7.0 resolves TextAppearance.AppCompat.Large to 22sp. The 8dp separation is insufficient even for a default-sized line. This is a source-supported constraint conflict, not a screenshot claim.
- Android source semantics verified against [ConstraintLayout visibility behavior](https://developer.android.com/reference/androidx/constraintlayout/widget/ConstraintLayout#visibility-behavior). The inference above combines that documented behavior with the frozen XML and controller state.
- Eleven focused read-only source assertions passed for anchors, margins, style, visibility transitions, nonempty failure mapping and absence of dynamic rewiring; three nominal arithmetic sanity checks also passed. These are not actual Android/font measurements or test-suite results.
- Recommended fix for main integration: one vertical flow or state-aware constraints/barrier that reserves both message and action height before the changelog. Add rectangle/non-overlap checks for permission, failed verification/download, installer handoff and expired metadata at normal/enlarged text sizes.
- Proposed Android replay remains NOT EXECUTED. No production fix is included.

Frozen context hashes (not added to the reviewed file CSV):

- app/src/main/java/org/adaway/ui/update/UpdateActivity.java 6567fd634390d007bd633a700ec3759696d2f00ee0dfc85d7e08f9dc338e283d
- app/src/main/java/org/adaway/ui/update/InstallDownloadStatus.java 8776d6a8cdf31387b47a886ff0bf3d3599e71f392c68476c9eb0a9e0135e85f7
- app/src/main/res/values/strings_update.xml e75b82dc02b324a4f10659e06734c47ccb06788fb80c82bb6e0ab86ee161e40b

## Assistant Fullread Files

- app/src/main/res/animator/adaway_fragment_close_enter.xml
- app/src/main/res/animator/adaway_fragment_close_exit.xml
- app/src/main/res/animator/adaway_fragment_open_enter.xml
- app/src/main/res/animator/adaway_fragment_open_exit.xml
- app/src/main/res/interpolator/adaway_fragment_fast_out_extra_slow_in.xml
- app/src/main/res/layout/activity_home_nav.xml
- app/src/main/res/layout/activity_onboarding.xml
- app/src/main/res/layout/log_redirect_dialog.xml
- app/src/main/res/layout/pref_edittext_dialog.xml
- app/src/main/res/layout/source_edit_activity.xml
- app/src/main/res/layout/update_actity.xml
- app/src/main/res/menu/bottom_nav_menu.xml
- app/src/main/res/menu/log_menu.xml
- app/src/main/res/menu/source_edit_menu.xml
- app/src/main/res/mipmap-anydpi/icon.xml
- app/src/main/res/mipmap-anydpi/icon_round.xml
- app/src/main/res/mipmap-anydpi/shortcut_dns_requests.xml
- app/src/main/res/mipmap-anydpi/shortcut_preferences.xml
- app/src/main/res/mipmap-anydpi/shortcut_your_lists.xml
- app/src/main/res/values-night/colors.xml
- app/src/main/res/values-night/styles.xml
- app/src/main/res/values/colors.xml
- app/src/main/res/values/dimens.xml
- app/src/main/res/values/strings_filter_catalog.xml
- app/src/main/res/values/strings_filter_sets.xml
- app/src/main/res/values/strings_hosts.xml
- app/src/main/res/values/strings_source_edit.xml
- app/src/main/res/values/strings_static.xml
- app/src/main/res/values/strings_welcome.xml
- app/src/main/res/values/styles.xml
- app/src/main/res/xml/list_searchable.xml
- app/src/main/res/xml/shortcuts.xml

## Active Resources Excluded

- app/src/main/res/layout/fragment_home.xml
- app/src/main/res/layout/hosts_file_viewer.xml
- app/src/main/res/layout/log_entry.xml
- app/src/main/res/values/strings_domainchecker.xml
- app/src/main/res/values/strings_hosts_viewer.xml
- app/src/main/res/values/strings_lists.xml
- app/src/main/res/values/strings_log.xml
- app/src/main/res/values/strings_prefs_backup_restore.xml

## Existing Reviewed Hashes Excluded

- app/src/main/res/layout/about_activity.xml 13f371677b4f5d269b3b84ca104d918554902152a3fc152f9c1789acbc9713e5
- app/src/main/res/layout/adware_fragment.xml 732a2d9260f06185beb0aecf2b3b98373e230f13171236029f836cd83582d25f
- app/src/main/res/layout/checkbox_list_entry.xml 0cd30e9fbb9e473eef2cab2f29c293c52044996c90f7116e31f0ac2826e23047
- app/src/main/res/layout/checkbox_list_two_entries.xml b49922f1349cbd335dd8dcdc5be23787a097c52b0beb938e0ca5f2dd0ef0ef0e
- app/src/main/res/layout/filter_catalog_item.xml 77f75002bbede4b4ee4cf11ddc7c9b95a1e61faf7da724f20f8447e61d4bdd36
- app/src/main/res/layout/filter_category_header.xml 9bfba3f3ef257bec7057b0bf329a3fba5ea619016cebdbf667be65f66d6a06c5
- app/src/main/res/layout/filter_source_item.xml 34389c66c2f85770dc4ec8b3f8972ec27184fd4441ce4e610de434e2d110d30d
- app/src/main/res/layout/filterlists_import_item.xml 13a8a2943ad0d52399db6150da4109c47e49819c8b29711911b5126c028543fe
- app/src/main/res/layout/fragment_discover.xml 1f578b30dda6d91d6cf9f8c200a7dc32e9d260e0d3657c9beb83ce7422c1f5d2
- app/src/main/res/layout/fragment_discover_catalog.xml 3c13f27d4b72c70b6adfe39270c9346da99c2d409856efc3dc17af4d1fa22be5
- app/src/main/res/layout/fragment_discover_filterlists.xml 946c11f0194932f3d854afad2065c9e8186589c57b4a03ba10dc4c3c01a489d5
- app/src/main/res/layout/fragment_domain_checker.xml 01545fd7b7a0d9d97eaa97ddbeb628b6540be48fb996f50ff416fc8469848be2
- app/src/main/res/layout/fragment_hosts_sources_tab.xml edfc0a3c654ec14410563d81cb20ac41f447946cd79b9ea588eec9c9892b40be
- app/src/main/res/layout/fragment_more.xml 97102fd966d60801c8f96433b378010647d1beda9f65489f1c2c3296a4a483d3
- app/src/main/res/layout/hosts_add_options_sheet.xml fd314007d23b5dd3240e4c95e5fa8ee57c7841db92bca75f41f275baec1c0e2d
- app/src/main/res/layout/hosts_lists_fragment.xml a08b49b8857a434556d9fb197db3a0ae8e180d028ee90e4d717b4feff67fff24
- app/src/main/res/layout/hosts_sources_card.xml 575e4f48bce2ab2969c31e4af9a48f83c582cd97e2e47dcb439068e563169b73
- app/src/main/res/layout/hosts_sources_fragment.xml ee953ff1b5ac3d168773aebb8027a2a266c3ffd6995864d2becc055482d4fd97
- app/src/main/res/layout/list_two_entries.xml f2935958575a1bbc083504af01daa65213f74289c1e42627744f0fb895905e75
- app/src/main/res/layout/lists_allowed_dialog.xml 1fbdf181a3f4a3e86b44ee3f2773f2d1837725ffe9929a57aa00f9dce7d86a51
- app/src/main/res/layout/lists_blocked_dialog.xml fdc2774b057b87f5660721b6d740f9ea90eba8cf50ded7af85956077a66d852f
- app/src/main/res/layout/lists_fragment.xml aa16217bae680ef251fe88e03b87f83514337ce7250b4fed0a7f8222ac45125a
- app/src/main/res/layout/lists_redirected_dialog.xml 22aff7400057ab400acb184a8f8b676257c80dda545516c3db477f4f320f0500
- app/src/main/res/layout/log_activity.xml 5810e8aa45fc65608123d13c0f5e32ee2bda81c389eb0a6a4451db31381b0673
- app/src/main/res/layout/schedules_activity.xml 36ca883cf1dd3abffc6fc307d858d882bb6fc4b009455a7fbb1ee7a6c26b9425
- app/src/main/res/layout/vpn_excluded_app_activity.xml d2849906ee1bbdb357f567315f31682da57bfa84fa20bcb5ccd864b1363cb2fb
- app/src/main/res/layout/vpn_excluded_app_entry.xml e45aaf388886403d752cdfd0215db278fd9a9a5821763eb47879c672a54f932b
- app/src/main/res/menu/checkbox_list_context.xml ba666dc2f523bc4f4c826d378928eabff768cfaf755022ca3d2636274eaf1bf0
- app/src/main/res/menu/hosts_sources_menu.xml ab10a03dfc2b65173d5e4c8673f815da4965ea463e20969ce1386f0cd2c9280b
- app/src/main/res/menu/list_menu.xml 3d731f985941b66e61078bf22468cf3263719e7f70b22c830bb1dcb6cbebb0c4
- app/src/main/res/menu/lists_navigation.xml 0776648950f816e55032a78c3a72ac2f8f835f695574ab44922c517486dd427b
- app/src/main/res/menu/vpn_excluded_app_menu.xml 5d2c8bf5e9917a816609a558b465ed7f08408fd498ccde7385ed7bb0e114b852
- app/src/main/res/values/preferences.xml 06321f8d37bf49d60c625a8e3ff58f3d7bd95a7664896219d0ca490c8f741e67
- app/src/main/res/values/strings.xml e73aa63f376fb95144bcb85ef73e210a46b2a9cb1e06c83ec7c3e06ed33bdc65
- app/src/main/res/values/strings_app.xml 7ed409de1ef75d4f196a84394b8c6676f13beca845b54d2bd91ae59f2cd02074
- app/src/main/res/values/strings_errors.xml 43868b52a73084827e76a6cfc552b123c4fd5e0b2fc7fa84227c5d1fb0eca967
- app/src/main/res/values/strings_home.xml 67b4dc8810cea9d6bf4dd288306c3df26bcadb5b342539120cbef76b56ed7c27
- app/src/main/res/values/strings_notification.xml 6e34942be1fb91bfc52b25cd280deab8e5c50e708096d3f9d071cb2845b47470
- app/src/main/res/values/strings_prefs_main.xml 41da985edced0fe78043cb800ec9cf8928bcc614dc145fc49d94f6990114ff69
- app/src/main/res/values/strings_prefs_root.xml b5ffaec3f65e763fdb21e2ae0b6a3febad4cc84a5dde267e595ac98c967aefe3
- app/src/main/res/values/strings_prefs_update.xml 1753871bd8e77776c9b270f673b79655ab9bca2f021ccf6512f8ba7615fad1f4
- app/src/main/res/values/strings_prefs_vpn.xml 4563d79d121ff7b02d7eea84d348744e53a59872dc3cabd892fded4588408d19
- app/src/main/res/values/strings_update.xml e75b82dc02b324a4f10659e06734c47ccb06788fb80c82bb6e0ab86ee161e40b
- app/src/main/res/xml/data_extraction_rules.xml 2d9361dacc7911f63fe96cf891b1fe6f36343dad2df35e281764d02560b3b801
- app/src/main/res/xml/full_backup_content.xml c4a4d5ad5fb91fb839f02854272e8ebc22ce5ddbb041315fc9853d7146322380
- app/src/main/res/xml/preferences_backup_restore.xml bc10116eacd9d6891857e82ae9ffdf9d147ce8bbc53b18d0bdc1d06e5185c45a
- app/src/main/res/xml/preferences_main.xml 6e2ad4b09c993ddccbc95d61f872f533941a358a789fad4a4f5417c54caf357d
- app/src/main/res/xml/preferences_root.xml d6fe131240df9dde81b5c5e9611492df9d546b4bac1395aa932e935a36e88008
- app/src/main/res/xml/preferences_update.xml da33287ab6e3dd4fbff2c9b052da59e71d4decb314415d64387f0c2e52ea1ef2
- app/src/main/res/xml/preferences_vpn.xml afa90b17024a44092a80503132fa292deb951c0ec6ffe4118af57b764b3f95fa

## Unchecked Bitmap Scope

All 15 below remain pending, not reviewed. Their hashes are inventory identity only; they are intentionally absent from resources-audit-files.csv so importing that CSV cannot credit them.

- app/src/main/res/mipmap-hdpi/icon.png 9e1377fbbb22a6489dc59a6d5bcc75a16cf747c7d35b92ea40c8dad8977247ec
- app/src/main/res/mipmap-hdpi/icon_foreground.png 31a99186703a560228eec1236aa099eaa51738aefe126dea0927f28f3fa7745f
- app/src/main/res/mipmap-hdpi/icon_round.png d724456e7a635e94e23598011673afa12da2ee0608e52d405a37e44feddbb519
- app/src/main/res/mipmap-mdpi/icon.png 0d0108483bb951e59e1ed929b17f402f59d757683491019c000c19ab806fe9d3
- app/src/main/res/mipmap-mdpi/icon_foreground.png 8cb34fd47f37c5466ce26d7b7bbe3e7a8f50ee1c5c00bd49b181f9af02456bfd
- app/src/main/res/mipmap-mdpi/icon_round.png 05804f15a6d469d6f6af3d9ef578059b8aa5ba5c7e11612fdb4a29e6d0e91c2d
- app/src/main/res/mipmap-xhdpi/icon.png 3ef343e350a8d8ad7a95bc75c2f450cd49b4fb6f08a54bb861ae82064535d3d6
- app/src/main/res/mipmap-xhdpi/icon_foreground.png 4ed2b0ebc2995f42b06079119377588bceef1038fd741974e2db10cddea5f499
- app/src/main/res/mipmap-xhdpi/icon_round.png 92eaec8b4216d2272fdf12887b648a1c0fd2715e2319030e5b28641e52539543
- app/src/main/res/mipmap-xxhdpi/icon.png 40827cb9534abd625166413608f3e46095651aae9ce6397b91580079cf20480f
- app/src/main/res/mipmap-xxhdpi/icon_foreground.png 426bfa103064757f025ee1471e352fb05c2d75bd6303f26156a78898a3888d91
- app/src/main/res/mipmap-xxhdpi/icon_round.png 1c9fb578d9570a5c0dbabac47437e18da6463bb7e69a9815953538e50aeeefaf
- app/src/main/res/mipmap-xxxhdpi/icon.png afc66c257e9c511700419eaffed6bd894bea386a757df4a6d40c9c0da1f75d4d
- app/src/main/res/mipmap-xxxhdpi/icon_foreground.png ca41366222c766a9afe6725b410cd9b785ab4056120be6c6654d0f8545fd395e
- app/src/main/res/mipmap-xxxhdpi/icon_round.png 2490fe1e40871a01392190b7a99998373ec811eb97f15b681ba76aa0626f526b

## Handoff

resources-audit-files.csv uses path,sha256,assessment,bug_ids exactly. resources-audit-bugs.csv uses the central register's 11 columns exactly and contains one unique RES-AUD-001 row. Central inventory/register/task files were not edited or imported.

Final handoff validation checks CSV schemas, duplicate paths/IDs, each XML hash, complete eligible-scope equality, absence of active/previously reviewed/bitmap paths, nonempty methodology assessments and bug-reference integrity. Any later integration edit invalidates that path's hash-bound credit; do not silently carry this review onto changed bytes.

Final validation PASSED: 1,088 unique rows and matching on-disk SHA-256 hashes; exact fresh eligible-scope equality; zero active/previously-reviewed/bitmap entries; 4 file columns; 11 bug columns matching central bugs.csv; one unique RES-AUD-001; 32 assistant-fullread rows and 1,056 parser-semantic-only rows. HEAD was unchanged. The three sidecars are new untracked files, ready for the main lane to inspect and integrate; no import or commit was performed.

Execution caveats: the first in-memory Ruby traversal used filter_map unavailable in Ruby 2.6 and was corrected to map.compact before coverage was counted. A later redundant per-file git verification timed out and reset the in-memory session; no files were written by that probe. Scope/trees and exact format comparison were reconstructed successfully, HEAD remained unchanged, and the final direct file-hash check is the authoritative handoff check.
