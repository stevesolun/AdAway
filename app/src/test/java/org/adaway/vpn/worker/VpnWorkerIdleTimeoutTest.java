package org.adaway.vpn.worker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VpnWorkerIdleTimeoutTest {
    @Test
    public void pollTimeoutWithoutPendingWorkIsIdle() {
        assertTrue(VpnWorker.isIdlePollTimeout(false, 0));
    }

    @Test
    public void pollTimeoutWithPendingDnsQueryBelongsToWatchdog() {
        assertFalse(VpnWorker.isIdlePollTimeout(false, 1));
    }

    @Test
    public void pollTimeoutWithPendingDeviceWriteBelongsToWatchdog() {
        assertFalse(VpnWorker.isIdlePollTimeout(true, 0));
    }
}
