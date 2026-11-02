package com.doujinzip.android.data

import com.doujinzip.android.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SubscriptionFeedTest {
    @Test fun unchangedProbeSkipsFullScanUntilDailyCheckAndManualAlwaysScans() {
        assertFalse(subscriptionFeedFullRefreshDue(100,1,"same","same",false))
        assertTrue(subscriptionFeedFullRefreshDue(100,1,"old","new",false))
        assertTrue(subscriptionFeedFullRefreshDue(100,1,"same","same",true))
        assertTrue(subscriptionFeedFullRefreshDue(86_400_001,1,"same","same",false))
        assertTrue(subscriptionFeedFullRefreshDue(100,1,"same",null,false))
    }

    @Test fun refreshPolicyHandlesFreshCacheExpiryManualRefreshAndClockRollback() {
        assertFalse(subscriptionFeedRefreshDue(30_000,1,true,false))
        assertTrue(subscriptionFeedRefreshDue(30_001,1,true,false))
        assertTrue(subscriptionFeedRefreshDue(10,1,true,true))
        assertTrue(subscriptionFeedRefreshDue(10,1,false,false))
        assertTrue(subscriptionFeedRefreshDue(1,10,true,false))
    }

    @Test fun combinesAllMatchingWorksDeduplicatesAndSortsNewestFirst() = runBlocking {
        val subscriptions=listOf(
            SearchSubscription(name="A", query="artist:alpha", enabled=true),
            SearchSubscription(name="B", query="tag:beta", enabled=true),
            SearchSubscription(name="Paused", query="tag:paused"),
            SearchSubscription(name="Deleted", query="tag:deleted", enabled=true, deleted=true))
        val calls=mutableListOf<String>()
        val ids=subscriptionFeedIds(subscriptions, emptySet(), emptySet()) { query ->
            calls+=query
            if(query=="artist:alpha") listOf(1L, 3L) else listOf(3L, 2L)
        }
        assertEquals(listOf(3L, 2L, 1L),ids)
        assertEquals(listOf("artist:alpha","tag:beta"), calls)
    }
    @Test fun preservesSubscriptionLanguagesExclusionsAndSearchTagFilters() = runBlocking {
        val value=SearchSubscription(name="A",query="artist:alpha -tag:excluded",languages=setOf("korean","english"),enabled=true)
        val calls=mutableListOf<String>()
        subscriptionFeedIds(listOf(value,value),setOf(SearchTagFilter.BL),setOf("tag:blocked")) {
            validateSubscriptionQuery(it); calls+=it; emptyList()
        }
        assertEquals(2,calls.size)
        assertTrue(calls.all { "-tag:excluded" in it && "-tag:yaoi" in it && "-tag:blocked" in it })
        assertTrue(calls.any { "language:korean" in it })
        assertTrue(calls.any { "language:english" in it })
    }
}
