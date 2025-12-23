---
description: Show the team and available commands
---

# /team

Show the team and available skills.

## Trigger
When user runs `/team` or asks "what can you do" / "help".

## The Team

```
╔══════════════════════════════════════════════════════════════════╗
║                    THE ADAWAY ENGINEERING TEAM                   ║
╠══════════════════════════════════════════════════════════════════╣
║                                                                  ║
║  🔧 C ENGINEERS          Memory. Pointers. The metal.           ║
║     └─ /c-review         Deep native code analysis              ║
║                                                                  ║
║  ☕ JAVA ENGINEERS       Android. Lifecycle. Threading.          ║
║     └─ /java-review      Android code quality review            ║
║                                                                  ║
║  🏗️  ARCHITECTS          System design. Data flow. Big picture.  ║
║     └─ /trace-dataflow   Map data flow through system           ║
║     └─ /ultrathink       Deep design thinking                   ║
║                                                                  ║
║  ⚡ PERFORMANCE TEAM     Profiling. Bottlenecks. Speed.          ║
║     └─ /investigate-perf Full performance investigation         ║
║     └─ /review-threading Threading and concurrency analysis     ║
║     └─ /db-analyze       SQLite/Room deep dive                  ║
║                                                                  ║
║  📚 GIT HISTORIANS       Clean history. Atomic commits.          ║
║     └─ /git-history      Commit quality and PR prep             ║
║                                                                  ║
╚══════════════════════════════════════════════════════════════════╝
```

## Quick Reference

| Skill | When to Use |
|-------|-------------|
| `/investigate-perf` | App feels slow, bulk operations lag |
| `/trace-dataflow` | "How does X work?" |
| `/review-threading` | Race conditions, ANR, deadlocks |
| `/db-analyze` | Database performance, schema questions |
| `/c-review` | Before touching native code |
| `/java-review` | Code quality check |
| `/ultrathink` | Complex design decisions |
| `/git-history` | Preparing commits or PRs |
| `/team` | This help screen |

## Current Mission

> Transform a functional ad-blocker into an *insanely great* one.

**The Problem:** App clogs during bulk filter updates.

**Start Here:**
1. Run `/investigate-perf` to analyze the bottleneck
2. Run `/trace-dataflow update` to understand the flow
3. Run `/db-analyze` to check database configuration
4. Run `/ultrathink` when you have a hypothesis

## Philosophy Reminder

> "Take a deep breath. We're not here to write code. We're here to make a dent in the universe."
