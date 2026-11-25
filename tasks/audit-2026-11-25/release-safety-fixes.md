# Release Safety Fixes: REL-007 and REL-008

Date: 2026-11-25
Repository: /Users/steves/Steves_Files/Work/Research_and_Papers/AdAway
Branch: codex/full-codebase-audit (existing dirty checkout; unchanged)

## Scope and Handoff

Scripts/JVM test sources are stable-ready for main's combined batch. This lane changed only
`.github/workflows/cleanup-releases.yml`, `scripts/verify-ux-signoff.ps1`, focused tests, and
these three lane artifacts. Read `CLAUDE.md`, `tasks/lessons.md`, `release-review.md`, and
`release-bugs.csv`. Main-owned pre-existing edits in `UxMatrixScriptTest.java` were preserved.
The canonical `release-bugs.csv`, other audit artifacts, capture generator, sign-off workflow,
release readiness gates, branch protections, and other CI workflows were not edited here.

- [x] Confirm the cleanup command context and shell failure-propagation defects.
- [x] Read the actual packet generator and workflow consumer before defining validation.
- [x] Implement the two scoped fixes and focused regression tests.
- [x] Run isolated offline shell/PowerShell tests, actionlint, and whitespace checks.
- [x] Produce two-ID, 11-column bug CSV and SHA-256 file manifest.

No Gradle, ADB, network calls, real GitHub CLI invocation, workflow dispatch, live deletion,
commit, push, branch change, or protection change was performed. No JVM or Android execution
is claimed by this lane. Main owns compilation, combined JVM/lint, and Android validation.

## REL-007: Cleanup Context and Failure Propagation

Root cause: cleanup had no checkout or explicit repository. Its `gh release list` ran inside
process substitution feeding `mapfile`; the list command's failure did not fail the parent shell.

Current implementation: `.github/workflows/cleanup-releases.yml:31-50` passes
`--repo "$GITHUB_REPOSITORY"` to both enumeration and deletion. Enumeration is assigned in a
standalone command under `set -euo pipefail`, so its nonzero exit prevents parsing or deletion,
including when it prints partial output. A successful empty list exits zero without attempting
an empty-tag delete. A quoted read loop populates the array without requiring Bash 4 `mapfile`.
Delete failures remain fatal. Manual trigger, token source, permissions, integer-input check,
retention count/order/limit, and tag preservation are unchanged. No `--cleanup-tag` was added.

`scripts/tests/cleanup-releases-safety.Tests.sh` parses the actual YAML, checks the manual-only
trigger and unchanged permissions, then executes its actual shell in a temporary non-checkout
directory. A shell function replaces `gh` and asserts explicit repository arguments on every
call; there is no fallback to the binary. Fixtures cover failed and partially failed enumeration,
empty output, below/exact/above retention, zero/leading-zero retention, invalid values, quoted
tag arguments, and stopping at the first deletion failure. Every delete is only a logged mock.

Residual requirement before any live cleanup: the existing policy has no per-channel floor.
`keep_latest=0` still selects all release records, and a global retention count can select the
sole stable/beta feed. Channel-aware retention needs a separate authorized policy/fix before
enabling real deletion. This lane verifies context/failure handling, not release-retention safety
as a whole, GitHub permissions, hosted-runner behavior, or production availability.

## REL-008: Structural UX Sign-Off

Root cause: the verifier accepted any positive number of checked boxes, allowing a valid source
commit plus one arbitrary checked item to pass after deleting the actual review matrix.

Read `Write-UxMatrixReviewManifest`, `Get-UxScreenshotPath`, `$UxMatrixScreens`, and
`$UxMatrixVariantSpecs` in `scripts/run-ux-matrix.ps1` and the packet-only input contract in
`.github/workflows/verify-ux-signoff.yml`. The verifier now requires exactly one packet title,
source-commit entry, global checklist marker, each of the five exact global review items,
each variant section/screens marker, and each screen item within that variant:

- Variants: `baseline`, `font-1.3`, `font-1.6`, `font-1.3-rtl`, `font-1.6-rtl`.
- Screens per variant: `home`, `discover`, `sources`, `more`, `domain_checker`, `onboarding`,
  `custom_rules`, `update`.
- Total checked items on success: 45 (five global plus 40 screen entries).

`scripts/verify-ux-signoff.ps1:69-156` validates identity, multiplicity, grouping and screenshot
paths; `:221-225` integrates this validation before existing count/commit/reviewer gates. Missing,
duplicate, unknown, misplaced or substituted items fail even if the total checked count stays 45.
`MISSING`, mismatched variants/screens, traversal, absolute paths and URLs fail. Both generated
layouts remain valid: `variant/screen.png` and `variant/ux-matrix/screen.png`. Uppercase `X`, CRLF,
normal indentation, and all existing report fields/packet SHA-256 binding remain supported.
Fenced example packets and HTML-comment-hidden packets are rejected.

The standalone PowerShell harness extracts only generator data and named packet-writing helpers
through the PowerShell parser. It never runs capture setup, tool discovery, instrumentation,
ADB or Gradle. It generates actual packets using temporary empty screenshot placeholders, then
executes the complete verifier in isolated PowerShell instances and checks exit codes, status,
diagnostics, counts, and actual packet hashes. It deletes and duplicates every one of the 45
checklist items individually and every one of the five variant sections, plus malformed-path,
metadata, marker, reviewer, source-drift, unchecked, and arbitrary-one-checkbox cases.

The three existing JVM sign-off cases now obtain their fixtures from the actual generator and
expect 45 checks for success. A focused JVM test invokes the standalone schema harness. The
earlier capture-safety/source-contract additions in this already-dirty test file are unchanged.
The JVM changes have not been compiled/executed here; main's combined run is required.

Residual requirements: this is structural sign-off validation, not screenshot existence,
image-integrity, reviewer-authentication, or human-review proof. The current CI sign-off workflow
receives Markdown alone; requiring local screenshot files would break that legitimate contract.
Preserve screenshots for human review, rerun sign-off on the intended source revision after
integration, and do not treat old pre-fix reports as newly validated. Readiness/report provenance
work remains outside this lane. A future generator schema change must update the verifier and
generator-backed tests together; this implementation intentionally fails closed on schema drift.

## Actual Verification Results

All commands below were run from the repository root; all completed with exit 0:

| Command | Actual result |
| --- | --- |
| `bash scripts/tests/cleanup-releases-safety.Tests.sh` | PASS: 14 cleanup safety scenarios (mocked gh; no network/deletion). |
| `pwsh -NoProfile -File scripts/tests/verify-ux-signoff-safety.Tests.ps1` | PASS: 126 UX sign-off safety scenarios (generator-backed; no adb/Gradle). |
| `actionlint -shellcheck= .github/workflows/cleanup-releases.yml` | Passed; no diagnostics. ShellCheck deliberately disabled; workflow shell was executed by the mock harness. |
| `git diff --check` | Passed; no diagnostics. |

An initial harness run exposed PowerShell's `HadErrors` being set by an intentional `exit 1`
without a stream error. The harness was corrected to assert the actual exit code and error
stream separately; the complete 126-case run then passed. This was a test-harness correction,
not a suppressed verifier failure.

## Artifact Contract

`release-safety-fixes-bugs.csv` has the canonical 11-column header and exactly REL-007/REL-008.
Its `verified-fixed` status means scoped offline behavioral verification only. No other finding
is closed. `release-safety-fixes-files.csv` uses `path,sha256,assessment,bug_ids` and records the
five changed source/test files, two read-only schema/consumer files, and this report/bug CSV.
It excludes its own self-referential hash. Hashes describe the handoff snapshot, not immutable
ownership of files another lane may subsequently change.
