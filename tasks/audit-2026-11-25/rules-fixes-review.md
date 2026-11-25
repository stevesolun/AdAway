# Rules Fixes

## Ownership and Freeze

- Approved scope: RR-001, RR-002, RR-005, RR-006, RR-007 only.
- RR-003/RR-004 and every Domain Checker Java file remain owned by main.
- Integration freeze received before any production or test edit. As of this planning entry, no production/test file has been written by this lane. Only this new sidecar is being written during the freeze.
- Await explicit GO before production/test writes. No Gradle, adb, emulator, central ledger edits, commit, or push.

## Confirmed Contracts

- `HostListItemDao.loadList` returns a Room PagingSource over hosts_lists and hosts_meta. Mutations invalidate its tables; changing the user filter is unnecessary to refresh the rows.
- `getHostId` is scoped to source_id=1; upserting an exception does not overwrite a downloaded row.
- `HostEntryDao.resolveEntry` and `resolveRootEntry` check an enabled exact redirect BEFORE checking active allow rules. An allow entry alone cannot neutralize an enabled redirect.
- Redirect disable will therefore atomically add/update one exact user ALLOWED entry and disable only the selected downloaded row. Other redirect rows remain untouched; the dialog must state that they can still apply. Reimport can restore a downloaded redirect; this lane does not change global precedence or source import policy.
- `HostListItem.setHost` lowercases/reverses labels but does not collapse repeated wildcards. Reject overlength raw patterns consistently rather than inventing a normalization contract.
- Existing AppExecutors.diskIO is a single-thread executor. New tests can hold its queue and main delivery without changing global executor ownership.

## Planned Implementation

1. Use explicit requested enabled state and immutable mutation copies. Restore the checkbox to the bound row before opening confirmation; cancellation then needs no synthetic filter refresh and every dismissal path remains authoritative.
2. Make supported moves explicit: block/allow swaps remain; a downloaded redirect can become an exact allow override; a user redirect has no meaningful Move and that menu item is hidden. Clear irrelevant redirect data on the new allow entry and wrap override plus source-row disable/stats in one Room transaction.
3. Expose shared query LiveData. Bind each editor after hierarchy restoration, ignore model-originated text changes, and opt out of independent editor text saving. Retained views observe the same query owner.
4. Remove all mutation-triggered filter replacement. Use Room invalidation; keep user search/source commands on their existing UI path and suppress identical query writes from editor synchronization.
5. Apply the 253-character raw limit before wildcard validation; share add/edit write validation; report the overlength condition in the Allowed dialog and initially validate existing edit values.

## Planned Regressions

- Actual downloaded redirect checkbox: confirmation inserts exact ALLOWED, disables selected redirect, and live DAO resolution becomes ALLOWED with an isolated fixture.
- Duplicate-redirect control: a second source's redirect remains enabled and wins; no broad cross-source mutation.
- Context Move availability: user redirect hidden; supported block/allow and downloaded-redirect operations remain meaningful.
- Actual checkbox and row cancellation: No, Back, and outside touch leave DAO/visual state unchanged; later confirmed off/on requests agree with the requested state.
- Repeated explicit same-state calls are idempotent and do not mutate the Paging snapshot object.
- Retained adjacent tabs synchronize query text and matching rows; view/activity recreation cannot restore an obsolete tab-local query over the ViewModel.
- Deterministic mutation/search interleaving: hold main delivery until a disk mutation has finished, then set a newer query/source filter before draining queued delivery; latest selection survives and Room refresh still publishes data.
- Long wildcard at add/edit boundaries: 255-character input rejected with visible error and disabled button; short controls and valid multiline entries remain accepted; no partial accepted batch.
- Standalone host tests for null/empty and wildcard length boundaries, including the previously accepted repeated-star case. Android tests and compilation are main-owned unless a standalone isolated compiler check can use existing artifacts without invoking Gradle.

## Status

- [x] DAO/runtime precedence and current list contracts read.
- [x] Freeze respected; production/test paths still unmodified.
- [x] Implementation and regression design prepared.
- [ ] Explicit GO received.
- [ ] Scoped implementation and new focused tests.
- [ ] Standalone host validation and source/compile review.
- [ ] Final hashes, exact case inventory, and fix sidecars.

No implementation, test execution, or fix verification is claimed by this planning entry.
