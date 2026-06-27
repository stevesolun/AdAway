package org.adaway.vpn.worker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

public class VpnConnectionMonitorTest {
    @Test
    public void resetRearmsMonitorAfterRecoveryStop() throws Exception {
        VpnConnectionMonitor monitor = new VpnConnectionMonitor(null);

        monitor.stop();
        assertFalse(isRunning(monitor));

        monitor.reset();

        assertTrue("Recovered VPN restarts must submit an active monitor loop.",
                isRunning(monitor));
    }

    private static boolean isRunning(VpnConnectionMonitor monitor) throws Exception {
        Field field = VpnConnectionMonitor.class.getDeclaredField("running");
        field.setAccessible(true);
        return ((AtomicBoolean) field.get(monitor)).get();
    }
}
