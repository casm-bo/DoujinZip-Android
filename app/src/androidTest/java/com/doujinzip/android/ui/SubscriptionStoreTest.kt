package com.doujinzip.android.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.data.SubscriptionStore
import com.doujinzip.android.model.SearchSubscription
import com.doujinzip.android.model.GallerySummary
import org.junit.Assert.*
import org.junit.Test

class SubscriptionStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun gallery(id: Long) = GallerySummary(id,"Example $id",listOf("Example"),emptyList(),null,emptyList(),null,12,"korean")
    @Test fun favoritesMigrateOnceAndRemainInactiveWithNamesAndQueriesUnchanged() {
        val isolated=object:android.content.ContextWrapper(context) {
            override fun getSharedPreferences(name:String,mode:Int)=context.getSharedPreferences("synthetic_subscription_favorites",mode)
        }
        val preferences=com.doujinzip.android.security.BrowserPreferenceStore(isolated)
        try {
            preferences.saveFavorites(listOf(com.doujinzip.android.model.SearchFavorite("synthetic-favorite","Saved title","title words")))
            SubscriptionStore(context,null).use { store ->
                store.migrateFavorites(isolated,"")
                store.migrateFavorites(isolated,"")
                val record=store.records("").single()
                assertEquals("Saved title",record.value.name);assertEquals("title words",record.value.query);assertFalse(record.value.enabled)
                store.adoptLocal("pc")
                assertEquals("title words",store.records("pc").single().value.query)
                assertTrue(store.records("").isEmpty())
            }
        } finally { isolated.getSharedPreferences("unused",0).edit().clear().commit() }
    }
    @Test fun baselineFailureDeduplicationReadAndScopeIsolation() {
        SubscriptionStore(context,null).use { store ->
            val value = SearchSubscription(name="Example",query="artist:example",enabled=true)
            store.save("pc-a",value)
            store.failed("pc-a",value.id,"Offline")
            assertNull(store.records("pc-a").single().baseline)
            store.checked("pc-a",value,listOf(10),emptyList(),100)
            assertTrue(store.notices("pc-a").isEmpty())
            store.markRead("pc-a",12)
            store.checked("pc-a",value,listOf(12,11,10),listOf(gallery(12),gallery(11)),200)
            val second=value.copy(id="second-example",query="tag:example")
            store.save("pc-a",second)
            store.checked("pc-a",second,listOf(10),emptyList(),100)
            store.checked("pc-a",second,listOf(12,11,10),listOf(gallery(12),gallery(11)),200)
            assertEquals(2,store.notices("pc-a").size)
            assertTrue(store.notices("pc-a").first().read)
            assertEquals(2,store.notices("pc-a").first().subscriptions.size)
            assertTrue(store.notices("pc-b").isEmpty())
            assertTrue(store.records("pc-b").isEmpty())
        }
    }
    @Test fun staleChecksCannotPublishAfterDeletionAndConflictsKeepBothEdits() {
        SubscriptionStore(context,null).use { store ->
            val value=SearchSubscription(name="Phone",query="tag:example",enabled=true)
            store.save("pc",value)
            store.save("pc",value.copy(deleted=true))
            assertFalse(store.checked("pc",value,listOf(20),emptyList(),100))
            store.receive("pc",value.copy(name="Desktop",version=2))
            assertEquals("Phone",store.records("pc").single().value.name)
            assertEquals("Desktop",store.records("pc").single().conflict!!.name)
            store.resolve("pc",value.id,true)
            assertEquals(2L,store.records("pc").single().value.version)
            assertNull(store.records("pc").single().conflict)
        }
    }
    @Test fun matchingChangesAcceptRemoteVersionButDifferentEditsStillConflict() {
        SubscriptionStore(context, null).use { store ->
            val value = SearchSubscription(id = "fixture", name = "Example", query = "tag:example")
            store.save("pc", value)
            store.receive("pc", value.copy(version = 2))
            assertEquals(2L, store.records("pc").single().value.version)
            assertNull(store.records("pc").single().mutationId)

            store.save("pc", value.copy(enabled = true))
            store.conflict("pc", value.id, value.copy(enabled = true, version = 3))
            assertEquals(3L, store.records("pc").single().value.version)
            assertNull(store.records("pc").single().mutationId)

            store.save("pc", value.copy(name = "Phone"))
            store.conflict("pc", value.id, value.copy(name = "PC", version = 4))
            assertEquals("Phone", store.records("pc").single().value.name)
            assertEquals("PC", store.records("pc").single().conflict?.name)
        }
    }

    @Test fun matchingStoredConflictsReconcileInInactiveScopes() {
        SubscriptionStore(context, null).use { store ->
            val value = SearchSubscription(id = "fixture", name = "Example", query = "tag:example")
            store.save("previous-pc", value)
            store.writableDatabase.execSQL(
                "UPDATE subscriptions SET conflict=? WHERE scope=? AND id=?",
                arrayOf(value.copy(version = 2).json().toString(), "previous-pc", value.id),
            )
            store.reconcileMatchingConflicts()
            val record = store.records("previous-pc").single()
            assertEquals(2L, record.value.version)
            assertNull(record.conflict)
            assertNull(record.mutationId)
        }
    }

    @Test fun notificationsAndPendingEditsSurviveDatabaseReopen() {
        val name="subscription-test-${System.nanoTime()}.db"
        try {
            SubscriptionStore(context,name).use { store ->
                val value=SearchSubscription(id="example",name="Saved",query="tag:example",enabled=true)
                store.save("pc",value)
                store.checked("pc",value,listOf(1),emptyList(),1)
                store.checked("pc",value,listOf(2,1),listOf(gallery(2)),2)
                store.markRead("pc",2)
            }
            SubscriptionStore(context,name).use { store ->
                assertNotNull(store.records("pc").single().mutationId)
                assertEquals(2L,store.records("pc").single().baseline)
                assertTrue(store.notices("pc").single().read)
                assertEquals(listOf(2L),store.pendingReads("pc"))
            }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun acknowledgementKeepsNewerPhoneEditsAndLocalAdoptionKeepsNotices() {
        SubscriptionStore(context,null).use { store ->
            val value=SearchSubscription(id="example",name="Before",query="tag:example",enabled=true)
            store.save("",value)
            store.checked("",value,listOf(1),emptyList(),1)
            store.checked("",value,listOf(2,1),listOf(gallery(2)),2)
            store.adoptLocal("pc")
            assertTrue(store.records("").isEmpty())
            assertEquals(2L,store.notices("pc").single().galleryId)
            val mutation=store.records("pc").single().mutationId!!
            store.save("pc",value.copy(name="After"))
            store.receive("pc",value.copy(version=1),mutation)
            assertEquals("After",store.records("pc").single().value.name)
            assertEquals(1L,store.records("pc").single().value.version)
            assertNull(store.records("pc").single().conflict)
            assertNotNull(store.records("pc").single().mutationId)
        }
    }
}
