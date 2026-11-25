# Post-beta update screen review

**POST-BETA-UPDATE-001 (P2):** Original lane analysis below; current fix status is
in the [bug register](bugs.csv), with [integrated Android evidence](validation.md#post-beta-review).
Source trace confirms both the incorrect verdict and the missing recovery action.

## Entry and recovery

- Application creation constructs one process-local UpdateModel: the manifest
  has never emitted; the install state already contains IDLE. Synchronizing the
  daily work preference schedules or cancels periodic work, not a guaranteed
  entry-time check.
- A fresh HomeActivity requests an APK check only if its startup preference is
  enabled and there is no saved instance state. HomeViewModel queues that work;
  the version link itself only opens UpdateActivity. Home's sources-refresh
  control does not check the app manifest.
- Separately, the daily APK worker checks the manifest and posts a notification
  for a newer release. The notification targets UpdateActivity with NEW_TASK
  and CLEAR_TASK, no manifest extra. The worker's in-memory result does not
  accompany the intent into a new process.
- UpdateActivity observes the manifest and progress. Its ViewModel can reattach
  to a known download, and resume can continue a pending install; neither is a
  manifest check. Previously, the only manual refresh path was behind the
  update button. With no result, IDLE hid it; the manifest observer never
  replaced the XML verdict. A passive check failure emitted null, which fixed
  the header but still left no action. Merely waiting or rotating need not help.
- Now the screen starts with the update title. An eligible install gets
  **Check updates** without turning either automatic preference on. A passive
  failure offers **Check again**. Explicit checking hides the action and ends
  in an actionable failure or a fresh version verdict. Without direct-update
  eligibility the idle screen says a check is unavailable, with no dead button.
  A fresh newer manifest still offers installation; a fresh current manifest
  shows its changelog and no install action.

## Scope and evidence

Full current reads of both stale model classes, activity, layout, and the
listed Android fixtures are pinned by SHA-256 in the file-review CSV. The
download poller still uses the original start time and owned row ID; a terminal
poll is a hint, not an install result. No changes to workers, schedule settings,
installer handling or distribution configuration. Passive checks keep their
existing asynchronous publication; this change does not serialize independent
Home/worker requests or persist manifests across processes.

Offline JDK 21 javac compiled the three changed production and three changed
test sources using Android 36 and existing debug classes/dependencies, writing
only temporary class output. XML parse checked the initial header and hidden
controls, retry anchor and default Activity string references. This was not an
APK build, device reproduction or instrumentation pass. No upstream service,
Gradle, git, emulator or global audit CSV was touched by this lane.

## Tests to run

From the repository root, on a disposable connected Android device (the
fixtures reset app preferences and work schedules), run the new regression:

```sh
./gradlew --offline :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class='org.adaway.model.update.UpdateModelNetworkFailureInstrumentedTest#coldNotificationEntryCanCheckAndRetryUntilCurrent'
```

The test uses only the fixture's HTTPS loopback server and signed test
response. It substitutes a newly constructed model with genuinely uninitialized
LiveData; it does not approximate process death by publishing null. Latches
wait for the actual model outcomes, not elapsed sleeps.

Adjacent regression classes (add the rest of the network fixture when running
the group):

```sh
./gradlew --offline :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.update.UpdateModelNetworkFailureInstrumentedTest,org.adaway.ui.update.UpdateActivityDownloadStatusInstrumentedTest,org.adaway.ui.update.UpdateRetryUiInstrumentedTest,org.adaway.ui.update.UpdateStatusLayoutInstrumentedTest,org.adaway.ui.update.UpdateProgressTrackingInstrumentedTest
```
