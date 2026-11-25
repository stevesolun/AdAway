# Native Feature Fixes - NAT-001 / NAT-002

## Ownership and Plan

- [x] Read CLAUDE.md, CONTRIBUTING.md, tasks/lessons.md and native audit findings.
- [x] Confirm six production target files were clean before editing.
- [x] Leave dirty Home, VPN, Application and other owners' files untouched.
- [x] Guard disabled capture before shell/cache work; isolate legacy cleanup on
  RootModel's existing background executor; keep disabled webserver queries inert.
- [x] Disable root Record and independently guard the ViewModel; publish backend
  state after supported toggles, preserving synchronous VPN recording behavior.
- [x] Add and execute focused host fixtures plus passive/source checks.
- [x] Record exact changed paths/hashes, results and remaining platform limits.

The attempted ownership notification via desktop thread messaging was rejected
because the destination is this task's native ancestor. No Home edit was needed:
its sources do not consume the recording toggle. All selected production files
were clean immediately before the patch. No central ledger or shared task file
is edited by this lane.

## Disposition

NAT-001 and NAT-002 are **implemented and host/source verified**, not Android
runtime verified. Baseline HEAD is `d51eb7aea06871c2c6ad2bbedb9ddbaaaca48d77` on
`codex/full-codebase-audit`. No Gradle, emulator, ADB, root shell, native launch,
real process cleanup, listener, packet capture, commit or push was performed.
No C, vendor or native-module source was changed.

### NAT-001: Recording Truth

- `app/src/main/java/org/adaway/ui/log/LogActivity.java:110` disables the root
  recording FAB, does not attach its click listener, and reuses the existing
  unavailable explanation as its accessibility description. Its initial record
  icon remains unchanged. The existing root empty-state explanation is retained.
- `app/src/main/java/org/adaway/ui/log/LogViewModel.java:49` initializes unavailable
  recording to false without querying the backend. `:96` independently rejects
  programmatic root toggles and posts false. Supported methods now publish the
  actual post-operation backend state, including rejected starts and stops.
- VPN production code was not edited. Its exact synchronous recording getter and
  setter were exercised with the new ViewModel method excerpts. This does not
  establish VPN service, DNS packet, or Android lifecycle correctness.

### NAT-002: Disabled Paths and Legacy Cleanup

- `app/src/main/java/org/adaway/model/root/TcpdumpUtils.java:78` returns before
  stop/truncation/file access when disabled. `:112` makes disabled stop inert.
  Capture remains hard-disabled, and disabled status/log reads remain false/empty.
- `app/src/main/java/org/adaway/util/WebServerUtils.java:70` makes unavailable
  stop inert; `:88` avoids process queries; `:102` returns unavailable without
  constructing a network client or probing localhost. Availability stays false.
- `app/src/main/java/org/adaway/ui/prefs/PrefsRootFragment.java:99` guards external
  icon preference callbacks. `:212` renders disabled/off controls without cleanup.
  Existing enable/test/state availability guards remain intact.
- `app/src/main/java/org/adaway/model/root/RootModel.java:84` reuses its existing
  disk-executor initialization task. `:160` invokes explicit worker-only legacy
  webserver/tcpdump cleanup. This preserves webserver migration cleanup and moves
  tcpdump cleanup out of user recording actions. It runs once per RootModel
  construction, not once per application lifetime. No retry, timeout or process
  identity redesign is claimed; the pre-existing name-based kill helper remains.

## Executed Evidence

Run from the repository root:

```sh
ruby scripts/tests/native-feature-fixes.Tests.rb
```

Final result: **OK (17 tests)** using OpenJDK 21 `javac --release 17`, cached
JUnit 4.13.2 and Hamcrest 1.3. No dependency download or Gradle invocation occurs.
The runner uses a temporary directory and deletes it afterward. It compiles both
complete production wrapper classes with deliberately throwing shell, network,
native-launch and cache-access doubles. RootModel hosts operations are not run.

| Suite | Count | Evidence |
| --- | --- | --- |
| NativeFeatureAvailabilityTest | 4 | Actual wrappers: repeated disabled start/stop, false status, empty logs and inert webserver controls/state. |
| NativeRecordingHostTest | 4 | Exact production initialization/availability/toggle excerpts: root repetition and fixture reconstruction, exact VPN accessors, rejected start and rejected stop. |
| NativePreferencesHostTest | 3 | Exact configure/listener/state excerpts with preference doubles: repeated configuration/reconstruction, icon callback, no scheduled health probe. |
| NativeFeatureUiContractTest | 4 | Source guards for disabled FAB/listener/accessibility, ViewModel guards/truth, disabled settings and background cleanup wiring. |
| NativeCleanupHostTest | 1 | Exact RootModel cleanup method plus real wrapper cleanup methods on a host executor: blocked cleanup does not stop disabled controls; released cleanup visits webserver then tcpdump. |
| NativeSourceSyntaxHostTest | 1 | JDK parser accepts all six complete changed production Java sources; syntax only, not Android symbol/type checking. |

The two checked-in Java test classes are normal `app/src/test` sources. The other
host suites are generated by the Ruby runner from production excerpts; no test
copy substitutes for the actual recording/preference method bodies. The host
LiveData double delivers immediately, and preference doubles do not implement
Android persistence callbacks. Reconstruction is a host fixture reconstruction,
not ActivityScenario recreation or process-death proof.

Regression sensitivity command:

```sh
ruby scripts/tests/native-feature-fixes.Tests.rb --baseline
```

This compiles production wrapper/excerpt bytes from HEAD without changing the
checkout. Result: **11 tests, 8 expected failures, exit 1**. Failures expose three
disabled cleanup paths, optimistic rejected start/stop state, unguarded root
backend access, settings icon process query and settings construction cleanup.
The unchanged VPN accessor round-trip, empty disabled logs and unavailable health
state excerpt pass. Current-only wiring/syntax/legacy fixture cases are omitted
from baseline mode. Final `git diff --check` passed.

## Handoff and Limits

- Exact changed implementation/test/runner hashes are in
  `native-feature-fixes-files.csv`; scoped bug disposition is in
  `native-feature-fixes-bugs.csv`; commands/results/limits are in
  `native-feature-fixes-tests.csv`. None replace or edit the original audit files.
- Shared context only: `app/src/main/java/org/adaway/model/vpn/VpnModel.java` had
  SHA-256 `4e5f908a15f4495c6219b49a58d1f4175f97d25f2530129ad7de26afe63551f7` at
  final fixture execution. Only its recording accessors were used; this lane does
  not claim review/ownership of that dirty file or import it as fully reviewed.
- Main owns the integrated Gradle compile/test and emulator gate. Suggested focused
  JVM selections are `org.adaway.model.root.NativeFeatureAvailabilityTest` and
  `org.adaway.ui.log.NativeFeatureUiContractTest`; also run the existing
  `LogRootExpectationInstrumentedTest` and
  `PrefsRootWebServerUnavailableInstrumentedTest` if passive device UI is available.
- Android LiveData scheduling, actual disabled-FAB rendering/accessibility,
  persistence callbacks, recreation and denied/busy-root main-loop responsiveness
  are not executed here. A real rooted old-install cleanup migration is also not
  verified. Existing supported root hosts apply/export/remount logic is untouched.
- All new Java tests use Android-compatible file APIs; successful host compilation
  does not replace Android compilation or full integration validation.

**Final handback:** implementation complete within the bounded native-feature
scope, ready for main's integrated compile gate; no active command session remains.
