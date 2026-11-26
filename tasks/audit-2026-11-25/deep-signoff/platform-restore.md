# Android backup: selections that survive a fresh restore

Tested on disposable non-root Android 14 using Android's local key/value
backup transport. No cloud account, root access, database copy, or VPN
consent override was used. This is separate from exporting a JSON file
through the document picker.

| Action | Before the fix | With the fix |
| --- | --- | --- |
| Disable AdAway Official, leave Peter Lowe and StevenBlack enabled, save TravelRestore, schedule it daily. Back up, clear the app, restore, reopen Sources. | The signed app went from 2 of 3 enabled to 2 of 2: AdAway Official was missing. | All three rows returned, including the disabled AdAway Official. Both other switches were still on. Downloaded rows are not backed up. |
| Open Manage schedules after the same restore. | Android reported success, but the named schedule summary changed from 1 scheduled set to Off. | The screen showed 1 scheduled set, and the edit picker contained TravelRestore. |
| Check a manually blocked host after restore. | Not established on the original signed run. | The signed app identified example.org as blocked by User list. After turning the VPN on, its DNS endpoint returned no address and the AdAway SOA. The same endpoint returned an address for www.kernel.org. |

**Build identity:** The before APK was the local 13.5.7b candidate with SHA-256
`e08123d932628ec77d6f38ed74c3727eca3494c2d94ab68f315cb5cf8a1e3570`.
For the after test, installed the public 13.5.6b, created the selection and
daily schedule in its UI, then upgraded it in place to 13.5.7b APK
`8f1b3cc5e4639790eee74c08b43c0cbad0dcfbb3ca60b40144192474b4f46447`.
The original installation time was unchanged; v2/v3 signatures verified
against the pinned public certificate. Added and verified the user block
on that build, then performed the destructive restore **only on the emulator**.
Download cache counts are expected to start at zero until sources refresh.

**Independent debug control:** The extended platform test seeds a named
profile, its precise URL membership, an enabled source, a disabled source
sorting first, a weekly named schedule, an individual URL schedule, global
scheduling turned off, a normal preference and three distinct personal rule
types. On the baseline its ordinary data returned but its set URLs were
empty. On the corrected build all assertions passed, including restored
hour/minute and weekday, and the absence of imported redirect permission.
An initial signed-to-debug test setup was discarded because Android
correctly rejected the certificate mismatch; each valid run used a matching
signer and an isolated local transport dataset.

Repeated on an independent fresh API 34 emulator after adding scheduler
reconciliation at normal startup. With the global schedule off and the
named/URL schedules on, both seed and restore assertions passed; Android
had exactly one unfinished filter-update job. With every schedule off,
both phases also passed and Android had zero unfinished jobs. The
`final-on-*` and `final-off-*` logs include the installed app/test identities,
database-absent checks after clear, transport results, and assertions.

The original backup helper keys and JSON format are unchanged. A backup
made by an older version cannot restore filter settings it never contained;
no already-corrupt rule ownership is rewritten. See [reserved source identity](restore-identity.md).
The captured UI trees, command outputs, package identity, and two DNS
responses are in the local build report `platform-restore/` under
`app/build/reports/audit-2026-11-25/`. This does not establish cloud/OEM
restore, restored document permissions, or a physical-phone result.

To repeat the debug proof, use a disposable emulator with matching debug and
instrumentation APKs installed and Android's local-only transport selected.
The clear step below deletes **that installation's** settings. Do not run
it against a phone or an emulator with data to keep.

```sh
# Set ADB to the adb executable and SERIAL to the disposable emulator serial.
C=org.adaway.model.backup.AppBackupAgentPlatformInstrumentedTest
R=org.adaway.test/androidx.test.runner.AndroidJUnitRunner
"$ADB" -s "$SERIAL" shell am instrument -w -e platformBackupPhase seed \
    -e class "$C#seedPlatformBackupState" "$R"
"$ADB" -s "$SERIAL" shell bmgr backupnow org.adaway
"$ADB" -s "$SERIAL" shell bmgr list sets
"$ADB" -s "$SERIAL" shell pm clear org.adaway
"$ADB" -s "$SERIAL" shell bmgr restore 1 org.adaway  # use token from list sets
"$ADB" -s "$SERIAL" shell am instrument -w -e platformBackupPhase assert \
    -e class "$C#restoredPlatformBackupStateIsPresent" "$R"
```

Require both instrumentation phases to pass: the backup service's own success
line alone did not detect the original failure. For the all-schedules-off
variant, pass `-e backupSchedules off` to **both** instrumentation commands.

## Open boundaries

- Both transports in these tests were forced with `bmgr backupnow`. The
  app's schedule preference writers do not themselves request a key/value
  backup. Do not assume a schedule-only edit is promptly backed up on a phone.
  [Android's change-notification contract](https://developer.android.com/reference/android/app/backup/BackupManager#dataChanged()).
- The fresh database now reserves its user row before import, but catalog
  defaults are still seeded asynchronously. This test preserved a disabled
  default; it did not prove that a default **deleted** before backup never
  reappears when initialization and restore overlap. Do not rewrite existing
  user data as a speculative repair.
