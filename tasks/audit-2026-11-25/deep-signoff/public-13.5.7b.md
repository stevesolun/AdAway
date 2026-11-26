# Public non-root Android beta: 13.5.7b

[PR #23](https://github.com/stevesolun/AdAway/pull/23) merged as
`a6a92eb6cab129f04af8db5c631e98d13e8f8f4f`.
The [tagged workflow](https://github.com/stevesolun/AdAway/actions/runs/45763219241)
published [AdAway_13.5.7b.apk](https://github.com/stevesolun/AdAway/releases/download/v13.5.7b/AdAway_13.5.7b.apk).
The [final PR Android run](https://github.com/stevesolun/AdAway/actions/runs/45760328914)
reported 679 passed, 21 skipped, zero failures.

## Release identity

| Verified item | Observed value |
| --- | --- |
| APK SHA-256 | `039020460a1c219961a18d37c9d1660f144a95b37663e5337deeb918e7861fbc` |
| Signer SHA-256 | `9bf3a40102638ed9271f4dea6225a2d66e5d2c5f3e5204cc125376288f7f9595` |
| Package / version code | `org.adaway` / `130507`, not debuggable |
| Source | `refs/tags/v13.5.7b` at the merge commit above |

The [independent hosted run](https://github.com/stevesolun/AdAway/actions/runs/45763790578)
verified the actual APK identity, signed manifest and attestations for six assets.
A separate [cross-release run](https://github.com/stevesolun/AdAway/actions/runs/45764511394)
verified all six 13.5.6b assets from the newer checkout: the tool revision was
`a6a92eb6cab129f04af8db5c631e98d13e8f8f4f`, while the APK came from
`29729ecd4b6142c108b82b7d8c0e8cab5f6f23b7`. These are not physical-device tests.

## Public updater on Android 14

The installed public 13.5.6b found this update after reopening Home. AdAway
opened Android's install-source permission screen and package installer; the
user confirmed Update. No direct adb install of the new APK. The original
first-install time stayed unchanged; the package manager recorded AdAway as
the originating app. An extracted copy of the installed APK matched the
published bytes. Firebog AdGuard DNS remained the only enabled source and the
beta-channel preference survived. Temporary install permissions were revoked.

Protection was off on opening the upgraded app. One tap restored the tunnel
without new consent. With protection off, doubleclick.net resolved. With it on,
doubleclick.net stopped resolving, example.org answered, and Chrome displayed
RFC 1855 over HTTPS. No claim of automatic restoration, first-party/cosmetic
blocking, physical-phone coverage or every possible click sequence.
See [issue #25](https://github.com/stevesolun/AdAway/issues/25).

## Android 11 first-query investigation

Three fresh non-root API 30 emulators ran the eight historical methods in the
original order, once per boot, from a debug build of the merge commit. Each
first blocked UDP query followed seven passing methods and returned the expected
NODATA response with AdAway's SOA on the first send. All 24 passed without
retries, timeout changes or skips. The controlled probes used only test names;
this was not a zero-network-traffic measurement. The app/test APK hashes
were `5dfaecd05142de1b352e44e79efedcade355bd421cecd2f7d852a8486585447f` and
`47225e9285dc7f23b1754e6cfbb1c0ef842f0f70d6e4f734f6d042756fa6001c`.
Local method and socket receipts are in
`app/build/reports/audit-2026-11-25/postpublish-api30/`. The original
intermittent failure QA-VPN-DNS-001 was not reproduced; its cause is unknown.
