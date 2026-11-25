package org.adaway.ui.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilderFactory;

/** Source-contract checks, not proof of Android lifecycle or rendering behavior. */
public class HostsFileViewerContractTest {
    private static final String PREFS = "app/src/main/java/org/adaway/ui/prefs/";

    @Test
    public void openHostsUsesOnlyInternalViewerWithoutFileUriOrRemount() throws Exception {
        String source = read(PREFS + "PrefsRootFragment.java");
        String open = source.substring(source.indexOf("private boolean openHostsFile("),
                source.indexOf("private void bindRedirection("));
        assertTrue(open.contains("new HostsFileViewerDialog().showNow("));
        assertTrue(open.contains("!manager.isStateSaved()"));
        assertTrue(open.contains("manager.findFragmentByTag(HostsFileViewerDialog.TAG) == null"));
        assertFalse(open.contains("Intent"));
        assertFalse(open.contains("startActivity"));
        assertFalse(source.contains("file://"));
        assertFalse(source.contains("remountPartition"));
        assertFalse(source.contains("READ_WRITE"));
        assertFalse(source.contains("openHostsFileLauncher"));
        assertTrue(source.contains("setSummary(R.string.hosts_viewer_read_only)"));
    }

    @Test
    public void viewerHasNoExternalUriGrantOrSystemWritePath() throws Exception {
        String source = read(PREFS + "HostsFileViewerDialog.java");
        assertTrue(source.contains("new File(ANDROID_SYSTEM_ETC_HOSTS)"));
        assertTrue(source.contains("HostsFileSnapshot.read(new FileInputStream(hosts))"));
        for (String forbidden : new String[]{"Intent", "Uri", "FileProvider", "Shell.",
                "remount", "OutputStream", "GRANT_", "getArguments()"}) {
            assertFalse(forbidden, source.contains(forbidden));
        }
    }

    @Test
    public void loadingErrorsAndStaleDeliveryAreExplicitlyGuarded() throws Exception {
        String source = read(PREFS + "HostsFileViewerDialog.java");
        assertTrue(source.contains("executors.diskIO().execute(mLoadTask)"));
        assertTrue(source.contains("executors.mainThread().execute("));
        assertTrue(source.contains("catch (IOException | SecurityException exception)"));
        assertTrue(source.contains("catch (RejectedExecutionException exception)"));
        assertTrue(source.contains("generation != mGeneration || mContent == null"));
        assertTrue(source.contains("mStatus.setText(R.string.hosts_viewer_error)"));
        String stopped = source.substring(source.indexOf("public void onStop()"),
                source.indexOf("public void onDestroyView()"));
        assertTrue(stopped.contains("++mGeneration"));
        assertTrue(stopped.contains("mLoadTask.cancel(true)"));
        assertTrue(source.contains("mContent = null"));
    }

    @Test
    public void layoutIsReadOnlyAndExplainsSnapshotAndTruncation() throws Exception {
        Document layout = xml("app/src/main/res/layout/hosts_file_viewer.xml");
        assertEquals(0, layout.getElementsByTagName("EditText").getLength());
        assertEquals(0, layout.getElementsByTagName("WebView").getLength());
        assertEquals(1, layout.getElementsByTagName("ScrollView").getLength());
        Element content = (Element) layout.getElementsByTagName("TextView").item(2);
        assertEquals("true", content.getAttribute("android:textIsSelectable"));
        assertEquals("false", content.getAttribute("android:saveEnabled"));
        String strings = xml("app/src/main/res/values/strings_hosts_viewer.xml")
                .getDocumentElement().getTextContent();
        assertTrue(strings.contains("Read-only snapshot"));
        assertTrue(strings.contains("system hosts file is not changed"));
        assertTrue(strings.contains("first 64 KiB"));
        assertTrue(strings.contains("No changes were made"));
    }

    private static Document xml(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(resolve(path).toFile());
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(resolve(path)), StandardCharsets.UTF_8);
    }

    private static Path resolve(String path) {
        Path cwd = Paths.get("").toAbsolutePath();
        return (Files.isDirectory(cwd.resolve("app")) ? cwd : cwd.getParent()).resolve(path);
    }
}
