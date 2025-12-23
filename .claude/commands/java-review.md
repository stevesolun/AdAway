---
description: Android/Java code review with the Java Engineers' perspective
argument-hint: [file/path]
---

# /java-review

Android/Java code review with the Java Engineers' perspective.

## Trigger
When user runs `/java-review [file/path]` or asks about Java/Android code quality.

## The Java Engineers' Checklist

### Android Lifecycle
```java
// Check for:
// - Activity/Fragment leaks (holding Context references)
// - ViewModel misuse (holding View references)
// - LiveData observer leaks (observeForever without remove)
// - WorkManager proper cancellation
```

### Room/Database
```java
// Check for:
// - Main thread database access
// - Transaction boundaries
// - Query efficiency (@Query vs loading all)
// - Migration completeness
```

### Threading
```java
// Check for:
// - Correct executor usage
// - UI updates from background threads
// - Race conditions on shared state
// - Proper synchronization
```

### Memory
```java
// Check for:
// - Bitmap handling (recycling, sizing)
// - Collection growth (unbounded lists)
// - String concatenation in loops
// - Anonymous class captures
```

### Code Style (per CLAUDE.md)
```java
// Verify:
// - 4-space indentation
// - 100 char line width
// - mFieldName for non-public fields
// - getUrl() not getURL()
```

## Android-Specific Patterns

### Good Patterns
```java
// ViewModel + LiveData for UI state
// WorkManager for deferrable work
// Room for persistence
// Coroutines/RxJava for async (if used)
```

### Anti-Patterns to Flag
```java
// AsyncTask (deprecated)
// Loader framework (deprecated)
// HandlerThread for simple background work
// Static Activity/Context references
```

## Output Format

```
## Java Code Review: [file/class]

### Summary
[Brief assessment]

### Lifecycle Safety
- [x] No Context leaks
- [x] Proper observer cleanup
- [ ] Issue: [description]

### Threading Correctness
- [x] Background work off main thread
- [x] UI updates on main thread
- [ ] Issue: [description]

### Database Usage
- [x] Proper transaction boundaries
- [x] Efficient queries
- [ ] Issue: [description]

### Style Compliance
- [x] Indentation: OK
- [x] Field naming: OK
- [ ] Issue: [description]

### The Java Engineers' Verdict
[Assessment in the voice of Android veterans]

### Suggested Improvements
1. [Improvement with rationale]
2. [Improvement with rationale]
```
