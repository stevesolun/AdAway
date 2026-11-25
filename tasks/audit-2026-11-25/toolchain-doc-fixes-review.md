# Toolchain Documentation Fix - 2026-11-25

## Scope

Approved finding: **TOOL-AUD-007** from `remaining-tooling-bugs.csv`.
This is a minimal contributor bootstrap correction, separate from the earlier TOOL-AUD-006 debug-first fix.

Changed only:

- `CONTRIBUTING.md`: prerequisite versions and SDK bootstrap steps.
- New `scripts/tests/test_toolchain_doc_fixes.py`: four focused offline tests.
- New `toolchain-doc-fixes-{files.csv,bugs.csv,tests.csv,review.md}` sidecars.

No application, build configuration, dependency, signing, or release-guard edit. No Gradle build, adb/device command, actual SDK Manager/package installation, actual certificate generation, staging, or commit. The fake `gradlew` in the tests only records arguments; it is not the repository wrapper. The app phase-3 test APK was not accessed or modified.

## Minimal Change

The guide now selects JDK 21, Android platform 36, build-tools 36.0.0, and NDK 27.2.12479018. It links the current Command-Line Tools layout, defines quoted `JAVA_HOME`/`ANDROID_HOME` paths before use, prepends the JDK's bin directory, and invokes the SDK-local manager with an explicit SDK root and pinned package arguments. License acceptance remains interactive.

The existing inline `./gradlew assembleDebug` command and the complete debug-key/release-guide/trust paragraph are **byte-for-byte unchanged**. A boundary comparison confirmed all text before the building section and from the debug-build step through EOF is unchanged from the approved pre-fix working copy.

Configuration and official-documentation rationale remain recorded in `remaining-tooling-review.md`. No further network requests were needed for this implementation.

## Red and Green Evidence

Commands actually run:

```sh
python3 -B -m unittest discover -s scripts/tests -p 'test_toolchain_doc_fixes.py' -v
python3 -B -m unittest discover -s scripts/tests -p 'test_tooling_audit_fixes.py' -v
```

| Check | Actual result |
| --- | --- |
| Before documentation edit | 4 tests run; 2 failed on the old missing SDK Manager path; 2 existing-contract/configuration guards passed; 0.035s |
| After documentation edit | 4/4 new tests passed; 0.821s |
| Earlier tooling regressions, unchanged test file | 33/33 passed; 3.344s |
| In-memory negative controls | 6/6 rejected |
| Existing tracked documentation diff | `git diff --check -- CONTRIBUTING.md` passed |

The red harness reported `/bin/bash: tools/bin/sdkmanager: No such file or directory` and exit 1 under its restricted environment. This is distinct from the earlier audit's direct-shell exit-127 reproduction; neither invoked a real SDK Manager.

The six negative controls modified only the extracted section in memory: legacy SDK path, wrong NDK version, omitted platform package, omitted JAVA_HOME, omitted ANDROID_HOME, and omitted JDK PATH prefix. Each was rejected by the final harness. No negative-control edit was made to repository files.

Main reported its configured **63-test pass** (33 tooling + 21 artifact + 9 inventory) before this change. That is preserved as user-supplied integration evidence, **not** claimed as a run by this pass. Only the four new tests and the 33 existing tooling tests were executed here.

## Offline Test Boundary

The test extracts the actual fenced/inline shell examples in document order; it does not substitute a separately maintained setup command. Only the user-editable path placeholders are replaced with temporary locations. Quoting, flags, package IDs, exports, and the debug command are exercised unchanged.

SDK Manager and the wrapper are recording mocks. A fake JDK executable lets the records verify Java lookup without running Java. The process receives an isolated HOME and initially empty PATH, not the host's SDK/JDK environment. It runs from a temporary checkout, with spaces in SDK/JDK paths and a separate case covering spaces in the checkout parent. A command allowlist and shell-operator/substitution checks reject unexpected command forms before execution. No dependency was added.

The tests verify the exact SDK executable, explicit SDK root, package-install and license arguments, JAVA_HOME, ANDROID_HOME, PATH prefix/Java selection, working directory, and debug-only wrapper request. Source/configuration guards also check the expected project versions and unchanged trust wording.

**Limit:** these are offline command-contract tests, not proof of package availability, accepted licenses, real SDK installation, or a completed clean-clone application build. The harness uses Bash error/unset-variable checks; this does not claim the document itself installs a new fail-fast shell policy.

## Provenance

| File/state | SHA-256 |
| --- | --- |
| CONTRIBUTING before TOOL-AUD-007 | `c2c8aa29eb3b32b08f77d91e9e61fc9c984393262614d4744b65793f80d5b9e8` |
| CONTRIBUTING after TOOL-AUD-007 | `16384112a67419929b48d2a7202c7600cf61c0826804fa99962953a4925bd28a` |
| New test | `3ac8d96994c5c63d40b900fec21a9736e654dc560f658302d0a2f42632bc5609` |
| Unchanged app/build.gradle | `a0a6c117c82bcdb74a4257fb38531349600d53a6f82468b909bf2cd2688166fe` |

The prior `tooling-fixes-*` and `remaining-tooling-*` files were neither overwritten nor amended. Their CONTRIBUTING hashes remain historical evidence for their original findings and fixes. This sidecar set records the new hash transition and verification for TOOL-AUD-007; it does not replace the provenance for TOOL-AUD-001 through TOOL-AUD-006.

All **13 protected baseline paths** matched after testing: `app/build.gradle`; the other four changed tooling source files; `test_tooling_audit_fixes.py`; the four earlier tooling-fixes sidecars; and the three remaining-tooling sidecars. Other collaborators' dirty files were left untouched.

The files and bugs CSVs retain the exact four-column and central eleven-column schemas. The tests CSV retains `test_id,kind,status,bug_ids,evidence`. TOOL-AUD-007 is marked verified-fixed only for the scoped documentation defect and offline checks above; main review remains separate.

Final validation passed: exact CSV schemas and row widths; two current matching source/test hashes; one finding row; eight unique test/evidence rows; four separate sidecars; no trailing whitespace in the new test or sidecars. Python AST parsing and Bash syntax checking of the extracted examples passed without executing example commands.
