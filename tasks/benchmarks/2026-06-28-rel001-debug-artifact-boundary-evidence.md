# REL-001 Debug Artifact Boundary Evidence - 2026-06-28

`REL-001` remains a release gate because MIT/provenance clearance and signed release APK/SBOM
artifact reports are still external. This slice adds and proves a CI/CD guard for the development
artifact path so ordinary PRs inspect the built debug APK plus a CycloneDX SBOM with the same
license-boundary script used by the release workflow.

## What Changed

Android CI now runs these steps after `assembleDebug`:

1. `./gradlew :app:cyclonedxBom --dependency-verification=strict --stacktrace`
2. `./scripts/check-license-boundary.ps1 -ApkPath app/build/outputs/apk/debug/app-debug.apk -SbomPath app/build/reports/cyclonedx/bom.json -StrictArtifacts -ReportPath app/build/reports/license-boundary/debug-artifact-license-boundary-report.md`
3. Upload `debug-artifact-license-boundary-report` and `AdAway-debug-sbom` artifacts.

The release-gated `:app:generateSbom` task is unchanged and still fails closed without release
signing/trust material. This avoids weakening the signed-release proof boundary.

## Local Proof

Environment:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@21
ANDROID_HOME=/Users/steves/.local/android-sdk
```

Command:

```bash
./gradlew --no-daemon :app:assembleDebug :app:cyclonedxBom \
  --dependency-verification=strict --stacktrace
```

Result:

```text
BUILD SUCCESSFUL in 5s
42 actionable tasks: 2 executed, 40 up-to-date
```

Artifact hashes:

```text
cc587365535bae924e7a12cd0f3c35b58fb6595320243c6f37b37580b1e26771  app/build/outputs/apk/debug/app-debug.apk
a75b7111dd87229a6c93541dd51190fb3f0ef1d7e785a290777269c7aa2706d1  app/build/reports/cyclonedx/bom.json
21027e77758de52edfde32651dfee2a1532c40c46ef1276a1617db2bf7044038  app/build/reports/license-boundary/debug-artifact-license-boundary-report.md
```

SBOM summary:

```json
{
  "bomFormat": "CycloneDX",
  "specVersion": "1.6",
  "components": 116,
  "metadataName": "app"
}
```

Strict artifact boundary command:

```bash
pwsh -NoProfile -File scripts/check-license-boundary.ps1 \
  -ApkPath app/build/outputs/apk/debug/app-debug.apk \
  -SbomPath app/build/reports/cyclonedx/bom.json \
  -StrictArtifacts \
  -ReportPath app/build/reports/license-boundary/debug-artifact-license-boundary-report.md
```

Result:

```text
License boundary check passed: no premature MIT release claim or artifact boundary drift detected.
```

Report summary:

```text
- Status: passed
- Source commit: c03030f89ec2337bb9213949cec2e2c3db69f309
- Strict artifacts: true
- APK: app-debug.apk
- SBOM: bom.json
- Source entries inspected: 2170
- APK entries inspected: 1119
- APK resources inspected: 265
- SBOM components inspected: 116
- MIT release status: blocked until GPL-derived material is cleared
- Issues: 0
```

## Boundary

This is CI/CD protection for the development APK and dependency SBOM. It does not close:

- Signed release APK artifact verification.
- Signed directRelease update/install smoke.
- Release SBOM and attestation proof.
- MIT/provenance legal clearance.
