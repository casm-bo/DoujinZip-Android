package com.doujinzip.android.ui

import android.graphics.Bitmap
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.R
import com.doujinzip.android.model.*
import com.doujinzip.android.ui.theme.DoujinZipTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SeriesDetailTest {
    @get:Rule val compose = createComposeRule()
    @Test fun seriesSettingsEditIndependentMetadataAndSelectEpisodeOrExternalCover() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val books = listOf("First", "Second").map { LibraryBook(it, it, locationUri = "", locationName = "", folderUri = "",
            pages = listOf(LibraryPage("file:///fixture-$it.png", "1")), modifiedAt = 1,
            metadata = LibraryMetadata(artists = listOf("Original artist"), tags = listOf("Episode tag"))) }
        var state by mutableStateOf(MainUiState(desktopLibraryReady = true, ))
        var selected: ReaderImageSource? = null
        var saved: GalleryPresentation? = null
        val registry = object : androidx.activity.result.ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: androidx.activity.result.contract.ActivityResultContract<I, O>, input: I,
                options: androidx.core.app.ActivityOptionsCompat?) {
                dispatchResult(requestCode, android.app.Activity.RESULT_OK, android.content.Intent().setData(android.net.Uri.parse("content://fixture/external-cover")))
            }
        }
        val owner = object : androidx.activity.result.ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        compose.setContent { CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides owner) { DoujinZipTheme {
            SeriesDetailContent("Fixture series", books, state, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, { _, _ -> }, {}, {}, {},
                onSavePresentation = { value, _ -> saved = value; state = state.copy(presentations = mapOf(value.key to com.doujinzip.android.data.PresentationRecord(value))) },
                onCover = { value, source -> assertEquals(seriesCoverPresentationKey("Fixture series"), value.key); selected = source })
        } } }
        fun settings() {
            compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(strings.getString(R.string.ui_detail_options)))
            compose.onNodeWithContentDescription(strings.getString(R.string.ui_detail_options)).performClick()
        }
        settings()
        compose.onNodeWithText(strings.getString(R.string.ui_cover_from_work)).performClick()
        compose.onNode(hasText("Second") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(books.last().coverUri, selected!!.uri)
        settings()
        compose.onNodeWithText(strings.getString(R.string.ui_cover_from_image)).performClick()
        compose.runOnIdle { assertEquals("content://fixture/external-cover", selected!!.uri) }
        settings()
        compose.onNodeWithText(strings.getString(R.string.ui_edit_metadata)).performClick()
        compose.onNode(hasText("Fixture series") and hasAnyAncestor(isDialog())).performTextReplacement("Edited series")
        compose.onNode(hasText("Original artist") and hasAnyAncestor(isDialog())).performTextReplacement("Series artist")
        compose.onNodeWithText(strings.getString(R.string.ui_save)).performClick()
        assertEquals("Edited series", saved!!.title)
        assertEquals(listOf("Series artist"), saved!!.metadata.artists)
        assertEquals(listOf("Original artist"), books.first().metadata.artists)
        compose.onNodeWithText("Edited series").assertExists()
    }

    @Test fun editedSeriesMetadataAppearsOnTheSharedCard() {
        val value = GalleryPresentation(seriesPresentationKey("Group"), "Edited group", LibraryMetadata(artists = listOf("Group artist"), tags = listOf("Group tag")), webtoon = false)
        val book = LibraryBook("episode", "Episode", locationUri = "", locationName = "", folderUri = "", pages = emptyList(), modifiedAt = 1, webtoon = true)
        compose.setContent { DoujinZipTheme { SeriesCard("Group", listOf(book), true, 1f, false, false, false, false, {}, {}, {}, presentation = value) } }
        compose.onNodeWithText("Edited group").assertExists()
        compose.onNodeWithText("Group artist").assertExists()
        compose.onNodeWithText("Group tag").assertExists()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.ui_webtoon_tag)).assertExists()
    }

    @Test fun selectingOnlyACoverKeepsAggregatedMetadataLive() {
        val book = LibraryBook("episode", "Episode", locationUri = "", locationName = "", folderUri = "", pages = emptyList(), modifiedAt = 1,
            metadata = LibraryMetadata(tags = listOf("Original tag")))
        var books by mutableStateOf(listOf(book))
        val value = GalleryPresentation(seriesCoverPresentationKey("Group"), "Group", LibraryMetadata(), coverHash = "a".repeat(64))
        compose.setContent { DoujinZipTheme { SeriesDetailContent("Group", books,
            MainUiState(desktopLibraryReady = true, presentations = mapOf(value.key to com.doujinzip.android.data.PresentationRecord(value))),
            {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}) } }
        compose.runOnIdle { books = listOf(book.copy(metadata = book.metadata.copy(tags = listOf("Original tag", "Fresh tag")))) }
        compose.onNodeWithText("Fresh tag").assertExists()
    }
    @Test fun continuingAtEpisodeEndThenBackReturnsToSeriesDetail() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cover = File(context.cacheDir, "series-navigation.png")
        val bitmap = Bitmap.createBitmap(100, 160, Bitmap.Config.ARGB_8888)
        cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val books = listOf("First episode", "Second episode").mapIndexed { index, title ->
            LibraryBook("reader-nav-$index", title, locationUri = "fixture", locationName = "Fixture", folderUri = "",
                pages = listOf(LibraryPage(cover.toURI().toString(), "Page 1")), modifiedAt = 1)
        }
        val home = BrowserPage.LibrarySeries("Navigation series")
        var workspace by mutableStateOf(BrowserWorkspace.initial(home))
        lateinit var back: OnBackPressedDispatcher
        compose.setContent { DoujinZipTheme {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            val current = workspace.activeTab.currentPage
            if (current is BrowserPage.LibraryReader) {
                BackHandler { workspace = workspace.goBack() }
                val book = books.first { it.id == current.bookId }
                LocalReaderScreen(current.key, book, 0, 0, false, null, false,
                    ViewerPreferences(readingMode = ViewerReadingMode.PAGE, readingDirection = ViewerReadingDirection.LEFT_TO_RIGHT, hideThumbnails = true),
                    onBack = { workspace = workspace.goBack() }, onToggleFavorite = {},
                    onProgress = { workspace = workspace.updateReaderPosition(current.key, it) },
                    nextBookTitle = if (book.id == books.first().id) books.last().title else null,
                    onOpenNextBook = { workspace = workspace.continueLibraryReader(BrowserPage.LibraryReader(books.last().id, books.last().title, 0)) },
                    onPreferencesChange = {})
            } else SeriesDetailContent("Navigation series", books, MainUiState(desktopLibraryReady = true, ), {}, {}, {},
                { id, page -> workspace = workspace.openLibraryReader(BrowserPage.LibraryReader(id, books.first { it.id == id }.title, page)) },
                {}, {}, {}, {}, { _, _ -> }, {}, {}, {})
        } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(context.getString(R.string.ui_read_from_beginning)))
        compose.onNodeWithText(context.getString(R.string.ui_read_from_beginning)).performClick()
        compose.onNodeWithTag("reader-pager").performTouchInput { click(Offset(width * .9f, height * .5f)) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(context.getString(R.string.ui_continue_with, books.last().title)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(context.getString(R.string.ui_continue_with, books.last().title)).assertExists()
        compose.onNodeWithText(context.getString(R.string.ui_open)).performClick()
        compose.onNodeWithText(books.last().title).assertExists()
        compose.runOnIdle {
            assertEquals(books.last().id, (workspace.activeTab.currentPage as BrowserPage.LibraryReader).bookId)
            assertEquals(2, workspace.activeTab.history.size)
            back.onBackPressed()
        }
        compose.onNodeWithText("Navigation series").assertExists()
        compose.runOnIdle { assertEquals(home, workspace.activeTab.currentPage) }
        cover.delete()
    }

    @Test fun resumesLatestEpisodeAndAggregatesPreviewPages() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val books = listOf("First", "Second").map { name -> LibraryBook(name, name, locationUri = "", locationName = "", folderUri = "",
            pages = List(4) { LibraryPage("file:///fixture-$name-$it.png", "$name-page-$it") }, modifiedAt = 1) }
        var opened: Pair<String, Int>? = null
        compose.setContent { DoujinZipTheme { SeriesDetailContent("Fixture series", books, MainUiState(desktopLibraryReady = true, libraryProgress = mapOf("Second" to 2)),
            {}, {}, {}, { id, page -> opened = id to page }, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(strings.getString(R.string.ui_continue_reading)))
        compose.onNodeWithText(strings.getString(R.string.ui_continue_reading)).performClick()
        assertEquals("Second" to 2, opened)
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Second-page-3"))
        compose.onNodeWithContentDescription("Second-page-3").assertExists()
    }
}
