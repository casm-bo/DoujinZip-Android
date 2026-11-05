package com.doujinzip.android.data

import com.doujinzip.android.model.DesktopProfile
import com.doujinzip.android.model.SearchSubscription
import com.doujinzip.android.network.CompanionClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SubscriptionSync(private val store: SubscriptionStore, private val client: CompanionClient = CompanionClient()) {
    suspend fun sync(profile: DesktopProfile): Unit = mutex.withLock {
        store.reconcileMatchingConflicts()
        val scope = profile.id
        // Retry receipts before pulling; a lost acknowledgement is not a concurrent PC edit.
        for(record in store.records(scope).filter { it.mutationId != null && it.conflict == null }) {
            val response = client.saveSubscription(profile,requireNotNull(record.mutationId),record.value)
            val remote = SearchSubscription.fromJson(response.getJSONObject("value"))
            when(response.getString("status")) {
                "applied" -> { require(remote.id == record.value.id); store.receive(scope,remote,record.mutationId) }
                "conflict" -> store.conflict(scope,record.value.id,remote)
                else -> error("알 수 없는 구독 동기화 응답입니다.")
            }
        }
        do {
            val reads = store.pendingReads(scope)
            if(reads.isNotEmpty()) { client.readSubscriptionNotices(profile,reads); store.acknowledgeReads(scope,reads) }
        } while(reads.isNotEmpty())
        var cursor = store.state("cursor/$scope")?.toLong() ?: 0L
        do {
            val response = client.subscriptionChanges(profile,cursor)
            val next = response.getLong("cursor")
            val more = response.getBoolean("hasMore")
            require(next >= cursor && (!more || next > cursor)) { "구독 동기화 위치가 올바르지 않습니다." }
            val changes = response.getJSONArray("changes")
            for(i in 0 until changes.length()) {
                val change = changes.getJSONObject(i)
                when(change.getString("kind")) {
                    "subscription" -> store.receive(scope,SearchSubscription.fromJson(change.getJSONObject("value")))
                    "read" -> store.markRead(scope,change.getJSONObject("value").getLong("galleryId"),dirty=false)
                    else -> error("지원하지 않는 구독 변경입니다.")
                }
            }
            cursor = next
            store.setState("cursor/$scope",cursor.toString())
        } while(more)
    }
    companion object { private val mutex = Mutex() }
}
