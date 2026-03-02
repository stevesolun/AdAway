package org.adaway.ui.lists;

/**
 * This class represents the filter to apply to host lists.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class ListsFilter {
    public static final ListsFilter ALL = new ListsFilter(true, "");
    /**
     * Whether included hosts from sources or not.
     */
    public final boolean sourcesIncluded;
    /**
     * The query filter to apply to hosts name (wildcard based).
     */
    public final String query;
    /**
     * The query filter to apply to hosts name (sql like format).
     */
    public final String sqlQuery;

    public ListsFilter(boolean sourcesIncluded, String query) {
        this.sourcesIncluded = sourcesIncluded;
        this.query = query;
        this.sqlQuery = convertToLikeQuery(query);
    }

    /**
     * Returns {@code true} when no query text has been entered (i.e. the filter shows all items).
     * Used by the search bar restore path in AbstractListFragment.
     */
    public boolean isEmpty() {
        return query.isEmpty();
    }

    private static String convertToLikeQuery(String query) {
        return "%" + query.replace("*", "%")
                .replace("?", "_") + "%";
    }
}
