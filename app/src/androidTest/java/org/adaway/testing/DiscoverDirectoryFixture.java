package org.adaway.testing;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

public final class DiscoverDirectoryFixture {
    private DiscoverDirectoryFixture() {
    }

    public static void seed(Context context) {
        long now = System.currentTimeMillis();
        SharedPreferences.Editor editor = context.getSharedPreferences("filterlists_cache", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .putString("listsJson", "["
                        + "{\"id\":101,\"name\":\"Regional Ads Safe\","
                        + "\"description\":\"Blocks regional ads\","
                        + "\"syntaxIds\":[1],\"tagIds\":[7],\"languageIds\":[2]},"
                        + "{\"id\":102,\"name\":\"Regional Browser Unsupported\","
                        + "\"description\":\"Browser-only regional rules\","
                        + "\"syntaxIds\":[3],\"tagIds\":[7],\"languageIds\":[2]},"
                        + "{\"id\":103,\"name\":\"Regional German Safe\","
                        + "\"description\":\"Blocks regional ads in German\","
                        + "\"syntaxIds\":[1],\"tagIds\":[7],\"languageIds\":[3]},"
                        + "{\"id\":104,\"name\":\"Privacy Safe\","
                        + "\"description\":\"Blocks tracking\","
                        + "\"syntaxIds\":[1],\"tagIds\":[8],\"languageIds\":[2]}"
                        + "]")
                .putString("syntaxesJson", "["
                        + "{\"id\":1,\"name\":\"Hosts\"},"
                        + "{\"id\":3,\"name\":\"Browser rules\"}"
                        + "]")
                .putString("tagsJson", "["
                        + "{\"id\":7,\"name\":\"Regional\",\"description\":\"Regional lists\"},"
                        + "{\"id\":8,\"name\":\"Privacy\",\"description\":\"Privacy lists\"}"
                        + "]")
                .putString("languagesJson", "["
                        + "{\"id\":2,\"name\":\"English\",\"iso6391\":\"en\"},"
                        + "{\"id\":3,\"name\":\"German\",\"iso6391\":\"de\"}"
                        + "]")
                .putLong("cachedAt", now)
                .putLong("tagsCachedAt", now);
        assertTrue(editor.commit());
    }
}
