package org.adaway.broadcast;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ResolveInfo;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CommandReceiverSecurityInstrumentedTest {
    private static final String SEND_COMMAND_PERMISSION = "org.adaway.permission.SEND_COMMAND";

    @Test
    public void installedCommandReceiverIsExportedAndSignaturePermissionProtected()
            throws PackageManager.NameNotFoundException {
        Context context = ApplicationProvider.getApplicationContext();
        PackageManager packageManager = context.getPackageManager();

        ActivityInfo receiver = packageManager.getReceiverInfo(
                new ComponentName(context, CommandReceiver.class),
                0
        );
        PermissionInfo permission = packageManager.getPermissionInfo(SEND_COMMAND_PERMISSION, 0);

        assertTrue("CommandReceiver is the intentional external command API.",
                receiver.exported);
        assertEquals("Exported command receiver must require the custom permission.",
                SEND_COMMAND_PERMISSION, receiver.permission);
        assertEquals("Command permission must be owned by the app package.",
                context.getPackageName(), permission.packageName);
        assertEquals("Command permission must only grant to same-signature callers.",
                PermissionInfo.PROTECTION_SIGNATURE,
                permission.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE);
    }

    @Test
    public void sendCommandActionResolvesOnlyToProtectedCommandReceiver() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(CommandReceiver.SEND_COMMAND_ACTION)
                .setPackage(context.getPackageName());

        List<ResolveInfo> receivers = context.getPackageManager()
                .queryBroadcastReceivers(intent, 0);

        assertEquals("Exactly one installed receiver should handle the command action.",
                1, receivers.size());
        ActivityInfo receiver = receivers.get(0).activityInfo;
        assertEquals(context.getPackageName(), receiver.packageName);
        assertEquals(CommandReceiver.class.getName(), receiver.name);
        assertTrue("Resolved command receiver must be exported for the public API.",
                receiver.exported);
        assertEquals("Resolved command receiver must require the signature permission.",
                SEND_COMMAND_PERMISSION, receiver.permission);
    }

    @Test
    public void instrumentationPackageDoesNotHoldCommandPermissionByDefault() {
        Context context = ApplicationProvider.getApplicationContext();

        assertEquals("The instrumentation package should not hold the app command permission.",
                PERMISSION_DENIED,
                context.getPackageManager().checkPermission(
                        SEND_COMMAND_PERMISSION,
                        InstrumentationRegistry.getInstrumentation().getContext().getPackageName()
                ));
    }
}
