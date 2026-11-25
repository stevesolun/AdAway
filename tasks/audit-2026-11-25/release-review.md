# Release, Update Security, and Native Boundary Review

Date: 2026-11-25
Repository: /Users/steves/Steves_Files/Work/Research_and_Papers/AdAway
Branch: codex/full-codebase-audit
Baseline and reviewed HEAD: d51eb7aea06871c2c6ad2bbedb9ddbaaaca48d77

## Outcome and Scope

This is an audit plus a narrowly authorized implementation checkpoint, not release approval. The original audit recorded eight confirmed issues and one conditional expiry-policy candidate (REL-004). REL-001 and REL-002 now have scoped fixes as described below; the other six confirmed findings and REL-004 remain open.

Read CLAUDE.md and tasks/lessons.md. Earlier release-review notes were treated as historical context, not current proof. Reviewed all 12 current production update model/UI files, all 13 existing release scripts, all 12 workflow-directory files, active build/manifest/ProGuard configuration, selected update tests, and native inclusion/launch boundaries. The companion release-files.csv and appendix now contain exactly 80 individually read/reviewed files, with current SHA256 and scoped assessment. Verification metadata received a full structured review, not individual external provenance validation of its 1,416 checksums.

The companion release-bugs.csv contains these findings only, with stable IDs REL-001 through REL-009 and the requested central schema. Status meanings:
- source-confirmed: implementation and cited evidence establish the behavior; no device/workflow reproduction is claimed.
- verified: the specific logic was executed with in-memory mocks, as stated; never means physical device or production release verification.
- needs-reproduction: candidate requiring the stated policy decision and discriminating runtime test before promotion.

The original audit made no product edits. The subsequent user-authorized implementation changes only REL-001/REL-002 production paths, their direct tests, the active README capture example, and these three release artifacts. No Gradle, adb, publishing, workflow dispatch, commits, branch changes, keystore reads, user-global Gradle properties, or credential-store inspection were performed. Public GitHub reads used gh during the audit. Concurrent main/reviewer edits, workflows, and main-owned audit tooling were preserved.

Paths below are relative to the repository root. Original defect evidence cites baseline d51eb7a unless explicitly labeled current; the implementation checkpoint and CSV files fields use current lines. No third-party C implementation review is implied by reviewing a makefile or by CodeQL configuration.

## Scoped Implementation Checkpoint

Authorized follow-up: fix only REL-001 receiver completion and REL-002 capture device safety. Status implemented-awaiting-test means source changed and focused host contracts pass, but Android execution remains outstanding. Status verified-fixed for REL-002 means mocked safety logic only, not physical-device or release validation.

### REL-001 Current Changes
- UpdateModel.java:245-283 cancels the previous active/pending receiver and registers with RECEIVER_EXPORTED. DownloadProvider's privileged separate UID must be permitted; a private/signature-only registration would reproduce the delivery failure. The local receiver reference keeps the immediate fallback independent of the terminal callback clearing the field.
- ApkDownloadReceiver.java:59-143 treats the broadcast as an untrusted hint. Exact action/enqueued ID is required; a fresh DownloadManager query filters by the owned ID and validates the returned ID/status. Only STATUS_SUCCESSFUL reaches the existing SHA256 and package/manifest-certificate/installed-signer checks. Failed downloads clean up without requesting an APK URI; pending/paused/running/missing/error rows do not consume the receiver. Terminal handling is synchronized and one-shot. Cancellation rejects queued stale completions and clears pending continuation.
- Current residuals: initial verification remains synchronous; REL-003 UI outcome handling and REL-004 expiry policy remain open. No new exported manifest component or unsolicited-download entry point was added.

### REL-002 Current Changes
- run-ux-matrix.ps1:160-195 requires both an explicit emulator-PORT serial and -AllowDestructiveCapture, then checks ready state and ro.kernel.qemu=1 before build/install/settings/data-clear. Physical serials and implicit device selection are refused. All raw calls and the instrumentation process are serial-pinned; helper calls without authorization fail closed.
- run-ux-matrix.ps1:409-480 snapshots font_scale/system_locales before mutation, rejects unsafe/unreadable values, restores exact prior values (including absent settings) in finally, attempts both restorations independently, surfaces restore failures, and revokes authorization afterward. A disconnected emulator or terminated host process cannot guarantee restoration; no fallback device is selected.
- README.md:266-276 now documents the explicit destructive emulator command. Searched .github (including hidden files), active scripts and README: no workflow invokes run-ux-matrix.ps1, so no workflow edit is required. The caller source-contract test guards any future workflow invocation. Historical task documents were not rewritten.

### Focused Verification
- Independent main-owner confirmation: reran `pwsh -NoProfile -File scripts/tests/run-ux-matrix-safety.Tests.ps1`, exit 0, PASS 26 scenarios with no adb/Gradle. Combined Gradle validation remains pending main's Home follow-up fixes.
- Exact standalone mock command from repo root: `pwsh -NoProfile -File scripts/tests/run-ux-matrix-safety.Tests.ps1`. PASS: 26 scenarios. Actual production functions are parsed and exercised; adb, Gradle and instrumentation processes are in-process mocks. Covers missing serial/consent, physical/offline/unverified devices, guarded direct helpers, changed serial, invalid snapshots, all five variants, success/build/instrument/timeout/packet failures, absent settings and partial restoration failure.
- Host-only JDK 21 `javac --release 17` plus cached JUnit 4.13.2/Hamcrest 1.3 ran `org.adaway.model.update.ApkDownloadReceiverContractTest` and `org.adaway.scripts.UxMatrixScriptTest`: OK (16 tests), 6.666 seconds. This includes two receiver source contracts and fourteen script tests. No Gradle invocation and no Android bootclasspath/build claim.
- Added ten behavioral methods in ApkDownloadReceiverInstrumentedTest and a fourth continuation case (cancelled pending update) in ApkInstallContinuationInstrumentedTest. These are not compiled or executed here. Main must run both Android classes, then real DownloadManager completion after update() returns on API 26/34 with a test-key direct build; prove exactly one verified handoff and reject wrong hash/signer. The test fixture stubs integrity outcomes and is not independent APK cryptography proof.
- PowerShell parsing passed through the mock harness; `git diff --check` passed. File/bug CSV schema and all 80 current hashes checked during handoff.
- Production changes: ApkDownloadReceiver.java, UpdateModel.java, run-ux-matrix.ps1 only. Direct test changes: new receiver Android test, existing continuation Android test, new receiver host contract, existing UxMatrixScriptTest, new capture mock script. Supporting changes: README capture example and the three release audit artifacts. No further product changes after handoff without coordination.

## Confirmed Findings

### REL-001 - P1 - DownloadProvider completion cannot reach the private receiver

Status: implemented-awaiting-test. Original defect is source-confirmed by the Android 14 platform/code trace; current fix passes host source contracts. Android build/instrumentation and real provider delivery remain main-owned and unrun here.

Location: app/src/main/java/org/adaway/model/update/UpdateModel.java:259-262. Supporting paths: ApkDownloadReceiver.java:56-85 and ui/update/UpdateViewModel.java:152-155.

The receiver is registered with ContextCompat.RECEIVER_NOT_EXPORTED. DownloadManager's provider is a separate app using android.media, not AdAway or the system UID. On Android 14 its broadcast is denied by the non-exported receiver check. The only immediate terminal check runs immediately after enqueue; ordinary asynchronous downloads are not finished then. Later polling changes the label to complete but never calls the terminal installer handler. Thus an ordinary update can download successfully without ever opening permission settings or the installer.

Primary platform evidence, read through public gh APIs at the pinned android-14.0.0_r1 ref:
- [DownloadProvider manifest](https://github.com/aosp-mirror/platform_packages_providers_downloadprovider/blob/android-14.0.0_r1/AndroidManifest.xml#L4): android.media shared UID.
- [DownloadInfo.java:230-255](https://github.com/aosp-mirror/platform_packages_providers_downloadprovider/blob/android-14.0.0_r1/src/com/android/providers/downloads/DownloadInfo.java#L230): builds ACTION_DOWNLOAD_COMPLETE for the requesting package.
- [RealSystemFacade.java:78-80](https://github.com/aosp-mirror/platform_packages_providers_downloadprovider/blob/android-14.0.0_r1/src/com/android/providers/downloads/RealSystemFacade.java#L78): sends through its own context.
- [BroadcastSkipPolicy.java:564-577](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/services/core/java/com/android/server/am/BroadcastSkipPolicy.java#L564): rejects cross-UID delivery to a non-exported registered receiver.
- [ActivityManager.java:4668-4693](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/core/java/android/app/ActivityManager.java#L4668): only root/system or owning app get the relevant unexported access.

Falsification considered: the immediate handleTerminalDownload call only covers a download that has already finished. The progress tracker does not provide a later fallback. ApkInstallContinuationInstrumentedTest directly invokes a receiver subclass and substitutes launch/permission/verification; it does not exercise this delivery boundary. Device/vendor behavior beyond the cited Android 14 implementation remains a runtime check.

Fix: use a registration compatible with DownloadProvider or a durable terminal-download observer. Do not simply export and trust incoming intents: require the expected action/ID, re-query terminal DownloadManager state, preserve hash/package/signer validation, and make processing idempotent. An early spoofed completion must not unregister a still-running download.

Regression plan: install a test-key directRelease on Android 14, make the real DownloadManager finish only after update() returns, and require exactly one permission/installer handoff. Repeat on minimum API 26 and current supported platforms. Add forged-ID, early/nonterminal, duplicate-completion, hash mismatch, and signer mismatch cases. No such device execution occurred in this review.

### REL-002 - P1 - UX matrix can erase app data on the attached phone

Status: verified-fixed (mocked logic only). Original defect and current fix were exercised through actual PowerShell functions with in-process mocks; no actual device mutation or full capture run.

Locations: scripts/run-ux-matrix.ps1:183-208 and :353-388.

Invoke-UxMatrix waits for any device, installs test packages, changes settings, and invokes a test that runs pm clear org.adaway. It neither verifies an emulator nor requires a destructive opt-in/explicit serial. A compatible already-installed debug/same-signer app on a physical developer phone can lose its rules, profiles, settings, and database. Installation can fail on a differently signed production app; that narrows the affected state but is not a device safety boundary. Finally deletes font_scale and system_locales instead of restoring their previous values.

Executed the unchanged Invoke-UxMatrix, Set-DeviceState, and Invoke-UxTest function definitions extracted via the PowerShell parser. Replaced external command/filesystem helpers with in-memory mocks and stopped at the data-clear invocation. Observed trace:
    wait-for-device
    shell settings delete system font_scale
    shell settings delete system system_locales
    shell am force-stop org.adaway
    shell pm clear org.adaway
    shell settings delete system font_scale
    shell settings delete system system_locales
    shell am force-stop org.adaway

Fix: require an explicitly selected disposable emulator before installation or any mutation, or a separately confirmed destructive physical-device mode with backup prerequisites. Snapshot and restore original device settings.

Regression plan: mocks must prove zero install/clear/settings commands for physical, ambiguous, offline, or unverified devices. Emulator success and instrumentation-failure tests must restore exactly the original settings. The nearby check-minified-startup.sh already demonstrates an emulator-only destructive-operation boundary.

### REL-003 - P2 - Verification rejection or installer cancellation leaves a completed dead end

Status: source-confirmed.

Locations: ApkDownloadReceiver.java:104-119, :147-168; ui/update/UpdateViewModel.java:65-72, :152-155; ui/update/UpdateActivity.java:74-90.

DownloadManager success posts CompleteDownloadStatus independently of verification. Hash/signer failure only logs; no terminal failure reaches the view model. With install permission already granted, launching the installer retains no pending state and requests no result. Canceling it returns to an activity whose action is still hidden. resumePendingInstall does nothing without pending state. Missing installer resolution has the same lost-feedback outcome.

Repro plan: after resolving REL-001, complete a download with wrong expected digest/signer; separately complete a valid APK with permission already granted and cancel the installer. Require useful failure/cancellation feedback and a retry/install action without leaving and reopening the screen.

Fix: represent download, verification, permission, installer launch, and installation/cancellation distinctly. Publish verification/launch failure to the UI and retain a controlled retry path. Do not describe DownloadManager completion as installation success.

Evidence against overclaim: a failed network download resets progress, and permission-denied continuation does preserve a URI. Those branches do not fix the permission-already-granted/rejected-artifact paths. Existing UpdateActivityDownloadStatusInstrumentedTest injects CompleteDownloadStatus and asserts the action is invisible; no real cancellation/verification-result test was run.

### REL-005 - P2 - Published-artifact verifier does not inspect actual APK identity

Status: source-confirmed. Existing regression fixture inspected, not executed.

Locations: scripts/VerifyReleaseArtifacts.java:46-72, :135-183; scripts/GenerateUpdateManifest.java:44-63; .github/workflows/verify-release-artifacts.yml:76-92.

The standalone/public-artifact verifier hashes the APK, verifies a signed manifest, and compares --expected-cert-sha256 only to the manifest's claimed certificate. It never verifies the APK signature, package name, debuggable state, version name, or versionCode. It does not even parse versionCode. A valid metadata signature authenticates the producer's assertion; it does not establish that the asserted APK identity is true.

Concrete repository evidence: app/src/test/java/org/adaway/security/SecurityHardeningTest.java:656-719 writes plain text "test apk bytes" as the APK, generates metadata with a caller-selected certificate digest, and explicitly expects verification exit 0. That file was inspected only in supporting excerpts and is not claimed as a fully reviewed ledger entry.

Fix: perform actual pinned APK signature/package/version/debug checks in the canonical verifier or clearly separate a metadata-only verifier from the APK identity gate. Compare APK versionCode with signed metadata as well as requested release identity.

Regression plan: reject non-APK bytes, wrong certificate, wrong package, mismatched version name/code, and debuggable APK even when hashes and metadata signatures match. Accept a real fixture APK signed with an ephemeral test key. Ensure the published-artifact workflow invokes the strengthened gate.

Boundary: fork-release-apk.yml:212-271 already performs a real apksigner/aapt check, and renewal also checks the actual APK. No claim is made that the currently published APK has the wrong signer, or that this gap bypasses Android's installation checks.

### REL-006 - P2 - Reports confuse the verification checkout with artifact provenance

Status: source-confirmed, supported by public tag targets.

Locations: scripts/VerifyReleaseArtifacts.java:199, :253-275, :335-348; scripts/run-release-smoke.ps1:96-117, :152; .github/workflows/verify-release-artifacts.yml:21-22; .github/workflows/physical-release-smoke.yml:24-25; scripts/verify-release-readiness.ps1:138-182.

The workflows accept an arbitrary release tag but checkout the dispatch ref. Both report writers label GITHUB_SHA (or local HEAD) as "Source commit" even when the downloaded APK was built from another commit. gh attestation verify is invoked only with --repo, and its verified predicate/source is not decoded into the report. Readiness compares these text fields as if they were the artifact's source.

Public example: v13.5.3b targets cf453b1a7d2f250e143a4d86fce7c79f1f3d2b7c, whereas this checkout and v13.5.4b target d51eb7aea06871c2c6ad2bbedb9ddbaaaca48d77. Running the verifier for the older tag from this checkout would label it d51eb7a. No workflow was dispatched. Tag evidence: [v13.5.3b annotated object](https://api.github.com/repos/stevesolun/AdAway/git/tags/1d16d0185d9975c9e50994236e42844c4bb09e65), [v13.5.4b annotated object](https://api.github.com/repos/stevesolun/AdAway/git/tags/fbaea9addb3cfdbff62c3b2c0cb5411bd19d746c).

Impact: legitimate post-release evidence can fail consistency against an original tag-bound license report; alternatively rebuilding all reports from one later checkout can falsely imply that the old binary corresponds to the later source. Equality of report strings is not an artifact provenance check.

Fix: distinguish tool revision from verified artifact source commit. Resolve and verify the selected tag/attestation subject, expected signer workflow and source ref/commit; bind the actual APK/SBOM identities through aggregation. Refuse report bundles originating from unrelated workflow/ref contexts.

Regression plan: use two source commits and an artifact from only the older one. Verifying it from the newer tooling checkout must report both identities correctly and reject a readiness bundle claiming it was built from the newer source.

### REL-007 - P2 - Cleanup is a silent no-op without a repository checkout

Status: source-confirmed; failing read-only gh subcommand reproduced.

Location: .github/workflows/cleanup-releases.yml:17-43.

The job has no checkout and supplies neither GH_REPO nor --repo for gh release list/delete. GitHub CLI cannot infer the repository from an empty runner directory. The list command runs inside process substitution feeding mapfile; failure is not propagated through mapfile by set -e/pipefail, so the loop can finish successfully with no releases.

Executed the exact read-only release-list arguments from /private/tmp, outside any Git repository: exit 1, "failed to run git: fatal: not a git repository". Did not execute the workflow, mapfile wrapper, or any delete operation.

Fix: pass --repo "$GITHUB_REPOSITORY" for both list/delete and capture listing in a command whose exit is checked before filling the array. Keep tags. Before enabling deletion, protect the sole stable/beta feed from retention rules so cleanup cannot create a separate updater outage.

Regression plan: mock gh failures and empty/successful listings in a clean directory; failure must exit nonzero. Assert repository is passed to every invocation and tags are never deleted. No pipeline run is required for the mock regression.

### REL-008 - P2 - One arbitrary checkbox passes full UX sign-off

Status: verified using the unchanged script with in-memory filesystem/hash mocks.

Locations: scripts/verify-ux-signoff.ps1:132-165, :180; scripts/verify-release-readiness.ps1:396-467; .github/workflows/verify-ux-signoff.yml:42-44.

Sign-off checks only that some checkbox exists, all remaining items are checked, and the source commit matches. It never requires the expected five variants, eight screens per variant, or the global checklist generated by run-ux-matrix.ps1. Deleting unfinished sections turns an incomplete packet green. Readiness only requires a positive checked count and, when provided, a matching packet hash.

Probe packet had a valid synthetic 40-hex source commit and one line "- [x] One arbitrary item". The unchanged sign-off script emitted:
    - Status: passed
    - Checked items: 1
    - Unchecked items: 0
    - Issues: 0

Get-Content, Test-Path, Get-FileHash, New-Item and Set-Content were mocked; the hash was deliberately synthetic. The report was captured to stdout only; no in-memory-report.md file was created. This proves structural false acceptance, not that any actual human review was skipped.

Fix: validate a required, explicit packet schema or exact variant/screen identities plus global checks. Reject missing/duplicate sections and "MISSING" screenshot entries; retain human review as a separate truth assertion.

Regression plan: remove each required variant/screen/checklist row, substitute a one-item packet, add duplicates, and mark missing screenshots checked; every malformed packet must fail. A complete reviewed packet must pass.

### REL-009 - P2 - Current-version metadata renders "unavailable", never "up to date"

Status: source-confirmed.

Locations: Manifest.java:85-89; UpdateModel.java:190-205; ui/update/UpdateActivity.java:62-70, :93-111.

A valid signed manifest with versionCode equal to the installed version throws an exception. The model maps it to null and the activity displays unavailable. All successfully constructed manifests have updateAvailable=true, so the production parser cannot reach markUpToDate. Equality rejection is explicitly codified in ManifestTest.signedManifest_rejectsEqualOrLowerVersionCode.

Fix: separate authenticated metadata from permission to install. Accept same-version metadata as updateAvailable=false while preserving signature/channel/store/expiry checks and rejecting any downgrade installation.

Regression plan: newer valid metadata shows Update; same-version valid metadata shows up to date; invalid/expired metadata shows unavailable; lower-version metadata never becomes installable. UI execution was not performed.

## Expiry Candidate Requiring Policy Confirmation

### REL-004 - P2 candidate - Pending installation forgets metadata expiry

Status: needs-reproduction. The missing state is source-confirmed; whether it violates intended authorization policy needs explicit owner confirmation.

Locations: UpdateModel.java:241-258; ApkDownloadReceiver.java:29-52, :158-179.

Expiry is checked only before enqueue. The receiver retains a download ID, expected APK digest and certificate digest, but not expiresAt or version. On permission return it rechecks bytes and signer, then launches installation. It cannot distinguish a still-valid authorization from one that expired while downloading or waiting in settings.

Discriminating test: use a short-lived signed manifest, enqueue before expiry, leave unknown-app settings pending, advance past expiresAt, then grant and return. Separately finish the download after expiry. Not executed.

If expiry governs installer authorization, retain immutable verified metadata and revalidate/renew it at handoff, refusing stale continuation with an actionable refresh. If the intended contract explicitly authorizes indefinite installation once a download began under fresh metadata, document that narrower policy instead of calling this a crypto bypass. Existing hash/signature checks still prevent arbitrary unsigned replacement. This candidate must not be merged as a reproduced exploit.

## Metadata Renewal and Signer Check

Public read-only snapshot on 2026-11-25:
- Latest beta v13.5.4b APK: 4,142,091 bytes; asset last updated 2026-10-14T20:38:42Z.
- manifest.json and its checksum were replaced 2026-11-24T17:53:13Z.
- Retrieved payload claims versionCode 130504, channel beta, store adaway, expiresAt 2026-12-08T17:53:01Z, APK SHA256 d33d02aabf4d502626fc7ae08c509ff28e3977712fb8ecdb5659cfc6f335ab63, certificate SHA256 9bf3a40102638ed9271f4dea6225a2d66e5d2c5f3e5204cc125376288f7f9595.
- Latest scheduled renewal [run 45453341715](https://github.com/stevesolun/AdAway/actions/runs/45453341715) succeeded on 2026-11-24 from baseline d51eb7a. Six returned weekly runs, October 20 through November 24, reported success.
- [Latest stable API](https://api.github.com/repos/stevesolun/AdAway/releases/latest) resolves to v13.5.0 with only AdsAway_13.5.0.apk, no manifest. The stable lane's no-manifest branch warns and exits successfully at refresh-update-manifest.yml:61-65. This is a current rollout gap, not evidence that beta renewal is broken. Do not enable beta silently or relabel it stable.
- [Release assets](https://github.com/stevesolun/AdAway/releases/tag/v13.5.4b) remain mutable; the observed API immutable value was false.

The public payload was parsed/inspected, not independently signature-verified against an extracted installed-client key in this pass. No APK was downloaded or executed. Public certificate hashes are not private keys. No signing or manifest secrets were read.

Positive controls: signature verification precedes payload interpretation; expected channel/store and 14-day maximum validity are enforced; direct-update builds require a configured signer; F-Droid is excluded; archive package name and installed signer history are checked; continuation rehashes bytes and rechecks signer after settings.

Remaining signer/renewal risks are not claimed as reproduced bugs:
- Key continuity: renewal validates against current configured metadata key but does not compare it with keys embedded in installed client versions. An accidental signing/public-key replacement could strand older clients. Prove a rotation/migration plan with test keys; do not rotate production material as part of this audit.
- APK signer history/multisigner semantics: intersection matching is not full signing-lineage capability validation. Android also enforces its own install rules. Test real APK pairs/rotation on API 26 and 28+ before claiming continuity; no unauthorized installation demonstrated.
- Process death: pending URI/receiver live only in application memory; a killed process cannot recover an active download/install session. This was already disclosed by the historical review and remains an explicit device regression gap.
- Initial completion verification still hashes, copies and parses on the broadcast/main-thread path. No latency/ANR measurement was taken. Off-main work is explicitly deferred outside these two scoped fixes, not claimed resolved.
- Candidate feed selection is capped at the first 20 releases; pagination and republication/version-order tests remain desirable. Current public feed has only five releases, so no current pagination outage was established.
- Release-readiness consumes old report snapshots without rechecking current metadata expiry. A past passing artifact report is not proof of a fresh feed at launch time.

## Native Build Boundary

Confirmed source/build facts:
- settings.gradle includes only :app and :sentrystub. app/build.gradle has no tcpdump/webserver project dependency or native-source build block.
- tcpdump/build.gradle still describes ndk-build and executable-to-lib*_exec.so copying, but the module is not included in the active graph.
- tcpdump/jni/Android.mk includes stub, libpcap and tcpdump. Its child makefiles list source files and compiler/linker options; this review did not read those C bodies.
- TcpdumpUtils.TCPDUMP_CAPTURE_ENABLED is false; capture start/read return disabled. Some system detection/stop helpers remain.
- WebServerUtils.isWebServerAvailable always returns false and startWebServer has no executable launch.
- .gitattributes excludes dormant tcpdump/webserver and historical resources from exported tag archives. This is not deletion from the repository.
- Configured signed builds select :sentrystub; unsigned development builds select external Sentry artifacts. JNA/pcap4j remain declared/transitive dependency surfaces. No claim is made that every resulting APK is native-library-free.
- check-license-boundary.ps1 rejects named tcpdump/pcap/mongoose/webserver/ssl/crypto APK entries and checks declared notice coverage. This is a bounded denylist, not proof of every dependency's security, native alignment, licensing, or provenance.
- The C++ CodeQL lane uses build-mode: none. A passing source scan is not an NDK build, runtime proof, or this reviewer's manual C coverage.

Required external proof: inspect the actual selected release APK's entries and ABI/page alignment, reconcile its dependency graph/SBOM, and verify no dormant executables were reintroduced. Do not list bundled third-party C files as reviewed based on this section.

## Original Audit Checks

- Read-only branch/HEAD/status checks confirmed the requested baseline. Reviewed scope had no tracked diff when hashes were captured.
- Actionlint across all 11 YAML workflows: initial invocation reported only the locally unknown custom self-hosted android-device label; rerun suppressing exactly that label passed. ShellCheck was disabled. This does not prove a matching runner exists.
- PowerShell AST parsing: all seven existing .ps1 release scripts parsed with zero errors.
- Separate bash -n checks passed for all four .sh release scripts; sh -n passed for gradlew. Windows gradlew.bat was read but not executed.
- Full structured XML inspection of gradle/verification-metadata.xml: 762 components, 1,416 artifacts, 1,416 SHA256 entries, no malformed SHA256, no artifact without a digest/PGP entry, no trusted-artifact bypass rule. Metadata and signature checks are enabled; network key servers disabled. External trust/checksum provenance was not re-established.
- REL-002: actual extracted functions with all external/filesystem calls mocked; stopped before the mock destructive call could proceed.
- REL-008: unchanged sign-off script with mocked packet/filesystem/hash; captured false-positive report in memory/stdout.
- REL-007: read-only gh release-list failure outside a Git checkout.
- Public gh release, workflow-run, annotated-tag and pinned AOSP source reads. No workflow logs containing secrets were requested.
- Unit/instrumentation/build/signing/publishing pipelines were not run. Test-source inspection is not a passing test run.

## Remaining File and Coverage Gaps

These files/surfaces are not claimed as reviewed by this lane:
- gradle/wrapper/gradle-wrapper.jar: no binary decompilation or independent upstream checksum validation. Wrapper properties/scripts and CI wrapper-validation wiring were reviewed.
- gradle/verification-keyring.gpg and gradle/verification-keyring.keys: not opened or independently validated. They are public verification keyrings, but no key ownership/provenance coverage is claimed.
- third_party/maven/** artifact bodies and all resolved external dependency binaries/source. Repository filtering and metadata structure were reviewed, not upstream vulnerability/license clearance.
- tcpdump/jni/**/*.c, **/*.h and other third-party native/autoconf sources, including stub.c; any historical webserver C sources. Only the explicitly listed build boundary files are in the ledger.
- Other app runtime/root/VPN/UI Home files and the main-owned full inventory/new audit scripts. ShellUtils/TcpdumpUtils/WebServerUtils rows are explicitly native-boundary assessments, not broad root subsystem approval.
- SecurityHardeningTest.java was inspected only in supporting targeted excerpts (including :656-830 and installer source-contract sections), so excluded from the full-file ledger. Other unlisted release/script tests, layouts/resources, and historical docs are not claimed fully reviewed.
- No merged/minified manifest/APK inspection, physical-device install or same-key upgrade, live DownloadProvider delivery, installer return/cancellation, signer rotation, process-death recovery, or release-readiness aggregation run.

Next main-owner gates: validate the implemented REL-001/REL-002 scope, then triage REL-003 installer outcomes, REL-005/REL-006 evidence integrity, REL-008 complete UX schema, REL-007 cleanup behavior, and REL-009 current-version UI. Resolve REL-004 policy before implementing its candidate remedy. None of those unrelated findings were fixed in this checkpoint.

## File-by-File Ledger

The following current-file hashes mirror release-files.csv. "Reviewed" is scoped to the stated assessment and is not a certification of absence of defects.

| Path | SHA256 | Assessment | Bug IDs |
| --- | --- | --- | --- |
| app/src/main/java/org/adaway/model/update/ApkDownloadReceiver.java | 70e801595f0e8cd7b9fea34c3bdbd8c9037e313f987e14a21d08ea674c7ef460 | Reviewed fully: completion hints require matching provider ID/status; success alone reaches unchanged hash/package/signer checks; duplicate/cancel guards added. REL-003/004 and synchronous verification remain open. | REL-001;REL-003;REL-004 |
| app/src/main/java/org/adaway/model/update/ApkIntegrityVerifier.java | 5bd6b5897cb25926ca1a609c50b0c92179b0e1a8e854278224cd1635396c6444 | Reviewed fully: streams SHA256; copies APK privately; checks package and signer-history intersection. Rotation/multisigner platform fixtures remain untested. | none |
| app/src/main/java/org/adaway/model/update/ApkUpdateService.java | 3d9b1459111de5cbf1cd46da95834faaa7e3af3d55a7ac94d36689d43253d2d0 | Reviewed fully: worker uses fresh synchronous result; preference sync uses unique periodic UPDATE. Network constraints/retry remain limited. | none |
| app/src/main/java/org/adaway/model/update/Manifest.java | 8f8241a4f07f848cc9462cad657ffc4d3051be7417218076a48a5dfa6e9ea3bf | Reviewed fully: signed payload before parsing; channel/store/HTTPS/hash/14-day expiry enforced; same-version metadata becomes an error. | REL-004;REL-009 |
| app/src/main/java/org/adaway/model/update/ReleaseManifestSource.java | 1c119644993c7e0a0674a4c0063edf53ff3d9a7c1914ece07bc1bd316f0bb410 | Reviewed fully: fork asset URL and published release selection; beta queries first 20 only; no pagination. No current selection defect confirmed. | none |
| app/src/main/java/org/adaway/model/update/UpdateModel.java | f1a7a8035aff4344ebc357961b4cd3e92b7da17c8599a0e9b27d858487756400 | Reviewed fully: provider-compatible exported registration; previous receiver/pending installer cancelled; direct-build/store and pre-enqueue expiry gates retained. Real provider delivery awaits main. | REL-001;REL-004;REL-009 |
| app/src/main/java/org/adaway/model/update/UpdateStore.java | 42f1faf65b0dab829fc6edea5d21994b79beb99d1119bd65f502048ff73e3d49 | Reviewed fully: configured direct certificate required; F-Droid excluded; signer history accepted. Real rotation/multisigner fixtures remain open. | none |
| app/src/main/java/org/adaway/ui/update/CompleteDownloadStatus.java | 5d6330a1bd818b90b409bb295c2aff0d58ed4b69d2729f7edcdfaeeef925eff4 | Reviewed fully: download completion formatting only; must not stand in for verified install result. | REL-003 |
| app/src/main/java/org/adaway/ui/update/DownloadStatus.java | 2b55d0d38259ac38cb6a9ebc99e899c33c6b6321f2f6b31a50af618ae366544f | Reviewed fully: progress-only interface cannot represent verification/installer outcomes. | REL-003 |
| app/src/main/java/org/adaway/ui/update/PendingDownloadStatus.java | 9ea9711d3524a540d14c632b8cd0374890ae83e0e39d832e5b324bc2139dcb0f | Reviewed fully: progress arithmetic and localized byte formatting; caller checks positive total. | none |
| app/src/main/java/org/adaway/ui/update/UpdateActivity.java | 630e78bb977a6894f47c4e165b0f6221c372b2770812c5b346b430b1a3cc5607 | Reviewed fully: permission-return hook exists; installer cancellation/rejection can leave hidden action; up-to-date branch unreachable for valid current feed. | REL-003;REL-009 |
| app/src/main/java/org/adaway/ui/update/UpdateViewModel.java | 441c37cd2a9d11a9f2246db4b3cfabcc9541500d1eee74ce5deac1049e970caa | Reviewed fully: bounded DownloadManager polling; success ignores verifier/installer outcome; resume only when pending state exists. | REL-001;REL-003 |
| scripts/GenerateUpdateManifest.java | 57a9b49fb1e258ca3c4ebda2e3e6fe8869a88ea169109205de8d1cb7f5f3221d | Reviewed fully: RSA payload signing; bounded validity; HTTPS allowlist; certificate/version assertions supplied by caller rather than APK inspection. | REL-005 |
| scripts/VerifyReleaseArtifacts.java | 167a39a863ecb1f0460ca5f7a63f20a0142a76501b647c1af3d9b6803ae88372 | Reviewed fully: signed manifest/checksums checked; actual APK identity not inspected; reported source commit comes from verifier checkout. | REL-005;REL-006 |
| scripts/check-license-boundary.ps1 | c78c5677abe4b802e525248722c9a57e01398823f3536dc4904d2ebe27b15375 | Reviewed fully: source/archive/APK-name/SBOM notice guards; not a complete native audit or legal approval; report binds artifact names not hashes. | none |
| scripts/check-license-boundary.sh | edec5a6a88089c6c14fdd973648c87873f9ad3587ed5befe41775a81e09d392b | Reviewed fully: quoted PowerShell delegation and exit propagation. | none |
| scripts/check-minified-startup.sh | a27d255168bdf76fe4c62d58fadf0d8e2d093d65d86b0a87894b7045494acc74 | Reviewed fully: signature/package/debug checks; emulator-only destructive install; bounded stable process/foreground polling; startup-only scope. | none |
| scripts/generate-update-manifest.ps1 | 1cbef8a81acb26b161d5181ada68d2abf5350de05b3402a893ed982513972d5c | Reviewed fully: Java tool discovery and argument/exit forwarding. | none |
| scripts/generate-update-manifest.sh | c97792903378c5fd371a38ca28eb69eec6cd274ace28d3dec626b7837b35689a | Reviewed fully: quoted Java source-launch delegation. | none |
| scripts/run-release-smoke.ps1 | 2e825a31fb551743ffb96792a7e3d6f38d120b264bc37e8773c4a45cd2ebfa41 | Reviewed fully: pinned signer tool and nondebug package check; physical-device gate; launch-only evidence; source commit mislabeled from checkout. | REL-006 |
| scripts/run-ux-matrix.ps1 | 761dfbcb404d76c0439343f18d6679ff7272a61860ed7ed21f5b25dc4ffa9931 | Reviewed fully: explicit emulator serial and destructive authorization; all adb paths pinned; settings snapshot/restoration and fail-closed probes; 26 mocked safety scenarios pass. | REL-002 |
| scripts/verify-release-artifacts.ps1 | 66c5e606fed6c6cb68f9876d464d78aceeaff1fe1a777f9cd4b01b072fdde488 | Reviewed fully: Java tool discovery and argument/exit forwarding. | none |
| scripts/verify-release-artifacts.sh | 0a4094dc3ffccc8fe03be390725aa70cdf6814d4005fb1b6c07dffab18b70ad4 | Reviewed fully: quoted Java source-launch delegation. | none |
| scripts/verify-release-readiness.ps1 | 9a2946a6f8d063ebf70120f903362f91146b474dfac72ff27c67aac2db9007fd | Reviewed fully: report consistency and UX packet hash checks; does not bind APK provenance to release source or require complete UX matrix. | REL-006;REL-008 |
| scripts/verify-ux-signoff.ps1 | 9250756bd1b681d0196e005a63e8d7a9e44eabe1a1772afaec683afb53d93eee | Reviewed fully: reviewer/commit/check state checks; accepts arbitrary single checkbox; mock probe confirmed false pass. | REL-008 |
| .github/workflows/AndroidLocaleChecker.java | 2aa9a5569f6ca2454650f2fcd6e58818da6ae06120c33065c43a2085898a3936 | Reviewed fully: locale path/header/ellipsis normalizer and GITHUB_OUTPUT; changed/deleted path edge cases not exercised. | none |
| .github/workflows/android-analysis.yml | 5bff34460583d906793c75c757890e68a13fcaac07d006546146320e71569296 | Reviewed fully: pinned CodeQL/actions; debug Java build; C++ no-build scan is not native compilation or manual C review. | none |
| .github/workflows/android-ci.yml | a201faf18822faa2c595c0729ae9f694d03938330db6145eb2bcc070edda0dac | Reviewed fully: strict dependency checks; ephemeral release dry-run key; connected debug suite then minified startup; no end-to-end signed updater gate. | REL-001 |
| .github/workflows/android-locale-check.yml | 96066802c58113c32b12a8ba52e34f327b7cb1e658971ed8bb3823e4e7a2f309 | Reviewed fully: read-only PR permissions; pinned actions; normalization fails on edits; unquoted filename splitting remains an edge-case gap. | none |
| .github/workflows/cleanup-releases.yml | 1a041025dda3479aa317516b5c962fd755b816f45f0d32741ce1c83b6e8a9e02 | Reviewed fully: no checkout or explicit repository for gh; enumeration failure hidden inside process substitution. | REL-007 |
| .github/workflows/fork-release-apk.yml | a307820460275b143eda8cdd4a025d0e01fb4255e54cb6856f7039b0ab880c09 | Reviewed fully: signed tag; strict build; APK package/version/signer gate; checksums/SBOM/attestations before publishing. Not executed. | none |
| .github/workflows/issues-checker.yml | 8752c38814c70750a4ccde482335a2f9de75b242e39f2fa5e8155fa9944d50ca | Reviewed fully: narrowly scoped issue-comment action; pinned dependency; no shell evaluation of issue content. | none |
| .github/workflows/physical-release-smoke.yml | 5919a085865ca8233417f956e6017ca5d29691f6ab347a4e255ad6f6a392cc1a | Reviewed fully: manual self-hosted workflow; downloaded tag and runner source ref not bound; direct input interpolation is trusted-dispatch hardening debt. | REL-006 |
| .github/workflows/refresh-update-manifest.yml | cdc2a99b64e7cd8c13143f91f4206ee474a299dfb290cf26a5ddb0d456b19374 | Reviewed fully: weekly stable/beta metadata renewal; existing APK checksum/signer verification; latest beta operationally fresh; key continuity and missing stable feed remain gates. | none |
| .github/workflows/verify-release-artifacts.yml | 5b98c11e7c428b6031ff99253d49fd4f1bfc680d6b81cd4a2c12926775451e25 | Reviewed fully: six asset download/attestation checks; no APK parser; arbitrary tag checked on dispatch checkout. | REL-005;REL-006 |
| .github/workflows/verify-release-readiness.yml | 62e769da85c1c789a89054e930ef47891eb5be872c1a8bd9be276ee7254c20a4 | Reviewed fully: numeric run IDs and report existence checks; no originating workflow/ref provenance validation for downloaded reports. | REL-006;REL-008 |
| .github/workflows/verify-ux-signoff.yml | de5175d0024551050aaee567b483a10fb3ab86da58fc8175b963eb8db870b6de | Reviewed fully: manual packet/reviewer inputs; verifier permits incomplete packet; screenshot artifacts not required. | REL-008 |
| app/build.gradle | a0a6c117c82bcdb74a4257fb38531349600d53a6f82468b909bf2cd2688166fe | Reviewed fully: direct-only update flag; release trust property checks; minification rules; app/sentrystub dependency boundary. Debug uses release key when supplied; no build run. | none |
| app/proguard-rules.pro | 9cfec52e582471fc3202fd2d94904f9d12466234054d4feb698a8a3ccd81c9e4 | Reviewed fully: Room/InputMerger constructors retained; dnsjava desktop-only exclusions; consumer worker rules and shrunk runtime not independently executed. | none |
| app/src/main/AndroidManifest.xml | b917d420e6f46f83e8320113ca2052494be6ac536ecafb50287855df38942140 | Reviewed fully: private update UI; signature command permission; privileged VPN/tile bindings; installer permission absent; backup implementation outside lane. | none |
| app/src/directRelease/AndroidManifest.xml | 87da2b5a77842c743f5df9fd4fbee7892c54ffbd9b6ee9b954108d9af905046f | Reviewed fully: installer permission only in direct-release overlay. | none |
| app/src/debug/AndroidManifest.xml | 84f0e7e9bd3cb3813211f7c7269be7349d66e77e1fd8e00bee103695838d085d | Reviewed fully: two activities exported only for debug; no direct-update permission. | none |
| app/src/androidTest/AndroidManifest.xml | 3dfc58cc206463d77125d9137bd4a4ad5be384eeb60b98a635841176306b3020 | Reviewed fully: exported test activity/provider are test APK fixtures; no production component claim. | none |
| build.gradle | beb90a76566829e5f99f59061e4f735dff9b20ab3ad1047ffc4b2d266c942b4b | Reviewed fully: Google buildscript repository and pinned Sonar/CycloneDX plugin versions; no native module inclusion. | none |
| settings.gradle | cdb393aa7c07ccc4554bea8c43ac445e37cb34bbc66bd9b18e45c4562736e555 | Reviewed fully: only app and sentrystub included; repository mode constrained; libsu local/JitPack group filter; tcpdump/webserver excluded. | none |
| gradle.properties | a08d181463cd8dfc89846134a5a490393ffdc7aca897ce32d1903e556c24d305 | Reviewed fully: tracked nonsecret JVM/cache/AndroidX settings; user-global properties intentionally not read. | none |
| gradle/libs.versions.toml | 7b20730f8ebb2945d856a4d7023a8d9fb949768cec93353c9386a37c4bd274d6 | Reviewed fully: fixed dependency versions; OpenSSL alias exists but unused by active build; no CVE completeness claim. | none |
| gradle/wrapper/gradle-wrapper.properties | 558c2df8183916424bb7ab87257d16bb70ee6a79498fd5f2e2d02669f95ef54e | Reviewed fully: HTTPS Gradle distribution with SHA256 pin; binary wrapper origin not independently attested. | none |
| gradle/verification-metadata.xml | b00c78cd2a6af3b0cea617bc8c0b34f42febd119e5f88e4d20f6e8c287d9fa6e | Structured full-file review: 762 components and 1416 artifacts; all 1416 have valid SHA256; signatures/metadata enabled; no trusted-artifact bypasses. Hash provenance/keyring not revalidated. | none |
| gradlew | 63135287117a1e6d12c84580f1f49c61d1ba02218ecd28660605e97f976e7d65 | Reviewed fully: quoted JVM wrapper launch and platform branches; not executed. | none |
| gradlew.bat | b93ed4508dbde64b4655984164af86659861d4dfcd0059fb31e5204083d08777 | Reviewed fully: Windows JVM discovery and SUBST wrapper; platform behavior not executed. | none |
| sentrystub/build.gradle | ee83f2980bef09eedeb3d930390591f80c5c801b3859322ba050166af39560ed | Reviewed fully: Java Android library included for signing-configured builds; no native source build. | none |
| sentrystub/src/main/AndroidManifest.xml | 571f2735faf5e857752ee057ac8ef63425576a721616c60b86ee5d4c0a2d682f | Reviewed fully: empty library manifest. | none |
| tcpdump/build.gradle | 5dff3871b8e4838d82927660e154e38161ae56cb52a5571f6b12a15119f5de2b | Native boundary review only: dormant NDK module and executable-to-SO packaging; excluded by settings. No C implementation coverage. | none |
| tcpdump/src/main/AndroidManifest.xml | 571f2735faf5e857752ee057ac8ef63425576a721616c60b86ee5d4c0a2d682f | Reviewed fully: empty dormant library manifest; no C implementation coverage. | none |
| tcpdump/jni/Android.mk | d7714824db3a93e9ee6cf904a10117df99916c916feac21b22cf85168cd430ba | Native boundary review only: includes stub/libpcap/tcpdump makefiles; inactive in app graph. No C implementation coverage. | none |
| tcpdump/jni/Application.mk | 149045cd037fbd29d570b6096177fcc2a1d4043dd160229c7df81d14e1f5ff54 | Native boundary review only: all ABIs and API26 selection; no native build executed. | none |
| tcpdump/jni/stub/Android.mk | 85d0328f50e15f71e7f8d65c385b3162c1cb2a4ddd742cddf16a1a2b3f4aaac8 | Native boundary review only: shared stub library packaging target; stub.c not reviewed. | none |
| tcpdump/jni/libpcap/Android.mk | 21b11bee33bfe1d5d5cd7c9c1cf3384519fb10a851ff3f570b464edb87d88d1d | Native boundary review only: static libpcap source list/compiler flags; listed C files not reviewed. | none |
| tcpdump/jni/tcpdump/Android.mk | 6ff54074f423e7ae5a6880a5a4bbe42676499f5aa25bd8e79446be2ec0c61c26 | Native boundary review only: executable source list/PIE/page-size flags/static pcap linkage; listed C files not reviewed. | none |
| tcpdump/jni/libpcap/CleanSpec.mk | 0b2a8448d66cb905a29c4ff463f6f837459687d3f5e0c51fbe59f9fac5201f4a | Reviewed fully: comment-only legacy clean template; no active commands or C coverage. | none |
| tcpdump/jni/tcpdump/CleanSpec.mk | 0b2a8448d66cb905a29c4ff463f6f837459687d3f5e0c51fbe59f9fac5201f4a | Reviewed fully: comment-only legacy clean template; no active commands or C coverage. | none |
| .gitattributes | b320a3613c16d10cd00b7b1190f5bc442bc174c86b61a7c73d1096d849fde66e | Reviewed fully: dormant native/artwork/test assets export-ignored; local Maven bytes marked binary; git contents are not deleted. | none |
| app/src/main/java/org/adaway/model/root/TcpdumpUtils.java | f42dc881b220466ade08ddac47c8dfd7e66b7d82244667bb6ff575935c56bb53 | Reviewed fully for native boundary: capture constant false and start/read return disabled; system probe/stop remain; no third-party C coverage. | none |
| app/src/main/java/org/adaway/model/root/ShellUtils.java | 0b3a490c055627f772c92f8833e6eec450e06fbb430806f14c19a96b2e432b6d | Reviewed fully for native boundary: escaped native executable path and shell helpers; parameters assembled by callers; broader root runtime outside lane. | none |
| app/src/main/java/org/adaway/util/WebServerUtils.java | 6119d088acef09c9aabc3874bdbac1f0630e4f06eb36d9cbf39b02bdc8d96576 | Reviewed fully for native boundary: availability always false and no server start; stop/status helpers remain; no native implementation coverage. | none |
| app/src/test/java/org/adaway/model/update/ManifestTest.java | ee6004f33b3aff55f93d3f4453b709811b7f42bc2c4b2c5e7bfcbe72933fcaeb | Reviewed fully: envelope/channel/store/expiry/URL tests; equal-version rejection codified; no post-enqueue expiry test. Not run. | REL-004;REL-009 |
| app/src/test/java/org/adaway/model/update/ReleaseManifestSourceTest.java | 688a5461578b711f9e95ed22112a4c4368f12e84f821a3bcfa7e3b1e15373c04 | Reviewed fully: stable/beta/foreign asset/draft/time tests; no 20-release pagination fixture. Not run. | none |
| app/src/test/java/org/adaway/model/update/ApkIntegrityVerifierTest.java | 4f9fcce5e9fec57a9f1311e1551415b453601f086ff977185c93742e0862c026 | Reviewed fully: byte digest and signer-set unit cases; no real Android APK/lineage validation. Not run. | none |
| app/src/test/java/org/adaway/model/update/UpdateStoreTest.java | 50c82728195b3c204244f582469ac66e8fb5ccfd54d0bdf40eb5d9cd99ff1836 | Reviewed fully: configured pin/F-Droid/upstream/normalization cases; no real installed lineage fixtures. Not run. | none |
| app/src/test/java/org/adaway/security/ReleaseWorkflowRegressionTest.java | 9979847109070f9edd662d93761a049b9ae00431c807045edafa6ee87a46f5e1 | Reviewed fully: source contracts and mocked toolchain/minified startup cases; no actual DownloadProvider delivery test. Not run. | REL-001 |
| app/src/androidTest/java/org/adaway/model/update/ApkInstallContinuationInstrumentedTest.java | 11dde235cacff365bc15cabf1aad4e59aff91da79538039d5edf1e9f54fc6422 | Reviewed fully: four isolated continuation tests including cancelled pending update; Android execution pending main; no real installer result/expiry/process-death proof. | REL-001;REL-003;REL-004 |
| app/src/androidTest/java/org/adaway/model/update/ApkDownloadReceiverInstrumentedTest.java | e9c2a6399dcb3fa814d351bbefcb6a4831992612d1c5aff55bc04afb383e8bd6 | Reviewed fully: ten isolated Android receiver regression methods for forged/early/duplicate/missing/failed/cancelled completion; provider/URI/verification fixtures; not executed in this lane. | REL-001 |
| app/src/test/java/org/adaway/model/update/ApkDownloadReceiverContractTest.java | 71dba32279f093873ccad640e82cf3823895f7be8c3931825727992ae54017c0 | Reviewed fully: two host source-contract checks passed for exported provider registration and retained download/integrity boundaries; not Android runtime proof. | REL-001 |
| app/src/test/java/org/adaway/scripts/UxMatrixScriptTest.java | 576fb349372cca8fb743be4e2d64c65cb29ac191bee684b2401510ac8cfc559a | Reviewed fully: fourteen host JUnit tests passed including mocked capture safety and active caller authorization contracts; existing sign-off schema gap remains. | REL-002;REL-008 |
| scripts/tests/run-ux-matrix-safety.Tests.ps1 | 03c9dc6150614889ce3fd9aecdf0b7737e5fad2f410505d3cc2abcdaabe73ca3 | Reviewed fully: 26 passing in-process mocked scenarios; no adb/Gradle launched; authorization denial, serial pinning, five-variant capture, timeout/failure restoration, and raw-entry checks. | REL-002 |
| README.md | e1132b6b24945ef9647d66319a73f7feae2915ef09b78a0c1c0ec38a7cffa07b | Read fully; review scoped to release/capture instructions: explicit disposable-emulator serial and destructive authorization example; no whole-product documentation correctness claim. | REL-002 |
| app/src/androidTest/java/org/adaway/model/update/UpdateModelNetworkFailureInstrumentedTest.java | 6e76ae1f947ec3b0cb2a945c05ee91d0976c78fcbb7989666f4759b23e9ff70c | Reviewed fully: HTTPS mock feed and fresh/failure LiveData result; no actual DownloadManager or signature continuity smoke. Not run. | none |
| app/src/androidTest/java/org/adaway/model/update/ApkUpdateServiceWorkManagerTest.java | df11d88f8abe24c642c7ddae65f98bfe78953ebc9d5b23186d76d8a157637805 | Reviewed fully: preference scheduling and periodic replacement; does not exercise notification-to-install flow. Not run. | none |
| app/src/androidTest/java/org/adaway/ui/update/UpdateActivityDownloadStatusInstrumentedTest.java | 2367bb73449cc3347bbb5dc6119f2e57d03e055f75716609cf9c0b3b28df7826 | Reviewed fully: injected pending/complete/null rendering; completion hides action as asserted; no installer result feedback. Not run. | REL-003 |
