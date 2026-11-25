# Release Test and Documentation Migration

Date: 2026-11-25. Branch: `codex/full-codebase-audit`.
Follow-up ownership: the named legacy release tests and README/RELEASING smoke examples only, plus shared host fixtures and a standalone runner. Source snapshot: `artifact-verification-fixes-tests-docs-files.csv`.

## Scope and Preservation

- No production verifier, smoke script, readiness script, workflow, app source, connected-test source, cleanup, or UX-signoff file changed in this follow-up.
- Existing test methods were retained: SecurityHardeningTest **53 -> 53**; ReleaseWorkflowRegressionTest **17 -> 17**; ReleaseReadinessScriptTest **13 -> 14** (one added fail-closed case).
- SecurityHardeningTest changes are confined to four artifact/smoke methods. ReleaseWorkflowRegressionTest's three existing smoke methods keep their names and delegate through their shared fixture helper; its other tests are untouched.
- The JUnit bridge invokes the shared real-APK Python cases and requires both a successful process and an unskipped unittest result. SDK location comes from ANDROID_HOME/ANDROID_SDK_ROOT or the repository's local sdk.dir. It uses the test JVM's JDK, does not invoke Gradle, and never substitutes source-string checks for behavioral coverage.
- README edits are confined to release smoke/readiness text and one release-workflow summary line. The preexisting dirty UX-capture instructions and introductory content were preserved.
- Documentation distinguishes the local pre-attestation verification gate from post-publish smoke; both smoke modes require all six assets, trusted metadata key, certificate pin, release tag and bound attestations. It documents explicit refresh pins and separate artifact/tooling provenance.
- The prior sidecars remain historical snapshots. This new sidecar supersedes their test/docs hashes and closes the legacy-fixture migration handoff item; it does not claim release approval.

## Exact Migrated Cases

SecurityHardeningTest:
- `atk34_releaseArtifactVerifierChecksManifestAndChecksums`: 12 real-APK cases covering a valid signed bundle, non-APK/unsigned/multiple signers, package/version/code/debug/signer mismatches, metadata code validation, signature/expiry/hash/pin checks, APK signature tampering, six pinned attestation subjects, wrong commit/ref/workflow, unknown/failed/moving tag targets, explicit refreshed metadata, stale reports, and transient attestation lookup retry. Report field and public-key nondisclosure assertions remain behavioral.
- `atk34_releaseSmokeRequiresReleaseApkOnRealDevice`: authenticated bundle/debug rejection before device access, emulator refusal before install, wrong certificate pin and actual-signer rejection; existing manual workflow/documentation contracts retained.
- `atk34_releaseSmokeVerifyOnlyUsesHighestParsedBuildToolsVersion`: legacy method name retained; behavior now requires pinned 36.0.0 over 9.0.0/37.0.0/PATH traps, colon-separated trusted pin acceptance, complete identity-only report, and no adb on identity/provenance failure.
- `atk34_releaseSmokeReportRecordsPhysicalLaunchWithoutSerialLeak`: real authenticated APK plus mocked physical install/force-stop/launch/pid calls; report checks exact APK hash/certificate/artifact/tooling commits, launch PID and serial hash, with no raw serial.

ReleaseWorkflowRegressionTest:
- `releaseSmokePrefersTestedToolsOverNewerInstalledAndPath`: the real SDK fixture succeeds while older/newer/PATH trap tools remain unused.
- `releaseSmokeRefusesMissingTestedSignerWithoutFallback`: pinned signer jar absent while alternate signer jars exist; fails before gh or adb, no report.
- `releaseSmokeRejectsWrongCertificateWithPinnedTools`: both wrong trusted pin and a genuinely different APK signing key fail before device access.

ReleaseReadinessScriptTest: all 13 existing cases retained and executed with current report fixtures; new `releaseReadinessRejectsUnknownOrAmbiguousArtifactIdentity` first accepts a complete control then rejects nine mutations (old ambiguous source field, unknown commit, wrong ref/workflow, unverified identity, debug APK, versionCode mismatch, misleading provenance marker, duplicate source field). Passing fixtures deliberately use distinct artifact/verifier/smoke-tool commits. Existing sparse report, tag/hash/source mismatch, physical-run, license, UX evidence/hash and documentation cases retain their assertions.

## Host Verification

Full shared real-APK suite:

```sh
env ANDROID_HOME=/Users/steves/.local/android-sdk JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin python3 scripts/tests/test_release_artifact_identity.py
```

Result: **exit 0; Ran 21 tests in 113.873s; OK**. Uses real AAPT2/apksigner and ephemeral RSA keys, with local-only gh/adb fixtures. The moving-tag subcase now clears mock state and asserts the post-verification tag-move error specifically.

Standalone JUnit compile, performed without Gradle:

```sh
/opt/homebrew/opt/openjdk@21/bin/javac --release 17 -cp '/Users/steves/.gradle/caches/modules-2/files-2.1/junit/junit/4.13.2/8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12/junit-4.13.2.jar:/Users/steves/.gradle/caches/modules-2/files-2.1/org.hamcrest/hamcrest-core/1.3/42a25dc3219429f0e5d060061f71acb49bf010a0/hamcrest-core-1.3.jar:/Users/steves/.gradle/caches/modules-2/files-2.1/org.json/json/20260522/4b6403288ab9457f3b841884a4bcf73acc544f4/json-20260522.jar:/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes' -d /tmp/adaway-release-migration.dgZ5Cf app/src/test/java/org/adaway/security/ReleaseArtifactTestHarness.java app/src/test/java/org/adaway/security/SecurityHardeningTest.java app/src/test/java/org/adaway/security/ReleaseWorkflowRegressionTest.java app/src/test/java/org/adaway/scripts/ReleaseReadinessScriptTest.java scripts/tests/RunReleaseMigrationTests.java
```

Result: **exit 0, no diagnostics**, Java 17 target under JDK 21. The classpath reads main's existing compiled RegexUtils dependency; these selected release cases do not call it. No app build output was written by this lane.

Standalone JUnit execution (only the seven named artifact/smoke cases plus all readiness cases):

```sh
env ANDROID_HOME=/Users/steves/.local/android-sdk JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/opt/openjdk@21/bin/java -cp '/tmp/adaway-release-migration.dgZ5Cf:/Users/steves/.gradle/caches/modules-2/files-2.1/junit/junit/4.13.2/8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12/junit-4.13.2.jar:/Users/steves/.gradle/caches/modules-2/files-2.1/org.hamcrest/hamcrest-core/1.3/42a25dc3219429f0e5d060061f71acb49bf010a0/hamcrest-core-1.3.jar:/Users/steves/.gradle/caches/modules-2/files-2.1/org.json/json/20260522/4b6403288ab9457f3b841884a4bcf73acc544f4/json-20260522.jar:/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes' RunReleaseMigrationTests
```

Result: **exit 0; Release migration: 21 tests, 0 failures/skips**. All seven individually named artifact/smoke cases passed; all 14 ReleaseReadinessScriptTest cases passed. The runner treats assumptions/ignored cases as failures, not successful coverage. The scoped migration is compile-ready for main's integrated gate.

Documentation smoke snippets were parsed, not executed:

```sh
/opt/homebrew/bin/pwsh -NoProfile -Command '$count = 0; $fence = ([string][char]96) * 3; $pattern = "(?s)" + $fence + "powershell\r?\n(.*?)\r?\n" + $fence; foreach ($file in @("README.md", "RELEASING.md")) { foreach ($block in [Regex]::Matches((Get-Content -Raw $file), $pattern)) { $code = $block.Groups[1].Value; if ($code -like "*run-release-smoke.ps1*") { $tokens = $null; $errors = $null; $null = [System.Management.Automation.Language.Parser]::ParseInput($code, [ref] $tokens, [ref] $errors); if ($errors.Count) { throw ($errors | Out-String) }; $count++; Write-Output "PASS smoke example parse: $file" } } }; if ($count -ne 2) { throw "Expected two smoke example blocks, got $count" }'
```

Result: **exit 0**, PASS for README.md and RELEASING.md. `git diff --check`: exit 0, no diagnostics.

Final hash verification:

```sh
/opt/homebrew/bin/pwsh -NoProfile -Command '$rows = Import-Csv tasks/audit-2026-11-25/artifact-verification-fixes-tests-docs-files.csv; foreach ($row in $rows) { $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $row.path).Hash.ToLowerInvariant(); if ($actual -cne $row.sha256) { throw "Hash mismatch: $($row.path)" } }; Write-Output "PASS: $($rows.Count) migration source hashes"'
```

Result: **exit 0; PASS: 9 migration source hashes**.

## Limits

- No Gradle, emulator, connected test, real adb, release download, live attestation request, network mutation, commit or push. Main owns the integrated and connected gates.
- The shared fixture requires Python 3, OpenSSL, PowerShell, JDK 21, SDK build-tools 36.0.0 and android-36. Its process mocks run on POSIX hosts. Missing prerequisites fail the migrated behavioral bridge rather than silently passing.
- SDK APK signature and metadata RSA verification are real. gh policy and physical-device behavior are mocks; no Sigstore cryptography, actual installer, production APK, or release-readiness approval is claimed.
- Readiness's Java report-mutation fixtures are synthetic report-contract tests. The shared Python integration additionally produces verifier/smoke reports through the real scripts before exercising readiness. Neither authenticates the human provenance of supplied plain-text proof reports.
- Unrelated legacy security tests were preserved, not included in this focused JUnit run. Full app compilation and full JVM suite remain main-owned.
