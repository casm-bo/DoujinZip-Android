package com.doujinzip.android.network

import org.junit.Assert.*
import org.junit.Test
class ServerDiscoveryTest {
    @Test fun acceptsOnlyPrivateNumericAddressesForUnauthenticatedProbes() {
        listOf("10.0.0.2", "192.168.10.2", "172.16.0.2", "172.31.255.254").forEach { assertTrue(discoveryIpv4(it)) }
        listOf("127.0.0.1", "8.8.8.8", "172.32.0.2", "192.168.1.999", "host.invalid", "192.168.1.1/path", "192.168.1.1:80").forEach { assertFalse(discoveryIpv4(it)) }
    }
}
