# Home Flow Fixes Review

Scope: HOME-QA-007 and HOME-QA-008 only. Implementation is test-only.
Corrected test source frozen at 2026-11-25 11:32:29 UTC for main's rerun.

## Frozen Source

- New file: `app/src/androidTest/java/org/adaway/ui/home/HomeFlowRoundTripInstrumentedTest.java`.
- Class: `org.adaway.ui.home.HomeFlowRoundTripInstrumentedTest`.
- SHA256: `cb5d718383a1ea1ff32ba60eba4787d3212cbbc532ee63acd7beca47f6af93a0` (501 lines, fully reread).
- Four @Test methods: two counter fixtures and two settings routes. No new workflow scope.
- No production file or existing Home test was edited. In particular, HomeCounters and HomeLeakStatus remain unchanged.
- No Gradle, adb, staging, commit, compile or device execution was performed by this sidecar. Main owns build and runtime results. Source freeze is not a pass claim.

## Counter Navigation

The existing HomeCounters test asserted numbers but never clicked a counter.
ListsTabs separately launched ListsActivity with an explicit tab; that does not
prove the Home click listener. The new tests start from the Home cards and inject
touch events at their visible screen coordinates after requesting scroll reachability.

For each blocked, allowed and redirected card, both empty and populated fixtures
require the real destination intent's TAB extra, pager index, selected navigation
ID, correct fragment type and actual content. Empty fixtures wait for NO_RULES,
not just zero adapter items during loading. Populated fixtures assert the matching
host, absence of the other types, and the redirect address. Each destination is
recreated and rechecked, then actual Back must return to the same Home instance
with Home selected. Home is recreated before continuing. Final row counts ensure
navigation did not add/delete fixture rules.

The database is an isolated in-memory Room instance with a real USER source and
zero or three real rows; the application database singleton is restored afterward.
No source download, privileged root apply, live VPN filtering, process death or
physical rotation is claimed.

## Leak Settings

Production routes read:
- HomeFragment counter-card listeners and startHostListActivity.
- HomeFragment opens `android.settings.PRIVATE_DNS_SETTINGS` with a wireless-settings fallback on ActivityNotFoundException; VPN route uses `Settings.ACTION_VPN_SETTINGS`.
- HomeFragment.onResume calls renderLeakStatus; LeakStatus reads actual Android global settings, persisted application preferences and framework-backed VPN status.

The new settings tests click the actual Home buttons. An ActivityMonitor observes
the outgoing attempts and returns null, so it neither blocks nor substitutes the
Android destination. Private DNS allows the real production wireless-settings
fallback, requiring the attempted actions in order. Tests also require the resolved external package to be the
foreground accessibility window. An intent-only assertion cannot satisfy the test.

Private DNS: a real global hostname-mode/provider fixture makes the button
available. After Home recreation and the real settings launch, delegated
WRITE_SECURE_SETTINGS writes the actual Android global mode to off. System Back
must return to Home and update the detail, two-risk summary and hidden Private DNS
button. Another recreation must retain the off detail. This deliberately tests
return-state refresh without depending on OEM-specific radio-button text; it does
not claim a manual settings-toggle path or working DNS-provider connectivity.

VPN settings: with no active tunnel, the real VPN settings window is opened.
While Home is away, the actual persisted excluded-app set changes to include the
installed AdAway package. Back must refresh the exclusion detail, four-risk
summary and stopped-VPN text; recreation retains the exclusion detail. Framework
network and AdAway running checks must still report no VPN. This is real
persisted-state refresh, not a manually driven exclusion picker or live VPN
revocation test.

## Fixture Boundaries

- The suite assumes no active VPN before any shared passive-reset operation. An active VPN produces an explicit assumption skip, not a pass or forced shutdown.
- The settings tests require API 29+ shell-permission delegation. Counter tests have no new API29 precondition.
- The app uses the existing passive-UI reset helper to drain/cancel background work and reset preferences. Run serially on main's disposable test device, not against personal configuration.
- Both original Private DNS values are captured before modification. Teardown restores them, including null values, and drops delegated permission in a finally block on every write.
- Teardown removes the intent monitor, backs out of the owned Settings package if still foreground, finishes remaining Lists activities, closes Home, resets passive state, restores the prior database singleton and closes only the test database.
- No global synchronous LiveData executor is installed. No protection/navigation model is replaced with a fake.
- Tests do not use shell commands, force-stop Settings, disable packages, revoke permissions or start a VPN. Real settings provider writes happen only when main runs the tests.

## Still Pending

The original phase-three APK passed both counter methods and VPN settings. Private
DNS failed in the fixture before the state-change/return assertions. The corrected
source above awaits main's build and rerun; it is not a passing fallback claim.
No central workflow status has been changed. HOME-QA-008 still needs separate
evidence for devices with neither settings handler, actual VPN consent
revocation/live status transition and real exclusion-picker/traffic behavior.
The configured `dns.example` hostname is only settings state, not provider reachability.

No demonstrated new product bug was found in this source-only pass, and no
product correction is proposed. Should the new connected assertions fail, report
the exact failure separately for approval rather than editing product code
under this ownership. The unguarded VPN settings launch is a visible source
boundary, not a reproduced unavailable-handler defect.

## Verification And Inventory

Performed: full new-test read plus final assertion-diff review; existing test and
scoped product reads; whitespace check; all referenced resource names found;
method/API/DAO call sites inspected locally. No compilation or runtime validation
is implied by those checks.

`home-flow-fixes-files.csv` contains only the eight full-file-read rows and their
SHA256 values. Partial context reads and declaration searches were removed from
that import inventory; they must not mark whole files reviewed. These are
handoff-time source hashes, not APK identity. `home-flow-fixes-tests.csv` names
each method, its exact intended
path, state provenance and limits. Main can select the class above in its next
combined run. All earlier evidence mappings and central workflow/bug ledgers
are untouched by this change.

## Phase-Three Fixture Correction

Read the relevant records in `/tmp/adaway-audit-phase-three-api34.log`, not the
whole log: lines 658-681 show Private DNS failing at the old waitForSettings
resolveActivity assertion; lines 687-723 show status 0 for both counter methods
and VPN settings. The full run reports 96 tests / 2 failures at lines 1390-1407;
the other failure belongs to ListsRuleFixesInstrumentedTest, outside this ownership.
The original test source hash was
`725b2c063869525df454422f06d2fdd55dfb5c32dc5b63a50171e57c305160f4`.

Main supplied the completed API34 device probe: PRIVATE_DNS_SETTINGS has no
activity; WIRELESS_SETTINGS resolves to
`com.android.settings/.Settings$NetworkDashboardActivity`. HomeFragment lines
874-880 already catch ActivityNotFoundException and launch that fallback. Those
production lines were reread as partial context, not a whole-file review.

The old nonblocking monitor captured the first attempted intent even though it
could fail. Resolving only that attempt made a valid production fallback fail the
test. The corrected monitor keeps an ordered thread-safe list of both permitted
attempts. The wait requires the first action to be Private DNS, permits exactly
one subsequent WIRELESS_SETTINGS attempt only for that route, resolves the last
attempt and requires its external package to be the actual foreground window.
A missing fallback does not skip or pass; it times out. Supported direct routes
still require the direct destination. VPN permits only its original single action.
The test prints the observed route and foreground package for main's next log.

The actual Settings.Global write, provider-value checks, system Back, refreshed
detail/risk/button assertions, recreation and restoration remain unchanged. No
product, manifest, package query or permission edit was made; no fake Settings
activity, intercepted result or injected exception was added. Both passing counter
method bodies are unchanged. The shared observation helper also serves VPN, so
rerun all four methods on the rebuilt test APK.

Public API checks: ActivityMonitor returns null to leave the launch unblocked
([Android reference](https://developer.android.com/reference/android/app/Instrumentation.ActivityMonitor#onStartActivity(android.content.Intent))).
Android recommends attempting the launch and handling ActivityNotFoundException
under limited visibility; a package query alone is not launch proof
([package visibility guidance](https://developer.android.com/training/package-visibility/use-cases)).
WIRELESS_SETTINGS is the public wireless-controls action and may itself lack a
handler on some devices
([Settings reference](https://developer.android.com/reference/android/provider/Settings#ACTION_WIRELESS_SETTINGS)).
Main's actual device resolution, rather than an assumed visibility explanation,
establishes the missing-direct-handler branch here. No further device output is
needed before the rerun. Performed here: full 501-line reread and whitespace scan;
no compilation, Gradle, adb, staging or commit.
