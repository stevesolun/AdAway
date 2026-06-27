package org.adaway.ui.lists.type;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RedirectedHostsValidationContractTest {

    @Test
    public void redirectedUserRuleValidationUsesSharedPublicRedirectIpPolicy()
            throws IOException {
        String fragment = read("app/src/main/java/org/adaway/ui/lists/type/"
                + "RedirectedHostsFragment.java");
        String logActivity = read("app/src/main/java/org/adaway/ui/log/LogActivity.java");
        String logEntryLayout = read("app/src/main/res/layout/log_entry.xml");
        String backupFormat = read("app/src/main/java/org/adaway/model/backup/BackupFormat.java");
        String regexUtils = read("app/src/main/java/org/adaway/util/RegexUtils.java");

        assertTrue("Redirected list add/edit validation must reject private redirect IPs.",
                fragment.contains("RegexUtils.isValidRedirectIp(ip)"));
        String weakRedirectPolicy = "RegexUtils.isValidHostname(hostname) && "
                + "RegexUtils.isValidIP(ip)";
        assertFalse("Redirected list UI must not accept every syntactically valid IP.",
                fragment.contains(weakRedirectPolicy));
        assertTrue("DNS log redirect dialog must reject private redirect IPs.",
                logActivity.contains("RegexUtils.isValidRedirectIp(ip)")
                        && logActivity.contains(
                        "AlertDialogValidator(alertDialog, RegexUtils::isValidRedirectIp"));
        assertFalse("DNS log redirect dialog must not accept every syntactically valid IP.",
                logActivity.contains("RegexUtils.isValidIP(ip)")
                        || logActivity.contains(
                        "AlertDialogValidator(alertDialog, RegexUtils::isValidIP"));
        assertTrue("DNS log redirect action should not reuse allowlist accessibility copy.",
                logEntryLayout.contains("@string/tcpdump_entry_add_redirect"));
        assertTrue("Backup import must use the same redirect-target policy as the UI.",
                backupFormat.contains("RegexUtils.isValidRedirectIp(redirection)"));
        assertTrue("Shared redirect-target policy must require a valid non-private IP.",
                regexUtils.contains("public static boolean isValidRedirectIp(String ip)")
                        && regexUtils.contains("isValidIP(ip) && !isPrivateOrReservedIp(ip)"));
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
