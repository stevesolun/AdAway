# Host Test Fixes: Backup XML Guard

Date: 2026-11-25. HEAD observed: `f7dbbb5e7a8eca6983135f04adc945bab6518308`; shared dirty worktree.
Scope: JVM-AUD-001 only. **Ready and frozen for main's integrated compile/test.**

## Change

Only `app/src/test/java/org/adaway/model/backup/AppBackupAgentContractTest.java` changed.
Original manifest eligibility/reference checks, helper registration checks and export/restore ordering checks remain.
The shared exclusion assertion now parses XML with DOM and checks these absolute XPath locations:

- `/data-extraction-rules/cloud-backup/exclude[@domain='root' and @path='.']`
- `/data-extraction-rules/device-transfer/exclude[@domain='root' and @path='.']`
- `/full-backup-content/exclude[@domain='root' and @path='.']`

Each location requires the direct child named `exclude`, with both attributes on that element.
A sibling section, include element, comment or unrelated descendant cannot satisfy that path.
DOCTYPE is rejected before parsing, so the test does not resolve external entities.
Fixtures use in-memory strings; no resource file is mutated.

## Red/Green Evidence

Installed OpenJDK 21.0.11, cached JUnit 4.13.2 and Hamcrest 1.3; no dependencies installed/resolved.
No Gradle, emulator, adb, stage or commit.

First extracted the original Boolean predicate unchanged into the assertion called by the actual resource test and five new negative fixtures.
Standalone `javac` succeeded. JUnit returned exit 1: **8 methods, 5 failures**.
All five failures were exactly `expected java.lang.AssertionError to be thrown, but nothing was thrown`:

- `backupExclusionsRejectCloudIncludeSubstitution`
- `backupExclusionsRejectDeviceTransferIncludeSubstitution`
- `backupExclusionsRejectLegacyIncludeSubstitution`
- `backupExclusionsRejectMissingCloudExclusion`
- `backupExclusionsRejectMissingDeviceTransferExclusion`

The three original tests passed. Red source SHA-256: `86b20c38d604e0414d8ecd29f5f77ce494b1fa45912b94666822c6cf797137a3`.

After replacing only that predicate with structured parsing, added two bounded checks:
`backupExclusionsRequireEachSection` rejects an absent cloud parent, an absent device-transfer parent and an empty legacy root;
`backupExclusionsAcceptEquivalentXmlFormatting` accepts reordered attributes, single quotes and line breaks.
Final standalone `javac` exit 0 and JUnit exit 0: **OK (10 tests), 0 failures, 0 skips**.
The five regressions and additional fixtures invoke the same assertion used on the real repository XML, not a copied validator.

Commands used (from repository root; `CP` names the two existing cached jars):

```text
/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/javac -cp "$CP" -d /tmp/adaway-host-test-fixes.C6wLXH/red app/src/test/java/org/adaway/model/backup/AppBackupAgentContractTest.java
/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java -cp "/tmp/adaway-host-test-fixes.C6wLXH/red:$CP" org.junit.runner.JUnitCore org.adaway.model.backup.AppBackupAgentContractTest
/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/javac -cp "$CP" -d /tmp/adaway-host-test-fixes.C6wLXH/green app/src/test/java/org/adaway/model/backup/AppBackupAgentContractTest.java
/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java -cp "/tmp/adaway-host-test-fixes.C6wLXH/green:$CP" org.junit.runner.JUnitCore org.adaway.model.backup.AppBackupAgentContractTest
```

Cached jars:
`/Users/steves/.gradle/caches/modules-2/files-2.1/junit/junit/4.13.2/8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12/junit-4.13.2.jar`
and `/Users/steves/.gradle/caches/modules-2/files-2.1/org.hamcrest/hamcrest-core/1.3/42a25dc3219429f0e5d060061f71acb49bf010a0/hamcrest-core-1.3.jar`.
Compilation outputs remain isolated beneath the temporary directory, not `app/build`.

## Freeze and Limits

Final test SHA-256: `0271774dc8cb66d66fccb886da7f1a254890c34b1aa0dd1ed804de5ea6b7b721`.
The entire final 176-line test was read; `git diff --check` was clean; no staged diff exists for this test.
The two production resource hashes still match the pre-edit audit values and are recorded as context-only rows in the files sidecar.

This verifies the JVM assertion defect and its five exact counterexamples, not Android backup transport behavior or full Gradle integration.
Main owns the integrated gate. No product resources, frozen domain sidecars, prior audit sidecars, central ledgers or unrelated files were edited.
Only this test and the three `host-test-fixes-*` sidecars are this lane's new changes; all remain unstaged.
