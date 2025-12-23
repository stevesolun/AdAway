---
description: Analyze and craft git history like the Git Historians
---

# /git-history

Analyze and craft git history like the Git Historians.

## Trigger
When user runs `/git-history` or asks about commits, history, or preparing a PR.

## The Git Historians' Principles

> "Clean commits, meaningful PRs, bisectable history."

### Atomic Commits
Each commit should:
- Do exactly one thing
- Be independently revertable
- Pass all tests
- Tell a complete story

### Commit Message Format
```
<type>: <what changed>

<why it changed - the important part>

<any breaking changes or notes>
```

**Types:**
- `fix:` - Bug fix
- `feat:` - New feature
- `perf:` - Performance improvement
- `refactor:` - Code change that neither fixes nor adds
- `docs:` - Documentation only
- `test:` - Adding tests
- `chore:` - Maintenance

### Present Tense, Imperative Mood
```
Good: "Add batch insert optimization"
Bad:  "Added batch insert optimization"
Bad:  "Adds batch insert optimization"
```

### The Why Matters
```
Good:
  perf: Batch database inserts in groups of 500

  Room's @Insert with individual items creates a transaction per item,
  causing significant overhead during bulk operations. Batching reduces
  transaction count from N to N/500.

  Measured: 10,000 inserts reduced from 45s to 3s.

Bad:
  Update SourceLoader.java
```

## Analysis Commands

### View Recent History
```bash
git log --oneline -20
git log --graph --oneline --all -20
```

### Find When Something Broke
```bash
git bisect start
git bisect bad HEAD
git bisect good <known-good-commit>
# Then test at each step
```

### Analyze a File's History
```bash
git log --follow -p -- path/to/file
git blame path/to/file
```

## Output Format

```
## Git History Analysis

### Recent Commits
[Summary of recent changes and their quality]

### Commit Message Assessment
| Commit | Quality | Issue |
|--------|---------|-------|
| abc123 | Good | - |
| def456 | Needs work | Missing 'why' |

### Suggested Commit Structure for Current Work
[Proposed atomic commits for pending changes]

### PR Description Template
## Summary
[What this PR does]

## Motivation
[Why this change is needed]

## Changes
- [ ] Change 1
- [ ] Change 2

## Testing
[How this was tested]

## The Git Historians' Verdict
[Assessment of history cleanliness]
```
