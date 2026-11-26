# Public source and Chrome: intermediate signed emulator

**Result:** On 2026-11-26, two independently operated public sites loaded in
Android Chrome with the new list active. A listed hostname lost its IPv4
answer, regained it when only the list was disabled, and lost it again when
the list was re-enabled. A separate unlisted hostname resolved every time.
One other site returned an HTTP challenge; it is not counted as a page-load pass.

## Installed build

- Device: `emulator-5576`, Android 14; AdAway `org.adaway` 13.5.7b
  (130507), non-root VPN; Chrome `com.android.chrome` 113.0.5672.136.
- Installed base APK SHA-256, measured on-device before and after:
  `c0d6af7ef3e820e2f6c0adb7e42e36fbd2109180a4961d94bbf92236e842c8f5`.
  The existing signing record for this build reports v2/v3 verification and
  certificate SHA-256 `9bf3a40102638ed9271f4dea6225a2d66e5d2c5f3e5204cc125376288f7f9595`.
- This is **intermediate evidence only**. No APK was built or installed in this
  pass. Updating to and retesting the exact final signed build belongs to the parent.

## Source isolation

Added [Firebog's EasyList domain mirror](https://v.firebog.net/hosts/Easylist.txt)
as a custom URL named `QA EasyList`. AdAway placed it under Ads. The active
row showed **52k**; AdAway Official, Peter Lowe's Ad Servers, StevenBlack
Unified and the existing CategoryControl file were disabled during the test.
The AdAway VPN stayed enabled for all three measurements; "off" means the
list was disabled and applied, not that the VPN was stopped.

The separately fetched public text snapshot had 51,631 non-comment, nonblank
lines and SHA-256 `a0d0b83700b7d55f466ee38dcdcedab0a835af934f9158148f2f88a99f4c7f7e`.
Line 32361 is exactly `pagead2.googlesyndication.com`; `example.net` is
absent. The line count describes the public snapshot, not a database count.

## Chrome navigation

One foreground Chrome tab, signed out, one top-level navigation per URL at
manual pace. Main-frame HTTP status came from Chrome's network events; the
title came from the loaded document. None reported a disk-cache or service-worker
response. DNS is a separate direct VPN A lookup, not inferred from Chrome.

| URL | Document title | HTTP | Direct VPN DNS |
| --- | --- | --- | --- |
| `https://www.sqlite.org/index.html` | SQLite Home Page | 200 | CNAME `sqlite.org`; A `194.195.208.62` |
| `https://www.w3.org/` | Just a moment... | 403 | A `104.18.22.19`, `104.18.23.19` |
| `https://curl.se/` | curl | 200 | A `151.101.193.91`, `151.101.129.91`, `151.101.1.91`, `151.101.65.91` |

W3C served a challenge, not a DNS failure. No challenge retry was requested;
curl was the replacement second ordinary page. The two 200s establish page
availability, not cosmetic filtering, subresource coverage, or absence of ads.

## Matched DNS

The same on-device probe sent individual UDP A queries directly to AdAway at
`192.0.2.2:53` on `tun0`, bypassing the browser and Android resolver caches.
Each reply matched the question and transaction ID and returned `NOERROR`.

| List state (local time, UTC+02) | Listed: `pagead2.googlesyndication.com` | Unlisted: `example.net` |
| --- | --- | --- |
| On, imported (03:38:34) | 0 A; empty answer | 2 A: `172.66.175.59`, `104.20.21.8` |
| Off, applied (03:40:11) | 1 A: `142.250.75.194` | Same 2 A, reversed order |
| On again, applied (03:40:28) | 0 A; empty answer | Same 2 A |

## Parent handoff

Removed only `QA EasyList`, restored and applied the three defaults (3/3,
92k hosts), then enabled CategoryControl **without applying**. Left Sources
open with Custom expanded, 1/1 enabled, 0 hosts, "Never updated" and Apply
offered. Its fixture hostname `example.org` still returned two A records
before Apply. The existing document's SHA-256 matched the original fixture
(`46d91eaf8adac4fc8055dccabeada72e53f9797bd13604857b7e62a88b8b35d2`);
the URI, persisted read mode and grant creation time matched before/after.
The signed emulator is released to the parent. No reset, app-data clear,
emulator stop, product edit, canonical CSV edit or commit.

Local [raw evidence directory](../../../app/build/reports/audit-2026-11-25/deep-source-category/)
(intentionally ignored): `qa-installed-{start,end}.txt`, `atomic-signer.txt`,
`qa-editor.txt`, `qa-on1.txt`, `qa-{off,on2}-apply.txt`, `qa-public-source.txt`,
`qa-chrome-{sqlite,w3c,curl}.json`, `qa-dns-{on1,off,on2,replacement,handoff}.jsonl`,
`qa-vpn-browsing.txt`, `qa-grant-{start,end}.txt` and `qa-handoff.txt`.
