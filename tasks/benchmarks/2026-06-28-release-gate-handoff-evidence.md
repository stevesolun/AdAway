# Release Gate Handoff Evidence - 2026-06-28

Scope:
- Stories: `RUNTIME-007`, `UPDATE-002`, `UPDATE-004`, `REL-001`, `REL-002`, `REL-003`,
  `REL-004`, `REL-005`
- Commit under test: `1a25953daf58d51b56d796e641e34e6cf34bfc65`
- PR: `#7`

## Baseline CI State Before This Slice

`gh pr checks codex/market-leading-quality --repo stevesolun/AdAway` passed on the pushed
`1a25953daf58d51b56d796e641e34e6cf34bfc65` head before this handoff refresh:

```text
Analyze (cpp): pass (1m21s)
Analyze (java): pass (3m21s)
CodeQL: pass (2s)
Connected Android tests: pass (9m25s)
Development build: pass (5m59s)
```

## Local Release-Gate Verifier Evidence

Focused JVM verifier batch:

```bash
./gradlew --no-daemon :app:testDebugUnitTest \
  --tests org.adaway.scripts.ReleaseReadinessScriptTest \
  --tests org.adaway.scripts.UxMatrixScriptTest \
  --tests org.adaway.tasks.UserStoryStatusTrackerTest \
  --dependency-verification=strict --stacktrace
```

Result:

```text
BUILD SUCCESSFUL in 23s
```

PowerShell was present locally (`pwsh 7.6.3`), so the script tests exercised the real `.ps1`
release-readiness and UX sign-off logic rather than skipping.

## Current-Head Readiness Preflight

Command:

```bash
pwsh -NoProfile -File scripts/verify-release-readiness.ps1 \
  -ReleaseArtifactReport release-artifacts/verification-report.md \
  -PhysicalSmokeReport release-smoke/release-smoke-report.md \
  -UxSignOffReport app/build/reports/ux-matrix-2026-06-28-rel004-head-1a25953d/ux-signoff-preflight-report.md \
  -UxReviewPacket app/build/reports/ux-matrix-2026-06-28-rel004-head-1a25953d/ux-matrix-review.md \
  -LicenseBoundaryReport app/build/reports/license-boundary/debug-artifact-license-boundary-report.md \
  -ReportPath app/build/reports/release-readiness-head-1a25953d-preflight.md
```

Expected fail-closed result:

```text
Status: failed
UX review packet SHA-256: d481b9ab3152760fb917474704131b15c44ae45c4aec615581714e5d9e29eae4
UX review packet file SHA-256: d481b9ab3152760fb917474704131b15c44ae45c4aec615581714e5d9e29eae4
Release artifact verification: failed
Physical release smoke: failed
UX sign-off: failed
License boundary: failed
Issues: 7
```

The seven issues were the intended release blockers:

```text
Release artifact verification report is missing: release-artifacts/verification-report.md
Physical release smoke report is missing: release-smoke/release-smoke-report.md
UX sign-off report must contain '- Status: passed'.
UX sign-off report must contain '- Unchecked items: 0'.
UX sign-off report must contain '- Issues: 0'.
license boundary report must use Source mode: GitTracked for release readiness.
license boundary report must use Strict source archive: true.
```

The report hashes captured by this preflight were:

```text
UX sign-off report SHA-256: 2360238e3620277b77011c9034e481757e8e59570860dc77f0460817fcc8cd4b
License boundary report SHA-256: b7c5102eff8ff6d13f8628a1abc7d3ed480d6311a40aa8506c75f06bd271cb83
Readiness report SHA-256: 1c11cb427f3efc4cc40955d5fadb244bbfc4736c4cd3059b8ef4fac3dc0b16b8
```

This proves `REL-005` still fails closed with the refreshed `1a25953d` UX packet and does not
accept debug/dry-run or preflight reports as final release readiness.

## Remaining P0 Release Board

These gates are intentionally still open because their required proof depends on external
artifacts, a real physical/rooted device, legal review, or human review.

| Story | Needed proof | Owner action |
| --- | --- | --- |
| `RUNTIME-007` | Real rooted writable hosts-file apply smoke | Provide rooted physical device or trusted writable-system target where AdAway/libsu can write and restore `/system/etc/hosts`. |
| `UPDATE-002` | Signed APK self-update/install proof | Produce signed direct-release artifact, signed manifest, APK hash, and target-device install/update evidence. |
| `UPDATE-004` | Signed directRelease channel proof | Run the signed `directRelease` install/update path and verify installer permission/channel behavior. |
| `REL-001` | Legal/provenance signoff | Review GPL-derived code/assets/notices and sign off the GPL/MIT boundary; local source reports are not legal clearance. |
| `REL-002` | Release artifact verification report | Run the release artifact workflow/verifier on the tagged APK, manifest, checksum files, SBOM, cert, and attestations. |
| `REL-003` | Physical-device release smoke report | Run `.github/workflows/physical-release-smoke.yml` on a self-hosted Android physical-device runner with release tag and expected cert SHA-256. |
| `REL-004` | Human UX sign-off report | Generate/check the UX matrix review packet, complete every checklist item, and dispatch `.github/workflows/verify-ux-signoff.yml` with reviewer identity. |
| `REL-005` | Final readiness report | Dispatch `.github/workflows/verify-release-readiness.yml` with run IDs for release artifacts, physical smoke, UX signoff, and license-boundary reports. |

## Workflow Inputs

`physical-release-smoke.yml` requires:

```text
tag: vX.Y.Z
expected_cert_sha256: <release signer certificate SHA-256>
device_serial: optional adb serial when more than one physical device is attached
```

`verify-ux-signoff.yml` requires:

```text
review_packet_base64: <base64 checked ux-matrix-review.md>
reviewer: <human reviewer identity>
```

`verify-release-readiness.yml` requires numeric GitHub Actions run IDs:

```text
release_artifacts_run_id
physical_smoke_run_id
ux_signoff_run_id
license_boundary_run_id
```

## Verifier Boundaries

- `scripts/run-release-smoke.ps1` rejects emulators and writes a passing physical smoke report only
  after installing and launching a non-debuggable release APK on a real device.
- `scripts/verify-release-readiness.ps1` fails if reports are missing, sparse, tied to different
  source commits/tags/APKs/certs, missing the UX packet hash, or missing artifact license-boundary
  evidence.
- `scripts/verify-ux-signoff.ps1` validates a checked packet and reviewer identity, but it does not
  replace human visual judgment.
- This evidence file is a release-operator handoff. It does not close the external gates.
