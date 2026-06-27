# Release Gate Handoff Evidence - 2026-06-28

Scope:
- Stories: `RUNTIME-007`, `UPDATE-002`, `UPDATE-004`, `REL-001`, `REL-002`, `REL-003`,
  `REL-004`, `REL-005`
- Commit under test: `4dd9abc8 test: prove prepared vpn lifecycle`
- PR: `#7`

## Baseline CI State Before This Slice

`gh pr checks 7 --repo stevesolun/AdAway` passed on the pushed `4dd9abc8` head before this
handoff update:

```text
Analyze (cpp): pass
Analyze (java): pass
CodeQL: pass
Connected Android tests: pass
Development build: pass
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
