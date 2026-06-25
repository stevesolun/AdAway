/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.broadcast;

import static android.content.Intent.ACTION_BOOT_COMPLETED;
import static android.content.Intent.ACTION_PACKAGE_REPLACED;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.util.Constants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class BootReceiverBehaviorTest {
    private Context context;
    private AdBlockMethod originalMethod;
    private boolean originalVpnOnBoot;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        originalMethod = PreferenceHelper.getAdBlockMethod(context);
        originalVpnOnBoot = PreferenceHelper.getVpnServiceOnBoot(context);
    }

    @After
    public void tearDown() {
        PreferenceHelper.setAbBlockMethod(context, originalMethod);
        setVpnOnBoot(originalVpnOnBoot);
    }

    @Test
    public void bootReceiverIgnoresNonBootIntent() {
        RecordingBootRestoreController controller = new RecordingBootRestoreController();

        new BootReceiver(controller).onReceive(context, new Intent(ACTION_PACKAGE_REPLACED));

        assertEquals(0, controller.restoreCalls);
    }

    @Test
    public void bootReceiverDispatchesBootCompletedIntent() {
        RecordingBootRestoreController controller = new RecordingBootRestoreController();

        new BootReceiver(controller).onReceive(context, new Intent(ACTION_BOOT_COMPLETED));

        assertEquals(1, controller.restoreCalls);
    }

    @Test
    public void bootRestoreDoesNothingWhenVpnOnBootDisabledOrMethodIsNotVpn() {
        RecordingContext recordingContext = new RecordingContext(context);
        RecordingVpnGateway gateway = new RecordingVpnGateway();
        BootRestoreController controller = new BootRestoreController(gateway);

        PreferenceHelper.setAbBlockMethod(context, ROOT);
        setVpnOnBoot(true);
        controller.restoreAfterBoot(recordingContext);

        PreferenceHelper.setAbBlockMethod(context, VPN);
        setVpnOnBoot(false);
        controller.restoreAfterBoot(recordingContext);

        assertEquals(0, gateway.prepareCalls);
        assertEquals(0, gateway.startCalls);
        assertEquals(0, recordingContext.startedActivities.size());
    }

    @Test
    public void bootRestoreRequestsVpnPermissionWithoutStartingServiceWhenPermissionIsMissing() {
        RecordingContext recordingContext = new RecordingContext(context);
        RecordingVpnGateway gateway = new RecordingVpnGateway();
        gateway.permissionRequired = true;
        BootRestoreController controller = new BootRestoreController(gateway);

        PreferenceHelper.setAbBlockMethod(context, VPN);
        setVpnOnBoot(true);
        controller.restoreAfterBoot(recordingContext);

        assertEquals(1, gateway.prepareCalls);
        assertEquals(0, gateway.startCalls);
        assertEquals(1, recordingContext.startedActivities.size());
        assertTrue((recordingContext.startedActivities.get(0).getFlags()
                & FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void bootRestoreStartsVpnServiceWhenPermissionIsAlreadyGranted() {
        RecordingContext recordingContext = new RecordingContext(context);
        RecordingVpnGateway gateway = new RecordingVpnGateway();
        BootRestoreController controller = new BootRestoreController(gateway);

        PreferenceHelper.setAbBlockMethod(context, VPN);
        setVpnOnBoot(true);
        controller.restoreAfterBoot(recordingContext);

        assertEquals(1, gateway.prepareCalls);
        assertEquals(1, gateway.startCalls);
        assertEquals(0, recordingContext.startedActivities.size());
    }

    private void setVpnOnBoot(boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        prefs.edit()
                .putBoolean(context.getString(R.string.pref_vpn_service_on_boot_key), enabled)
                .commit();
    }

    private static class RecordingBootRestoreController extends BootRestoreController {
        private int restoreCalls;

        RecordingBootRestoreController() {
            super(new RecordingVpnGateway());
        }

        @Override
        void restoreAfterBoot(Context context) {
            restoreCalls++;
        }
    }

    private static class RecordingVpnGateway implements BootRestoreController.VpnGateway {
        private boolean permissionRequired;
        private int prepareCalls;
        private int startCalls;

        @Override
        public Intent prepare(Context context) {
            prepareCalls++;
            return permissionRequired ? new Intent("org.adaway.TEST_VPN_PERMISSION") : null;
        }

        @Override
        public boolean start(Context context) {
            startCalls++;
            return true;
        }
    }

    private static class RecordingContext extends ContextWrapper {
        private final List<Intent> startedActivities = new ArrayList<>();

        RecordingContext(Context base) {
            super(base);
        }

        @Override
        public void startActivity(Intent intent) {
            startedActivities.add(new Intent(intent));
        }
    }
}
