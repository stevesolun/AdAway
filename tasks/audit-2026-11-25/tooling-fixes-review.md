# Tooling Fixes Handoff - 2026-11-25

Repository: /Users/steves/Steves_Files/Work/Research_and_Papers/AdAway
Branch: codex/full-codebase-audit
Disposition: ready for main's review; not staged or committed.

## Scope and Status

Implemented TOOL-AUD-001 through TOOL-AUD-006 in exactly the five approved
existing paths, with one new focused offline test file:

- docs/wa-tg-audit/scan_lists.py
- Resources/certificate/generate.sh
- .tx/config
- .tx/github-integration.yaml
- CONTRIBUTING.md
- scripts/tests/test_tooling_audit_fixes.py (new)

The five existing files matched their audited baseline hashes before editing.
No app/build/dependency files, release guard, certificate config, historical
scan output, old audit report or central audit ledger was edited. Main's dirty
and frozen application work was left alone.

New lane sidecars are tooling-fixes-files.csv, tooling-fixes-bugs.csv,
tooling-fixes-tests.csv and this report. All six findings are marked
verified-fixed in this lane's bug CSV, meaning the scoped offline contracts
below passed. This is not a full-repository, release, Android or hosted-service
verification claim. Main owns review and integration.

No actual certificate generation, repository deletion, remote request,
Transifex operation, Gradle, ADB/device operation, git stage/commit or other git
mutation was performed. The certificate script was executed only as a copy
inside disposable temporary fixtures with a replacement OpenSSL executable.
Baseline deletion affected deliberately created test fixtures only. Neither
the baseline nor fixed repository helper was executed in place.

## Scanner Fixes

### TOOL-AUD-001: Exact and Suffix Semantics

The existing scanner now keeps exact hosts and explicit suffix rules in
separate sets. Host records and plain domains stay exact; context-free ABP
rules use the same ADBLOCK_DOUBLE_PIPE pattern as SourceLoader, including its
optional final caret. Browser options, paths, exceptions and cosmetic rules
remain unsupported rather than being promoted to global DNS blocks.

This is a modest implementation of the scanner's existing hosts/plain/ABP
subset, not a replacement domain engine or a port of every app syntax.
No new parser dependency or source format was added. A focused test reads the
current app constants and ABP pattern and checks the supported subset for
drift. It does not compile or execute Java and does not claim full parser parity.

Resolved raw_hits are preserved during merge, so the report neither expands
an exact hit a second time nor loses a suffix hit's original matching parent.
Historical ALREADY_KNOWN entries are treated as exact hosts. The existing
blocks list remains available in the report; raw_hits is additive evidence.
A caller search found no other Python callers of the changed extraction
helper outside the scanner and the new test file.

### TOOL-AUD-002: Hosts Aliases and Mixed Input

Parsing is per line, with whitespace and inline comments handled before
consuming every valid alias. The app's blocking address literals are honored:
0.0.0.0, 127.0.0.1 and ::1. An unrelated hosts record no longer suppresses
plain-domain lines elsewhere in the same input. Existing domain regex
validation is retained; unsupported redirect/browser input is not inferred
to be a block.

The five originally missed inputs now produce the expected critical hit:
two IPv4 alias forms, IPv6 loopback, indentation, and mixed hosts/plain domains.
Case, duplicate, malformed-alias and comment controls remain green.

### TOOL-AUD-003: Conservative Compatibility Advice

Recommendations are exact unprotected hosts with positive compatibility
impact only. Ads and telemetry are still reported as blocked evidence but
are never automatically suggested for allowlisting. Parent expansion and
unconditional broad wildcard recommendations were removed.

JSON and Markdown consume the same filtered recommendation set. The Markdown
no longer labels all unprotected hits as requiring allowlist entries or
proposes future wildcard expansion. Empty scans and telemetry-only inputs
produce no advice; a mixed fixture recommends only mmg.whatsapp.net; suffix
hits do not reopen telemetry siblings; already-allowed hosts are not repeated.

Historical data, fixed output locations and the saved report files were not
refreshed. Tests call the actual main function only after replacing downloads
and all three output writes; output is held in memory.

## Certificate Helper

### TOOL-AUD-004: Confined, Staged Replacement

The helper resolves ssl.conf and the two explicit final names relative to
its own directory, independent of the caller's working directory. It checks
configuration/tool availability, rejects non-regular or symbolic-link output
paths, and uses a cooperative directory lock to serialize invocations.

Generation takes place in a private same-filesystem temporary directory under
umask 077. Existing outputs are backed up without removing them. Only a
successful OpenSSL exit with both nonempty regular output files reaches
publication. Ordinary replacement failures restore the original pair, or
remove only a newly published partial output if there was no previous pair.
Cleanup names only owned staging files and the two explicit output names;
there is no wildcard deletion. If rollback itself fails, staging/lock state
is retained with a recovery message instead of discarding the backup.

Tests execute a copied script with a Python OpenSSL mock that writes dummy
text only and asserts every target stays under its temporary fixture root.
The second-replacement failure uses a separate confined move mock. No real
OpenSSL process is launched. Tested cases include foreign CWD, directory
names with spaces, unrelated files, generation failure, incomplete/empty
output, first-time output, publish rollback, key mode 0600, symbolic-link/
directory rejection and preservation of another invocation's lock.

Boundary: generation failure cannot replace the final pair, and ordinary
publication rollback is tested. Two separate file renames are not a
power-loss/SIGKILL-atomic filesystem transaction; interruption or recovery
failure may require manual handling of a retained private staging directory
and lock. Cryptographic validity of real OpenSSL output, Windows/MINGW
execution and uncatchable-signal recovery were not exercised. The existing
MINGW environment handling and certificate parameters were preserved.

## Translation Configuration

### TOOL-AUD-005: Remove Retired Sources Consistently

Removed only the four retired resource entries from both .tx files:

- app/src/main/res/values/strings_support.xml
- app/src/main/res/raw/help_faq.html
- app/src/main/res/raw/help_problems.html
- app/src/main/res/raw/help_s_on_s_off.html

Both configurations now contain the same 19 existing source resources.
The shared regression guard uses ConfigParser and Ruby's YAML parser, checks
every source path, and compares the remaining file filters, source languages
and formats. Other mappings and language overrides were not changed.
No translation resource content was modified or uploaded.

## Contributor Documentation

### TOOL-AUD-006: Debug First, Release Guide for Production

The first build command is now ./gradlew assembleDebug. The text qualifies
local debug signing as the behavior without release signing properties and
links RELEASING.md for signed production builds and required trust inputs.

The release build configuration is untouched. app/build.gradle still hashes
to a0a6c117c82bcdb74a4257fb38531349600d53a6f82468b909bf2cd2688166fe.
No dependency change or release-guard weakening was made. Debug-first and
release-guide-link tests both passed; no build was invoked.

## Actual Red/Green Evidence

Tests were written and run before each corresponding implementation slice,
against the actual existing source or a confined copy of the certificate
helper, not a reimplementation of the defective behavior.

| Slice | Actual red result | Actual green result |
| --- | --- | --- |
| Parser/report, 001/002 | 8 methods ran: 7 failed and one control passed; 12 assertion failure events including subtests | Same 8 passed after parser/report edit |
| Allowlist advice, 003 | All four newly added methods failed; the preceding parser tests stayed green | All 12 scanner methods then passed |
| Certificate helper, 004 | Eight methods failed: 15 assertion failure events and one missing-fixture error caused by the baseline deletion | Same 8 passed after helper edit; six additional controls also passed |
| Translation/docs, 005/006 | Four methods failed with five failure events including both source-config subtests | Same 4 passed after the three file edits |
| Final combined suite | Baseline failures retained in this record; additional controls are not claimed to have failed on baseline | 33 methods passed, zero skips, zero failures/errors |

Final suite breakdown: 15 scanner tests, 14 copied-helper/mock tests, and four
configuration/documentation tests. Every method and its evidence category is
listed in tooling-fixes-tests.csv. Subtests account for the difference between
method counts and red failure-event counts.

Executed final checks:

| Command | Result |
| --- | --- |
| python3 -B scripts/tests/test_tooling_audit_fixes.py -v | 33 passed; last recorded run 3.153 seconds |
| bash -n Resources/certificate/generate.sh | Passed |
| shellcheck Resources/certificate/generate.sh | Passed |
| git diff --check -- docs/wa-tg-audit/scan_lists.py Resources/certificate/generate.sh .tx/config .tx/github-integration.yaml CONTRIBUTING.md | Passed |
| Read-only caller search for scanner helper functions | No outside Python callers found |
| app/build.gradle SHA-256 comparison | Unchanged from baseline |

ShellCheck initially warned about the empty CDPATH assignment. It was made
explicit as CDPATH='' and the check plus entire test suite were rerun green.
No tests were weakened to obtain the green run.

The offline harness requires Python 3, Bash and Ruby with their standard
libraries; no Python package or other dependency was added. Run it from the
repository root with the command above. It uses temporary filesystem
fixtures, standard-library mocks and in-memory scanner output. It does not
invoke Gradle, Android tools, real OpenSSL or remote services.

## Baseline Provenance

These hashes identify the actual five-file baseline used for red testing,
matching the original tooling-audit file manifest:

| Path | Baseline SHA-256 |
| --- | --- |
| docs/wa-tg-audit/scan_lists.py | 80db99fe30264f062656454e280298dab70e41c754c60e1de4054b34f05bbf69 |
| Resources/certificate/generate.sh | dd7b6cb86088e7003ad6506abca0e87552d132aeee95f3b03936d5b9f8909931 |
| .tx/config | ab27c68281a315edfa4512994744ce0137115d7a1dbc02160468c56016d19228 |
| .tx/github-integration.yaml | c1f5da613e00113d3d9a3eea43312e328a02604c9444f1f86a0953af88c87af6 |
| CONTRIBUTING.md | 9d7af4c67a578b753e1dbd3a6b201e643d425a0ad23075ecc537427446f4f7cb |

Current source/test hashes are in tooling-fixes-files.csv. Sidecar hashes are
not self-referenced and no central coverage import was performed.

## Sidecar Contract

- tooling-fixes-files.csv: exact path,sha256,assessment,bug_ids header; six
  source/test rows, no unrelated context or audit artifacts.
- tooling-fixes-bugs.csv: the central 11-column header; exactly the six original
  TOOL-AUD IDs, each verified-fixed within the offline boundary above.
- tooling-fixes-tests.csv: test_id,kind,status,bug_ids,evidence, matching the
  existing support-fixes test ledger shape; 33 unique passed test methods.
- tooling-fixes-review.md: this handoff, including red/green history and limits.

Main should review the scoped diff and the six fixes' evidence before any
central-ledger reconciliation or integration. No staging/commit or follow-on
expansion is part of this lane.
