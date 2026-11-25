# Settings Fixes - Ready for Integration

Date: 2026-11-25. Branch: `codex/full-codebase-audit`; phase-one base reported by main: `f7dbbb5e7`.

## Frozen Scope

Production and test inputs are frozen for main's next compilation. This lane changed five production Java files and one narrow strings resource, and added five test files. Main staged phase two; the follow-up below changes only BackupExportCapacityInstrumentedTest, unstaged on top of that index. Exact current hashes are in `settings-fixes-files.csv`.

No Gradle, ADB, emulator, device, root write, staging, commit, push, DAO/schema edit, central-ledger edit, or shared-controller/service edit was performed. Other lanes' changes were preserved. `AppBackupAgent` and `PrefsBackupRestoreFragment` need no source edit: their existing calls now enter the corrected shared backup path.

## Implemented

- **REM-SET-001:** Context/URI restore parses and validates before mutation, acquires the application SourceOperationCoordinator lease, then enters SourceModel.runSourceMutation. Source/user changes share an AppDatabase transaction; syncHostEntries and its application VPN-cache invalidation finish before success. Both guards remain held through maintenance and close on failure. A rebuild failure after commit explicitly invalidates the VPN cache and reports failure. Busy rejection has a retry instruction, not a success toast or silent deferred job. No VPN start/apply or root-file operation is called.
- **REM-SET-002:** Shared BackupFormat parsing enforces the existing 200-source and 50,000-total-rule limits and validates generated export rows. Export checks the actual pretty-printed JSON length against the unchanged 16 Mi-character limit before opening the destination in truncate mode. No cap was raised and no rows are silently omitted.
- **REM-SET-003:** Source URLs reconcile inside the import transaction. Existing rows keep their IDs and directory metadata; label/selection-only restores retain downloaded rows and HTTP validators. Selection changes propagate to source items. Allow-format changes or revocation of existing redirect trust remove source rules and staging rows, reset HTTP dates/ETag/counters/errors, and invalidate database-only root-export caches using existing editor/DAO APIs. Redirect trust is always false after restore. Reserved user-source documents are rejected before mutation.
- **REM-SET-004:** Main and VPN preferences observe relevant SharedPreferences changes after persistence. IPv6 and watchdog changes reach VpnServiceControls.restart only when VPN is the selected method and is currently running. Bypass and system exclusions use the same post-persistence path; user-exclusion return remains supported. Listeners unregister on destruction and do not post delayed activity-owned restart callbacks.

## Executed Verification

- Standalone JDK 21 `javac` compilation passed against Android API 36 `android.jar` and its cached `jdkImage` (`--system`, source/target 21, no annotation processing), using existing debug app/test classes and cached dependencies. All new Java tests and changed production Java compiled.
- Compilation outputs are isolated under `/tmp/adaway-settings-fixes-checks`; `/tmp/adaway-settings-checks.rb` is the local reproducibility helper. A temporary R source is mechanically generated from existing resource symbols with the new retry-string symbol. Therefore this proves Java/API compatibility, **not** Android resource linking, annotation processing, APK assembly, a clean build, or lint. The edited XML also parsed successfully.
- Host JUnit: **22 passed**, including **5 new BackupCapacityTest cases** plus the existing BackupValidationTest (5), BackupFormatSecurityTest (8), BackupSafContractTest (1), and AppBackupAgentContractTest (3).
- Scoped whitespace/diff checks passed. All eleven current hashes and sidecar schemas were checked. This lane did not stage any file; main owns the existing staged phase-two inputs.
- Main's phase-two log records **16 passed, 1 failed fixture, 2 skipped** among the 19 new Android cases. The corrected oversized-export case is standalone-compiled but awaits Android rerun. Exact cases and limitations are in `settings-fixes-tests.csv`. Main owns all Room/provider/UI/VPN execution.

## Phase-Two Fixture Correction

Evidence: `/tmp/adaway-audit-phase-two-focused.log`, lines 731-778 and 1674-1695. The oversized-export fixture stored one 16 Mi-character label. HostsSourceDao_Impl.getAll failed with SQLiteBlobTooBigException while filling CursorWindow, called by BackupExporter.makeBackup before serializeBackup or destination opening. This is a fixture failure, not evidence that the export capacity guard or sentinel protection failed. Catching that database exception as IOException would let the original test pass without ever exercising its intended guard; production code is unchanged.

The corrected fixture uses exactly the allowed 200 sources with 83,887 ASCII characters per label. Labels alone total 16,777,400 characters, above the unchanged 16,777,216-character cap; JSON overhead only increases the document size. Every row remains small enough for the intended cursor read, and an explicit post-update getAll/count assertion verifies readability before export. The existing IOException assertion and exact sentinel comparison remain. The test additionally requires a JSONException cause with `Backup exceeds maximum size.`, so a count failure, invalid row, database read failure, or provider failure cannot masquerade as successful size-guard coverage.

After this test-only correction, standalone Android API-image compilation passed and all **22 host backup tests passed again**. No Android rerun was performed by this lane. Main should rerun all four BackupExportCapacityInstrumentedTest cases; the two opt-in live-VPN cases also remain pending. The staged test blob remains `fd369c9a29b8b2694f0a9eee8baba244aca478e3`; only the working-tree test and lane sidecars changed for this follow-up. Future large-document fixtures should distribute payload across readable rows and assert the intended rejection cause, not merely any exception.

Main's new Android class selection:

```text
org.adaway.model.backup.BackupSourceReconciliationTest
org.adaway.model.backup.BackupExportCapacityInstrumentedTest
org.adaway.model.backup.BackupRestoreRuntimeInstrumentedTest
org.adaway.ui.prefs.PrefsLiveConfigurationInstrumentedTest
```

Also rerun existing BackupImportAtomicityTest, PrefsBackupRestoreSafInstrumentedTest, AppBackupAgentPlatformInstrumentedTest, PrefsVpnSettingsInstrumentedTest, PrefsVpnRestartInstrumentedTest, and relevant theme/language navigation cases.

## Explicit Limits

- Android success is limited to the 16 cases recorded in main's phase-two log. Runtime restore tests use the real Context/URI path and a warmed application VpnModel, but deliberately keep the VPN stopped. Live setting tests separately require `adawayLiveVpnSmoke=true`, already prepared consent, and no existing service/tunnel. They click actual switch widgets and require replacement worker/tunnel evidence, not persistence alone. Both were skipped without opt-in in phase two.
- The live IPv6 test checks DNS-family changes only when the initial upstream advertises both IPv4 and IPv6 DNS; on other networks it still requires replacement of the tunnel. It does not disable the independent known-DoH-provider IPv6 block routes. Network changes during this fixture can affect that assertion.
- Shared VpnServiceControls.restart still sends START. Settings checks selected mode and current established state immediately before dispatch; the shared controller/service's cross-command ordering of a concurrent STOP and START was not changed. Already-stopped/root controls do not dispatch. A change made while the tunnel is not established relies on the next normal worker configuration reading current preferences; no stopped VPN is started to apply a setting.
- The transaction protects source/user writes, not a later derived-cache rebuild. If rebuilding fails after commit, the restored rows remain committed, the warmed VPN cache is invalidated, and the operation reports failure rather than false success. A held/failing maintenance callback was source-reviewed but not runtime-injected.
- BackupAgent continues through the same import/export functions. Real Android backup transport timing and busy restore recovery were not executed; no durable retry job was added. Busy UI restore is explicitly retried by the user.
- SAF grants are not transferable. New/format-changed subscriptions are left needing the normal update flow; restore does not download, auto-enable protection, change schedules, or write a root hosts file. Deliberate redirect-trust revocation is preserved.
- Count limits are enforced during shared parsing after export data is gathered. Very large existing databases can still require allocation to gather/serialize a candidate document; this patch does not claim streaming export or low-memory hardening.

## Result

All four approved findings are implemented and standalone-compiled. REM-SET-001 and REM-SET-003 now have their focused Android cases passing; REM-SET-002 awaits the corrected oversized-export rerun, and REM-SET-004 awaits live-VPN proof. Sidecar status is `implemented-runtime-partial`, not blanket verified-fixed. Ready/frozen for main to compile; this follow-up makes no production changes and does not alter the index.
