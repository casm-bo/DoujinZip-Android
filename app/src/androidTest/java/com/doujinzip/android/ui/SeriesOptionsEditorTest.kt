package com.doujinzip.android.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.R
import com.doujinzip.android.model.*
import com.doujinzip.android.ui.theme.DoujinZipTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SeriesOptionsEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val books = listOf("A", "B", "C").map { LibraryBook(it, it, locationUri = "", locationName = "", folderUri = "", pages = emptyList(), modifiedAt = 1) }
    private val assignments = mapOf("A" to CustomSeriesAssignment("Target", 0), "B" to CustomSeriesAssignment("Target", 1), "C" to CustomSeriesAssignment("Other", 0))
    @Test fun largeTitleStartsBelowStatusBarOnIndependentSeriesRoute() {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent { DoujinZipTheme(fontSizeSp = 28f) { SeriesOptionsEditor("Target", books, assignments, PaddingValues(0.dp),
            onSave = { _, _ -> true }, onDone = {}, onCancel = {}, displayTitle = "Edited title") } }
        var top = 0
        compose.activityRule.scenario.onActivity {
            top = androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)!!.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
        }
        val title = compose.onNodeWithText("Edited title").fetchSemanticsNode().boundsInRoot
        assertTrue("Series title must clear the status bar", title.top >= top)
        val save = compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.ui_save)).fetchSemanticsNode().boundsInRoot
        assertEquals("Title and actions share a centered toolbar row", save.center.y, title.center.y, 2f)
        compose.onNodeWithText("Target").assertDoesNotExist()
    }
    @Test fun addAndExcludeStayInDraftUntilSaveAndKeepFiles() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        var saved: List<String>? = null
        var done = false
        compose.setContent { DoujinZipTheme { SeriesOptionsEditor("Target", books, assignments, PaddingValues(0.dp),
            onSave = { baseline, order -> assertEquals(assignments, baseline); saved = order; true }, onDone = { done = true }, onCancel = {}) } }
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_exclude_episodes)).performClick()
        assertNull(saved)
        compose.onNodeWithText(strings.getString(R.string.ui_add_episodes)).performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_save)).assertDoesNotExist()
        compose.onNodeWithText(strings.getString(R.string.ui_cancel)).assertDoesNotExist()
        compose.onNodeWithText(strings.getString(R.string.ui_add_episodes)).assertDoesNotExist()
        compose.onNodeWithText(strings.getString(R.string.ui_exclude_episodes)).assertDoesNotExist()
        compose.onNodeWithText(strings.getString(R.string.ui_confirm)).assertIsNotEnabled()
        compose.onNodeWithText(strings.getString(R.string.ui_return)).assertExists()
        compose.onNodeWithText(strings.getString(R.string.ui_sort)).assertExists()
        compose.onNodeWithText("C").performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_confirm)).performClick()
        assertNull(saved)
        compose.onNodeWithText(strings.getString(R.string.ui_save)).performClick()
        assertEquals(listOf("B", "C"), saved)
        assertTrue(done)
        assertEquals(3, books.size)
    }
    @Test fun dragReordersOnlyDraftUntilSave() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        var saves = 0
        compose.setContent { DoujinZipTheme { SeriesOptionsEditor("Target", books, assignments, PaddingValues(0.dp),
            onSave = { _, order -> assertEquals(listOf("B", "A"), order); saves++; true }, onDone = {}, onCancel = {}) } }
        val delta = compose.onNodeWithText("A").fetchSemanticsNode().boundsInRoot.center.y - compose.onNodeWithText("B").fetchSemanticsNode().boundsInRoot.center.y
        compose.onNodeWithText("B").performTouchInput { down(center); advanceEventTime(700); moveBy(Offset(0f, delta * 1.2f)); up() }
        assertEquals(0, saves)
        compose.onNodeWithText(strings.getString(R.string.ui_save)).performClick()
        assertEquals(1, saves)
    }
    @Test fun cancellingAnExcludedEpisodeNeverCallsSave() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        var saves = 0
        var cancelled = false
        compose.setContent { DoujinZipTheme { SeriesOptionsEditor("Target", books, assignments, PaddingValues(0.dp),
            onSave = { _, _ -> saves++; true }, onDone = {}, onCancel = { cancelled = true }) } }
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_exclude_episodes)).performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_cancel)).performClick()
        assertEquals(0, saves); assertTrue(cancelled)
        assertEquals(2, assignments.count { it.value.name == "Target" })
    }

    @Test fun episodeSearchUsesGalleryFacetsAndPreservesHiddenSelections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val strings = context.resources
        val candidates = books + listOf(
            books[0].copy(id = "D", title = "Find me", metadata = LibraryMetadata(artists = listOf("Alice"), tags = listOf("full color"))),
            books[0].copy(id = "E", title = "Other candidate", metadata = LibraryMetadata(artists = listOf("Bob"))))
        var saved: List<String>? = null
        compose.setContent {
            val themed = remember { android.view.ContextThemeWrapper(context, R.style.Theme_DoujinZip) }
            CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides themed) {
                DoujinZipTheme(fontSizeSp = 24f) { SeriesOptionsEditor("Target", candidates, assignments, PaddingValues(24.dp, 0.dp),
                    onSave = { _, order -> saved = order; true }, onDone = {}, onCancel = {}) }
            }
        }
        compose.onNodeWithText(strings.getString(R.string.ui_add_episodes)).performClick()
        compose.onNodeWithText("C").performClick()
        compose.onNode(hasSetTextAction()).performClick().performTextInput("artist:Alice, tag:full_color")
        compose.onNodeWithTag("search-editor").assertDoesNotExist()
        compose.onNodeWithTag("search-history-list").assertDoesNotExist()
        compose.onNodeWithText("Other candidate").assertDoesNotExist()
        compose.onNodeWithText("C").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText("Find me").performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_confirm)).performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_save)).performClick()
        assertEquals(listOf("A", "B", "C", "D"), saved)
    }
}
