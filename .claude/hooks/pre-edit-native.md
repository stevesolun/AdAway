# Pre-Edit Hook: Native Code Protection

## Trigger
Before editing any file in:
- `tcpdump/**`
- `webserver/**`
- Any `.c`, `.h`, `.cpp` file

## Action

Display warning:
```
[WARNING] NATIVE CODE BOUNDARY

You are about to modify C/C++ code in the native layer.

The C Engineers remind you:
- Memory safety is your responsibility
- No garbage collector will save you
- Test on actual devices, not just emulator
- Consider JNI implications

Proceed with caution.

Files in scope: [list affected files]
```

## Validation
Before committing native changes:
1. Check for obvious memory issues
2. Verify Android.mk / CMakeLists.txt if needed
3. Confirm build still works: `./gradlew assembleDebug`

## Escalation
If changes are significant, suggest:
- Running `/c-review` on the changes
- Testing on multiple architectures (arm64, x86)
- Reviewing with the "C Engineers" mindset
