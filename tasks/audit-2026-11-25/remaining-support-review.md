# Remaining Support Review

Date: 2026-11-25. Branch: `codex/full-codebase-audit`.
HEAD: `d51eb7aea06871c2c6ad2bbedb9ddbaaaca48d77`.
Repository: `/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway`.
Paths and line references below are relative to this repository.

## Scope and Evidence

- Read `CLAUDE.md` and `tasks/lessons.md` first, then the audit README, central
  bug register/schema, relevant existing lane records and coverage entries.
- Fully read all 24 assigned Java files, including all methods and imports.
  Fully read eight directly associated XML resources; seven are new coverage.
  `strings_log.xml` already has matching-byte native-lane coverage and is excluded
  from this lane's new-coverage CSV. The CSV therefore records 31 full-file reads:
  24 assigned Java files and seven associated resources, each with its read SHA256.
- Three new P2 findings, all `source-confirmed`. No app execution or Android
  reproduction occurred. Reproduction and regression instructions are proposals,
  not passed tests. No live Git-host service behavior is claimed.
- Only the three new `remaining-support-*` sidecars were written. No production,
  test, central-ledger or other-agent changes; no Gradle, compilation, emulator,
  adb, install/uninstall, root runtime, commit or push. `LogActivity.java` and
  `LogViewModel.java` were excluded from this review, including content searches.

## Findings by Severity

### SUP-AUD-001 - P2 - Scanner Cannot Request Uninstall Without Permission

Owned location: `app/src/main/java/org/adaway/ui/adware/AdwareFragment.java:139-142`.
Supporting declarations: `app/src/main/AndroidManifest.xml:31-44` and
`app/build.gradle:22`.

The scanner's advertised remediation launches ACTION_DELETE with a package URI.
The application targets API 34, but its main, debug and directRelease manifests
do not request REQUEST_DELETE_PACKAGES or hold DELETE_PACKAGES. Existing merged
debug and directRelease manifests independently confirm target 34 and neither
permission. They were inspected only, not rebuilt or treated as runtime proof.

This is not inferred solely from ACTION_UNINSTALL_PACKAGE documentation: the
[AOSP installer manifest](https://android.googlesource.com/platform/frameworks/base/+/master/packages/PackageInstaller/AndroidManifest.xml)
routes both package DELETE and UNINSTALL_PACKAGE to UninstallerActivity. Its
[permission gate](https://android.googlesource.com/platform/frameworks/base/+/b927d9db6bff/packages/PackageInstaller/src/com/android/packageinstaller/UninstallerActivity.java)
rejects target-P-or-newer callers lacking both permissions before displaying
confirmation. The [normal permission contract](https://developer.android.com/reference/android/Manifest.permission#REQUEST_DELETE_PACKAGES)
provides the appropriate app-level permission; privileged deletion is not needed.

Proposed reproduction: on an ordinary nonprivileged Android 9+ install, install a
disposable, launchable fixture with a component matching `com.airpush.`; open More
> Adware Scanner and tap its detected row. Expected: system confirmation names
the fixture. Source-predicted result: the AOSP uninstaller finishes at its
permission gate, leaving the package installed. Exact OEM presentation has not
been observed. Do not test against a real user's application.

Likely fix: declare REQUEST_DELETE_PACKAGES in the app manifest used by every
scanner-enabled variant and retain system confirmation. Merely replacing
ACTION_DELETE with ACTION_UNINSTALL_PACKAGE does not fix the permission gate.

Smallest regression: assert the merged app permission, then tap a detected
disposable fixture's row and assert the real system confirmation is shown;
cancel and verify the fixture remains installed. Add a separate affirmative
uninstall case in a disposable fixture environment. Recording an emitted intent
alone cannot prove this boundary works. Package-list refresh is SUP-AUD-002.

### SUP-AUD-002 - P2 - Adware Results Remain Stale After Package Changes

Owned locations: `ui/adware/AdwareLiveData.java:54-56`,
`ui/adware/AdwareViewModel.java:24-26`, and `ui/adware/AdwareFragment.java:76-80`
under `app/src/main/java/org/adaway/`.

The only call scheduling `loadData()` is the LiveData constructor. The ViewModel
retains that one LiveData instance, and the fragment obtains the ViewModel from
the parent activity. Neither class implements a refresh, package-change observer,
on-active reload, resume rescan or uninstall-result callback. Navigating Back and
reopening the scanner reuses the same activity-owned result; rotation retains it
as well. The layout provides no manual refresh action.

Proposed reproduction, independent of SUP-AUD-001: scan a launchable disposable
fixture containing a matching component, uninstall it through Android Settings
while keeping AdAway's activity/process alive, and return to the scanner. Then
Back out and reopen it in the same activity. Expected: removed package disappears
and the empty-state message appears if it was the last match. Source-predicted
result: its old row remains. Conversely, installing a matching fixture after an
empty scan leaves the cached no-adware result until the activity ViewModel is
discarded. No real package changes were performed in this lane.

Likely fix: expose an asynchronous refresh in the model, invoke it on scanner
activation/return, and invalidate on relevant package changes if observing them.
Keep the dedicated package-scan executor and prevent an older scan from replacing
a newer result. Do not optimistically remove the row when uninstall is canceled.

Smallest regression: one activity-owned ViewModel, initial matching package
snapshot, changed package snapshot, scanner resume/reopen, then assert the new
list and empty state. Include unchanged/canceled uninstall and rotation controls.
No production/test modifications or execution were made here.

### SUP-AUD-003 - P2 - DNS Allow and Redirect Have Swapped Spoken Labels

Owned binding: `app/src/main/java/org/adaway/ui/log/LogAdapter.java:73-75,78-85`.
Associated resource: `app/src/main/res/layout/log_entry.xml:29,43`.
Supporting text: `app/src/main/res/values/strings.xml:324-326`.

The adapter binds `allowImageView` to ALLOWED and `redirectionImageView` to
REDIRECTED. The XML describes Allow as `tcpdump_entry_add_redirect` and Redirect
as `tcpdump_entry_add_whitelist`. Parsed XML/default-string inspection confirmed
the descriptions are respectively "Add entry to redirects" and "Add entry to
white list". Binding never corrects either contentDescription.

Proposed reproduction: populate a neutral DNS log row and inspect its accessibility
nodes, or focus the action icons with TalkBack. Activating the action announced
as adding a redirect instead invokes the ALLOWED callback; the announced allow
action invokes REDIRECTED. This is an action mismatch, not a visual-style concern.
No TalkBack or UI execution was performed.

Likely fix: swap the two resource references so accessible names agree with the
bound ListType. Also give a selected icon an appropriate remove-action description
when `bindImageView` changes its listener to removeListItem; all three current
static descriptions still say Add in that state. This is one labeling/state issue,
not a separate duplicate finding.

Smallest regression: inflate and bind a row with a recording LogViewCallback;
assert each icon's contentDescription matches the ListType received on click.
Rebind through null, ALLOWED, REDIRECTED and BLOCKED states and assert the selected
icon announces removal and calls removeListItem. Then verify the corrected
announcements once with a screen reader. No database or VPN fixture is necessary
for the adapter-level regression.

## Other Assigned Files

No additional actionable bug was established in the five Git metadata classes,
AdwareInstall, LogEntry, LogEntrySort, LogViewCallback, AboutActivity, MoreFragment,
Animations, the two error classes, AdBlockMethod, UndefinedBlockModel, Clipboard,
Constants or the two logging helpers. Their full-read assessments are in the CSV.
This does not certify remote APIs, all URL/ref forms, UI sizes/locales, logging
backends, native features or asynchronous runtime behavior.

Counterchecks avoided speculative findings: LogAdapter resets both icon listeners
and tint when rebinding; null ListType is handled. Domain-suffix comparison has a
defined prefix/tie result. Git JSON/date parse failures return unknown dates through
their existing handlers. Animations' animated paths use auto-cancel; no failing
current-caller ordering was established. Stub/DSN gates exist before enabling
telemetry. Scope did not include reopening the already-reviewed log activity/model.

## Coverage Boundaries

- New whole-file coverage: exactly the 31 paths in `remaining-support-files.csv`.
- Full-read but already covered: `app/src/main/res/values/strings_log.xml`, SHA256
  `40434f216381036a70dedff0ba4b433f386d7fad99260d58a04f3ea8b024bef7`.
- Partial supporting reads, not new whole-file coverage: main manifest lines 1-85;
  build SDK/Sentry declarations; selected `strings.xml` entries; SourceModel's
  Git metadata dispatch at lines 498-516; animation call-site searches in Home and
  SourceEdit. Search hits in existing tests establish names only, not test review
  or execution. No translated XML or drawable assets were audited.
- Debug and directRelease overlay manifests were read as permission dependencies,
  not claimed as a new release-lane audit. Existing merged manifests were parsed
  only for target SDK and uninstall permissions. Observed SHA256 values:
  debug `ff073896dd91ef5538ffa98d539d751a6a6def48dea9db1e63eb8237e504af32`;
  directRelease `1907c18770516b7272e6a6ea2eb9cc36898c07da36f67721ed0d88d3b1611c1c`.
  Generated artifacts may change under the main lane and are not file coverage.

## Sidecar Validation

Final read-only Ruby CSV/Digest/Open3 validation passed: exact central 11-column bug schema, the
4-column coverage schema, unique/new bug IDs, all 24 assigned paths, 31 unique
coverage paths, current SHA256 matches, no duplicate matching-byte coverage, bug
cross-references and patch whitespace. Git no-index exit 1 for new-file differences
was accepted only with no whitespace diagnostics. These are report-integrity checks only.
The main lane owns central import, implementation and Android regression gates.
