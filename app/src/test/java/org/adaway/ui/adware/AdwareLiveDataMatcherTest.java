package org.adaway.ui.adware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class AdwareLiveDataMatcherTest {
    @Test
    public void matcherScansEveryInstalledComponentType() throws Exception {
        String source = readAdwareLiveData();

        assertTrue("Adware matcher must scan installed activities.",
                source.contains("checkComponent(packageName, \"activity\", info.activities)"));
        assertTrue("Adware matcher must scan installed receivers.",
                source.contains("checkComponent(packageName, \"receiver\", info.receivers)"));
        assertTrue("Adware matcher must scan installed services.",
                source.contains("checkComponent(packageName, \"service\", info.services)"));
    }

    @Test
    public void signaturePrefixesStayWellFormedForPrefixMatching() throws Exception {
        String[] prefixes = adPackagePrefixes();
        Set<String> uniquePrefixes = new HashSet<>();

        assertTrue("Adware matcher must keep at least one signature prefix.", prefixes.length > 0);
        for (String prefix : prefixes) {
            assertFalse("Adware signature prefix must not be blank.", prefix.trim().isEmpty());
            assertEquals("Adware signature prefix must not contain trim-sensitive whitespace.",
                    prefix, prefix.trim());
            assertTrue("Adware signature prefix must be a package-prefix ending in '.': " + prefix,
                    prefix.endsWith("."));
            assertTrue("Adware signature prefix must not start with '.': " + prefix,
                    !prefix.startsWith("."));
            assertTrue("Adware signature prefixes must be unique: " + prefix,
                    uniquePrefixes.add(prefix));
        }

        assertContains(prefixes, "com.airpush.");
        assertContains(prefixes, "com.Leadbolt.");
        assertContains(prefixes, "com.tapjoy.");
    }

    @Test
    public void matcherUsesComponentPrefixBoundary() throws Exception {
        String source = readAdwareLiveData();

        assertTrue("Adware matcher must compare component names against signature prefixes.",
                source.contains("componentName.startsWith(adPackagePrefix)"));
        assertFalse("Adware matcher must not flag arbitrary component-name substrings.",
                source.contains("componentName.contains(adPackagePrefix)"));
    }

    private static void assertContains(String[] values, String expected) {
        for (String value : values) {
            if (expected.equals(value)) {
                return;
            }
        }
        assertTrue("Expected adware signatures to include " + expected, false);
    }

    private static String[] adPackagePrefixes() throws Exception {
        Field field = AdwareLiveData.class.getDeclaredField("AD_PACKAGE_PREFIXES");
        field.setAccessible(true);
        return (String[]) field.get(null);
    }

    private static String readAdwareLiveData() throws Exception {
        return readRepoFile("app/src/main/java/org/adaway/ui/adware/AdwareLiveData.java");
    }

    private static String readRepoFile(String relativePath) throws Exception {
        byte[] bytes = Files.readAllBytes(resolveRepoFile(relativePath));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static Path resolveRepoFile(String relativePath) {
        Path cwd = Paths.get("").toAbsolutePath();
        Path repo = Files.isDirectory(cwd.resolve("app")) ? cwd : cwd.getParent();
        return repo.resolve(relativePath);
    }
}
