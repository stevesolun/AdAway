# Settings Fixes - Integration Freeze Plan

Status: design/read/test planning only; awaiting explicit GO. No production or test files have been edited by this lane since approval. This new sidecar is the only write during the freeze.

## Ownership

- Approved: backup package; PrefsBackupRestoreFragment; PrefsMainFragment; PrefsVpnFragment; focused tests; narrow backup strings.
- Preserve the shared worktree. No DAO/schema/shared-controller changes, Gradle, ADB, emulator, commit/push, or central-ledger edits.
- Re-read touched files and current shared integration APIs after GO before applying patches.

## Implementation Decisions

### REM-SET-001 - Coordinated Restore Completion

- Read and fully validate the document before mutation. Production Context/URI restoration obtains the application's SourceOperationCoordinator lease, then uses SourceModel.runSourceMutation to exclude direct source mutations/updates too.
- Reconcile sources and user rules in one AppDatabase transaction. Hold both operation guards through syncHostEntries and application VPN-cache invalidation. Report success only after maintenance finishes; never call AdBlockModel.apply, root write paths, or VPN start.
- Surface busy rejection with a specific retry instruction rather than a generic success/failure message. Do not silently queue a stale restore behind an unrelated operation.
- Ensure lease closure on validation/mutation/maintenance failure. Keep invalidation safe if a post-commit runtime rebuild fails; do not claim the committed database transaction was rolled back in that case.
- Keep the database-only test entry point for isolated Room transaction tests; all actual UI/platform restore entry points use coordinated completion.

### REM-SET-002 - Shared Conservative Backup Validation

- Retain 200 sources, 50,000 aggregate user rules, and 16 Mi decoded UTF-16 characters. Do not raise input caps.
- Share count and row validation between importer and exporter. Validate generated JSON and its actual serialized character length before opening the destination stream in truncate mode.
- Invalid/unrestorable exports fail explicitly without silently omitting rows. Existing file contents remain untouched when preflight fails.
- Preserve existing UTF-8, null-stream, security-error, and stream-closing behavior. Retain or deliberately adapt existing test references to the reader's limit constant.

### REM-SET-003 - Stable-ID Source Reconciliation

- Match sources by exact URL inside the existing database transaction. Insert absent URLs; update present rows in place.
- Restore label, enabled selection, and allow-format permission. Redirect trust stays false, including for previously trusted existing sources.
- Label/selection-only changes preserve IDs, HTTP validators, downloaded rows, and unrelated directory metadata. Propagate selection to source rows with the existing DAO methods and refresh source statistics.
- Changed allow/redirect format clears source rows, source staging rows, HTTP dates/ETag, counters, skipped count, and stale download errors using existing editor behavior. Invalidate the existing root-export materialized cache in the database only; no privileged file write.
- Protect the reserved user-source identity from malicious source entries now that existing rows can be updated. Validate before writes, and keep malformed-later-row/transaction-failure rollback guarantees.
- Do not add downloads or scheduling changes to the restore path; new/format-changed subscriptions remain explicitly out of date for the normal update flow.

### REM-SET-004 - Post-Persistence Live VPN Reconfiguration

- Use lifecycle-scoped preference-change observation so the restart reads the newly persisted value, not the old value from an OnPreferenceChange callback.
- Bind IPv6 in the main preferences and watchdog in VPN preferences. Reuse existing VpnServiceControls.restart; guard selected method and current active VPN state. Do not use VpnStatus.isStarted alone because it includes STOPPING.
- Recheck state at dispatch and avoid delayed restart callbacks surviving fragment destruction. Stopped/root modes do not start a service or ask for consent. Preserve existing bypass/exclusion behavior while avoiding duplicate listener restarts.
- Controller limitation: restart currently sends START, not a conditional reconfigure command. This lane will not change shared service/controller APIs. Any stronger cross-command STOP/START ordering guarantee requires coordination with that owner rather than a new settings-only assumption.

## Focused Regression Plan

| Area | Planned evidence |
| --- | --- |
| Restore runtime | Public Context/URI import with the same warmed application VpnModel: ALLOWED to BLOCKED and inverse; no manual Apply; verify stopped service remains stopped. |
| Restore ownership | Hold the shared lease and separately the SourceModel mutation guard; restore rejects without data changes; retry succeeds after release; maintenance owns the lease through completion; malformed and failing mutations release ownership. |
| Restore reconciliation | Existing same-URL disabled/enabled restore preserves ID and row identity; label-only retains validators; allow-format and redirect-trust changes clear stale source/stage rows and validators; new URL insertion; reserved user-source rejection; rollback on a later failing row. |
| Export limits | 200/201 sources; 50,000/50,001 aggregate rules across arrays; exact/over serialized character ceiling; invalid generated row; destination-open counter or unchanged sentinel content proves preflight occurs before truncation; normal small round trip. |
| Live settings | Actual IPv6 and monitor switches cause a replacement worker/tunnel reading the new state; exercise both directions and recreation; stopped/root controls do not dispatch a service start; no persistence-only success assertion. |
| Existing coverage | Preserve backup validation/security, atomicity, SAF round trip, and live bypass regressions. Compile against Android's Java API surface when doing standalone checks. |

Tests will be authored only after GO. Standalone checks may then run without Gradle; main owns consolidated compilation, lint, connected tests, prepared VPN consent, and actual platform reconfiguration proof. No tests are claimed executed by this plan.

## Handoff Artifacts After GO

- New settings-fixes-review.md with changes, exact cases, verification results, and remaining limits.
- New settings-fixes-files.csv with current hashes for fully read changed/verified files only.
- New settings-fixes-bugs.csv using the central 11-column schema and the existing four REM-SET IDs; status distinguishes implementation/standalone evidence from unrun Android gates.
