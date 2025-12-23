# Pre-Edit Hook: Database Schema Protection

## Trigger
Before editing:
- `app/src/main/java/org/adaway/db/Migrations.java`
- `app/src/main/java/org/adaway/db/entity/*.java` (entity annotations)
- `app/src/main/java/org/adaway/db/AppDatabase.java` (version number)

## Action

Display warning:
```
[WARNING] DATABASE SCHEMA CHANGE

You are about to modify the Room database schema.

Critical reminders:
1. Increment database version in AppDatabase.java
2. Write a migration in Migrations.java
3. Test upgrade path from previous versions
4. Schema changes are permanent for users

Current version: [read from AppDatabase.java]
```

## Validation Checklist
- [ ] Database version incremented?
- [ ] Migration written for version N-1 -> N?
- [ ] Migration tested with existing data?
- [ ] Fallback behavior defined?

## Required Actions
If schema changes detected:
1. Run `/db-analyze` to understand current state
2. Document the migration strategy
3. Test on a device with existing data
