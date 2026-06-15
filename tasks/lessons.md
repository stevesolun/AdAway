# Lessons

## 2026-06-11 - Let Running Experts Finish

- When the user explicitly says not to close a running agent, do not summarize around it or close it for convenience.
- If an expert is still working, keep it open unless it is clearly stuck, harmful, or the user reverses direction.
- Update the task ledger after corrections before continuing implementation so process mistakes become durable rules.
- If a reviewer is slow or absent from an interim synthesis, wait or rerun the reviewer instead of treating its domain as closed.

## 2026-06-12 - Verification Can Be the Long Pole

- When the user asks why work is taking a long time, answer with the active long-running command,
  its scale, and the current result instead of hand-waving.
- Do not stop a worst-case benchmark early after the user explicitly says to let it finish.
- For Android instrumentation scripts, parse `am instrument` output for failures; do not trust
  process exit code alone.
- During Android work, proactively call out emulator/instrumentation and full Gradle gates as
  the slow path before the user has to ask for a status explanation.

## 2026-06-14 - Preview Destructive Profile Changes

- Treat saved filter profile application as a destructive bulk action when it can disable active
  protection lists.
- Show impact counts and a cancelable confirmation before mutating source state.
- Keep the impact diff pure and testable so UI wording and DAO mutation can be verified
  separately.
- Bind confirmation to the exact profile data that was previewed, and let the DAO own fresh source
  reads plus transactional mutation.
- Do not normalize user-visible saved set names into active identity keys; preserve identity or
  move to opaque ids.
- Validate saved set display names with canonical collision checks so built-in profile names and
  case/whitespace duplicates cannot spoof separate identities.

## 2026-06-15 - Protect Product Identity Assets

- Do not replace the app logo or launcher identity with a generic concept mark unless the user has
  explicitly made a branding decision.
- When a visual asset is identity-critical, add a lightweight regression guard that rejects the
  wrong shape as well as accepting the intended one.
- Include launcher fallback assets and manifest icon hooks in branding checks; testing only the
  visible vector logo can still leave launcher icons broken.
- Treat user-visible branding regressions as product bugs, not cosmetic cleanup.
