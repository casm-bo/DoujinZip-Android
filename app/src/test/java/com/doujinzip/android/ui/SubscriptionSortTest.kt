package com.doujinzip.android.ui

import com.doujinzip.android.data.SubscriptionRecord
import com.doujinzip.android.model.SearchSubscription
import org.junit.Assert.*
import org.junit.Test

class SubscriptionSortTest {
    @Test fun registrationSortIgnoresCheckTimesAndPreservesSourceOrder() {
        val records = listOf("Zebra", "Alpha", "Middle").mapIndexed { index, name ->
            SubscriptionRecord(SearchSubscription(id="synthetic-$index", name=name, query="artist:sample"),
                null, null, null, if (index == 1) 100 else 0, null)
        }
        assertEquals(listOf("Middle", "Alpha", "Zebra"), sortSavedSubscriptions(records, 0).map { it.value.name })
        assertEquals(listOf("Alpha", "Middle", "Zebra"), sortSavedSubscriptions(records, 1).map { it.value.name })
        assertEquals(records, sortSavedSubscriptions(records, 2))
        assertEquals(listOf("Zebra", "Alpha", "Middle"), records.map { it.value.name })
    }
}
