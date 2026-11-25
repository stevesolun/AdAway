# Full Repository Audit

Objective: review the repository file by file, log bugs in CSV, fix confirmed bugs,
and exercise the UI controls and workflows including back/cancel/error/recreation
paths. This is broader than the October bounded release review. The goal stays
active until coverage and fixes are verified, not merely until a test suite passes.

Baseline: `d51eb7aea06871c2c6ad2bbedb9ddbaaaca48d77` (merged 13.5.4b).
Initial inventory: 2,534 tracked files. Branch: `codex/full-codebase-audit`.

## Evidence Rules

- `files.csv` includes tracked and non-ignored new files. Each non-generated file
  has a content hash. Inventory alone leaves review status `pending`.
- Individual reviewers record path, hash, assessment, and bug IDs in `*-files.csv`.
  Import these with `python3 scripts/audit_inventory.py --record <report>`.
- A changed reviewed file becomes `stale` until reviewed again. `--check` catches
  inventory/hash drift, not completion. Binary/resource/vendor files are included;
  their presence or successful build does not count as a manual review.
- `bugs.csv` is the central bug register. Source-supported findings, reproduced
  failures, implemented fixes and verified closure are separate statuses.
- `*-workflows.csv` records control/chain expectations and variants. Static tracing
  is not executed UI QA. Screenshots, tests and exact commands must support closure.
- Existing October records remain historical. Their test counts are not current
  proof for changed files. Do not weaken branch protection or tests to close a row.
- Android VPN is the primary runtime surface per the user's product direction.
  Root source still belongs in code review; privileged root runtime is not claimed.
- Physical devices, external service failures and manual permission boundaries
  remain explicit gaps, never implicit passes.

## Work Lanes

1. Main: complete repository inventory; Home controls and consent; integrated QA.
2. Data: all database and source-import files and cross-boundary behavior.
3. VPN: all DNS/VPN and VPN-model files, protocol and lifecycle cases.
4. Release: updater, installer, build/CI, release/security/native build boundaries.
5. Sources UX: Discover and Sources controls, selection, URL and operation chains.

Remaining packages, translations/assets, native vendor sources and all unassigned
controls stay visible as pending. A finished lane is not whole-goal completion.
