# Pre-Commit Hook: Git Historians Standards

## Trigger
Before any git commit operation.

## Commit Message Validation

### Format Check
```
<type>: <subject>

<body - the WHY>
```

### Allowed Types
- `fix:` - Bug fix
- `feat:` - New feature
- `perf:` - Performance improvement
- `refactor:` - Code restructuring
- `docs:` - Documentation
- `test:` - Tests
- `chore:` - Maintenance

### Subject Rules
- Present tense, imperative mood ("Add" not "Added")
- No period at end
- Max 72 characters
- Capitalize first letter

### Body Rules
- Explain WHY, not just WHAT
- Wrap at 72 characters
- Reference issues if applicable

## Example Good Commit
```
perf: Batch database inserts in groups of 500

Room s @Insert creates a transaction per item when called individually,
causing significant I/O overhead during bulk operations. This change
batches inserts to reduce transaction count from N to N/500.

Measured improvement: 10,000 inserts reduced from 45s to 3s.

Closes #123
```

## Example Bad Commits (reject)
```
Update SourceLoader.java          # No type, no why
fixed the bug                     # Past tense, no type, no why
WIP                               # Not atomic
```

## Auto-Append
All commits should end with:
```

[bot] Generated with [Claude Code](https://claude.ai/code)

Co-Authored-By: Claude <noreply@anthropic.com>
```
