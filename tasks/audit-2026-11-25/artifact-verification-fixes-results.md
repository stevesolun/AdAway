# Artifact Verification Fixes: REL-005 and REL-006

Date: 2026-11-25. Branch: `codex/full-codebase-audit`.
Scope: five release implementation/workflow files and two new focused test files.
SHA-256 snapshot: `artifact-verification-fixes-files.csv`. No base audit ledger edits.

Historical snapshot: results below apply to the recorded hashes. See the
[publication follow-up](validation.md#publication-follow-up) for later CLI
compatibility and live provenance evidence.

## REL-005: Actual APK Identity

- Canonical verifier uses installed Android build-tools **36.0.0**: real `apksigner.jar verify --verbose --print-certs` and `aapt dump badging`. It does not parse APK binary structures itself.
- Requires exactly one verified signer, matching the authenticated metadata certificate and any caller-supplied certificate pin. Requires actual package `org.adaway`, non-debuggable state, and actual version name/code matching signed metadata; versionCode must be a positive Java integer.
- Missing pinned SDK tools, unreadable/unsigned/non-APK content, unrecognized or ambiguous tool output, identity mismatches and nonzero tool exits fail closed. External verification commands have a 120-second timeout. Existing checksum, RSA metadata signature and expiry checks remain.
- `GenerateUpdateManifest.java` was not changed: existing signed metadata already includes the versionCode and certificate required by the strengthened consumer.
- Smoke now runs the complete canonical bundle verifier before any device discovery, then checks the APK hash again before installation. Both identity-only and physical modes require the release tag, expected certificate and manifest public key; the six-artifact bundle must be next to the APK.

## REL-006: Artifact Versus Tooling Provenance

- Tool checkout deliberately remains the workflow dispatch checkout so current verification code can inspect older releases. Reports use separate `Artifact source commit/ref/workflow` and `Tooling source commit` fields. `GITHUB_SHA` is never substituted for APK provenance.
- The current provenance policy and supported signer selector are owned by the [release guide](../../RELEASING.md#stable-releases).
- Original artifacts must come from the exact tagged commit and `fork-release-apk.yml`. Renewed manifest and manifest checksum are accepted only with an explicitly supplied 40-hex `--metadata-source-commit` and `--metadata-source-ref refs/heads/...`; those two subjects must come from `refresh-update-manifest.yml` at that identity. APK and SBOM provenance never inherit the metadata refresh identity. Metadata provenance is reported separately.
- Both manual workflows expose the optional refresh pins. Their permissions remain read-only, actions stay immutable-pinned, and the self-hosted physical-device runner selection is unchanged. No branch protection or publish workflow edits.
- Unattested local bundle checks remain available for the existing pre-attestation build gate, but report `Artifact source commit: not-verified` / `Artifact provenance: not-verified`. Readiness rejects them, old ambiguous reports, missing/duplicate fields, mismatched artifact commits/refs/workflows and unverified APK identity. It compares UX/license source commits with the actual artifact commit, while allowing different verified-tool checkout revisions.

## Exact Host Verification

Environment observed: OpenJDK 21.0.11, PowerShell 7.6.3, installed SDK build-tools 36.0.0 and android-36 platform. Test fixtures use fresh temporary RSA keys; no project/user signing keys are accessed.

Full suite command from repository root:

```sh
env ANDROID_HOME=/Users/steves/.local/android-sdk JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin python3 scripts/tests/test_release_artifact_identity.py
```

Final exact-source run: **exit 0, `Ran 15 tests in 93.769s`, `OK`**. This includes the explicit `SIGNER0`/`SIGNER1` fixture correction requested by main. Implementation and the focused host gate are complete; legacy integration fixture migration below remains main-owned.

The harness itself compiles the standalone verifier with `javac --release 17`, builds minimal real APKs with AAPT2, signs them with the SDK signer, and signs metadata with ephemeral RSA material. It tests successful identity; non-APK/unsigned/multi-signer/debug/wrong-package/version/code/signer/tampered APKs; invalid metadata codes/signature/expiry/hash/pin; missing SDK; all six provenance checks; unknown/failed/moved tag resolution; wrong source commit/ref/workflow and rejected sidecar; explicit refresh pins; stale-report invalidation; zero mocked adb access on identity/provenance failure; generated smoke/readiness acceptance with differing tooling revisions and negative report mutations.

All `gh` and `adb` executables in these tests are the dedicated local fixture. The fake SDK root contains only a symlink to build-tools, never real platform-tools, so smoke cannot accidentally select a real adb through SDK discovery. The mock never forwards a command. The physical-smoke report in the fixture is synthetic, not physical-device evidence.

Focused final multi-signer regression:

```sh
env ANDROID_HOME=/Users/steves/.local/android-sdk JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin python3 scripts/tests/test_release_artifact_identity.py ReleaseArtifactIdentityTests.test_non_apk_unsigned_and_extra_signer_fail_with_authenticated_metadata
```

Result: exit 0, `Ran 1 test in 3.017s`, `OK`. Fixture uses explicit distinct `--v1-signer-name SIGNER0` and `SIGNER1`, not just distinct aliases. Earlier runs found the duplicate-v1-name fixture defect and a stale report after invalid refresh options; both were corrected. Before the explicit basename addition, the expanded full suite passed 15 tests in 95.175 seconds. Main separately reported an earlier 13-test snapshot with 12 passes and the same signer-fixture failure; that is not a final-source result.

Workflow syntax check:

```sh
/opt/homebrew/bin/actionlint -shellcheck= -ignore 'label "android-device" is unknown' .github/workflows/verify-release-artifacts.yml .github/workflows/physical-release-smoke.yml
```

Result: exit 0, no diagnostics. Unsuppressed actionlint reports only the pre-existing custom `android-device` runner label. ShellCheck was not run.

PowerShell parser check:

```sh
/opt/homebrew/bin/pwsh -NoProfile -Command '$failed = $false; foreach ($file in @("scripts/run-release-smoke.ps1", "scripts/verify-release-readiness.ps1")) { $tokens = $null; $errors = $null; $null = [System.Management.Automation.Language.Parser]::ParseFile((Join-Path (Get-Location) $file), [ref] $tokens, [ref] $errors); if ($errors.Count) { $errors; $failed = $true } else { Write-Output "PASS parse: $file" } }; if ($failed) { exit 1 }'
```

Result: exit 0, `PASS parse` for both scoped PowerShell scripts. `git diff --check`: exit 0, no diagnostics.

Hash snapshot verification:

```sh
/opt/homebrew/bin/pwsh -NoProfile -Command '$rows = Import-Csv tasks/audit-2026-11-25/artifact-verification-fixes-files.csv; foreach ($row in $rows) { $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $row.path).Hash.ToLowerInvariant(); if ($actual -cne $row.sha256) { throw "Hash mismatch: $($row.path)" } }; Write-Output "PASS: $($rows.Count) scoped source hashes"'
```

Result: exit 0, `PASS: 7 scoped source hashes`.

## Integration Limits / Main-Owned Work

- No Gradle, emulator, real adb, real physical install/launch, release downloads, attestation network verification, workflow dispatch, release/delete/network mutation, commit or push was performed. This is host verification, not release approval. Primary documentation was read; tests did not contact GitHub.
- Existing release tests were not edited or run in this lane. Source inspection identifies legacy fixtures/contracts that main must migrate before claiming a green integrated suite: `SecurityHardeningTest.atk34_releaseArtifactVerifierChecksManifestAndChecksums` expects plain-text APK acceptance and the old report field; its release-smoke source/verify-only/report tests expect inline PowerShell APK inspection and an APK-only input. `ReleaseWorkflowRegressionTest` has three old smoke toolchain fixtures without authenticated metadata. `ReleaseReadinessScriptTest` fabricates old `Source commit`-only artifact/smoke reports. Do not restore permissive production behavior to satisfy these old fixtures; use the new real-APK harness and new explicit report schema.
- Old README/RELEASING APK-only smoke examples also need main-owned migration to the full bundle/public-key/tag contract. Those files are outside this bounded lane (README was already dirty).
- Refreshed metadata is deliberately rejected unless its exact refresh commit/ref are supplied. No implicit repository-only fallback. Existing historical artifacts with missing source certificate extensions, other producing workflows or self-hosted attestations fail closed and need an explicit policy decision, not a bypass.
- Standard tools remain the trust boundary: real apksigner/metadata RSA cryptography was executed, but gh/Sigstore cryptography and GitHub's live annotated/lightweight-tag API behavior were not reproduced locally. The API contract supports `refs/tags/...`; the API response must be a commit SHA, never an annotated-tag object SHA.
- Readiness still consumes supplied plain-text proof reports; this change validates their schema and identity consistency, not the authenticity of whoever supplied them. UX/license report generation and dirty cleanup/UX-signoff scripts/tests remain untouched. Tooling commit identifies checkout HEAD, not a claim that the checkout is clean; the companion CSV records the actual reviewed source hashes.

Primary references: [Android apksigner](https://developer.android.com/tools/apksigner), [Android AAPT2](https://developer.android.com/tools/aapt2), [GitHub attestation verify policy](https://cli.github.com/manual/gh_attestation_verify), [GitHub get-commit API](https://docs.github.com/en/rest/commits/commits#get-a-commit). Local `gh attestation verify --help` also confirmed the required source/signer policy flags; unsupported CLI versions fail closed.
