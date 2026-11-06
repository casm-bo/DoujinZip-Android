package com.doujinzip.android.data

import org.junit.Assert.*
import org.junit.Test

class DownloadRequestTest {
    @Test fun `deleting owned books permits redownload without clearing other targets`() {
        val local = DownloadRequest(galleryId = 42, title = "Fixture", target = DownloadTarget.LOCAL, status = RequestStatus.COMPLETED)
        val pc = local.copy(id = "pc", target = DownloadTarget.DESKTOP, profileId = "pc")
        val pending = local.copy(id = "pending", status = RequestStatus.PAUSED)
        val book = com.doujinzip.android.model.LibraryBook("fixture", "Fixture", locationUri = "", locationName = "", folderUri = "",
            pages = emptyList(), modifiedAt = 1, metadata = com.doujinzip.android.model.LibraryMetadata(hitomiId = "42"))
        assertEquals(listOf(pc, pending), listOf(local, pc, pending).withoutDeletedBooks(listOf(book), "pc"))
        assertEquals(listOf(local, pending), listOf(local, pc, pending).withoutDeletedBooks(listOf(book.copy(isCloud = true)), "pc"))
        assertEquals(listOf(pc), listOf(pc).withoutDeletedBooks(listOf(book.copy(isCloud = true)), "other"))
        assertEquals(1, addDownloadRequest(listOf(local).withoutDeletedBooks(listOf(book), null), local.copy(id = "new")).size)
    }

    @Test fun `queue round trip preserves target and desktop without starting work`() {
        val request = DownloadRequest(galleryId = 42, title = "제목", profileId = "pc-one")
        val restored = decodeDownloadRequests(encodeDownloadRequests(listOf(request))).single().recover()
        assertEquals(request, restored)
        assertEquals(RequestStatus.WAITING, restored.status)
        assertEquals(DownloadTarget.DESKTOP, restored.target)
        assertFalse(restored.isBusy)
    }

    @Test fun `duplicate click does not add another pending request`() {
        val request = DownloadRequest(galleryId = 42, title = "Example")
        assertEquals(listOf(request), addDownloadRequest(listOf(request), request.copy(id = "other")))
        for (status in listOf(RequestStatus.SENT, RequestStatus.COMPLETED)) {
            val finished = request.copy(status = status)
            assertEquals(listOf(finished), addDownloadRequest(listOf(finished), request))
        }
        val local = request.copy(target = DownloadTarget.LOCAL, status = RequestStatus.COMPLETED)
        assertEquals(listOf(local), addDownloadRequest(listOf(local), local.copy(id = "other")))
        assertEquals(2, addDownloadRequest(listOf(local), request).size)
        assertEquals(2, addDownloadRequest(listOf(request.copy(profileId = "other-pc", status = RequestStatus.COMPLETED)), request).size)
    }

    @Test fun `interrupted local work never becomes desktop work or starts itself`() {
        for (status in listOf(RequestStatus.RUNNING, RequestStatus.WAITING)) {
            val recovered = DownloadRequest(galleryId = 42, title = "Example", target = DownloadTarget.LOCAL,
                status = status, downloadedFiles = 3, totalFiles = 5).recover()
            assertEquals(RequestStatus.PAUSED, recovered.status)
            assertEquals(DownloadTarget.LOCAL, recovered.target)
            assertEquals(3, recovered.downloadedFiles)
        }
    }

    @Test fun `sync sends only waiting desktop requests for the selected PC`() {
        val waiting = DownloadRequest(galleryId = 42, title = "Example")
        assertTrue(waiting.pendingForDesktop("one"))
        assertTrue(waiting.copy(profileId = "one").pendingForDesktop("one"))
        assertFalse(waiting.copy(profileId = "two").pendingForDesktop("one"))
        assertFalse(waiting.copy(target = DownloadTarget.LOCAL).pendingForDesktop("one"))
        assertFalse(waiting.copy(status = RequestStatus.SENT).pendingForDesktop("one"))
        assertFalse(waiting.copy(status = RequestStatus.SENDING).pendingForDesktop("one"))
    }

    @Test fun `ambiguous desktop submission retains identity and cannot switch to local`() {
        val sending = DownloadRequest(galleryId = 42, title = "Example", profileId = "one",
            status = RequestStatus.SENDING, desktopAttempted = true)
        val restored = decodeDownloadRequests(encodeDownloadRequests(listOf(sending))).single().recover()
        assertEquals(sending.id, restored.id)
        assertTrue(restored.pendingForDesktop("one"))
        assertFalse(restored.pendingForDesktop("two"))
        assertFalse(restored.canDownloadLocally)
        assertTrue(sending.copy(target = DownloadTarget.LOCAL, status = RequestStatus.PAUSED, desktopAttempted = false).canDownloadLocally)
    }
}
