package org.adaway.model.root;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.adaway.R;
import org.adaway.util.WebServerUtils;
import org.junit.Test;

public class NativeFeatureAvailabilityTest {
    @Test
    public void unavailableCaptureStartDoesNotRequireContextOrCleanup() {
        assertFalse(TcpdumpUtils.startTcpdump(null));
        assertFalse(TcpdumpUtils.startTcpdump(null));
        assertFalse(TcpdumpUtils.isTcpdumpRunning());
    }

    @Test
    public void unavailableCaptureStopIsInert() {
        TcpdumpUtils.stopTcpdump();
        TcpdumpUtils.stopTcpdump();
        assertFalse(TcpdumpUtils.isTcpdumpRunning());
    }

    @Test
    public void unavailableCaptureDoesNotReadOldLogs() {
        assertTrue(TcpdumpUtils.getLogs(null).isEmpty());
    }

    @Test
    public void unavailableWebServerControlsAndQueriesAreInert() {
        assertFalse(WebServerUtils.isWebServerAvailable(null));
        WebServerUtils.startWebServer(null);
        WebServerUtils.stopWebServer();
        assertFalse(WebServerUtils.isWebServerRunning());
        assertEquals(R.string.pref_webserver_state_unavailable,
                WebServerUtils.getWebServerState());
    }
}
