# General filters: selection, recovery and removal

Android 14 emulator; system VPN permission granted in the public APK. No root.
Raw run directory: `app/build/reports/audit-2026-11-25/deep-discover-order/`.

| Build and action | Observed |
| --- | --- |
| Public 13.5.6b: enter an unrelated URL as GeneralLight | The entered raw GitHub light.txt URL returned HTTP 404. Its saved row correctly had zero hosts and no successful update. |
| Upgrade in place and edit the failed source | Entered `https://v.firebog.net/hosts/Prigent-Ads.txt`; the endpoint returned 200 and 70,554 bytes. The app displayed 4k imported rules. This was on the intermediate signed candidate, not a second import on the final one. |
| Directory row vs subscription switch | On the intermediate build, MoaAB opened the editor with the catalog label and external URL. Back left its switch off; resolving its metadata is not a successful provider download. |
| In-place installation of final signed candidate | The app ID (10192) and first install time (02:58:22) remained unchanged from the public APK. Installed file matched the [signed candidate](signed-upgrade.md#final-signed-phone-candidate). |
| Final: activate protection and check two real domains | Direct VPN DNS returned zero A addresses for doubleclick.net and one for www.kernel.org. Three default sources were enabled alongside GeneralLight; this is not single-source attribution. |
| Final: select one and then two eligible catalog rows | The first confirmation showed one candidate. Cancel left MoaAB checked but unsubscribed. Added Hosts Blocking, saw two candidates, canceled, reopened and confirmed. The worker reported Added 2, Already 0, No URL 0, Unsupported 0. |
| Final: show subscribed and remove selected | Both chosen rows appeared. After confirmation of removal there were no matching subscribed directory entries; Sources retained the unrelated GeneralLight (4k) and its three defaults. This checks subscription ownership, not rule import or ad removal by these two providers. |

Snapshots 01-09 are public; 10-25 intermediate; 26-46 final. Key evidence:
`31-final-confirm`, `35-final-two-confirm`, `39-final-bulk-settled`,
`40-final-show-subscribed`, `44-final-after-settle`, `46-final-subscriptions-after`.

## Fresh signed install and browsing

Installed the exact Downloads APK on a clean app profile, verified its
installed SHA-256, started the recommended VPN mode and accepted Android's
consent dialog. Home showed active protection with three defaults. Pasted
the Prigent-Ads URL again, this time directly on the final APK, as PrigentQA.
Sources reported 4k for this row; after browsing Home showed four up-to-date
sources and 83.8k distinct blocked hosts. An incidental blank Add screen was
left with Back, without saving another source.

Android Chrome, no account, cache disabled:

| Destination | Main document | Visible result |
| --- | --- | --- |
| www.kernel.org | HTTP 200 | The Linux Kernel Archives; completed page with 104 links |
| www.rfc-editor.org | HTTP 200 | RFC Editor; The official home of RFCs; 38 links |
| doubleclick.net | No response | ERR_NAME_NOT_RESOLVED; Chrome showed its unavailable page |

See `48-signed-fresh`, `49-fresh-consent`, `50-fresh-active`, `55-fresh-saved`,
`58-post-browser-home` and `final-browser.json` (SHA-256
`b5cd3885acf428cf6796b3baa58fd76292bfd14e44f859ae35bd056f9ecb6678`).
The enabled filters overlap; this does not attribute the block exclusively
to Prigent, measure cosmetic ad removal, or claim a speed/security benchmark.

## Rough input and reconnection

On the same signed app, disconnected Wi-Fi and mobile data and directed
2,000 random input events only at AdAway (seed 262507). The runner recorded
14.052 s disconnected, no app crash or unresponsive-process report, no
dropped pointer/key events, and four dropped hardware flip events. The crash
buffer was empty. Random taps changed the preset, search and filters; this
was not a test that settings remain unchanged.

Restored connectivity and observed a connected VPN. Discover correctly
showed its offline Retry state. The immediate first retry still showed the
error; after clearing the random query, another retry restored tags and
languages. With Show subscribed still on, no rows matched. Turning that
switch off rendered the live catalog again. See snapshots 59-64 and
`final-offline-input.txt` (SHA-256
`221e7e696cc1c0593b072ca9422c72b569ab557a3e0369e13d59c983de894df3`).
The two disposable emulator images were removed after evidence collection.

## Fault injection

Held a subscribed row's disk lookup, tapped another compatible row, then
released the first lookup. Before the fix the test saw two editor launches
instead of one. After the fix only the last tap opened an editor. A separate
case stopped the screen during the lookup and verified a new tap on return.
Temporary lookup failure, a detail without a URL, and successful retry with
a URL but no detail name were tested in sequence. The directory name reached
the real editor and Back did not save. Before the fix its label was blank.
The two focused classes passed 17/17. See `red-test.txt`, `label-red.txt`,
`green-test.txt` and `focused.txt`; [finding register](../deep-discover-order-bugs.csv).

Independent review also found that CI did not run the existing hash-drift
check. On the new app commit six changed files still had old "reviewed"
hashes, and the seventh changed file also differed. The development build
now runs the check immediately after checkout, before creating any outputs.
It detects stale evidence; it does not require every file to be reviewed.

## Suite provenance

For suite provenance, the installed debug/test SHA-256 values on both
devices were `4299f5f5c043ace69c3904db8e18c2a8646e254a61750f3506c49cd28c121b07`
and `979185dc4d3b4d5cf3a12649e6343ef6dc40c140fedc6472354e300bad67f212`.
The frame of reference for method names was an earlier uninterrupted run
before the confirmation resource change: 653 pass, 19 skip in 626.767 s.
It is not credited to the final binaries. Final raw SHA-256:
`final-full.txt` `2637378dd7b0303484b58486763e552d355d9b54ca7e7bd4142f26fa6edd6f78`;
`final-remainder.txt` `4973ded68c657a1db4bf68bc60a80e0fb50018ef0a471079848bbbf9e7a26669`.
The conditional Quick Settings case skipped on the final run because the
system did not dispatch the click. See the [deduplicated results](discover-order-android14-methods.csv).

## Archived documentation

The optional native archive is not part of the phone APK. The 54 function
manuals were reviewed for API declarations, description and return contracts;
55 SYNOPSIS blocks (including the umbrella page) passed mandoc lint and C
compilation against the archived header, covering 71 named declarations.
The umbrella page's full text and most of the native implementation are not
signed off. The offline status regression compiles the actual function with
an inert callback: one of four cases failed before, all four passed after.
The historical CSV checks compare 239 finding records in 66 reports to the
central register (178 unique IDs). Two reports had headers only; differences
in archived statuses were retained. These are data checks, not 239 new retests.
