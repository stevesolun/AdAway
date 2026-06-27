/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.ui.adware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AdwareUninstallIntentContractTest {
    @Test
    public void adwareInstallStoresDetectedPackageSeparatelyFromDisplayName() {
        AdwareInstall install = new AdwareInstall("Display name", "com.example.adware");

        assertEquals("Display name", install.get(AdwareInstall.APPLICATION_NAME_KEY));
        assertEquals("com.example.adware", install.get(AdwareInstall.PACKAGE_NAME_KEY));
    }

    @Test
    public void rowClickLaunchesPackageDeleteIntentForDetectedPackage()
            throws Exception {
        String fragment = readRepoFile("app/src/main/java/org/adaway/ui/adware/AdwareFragment.java");

        int clickHandler = fragment.indexOf("this.mListView.setOnItemClickListener");
        int getClickedInstall = fragment.indexOf(
                "AdwareInstall adwareInstall = (AdwareInstall) parent.getItemAtPosition(position)",
                clickHandler);
        int uninstallCall = fragment.indexOf(
                "AdwareFragment.this.uninstallAdware(adwareInstall)",
                getClickedInstall);
        assertTrue("Adware list row clicks must uninstall the selected detected package.",
                clickHandler >= 0 && clickHandler < getClickedInstall &&
                        getClickedInstall < uninstallCall);

        String uninstallBlock = methodBlock(fragment, "uninstallAdware");
        int deleteIntent = uninstallBlock.indexOf("new Intent(Intent.ACTION_DELETE)");
        int packageUri = uninstallBlock.indexOf("Uri.parse(\"package:\" + " +
                "adwareInstall.get(AdwareInstall.PACKAGE_NAME_KEY))", deleteIntent);
        int setData = uninstallBlock.indexOf("intent.setData", deleteIntent);
        int startActivity = uninstallBlock.indexOf("this.startActivity(intent)", setData);

        assertTrue("Uninstall must use Android's package delete action.",
                deleteIntent >= 0);
        assertTrue("Uninstall URI must be built from the detected package name key.",
                deleteIntent < setData && setData < packageUri && packageUri < startActivity);
        assertFalse("Uninstall must not build a destructive package URI from display text.",
                uninstallBlock.contains("APPLICATION_NAME_KEY"));
    }

    private static String methodBlock(String source, String methodName) {
        int start = source.indexOf("void " + methodName);
        if (start < 0) {
            throw new AssertionError("Missing method: " + methodName);
        }
        int end = source.indexOf("\n    }\n}", start);
        if (end < 0) {
            end = source.length();
        }
        return source.substring(start, end);
    }

    private static String readRepoFile(String relativePath) throws Exception {
        return new String(Files.readAllBytes(resolveRepoFile(relativePath)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private static Path resolveRepoFile(String relativePath) {
        Path cwd = Paths.get("").toAbsolutePath();
        Path direct = cwd.resolve(relativePath);
        if (Files.exists(direct)) {
            return direct;
        }
        Path parent = cwd.getParent();
        while (parent != null) {
            Path candidate = parent.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            parent = parent.getParent();
        }
        return direct;
    }
}
