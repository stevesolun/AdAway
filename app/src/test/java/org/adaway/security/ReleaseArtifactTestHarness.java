package org.adaway.security;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Runs the shared real-APK fixtures without Gradle, a device, or network access. */
final class ReleaseArtifactTestHarness {
    private ReleaseArtifactTestHarness() {
    }

    static void run(Path repository, String... cases) throws Exception {
        Path root = repository.toAbsolutePath().normalize();
        List<String> command = new ArrayList<>();
        command.add(System.getenv().getOrDefault("PYTHON", "python3"));
        command.add(root.resolve("scripts/tests/test_release_artifact_identity.py").toString());
        for (String testCase : cases) {
            command.add("ReleaseArtifactIdentityTests." + testCase);
        }
        ProcessBuilder builder = new ProcessBuilder(command).directory(root.toFile());
        Map<String, String> environment = builder.environment();
        String sdk = environment.get("ANDROID_HOME");
        if (sdk == null || sdk.isEmpty()) {
            sdk = environment.get("ANDROID_SDK_ROOT");
        }
        if ((sdk == null || sdk.isEmpty()) && Files.isRegularFile(root.resolve("local.properties"))) {
            Properties local = new Properties();
            try (InputStream input = Files.newInputStream(root.resolve("local.properties"))) {
                local.load(input);
            }
            sdk = local.getProperty("sdk.dir");
        }
        assertTrue("Real APK tests require ANDROID_HOME/ANDROID_SDK_ROOT or local sdk.dir",
                sdk != null && !sdk.isEmpty());
        environment.put("ANDROID_HOME", sdk);
        environment.put("ANDROID_SDK_ROOT", sdk);
        String javaHome = System.getProperty("java.home");
        environment.put("JAVA_HOME", javaHome);
        environment.put("PATH", Path.of(javaHome, "bin") + java.io.File.pathSeparator +
                environment.getOrDefault("PATH", ""));
        Path log = Files.createTempFile("adaway-release-host-", ".log");
        Process process = null;
        try {
            process = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
            assertTrue("Real APK fixture timed out: " + String.join(", ", cases),
                    process.waitFor(300, TimeUnit.SECONDS));
            String output = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
            assertEquals(output, 0, process.exitValue());
            assertTrue("Every selected behavior must run, not skip:\n" + output,
                    output.contains("Ran " + cases.length + " test") &&
                            output.matches("(?s).*\nOK\\s*$"));
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(log);
        }
    }
}
