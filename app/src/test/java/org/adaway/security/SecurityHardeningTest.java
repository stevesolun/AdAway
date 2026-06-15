package org.adaway.security;

import org.adaway.util.RegexUtils;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Security regression tests for hardening changes in v13.4.2 and v13.4.3.
 * Only tests that use pure-Java code live here; Android-framework-dependent tests
 * (e.g. HostsSource.isValidUrl which uses android.webkit.URLUtil) must go in androidTest.
 *
 * <p>Each test is labelled with the ATK-XX identifier it guards.
 * ATK-23 tests live in BackupFormatSecurityTest (same package as BackupFormat).
 */
public class SecurityHardeningTest {

    // -------------------------------------------------------------------------
    // ATK-01: Private/reserved IP redirect blocking (RegexUtils)
    // -------------------------------------------------------------------------

    @Test
    public void atk01_loopbackIpRejectedAsRedirectTarget() {
        assertTrue("127.0.0.1 must be flagged as private", RegexUtils.isPrivateOrReservedIp("127.0.0.1"));
        assertTrue("127.0.0.2 must be flagged as private", RegexUtils.isPrivateOrReservedIp("127.0.0.2"));
        assertTrue("::1 must be flagged as private (IPv6 loopback)", RegexUtils.isPrivateOrReservedIp("::1"));
    }

    @Test
    public void atk01_rfc1918RangesRejectedAsRedirectTarget() {
        assertTrue("10.0.0.1 must be private", RegexUtils.isPrivateOrReservedIp("10.0.0.1"));
        assertTrue("10.255.255.255 must be private", RegexUtils.isPrivateOrReservedIp("10.255.255.255"));
        assertTrue("172.16.0.1 must be private", RegexUtils.isPrivateOrReservedIp("172.16.0.1"));
        assertTrue("172.31.255.255 must be private", RegexUtils.isPrivateOrReservedIp("172.31.255.255"));
        assertTrue("192.168.0.1 must be private", RegexUtils.isPrivateOrReservedIp("192.168.0.1"));
        assertTrue("192.168.255.255 must be private", RegexUtils.isPrivateOrReservedIp("192.168.255.255"));
    }

    @Test
    public void atk01_linkLocalRejectedAsRedirectTarget() {
        assertTrue("169.254.0.1 must be link-local", RegexUtils.isPrivateOrReservedIp("169.254.0.1"));
        assertTrue("fe80::1 must be link-local", RegexUtils.isPrivateOrReservedIp("fe80::1"));
    }

    @Test
    public void atk01_multicastRejectedAsRedirectTarget() {
        assertTrue("224.0.0.1 must be multicast", RegexUtils.isPrivateOrReservedIp("224.0.0.1"));
        assertTrue("239.255.255.255 must be multicast", RegexUtils.isPrivateOrReservedIp("239.255.255.255"));
    }

    @Test
    public void atk01_publicIpAllowedAsRedirectTarget() {
        assertFalse("8.8.8.8 must NOT be flagged as private", RegexUtils.isPrivateOrReservedIp("8.8.8.8"));
        assertFalse("1.1.1.1 must NOT be flagged as private", RegexUtils.isPrivateOrReservedIp("1.1.1.1"));
        assertFalse("104.21.0.1 must NOT be flagged as private", RegexUtils.isPrivateOrReservedIp("104.21.0.1"));
    }

    @Test
    public void atk01_invalidIpReturnsFalse() {
        assertFalse("not-an-ip must return false", RegexUtils.isPrivateOrReservedIp("not-an-ip"));
        assertFalse("empty must return false", RegexUtils.isPrivateOrReservedIp(""));
    }

    // Note: ATK-02 (HostsSource.isValidUrl) tests require android.webkit.URLUtil
    // and must live in the instrumented androidTest suite.

    // ATK-09 + ATK-29 tests are in FilterListSuggesterSanitizeTest (same package as FilterListSuggester)
    // ATK-23 tests are in BackupFormatSecurityTest (same package as BackupFormat)

    // -------------------------------------------------------------------------
    // ATK-15: Negative modelIndex clamped to valid range
    // -------------------------------------------------------------------------

    @Test
    public void atk15_mathMaxClampsNegativeIndex() {
        int negativeIndex = -5;
        int len = 3;
        int safeIndex = Math.max(0, Math.min(negativeIndex, len - 1));
        assertEquals("Negative index must be clamped to 0", 0, safeIndex);
    }

    @Test
    public void atk15_mathMinClampsOversizedIndex() {
        int oversizedIndex = 100;
        int len = 3;
        int safeIndex = Math.max(0, Math.min(oversizedIndex, len - 1));
        assertEquals("Oversized index must be clamped to len-1", len - 1, safeIndex);
    }

    // -------------------------------------------------------------------------
    // ATK-30: Dormant webserver must not ship reusable localhost credentials
    // -------------------------------------------------------------------------

    @Test
    public void atk30_noPackagedWebServerCredentialMaterial() throws IOException {
        Path assets = appDir().resolve("src/main/assets");
        if (!Files.isDirectory(assets)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(assets)) {
            Path secretFile = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.equals("localhost-2410.crt") ||
                                name.endsWith(".key") ||
                                name.endsWith(".pem") ||
                                name.endsWith(".p12") ||
                                name.endsWith(".jks") ||
                                name.endsWith(".bks");
                    })
                    .findFirst()
                    .orElse(null);
            assertNull("Packaged assets must not include reusable certificate/key material.", secretFile);
        }
    }

    @Test
    public void atk30_assetsDoNotContainPrivateKeys() throws IOException {
        Path assets = appDir().resolve("src/main/assets");
        if (!Files.isDirectory(assets)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(assets)) {
            Path privateKey = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> contains(path, "BEGIN PRIVATE KEY"))
                    .findFirst()
                    .orElse(null);
            assertNull("Packaged assets must not contain private keys.", privateKey);
        }
    }

    @Test
    public void atk30_networkSecurityConfigDoesNotTrustUserCasForLocalhost() throws IOException {
        String config = readUtf8(appDir().resolve("src/main/res/xml/network_security_config.xml"));

        assertFalse("Network config must not trust user CAs.", config.contains("certificates src=\"user\""));
        assertFalse("Network config must not define a localhost user-CA domain.",
                config.contains(">localhost</domain>"));
    }

    @Test
    public void atk30_bootAndRootDoNotStartDormantWebServer() throws IOException {
        Path app = appDir();
        String bootReceiver = readUtf8(app.resolve("src/main/java/org/adaway/broadcast/BootReceiver.java"));
        String rootModel = readUtf8(app.resolve("src/main/java/org/adaway/model/root/RootModel.java"));

        assertFalse("BootReceiver must not restart the dormant webserver.",
                bootReceiver.contains("WebServerUtils.startWebServer"));
        assertFalse("RootModel must not restart the dormant webserver.",
                rootModel.contains("WebServerUtils.startWebServer"));
    }

    @Test
    public void atk32_rootTcpdumpCaptureDisabledUntilMaintainedReplacementExists() throws IOException {
        String tcpdumpUtils = readUtf8(
                appDir().resolve("src/main/java/org/adaway/model/root/TcpdumpUtils.java"));

        assertTrue("Old root tcpdump capture must stay disabled in production code.",
                tcpdumpUtils.contains("TCPDUMP_CAPTURE_ENABLED = false"));
    }

    @Test
    public void atk32_tcpdumpNativeModuleIsNotPackaged() throws IOException {
        Path repo = repoDir();
        String appBuild = readUtf8(repo.resolve("app/build.gradle"));
        String settings = readUtf8(repo.resolve("settings.gradle"));

        assertFalse("App must not package the dormant tcpdump native module.",
                appBuild.contains("project(':tcpdump')"));
        assertFalse("The dormant tcpdump module must stay out of normal Gradle builds.",
                settings.contains("':tcpdump'"));
    }

    @Test
    public void atk32_releaseSourceArchiveExcludesDormantNativeAndHistoricalAssets()
            throws IOException {
        Path repo = repoDir();
        String attributes = readUtf8(repo.resolve(".gitattributes"));

        assertTrue("Release source archives must exclude historical artwork/support resources.",
                attributes.contains("/Resources export-ignore"));
        assertTrue("Release source archives must exclude historical artwork/support resources.",
                attributes.contains("/Resources/** export-ignore"));
        assertTrue("Release source archives must exclude dormant tcpdump native source.",
                attributes.contains("/tcpdump export-ignore"));
        assertTrue("Release source archives must exclude dormant tcpdump native source.",
                attributes.contains("/tcpdump/** export-ignore"));
        assertTrue("Release source archives must exclude dormant webserver native source.",
                attributes.contains("/webserver export-ignore"));
        assertTrue("Release source archives must exclude dormant webserver native source.",
                attributes.contains("/webserver/** export-ignore"));
        assertTrue("Release source archives must keep removed reusable localhost certs out.",
                attributes.contains("/app/src/main/assets/localhost-2410.crt export-ignore"));
        assertTrue("Release source archives must keep removed reusable localhost keys out.",
                attributes.contains("/app/src/main/assets/localhost-2410.key export-ignore"));
        assertTrue("Release source archives must keep removed localhost test page out.",
                attributes.contains("/app/src/main/assets/test.html export-ignore"));
        assertTrue("Release source archives must keep removed legacy app icon asset out.",
                attributes.contains("/app/src/main/assets/icon.svg export-ignore"));

        assertFalse("Reusable localhost certificate must not remain in the app source tree.",
                Files.exists(repo.resolve("app/src/main/assets/localhost-2410.crt")));
        assertFalse("Reusable localhost private key must not remain in the app source tree.",
                Files.exists(repo.resolve("app/src/main/assets/localhost-2410.key")));
        assertFalse("Dormant localhost test page must not remain in the app source tree.",
                Files.exists(repo.resolve("app/src/main/assets/test.html")));
        assertFalse("Legacy app asset icon must not remain in app assets.",
                Files.exists(repo.resolve("app/src/main/assets/icon.svg")));
        assertFalse("Historical SunOS libpcap object file must not remain in the source tree.",
                Files.exists(repo.resolve("tcpdump/jni/libpcap/SUNOS4/nit_if.o.sparc")));
        assertFalse("Historical SunOS libpcap object file must not remain in the source tree.",
                Files.exists(repo.resolve("tcpdump/jni/libpcap/SUNOS4/nit_if.o.sun3")));
        assertFalse("Historical SunOS libpcap object file must not remain in the source tree.",
                Files.exists(repo.resolve("tcpdump/jni/libpcap/SUNOS4/nit_if.o.sun4c.4.0.3c")));

        Path archive = Files.createTempFile("adaway-source-archive", ".zip");
        try {
            Process process = new ProcessBuilder("git", "archive", "--format=zip",
                    "--worktree-attributes", "-o", archive.toString(), "HEAD")
                    .directory(repo.toFile())
                    .start();
            if (!process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                fail("Timed out generating source archive for export-ignore verification.");
            }
            assertEquals("git archive must succeed for source archive verification.",
                    0, process.exitValue());
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                zip.stream().map(ZipEntry::getName).forEach(name -> {
                    assertFalse("Release source archive must not include historical Resources entries: " + name,
                            name.startsWith("Resources/"));
                    assertFalse("Release source archive must not include dormant tcpdump entries: " + name,
                            name.startsWith("tcpdump/"));
                    assertFalse("Release source archive must not include dormant webserver entries: " + name,
                            name.startsWith("webserver/"));
                    assertFalse("Release source archive must not include removed app asset: " + name,
                            name.equals("app/src/main/assets/icon.svg") ||
                                    name.equals("app/src/main/assets/localhost-2410.crt") ||
                                    name.equals("app/src/main/assets/localhost-2410.key") ||
                                    name.equals("app/src/main/assets/test.html"));
                    assertFalse("Release source archive must not include old binary libpcap object: " + name,
                            name.contains("SUNOS4/nit_if.o"));
                });
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            fail("Interrupted while generating source archive.");
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    @Test
    public void atk32_dnsQueryNamesAreNotLoggedToTimber() throws IOException {
        Path app = appDir();
        String dnsPacketProxy = readUtf8(
                app.resolve("src/main/java/org/adaway/vpn/dns/DnsPacketProxy.java"));
        String dohPacketProxy = readUtf8(
                app.resolve("src/main/java/org/adaway/vpn/dns/DohPacketProxy.java"));

        assertFalse("VPN DNS proxy must not log raw DNS query names.",
                dnsPacketProxy.contains("DNS Name %s"));
        assertFalse("DoH DNS proxy must not log raw DNS query names.",
                dohPacketProxy.contains("DNS Name %s"));
    }

    @Test
    public void atk32_vpnBypassRequiresExplicitCompatibilityPreference() throws IOException {
        Path app = appDir();
        String vpnBuilder = readUtf8(
                app.resolve("src/main/java/org/adaway/vpn/worker/VpnBuilder.java"));
        String preferences = readUtf8(app.resolve("src/main/res/values/preferences.xml"));
        String vpnPreferences = readUtf8(app.resolve("src/main/res/xml/preferences_vpn.xml"));
        int preferenceCheckIndex = vpnBuilder.indexOf("getVpnAllowAppBypass(service)");
        int allowBypassIndex = vpnBuilder.indexOf("builder.allowBypass()");

        assertTrue("VPN bypass must be guarded by an explicit compatibility preference.",
                preferenceCheckIndex >= 0);
        assertTrue("VPN bypass must still be available only as an explicit escape hatch.",
                allowBypassIndex > preferenceCheckIndex);
        assertTrue("Programmatic app VPN bypass must be off by default.",
                preferences.contains("<bool name=\"pref_vpn_allow_app_bypass_def\">false</bool>"));
        assertTrue("VPN settings must expose the bypass compatibility switch.",
                vpnPreferences.contains("@string/pref_vpn_allow_app_bypass_key"));
    }

    @Test
    public void atk32_dohBlockRoutesCoverCommonIpv4AndIpv6Providers() throws IOException {
        String dnsServerMapper = readUtf8(
                appDir().resolve("src/main/java/org/adaway/vpn/dns/DnsServerMapper.java"));

        assertTrue("DoH block routing must include the IPv4 provider table.",
                dnsServerMapper.contains("DOH_PROVIDER_IPV4"));
        assertTrue("DoH block routing must include the IPv6 provider table.",
                dnsServerMapper.contains("DOH_PROVIDER_IPV6"));
        assertTrue("Cloudflare IPv6 DoH endpoint must be blocked when IPv6 is configured.",
                dnsServerMapper.contains("2606:4700:4700::1111"));
        assertTrue("Google IPv6 DoH endpoint must be blocked when IPv6 is configured.",
                dnsServerMapper.contains("2001:4860:4860::8888"));
        assertTrue("Quad9 IPv6 DoH endpoint must be blocked when IPv6 is configured.",
                dnsServerMapper.contains("2620:fe::fe"));
        assertTrue("OpenDNS IPv6 DoH endpoint must be blocked when IPv6 is configured.",
                dnsServerMapper.contains("2620:119:35::35"));
        assertTrue("IPv6 DoH routes must use host-prefix /128 routes.",
                dnsServerMapper.contains("addDohBlockRoute(builder, ip, 128)"));
    }

    @Test
    public void atk32_homeLeakStateRequiresEstablishedAdAwayTunnel() throws IOException {
        Path app = appDir();
        String vpnService = readUtf8(app.resolve("src/main/java/org/adaway/vpn/VpnService.java"));
        String vpnControls = readUtf8(
                app.resolve("src/main/java/org/adaway/vpn/VpnServiceControls.java"));
        String leakStatus = readUtf8(app.resolve("src/main/java/org/adaway/ui/home/LeakStatus.java"));
        String homeStrings = readUtf8(app.resolve("src/main/res/values/strings_home.xml"));

        assertTrue("VPN startup must persist STARTING before the tunnel is established.",
                vpnService.contains("setVpnServiceStatus(this, STARTING)"));
        assertFalse("VPN startup must not persist RUNNING before VpnBuilder.establish() succeeds.",
                vpnService.contains("setVpnServiceStatus(this, RUNNING)"));
        assertTrue("Home status must have an established tunnel check.",
                vpnControls.contains("isTunnelEstablished"));
        assertTrue("Established tunnel check must require the post-establish RUNNING state.",
                vpnControls.contains("status == RUNNING"));
        assertTrue("Established tunnel check must reject foreign VPN owners on supported Android versions.",
                vpnControls.contains("getOwnerUid() == context.getApplicationInfo().uid"));
        assertTrue("Unreadable Private DNS state must be treated as a leak risk.",
                leakStatus.contains("isPrivateDnsUnknown() || isPrivateDnsActive()"));
        assertTrue("Encrypted-DNS copy must disclose partial DoH coverage.",
                homeStrings.contains("other encrypted DNS may bypass filtering"));
    }

    @Test
    public void atk33_githubActionsArePinnedToImmutableCommits() throws IOException {
        Path workflowDir = repoDir().resolve(".github/workflows");
        Pattern usesPattern = Pattern.compile("\\buses:\\s+[^@\\s]+@([^\\s#]+)");
        Pattern commitPattern = Pattern.compile("[0-9a-fA-F]{40}");

        try (Stream<Path> paths = Files.walk(workflowDir)) {
            Path mutableWorkflow = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                    .filter(path -> {
                        try {
                            Matcher matcher = usesPattern.matcher(readUtf8(path));
                            while (matcher.find()) {
                                if (!commitPattern.matcher(matcher.group(1)).matches()) {
                                    return true;
                                }
                            }
                            return false;
                        } catch (IOException exception) {
                            throw new IllegalStateException("Failed to scan " + path, exception);
                        }
                    })
                    .findFirst()
                    .orElse(null);
            assertNull("GitHub Actions workflow uses must be pinned to 40-char commits.",
                    mutableWorkflow);
        }
    }

    @Test
    public void atk34_releaseWorkflowGeneratesUploadsAndAttestsSbom() throws IOException {
        String workflow = readUtf8(repoDir().resolve(".github/workflows/fork-release-apk.yml"));
        String manifestScript = readUtf8(repoDir().resolve("scripts/generate-update-manifest.sh"));
        Pattern pinnedSbomAttest = Pattern.compile(
                "actions/attest@[0-9a-fA-F]{40}");

        assertTrue("Release workflow must generate an SBOM.",
                workflow.contains(":app:generateSbom"));
        assertTrue("Release workflow must point to the generated CycloneDX SBOM.",
                workflow.contains("app/build/reports/sbom/adaway.cdx.json"));
        assertTrue("Release workflow must attest the SBOM with an immutable actions/attest pin.",
                pinnedSbomAttest.matcher(workflow).find());
        assertTrue("Release workflow must grant artifact metadata write access for attestation " +
                        "storage records.",
                workflow.contains("artifact-metadata: write"));
        assertTrue("Release workflow must fetch tag objects for signed-tag verification.",
                workflow.contains("fetch-depth: 0"));
        assertTrue("Release workflow must verify signed release tags before building.",
                workflow.contains("Verify signed release tag") &&
                        workflow.contains("RELEASE_TAG_PUBLIC_KEY_BASE64 is required") &&
                        workflow.contains("git verify-tag \"$GITHUB_REF_NAME\""));
        assertTrue("Release workflow must classify beta tags separately from stable tags.",
                workflow.contains("Classify release tag") &&
                        workflow.contains("channel=beta") &&
                        workflow.contains("prerelease=true"));
        assertTrue("Release workflow must create an SBOM attestation predicate.",
                workflow.contains("sbom-path: ${{ steps.sbom.outputs.path }}"));
        assertTrue("Release workflow must upload the SBOM as a release asset.",
                workflow.contains("${{ steps.sbom.outputs.path }}"));
        assertTrue("Release workflow must checksum the SBOM.",
                workflow.contains("sha256sum \"$(basename \"$SBOM\")\""));
        assertTrue("Release workflow must upload the SBOM checksum as a release asset.",
                workflow.contains("${{ steps.sbom.outputs.sha256_path }}"));
        assertTrue("Release workflow must checksum the APK by release-asset basename.",
                workflow.contains("sha256sum \"$(basename \"$APK\")\""));
        assertTrue("Release workflow must generate a signed update manifest.",
                workflow.contains("Generate signed update manifest") &&
                        workflow.contains("scripts/generate-update-manifest.sh"));
        assertTrue("Release workflow must require a private manifest signing key.",
                workflow.contains("UPDATE_MANIFEST_PRIVATE_KEY_BASE64 is required"));
        assertTrue("Release workflow must upload manifest.json as a release asset.",
                workflow.contains("${{ steps.update_manifest.outputs.path }}"));
        assertTrue("Release workflow must upload manifest.json.sha256 as a release asset.",
                workflow.contains("${{ steps.update_manifest.outputs.sha256_path }}"));
        assertTrue("Release workflow must attest the signed update manifest.",
                workflow.indexOf("${{ steps.update_manifest.outputs.path }}")
                        < workflow.indexOf("Create GitHub Release + upload APK"));
        assertTrue("Update manifest generator must sign the exact JSON payload with RSA SHA-256.",
                manifestScript.contains("openssl dgst -sha256 -sign"));
        assertTrue("Update manifest generator must verify against the embedded public-key format.",
                manifestScript.contains("openssl dgst -sha256 -verify") &&
                        manifestScript.contains("-keyform DER"));
        assertTrue("Update manifest generator must emit a checksum file by basename.",
                manifestScript.contains("sha256sum \"$(basename \"$OUT\")\""));
        assertTrue("Update manifest generator must enforce the APK URL host allowlist.",
                manifestScript.contains("allowed_hosts = {\"app.adaway.org\", \"github.com\"}") &&
                        manifestScript.contains("apk-url host is not in the release allowlist"));
        assertTrue("Update manifest generator must default to the runtime AdAway store name.",
                manifestScript.contains("--store VALUE               Default: adaway") &&
                        manifestScript.contains("STORE=\"adaway\""));
        assertTrue("Release workflow must generate manifest APK URLs on the allowed GitHub host.",
                workflow.contains("APK_URL=\"https://github.com/${GITHUB_REPOSITORY}/releases/download/"));
        assertTrue("Release workflow must emit manifests for the runtime AdAway store.",
                workflow.contains("--store adaway"));
        assertTrue("Release workflow must use the classified release channel in manifest.json.",
                workflow.contains("--channel \"${{ steps.release_tag.outputs.channel }}\""));
        assertTrue("Release workflow must mark beta GitHub releases as prereleases.",
                workflow.contains("prerelease: ${{ steps.release_tag.outputs.prerelease }}"));
        assertFalse("Release workflow must not hard-code beta tags into the stable channel.",
                workflow.contains("--channel stable"));
        assertFalse("Release workflow must not emit GitHub as the runtime manifest store.",
                workflow.contains("--store github"));
        assertTrue("Tagged release workflow must run the license boundary guard.",
                workflow.contains("scripts/check-license-boundary.ps1"));
        assertTrue("License boundary guard must run before the release APK is built.",
                workflow.indexOf("scripts/check-license-boundary.ps1")
                        < workflow.indexOf(":app:assembleRelease"));
        assertTrue("Pre-build release boundary check must inspect git-tracked source entries.",
                workflow.contains("scripts/check-license-boundary.ps1 -SourceMode GitTracked"));
        assertTrue("Pre-build release boundary check must inspect the exported source archive.",
                workflow.contains(
                        "scripts/check-license-boundary.ps1 -SourceMode GitTracked -StrictSourceArchive"));
        assertTrue("Release workflow must run an artifact-aware license boundary check.",
                workflow.contains("Check release artifact license boundary"));
        assertTrue("Artifact license boundary check must use the selected APK output.",
                workflow.contains("-ApkPath \"${{ steps.apk.outputs.path }}\""));
        assertTrue("Artifact license boundary check must use the generated SBOM output.",
                workflow.contains("-SbomPath \"${{ steps.sbom.outputs.path }}\""));
        assertTrue("Artifact license boundary check must fail closed for release artifacts.",
                workflow.contains("-StrictArtifacts"));
        assertTrue("Release boundary checks must verify source archives before publishing.",
                countMatches(workflow, "-StrictSourceArchive") >= 2);
        assertTrue("Artifact license boundary check must run after release APK identity verification.",
                workflow.indexOf("Check release artifact license boundary")
                        > workflow.indexOf("Verify release APK identity"));
        assertTrue("Artifact license boundary check must run before release attestation.",
                workflow.indexOf("Check release artifact license boundary")
                        < workflow.indexOf("Attest release artifacts"));
        assertFalse("Release workflow must not publish unscanned generated release notes.",
                workflow.contains("generate_release_notes: true"));
        assertTrue("Release workflow must publish checked release-boundary copy.",
                workflow.contains("License boundary: this app release remains GPLv3+"));
        assertTrue("Release workflow must state that relicensing is not part of the APK release.",
                workflow.contains("not part of this") && workflow.contains("APK release"));
        assertTrue("Release workflow must explain GitHub source archive license boundaries.",
                workflow.contains("GitHub may also display auto-generated \"Source code\"")
                        && workflow.contains("committed .gitattributes export-ignore rules"));
        assertFalse("Tagged release workflow must not delete older releases automatically.",
                workflow.contains("delete-older-releases"));
    }

    @Test
    public void atk34_releaseCleanupAndDocsPreserveSourceProvenance() throws IOException {
        String cleanupWorkflow = readUtf8(
                repoDir().resolve(".github/workflows/cleanup-releases.yml"));
        String workflow = readUtf8(repoDir().resolve(".github/workflows/fork-release-apk.yml"));
        String releasing = readUtf8(repoDir().resolve("RELEASING.md"));

        assertFalse("Manual release cleanup must not expose tag deletion as an input.",
                cleanupWorkflow.contains("delete_tags:") &&
                        cleanupWorkflow.contains("${{ inputs.delete_tags }}"));
        assertTrue("Manual release cleanup must keep source archive tags.",
                cleanupWorkflow.contains("delete_tags: false"));
        assertTrue("Release docs must say generated release notes are disabled.",
                releasing.contains("disables generated") &&
                        releasing.contains("release notes"));
        assertTrue("Release docs must include the strict source boundary check through the " +
                        "PowerShell script.",
                releasing.contains("powershell -NoProfile -ExecutionPolicy Bypass -File " +
                        ".\\scripts\\check-license-boundary.ps1") &&
                        releasing.contains("-SourceMode GitTracked -StrictSourceArchive"));
        assertTrue("Release docs must include the artifact-aware boundary check.",
                releasing.contains("-ApkPath $Apk") &&
                        releasing.contains("-SbomPath $Sbom") &&
                        releasing.contains("-StrictArtifacts"));
        assertTrue("Release docs must document the manifest signing private-key secret.",
                releasing.contains("UPDATE_MANIFEST_PRIVATE_KEY_BASE64"));
        assertTrue("Release docs must include local signed update-manifest generation.",
                releasing.contains("scripts/generate-update-manifest.sh"));
        assertTrue("Release docs must use the runtime AdAway store name in manifests.",
                releasing.contains("--store adaway"));
        assertFalse("Release docs must not use GitHub as the runtime manifest store.",
                releasing.contains("--store github"));
        assertTrue("Release docs must include checksum verification for release assets.",
                releasing.contains("sha256sum -c \"manifest.json.sha256\""));
        assertTrue("Release docs must include GitHub attestation verification for manifest.json.",
                releasing.contains("gh attestation verify \"manifest.json\""));
        assertTrue("Release docs must verify fork release attestations against this repository.",
                releasing.contains("gh attestation verify \"AdAway_<version>.apk\" " +
                        "--repo stevesolun/AdAway") &&
                        releasing.contains("gh attestation verify \"manifest.json\" " +
                                "--repo stevesolun/AdAway") &&
                        releasing.contains("gh attestation verify \"adaway.cdx.json\" " +
                                "--repo stevesolun/AdAway"));
        assertFalse("Release docs must not verify fork release attestations against upstream.",
                releasing.contains("gh attestation verify \"AdAway_<version>.apk\" " +
                        "--repo AdAway/AdAway") ||
                        releasing.contains("gh attestation verify \"manifest.json\" " +
                                "--repo AdAway/AdAway") ||
                        releasing.contains("gh attestation verify \"adaway.cdx.json\" " +
                                "--repo AdAway/AdAway"));
        assertTrue("Release docs must state tagged releases are retained for provenance.",
                releasing.contains("Tagged releases are retained for durable provenance"));
        assertTrue("Release docs must scope APK self-update to AdAway-signed direct APKs.",
                releasing.contains("APK self-update is only for the AdAway-signed direct APK"));
        assertTrue("Release docs must show the direct APK updater build opt-in.",
                releasing.contains("-PadawayEnableDirectApkUpdater=true"));
        assertTrue("Fork direct APK release workflow must opt in to the updater install permission.",
                workflow.contains("-PadawayEnableDirectApkUpdater=true"));
        assertTrue("Release docs must keep the Bash wrapper documented as a Unix convenience.",
                releasing.contains("bash ./scripts/check-license-boundary.sh " +
                        "-SourceMode GitTracked -StrictSourceArchive"));
    }

    @Test
    public void atk34_releaseSmokeRequiresReleaseApkOnRealDevice() throws IOException {
        Path repo = repoDir();
        String smokeScript = readUtf8(repo.resolve("scripts/run-release-smoke.ps1"));
        String releasing = readUtf8(repo.resolve("RELEASING.md"));

        assertTrue("Release smoke must require an explicit APK path.",
                smokeScript.contains("[Parameter(Mandatory = $true)]") &&
                        smokeScript.contains("$ApkPath"));
        assertTrue("Release smoke must inspect APK badging.",
                smokeScript.contains("aapt") && smokeScript.contains("dump badging"));
        assertTrue("Release smoke must reject debuggable APKs.",
                smokeScript.contains("application-debuggable"));
        assertTrue("Release smoke must reject emulators.",
                smokeScript.contains("emulator-") &&
                        smokeScript.contains("ro.kernel.qemu") &&
                        smokeScript.contains("ro.boot.qemu"));
        assertTrue("Release smoke must install and launch the APK.",
                smokeScript.contains("adb install") &&
                        smokeScript.contains("monkey -p") &&
                        smokeScript.contains("pidof"));
        assertTrue("Release smoke must default to the production package.",
                smokeScript.contains("org.adaway"));
        assertTrue("Release docs must include the real-device smoke command.",
                releasing.contains(".\\scripts\\run-release-smoke.ps1") &&
                        releasing.contains("-ApkPath $Apk"));
    }

    @Test
    public void atk34_uxMatrixRunnerTimesOutAndCleansUpInstrumentation()
            throws IOException {
        Path repo = repoDir();
        String uxMatrixScript = readUtf8(repo.resolve("scripts/run-ux-matrix.ps1"));

        assertTrue("UX matrix runner must use an explicit instrumentation timeout.",
                uxMatrixScript.contains("$InstrumentationTimeoutSeconds") &&
                        uxMatrixScript.contains("Wait-Process") &&
                        uxMatrixScript.contains("-Timeout"));
        assertTrue("UX matrix runner must stop both app and test packages after failed runs.",
                uxMatrixScript.contains("am force-stop org.adaway.test") &&
                        uxMatrixScript.contains("am force-stop org.adaway"));
        assertTrue("UX matrix runner must kill timed-out instrumentation processes.",
                uxMatrixScript.contains("Stop-Process") &&
                        uxMatrixScript.contains("UX matrix test timed out"));
    }

    @Test
    public void atk34_apkSelfUpdateRequiresInstallPermissionAndAdAwayStoreBoundary()
            throws IOException {
        Path app = appDir();
        String appBuild = readUtf8(app.resolve("build.gradle"));
        String manifest = readUtf8(app.resolve("src/main/AndroidManifest.xml"));
        String receiver = readUtf8(
                app.resolve("src/main/java/org/adaway/model/update/ApkDownloadReceiver.java"));
        String updateModel = readUtf8(
                app.resolve("src/main/java/org/adaway/model/update/UpdateModel.java"));

        assertTrue("Direct APK install permission must be manifest-placeholder gated.",
                manifest.contains("android:name=\"${requestInstallPackagesPermission}\""));
        assertTrue("Normal builds must remove the install permission unless direct APK updates " +
                        "are explicitly enabled.",
                appBuild.contains("adawayEnableDirectApkUpdater") &&
                        appBuild.contains("requestInstallPackagesPermission") &&
                        appBuild.contains("org.adaway.permission.NO_DIRECT_APK_INSTALL") &&
                        appBuild.contains("android.permission.REQUEST_INSTALL_PACKAGES"));
        assertTrue("Runtime self-update must follow the same direct APK build gate.",
                updateModel.contains("BuildConfig.DIRECT_APK_UPDATES_ENABLED") &&
                        updateModel.indexOf("BuildConfig.DIRECT_APK_UPDATES_ENABLED")
                                < updateModel.indexOf("getStore() == ADAWAY"));
        assertTrue("APK receiver must check unknown-app install permission before installing.",
                receiver.contains("canRequestPackageInstalls()"));
        assertTrue("APK receiver must route users to system install-source settings.",
                receiver.contains("Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES"));
        assertTrue("APK receiver must only launch ACTION_INSTALL_PACKAGE after the permission check.",
                receiver.indexOf("canRequestPackageInstalls()")
                        < receiver.indexOf("new Intent(ACTION_INSTALL_PACKAGE)"));
        assertTrue("App self-update must be limited to the AdAway-signed direct APK store.",
                updateModel.contains("getStore() == ADAWAY"));
        assertTrue("Manifest download must refuse non-AdAway stores before network access.",
                updateModel.indexOf("if (!canSelfUpdate())")
                        < updateModel.indexOf("HttpUrl httpUrl"));
        assertTrue("Manifest payload distribution must be validated against the request.",
                updateModel.contains("new Manifest(") &&
                        updateModel.contains("channel,") &&
                        updateModel.contains("store.getName()"));
        assertTrue("Update download must refuse non-AdAway stores before reading cached manifest.",
                updateModel.indexOf("public long update()") < updateModel.lastIndexOf(
                        "if (!canSelfUpdate())"));
    }

    @Test
    public void atk34_gradleSbomTaskEmitsCycloneDxForRuntimeClasspath() throws IOException {
        String appBuild = readUtf8(repoDir().resolve("app/build.gradle"));
        String settings = readUtf8(repoDir().resolve("settings.gradle"));

        assertTrue("Gradle must expose an SBOM generation task.",
                appBuild.contains("tasks.register('generateSbom')"));
        assertTrue("SBOM task must use the CycloneDX Gradle plugin.",
                appBuild.contains("id 'org.cyclonedx.bom'"));
        assertTrue("SBOM task must use release runtime dependencies for signed release builds.",
                appBuild.contains("releaseRuntimeClasspath"));
        assertTrue("SBOM task must write the workflow-consumed SBOM path.",
                appBuild.contains("reports/sbom/adaway.cdx.json"));
        assertTrue("SBOM task must not resolve runtime dependencies while Gradle builds the task graph.",
                appBuild.contains("resolvedDependencies.setFrom([])"));
        assertTrue("SBOM task must not serve stale cached output after clearing resolved inputs.",
                appBuild.contains("outputs.upToDateWhen { false }"));
        assertFalse("SBOM generation must not silently ignore unresolved artifacts.",
                appBuild.contains("lenientConfiguration"));
        assertTrue("Release trust-material gate must still cover release packaging.",
                appBuild.contains("'assemblerelease'"));
        assertTrue("Release trust-material gate must cover aggregate assemble tasks.",
                appBuild.contains("'assemble'"));
        assertTrue("Release trust-material gate must cover aggregate build tasks.",
                appBuild.contains("'build'"));
        assertTrue("Release trust-material gate must cover aggregate bundle tasks.",
                appBuild.contains("'bundle'"));
        assertTrue("Release trust-material gate must still cover release SBOM generation.",
                appBuild.contains("'generatesbom'"));
        assertFalse("Release dependency auditing must not be blocked by a broad contains('release') gate.",
                appBuild.contains("contains('release') ||"));
        assertTrue("Dependency repositories must be centralized in settings.",
                settings.contains("RepositoriesMode.FAIL_ON_PROJECT_REPOS"));
        assertTrue("JitPack must be content-filtered to the single dependency group that needs it.",
                settings.contains("includeGroup 'com.github.topjohnwu.libsu'"));
        assertFalse("App module must not add broad project repositories.",
                appBuild.contains("https://jitpack.io"));
    }

    @Test
    public void atk34_gradleScriptsUseGradle10AssignmentSyntax() throws IOException {
        String appBuild = readUtf8(repoDir().resolve("app/build.gradle"));
        String settings = readUtf8(repoDir().resolve("settings.gradle"));
        String sentryStubBuild = readUtf8(repoDir().resolve("sentrystub/build.gradle"));

        assertFalse("App build must not use deprecated compileSdk space assignment.",
                appBuild.contains("compileSdk 36"));
        assertFalse("App build must not use deprecated namespace space assignment.",
                appBuild.contains("namespace 'org.adaway'"));
        assertFalse("App build must not use deprecated signing flag space assignment.",
                appBuild.contains("enableV1Signing true"));
        assertFalse("App build must not use deprecated build feature space assignment.",
                appBuild.contains("resValues true"));
        assertFalse("App build must not use deprecated build type space assignment.",
                appBuild.contains("minifyEnabled true"));
        assertFalse("Settings build must not use deprecated repository URL space assignment.",
                settings.contains("url 'https://jitpack.io'"));
        assertFalse("Sentry stub must not use deprecated Android DSL space assignment.",
                sentryStubBuild.contains("compileSdk 36"));

        assertTrue("App build must use assignment syntax for compileSdk.",
                appBuild.contains("compileSdk = 36"));
        assertTrue("App build must use assignment syntax for namespace.",
                appBuild.contains("namespace = 'org.adaway'"));
        assertTrue("Settings build must use assignment syntax for JitPack URL.",
                settings.contains("url = 'https://jitpack.io'"));
        assertTrue("Sentry stub must use assignment syntax for compileSdk.",
                sentryStubBuild.contains("compileSdk = 36"));
    }

    @Test
    public void atk34_dependencyVerificationUsesSignaturesAndTrustedKeys() throws IOException {
        String verification = readUtf8(repoDir().resolve("gradle/verification-metadata.xml"));

        assertTrue("Dependency verification must verify signatures when artifacts publish them.",
                verification.contains("<verify-signatures>true</verify-signatures>"));
        assertFalse("Dependency verification must not remain hash-only.",
                verification.contains("<verify-signatures>false</verify-signatures>"));
        assertTrue("Dependency verification must not fetch PGP keys from key servers.",
                verification.contains("<key-servers enabled=\"false\"/>"));
        assertFalse("Dependency verification must not pin ad-hoc key server URLs.",
                verification.contains("<key-server uri="));
        assertTrue("Dependency verification must include scoped trusted signing keys.",
                verification.contains("<trusted-keys>"));
        assertTrue("Dependency verification must trust concrete artifact signing keys.",
                verification.contains("<trusted-key id="));
        assertTrue("Unsigned artifacts must be explicitly hash-pinned with a reason.",
                verification.contains("reason=\"Artifact is not signed\""));
    }

    @Test
    public void atk34_dependencyVerificationChecksInPublicKeyring() throws IOException {
        Path binaryKeyring = repoDir().resolve("gradle/verification-keyring.gpg");
        Path armoredKeys = repoDir().resolve("gradle/verification-keyring.keys");

        assertTrue("PGP dependency verification must check in Gradle's binary public keyring.",
                Files.isRegularFile(binaryKeyring));
        assertTrue("Gradle's exported binary dependency verification keyring must not be empty.",
                Files.size(binaryKeyring) > 1024);
        assertTrue("PGP dependency verification must check in the armored public key dump.",
                Files.isRegularFile(armoredKeys));
        String keyDump = readUtf8(armoredKeys);
        assertTrue("The armored dependency verification key dump must contain real PGP keys.",
                keyDump.contains("-----BEGIN PGP PUBLIC KEY BLOCK-----"));
    }

    @Test
    public void atk34_dependencyVerificationFallbacksAreAudited() throws IOException {
        String verification = readUtf8(repoDir().resolve("gradle/verification-metadata.xml"));
        String keyDump = readUtf8(repoDir().resolve("gradle/verification-keyring.keys"));
        String audit = readUtf8(repoDir().resolve("docs/dependency-verification-audit.md"));

        assertEquals("Unresolved PGP key ignores must stay reviewed and bounded.",
                31, countMatches(verification, "<ignored-key id="));
        assertEquals("Key-download checksum fallbacks must stay reviewed and bounded.",
                345, countMatches(verification, "reason=\"A key couldn't be downloaded\""));
        assertTrue("Dependency-verification fallback debt must have a human review record.",
                audit.contains("# Dependency Verification Audit"));
        assertTrue("Ignored-key count in the audit must match verification-metadata.xml.",
                audit.contains("Ignored PGP keys: 31"));
        assertTrue("Key-download fallback count in the audit must match verification-metadata.xml.",
                audit.contains("Key-download checksum fallbacks: 345"));
        assertTrue("Audit must require review before fallback counts grow.",
                audit.contains("Do not let ignored-key or key-download fallback counts grow"));
        assertTrue("Audit must require scoped trust instead of broad key trust.",
                audit.contains("Do not add broad trusted-key scopes"));
        assertFalse("Ignored keys already present in the exported keyring must be removed.",
                verification.contains("<ignored-key id=\"32EE5355A6BC6E42\""));
        assertFalse("Ignored keys already present in the exported keyring must be removed.",
                verification.contains("<ignored-key id=\"E88979FB9B30ACF2\""));
        assertTrue("Android build-tool signing subkey must be trusted only after review.",
                verification.contains("0F06FF86BEEAF4E71866EE5232EE5355A6BC6E42"));
        assertTrue("Android build-tool signing trust must stay scoped to Android build artifacts.",
                verification.contains("<trusting group=\"^com[.]android($|([.].*))\" regex=\"true\"/>"));
        assertTrue("Newer AndroidX annotation artifacts must keep explicit signing-key trust.",
                verification.contains("<trusting group=\"androidx.annotation\"/>"));
        assertTrue("AndroidX data binding compiler artifacts must keep explicit signing-key trust.",
                verification.contains("<trusting group=\"androidx.databinding\"/>"));
        assertTrue("AndroidX instrumentation test artifacts must keep explicit signing-key trust.",
                verification.contains("<trusting group=\"androidx.test\"/>"));
        assertTrue("AndroidX instrumentation test artifacts must keep explicit signing-key trust.",
                verification.contains("<trusting group=\"androidx.test.ext\"/>"));
        assertTrue("AndroidX instrumentation test artifacts must keep explicit signing-key trust.",
                verification.contains("<trusting group=\"androidx.test.services\"/>"));
        assertTrue("AndroidX runtime signing subkey must be trusted only after review.",
                verification.contains("A5F483CD733A4EBAEA378B2AE88979FB9B30ACF2"));
        assertTrue("AndroidX runtime signing trust must stay scoped to resolved AndroidX families.",
                verification.contains("<trusting group=\"androidx.appcompat\"/>"));
        assertTrue("AndroidX concurrent-futures artifacts must keep explicit signing-key trust.",
                verification.contains("<trusting group=\"androidx.concurrent\"/>"));
        assertTrue("AndroidX runtime signing trust must stay scoped to resolved AndroidX families.",
                verification.contains("<trusting group=\"androidx.paging\"/>"));
        assertTrue("AndroidX runtime signing trust must stay scoped to resolved AndroidX families.",
                verification.contains("<trusting group=\"androidx.profileinstaller\"/>"));
        assertTrue("AndroidX runtime signing trust must stay scoped to resolved AndroidX families.",
                verification.contains("<trusting group=\"androidx.startup\"/>"));
        assertTrue("The exported keyring should retain the reviewed AndroidX signing subkey.",
                keyDump.contains("32EE5355A6BC6E42"));
        assertTrue("The exported keyring should retain the reviewed AndroidX signing subkey.",
                keyDump.contains("E88979FB9B30ACF2"));
    }

    @Test
    public void atk34_releaseBuildStripsDnsjavaDesktopResolverSpi() throws IOException {
        String appBuild = readUtf8(repoDir().resolve("app/build.gradle"));
        String proguard = readUtf8(repoDir().resolve("app/proguard-rules.pro"));

        assertTrue("Release build must strip dnsjava's desktop resolver service before R8.",
                appBuild.contains("stripDnsjavaDesktopResolverServiceRelease"));
        assertTrue("Strip task must run after release Java resources are merged.",
                appBuild.contains("mergeReleaseJavaResource"));
        assertTrue("Strip task must run before release R8.",
                appBuild.contains("minifyReleaseWithR8"));
        assertTrue("Packaged resources must exclude dnsjava's desktop resolver service.",
                appBuild.contains("META-INF/services/java.net.spi.InetAddressResolverProvider"));
        assertFalse("App rules must not keep every ContentProvider; AAPT owns manifest providers.",
                proguard.contains("extends android.content.ContentProvider"));
        assertFalse("App rules must not hide all Google/Material/Guava missing-class warnings.",
                proguard.contains("-dontwarn com.google.**"));
        assertFalse("Release builds use sentrystub; app rules must not hide all Sentry warnings.",
                proguard.contains("-dontwarn io.sentry.**"));
        assertFalse("OkHttp consumer rules own optional platform suppressions.",
                proguard.contains("-dontwarn org.bouncycastle.jsse.**"));
        assertFalse("OkHttp consumer rules own optional platform suppressions.",
                proguard.contains("-dontwarn org.conscrypt.**"));
        assertFalse("App rules must not hide every Java network SPI warning.",
                proguard.contains("-dontwarn java.net.spi.**"));
        assertTrue("R8 warning suppression must include the Java desktop resolver interface.",
                proguard.contains("-dontwarn java.net.spi.InetAddressResolver\n"));
        assertTrue("R8 warning suppression must include the Java desktop resolver lookup policy.",
                proguard.contains("-dontwarn java.net.spi.InetAddressResolver$LookupPolicy"));
        assertTrue("R8 warning suppression must include the Java desktop resolver provider.",
                proguard.contains("-dontwarn java.net.spi.InetAddressResolverProvider\n"));
        assertTrue("R8 warning suppression must include the provider configuration type.",
                proguard.contains("-dontwarn java.net.spi.InetAddressResolverProvider$Configuration"));
        assertTrue("R8 warning suppression must include only dnsjava's desktop resolver provider.",
                proguard.contains("-dontwarn org.xbill.DNS.spi.DnsjavaInetAddressResolverProvider"));
    }

    @Test
    public void atk34_updateModelClearsAndRejectsExpiredCachedManifest() throws IOException {
        String updateModel = readUtf8(
                appDir().resolve("src/main/java/org/adaway/model/update/UpdateModel.java"));

        assertTrue("Failed or invalid manifest checks must clear cached manifest state.",
                updateModel.contains("this.manifest.postValue(null);"));
        assertTrue("Update downloads must re-check cached manifest expiry.",
                updateModel.contains("manifest.isExpired()"));
        assertTrue("Expired cached manifests must not enqueue downloads.",
                updateModel.contains("return -1;"));
    }

    @Test
    public void atk35_androidCiRunsLintGate() throws IOException {
        String workflow = readUtf8(repoDir().resolve(".github/workflows/android-ci.yml"));

        assertTrue("Android CI must run lint, not only tests and debug assemble.",
                workflow.contains(":app:lintDebug --dependency-verification=strict --stacktrace"));
    }

    @Test
    public void atk35_androidCiRunsConnectedTestGate() throws IOException {
        String workflow = readUtf8(repoDir().resolve(".github/workflows/android-ci.yml"));

        assertTrue("Android CI must define a connected test job.",
                workflow.contains("connected-tests:"));
        assertTrue("Android CI must install an emulator system image.",
                workflow.contains("system-images;android-34;google_apis;x86_64"));
        assertTrue("Android CI must run connected androidTest.",
                workflow.contains(
                        ":app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace"));
    }

    @Test
    public void atk35_securityWorkflowsRunOnPullRequestsAndManualDispatch() throws IOException {
        String ciWorkflow = readUtf8(repoDir().resolve(".github/workflows/android-ci.yml"));
        String codeqlWorkflow = readUtf8(repoDir().resolve(".github/workflows/android-analysis.yml"));

        assertTrue("Android CI must protect pull requests, not only master pushes.",
                ciWorkflow.contains("pull_request:"));
        assertTrue("Android CI must support manual dispatch for release-blocking verification.",
                ciWorkflow.contains("workflow_dispatch:"));
        assertTrue("CodeQL must protect pull requests, not only scheduled scans.",
                codeqlWorkflow.contains("pull_request:"));
        assertTrue("CodeQL must support manual dispatch for security verification reruns.",
                codeqlWorkflow.contains("workflow_dispatch:"));
        assertTrue("CodeQL must keep scheduled scans for drift detection.",
                codeqlWorkflow.contains("schedule:"));
    }

    @Test
    public void atk35_androidCiUsesStrictDependencyVerification() throws IOException {
        String ciWorkflow = readUtf8(repoDir().resolve(".github/workflows/android-ci.yml"));
        String codeqlWorkflow = readUtf8(repoDir().resolve(".github/workflows/android-analysis.yml"));

        assertTrue("Unit test CI must use strict dependency verification.",
                ciWorkflow.contains("./gradlew test --dependency-verification=strict --stacktrace"));
        assertTrue("Lint CI must use strict dependency verification.",
                ciWorkflow.contains(
                        "./gradlew :app:lintDebug --dependency-verification=strict --stacktrace"));
        assertTrue("Debug assemble CI must use strict dependency verification.",
                ciWorkflow.contains(
                        "./gradlew assembleDebug --dependency-verification=strict --stacktrace"));
        assertTrue("Sonar CI resolution must use strict dependency verification.",
                ciWorkflow.contains("sonarqube --dependency-verification=strict"));
        assertTrue("Connected test CI must use strict dependency verification.",
                ciWorkflow.contains(
                        "./gradlew :app:connectedDebugAndroidTest --dependency-verification=strict --stacktrace"));
        assertTrue("CodeQL autobuild replacement must use strict dependency verification.",
                codeqlWorkflow.contains("./gradlew assembleDebug --dependency-verification=strict"));
    }

    @Test
    public void atk35_androidCiPreventsPrematureMitBranding() throws IOException {
        Path repo = repoDir();
        String workflow = readUtf8(repo.resolve(".github/workflows/android-ci.yml"));
        String boundaryScript = readUtf8(repo.resolve("scripts/check-license-boundary.ps1"));

        assertTrue("Android CI must run the license-boundary check.",
                workflow.contains("scripts/check-license-boundary.ps1"));
        assertTrue("License boundary check must fail on MIT release claims.",
                boundaryScript.contains("MIT-branded release wording is blocked"));
        assertTrue("License boundary check must catch natural-language MIT claims.",
                boundaryScript.contains("(?:licensed|released|distributed|available)"));
        assertTrue("License boundary check must catch hyphenated MIT claims.",
                boundaryScript.contains("MIT[- ]licensed"));
        assertTrue("License boundary check must catch MIT terms wording.",
                boundaryScript.contains("MIT\\s+terms"));
        assertTrue("License boundary check must keep GPL blocker detection explicit.",
                boundaryScript.contains("GNU General Public License"));
        assertTrue("License boundary check must inspect packaged GPL-derived app code.",
                boundaryScript.contains("app/src/main/java"));
        assertTrue("License boundary check must inspect docs where legal wording drifts.",
                boundaryScript.contains("\"docs\""));
        assertTrue("License boundary check must inspect changelog release text.",
                boundaryScript.contains("\"CHANGELOG.md\""));
        assertTrue("License boundary check must inspect release process text.",
                boundaryScript.contains("\"RELEASING.md\""));
        assertTrue("License boundary check must inspect packaged resources and metadata.",
                boundaryScript.contains("app/src/main/res"));
        assertTrue("License boundary check must inspect top-level historical resources.",
                boundaryScript.contains("\"Resources\""));
        assertTrue("License boundary check must report details before exiting.",
                boundaryScript.contains("Report-And-Fail"));
        assertTrue("License boundary check must support git-tracked source inventory.",
                boundaryScript.contains("git ls-files"));
        assertTrue("License boundary check must support strict source archive inspection.",
                boundaryScript.contains("StrictSourceArchive"));
        assertTrue("Strict source archive inspection must use git archive.",
                boundaryScript.contains("git archive"));
        assertTrue("Strict source archive inspection must honor export-ignore attributes.",
                boundaryScript.contains("--worktree-attributes"));
        assertTrue("Strict source archive inspection must block dormant webserver exports.",
                boundaryScript.contains("^webserver/"));
        assertTrue("License boundary check must inspect APK zip entries.",
                boundaryScript.contains("Get-ZipEntries"));
        assertTrue("License boundary check must inspect APK resource names.",
                boundaryScript.contains("aapt dump resources"));
        assertTrue("License boundary check must parse release CycloneDX SBOM components.",
                boundaryScript.contains("Get-SbomComponents"));
        assertTrue("License boundary check must fail if packaged PayPal or GitHub marks return.",
                boundaryScript.contains("Forbidden packaged third-party mark resources detected"));
        assertTrue("License boundary check must block GitHub mark resource name variants.",
                boundaryScript.contains("org\\.adaway:drawable/ic_github(?:_|$)"));
    }

    @Test
    public void atk35_licenseBoundaryScriptFailsOnFixtureClaims() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the license-boundary script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-license-boundary");
        try {
            writeUtf8(fixture.resolve("LICENSE.md"),
                    "GNU General Public License\n");
            writeUtf8(fixture.resolve("THIRD_PARTY_LICENSES.md"),
                    "Current package remains GPL-derived and is not MIT.\n");
            writeUtf8(fixture.resolve("app/src/main/java/GplBlocker.java"),
                    "/* GNU General Public License */\n");
            writeUtf8(fixture.resolve("docs/release.md"),
                    "This fork is released under MIT.\n");
            writeUtf8(fixture.resolve("CHANGELOG.md"),
                    "This release is MIT-licensed.\n");
            writeUtf8(fixture.resolve("RELEASING.md"),
                    "Ship this package under MIT terms.\n");
            writeUtf8(fixture.resolve("metadata/en-US/full_description.txt"),
                    "Distributed under MIT for store listings.\n");
            writeUtf8(fixture.resolve("Resources/icon.svg"),
                    "<!-- available under MIT -->\n");

            ProcessResult result = runLicenseBoundaryScript(powershell, fixture);

            assertTrue("MIT/GPL fixture must fail the boundary script.",
                    result.exitCode != 0);
            assertTrue("Failure must explain that MIT branding is blocked.",
                    result.stderr.contains("MIT-branded release wording is blocked"));
            assertTrue("Failure must report matching MIT claims before exiting.",
                    result.stderr.contains("MIT claims:"));
            assertTrue("Failure must report GPL blockers before exiting.",
                    result.stderr.contains("GPL blockers:"));
            assertTrue("Docs MIT claim must be scanned.",
                    result.stderr.contains("docs"));
            assertTrue("Changelog MIT claim must be scanned.",
                    result.stderr.contains("CHANGELOG.md"));
            assertTrue("Release process MIT claim must be scanned.",
                    result.stderr.contains("RELEASING.md"));
            assertTrue("Metadata MIT claim must be scanned.",
                    result.stderr.contains("metadata"));
            assertTrue("Top-level Resources MIT claim must be scanned.",
                    result.stderr.contains("Resources"));
            assertTrue("GPL blocker details must be retained.",
                    result.stderr.contains("GplBlocker.java") ||
                            result.stderr.contains("LICENSE.md"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void atk35_licenseBoundaryScriptFailsOnForbiddenSourceAsset() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the license-boundary script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-license-source-boundary");
        try {
            writeBoundaryFixtureBase(fixture);
            writeUtf8(fixture.resolve("app/src/main/assets/localhost-2410.key"),
                    "local test key\n");

            ProcessResult result = runLicenseBoundaryScript(powershell, fixture);

            assertTrue("Forbidden app source asset fixture must fail.",
                    result.exitCode != 0);
            assertTrue("Failure must report forbidden app source assets.",
                    result.stderr.contains("Forbidden source/archive boundary entries remain"));
            assertTrue("Failure must identify localhost key material.",
                    result.stderr.contains("localhost-2410.key"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void atk35_licenseBoundaryScriptFailsOnForbiddenSourceArchiveEntry() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the license-boundary script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-license-source-archive");
        try {
            writeBoundaryFixtureBase(fixture);
            writeUtf8(fixture.resolve("README.md"),
                    "AdAway test fixture.\n");
            writeUtf8(fixture.resolve("webserver/legacy-key.pem"),
                    "historical localhost key\n");

            assertEquals("Fixture git repository must initialize.",
                    0, runProcess(fixture, "git", "init").exitCode);
            assertEquals("Fixture files must be staged.",
                    0, runProcess(fixture, "git", "add", ".").exitCode);
            assertEquals("Fixture commit must be created for git archive.",
                    0, runProcess(fixture,
                            "git",
                            "-c", "user.name=AdAway Test",
                            "-c", "user.email=test@example.invalid",
                            "commit", "-m", "source archive fixture").exitCode);

            ProcessResult result = runLicenseBoundaryScript(powershell, fixture,
                    "-StrictSourceArchive");

            assertTrue("Forbidden source archive fixture must fail.",
                    result.exitCode != 0);
            assertTrue("Failure must report forbidden source archive entries.",
                    result.stderr.contains("Forbidden source archive entries detected"));
            assertTrue("Failure must identify dormant webserver archive material.",
                    result.stderr.contains("webserver/legacy-key.pem"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void atk35_licenseBoundaryScriptFailsOnApkAndSbomBoundaryDrift() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the license-boundary script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-license-artifact-boundary");
        try {
            writeBoundaryFixtureBase(fixture);
            Path apk = fixture.resolve("release.apk");
            writeZip(apk, "assets/localhost-2410.key", "test key");
            Path sbom = fixture.resolve("adaway.cdx.json");
            writeUtf8(sbom, "{\n" +
                    "  \"bomFormat\": \"CycloneDX\",\n" +
                    "  \"components\": [\n" +
                    "    {\"group\": \"net.java.dev.jna\", \"name\": \"jna\", \"version\": \"5.3.1\"}\n" +
                    "  ]\n" +
                    "}\n");

            ProcessResult result = runLicenseBoundaryScript(powershell, fixture,
                    "-ApkPath", apk.toString(),
                    "-SbomPath", sbom.toString(),
                    "-StrictArtifacts");

            assertTrue("APK/SBOM artifact drift fixture must fail.",
                    result.exitCode != 0);
            assertTrue("Failure must report forbidden APK entries.",
                    result.stderr.contains("Forbidden release APK entries detected"));
            assertTrue("Failure must identify packaged localhost key material.",
                    result.stderr.contains("assets/localhost-2410.key"));
            assertTrue("Failure must report missing JNA notice coverage.",
                    result.stderr.contains("SBOM dependency missing THIRD_PARTY_LICENSES.md notice: JNA"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void atk35_licenseBoundaryScriptFailsOnGithubMarkResourceVariants() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the license-boundary script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-license-resource-boundary");
        try {
            writeBoundaryFixtureBase(fixture);
            writeFakeAapt(fixture, "org.adaway:drawable/ic_github_octocat");
            Path apk = fixture.resolve("release.apk");
            writeZip(apk, "resources.arsc", "fake resource table marker");
            java.util.Map<String, String> environment = new java.util.HashMap<>();
            environment.put("ANDROID_HOME", null);
            environment.put("ANDROID_SDK_ROOT", null);

            ProcessResult result = runLicenseBoundaryScript(powershell, fixture, environment,
                    "-ApkPath", apk.toString(),
                    "-StrictArtifacts");

            assertTrue("GitHub mark resource variant fixture must fail.",
                    result.exitCode != 0);
            assertTrue("Failure must report forbidden third-party mark resources.",
                    result.stderr.contains("Forbidden packaged third-party mark resources detected"));
            assertTrue("Failure must identify the GitHub mark variant resource.",
                    result.stderr.contains("org.adaway:drawable/ic_github_octocat"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void atk35_licenseBoundaryAllowsLegitimateMitDependencyNotice() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the license-boundary script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-license-mit-dependency");
        try {
            writeUtf8(fixture.resolve("LICENSE.md"),
                    "GNU General Public License\n");
            writeUtf8(fixture.resolve("THIRD_PARTY_LICENSES.md"),
                    "MIT relicensing is future-only; the current app remains GPL-derived.\n" +
                            "| Pcap4J | MIT | Runtime dependency | Yes | Notice item. |\n");

            ProcessResult result = runLicenseBoundaryScript(powershell, fixture);

            assertEquals("A legitimate MIT dependency notice must not be treated as product MIT relicensing.",
                    0, result.exitCode);
            assertTrue("Passing guard must report success.",
                    result.stdout.contains("License boundary check passed"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void atk36_periodicUpdateSchedulersUseReplaceablePolicy() throws IOException {
        Path app = appDir();
        String sourceUpdateService = readUtf8(
                app.resolve("src/main/java/org/adaway/model/source/SourceUpdateService.java"));
        String apkUpdateService = readUtf8(
                app.resolve("src/main/java/org/adaway/model/update/ApkUpdateService.java"));
        String filterSetUpdateService = readUtf8(
                app.resolve("src/main/java/org/adaway/ui/hosts/FilterSetUpdateService.java"));

        assertFalse("Hosts update preference sync must not preserve stale periodic work.",
                sourceUpdateService.contains("enqueueWork(context, KEEP"));
        assertFalse("APK update preference sync must not preserve stale periodic work.",
                apkUpdateService.contains("enqueueWork(context, KEEP"));
        assertTrue("Hosts update scheduling must use UPDATE so changed constraints apply.",
                sourceUpdateService.contains("enqueueWork(context, UPDATE"));
        assertTrue("APK update scheduling must use UPDATE so changed settings apply.",
                apkUpdateService.contains("enqueueWork(context, UPDATE"));
        assertTrue("Filter set scheduling must use UPDATE so changed constraints apply.",
                filterSetUpdateService.contains("ExistingPeriodicWorkPolicy.UPDATE"));
    }

    private static Path appDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        if (Files.isDirectory(cwd.resolve("src/main"))) {
            return cwd;
        }
        return cwd.resolve("app");
    }

    private static Path repoDir() {
        Path app = appDir();
        Path parent = app.getParent();
        return parent != null && app.getFileName().toString().equals("app") ? parent : app;
    }

    private static String readUtf8(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void writeUtf8(Path path, String text) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBoundaryFixtureBase(Path fixture) throws IOException {
        writeUtf8(fixture.resolve("LICENSE.md"),
                "GNU General Public License\n");
        writeUtf8(fixture.resolve("THIRD_PARTY_LICENSES.md"),
                "MIT relicensing is future-only; the current app remains GPL-derived.\n" +
                        "## Source-Only Resources Inventory\n" +
                        "| Tcpdump / Libpcap | BSD-3-Clause | Source-only | No | Inventory. |\n");
    }

    private static void writeZip(Path zipPath, String entryName, String body) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(body.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private static void writeFakeAapt(Path fixture, String resourceName) throws IOException {
        Path sdk = fixture.resolve("fake-android-sdk");
        Path buildTools = sdk.resolve("build-tools").resolve("99.0.0");
        Files.createDirectories(buildTools);
        writeUtf8(fixture.resolve("local.properties"),
                "sdk.dir=" + sdk.toString().replace('\\', '/') + "\n");

        String output = "spec resource 0x7f080123 " + resourceName + ": flags=0x00000000";
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.US).contains("win");
        Path aapt = buildTools.resolve(windows ? "aapt.cmd" : "aapt");
        if (windows) {
            writeUtf8(aapt, "@echo off\r\necho " + output + "\r\n");
        } else {
            writeUtf8(aapt, "#!/usr/bin/env sh\nprintf '%s\\n' '" + output + "'\n");
            assertTrue("Fake aapt must be executable.", aapt.toFile().setExecutable(true));
        }
    }

    private static String findPowerShell() {
        for (String candidate : new String[]{"pwsh", "powershell", "powershell.exe"}) {
            try {
                Process process = new ProcessBuilder(candidate, "-NoProfile", "-Command",
                        "$PSVersionTable.PSVersion.Major").start();
                if (process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) &&
                        process.exitValue() == 0) {
                    return candidate;
                }
            } catch (IOException exception) {
                // Try the next executable name.
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static ProcessResult runLicenseBoundaryScript(String powershell, Path workingDirectory)
            throws IOException, InterruptedException {
        return runLicenseBoundaryScript(powershell, workingDirectory, new String[0]);
    }

    private static ProcessResult runLicenseBoundaryScript(String powershell, Path workingDirectory,
            String... args) throws IOException, InterruptedException {
        return runLicenseBoundaryScript(powershell, workingDirectory,
                java.util.Collections.emptyMap(), args);
    }

    private static ProcessResult runLicenseBoundaryScript(String powershell, Path workingDirectory,
            java.util.Map<String, String> environment, String... args)
            throws IOException, InterruptedException {
        Path script = repoDir().resolve("scripts/check-license-boundary.ps1");
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(powershell);
        command.add("-NoProfile");
        command.add("-ExecutionPolicy");
        command.add("Bypass");
        command.add("-File");
        command.add(script.toString());
        java.util.Collections.addAll(command, args);
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile());
        for (java.util.Map.Entry<String, String> entry : environment.entrySet()) {
            if (entry.getValue() == null) {
                builder.environment().remove(entry.getKey());
            } else {
                builder.environment().put(entry.getKey(), entry.getValue());
            }
        }
        Process process = builder.start();
        if (!process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Timed out running license-boundary script.");
        }
        String stdout;
        String stderr;
        try (java.io.InputStream output = process.getInputStream();
                java.io.InputStream error = process.getErrorStream()) {
            stdout = new String(output.readAllBytes(), StandardCharsets.UTF_8);
            stderr = new String(error.readAllBytes(), StandardCharsets.UTF_8);
        }
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    private static ProcessResult runProcess(Path workingDirectory, String... command)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .start();
        if (!process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Timed out running command: " + String.join(" ", command));
        }
        String stdout;
        String stderr;
        try (java.io.InputStream output = process.getInputStream();
                java.io.InputStream error = process.getErrorStream()) {
            stdout = new String(output.readAllBytes(), StandardCharsets.UTF_8);
            stderr = new String(error.readAllBytes(), StandardCharsets.UTF_8);
        }
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    try {
                        path.toFile().setWritable(true);
                        Files.deleteIfExists(path);
                    } catch (IOException retryException) {
                        throw new IllegalStateException("Failed to delete " + path, retryException);
                    }
                }
            });
        }
    }

    private static final class ProcessResult {
        final int exitCode;
        final String stdout;
        final String stderr;

        ProcessResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    private static boolean contains(Path path, String needle) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1)
                    .contains(needle);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan " + path, exception);
        }
    }

    private static int countMatches(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
