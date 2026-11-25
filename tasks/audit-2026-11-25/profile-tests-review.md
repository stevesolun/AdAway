# Profile Test Inventory Closure

Date: 2026-11-25. Read-only review; migration implementation remains frozen.

## Findings

**No new actionable bugs confirmed.** The central ledger was checked for profile/subscription findings before reporting. `profile-tests-bugs.csv` contains the exact standard 11-column header and zero finding rows; no new or duplicate bug ID was assigned.

## Completed Full Reads

All three assigned files were read in full, not counted from searches or test-name scans: **393 lines, 11 test methods** (one Android method and ten JVM methods). Current SHA-256 fingerprints and assessments are in `profile-tests-files.csv` using `path,sha256,assessment,bug_ids`.

| File | Full Read | Assessment |
| --- | ---: | --- |
| `app/src/androidTest/java/org/adaway/ui/discover/DiscoverProfileStatusInstrumentedTest.java` | 185 lines | Reviewed passive fixture reset, queued database preparation, three controlled sources, named-profile setup, exact/extended/partial/custom text assertions, navigation refresh, bounded UI polling, disk-task failure propagation, and cleanup. No new actionable defect established. |
| `app/src/test/java/org/adaway/ui/discover/FilterListsSubscriptionStateTest.java` | 102 lines | Reviewed five cases for NONE/PARTIAL/ALL, unsupported-row exclusion, and missing/empty cached URL treatment; checked maps/sets and resolver argument order. Expected states agree with the resolver contract. |
| `app/src/test/java/org/adaway/ui/hosts/FilterProfileStateTest.java` | 106 lines | Reviewed profile relationship classifications and four diff scenarios, including exact no-op, enabled/disabled/kept/missing counts, empty-profile disable-all risk, and missing-only profile behavior. Assertions agree with the state/diff contracts. |

Supporting contract reads were limited to `FilterListsSubscriptionState`, `FilterProfileState`, `FilterProfileDiff`, and the relevant passive-reset helper excerpt. They are context cross-checks, not additional pending-inventory closures.

## Evidence Limits

- This is source-review completion only. No JVM or Android test was executed or compiled for this request; no runtime pass is claimed.
- The Discover test calls `refreshDiscover`, navigating away and back after source changes. It establishes intended refreshed-screen status coverage, not same-view observer delivery. The latter is already tracked by central finding `AUD-SRC-006` and its separate regression evidence, so it is not duplicated as a new finding here.
- The instrumentation fixture clears source/profile test state using shared passive-reset helpers; it assumes a disposable test application state. It was read, not invoked. No privileged root operation was run.
- Missing permutations alone were not elevated to bugs, and this review does not claim exhaustive state-machine, lifecycle, or scheduling coverage.

Only `profile-tests-review.md`, `profile-tests-files.csv`, and `profile-tests-bugs.csv` were added. App files, migration inputs, central ledgers, and historical schemas were not edited. No Gradle, ADB, staging, or commit was performed.
