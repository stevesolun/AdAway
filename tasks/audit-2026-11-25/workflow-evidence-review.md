# Workflow Evidence Review

Date: 2026-11-25. Read-only evidence mapping for main review, not release signoff.
Only this review and `workflow-evidence-refresh.csv` are authored by this sidecar.
No build, test execution, device command, app edit, workflow-ledger edit, or bug edit was performed.

## Bounded Inventory

Exactly the existing 145 rows from `sources-workflows.csv` and 9 rows from
`home-workflows.csv` are mapped once, in original file order. No new workflows
or QA scope were added. The original statuses are retained in `previous_status`;
proposals have not been applied to the original ledgers.

| Existing group | Rows | passed-scoped | partial | pending |
| --- | ---: | ---: | ---: | ---: |
| DISC | 60 | 0 | 41 | 19 |
| SRC | 48 | 0 | 26 | 22 |
| SCH | 20 | 0 | 10 | 10 |
| CAT | 13 | 0 | 3 | 10 |
| LEG | 4 | 0 | 0 | 4 |
| HOME-QA | 9 | 0 | 8 | 1 |
| Total | 154 | 0 | 88 | 66 |

81 stale pending rows receive scoped partial evidence; 66 remain pending.
Six prior partial rows remain partial. HOME-QA-001 is conservatively proposed
from passed-scoped to partial: a fake Home switch test and a separate live service
test do not prove the combined Home-to-retained-rules workflow.

Status policy:
- `passed-scoped`: would require the row's stated control path and explicit variants to be established within a clearly declared execution scope. None of these broad rows meets that threshold here.
- `partial`: a relevant executed subset exists; `evidence` specifies exactly what it proves and `remaining_variants` keeps the unproven behavior open.
- `pending`: no affirmative execution of the stated behavior is established in the reviewed artifacts. Related rendering, a failing assertion, or an unexecuted current test can still be listed, explicitly without closure credit.

These are evidence judgments, not declarations that pending features fail.
Partial is not a passed workflow, and no percentage of buttons verified follows
from the suite's pass count.

## Evidence Register

All results below were parsed, not inferred from test class names.

| Key | Retained artifact | Exact observed outcome |
| --- | --- | --- |
| F | `app/build/reports/audit-2026-11-25/connected-four.xml` | 488 cases: 476 PASS, 10 SKIP, 2 FAIL, 0 errors. XML timestamp 2026-11-25T10:53:23. |
| R | `/tmp/adaway-audit-phase-one-replay.log` | 22 terminal status-code 0 results; OK (22 tests), no skip. Nine notification, one UX, ten Discover state, two large-scope tests. |
| L | `/tmp/adaway-audit-phase-one-live-vpn.log` | Four terminal status-code 0 results; OK (4 tests), no skip. Three lifecycle and one DNS tunnel method. |
| V | `tasks/audit-2026-11-25/validation.md` | Narrative provenance/manual observations, especially Consolidated Phase-One Gate and Actual Boot Consent Recovery. Not substituted for a testcase outcome. |

`test_names` contains full class#method identifiers; `test_outcomes` gives every
available F/R/L outcome for each listed identifier. `run_artifacts` resolves
those keys in each CSV row. `NOT_EXECUTED_IN_F_R_L` means no result in this
bounded evidence set, not proof that no other run exists. The corresponding CAT
rows stay pending. `test_code_references` points to the declarations of methods
whose bodies and relevant fixture/helper behavior were read.

125 distinct referenced methods in 38 source files were checked. Six are current
legacy-catalog selection methods with no F/R/L result. Remaining references have
retained results, including negative/skip outcomes where applicable. No historical
release or earlier aggregate run is used to promote a row. V's host-suite
aggregates are context, not uninspected JVM method-level proof.

### Full Run Versus Focused Replay

The two F failures remain part of the full-run record:
- `org.adaway.helper.NotificationHelperChannelInstrumentedTest#createChannelsUpdatesMetadataAndPreservesExistingSettings`: setup failed with `Notification should not be active: 21`, via notification cleanup. The same method is PASS in R. V records the cleanup follow-up.
- `org.adaway.ui.UxDeviceMatrixTest#keyScreens_haveScreenshotsLabelsAndTouchTargets`: failed on an ellipsized `filterlistsItemName`, "MoaAB: Mother of All AD-BLOCKING". The same method is PASS in R, subject to the capture boundary below.

There is **no full-green 488-case replay**. R and L must not be added to F as
independent coverage totals or used to erase F's failures/skips. Two F-skipped
methods (framework VPN start and UDP tunnel) pass in L; this is later prepared-
consent scope, not retroactive full-suite success.

The ten F skips are two platform-backup transport fixtures, privileged root
apply, three opt-in source-scale cases, DownloadProvider, preference VPN restart,
UDP tunnel, and framework VPN start. Their unexercised conditions are not passes.

V identifies the immutable saved phase-one APKs used by R and L. Their hashes were
independently read:
- App: `44d51782eb11f414a9a6c9a0815e3c6a34eb4cf589d995b2f6ae6caca089acd1`.
- Android tests: `95ea2b386198132a58fec3d433492552b03ea4782cb6360ec9aea247f84f1347`.

These artifacts do not establish current next-batch source success or signed
release readiness. The source references describe inspected code, not a new
binary/source identity attestation.

## UX Capture Boundary

The retained `phase-one-ux-replay/discover.png` was visually inspected: it says
**Loading filter lists**, has no populated directory rows, and therefore does not
exercise the row name/format that failed in F. R proves the matrix completed for
the state actually captured, not that long loaded labels are now visually sound.

The method resets background work, drains the main thread, waits a fixed 750ms,
then audits currently rendered views and captures a bitmap. It does not wait for
a loaded directory, visit every row, click every control, or run every font,
locale, theme and height combination. Its accessible-name heuristic is not a
screen-reader interaction test. Eight screenshots exist; only Discover and
Sources images were visually inspected for this mapping.

The separate `landscapeDiscoverKeepsFilterRowsReachable` test genuinely loads
four fixture rows, asserts at least 48dp visible list height and injects a
physical checkbox tap. That supports DISC-060 partially, not the complete
short-height/history/keyboard/large-font matrix.

Current `DiscoverLabelLayoutInstrumentedTest` source was inspected for its
normal/1.5x portrait/landscape measured-row assertions, but the class is absent
from F/R/L. No execution credit is assigned to it. DISC-058 remains pending;
DISC-059/060 retain explicit loaded-row and layout gaps.

## Important Scope Distinctions

- DISC-002 proves Safe chip insertion/profile persistence/update enqueue from an empty DAO. It does not prove Balanced/Aggressive, reenable/no-op/offline, or download success.
- DISC-009/017/021 have current executed refresh/restoration regressions. Concurrent subscription changes in the two late-refresh tests are DAO writes, not switch taps; parcel restoration is not actual process death.
- DISC-029/039 have real accepted control paths in separate fixtures. Most runtime unsubscribe tests invoke confirmed handlers directly with recording models. They do not close Cancel/Back/outside, real root installation, or the whole dialog-to-live-VPN sequence.
- The 6000-ID Retry path clicks the button and uses real WorkManager. The checked-ID path injects selection and invokes the confirmed command reflectively. Both use fake directory dependencies and an isolated recorder database; neither physically checks 6000 rows.
- Recovery action IDs are stored separately from capped display history. Tested 80-row formatting is a review-message limit, not an unsupported-choice cap: an actual chooser test reaches choice 121. Keep that distinction when reviewing DISC-050/053/054.
- Sources Apply/update tests often use real DAO/content-provider processing with a recording protection model. Passing those tests does not establish privileged root behavior or public-network download handling.
- Saved-set scheduling from Sources, current-selection scheduling, and manager-screen scheduling are different routes. Current-selection Cancel/Back tests cannot close saved-set or source nested-dialog rows.
- Package replacement tests call the real receiver directly with a synthetic Intent; they do not perform an app upgrade. WorkManager registration is not due-schedule execution.
- CAT remains a direct-host/legacy scope with no asserted production route. Six newly read selection tests have no supplied result. Confirmed removal/runtime tests do not cover legacy Add, selection, editor return or long-press dismissal.
- HOME-QA-007 stays pending: rendered counter values do not prove tapping counters opens the corresponding rule tabs. HOME-QA-008 has risk text/button visibility only, not actual Android settings launch/return.
- HOME-QA-003/004 distinguish injected consent-result tests from V's real boot/Cancel/Back/retry/rotated-grant record. That manual record uses an empty rules database; retained-rule replay and competing always-on VPN remain pending.
- L's DNS test inserts unique blocked/redirected USER canaries and validates UDP replies through the prepared tunnel. Its optional public upstream branch depends on an extra argument not established by this retained log; public upstream success is not claimed here.
- HOME-QA-009 receives current F credit for version-label/Discover navigation, not its newly added geometry assertions. The working-tree diff adds the CTA-versus-scroll bounds checks after the retained run; HOME-AUD-005 is not closed.

## Integrity And Review Use

CSV joins are by unchanged `workflow_id`, not display text. Every original row
has one proposal, explicit evidence and remaining variants. Pending rows with no
matching executed test leave `test_names` empty instead of inventing a test.
Main should review the proposal against each original row before any ledger
application; this sidecar applies no statuses and changes no bugs.

The original workflow hashes read for this mapping:
- sources: `5005fc9452be569ca78bd93f955bf99086c57f6f74ed4d4eebd5fe5c0fe8b419`.
- home: `9924272de8bf5fba273af1066bb603330599d863f3e86ecf1747c72dd93bf28f`.

Retained input hashes:
- F: `547093be8285b1c0bff3b7901c8ba9c55fb3b64a9ff9e7360a083012259f393d`.
- R: `fc5bdd7725c6731188042ee9f5a47b2ccd2bd5a5d8765aafdf0f2ce807ba9406`.
- L: `c0699549ccf59deae88e6a289a9c82e53c9413ce3f155c00440413cf268702f9`.

Verification checks: CSV parse/column shape; exactly 145+9 unique original IDs;
no extra IDs; each referenced declaration exists; every claimed F/R/L outcome
matches the parsed artifact; each partial row has affirmative scoped evidence;
all absent-result legacy methods stay pending. No runtime verification was run.

### Referenced Source Snapshots

Hashes identify the code inspected, not the immutable APK's build inputs.
Only relevant methods/helpers are claimed as read, not whole-file review of
every file below.

| Referenced test source | SHA256 |
| --- | --- |
| `app/src/androidTest/java/org/adaway/ui/adblocking/SourceApplyFeedbackInstrumentedTest.java` | `8246bbee1a759619f7bc59d67bdf3da39bef4d5398fed7e1e0c54bb7866d712d` |
| `app/src/androidTest/java/org/adaway/ui/discover/DiscoverCacheRecoveryInstrumentedTest.java` | `d27699aa61577f17c45eb2af6c0cf167ea8f8ec1edebc08c08f554e1568ff4cf` |
| `app/src/androidTest/java/org/adaway/ui/discover/DiscoverCatalogSelectionInstrumentedTest.java` | `a1fcc1d1a1b4eb046a28360081378090cac8bfe1fcdeed31a58dbc5030f141da` |
| `app/src/androidTest/java/org/adaway/ui/discover/DiscoverFilterListsFiltersInstrumentedTest.java` | `7d11b4cb1cda47f22bb006f6980e67a748f7e84c24bc93b47ee4ce05eb4f1932` |
| `app/src/androidTest/java/org/adaway/ui/discover/DiscoverQuickPresetInstrumentedTest.java` | `71f1467aa24ae434aef0efc7ca8e701816c8055602f7143c1b6f329ac8b595bd` |
| `app/src/androidTest/java/org/adaway/ui/discover/DiscoverRecoveryInstrumentedTest.java` | `cce60d6454eb102695b1c59806585e0046ecd40a3acba35faead76629fc9fe0e` |
| `app/src/androidTest/java/org/adaway/ui/discover/DiscoverStateRegressionInstrumentedTest.java` | `998a44029b081cdf079fc87203a16f87f4c4039060e1c152ce28fe3629a34d0f` |
| `app/src/androidTest/java/org/adaway/ui/discover/DiscoverUnsubscribeRuntimeInstrumentedTest.java` | `bcf2a94daa5ad3495539c7cbeec7eb3c1d8be07397d7c77532316d91c10e988a` |
| `app/src/androidTest/java/org/adaway/ui/discover/DiscoverUnsupportedReviewInstrumentedTest.java` | `bc657a0e9b844c318e8645c40343d8acd92df99335bf3e99dba74503e012dce3` |
| `app/src/androidTest/java/org/adaway/ui/home/HomeCountersInstrumentedTest.java` | `0cc60a827b089e359892e6ec86c6eb4ebe8fd65ddd688e62f20abc11902a504b` |
| `app/src/androidTest/java/org/adaway/ui/home/HomeDiscoverDeepEntryInstrumentedTest.java` | `96bca5ecdf5ffd973cfb724958c1f5c28e34f030401fef98771e6899ebc20f5c` |
| `app/src/androidTest/java/org/adaway/ui/home/HomeLaunchNavigationStateInstrumentedTest.java` | `c2ea44fbd2b134d6a40697b4ba67351309394c672fc58b1caf3b774f8bb802a6` |
| `app/src/androidTest/java/org/adaway/ui/home/HomeLeakStatusInstrumentedTest.java` | `6975b99219d1ed779a4adc746f8e7d3e75d3397cc61bd4e966c612291dbb0456` |
| `app/src/androidTest/java/org/adaway/ui/home/HomeNavigationSourcesInstrumentedTest.java` | `013a7fd294c07bc091fff4dbe49645f17a9f45df5af9310e5aa662ec896ed937` |
| `app/src/androidTest/java/org/adaway/ui/home/HomeUpdateActionsInstrumentedTest.java` | `56e153a380750cd9c00a6749c9abedf6c091d1543ec2e7bfddbe46532a1b5791` |
| `app/src/androidTest/java/org/adaway/ui/home/HomeUpdateControlsInstrumentedTest.java` | `91155016bb6cea628dbe85d08fe1f2aab0ca81fec47427293964675ecdffcc54` |
| `app/src/androidTest/java/org/adaway/ui/home/HomeUpdateSignalErrorInstrumentedTest.java` | `f50cfcd0a6c5a94e48c74f7979de3e9d0ec72a109f4137c661aa63077d97de2e` |
| `app/src/androidTest/java/org/adaway/ui/home/HomeWorkProgressRegressionInstrumentedTest.java` | `485c15816bd3790fb4d3a53ef5b9e1be355205f5f9afc163e6dce18cc392b4e6` |
| `app/src/androidTest/java/org/adaway/ui/hosts/FilterListsBulkUiInstrumentedTest.java` | `0235aea2961b64e3df76fa52a7fa8d888af477f7d3acd2da23a2b7c96c475f2e` |
| `app/src/androidTest/java/org/adaway/ui/hosts/FilterListsLargeScopeUiInstrumentedTest.java` | `838d09adffbea24a72e4a053d34d7c91e0899557e32d47b398d5c9c459383f90` |
| `app/src/androidTest/java/org/adaway/ui/hosts/FilterListsRecoveryInstrumentedTest.java` | `288ea504b7e024ace4f3ad99bd90d2cca3ff944a1983a704ebfffa371773536e` |
| `app/src/androidTest/java/org/adaway/ui/hosts/FilterListsSubscribeAllWorkerDoWorkTest.java` | `52fcfa6682868d2278e1368824f75c607b2b48fc3179a5bb0884d4e6a82db5a2` |
| `app/src/androidTest/java/org/adaway/ui/hosts/FilterListsSubscribeAllWorkerRoomTest.java` | `c0af120add753d60a073503b7994ccc6858b05b30dba7da9109f781bd3074327` |
| `app/src/androidTest/java/org/adaway/ui/hosts/FilterListsVisibleBulkActionsInstrumentedTest.java` | `cd01b5fe4fd7faf289c7693e5069efb2b9506d134419928a338948656c8f0cb9` |
| `app/src/androidTest/java/org/adaway/ui/hosts/FilterSetSaveApplyInstrumentedTest.java` | `c4ec83425667ffc605f5d8fab44cd27524b30a10149a545f0e5f4aeed38ec535` |
| `app/src/androidTest/java/org/adaway/ui/hosts/FilterSetScheduleReconciliationInstrumentedTest.java` | `75cf95d25c3504bec632aaf4080964611eb05f0816f643bf9ea8b64273fd05ae` |
| `app/src/androidTest/java/org/adaway/ui/hosts/FilterSetSchedulingRegressionInstrumentedTest.java` | `d81d7c7b5ee2b7d7dd2202b378cabdb3cf7d73f602b03cf3af89406f89c36874` |
| `app/src/androidTest/java/org/adaway/ui/hosts/FilterSetStoreMigrationTest.java` | `20d32d83d3d11b2455e460a1b83fb2ccfda83dfc713a8610ad92f1a1e5d0fa69` |
| `app/src/androidTest/java/org/adaway/ui/hosts/FilterSetUpdateServiceWorkManagerTest.java` | `5e9cbdf3249a2970a262db3a7751f612131b07cc344c45653d5ff24b701407ec` |
| `app/src/androidTest/java/org/adaway/ui/hosts/SchedulesLifecycleRegressionInstrumentedTest.java` | `52a552f88a4354c5ee6d1cb95a2e6cb3613252a95eafea6d1aa69ff46fcff690` |
| `app/src/androidTest/java/org/adaway/ui/hosts/SchedulesNavigationInstrumentedTest.java` | `5979bbd1b5be243f28f175f4d0a37de78c8771acac11856cbb492baabcc34ddf` |
| `app/src/androidTest/java/org/adaway/ui/hosts/SourcesApplyStateRegressionTest.java` | `5cc2ac364c371a49b2a463b6ed20a808d76c0897ee3a6d1f664e211061191c56` |
| `app/src/androidTest/java/org/adaway/ui/hosts/SourcesToggleApplyInstrumentedTest.java` | `b633b768a2085e4fb0d7121367cfcc7a84e7d2ad55f626961ea7d4455350a35d` |
| `app/src/androidTest/java/org/adaway/ui/hosts/SourcesUpdateAllInstrumentedTest.java` | `e9ce98ed1925a29d806cbc7121086858df50d21d2d7f4740ba3c45fb8681b4fe` |
| `app/src/androidTest/java/org/adaway/ui/source/SourceEditAddEditInstrumentedTest.java` | `3c19c9afe3269536cf1248640924724d84aad0659af5b434fb18d7c895bda6e6` |
| `app/src/androidTest/java/org/adaway/ui/UxDeviceMatrixTest.java` | `3369c4dfc94b1cb27a1ffc4ce715366709746614eefed370b012c110c848d38f` |
| `app/src/androidTest/java/org/adaway/vpn/VpnDnsTunnelInstrumentedTest.java` | `15a3efb7e715d8558fd4414044feefc7cb3dbcb494e42e8db1e4ac65968f20c1` |
| `app/src/androidTest/java/org/adaway/vpn/VpnLifecycleInstrumentedTest.java` | `d3d741c10e6c1917913c9c42977cc88feb78642a2d5a81cdd44247240aa57213` |
