package org.adaway.model.source;

import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for FilterListsDirectoryApi — covers Phase 2 additions:
 * Tag, Language classes, tagIds/languageIds in ListSummary, parseTagsJson, parseLanguagesJson.
 */
public class FilterListsDirectoryApiTest {

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
}
