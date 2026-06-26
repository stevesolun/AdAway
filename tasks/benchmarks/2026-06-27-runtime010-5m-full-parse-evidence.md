# RUNTIME-010 Fresh 5M Full Parse Proof - 2026-06-27

PR head: `b132bf71 perf: stage root export during source import`

Environment:
- JDK: Temurin 21.0.11
- SDK: `/Users/steves/.local/android-sdk`
- Device: `adaway-api34-16g(AVD) - 14`
- `/data` free before run: 15G

Command:

```bash
./gradlew --no-daemon --no-build-cache :app:connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest#parseInsertSyncAndRootApply_requestedScale_recordsBenchmark' \
  '-Pandroid.testInstrumentationRunnerArguments.adawayPerfLines=5000000' \
  '-Pandroid.testInstrumentationRunnerArguments.adawayPerfSyncBudgetMs=300000' \
  '-Pandroid.testInstrumentationRunnerArguments.adawayPerfRootWriteBudgetMs=300000' \
  --dependency-verification=strict --stacktrace
```

Raw logs:
- `tasks/benchmarks/2026-06-27-runtime010-5m-full-parse-gradle.out.log`
- `tasks/benchmarks/2026-06-27-runtime010-5m-full-parse-gradle.err.log`

Result:

```text
BUILD SUCCESSFUL in 4m 56s
OK (1 test)
HostEntryDao.root-export-stage duplicate-skip addedSkippedRows=250000 totalSkippedRows=250000 totalMs=1559
HostEntryDao.root-export-stage perf: indexDropMs=1 clearMs=0 blockedMs=6642 indexCreateMs=0 allowMs=0 redirectShadowMs=0 redirectedMs=0 dedupeMs=0 finishMs=1 totalMs=6644 allowRules=false wildcardExactAllowRules=false stageBacked=true
SourceLoaderScaleBenchmark lines=5000000 inserted=4750000 runtimeRows=4500000 progressEvents=2375 parseMs=124749 syncMs=8739 rootRows=4500000 rootWriteMs=2476 rootWriteBytes=150601774
```

The fresh full parse/import/sync/root-write path passed the explicit 300,000 ms sync and root-write
budgets. This closes the RUNTIME-010 full-scale benchmark gap, but does not close the overall
market-leading release objective.
