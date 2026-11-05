package com.doujinzip.android.security

import org.junit.Assert.*
import org.junit.Test

class PairingIdentityTest {
    @Test fun identityIsStableAndDoesNotContainTheSourceIdentifier() {
        val identity = hashPairingIdentity("synthetic-device-a")
        assertEquals(identity, hashPairingIdentity("synthetic-device-a"))
        assertTrue(identity.matches(Regex("[a-f0-9]{64}")))
        assertNotEquals(identity, hashPairingIdentity("synthetic-device-b"))
    }
}
