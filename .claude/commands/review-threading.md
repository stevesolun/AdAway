---
description: Deep analysis of threading model and concurrency
---

# /review-threading

Deep analysis of threading model and concurrency.

## Trigger
When user runs `/review-threading` or mentions race conditions, deadlocks, ANR, threading issues.

## The Java Engineers' Checklist

### 1. Thread Pool Audit
Check `AppExecutors.java`:
- How many pools? What sizes?
- Are they bounded or unbounded?
- What's the rejection policy?

### 2. Room Threading
Examine database access:
- Are DAOs accessed from correct threads?
- Any `allowMainThreadQueries()`? (red flag)
- Transaction boundaries clear?

### 3. LiveData Patterns
Review observer usage:
- `observe` vs `observeForever`
- `postValue` vs `setValue`
- Observers during bulk operations?

### 4. WorkManager Integration
Check background work:
- Constraints properly set?
- Chained work dependencies?
- Conflicts with AppExecutors?

## The C Engineers' Checklist

### 1. JNI Boundaries
If native code involved:
- Thread attachment/detachment
- Local vs global references
- Exception handling across boundary

### 2. Native Threading
In tcpdump/webserver:
- pthread usage patterns
- Mutex/condition variable usage
- Signal handling

## Red Flags to Search For

```java
// ANR risks
runOnUiThread { /* heavy work */ }
Handler(Looper.getMainLooper()).post { /* heavy work */ }

// Race conditions
if (x != null) x.method()  // TOCTOU
sharedVar = value  // without synchronization

// Deadlock patterns
synchronized(A) { synchronized(B) { } }  // elsewhere: synchronized(B) { synchronized(A) { } }

// LiveData spam
for (item in items) { liveData.postValue(item) }
```

## Output Format

```
## Threading Analysis

### Thread Pools
| Pool | Size | Purpose | Concern |
|------|------|---------|---------|
| diskIO | N | DB ops | [any issue] |
| ... | ... | ... | ... |

### Critical Sections
1. [Lock/sync point] - file:line
   - Held by: [what]
   - Duration: [estimate]
   - Risk: [deadlock/contention/none]

### Main Thread Violations
- [ ] [Violation 1] - file:line
- [ ] [Violation 2] - file:line

### Race Condition Candidates
- [ ] [Shared state] accessed from [threads] without sync

### Recommendations
1. [Fix 1]
2. [Fix 2]
```
