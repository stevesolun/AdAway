# Interrupted due source schedule

**DEEP-SCHED-DUE-001: verified fixed on API 34 debug. Release re-sign required.**

Existing WorkManager tests check the unmetered constraint and independent dispatcher registration. SourceModel tests exercise targeted imports and offline rollback. Neither checks what the due worker does when WorkManager stops it during targeted retrieval.

## Reproduction and result

1. Disable global scheduling. Make a named set and its single enabled source independently overdue. Configure another overdue source but leave it disabled. Use local content URIs and a known two-rule fixture; no source request uses the network.
2. Invoke the production worker with the WorkManager test builder. The recording source model delegates to the real targeted import, then delivers `STOP_REASON_CONSTRAINT_CONNECTIVITY` before retrieval returns. The VPN apply method counts calls instead of opening a tunnel.
3. Baseline failed: `Stopped retrieval must not start VPN apply expected:<0> but was:<1>`. The old path had no stop check between fetching and recording success.
4. With the pre-apply stop check, the first attempt returns retry, never calls apply, and preserves the named and individual last-run timestamps. A fresh worker sees the same occurrence due, retrieves only the enabled ID, applies once, and stamps the set and URL with the same time inside the retry window. The enabled source contains two rules. The disabled source is never passed to retrieval and retains its disabled flag, missing modification date, and old schedule timestamp.

This tests the worker's response to a delivered stop, not the OS delivering one on a physical Wi-Fi-to-metered handoff. WorkManager may discard a stopped worker's return value; the durable retry evidence is the unchanged due clocks and successful fresh run. An already-running import is not aborted by this change. Stops during apply, global fetches, physical VPN startup and release binaries were not exercised by this regression.

## Verification

The stop-after-retrieval fix does not define a new policy for a stop that
arrives during apply itself. In that interleaving a normal return can still
advance the clocks. A future check should distinguish an actually committed
apply from an incomplete one before treating that timestamp as incorrect.

- New `FilterSetUpdateWorkerDueTest`: 1 failure before the production edit; 1 pass after.
- Existing `FilterSetUpdateServiceWorkManagerTest`: 3 passed. Existing `FilterSetScheduleReconciliationInstrumentedTest`: 6 passed. Existing `FilterSetUpdateWorkerTest`: 2 host tests passed.
- Rebuild with JDK 21: `./gradlew --offline :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest --tests org.adaway.ui.hosts.FilterSetUpdateWorkerTest`. Install both debug APKs on the disposable API 34 device, then run:

```sh
adb -s emulator-5578 shell am instrument -w -r \
  -e class org.adaway.ui.hosts.FilterSetUpdateWorkerDueTest \
  org.adaway.test/androidx.test.runner.AndroidJUnitRunner
```

- Installed green APK SHA-256: app `4eccdfd0b21ace8c3723f22aa822fb5ed9bb72f8f0d204733ad3d56aa2c38c85`; test `1c8434a7fe9b04508c8b6bd910ebf7cc24673e8714c7b2063024b68d76514e40`.
- Raw local runner outputs: `red.txt`, `green.txt`, and `reconciliation.txt` retained under `app/build/reports/audit-2026-11-25/platform-restore/worker-and-identity/`, independently of the disposable AVD.
- Release handoff: include the verified worker change before re-signing. No release build or signing in this lane; no canonical ledger edits or commits.
