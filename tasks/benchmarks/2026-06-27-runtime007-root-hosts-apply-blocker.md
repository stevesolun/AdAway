# RUNTIME-007 Root Hosts Apply Blocker - 2026-06-27

Scope:
- Story: `RUNTIME-007`
- Device attempted: `adaway-api34-16g`
- Goal: prove rooted hosts-file apply with real remount/write/restore evidence.

Commands and observations:

```bash
adb root
adb shell id
adb shell 'su 0 id'
adb remount
adb shell 'su 0 sh -c "avbctl get-verification; avbctl disable-verification"'
adb shell 'su 0 sh -c "printf ... > /system/etc/hosts"'
emulator -avd adaway-api34-16g ... -writable-system ...
```

Evidence:
- `adb root` succeeded and shell became `uid=0(root)`.
- `/system/xbin/su` exists and `su 0 id` returns `uid=0(root)`.
- `adb remount` failed with `Device must be bootloader unlocked`.
- `avbctl disable-verification` failed while writing `vbmeta`.
- Direct `/system/etc/hosts` write failed before mutation with `Read-only file system`.
- The `-writable-system` AVD launch attempt did not produce a usable connected device; the normal
  AVD was relaunched and reached `boot_completed=1`.

Result:
- This environment is root-shell capable but is not accepted as a rooted hosts-apply smoke target.
- `RUNTIME-007` remains open until a rooted physical device or trusted writable-system emulator can
  run the real `RootModel.apply()` path and restore `/system/etc/hosts` afterward.
