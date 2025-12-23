---
description: Deep architectural thinking before any implementation
argument-hint: [problem]
---

# /ultrathink

Deep architectural thinking before any implementation.

## Trigger
When user runs `/ultrathink [problem]` or faces a complex design decision.

## Philosophy

> "Take a deep breath. We're not here to write code. We're here to make a dent in the universe."

This is not a coding skill. This is a *thinking* skill.

## Process

### Phase 1: Question Everything
Before any solution, ask:
1. Why does it work this way currently?
2. What constraints shaped this design?
3. What if we started from zero?
4. What would the most elegant solution look like?
5. What would a user never notice but always feel?

### Phase 2: Read Like Archaeologists
Understand the history:
1. What patterns exist in the codebase?
2. What scars from past bugs or rewrites?
3. What implicit contracts between components?
4. What would break if we changed this?

### Phase 3: Design Before Typing
Create clarity:
1. Sketch the architecture (ASCII diagrams)
2. Draw the data flow
3. Identify the threading model
4. Map the failure modes
5. Make the plan so clear that implementation feels inevitable

### Phase 4: Simplify Ruthlessly
Challenge complexity:
1. Can we delete code and maintain function?
2. Are there fewer moving parts possible?
3. What's the minimum viable change?
4. Will this be obvious to read in 6 months?

### Phase 5: Measure, Don't Guess
Plan for evidence:
1. What metrics will prove this works?
2. How will we test the hypothesis?
3. What's the rollback plan?

## Output Format

```
## Ultrathink: [Problem Statement]

### The Current Reality
[What exists, why it exists, what it costs]

### The Ideal State
[What perfection looks like, unconstrained]

### The Constraints
[What's actually fixed vs assumed fixed]

### The Design
[ASCII architecture diagram]
[Data flow]
[Threading model]

### The Path
1. [Step 1] - Why this first
2. [Step 2] - Why this second
...

### The Evidence Plan
- Metric 1: [what to measure]
- Metric 2: [what to measure]
- Success criteria: [definition]

### The Risk
[What could go wrong, mitigation]

### The Verdict
[Does this make a dent in the universe?]
```

## When NOT to Use This

- Simple bug fixes with obvious solutions
- Style/formatting changes
- Direct feature requests with clear specs

Use this for:
- Performance mysteries
- Architecture decisions
- "It works but feels wrong"
- "We've tried everything"
