# Fresh restore: reserved source identity

**DEEP-RESTORE-ID-001 (P1).** A subscription can physically exist but disappear from both source selection and enabled-source queries. Both exclude ID 1 because it belongs to the user rule list. The first ordinary insert into a fresh database could take that ID while the default seed waited for the disk executor.

## Evidence

- Parent reported the signed backup / clear / restore disappearing-first-source result twice. This lane independently reproduced the ID allocation, without calling the backup agent, changing system backup state, or fetching lists.
- On API 34, opened a fresh Room database using the production creation callback with deferred work held back. Inserted AdAway Official first, followed by another enabled row and one disabled row. Baseline failure: `First restored source took ID 1`.
- After the fix, the first ID is greater than 1, all three subscriptions are listed, exactly two are enabled, and the disabled subscription stays disabled. ID 1 resolves to the canonical user URI, localized label, and original enabled/allow/redirect flags.
- Separate on-disk version-32 control: deliberately create a database with a disabled subscription at ID 1 and an attached allow rule. Reopen it with the production callback. The same URL, selection, rule type, and source association survive; creation/seed is not invoked.
- Debug runtime result on `emulator-5578`: 2 focused identity checks and 25 existing migration checks passed. Local runner outputs: `identity-red.txt` and `identity-green.txt` in `app/build/reports/audit-2026-11-25/platform-restore/worker-and-identity/`. A final cleanup changed the callback's test hook from an enqueue function to the disk executor itself; the reservation SQL and assertions are unchanged. Both final debug APKs compiled; the 27-test run preceded that cleanup. Device handed to parent for the integrated suite, with no further device actions in this lane.

## Compatibility and recovery

The reservation uses the live creation transaction and bound ID, label, and URI. It does not rely on a sequence bump: user rules have a real parent row immediately. Existing catalog and allowlist work stays asynchronous. Its existing `getAll().isEmpty()` check ignores the reserved row, so a new installation can still receive defaults. Schema version, migrations, backup format, and backup agent are unchanged.

This is prevention, not historical repair. If an ordinary URL already occupies ID 1, restoring that URL against the same database reaches the importer's reserved-ID guard; reopening alone does not recover it. A source export that already omitted the row cannot supply the missing subscription. ID 1 may also own personal rules: deleting the row, changing it in place, or bulk-reassigning its rules would risk data loss or incorrect attribution. Preserve the original database and any known-good backup for separate recovery analysis. No destructive repair is implemented.

Platform backup/restore and profile preferences remain the parent's checks. Parent reported the combined debug fresh restore green on 5576; this is not a signed end-to-end verdict from this lane.
