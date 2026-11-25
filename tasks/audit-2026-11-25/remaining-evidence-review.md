# Remaining Task Evidence Review

Review date: 2026-11-25. Bounded scope: all 60 files under `tasks/benchmarks/**`
and the 11 root `tasks/*.md` / `tasks/*.tsv` files present in the initial inventory.
The central audit directory and other owners' sidecars were excluded from this
evidence review. The initial outputs were the three `remaining-evidence-*` sidecars.
After explicit approval, the two historical summary documents for REM-EVID-001
and REM-EVID-003 were corrected; raw reports/logs remain unchanged. REM-EVID-002
has exact main-owned cell proposals only. The separately authorized earlier Home
fixture correction is documented below; no app edit was made in this follow-up.

## Import Contract

- `remaining-evidence-files.csv` contains exactly 64 FULL ASSISTANT READ rows:
  55 benchmark files and nine root Markdown documents. Eleven are empty stderr
  files, completely inspected as zero-byte artifacts; emptiness is not a pass.
- The seven semantic-only files below are deliberately ABSENT from that CSV.
  Do not import their hashes as whole-file reviewed from this report.
- Hashes identify the exact bytes read, not an APK, release, branch tip or proof
  that the referenced implementation is unchanged. All 64 full-read hashes and
  seven semantic snapshots matched a second filesystem scan before sidecar writing.
  Main must reject/review a hash mismatch if a file changes afterward.
- Full read means the assistant saw the complete file without truncation.
  Structured parse, search, excerpt and hash-only work do not satisfy that rule.
- No central task/workflow/bug ledger was changed. No new product fix or runtime
  command, Gradle, adb, build, staging, commit or release operation was performed.

## Findings And Disposition

- REM-EVID-001 (P2): fixed under approval. The June27 license summary now assigns
  each preserved report its actual commit, explicitly marks the mixed bundle and
  links the separate June28 historical refresh. No raw identity was rewritten.
- REM-EVID-002 (P2): publication conflict verified; exact guarded OCT-005 status
  and retest_status replacements are below. Application remains main-owned and
  pending. No central TSV cell was edited.
- REM-EVID-003 (P3): fixed under approval. The June27 command is retained but its
  live outcome is explicitly unestablished; the unpaired heartbeat narrative is
  separated and the explicit June28 prepared-device result is linked.

The bug sidecar retains original evidence and records these dispositions. These
are documentation/evidence corrections, not new app behavior or runtime passes.

## Historical Versus Current Proof

- June source-boundary passes preserve MIT/legal and signed-artifact blockers.
  Debug APK/SBOM and ephemeral directRelease dry runs are not production-signer,
  installation, self-update, physical-device or legal clearance.
- June27 shell-root failure is explicitly superseded by June28 writable-system
  evidence. That follow-up still fails the app/libsu root grant before mutation.
  Shell write/restore does not close real app-owned root apply.
- June28 prepared VPN lifecycle explicitly records direct instrumentation status 0
  and cleanup. It is historical prepared-device proof, not a November live result.
  Unprepared class runs may skip; do not turn build success into no-skip coverage.
- The June consent bundle corroborates a real AdAway tunnel, not loaded blocking
  rules. The parsed Home dump shows zero blocked rules. This is consistent with
  retaining the later October first-run filtering defect as separate evidence.
- Continuation75 preserves the original SQLiteFullException and failed build;
  the later 16g success is a different environment. Continuation93 is a
  stage-seeded allow-heavy rebuild, not a fresh five-million-line parse.
  Runtime010 is the separate full-parse narrative. Their stdout logs preserve run
  outcomes/durations but not the detailed benchmark metric lines quoted in prose.
  Metrics are recorded historical claims, not independently remeasured here.
- The June UX packet reports five variants / 40 captures and 45 unchecked signoff
  items. Its named packet and the continuation93 capture directory are absent from
  this checkout. That is an archive availability limit, not proof those runs never
  happened. No images were visually inspected in this pass.
- Preserve the current UX capture/loading boundary: a Discover capture while
  Loading does not prove loaded catalog labels or row controls. Historical UX
  capture success or a small spot-check cannot replace current loaded-state or
  human matrix review.
- October reports distinguish initial snapshots, worker compilation, deferred
  connected runs, parent follow-ups, CI and signed/minified artifact smoke.
  The 13.5.4b document explicitly remains a pre-publication checkpoint with a local
  APK hash, not a hash claim about the eventual CI asset. These dated reports are
  historical context only, never proof for the current app/test revision.
- No current full-green claim: the earlier full488 evidence retains 476 pass,
  10 skip and 2 fail; the corrected focused22 and separate live4 do not retroactively
  make it green. Main's subsequent phase-three96 has 94 pass / 2 fail.
  This pass did not re-audit those full runtime artifacts or change their ledgers.
  The active root todo/status row explicitly keeps the expanded November audit open.

## Link And Structure Checks

Read-only filesystem/parser checks, not runtime validation:

- All eight ordinary local Markdown links in the scoped documents resolve.
- All 574 parsed source/test references beginning app/, scripts/, tasks/ or .github/
  in the TSV's code_evidence/existing_test_evidence fields exist as files or
  directories. Existence does not verify an anchor, method, assertion or coverage.
- The Markdown benchmark-reference scan found 47 per-document unique tokens.
  Forty-five concrete targets exist; two todo tokens are abbreviated
  `tasks/benchmarks/...` and `...final-postfix-*` prefixes, not broken concrete links.
- Archived Windows/temp/build output paths remain historical environment locations.
  No automatic rewriting or requirement that those transient paths exist now.
- October release13.5.4b and Israeli-site report directories exist locally, but their
  contents were not opened in this bounded task. Existence is not artifact proof.
- The initial review did not fetch historical external CI/release links. The approved
  follow-up below read only the v13.5.3b GitHub release metadata; it did not re-run
  CI, fetch a current manifest or download/reverify the APK. Official Android API
  pages were separately checked only for the earlier Home correction.
- No test is mapped as executing a behavior merely because its name appears here;
  current test implementation/coverage beyond the owned Home file is out of scope.

## Semantic-Only Snapshots

### tasks/benchmarks/2026-06-27-runtime008-vpn-smoke-final-postfix-consent.xml

- SHA256: `496fbb857adee61e397840b5d593f58a75dd52d6352d2d96361fb66319f2c585`.
- REXML parse: 15 nodes, com.android.vpndialogs, Connection request plus Cancel/OK. Dump is a snapshot, not proof the button was clicked.
- Assessment: semantic-only / partial assistant context; exclude from whole-file reviewed imports.

### tasks/benchmarks/2026-06-27-runtime008-vpn-smoke-final-postfix-onboarding-before.xml

- SHA256: `9412b8ac05f84087ddc32c4d211cd23b6c881bf00eae59de2a2cef784a650be2`.
- REXML parse: 25 nodes, org.adaway, VPN/Root choices and Start Protecting.
- Assessment: semantic-only / partial assistant context; exclude from whole-file reviewed imports.

### tasks/benchmarks/2026-06-27-runtime008-vpn-smoke-final-postfix-home-after.xml

- SHA256: `94cfd0f2078db9a510a42167a8456a6dd8139128a0018842aa04864a8546af9e`.
- REXML parse: 75 nodes, org.adaway, Protection active and VPN running. Also 0 blocked, 60 allowed, and 0/0 freshness totals: tunnel creation is not proof of downloaded filtering rules.
- Assessment: semantic-only / partial assistant context; exclude from whole-file reviewed imports.

### tasks/benchmarks/2026-06-27-runtime008-vpn-smoke-final-postfix-dumpsys-connectivity.txt

- SHA256: `37de245a8f9578e470a75be038c1d75e512fb49d43acf6c9288c564023bb7188`.
- 317 lines processed; targeted line 105 has VPN CONNECTED, tun0, AdAway, OwnerUid 10195 and bypassable=false. Correlates with the fully read vpn dump; unrelated connectivity text not assistant-read.
- Assessment: semantic-only / partial assistant context; exclude from whole-file reviewed imports.

### tasks/benchmarks/2026-06-27-runtime008-vpn-smoke-final-postfix-logcat.txt

- SHA256: `ebd9fc278abc9b232035388628f79a0b96e1a347af57b85e3f7019746715890d`.
- 1452 lines scanned for selected lifecycle/crash markers: start at 817, START at 869, org.adaway/tun0 at 1061, established at 1062, RUNNING at 1211. No selected fatal/ANR/disk-full markers found; not an exhaustive log or crash-free review.
- Assessment: semantic-only / partial assistant context; exclude from whole-file reviewed imports.

### tasks/todo.md

- SHA256: `7197bca4eb4e7c61be28d1fddd93f8c2701ee434a436b3d2fb5873625ab49ad1`.
- 11904-line semantic scan: 566 level-two headings, 1472 checked and 16 unchecked task lines. First 90 and last 100 lines inspected; intervening narrative not fully read. Active November header explicitly says no green full consolidated rerun and historical October delivery does not complete this audit.
- Assessment: semantic-only / partial assistant context; exclude from whole-file reviewed imports.

### tasks/user-story-status.tsv

- SHA256: `42a63d6477e1294094b1e6b4f4f197ec9145cdf0fb0198ac486ce0336d2c9dc7`.
- CSV parser with tab delimiter: 104 rows, 15 columns each, 104 unique well-formed IDs, UTF-8 valid; required ID/status/test_state/priority fields nonempty; P0/P1/P2 priorities only. All IDs/statuses inspected and selected complete rows inspected. Other long narrative cells not fully assistant-read; see REM-EVID-002.
- Assessment: semantic-only / partial assistant context; exclude from whole-file reviewed imports.

## Home Full-Read And Freeze Handoff

The frozen Home review and complete original 484-line test were read before this
remaining-evidence pass. The Home import CSV was reduced to its eight full-read
rows; partial product context rows were removed. No partial row is restored.
Main's updated nine-row Home workflow ledger was fully reread after the final
hash check detected its change; only this lane's inventory hash was refreshed.
The corrected Home review was also fully reread, with SHA256
`b8973ca033cb4b10eeeb91b7070d63c9e522137b6b85835567405023a436ed14`.

Main's phase-three result then took priority. The Private DNS fixture incorrectly
required the first attempted settings intent to resolve even though production
already falls back after ActivityNotFoundException. Main's actual API34 probe
found no direct handler and resolved WIRELESS_SETTINGS to
`com.android.settings/.Settings$NetworkDashboardActivity`.

The dedicated test now records ordered attempts, accepts only the production
direct or DNS-then-wireless chain, resolves the destination and requires its real
external foreground window. Global settings mutation, system Back, refreshed
Home state and recreation assertions remain. No product/manifest/permission edits.

Corrected source was fully reread (501 lines), whitespace-checked and frozen at
2026-11-25 11:32:29 UTC:
`cb5d718383a1ea1ff32ba60eba4787d3212cbbc532ee63acd7beca47f6af93a0`.

Main owns build and rerun. The three original passes and one DNS fixture failure
remain recorded at the prior source/APK boundary in `home-flow-fixes-tests.csv`
and `home-flow-fixes-review.md`. This correction is not a new passing result.

## Approved Evidence Corrections

Approved scope: REM-EVID-001..003 only. Two historical summary edits, three
existing sidecars updated, no raw log/report edit and no central TSV edit.
Both corrected summaries were fully reread after editing.

| Summary | Before SHA256 | Final SHA256 |
| --- | --- | --- |
| tasks/benchmarks/2026-06-27-rel001-license-boundary-evidence.md | c61413f0e42a31ae3ab39940697c036be14b2027bd5a419509ea490f58c11ed6 | 00be0e1f230de24df356cfa15442666e47d1ec10ff3004b06157b204e7a21d15 |
| tasks/benchmarks/2026-06-27-runtime009-vpn-lifecycle-boundary.md | 144a8f5a21141ab4ca85327797fec455d1ac5da10e6a2de25b80a84f5f3a8a5e | 16e944ba2b0b7d4650dc2c3aeb9dc7171a1e946ae1ae8db3dd28d07109c71e25 |

The existing 64-row full-read inventory now carries these final hashes; the
seven original semantic-only snapshots remain excluded. No hash-only reading
has been promoted to full review.

### Old Publication Verification

Read-only GitHub query at approximately 2026-11-25 11:39:36 UTC:
`gh release view v13.5.3b --repo stevesolun/AdAway --json tagName,isDraft,isPrerelease,publishedAt,url,assets`.

The [v13.5.3b release](https://github.com/stevesolun/AdAway/releases/tag/v13.5.3b)
reports publication at 2026-10-12T22:38:28Z, non-draft, prerelease, six assets.
Its `AdAway_13.5.3b.apk` asset reports size 4,139,530 bytes and SHA256
`9c7f8f76ca173f9ae6430322f931c93d7b3015f7e9be6eb0918278599e55ddfa`.
This matches the saved passed verifier report for source
`cf453b1a7d2f250e143a4d86fce7c79f1f3d2b7c`, generated
2026-10-12T22:40:35.965397Z. The report records checksum, manifest signature and
payload passes plus six verified attestations. Those checks were not rerun here.
The old publication is established; no claim is made that it is the newest release.

The later saved v13.5.4b verifier records a different published APK hash,
`d33d02aabf4d502626fc7ae08c509ff28e3977712fb8ecdb5659cfc6f335ab63`,
and its saved release JSON reports publication on 2026-10-14T20:38:40Z.
Its saved publication-run JSON reports success. These are corroborating later
historical records, not proof of the current November candidate or its CI.
They were not used to silently overwrite the explicitly pre-publication
`tasks/release-13.5.4b.md` checkpoint.

Supplemental local records fully read for this approved check (not added to the
bounded repository-file import inventory because they are build/report artifacts):

| Fully Read Local Record | SHA256 |
| --- | --- |
| app/build/reports/published-release/verification-report.md | 153961ecab60a1e6b95c61dff5c33631083ef8668cde93e31667cd9dc16f806d |
| app/build/reports/published-13.5.4b/verification-report.md | a0f67beafe7a86307f8a8e4f3fc34582bb7bf2ef145d3b9d97de6d0c79eda7f8 |
| app/build/reports/published-13.5.4b/release.json | b854e86689775382c0b3eac46005166a2762eb697c3a7409d066c80834ebb7b3 |
| app/build/reports/published-13.5.4b/publication-run.json | 93ae165613a4ab62d8759ec9314842526de64d297037ea1ccd730a8e778f39fc |

The saved manifests' expiry timestamps are October26/October28. That limits
these historical reports; it does not establish whether live metadata has since
been refreshed. No current-feed validity, fresh signature verification, full
November green run, physical-phone approval, legal clearance or release signoff
is claimed. No action was taken on GitHub.

### OCT-005 Cell Proposal

Apply only the two named cells, after main checks that the expected values still
match. The SHA256 identifies the read snapshot, not a whole-file review. Other
rows may change concurrently; do not overwrite the file from this snapshot.
Keep all remaining OCT-005 cells unchanged. Exact replacement strings follow:

```json
{
  "target_path": "tasks/user-story-status.tsv",
  "row_key": {
    "story_id": "OCT-005"
  },
  "read_snapshot_sha256": "502a8b9a57dadce6b844e936db35518c38ac62d89b426b418cd47523ffc498e9",
  "updates": [
    {
      "column": "status",
      "expected_value": "Locally verified candidate; protected CI and publication pending",
      "replacement_value": "Historical v13.5.3b publication verified; current November audit and release remain open"
    },
    {
      "column": "retest_status",
      "expected_value": "Local consolidated proof: 591 JVM tests with 3 skips; Latest full API 34 rerun: 297 tests with 6 intentional skips and zero failures, including live VPN/DNS; two CI test synchronization fixtures corrected. Signed APK smoke and detailed limits: tasks/review-2026-10-12.md. PR 10 CI passed; publication follow-up CI remains open for 13.5.3b after v13.5.2b failed closed on apksigner output drift.",
      "replacement_value": "Historical October local proof: 591 JVM tests with 3 skips; 297 API 34 tests with 6 intentional skips and zero failures, including live VPN/DNS; two CI synchronization fixtures corrected; PR 10 CI passed (tasks/review-2026-10-12.md). The v13.5.2b publication failed closed on apksigner output drift; that tag is preserved. The v13.5.3b beta was subsequently published at 2026-10-12T22:38:28Z. Read-only GitHub release metadata checked 2026-11-25 confirms a non-draft prerelease with six assets and AdAway_13.5.3b.apk SHA256 9c7f8f76ca173f9ae6430322f931c93d7b3015f7e9be6eb0918278599e55ddfa, matching app/build/reports/published-release/verification-report.md (historical checksum, manifest signature/payload and six-artifact attestation verification passed). Publication URL: https://github.com/stevesolun/AdAway/releases/tag/v13.5.3b. This is old publication/artifact evidence, not current manifest validity, a November build/test pass, or physical-device, stable-release or legal approval."
    }
  ]
}
```

REM-EVID-002 remains pending main's application/review of these cells, not closed
by creating this proposal. The current November audit and release remain separate.

### Correction Validation

- All 64 import-inventory hashes match the final files; all rows remain full-read only.
- Both new historical follow-up links resolve. The proposal JSON parses and both
  expected OCT-005 cell values still match main's central row at the final check.
- Before/after hashes changed only for the two approved benchmark summaries.
  The other 58 benchmark files, including every preserved raw report/log, match.
- Central TSV stayed at SHA256
  `502a8b9a57dadce6b844e936db35518c38ac62d89b426b418cd47523ffc498e9`.
  Frozen Home source stayed at SHA256
  `cb5d718383a1ea1ff32ba60eba4787d3212cbbc532ee63acd7beca47f6af93a0`.
- Scoped diff whitespace check and direct whitespace scans passed. No app edit,
  Gradle, adb, runtime test, staging, commit or publishing operation was performed.
