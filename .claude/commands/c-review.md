---
description: Deep C code review with the C Engineers' perspective
argument-hint: [file/path]
---

# /c-review

Deep C code review with the C Engineers' perspective.

## Trigger
When user runs `/c-review [file/path]` or asks about native code in tcpdump/ or webserver/.

## Scope
- `tcpdump/jni/` - libpcap and tcpdump
- `webserver/jni/` - mongoose HTTP server
- Any `.c`, `.h` files
- Android.mk / CMakeLists.txt

## The C Engineers' Checklist

### Memory Safety
```c
// Check for:
malloc() without free()
free() without NULL assignment
Buffer overflows (strcpy vs strncpy)
Off-by-one errors
Use after free
Double free
Stack buffer overflows
```

### Resource Leaks
```c
// File descriptors
open() without close()
fopen() without fclose()
socket() without close()

// Memory mappings
mmap() without munmap()
```

### Thread Safety
```c
// Global state without protection
static variables in multi-threaded context
errno usage across threads
Signal handler safety
```

### Defensive Coding
```c
// NULL checks before dereference
// Return value checking
// Integer overflow guards
// Format string safety (printf with user data)
```

### Android NDK Specifics
```c
// JNI best practices
// Local reference limits
// Exception checking after JNI calls
// UTF-8 handling
```

## Output Format

```
## C Code Review: [file/module]

### Summary
[Brief assessment: safe/concerns/critical]

### Memory Analysis
| Location | Issue | Severity | Fix |
|----------|-------|----------|-----|
| file:line | [issue] | High/Med/Low | [fix] |

### Resource Management
- [x] File descriptors: [status]
- [x] Memory allocations: [status]
- [x] Sockets: [status]

### Thread Safety
[Assessment of concurrent access patterns]

### Recommendations
1. [Priority fix]
2. [Secondary fix]

### The C Engineers' Verdict
[Final assessment in the voice of seasoned C developers]
```

## Warning
Native code changes require careful review. The C engineers remind us:
> "In C, the compiler trusts you. Don't betray that trust."
