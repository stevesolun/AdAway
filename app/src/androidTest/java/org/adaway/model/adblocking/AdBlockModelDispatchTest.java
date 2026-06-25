/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.model.adblocking;

import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.adaway.model.adblocking.AdBlockMethod.UNDEFINED;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.adaway.AdAwayApplication;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.root.RootModel;
import org.adaway.model.vpn.VpnModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AdBlockModelDispatchTest {
    private AdAwayApplication application;
    private AdBlockMethod originalMethod;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        application = (AdAwayApplication) context.getApplicationContext();
        originalMethod = PreferenceHelper.getAdBlockMethod(application);
    }

    @After
    public void tearDown() {
        PreferenceHelper.setAbBlockMethod(application, originalMethod);
        application.getAdBlockModel();
    }

    @Test
    public void getAdBlockModelDispatchesUndefinedRootAndVpnMethods() {
        PreferenceHelper.setAbBlockMethod(application, UNDEFINED);
        AdBlockModel undefinedModel = application.getAdBlockModel();

        assertTrue(undefinedModel instanceof UndefinedBlockModel);
        assertSame(UNDEFINED, undefinedModel.getMethod());

        PreferenceHelper.setAbBlockMethod(application, ROOT);
        AdBlockModel rootModel = application.getAdBlockModel();

        assertTrue(rootModel instanceof RootModel);
        assertSame(ROOT, rootModel.getMethod());

        PreferenceHelper.setAbBlockMethod(application, VPN);
        AdBlockModel vpnModel = application.getAdBlockModel();

        assertTrue(vpnModel instanceof VpnModel);
        assertSame(VPN, vpnModel.getMethod());
    }

    @Test
    public void getAdBlockModelRebuildsCachedModelWhenStoredMethodChanges() {
        PreferenceHelper.setAbBlockMethod(application, ROOT);
        AdBlockModel rootModel = application.getAdBlockModel();

        PreferenceHelper.setAbBlockMethod(application, VPN);
        AdBlockModel vpnModel = application.getAdBlockModel();
        AdBlockModel cachedVpnModel = application.getAdBlockModel();

        assertTrue(rootModel instanceof RootModel);
        assertTrue(vpnModel instanceof VpnModel);
        assertNotSame(rootModel, vpnModel);
        assertSame(vpnModel, cachedVpnModel);
    }
}
