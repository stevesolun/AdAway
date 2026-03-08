package org.adaway.db.entity;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link HostsSource#equals} and {@link HostsSource#hashCode},
 * specifically guarding that lastDownloadError participates in both.
 *
 * DiffUtil.ItemCallback.areContentsTheSame() relies on equals() to detect when a
 * source transitions from error → success (or vice versa) and must redraw the card.
 * If lastDownloadError were absent from equals/hashCode, the RecyclerView would never
 * refresh the error text view after a download succeeded.
 */
public class HostsSourceTest {

    /**
     * Build a HostsSource with the minimum fields required for equals() not to NPE.
     * equals() calls url.equals() and Objects.equals() on date fields.
     */
    private static HostsSource makeSource(String label, String url, String lastDownloadError) {
        HostsSource source = new HostsSource();
        source.setLabel(label);
        source.setUrl(url);
        source.setLastDownloadError(lastDownloadError);
        return source;
    }

    /**
     * equals() must return false when lastDownloadError differs — this is the DiffUtil
     * guard that triggers RecyclerView rebind when an error message changes.
     */
    @Test
    public void equals_differsByLastDownloadError_returnsFalse() {
        HostsSource source1 = makeSource("AdAway", "https://adaway.org/hosts.txt", "Connection refused");
        HostsSource source2 = makeSource("AdAway", "https://adaway.org/hosts.txt", "Timeout after 30s");

        assertFalse("equals() must return false when lastDownloadError differs (asymmetry check 1→2)",
                source1.equals(source2));
        assertFalse("equals() must return false when lastDownloadError differs (symmetric check 2→1)",
                source2.equals(source1));
    }

    /**
     * hashCode() must differ when lastDownloadError differs — this ensures HashMap/Set
     * operations correctly distinguish sources with different error states.
     */
    @Test
    public void hashCode_differsByLastDownloadError() {
        HostsSource source1 = makeSource("AdAway", "https://adaway.org/hosts.txt", "Connection refused");
        HostsSource source2 = makeSource("AdAway", "https://adaway.org/hosts.txt", "Timeout after 30s");

        assertNotEquals(
                "hashCode() must differ when lastDownloadError differs",
                source1.hashCode(), source2.hashCode());
    }

    /**
     * equals() must return true when both sources have null lastDownloadError — this
     * represents the common "no error" state and must not cause unnecessary redraws.
     */
    @Test
    public void equals_nullLastDownloadError_bothNull_returnsTrue() {
        HostsSource source1 = makeSource("AdAway", "https://adaway.org/hosts.txt", null);
        HostsSource source2 = makeSource("AdAway", "https://adaway.org/hosts.txt", null);

        assertTrue("equals() must return true when both lastDownloadError are null",
                source1.equals(source2));
        assertEquals(
                "hashCode() must be the same when both lastDownloadError are null",
                source1.hashCode(), source2.hashCode());
    }
}
