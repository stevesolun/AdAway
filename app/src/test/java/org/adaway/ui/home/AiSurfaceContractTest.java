package org.adaway.ui.home;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;

public class AiSurfaceContractTest {

    @Test
    public void aiUserSurfaceIsRemovedFromDefaultProduct() throws IOException {
        String gradle = read("app/build.gradle");
        String home = read("app/src/main/java/org/adaway/ui/home/HomeFragment.java");
        String discover = read("app/src/main/java/org/adaway/ui/discover/DiscoverFragment.java");
        String prefs = read("app/src/main/res/xml/preferences_main.xml");
        String readme = read("README.md");

        assertFalse("Gradle must not expose a dormant AI feature gate.",
                gradle.contains("AI_FEATURE_ENABLED") || gradle.contains("adawayEnableAi"));
        assertFalse("Home must not keep AI binding or UI hooks.",
                home.contains("BuildConfig.AI_FEATURE_ENABLED") ||
                        home.contains("AiSuggestBottomSheet") ||
                        home.contains("homeAi"));
        assertFalse("Discover must not keep AI chip wiring.",
                discover.contains("BuildConfig.AI_FEATURE_ENABLED") ||
                        discover.contains("AiSuggestBottomSheet"));
        assertFalse("Main preferences must not expose AI settings.",
                prefs.contains("PrefsAiFragment") || prefs.contains("preferences_ai"));
        assertFalse("AI production source package must not contain Java sources.",
                containsJavaFile(repoDir().resolve("app/src/main/java/org/adaway/model/ai")));
        assertFalse("AI UI package must not contain Java sources.",
                containsJavaFile(repoDir().resolve("app/src/main/java/org/adaway/ui/ai")));
        assertFalse("AI settings fragment must be removed.",
                Files.exists(repoDir().resolve(
                        "app/src/main/java/org/adaway/ui/prefs/PrefsAiFragment.java")));
        assertFalse("AI layouts and strings must be removed.",
                Files.exists(repoDir().resolve("app/src/main/res/layout/bottom_sheet_ai_suggest.xml")) ||
                        Files.exists(repoDir().resolve("app/src/main/res/xml/preferences_ai.xml")) ||
                        Files.exists(repoDir().resolve(
                                "app/src/main/res/values/strings_prefs_ai.xml")));
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

    private static boolean containsJavaFile(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.anyMatch(path -> Files.isRegularFile(path)
                    && path.toString().endsWith(".java"));
        }
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
