package com.doujinzip.android.data

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ServerEditStoreTest {
    @Test fun survivesRecreationAndDoesNotRemoveANewerProgressEdit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ServerEditStore(context)
        val profile = "synthetic-test-profile"
        val id = "progress:$profile:synthetic-book"
        fun body(mutation: String, page: Int) = JSONObject("""{"mutations":[{"mutationId":"$mutation","bookSyncId":"synthetic-book","baseVersion":2,"currentPage":$page}]}""")
        store.put(profile, id, "state", body("first", 3))
        val sent = ServerEditStore(context).pending(profile).single { it.first == id }.second
        store.put(profile, id, "state", body("second", 4))
        store.advanceProgress(id, sent, 3)
        store.remove(id, sent)
        val newer = store.pending(profile).single { it.first == id }.second
        val patch = newer.getJSONObject("body").getJSONArray("mutations").getJSONObject(0)
        assertEquals("second", patch.getString("mutationId"))
        assertEquals(4, patch.getInt("currentPage"))
        assertEquals(3, patch.getInt("baseVersion"))
        assertTrue(store.pending("another-synthetic-profile").none { it.first == id })
        store.remove(id, newer)
        assertTrue(store.pending(profile).none { it.first == id })
    }
}
