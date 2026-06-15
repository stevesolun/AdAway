package org.adaway.ui.discover;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DiscoverCatalogDetachSafetyTest {
    @Test
    public void backgroundCallbacksDoNotRequireActivityBeforeMainThreadDispatch()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/discover/DiscoverCatalogFragment.java");

        assertFalse("Background callbacks must not call requireActivity().runOnUiThread(); "
                        + "a detached fragment throws before the UI guard can run.",
                source.contains("requireActivity().runOnUiThread"));
    }

    @Test
    public void unsupportedReviewDialogCallbackChecksFragmentAttachmentBeforeContextAccess()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/discover/DiscoverFilterListsFragment.java");
        int methodStart = source.indexOf("private void showUnsupportedReviewDialog");
        int methodEnd = source.indexOf("static int[] parseRetryableNoUrlIds", methodStart);
        String method = source.substring(methodStart, methodEnd);

        assertTrue("DiscoverFilterListsFragment should provide the shared detach-safe "
                        + "main-thread helper.",
                source.contains("private void runOnMainThreadIfAdded(@NonNull Runnable action)"));
        assertTrue("Unsupported-list review loads details asynchronously, so its UI callback "
                        + "must verify the fragment is still attached before requireContext().",
                method.contains("runOnMainThreadIfAdded(() -> {"));
        assertFalse("Unsupported-list review must not post raw main-thread callbacks that only "
                        + "check binding before touching context/dialog APIs.",
                method.contains("AppExecutors.getInstance().mainThread().execute(() -> {"));
    }

    private static String readRepoFile(String relativePath) throws Exception {
        Path cwd = Paths.get("").toAbsolutePath();
        Path repo = Files.isDirectory(cwd.resolve("app")) ? cwd : cwd.getParent();
        return new String(Files.readAllBytes(repo.resolve(relativePath)), StandardCharsets.UTF_8);
    }
}
