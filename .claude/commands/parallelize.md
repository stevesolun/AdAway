# /parallelize

Parallelism & Concurrency Expert analysis.

## Trigger
When user runs `/parallelize` or asks about making operations faster through parallelism.

## Expertise
- Thread pools, executors, coroutines
- Lock-free data structures
- Producer-consumer patterns
- Work stealing, fork-join
- Android-specific: HandlerThread, AsyncTask, WorkManager, Kotlin coroutines, RxJava
- Database: WAL mode, connection pooling, transaction batching
- I/O: parallel downloads, async parsing, non-blocking I/O

## Analysis Process

### Phase 1: Threading Model Inventory
Identify all threading mechanisms in use:
1. Search for `ExecutorService`, `Executors.newFixedThreadPool`, `Executors.newCachedThreadPool`
2. Search for `Semaphore`, `CountDownLatch`, `CyclicBarrier`
3. Search for `AtomicInteger`, `AtomicLong`, `AtomicReference`
4. Search for `synchronized`, `ReentrantLock`, `ReadWriteLock`
5. Search for `BlockingQueue`, `LinkedBlockingQueue`, `ConcurrentHashMap`
6. Search for WorkManager, AsyncTask, HandlerThread patterns

### Phase 2: Serialization Bottlenecks
Find things running sequentially that could be parallel:
1. Sequential loops with independent iterations
2. Sequential file processing
3. Sequential network requests
4. Sequential database operations
5. Waiting for one task before starting another (unnecessary dependencies)

Look for patterns like:
```java
for (item : items) {
    download(item);    // Could be parallel!
    parse(item);       // Could overlap with next download!
}
```

### Phase 3: Synchronization Bottlenecks
Find unnecessary locking and contention:
1. Global locks protecting independent resources
2. Coarse-grained locks that could be fine-grained
3. Lock ordering problems
4. Busy-waiting instead of proper signaling
5. Unnecessary thread-safe collections in single-threaded contexts

### Phase 4: Resource Utilization
Check if hardware is being used effectively:
1. CPU-bound vs I/O-bound classification
2. Are all cores being utilized?
3. Memory pressure from over-parallelization
4. Connection pool sizing for network operations
5. Database connection pooling

### Phase 5: Android-Specific Concerns
1. Main thread blocking (ANR risk)
2. Room threading model (query executors)
3. LiveData postValue vs setValue
4. WorkManager constraints and chaining
5. OkHttp connection pool and dispatcher

## Output Format

```
## Parallelism Analysis

### Current Threading Model
| Component | Mechanism | Pool Size | Bottleneck? |
|-----------|-----------|-----------|-------------|
| Check phase | FixedThreadPool | N | [Yes/No] |
| Download phase | FixedThreadPool | N | [Yes/No] |
| Parse phase | Semaphore-bounded | N | [Yes/No] |
| DB inserts | Room executor | N | [Yes/No] |

### Serialization Bottlenecks Found
1. **[Bottleneck]** - file:line
   - Current: [What happens now]
   - Issue: [Why it's slow]
   - Fix: [Proposed solution]
   - Speedup: [Estimated improvement]

### Synchronization Bottlenecks Found
1. **[Bottleneck]** - file:line
   - Lock: [What's locked]
   - Contention: [Who's waiting]
   - Fix: [Proposed solution]

### Hardware Utilization
- CPU cores: N
- Heap size: X MB
- Current parallelism: [analysis]
- Optimal parallelism: [recommendation]

### Concrete Recommendations
1. [Specific change with code example]
2. [Specific change with code example]
...

### Expected Overall Speedup
[Estimate based on Amdahl's law and bottleneck analysis]
```

## Files to Always Check
- `app/src/main/java/org/adaway/model/source/SourceModel.java` - Pipeline orchestration
- `app/src/main/java/org/adaway/model/source/SourceLoader.java` - Parsing parallelism
- `app/src/main/java/org/adaway/db/AppDatabase.java` - Room configuration
- `app/src/main/java/org/adaway/db/dao/HostListItemDao.java` - Insert methods
- `app/src/main/java/org/adaway/util/AppExecutors.java` - Thread pools

## Key Optimization Opportunities

### 1. Database Inserts
Room inserts can be batched. Check `INSERT_BATCH_SIZE`. Larger batches = fewer transactions = faster.

### 2. Download Parallelism
OkHttp has a dispatcher. Check `maxRequestsPerHost`. Default is 5 per host - may need increasing.

### 3. Parse Parallelism
SourceLoader uses internal thread pool. Check `PARSER_COUNT`. Each parser spawns threads for:
- Reading lines
- Parsing lines (N threads)
- Inserting to DB

### 4. Pipeline Overlap
Ideal: Check -> Download -> Parse should overlap. Check if we're waiting unnecessarily between phases.

### 5. Connection Pool
OkHttp connection pooling. Check `ConnectionPool` size. Reusing connections is faster than creating new ones.
