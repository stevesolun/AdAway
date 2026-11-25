package org.adaway.vpn;

import static android.net.VpnService.SERVICE_INTERFACE;
import static org.adaway.broadcast.Command.START;
import static org.adaway.broadcast.Command.STOP;
import static org.adaway.broadcast.Command.UNKNOWN;
import static org.junit.Assert.assertEquals;

import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.adaway.broadcast.Command;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VpnServiceCommandInstrumentedTest {
    @Test
    public void stickyRecreationStartsWithoutAnIntent() {
        assertEquals(START, VpnService.readServiceCommand(null));
    }

    @Test
    public void frameworkActionStartsWithoutPrivateCommandEvenWithOtherExtras() {
        Intent intent = new Intent(SERVICE_INTERFACE);
        assertEquals(START, VpnService.readServiceCommand(intent));
        intent.putExtra("unrelated", true);
        assertEquals(START, VpnService.readServiceCommand(intent));
    }

    @Test
    public void unrelatedUnflaggedIntentsDoNotStartVpn() {
        assertEquals(UNKNOWN, VpnService.readServiceCommand(new Intent()));
        assertEquals(UNKNOWN, VpnService.readServiceCommand(new Intent("unrelated")));
    }

    @Test
    public void explicitCommandsTakePrecedenceOverFrameworkFallback() {
        for (String action : new String[]{null, SERVICE_INTERFACE}) {
            for (Command command : new Command[]{START, STOP, UNKNOWN}) {
                Intent intent = new Intent(action);
                command.appendToIntent(intent);
                assertEquals(command, VpnService.readServiceCommand(intent));
            }
        }
    }

    @Test
    public void invalidPrivateCommandDoesNotFallBackToStart() {
        for (String value : new String[]{null, "", "start", "INVALID"}) {
            Intent intent = new Intent(SERVICE_INTERFACE).putExtra("COMMAND", value);
            assertEquals(UNKNOWN, VpnService.readServiceCommand(intent));
        }
        Intent wrongType = new Intent(SERVICE_INTERFACE).putExtra("COMMAND", 42);
        assertEquals(UNKNOWN, VpnService.readServiceCommand(wrongType));
    }

    @Test
    public void ordinaryCommandReaderStillRejectsAbsentCommand() {
        assertEquals(UNKNOWN, Command.readFromIntent(null));
        assertEquals(UNKNOWN, Command.readFromIntent(new Intent(SERVICE_INTERFACE)));
    }
}
