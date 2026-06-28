# REL-001 License Boundary Current-Head Evidence - 2026-06-28

Scope:
- Story: `REL-001`
- Commit under test: `72b64e6f4c848a4667adcab115d432e9dad32376`
- Purpose: refresh the source-side GPL/MIT boundary reports after the latest release-gate commits.

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
  Source entries inspected: 2474
  Issues: 0

WorkingTree:
  Status: passed
  Source entries inspected: 2170
  Issues: 0

GitTracked strict source archive:
  Status: passed
  Source entries inspected: 2474
  Source archive entries inspected: 2180
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

This refresh proves the current tracked source, current working-tree source, and strict source
archive do not contain a premature MIT release claim or detected source-boundary drift. It does not
close `REL-001`: legal/provenance review and release artifact APK/SBOM boundary checks still need
real release artifacts and human/legal signoff.
