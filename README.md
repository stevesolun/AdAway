# ![AdsAway logo](app/src/main/res/mipmap-mdpi/icon.png) AdsAway

**AdsAway** is an open-source, system-level ad blocker for Android. It uses the hosts file (root mode) or a local VPN (non-root mode) to block ads, trackers, malware, and unwanted domains network-wide — across every app and browser.

AdsAway ships with a modernized Material 3 UI, deep FilterLists.com integration, an AI-powered filter assistant (Claude / Gemini / ChatGPT), categorized filter management, one-tap onboarding, and enterprise-grade API key encryption.

[![GitHub Downloads](https://img.shields.io/github/downloads/stevesolun/adaway/total?logo=github)](https://github.com/stevesolun/AdAway/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](/LICENSE.md)

---

## Table of Contents

1. [What's New](#whats-new)
2. [Features](#features)
3. [Screenshots](#screenshots)
4. [Installing](#installing)
5. [AI Assistant](#ai-assistant)
6. [Architecture](#architecture)
7. [Filter Categories](#filter-categories)
8. [Building from Source](#building-from-source)
9. [How-To Guides](#how-to-guides)
10. [Permissions](#permissions)
11. [Troubleshooting](#troubleshooting)
12. [Contributing](#contributing)
13. [License](#license)

---

## What's New

| Version | Highlight |
|---------|-----------|
| **v13.4.9** | Pre-LLM topic filter — off-topic queries rejected locally, zero API cost |
| v13.4.8 | Fix literal `"null"` description in FilterLists.com Discover tab |
| v13.4.7 | Dynamic model list fetched live from provider API; per-provider model memory |
| v13.4.6 | ATK-29b: dotless-i / dotted-I Unicode injection bypass closed |
| v13.4.5 | 288-test AI security suite covering all injection/bypass/hallucination vectors |
| v13.4.4 | **AI Conversational Agent** — reads live app state, plans and executes filter actions |
| v13.4.2 | Security hardening: 22 attack vectors fixed (SSRF, MitM CA pin, backup limits, etc.) |
| v13.4.1 | Provider-specific AI error messages; active model shown in header |
| v13.4.0 | **AI Filter Assistant** — natural language filter config with Claude / Gemini / ChatGPT |
| v13.3.x | Reliability: NPE/race/threading fixes, Force English locale, download error feedback |
| v13.0–v13.2 | Parallel pipeline, Conditional GET, WAL mode, FilterLists.com browser, presets |

---

## Features

### Blocking Modes

| Mode | How it works | Root needed? |
|------|-------------|:------------:|
| **Root** | Writes to `/etc/hosts` — blocks at kernel level | ✅ Yes |
| **VPN** | Runs a local DNS proxy via Android VPN API | ❌ No |

Both modes block at the DNS level — no content injection, no HTTPS inspection.

### AI Filter Assistant

- Natural language: "Block crypto miners but keep YouTube"
- **Agent mode**: AI reads live app state, plans actions, and executes them on your approval
- Supports **Claude Haiku 4.5 / Sonnet 4.6 / Opus 4.6**, **Gemini 2.5 Flash / Pro / Flash Lite**, **GPT-4.1 Mini / GPT-4.1 / GPT-4.1 Nano**
- API keys encrypted on-device with AES-256-GCM backed by Android Keystore hardware (API 23+)
- Configure at **Settings → AI Assistant**

### Filter Management

- **12 filter categories** — ADS, YOUTUBE, PRIVACY, MALWARE, CRYPTO, SOCIAL, DEVICE, SERVICE, ANNOYANCES, REGIONAL, USER, CUSTOM
- **Quick-start presets** — Safe / Balanced / Aggressive chip taps on Discover
- **FilterLists.com browser** — 5000+ community lists, search by name, tag, or language
- **Categorized source view** — sources grouped and color-coded by category
- **Custom lists** — any URL, any format (Hosts / Domains / Adblock / Allowlist / Redirect)
- **One-tap subscribe / unsubscribe** from FilterLists.com directory
- **Filter Sets** — save named presets, apply with one tap
- **Scheduled updates** — daily or weekly automatic list refresh

### Performance

- Parallel download + parse pipeline using all available CPU cores
- Conditional GET skips re-downloading unchanged lists (ETag / Last-Modified)
- Batch DB inserts (5000 hosts per transaction)
- WAL mode — reads never block writes
- Global host deduplication — same host from multiple sources stored once
- OkHttp connection pool tuning — larger pool, more parallel connections

### Safety

- WaTg Safety Allowlist — wildcards for `*.whatsapp.com`, `*.whatsapp.net`, `*.fbcdn.net`, `*.facebook.com`, `*.telegram.org`, `*.telegram.me`, `*.t.me` prevent blocking WhatsApp and Telegram
- SOCIAL / DEVICE categories flagged as "may break services" — shown with warnings in UI
- Download errors persisted per-source — visible in the source card without re-downloading

---

## Screenshots

| **Home** | **Discover** | **More** |
|:---:|:---:|:---:|
| <img src="docs/screenshots/home_final.png" height="480"> | <img src="docs/screenshots/sources_final.png" height="480"> | <img src="docs/screenshots/catalog_final.png" height="480"> |
| Status, stats, one-tap toggle | Browse & subscribe FilterLists.com | Tools, settings, about |

---

## Installing

### Download APK (Quickest)

1. Go to [**Releases**](https://github.com/stevesolun/AdAway/releases/latest).
2. Download `AdsAway_13.4.9.apk` (or whichever is latest).
3. **If you have the official AdAway installed**, uninstall it first — different signing key.
4. Open the APK on your device → allow "Install unknown apps" if prompted.
5. Open AdsAway and complete one-tap onboarding.

### Requirements

- Android 8.0 (API 26) or later
- Root mode: rooted device with `su` binary
- VPN mode: no root needed

> **Note**: Builds are signed with a debug key. Android prevents updating over the official AdAway due to signature mismatch. Uninstall first.

---

## AI Assistant

### Setup

1. Get an API key from your preferred provider:
   - **Claude**: [console.anthropic.com](https://console.anthropic.com) → API Keys
   - **Gemini**: [aistudio.google.com](https://aistudio.google.com) → Get API key
   - **ChatGPT**: [platform.openai.com](https://platform.openai.com) → API keys
2. In AdsAway: **More → Preferences → AI Assistant**
3. Select your provider and model tier
4. Tap the provider's API key row → paste your key → Save

Your key is **encrypted immediately** on-device using AES-256-GCM with a hardware-backed Android Keystore key. It is never stored in plain text, never synced, and never sent anywhere except the selected provider's API endpoint.

### Using the AI

1. Tap the **Discover** tab
2. Tap the **Ask AI** chip (next to Safe / Balanced / Aggressive)
3. Type what you want in plain English, e.g.:
   - *"Block ads and protect privacy but keep WhatsApp working"*
   - *"Stop crypto miners and YouTube ads"*
   - *"Is WhatsApp blocked? If so, unblock it"*
   - *"Maximum blocking — I don't care if some apps break"*
4. Tap **Ask** — the AI reads your current filter state and plans a set of actions
5. Review the planned actions list + the AI's brief explanation
6. Tap **Execute** — actions run immediately, results shown inline

### What gets sent to the AI

The request contains:
- Your typed query (sanitized — injection patterns neutralized)
- Live app state: subscribed/enabled counts per filter category, user rule counts
- The list of available action types

**Nothing else** — no hostnames from your DB, no installed apps, no device info, no personal data.

### Security & Encryption Detail

Keys are stored using **direct Android Keystore + AES/GCM/NoPadding** encryption:

```
Key generation:
  KeyGenParameterSpec → KeyProperties.PURPOSE_ENCRYPT | DECRYPT
  → AES / GCM / NoPadding, 256-bit
  → Hardware-backed on supported devices (API 28+)

Storage:
  Encrypt(apiKey) → IV (12 bytes) || Ciphertext → Base64 → SharedPreferences

Retrieval:
  SharedPreferences → Base64 decode → split IV + ciphertext → Decrypt
```

No third-party crypto libs required. No EncryptedSharedPreferences (deprecated July 2025).

### Supported Models

Since v13.4.7, **available models are fetched live from each provider's API** when you save an API key — you always see the current model catalog, not a hardcoded list. Each provider remembers your chosen model independently.

Built-in fallback (used if fetch fails or before a key is entered):

| Provider | Fallback Models |
|----------|----------------|
| Claude | Haiku 4.5 · Sonnet 4.6 · Opus 4.6 |
| Gemini | Flash 2.5 · Pro 2.5 · Flash Lite 2.5 |
| ChatGPT | GPT-4.1 Mini · GPT-4.1 · GPT-4.1 Nano |

---

## Architecture

### Module Structure

```
AdsAway/
├── app/                    # Android application (Java 17)
├── tcpdump/                # Native packet capture (C, NDK)
├── webserver/              # Native HTTP server — mongoose (C, NDK)
└── sentrystub/             # Sentry stub for release builds
```

**Language distribution**: C 77%, Java 7%, HTML 9%, C++ 2%, rest is build config.
This is a systems project wearing Android clothes. The critical path is native code.

### App Source Layout

```
app/src/main/java/org/adaway/
├── broadcast/
│   ├── BootReceiver.java          # Restart protection on reboot
│   ├── UpdateReceiver.java        # Trigger source updates externally
│   └── Command.java               # Broadcast command enum
├── db/
│   ├── dao/                       # HostsSourceDao, HostListItemDao, HostEntryDao
│   ├── entity/                    # HostsSource, HostListItem, HostEntry, HostsMeta
│   └── AppDatabase.java           # Room DB (WAL mode, v11, 10 migrations)
├── model/
│   ├── ai/                        # ← NEW in v13.4.0, extended in v13.4.4
│   │   ├── FilterListSuggester.java   # LLM orchestration + HTTP calls + agent execute()
│   │   ├── AiAgentAction.java         # Action type enum + payload
│   │   ├── AiAgentResponse.java       # Parsed LLM response (reasoning + actions)
│   │   ├── AppStateContext.java       # Live app state JSON for system prompt
│   │   ├── AiActionExecutor.java      # Validates + executes actions against DAOs
│   │   ├── LlmProvider.java           # Claude / Gemini / OpenAI enum
│   │   ├── LlmSuggestion.java         # Legacy suggestion result data class
│   │   └── SecureApiKeyStore.java     # AES-256-GCM keystore wrapper
│   ├── source/
│   │   ├── SourceModel.java           # Download → Parse → Insert pipeline
│   │   ├── SourceLoader.java          # Parser (hosts/domains/adblock formats)
│   │   ├── FilterListCatalog.java     # 60+ curated lists across 12 categories
│   │   ├── FilterListCategory.java    # Category enum (ADS/PRIVACY/MALWARE/…)
│   │   └── FilterSetStore.java        # Named presets + scheduling
│   ├── adblocking/                # Root/VPN switching logic
│   ├── backup/                    # Backup/restore (JSON format)
│   ├── git/                       # GitHub/GitLab/Gist source types
│   └── update/                    # APK self-update check
├── ui/
│   ├── ai/
│   │   └── AiSuggestBottomSheet.java  # Agent UI: action list + Execute button
│   ├── home/                      # HomeActivity (nav shell) + HomeFragment + ViewModel
│   ├── discover/                  # FilterLists.com browser + catalog + AI chip
│   ├── more/                      # Tools & settings navigation
│   ├── onboarding/                # First-run single-screen wizard
│   ├── lists/                     # Custom blocked/allowed/redirected rules
│   ├── hosts/                     # Filter sources management (HostsSourcesActivity)
│   ├── prefs/                     # All preferences screens including PrefsAiFragment
│   ├── adware/                    # Installed app adware scanner
│   ├── log/                       # Real-time DNS query log
│   └── domainchecker/             # Manual domain lookup tool
├── util/
│   └── AppExecutors.java          # diskIO / networkIO / mainThread pools
└── vpn/                           # Local VPN + DNS proxy (non-root mode)
    ├── dns/                       # DnsPacketProxy, DnsQuery, DnsQueryQueue
    └── worker/                    # VpnWorker, watchdog, throttler
```

### Threading Model

```
diskIO()     → Single-threaded executor — Room DB reads/writes
networkIO()  → Fixed-size pool — OkHttp HTTP calls (NEVER mix with diskIO)
mainThread() → Android main looper — UI updates only
WorkManager  → Long-running background jobs (subscribe-all, scheduled updates)
```

**Rule**: Network on `networkIO()`. DB on `diskIO()`. UI on `mainThread()`. Never cross streams.

### Database Schema (Room v11, SQLite WAL)

```
hosts_sources
  id, label, url, enabled, type, entityTag, last_modified_online,
  last_download_error, category, generation

hosts_lists
  id, host, type (0=block/1=allow/2=redirect), source_id, generation

hosts_meta
  id (single row), current_generation

host_entries
  id, host, type, redirect_target (runtime resolved entries for VPN)
```

**Generation system**: Each update cycle bumps the generation counter. Entries from the previous generation are cleaned up after the new generation is fully written — prevents partial state.

**HTTP 304 handling (v13.3.3 fix)**: When a server returns 304 Not Modified, existing entries are migrated from generation G → G+1 *before* cleanup runs, so nothing is deleted.

### AI Request Lifecycle (Agent Mode)

```
User types query
      ↓
AiSuggestBottomSheet.onAskClicked()
      ↓
AppExecutors.networkIO()
      ↓
FilterListSuggester.execute(context, query)
  ├── sanitizeQuery() → NFKC normalize + injection pattern neutralise
  ├── isAdAwayTopicQuery() → reject off-topic queries locally (no API call)
  ├── AppStateContext.build() → compact JSON state (diskIO-safe, called inline)
  ├── Injects state into AGENT_SYSTEM_PROMPT_TEMPLATE
  ├── Reads selected provider + model from SharedPreferences
  ├── Reads encrypted API key from SecureApiKeyStore
  ├── Builds provider-specific JSON request body
  ├── POST to provider API endpoint (OkHttp, 15s connect / 60s read timeout)
  └── Parses JSON response → AiAgentResponse(reasoning, List<AiAgentAction>)
      ↓
mainThread() → show action list + reasoning text
      ↓
onExecuteClicked()
      ↓
AppExecutors.diskIO()
      ↓
AiActionExecutor.execute(action) for each action
  ├── SUBSCRIBE_CATEGORY → FilterListCatalog → HostsSourceDao.insert()
  ├── ENABLE/DISABLE_CATEGORY → HostsSourceDao.setSourceEnabled()
  ├── UPDATE_SOURCES → SourceUpdateService.enqueueUpdateNow()
  ├── CHECK_DOMAIN → HostListItemDao.getEntriesForHost()
  └── ALLOW/BLOCK_DOMAIN → normalizeDomain() → HostListItemDao.insert()
      ↓
mainThread() → show results inline
```

---

## Filter Categories

| Category | Safe? | Default On | Description |
|----------|:-----:|:----------:|-------------|
| **ADS** | ✅ Safe | ✅ Yes | Advertisement servers — rarely breaks anything |
| **MALWARE** | ✅ Safe | ✅ Yes | Malware, phishing, ransomware domains |
| **YOUTUBE** | ✅ Safe | ❌ No | YouTube in-stream ad servers |
| **PRIVACY** | ✅ Safe | ❌ No | Trackers, analytics, fingerprinting |
| **CRYPTO** | ✅ Safe | ❌ No | Browser-based cryptocurrency mining |
| **ANNOYANCES** | ✅ Safe | ❌ No | Cookie banners, popup overlays |
| **REGIONAL** | ✅ Safe | ❌ No | Region-specific ad networks |
| **SERVICE** | ⚠️ Moderate | ❌ No | In-app ads (Spotify, etc.) — may break those apps |
| **SOCIAL** | 🔴 Aggressive | ❌ No | Social media trackers — **may break Facebook/Instagram/WhatsApp** |
| **DEVICE** | 🔴 Aggressive | ❌ No | OEM telemetry (Samsung/Xiaomi) — **may break OEM features** |
| **USER** | — | — | Your personal blocked/allowed/redirected rules |
| **CUSTOM** | — | — | Custom sources added via URL |

### Quick-Start Presets

| Preset | Categories Included |
|--------|-------------------|
| **Safe** | ADS + MALWARE |
| **Balanced** | ADS + MALWARE + PRIVACY + YOUTUBE |
| **Aggressive** | ADS + MALWARE + PRIVACY + YOUTUBE + CRYPTO + ANNOYANCES + REGIONAL |

---

## Building from Source

### Prerequisites

| Tool | Required Version |
|------|-----------------|
| Android Studio | Hedgehog (2023.1) or later |
| Android SDK | API 34 |
| Android NDK | **27.2.12479018** (exact — C native code) |
| JDK | 21 |
| Git | Any recent version |

### Clone and Build

```bash
git clone https://github.com/stevesolun/AdAway.git
cd AdAway

# Debug build (for development + testing)
./gradlew assembleDebug

# Release build (skip lint — pre-existing upstream lint failures)
./gradlew assembleRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease

# Or use the package task directly for signed release APK:
./gradlew :app:packageRelease

# Run unit tests
./gradlew testDebugUnitTest

# Install directly to connected device or emulator
./gradlew installDebug
```

**Output APKs:**
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

### Production Signing (Optional)

Add to `~/.gradle/gradle.properties`:

```properties
signingStoreLocation=/path/to/keystore.jks
signingStorePassword=your_store_password
signingKeyAlias=your_key_alias
signingKeyPassword=your_key_password
```

### CI/CD — Automatic Releases

Push a version tag to trigger the release pipeline:

```bash
git tag v13.4.9
git push origin v13.4.9
```

GitHub Actions (`.github/workflows/fork-release-apk.yml`) will:
1. Build `assembleRelease`
2. Rename the APK to `AdsAway_{version}.apk`
3. Create a GitHub Release with the APK attached
4. Prune older releases (keeps latest 3)

**Repository Secrets** (for production-signed APKs):

| Secret | Description |
|--------|-------------|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded keystore file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

### Key Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| OkHttp | 4.12.0 | HTTP downloads + AI API calls |
| Timber | 5.0.1 | Logging (no-op in release) |
| libsu | 6.0.0 | Root shell access |
| pcap4j | 1.8.2 | VPN packet capture/processing |
| dnsjava | 3.5.3 | DNS packet parsing |
| Guava | 32.0.1-android | Utilities |

All LLM API calls use raw OkHttp — no third-party AI SDK dependencies.

---

## How-To Guides

### Set Up Protection (First Time)

1. Open the app — the onboarding screen appears.
2. Auto-detection: root available → **Root mode** pre-selected; otherwise → **VPN mode**.
3. Tap **Start protecting** — default filter lists subscribe in the background.
4. You land on Home. Protection is active.

### Configure the AI Assistant

1. Go to **More → Preferences → AI Assistant**.
2. Under **Model**, select your provider (Claude / Gemini / ChatGPT) and model tier.
3. Under **API Keys**, tap your chosen provider → paste your key → tap **Save**.
4. The key is encrypted immediately. The row now shows "Configured (tap to change)".

### Ask AI to Manage Your Filters

1. Go to **Discover** tab.
2. Tap **Ask AI** chip.
3. Type what you want in plain English, e.g.:
   - "Block ads and trackers, keep WhatsApp working"
   - "Maximum privacy, I don't use Facebook"
   - "Is YouTube blocked? If so, unblock it"
   - "Subscribe to all ad-blocking lists and update them"
4. Tap **Ask** and wait a few seconds.
5. Review the **planned action list** + the AI's reasoning.
6. Tap **Execute** — actions run immediately.

### Add a Filter List from FilterLists.com

1. Tap the **Discover** tab.
2. Use the search box or filter by tag / language.
3. Tap a list → **Subscribe**.
4. The list is added and enabled. Tap **Update** on Home to apply.

### Apply a Quick-Start Preset

1. Tap the **Discover** tab.
2. Tap **Safe**, **Balanced**, or **Aggressive**.
3. A snackbar confirms how many lists were added.
4. Tap **Update** on Home to activate.

### Add a Custom Filter List

1. Go to **More → Filter Sources**.
2. Tap the **+** button.
3. Enter the list URL and select the format.
4. Tap **Add** → enable → tap **Update** on Home.

### Block / Allow a Specific Domain

1. Go to **More → Custom Rules**.
2. Tap **+** → choose **Block** or **Allow** → enter the domain.
3. Tap **Save**. Rules apply immediately (no update needed).

Alternatively, ask the AI: *"Block ads.example.com"* → tap Execute.

### Batch Import Domains

1. **More → Custom Rules → ⋮ → Batch Import**.
2. Paste domains, one per line.
3. Choose Block or Allow → confirm.

### View DNS Query Log (VPN mode only)

1. Go to **More → DNS Log**.
2. See real-time queries. Blocked = red, allowed = green.
3. Tap any entry → **Block** or **Allow** to add a custom rule.

### Schedule Automatic Updates

1. **More → Filter Sources → ⋮ → Manage schedules**.
2. Set daily (time) or weekly (day + time).
3. Updates run in the background at the scheduled time.

### Check If a Domain Is Blocked

**Option A — Domain Checker (no API key needed):**
1. **More → Domain Checker**.
2. Type any domain (or URL — port is stripped automatically).
3. Tap **Check** → see BLOCKED / ALLOWED and which source blocked it.

**Option B — Ask AI (natural language):**
1. Open the AI sheet, type *"Is whatsapp.com blocked?"*.
2. AI checks and reports; if blocked, say *"Unblock it"* to add an allowlist entry.

### Backup & Restore

1. **More → Preferences → Backup & Restore**.
2. **Backup** — exports filter sources, custom rules, and settings as JSON.
3. **Restore** — imports from a backup file.

### Force English Locale

Some Android versions override language settings. To lock AdsAway in English:
1. **More → Preferences → General → Force English**.
2. Force-stop and relaunch the app.

---

## FilterLists.com API

The Discover tab integrates the FilterLists.com public API:

| Endpoint | Used for |
|----------|----------|
| `GET /lists` | Browse all filter lists (with tagIds, languageIds, syntax) |
| `GET /tags` | Tag chip labels (41 tags, cached 24h in SharedPreferences) |
| `GET /languages` | Language spinner (81 languages, cached 24h) |
| `GET /lists/{id}` | Subscription URL for a specific list |

Responses are cached with a 24-hour TTL. No API key required.

---

## Permissions

| Permission | Purpose |
|------------|---------|
| `INTERNET` | Download filter lists + FilterLists.com API + AI API calls |
| `RECEIVE_BOOT_COMPLETED` | Restart VPN / hosts protection on device reboot |
| `FOREGROUND_SERVICE` | Background subscribe-all worker notification |
| `POST_NOTIFICATIONS` | Update completion notifications (Android 13+) |
| `QUERY_ALL_PACKAGES` | Adware scanner — list installed apps |
| `BIND_VPN_SERVICE` | Local VPN mode (non-root) |

Root mode additionally requires `su` access to write `/etc/hosts`.

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Ads not blocked | Tap **Update** on Home to refresh filter lists |
| AI: "No API key set" | Configure key in **Settings → AI Assistant** |
| AI: "Something went wrong" | Check API key is correct; check quota/billing on provider dashboard |
| AI response is slow | Switch to a faster model (Haiku / Flash / Nano) in AI settings |
| VPN disconnects randomly | Check **More → Preferences → VPN** settings |
| WhatsApp stops working | Disable the **SOCIAL** category — it blocks WhatsApp domains |
| Samsung Pay / OEM feature broken | Disable the **DEVICE** category |
| Filter list not updating | Check network; try **More → Filter Sources → long-press → Update** |
| App crashes on subscribe | Clear app data and re-onboard |
| Can't install over official AdAway | Uninstall the official AdAway first (different signing key) |
| "Install unknown apps" blocked | Android Settings → Apps → Special app access → Install unknown apps |
| Domain Checker shows wrong result | Port numbers (e.g., `:8080`) are stripped automatically since v13.3.5 |
| Force English not working | Force-stop the app after toggling the setting |

---

## Contributing

- Bug reports: [this fork's issues](https://github.com/stevesolun/AdAway/issues)
- PRs welcome — keep commits atomic, present tense, explain *why* not *what*

**Code style:**
- Java 17 target
- 4-space indent, no tabs
- 100-char line width
- Non-public fields prefixed `m`
- Acronyms as words: `getUrl()` not `getURL()`

**Threading rules (mandatory):**
- DB operations: `AppExecutors.getInstance().diskIO()`
- HTTP calls: `AppExecutors.getInstance().networkIO()`
- UI: `AppExecutors.getInstance().mainThread()`
- Never call `AppExecutors.getInstance()` in a `static final` field initializer

**DB migrations:** Room v11. Any schema change requires a migration file in `db/migration/`. Test with `MigrationTest`.

---

## License

[GPL v3](/LICENSE.md) — same as the upstream AdAway project.

---

## Upstream & Credits

This is a fork of [AdAway/AdAway](https://github.com/AdAway/AdAway). All credit to the original AdAway team and contributors.

Crafted with love by Steve Solun · Forked from AdAway
