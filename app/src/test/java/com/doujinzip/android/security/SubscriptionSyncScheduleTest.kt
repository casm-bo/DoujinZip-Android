package com.doujinzip.android.security

import org.junit.Assert.*
import org.junit.Test

class SubscriptionSyncScheduleTest {
    private val now=100_000_000L
    @Test fun retiredIntervalsMigrateToTheNextSupportedInterval() {
        for(minutes in SubscriptionPreferences.checkIntervals) assertEquals(minutes,normalizeSubscriptionCheckInterval(minutes))
        assertEquals(15,normalizeSubscriptionCheckInterval(5))
        assertEquals(15,normalizeSubscriptionCheckInterval(10))
        assertEquals(360,normalizeSubscriptionCheckInterval(180))
        assertEquals(1440,normalizeSubscriptionCheckInterval(720))
        assertEquals(60,normalizeSubscriptionCheckInterval(0))
    }
}
