# REL-001 Current-Head License Boundary Evidence - 2026-06-28

Scope:
- Story: `REL-001`
- Source commit under test: `c03030f89ec2337bb9213949cec2e2c3db69f309`
- Purpose: refresh the source-side GPL/MIT boundary reports at the current PR #7 head while
  keeping legal/provenance and signed-release artifact gates open.

## Commands

```bash
pwsh -NoProfile -File scripts/check-license-boundary.ps1 \
  -SourceMode GitTracked \
  -ReportPath tasks/benchmarks/2026-06-28-rel001-license-boundary-gittracked-report.md

pwsh -NoProfile -File scripts/check-license-boundary.ps1 \
  -SourceMode WorkingTree \
  -ReportPath tasks/benchmarks/2026-06-28-rel001-license-boundary-workingtree-report.md

pwsh -NoProfile -File scripts/check-license-boundary.ps1 \
  -SourceMode GitTracked \
  -StrictSourceArchive \
  -ReportPath tasks/benchmarks/2026-06-28-rel001-license-boundary-gittracked-strict-source-archive-report.md
```

## Results

```text
GitTracked exitcode: 0
WorkingTree exitcode: 0
GitTracked strict source archive exitcode: 0
```

Report summaries:

```text
GitTracked:
  Status: passed
  Source commit: c03030f89ec2337bb9213949cec2e2c3db69f309
  Source entries inspected: 2492
  Issues: 0

WorkingTree:
  Status: passed
  Source commit: c03030f89ec2337bb9213949cec2e2c3db69f309
  Source entries inspected: 2170
  Issues: 0

GitTracked strict source archive:
  Status: passed
  Source commit: c03030f89ec2337bb9213949cec2e2c3db69f309
  Source entries inspected: 2492
  Source archive entries inspected: 2199
  Issues: 0
```

All reports retain:

```text
MIT release status: blocked until GPL-derived material is cleared
APK: not-provided
SBOM: not-provided
Strict artifacts: false
```

## Raw Artifacts

- `tasks/benchmarks/2026-06-28-rel001-license-boundary-gittracked-report.md`
- `tasks/benchmarks/2026-06-28-rel001-license-boundary-gittracked.out.log`
- `tasks/benchmarks/2026-06-28-rel001-license-boundary-gittracked.err.log`
- `tasks/benchmarks/2026-06-28-rel001-license-boundary-gittracked.exitcode`
- `tasks/benchmarks/2026-06-28-rel001-license-boundary-workingtree-report.md`
- `tasks/benchmarks/2026-06-28-rel001-license-boundary-workingtree.out.log`
- `tasks/benchmarks/2026-06-28-rel001-license-boundary-workingtree.err.log`
- `tasks/benchmarks/2026-06-28-rel001-license-boundary-workingtree.exitcode`
- `tasks/benchmarks/2026-06-28-rel001-license-boundary-gittracked-strict-source-archive-report.md`
- `tasks/benchmarks/2026-06-28-rel001-license-boundary-gittracked-strict-source-archive.out.log`
- `tasks/benchmarks/2026-06-28-rel001-license-boundary-gittracked-strict-source-archive.err.log`
- `tasks/benchmarks/2026-06-28-rel001-license-boundary-gittracked-strict-source-archive.exitcode`

## Boundary

This refresh proves the tracked source, working-tree source, and strict source archive for current
PR head `c03030f89ec2337bb9213949cec2e2c3db69f309` do not contain a premature MIT release claim
or detected source-boundary drift. It does not close `REL-001`: legal/provenance review and
release artifact APK/SBOM boundary checks still need real release artifacts and human/legal
signoff.
