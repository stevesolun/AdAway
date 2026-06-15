# Continuation 93 Evidence

Date: 2026-06-15

## Crash Capture

- Device: `adaway-api34`
- Action: installed `app/build/outputs/apk/debug/app-debug.apk`, cleared logcat, launched
  `org.adaway` with `monkey`.
- Result: `pidof org.adaway` returned a live process id. Filtered logcat showed no AdAway
  `FATAL EXCEPTION`, native crash, `SQLiteFullException`, or AdAway ANR. Observed ANR noise was
  external emulator/system output from `com.google.android.gms.persistent`.

## UX Matrix

Command:

```powershell
.\scripts\run-ux-matrix.ps1 -OutputDir app\build\reports\ux-matrix-continuation93-devil
```

Result: exit code `0`; screenshot files were pulled for all tested variants.

## Focused Unit And Build Gates

- `:app:testDebugUnitTest` passed for:
  - `org.adaway.security.CrashSurfaceHardeningTest`
  - `org.adaway.ui.home.AiSurfaceContractTest`
  - `org.adaway.scripts.UxMatrixScriptTest`
  - `org.adaway.ui.discover.DiscoverCatalogDetachSafetyTest`
  - `org.adaway.model.source.SourceModelCarryForwardConcurrencyTest`
- `:app:assembleDebug` passed with strict dependency verification.
- `:app:compileDebugAndroidTestJavaWithJavac` passed with strict dependency verification.
- `:app:compileDebugJavaWithJavac -PadawayEnableAi=true` passed with strict dependency
  verification, proving the optional gate still compiles.

## Connected Runtime Gates

Command group:

```powershell
.\gradlew.bat --no-daemon --no-build-cache :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceModelGenerationFailureTest,org.adaway.model.source.SourceModelHttpConditionalTest,org.adaway.ui.domainchecker.DomainCheckerRuntimeTruthTest,org.adaway.db.MigrationTest" --dependency-verification=strict --stacktrace
```

Result: 29 tests, 0 failures.

Command group:

```powershell
.\gradlew.bat --no-daemon --no-build-cache :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceUpdateServiceWorkManagerTest,org.adaway.model.vpn.VpnModelCacheInvalidationTest,org.adaway.ui.domainchecker.DomainCheckerRuntimeTruthTest" --dependency-verification=strict --stacktrace
```

Result: 7 tests, 0 failures.

## Scale Benchmarks

100k stage-seeded allow-heavy gate:

```text
HostEntryAllowHeavyRebuildBenchmark blockedRows=100000 exactBlockedRows=50000 suffixBlockedRows=50000 exactAllowRules=1000 suffixAllowRules=1000 exactAllowMatches=1000 suffixAllowExactMatches=1000 suffixAllowSuffixMatches=1000 runtimeRows=97000 rootRows=97000 materializedRuntimeCache=true seedRootStage=true stageRows=100000 syncMs=1809 rootCursorMs=584
```

1M stage-seeded allow-heavy gate:

```text
HostEntryAllowHeavyRebuildBenchmark blockedRows=1000000 exactBlockedRows=500000 suffixBlockedRows=500000 exactAllowRules=10000 suffixAllowRules=10000 exactAllowMatches=10000 suffixAllowExactMatches=10000 suffixAllowSuffixMatches=10000 runtimeRows=0 rootRows=970000 materializedRuntimeCache=false seedRootStage=true stageRows=1000000 syncMs=25986 rootCursorMs=8266
```

5M stage-seeded allow-heavy gate:

```powershell
.\gradlew.bat --no-daemon --no-build-cache :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.adaway.model.source.SourceLoaderPerformanceTest#rebuildRuntimeEntries_allowHeavyRequestedRows_recordsBenchmark" "-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildBlockedRows=5000000" "-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildExactRules=50000" "-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildSuffixRules=25000" "-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildSeedRootStage=true" "-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildSyncBudgetMs=300000" "-Pandroid.testInstrumentationRunnerArguments.adawayAllowRebuildRootCursorBudgetMs=300000" --dependency-verification=strict --stacktrace
```

Preserved Gradle output:
`tasks/benchmarks/continuation93-5m-gradle.out.log`

Result:

```text
BUILD SUCCESSFUL in 38m 38s
HostEntryAllowHeavySeedBenchmark blockedRows=5000000 exactAllowRules=50000 suffixAllowRules=25000 seedRootStage=true stageRows=5000000 seedMs=1654470 checkpointMs=28
HostEntryDao.root-export-stage perf: indexDropMs=10 clearMs=3 blockedMs=51366 indexCreateMs=46856 allowMs=9594 redirectShadowMs=0 redirectedMs=0 dedupeMs=92978 finishMs=5 totalMs=200812 allowRules=true wildcardExactAllowRules=false
HostEntryAllowHeavyRebuildBenchmark blockedRows=5000000 exactBlockedRows=2500000 suffixBlockedRows=2500000 exactAllowRules=50000 suffixAllowRules=25000 exactAllowMatches=50000 suffixAllowExactMatches=25000 suffixAllowSuffixMatches=25000 runtimeRows=0 rootRows=4900000 materializedRuntimeCache=false seedRootStage=true stageRows=5000000 syncMs=270805 rootCursorMs=284247
```

Both 5M measured phases stayed under the explicit `300000ms` budgets.

## Hygiene

- `.\scripts\check-license-boundary.ps1 -SourceMode WorkingTree` passed.
- `git diff --check` exited `0`; output contained only existing line-ending warnings.
