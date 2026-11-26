# Non-root signoff inventory

This is the frozen discovery inventory, **not** a release or journey pass list. In particular, PU-03 describes the defect as found. See [startup journeys](../deep-startup-journeys.csv) and [startup bug](../deep-start-bugs.csv) for the later fix and its separate execution evidence.

For what actually ran and what is still open, see the
[arbitrary-filter checkpoint](general-filter-checkpoint.md),
[signed upgrade](signed-upgrade.md#final-signed-phone-candidate) and
[verification decision](decision.md).

| File | Meaning |
|---|---|
| `control-oracles.csv` | 260 control/journey variants: route, action, intended effect, relevant test methods and their limits, hands-on check and open gap. Blank methods are intentional. |
| `story-index.csv` | All 104 canonical stories; 90 link to oracles and 14 have explicit umbrella, orphan, excluded or release-only dispositions. `WF:` IDs refer to earlier workflow rows, not canonical stories. |
| `resource-crosswalk.csv` | 54 XML resources (37 layouts, 8 menus, 9 configuration/shortcut XMLs); includes inactive and root-only resources, not fabricated routes. |
| `method-links.csv` | Exact method-to-oracle links and source pointers from the discovery snapshot. A fixture or a screen-entry assertion proves only what it actually asserts. |
| `ci-methods.csv`, `ci-provenance.json` | Per-method Android 14 execution, separate from coverage. Includes unmapped methods, skips, the failed method, host-only links and methods absent from this run. |
| `idle-fix-android14-methods.csv` | Current local 669-case run on app commit `17a7d1415`: terminal result and reason per case. The last column preserves prior control links; it is not a new per-control claim. |
| `final-android14-methods.csv` | Earlier 668-case run on app commit `07d396dd0`, preserved for historical journey references. |

[CI run 45661198805](https://github.com/stevesolun/AdAway/actions/runs/45661198805), commit `3837d1951138b531410bb5767f7bf1a434f5dea4`, artifact `15048006671`: 626 cases, 607 passed, 18 skipped, 1 failed. That failure was the final SQLite cleanup assertion in `SourceModelOfflineFileTest#targetedDisabledUrlCleansUpOfflineWithoutTouchingUserRules`, not proof of user-rule loss. The archive and XML digests are in the provenance file. Neither these results nor historical manual evidence transfers to a later branch build.

Privileged root runtime is out of scope, not failed. Live consent and VPN traffic, notification shade actions, physical launcher shortcuts, per-app routing, signed upgrade, supported API/OEM variants and human visual acceptance each require their own evidence. No raw CI XML, APKs or screenshots are included here.
