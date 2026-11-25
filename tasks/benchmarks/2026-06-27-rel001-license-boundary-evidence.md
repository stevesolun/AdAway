# REL-001 License Boundary Source Reports - 2026-06-27

Historical source identities, as recorded in the preserved reports:
- GitTracked: `6cb4d35b5178beb15625e8a4d6c8be6f7e5c9ef2`.
- WorkingTree: `6cb4d35b5178beb15625e8a4d6c8be6f7e5c9ef2`.
- GitTracked strict source archive: `fe5f66da46c149829d6d3883d6e9d532c06b71e2`.

Evidence correction, 2026-11-25 (REM-EVID-001): this is a mixed-commit bundle,
not three checks of one source snapshot. Raw reports, logs and exit codes are
unchanged. The [June 28 refresh](2026-06-28-rel001-license-boundary-current-head-evidence.md)
is a separate historical bundle at `c03030f89ec2337bb9213949cec2e2c3db69f309`,
not evidence for the current branch tip.

Environment:
- PowerShell: 7.6.3, installed via Homebrew during this Mac setup
- JDK: Temurin 21
- SDK: `/Users/steves/.local/android-sdk`

Commands:

```bash
pwsh -NoProfile -File scripts/check-license-boundary.ps1 \
  -SourceMode GitTracked \
  -ReportPath tasks/benchmarks/2026-06-27-rel001-license-boundary-gittracked-report.md

pwsh -NoProfile -File scripts/check-license-boundary.ps1 \
  -SourceMode WorkingTree \
  -ReportPath tasks/benchmarks/2026-06-27-rel001-license-boundary-workingtree-report.md

pwsh -NoProfile -File scripts/check-license-boundary.ps1 \
  -SourceMode GitTracked \
  -StrictSourceArchive \
  -ReportPath tasks/benchmarks/2026-06-27-rel001-license-boundary-gittracked-strict-source-archive-report.md

./gradlew --no-daemon --no-build-cache :app:testDebugUnitTest \
  --tests org.adaway.security.SecurityHardeningTest \
  --dependency-verification=strict --stacktrace

./gradlew --no-daemon --no-build-cache :app:testDebugUnitTest \
  --tests org.adaway.security.SecurityHardeningTest \
  --tests org.adaway.scripts.ReleaseReadinessScriptTest \
  --tests org.adaway.scripts.UxMatrixScriptTest \
  --tests org.adaway.model.update.ApkIntegrityVerifierTest \
  --dependency-verification=strict --stacktrace
```

Raw artifacts:
- `tasks/benchmarks/2026-06-27-rel001-license-boundary-gittracked-report.md`
- `tasks/benchmarks/2026-06-27-rel001-license-boundary-gittracked.out.log`
- `tasks/benchmarks/2026-06-27-rel001-license-boundary-gittracked.err.log`
- `tasks/benchmarks/2026-06-27-rel001-license-boundary-gittracked.exitcode`
- `tasks/benchmarks/2026-06-27-rel001-license-boundary-workingtree-report.md`
- `tasks/benchmarks/2026-06-27-rel001-license-boundary-workingtree.out.log`
- `tasks/benchmarks/2026-06-27-rel001-license-boundary-workingtree.err.log`
- `tasks/benchmarks/2026-06-27-rel001-license-boundary-workingtree.exitcode`
- `tasks/benchmarks/2026-06-27-rel001-license-boundary-gittracked-strict-source-archive-report.md`
- `tasks/benchmarks/2026-06-27-rel001-license-boundary-gittracked-strict-source-archive.out.log`
- `tasks/benchmarks/2026-06-27-rel001-license-boundary-gittracked-strict-source-archive.err.log`
- `tasks/benchmarks/2026-06-27-rel001-license-boundary-gittracked-strict-source-archive.exitcode`

Result:

```text
GitTracked report: Status passed, Source entries inspected 2416, Issues 0
WorkingTree report: Status passed, Source entries inspected 2169, Issues 0
Strict source archive report: Status passed, Source entries inspected 2425,
  Source archive entries inspected 2115, Issues 0
All reports: MIT release status: blocked until GPL-derived material is cleared
SecurityHardeningTest: BUILD SUCCESSFUL in 18s
SecurityHardeningTest + ReleaseReadinessScriptTest + UxMatrixScriptTest +
  ApkIntegrityVerifierTest: BUILD SUCCESSFUL in 31s
```

The recorded source boundary checks found no premature MIT release claim or detected
source-boundary drift in their respective historical snapshots. They do not establish
one commit passing all three checks or verify release APK/SBOM artifacts. This advances
historical `REL-001` evidence but does not close the gate: legal/provenance clearance
is still required before any MIT release claim can be made.
