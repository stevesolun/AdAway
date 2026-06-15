package org.adaway.ui.home;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AiSurfaceContractTest {

    @Test
    public void aiUserSurfaceIsDisabledByDefault() throws IOException {
        String gradle = read("app/build.gradle");
        String home = read("app/src/main/java/org/adaway/ui/home/HomeFragment.java");
        String discover = read("app/src/main/java/org/adaway/ui/discover/DiscoverFragment.java");
        String prefs = read("app/src/main/res/xml/preferences_main.xml");
        String readme = read("README.md");

        assertTrue("BuildConfig must expose a default-disabled AI feature gate.",
                gradle.contains("AI_FEATURE_ENABLED")
                        && gradle.contains("adawayEnableAi"));
        assertTrue("Home AI binding must be gated.",
                home.contains("BuildConfig.AI_FEATURE_ENABLED"));
        assertTrue("Discover AI chip must be gated.",
                discover.contains("BuildConfig.AI_FEATURE_ENABLED"));
        assertFalse("Main preferences must not expose AI settings by default.",
                prefs.contains("PrefsAiFragment"));
        assertFalse("README must not advertise AI as a default feature.",
                readme.contains("AI-powered filter assistant"));
        assertFalse("README must not expose an AI assistant guide in the default product docs.",
                readme.contains("AI Assistant"));
        assertFalse("README must not tell default users to use an Ask AI flow.",
                readme.contains("Ask AI"));
    }

    private static String read(String relativePath) throws IOException {
        Path path = repoDir().resolve(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path repoDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        if (Files.isDirectory(cwd.resolve("src/main"))) {
            Path parent = cwd.getParent();
            return parent != null && cwd.getFileName().toString().equals("app") ? parent : cwd;
        }
        return cwd;
    }
}
