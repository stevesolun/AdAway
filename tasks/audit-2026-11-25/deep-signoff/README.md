# Non-root signoff inventory

This is the frozen discovery inventory, **not** a release or journey pass list. In particular, PU-03 describes the defect as found. See [startup journeys](../deep-startup-journeys.csv) and [startup bug](../deep-start-bugs.csv) for the later fix and its separate execution evidence.

For the later outcomes and remaining limits, see the
[public 13.5.7b verification](public-13.5.7b.md) and
[13.5.8b candidate evidence](nonroot-13.5.8b.md).
Earlier evidence remains in the [arbitrary-filter checkpoint](general-filter-checkpoint.md),
[signed upgrade](signed-upgrade.md#final-signed-phone-candidate) and
[verification decision](decision.md).

After public 13.5.8b, the [Sources freshness checks](post8b-freshness.md)
cover real offline/reconnect, rotation and named toolbar selection. The
[coverage reconciliation](post8b-unmapped-controls.csv) revisits exactly the
75 original rows without a linked test. An observed variant is not proof of
all its click chains; remaining steps are listed per row.

| File | Meaning |
|---|---|
| `control-oracles.csv` | 260 control/journey variants: route, action, intended effect, relevant test methods and their limits, hands-on check and open gap. Blank methods are intentional. |
| `story-index.csv` | All 104 canonical stories; 90 link to oracles and 14 have explicit umbrella, orphan, excluded or release-only dispositions. `WF:` IDs refer to earlier workflow rows, not canonical stories. |
| `resource-crosswalk.csv` | 54 XML resources (37 layouts, 8 menus, 9 configuration/shortcut XMLs); includes inactive and root-only resources, not fabricated routes. |
| `method-links.csv` | Exact method-to-oracle links and source pointers from the discovery snapshot. A fixture or a screen-entry assertion proves only what it actually asserts. |
| `ci-methods.csv`, `ci-provenance.json` | Per-method Android 14 execution, separate from coverage. Includes unmapped methods, skips, the failed method, host-only links and methods absent from this run. |
| `discover-pick-order.md` | Signed non-regional source recovery, cancellation, multi-selection, removal and DNS; separate debug regressions for delayed row opens. |
| `source-category.md` | Exact signed APK: public URL and local file, domain attribution, on/off/on DNS, saved-task recovery, and scheduling without losing Apply. |
| `source-category-phone.md`, `offline-ui-stress.md` | Independent public URL, ordinary sites and challenge result; separate bounded offline stresses. Each names the tested binary. |
| `recovery-android14-methods.csv` | Consolidated debug results, including the initial schedule-picker failures and test-only replay; see the [decision](decision.md) for app identity, counts and limits. |
| `source-category-android14-methods.csv` | Earlier debug run on app commit `6da4194eb`, one uninterrupted API 34 online run with skip reasons. |
| `discover-order-android14-methods.csv` | Earlier 672 unique debug cases on app commit `8e2eace32`, including skip reasons and which exact-binary execution supplied each terminal result. |
| `idle-fix-android14-methods.csv` | Earlier local 669-case run on app commit `17a7d1415`: terminal result and reason per case. The last column preserves prior control links; it is not a new per-control claim. |
| `final-android14-methods.csv` | Earlier 668-case run on app commit `07d396dd0`, preserved for historical journey references. |

[CI run 45661198805](https://github.com/stevesolun/AdAway/actions/runs/45661198805), commit `3837d1951138b531410bb5767f7bf1a434f5dea4`, artifact `15048006671`: 626 cases, 607 passed, 18 skipped, 1 failed. That failure was the final SQLite cleanup assertion in `SourceModelOfflineFileTest#targetedDisabledUrlCleansUpOfflineWithoutTouchingUserRules`, not proof of user-rule loss. The archive and XML digests are in the provenance file. Neither these results nor historical manual evidence transfers to a later branch build.

Privileged root runtime is out of scope, not failed. Live consent and VPN traffic, notification shade actions, physical launcher shortcuts, per-app routing, signed upgrade, supported API/OEM variants and human visual acceptance each require their own evidence. No raw CI XML, APKs or screenshots are included here.
