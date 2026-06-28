# NOTIF-002 Non-Mutating Alert Proof - 2026-06-28

## Scope

`NOTIF-002` covers the app-owned hosts/app update alert contract: distinct clearable
notifications, actionable Home/UpdateActivity targets, channel metadata, and permission-state
posting behavior without mutating `POST_NOTIFICATIONS`.

Notification permission UX remains tracked by `NOTIF-003` and `PREF-010`.

## Commands

```bash
tr -d '\r' < ./gradlew | JAVA_HOME="$HOME/.local/jdks/temurin-21/Contents/Home" \
  ANDROID_HOME="$HOME/.local/android-sdk" \
  ANDROID_SDK_ROOT="$HOME/.local/android-sdk" \
  PATH="$HOME/.local/jdks/temurin-21/Contents/Home/bin:$HOME/.local/android-sdk/platform-tools:$PATH" \
  bash -s -- --no-daemon \
  :app:testDebugUnitTest \
  --tests org.adaway.helper.NotificationHelperContractTest \
  :app:compileDebugAndroidTestJavaWithJavac \
  --dependency-verification=strict --stacktrace
```

Result: passed, `BUILD SUCCESSFUL in 4s`.

```bash
tr -d '\r' < ./gradlew | JAVA_HOME="$HOME/.local/jdks/temurin-21/Contents/Home" \
  ANDROID_HOME="$HOME/.local/android-sdk" \
  ANDROID_SDK_ROOT="$HOME/.local/android-sdk" \
  PATH="$HOME/.local/jdks/temurin-21/Contents/Home/bin:$HOME/.local/android-sdk/platform-tools:$HOME/.local/android-sdk/emulator:$PATH" \
  bash -s -- --no-daemon \
  :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.helper.NotificationHelperChannelInstrumentedTest \
  --dependency-verification=strict --stacktrace
```

Result: passed, `BUILD SUCCESSFUL in 8s`.

Device: `adaway-api34-16g(AVD) - 14`.

Focused connected methods covered:

- `createChannelsInstallsExpectedUserVisibleMetadata`
- `existingFilterListsStyleChannelDoesNotUpgradeFromLowToDefaultImportance`
- `updateAlertsBuildDistinctActionableNotificationsWithoutPermissionMutation`
- `updateAlertPostingMatchesCurrentNotificationPermissionState`

## Notes

The first connected attempt failed before instrumentation with Gradle `No connected devices!`
after the emulator process exited. The emulator was restarted as a held-open process, booted to
`sys.boot_completed=1`, and the focused connected suite then passed.
