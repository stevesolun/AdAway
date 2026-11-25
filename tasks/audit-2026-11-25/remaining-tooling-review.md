# Remaining Tooling Audit - 2026-11-25

## Scope and Result

- Checkout: `/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway`.
- Branch: `codex/full-codebase-audit`; observed HEAD: `f7dbbb5e7a8eca6983135f04adc945bab6518308`.
- Start set: the original report's explicit **29 unread files**, not an expanded repository scan.
- Excluded **13 ordinary documentation/metadata files** handed to Erdos, listed below.
- Remaining tooling set: **16 files**, all clean and without a current-hash entry in `current-reviews.csv` at this pass's checks. The 13 `.claude/commands` and `.claude/hooks` files are treated as operational prompt templates, not ordinary project documentation.
- Completed: **13 full text reads plus 2 complete keyring data/packet inspections**. The wrapper JAR received complete archive reading and limited bytecode review, and **remains partially reviewed**.
- Explicit follow-up exception: fully reread frozen, dirty `CONTRIBUTING.md` to investigate setup instructions; did not edit it. This adds **1** reviewed file, not new original-backlog coverage.
- Manifest: **17 rows = 14 full text reads + 2 structured keyring reads + 1 partial JAR review**. Original backlog disposition: **15 assessed + 1 partial + 13 handed off = 29**.
- **1 new actionable P2 finding: TOOL-AUD-007.** No new confirmed defect in the original 16-file tooling set. Partial inspection is not a clean bill of health.
- Only this report and `remaining-tooling-files.csv` / `remaining-tooling-bugs.csv` were written. No central ledger, production file, existing test, or frozen tooling-fixes sidecar was changed.

The files CSV uses exactly `path,sha256,assessment,bug_ids`; the bugs CSV uses the central eleven columns. Each assessment states its actual coverage. In particular, the JAR row must not be imported as a fully reviewed file.

## Fresh-Clone Setup Check

### TOOL-AUD-007: Obsolete SDK Bootstrap and Missing Project Toolchain

**P2, open.** Location: `CONTRIBUTING.md:57-67`, particularly line 65. Reviewed SHA-256: `c2c8aa29eb3b32b08f77d91e9e61fc9c984393262614d4744b65793f80d5b9e8`.

The guide tells command-line contributors to run:

```sh
tools/bin/sdkmanager "build-tools;x.y.z" ndk-bundle
```

This is not the current Command-Line Tools layout, and the guide defines `ANDROID_HOME` only afterward. The package list does not install the project platform or select its configured side-by-side NDK. It also omits JDK selection. The debug command and release-guide paragraph added for TOOL-AUD-006 are correct and unchanged; this is a separate, earlier setup failure.

**Observed reproduction:** extracted that exact command, parsed it with Ruby `Shellwords`, confirmed its executable path was absent before allowing the shell attempt, then invoked it from the repository root. Bash returned **127**, `tools/bin/sdkmanager: No such file or directory`. No SDK Manager process, download, package installation, or Gradle build ran. Substituting a real build-tools version cannot repair the missing executable path.

**Configuration evidence:**

| Source | Relevant setting |
| --- | --- |
| `app/build.gradle:15-16` | `compileSdk = 36`; `ndkVersion = '27.2.12479018'` |
| `app/build.gradle:131-132` | Java source/target compatibility 21 |
| `sentrystub/build.gradle:4,13-14` | SDK 36; Java source/target compatibility 21 |
| `.github/workflows/android-ci.yml:34,84-88` | JDK 21; platform android-36; build-tools 36.0.0; platform-tools; NDK 27.2.12479018 |
| `gradle/libs.versions.toml:5` | AGP 9.2.1 |
| `gradle/wrapper/gradle-wrapper.properties:3-4` | Gradle 9.5.1 distribution and pinned distribution SHA-256 |

**Official documentation cross-check, requested by main:**

- Android's [SDK Manager documentation](https://developer.android.com/tools/sdkmanager) places the command at `cmdline-tools/latest/bin/sdkmanager`, documents quoted package IDs, `--sdk_root`, and interactive license review. Its current deprecation notice does not make a wider CLI migration necessary for this minimal, existing-CI-compatible correction.
- Android's [NDK installation guide](https://developer.android.com/studio/projects/install-ndk) describes versioned side-by-side installation and distinguishes the legacy `ndk-bundle` folder. It also documents automatic NDK installation when licenses are accepted.
- Android's [Java build guide](https://developer.android.com/build/jdks) explains terminal `JAVA_HOME` selection and that the compile toolchain defaults to the Gradle JDK without an explicit toolchain. JDK 21 here comes from this project's Java 21 settings, not a claim that AGP alone requires 21.
- The [AGP 9.2 compatibility table](https://developer.android.com/build/releases/agp-9-2-0-release-notes) lists build-tools 36.0.0 as default/minimum and JDK 17 as AGP's minimum. Project settings and CI make JDK 21 the appropriate contributor baseline.

These were documentation requests only, authorized by the follow-up. No remote repository, Transifex, build service, release operation, or artifact download was used.

**Evidence against overstating the finding:** Android Studio may already supply a usable JDK/SDK, and accepted licenses can allow missing SDK/NDK components to be installed automatically. Therefore the package omissions do not prove every fresh build fails. The active app file has no `externalNativeBuild`/`ndkBuild` block, so missing the pinned NDK is not asserted as an independently reproduced native compilation failure. The directly demonstrated failure is the documented command-line route's executable path; the toolchain omissions are confirmed configuration mismatches. No whole-app or fresh-SDK build result is claimed.

### Minimal Correction Proposed, Not Applied

Replace only the setup paragraph and prerequisite steps. Say that contributors should install/select JDK 21, Android platform 36, build-tools 36.0.0, and NDK 27.2.12479018, rather than unqualified latest versions. For Android Studio, identify these packages in SDK Manager and select JDK 21 for Gradle. For the command-line route, link the official Command-Line Tools installation/layout instructions before this sequence:

```sh
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
export PATH="$JAVA_HOME/bin:$PATH"

"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --install \
  "platform-tools" "platforms;android-36" "build-tools;36.0.0" "ndk;27.2.12479018"
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses

./gradlew assembleDebug
```

License acceptance remains the contributor's decision; do not silently pipe `yes`. Keep the existing debug-key qualification and `RELEASING.md` link/trust paragraph verbatim. No dependency, Gradle, signing, or release-guard change is proposed. The proposed package command was token-checked locally: the executable, explicit SDK root, and four package arguments matched the intended values; it was not executed.

**Focused test plan after approval:** use a temporary fake SDK exposing only `cmdline-tools/latest/bin/sdkmanager`, with a mock that records arguments and never downloads. Assert the guide sets `ANDROID_HOME` before use, selects JDK 21, requests the exact project/CI package versions, handles a path containing spaces, retains `assembleDebug` and `RELEASING.md`, and does not reintroduce `ndk-bundle` or `tools/bin/sdkmanager` as a standalone legacy path. The old guide should fail these guards. A real clean-home build requires separate authorization and is not part of this pass.

## Remaining Tooling Check

### Command and Hook Templates

Read every line of all **10 command files and 3 hook templates**. Reviewed embedded command syntax, destructive-operation intent, and whether examples constituted executable checks. None of the embedded Git/build/device instructions was executed. These are advisory templates, not demonstrated automatically enforced safety hooks. Illustrative snippets and stale architectural assumptions are not promoted into application defects without a relevant executable path; application/native/schema investigation was deliberately excluded.

### Verification Keyrings

Read the complete binary and armored data with local parsers, without importing keys:

- GnuPG packet inspection with `--no-options --homedir /dev/null --batch --no-keyring --no-auto-check-trustdb --lock-never --list-packets` consumed **104134/104134 bytes** as **266 contiguous packets**, with no trailing data or parser diagnostics.
- Packet tags: **63 public keys**, **73 public subkeys**, **35 user IDs**, **95 signatures**. No secret-key or other unexpected packet type.
- Strict Base64 decoding of **all 63 armored blocks** succeeded; **all 63 CRC24 checks** passed. Concatenated decoded bytes exactly matched the binary keyring.
- Read all outer armor annotations: **63 pub**, **73 sub**, **35 uid** lines; no other nonblank text outside the blocks.
- Derived **136 unique v4 fingerprints** from packet bodies. All **65 unique trusted-key/PGP IDs** referenced by the current verification metadata had a local matching fingerprint.
- Contextual XML parsing confirmed metadata/signature verification enabled, remote keyservers disabled, and no unscoped global trusted-key entry. This was a relationship check against previously reviewed metadata, not a new full XML review.

These checks establish local format completeness and consistency only. They do not independently establish publisher identity, cryptographically verify every key-binding signature, evaluate every artifact signature at its signing time, or prove the live dependency graph is resolvable. Expiration/revocation annotations and legacy algorithms alone were not logged as a concrete current dependency failure. An initial no-default-keyring GnuPG attempt failed because `/dev/null` is not a home directory; nothing could be created there. The subsequent `--no-keyring --list-packets` inspection succeeded without a keyring import or home-directory creation.

### Wrapper JAR: Partial Review

Read the entire **59203-byte** JAR, all **54 archive entries**, and all **111676 uncompressed bytes**. ZIP CRC checks passed; no duplicate names, absolute paths, or `..` entry components. The five non-class resources were read completely. There are **44 classes**, all class-file version 50.

Using the existing Homebrew JDK 21 `javap`, fully read disassembly of exactly four classes:

- `org.gradle.wrapper.GradleWrapperMain`
- `org.gradle.wrapper.WrapperExecutor`
- `org.gradle.wrapper.Install`
- `org.gradle.wrapper.Install$1`

The reviewed path loads the configured distribution SHA-256 and checks a newly installed distribution ZIP before extraction. Cached installations take a separate existing-cache path; this is not a claim of revalidation on every invocation. No wrapper/Gradle method was executed. ZIP-entry path checks in the inspected legacy installer are absent, but no attacker-controlled archive reaching extraction despite the current pinned checksum was established; no generic vulnerability claim was filed.

**Outstanding:** the other **40 class implementations** were not read. The 24 `org.gradle.cli` classes, including nested classes, and these 16 wrapper classes remain unread:

```text
BootstrapMainStarter$1
BootstrapMainStarter
Download$1
Download$DefaultDownloadProgressListener
Download$ProxyAuthenticator
Download
DownloadProgressListener
ExclusiveFileAccessManager
GradleUserHomeLookup
IDownload
Install$InstallCheck
Logger
PathAssembler$LocalDistribution
PathAssembler
SystemPropertiesHandler
WrapperConfiguration
```

Upstream JAR authenticity/version provenance was not checked remotely. The JAR remains the **one incompletely reviewed file** in this tooling lane; full-byte archive reading is explicitly not counted as full behavioral/source review. This is the bounded stopping point, not an expansion into decompiling the entire third-party implementation.

## Context Hashes

Context files below support relationships or the proposed setup correction; they are not additional new audit coverage. App/build and workflow reads were targeted and read-only.

| Path | SHA-256 |
| --- | --- |
| `app/build.gradle` | `a0a6c117c82bcdb74a4257fb38531349600d53a6f82468b909bf2cd2688166fe` |
| `sentrystub/build.gradle` | `ee83f2980bef09eedeb3d930390591f80c5c801b3859322ba050166af39560ed` |
| `.github/workflows/android-ci.yml` | `a201faf18822faa2c595c0729ae9f694d03938330db6145eb2bcc070edda0dac` |
| `gradle/libs.versions.toml` | `7b20730f8ebb2945d856a4d7023a8d9fb949768cec93353c9386a37c4bd274d6` |
| `gradle/wrapper/gradle-wrapper.properties` | `558c2df8183916424bb7ab87257d16bb70ee6a79498fd5f2e2d02669f95ef54e` |
| `gradle/verification-metadata.xml` | `b00c78cd2a6af3b0cea617bc8c0b34f42febd119e5f88e4d20f6e8c287d9fa6e` |
| `build.gradle` | `beb90a76566829e5f99f59061e4f735dff9b20ab3ad1047ffc4b2d266c942b4b` |
| `settings.gradle` | `cdb393aa7c07ccc4554bea8c43ac445e37cb34bbc66bd9b18e45c4562736e555` |
| `gradle.properties` | `a08d181463cd8dfc89846134a5a490393ffdc7aca897ce32d1903e556c24d305` |
| `.claude/settings.json` | `f327eacfb9ef1f4d110e8b82afc519845a5d426885bfa47db1e5b19c997ec7ee` |

## Explicit Exclusions and Unread Files

**13 files from the original 29 remain unread in this pass because they are handed to Erdos:**

- `CHANGELOG.md`
- `CODE_OF_CONDUCT.md`
- `LICENSE.md`
- `docs/audit/2026-03-05-fix-report.md`
- `docs/audit/2026-03-05-full-audit.md`
- `docs/audit/priority-matrix.json`
- `docs/audit/recon-report.json`
- `docs/plans/2026-02-25-adaway-modernization-plan.md`
- `docs/wa-tg-audit/allowlist-fix-report.md`
- `docs/wa-tg-audit/catalog-analysis.json`
- `docs/wa-tg-audit/domain-scan-report.json`
- `docs/wa-tg-audit/domain-scan-summary.md`
- `filterlists-analysis.md`

`gradle/wrapper/gradle-wrapper.jar` remains partially unread as detailed above. There are **no other unread first-party tooling text files within the selected 16**. `CLAUDE.md` and `tasks/lessons.md` were instruction reads, not audit credit. Audit artifacts were used solely to define ownership, hashes, schemas, and prior findings; they were not audit targets. The app, native trees, other active dirty files, already reviewed scripts/build files, and frozen tooling-fixes were not reopened as new audit scope.

The five changed existing fix files plus the new tooling test all still matched `tooling-fixes-files.csv` hashes during the freeze check. No Gradle build, adb/device command, real certificate generation, deletion/release script, remote Transifex call, staging, commit, or Git mutation was performed. The main thread's configured discovery run is separate evidence; this pass neither reran it nor treated its earlier missing `ANDROID_HOME` as a product defect.

Final sidecar validation passed: exact 4/11-column schemas; 17 unique file rows with 17 current matching hashes; one bug row with no ID collision in other bug ledgers; all file-to-bug references valid; exactly one explicitly partial row; exactly three new sidecars; no trailing whitespace. All six frozen fix/test hashes matched. The original 16 tooling paths remained clean in Git status.
