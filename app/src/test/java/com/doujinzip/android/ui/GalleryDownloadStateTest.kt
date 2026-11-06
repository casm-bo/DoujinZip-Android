package com.doujinzip.android.ui

import com.doujinzip.android.data.*
import com.doujinzip.android.model.*
import org.junit.Assert.*
import org.junit.Test

class GalleryDownloadStateTest {
    private val book = LibraryBook("fixture", "Fixture", locationUri = "", locationName = "", folderUri = "",
        pages = emptyList(), modifiedAt = 1, metadata = LibraryMetadata(hitomiId = "42"))

    @Test fun ownedGalleryIsDetectedByIdIncludingHiddenBooksAndRequestedDestination() {
        val state = MainUiState(libraryBooks = listOf(book), libraryHiddenIds = setOf(book.id))
        assertTrue(state.hasDownloadedGallery(42))
        assertTrue(state.hasDownloadedGallery(42, DownloadDestination.DEVICE))
        assertFalse(state.hasDownloadedGallery(42, DownloadDestination.DESKTOP))
        assertFalse(state.allDownloadDestinationsOwned(42))
        assertTrue(state.copy(downloadDestination = DownloadDestination.DEVICE).allDownloadDestinationsOwned(42))
        assertTrue(state.copy(libraryBooks = listOf(book, book.copy(id = "pc", isCloud = true))).allDownloadDestinationsOwned(42))
        assertFalse(state.hasDownloadedGallery(43))
    }

    @Test fun completedRequestProtectsTheGapBeforeLibraryScanAndRespectsPcScope() {
        val completed = DownloadRequest(galleryId = 42, title = "Fixture", target = DownloadTarget.LOCAL, status = RequestStatus.COMPLETED)
        val state = MainUiState(downloadRequests = listOf(completed))
        assertTrue(state.hasDownloadedGallery(42, DownloadDestination.DEVICE))
        assertFalse(state.hasDownloadedGallery(42, DownloadDestination.DESKTOP))
        val pc = state.copy(selectedProfileId = "pc", downloadRequests = listOf(completed.copy(target = DownloadTarget.DESKTOP, profileId = "other")))
        assertFalse(pc.hasDownloadedGallery(42))
        assertTrue(pc.copy(selectedProfileId = "other").hasDownloadedGallery(42))
    }
}
