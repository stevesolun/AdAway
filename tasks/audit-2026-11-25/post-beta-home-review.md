# Post-beta Home file review

**Result:** Two new regressions in the beta failed-download recovery path were
confirmed by source inspection and patched. Both are **implemented-awaiting-test**.
Only HomeFragment and its focused Android test changed outside this evidence set.

## Comparison

Read the two stale Home classes and the pending Home wrapper layout in full.
Compared the checkout at `29729ecd4` with `f7dbbb5e7`: the latter's HomeFragment
and HomeViewModel SHA256s match the actual prior reviews in
`home-status-fixes-files.csv` and `main-files.csv`. The shipped Java delta was
15 lines of failed-download dialog behavior and a five-line acknowledgement
method (`b61e97519`), not a rewrite of protection, counters, or progress.
Before this fix the fragment's SHA256 was
`80d84f17ffe3fb7f38b6707cd7c03a6cf45a9ce42276de0308281a0ae044472e`.

- **HomeFragment:** traced view setup/teardown, both worker snapshots, import
  phases, counter freeze/refresh, rule-aware protection state, navigation,
  settings and error actions. The two findings below are specific to the beta
  recovery path, not reopenings of the older status or progress findings.
- **HomeViewModel:** the added acknowledgement clears the matching current error
  on the main thread. Pending operations, bootstrap deferral, VPN authorization,
  shared lease acquisition/release and observer cleanup are unchanged. Keep the
  error on view teardown; no model change needed.
- **Home layout:** compared the pending layout with its earlier overlay and the
  HOME-AUD-005 record. The scroll viewport now takes the space remaining above
  the CTA; a hidden CTA does not reserve space. The include, binding IDs and
  click destination are intact. No shipped reference to the removed clearance.
  The recorded earlier geometry pass is historical, not execution here.
- **Supporting files:** read the dimension file, HostError mapping, default and
  Hebrew error strings, and both relevant tests. The recovery copy distinguishes
  a failed list from no connectivity. Concurrent root-error and busy-message
  changes in both string files were reread and left untouched. The CSV pins the reviewed bytes.
  Other pending test sources are not shipped Home UI and are not marked reviewed.

## New findings

1. **POST-BETA-HOME-001 (P2):** With a download failure displayed, HomeActivity's
   Discover intent replaces the Home fragment in the same activity. The window
   previously survived that replacement: Discover was covered and tapping the
   new Sources button could call `requireActivity()` on a detached fragment.
   Home now dismisses the recovery dialog on view teardown or error replacement.
   Dismissal is not cancellation; the user can act on the outstanding error on
   return. Only callbacks from the current dialog may acknowledge or navigate.
2. **POST-BETA-HOME-002 (P3):** A newer model status can arrive while the dialog is
   open. The beta acknowledgement publishes null; the old renderer hid the new
   status before checking for null. Clearing the error now leaves status text
   alone. Starting a new operation still clears the previous status, now in
   the pending observer, and non-null errors still hide the text.

## Verification

JDK 21 parser: HomeFragment, HomeViewModel, HostError and both test sources,
no syntax diagnostics. Parsed the four XML files, checked scroll/CTA structure
and all 18 default error keys. Neither parsing nor a hash check is an Android
compile, visual inspection, test pass or runtime reproduction.

Run later from the repository root with the project's JDK/SDK and a disposable
device (the existing fixtures reset test state):

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.ui.home.HomeUpdateSignalErrorInstrumentedTest,org.adaway.ui.home.HomeDiscoverDeepEntryInstrumentedTest
```

The first class includes both new regression cases and the existing Sources and
Help actions. The second checks the zero-source CTA geometry and navigation.
Remaining manual checks: Back/outside cancel, rotation with the prompt open,
and a real failed download followed by a background status change. No global
ledger import, staging, branch change, Gradle, adb or emulator action was taken.
