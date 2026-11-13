package com.doujinzip.android.ui

import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.EditText
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.R
import com.doujinzip.android.model.*
import com.doujinzip.android.ui.theme.DoujinZipTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SearchScreenInsetsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
    private fun findInput() = WindowInspector.getGlobalWindowViews().asSequence().flatMap(::descendants).filterIsInstance<EditText>().singleOrNull()
    private fun input(action: (EditText) -> Unit) = instrumentation.runOnMainSync { action(requireNotNull(findInput())) }
    @Test fun searchResultsReachKeyboardAboveReservedNavigationBar() = checkScreen(false)
    @Test fun galleryResultsReachKeyboardAndHistoryScrollKeepsInput() = checkScreen(true)
    @Test fun searchResultsSelectMultipleGalleriesForBothActions() = checkScreen(false, selection = true)
    private fun checkScreen(gallery: Boolean, selection: Boolean = false) {
        compose.activityRule.scenario.onActivity {
            it.enableEdgeToEdge()
            it.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val context = instrumentation.targetContext
        val strings = context.resources
        val books = List(40) { LibraryBook("$it", "Fixture $it", locationUri = "fixture", locationName = "Fixture", folderUri = "",
            pages = emptyList(), modifiedAt = it.toLong(), metadata = LibraryMetadata(tags = listOf("draft"))) }
        val history = List(30) { SearchQueryEntry("artist:fixture_$it") }
        var opened = emptyList<Long>()
        var downloaded = emptyList<Long>()
        var state by mutableStateOf(MainUiState(libraryBooks = books, libraryLocations = listOf(StorageLocation("fixture", "Fixture")),
            libraryQueryHistory = history, searchQueryHistory = history,
            galleries = List(40) { GallerySummary(it.toLong() + 1, "Fixture $it", emptyList(), emptyList(), null, emptyList(), null, 1, null, publishedDate="2026-01-02") }))
        compose.setContent {
            val themed = remember { android.view.ContextThemeWrapper(context, R.style.Theme_DoujinZip) }
            CompositionLocalProvider(LocalContext provides themed) { DoujinZipTheme {
                Scaffold(bottomBar = { Box(Modifier.fillMaxWidth().height(80.dp)) }) { padding ->
                    val header: @Composable (@Composable () -> Unit, @Composable () -> Unit) -> Unit = { query, actions ->
                        BrowserTabStrip(BrowserWorkspace.initial(), {}, {}, remember { BrowserTabPreviewCache(context) }, titleContent = query, extraActions = { actions() })
                    }
                    if (gallery) LibraryScreen(
                        state = state,
                        tabHeader = header,
                        onSync = {},
                        contentPadding = padding,
                        onQueryChange = { state = state.copy(libraryQueryDraft = it, libraryQuery = it) },
                        onToggleFavoritesFilter = {},
                        onExcludeSeriesChange = {},
                        onReadFilterChange = {},
                        onVisibilityFilterChange = {},
                        onSortChange = {},
                        onLocationChange = {},
                        onRefresh = {},
                        onOpenBook = {},
                        onToggleFavorite = {},
                        onToggleSeriesFavorite = {},
                        onToggleRead = {},
                        onAddFavorites = {},
                        onMarkRead = {},
                        onMarkUnread = {},
                        onAssignSeries = { _, _ ->  },
                        onAppendSeries = { _, _ -> true },
                        onSetBooksHidden = { _, _ ->  },
                        onDeleteBooks = {},
                        onRenameBook = { _, _ ->  },
                        onRemoveBooksFromSeries = {},
                        onRenameSeries = { _, _ ->  },
                        onMergeSeries = { _, _ ->  },
                        onDeleteSeries = {},
                        onAutoCreateSeries = {},
                        onSeriesModeChange = {},
                        onSelectedSeriesChange = {},
                        onBackToSearch = {},
                        pageKey = "fixture",
                        initialScrollAnchorKey = null,
                        initialScrollIndex = 0,
                        initialScrollOffset = 0,
                        onScrollChange = { _, _, _ ->  },
                    )
                    else BrowserScreen(
                        state = state,
                        tabHeader = header,
                        contentPadding = padding,
                        onQueryChange = { state = state.copy(searchQuery = it) },
                        onSearch = {},
                        onRecentSearch = {},
                        onClearSearchHistory = {},
                        onToggleLanguage = {},
                        onLoadLanguages = {},
                        onSelectAnyLanguage = {},
                        onRemoveCustomLanguage = {},
                        onSelectSuggestion = {},
                        onRefresh = {},
                        onLoadNextPage = {},
                        onLoadPreviousPage = {},
                        onJumpToPage = {},
                        onGalleryClick = {},
                        onGalleryLongClick = {},
                        onOpenSelectedGalleries = { opened = it.map(GallerySummary::id) },
                        onDownloadSelectedGalleries = { downloaded = it.map(GallerySummary::id) },
                        onSearchFacet = {},
                        onToggleLibraryFavorite = {},
                        onConnect = {},
                        pageKey = "fixture",
                        initialScrollAnchorKey = null,
                        initialScrollIndex = 0,
                        initialScrollOffset = 0,
                        onScrollChange = { _, _, _, _ ->  },
                        onEnsureResults = {},
                        onVisibleResultsChange = { _, _ ->  },
                    )
                }
            } }
        }
        if (selection) {
            fun selectTwo() {
                compose.onNodeWithText("Fixture 0").performTouchInput { longClick() }.assertIsSelected()
                compose.onNodeWithText("Fixture 1").performClick().assertIsSelected()
            }
            selectTwo()
            compose.onNodeWithText(strings.getString(R.string.ui_open_in_new_tab)).performClick()
            compose.runOnIdle { assertEquals(listOf(1L, 2L), opened) }
            selectTwo()
            compose.onNodeWithText(strings.getString(R.string.ui_download)).performClick()
            compose.runOnIdle { assertEquals(listOf(1L, 2L), downloaded) }
            return
        }
        if(!gallery) compose.onAllNodesWithText(strings.getString(R.string.ui_uploaded,"2026.01.02")).onFirst().assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToIndex(39)
        val anchor = compose.onAllNodesWithText("Fixture ", substring = true).fetchSemanticsNodes()
            .filter { it.boundsInRoot.height > 30 }.minBy { it.boundsInRoot.top }
        val anchorTitle = anchor.config[androidx.compose.ui.semantics.SemanticsProperties.Text].first { it.text.startsWith("Fixture ") }.text
        val anchorTop = anchor.boundsInRoot.top
        compose.onNodeWithContentDescription(strings.getString(if (gallery) R.string.ui_search_gallery else R.string.ui_search_conditions)).performClick()
        compose.waitUntil(5_000) {
            var ready = false
            instrumentation.runOnMainSync { findInput()?.let { ready = it.hasFocus() && ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime()) == true } }
            ready
        }
        var expectedBottom = 0f
        var tolerance = 0f
        fun bottomGap(): Float {
            input { field ->
                val owner = generateSequence(field.parent) { it.parent }.filterIsInstance<View>().first { it.javaClass.name.endsWith("AndroidComposeView") }
                val ownerPosition = IntArray(2).also(owner::getLocationOnScreen)
                val decor = field.rootView
                val decorPosition = IntArray(2).also(decor::getLocationOnScreen)
                expectedBottom = (decorPosition[1] + decor.height - ViewCompat.getRootWindowInsets(field)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom - ownerPosition[1]).toFloat()
                tolerance = 8 * field.resources.displayMetrics.density
            }
            return expectedBottom - compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().maxOf { it.boundsInRoot.bottom }
        }
        try { compose.waitUntil(5_000) { kotlin.math.abs(bottomGap()) <= tolerance } }
        catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("IME bottom=$expectedBottom gap=${bottomGap()} tolerance=$tolerance", error)
        }
        assertEquals("Browsing must reach the IME without a second navigation-bar gap", 0f, bottomGap(), tolerance)
        input { it.setSelection(0) }
        compose.onNodeWithTag("search-history-list").performTouchInput { swipeUp() }
        compose.waitUntil(5_000) {
            var hidden = false
            input { hidden = ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime()) == false }
            hidden
        }
        compose.onNodeWithTag("search-editor").assertExists()
        input { assertTrue(it.hasFocus()); assertEquals(0, it.selectionStart) }
        compose.onNodeWithContentDescription(strings.getString(R.string.ui_return)).performClick()
        assertEquals("Search expansion near the end must retain the same card position", anchorTop,
            compose.onNodeWithText(anchorTitle).fetchSemanticsNode().boundsInRoot.top, 2f)
    }
}
