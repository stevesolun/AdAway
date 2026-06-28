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

## 2026-06-15 - CI Hangs Need Evidence

- When a CI job is pending or running long without live logs, treat missing diagnostics as a CI
  defect, not only as elapsed time.
- Bound emulator boot and instrumentation steps separately so failures point at the stalled layer.
- Wrap `adb wait-for-device` itself; an outer boot-step timeout is too coarse when diagnosing
  emulator startup.
- Bound every diagnostic `adb` call too; failure collection often runs specifically when no device
  is reachable.
- Force `ANDROID_AVD_HOME` and verify `emulator -list-avds` before launch; otherwise `avdmanager`
  can appear to succeed while `emulator -avd` cannot find the created AVD.
- Always upload connected-test reports, emulator logs, logcat, and device state on failure before
  claiming the CI lane is understood.

## 2026-06-27 - Keep CI Product-Oriented

- Use focused local tests to protect the product behavior touched by a slice, then let PR CI prove
  repository integration.
- Do not turn release hardening into an endless loop; keep a finite gate list with explicit
  evidence, owners, and external/manual blockers.
- Prefer small verified commits over large unverified batches when the change can damage runtime
  behavior, security posture, or user trust.
- When a focused gate finds a real user-facing bug, fix the product behavior and keep the failing
  proof as the regression guard.
- When the remaining work is a finite set of local proof/documentation gaps, batch the closure into
  one small verified commit instead of repeatedly rediscovering the same board.
- Treat consent-, hardware-, legal-, or human-gated work as explicit product release gates, not as a
  reason to keep looping on locally unfinishable automation.

## 2026-06-28 - Converge With Gate Boundaries

- Split locally finishable app-owned behavior from external release smoke before starting a new
  slice.
- Close rows only when executable evidence proves the app-owned contract, and keep platform,
  legal, hardware, consent, or human-review caveats visible in the tracker.
- Add tracker guardrail tests when changing status language so CI protects the product boundary
  from optimistic wording drift.
- When the user calls out looping, stop rediscovering the same release board: pick the strongest
  locally finishable row, finish it with proof, and leave external gates explicitly blocked.

## 2026-06-28 - Re-Probe Stale Blockers

- When the user challenges an external blocker, re-probe the current device/emulator capability
  before repeating old evidence.
- Treat a newly writable rooted emulator as a new proof surface: first prove controlled shell
  write/restore, then run the real app-owned path behind an explicit opt-in guard.
- Do not close a root-hosts gate from `adb root` alone; close it only when the app/libsu path
  writes the generated hosts file and restores the original system file afterward.

## 2026-06-28 - Exercise The Reported Control

- When the user reports that a visible toggle is broken, test the actual switch/toggle path, not
  only the adjacent row-click or dialog path.
- Keep bulk-safety gates separate from explicit single-item user actions so conservative automation
  does not make intentional controls feel disabled or broken.
