# AUD-EDIT-002: Retained Editor Mutations

Date: 2026-11-25
Branch: `codex/full-codebase-audit`
Status: implemented, source-reviewed, awaiting main's build and instrumentation run.

## Scope and Preservation

Only `SourceEditActivity.java`, `SourceEditAddEditInstrumentedTest.java`, the new
editor-local `SourceEditViewModel.java`, and these new `editor-save-fixes-*` artifacts
were edited by this assignment. No base audit ledger edits, commits, or pushes.

Read `CLAUDE.md`, `CONTRIBUTING.md`, `tasks/lessons.md`, the complete starting
Activity/test files, `editor-patch-review.md`, and the AUD-EDIT-002 ledger entry.
No applicable AGENTS.md was found in the repository or its ancestors.

The existing saved-draft restoration and Material enable-before-hydration edits
are preserved. Source comparison against the assignment's starting snapshot
confirmed all pre-existing test methods/helpers are byte-for-byte unchanged.
It also confirmed unchanged draft serialization, initial-value/draft loading,
Material hydration ordering, form listeners, validation, and SAF result handling.
The restoration callback now reapplies retained mutation state after framework
restoration, and form enablement additionally respects that retained state.

## Ownership and Code Paths

- `app/src/main/java/org/adaway/ui/source/SourceEditActivity.java:122`:
  obtains an Activity-scoped ViewModel through ViewModelProvider before restoring
  the form; observes status with the current Activity as lifecycle owner.
- `app/src/main/java/org/adaway/ui/source/SourceEditViewModel.java:34`:
  retains IDLE, PENDING, SUCCEEDED, duplicate-URL, busy-update, and save-failed
  states in LiveData. Both entry points synchronously claim PENDING on the UI
  thread before queuing disk work. PENDING and SUCCEEDED reject further work.
- `app/src/main/java/org/adaway/ui/source/SourceEditViewModel.java:72` and
  `:150`: save/delete capture the source ID and submitted values, not an
  Activity, its binding, or its mutable edited-row field. The application owns
  database access, profile marking, schedule cleanup, and update enqueueing.
- `app/src/main/java/org/adaway/ui/source/SourceEditViewModel.java:189`:
  publishes the terminal state on the main thread, regardless of whether an
  Activity is currently started. Errors remain available to a later owner and
  permit retry; success stays terminal until the editor finishes.
- `app/src/main/java/org/adaway/ui/source/SourceEditActivity.java:247`,
  `:348`, and `:358`: form controls, menu enablement, and direct action guards
  consult the same retained state. Delete confirmation still precedes mutation.
- `app/src/main/java/org/adaway/ui/source/SourceEditActivity.java:406`:
  renders current state and closes the current editor on success; finishing or
  destroyed Activities cannot handle it. Rendering does not repeat database,
  schedule, download, or profile effects. Reapplying status after view-state
  restoration prevents an older saved error/enablement state from winning.
  IDLE does not clear independently restored validation errors.

Success is durable state, not a consumable event that can be lost between
Activities. Re-observation is safe: it only closes a current non-finishing
editor, while the mutation remains blocked and its side effects run once.

The existing mutation worker bodies were compared against the starting source:
transaction logic, duplicate detection, in-place row updates, rule/cache cleanup,
runtime synchronization, error mapping, schedules, profile marking, and download
enqueueing are unchanged except for owner/context/ID substitutions.
No generalized operation abstraction or new dependency was introduced.

## Focused Regression Tests

Added five tests in `SourceEditAddEditInstrumentedTest`:

1. `pendingAddSurvivesRecreationAndRejectsRepeatedApply` (line 267):
   holds disk work; submits add; repeats Apply before/after recreation; checks
   retained ViewModel identity, disabled form/menu, preserved draft, rejection of
   another direct ViewModel save, persisted add, and closure of the new editor.
2. `pendingLabelSaveClosesRecreatedEditorAndPreservesAllowRules` (line 303):
   recreates during a label-only save of an Allow source; asserts selected Allow
   while controls are disabled, stable ID/format/rules, and current-editor closure.
3. `pendingDeleteClosesRecreatedEditorWithoutResubmission` (line 343):
   confirms deletion, recreates while queued, attempts Apply/Delete and another
   direct delete, then asserts current-editor closure, missing source/rules,
   and terminal success rather than a duplicate-operation failure.
4. `duplicateSaveErrorSurvivesStoppedOwnerAndRecreationThenRetries` (line 374):
   completes a duplicate save while the recreated owner is stopped; checks the
   retained error on resume and again after recreation, unlocked controls,
   unchanged rows/draft, and a successful corrected retry.
5. `failedDeleteSurvivesRecreationAndAllowsConfirmedRetry` (line 421):
   uses a test-only SQLite abort trigger to fail deletion deterministically;
   checks retained failure, unchanged source/rules, unlocked controls, and a
   successful confirmed retry after removing the trigger. Cleanup drops it.

The companion CSV records these cases and the existing draft/Material
regressions that must remain in main's combined run.

## Verification Boundary

- PASS: scoped `git diff --check`.
- PASS: source comparison preserving all pre-existing test bodies/helpers.
- PASS: source comparison preserving draft/hydration/validation blocks.
- PASS: source comparison of relocated save/delete worker bodies.
- Source-reviewed: pending claim before dispatch, lifecycle-aware observation,
  stopped-owner terminal retention, blocked repeat submissions, retry enablement,
  application-only worker ownership, and retained terminal success.
- NOT RUN: Gradle, compilation, unit tests, instrumentation, adb, or emulator.
  All added Android tests are unexecuted; runtime outcomes are not claimed.
- ViewModel lifetime covers configuration recreation within the same process.
  This does not add process-death persistence or a durable background work queue.
- Main owns combined build/runtime verification and any base-ledger status change.

## Source-Reviewed SHA256

These hashes identify the reviewed final source, not compiled/tested artifacts.

| File | SHA256 |
| --- | --- |
| `app/src/main/java/org/adaway/ui/source/SourceEditActivity.java` | `6f4379e0e244cb22b0490c70adb50293d08abdc09f5cce996fcb15db4980e357` |
| `app/src/main/java/org/adaway/ui/source/SourceEditViewModel.java` | `7de99b82a4aa24328eb4cfa11ccac71a89d91ae351c2882d31b71e82b0d94997` |
| `app/src/androidTest/java/org/adaway/ui/source/SourceEditAddEditInstrumentedTest.java` | `3c19c9afe3269536cf1248640924724d84aad0659af5b434fb18d7c895bda6e6` |

Assignment starting hashes (already contained main's draft/Material changes):

- Activity: `d3a1bbd8812be62926e2b515cc2ec9a2a2c0794ddf317b5b4a2ee6a1b1827346`
- Instrumentation test: `17a5e1dcdfb2f664af9b16d63ae452fe962db292b1964c2fd2cb66dbd71a70ed`
