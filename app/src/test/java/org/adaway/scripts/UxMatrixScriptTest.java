package org.adaway.scripts;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class UxMatrixScriptTest {

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

    private static Path createFakeAndroidHome(Path fixture) throws IOException {
        Path platformTools = fixture.resolve("android-sdk").resolve("platform-tools");
        Files.createDirectories(platformTools);
        Path adb = platformTools.resolve("adb.exe");
        Files.write(adb, new byte[0]);
        return fixture.resolve("android-sdk");
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
