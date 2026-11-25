# Source Editor Draft Patch Review

Date: 2026-11-25. Read-only review of the current `SourceEditActivity.java` draft-restoration diff and four added `SourceEditAddEditInstrumentedTest.java` tests. Supporting layout/menu, test helpers, and the locally resolved Material 1.12.0 bytecode were inspected. No product/test edits, Gradle, adb, device execution, or ledger/bug additions.

## Finding

### P1 - Enable Material buttons before assigning the initially loaded source format

**Source-confirmed, including dependency bytecode; runtime reproduction pending.**

- Location: [SourceEditActivity.java:202](/Users/steves/Steves_Files/Work/Research_and_Papers/AdAway/app/src/main/java/org/adaway/ui/source/SourceEditActivity.java:202), lines 202-204, together with the newly added disabling at lines 252-255.
- On the first edit load, `renderFormState()` runs with `formInitialized == false` and disables both format buttons and both type buttons. The database callback then calls `applyInitialValues()` while they are still disabled; only the following `initializeForm()` enables them.
- The app pins Material 1.12.0 (`app/build.gradle:218`). Inspection of its resolved runtime JAR with `javap -c -p` confirms that `MaterialButton.setChecked()` returns without changing state when `isEnabled()` is false (bytecode offsets 7-11 branch to return). `MaterialButtonToggleGroup` also delegates child checked-state changes to that method.
- Consequently, loading an existing allow-list source cannot execute the checked-state changes at lines 276-277. The XML default block button stays checked and the allow button stays unchecked. `validate()` reads the latter at lines 651-653, so an otherwise label-only save can change the persisted source from allow-list to block-list, clearing/reinterpreting its downloaded data via the existing content-change path. Rotation can preserve this already-wrong format as the new draft.
- File sources have a related initial-load inconsistency: the toggle group's internal checked ID can become FILE while its disabled child buttons retain the URL checked appearance. The file draft test only checks the child after recreation, when draft restoration occurs before disabling and can repair that mismatch.
- Reproduction to execute: seed an existing URL source with `allowEnabled=true`; open the editor and wait for its label; verify Allow is checked before making edits; change only the label and save; verify `allowEnabled` remains true. Repeat with recreation before the first load completes. Also assert FILE child checked state immediately after first loading a content-URI source, before recreation.
- Minimal fix direction: enable the Material buttons before assigning loaded checked states, while keeping application listeners unbound or restoration-guarded until assignments finish. Merely moving the current `initializeForm()` before `applyInitialValues()` would bind type listeners too early and risks clearing the URL/opening the picker.
- Regression addition: an existing-allow-list first-load/label-save case, plus pre-recreation FILE checked-state assertions. The four new tests seed block-list format and do not cover initial allow-list hydration.

## Remaining Assessment

- No additional introduced draft-loss defect or inherently invalid assertion was identified in the bounded review. Restoring the explicit draft before listener binding, suppressing listener side effects during framework restoration, and skipping database value application after `formInitialized` are consistent with preserving a newer draft.
- `delayedReloadAfterRecreationPreservesUrlAndNewerDraftEdits`: the serial disk barrier plus main-thread idle drain orders the late callback before final assertions. It tests URL/label draft retention and no implicit save, but starts from a block-list source.
- `recreationBeforeFirstLoadStillLoadsSource`: correctly distinguishes uninitialized form state from an initialized empty draft. Its URL/block fixture does not expose the disabled-button issue.
- `fileDraftAndRedirectFormatSurviveRecreationWithoutPicker`: useful text/type/redirect restoration checks. It supplies a synthetic URI directly, so it is not SAF grant/result-delivery coverage; it also needs the initial-load checked-state assertion described above.
- `addDraftOverridesIntentPrefillAfterRecreation`: the add-draft precedence and no-insert assertions match the intended behavior; its database check runs on the instrumentation thread, not inside `onActivity`.
- The separately logged save/delete completion-ownership defect `AUD-EDIT-002` was deliberately excluded and is not claimed fixed.

## Evidence Boundary

No connected test or visual workflow was executed by this reviewer. Dependency bytecode inspection is static evidence, not a reproduced UI failure. Main owns integrated build/runtime verification.

Reviewed snapshot SHA256:

| File | SHA256 |
| --- | --- |
| `app/src/main/java/org/adaway/ui/source/SourceEditActivity.java` | `1773ebf358893b760177b224c9a71f90f39b74b67adbda2297633e33e00e3f6e` |
| `app/src/androidTest/java/org/adaway/ui/source/SourceEditAddEditInstrumentedTest.java` | `ff969a23bd44c16ee5c8095a7bce8b970fadf319a7a12313a70f119108cae245` |
| Resolved `material-1.12.0-runtime.jar` | `49295bcdcb7da32ef70c5ee7a16562d5f7285f17f84ec0a09ad2d43957e9469c` |

Only this report was created for this review.
