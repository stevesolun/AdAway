# Legacy Catalog and Discover Label Follow-up

## Phase-two Fixture Corrections

Main's `/tmp/adaway-audit-phase-two-focused.log` reports 87 tests / 11 failures.
Eight failures belong to these fixtures (summary lines 1696-1860). These are
executed failures, not the previously reported typecheck-only evidence. The two
catalog cases for individual hidden-selection pruning and filtered preset identity
passed (status-code 0 at lines 1207-1213 and 1331-1337).

**Catalog click fixture:** all four failures stop at the assertion on
`clickSelectAll()`'s `performClick()` return value, before later selection checks.
The production control has an OnCheckedChangeListener, not an OnClickListener.
Android's CompoundButton toggles first and returns the superclass click-handler
result, so a successful switch transition need not return true. This is confirmed
by the [Android 14 CompoundButton implementation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-14.0.0_r1/core/java/android/widget/CompoundButton.java)
at lines 132-141. The fixture now asserts that the actual control is present,
enabled, and clickable, invokes the same `performClick()`, then requires its checked
state to change to the opposite value. All existing N-1 selection, row state,
hidden-selection, preset identity, and Add-button assertions are unchanged.

**Label inflation fixture:** all four failures are InflateException caused by
`RecyclerView.generateLayoutParams`: the fixture's parent had no LayoutManager.
Production `DiscoverFilterListsFragment.onViewCreated()` installs a
LinearLayoutManager before adapter creation. The fixture now does the same before
calling the real adapter's `createViewHolder`. Only the import and one setup line
changed. All four font/orientation cases and every exact-text, line-end, ellipsis,
height, and non-overlap assertion remain unchanged. The failed run never reached
those geometry assertions; it is not evidence that text wrapping failed or passed.

**Current checkpoint:** only the two test files changed in this follow-up; the
previously staged catalog production fix and real row layout are byte-identical.
Changes remain unstaged over main's staged phase-two inputs. Isolated Android
API36 / Android Java-image type-check passed again for the five-source set listed
below; scoped diff whitespace checks passed. No Gradle, adb, emulator, stage, or
commit actions were taken. Main must rerun the corrected fixtures; no eight-case
runtime pass is claimed. Source/test freeze is ON again.

## Freeze Checkpoint

Source/test freeze: **ON**. Main may compile these inputs. No further app/test
edits are planned by this lane. Three inputs changed:

- `app/src/main/java/org/adaway/ui/discover/DiscoverCatalogFragment.java`
- `app/src/androidTest/java/org/adaway/ui/discover/DiscoverCatalogSelectionInstrumentedTest.java`
- `app/src/androidTest/java/org/adaway/ui/discover/DiscoverLabelLayoutInstrumentedTest.java`

The existing label layout is unchanged. The six source/test hashes in the prior
system-command sidecar still match. Main's Home geometry and real boot-consent
validation are separate and were not modified or claimed as this lane's evidence.
No Gradle, adb, emulator, staging, commit, or push actions by this lane. Main's
phase-two Android results are recorded above; no Android tests were executed by
this lane. Root runtime remains excluded.

## Legacy Selection: AUD-SRC-L01

**Reachability:** production searches for `DiscoverCatalogFragment`,
`fragment_discover_catalog`, and `FilterCatalogActivity` found only the catalog's
own source/binding references and its obsolete comment. Current Discover creates
`DiscoverFilterListsFragment` (`DiscoverFragment.java:69`); Home routes to that
Discover wrapper (`HomeActivity.java:184`). No manifest, navigation resource,
production constructor, or reflective class-name route to the legacy catalog
was found. Existing catalog references elsewhere were tests and historical docs.
The misleading catalog comment now explicitly describes its legacy status. No
route was added. This remains a latent P3 bug, not a reproduced active-route bug.

**Baseline source proof:** baseline SHA-256
`99c57f9eb54c9e9fe71592d3060c482d8e1f91f64773ea695f6ac84f597c2b77`.
After Select All, deselecting one row set the master switch false at old lines
454-455. Its checked-change listener (old lines 84-98) cleared the entire set.
Filtering while the master was checked repeated that path; individually selected
hidden rows instead remained selected. Main has since executed the direct host:
two cases passed and four stopped in the click fixture as detailed above. The
core deselect-one regression still awaits a successful corrected-fixture run.

**Minimal fix:**

- Lines 85-86 and 244-260 guard programmatic master-switch updates. The master
  reflects whether all visible eligible rows are selected; updating that display
  does not run the user's clear-all command.
- Lines 166-169 and 238-242 prune selections to visible rows after search. Clearing
  the filter cannot resurrect the removed checks. The Add count/visibility updates
  immediately, including the empty-result case.
- Line 199 applies the same visible-only policy when a preset is selected under
  an active search. Pruning a preset also clears its full-profile identity so a
  partial visible selection is treated as Custom, not as a complete preset.
- The existing row listener (473 onward) updates the selection then refreshes the
  master through the guarded path. An explicit user clear-all still clears.

**Direct-host fixture:** six cases in
`org.adaway.ui.discover.DiscoverCatalogSelectionInstrumentedTest`. It mounts the
real catalog through a test-only FragmentTransaction in Home, waits for the real
adapter load, then seeds in-memory deterministic rows. It clicks actual master
and row controls and checks selected entries, checked rows, and Add-button counts.
No Add/Remove/apply action is invoked and no test source rows are written to the
database. The existing passive-UI reset helper runs only when main executes this
fixture; it resets preferences/work state and is not root-runtime evidence.

Cases: Select All then deselect one retains N-1; filtering Select All retains the
visible subset; individual hidden selection is pruned; selecting every row updates
the master and explicit clear-all works; filtered preset cannot select hidden rows
or retain full-preset identity; existing sources are excluded and empty results
clear hidden selection.

## Label Layout: AUD-SRC-012 Evidence Gap

The prior UX matrix screenshot showing Loading did not prove wrapped loaded row
labels. This lane adds test evidence, not another production fix. The current
`filterlists_import_item.xml` already has uncapped name/format text and
`wrap_content` heights; its SHA-256 remains
`13a8a2943ad0d52399db6150da4109c47e49819c8b29711911b5126c028543fe`.

`org.adaway.ui.discover.DiscoverLabelLayoutInstrumentedTest` has four independent
cases: portrait and landscape available-width configurations at 1.0 and 1.5 font
scale. Each constructs the real private directory Adapter using reflection,
inflates its actual row XML, and binds a deterministic long name plus two long
format labels through the real formatter. It does not substitute TextViews or
write display text directly into the labels being asserted.

The test provides a context-only fragment to support real binding without starting
the fragment's directory/network lifecycle. Its resolved URL and syntax maps are
fixture-local. Configuration contexts use the device's available short/long dp
bounds, the app theme, and local font/orientation overrides. No system font setting,
device rotation, preferences, cache, permission, or channel changes are needed.

Assertions require exact bound strings, actual multi-line wrapping, no ellipsize
mode, zero ellipsis count on every line, contiguous line starts/ends through the
final character, sufficient height including padding, bounds inside the measured
row, vertical separation of name/format/description, and separate checkbox/switch
columns. This is a deterministic measured-row fixture, not a new full-screen
screenshot or proof of physical rotation or scrolling accessibility.

## Checks and Handoff

- **Passed:** isolated Android API36 / Android Java-image type-check for the changed
  catalog, unchanged directory fragment, both new test classes, and existing
  `InstrumentedTestState`. Used JDK21 with cached dependencies, generated binding/R
  classes, `-proc:none -implicit:none -sourcepath ""`, and temporary output. The
  initial classpath lacked LifecycleOwner; adding cached lifecycle dependencies
  resolved that check without source changes. No Gradle build was run here.
- **Passed:** two existing host `DiscoverCatalogDetachSafetyTest` source contracts.
- **Passed:** scoped diff whitespace check and previous system-command hash freeze.
- **Main phase two:** individual hidden-selection and filtered-preset catalog
  cases passed; four Catalog cases and four label cases failed in fixture setup
  or an incorrect click-return assertion. Exact evidence and corrections are above.
- **Pending main:** rerun six direct-host catalog cases and four measured-label
  cases after the two test-only corrections. Compile-ready does not mean
  runtime-passed; the corrected eight failures have not been rerun by this lane.

Only the owned catalog was fully reread as production source. Directory adapter,
formatter, URL-cache lookup, Home/Discover route blocks, catalog-entry constructor,
and earlier instrumented fixture setup were inspected as targeted dependencies,
not full-file audit coverage. The three relevant row/catalog XML layouts and
existing detach-safety test were read fully. No unrelated legacy risks were fixed.

`legacy-fixes-files.csv` records frozen byte hashes and review granularity;
`legacy-fixes-bugs.csv` uses the canonical eleven-column schema for AUD-SRC-L01;
`legacy-fixes-tests.csv` distinguishes earlier host passes, two phase-two device
passes, and fixture-corrected cases awaiting a device rerun.
Main owns ledger merge and integrated execution.
