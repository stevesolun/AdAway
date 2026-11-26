# Public app update journey

**Partial on signed API 34, 2026-11-26:** public 13.5.4b discovered and downloaded official 13.5.6b through AdAway, twice. Neither attempt opened Android's package installer. Current public 13.5.6b correctly showed **You are up-to-date**. No public manifest offers the newer private 13.5.7b candidate. This is not an end-to-end installer pass.

## Supported route and authorization

- Use **More > Preferences > Automatic updates > Application updates > Include beta releases**. With **Check for update at startup** enabled, cold-launch the app, then tap the **Home version badge** (or **Update available!**). This is distinct from Home's hosts-update icons and the Sources update menu.
- The direct APK variant accepts its configured release signer and identifies the store as `adaway`; the direct self-updater is not the F-Droid path. Stable requests the fork's [latest stable manifest](https://github.com/stevesolun/AdAway/releases/latest/download/manifest.json), which returned HTTP 404. Beta queries the fork's [release index](https://api.github.com/repos/stevesolun/AdAway/releases?per_page=20) and locates the newest published eligible signed manifest.
- The [public 13.5.6b manifest](https://github.com/stevesolun/AdAway/releases/download/v13.5.6b/manifest.json) advertised code 130506, channel `beta`, store `adaway`, expiring 2026-12-09 15:38:56 UTC. Both it and the [prior public manifest](https://github.com/stevesolun/AdAway/releases/download/v13.5.4b/manifest.json) passed RSA/SHA-256 verification against the public key embedded in the starting signed candidate. Both official APK hashes matched their signed payloads, and Android APK signature verification identified the same certificate as the candidate.

| Official public APK | SHA-256 |
| --- | --- |
| [13.5.4b / 130504](https://github.com/stevesolun/AdAway/releases/tag/v13.5.4b) | `d33d02aabf4d502626fc7ae08c509ff28e3977712fb8ecdb5659cfc6f335ab63` |
| [13.5.6b / 130506](https://github.com/stevesolun/AdAway/releases/tag/v13.5.6b) | `75f064326e8e2059808ffcd656448f2deb1d2825ac0f482f09eb66913681f621` |

Shared signer SHA-256: `9bf3a40102638ed9271f4dea6225a2d66e5d2c5f3e5204cc125376288f7f9595`.

## Observed journey

1. **Private 13.5.7b / 130507:** the restored installation had beta unchecked. Tapping the Home badge, then **Check again**, displayed **Unable to check for updates**. Enabling beta and checking again did not offer a downgrade. The beta manifest was one code behind; the candidate's manifest policy rejects lower version codes.
2. **Public 13.5.4b setup:** uninstalled the candidate from disposable `emulator-5576`, installed the verified official prior APK without a downgrade override, and completed VPN onboarding. Enabled beta through Preferences and cold-launched. Home displayed **Update available!**; its update page displayed **Update now** and **See GitHub release v13.5.6b.**
3. **First app-initiated download:** tapped **Update now**. Android DownloadManager row 11 finished with `SUCCESS`; AdAway displayed **Complete**. No Android install-permission screen or installer was observed.
4. **Permission control:** opened Android's ordinary **Install unknown apps > AdAway** screen and enabled **Allow from this source**. Confirmed the app-specific permission was allowed, returned to AdAway, reopened the update offer, and tapped **Update now** again. Download 12 also finished with `SUCCESS`. The settled view was still **Complete**, with AdAway's update activity in front and Android reporting 13.5.4b / 130504 installed. Disabled the temporary permission afterward.
5. **Current public control:** only after recording both failures, installed the verified official 13.5.6b using a normal host-side package update, retaining the enabled beta preference. This setup step is **not** credited to AdAway's updater. On relaunch, Home showed **13.5.6b**; tapping it displayed **You are up-to-date**, the matching release note, and no installation button.

The [13.5.4b implementation](https://github.com/stevesolun/AdAway/blob/v13.5.4b/app/src/main/java/org/adaway/model/update/UpdateModel.java) uses a non-exported completion receiver and an immediate download-state check. The [13.5.6b implementation](https://github.com/stevesolun/AdAway/blob/v13.5.6b/app/src/main/java/org/adaway/model/update/UpdateModel.java) has different completion handling, including polling. This is a plausible explanation for the old build's stall, not an isolated cause or evidence that the private candidate stalls. There was no newer public target to test the modern implementation against. No fabricated manifest, direct installer launch, signature override, external publication, or product edit.

## Evidence and handoff

Local receipts: `/tmp/adaway-public-update-5576.rCQmst/`. Relevant trees: `candidate-check.xml`, `candidate-beta-checked.xml`, `prior-beta-toggle.xml`, `prior-relaunch.xml`, `prior-offer.xml`, `prior-after-download.xml`, `prior-permission-return.xml`, `prior-second-settled.xml`, and `current-manifest-screen.xml`. See also `download-events.txt`, both downloaded manifests, and `current-up-to-date.png`.

The UI trees, both manifests, download events and final screenshot are also retained in the ignored [public-app-update-5576 proof directory](../../../app/build/reports/audit-2026-11-25/public-app-update-5576/). The subsequent [signed in-place upgrade](restored-phone-release.md) records the newer handoff state.

**Historical handoff:** 5576 was left running on official public 13.5.6b, beta enabled, the up-to-date screen open, and `REQUEST_INSTALL_PACKAGES` denied. See the [verification decision](decision.md) for subsequent device cleanup. The original restored app data was cleared when replacing the candidate for this test. The exact starting candidate APK was retained locally as `original-candidate.apk` (SHA-256 `8f1b3cc5e4639790eee74c08b43c0cbad0dcfbb3ca60b40144192474b4f46447`); it was not reinstalled. No commands were sent to 5578 during this journey.
