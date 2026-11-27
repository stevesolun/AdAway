# Non-root Android release handoff, 26 November 2026

## Protected baseline

[PR #19](https://github.com/stevesolun/AdAway/pull/19) merged as
`3a0878fae` after all six required checks passed. Its independent
[hosted emulator run](https://github.com/stevesolun/AdAway/actions/runs/45747893897/job/139671577888)
completed **698 cases: 678 passed, 20 skipped, zero errors or failures**.
This uninterrupted run supplements the separate [local shard/replay record](decision.md).

## What a phone user can expect

The signed pre-follow-up build (`a1b347a01ae9a70cbb2f6148618d40bf2e2af4f966e0326c7e7120f5ec03b2ac`)
was installed on a non-root Android 14 emulator. Real checkbox selections in
Discover added and removed MVPS HOSTS, MoaAB and Hosts Blocking. A mixed
selection added the compatible source and explicitly skipped one incompatible
list. Four additions and four removals were observed, including cold relaunches.
Enabled and imported are different states: two newly enabled sources initially
had zero rules. The signed app then received 5,000 offline inputs (seed
2026112658); no dropped events, crash, ANR or captured traffic. After a
deliberate app-data reset and VPN re-onboarding, example.com resolved and
doubleclick.net received an AdAway negative DNS answer. This is clean-state
recovery, not proof that a reset preserves configuration.

On a separate emulator, public 13.5.6b ran only the custom Firebog AdGuard DNS
URL. doubleclick.net resolved before protection and not afterward, while
example.org remained reachable and Chrome displayed RFC Editor and Debian.
The earlier signed upgrade used only Dan Pollock's list and loaded Debian and
Python.org. Neither test demonstrates cosmetic ad removal or arbitrary browser
filter syntax. Challenged pages were not counted.

## Discover correction

[Issue #21](https://github.com/stevesolun/AdAway/issues/21): a committed removal could
still appear in Show subscribed during a runtime rebuild, and the previous
subscription count could read like the result of that removal. The follow-up
updates visible membership from the committed deletion, counts the removals and
labels the previous subscription result. It preserves both selected actions
for checked rows; when none has a saved subscription, Remove explains that.

The older debug app retained two rows instead of one with the rebuild held.
The follow-up passed ten focused Android checks, including the held rebuild,
unrelated-source preservation, a stale cached URL, mixed selection and revisit;
33 focused JVM checks passed. The evidence is in
`app/build/reports/audit-2026-11-25/protected-ui-5588/proposal/`.
The 5,000-input result above was not obtained on this follow-up.

## Android backup notification

[Issue #20](https://github.com/stevesolun/AdAway/issues/20): no pending request was
observed after ten real schedule/set edits on the previous build. The follow-up
queued all ten in Android's local backup manager. The five negative controls
were absent from the sampled queue snapshots on both builds; the samples do
not establish absence between observations. Reconfirming an unchanged URL schedule keeps
its last-run timestamp: [15 red/green cases](nov26-backup-queue-results.csv).
Sixteen focused unit tests and 19 connected regression tests passed in the
isolated change. A pending request is not proof of OS timing or cloud delivery.

Physical/OEM validation remains deferred. The signed beta, hosted CI and these
bounded journeys are not a security certification, all-sites guarantee, or
sign-off of every possible sequence. The archived non-shipping source audit
continues independently of the Android release.

## Archive receipt index

The [file-by-file receipts](../deep-nov26-archive-receipts.csv) cover 354
archived files and one historical evidence record. The detailed reviewer CSVs
are retained locally in `app/build/reports/audit-2026-11-25/archive-followup/`.
All 354 archive entries match the subject hashes at their cited
CSV rows. The historical QA entry (index row 71) is different: `archive-support.csv`
row 2 reviewed `deep-nov26-handoff-files.csv` at `fb1bada98` (hash `e9caff98...`).
Row 71 records the later version at `cdd535a52` (hash `b5a6e8fd...`).
Only the assessment in row 22 changed: both schedule states used the same
local backup transport. These are static assessments, not platform tests or
authorization to ship the archived code.

At this handoff, 61 archive files and the
[follow-up evidence receipt](../deep-nov26-followup-files.csv) still awaited
review, and untriaged findings remained open. See the
[audit inventory](../files.csv) for current file review status.

| Local detailed receipt | File rows | SHA-256 |
| --- | ---: | --- |
| `archive-pcap-core-continuation.csv` | 15 | `60eb222980dc63367b43979484f7ac94b6827e97d78e11061a1d347bfd66aa59` |
| `archive-pcap-core.csv` | 54 | `5bee73b5267cec7f8cd9befc63e7de483ad74994193555984608c8d2eeedd6a9` |
| `archive-support.csv` | 55 | `6e62c19bf6037e8fe761b0415d7166f9e36b997fde54b860e9cf9ee7892a7543` |
| `archive-tcp-early-continuation.csv` | 18 | `19fc9a6e477954cb0e61166633eb700e6efb53aff07a84ab65eeff62039db9f3` |
| `archive-tcp-early.csv` | 70 | `f7bc99c36a312aef1fbda9e987261794b06330c0bc0eee36dd52585daff46163` |
| `archive-tcp-late-continuation.csv` | 22 | `0442f659e8f22c055b7359c3023a15b2969cfcce162043355dcee85f8419d44d` |
| `archive-tcp-late.csv` | 43 | `b3951895d58753e4959e50bce08d03fe6a445f68ce57b663f26bedbb6f77f856` |
| `archive-tcp-middle-continuation.csv` | 12 | `9863824d9fc63c5cdb1d95f9312795830c14202c48ef733799557e6eeb1d7a9b` |
| `archive-tcp-middle.csv` | 32 | `247564feb36371e64f4eef387c0f6044a6488853c0430ac34b745d7acda0bcf1` |
| `archive-win-dos.csv` | 34 | `0ef9cfa774da212376d129aa32994dd8e02219a5a765573ebe5bd0e0544281d8` |
