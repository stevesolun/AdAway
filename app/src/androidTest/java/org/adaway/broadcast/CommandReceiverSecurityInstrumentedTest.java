package org.adaway.broadcast;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

import org.adaway.AdAwayApplication;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    @Test
    public void packageWithoutCommandPermissionCannotDeliverCommandBroadcast()
            throws Exception {
        Context targetContext = ApplicationProvider.getApplicationContext();
        AdAwayApplication application =
                (AdAwayApplication) targetContext.getApplicationContext();
        AdBlockModel originalAdBlockModel = application.getAdBlockModel();
        RecordingAdBlockModel recordingAdBlockModel = new RecordingAdBlockModel(targetContext);
        injectAdBlockModel(application, recordingAdBlockModel);
        try {
            Intent intent = new Intent(CommandReceiver.SEND_COMMAND_ACTION)
                    .setComponent(new ComponentName(targetContext, CommandReceiver.class));
            Command.START.appendToIntent(intent);

            boolean blockedBySecurityException = false;
            try {
                InstrumentationRegistry.getInstrumentation().getContext().sendBroadcast(intent);
            } catch (SecurityException securityException) {
                blockedBySecurityException = true;
            }

            assertTrue("Broadcast should be blocked by the platform permission check or ignored.",
                    blockedBySecurityException ||
                            !recordingAdBlockModel.awaitCommand(1_500L));
            assertFalse("CommandReceiver must not apply ad blocking for an unpermitted sender.",
                    recordingAdBlockModel.wasApplied());
        } finally {
            injectAdBlockModel(application, originalAdBlockModel);
        }
    }

    private static void injectAdBlockModel(
            AdAwayApplication application,
            AdBlockModel adBlockModel) throws Exception {
        Field field = AdAwayApplication.class.getDeclaredField("adBlockModel");
        field.setAccessible(true);
        field.set(application, adBlockModel);
    }

    private static final class RecordingAdBlockModel extends AdBlockModel {
        private final CountDownLatch commandLatch = new CountDownLatch(1);
        private boolean applied;

        private RecordingAdBlockModel(Context context) {
            super(context);
        }

        @Override
        public AdBlockMethod getMethod() {
            return AdBlockMethod.ROOT;
        }

        @Override
        public void apply() throws HostErrorException {
            this.applied = true;
            this.commandLatch.countDown();
        }

        @Override
        public void revert() throws HostErrorException {
            this.commandLatch.countDown();
        }

        private boolean awaitCommand(long timeoutMs) throws InterruptedException {
            return this.commandLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
        }

        private boolean wasApplied() {
            return this.applied;
        }

        @Override
        public boolean isRecordingLogs() {
            return false;
        }

        @Override
        public void setRecordingLogs(boolean recording) {
            // No-op for the test model.
        }

        @Override
        public List<String> getLogs() {
            return Collections.emptyList();
        }

        @Override
        public void clearLogs() {
            // No-op for the test model.
        }
    }
}
