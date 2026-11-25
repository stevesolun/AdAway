package org.adaway.ui.log;

import org.adaway.R;
import org.adaway.db.entity.ListType;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.*;

public class LogActionDescriptionTest {
    @Test
    public void unselectedActionsDescribeTheirActualListType() {
        assertEquals(R.string.tcpdump_entry_add_blacklist,
                LogAdapter.getActionDescription(ListType.BLOCKED, false));
        assertEquals(R.string.tcpdump_entry_add_whitelist,
                LogAdapter.getActionDescription(ListType.ALLOWED, false));
        assertEquals(R.string.tcpdump_entry_add_redirect,
                LogAdapter.getActionDescription(ListType.REDIRECTED, false));
    }

    @Test
    public void selectedActionsDescribeRemovalFromTheirCurrentType() {
        assertEquals(R.string.log_remove_blocked_entry,
                LogAdapter.getActionDescription(ListType.BLOCKED, true));
        assertEquals(R.string.log_remove_allowed_entry,
                LogAdapter.getActionDescription(ListType.ALLOWED, true));
        assertEquals(R.string.log_remove_redirected_entry,
                LogAdapter.getActionDescription(ListType.REDIRECTED, true));
    }

    @Test
    public void initialLayoutDescriptionsAgreeWithAllowAndRedirectActions() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Path repo = Paths.get(System.getProperty("adaway.repo", "")).toAbsolutePath();
        while (repo != null && !Files.isDirectory(repo.resolve("app/src/main"))) {
            repo = repo.getParent();
        }
        assertNotNull("Repository not found", repo);
        NodeList icons = factory.newDocumentBuilder().parse(
                repo.resolve("app/src/main/res/layout/log_entry.xml").toFile())
                .getElementsByTagName("ImageView");
        String android = "http://schemas.android.com/apk/res/android";
        int checked = 0;
        for (int i = 0; i < icons.getLength(); i++) {
            Element icon = (Element) icons.item(i);
            String id = icon.getAttributeNS(android, "id");
            if ("@+id/allowImageView".equals(id)) {
                assertEquals("@string/tcpdump_entry_add_whitelist",
                        icon.getAttributeNS(android, "contentDescription"));
                checked++;
            } else if ("@+id/redirectionImageView".equals(id)) {
                assertEquals("@string/tcpdump_entry_add_redirect",
                        icon.getAttributeNS(android, "contentDescription"));
                checked++;
            }
        }
        assertEquals(2, checked);
    }
}
