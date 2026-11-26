# Sources: selecting filters under load

These checks use ordinary DNS-compatible lists on a non-root Android 14
emulator. Each APK is identified separately. They do not establish cosmetic
ad removal, every possible filter format, or physical handset coverage.

## Measured failures

* On signed APK `7237fe228acb59c7b8678f0ee9a4052d33d9f69122a276a999ebeff9ae6347be`,
  collapsing Ads and then rotating or leaving and returning to Sources reopened
  the category. A debug regression independently failed both recreation and
  Home tab return (`red-rotate.txt`).
* With the first category-state correction installed, disabling Ads exposed
  `2 of 3 enabled` during a single group change (`31-ads-off.xml`). Forcing
  the second of two local SQLite source writes to fail crashed the pre-atomic
  debug application (`red-write.txt`, `red-write-crash.txt`).
* On the first atomic signed build, disabling and applying the local file
  cleared its rules. Enabling it again showed **1 of 1 enabled, 0 hosts** and
  **Apply**. Going to More and back removed Apply while leaving the file
  selected and its rule missing. The example.org DNS query returned two
  addresses, not the VPN block response. See snapshots 69, 70, 72 and probe 73.

## First corrections

First atomic signed APK: `c0d6af7ef3e820e2f6c0adb7e42e36fbd2109180a4961d94bbf92236e842c8f5`.
On this build, category folds survived portrait/landscape, switching tabs,
and Android restoring the same task after the background application process
was killed. The process ID changed; Ads remained collapsed and Custom
expanded. This test used protection off so Android could reclaim the process.

The file `CategoryControl.txt` contained only `0.0.0.0 example.org` and was
added through Android's document picker. Direct DNS observations under VPN:

| Selection | example.org | www.kernel.org |
| --- | --- | --- |
| Before adding file (original signed build) | 2 IPv4 answers | 1 IPv4 answer |
| File on, Ads off (first atomic build) | 0 answers, AdAway block authority | 1 IPv4 answer |
| File off and applied, Ads off | 2 IPv4 answers | 1 IPv4 answer |
| File re-enabled and applied, Ads off | 0 answers, AdAway block authority | 1 IPv4 answer |

On the matching development build, five targeted category cases passed,
including the forced second-write failure. Both source flags and stored rules
rolled back; retry succeeded. An empty Custom group could open the real add
sheet, but its disabled switch made no changes. The intermediate results are
not substituted for final candidate results.

Local raw evidence: `app/build/reports/audit-2026-11-25/deep-source-category/`;
the numbered Android UI trees, DNS results and build identifiers distinguish
the installed versions. No production database, credentials or APK is in Git.

## Phone candidate

Final installed APK SHA-256:
`e08123d932628ec77d6f38ed74c3727eca3494c2d94ab68f315cb5cf8a1e3570`.
Version 13.5.7b / 130507, signed with the pinned certificate (v2/v3).
In-place installation retained app ID 10192, the original install time,
the Android document grant and the checked public EasyList subscription.
The Downloads copy matched the installed SHA-256. The packaged manifest
did not set `debuggable` or `testOnly`; the VPN service requires
`android.permission.BIND_VPN_SERVICE`. These checks verify packaging,
not a general security certification.

| Final signed check | Observed |
| --- | --- |
| Toggle a filter, schedule the current selection daily, confirm the time | Apply remained visible and actionable after the notice expired (113, 118, 119). On the previous signed build the notice removed Apply until leaving and returning to Sources (104, 107, 109). |
| Enable the cleared file, visit More, return and restore the same Android task | Folded Ads, expanded Custom, zero imported rules and Apply all survived. Android killed the background process after VPN was explicitly stopped (PID 12351 to 13081; same task 16). This is not a force-stop/new-task guarantee. |
| Apply once after restoration | The file showed one rule. Direct VPN DNS for example.org went from two A answers before to no A answers with AdAway authority after; www.kernel.org returned one A answer in both cases. |
| Android Chrome with VPN and the file active | Kernel Archives and RFC Editor returned HTTP 200 and rendered 104 and 38 links; example.org failed DNS, with no main document response. Browser cache disabled. |
| Isolate a public filter, not the local fixture | Only Firebog's EasyList domain mirror was enabled (52k badge). With the VPN running, pagead2.googlesyndication.com returned **0, 8, 0 IPv4 answers** as the list was enabled, disabled/applied, then re-enabled/applied. example.net returned the same two addresses throughout. Both groups showed disabled in the off state. |
| Trace a block to its source, allow it, then undo | Domain Checker showed **Blocked by: PublicEasyList** for that same ad host. Adding an exact allow returned one live IPv4 answer; removing the allow restored the original source attribution and block. The independent control kept two addresses. UI 157-160, DNS 159/161, screenshot `why-blocked.png`. |

Evidence: `phone-installed.txt`, `phone-signer.txt`, `phone-chrome.json`,
UI trees 110-151, probes 126, 136, 146, 149 and 152. Raw IPs can change;
the matched comparisons and block authority are the relevant observations.
See also the [independent public-list test](source-category-phone.md) and
[bounded offline interaction stress](offline-ui-stress.md), each with its own APK hash.

## Regression controls

* A held Room delivery exposed the old visually checked master for a partial
  group. On the same earlier build, named Save persisted two URLs instead of
  the three the user had selected (`red-additional.txt`).
* In the final connected tests, forced second-write errors check stored rules
  and source flags together. Unrelated successful writes keep their results
  without concealing the failure; deleting the failed sources clears the
  notice. Retry works for an unchanged saved set. Renaming, deleting,
  replacing it under the same name, or editing its URLs before queued retry
  executes does not alter sources, rules or the active profile.
* On app commit `6da4194eb`, the two category and filter-set classes passed
  all 16 cases in 47.389 s. The separate consolidated build passed all 25
  focused cases across these two classes and the source-apply class, before
  the four stale-retry cases were added.
* The signed editor did not save an FTP subscription; backing out returned
  to the same sources (162-169). This is a scheme-rejection check, not a
  complete security assessment.

The Schedule current selection test on the signed app used actual controls
but did not force the disk queue to stall at snapshot time. The retry checks
cover edits before queued validation, not an independent rename between the
database write and the later preference write. These are different from the
verified database rollback guarantee. A physical phone remains deferred.

The first attempt to run the complete final suite mistakenly used the
offline-stress AVD. It had a launch-time dead HTTP proxy and local-only DNS;
enabling Android's network switches did not give it a validated network.
Three Discover preset background-work tests timed out, and the runner was
explicitly stopped during case 302. Its `Process crashed` footer follows
that deliberate force-stop. Do not count this as a completed or green run.
All three methods subsequently passed on the online AVD. Raw first-attempt
log: `phone-suite.txt` SHA-256
`4cbb9bb68dc6e8d2da79bef736e375e9a202da93684ffb018397e23bf1c0ddd4`.

The uninterrupted replacement run on the network-validated AVD finished all
687 cases in 483.82 s: 668 passed, 19 assumption skips, zero failures or errors.
Installed debug app SHA-256 `a74d233d351fec18990782f0891f9be7252172a846e085b669f4f4cf72514f8e`;
test APK `ee0160930394d954b47be45519e638a899038cb5f576aaa99efc4e68f34bfa96`;
`phone-online-suite.txt` `ff23f7f5af1ce6b16a3d109adc2ed499f8358997eea36674eb4a278a90de474a`.
[Per-method results](source-category-android14-methods.csv). All three
disposable AVDs were stopped and removed after verification.
