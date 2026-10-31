package com.doujinzip.android.data

import com.doujinzip.android.model.DownloadQueueItem
import com.doujinzip.android.model.DownloadStatus
import org.junit.Assert.*
import org.junit.Test

class DesktopCompletionTest {
    private fun item(id: Long, status: DownloadStatus) = DownloadQueueItem(id, 42, "Synthetic", null, null, status, 100, 1, 1, 0, null, "2026-01-01T00:00:00Z")
    @Test fun completionRequiresMatchingServerAndQueueItem() {
        val request = DownloadRequest(galleryId=42, title="Synthetic", profileId="test-pc", status=RequestStatus.SENT, desktopQueueId=8)
        assertEquals(RequestStatus.SENT, listOf(request).confirmDesktopCompletions("other-pc", listOf(item(8, DownloadStatus.COMPLETED))).single().status)
        assertEquals(RequestStatus.SENT, listOf(request).confirmDesktopCompletions("test-pc", listOf(item(9, DownloadStatus.COMPLETED))).single().status)
        assertEquals(RequestStatus.SENT, listOf(request).confirmDesktopCompletions("test-pc", listOf(item(8, DownloadStatus.DOWNLOADING))).single().status)
        assertEquals(RequestStatus.COMPLETED, listOf(request).confirmDesktopCompletions("test-pc", listOf(item(8, DownloadStatus.COMPLETED))).single().status)
    }
    @Test fun queueIdSurvivesStorage() {
        val request = DownloadRequest(galleryId=42,title="Synthetic",desktopQueueId=8)
        assertEquals(8L, decodeDownloadRequests(encodeDownloadRequests(listOf(request))).single().desktopQueueId)
    }
    @Test fun missedCompletionsAreRecoveredAfterRestart() {
        val requests = (1L..3L).map { id -> DownloadRequest(galleryId=42, title="Synthetic", profileId="test-pc",
            status=if (id == 1L) RequestStatus.COMPLETED else RequestStatus.SENT, desktopQueueId=id) }
        val restored = decodeDownloadRequests(encodeDownloadRequests(requests)).map { it.recover() }
        assertTrue(restored.confirmDesktopCompletions("test-pc", (1L..3L).map { item(it, DownloadStatus.COMPLETED) })
            .all { it.status == RequestStatus.COMPLETED })
        val interrupted = requests.last().copy(status=RequestStatus.SENDING, desktopAttempted=true).recover()
        assertEquals(RequestStatus.COMPLETED, listOf(interrupted).confirmDesktopCompletions("test-pc", listOf(item(3, DownloadStatus.COMPLETED))).single().status)
        assertEquals(RequestStatus.WAITING, listOf(interrupted.copy(desktopAttempted=false)).confirmDesktopCompletions("test-pc", listOf(item(3, DownloadStatus.COMPLETED))).single().status)
    }
}
