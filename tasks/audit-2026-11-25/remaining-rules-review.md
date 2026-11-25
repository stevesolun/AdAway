# Remaining Rules Review

## Boundary and Evidence

- Lane: `remaining-rules`; branch: `codex/full-codebase-audit`; HEAD at handoff: `d51eb7aea06871c2c6ad2bbedb9ddbaaaca48d77`.
- Read `CLAUDE.md` and `tasks/lessons.md` first. Shared dirty worktree preserved.
- Fully read all 19 assigned Java files and 13 associated XML files. Every counted file has its own current SHA256 and assessment in `remaining-rules-files.csv`.
- Seven new findings: six P2, one P3. All are **source-confirmed**, not Android/runtime reproductions, and none is fixed by this lane.
- Checked central `bugs.csv` and the other bug sidecars for existing IDs and overlapping scoped-file findings before assigning `RR-001` through `RR-007`.
- Only the three new `remaining-rules-*` sidecars were written. No production/test edits, central ledger updates, Gradle, emulator, adb, privileged root work, commit, or push.

## Findings by Severity

### RR-001 - P2 - Confirmed downloaded-redirect override does nothing

**Evidence:** `ui/lists/type/ListsAdapter.java:83-95` enables the checkbox and changes its visual state before calling `onToggleListItem`. `ui/lists/type/AbstractListFragment.java:278-298` offers every downloaded rule the same override confirmation. Its Yes callback calls `ListsViewModel.moveListItem`, but `ui/lists/ListsViewModel.java:140-148` immediately returns for REDIRECTED. The context menu also leaves Move visible for redirects (`AbstractListFragment.java:132-136`).

**Repro, not run:** With an enabled downloaded redirect for `redirect.example`, open Redirected, turn its checkbox off, and confirm Yes. The checkbox remains off, but no DAO mutation, user override, model-change event, or refresh occurs. Long-press Move is also a no-op. This is not dependent on a root apply.

**Expected:** Confirming the offered override creates an effective user exception, or the UI does not offer an unsupported command and restores the checkbox.

**Likely fix:** Define explicit redirect override semantics, route the confirmed action to that mutation, and hide/disable unsupported Move variants. Drive final checkbox state from mutation completion.

**Smallest regression:** Seed one downloaded redirect; exercise the actual checkbox and Yes button; assert the intended user override and row state. Also assert that the context action is either supported or absent.

### RR-002 - P2 - Canceling a downloaded-rule toggle can invert the next mutation

**Evidence:** The adapter optimistically changes the checkbox (`ListsAdapter.java:85-95`). The confirmation only restores on the negative button (`AbstractListFragment.java:290-294`); it has no cancel handler. The next checked=true path calls `toggleItemEnabled` (`AbstractListFragment.java:295-297`), which negates the entity's enabled value instead of assigning the requested value (`ListsViewModel.java:84-89`).

**Repro, not run:** Start with an enabled downloaded block and no concurrent reload. Turn the row off, dismiss the confirmation with Back, then click the row to turn its still-off checkbox on. After cancellation the checkbox is false but the entity is still true; the next click sets the checkbox true and persists entity.enabled=false. The user's apparent enable action therefore disables the downloaded row.

**Expected:** All cancellation paths restore authoritative state, and a checked=true request cannot persist false.

**Likely fix:** Revert pending visuals on every cancellation/dismissal without a committed mutation, and use an explicit desired-enabled operation rather than a second toggle of potentially stale state.

**Smallest regression:** One downloaded block, actual off click, Back cancellation, actual on click; assert no cancel-time write and final checkbox/entity/DAO agreement. Include outside-touch cancellation and a normal No-button control.

### RR-003 - P2 - Domain Checker announces success before writes and has no failure completion

**Evidence:** `ui/domainchecker/DomainCheckerFragment.java:125-127,137-146,152-162` shows deleted/allowed/blocked success immediately after enqueueing work. `DomainCheckerViewModel.java:120-139,146-153,161-177,184-200` sets loading and performs writes/sync without any catch/finally or error result; its loading=false and refreshed result are reached only on success. Reads at lines 69-112 have the same missing failure completion. The fragment's loading observer only disables Check (lines 77-78).

**Repro, not run:** Hold the disk executor, check an unblocked fixture, then tap Block it. The UI announces success before the insert has run. Release the task with its first DAO write throwing a storage/database exception. No successful write or new check result exists, yet success was already announced and this ViewModel has no terminal failure/reset path. Process-level exception behavior was not tested and is not asserted here.

**Expected:** Success follows completed mutation/synchronization; failure gives actionable feedback and permits retry without claiming a completed protection change.

**Likely fix:** Publish a lifecycle-owned operation result from the ViewModel, restore loading in failure-safe completion, and show success only for a successful result. Disable mutation controls while their command is pending and preserve the last result as explicitly stale on failure.

**Smallest regression:** Hold one mutation and assert no success snackbar before completion; inject a DAO failure and assert error feedback plus restored controls and unchanged data; retry successfully. Add one read-failure reset case.

### RR-004 - P2 - URL credentials are interpreted as the hostname to check and mutate

**Evidence:** `ui/domainchecker/DomainNormalizer.java:38-63` strips the scheme/path then truncates at the first colon, without parsing URL authority or user-info. Its remaining validation at lines 66-80 accepts the resulting ordinary hostname. Checker mutation callbacks use `result.domain`, so the wrong parsed target also becomes the write target (`DomainCheckerFragment.java:143-145,159-161`).

**Repro, not run:** Enter `https://account.example:secret@target.example/path`. After scheme/path removal, the first-colon truncation produces `account.example`, which passes the ASCII hostname checks. The checker queries that name and Block it/Add to Allow List acts on that name, not `target.example`. A domain-shaped username is the required condition.

**Expected:** A supported full URL resolves to its authority hostname, or credential-bearing URLs are explicitly rejected; credentials must never become a rule target.

**Likely fix:** Parse http/https URLs structurally, then normalize the extracted hostname; explicitly reject unsupported authority forms. Keep bare-host/path input handling separate.

**Smallest regression:** Pure normalizer case for the URL above expecting `target.example` or explicit rejection; include no-credentials URL, `target.example:8080/path`, mixed case, and IDN controls. Assert the checker never writes `account.example` for that input.

### RR-005 - P2 - Retained tabs show one search string while filtering on another

**Evidence:** All list fragments share the Activity ViewModel (`AbstractListFragment.java:185-188`). Every fragment's text watcher changes that shared filter (lines 193-200), but each editor only copies the current query once on creation (lines 204-208). No query observer updates other live editors. All three Paging streams use the shared filter (`ListsViewModel.java:53-64`).

**Repro, not run:** Keep the adjacent Blocked and Allowed views alive. Search `alpha` in Blocked, switch to Allowed and search `beta`, then return to Blocked. Its existing editor still says `alpha`, but its Paging source now uses the global `beta` filter. Fixture rows for both strings make the wrong scope visible. No list database mutation is required.

**Expected:** The displayed query describes the rows, whether search is global or per-tab.

**Likely fix:** Either observe a single shared query into every live editor with a loop guard, or make filters genuinely tab-specific. Ensure view-state restoration cannot publish an obsolete editor value back over the chosen owner.

**Smallest regression:** Two retained adjacent fragments, alpha/beta fixture rows, and the navigation sequence above; assert both query text and displayed rows agree before and after recreation.

### RR-006 - P2 - Background mutation refresh can overwrite a newer search or source filter

**Evidence:** Add/edit/move tasks call `refresh()` from diskIO (`ListsViewModel.java:103-108,124-129,152-156,169-181`). `refresh()` snapshots the entire filter at lines 186-188 and `setFilter()` posts that snapshot from background threads at lines 222-227. User search/source-toggle calls publish newer snapshots synchronously on main (lines 191-210). There is no generation check when the background snapshot is eventually delivered.

**Repro, not run:** Set search to `alpha`; pause a completing add/edit task after `refresh()` reads the old filter. On main, enter `beta` or toggle source visibility. Resume the task and drain its posted value. The old alpha/source-selection snapshot is republished and recreates all three Paging streams, undoing the newer user filter. This is an explicit interleaving, not a measured incidence claim.

**Expected:** Completing a rule write refreshes data without changing newer search text or source visibility.

**Likely fix:** Remove redundant filter replacement if Room invalidation suffices, or dispatch the refresh to main before reading the current filter. Do not enqueue an unversioned background copy of user-owned filter state.

**Smallest regression:** Controlled executor/main queue with the exact interleaving above; assert the latest query and sourcesIncluded survive completion. Include a same-filter refresh control.

### RR-007 - P3 - Bulk allow entry accepts a wildcard that the write silently discards

**Evidence:** `util/RegexUtils.java:125-132` validates a wildcard after deleting or replacing its wildcard characters, without checking raw pattern length. `AllowedHostsFragment.java:87-101` therefore enables Add for such a line and lines 66-78 dismiss and submit it. `ListsViewModel.java:93-96` silently returns for a raw host longer than 253 characters. The edit path at lines 121-129 does not apply that same limit.

**Repro, not run:** In the multiline Allowed add dialog, enter `example.com` followed by 244 asterisks (255 raw characters), plus a second line `control.example`. Removing wildcards leaves valid `example.com`, so the whole dialog passes validation. Add closes it; the first call returns without insertion and only the control line is submitted. No failure or skipped-line count is surfaced. The long pattern is deliberately redundant but accepted by the offered validator.

**Expected:** Every accepted line is submitted successfully or rejected visibly before dismissal; add and edit use one rule-length policy.

**Likely fix:** Share validation/normalization at dialog and write boundaries, optionally collapse repeated asterisks before enforcing a documented pattern limit, and report rejected lines instead of silent returns.

**Smallest regression:** Two-line batch above; assert either validation prevents submission with a line-specific error or both canonicalized rules persist. Include the same input in Edit and a normal short wildcard control.

## Coverage and Limits

- Lists: all Activity/pager/filter/callback/ViewModel files and all six type files read fully, including row identity, placeholder clearing, refresh error/retry state, add/edit/delete/move paths, and multiline loops. There is no separate bulk-delete command in this scope.
- Domain checker: all four files read fully, including normalization, user mutations, result rendering, and operation ownership. DAO matching/uniqueness, SourceModel synchronization, and runtime precedence implementations are outside this lane; no new finding depends on assuming their unreviewed implementation.
- Dialog/utilities: both dialogs and both utilities read fully. No additional proven caller-level bug is filed for Unicode key normalization, wildcard matching engine behavior, or redirect-address classification without their external contracts/runtime evidence.
- Paging generation recollection after recreation, append/prepend failures, duplicate-edit collisions, mutation/import contention, and IDN persistence are unexecuted integration boundaries, not extra findings or passes.
- Associated XML: eight layouts, three menus, and two values files, totaling 13. The file ledger is authoritative. Shared strings were read in full but assessed only for this lane's bindings/copy; no other screen review is claimed.
- Resource filename enumeration, bug-ledger searches, and repository status checks are discovery/context only and contribute no production coverage. No tests were read or run by this lane.

## Handoff Checks

- `remaining-rules-bugs.csv` uses the central 11-column schema unchanged.
- `remaining-rules-files.csv` uses `path,sha256,assessment,bug_ids`; 19 Java plus 13 XML rows, all fully read.
- Sidecar validation passed: exact headers, row widths, unique/noncentral bug IDs, referenced IDs, exact in-scope Java coverage, and all 32 SHA256 values against the shared worktree. No reviewed-file hash drift was found. This is artifact validation only, not product testing.
- Main owns any reproduction, implementation, regression execution, central merge, and integration decision. Suggested tests above are proposed, not authored or passed.
