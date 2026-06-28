# UPDATE-002/UPDATE-004 Release Preflight Evidence - 2026-06-28

## Scope

This is not the signed APK install smoke. It records the strongest locally available preflight for
the app-update release gates without release signing secrets or a signed `directRelease` artifact.

## Commands

Verifier and direct APK boundary contracts:

```bash
./gradlew --no-daemon :app:testDebugUnitTest \
  --tests org.adaway.model.update.ApkIntegrityVerifierTest \
  --tests org.adaway.security.SecurityHardeningTest.atk34_apkSelfUpdateRequiresInstallPermissionAndAdAwayStoreBoundary \
  --dependency-verification=strict --stacktrace
```

Fail-closed direct release build preflight:

```bash
./gradlew --no-daemon :app:assembleDirectRelease \
  --dependency-verification=strict --stacktrace
```

## Result

- `ApkIntegrityVerifierTest` and
  `SecurityHardeningTest.atk34_apkSelfUpdateRequiresInstallPermissionAndAdAwayStoreBoundary`
  passed in `:app:testDebugUnitTest`.
- Unsigned `:app:assembleDirectRelease` exited with code `1`, as expected, before producing an
  installable release APK.
- Gradle rejected the build with:
  `Release and release-SBOM builds require signingStoreLocation, signingStorePassword, signingKeyAlias, and signingKeyPassword.`

## Remaining Gate

`UPDATE-002` and `UPDATE-004` remain open until a signed direct-release artifact exists and is
installed/tested on a target device with APK hash, manifest signature, and signing certificate
evidence recorded.
