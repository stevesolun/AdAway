package org.adaway.db.entity;

import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import org.adaway.util.RegexUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Objects;

import static org.adaway.db.entity.SourceType.FILE;
import static org.adaway.db.entity.SourceType.UNSUPPORTED;
import static org.adaway.db.entity.SourceType.URL;

/**
 * This entity represents a source to get hosts list.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@Entity(
        tableName = "hosts_sources",
        indices = {@Index(value = "url", unique = true)}
)
public class HostsSource {
    /**
     * The user source ID.
     */
    public static final int USER_SOURCE_ID = 1;
    /**
     * The user source URL.
     */
    public static final String USER_SOURCE_URL = "content://org.adaway/user/hosts";

    @PrimaryKey(autoGenerate = true)
    private int id;
    @NonNull
    private String label;
    @NonNull
    private String url;
    private boolean enabled = true;
    private boolean allowEnabled = false;
    private boolean redirectEnabled = false;
    @ColumnInfo(name = "last_modified_local")
    private ZonedDateTime localModificationDate;
    @ColumnInfo(name = "last_modified_online")
    private ZonedDateTime onlineModificationDate;
    /**
     * The HTTP ETag (strong from, may be <code>null</code>).
     */
    private String entityTag;
    /**
     * The number of hosts list items (<code>0</code> until synced).
     */
    private int size;
    /**
     * The number of hosts list items skipped during parsing (<code>0</code> until synced).
     */
    @ColumnInfo(name = "skipped_count")
    private int skippedCount;
    /**
     * The number of enabled active rows for this source.
     */
    @ColumnInfo(name = "active_rule_count")
    private int activeRuleCount;
    /**
     * The number of enabled active blocked rows for this source.
     */
    @ColumnInfo(name = "blocked_count")
    private int blockedCount;
    /**
     * The number of enabled active exact blocked rows for this source.
     */
    @ColumnInfo(name = "blocked_exact_count")
    private int blockedExactCount;
    /**
     * The number of enabled active allowed rows for this source.
     */
    @ColumnInfo(name = "allowed_count")
    private int allowedCount;
    /**
     * The number of enabled active redirected rows for this source.
     */
    @ColumnInfo(name = "redirected_count")
    private int redirectedCount;
    /**
     * The last download error message, or {@code null} if the last download succeeded.
     */
    @ColumnInfo(name = "last_download_error")
    @Nullable
    private String lastDownloadError;
    /**
     * FilterLists.com list identifier when this source came from the directory.
     */
    @ColumnInfo(name = "filter_list_id")
    @Nullable
    private Integer filterListId;
    /**
     * Comma-separated FilterLists syntax ids captured at subscription time.
     */
    @ColumnInfo(name = "filter_list_syntax_ids")
    @Nullable
    private String filterListSyntaxIds;
    /**
     * Conservative compatibility label shown for FilterLists-derived sources.
     */
    @ColumnInfo(name = "filter_list_compatibility")
    @Nullable
    private String filterListCompatibility;
    /**
     * Conservative compatibility score captured at subscription time.
     */
    @ColumnInfo(name = "filter_list_compatibility_score")
    private int filterListCompatibilityScore;
    /**
     * The exact selected URL from FilterLists viewUrls.
     */
    @ColumnInfo(name = "filter_list_selected_url")
    @Nullable
    private String filterListSelectedUrl;
    /**
     * Original FilterLists.com directory name captured at subscription time.
     */
    @ColumnInfo(name = "filter_list_name")
    @Nullable
    private String filterListName;
    /**
     * Comma-separated FilterLists tag ids captured at subscription time.
     */
    @ColumnInfo(name = "filter_list_tag_ids")
    @Nullable
    private String filterListTagIds;
    /**
     * Comma-separated FilterLists language ids captured at subscription time.
     */
    @ColumnInfo(name = "filter_list_language_ids")
    @Nullable
    private String filterListLanguageIds;

    /**
     * Check whether an URL is valid for as host source.<br>
     * A valid URL is a HTTPS URL or file URL.
     *
     * @param url The URL to check.
     * @return {@code true} if the URL is valid, {@code false} otherwise.
     */
    public static boolean isValidUrl(String url) {
        if (URLUtil.isContentUrl(url)) return true;
        if ("https://".equals(url) || !URLUtil.isHttpsUrl(url)) return false;
        // Block SSRF: reject URLs whose host is a private/reserved IP (ATK-02).
        try {
            String host = new URL(url).getHost();
            if (RegexUtils.isValidIP(host) && RegexUtils.isPrivateOrReservedIp(host)) {
                return false;
            }
        } catch (MalformedURLException ignored) {
            return false;
        }
        return true;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @NonNull
    public String getLabel() {
        return label;
    }

    public void setLabel(@NonNull String label) {
        this.label = label;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    public void setUrl(@NonNull String url) {
        this.url = url;
    }

    public SourceType getType() {
        if (this.url.startsWith("https://")) {
            return URL;
        } else if (this.url.startsWith("content://")) {
            return FILE;
        } else {
            return UNSUPPORTED;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAllowEnabled() {
        return allowEnabled;
    }

    public void setAllowEnabled(boolean allowEnabled) {
        this.allowEnabled = allowEnabled;
    }

    public boolean isRedirectEnabled() {
        return redirectEnabled;
    }

    public void setRedirectEnabled(boolean redirectEnabled) {
        this.redirectEnabled = redirectEnabled;
    }

    public ZonedDateTime getLocalModificationDate() {
        return localModificationDate;
    }

    public void setLocalModificationDate(ZonedDateTime localModificationDate) {
        this.localModificationDate = localModificationDate;
    }

    public ZonedDateTime getOnlineModificationDate() {
        return onlineModificationDate;
    }

    public void setOnlineModificationDate(ZonedDateTime lastOnlineModification) {
        this.onlineModificationDate = lastOnlineModification;
    }

    public String getEntityTag() {
        return this.entityTag;
    }

    public void setEntityTag(String entityTag) {
        this.entityTag = entityTag;
    }

    public int getSize() {
        return this.size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getSkippedCount() {
        return this.skippedCount;
    }

    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }

    public int getActiveRuleCount() {
        return this.activeRuleCount;
    }

    public void setActiveRuleCount(int activeRuleCount) {
        this.activeRuleCount = activeRuleCount;
    }

    public int getBlockedCount() {
        return this.blockedCount;
    }

    public void setBlockedCount(int blockedCount) {
        this.blockedCount = blockedCount;
    }

    public int getBlockedExactCount() {
        return this.blockedExactCount;
    }

    public void setBlockedExactCount(int blockedExactCount) {
        this.blockedExactCount = blockedExactCount;
    }

    public int getAllowedCount() {
        return this.allowedCount;
    }

    public void setAllowedCount(int allowedCount) {
        this.allowedCount = allowedCount;
    }

    public int getRedirectedCount() {
        return this.redirectedCount;
    }

    public void setRedirectedCount(int redirectedCount) {
        this.redirectedCount = redirectedCount;
    }

    @Nullable
    public String getLastDownloadError() {
        return this.lastDownloadError;
    }

    public void setLastDownloadError(@Nullable String lastDownloadError) {
        this.lastDownloadError = lastDownloadError;
    }

    @Nullable
    public Integer getFilterListId() {
        return this.filterListId;
    }

    public void setFilterListId(@Nullable Integer filterListId) {
        this.filterListId = filterListId;
    }

    @Nullable
    public String getFilterListSyntaxIds() {
        return this.filterListSyntaxIds;
    }

    public void setFilterListSyntaxIds(@Nullable String filterListSyntaxIds) {
        this.filterListSyntaxIds = filterListSyntaxIds;
    }

    @Nullable
    public String getFilterListCompatibility() {
        return this.filterListCompatibility;
    }

    public void setFilterListCompatibility(@Nullable String filterListCompatibility) {
        this.filterListCompatibility = filterListCompatibility;
    }

    public int getFilterListCompatibilityScore() {
        return this.filterListCompatibilityScore;
    }

    public void setFilterListCompatibilityScore(int filterListCompatibilityScore) {
        this.filterListCompatibilityScore = filterListCompatibilityScore;
    }

    @Nullable
    public String getFilterListSelectedUrl() {
        return this.filterListSelectedUrl;
    }

    public void setFilterListSelectedUrl(@Nullable String filterListSelectedUrl) {
        this.filterListSelectedUrl = filterListSelectedUrl;
    }

    @Nullable
    public String getFilterListName() {
        return this.filterListName;
    }

    public void setFilterListName(@Nullable String filterListName) {
        this.filterListName = filterListName;
    }

    @Nullable
    public String getFilterListTagIds() {
        return this.filterListTagIds;
    }

    public void setFilterListTagIds(@Nullable String filterListTagIds) {
        this.filterListTagIds = filterListTagIds;
    }

    @Nullable
    public String getFilterListLanguageIds() {
        return this.filterListLanguageIds;
    }

    public void setFilterListLanguageIds(@Nullable String filterListLanguageIds) {
        this.filterListLanguageIds = filterListLanguageIds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HostsSource that = (HostsSource) o;

        if (id != that.id) return false;
        if (enabled != that.enabled) return false;
        if (!url.equals(that.url)) return false;
        if (!Objects.equals(localModificationDate, that.localModificationDate))
            return false;
        if (!Objects.equals(onlineModificationDate, that.onlineModificationDate)) return false;
        return Objects.equals(lastDownloadError, that.lastDownloadError);

    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + url.hashCode();
        result = 31 * result + (enabled ? 1 : 0);
        result = 31 * result + (localModificationDate != null ? localModificationDate.hashCode() : 0);
        result = 31 * result + (onlineModificationDate != null ? onlineModificationDate.hashCode() : 0);
        result = 31 * result + (lastDownloadError != null ? lastDownloadError.hashCode() : 0);
        return result;
    }
}
