# Dependency Verification Audit

AdAway uses Gradle dependency verification in strict mode with signature
verification enabled and key-server downloads disabled. This file is the human
review record for dependency-verification fallback debt that cannot be resolved
by checked-in trusted keys yet.

## Current Counts

- Ignored PGP keys: 31
- Key-download checksum fallbacks: 345

These counts must match `gradle/verification-metadata.xml`. If either count
changes, review the new dependency family, update this audit, and update the
security regression test in `SecurityHardeningTest`.

## Reviewed Fallback Families

| Family | Current treatment | Rationale |
| --- | --- | --- |
| Android Gradle plugin and Android build tools | Trusted keys are scoped to `com.android`, AndroidX annotation/data binding/test artifacts, and key-download fallbacks remain hash-pinned where publisher keys are unavailable. | Build tooling is required for the Android app. Scope trusted keys tightly and keep unresolved-key fallbacks bounded. |
| AndroidX runtime and instrumentation libraries | Trusted keys are scoped to the resolved AndroidX families; remaining unavailable signing keys stay hash-pinned. | AndroidX is core runtime/test infrastructure. Broad group trust is avoided where explicit family trust is available. |
| Google, Kotlin, protobuf, Error Prone, ASM, SLF4J, Apache, Room/SQLite, WorkManager, CycloneDX, dnsjava, OkHttp/transitive utilities | Trusted keys are scoped by exact group/artifact or narrow regex in `verification-metadata.xml`; unsigned artifacts are hash-pinned. | These are build/runtime dependencies already represented in the SBOM and lock file. Verification should fail if metadata changes without an explicit review. |
| Unavailable PGP keys | Listed as ignored keys with Gradle's `Key couldn't be downloaded from any key server` reason. | Key servers are intentionally disabled for reproducible builds. Ignored keys are acceptable only while counts stay bounded and artifacts remain hash-pinned. |

## Review Rules

- Do not enable external key-server lookup in CI.
- Do not replace signature verification with hash-only verification.
- Do not add broad trusted-key scopes when exact group or artifact scopes are
  available.
- Do not let ignored-key or key-download fallback counts grow without updating
  this audit and the corresponding security test.
- Prefer removing fallback entries when an exported public key is added to
  `gradle/verification-keyring.keys`.
