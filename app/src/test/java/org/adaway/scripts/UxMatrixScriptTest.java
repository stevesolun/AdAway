package org.adaway.scripts;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class UxMatrixScriptTest {
    private static final String SOURCE_COMMIT =
            "0123456789abcdef0123456789abcdef01234567";
    private static final String OTHER_SOURCE_COMMIT =
            "fedcba9876543210fedcba9876543210fedcba98";
    private static final String[] UX_VARIANTS = {
            "baseline",
            "font-1.3",
            "font-1.6",
            "font-1.3-rtl",
            "font-1.6-rtl"
    };
    private static final String[] UX_SCREENS = {
            "home",
            "discover",
            "sources",
            "more",
            "domain_checker",
            "onboarding",
            "custom_rules",
            "update"
    };

    @Test
    public void instrumentationParserAcceptsOkTranscriptWhenAdbExitCodeIsNonZero()
            throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the UX matrix parser.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-ux-matrix-ok");
        try {
            Path androidHome = createFakeAndroidHome(fixture);
            Path javaHome = fixture.resolve("java");
            Files.createDirectories(javaHome);

            ProcessResult result = runPowerShell(powershell, "$ErrorActionPreference = 'Stop';" +
                    ". " + quote(repoDir().resolve("scripts/run-ux-matrix.ps1")) +
                    " -AndroidHome " + quote(androidHome) +
                    " -JavaHome " + quote(javaHome) + ";" +
                    "$output = @(" +
                    "'INSTRUMENTATION_STATUS: class=org.adaway.ui.UxDeviceMatrixTest'," +
                    "'INSTRUMENTATION_STATUS: test=rendersHome'," +
                    "'OK (1 test)'" +
                    ") -join [Environment]::NewLine;" +
                    "if (-not (Test-UxInstrumentationResult -ExitCode 1 -Output $output)) {" +
                    " throw 'OK transcript rejected';" +
                    "}");

            assertEquals("OK instrumentation output must be authoritative over adb exit drift.\n" +
                    result.stderr, 0, result.exitCode);
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void instrumentationParserRejectsFailureMarkersWhenAdbExitCodeIsZero()
            throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the UX matrix parser.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-ux-matrix-fail");
        try {
            Path androidHome = createFakeAndroidHome(fixture);
            Path javaHome = fixture.resolve("java");
            Files.createDirectories(javaHome);

            ProcessResult result = runPowerShell(powershell, "$ErrorActionPreference = 'Stop';" +
                    ". " + quote(repoDir().resolve("scripts/run-ux-matrix.ps1")) +
                    " -AndroidHome " + quote(androidHome) +
                    " -JavaHome " + quote(javaHome) + ";" +
                    "$output = @(" +
                    "'INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: boom'," +
                    "'FAILURES!!!'," +
                    "'OK (1 test)'" +
                    ") -join [Environment]::NewLine;" +
                    "if (Test-UxInstrumentationResult -ExitCode 0 -Output $output) {" +
                    " throw 'Failure transcript accepted';" +
                    "}");

            assertEquals("Failure markers must dominate any misleading OK text.\n" +
                    result.stderr, 0, result.exitCode);
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void runnerDiscoversUnixAdbFromAndroidSdkRoot() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the UX matrix runner preflight.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-ux-matrix-unix-adb");
        try {
            Path androidHome = createFakeAndroidHome(fixture, "adb");
            Path javaHome = fixture.resolve("java");
            Files.createDirectories(javaHome);

            ProcessResult result = runPowerShell(powershell, "$ErrorActionPreference = 'Stop';" +
                    "$env:ANDROID_HOME = '';" +
                    "$env:ANDROID_SDK_ROOT = " + quote(androidHome) + ";" +
                    ". " + quote(repoDir().resolve("scripts/run-ux-matrix.ps1")) +
                    " -JavaHome " + quote(javaHome) + ";" +
                    "$resolvedAdb = Get-Variable -Name adb -ValueOnly;" +
                    "if ([System.IO.Path]::GetFileName($resolvedAdb) -ne 'adb') {" +
                    " throw \"Expected unix adb, got $resolvedAdb\";" +
                    "}" +
                    "$resolvedGradle = Get-Variable -Name gradle -ValueOnly;" +
                    "if ([System.IO.Path]::GetFileName($resolvedGradle) -notmatch " +
                    "'^gradlew(\\.bat|\\.cmd)?$') {" +
                    " throw \"Expected Gradle wrapper, got $resolvedGradle\";" +
                    "}");

            assertEquals("UX matrix preflight must accept macOS/Linux platform-tools/adb.\n" +
                    result.stderr, 0, result.exitCode);
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void runnerPullsScreenshotsOnlyAfterSuccessfulInstrumentationGate()
            throws IOException {
        String script = readUtf8(repoDir().resolve("scripts/run-ux-matrix.ps1"));
        int gateIndex = script.indexOf("Test-UxInstrumentationResult");
        int failureIndex = script.indexOf("UX matrix test failed");
        int pullIndex = script.indexOf("Invoke-Adb pull");

        assertTrue("Runner must evaluate instrumentation output before pulling screenshots.",
                gateIndex >= 0 && failureIndex > gateIndex);
        assertTrue("Runner must pull screenshots after the success gate.",
                pullIndex > failureIndex);
    }

    @Test
    public void runnerIncludesLargeFontAndRtlStressVariants() throws IOException {
        String script = readUtf8(repoDir().resolve("scripts/run-ux-matrix.ps1"));

        assertTrue("UX matrix must be driven by the shared variant spec.",
                script.contains("$UxMatrixVariantSpecs") &&
                        script.contains("foreach ($variant in $UxMatrixVariantSpecs)") &&
                        script.contains("Invoke-UxTest -Variant $variant.Name"));
        assertTrue("UX matrix must keep baseline coverage.",
                script.contains("Name = \"baseline\""));
        assertTrue("UX matrix must keep the 1.3 large-font variant.",
                script.contains("Name = \"font-1.3\"") &&
                        script.contains("FontScale = \"1.3\""));
        assertTrue("UX matrix must include a stronger 1.6 font-scale stress variant.",
                script.contains("Name = \"font-1.6\"") &&
                        script.contains("FontScale = \"1.6\""));
        assertTrue("UX matrix must include RTL at both large font scales.",
                script.contains("Name = \"font-1.3-rtl\"") &&
                        script.contains("Name = \"font-1.6-rtl\"") &&
                        script.contains("Locales = \"ar-XB\""));
    }

    @Test
    public void runnerDismissesExternalDialogsBeforeInstrumentation() throws IOException {
        String script = readUtf8(repoDir().resolve("scripts/run-ux-matrix.ps1"));

        int helperIndex = script.indexOf("function Dismiss-ExternalSystemDialogs");
        int invokeIndex = script.indexOf("function Invoke-UxTest");
        int beforeRunIndex = script.indexOf("Dismiss-ExternalSystemDialogs", invokeIndex);
        int instrumentationIndex = script.indexOf("\"shell\", \"am\", \"instrument\"", invokeIndex);
        int deviceStateIndex = script.indexOf("function Set-DeviceState");
        int afterStateIndex = script.indexOf("Dismiss-ExternalSystemDialogs", deviceStateIndex);

        assertTrue("Runner must keep a helper for emulator launcher/system ANR dialogs.",
                helperIndex >= 0 &&
                        script.contains("android.intent.action.CLOSE_SYSTEM_DIALOGS") &&
                        script.contains("com.google.android.apps.nexuslauncher") &&
                        script.contains("com.android.launcher3"));
        assertTrue("Runner must dismiss external dialogs before instrumentation starts.",
                beforeRunIndex > invokeIndex && beforeRunIndex < instrumentationIndex);
        assertTrue("Runner must also dismiss external dialogs after locale/font-scale changes.",
                afterStateIndex > deviceStateIndex);
    }

    @Test
    public void runnerWritesReviewManifestForManualSignOff() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the UX matrix manifest writer.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-ux-matrix-manifest");
        try {
            Path androidHome = createFakeAndroidHome(fixture);
            Path javaHome = fixture.resolve("java");
            Path output = fixture.resolve("ux-output");
            Files.createDirectories(javaHome);
            createCompleteUxMatrixScreens(output);

            ProcessResult result = runPowerShell(powershell, "$ErrorActionPreference = 'Stop';" +
                    ". " + quote(repoDir().resolve("scripts/run-ux-matrix.ps1")) +
                    " -AndroidHome " + quote(androidHome) +
                    " -JavaHome " + quote(javaHome) + ";" +
                    "Write-UxMatrixReviewManifest -Directory " + quote(output) + ";");

            assertEquals("UX matrix manifest writer must exit successfully.\n" +
                    result.stderr, 0, result.exitCode);
            String manifest = readUtf8(output.resolve("ux-matrix-review.md"));
            assertTrue("Manifest must include the manual review checklist.",
                    manifest.contains("Manual sign-off checklist"));
            assertTrue("Manifest must bind the packet to the source commit.",
                    Pattern.compile("(?m)^- Source commit: [0-9a-f]{40}$")
                            .matcher(manifest)
                            .find());
            assertTrue("Manifest must include the strongest RTL/font-scale variant.",
                    manifest.contains("## font-1.6-rtl"));
            assertTrue("Manifest must link expected screenshots by relative path.",
                    manifest.contains("home - font-1.6-rtl/ux-matrix/home.png"));
            assertTrue("Manifest must preserve the bird-logo review item.",
                    manifest.contains("The AdAway bird remains the first-screen brand signal"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void runnerReviewManifestFailsWhenExpectedScreenshotIsMissing() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the UX matrix manifest writer.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-ux-matrix-missing");
        try {
            Path androidHome = createFakeAndroidHome(fixture);
            Path javaHome = fixture.resolve("java");
            Path output = fixture.resolve("ux-output");
            Files.createDirectories(javaHome);
            createCompleteUxMatrixScreens(output);
            Files.delete(output.resolve("font-1.6-rtl").resolve("ux-matrix")
                    .resolve("home.png"));

            ProcessResult result = runPowerShell(powershell, "$ErrorActionPreference = 'Stop';" +
                    ". " + quote(repoDir().resolve("scripts/run-ux-matrix.ps1")) +
                    " -AndroidHome " + quote(androidHome) +
                    " -JavaHome " + quote(javaHome) + ";" +
                    "Write-UxMatrixReviewManifest -Directory " + quote(output) + ";");

            assertTrue("UX matrix manifest must fail when a screenshot is missing.",
                    result.exitCode != 0);
            assertTrue("Failure must name the missing variant and screen.\n" +
                            result.stdout + result.stderr,
                    (result.stdout + result.stderr).contains("font-1.6-rtl/home.png"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void uxSignOffVerifierFailsWhenChecklistIsIncomplete() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the UX sign-off verifier.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-ux-signoff-incomplete");
        try {
            Path packet = fixture.resolve("ux-matrix-review.md");
            Path report = fixture.resolve("ux-signoff-report.md");
            Files.write(packet, (
                    "# UX Matrix Review Packet\n\n" +
                            "- Source commit: " + SOURCE_COMMIT + "\n\n" +
                            "Manual sign-off checklist:\n" +
                            "- [x] Text is readable without clipping, ellipsizing, or overlap.\n" +
                            "- [ ] Touch targets remain reachable and visually stable.\n" +
                            "## baseline\n" +
                            "- [x] home - baseline/ux-matrix/home.png\n"
            ).getBytes(StandardCharsets.UTF_8));

            ProcessResult result = runPowerShell(powershell, "$ErrorActionPreference = 'Stop';" +
                    "$env:GITHUB_SHA = '" + SOURCE_COMMIT + "';" +
                    "& " + quote(repoDir().resolve("scripts/verify-ux-signoff.ps1")) +
                    " -ReviewPacket " + quote(packet) +
                    " -Reviewer 'QA Lead'" +
                    " -ReportPath " + quote(report) + ";");

            assertTrue("Incomplete UX sign-off packet must fail.",
                    result.exitCode != 0);
            assertTrue("Incomplete UX sign-off must write a failure report.",
                    Files.isRegularFile(report));
            String reportText = readUtf8(report);
            assertTrue("Failure report must name the unchecked review item.",
                    reportText.contains("# UX Sign-Off Report") &&
                            reportText.contains("- Status: failed") &&
                            reportText.contains("- Reviewer: QA Lead") &&
                            reportText.contains("- Unchecked items: 1") &&
                            reportText.contains("Touch targets remain reachable"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void uxSignOffVerifierFailsWhenPacketSourceCommitDiffersFromCurrentCommit()
            throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the UX sign-off verifier.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-ux-signoff-source-drift");
        try {
            Path packet = fixture.resolve("ux-matrix-review.md");
            Path report = fixture.resolve("ux-signoff-report.md");
            Files.write(packet, (
                    "# UX Matrix Review Packet\n\n" +
                            "- Source commit: " + OTHER_SOURCE_COMMIT + "\n\n" +
                            "Manual sign-off checklist:\n" +
                            "- [x] Text is readable without clipping, ellipsizing, or overlap.\n" +
                            "- [x] Touch targets remain reachable and visually stable.\n" +
                            "## baseline\n" +
                            "- [x] home - baseline/ux-matrix/home.png\n"
            ).getBytes(StandardCharsets.UTF_8));

            ProcessResult result = runPowerShell(powershell, "$ErrorActionPreference = 'Stop';" +
                    "$env:GITHUB_SHA = '" + SOURCE_COMMIT + "';" +
                    "& " + quote(repoDir().resolve("scripts/verify-ux-signoff.ps1")) +
                    " -ReviewPacket " + quote(packet) +
                    " -Reviewer 'QA Lead'" +
                    " -ReportPath " + quote(report) + ";");

            assertTrue("UX sign-off must fail when the packet source commit is stale.",
                    result.exitCode != 0);
            String reportText = readUtf8(report);
            assertTrue("Failure report must explain the source-commit mismatch.",
                    reportText.contains("# UX Sign-Off Report") &&
                            reportText.contains("- Status: failed") &&
                            reportText.contains("Review packet source commit") &&
                            reportText.contains("does not match"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void uxSignOffVerifierWritesPassingReportForCompletedChecklist() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the UX sign-off verifier.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-ux-signoff-pass");
        try {
            Path packet = fixture.resolve("ux-matrix-review.md");
            Path report = fixture.resolve("ux-signoff-report.md");
            Files.write(packet, (
                    "# UX Matrix Review Packet\n\n" +
                            "- Source commit: " + SOURCE_COMMIT + "\n\n" +
                            "Manual sign-off checklist:\n" +
                            "- [x] Text is readable without clipping, ellipsizing, or overlap.\n" +
                            "- [x] Touch targets remain reachable and visually stable.\n" +
                            "## baseline\n" +
                            "- [x] home - baseline/ux-matrix/home.png\n"
            ).getBytes(StandardCharsets.UTF_8));

            ProcessResult result = runPowerShell(powershell, "$ErrorActionPreference = 'Stop';" +
                    "$env:GITHUB_SHA = '" + SOURCE_COMMIT + "';" +
                    "& " + quote(repoDir().resolve("scripts/verify-ux-signoff.ps1")) +
                    " -ReviewPacket " + quote(packet) +
                    " -Reviewer 'QA Lead'" +
                    " -ReportPath " + quote(report) + ";");

            assertEquals("Completed UX sign-off packet must pass.\n" + result.stderr,
                    0, result.exitCode);
            String reportText = readUtf8(report);
            assertTrue("Passing report must summarize reviewer sign-off.",
                    reportText.contains("# UX Sign-Off Report") &&
                            reportText.contains("- Status: passed") &&
                            reportText.contains("- Reviewer: QA Lead") &&
                            Pattern.compile("(?m)^- Source commit: [0-9a-f]{40}$")
                                    .matcher(reportText)
                                    .find() &&
                            reportText.contains("- Review packet source commit: " +
                                    SOURCE_COMMIT) &&
                            Pattern.compile("(?m)^- Review packet SHA-256: [0-9a-f]{64}$")
                                    .matcher(reportText)
                                    .find() &&
                            reportText.contains("- Checked items: 3") &&
                            reportText.contains("- Unchecked items: 0") &&
                            reportText.contains("- Review packet: ux-matrix-review.md"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void readmeDocumentsUxSignOffVerifier() throws IOException {
        String readme = readUtf8(repoDir().resolve("README.md"));

        assertTrue("README must document the UX sign-off verifier command.",
                readme.contains("verify-ux-signoff.ps1") &&
                        readme.contains("-Reviewer") &&
                        readme.contains("Source commit") &&
                        readme.contains("Review packet source commit") &&
                        readme.contains("Review packet SHA-256") &&
                        readme.contains("ux-signoff-report.md"));
    }

    private static Path createFakeAndroidHome(Path fixture) throws IOException {
        return createFakeAndroidHome(fixture, "adb.exe");
    }

    private static Path createFakeAndroidHome(Path fixture, String adbFileName) throws IOException {
        Path platformTools = fixture.resolve("android-sdk").resolve("platform-tools");
        Files.createDirectories(platformTools);
        Path adb = platformTools.resolve(adbFileName);
        Files.write(adb, new byte[0]);
        return fixture.resolve("android-sdk");
    }

    private static void createCompleteUxMatrixScreens(Path output) throws IOException {
        for (String variant : UX_VARIANTS) {
            Path directory = output.resolve(variant).resolve("ux-matrix");
            Files.createDirectories(directory);
            for (String screen : UX_SCREENS) {
                Files.write(directory.resolve(screen + ".png"), new byte[]{1});
            }
        }
    }

    private static ProcessResult runPowerShell(String powershell, String command)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(powershell, "-NoProfile", "-ExecutionPolicy",
                "Bypass", "-Command", command)
                .directory(repoDir().toFile())
                .start();
        if (!process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Timed out running PowerShell command.");
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

    private static Path repoDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        if (Files.isDirectory(cwd.resolve("app/src/test"))) {
            return cwd;
        }
        Path parent = cwd.getParent();
        return parent != null && cwd.getFileName().toString().equals("app") ? parent : cwd;
    }

    private static String quote(Path path) {
        return "'" + path.toString().replace("'", "''") + "'";
    }

    private static String readUtf8(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
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
                    throw new IllegalStateException("Failed to delete " + path, exception);
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
}
