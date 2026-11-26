# Arbitrary DNS filter: bounded Android signoff

Use the [signed candidate](signed-upgrade.md#final-signed-phone-candidate) for
phone testing. This checkpoint is about user-selected filter sources, not
one region, a root installation, or browser-extension cosmetic filtering.

| Journey | Observed on a non-root emulator |
| --- | --- |
| Use an arbitrary public list as the only active source | Firebog EasyList loaded 52k rules. On the new signed APK the ad host returned 0, 8, then 0 IPv4 addresses with the list on/off/on; the control kept both addresses. [Final signed measurements](source-category.md#phone-candidate). |
| Leave, rotate or restore Sources before applying | Pending changes survived tab replacement and Android restoring the task with a new process. Scheduling a named snapshot did not hide Apply. Database failures and competing taps have separate [source-category regressions](source-category.md). |
| Select, cancel, subscribe and remove multiple filters | On signed app commit `8e2eace32`, selected MoaAB and Hosts Blocking, confirmed 1 and 2 candidates, saw two actual additions, and removed only those subscriptions. The separately entered Prigent-Ads URL retained its 4k rules. See [tested steps and limits](discover-pick-order.md). |
| Block a logged request and undo it | On signed app commit `17a7d1415`, www.example.com returned two IPv4 answers, then zero after Block and Apply, then two after removal and Apply. The same APK assigned and removed a test redirect. See [measurements and UI checks](pending-apply-and-locale.md). |
| Enter a custom public URL | HaGeZi Multi LIGHT imported 85,887 rules and remained selected with the defaults. A different Dan Pollock URL imported 13,118; 25 unsupported lines were reported. |
| Keep a third filter through an upgrade | Firebog's EasyPrivacy domain list showed 43k in the Sources UI before and after a public-to-local update and remained enabled. |
| Keep a fourth arbitrary URL and use Domain Checker independently | Firebog's AdGuard DNS list stayed selected with a 190k badge across an in-place update. Block and Delete changed real VPN answers in both directions; the Block confirmation survived rotation. |
| Import browser conditions without overblocking | Added a file with one unconditional rule and two conditional rules through Android's picker. The named unconditional domain was blocked, both browser-conditional domains still loaded, and turning off the file restored the blocked domain. |
| Turn one list off and on | The recorded HaGeZi journey changed direct DNS results for two list-specific domains without removing the defaults. A cached system DNS response was distinguished from the direct VPN result. |
| Upgrade with a selected list | A public-to-candidate upgrade chain kept app identity, the checked custom list and its 13k rule count. Restarting the Home switch restored protection. |
| Browse ordinary sites on the phone candidate | On signed `8e2eace32`, after adding Prigent-Ads from its URL, Chrome loaded The Linux Kernel Archives and RFC Editor (both HTTP 200) and failed DNS for doubleclick.net. Earlier candidates also loaded Debian, NASA and Example Domain. |
| Record, pause, sort, clear and refresh | Actual DNS traffic populated Log. Upon pause and clear, new DNS queries still filtered correctly and refresh kept the list empty. Two independent regressions cover an uncommitted recorder append and a stale visible snapshot. |
| Use a large local file repeatedly | Two real 5,000,000-rule refreshes survived on the same 3 GiB emulator after reproducing a foreground memory kill on the old code. Both retained 75 user rules. |
| Protect a user's rule scope | An exact allow at a suffix rule's apex left the child blocked. Removing/replacing the exact rule did not delete its independent suffix row. |

See the [bug register](../bugs.csv), [domain and source journeys](../deep-general-journeys.csv),
[matched stress results](../deep-stress-journeys.csv) and [verification decision](decision.md).
The 260 enumerated UI variants are an inventory, not 260 successful runs.
The 65 locale files checked for Custom Rules had no unknown keys, empty
translated values or typed-placeholder mismatches in the checked strings;
many still rely on English fallbacks. Neither structural review nor two
tested layout languages establishes linguistic QA for all locales.

**Remaining:** physical handset testing is user-deferred. The independent
pre-push worker stopped on its usage limit; branch protection stays in
place. The intermittent Android 11 first DNS timeout has not been explained.
Domain Checker feedback loss across rotation was reproduced and fixed.
Two focused Android package runs passed 18/18 each; the signed app also
kept the confirmation across rotation and cleared it on the next check.
The [editor recovery candidate](signed-upgrade.md#filter-editor-recovery-candidate)
addresses the document grant refusal, deleted-source lookup, orphaned
schedule and canceled-picker address loss. All 21 editor tests passed. No
public 13.5.7b release is claimed here.
