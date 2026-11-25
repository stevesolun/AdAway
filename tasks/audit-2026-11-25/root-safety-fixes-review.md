# Root Safety Fixes: ROOT-AUD-001 and ROOT-AUD-002

## Scope and Freeze

Implemented only the two findings in `main-followup-review.md`. Read `CLAUDE.md`
and preserved the existing staged phase-one changes. No staging, commit, Gradle,
adb, root shell, remount, or system-hosts modification by this lane. Production,
resources, and tests are frozen for main's integrated build. Home geometry and
all other agents' files are untouched. The shared task ledgers are not edited.

## ROOT-AUD-001: Owned Output Cleanup

The only RootModel edit is `HostsFileWriter.close()`: the owned buffered output
is now a try-with-resources resource around `flushBuffer()`. A write failure
remains the primary exception, the output is still closed, and a distinct close
failure is suppressed. Existing export, apply, copy, and remount logic is unchanged.

The regression constructs the actual private writer reflectively, not a copy of
its implementation or a RootModel instance. Exactly 1 MiB of ASCII fills its
outer buffer without an earlier write, forcing the failing write during close.
This matters: a smaller buffer can defer failure until BufferedOutputStream.close
and miss the original leak.

## ROOT-AUD-002: Internal Read-only Snapshot

`Open hosts file` now opens a narrow internal DialogFragment. The old external
file-URI launch, activity-result remount callback, and read-write remount are
removed entirely. There is no provider, manifest change, filesystem grant,
external editor, write-back command, or added dependency.

- The preference and dialog explicitly say: "Read-only snapshot. The system
  hosts file is not changed." This intentionally removes direct external editing;
  it does not replace it with a concealed write-back feature.
- The fixed system-hosts path is read using an ordinary unprivileged FileInputStream
  on the existing disk executor, after checking that it resolves to a regular file.
  There is no root fallback. Permission denial or unreadability gives a clear
  in-dialog error rather than a launch exception or elevated operation.
- A private in-memory snapshot is capped at 64 KiB plus one lookahead byte, read
  in at most 8 KiB chunks. Only the first 64 KiB are decoded for display; a larger
  file has an explicit truncation notice. The preview is not an export or a live
  file handle. Nothing is written to disk or restored into saved text state.
- The input is owned and closed on success, read failure, cancellation, and
  zero-progress failure. The TextView is selectable but not editable.
- Main-thread delivery checks a generation token and existing dialog/content.
  Stop invalidates the token and cancels queued/in-progress work; destroying the
  view clears references. Restart reads a fresh snapshot. Duplicate opens and
  transactions after state save are guarded at the preference entry point.
- IOException, SecurityException, and executor rejection have visible failure
  handling. No external-app availability is needed, so no URI launch failure can
  leave a partition remounted by this action.

## Executed Host Checks

Direct JDK 21 `javac -proc:none --release 17` compilation and JUnit 4.13.2 execution
passed **16 tests**. The compile used the current complete RootModel.java,
HostsFileSnapshot.java, and three JVM test classes, with cached Android/dependency
jars and existing main-built app classes as dependencies. Compilation outputs
were confined to an automatically removed temporary directory. This was not a
Gradle build, Android Java API-image compilation, or a new APK compilation.

Executed classes and method counts:

- `org.adaway.model.root.HostsFileWriterCloseTest`: 5, all passed. Full-buffer
  flush failure closes the output; full-buffer failure retains suppressed close
  failure; partial-buffer failure closes; close-only failure propagates; success
  flushes all bytes and closes.
- `org.adaway.ui.prefs.HostsFileSnapshotTest`: 7, all passed. UTF-8 and empty input,
  exact limit, oversized read bound/truncation, read-plus-close failure,
  zero-progress failure, interrupted read ownership.
- `org.adaway.ui.prefs.HostsFileViewerContractTest`: 4, all passed. Internal launch
  without file URI/remount; no grant/system-write path; background/error/lifecycle
  guards; parsed XML read-only layout and snapshot/truncation copy. These four
  checks inspect source/XML, not Android runtime behavior.

For negative proof, `git show :app/src/main/java/org/adaway/model/root/RootModel.java`
was compiled in a separate temporary directory against the same writer test.
Exactly the two full-buffer tests failed, with missing close/suppressed failure
counts (`expected 1, was 0`); the other three passed. The staged baseline was not
modified. The current source then has the 16-pass result above.

Host invocation used the following entry point after the direct compile:

```text
java -cp <temporary-classes>:<cached-dependencies-and-main-built-classes> org.junit.runner.JUnitCore
  org.adaway.model.root.HostsFileWriterCloseTest
  org.adaway.ui.prefs.HostsFileSnapshotTest
  org.adaway.ui.prefs.HostsFileViewerContractTest
```

Scoped tracked-file `git diff --check` passed. The final file CSV records full
untruncated reads of the final bytes of all ten code/resource/test files, with
SHA-256 computed from the same read bytes. These are source-review claims only
unless the execution above explicitly says otherwise. No claim is made that
reading all of RootModel proves its privileged runtime paths.

## Precisely Not Run Here

The following new fixture-only Android tests are **source-reviewed, not compiled
or executed by this lane**:

```text
org.adaway.ui.prefs.HostsFileViewerInstrumentedTest#truncatedSnapshotIsReadOnlyAndLoadedOffMainThread
org.adaway.ui.prefs.HostsFileViewerInstrumentedTest#readAndPermissionErrorsStayInTheReadOnlyDialog
org.adaway.ui.prefs.HostsFileViewerInstrumentedTest#dismissedDialogIgnoresLateReadResult
org.adaway.ui.prefs.HostsFileViewerInstrumentedTest#stoppedDialogDropsOldResultAndReloadsOnStart
```

They use an overridden reader with in-memory bytes/failures/latches, never a root
shell or system hosts file. They cover selectable/noneditable bounded output,
background loading, read/security errors, late completion after dismissal, and
stop/start freshness. They do not establish configuration-change/process-death
restoration or actual device file access.

Also not run: any Gradle JVM suite, app/resource compilation, lint, APK packaging,
connected test suite, actual preference navigation smoke, portrait/landscape or
large-font dialog screenshots, configuration-change/process-death test, unreadable
real-device hosts test, or API26/API34 device comparison. Main owns integration
and UI checks. Privileged root apply/copy/remount proof is explicitly excluded by
the user and is not a required handoff gate for this lane.

## Changed Paths

```text
app/src/main/java/org/adaway/model/root/RootModel.java
app/src/main/java/org/adaway/ui/prefs/PrefsRootFragment.java
app/src/main/java/org/adaway/ui/prefs/HostsFileSnapshot.java
app/src/main/java/org/adaway/ui/prefs/HostsFileViewerDialog.java
app/src/main/res/layout/hosts_file_viewer.xml
app/src/main/res/values/strings_hosts_viewer.xml
app/src/test/java/org/adaway/model/root/HostsFileWriterCloseTest.java
app/src/test/java/org/adaway/ui/prefs/HostsFileSnapshotTest.java
app/src/test/java/org/adaway/ui/prefs/HostsFileViewerContractTest.java
app/src/androidTest/java/org/adaway/ui/prefs/HostsFileViewerInstrumentedTest.java
tasks/audit-2026-11-25/root-safety-fixes-review.md
tasks/audit-2026-11-25/root-safety-fixes-files.csv
tasks/audit-2026-11-25/root-safety-fixes-bugs.csv
```

At the freeze checkpoint the original index blobs still match the initial
staged diff: RootModel `4438d7404320aab8628676d9236728527fc4de25` and
PrefsRootFragment `e6fea2180b3e24dac5daae1082fffd38eaad1484`. The unstaged
RootModel diff contains only the close fix; the unstaged PrefsRootFragment diff
contains only removal/replacement of the hosts opener and its imports/field.
New code/resources/tests remain untracked for main to integrate. Sidecars do not
hash themselves, avoiding recursive/self-referential evidence.
