# Native Systems Review - 2026-11-25

## Scope and Disposition

Read-only review on dirty branch `codex/full-codebase-audit`; project guidance
`CLAUDE.md` and `tasks/audit-2026-11-25/files.csv` read first. The only files
created by this lane are this report, `native-files.csv`, and `native-bugs.csv`.
No product edits, Gradle, ADB, native compilation, commits, pushes, packet capture,
root commands, network listener, or real process-kill command were performed.

**Two P2 current-source integration findings**, neither fixed here:
- NAT-001: unavailable root capture still has a live recording control that reports false success.
- NAT-002: unavailable native features still synchronously invoke shell cleanup on the UI thread.

The requested first-party native webserver does **not exist in this checkout**.
The inventory contains zero `webserver/` entries, the directory is absent, and
there are no first-party C/C++ sources outside the retained `tcpdump/` tree.
`CLAUDE.md`'s mongoose/webserver module description is historical, not current
runtime evidence. There is consequently no owned HTTP accept loop, TLS context,
C request handler, resource-serving routine, JNI entry point, or native server
shutdown implementation here to audit. Do not substitute a historical implementation
and call it current review.

`native-files.csv` records 34 finite files individually, with exact SHA-256,
assessment scope and bug IDs. Thirteen are within `tcpdump/`: build/wiring,
an empty stub, and five one-line version metadata definitions. No packet
dissector or libpcap implementation is claimed reviewed. The 11 additional
context-only files below are deliberately excluded from the importable CSV.

## Build and Security Boundary

1. `settings.gradle:29` includes only `:app` and `:sentrystub`.
   `app/build.gradle:201-254` has no tcpdump/webserver dependency and no
   external native build. The catalog's OpenSSL alias does not itself package OpenSSL.
2. `WebServerUtils.java:56-62,80-82` unconditionally refuses startup because
   availability is false. Even the branch after that guard merely logs.
   `TcpdumpUtils.java:48,66-81,149-152` hard-disables capture, status and log reads.
3. `BootReceiver` delegates to `BootRestoreController`; ROOT exits at
   controller lines 46-48. App/model construction can create `RootModel`,
   but its native initialization is stop-only on the disk executor (79-84,160-162).
4. Root/log settings activities are non-exported in the manifest (106-109,124-127).
   No native service or broadcast receiver bypass was found. A public Java helper
   is not an independently remotely callable endpoint.
5. `.gitattributes` excludes retained tcpdump/webserver/history assets from
   Git source archives. Read-only `git check-attr export-ignore --` on
   `tcpdump/jni/tcpdump/tcpdump.c`, `Resources/test-webserver.sh`,
   `webserver/jni/webserver.c`, and the historical localhost key returned
   `set` for all four. Rules matching an absent path do not imply that path exists.
6. `git ls-files webserver tcpdump/libs app/src/main/jniLibs app/src/main/assets`
   returned no paths. This is tracked-file evidence, not a universal dependency
   or filesystem secret scan.
7. Actual retained APKs were inspected read-only by ZIP entry listing and hashed.
   No APK was rebuilt or installed. Artifact evidence is bounded to those bytes:

| Artifact | SHA-256 | UTC mtime | ZIP-entry observation |
| --- | --- | --- | --- |
| `app/build/outputs/apk/directRelease/app-directRelease.apk` | `95f5d0dfc7b445d55772bfcda18a14e2bc2d5a031242ac05c323654c41498203` | 2026-10-14 20:12:25 | 871 entries; no lib/ entries; no tcpdump/webserver/mongoose or matching historical credential names. |
| `app/build/outputs/apk/debug/app-debug.apk` | `49694499e52eeed126fe105ce2b5c452e71e0c13f7da6bdd2bb0c289ebb0244c` | 2026-11-25 09:37:52 | 1119 entries; eight Sentry libraries across four ABIs; no tcpdump/webserver/mongoose or matching historical credential names. |

Both output metadata files identify version 13.5.4b (130504). The older direct
release does not verify today's dirty tree; neither filename nor version proves
source equivalence. Sentry is third-party native code and is not audited here.
ZIP-name inspection is not an arbitrary-content/key scan or binary reverse engineering.

**Reachability conclusion:** no shipped webserver network surface or bundled
tcpdump packet-parser surface was demonstrated in the inspected source/build
configuration or APKs. This is not a certification of the retained vendor tree,
all native dependencies, old installed versions, or already-running legacy daemons.

## Owned Entry Points and Lifecycle

| Entry/control | Current path and result | Coverage limit |
| --- | --- | --- |
| Webserver enable | PrefsRootFragment 173-194 rejects unavailable change; start helper also rejects. | No listener launched; unavailable behavior traced, not device-run. |
| Webserver test | Click guard 200-205 returns; state guard 241-243 avoids HTTPS. | Dormant getWebServerState still probes https://localhost/internal-test if called directly; no ordinary unguarded caller found. |
| Webserver icon | Disabled/off in 212-235. Listener 97-107 may check/stop by process name on a preference change, but start still refuses. | External preference edits could invoke shell work; cannot enable the missing server. |
| Root settings creation | 58-71 calls configureWebServerAvailability; 217 always calls stopWebServer. | NAT-002: synchronous shell even with no executable present. |
| Root initialization | RootModel schedules checkApplied and syncPreferences on diskIO; sync stops webserver only. | Existing background cleanup is not a main-thread defect. Old-daemon upgrade/multiuser behavior untested. |
| Boot | BootReceiver -> BootRestoreController; ROOT returns. | No native boot start. |
| Root recording status | RootModel -> TcpdumpUtils; false && process-check short-circuits. | No status shell spawned by this path. |
| Root record action | Activity -> ViewModel -> RootModel -> startTcpdump -> stop/truncate -> false. | NAT-001 and NAT-002; feature flag guards launch, not preparatory work. |
| Root stop/clear | stop delegates killall; clear truncates app-cache dns_log.txt if it exists. | Real shell/cache operations not executed by this audit. |
| Log refresh | getLogs returns empty before file reads while disabled. | No runtime DNS-output parsing in this build. |
| System tcpdump check | checkSystemTcpdump can run fixed tcpdump --version; no main-source caller found. | Does not start capture and is not a fallback launch path. |
| Shared native runner | runBundledExecutable builds LD_LIBRARY_PATH + escaped native path + raw fixed parameters + '&'. | Only production caller is behind false capture flag; unsafe to treat its return as readiness if restored. |
| Manual support script | Resources/test-webserver.sh invokes missing legacy arm64 executable/resources. | Export-ignored manual script, not an app entry point; not executed. |

## NAT-001 - False Root Recording State (P2)

**Evidence and reproduction:** `LogActivity.java:93-95` changes only the empty
message; its FAB remains active and is always bound at 110. The observer at
111-115 displays Pause for true. `LogViewModel.java:95-98` computes
`!adBlockModel.isRecordingLogs()`, invokes the void setter, then posts the requested
boolean without checking success. `RootModel.java:123-134` forwards to the
disabled helper and discards its false start result. The backend status is always
false; therefore both a first Record tap and a subsequent apparent Pause tap
request start=true.

**Executed isolated probe:** exact five production lines 95-99 were extracted,
unchanged, into an in-memory JShell class. The model double always returned false
and counted requested starts; the postValue double recorded its argument. Output:

```text
tap=1 requested=true backend=false posted=true starts=1
tap=2 requested=true backend=false posted=true starts=2
```

The probe proves the production method's state logic with an inert backend. It
does not exercise Android lifecycle, LiveData scheduling, rendering, libsu,
native code or real files. Source inspection establishes that the ROOT backend
has the same always-false status. A device screenshot or full app reproduction
is not claimed.

Reproduce the host excerpt without writing source files:

```ruby
source = File.readlines("app/src/main/java/org/adaway/ui/log/LogViewModel.java")[94..98].join
abort "Unexpected source" unless source.include?("public void toggleRecording()")
program = <<~JAVA
class NativeStateProbe {
  static class Model {
    boolean lastRequested; int starts;
    boolean isRecordingLogs() { return false; }
    void setRecordingLogs(boolean v) { lastRequested=v; if(v) starts++; }
  }
  static class Posted {
    Boolean value=false;
    void postValue(boolean v) { value=v; }
  }
  final Model adBlockModel=new Model();
  final Posted recording=new Posted();
  #{source}
  void run() {
    for(int i=1;i<=2;i++) {
      toggleRecording();
      System.out.println("tap="+i+" requested="+adBlockModel.lastRequested
        +" backend="+adBlockModel.isRecordingLogs()+" posted="+recording.value
        +" starts="+adBlockModel.starts);
    }
  }
}
new NativeStateProbe().run();
/exit
JAVA
IO.popen(["/opt/homebrew/opt/openjdk@21/bin/jshell",
          "--execution", "local", "--feedback", "concise"], "r+") do |io|
  io.write(program); io.close_write; puts io.read
end
abort "Probe failed" unless $?.success?
```

Probe setup attempts using system `java` failed because no default runtime was
registered; direct source-file launch from `/dev/stdin` failed with Illegal seek.
The successful probe used the discovered OpenJDK21 JShell path above. These
failures were harness setup failures, not app failures.

**Minimal fix:** preserve capture disablement. Disable or hide the FAB in the
already-known unavailable mode and independently guard `toggleRecording()`.
For supported backends publish actual status after completion, not assumed success.
Do not restore the obsolete native module to make the UI appear functional.

**Tests to add:** extend `LogRootExpectationInstrumentedTest` beyond text:
assert recording control is disabled/absent, repeated taps/programmatic invocation
cannot publish true, recreation preserves unavailable state, and unsupported
mode never invokes capture cleanup. Retain VPN record/pause behavior and add a
supported-backend failed-start case. No test files were changed or run.

**Falsification:** the existing explanatory text might have made the control
unavailable, but neither layout nor activity disables it. Source plus the
two-tap host output refutes that interpretation. Main-thread LiveData rendering
remains an unexecuted platform step, not a source uncertainty.

## NAT-002 - UI-Thread Native Cleanup (P2)

**Evidence:** `PrefsRootFragment.onCreatePreferences:67` calls
`configureWebServerAvailability`. Its unavailable branch calls
`stopWebServer:217` before applying disabled UI state. That helper calls
`ShellUtils.killBundledExecutable:61-62`, which invokes
`Shell.cmd(...).exec()` inline, not submit/background execution. The normal
fragment lifecycle caller waits for shell completion. This does not require a
native library to exist. The still-live Record button follows an analogous path
through `TcpdumpUtils.startTcpdump:76-81`: stop occurs before the false flag check.

**Impact and bounds:** root authorization, shell initialization or a busy shell
can delay navigation/input even though the feature cannot be enabled. This
synchronous dependency is source-confirmed. No Android stall duration, root-manager
dialog, ANR, denial failure, or host-shell equivalent was measured; do not label
this runtime-reproduced or claim it always freezes. A warm immediately available
shell can make the defect invisible. RootModel's existing diskIO stop is evidence
against falsely claiming every stop runs on the main thread.

**Minimal fix:** render unavailable state immediately. Remove redundant UI
cleanup or move required migration cleanup to a bounded background operation;
RootModel already has a background cleanup site. Preserve intentional removal
of old daemons without requiring root work to construct preferences. Close the
root record entry point via NAT-001 and keep any necessary tcpdump retirement
cleanup off the main thread.

**Tests to add:** fake a delayed/denied shell in unavailable-root settings and
assert the main loop remains responsive while any required cleanup eventually
executes. Verify repeated screen entry does not launch unbounded duplicate
cleanup. Existing webserver instrumentation verifies disabled values and no
browser launch, not shell thread ownership. No delay/device test executed.

## Dormant Reactivation Risks (Not Current Exploit Findings)

These observations are deliberately outside the active bug CSV. Reaching the
old capture implementation would require build inclusion plus changing the
false gate; the missing webserver would require restoring an implementation.

- `ShellUtils:44-58` uses substring/regex `ps | grep` rather than owned process
  identity and returns shell success after '&' rather than executable readiness.
  `killall` uses a process name rather than an owned PID. Reintroduction needs
  exact identity, startup/exit acknowledgement, and lifecycle cleanup.
- The dormant tcpdump command captures `udp dst port 53` on any interface,
  writes text indefinitely to private cache, and includes no `-Z` drop-user
  argument. Excerpts in vendor `tcpdump.c` initialize username/chroot to NULL;
  default WITH_USER/WITH_CHROOT are undefined in config.h, and droproot is
  conditional. A root-shell launch would therefore require deliberate privilege
  separation before decoding network-controlled packets. This is a conditional
  source inference, not a current capture exploit or a full main() review.
- Snap length is 512 bytes (not the helper comment's 512 bits). A DNS-only BPF
  expression narrows traffic but does not validate network bytes or prove the
  many linked parsers safe. DNS names/compression, truncation, IPv4/IPv6,
  fragmentation and capture length boundaries remain unreviewed.
- The module's Copy task guesses debug/release from all requested task text,
  uses an internal AGP task class, and renames executables into library output.
  Reactivation needs isolated multi-variant/ABI packaging tests and actual
  executable type/entry-point verification, not reliance on stub existence.
- Provenance is inconsistent: tcpdump VERSION is 4.7.4 but compiled version.c
  says 4.5.1; libpcap VERSION is 1.7.4 while version.c/version.h say 1.5.2.
  The Android makefiles compile these version definitions directly. Do not
  assign CVEs, patch coverage or an exact upstream release from one label.
  Establish pinned source provenance and generate consistent version metadata.
- The dormant HTTPS state helper considers any successful localhost response
  healthy and any SSL handshake error evidence of an uninstalled certificate.
  That is not authenticated daemon identity. Its normal UI caller is gated off,
  so this is a future health-check requirement, not a current exposed server bug.
- Removed legacy native code is not recreated by this report. A replacement
  server would need a fresh bind-address, privilege, TLS key/trust, parser,
  resource traversal, timeout, descriptor ownership and shutdown review.

## Finite Reviewed Files

The following 34 files are the complete importable list; exact hashes and
individual assessments are in `native-files.csv`. Inclusion means the stated
file-level source review, not device testing or whole-dependency review.

- `.gitattributes`
- `settings.gradle`
- `build.gradle`
- `app/build.gradle`
- `gradle/libs.versions.toml`
- `app/src/main/java/org/adaway/util/WebServerUtils.java`
- `app/src/main/java/org/adaway/model/root/TcpdumpUtils.java`
- `app/src/main/java/org/adaway/model/root/ShellUtils.java`
- `app/src/main/java/org/adaway/broadcast/BootReceiver.java`
- `app/src/main/java/org/adaway/broadcast/BootRestoreController.java`
- `app/src/main/java/org/adaway/ui/prefs/PrefsRootFragment.java`
- `app/src/main/java/org/adaway/ui/log/LogActivity.java`
- `app/src/main/java/org/adaway/ui/log/LogViewModel.java`
- `app/src/main/java/org/adaway/model/adblocking/AdBlockModel.java`
- `app/src/main/res/layout/log_activity.xml`
- `app/src/main/res/xml/preferences_root.xml`
- `app/src/main/res/values/strings_log.xml`
- `app/src/main/res/values/strings_prefs_root.xml`
- `app/src/androidTest/java/org/adaway/ui/log/LogRootExpectationInstrumentedTest.java`
- `app/src/androidTest/java/org/adaway/ui/prefs/PrefsRootWebServerUnavailableInstrumentedTest.java`
- `Resources/test-webserver.sh`
- `tcpdump/build.gradle`
- `tcpdump/jni/Android.mk`
- `tcpdump/jni/Application.mk`
- `tcpdump/jni/stub/Android.mk`
- `tcpdump/jni/stub/stub.c`
- `tcpdump/jni/libpcap/Android.mk`
- `tcpdump/jni/tcpdump/Android.mk`
- `tcpdump/src/main/AndroidManifest.xml`
- `tcpdump/jni/tcpdump/VERSION`
- `tcpdump/jni/tcpdump/version.c`
- `tcpdump/jni/libpcap/VERSION`
- `tcpdump/jni/libpcap/version.c`
- `tcpdump/jni/libpcap/version.h`

## Scoped Context Only - Do Not Mark Fully Reviewed

The following files provided caller/configuration evidence only and are NOT
in `native-files.csv`. Their hashes identify the exact snapshot used. Some
were read end to end to find callers; that is not a claim to have completed
their unrelated subsystems or vendor logic.

| Path | SHA-256 | Scope |
| --- | --- | --- |
| `app/src/main/java/org/adaway/model/root/RootModel.java` | `6fee6518c7c627a39408b8fdd3ab35807c0e2c32605e31fac5a990ebcd3e0f8e` | Full text read; native recording delegation 123-144 and background webserver stop 79-84/160-162 traced. Hosts export/DAO/remount behavior not independently audited by this lane. |
| `app/src/main/java/org/adaway/AdAwayApplication.java` | `2e8cbee570ddc2772f24ba45deaa82f0924e1d7e3e7ecbd349f664f88eb17142` | Full text read for model construction/startup 95-101; no direct native starts. Other application initialization remains outside this review. |
| `app/src/main/java/org/adaway/ui/prefs/PrefsActivity.java` | `1feb6cb61261802e482e1b38053ec5f9e9a4e39a90f9683f0749fa23297b28cf` | Full text read for root-fragment navigation 84-110; other settings/navigation behavior not independently audited. |
| `app/src/main/java/org/adaway/ui/prefs/PrefsMainFragment.java` | `d937b90a2db7b6624781137e650748c56d53aaa086ce887888e11eef425b645e` | Full text read for active-root settings navigation 73-118 and DNS-log launch 152-159; other preferences outside this lane. |
| `app/src/main/res/xml/preferences_main.xml` | `6e2ad4b09c993ddccbc95d61f872f533941a358a789fad4a4f5417c54caf357d` | Full text read to confirm root preference and DNS-log navigation; other resource behavior outside this lane. |
| `app/src/main/AndroidManifest.xml` | `b917d420e6f46f83e8320113ca2052494be6ac536ecafb50287855df38942140` | Full text read for no native components and internal log/settings activities; broader export/permission semantics outside this lane. |
| `app/src/main/java/org/adaway/helper/PreferenceHelper.java` | `4080e4e3702ee5de4c174a7e3bd394dc621c7d6059c4097bb9477b85594dafa8` | Only native-related preference accessors 191-212 and adjoining redirection/model selection context inspected; rest not reviewed. |
| `app/src/test/java/org/adaway/security/SecurityHardeningTest.java` | `87cf16ca37aad5fd28c718601e585bddd68aa445c7717db51c4a6027cb6fe6c0` | Only native boundary tests approximately 112-221 inspected; source-text guards do not prove compiled APK reachability. Remaining tests not reviewed or run. |
| `tcpdump/jni/tcpdump/tcpdump.c` | `d5c549dd066f5c842239db1a88677fcb5f76ce1fdb69254bd4c1072685c7fdf3` | Selected privilege/capture/version excerpts only: 1004-1034,1500-1550,1727-1737,1815-1850,1988-2010 plus search hits. NOT full main/lifecycle/vendor implementation review. |
| `tcpdump/jni/tcpdump/config.h` | `88bbd6cdf25b27086b44c1bb640567c3de221c99ee9a4a1713737fa27243c466` | Selected WITH_USER/WITH_CHROOT context 268-294 and feature search hits only; remains pending full review. |
| `tcpdump/jni/libpcap/config.h` | `221d470e44f28fc4e30c798b819e4e2d8a20d4a10c0a1b17ed30ce4a440160cc` | Keyword search hits only; no full configuration or platform review. |

## Pending Coverage and Handoff

Report validation completed: native-bugs.csv exactly matches the central
11-column schema and has two source-confirmed rows; native-files.csv has 34
unique paths with nonempty assessments and current SHA-256 hashes. All 11
context-only hashes also still match. Bug references, ASCII content and trailing
whitespace checks pass. The embedded Ruby/JShell reproduction was rerun directly
from this report and produced the same two false-success states. These checks
validate report consistency and the isolated excerpt, not Android execution.

- Inventory snapshot: 562 tcpdump-tree files, including 254 under libpcap and
  302 under tcpdump vendor directories. This lane fully assessed 13 of those
  files and leaves **549 outside its completed file list**. Two legacy CleanSpec
  files have another reviewer's records; this lane does not inherit that review.
  All substantive vendor packet/capture implementations remain unreviewed here.
- No full review of tcpdump main(), packet printers, libpcap Linux capture,
  BPF compiler/interpreter, generated scanner/grammar, savefile readers,
  platform-specific files, tests, build generators, or third-party Sentry/pcap4j/
  libsu native/transitive internals is claimed. Selected tcpdump.c excerpts
  remain explicitly partial, not a reviewed vendor entry.
- Remaining vendor work should be tracked file by file using exact hashes and
  reachability prioritization if reactivation is contemplated. No sampling-to-tree
  promotion is justified by a version label, a successful build, or dormant status.
- Native webserver implementation coverage is unavailable because the files are
  absent, not because they passed review. Reviewing a restored tree is new work.
- Outstanding executed-platform coverage: root settings and log record/pause,
  cold/denied/busy root shell, old native daemon upgrade/cleanup, multiuser process
  ownership, and packaged release identity. No Gradle/ADB permission was assumed.
- This lane does not import its files into the central inventory or merge its
  bug rows. The main audit owner can ingest only the finite CSV rows after
  validating hashes; it must not import context-only entries as complete.
- No finding is marked fixed. NAT-001's host excerpt proof and NAT-002's static
  call trace are separate evidence levels. Proposed minimal fixes and tests
  above are a handoff, not implemented product changes.
