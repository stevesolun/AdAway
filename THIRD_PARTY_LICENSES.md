# Licenses

AdAway is licensed under the GPLv3 or later. The file [LICENSE.md](LICENSE.md)
contains the full license text.

MIT relicensing is a future track only. The distributed app remains GPLv3+
until GPL-derived app code, VPN code, shipped assets, and notice boundaries are
removed, rewritten, or permission-cleared. See
[docs/mit-relicensing-plan.md](docs/mit-relicensing-plan.md).

## Current App And Source Inventory

| Component | License | Scope | Packaged in `:app` APK | MIT-edition status |
| --- | --- | --- | --- | --- |
| AdAway application code and resources | GPLv3+ | App source and resources | Yes | Blocking until GPL-derived code is removed, rewritten, or permission-cleared. |
| DNS66 / AdBuster-derived VPN code | GPLv3 | VPN service and DNS proxy source in `app/src/main/java` | Yes | Blocking. The VPN stack must be permission-cleared or replaced for any MIT edition. |
| Android Jetpack / AndroidX | Apache-2.0 | Android libraries | Yes | Compatible notice/dependency item. |
| dnsjava | BSD-3-Clause | DNS protocol library | Yes | Compatible notice/dependency item. |
| Guava | Apache-2.0 | Utility library | Yes | Compatible notice/dependency item. |
| libsu | Apache-2.0 | Root access support; `6.0.0` is mirrored under `third_party/maven` from JitPack because CI runners can receive HTTP 403 from the upstream Maven endpoint | Yes | Compatible notice/dependency item. |
| Material Components for Android | Apache-2.0 | UI component library | Yes | Compatible notice/dependency item. |
| OkHttp / OkHttp DNS-over-HTTPS | Apache-2.0 | HTTP client and DoH client library | Yes | Compatible notice/dependency item. |
| Okio | Apache-2.0 | I/O dependency pulled by OkHttp | Yes | Compatible notice/dependency item. |
| Pcap4J | MIT | Packet capture Java API dependency | Yes | Compatible notice/dependency item. |
| JNA | Apache-2.0 / LGPL-2.1 dual-license | Native access dependency pulled by Pcap4J | Yes | Compatible notice/dependency item. |
| Sentry Java / Android | MIT | Crash reporting dependency for eligible/debug or unsigned builds; signed production builds use `:sentrystub` | Conditional | Compatible notice/dependency item when packaged. |
| SLF4J | MIT | Logging facade dependency | Yes when pulled by packaged dependencies | Compatible notice/dependency item. |
| Timber | Apache-2.0 | App logging library | Yes | Compatible notice/dependency item. |
| Tcpdump / Libpcap | BSD-3-Clause | Vendored native source under `tcpdump/` | No, source-only in the current Gradle app build | Not an MIT blocker, but old native source still needs safety review before market-leading claims. |
| Mongoose Webserver | GPLv2 | Dormant historical/native webserver notice | No current `:app` packaging path known | Treat as dormant until a build artifact proves it is shipped; do not use in an MIT edition without replacement or legal review. |
| OpenSSL | OpenSSL / SSLeay license | Dormant historical webserver/TLS notice | No current `:app` packaging path known | Treat as dormant until a build artifact proves it is shipped; verify before release notices. |

## Shipped Images And App Assets

| Asset family | Source / lineage | Scope | Packaged in `:app` APK | MIT-edition status |
| --- | --- | --- | --- | --- |
| Launcher adaptive icon (`app/src/main/res/mipmap-anydpi/icon.xml`) | AdAway bird foreground vector from the app resource set | App launcher icon | Yes | App-code and asset provenance still block a full MIT edition until the bird artwork is permission-cleared or replaced. |
| Launcher density fallback icons (`app/src/main/res/mipmap-*/icon*.png`) | AdAway bird launcher PNG fallbacks from the app resource set | Density-specific launcher fallback icons | Yes | App-code and asset provenance still block a full MIT edition until the bird artwork is permission-cleared or replaced. |
| App logo (`app/src/main/res/drawable/logo.xml`) | AdAway bird logo vector from the app resource set | Manifest activity icon, notification icon, and in-app branding | Yes | App-code and asset provenance still block a full MIT edition until the bird artwork is permission-cleared or replaced. |
| Icon foreground/monochrome drawables (`app/src/main/res/drawable/icon_foreground_*`, `icon_monochrome`) | AdAway bird foreground vectors from the app resource set | Adaptive icon layers and home/FAB state icons | Yes | App-code and asset provenance still block a full MIT edition until the bird artwork is permission-cleared or replaced. |
| Generic support/source icons (`ic_support_24dp`, `ic_code_host_24dp`) | Project-created simple vector icons introduced for this fork | Donate/sponsor/source/help affordances | Yes | Replaces packaged PayPal and GitHub mark drawables. External support/source links remain separate product/legal decisions. |
| Shortcut and menu icons | Android/project UI assets | App shortcuts and UI | Yes | Must remain inventoried; replace or verify individually before MIT edition. |

## Source-Only / Build-Test Materials

| Component | License | Scope | Packaged in `:app` APK | MIT-edition status |
| --- | --- | --- | --- | --- |
| Android Gradle Plugin | Apache-2.0 | Build plugin | No | Build-time dependency; keep SBOM/provenance notices current. |
| SonarQube Gradle plugin | LGPL-3.0 | Static analysis plugin | No | Build-time dependency; not an APK blocker. |
| CycloneDX Gradle plugin | Apache-2.0 | SBOM generation plugin | No | Build-time dependency; keep release SBOM generation pinned. |
| Room compiler | Apache-2.0 | Annotation processor | No | Build-time dependency; not packaged in the APK. |
| JUnit / AndroidX Test / Room Testing / MockWebServer | EPL-1.0, Apache-2.0, and project-specific test-library licenses | Unit and connected test dependencies | No | Test-only materials; keep source distribution notices current. |
| org.json:json | Public Domain style JSON.org license | Unit-test JSON parsing dependency | No | Test-only material; keep notices current. |
| Gradle wrapper and dependency verification metadata | Gradle / dependency provenance metadata | Build reproducibility and dependency integrity | No | Keep wrapper validation and `gradle/verification-metadata.xml` current. |

## Source-Only Resources Inventory

| Asset family | Source / lineage | Scope | Packaged in `:app` APK | MIT-edition status |
| --- | --- | --- | --- | --- |
| `Resources/icon_old.svg` | Declares Creative Commons Attribution-ShareAlike 3.0 metadata | Historical source asset | No | ShareAlike asset; exclude, replace, or permission-clear before any MIT source edition. |
| `Resources/icon.svg`, `status_bar_icon.svg`, XCF/PNG icon sources | Historical AdAway/source artwork, incomplete visible metadata | Source artwork | No | License metadata must be recovered or assets replaced before MIT source distribution. |
| Store/download/social marks (`download_google_play.png`, `download_fdroid.png`, `get-it-on-fdroid.png`, `XDADevelopers.png`, PayPal donate image) | Third-party brand/trademark assets | Historical website/store/support materials | No | Verify trademark/license basis or exclude/replace. |
| Screenshots and Tasker screenshots | Historical app screenshots | Documentation/source materials | No | Verify generated-content ownership and whether they show third-party marks/data. |
| `Resources/symlink_hosts_to_data.zip` and `Resources/certificate/` helpers | Historical support/test materials | Source-only utilities | No | Re-audit before shipping in any permissive source bundle; do not treat as MIT-cleared by default. |

## Source Distribution Notes

- `settings.gradle` currently includes `:app` and `:sentrystub`; the old native
  tcpdump module is not part of the app APK build.
- Generated release source archives exclude historical `Resources/` material and
  dormant native `tcpdump/` and `webserver/` modules via `.gitattributes`
  `export-ignore` rules. A full repository checkout still preserves that
  historical material for auditability.
- Dormant native/webserver notices are retained here for source distribution
  transparency, not as proof that those components are in the current APK.
- Historical non-Android libpcap object files under `tcpdump/jni/libpcap/SUNOS4`
  have been removed from the working source tree.
- Before any MIT-branded artifact, generate a fresh dependency and asset bill of
  materials from the release artifact, not only from this source inventory.
