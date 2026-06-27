package org.adaway.model.source;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.*;

/**
 * Unit tests for FilterListsDirectoryApi — covers Phase 2 additions:
 * Tag, Language classes, tagIds/languageIds in ListSummary, parseTagsJson, parseLanguagesJson.
 */
public class FilterListsDirectoryApiTest {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Minimal /lists JSON with tagIds and languageIds arrays
    private static final String LISTS_JSON_WITH_TAGS = "[" +
            "{\"id\":1,\"name\":\"AdAway\",\"description\":\"Mobile ad blocker\"," +
            "\"syntaxIds\":[1],\"tagIds\":[1,2],\"languageIds\":[37]}," +
            "{\"id\":2,\"name\":\"StevenBlack\",\"description\":null," +
            "\"syntaxIds\":[],\"tagIds\":[],\"languageIds\":[]}" +
            "]";

    private static final String TAGS_JSON = "[" +
            "{\"id\":1,\"name\":\"Ads\",\"description\":\"Advertising block lists\"}," +
            "{\"id\":2,\"name\":\"Privacy\",\"description\":\"Tracking block lists\"}" +
            "]";

    private static final String LANGUAGES_JSON = "[" +
            "{\"id\":37,\"name\":\"English\",\"iso6391\":\"en\"}," +
            "{\"id\":42,\"name\":\"German\",\"iso6391\":\"de\"}" +
            "]";

    // ---- ListSummary tagIds + languageIds ----

    @Test
    public void parseListsJson_populatesTagIds() throws IOException {
        List<FilterListsDirectoryApi.ListSummary> lists =
                FilterListsDirectoryApi.parseListsJson(LISTS_JSON_WITH_TAGS);
        assertNotNull(lists);
        assertEquals(2, lists.size());

        FilterListsDirectoryApi.ListSummary first = lists.get(0);
        assertNotNull(first.tagIds);
        assertEquals(2, first.tagIds.length);
        assertEquals(1, first.tagIds[0]);
        assertEquals(2, first.tagIds[1]);
    }

    @Test
    public void parseListsJson_populatesLanguageIds() throws IOException {
        List<FilterListsDirectoryApi.ListSummary> lists =
                FilterListsDirectoryApi.parseListsJson(LISTS_JSON_WITH_TAGS);
        FilterListsDirectoryApi.ListSummary first = lists.get(0);
        assertNotNull(first.languageIds);
        assertEquals(1, first.languageIds.length);
        assertEquals(37, first.languageIds[0]);
    }

    @Test
    public void parseListsJson_emptyTagIds_notNull() throws IOException {
        List<FilterListsDirectoryApi.ListSummary> lists =
                FilterListsDirectoryApi.parseListsJson(LISTS_JSON_WITH_TAGS);
        FilterListsDirectoryApi.ListSummary second = lists.get(1);
        assertNotNull(second.tagIds);
        assertEquals(0, second.tagIds.length);
        assertNotNull(second.languageIds);
        assertEquals(0, second.languageIds.length);
    }

    // ---- Tag parsing ----

    @Test
    public void parseTagsJson_returnsTags() throws IOException {
        List<FilterListsDirectoryApi.Tag> tags =
                FilterListsDirectoryApi.parseTagsJson(TAGS_JSON);
        assertNotNull(tags);
        assertEquals(2, tags.size());

        FilterListsDirectoryApi.Tag first = tags.get(0);
        assertEquals(1, first.id);
        assertEquals("Ads", first.name);
        assertEquals("Advertising block lists", first.description);
    }

    @Test
    public void parseTagsJson_emptyArray_returnsEmptyList() throws IOException {
        List<FilterListsDirectoryApi.Tag> tags =
                FilterListsDirectoryApi.parseTagsJson("[]");
        assertNotNull(tags);
        assertTrue(tags.isEmpty());
    }

    // ---- Language parsing ----

    @Test
    public void parseLanguagesJson_returnsLanguages() throws IOException {
        List<FilterListsDirectoryApi.Language> languages =
                FilterListsDirectoryApi.parseLanguagesJson(LANGUAGES_JSON);
        assertNotNull(languages);
        assertEquals(2, languages.size());

        FilterListsDirectoryApi.Language first = languages.get(0);
        assertEquals(37, first.id);
        assertEquals("English", first.name);
        assertEquals("en", first.iso6391);
    }

    @Test
    public void parseLanguagesJson_emptyArray_returnsEmptyList() throws IOException {
        List<FilterListsDirectoryApi.Language> languages =
                FilterListsDirectoryApi.parseLanguagesJson("[]");
        assertNotNull(languages);
        assertTrue(languages.isEmpty());
    }

    // ---- Tag + Language inner classes ----

    @Test
    public void tag_constructorAndFields() {
        FilterListsDirectoryApi.Tag tag = new FilterListsDirectoryApi.Tag(5, "Security", "Malware lists");
        assertEquals(5, tag.id);
        assertEquals("Security", tag.name);
        assertEquals("Malware lists", tag.description);
    }

    @Test
    public void language_constructorAndFields() {
        FilterListsDirectoryApi.Language lang = new FilterListsDirectoryApi.Language(10, "French", "fr");
        assertEquals(10, lang.id);
        assertEquals("French", lang.name);
        assertEquals("fr", lang.iso6391);
    }

    @Test
    public void directoryJsonFetchesExpectedHttpsEndpoints() throws IOException {
        List<String> requestedPaths = new ArrayList<>();
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    assertEquals("https", request.url().scheme());
                    assertEquals("api.filterlists.com", request.url().host());
                    requestedPaths.add(request.url().encodedPath());

                    String body;
                    switch (request.url().encodedPath()) {
                        case "/lists":
                            body = LISTS_JSON_WITH_TAGS;
                            break;
                        case "/syntaxes":
                            body = "[{\"id\":1,\"name\":\"Hosts\"}]";
                            break;
                        case "/tags":
                            body = TAGS_JSON;
                            break;
                        case "/languages":
                            body = LANGUAGES_JSON;
                            break;
                        default:
                            return jsonResponse(request, 404, "{}");
                    }
                    return jsonResponse(request, 200, body);
                })
                .build();

        FilterListsDirectoryApi api = new FilterListsDirectoryApi(client);

        assertEquals(LISTS_JSON_WITH_TAGS, api.getListsJson());
        assertEquals("[{\"id\":1,\"name\":\"Hosts\"}]", api.getSyntaxesJson());
        assertEquals(TAGS_JSON, api.getTagsJson());
        assertEquals(LANGUAGES_JSON, api.getLanguagesJson());
        assertEquals(Arrays.asList("/lists", "/syntaxes", "/tags", "/languages"),
                requestedPaths);
    }

    @Test
    public void getListDetailsFetchesAndParsesNetworkResponse() throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    assertEquals("https", request.url().scheme());
                    assertEquals("api.filterlists.com", request.url().host());
                    assertEquals("/lists/42", request.url().encodedPath());
                    return jsonResponse(request, 200, "{"
                            + "\"id\":42,"
                            + "\"name\":\"Direct hosts\","
                            + "\"description\":\"Network detail\","
                            + "\"syntaxIds\":[1],"
                            + "\"viewUrls\":["
                            + "{\"segmentNumber\":0,\"primariness\":20,"
                            + "\"url\":\"https://example.com/page.html\"},"
                            + "{\"segmentNumber\":0,\"primariness\":10,"
                            + "\"url\":\"https://raw.githubusercontent.com/example/list/hosts.txt\"}"
                            + "]"
                            + "}");
                })
                .build();

        FilterListsDirectoryApi.ListDetails details =
                new FilterListsDirectoryApi(client).getListDetails(42);

        assertEquals(42, details.id);
        assertEquals("Direct hosts", details.name);
        assertEquals("Network detail", details.description);
        assertArrayEquals(new int[]{1}, details.syntaxIds);
        assertEquals(2, details.viewUrls.size());
        assertEquals("https://raw.githubusercontent.com/example/list/hosts.txt",
                details.pickBestDownloadUrl());
    }

    @Test
    public void getListsJson_httpFailureThrowsIOException() {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> jsonResponse(chain.request(), 503, "{}"))
                .build();

        try {
            new FilterListsDirectoryApi(client).getListsJson();
            fail("Expected HTTP failure to throw");
        } catch (IOException exception) {
            assertTrue(exception.getMessage(),
                    exception.getMessage().contains("HTTP 503 fetching "
                            + "https://api.filterlists.com/lists"));
        }
    }

    @Test
    public void getListsJson_offlineFailurePropagatesIOException() {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    throw new IOException("simulated offline");
                })
                .build();

        try {
            new FilterListsDirectoryApi(client).getListsJson();
            fail("Expected offline failure to throw");
        } catch (IOException exception) {
            assertEquals("simulated offline", exception.getMessage());
        }
    }

    @Test
    public void pickBestDownloadUrl_ignoresUnusableUrls() {
        FilterListsDirectoryApi.ListDetails details = new FilterListsDirectoryApi.ListDetails(
                1,
                "Example",
                "",
                new int[]{1},
                Arrays.asList(
                        new FilterListsDirectoryApi.ViewUrl(0, 10,
                                "http://example.com/hosts.txt"),
                        new FilterListsDirectoryApi.ViewUrl(0, 9,
                                "https://github.com/example/repo/blob/main/hosts.txt"),
                        new FilterListsDirectoryApi.ViewUrl(0, 8,
                                "https://raw.githubusercontent.com/example/repo/main/hosts.txt")
                ));

        assertEquals("https://raw.githubusercontent.com/example/repo/main/hosts.txt",
                details.pickBestDownloadUrl());
    }

    private static Response jsonResponse(Request request, int code, String body) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(code == 200 ? "OK" : "Failure")
                .body(ResponseBody.create(body, JSON))
                .build();
    }
}
