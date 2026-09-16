package com.ccb.common.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClientIpResolverTest {
    @Test
    void usesRemoteAddressWithoutForwardingHeader() {
        assertEquals("203.0.113.20", ClientIpResolver.resolve(null, "203.0.113.20"));
    }

    @Test
    void resolvesPublicClientBeforePrivateProxyChain() {
        assertEquals("203.0.113.10",
                ClientIpResolver.resolve("203.0.113.10, 172.18.0.1", "172.19.0.2"));
    }

    @Test
    void acceptsForwardedIpv4WithPort() {
        assertEquals("203.0.113.10", ClientIpResolver.resolve("203.0.113.10:443", "172.19.0.2"));
    }

    @Test
    void ignoresSpoofedHeaderOnPublicDirectConnection() {
        assertEquals("198.51.100.8", ClientIpResolver.resolve("203.0.113.99", "198.51.100.8"));
    }

    @Test
    void keepsFirstAddressWhenEntireChainIsPrivate() {
        assertEquals("10.0.0.25", ClientIpResolver.resolve("10.0.0.25, 172.18.0.1", "127.0.0.1"));
    }

    @Test
    void skipsUnknownAndMalformedForwardedValues() {
        assertEquals("203.0.113.30",
                ClientIpResolver.resolve("unknown, invalid-host, 203.0.113.30", "172.18.0.2"));
        assertNull(ClientIpResolver.resolve("unknown, invalid-host", null));
    }

    @Test
    void supportsIpv6AndPrivateIpv6Proxies() {
        assertEquals("2001:db8:0:0:0:0:0:25",
                ClientIpResolver.resolve("2001:db8::25, fc00::10", "::1"));
    }
}
