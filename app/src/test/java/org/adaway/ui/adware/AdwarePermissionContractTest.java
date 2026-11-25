package org.adaway.ui.adware;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.*;

public class AdwarePermissionContractTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String REQUEST_DELETE = "android.permission.REQUEST_DELETE_PACKAGES";
    private static final String PRIVILEGED_DELETE = "android.permission.DELETE_PACKAGES";

    @Test
    public void mainManifestRequestsOnlyNormalUninstallPermissionWithoutSdkCap() throws Exception {
        Document document = manifest("main");
        NodeList permissions = document.getElementsByTagName("uses-permission");
        int requests = 0;
        for (int i = 0; i < permissions.getLength(); i++) {
            Element permission = (Element) permissions.item(i);
            String name = permission.getAttributeNS(ANDROID, "name");
            assertNotEquals(PRIVILEGED_DELETE, name);
            if (REQUEST_DELETE.equals(name)) {
                requests++;
                assertFalse(permission.hasAttributeNS(ANDROID, "maxSdkVersion"));
            }
        }
        assertEquals(1, requests);
    }

    @Test
    public void variantsDoNotOverrideUninstallPermissionAndMinSdkSupportsIt() throws Exception {
        for (String variant : new String[]{"debug", "directRelease"}) {
            NodeList permissions = manifest(variant).getElementsByTagName("uses-permission");
            for (int i = 0; i < permissions.getLength(); i++) {
                String name = ((Element) permissions.item(i)).getAttributeNS(ANDROID, "name");
                assertNotEquals(REQUEST_DELETE, name);
                assertNotEquals(PRIVILEGED_DELETE, name);
            }
        }
        String build = new String(Files.readAllBytes(repo().resolve("app/build.gradle")),
                StandardCharsets.UTF_8);
        java.util.regex.Matcher minSdk = java.util.regex.Pattern.compile("minSdk\\s*=\\s*(\\d+)")
                .matcher(build);
        assertTrue(minSdk.find());
        assertTrue(Integer.parseInt(minSdk.group(1)) >= 26);
    }

    private static Document manifest(String variant) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(
                repo().resolve("app/src/" + variant + "/AndroidManifest.xml").toFile());
    }

    private static Path repo() {
        Path path = Paths.get(System.getProperty("adaway.repo", "")).toAbsolutePath();
        while (path != null && !Files.isDirectory(path.resolve("app/src/main"))) {
            path = path.getParent();
        }
        if (path == null) throw new AssertionError("Repository not found");
        return path;
    }
}
