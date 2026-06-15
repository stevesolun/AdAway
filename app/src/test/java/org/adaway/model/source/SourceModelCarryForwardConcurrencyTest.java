package org.adaway.model.source;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SourceModelCarryForwardConcurrencyTest {

    @Test
    public void deferredCarryForwardSources_isThreadSafeForParallelResultPaths()
            throws Exception {
        String sourceModel = readRepoFile(
                "app/src/main/java/org/adaway/model/source/SourceModel.java");
        String compactSourceModel = compact(sourceModel);

        assertFalse("Parallel parse/download result paths must not add to a plain ArrayList.",
                compactSourceModel.contains("final List<HostsSource> " +
                        "deferredCarryForwardSources = new ArrayList<>();"));
        assertTrue("Deferred carry-forward storage must be safe for worker-thread adds.",
                compactSourceModel.contains("final List<HostsSource> " +
                        "deferredCarryForwardSources = Collections.synchronizedList(" +
                        "new ArrayList<>());") ||
                        compactSourceModel.contains("final Queue<HostsSource> " +
                                "deferredCarryForwardSources = new ConcurrentLinkedQueue<>();"));
        assertTrue("The guard must cover the parallel parse failure path.",
                compactSourceModel.contains("parseCompletion.submit(() -> {") &&
                        compactSourceModel.contains("deferredCarryForwardSources.add(" +
                                "result.source);"));
    }

    private static String readRepoFile(String relativePath) throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        Path path = root.resolve(relativePath);
        if (!Files.exists(path)) {
            Path parent = root.getParent();
            while (parent != null && !Files.exists(parent.resolve(relativePath))) {
                parent = parent.getParent();
            }
            if (parent != null) {
                path = parent.resolve(relativePath);
            }
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", " ");
    }
}
