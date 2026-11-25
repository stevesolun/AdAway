# Arbitrary DNS filter: bounded Android signoff

Use the [signed candidate](signed-upgrade.md#deep-qa-phone-candidate) for
phone testing. This checkpoint is about user-selected filter sources, not
one region, a root installation, or browser-extension cosmetic filtering.

| Journey | Observed on a non-root emulator |
| --- | --- |
| Enter a custom public URL | HaGeZi Multi LIGHT imported 85,887 rules and remained selected with the defaults. A different Dan Pollock URL imported 13,118; 25 unsupported lines were reported. |
| Turn one list off and on | The recorded HaGeZi journey changed direct DNS results for two list-specific domains without removing the defaults. A cached system DNS response was distinguished from the direct VPN result. |
| Upgrade with a selected list | A public-to-candidate upgrade chain kept app identity, the checked custom list and its 13k rule count. Restarting the Home switch restored protection. |
| Browse ordinary sites on the phone candidate | Android Chrome returned HTTP 200 for Debian, NASA and Example Domain; the expected headings or document titles appeared. The listed media.fastclick.net failed DNS. |
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
Three editor edge cases identified in source (document grant refusal, an
already removed source, and scheduling an unsaved URL) are logged separately
from the demonstrated successful URL flow. No public 13.5.7b release is
claimed here.
