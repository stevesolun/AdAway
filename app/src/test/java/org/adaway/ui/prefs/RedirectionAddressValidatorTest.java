package org.adaway.ui.prefs;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

public class RedirectionAddressValidatorTest {
    @Test
    public void acceptsMatchingIpFamilies() {
        assertTrue(RedirectionAddressValidator.isValid(Inet4Address.class, "127.0.0.1"));
        assertTrue(RedirectionAddressValidator.isValid(Inet4Address.class, "0.0.0.0"));
        assertTrue(RedirectionAddressValidator.isValid(Inet6Address.class, "::1"));
        assertTrue(RedirectionAddressValidator.isValid(Inet6Address.class, "2001:db8::1"));
    }

    @Test
    public void rejectsCrossFamilyAddresses() {
        assertFalse(RedirectionAddressValidator.isValid(Inet4Address.class, "::1"));
        assertFalse(RedirectionAddressValidator.isValid(Inet6Address.class, "127.0.0.1"));
    }

    @Test
    public void rejectsNonIpInput() {
        assertFalse(RedirectionAddressValidator.isValid(Inet4Address.class, "localhost"));
        assertFalse(RedirectionAddressValidator.isValid(Inet4Address.class, "example.com"));
        assertFalse(RedirectionAddressValidator.isValid(Inet4Address.class, "127.0.0.1 "));
        assertFalse(RedirectionAddressValidator.isValid(Inet4Address.class, ""));
        assertFalse(RedirectionAddressValidator.isValid(Inet4Address.class, null));
        assertFalse(RedirectionAddressValidator.isValid(null, "127.0.0.1"));
    }

    @Test
    public void acceptsSubclassesOfRequestedFamily() {
        assertTrue(RedirectionAddressValidator.isValid(InetAddress.class, "127.0.0.1"));
        assertTrue(RedirectionAddressValidator.isValid(InetAddress.class, "::1"));
    }
}
