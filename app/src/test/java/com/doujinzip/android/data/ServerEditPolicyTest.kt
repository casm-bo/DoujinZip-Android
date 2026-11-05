package com.doujinzip.android.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ServerEditPolicyTest {
    private fun body() = JSONObject("""{"mutations":[{"mutationId":"synthetic-edit","bookSyncId":"synthetic-book","baseVersion":4,"isFavorite":true}]}""")
    @Test fun resetAcknowledgementMustMatchTheQueuedRequest() {
        val body = JSONObject("""{"mutationId":"synthetic-reset"}""")
        assertEquals(listOf("applied"), validateServerEditResponse("reset-cover", body, JSONObject("""{"mutationId":"synthetic-reset","status":"applied"}""")))
        assertTrue(runCatching { validateServerEditResponse("reset-cover", body, JSONObject("""{"mutationId":"wrong-reset","status":"applied"}""")) }.isFailure)
    }
    @Test fun acceptsAnExplicitConflictWithoutPretendingItWasApplied() {
        val response = JSONObject("""{"results":[{"mutationId":"synthetic-edit","status":"conflict"}]}""")
        assertEquals(listOf("conflict"), validateServerEditResponse("state", body(), response))
        assertFalse(body().getJSONArray("mutations").getJSONObject(0).has("currentPage"))
    }
    @Test fun lostOrMismatchedAcknowledgementsMustNotClearPendingRequests() {
        for (raw in listOf("""{"results":[]}""", """{"results":[{"mutationId":"another-edit","status":"applied"}]}""")) {
            assertTrue(runCatching { validateServerEditResponse("state", body(), JSONObject(raw)) }.isFailure)
        }
    }
    @Test fun metadataResponseMustMatchTheEditedResource() {
        val body = JSONObject("""{"value":{"key":"hitomi:123"}}""")
        val response = JSONObject("""{"status":"applied","value":{"key":"hitomi:456"}}""")
        assertTrue(runCatching { validateServerEditResponse("metadata", body, response) }.isFailure)
    }
}
