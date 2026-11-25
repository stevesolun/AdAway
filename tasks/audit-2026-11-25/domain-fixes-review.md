# Domain Checker Fixes: RR-003 and RR-004

## Ready / Frozen

- Production, resource, and test inputs are frozen for main's next integrated build.
- Initial handoff HEAD: `f7dbbb5e7a8eca6983135f04adc945bab6518308`. Main subsequently
  staged the phase-two inputs. The follow-up below changes only the instrumentation
  fixture on top of that index; its correction remains unstaged. Staged domain blob
  entries were compared before/after and are identical. No stage/commit/push by this lane.
- Read `CLAUDE.md`, `tasks/lessons.md`, the remaining-rules report, and all existing
  Domain Checker tests before implementation. Held all production/test writes during
  the requested integration freeze and resumed only after GO.
- Only RR-003/RR-004 are implemented. No ListsViewModel, RegexUtils, central audit
  inventory, todo, or lessons edits. Another lane's DomainCheckerTest adware-executor
  assertion change was observed, preserved, and not claimed as this lane's work.

## Phase-Two Retained-Failure Follow-Up

**Classification: fixture lifecycle error, not a demonstrated product retention defect.**
Only `DomainCheckerOperationsInstrumentedTest.java` and these four sidecars changed
in this follow-up. All staged domain production/resource/JVM files are untouched.

The supplied main-owned `/tmp/adaway-audit-phase-two-focused.log` has 14 domain
results: 13 passes and one failure. At lines 312-320,
`insertFailureIsDeliveredAfterStoppedOwnerRecreationAndCanRetry` fails with
`Missing feedback` at original test line 141 (helper line 485). The preceding
assertions at original lines 133-138 passed: loading was false and the exact failure
resource was retained while the Activity was stopped. The nine other operation
methods and all four RuntimeTruth methods passed in that APK.

Root cause evidence is local, version-exact AndroidX bytecode, inspected with
`javap -c -p` on cached `core-1.7.0-api.jar`. `ActivityScenario.recreate()` executes:

1. `moveToState(RESUMED)` at bytecode offsets 30-37, on the old Activity.
2. `activityInvoker.recreateActivity(oldActivity)` at offsets 38-46.
3. Wait for the replacement Activity, then restore the previous state at offsets 125-133.

The original fixture released the failed write while stopped, verified the pending
failure, then called `recreate()`. Its synthetic old-owner resume activates the old
fragment's LiveData observer, which shows/consumes the one-shot result at
`DomainCheckerFragment.java:82-107`. The test then incorrectly requires that already
consumed result on the replacement view. This explicit ordering explains the exact
failure; no new device reproduction was run by this lane.

Minimal fixture correction: cap the original fragment at `CREATED` after starting
the held write and before stopping the Activity. Keep that cap through recreation,
so the test helper's synthetic resume cannot activate the old view. On the replacement
Activity, assert a different fragment, the same ViewModel, the identical retained
OperationResult, and lifecycle `CREATED`; only then lift the cap to `RESUMED`.
All existing visible-error, enabled-Check, hidden-stale-result, no-success, unchanged
database, and successful UI-retry assertions remain intact. No sleeps/timeouts were
extended, feedback assertion removed, or product delivery policy changed.

Verification: the corrected test class passes isolated JDK 21 `javac` against the
Android API 36 Java system image, cached dependencies, and existing phase-two app/test
classes plus actual generated R classes; no temporary resource binding was needed.
No Gradle, adb, emulator, staging, or commit command was run. Main must rebuild and run
`org.adaway.ui.domainchecker.DomainCheckerOperationsInstrumentedTest#insertFailureIsDeliveredAfterStoppedOwnerRecreationAndCanRetry`.
The corrected case is **type-checked, not device-verified**; prior 13 passes are not
claimed as a fresh run of the changed test class.

Evidence SHA256 values:
- Supplied phase-two log: `17490dec38388a5b0058a6070654f901f5b5961a926cd9e8f05157cbfbf0a0b4`.
- Cached AndroidX core API jar: `3b8005eea60475bcab35b2208a3137145dc6633c2ef13fc02bd9cd98439f484e`.
- Corrected test source: `55a869e2b3f814ad352b58f19095b5eae97d69171db97f8967113bd3a751013c`.

## Changed Inputs

1. `app/src/main/java/org/adaway/ui/domainchecker/DomainCheckerViewModel.java`
2. `app/src/main/java/org/adaway/ui/domainchecker/DomainCheckerFragment.java`
3. `app/src/main/java/org/adaway/ui/domainchecker/DomainNormalizer.java`
4. `app/src/main/res/values/strings_domainchecker.xml` (new, three failure messages)
5. `app/src/test/java/org/adaway/ui/domainchecker/DomainNormalizerTest.java`
6. `app/src/androidTest/java/org/adaway/ui/domainchecker/DomainCheckerOperationsInstrumentedTest.java` (new)

Current SHA256 values are recorded in `domain-fixes-files.csv`. No layout edits.

## RR-003: Operation Completion

- ViewModel owns pending work and retained terminal feedback, observed through the
  current view lifecycle. Fragment callbacks no longer announce optimistic success.
- Busy begins synchronously on main and ends only after the worker's write, runtime
  synchronization, and result read complete. There is no `postValue(false)` followed
  by a requeued `checkDomain` refresh. Worker exceptions and scheduling rejection
  produce error feedback and release busy state.
- All mutation controls, including generated Delete buttons, are disabled while
  busy. The ViewModel also rejects repeated mutations. Failed current checks hide
  the previous result and its controls; Check remains available for recovery.
- Check generations prevent an older check or mutation refresh from replacing a
  newer requested domain. A pending-work count keeps busy true while newer checks
  remain queued.
- Mutations use the application's SourceOperationCoordinator lease and the existing
  SourceModel mutation guard. Database mutation is transactional; a failed block
  insert rolls back its preceding delete. Runtime synchronization follows the commit.
- Allow exceptions preserve underlying user blocks. A retry reuses an existing exact
  user allow rule, so a committed write followed by failed sync/refresh does not
  accumulate duplicate exceptions. Removing an exception restores the underlying block.
- No VPN enable, tunnel restart, root apply, or other protection-start action was added.

## RR-004: URL Authority

- HTTP/HTTPS input is parsed by `URI`, then `URL` host accessors (which retain Unicode
  authorities for IDN normalization). No connection is opened. Credential-bearing
  authorities are rejected rather than using the username as a host.
- Bare host/path/query/fragment/port input remains supported, including a query that
  contains another HTTP/HTTPS URL. Mixed case, trailing-dot hostnames and Unicode
  IDNs remain normalized. Unsupported/malformed authorities and IP literals fail closed.
- Every mutation entry point validates input, including deletion.

## Host Evidence

- Initial standalone red: 18 DomainNormalizer tests ran, with exactly one failure:
  `rejectsCredentialsInsteadOfCheckingUsername` expected null but got `account.example`.
- A subsequent compatibility test, `bareHostQueryMayContainAnotherUrl`, was red
  against an overbroad scheme check, then green after narrowing bare-host handling.
- Final focused standalone JVM run: **40 tests passed**, zero failures. This includes
  all 21 DomainNormalizer tests (four new methods), four advice tests, and 15 existing
  DomainChecker tests. Current RegexUtils source was compiled read-only for this run.
- These runs used JDK 21 and cached dependencies, with output only under `/tmp`.
  They are not Android runtime or integrated build proof.

## Initial Android Type-Check

Final isolated type-check passed with no diagnostics using JDK 21 `javac`, Android's
cached Java system image (`--system`), API 36 `android.jar`, cached dependencies, and
existing phase-one dependency class files. Explicit inputs included the changed
Domain Checker Java sources, DomainCheckResult, the new instrumentation class, the
existing RuntimeTruth class, all three focused JVM classes, and read-only RegexUtils.

New resource names used a temporary compile-only R binding; the new XML parses.
This checks Java signatures and supported Android API types, **not** AAPT/resource
linking, fresh dependency compilation, APK packaging, or device behavior. No Gradle,
emulator, adb, or instrumentation command was run by this lane.

At the initial handoff these ten Android cases were type-checked but unexecuted.
Their subsequent main-owned phase-two results and the one pending fixture rerun are
recorded above and per method in `domain-fixes-tests.csv`:

1. `heldBlockHasNoSuccessAndCompletesOnRecreatedView`
2. `heldAllowRemoveAndDeleteDoNotAnnounceSuccessEarly`
3. `insertFailureIsDeliveredAfterStoppedOwnerRecreationAndCanRetry`
4. `failedReplacementRollsBackPreviousAllowRuleThenRetries`
5. `runtimeSyncFailureDoesNotClaimSuccessAndRetrySynchronizes`
6. `readFailureClearsBusyAndStaleResultThenCheckRetries`
7. `failedAllowRefreshRetriesWithoutDuplicatesOrLosingUnderlyingBlock`
8. `newerCheckWinsOverMutationRefreshWithNoIdleGap`
9. `sharedSourceOperationRejectsMutationWithoutWritingAndAllowsRetry`
10. `credentialsCannotSelectOrMutateUsernameHost`

The domain regression selectors are
`org.adaway.ui.domainchecker.DomainCheckerOperationsInstrumentedTest` (10 new cases)
and `org.adaway.ui.domainchecker.DomainCheckerRuntimeTruthTest` (4 existing controls).
No follow-up Android green result is claimed. Tests use passive root UI setup, bounded
disk holds, real Room writes, SQL failure triggers, and two narrow DAO-read injections;
they do not grant VPN consent or start protection.

## Limits and Artifacts

- A synchronization or refresh failure after the rule transaction commits does not
  undo the saved rule. Error text intentionally does not promise an unchanged rule;
  retry is safe. Atomicity covers the delete/insert, not root/VPN enforcement.
- Retention covers view/configuration recreation, not process-death recovery.
- Runtime tests and the integrated resource/build/lint gate remain main-owned.
- `domain-fixes-bugs.csv` uses the exact central 11-column bug schema and the existing
  RR-003/RR-004 IDs. `domain-fixes-files.csv` uses the four-column file schema.
- `domain-fixes-tests.csv` lists all 54 exact methods with current source hashes,
  execution state and limitations: 40 prior host-passed, 13 main-owned phase-two
  Android passes, and 1 corrected fixture awaiting device rerun. Its seven columns
  include current source hashes; prior APK evidence is distinguished from new proof.
- Only the four `domain-fixes-*` sidecars are added to the audit directory. No central
  inventory merge or finding closure was performed.
