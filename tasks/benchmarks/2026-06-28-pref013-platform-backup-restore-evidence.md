# PREF-013 Platform Backup Restore Evidence - 2026-06-28

## Scope

`PREF-013` covers the app-owned Android backup agent contract: preferences and user
rules should survive Android Backup Manager backup/restore when a platform transport is available.

This proof uses Android's debug local transport on API 34. Cloud account availability,
OEM transport behavior, and user backup settings remain OS-owned.

## Device And Transport

Device:

```text
adaway-api34-16g(AVD) - API 34
```

Backup transport setup:

```bash
adb shell bmgr enable true
adb shell bmgr transport com.android.localtransport/.LocalTransport
adb shell bmgr list transports
```

Result:

```text
Backup Manager now enabled
Selected transport com.android.localtransport/.LocalTransport
  * com.android.localtransport/.LocalTransport
```

## Build And Install

```bash
./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest \
  --dependency-verification=strict --stacktrace
adb uninstall org.adaway.test || true
adb uninstall org.adaway || true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm list instrumentation | grep org.adaway
```

Result:

```text
BUILD SUCCESSFUL in 4s
Success
Success
instrumentation:org.adaway.test/androidx.test.runner.AndroidJUnitRunner (target=org.adaway)
```

## Seed Phase

The seed phase writes a SharedPreferences probe and user-rule data for blocked, allowed,
and redirected rules, then asserts that the data exists before backup.

```bash
adb shell am instrument -w -r \
  -e class org.adaway.model.backup.AppBackupAgentPlatformInstrumentedTest#seedPlatformBackupState \
  -e platformBackupPhase seed \
  org.adaway.test/androidx.test.runner.AndroidJUnitRunner
```

Result:

```text
OK (1 test)
```

## Backup, Clear, Restore

```bash
adb shell bmgr backupnow org.adaway
adb shell pm clear org.adaway
adb shell bmgr list sets
adb shell bmgr restore 1 org.adaway
```

Result:

```text
Running incremental backup for 1 requested packages.
Package @pm@ with result: Success
Package org.adaway with result: Success
Backup finished with result: Success
Success
  1 : Local disk image
Scheduling restore: Local disk image
restoreStarting: 1 packages
onUpdate: 0 = org.adaway
restoreFinished: 0
done
```

## Assert Phase

The assert phase verifies that Android Backup Manager restored the SharedPreferences probe
and that `AppBackupAgent` restored the source plus blocked, allowed, and redirected user rules.

```bash
adb shell am instrument -w -r \
  -e class org.adaway.model.backup.AppBackupAgentPlatformInstrumentedTest#restoredPlatformBackupStateIsPresent \
  -e platformBackupPhase assert \
  org.adaway.test/androidx.test.runner.AndroidJUnitRunner
```

Result:

```text
OK (1 test)
```

## Notes

An initial attempt used Gradle `connectedDebugAndroidTest` for the seed phase, but Gradle
cleaned up the target package before `bmgr backupnow`, producing `Package not found`.
The accepted proof uses manual APK install and `adb shell am instrument`, which keeps the target
package installed across seed, platform backup, app-data clear, restore, and assertion.
