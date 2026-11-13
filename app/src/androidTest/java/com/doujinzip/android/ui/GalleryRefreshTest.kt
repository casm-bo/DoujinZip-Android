package com.doujinzip.android.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.R
import com.doujinzip.android.data.*
import com.doujinzip.android.model.*
import com.doujinzip.android.ui.theme.DoujinZipTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GalleryRefreshTest {
    @get:Rule val compose = createComposeRule()

    @Test fun existingDetailReactsToCompletedDownloadsAndPullRefreshKeepsPreview() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val gallery = GallerySummary(42, "Fixture", emptyList(), emptyList(), null, emptyList(), null, 1, null)
        var state by mutableStateOf(MainUiState(downloadDestination = DownloadDestination.DEVICE,
            readerPagesByGalleryId = mapOf(42L to listOf("file:///fixture-page.png"))))
        var refreshes = 0
        var openedInTab = 0
        compose.setContent { DoujinZipTheme {
            GalleryDetailScreen("fixture", ReaderPosition(0), {}, gallery, state, {}, {}, {}, {}, {}, {},
                onRefresh = { assertEquals(42L, it); refreshes++; state = state.copy(refreshingGalleryIds = setOf(it)) },
                onOpenInNewTab = { openedInTab++ })
        } }
        compose.onNodeWithText(strings.getString(R.string.ui_download)).assertIsEnabled()
        compose.onNodeWithText("Fixture").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, openedInTab) }
        compose.runOnIdle { state = state.copy(downloadRequests = listOf(DownloadRequest(galleryId = 42, title = "Fixture",
            target = DownloadTarget.LOCAL, status = RequestStatus.COMPLETED))) }
        compose.onNodeWithText(strings.getString(R.string.ui_completed)).assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(downloadRequests = emptyList(), libraryBooks = listOf(LibraryBook("owned", "Owned",
            locationUri = "", locationName = "", folderUri = "", pages = emptyList(), modifiedAt = 1, metadata = LibraryMetadata(hitomiId = "42")))) }
        compose.onNodeWithText(strings.getString(R.string.ui_completed)).assertIsNotEnabled()
        compose.onNode(hasScrollAction()).performTouchInput { swipeDown() }
        compose.runOnIdle {
            assertEquals(1, refreshes)
            assertEquals(1, state.readerPagesByGalleryId[42]?.size)
            state = state.copy(refreshingGalleryIds = emptySet())
        }
    }
}
