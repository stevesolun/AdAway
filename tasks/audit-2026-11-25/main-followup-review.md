# Main Integration Follow-up

This records current full-file reads, not whole-goal completion. The associated
CSV distinguishes review of source from execution. Earlier lane reports retain
their historical hashes and test limits.

## Additional Source Findings

### ROOT-AUD-001: Writer cleanup can leak its output after an I/O failure

`RootModel.HostsFileWriter.close()` calls `flushBuffer()` and then `output.close()`
without failure-safe cleanup. If flushing buffered hosts bytes throws, close is
never attempted on the owned output stream. This is a source-confirmed P3 resource
ownership issue, not a measured production leak. A throwing OutputStream fixture
can reproduce it without root, remounts, or a real disk-full condition. Fix by
always closing the output while retaining the original exception and suppressed
cleanup failures. No implementation or test execution yet.

### ROOT-AUD-002: Hosts editor sends a file URI to an external application

`PrefsRootFragment.openHostsFile()` constructs a `file:///system/etc/hosts`
ACTION_VIEW intent. The application targets API34; no file-URI exposure policy
override or content provider exists in the current main code. The method catches
only IOException and ActivityNotFoundException, not FileUriExposedException.
On a normal modern Android policy this can fail at launch. It can also leave a
successful read-write remount unreverted if launch throws before a result callback.
This is a source-confirmed P2 launch/cleanup finding; no privileged operation or
device reproduction was performed. Keep it separate from the disabled webserver
controls. Any repair must avoid opening broad filesystem access and must retain
failure-safe remount cleanup. Privileged root runtime remains outside user scope.

## Integration Evidence

- Large-selection fixture now has a private Room recorder without the global
  synchronous LiveData rule. Its final assertions verify no subscriptions and a
  retained USER row, because getAll intentionally excludes that row. Both 6000-ID
  real WorkManager cases passed on API34.
- CrashSurfaceHardeningTest's boot case now enforces notification routing, no
  direct Activity launch, and return before VPN start. Its old direct-launch
  requirement was the only failure in the preceding 665-case JVM run; the full
  rerun passed 665 cases with three skips and no failures.
- Nine notification cases passed with real app notification permission and VPN
  consent prepared on the disposable emulator. Both previously skipped cases ran.
- Four live VPN/DNS cases passed on current debug APK SHA256
  `a6442448dfb19af7421bd2e3970ada2eebb864e8463a03e30ca876276264de4f`.
  This proves actual tunnel lifecycle and UDP DNS, not every network handoff.
- Manual landscape checks: collapsed list visible, header controls reachable with
  downward swipes, 150% font row selectable, both selected commands enabled, and
  Subscribe selected opens the unsupported-list review with the resolved URL.
  Close canceled that dialog. No list was subscribed by this check. Default font
  and portrait were restored before the full connected run. Screenshots are local
  ignored evidence under app/build/reports/audit-2026-11-25.
- The full connected run had two failures out of 488 cases (ten skipped): a
  notification STOP callback cleanup race and truncated filter identity text.
  Both were fixed and all 22 targeted cases passed without skips on the saved
  phase-one APK. Exact hashes and limits are in validation.md.

Other fully read files recorded in the CSV include application model ownership,
onboarding consent/result flow, the complete Apply snackbar, RootModel writer and
copy/remount flow, disabled native UI ViewModels/preferences, and root support
types. A full read is not a claim of executed root, process-death, locale or all
UI workflow variants. The two additional root findings remain open.
