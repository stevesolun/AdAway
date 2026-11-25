package org.adaway.util;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

/** Complements the resolver/transport tests: guard the actual production client creation sites. */
public class SourceHttpWiringTest {
    @Test
    public void sourceBodiesGenericHeadAndGitMetadataAllUseTheGuardedClient() throws Exception {
        Path root = Paths.get("src/main/java/org/adaway");
        if (!Files.isDirectory(root)) root = Paths.get("app").resolve(root);
        String sources = new String(Files.readAllBytes(root.resolve("model/source/SourceModel.java")),
                StandardCharsets.UTF_8);
        String git = new String(Files.readAllBytes(root.resolve("model/git/GitHostsJsonApiSource.java")),
                StandardCharsets.UTF_8);
        assertTrue(sources.contains("cachedHttpClient = SourceHttpPolicy.newClientBuilder()"));
        assertTrue(sources.contains("getHttpClient().newCall(request)"));
        assertTrue(sources.contains("cachedHeadClient = SourceHttpPolicy.newClientBuilder()"));
        assertTrue(sources.contains("getHeadClient().newCall(request)"));
        assertTrue(sources.contains("GitHostsSource.getSource(url).getLastUpdate()"));
        assertTrue(git.contains("getLastUpdateFromApi(getCommitApiUrl())"));
        assertTrue(git.contains("client = SourceHttpPolicy.newClientBuilder().build()"));
        assertTrue(git.contains("client.newCall(request).execute()"));
        assertFalse(sources.contains("new OkHttpClient("));
        assertFalse(sources.contains("new OkHttpClient.Builder("));
        assertFalse(git.contains("new OkHttpClient("));
        assertFalse(git.contains("new OkHttpClient.Builder("));
    }
}
