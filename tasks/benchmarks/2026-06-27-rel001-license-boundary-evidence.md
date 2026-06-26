# REL-001 License Boundary Source Reports - 2026-06-27

Source commit: `6cb4d35b5178beb15625e8a4d6c8be6f7e5c9ef2`

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

./gradlew --no-daemon --no-build-cache :app:testDebugUnitTest \
  --tests org.adaway.security.SecurityHardeningTest \
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

Result:

```text
GitTracked report: Status passed, Source entries inspected 2416, Issues 0
WorkingTree report: Status passed, Source entries inspected 2169, Issues 0
Both reports: MIT release status: blocked until GPL-derived material is cleared
SecurityHardeningTest: BUILD SUCCESSFUL in 18s
```

The local source boundary checks prove the working tree and tracked sources do not contain a
premature MIT release claim or detected artifact-boundary drift. This advances `REL-001` evidence,
but does not close the gate: legal/provenance clearance is still required before any MIT release
claim can be made.
