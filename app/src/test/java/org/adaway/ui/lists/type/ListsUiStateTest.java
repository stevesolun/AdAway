package org.adaway.ui.lists.type;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ListsUiStateTest {

    @Test
    public void resolve_loadingWithNoRows_showsLoadingState() {
        assertEquals(ListsUiState.LOADING, ListsUiState.resolve(true, false, 0, false));
    }

    @Test
    public void resolve_loadingWithVisibleRows_hidesInlineState() {
        assertEquals(ListsUiState.HIDDEN, ListsUiState.resolve(true, false, 4, false));
    }

    @Test
    public void resolve_error_showsLoadFailed() {
        assertEquals(ListsUiState.LOAD_FAILED, ListsUiState.resolve(false, true, 0, false));
    }

    @Test
    public void resolve_emptyWithoutSearch_showsNoRules() {
        assertEquals(ListsUiState.NO_RULES, ListsUiState.resolve(false, false, 0, false));
    }

    @Test
    public void resolve_emptyWithSearch_showsNoMatches() {
        assertEquals(ListsUiState.NO_MATCHES, ListsUiState.resolve(false, false, 0, true));
    }

    @Test
    public void resolve_rowsVisible_hidesInlineState() {
        assertEquals(ListsUiState.HIDDEN, ListsUiState.resolve(false, false, 4, true));
    }

    @Test
    public void listStateLayoutDefinesLoadingAndRetryBoundaries() throws Exception {
        String fragment = readRepoFile(
                "app/src/main/java/org/adaway/ui/lists/type/AbstractListFragment.java");
        String layout = readRepoFile("app/src/main/res/layout/hosts_lists_fragment.xml");
        String strings = readRepoFile("app/src/main/res/values/strings_lists.xml");

        assertTrue("Lists loading state must show a dedicated progress indicator.",
                layout.contains("android:id=\"@+id/hostsListsStateProgress\"") &&
                        layout.contains("android:indeterminate=\"true\"") &&
                        fragment.contains("stateProgress.setVisibility(" +
                                "state == ListsUiState.LOADING ? View.VISIBLE : View.GONE)"));
        assertTrue("Lists loading state must have user-visible copy.",
                strings.contains("<string name=\"lists_state_loading_title\">") &&
                        strings.contains("<string name=\"lists_state_loading_message\">") &&
                        fragment.contains("R.string.lists_state_loading_title") &&
                        fragment.contains("R.string.lists_state_loading_message"));
        assertTrue("Retry must remain limited to failed loads.",
                fragment.contains("retryButton.setVisibility(" +
                        "state == ListsUiState.LOAD_FAILED ? View.VISIBLE : View.GONE)"));
    }

    private static String readRepoFile(String relativePath) throws Exception {
        return new String(Files.readAllBytes(resolveRepoFile(relativePath)),
                StandardCharsets.UTF_8);
    }

    private static Path resolveRepoFile(String relativePath) {
        Path cwd = Paths.get("").toAbsolutePath();
        Path direct = cwd.resolve(relativePath);
        if (Files.exists(direct)) {
            return direct;
        }
        return cwd.resolve("..").resolve(relativePath).normalize();
    }
}
