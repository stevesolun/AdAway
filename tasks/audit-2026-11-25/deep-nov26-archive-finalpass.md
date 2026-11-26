# Archived native sources: issue #24

The [inventory input](deep-nov26-archive-finalpass-files.csv) records the 61
paths in [issue #24](https://github.com/stevesolun/AdAway/issues/24). They
were the 61 pending `tcpdump/jni/` rows in the ledger at `a6a92eb6`; none is
in the [earlier intake](deep-nov26-archive-receipts.csv). Each entry was
matched by path, SHA-256 and lane row to the issue and the detailed receipt.
The digest covers the file bytes, not the Git object header, and matched the
blob at `f81d07165ea628a16fbed7e1264c2fbdc40ebce5`, the blob at `a6a92eb6`
and the working file. Paths with identical bytes retain separate receipts.

**Review depth.** 57 files received individual static maintenance review.
Both generated `configure` scripts, `libpcap/scanner.c` and `libpcap/grammar.c`
received structural checks only: no regeneration or semantic equivalence
verified. No archived build, capture or live traffic was used.

**Finding status.** The CSV preserves the lane reviewers' bug IDs verbatim.
The six finding lists contain 167 distinct IDs; the file receipts also
reference 19 distinct earlier IDs. References are not new findings or
verified fixes. Only defensive scope summaries are included here.

**Provenance.** Originals are under the ignored
`app/build/reports/audit-2026-11-25/archive-finalpass/` directory. Row numbers
count the CSV header as row 1. The CSV embedded in the issue matches the local
`receipt-index.csv` by parsed rows. SHA-256 of each original file:

| Lane | Paths | `lane-N.csv` | Findings | `lane-N-findings.csv` |
| --- | ---: | --- | ---: | --- |
| 1 | 10 | `28b4f8ca56d3065c536093dd4d1b94e64fe01d0049ad08005d2079f02b0abe46` | 24 | `d27113eee48663bda68324a7ab909b180d6f2c43fdfecaa9de5987e77e6bdfb8` |
| 2 | 9 | `3835235ffd4d16e63d2c3b1ca01a799367da36af337f0bdb3ebd159e63b68da5` | 31 | `af48e25cb8b48436c1e115f269327f7112c14098a4fe8cc5747609a08d881529` |
| 3 | 10 | `d57768c4a0c83f612e97b0cae6bff5a76f5d2c6c09f083bac6e9dbf102fc6f5d` | 33 | `bcff11dce434fdf9a70903a99605915d41772bdf3659ac87f5f20d2c76f0b42f` |
| 4 | 10 | `0e8c7c6873483a1a72e5f00f8b409597572c244dcf76ef402ea5592bec413417` | 19 | `50938757a1dc49326ccb69e975d06e8c58a1a2fa40f6623b52b410bdd1e9875a` |
| 5 | 11 | `5dfb76a1b3a63051b9247a658ef82e0d785195f0060d5823456979d56fcffa97` | 37 | `3acf1bf1c5dafdbc150520702bf52d5176c70a1e1708786a4494a90d0a360e99` |
| 6 | 11 | `2dfd8fd9e4ead488168cab68a8b454bea8ea43beba4dc46776eb0bd66c47b7b2` | 23 | `0541b1d4780418440bd973cb6f316fa7e87815a3c179561f4816403d72c818a2` |

**Handoff.** Import this CSV with the inventory generator's `--record` option
when updating the global ledger. Its `reviewed` status records file review,
not finding closure. Keep the archive out of the APK. Before reuse, identify
the maintained upstream inputs, reconcile generated copies, triage findings,
run isolated semantic tests and recheck the packaging and license boundary.
