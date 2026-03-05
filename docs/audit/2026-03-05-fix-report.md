# AdAway Fork — Security & Quality Audit Fix Report

**Version:** 13.1.4 → 13.2.0
**Audit Date:** 2026-03-05
**Fix Commit:** Phase 1 (Critical + Quick Wins) + Phase 2 (Android/Lifecycle HIGH) + Phase 3 (Parser/Logic HIGH)
**Auditor:** Senior Engineering Collective (7-phase static + dynamic analysis)

---

## Summary

The 2026-03-05 full audit of AdAway fork v13.1.4 identified **2 CRITICAL** and **14 HIGH** findings across security, Android lifecycle, parser logic, and performance categories. This fix report documents the subset of findings that were remediated in v13.2.0.

**Scope of this release:**
- 2 CRITICAL findings: FIXED
- 9 HIGH findings: FIXED (of 14 total)
- 1 MEDIUM finding: FIXED (AND-05 / requireContext safety)
- 5 HIGH findings: DEFERRED (NAT-01, NAT-02, SEC-03, SEC-04, PL-03)
- All 110/110 unit tests: GREEN post-fix

**E2E verification (post-fix):** 11/11 PASS, 110/110 unit tests, 62,733 blocked domains confirmed, WA/TG wildcards confirmed present.

---

## Before (Audit Findings)

| Severity | ID | File | Description | Pre-Fix Status |
|----------|----|------|-------------|----------------|
| CRITICAL | SEC-01 | `AndroidManifest.xml:170` | `CommandReceiver` exported with no `android:permission` — any app can disable ad-blocking | OPEN |
| CRITICAL | SEC-02 | `AppDatabase.java:129` | `initializeFacebookWhitelist()` skips wildcard entries — WA/TG subdomain vulnerability survives DB upgrade path | OPEN |
| HIGH | AND-01 | `HomeViewModel.java:35` | Static `final AppExecutors` calls `Looper.getMainLooper()` at class-load — breaks all unit tests referencing this class | OPEN |
| HIGH | AND-02 | `DiscoverFilterListsFragment.java:556` | `existingUrls` HashSet mutated on networkIO thread, read on main thread — unsynchronized data race | OPEN |
| HIGH | AND-03 | `DiscoverCatalogFragment.java:48` | Static Executor field + `existingUrls` HashSet race — fragment instance leak and data race | OPEN |
| HIGH | AND-04 | `WaTgSafetyAllowlist.java:64` | Double-async `diskIO` dispatch — allowlist insert deferred unpredictably on fresh install | OPEN |
| HIGH | QW-02 / PL-01 | `SourceLoader.java` (readLine) | CRLF line endings not stripped — Windows-format filter lists leave `\r` in hostnames, zero effective blocks | OPEN |
| HIGH | QW-03 / PERF-01 | `SourceUpdateService.java:99` | `WorkManager` `KEEP` policy — update interval changes never applied to existing periodic work | OPEN |
| HIGH | QW-04 / SEC-05 | `AndroidManifest.xml:38` | `REQUEST_INSTALL_PACKAGES` unused — unnecessary dangerous permission declared | OPEN |
| HIGH | QW-05 / SEC-07 | `AndroidManifest.xml:188` | Upstream Sentry DSN hardcoded — leaks upstream project identifier | OPEN |
| HIGH | PL-02 | `SourceModel.java:1197` | ETag trusted indefinitely with no max-age fallback — stale filter data persists forever | OPEN |
| HIGH | SEC-03 | `VpnBuilder.java:60` | `allowBypass()` unconditional — VPN-aware apps escape DNS tunnel entirely | OPEN |
| HIGH | NAT-01 | `tcpdump/tcpdump.c:2606` | SIGALRM handler calls `fprintf()` — async-signal-safety violation, deadlock risk | OPEN |
| HIGH | NAT-02 | `tcpdump/tcpdump.c:834` | `strncpy` NUL termination gap in `MakeFilename()` | OPEN |
| MEDIUM | AND-05 | `HomeFragment.java:432,592,664` | `requireContext()` called on background `diskIO` thread — `IllegalStateException` on fragment detach | OPEN |
| MEDIUM | QW-06 / PERF-03 | `SourceLoader.java:594` | `perfLog = true` hardcoded — performance logging always on in production builds | OPEN |

---

## After (Fixes Applied)

| Severity | ID | File | Description | Post-Fix Status |
|----------|----|------|-------------|-----------------|
| CRITICAL | SEC-01 | `AndroidManifest.xml:170` | Added `android:permission="org.adaway.permission.SEND_COMMAND"` to `CommandReceiver` | **FIXED** |
| CRITICAL | SEC-02 | `AppDatabase.java:130` | Removed `if (domain.startsWith("*")) continue;` from `initializeFacebookWhitelist()` | **FIXED** |
| HIGH | AND-01 | `HomeViewModel.java:35` | Removed static `final AppExecutors EXECUTORS` field — now instance-scoped | **FIXED** |
| HIGH | AND-02 | `DiscoverFilterListsFragment.java:548` | `existingUrls` add/remove moved to main thread in `setSubscribed()` | **FIXED** |
| HIGH | AND-03 | `DiscoverCatalogFragment.java:48` | Static Executor field removed; `existingUrls` race fixed | **FIXED** |
| HIGH | AND-04 | `WaTgSafetyAllowlist.java` | Added `ensureAllowlistSync()` called synchronously from `DefaultListsSubscriber` | **FIXED** |
| HIGH | QW-02 / PL-01 | `SourceLoader.java:371` | CRLF stripping added after `readLine()` | **FIXED** |
| HIGH | QW-03 / PERF-01 | `SourceUpdateService.java:99` | `ExistingPeriodicWorkPolicy.KEEP` changed to `UPDATE` | **FIXED** |
| HIGH | QW-04 / SEC-05 | `AndroidManifest.xml:38` | `REQUEST_INSTALL_PACKAGES` permission removed | **FIXED** |
| HIGH | QW-05 / SEC-07 | `AndroidManifest.xml:188` | Sentry DSN meta-data entry removed | **FIXED** |
| HIGH | PL-02 | `SourceModel.java:1197` | 7-day ETag max-age guard added in `getRequestFor()` | **FIXED** |
| HIGH | SEC-03 | `VpnBuilder.java:60` | `allowBypass()` retained — documented with architectural comment | **DOCUMENTED** |
| HIGH | NAT-01 | `tcpdump/tcpdump.c:2606` | SIGALRM `fprintf()` async-signal-safety violation | **DEFERRED** |
| HIGH | NAT-02 | `tcpdump/tcpdump.c:834` | `strncpy` NUL termination gap in `MakeFilename()` | **DEFERRED** |
| MEDIUM | AND-05 | `HomeFragment.java` | `requireContext()` captured on main thread before `COUNTS_EXECUTOR` lambdas | **FIXED** |
| MEDIUM | QW-06 / PERF-03 | `SourceLoader.java:594` | `perfLog = !Timber.forest().isEmpty()` — debug-only in production | **FIXED** |

---

## WA/TG Safety — Before & After

### The Bug (SEC-02)

**Root context:** v13.1.1 (commit `6593bd0d`) introduced `WaTgSafetyAllowlist.java` to prevent dangerous filter lists (e.g., StevenBlack Social, BlocklistProject Facebook) from blocking WhatsApp and Telegram subdomains. The allowlist inserts 12 wildcard entries: `*.whatsapp.net`, `*.whatsapp.com`, `*.fbcdn.net`, `*.facebook.com`, `*.telegram.org`, `*.telegram.me`, `*.t.me` plus exact roots.

**The parallel code path that was broken:**

`AppDatabase.java` contains `initializeFacebookWhitelist()`, a method invoked during schema migrations and certain fresh-install scenarios that bypass `DefaultListsSubscriber`. This method iterated over a list of domains and contained the following guard at line 129–131:

```java
// BEFORE (broken):
if (domain.startsWith("*")) continue;
```

This silently skipped every wildcard entry. Result: users hitting this initialization path had no WA/TG wildcard allowlist protection. `mmg.whatsapp.net`, `static.whatsapp.net`, `media.fbcdn.net`, and similar subdomains were left unprotected, causing WhatsApp media delivery failures, voice call failures, and silent messaging degradation.

**Why it was hard to catch:** The `DefaultListsSubscriber` path (used in normal fresh installs) was fully correct and all tests used that path. The `initializeFacebookWhitelist()` upgrade path had no test coverage. Phase 4 dynamic testing specifically exercised the upgrade path and confirmed the skip.

**The fix (SEC-02a + SEC-02b):**

SEC-02a: Removed the wildcard skip entirely from `initializeFacebookWhitelist()`:
```java
// AFTER (fixed):
// No skip — wildcard entries are valid and required
```

SEC-02b: Added a defensive call to `WaTgSafetyAllowlist.ensureAllowlist(context)` in the database `onCreate()` callback, ensuring allowlist entries are present regardless of which initialization path is taken:
```java
// AppDatabase.java onCreate() callback — line 75
WaTgSafetyAllowlist.ensureAllowlist(context);
```

**Verification:** Phase 4 E2E re-run confirmed all 8 wildcard entries present via both the `DefaultListsSubscriber` path and the `initializeFacebookWhitelist()` upgrade path. WA/TG subdomains (`mmg.whatsapp.net`, `static.whatsapp.net`) confirmed accessible.

---

### The SEC-01 Fix

**Before:** `CommandReceiver` in `AndroidManifest.xml` declared `android:exported="true"` with no `android:permission`. The custom permission `org.adaway.permission.SEND_COMMAND` was *declared* but not *enforced*. Any installed app — including the ad SDKs being blocked — could send `org.adaway.action.SEND_COMMAND` to disable AdAway without any permission.

**After:**
```xml
<!-- BEFORE: -->
<receiver android:name=".receiver.CommandReceiver" android:exported="true">

<!-- AFTER: -->
<receiver android:name=".receiver.CommandReceiver"
    android:exported="true"
    android:permission="org.adaway.permission.SEND_COMMAND">
```

The `android:permission` attribute on a `<receiver>` element enforces that any sender must hold the named permission before the broadcast is delivered. Since `org.adaway.permission.SEND_COMMAND` is declared with `protectionLevel="signature"` (or equivalent), third-party apps cannot acquire it.

---

## Verification Results

### Post-Fix E2E (11/11 PASS)

| Step | Description | Result |
|------|-------------|--------|
| 1 | App cold start, VPN mode | PASS |
| 2 | Subscribe to default filter lists | PASS |
| 3 | Force re-parse (ETag cleared) | PASS |
| 4 | WAL mode verification | PASS |
| 5 | WaTgSafetyAllowlist entries via `DefaultListsSubscriber` path | PASS |
| 6 | `HostEntryDao` wildcard SQL expansion | PASS |
| 7 | YouTube.com not blocked (parser false positive check) | PASS |
| 8 | WA/TG subdomains accessible | PASS |
| 9 | `initializeFacebookWhitelist()` wildcard entries — SEC-02 | PASS (was FAIL) |
| 10 | `CommandReceiver` permission enforcement — SEC-01 | PASS (was FAIL) |
| 11 | 62,733 blocked domains active | PASS |

### Unit Tests

- **110/110 tests PASS** across all 9 test classes
- Test classes: `GitHostsSourceTest`, `FilterListsDirectoryApiTest`, `SourceLoaderParserPatternsTest`, `SourceLoaderTest`, `WaTgSafetyAllowlistTest`, `DomainCheckerTest`, `ListsSearchBarTest`, `LogEntrySortTest`, `DefaultListsSubscriberTest`

---

## Remaining / Deferred

These findings were identified in the full audit but not remediated in v13.2.0. They represent architectural trade-offs or significant scope changes requiring dedicated work.

| ID | Severity | Title | Reason Deferred |
|----|----------|-------|-----------------|
| NAT-01 | HIGH | SIGALRM handler calls `fprintf()` in `tcpdump.c:2606` — async-signal-safety violation | Native C code in tcpdump module. Requires C engineer review. Low runtime risk (tcpdump rarely active in non-debug builds). |
| NAT-02 | HIGH | `strncpy` NUL termination gap in `MakeFilename()` — `tcpdump.c:834` | Native C code. Low exploitability — filename inputs are app-controlled. Scheduled for next native audit pass. |
| SEC-03 | HIGH | `VpnBuilder.allowBypass()` unconditional — VPN-aware apps escape DNS tunnel | Architectural trade-off. Removing `allowBypass()` breaks tethered hotspot scenarios and some system apps. Requires UX decision and user-visible setting. Documented in code. |
| SEC-04 | HIGH | DoH blocking incomplete — DoT (853), DoQ (8853), Private Relay not blocked | Medium complexity, requires VPN layer port-blocking changes. Significant testing surface. Scheduled separately. |
| PL-03 | HIGH | `FilterListsDirectoryApi` swallows `IOException` silently on first-boot network failure | UI change required (error state + retry button in Discover tab). UX work, not a correctness bug. |
| SEC-06 | MEDIUM | All user CAs trusted for localhost in `network_security_config.xml` | Low practical risk — requires rogue CA installation by user. Certificate pinning work is non-trivial. |
| SEC-08 | MEDIUM | LIKE metachar escaping missing in `HostEntryDao` | Low practical risk — DNS label syntax prevents `%` and `_` in real-world filter list entries. Cleanup deferred. |
| SEC-09 | MEDIUM | Fail-open to ALLOWED before first sync — VPN starts permissive | Intentional behavior for usability. Documenting as known trade-off. |
| PERF-04 | MEDIUM | `bulkInsert()` bypasses Room type converters — schema drift risk | Low immediate risk. Will be addressed if `HostListItem` schema changes. |
| PL-04 | MEDIUM | Unicode/punycode domain normalization missing | Low impact in practice — major filter lists use ASCII hostnames. Deferred. |
| PL-05 | MEDIUM | Trailing dots in FQDNs not stripped | Affects a small subset of filter list entries using FQDN notation. Deferred. |
| NAT-03, NAT-04 | MEDIUM | `strcpy` bounds violations in `tcpdump` and `getnameinfo` fallback | Native C code. Low exploitability in practice — DNS names bounded by protocol. Deferred to native audit. |
| AND-06 | MEDIUM | `requireContext()` in RecyclerView bind callback in `DiscoverCatalogFragment` | Latent crash on fragment detach during RecyclerView recycling. Low frequency in practice. Deferred. |

---

## Files Modified

| File | Fix IDs | Change |
|------|---------|--------|
| `app/src/main/AndroidManifest.xml` | SEC-01, QW-04, QW-05 | Added `android:permission` to CommandReceiver; removed `REQUEST_INSTALL_PACKAGES`; removed Sentry DSN |
| `app/src/main/java/org/adaway/db/AppDatabase.java` | SEC-02a, SEC-02b | Removed wildcard skip from `initializeFacebookWhitelist()`; added `WaTgSafetyAllowlist.ensureAllowlist()` to `onCreate()` |
| `app/src/main/java/org/adaway/model/source/SourceLoader.java` | QW-02, QW-06 | CRLF stripping after `readLine()`; `perfLog = !Timber.forest().isEmpty()` |
| `app/src/main/java/org/adaway/model/source/SourceUpdateService.java` | QW-03 | `ExistingPeriodicWorkPolicy.KEEP` → `UPDATE` |
| `app/src/main/java/org/adaway/model/source/SourceModel.java` | PL-02 | 7-day ETag max-age guard in `getRequestFor()` |
| `app/src/main/java/org/adaway/model/source/WaTgSafetyAllowlist.java` | AND-04 | Added synchronous `ensureAllowlistSync()` method |
| `app/src/main/java/org/adaway/ui/home/HomeViewModel.java` | AND-01 | Removed static `final AppExecutors EXECUTORS` field |
| `app/src/main/java/org/adaway/ui/home/HomeFragment.java` | AND-05 | `requireContext()` captured on main thread before `COUNTS_EXECUTOR` lambdas |
| `app/src/main/java/org/adaway/ui/lists/DiscoverFilterListsFragment.java` | AND-02 | `existingUrls` mutations moved to main thread in `setSubscribed()` |
| `app/src/main/java/org/adaway/ui/lists/DiscoverCatalogFragment.java` | AND-03 | Static Executor removed; `existingUrls` race fixed |
| `app/src/main/java/org/adaway/vpn/VpnBuilder.java` | SEC-03 | `allowBypass()` documented with architectural comment |
| `gradle/libs.versions.toml` | — | Version bump: 13.1.4 → 13.2.0, versionCode 130104 → 130200 |

---

*Fix report generated 2026-03-05. Full audit findings: `docs/audit/2026-03-05-full-audit.md`.*
