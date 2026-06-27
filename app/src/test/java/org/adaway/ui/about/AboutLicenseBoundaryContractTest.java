package org.adaway.ui.about;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AboutLicenseBoundaryContractTest {
    @Test
    public void aboutScreenKeepsGplAttributionAndMitBoundaryVisible() throws Exception {
        String strings = readRepoFile("app/src/main/res/values/strings.xml");
        String layout = readRepoFile("app/src/main/res/layout/about_activity.xml");

        assertTrue("About attribution must keep the app GPL-branded.",
                strings.contains("name=\"about_attribution\"") &&
                        strings.contains("GPL-3.0"));
        assertTrue("About screen must disclose that MIT relicensing is unavailable.",
                strings.contains("name=\"about_license_boundary\"") &&
                        strings.contains("MIT relicensing is not available"));
        assertTrue("About screen must name the provenance conditions for any MIT edition.",
                strings.contains("GPL-derived code, assets, and notices are cleared"));
        assertFalse("About copy must not claim the current app is MIT licensed.",
                strings.contains("Current app license: MIT") ||
                        strings.contains("MIT licensed") ||
                        strings.contains("licensed under MIT"));
        assertTrue("About layout must render the license-boundary disclosure.",
                layout.contains("android:id=\"@+id/aboutLicenseBoundary\"") &&
                        layout.contains("android:text=\"@string/about_license_boundary\""));
    }

    @Test
    public void licenseInventoryStillBlocksMitEditionUntilProvenanceIsCleared()
            throws Exception {
        String thirdPartyLicenses = readRepoFile("THIRD_PARTY_LICENSES.md");
        String mitPlan = readRepoFile("docs/mit-relicensing-plan.md");

        assertTrue("Third-party inventory must keep current app license GPLv3+.",
                thirdPartyLicenses.contains("AdAway is licensed under the GPLv3 or later"));
        assertTrue("Third-party inventory must keep MIT relicensing future-only.",
                thirdPartyLicenses.contains("MIT relicensing is a future track only"));
        assertTrue("Third-party inventory must name packaged GPL blockers.",
                thirdPartyLicenses.contains("DNS66 / AdBuster-derived VPN code") &&
                        thirdPartyLicenses.contains("Blocking"));
        assertTrue("MIT plan must keep the current distributed app GPLv3+.",
                mitPlan.contains("current distributed app must remain GPLv3+"));
        assertTrue("MIT plan must require legal review of the final artifact.",
                mitPlan.contains("Legal review must approve the final artifact"));
    }

    private static String readRepoFile(String relativePath) throws Exception {
        return new String(Files.readAllBytes(resolveRepoFile(relativePath)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private static Path resolveRepoFile(String relativePath) {
        Path cwd = Paths.get("").toAbsolutePath();
        Path repo = Files.isDirectory(cwd.resolve("app")) ? cwd : cwd.getParent();
        return repo.resolve(relativePath);
    }
}
