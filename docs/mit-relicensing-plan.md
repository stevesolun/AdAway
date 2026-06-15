# MIT Relicensing Track

The current distributed app must remain GPLv3+ until GPL-derived code and assets
are cleared. Do not change `LICENSE.md` to MIT while any GPL-only or GPL-derived
material remains in the combined app.

## Required Inventory

- App Java/Kotlin/XML/resources, including fork-specific features.
- Native modules, especially bundled tcpdump/libpcap and any dormant webserver
  code or assets.
- Third-party notices in `THIRD_PARTY_LICENSES.md`.
- Images, icons, copy, generated catalogs, and bundled filter metadata.
- CI/release artifacts, including update manifests and APK packaging.

## Current Blockers

- `LICENSE.md`, `README.md`, and `THIRD_PARTY_LICENSES.md` must continue to
  describe the current GPLv3/GPLv3+ distribution. Keep `LICENSE.md` GPLv3+ until
  the blockers below are cleared; use `THIRD_PARTY_LICENSES.md` as the scoped
  source/APK/dormant component inventory.
- The VPN stack contains explicit DNS66/AdBuster-derived GPLv3 code, including
  `VpnService.java`, `DnsPacketProxy.java`, `VpnWorker.java`, and
  `VpnWatchdog.java`.
- Multiple app files still carry GPL headers from the upstream AdAway codebase,
  including Dominik Schurmann copyright notices.
- The old packaged launcher/app logo/adaptive icon artwork and packaged
  PayPal/GitHub mark drawables have been replaced with project-created
  geometric vectors in this fork. Remaining asset work is provenance review for
  other shortcut/menu resources and source-only historical material under
  `Resources/`.
- The old reusable localhost certificate and private key assets were removed.
  Keep that material out of any new permissive-license edition or hardened
  security posture.

## Native And Dormant Components

- The `webserver` module is not included by `settings.gradle`, and the dormant
  runtime path is hard-disabled pending replacement or re-audit.
- If HTTPS localhost serving remains a product goal, generate a per-install
  certificate/key pair and require explicit user opt-in before any CA-install
  prompt. Do not ship reusable private key material.
- `THIRD_PARTY_LICENSES.md` scopes Mongoose GPLv2 and OpenSSL as dormant until a
  build artifact proves they are shipped.
- tcpdump/libpcap are BSD-style licensed and not an MIT blocker, but the vendored
  C code is old and needs a separate safety review before market-leading claims.
- Historical non-Android libpcap object files under `tcpdump/jni/libpcap/SUNOS4`
  have been removed from the working source tree. Dormant native trees are also
  excluded from generated release source archives with `.gitattributes`
  `export-ignore` rules while remaining available in normal git checkouts for
  auditability.
- Build and test dependencies are tracked separately in `THIRD_PARTY_LICENSES.md`
  because they are source/build materials, not proof of APK packaging.

## MIT Paths

1. Obtain written relicensing permission from every required rights holder.
2. Remove GPL-derived components from the distributed app.
3. Rewrite GPL-derived components behind clean-room replacement interfaces.
4. Keep the GPL app and create a separate MIT-compatible edition with a verified
   dependency and asset bill of materials.

## Release Gate

Before any MIT-branded release:

- The license inventory must identify every shipped component and license.
- GPL-only/GPL-derived code must be removed, replaced, or permission-cleared.
- Release notes, app metadata, update manifests, and notices must match the
  verified license state.
- Legal review must approve the final artifact, not just the source tree.
