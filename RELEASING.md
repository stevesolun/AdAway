# Releasing

## 1. Checking bugs and technical debt

### Lint checks

Android development tools provide linter to check common errors.
Use `./gradlew :app:lint` to run the linter and produce (human readable) reports as HTML file located at `app/build/reports/lint-results.html`.

> [!IMPORTANT] 
> Check no new warning was introduced before releasing.

### SonarCloud analysis

The AdAway application source code is [analyzed by SonarCloud](https://sonarcloud.io/dashboard?id=org.adaway) to find bugs, code smells and compute technical debt.
While the overall score may be not perfect, each new release should not increase it.  

> [!IMPORTANT]
> Check no new bug nor debt was introduced before releasing.

## 2. Updating application version

Each version has its own number that follows the [Semantic Versioning](https://semver.org/) principle (starting from version 4).

> [!IMPORTANT]
> Update application version name (`appName`) and code (`appCode`) from the `gradle/libs.versions.toml` catalog file.

## 3. Updating the changelog

The AdAway project provides [a global changelog](CHANGELOG.md).

> [!IMPORTANT]
> Update the changelog to let users know what is inside each new version before releasing it.

## 4. Building release APK

Tagged GitHub releases build the `directRelease` variant with
`.github/workflows/fork-release-apk.yml`. The release artifact name is
`AdAway_<version>.apk`, where `<version>` is the tag without the leading `v`.
For example, tag `v13.5.0` produces `AdAway_13.5.0.apk`.

The workflow also generates `app/build/reports/sbom/adaway.cdx.json`,
SHA-256 checksum files for the APK and SBOM, signer-certificate verification,
and provenance/SBOM attestations.

Required repository secrets:

| Secret | Purpose |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded release keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Release key alias |
| `ANDROID_KEY_PASSWORD` | Release key password |
| `UPDATE_MANIFEST_PUBLIC_KEY_BASE64` | Public key embedded for signed update-manifest verification |
| `UPDATE_MANIFEST_PRIVATE_KEY_BASE64` | Base64-encoded PEM private key used to sign `manifest.json` |
| `ANDROID_RELEASE_CERT_SHA256` | Expected APK signing certificate SHA-256 digest |
| `RELEASE_TAG_PUBLIC_KEY_BASE64` | Base64-encoded public key used by `git verify-tag` for release tags |

Local release verification should run the same gates where possible:

```bash
./gradlew :app:assembleDirectRelease --dependency-verification=strict --no-daemon --stacktrace
./gradlew :app:generateSbom --dependency-verification=strict --no-daemon --stacktrace
```

## 5. Distributing release

Before sharing the any release, remember to test it.
Release variant apk does not behave like debug variant.
Same goes for real device versus emulator.

> [!IMPORTANT]
> Final tests should be done with release apk variant on real device.

Once tested, releases are posted on XDA development thread using the following template:
```
Hi all,

<welcoming message about the new version>

[U][SIZE="4"]Changelog:[/SIZE][/U]
[LIST]
[*] Item 1
[*] Item 2
[*] ...
[*] Item n
[/LIST]

[U][SIZE="4"]Thanks:[/SIZE][/U]

Special thanks to <contributors> for theirs contributions and <bug reporters> for theirs helpful bug reports.

[U][SIZE="4"]Download:[/SIZE][/U]

[URL="https://app.adaway.org/adaway.apk"]AdAway <application version>[/URL]
```

### Beta releases

The beta releases are only announced in the XDA development thread.

### Stable releases

The stable releases are distributed through [GitHub releases](https://github.com/AdAway/AdAway/releases) and [F-Droid store](https://f-droid.org/packages/org.adaway/) and are posted of the first post of XDA development thread.
Once ready, create and push a tag on GitHub repository using `vX.Y.Z` format
(or `vX.Y.Zb` for pre-releases). Tags ending in `b` are published as GitHub
pre-releases and use the `beta` update-manifest channel; all other release tags
use the `stable` channel. To publish the application in GitHub, push the signed
release tag. The workflow imports `RELEASE_TAG_PUBLIC_KEY_BASE64`, runs
`git verify-tag`, creates the GitHub release with fixed GPL boundary wording,
disables generated release notes, and uploads:

* `AdAway_<version>.apk`
* `AdAway_<version>.apk.sha256`
* `manifest.json`
* `manifest.json.sha256`
* `app/build/reports/sbom/adaway.cdx.json`
* `app/build/reports/sbom/adaway.cdx.json.sha256`

Before pushing a release tag locally, run:

```powershell
$Version = "<version>"
$Apk = "app\build\outputs\apk\directRelease\AdAway_$Version.apk"
$Sbom = "app\build\reports\sbom\adaway.cdx.json"
$Manifest = "app\build\outputs\update\manifest.json"

./gradlew :app:assembleDirectRelease --dependency-verification=strict --no-daemon --stacktrace
./gradlew :app:generateSbom --dependency-verification=strict --no-daemon --stacktrace
Copy-Item app\build\outputs\apk\directRelease\app-directRelease.apk $Apk -Force
Get-FileHash $Apk -Algorithm SHA256 |
  ForEach-Object { "$($_.Hash.ToLowerInvariant())  $(Split-Path $Apk -Leaf)" } |
  Set-Content "$Apk.sha256"
Get-FileHash $Sbom -Algorithm SHA256 |
  ForEach-Object { "$($_.Hash.ToLowerInvariant())  $(Split-Path $Sbom -Leaf)" } |
  Set-Content "$Sbom.sha256"
.\scripts\generate-update-manifest.ps1 `
  --apk $Apk `
  --version $Version `
  --version-code "<versionCode>" `
  --cert-sha256 "<release-certificate-sha256>" `
  --apk-url "https://github.com/stevesolun/AdAway/releases/download/v$Version/$(Split-Path $Apk -Leaf)" `
  --private-key-base64 "$env:UPDATE_MANIFEST_PRIVATE_KEY_BASE64" `
  --public-key-base64 "$env:UPDATE_MANIFEST_PUBLIC_KEY_BASE64" `
  --out $Manifest `
  --channel stable `
  --store adaway
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-license-boundary.ps1 `
  -SourceMode GitTracked -StrictSourceArchive `
  -ApkPath $Apk `
  -SbomPath $Sbom `
  -StrictArtifacts
.\scripts\run-release-smoke.ps1 `
  -ApkPath $Apk `
  -ExpectedCertSha256 "<release-certificate-sha256>" `
  -VerifyOnly
.\scripts\run-release-smoke.ps1 `
  -ApkPath $Apk `
  -ExpectedCertSha256 "<release-certificate-sha256>"
```

`generate-update-manifest.ps1` and `generate-update-manifest.sh` both delegate
to the same JDK-based manifest generator, so local Windows verification does not
require WSL, Bash, or OpenSSL. The release workflow still invokes the Bash
wrapper on GitHub-hosted Linux runners.

`run-release-smoke.ps1 -VerifyOnly` checks release APK badging and optional
signer identity without requiring a connected device. The full
`run-release-smoke.ps1` command still refuses debuggable APKs and emulators,
installs the release APK on an attached physical device, launches `org.adaway`,
and fails if the process is not running after launch.

On Unix-like shells with PowerShell available, the boundary checker wrapper can
also be run directly:

```bash
bash ./scripts/check-license-boundary.sh -SourceMode GitTracked -StrictSourceArchive
```

After the GitHub release is published, download the six uploaded assets to a
clean checkout and verify them as a single artifact set:

```powershell
$Version = "<version>"
$Apk = "AdAway_$Version.apk"
.\scripts\verify-release-artifacts.ps1 `
  --apk $Apk `
  --apk-sha256 "$Apk.sha256" `
  --manifest manifest.json `
  --manifest-sha256 manifest.json.sha256 `
  --sbom adaway.cdx.json `
  --sbom-sha256 adaway.cdx.json.sha256 `
  --public-key-base64 "$env:UPDATE_MANIFEST_PUBLIC_KEY_BASE64" `
  --expected-version $Version `
  --expected-channel stable `
  --expected-store adaway `
  --expected-apk-url "https://github.com/stevesolun/AdAway/releases/download/v$Version/$Apk" `
  --expected-cert-sha256 "<release-certificate-sha256>" `
  --repo stevesolun/AdAway `
  --verify-attestations
```

On Unix-like shells:

```bash
VERSION="<version>"
APK="AdAway_${VERSION}.apk"
bash ./scripts/verify-release-artifacts.sh \
  --apk "$APK" \
  --apk-sha256 "$APK.sha256" \
  --manifest manifest.json \
  --manifest-sha256 manifest.json.sha256 \
  --sbom adaway.cdx.json \
  --sbom-sha256 adaway.cdx.json.sha256 \
  --public-key-base64 "$UPDATE_MANIFEST_PUBLIC_KEY_BASE64" \
  --expected-version "$VERSION" \
  --expected-channel stable \
  --expected-store adaway \
  --expected-apk-url "https://github.com/stevesolun/AdAway/releases/download/v$VERSION/$APK" \
  --expected-cert-sha256 "<release-certificate-sha256>" \
  --repo stevesolun/AdAway \
  --verify-attestations
```

The signed update manifest may only point to `app.adaway.org` or to this fork's
GitHub APK release path:
`https://github.com/stevesolun/AdAway/releases/download/<tag>/<apk>.apk`.
Check the embedded payload before publishing or announcing a release.

Tagged releases are retained for durable provenance; the tagged release workflow
does not automatically delete older release artifacts.

APK self-update is only for the AdAway-signed direct APK distribution. Store
builds such as F-Droid should rely on their store update mechanism rather than
the in-app APK installer path. Build store releases with the normal `release`
variant so the installer permission is absent from the merged manifest; build
only the AdAway-signed direct APK channel with `directRelease`.

Pushing a tag will publish the application to F-Droid store.
It might takes some days to update but if it does not, build logs are available at the following address: `https://monitor.f-droid.org/builds/log/org.adaway/<versioncode>`.
