package org.adaway.model.source;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Minimal client for FilterLists Directory API.
 *
 * Endpoints documented at https://api.filterlists.com/index.html
 */
public final class FilterListsDirectoryApi {
    private static final String BASE_URL = "https://api.filterlists.com";

    private final OkHttpClient httpClient;

    public FilterListsDirectoryApi(@NonNull OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public static final class ListSummary {
        public final int id;
        public final String name;
        public final String description;
        public final int[] syntaxIds;
        public final int[] tagIds;
        public final int[] languageIds;

        public ListSummary(int id, String name, String description, int[] syntaxIds,
                int[] tagIds, int[] languageIds) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.syntaxIds = syntaxIds;
            this.tagIds = tagIds;
            this.languageIds = languageIds;
        }
    }

    public static final class Syntax {
        public final int id;
        public final String name;

        public Syntax(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static final class Tag {
        public final int id;
        public final String name;
        public final String description;

        public Tag(int id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
        }
    }

    public static final class Language {
        public final int id;
        public final String name;
        public final String iso6391;

        public Language(int id, String name, String iso6391) {
            this.id = id;
            this.name = name;
            this.iso6391 = iso6391;
        }
    }

    public static final class ViewUrl {
        public final int segmentNumber;
        public final int primariness;
        public final String url;

        public ViewUrl(int segmentNumber, int primariness, String url) {
            this.segmentNumber = segmentNumber;
            this.primariness = primariness;
            this.url = url;
        }
    }

    public static final class ListDetails {
        public final int id;
        public final String name;
        public final String description;
        public final int[] syntaxIds;
        public final List<ViewUrl> viewUrls;

        public ListDetails(int id, String name, String description, int[] syntaxIds, List<ViewUrl> viewUrls) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.syntaxIds = syntaxIds;
            this.viewUrls = viewUrls;
        }

        /**
         * Best effort: pick the primary viewUrl which looks like a raw text list.
         */
        @Nullable
        public String pickBestDownloadUrl() {
            ViewUrl best = null;
            for (ViewUrl v : viewUrls) {
                if (v.url == null) continue;
                if (best == null || v.primariness > best.primariness) {
                    best = v;
                }
            }
            if (best == null || best.url == null) return null;
            String u = best.url;
            // AdAway only supports HTTPS URLs or content:// URLs.
            // FilterLists "viewUrls" are typically direct list files; for all-syntax importing we accept any HTTPS viewUrl.
            return u.startsWith("https://") ? u : null;
        }
    }

    /**
     * Fetch all lists.
     */
    @NonNull
    public List<ListSummary> getLists() throws IOException {
        return parseListsJson(getListsJson());
    }

    @NonNull
    public Map<Integer, String> getSyntaxNames() throws IOException {
        return parseSyntaxNamesJson(getSyntaxesJson());
    }

    /**
     * Fetch raw lists JSON (for local caching).
     */
    @NonNull
    public String getListsJson() throws IOException {
        return getJson(BASE_URL + "/lists");
    }

    /**
     * Fetch raw syntaxes JSON (for local caching).
     */
    @NonNull
    public String getSyntaxesJson() throws IOException {
        return getJson(BASE_URL + "/syntaxes");
    }

    /**
     * Fetch raw tags JSON (for local caching).
     */
    @NonNull
    public String getTagsJson() throws IOException {
        return getJson(BASE_URL + "/tags");
    }

    /**
     * Fetch raw languages JSON (for local caching).
     */
    @NonNull
    public String getLanguagesJson() throws IOException {
        return getJson(BASE_URL + "/languages");
    }

    @NonNull
    public static List<ListSummary> parseListsJson(@NonNull String body) throws IOException {
        try {
            JSONArray arr = new JSONArray(body);
            List<ListSummary> out = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                int id = o.getInt("id");
                String name = o.optString("name", "");
                String desc = o.optString("description", null);
                int[] syntaxIds = toIntArray(o.optJSONArray("syntaxIds"));
                int[] tagIds = toIntArray(o.optJSONArray("tagIds"));
                int[] languageIds = toIntArray(o.optJSONArray("languageIds"));
                out.add(new ListSummary(id, name, desc, syntaxIds, tagIds, languageIds));
            }
            return out;
        } catch (JSONException e) {
            throw new IOException("Failed to parse FilterLists /lists JSON", e);
        }
    }

    @NonNull
    public static Map<Integer, String> parseSyntaxNamesJson(@NonNull String body) throws IOException {
        try {
            JSONArray arr = new JSONArray(body);
            Map<Integer, String> out = new HashMap<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                int id = o.getInt("id");
                String name = o.optString("name", "");
                out.put(id, name);
            }
            return out;
        } catch (JSONException e) {
            throw new IOException("Failed to parse FilterLists /syntaxes JSON", e);
        }
    }

    @NonNull
    public static List<Tag> parseTagsJson(@NonNull String body) throws IOException {
        try {
            JSONArray arr = new JSONArray(body);
            List<Tag> out = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                int id = o.getInt("id");
                String name = o.optString("name", "");
                String description = o.optString("description", null);
                out.add(new Tag(id, name, description));
            }
            return out;
        } catch (JSONException e) {
            throw new IOException("Failed to parse FilterLists /tags JSON", e);
        }
    }

    @NonNull
    public static List<Language> parseLanguagesJson(@NonNull String body) throws IOException {
        try {
            JSONArray arr = new JSONArray(body);
            List<Language> out = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                int id = o.getInt("id");
                String name = o.optString("name", "");
                String iso6391 = o.optString("iso6391", "");
                out.add(new Language(id, name, iso6391));
            }
            return out;
        } catch (JSONException e) {
            throw new IOException("Failed to parse FilterLists /languages JSON", e);
        }
    }

    /**
     * Fetch full details for one list.
     */
    @NonNull
    public ListDetails getListDetails(int id) throws IOException {
        String body = getJson(BASE_URL + "/lists/" + id);
        try {
            JSONObject o = new JSONObject(body);
            String name = o.optString("name", "");
            String desc = o.optString("description", null);
            int[] syntaxIds = toIntArray(o.optJSONArray("syntaxIds"));
            List<ViewUrl> viewUrls = new ArrayList<>();
            JSONArray v = o.optJSONArray("viewUrls");
            if (v != null) {
                for (int i = 0; i < v.length(); i++) {
                    JSONObject vo = v.getJSONObject(i);
                    viewUrls.add(new ViewUrl(
                            vo.optInt("segmentNumber", 0),
                            vo.optInt("primariness", 0),
                            vo.optString("url", null)
                    ));
                }
            }
            return new ListDetails(id, name, desc, syntaxIds, viewUrls);
        } catch (JSONException e) {
            throw new IOException("Failed to parse FilterLists /lists/{id} JSON", e);
        }
    }

    private String getJson(String url) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code() + " fetching " + url);
            }
            return response.body().string();
        }
    }

    private static int[] toIntArray(@Nullable JSONArray arr) {
        if (arr == null) return new int[0];
        int[] out = new int[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            out[i] = arr.optInt(i);
        }
        return out;
    }
}
