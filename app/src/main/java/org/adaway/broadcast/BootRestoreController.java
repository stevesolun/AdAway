/*
 * Copyright (C) 2011-2012 Dominik Schurmann <dominik@dominikschuermann.de>
 *
 * This file is part of AdAway.
 *
 * AdAway is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdAway is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.adaway.broadcast;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.vpn.VpnServiceControls;

class BootRestoreController {
    private final VpnGateway mVpnGateway;

    BootRestoreController() {
        this(new PlatformVpnGateway());
    }

    BootRestoreController(VpnGateway vpnGateway) {
        this.mVpnGateway = vpnGateway;
    }

    void restoreAfterBoot(Context context) {
        AdBlockMethod adBlockMethod = PreferenceHelper.getAdBlockMethod(context);
        if (adBlockMethod != VPN || !PreferenceHelper.getVpnServiceOnBoot(context)) {
            return;
        }

        Intent prepareIntent = this.mVpnGateway.prepare(context);
        if (prepareIntent != null) {
            prepareIntent.addFlags(FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(prepareIntent);
            return;
        }

        this.mVpnGateway.start(context);
    }

    interface VpnGateway {
        @Nullable
        Intent prepare(Context context);

        boolean start(Context context);
    }

    private static class PlatformVpnGateway implements VpnGateway {
        @Nullable
        @Override
        public Intent prepare(Context context) {
            return android.net.VpnService.prepare(context);
        }

        @Override
        public boolean start(Context context) {
            return VpnServiceControls.start(context);
        }
    }
}
