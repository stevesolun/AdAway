---
description: Deep SQLite/Room database analysis
---

# /db-analyze

Deep SQLite/Room database analysis.

## Trigger
When user runs `/db-analyze` or asks about database performance, schema, or Room configuration.

## The C Engineers' View of SQLite

SQLite is C. Room is just Java wrapping it. Think at the SQLite level:

### WAL Mode
```java
// In AppDatabase.java, check for:
.setJournalMode(JournalMode.WRITE_AHEAD_LOG)
// or via SQLiteOpenHelper callback
```

WAL = Write-Ahead Logging. Critical for concurrent reads during writes.

### Page Cache
- Default page size: 4KB
- Cache size affects memory vs I/O tradeoff
- Check if we're thrashing with small transactions

### Transaction Boundaries
```java
// Good: One big transaction
@Transaction
void insertAll(List<Item> items);

// Bad: Many small transactions
for (item : items) { dao.insert(item); }  // Each is a transaction!
```

## The Java Engineers' View of Room

### DAO Patterns
```java
// Check @Insert onConflict strategy
@Insert(onConflict = OnConflictStrategy.REPLACE)

// Check @Query efficiency
@Query("SELECT * FROM table WHERE id IN (:ids)")  // Better than loop

// Check @Transaction usage
@Transaction
@Query("SELECT * FROM parent")
List<ParentWithChildren> getAll();
```

### Threading
```java
// Room's internal threading
// - Queries return on calling thread (or suspend)
// - Writes dispatch to internal executor
// - LiveData observes on main thread

// Conflict with AppExecutors?
```

### Observable Queries
```java
// LiveData queries re-run on ANY table change
@Query("SELECT * FROM hosts")
LiveData<List<Host>> getAll();  // Fires on any hosts insert!
```

## Analysis Targets

1. **AppDatabase.java** - Configuration, migrations, callbacks
2. **HostsSourceDao.java** - Source CRUD operations
3. **HostListItemDao.java** - Bulk insert patterns (CRITICAL)
4. **HostEntryDao.java** - Sync operation (CRITICAL)
5. **Migrations.java** - Schema evolution

## Output Format

```
## Database Analysis

### Configuration
| Setting | Value | Optimal? |
|---------|-------|----------|
| Journal Mode | [WAL/DELETE/etc] | [Yes/No - why] |
| Page Size | [size] | [assessment] |
| Cache Size | [size] | [assessment] |

### Schema Review
[Entity relationship summary]
[Index coverage assessment]

### Query Analysis
| DAO Method | Type | Efficiency | Issue |
|------------|------|------------|-------|
| insertAll() | INSERT | [rating] | [any issue] |
| sync() | COMPLEX | [rating] | [any issue] |

### Transaction Patterns
- Bulk inserts: [batched/individual]
- Transaction boundaries: [appropriate/too granular]
- Conflict resolution: [strategy assessment]

### Observable Queries
- LiveData observers: [count]
- Trigger frequency: [assessment]
- Spam risk: [low/medium/high]

### The C Engineers Say
[SQLite-level insight]

### Recommendations
1. [Priority recommendation]
2. [Secondary recommendation]
```
