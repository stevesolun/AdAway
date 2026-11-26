# Bounded Tooling Audit - 2026-11-25

## Scope and Result

Repository: /Users/steves/Steves_Files/Work/Research_and_Papers/AdAway
Branch observed: codex/full-codebase-audit
Inventory snapshot HEAD: f7dbbb5e7a8eca6983135f04adc945bab6518308
Review identity: current working-tree SHA-256, not HEAD alone.

This is the requested bounded read-only non-native tooling pass, not completion
of the full-repository audit. Six actionable findings are recorded as open,
TOOL-AUD-001 through TOOL-AUD-006, all P2. No product fix is claimed.

Only these three new files were written by this pass:
- tasks/audit-2026-11-25/tooling-audit-files.csv
- tasks/audit-2026-11-25/tooling-audit-bugs.csv
- tasks/audit-2026-11-25/tooling-audit-review.md

No Gradle, device/ADB, real network request, release/deletion script, OpenSSL,
git mutation, workflow dispatch, test report generation, or source modification
was performed. Python used -B; probes kept output in memory. The certificate
helper was syntax-parsed and inspected, never executed. No preexisting audit
artifact was edited. Audit ledgers and release-safety records were consulted
only as selection/schema/deduplication metadata, not audited or credited as
source coverage. Active dirty source files were excluded.

CLAUDE.md and tasks/lessons.md were read in full. The active audit section of
tasks/todo.md was read, not its entire historical ledger. Instructions to edit
tasks/todo.md or lessons were superseded by this pass's explicit three-file
write limit.

## Coverage Accounting

The fixed candidate universe contains 114 inventory entries: scripts/,
sentrystub/, gradle/, .github/, .tx/, .claude/, .vscode/, docs/ excluding PNG
images; Resources/certificate/; selected root build/contributor/license
documents and .gitignore; app/build.gradle; third_party/maven/README.md.
All tasks/audit-2026-11-25 artifacts, native trees, other app code, images and
other vendored binaries are outside this candidate universe. The three Gradle
keyring/wrapper artifacts are retained as explicit unread entries below.

| Category | Count | Meaning |
| --- | ---: | --- |
| Newly fully read and assessed | 36 | Exact paths/hashes in tooling-audit-files.csv; 2,277 lines / 101,346 bytes |
| Already reviewed at current hash | 48 | Prior ledger evidence only; no new coverage credited |
| Still unassessed by this pass | 30 | 29 unread files plus CLAUDE.md, read as instructions only |
| Total fixed candidate universe | 114 | 36 + 48 + 30 |
| Additional full-file context reads | 9 | Eight previously reviewed build files plus SentryLog; excluded from the 36 new rows |
| Dirty untracked script excluded | 1 | scripts/tests/support-fixes.Tests.rb; not in the tracked candidate universe |

All 36 new review targets were clean and their hashes were rechecked before
writing. Concurrent edits elsewhere were present and were neither reverted nor
claimed. [tooling-audit-files.csv](tooling-audit-files.csv) contains only the 36 new assessments, no context-only files,
no artifact self-hashes, no dirty files, and no previous current-hash reviews.

### Scripts and Tests

All 23 tracked files under scripts/ already had current-hash review evidence;
zero clean pending scripts/tests remained there at selection time. The dirty
untracked scripts/tests/support-fixes.Tests.rb was excluded. The 23 files were
not rerun or credited to this pass. This specifically includes the verified
release-safety fixes and their tests; REL-007/REL-008 were not reopened or
duplicated. Other existing release findings were not copied into TOOL-AUD IDs.

The remaining three first-party executable text files outside scripts/ were
fully read: Resources/certificate/generate.sh and the two Python utilities in
docs/wa-tg-audit/. The classifier received ten mocked function scenarios. The
scanner received ten offline scenarios against its actual functions/main with
downloads replaced and all three file outputs captured in StringIO.

These March audit utilities contain fixed historical data, Windows output
paths and a task-specific output destination. They were treated as historical
support tools, not production runtime code or current live-site evidence.
Their saved JSON/Markdown outputs were not audited, refreshed or overwritten.
No broad portability/style finding was added merely for their one-off origin.

### Root Build and Gradle

All active root/app/Sentry build text was already reviewed at its current hash.
The following eight files were nevertheless read in full as bounded context:
- build.gradle
- settings.gradle
- gradle.properties
- gradle/libs.versions.toml
- gradle/wrapper/gradle-wrapper.properties
- app/build.gradle
- sentrystub/build.gradle
- sentrystub/src/main/AndroidManifest.xml

The active modules remain app and sentrystub; dormant native modules are not
included. Repository filters restrict the local/JitPack mirror to libsu.
Release signing/update trust checks, Sentry dependency selection, Java/SDK
settings, release resource stripping and SBOM configuration were inspected.
No new build-code defect was established. TOOL-AUD-006 concerns the contributor
command, not a request to weaken release trust checks.

gradlew and gradlew.bat were skipped because of prior current-hash reviews.
The wrapper JAR and both keyrings remain unread/unverified. Verification XML
was parsed only for the documented 31 ignored-key and 345 key-download SHA-256
fallback counts; it is not new full-file review credit. Local mirrored AAR,
module and POM bytes match the three README hashes; authenticity and upstream
provenance were not checked. No dependency-resolution/CVE/build-success claim.

### Sentry Stubs

All 11 stub Java files plus sentrystub/.gitignore were fully read. The ninth
additional context file, app/src/main/java/org/adaway/util/log/SentryLog.java,
was read in full. A clean-file-only caller search found the current Sentry API
uses in that helper. The three-argument Timber integration constructor matches;
STUB detection is present; the missing close method is intentionally caught;
the init/configureScope callbacks deliberately do not run. No actionable stub
defect was established. No Java/Android compilation or R8 execution is claimed.

### CI and Documentation

All 12 workflow/helper files under .github/workflows/ were already reviewed at
their current hashes, including the 11 YAML workflows. They were skipped.
The seven pending GitHub metadata/template files were fully read; the four
YAML files parsed successfully. No live hosted-runner, account/owner or issue
form validation was attempted.

Both translation configurations were read completely, including language maps.
Each declares 23 sources and the same four missing files. The three reviewed
editor/agent JSON files parsed successfully; descriptive custom settings are
not claimed to enforce a security boundary. Contributor/translation/license,
dependency-verification, rule-capability and mirror documentation were read
within the 36-file manifest. No external links or licensing authorities were
queried, and no license/provenance approval is implied.

## Findings

### TOOL-AUD-001 - Exact Hosts Become Suffix Blocks (P2)

Source: docs/wa-tg-audit/scan_lists.py:175-225, 257-268, 349-365.

The extractor returns an untyped set of domains. domain_is_blocked then walks
parent suffixes for every element. A single hosts entry
`0.0.0.0 whatsapp.net` produces seven critical-domain hits, including
mmg.whatsapp.net and static.whatsapp.net, although hosts/plain-domain rules are
exact matches. The capability document confirms this boundary. This inflates
risk scores and can drive unnecessary allowlist additions.

Keep exact and suffix rule kinds through parsing and merging. Regression tests
must distinguish an exact parent from a supported suffix rule, and check label
boundaries. The direct-host, unrelated-domain and plain-ABP controls succeeded.
Browser-option ABP syntax was also observed to be overaccepted; preserving
supported syntax/rule kind should include rejecting such context-only input,
but it is not counted as a separate finding in this bounded pass.

### TOOL-AUD-002 - Valid Blocked Hosts Disappear (P2)

Source: docs/wa-tg-audit/scan_lists.py:176-205.

The hosts regex extracts only the first alias, cannot consume leading space or
IPv6 loopback, and finding any hosts record suppresses plain-domain parsing for
the entire input. Five fixtures containing mmg.whatsapp.net return zero hits:
IPv4 aliases for both 0.0.0.0 and 127.0.0.1, an ::1 mapping, an indented mapping,
and mixed hosts/plain-domain input. A normal IPv4 mapping is recognized.

Context-only inspection of SourceLoader.java:583-635 shows per-line handling,
alias iteration and accepted IP forms; this app file was not fully audited or
credited here. Parse each line and every alias with supported address semantics.
Regression tests must retain comments/localhost/unrelated negative cases.

### TOOL-AUD-003 - Allowlist Advice Reopens Ads and Telemetry (P2)

Source: docs/wa-tg-audit/scan_lists.py:381-448, 545-583.

A mocked list containing only ads.telegram.org and crashlogs.whatsapp.net has
zero risk and no high_risk_lists, using the script's own NONE/LOW impact labels.
Nevertheless its report recommends both domains, plus broad *.telegram.org and
*.whatsapp.net patterns. The unprotected set ignores impact, and the advice
then adds all unprotected hosts and unconditional wildcard roots.

Restrict compatibility advice to justified positive-impact exact hosts and
preserve ad/telemetry blocks. Broad unblocking requires a separate explicit
decision, not an automatic repair suggestion. Regression: the telemetry-only
fixture must produce no allowlist additions; a true compatibility fixture must
recommend only its required exact host. No claim about current external
WhatsApp/Telegram service behavior is necessary for this internal contradiction.

### TOOL-AUD-004 - Certificate Helper Deletes Caller Files First (P2)

Source: Resources/certificate/generate.sh:1-9.

The first command is `rm localhost.*` in the caller's directory; it includes
localhost.backup and localhost.notes, not just generated keys/certificates.
Only later does OpenSSL load the equally relative ssl.conf. In the repository
root that config is absent, whereas Resources/certificate/ssl.conf exists.
Thus a natural root-directory invocation can remove matching unrelated files
and then fail to generate replacements. This is a dormant helper issue, not
a demonstrated APK vulnerability.

Anchor explicit paths, validate prerequisites and stage successful output
before replacing exact targets. A future mocked command-trace regression must
cover foreign CWD and generation failure while preserving original/unrelated
files. The original script was NOT executed; bash -n and non-writing glob/path
checks establish the command ordering and overbroad target set.

### TOOL-AUD-005 - Translation Sources No Longer Exist (P2)

Source: .tx/config:104-143 and .tx/github-integration.yaml:72-101.

Both source sets contain:
- app/src/main/res/values/strings_support.xml
- app/src/main/res/raw/help_faq.html
- app/src/main/res/raw/help_problems.html
- app/src/main/res/raw/help_s_on_s_off.html

All four are absent locally. Those resources cannot be read for source sync.
Retire or correct the mappings in both configurations and add a local
source-existence/set-consistency guard. The exact service failure/partial-sync
behavior was not exercised; the confirmed issue is the dangling source paths.
No dirty resource contents were read.

### TOOL-AUD-006 - Fresh Contributor Build Hits Release Guards (P2)

Source: CONTRIBUTING.md:56-69; context app/build.gradle:80-127.

After SDK setup the quickstart says `./gradlew assembleRelease`, without the
four signing properties, update public key or signer SHA-256 required by the
current build. The task-name predicate selects the release trust path and the
no-signing-properties branch throws before task execution.

Use assembleDebug for initial contributor builds and link the maintained
release guide for production signing. Validate the documentation contract
locally; reserve a clean-home build for a separately authorized Gradle run.
This was source/logic-confirmed, not an executed Gradle failure.

## Verification Results and Limits

| Check | Observed result |
| --- | --- |
| Scanner actual functions/main | 10 offline scenarios; three findings reproduced; all report writes captured in memory |
| Classifier actual functions | 10 passing tag/name/URL/retry scenarios; urlopen and retry sleeps mocked; main not executed |
| GitHub metadata YAML | Four YAML documents parsed |
| Translation configuration | YAML and ConfigParser each found 23 sources and exactly four missing paths |
| Agent/editor JSON | Three documents parsed |
| Certificate shell | bash -n passed; non-writing glob checks match backup/notes; root ssl.conf absent |
| Contributor command | Local source-path/predicate check confirms release trust guard selection; no Gradle |
| Verification policy documentation | 31 ignored keys and 345 SHA-256 key-download fallbacks agree |
| Mirrored dependency documentation | Three documented SHA-256 values match local bytes; no provenance claim |
| Coverage freeze | All 36 new targets clean and hashes unchanged before artifact creation |
| Final artifact validation | 36 unique file rows and matching hashes; exact four/11-column headers; six globally new TOOL-AUD IDs; all references valid; ASCII output |
| Embedded reproduction | Executed the shell block from this report successfully; ten offline scanner scenarios reproduced the same results |

The final branch and HEAD matched the snapshot above. All 36 review targets
were still clean at final validation. Git reported exactly the three requested
tooling-audit files as new lane artifacts; unrelated concurrent work remains
outside this pass. No preexisting artifact, source or central ledger was edited.

The scanner's ten scenarios are reproduction probes, not a green claim about
the incorrect behavior. They intentionally assert the existing failure results
so that the findings are reproducible; post-fix regression expectations are in
the findings and bugs.csv. No existing release/deletion tests were executed.

A diagnostic count initially selected ignored-key reasons when looking for
checksum fallbacks; an element-aware count confirmed the documented 345
sha256 reasons. A Ruby tally-based status summary was rerun using a compatible
counter. Neither diagnostic failure changed files or invalidates the evidence.

## Reproducible Offline Scanner Probe

Run from the repository root. This does not run the file's __main__ entry,
download data, write reports or import repository modules. The only explicit
main call occurs after replacing the download and output functions.

```sh
python3 -B -c 'import ast, contextlib, io, json, sys, urllib.request
from unittest.mock import patch
ns = {"__name__": "tooling_audit_probe"}
exec(compile(ast.parse(sys.stdin.read()), "scan_lists.py", "exec"), ns)
scan = ns["scan_list_for_critical"]
hits = scan("exact", "0.0.0.0 whatsapp.net\n")
assert len(hits) == 7 and any(h["domain"] == "mmg.whatsapp.net" for h in hits)
print("REPRO TOOL-AUD-001: exact host incorrectly produces seven domain hits")
missed = {
    "alias_0": "0.0.0.0 unrelated.example mmg.whatsapp.net\n",
    "alias_127": "127.0.0.1 unrelated.example mmg.whatsapp.net\n",
    "ipv6_loopback": "::1 mmg.whatsapp.net\n",
    "mixed": "0.0.0.0 unrelated.example\nmmg.whatsapp.net\n",
    "indented": "  0.0.0.0 mmg.whatsapp.net\n",
}
for name, content in missed.items():
    assert scan(name, content) == [], name
print("REPRO TOOL-AUD-002: five valid input fixtures produce no critical hits")
assert scan("direct", "0.0.0.0 mmg.whatsapp.net\n") == [
    {"domain": "mmg.whatsapp.net", "via": "mmg.whatsapp.net"}
]
assert scan("unrelated", "0.0.0.0 unrelated.example\n") == []
assert len(scan("plain-abp", "||mmg.whatsapp.net^\n")) == 1
class Capture(io.StringIO):
    def close(self):
        pass
writes = {}
def fake_open(path, mode="r", **kwargs):
    assert mode == "w"
    writes[path] = Capture()
    return writes[path]
ns["ALREADY_KNOWN"] = {}
ns["LISTS_TO_SCAN"] = [{"name": "fixture", "url": "https://invalid.example/fixture"}]
ns["download_list"] = lambda *args: (
    "0.0.0.0 ads.telegram.org\n0.0.0.0 crashlogs.whatsapp.net\n"
)
ns["open"] = fake_open
with patch.object(urllib.request, "urlopen", side_effect=AssertionError("network forbidden")):
    with contextlib.redirect_stdout(io.StringIO()):
        ns["main"]()
report = json.loads(next(v.getvalue() for k, v in writes.items()
                         if k.endswith("domain-scan-report.json")))
assert report["high_risk_lists"] == []
for domain in ("ads.telegram.org", "crashlogs.whatsapp.net", "*.telegram.org", "*.whatsapp.net"):
    assert domain in report["recommended_additions_to_allowlist"], domain
assert len(writes) == 3
print("REPRO TOOL-AUD-003: zero-risk fixture recommends ad/telemetry unblocking")
print("PASS: 10 offline scenarios; three report writes captured in memory; no network")' < docs/wa-tg-audit/scan_lists.py
```

Expected current output: three REPRO lines for TOOL-AUD-001/002/003, followed
by PASS for ten offline scenarios and memory-only outputs. A corrected scanner
should fail the intentionally old-behavior assertions until these are updated
to the regression expectations above.

## Explicit Unread Files

The following 29 files remain unread and unassessed by this pass. Hashing,
counting bytes/lines or recognizing filenames is not review credit.

- .claude/commands/c-review.md
- .claude/commands/db-analyze.md
- .claude/commands/git-history.md
- .claude/commands/investigate-perf.md
- .claude/commands/java-review.md
- .claude/commands/parallelize.md
- .claude/commands/review-threading.md
- .claude/commands/team.md
- .claude/commands/trace-dataflow.md
- .claude/commands/ultrathink.md
- .claude/hooks/pre-commit.md
- .claude/hooks/pre-edit-database.md
- .claude/hooks/pre-edit-native.md
- CHANGELOG.md
- CODE_OF_CONDUCT.md
- LICENSE.md
- docs/audit/2026-03-05-fix-report.md
- docs/audit/2026-03-05-full-audit.md
- docs/audit/priority-matrix.json
- docs/audit/recon-report.json
- docs/plans/2026-02-25-adaway-modernization-plan.md
- docs/wa-tg-audit/allowlist-fix-report.md
- docs/wa-tg-audit/catalog-analysis.json
- docs/wa-tg-audit/domain-scan-report.json
- docs/wa-tg-audit/domain-scan-summary.md
- filterlists-analysis.md
- gradle/verification-keyring.gpg
- gradle/verification-keyring.keys
- gradle/wrapper/gradle-wrapper.jar

CLAUDE.md is the 30th still-unassessed candidate: read completely as repository
instructions, not audited as a documentation deliverable. tasks/lessons.md is
also instructions-only and outside the candidate universe. The rest of
tasks/todo.md remains unread. The 48 prior-reviewed candidates were not newly
claimed; only the eight build context files listed above were fully reread.
Other app/native/resource files and PNG images are out of scope, not implicitly
reviewed. Stop here rather than expanding into those independent audits.
