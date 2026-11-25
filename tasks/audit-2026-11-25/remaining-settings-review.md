# Remaining Settings Review - 2026-11-25

## Scope and Evidence

- Branch: `codex/full-codebase-audit`; existing dirty shared worktree preserved.
- Read `CLAUDE.md` and `tasks/lessons.md` first. Checked central `bugs.csv` and other bug sidecars for duplicate findings/IDs before writing these new sidecars.
- Fully read 30 owned files: 16 Java files and 14 associated XML files. `remaining-settings-files.csv` records their current SHA-256 values. No owned file had a worktree diff when fingerprinted.
- Review only: no production/test changes, Gradle, emulator, device interaction, privileged-root runtime, commits, pushes, or central-ledger edits. Findings below are **source-confirmed, not runtime-reproduced**. Proposed regressions have not been written or run.
- Narrow caller/callee and existing-test excerpts were consulted to validate the owned behavior. Those contextual files are not counted or fingerprinted as full-file reviews. `PrefsRootFragment` is excluded.

## Findings by Severity

### P2 - REM-SET-001: Successful restore leaves the running VPN's cached rules stale

**Owned evidence:** `BackupImporter.java:68-89,126-165` commits user-rule changes and reports success; its only post-write maintenance is `updateActiveRuleStats(USER_SOURCE_ID)`. Neither the public import path nor `PrefsBackupRestoreFragment.java:73-76` synchronizes runtime state or invalidates the application VPN cache.

**Supporting trace:** `VpnModel.java:84-90,129-150` caches even an allowed decision. `HostEntryDao.java:1759-1787` resolves current user rows on a cache miss. `HostsSourceDao.java:141-154` only updates source counters. `SourceModel.java:1673-1701` has an existing sync/invalidation path, but restore never calls it. This is a missing invalidation caller, not the already-tracked in-flight cache replacement race (`NOV-VPN-02`).

**Concrete repro, not run:** While VPN is active, resolve `restore-cache.example` once with no matching rule, warming its ALLOWED cache entry. Restore a valid small backup whose only rule blocks that exact host. Keep the same VPN model and avoid other apply/update operations. The DAO now resolves BLOCKED, but the existing model returns its cached ALLOWED entry; the success toast does not imply active enforcement. The inverse blocked-to-allowed change also stays stale until eviction or another invalidating operation.

**Likely fix:** After a successful import transaction, use an application-owned completion path to synchronize runtime metadata and invalidate cached VPN decisions before reporting success. Coordinate that maintenance with existing source-operation ownership; do not enable a stopped VPN or introduce privileged-root writes.

**Smallest regression:** A Room-backed import test using the same application `VpnModel` before and after the public URI import: warm ALLOWED, import BLOCKED, await completion, and assert both DAO and model return BLOCKED without manual Apply. Add the inverse direction and a malformed-import control.

### P2 - REM-SET-002: Export reports success for backups the importer always rejects

**Owned evidence:** `BackupExporter.java:89-98,114-129,134-147` serializes every source and user rule without a count/serialized-size check. `BackupImporter.java:51-55,109-119,126-135` rejects more than 200 sources, more than 50,000 aggregate user rules, or more than 16 Mi characters. `AppBackupAgent.java:58-61,68-73` uses the same incompatible exporter/importer pair.

**Concrete repro, not run:** Populate 201 distinct valid HTTPS subscriptions and no user rules, then export through Backup. Export writes a complete JSON document and takes the success branch. Select that exact file in Restore on a clean fixture: the source-count guard throws before any mutation. This needs neither an oversized malicious input nor a provider error. A 50,001-user-rule fixture hits the same mismatch.

**Likely fix:** Share an explicit export/import capacity policy. At minimum, reject an unrestorable export before opening/truncating its destination and report that it did not create a usable backup. Prefer a bounded format/import strategy that round-trips supported large installations without removing hostile-input limits or silently truncating data.

**Smallest regression:** Export/import boundary fixtures with 200 and 201 sources; assert every successful export is restorable, or that over-limit export fails before opening the output stream. Cover the analogous 50,000/50,001 rule boundary and serialized-character ceiling. The existing over-limit import test checks rejection only, not exported-file compatibility.

### P2 - REM-SET-003: Restore silently discards backed-up settings for an existing source URL

**Owned evidence:** `BackupFormat.java:45-66` exports and parses source label, enabled, and allow flags. `BackupImporter.java:137-152` then unconditionally delegates all sources to `insertAll` and reports ordinary success. Context: `HostsSourceDao.java:32-36` declares `@Insert(onConflict = IGNORE)`; `HostsSource.java:29-32` makes URL unique. Existing URL collisions therefore do not restore any of those fields.

**Concrete repro, not run:** Back up a source with URL `https://restore-source.example/hosts`, label `Saved`, and `enabled=false`. Change that same existing source to label `Current` and `enabled=true`. Restore the original export. The import succeeds, but the source stays enabled with label `Current`. The same issue affects existing default subscriptions when restoring into an initialized installation. This finding does not ask to restore the deliberately disabled redirect permission.

**Likely fix:** Reconcile imported sources by URL inside the transaction and restore permitted configuration fields while preserving stable IDs and downloaded-row relationships. Use the normal selection propagation and format-change invalidation rules; keep the explicit redirect-trust restriction. If merge-only behavior is intentional, conflicts need explicit user-visible handling rather than a successful restore that silently ignores saved configuration.

**Smallest regression:** Seed one source, back up its disabled state, change its label/enabled state, then import into that same database. Assert restored fields and associated enabled-row state match the backup while ID is preserved. Include a new-URL control and verify redirect permission remains disabled as intended.

### P2 - REM-SET-004: IPv6 and connection-monitor switches do not update a running VPN

**Owned evidence:** `preferences_main.xml:37-41` and `preferences_vpn.xml:11-15` expose persistent IPv6 and monitor switches. `PrefsMainFragment.java:39-49` registers no IPv6-change action; `PrefsVpnFragment.java:30-40` registers bypass and exclusion actions but no watchdog-change action. The latter fragment already restarts the running VPN for bypass/system-exclusion changes (`55-71,85-89`). There is no shared-preference listener for these two unbound switches in the owned paths.

**Supporting trace:** `VpnWorker.java:221-226` reads the watchdog preference once when its work loop starts; `VpnWatchdog.java:80-87,102-111` continues using that captured enabled flag. `DnsServerMapper.java:104-105,117-135` reads IPv6 when building the VPN configuration. A saved preference alone changes neither existing watchdog state nor established DNS routes.

**Concrete repro, not run:** Start a VPN with Monitor connection off, switch it on in VPN preferences, and return without restarting or changing networks. The visible/persisted setting is on, but the existing watchdog remains disabled. Starting on and switching off likewise leaves it enabled. Separately, on a stable dual-stack network, changing Enable IPv6 support does not rebuild the established IPv6 DNS/routes; the setting only takes effect after an unrelated VPN rebuild. Grouped as one missing live-settings activation defect, not a duplicate of `NOV-VPN-05` route construction.

**Likely fix:** Bind both controls to a post-persistence reconfiguration of the currently active VPN, using the existing restart path or a safe in-place monitor update. Do not start a stopped VPN, request consent implicitly, or execute root-mode apply work.

**Smallest regression:** Exercise the actual switches while a test VPN worker is active and assert the new watchdog state and IPv6 configuration become effective without a manual restart. Include on/off, recreation, and stopped-VPN controls. A preference-persistence-only assertion is insufficient; the existing live bypass test does not exercise these controls.

## Reviewed Boundaries and Limits

- Import validates all arrays/rows before its transaction and retains explicit URL/redirect validation; malformed input is not partially committed. Redirect permission is deliberately cleared on source import, and SAF source read grants are not portable. Neither was logged as a new security defect.
- Exclusion row and switch use the same checked-change listener; rebinding clears the listener before setting checked state. Selection is persisted on each change, and the parent activity-result callback requests a restart when the VPN is running. No additional source-confirmed defect was established for that path. Installed-app visibility, actual per-app tunnel exclusion, and recreation with real platform results remain unexecuted.
- Theme, language, update scheduling, notification-settings routing, helper defaults, and backup picker lifecycle were read. No further evidenced finding is logged. This is not a claim of rendered, permission-dialog, process-death, provider, backup-transport, or root-device coverage.
- Only the 30 rows in this lane's files CSV count as full-file reads. Shared helper/XML overlap with earlier lanes is intentional re-review at the listed current hashes, not additional unique repository coverage.

## Handoff

Four new P2 findings, all `source-confirmed`; zero executed runtime reproductions. New files only: `remaining-settings-review.md`, `remaining-settings-files.csv`, and `remaining-settings-bugs.csv`. Main owns triage, implementation, central-ledger integration, and all integration/runtime gates.
