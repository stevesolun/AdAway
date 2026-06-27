package org.adaway.security;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.xml.sax.InputSource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CommandReceiverSecurityContractTest {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String SEND_COMMAND_ACTION = "org.adaway.action.SEND_COMMAND";
    private static final String SEND_COMMAND_PERMISSION = "org.adaway.permission.SEND_COMMAND";

    @Test
    public void sendCommandPermissionIsSignatureOnly() throws Exception {
        Document manifest = manifest();

        Element permission = findByAndroidName(
                manifest.getElementsByTagName("permission"),
                SEND_COMMAND_PERMISSION
        );

        assertNotNull("External command API must declare its custom permission.", permission);
        assertEquals("External command API must be limited to same-signature callers.",
                "signature", androidAttribute(permission, "protectionLevel"));
        assertEquals("Command permission must remain grouped with the API permissions.",
                "org.adaway.permission-group.API", androidAttribute(permission, "permissionGroup"));
    }

    @Test
    public void commandReceiverIsSingleExportedActionProtectedBySignaturePermission()
            throws Exception {
        Document manifest = manifest();
        NodeList receivers = manifest.getElementsByTagName("receiver");
        Element commandReceiver = findByAndroidName(receivers, ".broadcast.CommandReceiver");

        assertNotNull("CommandReceiver must remain declared in the manifest.", commandReceiver);
        assertEquals("CommandReceiver is the intentional external command entry point.",
                "true", androidAttribute(commandReceiver, "exported"));
        assertEquals("Exported command receiver must require the signature permission.",
                SEND_COMMAND_PERMISSION, androidAttribute(commandReceiver, "permission"));
        assertTrue("CommandReceiver must listen for the public command action.",
                receiverHandlesAction(commandReceiver, SEND_COMMAND_ACTION));

        int receiversForCommandAction = 0;
        for (int i = 0; i < receivers.getLength(); i++) {
            Element receiver = (Element) receivers.item(i);
            if (receiverHandlesAction(receiver, SEND_COMMAND_ACTION)) {
                receiversForCommandAction++;
                assertEquals("Only CommandReceiver may expose the command action.",
                        ".broadcast.CommandReceiver", androidAttribute(receiver, "name"));
                assertEquals("Every command-action receiver must be signature protected.",
                        SEND_COMMAND_PERMISSION, androidAttribute(receiver, "permission"));
            }
        }
        assertEquals("Exactly one receiver should expose the command action.",
                1, receiversForCommandAction);
    }

    @Test
    public void commandReceiverSourceKeepsActionAndDispatchGuardInOrder() throws IOException {
        String source = read("app/src/main/java/org/adaway/broadcast/CommandReceiver.java");

        assertEquals("Manifest and source command action must not drift.",
                SEND_COMMAND_ACTION, stringConstant(source, "SEND_COMMAND_ACTION"));

        int actionGuard = source.indexOf("if (SEND_COMMAND_ACTION.equals(intent.getAction()))");
        int modelLookup = source.indexOf("getAdBlockModel()", actionGuard);
        int commandRead = source.indexOf("Command.readFromIntent(intent)", actionGuard);
        int dispatch = source.indexOf("AppExecutors.getInstance().diskIO().execute", actionGuard);

        assertTrue("Receiver must gate all command work on the canonical action.",
                actionGuard >= 0);
        assertTrue("AdBlockModel lookup must happen only after the action guard.",
                actionGuard < modelLookup);
        assertTrue("Command parsing must happen only after the action guard.",
                actionGuard < commandRead);
        assertTrue("Command execution must stay off the broadcast thread after validation.",
                commandRead < dispatch);
    }

    private static Document manifest() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        String manifest = read("app/src/main/AndroidManifest.xml");
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(manifest)));
    }

    private static Element findByAndroidName(NodeList nodes, String name) {
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                if (name.equals(androidAttribute(element, "name"))) {
                    return element;
                }
            }
        }
        return null;
    }

    private static boolean receiverHandlesAction(Element receiver, String actionName) {
        NodeList actions = receiver.getElementsByTagName("action");
        for (int i = 0; i < actions.getLength(); i++) {
            Element action = (Element) actions.item(i);
            if (actionName.equals(androidAttribute(action, "name"))) {
                return true;
            }
        }
        return false;
    }

    private static String androidAttribute(Element element, String localName) {
        return element.getAttributeNS(ANDROID_NS, localName);
    }

    private static String stringConstant(String source, String constantName) {
        Pattern pattern = Pattern.compile(constantName + "\\s*=\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(source);
        assertTrue("Missing string constant " + constantName, matcher.find());
        return matcher.group(1);
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
