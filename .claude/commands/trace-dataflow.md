---
description: Trace data flow through the system like an Architect
argument-hint: [feature]
---

# /trace-dataflow

Trace data flow through the system like an Architect.

## Trigger
When user runs `/trace-dataflow [feature]` or asks "how does X work" / "what happens when".

## Process

### Step 1: Identify Entry Point
Find where the flow begins:
- UI event handler (Activity/Fragment)
- Broadcast receiver
- Service trigger
- WorkManager job

### Step 2: Map the Journey
For each step, document:
1. **What** - The operation
2. **Where** - File:line
3. **Thread** - Main/Background/Which pool?
4. **Blocks?** - Does it wait for something?
5. **Emits?** - Does it trigger observers?

### Step 3: Find Synchronization Points
Identify:
- Database transactions
- `synchronized` blocks
- `CountDownLatch` / `Semaphore`
- `LiveData.postValue` (implicit sync)
- Thread pool `submit` -> `get`

### Step 4: Identify State Changes
Track mutations:
- Database writes
- SharedPreferences updates
- In-memory state changes
- UI updates

## Output Format

```
## Data Flow: [Feature Name]

### Entry Point
[Activity/method that starts the flow]

### Flow Diagram
┌─────────────────┐
│ UI Event        │ Thread: Main
└────────┬────────┘
         ▼
┌─────────────────┐
│ SourceModel     │ Thread: diskIO
│ .retrieveHosts()│
└────────┬────────┘
         ▼
    [continue...]

### Critical Path
1. [Step] -> file:line (thread, ~duration)
2. [Step] -> file:line (thread, ~duration)
...

### Synchronization Points
- [Point 1]: Why it blocks, how long
- [Point 2]: ...

### Bottleneck Candidates
- [ ] [Potential issue 1]
- [ ] [Potential issue 2]
```

## Key Entry Points to Know
- `HomeFragment` -> Update button tap
- `HostsSourcesActivity` -> Source management
- `SourceUpdateService` -> Background updates
- `BootReceiver` -> Startup flow
