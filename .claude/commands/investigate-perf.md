---
description: Investigate performance bottlenecks with the full team's perspective
---

# /investigate-perf

Investigate performance bottlenecks with the full team's perspective.

## Trigger
When user runs `/investigate-perf` or asks about performance issues, slow operations, or jank.

## Process

### Phase 1: The C Engineers' View
First, examine the low-level:
1. Check SQLite WAL mode in `AppDatabase.java`
2. Look for memory allocation patterns in parsing loops (`SourceLoader.java`)
3. Identify syscall-heavy operations
4. Check for page cache thrashing

Search for:
- `@Database` annotations and Room configuration
- Loops that allocate inside hot paths
- String concatenation in loops
- Regex compilation inside loops

### Phase 2: The Java Engineers' View
Examine Android-specific concerns:
1. Main thread usage - any `runOnUiThread` in hot paths?
2. LiveData observer patterns - firing during bulk operations?
3. Room threading vs AppExecutors conflicts
4. WorkManager constraints

Search for:
- `postValue` / `setValue` calls during bulk operations
- `@MainThread` annotations or main thread assumptions
- Thread pool configurations in `AppExecutors.java`

### Phase 3: The Architects' View
Map the data flow:
1. Trace from UI tap to completion
2. Identify every synchronization point
3. Find where the pipeline stalls
4. Draw the threading model

Create a mental model of:
- Entry point (which Activity/Fragment?)
- Background execution path
- Database transaction boundaries
- UI update mechanism

### Phase 4: Evidence Gathering
Collect concrete data:
1. Find timing logs (`Timber.i` with durations)
2. Identify batch sizes and counts
3. Calculate theoretical vs actual throughput
4. Look for sequential operations that could be parallel

## Output Format

Present findings as:

```
## Root Cause Analysis

### What We Found
[Concrete evidence with file:line references]

### The C Engineers Say
[Memory/SQLite-level insights]

### The Java Engineers Say
[Android/threading insights]

### The Architects Say
[System-level bottleneck identification]

### Recommended Fix
[Specific, actionable solution with rationale]
```

## Files to Always Check
- `app/src/main/java/org/adaway/db/AppDatabase.java`
- `app/src/main/java/org/adaway/model/source/SourceModel.java`
- `app/src/main/java/org/adaway/model/source/SourceLoader.java`
- `app/src/main/java/org/adaway/util/AppExecutors.java`
- `app/src/main/java/org/adaway/db/dao/HostListItemDao.java`
