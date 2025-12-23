# ![AdAway logo](app/src/main/res/mipmap-mdpi/icon.png) AdAway

[![Build Status](https://github.com/adaway/adaway/actions/workflows/android-ci.yml/badge.svg)](https://github.com/AdAway/AdAway/actions/workflows/android-ci.yml) 
[![Sonarcloud Status](https://sonarcloud.io/api/project_badges/measure?project=org.adaway&metric=security_rating)](https://sonarcloud.io/project/overview?id=org.adaway)
[![GitHub Downloads](https://img.shields.io/github/downloads/adaway/adaway/total?logo=github)](https://github.com/AdAway/AdAway/releases)
[![GitHub Sponsors](https://img.shields.io/github/sponsors/perfectslayer?logo=github)](https://github.com/sponsors/PerfectSlayer)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](/LICENSE.md)

AdAway is an open source ad blocker for Android using the hosts file and local vpn.

[<img src="metadata/en-US/phoneScreenshots/screenshot1.png"
    alt="Home screen (progress)"
    height="256">](metadata/en-US/phoneScreenshots/screenshot1.png)
[<img src="metadata/en-US/phoneScreenshots/screenshot2.png"
    alt="Hosts sources (categorized)"
    height="256">](metadata/en-US/phoneScreenshots/screenshot2.png)
[<img src="metadata/en-US/phoneScreenshots/screenshot3.png"
    alt="FilterLists import"
    height="256">](metadata/en-US/phoneScreenshots/screenshot3.png)
[<img src="metadata/en-US/phoneScreenshots/screenshot4.png"
    alt="Schedule manager"
    height="256">](metadata/en-US/phoneScreenshots/screenshot4.png)
[<img src="metadata/en-US/phoneScreenshots/screenshot5.png"
    alt="Subscribe-all running"
    height="256">](metadata/en-US/phoneScreenshots/screenshot5.png)

For more information visit https://adaway.org

## Filter management

This fork adds an enhanced filter management experience:

- **Categorized filter lists**: Sources are organized into expandable categories (Ads, Malware, Privacy, Social, Regional, Custom, etc.) with per-category enable/disable.
- **Catalog browsing**: A curated catalog with **Safe/Balanced/Aggressive/Custom** selection modes, search, and “already added” indicators.
- **Custom lists**: Add your own list URL and choose a preferred list format (Hosts / Domains / Adblock rules best-effort / Allowlist / Redirect).
- **Per-list update**: Update a single list from the sources screen when an update is available, or update all enabled sources.
- **FilterLists.com integration**: Browse/import lists from FilterLists and run **Subscribe to all** in the background.
- **Background progress + notifications**: “Subscribe to all” runs in the background (foreground worker notification) and posts a completion notification; you can leave the screen/app and come back later.
- **Progress on Home**: Live progress text + bar on the Home screen during long-running operations (subscribe-all + updates). Overall percentage is monotonic (won’t go backwards).
- **Filter sets + scheduling**: Save/apply “filter sets” and schedule automatic updates for:
  - the current active set, or any saved set
  - individual sources
  - daily at a chosen time
  - weekly on a chosen day + time
- **Schedule manager**: Manage global + filter-set + per-source schedules from **Hosts sources → menu → Manage schedules**, or from the **+** button sheet.
- **Update performance improvements** (v13.0):
  - Hardware-adaptive parallelism (auto-detects CPU cores and memory)
  - Parallel check + download + parse pipeline (overlapping phases)
  - Global host deduplication (same host from multiple sources stored only once)
  - Larger OkHttp connection pool with dispatcher tuning
  - Aggressive batching for DB inserts (5000 entries per batch)
  - Conditional GET (`If-None-Match` / `If-Modified-Since`) to skip unchanged sources
  - Monotonic progress display (percentages never decrease)

## Installing

### Install this fork (download APK)

- Download the latest APK from [Releases (latest)](https://github.com/stevesolun/AdAway/releases/latest).
- On Android: open the downloaded APK and allow **Install unknown apps** when prompted.
  - If you see an error like “App not installed” / “Signature conflict”, uninstall the previous `org.adaway` app first, then install the APK again.
- Verify it installed: open AdAway and confirm the version shown on the Home screen (top-right).

There are two kinds of release:
* The preview builds: on the bleeding edge of development - for testers or adventurous
* The stable builds: ready for every day usage - for end users

### Preview builds

**Requirements:** Android 8 _Oreo_ or above

For users with bugs, there may be preview builds available from the [XDA development thread](https://forum.xda-developers.com/showthread.php?t=2190753) and [AdAway official website](https://app.adaway.org/beta.apk).
It is recommended to try those builds to see if your issue is resolved before creating an issue.
The preview builds may contain bug fixes or new features for new android versions.

[<img src="Resources/get-it-on-adaway.png"
      alt="Get it on official AdAway website"
      height="80">](https://app.adaway.org/beta.apk)
[<img src="Resources/XDADevelopers.png"
      raw="true"
      alt="Get it on XDA forum"
      height="60">](https://forum.xda-developers.com/showthread.php?t=2190753)

### Stable builds

**Requirements:**
* Android Android 8 _Oreo_ or above

After preview builds have been tested by the more technical or responsive community within the forums, we will then post the stable build to F-Droid.

[<img src="Resources/get-it-on-adaway.png"
    alt="Get it on official AdAway website"
    height="80">](https://app.adaway.org/adaway.apk)
[<img src="Resources/get-it-on-fdroid.png"
      raw="true"
      alt="Get it on F-Droid"
      height="80">](https://f-droid.org/app/org.adaway)

For devices older than Android 8 _Oreo_, use the version 4 of AdAway.

## Get Host File Sources

See the [Wiki](https://github.com/AdAway/AdAway/wiki), in particular the page [HostsSources](https://github.com/AdAway/AdAway/wiki/HostsSources) for an assorted list of sources you can use in AdAway.
Add the ones you like to the AdAway "Hosts sources" section.

## Getting Help

You can post [Issues](https://github.com/AdAway/AdAway/issues) here or obtain more detailed community support via the [XDA developer thread](http://forum.xda-developers.com/showthread.php?t=2190753).

## Contributing

You want to be involved in the project? Welcome onboard!  
Check [the contributing guide](CONTRIBUTING.md) to learn how to report bugs, suggest features and make you first code contribution :+1:

If you are looking for translating the application in your language, [the translating guide](TRANSLATING.md) is for you.

## Project Status

AdAway is actively developed by:
* Bruce Bujon ([@PerfectSlayer](https://github.com/PerfectSlayer)) - Developer  
[PayPal](https://paypal.me/BruceBUJON) | [GitHub Sponsorship](https://github.com/sponsors/PerfectSlayer)
* Daniel Mönch ([@Vankog](https://github.com/Vankog)) - Translations
* Jawz101 ([@jawz101](https://github.com/jawz101)) - Hosts list
* Anxhelo Lushka ([@AnXh3L0](https://github.com/AnXh3L0)) - Web site

We do not forget the past maintainers:
* Dāvis Mošenkovs ([@DavisNT](https://github.com/DavisNT)) - Developer  
[Paypal](https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=5GUHNXYE58RZS&lc=US&item_name=AdAway%20Donation&no_note=0&no_shipping=1)
* [@0-kaladin](https://github.com/0-kaladin) - Developer and XDA OP
* Sanjay Govind ([@sanjay900](https://github.com/sanjay900)) - Developer

And we thank a lot to the original author:
* Dominik Schürmann ([@dschuermann](https://github.com/dschuermann)) - Original developer  
[Paypal](https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=android%40schuermann.eu&lc=US&item_name=AdAway%20Donation&no_note=0&no_shipping=1&currency_code=EUR) | [Flattr](https://flattr.com/thing/369138/AdAway-Ad-blocker-for-Android) | BTC: `173kZxbkKuvnF5fa5b7t21kqU5XfEvvwTs`

## Permissions

AdAway requires the following permissions:

* `INTERNET` to download hosts files and application updates. It can send bug reports and telemetry [if the user wants to (opt-in only)](https://github.com/AdAway/AdAway/wiki/Telemetry)
* `ACCESS_NETWORK_STATE` to restart VPN on network connection change
* `RECEIVE_BOOT_COMPLETED` to start the VPN on boot
* `FOREGROUND_SERVICE` to run the VPN service in foreground
* `POST_NOTIFICATIONS` to post notifications about hosts source update, application update and VPN controls. All notifications can be enabled or disabled independently.
* `REQUEST_INSTALL_PACKAGES` to update the application using the builtin updater
* `QUERY_ALL_PACKAGES` to let the user pick the applications to exclude from VPN

## Licenses

AdAway is licensed under the GPLv3+.  
The file LICENSE includes the full license text.
For more details, check [the license notes](LICENSE.md).

## Building from source (developer notes)

### JDK / Gradle

- **This project requires JDK 21** (matches the GitHub Actions workflows).

If you have newer JDKs installed (e.g. Java 25), keep them — but **run Gradle with JDK 21** to avoid build-tool incompatibilities (e.g. “Unsupported class file major version 69”).

### Windows + NDK builds

This project includes native modules (`:tcpdump`, `:webserver`). On Windows, `ndk-build` can fail when the project path contains spaces.

- **Recommended**: build from a path without spaces (e.g. copy/clone to `C:\AdAwayBuild\`), then run:

```bash
gradlew assembleDebug
```
