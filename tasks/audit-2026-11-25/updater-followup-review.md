# Updater Follow-up: REL-003 and REL-004

Date: 2026-11-25
Branch: codex/full-codebase-audit
HEAD: d51eb7aea06871c2c6ad2bbedb9ddbaaaca48d77
Status: implemented-awaiting-main-validation, not release approval.

## Scope and Coordination

Read CLAUDE.md, tasks/lessons.md, release-review.md, and release-bugs.csv. The user explicitly authorized install-time expiry, resolving REL-004's earlier policy question. Changed only the four owned updater classes, two narrowly related status classes, default update strings, three new test files, and these three sidecars. Existing dirty model/UI tests, Manifest.java, ManifestTest.java, beta settings, dependencies, workflows, and base audit ledgers were not edited. No Gradle/emulator/adb, commit, push, branch change, or external release operation was performed by this lane.

The shared checkout already contained REL-001 receiver hardening, cancelled-continuation protection, and REL-009 current-version handling. Those changes are retained. The CSV hashes describe complete current files, including prior main-owned work, not an assertion that this lane authored every diff from HEAD.

Compile-ready was signaled after implementation. Main then confirmed production compilation passed and reported two wrong test IDs in `/tmp/adaway-audit-layout-red.log`. Both `R.id.updateButton` references are now `R.id.update_button`. Main separately owns the NetworkCapabilities.Builder Android test issue in that log. A new passing host guard checks every ID in the new UI test against the real layout. Source is settled for main's next build; Android test compilation/execution after the ID correction is not claimed here.

## REL-003: Retry Without False Success

- UpdateModel.java:45,129 exposes download, verification, permission, installer-opened, failure, expiry, and refetch phases. Receiver callbacks are scoped to the active download before UI publication, so a superseded background continuation cannot overwrite the next attempt.
- ApkDownloadReceiver.java:109,188 reports rejection and missing installer/settings outcomes. Permission denial retains the valid pending URI without automatically reopening settings. Verification failure, expiry, and launch failure clear the continuation. Installer launch clears the pending URI and reports INSTALLER_OPENED, never installed/success.
- UpdateViewModel.java:48,55,140 observes those phases. Both successful and failed provider polling go through the same independently validating, one-shot receiver. A queued progress update must match the tracked ID and DOWNLOADING state. Tracking failures cancel the old receiver and leave an actionable failure. Permission-return verification remains on networkIO as before.
- UpdateActivity.java:79 and InstallDownloadStatus.java keep retry visible for rejection, denied permission, missing handlers, and unconfirmed installer handoff. Busy phases do not offer overlapping retry. Manifest delivery cannot override an active phase's rendering. The legacy CompleteDownloadStatus label is download-only; production polling no longer creates it.
- Deliberate boundary: no system installer result callback was introduced. The app does not distinguish real cancellation from installer failure or infer installation success. Returning from either retains honest unconfirmed feedback and Retry update without an automatic installer relaunch. Retry downloads anew, rather than retaining an installer-opened URI. A real same-signer upgrade/cancel test remains required.

## REL-004: Expiry at Handoff

- UpdateModel.java:333 uses the production receiver constructor that takes a verified Manifest. Receiver expiry and both digests are captured from that same immutable signed payload; there is no new unsigned source of authorization. Current-version manifests still cannot enqueue; direct-build/store and signed-parser checks are unchanged.
- ApkDownloadReceiver.java:111,188,231,257 checks freshness at download completion, before permission continuation, after re-verification through installApk, and after activity resolution immediately before startActivity at :283. `now >= expiresAt` fails closed. Expiry clears the pending URI, publishes METADATA_EXPIRED, and invalidates the cached manifest. Later permission grant or clock rollback cannot revive that cleared continuation.
- Check again cancels old authorization and invokes the existing authenticated metadata fetch/parser. Failed refetch remains actionable; successful refetch returns to ordinary manifest rendering. It does not auto-download or auto-install the newly fetched manifest. The next explicit Update now still passes the existing version/expiry/store checks.
- Existing package-private `@VisibleForTesting` hash-only receiver constructors retain an unbounded test expiry solely for the pre-existing isolated fixtures, which were not edited. The former public constructor is now package-private. The only production receiver allocation passes Manifest; the new host contract guards that wiring. New expiry tests use explicit bounded expiry and a controlled clock, not the compatibility fixture overloads.
- Hash, APK package, manifest certificate, installed signer checks, exact action/download ID/provider row checks, exported-provider registration, terminal synchronization, and one-shot cleanup are preserved. Expiry is an additional authorization boundary, not a replacement for cryptography.

## Verification Performed Here

Standalone host compilation used JDK 21 with `--release 17`, cached JUnit 4.13.2 and Hamcrest 1.3. No Gradle or Android runtime was invoked.

```sh
/opt/homebrew/opt/openjdk@21/bin/javac --release 17 -cp /Users/steves/.gradle/caches/modules-2/files-2.1/junit/junit/4.13.2/8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12/junit-4.13.2.jar -d /tmp/adaway-updater-followup-tests app/src/test/java/org/adaway/model/update/ApkDownloadReceiverContractTest.java app/src/test/java/org/adaway/model/update/UpdaterFollowupContractTest.java
/opt/homebrew/opt/openjdk@21/bin/java -cp /tmp/adaway-updater-followup-tests:/Users/steves/.gradle/caches/modules-2/files-2.1/junit/junit/4.13.2/8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12/junit-4.13.2.jar:/Users/steves/.gradle/caches/modules-2/files-2.1/org.hamcrest/hamcrest-core/1.3/42a25dc3219429f0e5d060061f71acb49bf010a0/hamcrest-core-1.3.jar org.junit.runner.JUnitCore org.adaway.model.update.ApkDownloadReceiverContractTest org.adaway.model.update.UpdaterFollowupContractTest
```

Result: `OK (9 tests)`, 0.049 seconds on the final host run: seven new wiring/resource/expiry source contracts and two existing REL-001 contracts. This is not Android compilation or behavioral proof. `git diff --check` passed. New default strings and test layout IDs were parsed and checked through those tests.

## Main-owned Validation

- Run `org.adaway.model.update.ApkInstallExpiryRetryInstrumentedTest` (10 methods): equality/late completion, expiry during verification, permission return after expiry with permission denied/granted, expiry during re-verification, rejected APK, missing settings/installer, denial then grant, no automatic relaunch after returning from installer, unreadable/changed pending APK, and cancelled receiver rejection. Provider, byte/signature verification, clock, and launch outcomes are fixtures. The final post-resolution expiry check is currently source-guarded, not behaviorally exercised.
- Run `org.adaway.ui.update.UpdateRetryUiInstrumentedTest` (5 methods): retry states, busy states, expired authorization refetch action with no download, recreation after unconfirmed handoff, and late byte-progress rejection. Model actions are stubbed; the refetch test is wiring proof, not real network/signature validation.
- Rerun existing receiver, provider, continuation, current-version model, and download-status UI tests unchanged. The newly introduced model phase LiveData is application-scoped; these regressions matter across activity recreation and sequential tests.
- Exercise the real direct-build update flow on supported API 26/34 surfaces with a test-key trusted APK: digest/signer rejection, denied/granted permission, missing handler where reproducible, installer cancellation/failure, and successful upgrade. Separately use short-lived signed metadata and delay completion/permission return beyond expiry; require zero stale installer launches and actionable refetch. This lane makes no real-installer, real-signer, successful-install, or device-layout claim.

## Limits and Preserved Files

Initial completion verification remains synchronous; no latency/ANR improvement is claimed. Installer handoff is not durable across process death; recovery/persistence is out of scope. Wall-clock expiry uses the existing trust policy, not a new trusted-time source. Retries can re-download bytes. No localization beyond new default fallback strings was attempted. No dependency or beta/protection changes were made.

Protected files observed unchanged from this lane's pre-edit hashes:

| Path | SHA256 |
| --- | --- |
| app/src/main/java/org/adaway/model/update/Manifest.java | 1ad1bb649745e4301234cf80e55f67a05e0ca2003c066f2f3a7f33ad48170a35 |
| app/src/androidTest/java/org/adaway/model/update/ApkInstallContinuationInstrumentedTest.java | 11dde235cacff365bc15cabf1aad4e59aff91da79538039d5edf1e9f54fc6422 |
| app/src/androidTest/java/org/adaway/ui/update/UpdateActivityDownloadStatusInstrumentedTest.java | 38a737cff048666bb13f057569876acd04a679bfc079870308b7eaf4406a5dab |
| app/src/androidTest/java/org/adaway/model/update/UpdateModelNetworkFailureInstrumentedTest.java | a702734b23b908ef23bc7a6fddf4105ea657e19bb21255ed5091b3c7feefda97 |

ManifestTest.java was not edited; observed handoff SHA256 is `5dd925ff1a2a7624cb1f09b0d57879b75007628131f07bc6cd1c61a164756bf9`. Exact hashes and per-file test limits for all ten changed/added code/resource/test files are in `updater-followup-files.csv`; scoped finding status is in `updater-followup-bugs.csv`. Base ledgers were intentionally not updated.

Correction captured for this lane: view-binding property names are not necessarily resource IDs. Read the real layout before writing UI test IDs, and guard IDs/resources locally before declaring compile-ready. This lesson stays in the requested sidecar rather than changing the shared task ledger.
