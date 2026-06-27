package org.adaway.ui.prefs;

import com.google.common.net.InetAddresses;

import java.net.InetAddress;

final class RedirectionAddressValidator {
    private RedirectionAddressValidator() {
    }

    static boolean isValid(Class<? extends InetAddress> addressType, String redirection) {
        if (addressType == null || redirection == null) {
            return false;
        }

        try {
            InetAddress inetAddress = InetAddresses.forString(redirection);
            return addressType.isAssignableFrom(inetAddress.getClass());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
