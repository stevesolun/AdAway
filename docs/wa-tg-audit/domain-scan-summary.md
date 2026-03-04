# AdAway WA/TG Filter List Audit — Domain Scan Summary

**Generated:** 2026-03-04  
**Lists evaluated:** 17  
**Lists successfully scanned:** 14  

## Root Cause: Exact-Match Allowlist Cannot Protect Subdomains

AdAway's User List allowlist uses **exact host matching**. Adding `whatsapp.net`
to the allowlist does NOT protect `mmg.whatsapp.net`, `static.whatsapp.net`,
`crashlogs.whatsapp.net`, etc. Each subdomain must be individually listed,
or the app needs wildcard/suffix allowlist support.

Several active filter lists block specific WhatsApp and Telegram subdomains
that are NOT in the current allowlist, silently breaking functionality.

## Critical Domain Risk Table

| Domain | Impact | # Lists Block It | Protected | Status |
|--------|--------|-----------------|-----------|--------|
| `core.telegram.org` | CRITICAL | 0 | NO | SAFE |
| `t.me` | CRITICAL | 0 | NO | SAFE |
| `telegram.me` | CRITICAL | 0 | NO | SAFE |
| `telegram.org` | CRITICAL | 0 | NO | SAFE |
| `whatsapp.com` | CRITICAL | 3 | YES | PROTECTED |
| `whatsapp.net` | CRITICAL | 1 | YES | PROTECTED |
| `b-graph.facebook.com` | HIGH | 3 | YES | PROTECTED |
| `fbcdn.net` | HIGH | 3 | YES | PROTECTED |
| `graph.facebook.com` | HIGH | 3 | YES | PROTECTED |
| `mmg.whatsapp.net` | HIGH | 3 | NO | **UNPROTECTED** |
| `static.whatsapp.net` | HIGH | 3 | NO | **UNPROTECTED** |
| `wa.me` | HIGH | 3 | NO | **UNPROTECTED** |
| `web.telegram.org` | HIGH | 0 | NO | SAFE |
| `web.whatsapp.com` | HIGH | 3 | YES | PROTECTED |
| `edge-chat.facebook.com` | MEDIUM | 2 | NO | **UNPROTECTED** |
| `graph-fallback.facebook.com` | MEDIUM | 2 | NO | **UNPROTECTED** |
| `graph.whatsapp.com` | MEDIUM | 4 | NO | **UNPROTECTED** |
| `graph.whatsapp.net` | MEDIUM | 2 | NO | **UNPROTECTED** |
| `crashlogs.whatsapp.net` | LOW | 3 | NO | **UNPROTECTED** |
| `dit.whatsapp.net` | LOW | 4 | NO | **UNPROTECTED** |
| `privatestats.whatsapp.net` | LOW | 3 | NO | **UNPROTECTED** |
| `ads.telegram.org` | NONE | 2 | NO | **UNPROTECTED** |
| `promote.telegram.org` | NONE | 3 | NO | **UNPROTECTED** |

## Filter Lists That BREAK WA/TG (sorted by severity)

These lists block at least one unprotected critical domain:

| List | Risk Score | Unprotected Domains Blocked |
|------|------------|----------------------------|
| StevenBlack Social | 21 | `wa.me`, `mmg.whatsapp.net`, `static.whatsapp.net`, `edge-chat.facebook.com`, `graph.whatsapp.com`, `graph.whatsapp.net` |
| BlocklistProject Facebook | 17 | `wa.me`, `mmg.whatsapp.net`, `static.whatsapp.net`, `graph.whatsapp.com` |
| DeveloperDan Facebook | 17 | `wa.me`, `mmg.whatsapp.net`, `static.whatsapp.net`, `graph.whatsapp.com` |
| HaGeZi Ultimate | 6 | `graph-fallback.facebook.com`, `graph.whatsapp.com`, `graph.whatsapp.net` |
| Anudeep Facebook | 4 | `graph-fallback.facebook.com`, `edge-chat.facebook.com` |

## Unprotected Domains (require allowlist entries)

These critical domains are blocked by at least one active list but NOT
protected by the current User List allowlist:

- `ads.telegram.org` — NONE - only ads
  - Blocked by: HaGeZi Ultimate, HaGeZi Multi PRO
- `crashlogs.whatsapp.net` — LOW - telemetry only
  - Blocked by: StevenBlack Social, HaGeZi Multi PRO, GoodbyeAds
- `dit.whatsapp.net` — LOW - telemetry only
  - Blocked by: StevenBlack Social, HaGeZi Ultimate, HaGeZi Multi PRO, GoodbyeAds
- `edge-chat.facebook.com` — MEDIUM - WA chat connectivity
  - Blocked by: StevenBlack Social, Anudeep Facebook
- `graph-fallback.facebook.com` — MEDIUM - WA auth fallback
  - Blocked by: Anudeep Facebook, HaGeZi Ultimate
- `graph.whatsapp.com` — MEDIUM - WA features
  - Blocked by: StevenBlack Social, BlocklistProject Facebook, DeveloperDan Facebook, HaGeZi Ultimate
- `graph.whatsapp.net` — MEDIUM - WA features
  - Blocked by: StevenBlack Social, HaGeZi Ultimate
- `mmg.whatsapp.net` — HIGH - photos/videos/calls break
  - Blocked by: StevenBlack Social, BlocklistProject Facebook, DeveloperDan Facebook
- `privatestats.whatsapp.net` — LOW - telemetry only
  - Blocked by: StevenBlack Social, HaGeZi Ultimate, HaGeZi Multi PRO
- `promote.telegram.org` — NONE - only ads
  - Blocked by: HaGeZi Ultimate, HaGeZi Multi PRO, HaGeZi Light
- `static.whatsapp.net` — HIGH - WA assets break
  - Blocked by: StevenBlack Social, BlocklistProject Facebook, DeveloperDan Facebook
- `wa.me` — HIGH - WA links break
  - Blocked by: StevenBlack Social, BlocklistProject Facebook, DeveloperDan Facebook

## Recommended Wildcard Allowlist Entries

To reliably protect WhatsApp and Telegram from all current and future
filter lists, the following entries should be added to the User List allowlist.
Note: AdAway currently does not support wildcard entries natively —
these are the exact subdomains that need to be listed, plus a note
on the wildcard-support enhancement needed.

### Immediate (exact matches needed now):
```
ads.telegram.org
crashlogs.whatsapp.net
dit.whatsapp.net
edge-chat.facebook.com
graph-fallback.facebook.com
graph.whatsapp.com
graph.whatsapp.net
mmg.whatsapp.net
privatestats.whatsapp.net
promote.telegram.org
static.whatsapp.net
wa.me
```

### Long-term (wildcard support needed):
```
*.whatsapp.com
*.whatsapp.net
*.fbcdn.net
*.facebook.com
*.telegram.org
*.telegram.me
```

## Current Allowlist (for reference)
```
b-graph.facebook.com
facebook.com
fbcdn.net
graph.facebook.com
web.whatsapp.com
whatsapp.com
whatsapp.net
www.facebook.com
www.whatsapp.com
```
