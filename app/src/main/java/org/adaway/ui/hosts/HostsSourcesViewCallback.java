package org.adaway.ui.hosts;

import android.content.Context;

import org.adaway.db.entity.HostsSource;

/**
 * This class is represents the {@link HostsSourcesFragment} callback.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public interface HostsSourcesViewCallback {
    /**
     * Get the application context.
     *
     * @return The application context.
     */
    Context getContext();

    /**
     * Set host source enable status.
     *
     * @param source  The hosts source to update.
     * @param enabled The desired enabled state.
     */
    void setEnabled(HostsSource source, boolean enabled);

    /**
     * Update (download + parse) a single hosts source.
     */
    void updateSource(HostsSource source);

    /**
     * Update (download + parse) all enabled hosts sources.
     */
    void updateAllSources();

    /**
     * Request to add a custom source (used by the "Custom" category when empty).
     */
    void requestAddCustomSource();

    /**
     * Start an action.
     *
     * @param source     The hosts source to start the action.
     */
    void edit(HostsSource source);
}
