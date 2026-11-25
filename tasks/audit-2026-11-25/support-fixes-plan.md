# Support Fixes Plan

Date: 2026-11-25. Implementation authorized, then temporarily frozen by main.
No production or test file has been edited by this lane since authorization.
Only this planning sidecar is written during the freeze. Wait for explicit GO
before editing production/tests or launching any standalone compilation/tests.
No Gradle, emulator, adb, privileged deletion, commits or central-ledger changes.

## Owned Changes After GO

1. `app/src/main/AndroidManifest.xml`: add only the normal
   `android.permission.REQUEST_DELETE_PACKAGES` uses-permission, without a
   maxSdkVersion or privileged DELETE_PACKAGES. Preserve all existing declarations.
2. `app/src/main/java/org/adaway/ui/adware/AdwareFragment.java`: refresh the
   activity-owned model on resume, covering initial activation, scanner reopen,
   recreation and return from system uninstall/settings. Retain ACTION_DELETE
   with the detected package-name URI and system confirmation. Never remove an
   entry in response to a tap or cancellation. Clear view references when the
   fragment view is destroyed if introducing retained model/view lifecycle code.
3. `app/src/main/java/org/adaway/ui/adware/AdwareViewModel.java`: expose a narrow
   refresh method delegating to the retained LiveData. No Activity reference.
4. `app/src/main/java/org/adaway/ui/adware/AdwareLiveData.java`: keep package scans
   on packageScanIO; give each requested scan a generation. Post completion onto
   mainThread and compare generation immediately before setValue, not before
   postValue. A held old UI delivery or late old worker must not replace a newer
   request's data. Preserve the last result while refreshing, including unchanged
   results after cancellation. Keep matching/signatures and sorting unchanged.
   Use narrow package-private executor/scan injection for deterministic tests,
   avoiding new dependencies and global mutable test hooks.
5. `app/src/main/java/org/adaway/ui/log/LogAdapter.java`: bind accessible action
   text together with each listener, using the actual ListType and selected state.
   Selected types announce removal and invoke removeListItem; others announce the
   appropriate add action and invoke addListItem with the same type. Preserve
   existing tint, host click/copy behavior and callback interface.
6. `app/src/main/res/layout/log_entry.xml`: fix the swapped Allow/Redirect initial
   descriptions. `app/src/main/res/values/strings_log.xml`: add only the three
   type-specific removal descriptions; reuse existing translated add descriptions.
7. Add focused tests and a standalone host runner, then new support-fixes review,
   files, bugs and test-evidence sidecars. Do not edit LogActivity or LogViewModel.

## Existing Test Compatibility

`app/src/test/java/org/adaway/ui/domainchecker/DomainCheckerTest.java:164-175`
currently searches for the exact string `packageScanIO().execute(this::loadData)`.
Generation-capturing scheduling must change this syntax while retaining the
separate executor guarantee. A narrow assertion update needs coordination with
main because this existing test was not explicitly assigned to this lane. Do not
distort production scheduling or retain dead code just to satisfy that string.
No modification to this existing test has been made.

`AdwareUninstallIntentContractTest` checks the existing inline ACTION_DELETE and
package-name URI. Keeping that action is compatible with the normal permission
fix and preserves its cancellation contract. No intent extraction or switch to
privileged PackageManager deletion is needed.

## Host Regression Plan

- Permission/source contract: parse the main and variant XML; verify exactly the
  normal uninstall-request permission, no privileged delete declaration, no SDK
  cap/removal overlay, and the existing detected-package intent action. Existing
  merged manifests predate the change and must not be claimed as patched APK proof.
- Refresh scheduling: invoking refresh queues the scan instead of running it on
  the caller; publishing waits for the main executor. Use the existing test
  convention of ArchTaskExecutor delegation with cleanup after each test.
- Latest worker wins: release the newest captured request first, then the old one;
  the old result must not replace the new list.
- Latest request wins at delivery: queue an old worker's completion, request a new
  scan before its main callback runs, then release that callback; old data must
  not be published. This specifically rejects a guard checked only on the worker.
- Refresh/cancel: load one matching entry, refresh with the same installed-package
  snapshot, and verify the entry remains during and after the refresh. An empty
  subsequent snapshot must clear it only when that scan actually completes.
- Reopening/activation contract: verify resume delegates to the retained model's
  refresh rather than constructing a replacement model or clearing its results.
- XML/action mapping: initial Allow and Redirect descriptions agree with their
  bound types; removal strings exist and map to the corresponding selected action.
- Run the existing adware matcher/uninstall tests alongside the new bounded tests
  where their dependencies permit host execution. No broad existing-suite claim.

## Android Regression Plan (Main-Owned Execution)

- New adapter test inflates a real bound row with a recording LogViewCallback,
  then rebinds through null, ALLOWED, REDIRECTED and BLOCKED. For each state assert
  content descriptions and click callbacks, including removal and recycled-row
  transitions. No LogActivity/ViewModel or VPN/database mutation is necessary.
- Scanner lifecycle test uses controlled package snapshots while keeping the same
  activity-owned model across pause/resume and scanner reopen. Assert updated
  rows/empty state and unchanged rows after canceled uninstall.
- Installed-app gate verifies the merged normal permission and real row-to-system
  uninstall confirmation for a disposable fixture, followed by Cancel and a
  package-still-installed assertion. A fully intercepted intent is not equivalent
  to proving the system permission boundary. Never uninstall the instrumentation
  package while its test runner is executing, or any user's real application.
- Manual/controlled affirmative deletion and one TalkBack check remain explicit
  runtime gates, not implied by host success or Java compilation.

## Compile-Readiness Plan

Cached JUnit 4.13.2, Hamcrest, AndroidX lifecycle/arch-core and the Android 36 SDK
are available. Android's Java API image is also present under the existing cache.
Use a new temporary output directory, not app/build, for standalone javac checks
after GO. Android tests must be checked against the Android Java image and SDK,
not only a desktop --release class library. Distinguish any temporary resource or
view doubles from real Android compilation/runtime in the final evidence.

Final sidecars must retain SUP-AUD-001/002/003, separate source-confirmed baseline
from implemented/host-tested state, list exact executed methods and remaining
Android gates, and hash only files actually fully read in their final form.
