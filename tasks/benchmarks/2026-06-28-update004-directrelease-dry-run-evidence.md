# UPDATE-004 / REL-002 DirectRelease Dry-Run Evidence - 2026-06-28

This proof strengthens the signed direct APK release path without using production signing secrets.
It does not close the real signed artifact, update install, physical smoke, or release-readiness
gates. Those still require the production signing identity, release manifest, target device, and
release reports.

## Local Environment

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@21
ANDROID_HOME=/Users/steves/.local/android-sdk
Source commit: 20527762da2626afe8e67d5192b197e46121c3af
```

## First Attempt Rejected

A dry-run keystore generated with different store and key passwords reached release packaging, but
failed at `:app:packageDirectRelease`:

```text
Failed to read key dryrun from store ".../direct-release-dry-run.jks":
Get Key failed: Given final block not properly padded.
```

The CI dry-run therefore uses the same ephemeral password for `-storepass`, `-keypass`,
`signingStorePassword`, and `signingKeyPassword`.

## Passing Dry Run

Command shape:

```bash
keytool -genkeypair \
  -keystore "$KEYSTORE" \
  -storepass "$DRY_RUN_SIGNING_PASSWORD" \
  -keypass "$DRY_RUN_SIGNING_PASSWORD" \
  -alias dryrun \
  -keyalg RSA \
  -keysize 2048 \
  -validity 2 \
  -dname "CN=AdAway Direct Release Dry Run,O=AdAway,C=US"

UPDATE_MANIFEST_PUBLIC_KEY_BASE64="$(
  keytool -exportcert -rfc \
    -keystore "$KEYSTORE" \
    -storepass "$DRY_RUN_SIGNING_PASSWORD" \
    -alias dryrun |
    openssl x509 -pubkey -noout |
    sed -e '/BEGIN PUBLIC KEY/d' -e '/END PUBLIC KEY/d' |
    tr -d '\n'
)"

./gradlew --no-daemon :app:assembleDirectRelease :app:generateSbom \
  --dependency-verification=strict \
  --stacktrace \
  -PsigningStoreLocation="$KEYSTORE" \
  -PsigningStorePassword="$DRY_RUN_SIGNING_PASSWORD" \
  -PsigningKeyAlias=dryrun \
  -PsigningKeyPassword="$DRY_RUN_SIGNING_PASSWORD" \
  -PupdateManifestPublicKeyBase64="$UPDATE_MANIFEST_PUBLIC_KEY_BASE64"
```

Result:

```text
BUILD SUCCESSFUL in 22s
85 actionable tasks: 16 executed, 69 up-to-date
```

This exercises the `directRelease` packaging path, R8/minification, lint-vital, release signing
configuration, `REQUEST_INSTALL_PACKAGES` manifest merge, generated update public-key resource, and
release SBOM generation with ephemeral trust material.

## Artifact Boundary

Command:

```bash
pwsh -NoProfile -File scripts/check-license-boundary.ps1 \
  -ApkPath app/build/outputs/apk/directRelease/app-directRelease.apk \
  -SbomPath app/build/reports/sbom/adaway.cdx.json \
  -StrictArtifacts \
  -ReportPath app/build/reports/license-boundary/directrelease-dry-run-license-boundary-report.md
```

Result:

```text
License boundary check passed: no premature MIT release claim or artifact boundary drift detected.
```

Artifact hashes from the local dry run:

```text
287b535363e1c1672978ff94117d1a129e6f12654c4ff6cb0f5d4ee1fd73722e  app/build/outputs/apk/directRelease/app-directRelease.apk
e99220606350d95ae2be18b1c001a3f327d3b4ef463041e5397347daced92861  app/build/reports/sbom/adaway.cdx.json
```

Boundary report summary:

```text
- Status: passed
- Strict artifacts: true
- APK: app-directRelease.apk
- SBOM: adaway.cdx.json
- Source entries inspected: 2170
- APK entries inspected: 847
- APK resources inspected: 202
- SBOM components inspected: 105
- MIT release status: blocked until GPL-derived material is cleared
- Issues: 0
```

## CI Guard

Android CI now repeats the directRelease dry-run with an ephemeral signing key and uploads
`directrelease-dry-run-license-boundary-report`. The dry-run keeps production signing secrets out
of pull-request CI while still catching release-only packaging, minification, SBOM, and artifact
boundary regressions before tag time.

## Still Open

- Production signed `directRelease` artifact verification.
- Signed update manifest verification against the production update key.
- Device install/update smoke for the signed artifact.
- Physical release smoke and final release readiness aggregation.
