# WhatsApp & Telegram Allowlist Audit — Fix Report

**Date:** 2026-03-04
**Scope:** AdAway fork — User List wildcard allowlist for WA/Telegram critical domains

---

## Root Cause

AdAway's User List allowlist uses exact host matching at runtime. The entry `whatsapp.net`
protects ONLY `whatsapp.net`, not `mmg.whatsapp.net`, `static.whatsapp.net`, etc.

The wildcard system works — `*.whatsapp.net` expands during `HostEntryDao.sync()` via
SQL LIKE patterns — but no wildcard entries were present for subdomains.

---

## Unprotected Critical Domains (Scan Result)

| Domain | Impact | Blocked By |
|--------|--------|------------|
| `wa.me` | HIGH — WA short links break | Ad filter lists |
| `mmg.whatsapp.net` | HIGH — photos/videos/calls break | Ad filter lists |
| `static.whatsapp.net` | HIGH — WA assets break | Ad filter lists |
| `graph-fallback.facebook.com` | MEDIUM — WA auth fallback | Social filter lists |
| `edge-chat.facebook.com` | MEDIUM — WA chat | Social filter lists |
| `graph.whatsapp.com` | MEDIUM — WA features | Ad filter lists |
| `graph.whatsapp.net` | MEDIUM — WA features | Ad filter lists |
| `crashlogs.whatsapp.net` | LOW — telemetry only | Privacy filter lists |
| `dit.whatsapp.net` | LOW — telemetry only | Privacy filter lists |
| `privatestats.whatsapp.net` | LOW — telemetry only | Privacy filter lists |

---

## Fix: WaTgSafetyAllowlist.java

**File created:**
`app/src/main/java/org/adaway/model/source/WaTgSafetyAllowlist.java`

**Wildcard entries inserted (12 total):**

```
*.whatsapp.com      — all WhatsApp web/API subdomains
*.whatsapp.net      — all WhatsApp media/signaling subdomains
*.fbcdn.net         — WhatsApp media CDN (shared with Facebook)
*.facebook.com      — WA auth, fallback signaling
wa.me               — WhatsApp short link landing page
*.wa.me             — WhatsApp short link subdomains
telegram.org        — Telegram main site
*.telegram.org      — Telegram subdomains (API, cdn, etc.)
telegram.me         — Telegram invite links
*.telegram.me       — Telegram invite link subdomains
t.me                — Telegram short links
*.t.me              — Telegram short link subdomains
```

**How it works:**

1. `WaTgSafetyAllowlist.ensureAllowlist(context)` runs on `diskIO()`.
2. Fetches all existing user-list entries (`source_id = 1`).
3. For each of the 12 required domains, if NOT already present, inserts a new
   `HostListItem` with `type = ALLOWED`, `source_id = 1`, `enabled = true`, `generation = 0`.
4. Idempotent — existing entries are never duplicated (exact host match guard).

**Wired into:**
`app/src/main/java/org/adaway/ui/onboarding/DefaultListsSubscriber.java`
→ Called after `hostsSourceDao.insertAll(toInsert)` in `subscribeDefaultsIfEmpty()`.

---

## Emulator DB Verification

SQL applied directly to emulator (`emulator-5554`) via:
```
adb -e shell "run-as org.adaway sqlite3 /data/data/org.adaway/databases/app.db < /sdcard/wa_tg_allowlist.sql"
```

All 12 wildcard entries confirmed present:
```
SELECT host FROM hosts_lists WHERE source_id=1 AND type=1 ORDER BY host;
-- *.facebook.com, *.fbcdn.net, *.t.me, *.telegram.me, *.telegram.org,
-- *.wa.me, *.whatsapp.com, *.whatsapp.net, t.me, telegram.me,
-- telegram.org, wa.me  (+ existing Facebook/LinkedIn/Amazon entries)
```

---

## Test Evidence

**RED phase:** `./gradlew testDebugUnitTest --tests "org.adaway.model.source.WaTgSafetyAllowlistTest"` → exit 1
(compile error: `cannot find symbol WaTgSafetyAllowlist` — 16 errors)

**GREEN phase:** `./gradlew testDebugUnitTest --tests "org.adaway.model.source.WaTgSafetyAllowlistTest"` → exit 0
(BUILD SUCCESSFUL 18s — all 14 tests pass)

**Full suite:** `./gradlew testDebugUnitTest` → exit 0 (BUILD SUCCESSFUL 11s, no regressions)

**Compile check:** `./gradlew compileDebugJavaWithJavac` → exit 0 (BUILD SUCCESSFUL 7s)

---

## Files Changed

| File | Change |
|------|--------|
| `app/src/main/java/org/adaway/model/source/WaTgSafetyAllowlist.java` | CREATED |
| `app/src/main/java/org/adaway/ui/onboarding/DefaultListsSubscriber.java` | MODIFIED — added `ensureAllowlist` call |
| `app/src/test/java/org/adaway/model/source/WaTgSafetyAllowlistTest.java` | CREATED — 14 TDD tests |
| `wa_tg_allowlist.sql` | CREATED — emulator direct-apply SQL |
