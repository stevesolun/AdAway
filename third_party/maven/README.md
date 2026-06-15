# Local Maven Mirror

This repository mirrors the single JitPack-only runtime dependency that blocks
repeatable CI when GitHub Actions receives HTTP 403 from JitPack.

Do not add broad dependency mirrors here. Each artifact must stay pinned in
`gradle/verification-metadata.xml`, documented in `THIRD_PARTY_LICENSES.md`,
and scoped in `settings.gradle`.

## Mirrored Artifacts

| Component | Version | License | Source repository | Original Maven URL |
| --- | --- | --- | --- | --- |
| `com.github.topjohnwu.libsu:core` | `6.0.0` | Apache-2.0 | `https://github.com/topjohnwu/libsu` | `https://jitpack.io/com/github/topjohnwu/libsu/core/6.0.0/` |

| File | SHA-256 |
| --- | --- |
| `com/github/topjohnwu/libsu/core/6.0.0/core-6.0.0.aar` | `a1ca5a8adb9ab11c42b71fc2d2a61b5a95cb4cdd06df0eb8c204c06813c2bb5b` |
| `com/github/topjohnwu/libsu/core/6.0.0/core-6.0.0.module` | `0d87c4308c16150fb709adb03224532541b2c9e3faa487917aa989c0c0421ffa` |
| `com/github/topjohnwu/libsu/core/6.0.0/core-6.0.0.pom` | `16bc770ce1a36c957027c55e150cad31fa9a5b0b140fb6fcaa5fde13e949e587` |
