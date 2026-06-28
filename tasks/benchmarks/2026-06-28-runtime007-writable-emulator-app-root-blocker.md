# RUNTIME-007 Writable-System Emulator App-Root Blocker - 2026-06-28

Scope:
- Story: `RUNTIME-007`
- Worktree: `/private/tmp/adaway-pr6-slice`
- Device: `adaway-api34-16g` (`sdk_gphone64_arm64`, API 34)
- Goal: maximize local proof for rooted hosts-file apply before asking for human/device help.

## Emulator Launch

The emulator was launched with a writable system image:

```bash
ANDROID_HOME=$HOME/.local/android-sdk ANDROID_SDK_ROOT=$HOME/.local/android-sdk \
  $HOME/.local/android-sdk/emulator/emulator -avd adaway-api34-16g \
  -writable-system -no-snapshot -no-audio -no-boot-anim
```

Boot completed:

```text
boot_completed=1
```

## Shell Root And Writable Hosts Proof

The shell side is stronger than the older 2026-06-27 blocker:

```text
emulator-5554 device product:sdk_gphone64_arm64 model:sdk_gphone64_arm64 device:emu64a
restarting adbd as root
uid=0(root)
Successfully disabled verity
Remounted /system as RW
Remount succeeded
overlay on /system type overlay (rw,...)
```

The original hosts hash was:

```text
425c3e713d5bae19b031bc8639c20c6a23e311a54647ba1824cbf45969a11ff4  /system/etc/hosts
```

A controlled shell write and restore succeeded:

```bash
adb shell 'cp /system/etc/hosts /data/local/tmp/hosts.before'
adb shell 'printf "127.0.0.1 localhost\n::1 ip6-localhost\n127.0.0.1 codex-root-write-smoke.local\n" > /system/etc/hosts'
adb shell 'grep codex-root-write-smoke.local /system/etc/hosts'
adb shell 'cp /data/local/tmp/hosts.before /system/etc/hosts'
```

Observed mutation and restoration:

```text
127.0.0.1 codex-root-write-smoke.local
2756fda1cb426cdc9932e6615835d8247f86bf86181f45d4304e88bf371d4127  /system/etc/hosts
425c3e713d5bae19b031bc8639c20c6a23e311a54647ba1824cbf45969a11ff4  /system/etc/hosts
```

## App/Libsu Proof Attempt

An opt-in connected smoke was added:

```text
app/src/androidTest/java/org/adaway/model/root/RootModelApplyInstrumentedTest.java
```

The test is skipped unless explicitly invoked with:

```text
-Pandroid.testInstrumentationRunnerArguments.rootHostsApplySmoke=true
```

The androidTest source compiled:

```bash
./gradlew --no-daemon :app:compileDebugAndroidTestJavaWithJavac \
  --dependency-verification=strict --stacktrace
```

Result:

```text
BUILD SUCCESSFUL in 5s
```

The opt-in app-level smoke did not reach hosts-file mutation because the app process was not
granted root by this AOSP emulator:

```bash
./gradlew --no-daemon --no-build-cache :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.root.RootModelApplyInstrumentedTest \
  -Pandroid.testInstrumentationRunnerArguments.rootHostsApplySmoke=true \
  --dependency-verification=strict --stacktrace
```

Failure:

```text
RootModel.apply smoke requires a root shell, got:
uid=10194(u0_a194) gid=10194(u0_a194) groups=10194(u0_a194),3003(inet),...
context=u:r:untrusted_app:s0:c194,c256,c512,c768
```

The test was then aligned with production root selection by calling `Shell.getShell()` before the
root assertion. The result remained a non-root app shell.

## Emulator Su Bootstrap Attempt

The emulator's `su` binary is shell-only by default:

```text
4750 root shell /system/xbin/su
-rwsr-x--- 1 root shell u:object_r:su_exec:s0 /system/xbin/su
```

Because `/system` was writable, the emulator was temporarily bootstrapped to allow app execution:

```bash
adb shell 'chmod 4755 /system/xbin/su'
```

Confirmed:

```text
4755 root shell /system/xbin/su
-rwsr-xr-x 1 root shell u:object_r:su_exec:s0 /system/xbin/su
```

The opt-in smoke still failed before mutation:

```text
RootModel.apply smoke requires a root shell, got:
uid=10196(u0_a196) gid=10196(u0_a196) groups=10196(u0_a196),3003(inet),...
context=u:r:untrusted_app:s0:c196,c256,c512,c768
```

The emulator was restored after the probe:

```bash
adb shell 'chmod 4750 /system/xbin/su'
```

Final state:

```text
4750 root shell /system/xbin/su
425c3e713d5bae19b031bc8639c20c6a23e311a54647ba1824cbf45969a11ff4  /system/etc/hosts
```

## Result

The stale read-only blocker is superseded. The current local blocker is app-granted root:

- `adb root`, `adb remount`, and shell-level `/system/etc/hosts` write/restore now work.
- The real app/libsu path still cannot prove `RootModel.apply()` because `Shell.getShell()` and
  `Shell.cmd("id")` run as the app UID on this AOSP emulator.
- `RUNTIME-007` remains open until a rooted physical device, Magisk/root-manager emulator, or other
  trusted target grants root to `org.adaway` so the opt-in smoke can verify the generated AdAway
  hosts header and a seeded blocked host in `/system/etc/hosts`, then restore the original file.
