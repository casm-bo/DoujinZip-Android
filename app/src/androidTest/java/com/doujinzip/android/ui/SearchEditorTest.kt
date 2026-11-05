package com.doujinzip.android.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import android.os.SystemClock
import android.view.MotionEvent
import android.view.InputDevice
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.R
import com.doujinzip.android.model.*
import com.doujinzip.android.network.FilterSuggestion
import com.doujinzip.android.ui.theme.DoujinZipTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SearchEditorTest {
    @get:Rule val compose = createComposeRule()
    @org.junit.Before fun requiresWindowInspection() { org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 29) }
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    @Composable private fun AppTheme(fontSizeSp: Float = 16f, content: @Composable () -> Unit) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val themed = remember(context) { android.view.ContextThemeWrapper(context, R.style.Theme_DoujinZip) }
        CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides themed) { DoujinZipTheme(fontSizeSp = fontSizeSp) {
            InlineSearchLayout(remember { InlineSearchState() }) { content() }
        } }
    }
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
    private fun views() = WindowInspector.getGlobalWindowViews().asSequence().flatMap(::descendants)
    private fun field(action: (EditText) -> Unit) {
        instrumentation.runOnMainSync { action(views().filterIsInstance<EditText>().single()) }
    }
    private fun waitForInput() {
        compose.waitUntil(5_000) {
            var ready = false
            instrumentation.runOnMainSync { ready = views().filterIsInstance<EditText>().singleOrNull()?.hasFocus() == true }
            ready
        }
    }
    private fun waitForKeyboard(visible: Boolean) {
        compose.waitUntil(5_000) {
            var matches = false
            field { matches = ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime()) == visible }
            matches
        }
    }
    private fun send(downTime: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
    }
    @Test fun emptySearchOffersPasteOnLongPress() {
        var query by mutableStateOf("")
        compose.setContent { AppTheme { UnifiedSearchField(query, { query = it }, "Paste query", {}, {}) } }
        compose.onNodeWithContentDescription("Paste query").performClick()
        waitForInput()
        var x = 0f; var y = 0f
        field { view ->
            val clipboard = view.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Synthetic search", "tag:sample"))
            val location = IntArray(2).also { view.getLocationOnScreen(it) }
            val handle = (view as SearchQueryInput).caretHandlePosition()
            x = location[0] + handle.x
            y = location[1] + handle.y
        }
        val down = SystemClock.uptimeMillis()
        send(down, MotionEvent.ACTION_DOWN, x, y)
        SystemClock.sleep(700)
        send(down, MotionEvent.ACTION_UP, x, y)
        val paste = instrumentation.targetContext.getString(android.R.string.paste)
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            val action = views().filterIsInstance<android.widget.TextView>().first { it.text.toString() == paste }
            var target: View = action
            while (!target.isClickable && target.parent is View) target = target.parent as View
            assertTrue(target.performClick())
        }
        compose.waitUntil(5_000) { query == "tag:sample" }
    }

    @Test fun longQueryOpensAtEndAndDoesNotResetManualCursorSelection() {
        val original = List(30) { "tag:sample_$it" }.joinToString(", ")
        var query by mutableStateOf(original)
        var submitted = false
        compose.setContent { AppTheme { UnifiedSearchField(query, { query = it }, "Long query", {}, { submitted = true }) } }
        compose.onNodeWithContentDescription("Long query").performClick()
        waitForInput()
        field { assertTrue(it.hasFocus()); assertEquals(query.length, it.selectionStart); assertTrue(it.scrollX > 0) }
        field { it.setSelection(10); it.text.insert(10, "X") }
        field { assertEquals(11, it.selectionStart) }
        assertEquals(original.substring(0, 10) + "X" + original.substring(10), query)
        field { it.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) }
        assertTrue(submitted)
    }

    @Test fun bindingAndReopeningDoNotDispatchUserEdits() {
        var query by mutableStateOf("tag:original")
        var edits = 0
        compose.setContent { AppTheme { UnifiedSearchField(query, { edits++; query = it }, "Binding query", {}, {}) } }
        compose.onNodeWithContentDescription("Binding query").performClick()
        waitForInput()
        var originalInput: EditText? = null
        field { originalInput = it }
        assertEquals(0, edits)
        compose.runOnIdle { query = "artist:external" }
        compose.waitForIdle()
        field { assertEquals(query, it.text.toString()); it.text.append("x") }
        assertEquals(1, edits)
        field { it.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) }
        compose.onNodeWithContentDescription("Binding query").performClick()
        waitForInput()
        field { assertSame("Reopening must reuse the existing platform editor", originalInput, it) }
        assertEquals(1, edits)
    }

    @Test fun cursorHandleKeepsScrollingWhileHeldAtEdge() {
        var query by mutableStateOf(List(100) { "tag:sample_$it" }.joinToString(", "))
        compose.setContent { AppTheme { UnifiedSearchField(query, { query = it }, "Drag query", {}, {}) } }
        compose.onNodeWithContentDescription("Drag query").performClick()
        waitForInput()
        var x = 0f; var y = 0f; var edge = 0f
        field { view -> val position = IntArray(2).also { view.getLocationOnScreen(it) }
            val handle = (view as SearchQueryInput).caretHandlePosition()
            x = position[0] + handle.x; y = position[1] + handle.y; edge = position[0].toFloat() + 1
        }
        val down = SystemClock.uptimeMillis()
        send(down, MotionEvent.ACTION_DOWN, x, y)
        try {
            repeat(12) { step -> send(down, MotionEvent.ACTION_MOVE, x + (edge - x) * (step + 1) / 12, y); SystemClock.sleep(16) }
            var before = 0
            field { before = it.selectionStart }
            SystemClock.sleep(1_000)
            field { assertTrue("Held cursor should keep scrolling: $before then ${it.selectionStart}", it.selectionStart < before) }
        } finally { send(down, MotionEvent.ACTION_UP, edge, y) }
        var stopped = 0
        field { stopped = it.selectionStart }
        SystemClock.sleep(200)
        field { assertEquals(stopped, it.selectionStart) }
        field { view ->
            val position = IntArray(2).also { view.getLocationOnScreen(it) }
            val handle = (view as SearchQueryInput).caretHandlePosition()
            x = position[0] + handle.x; y = position[1] + handle.y
            edge = position[0] + view.width - 1f
        }
        val reverse = SystemClock.uptimeMillis()
        send(reverse, MotionEvent.ACTION_DOWN, x, y)
        try {
            repeat(12) { step -> send(reverse, MotionEvent.ACTION_MOVE, x + (edge - x) * (step + 1) / 12, y); SystemClock.sleep(16) }
            var before = 0
            field { before = it.selectionStart }
            SystemClock.sleep(500)
            field { assertTrue("Right edge should continue moving forward", it.selectionStart > before) }
            field { view -> val position = IntArray(2).also { view.getLocationOnScreen(it) }; edge = position[0] + view.width / 2f }
            send(reverse, MotionEvent.ACTION_MOVE, edge, y)
            field { stopped = it.selectionStart }
            SystemClock.sleep(200)
            field { assertEquals("Leaving the edge should stop auto-scroll", stopped, it.selectionStart) }
        } finally { send(reverse, MotionEvent.ACTION_CANCEL, edge, y) }
    }

    @Test fun longPressStillSelectsTextWithoutChangingTheQuery() {
        var query by mutableStateOf("tag:first, artist:sample")
        compose.setContent { AppTheme { UnifiedSearchField(query, { query = it }, "Selection query", {}, {}) } }
        compose.onNodeWithContentDescription("Selection query").performClick()
        waitForInput()
        var x = 0f; var y = 0f
        field { view -> val position = IntArray(2).also { view.getLocationOnScreen(it) }; x = position[0] + view.width / 3f; y = position[1] + view.height / 2f }
        val down = SystemClock.uptimeMillis()
        send(down, MotionEvent.ACTION_DOWN, x, y)
        SystemClock.sleep(700)
        send(down, MotionEvent.ACTION_UP, x, y)
        field { assertTrue("Platform text selection must remain available", it.selectionStart != it.selectionEnd) }
        assertEquals("tag:first, artist:sample", query)
    }

    @Test fun fullscreenEditorRestoresHistoryOnClearAndMovesAutofillCursorToEnd() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        var query by mutableStateOf("tag:first, artist:sa")
        var history by mutableStateOf(listOf(SearchQueryEntry("tag:full_color tag:multi-work_series")))
        var submitted = false
        compose.setContent { AppTheme { UnifiedSearchField(query, { query = it }, "Fixture search", {}, { submitted = true },
            history, if (query.endsWith("sa")) listOf(FilterSuggestion("artist:sample", "sample")) else emptyList(), false,
            {}, { history = emptyList() }, { query = applySearchSuggestion(query, it.token) }) } }
        compose.onNodeWithContentDescription("Fixture search").performClick()
        waitForInput()
        compose.onNodeWithText("artist:sample").performClick()
        compose.waitForIdle()
        field { assertEquals("tag:first, artist:sample, ", it.text.toString()); assertEquals(query.length, it.selectionStart) }
        compose.onNodeWithText(strings.getString(R.string.ui_clear_search)).performClick()
        compose.onNodeWithText("tag:full_color, tag:multi-work_series").assertExists()
        compose.onNodeWithText(strings.getString(R.string.ui_clear_history)).performClick()
        compose.onNodeWithText(strings.getString(R.string.ui_no_recent_searches)).assertExists()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onNodeWithContentDescription(strings.getString(R.string.ui_run_search)).performClick()
        assertTrue(submitted)
    }

    @Test fun expandedFieldCollapsesOnBlankTapAndScrollWithoutSubmittingOrLosingDraft() {
        var query by mutableStateOf("tag:draft")
        var submitted = 0
        lateinit var list: androidx.compose.foundation.lazy.LazyListState
        compose.setContent { AppTheme { Column(Modifier.fillMaxSize()) {
            UnifiedSearchField(query, { query = it }, "Inline query", {}, { submitted++ })
            list = rememberLazyListState()
            LazyColumn(Modifier.weight(1f).testTag("browsing"), state = list) {
                items(100) { androidx.compose.material3.Text("Result $it", Modifier.height(80.dp)) }
            }
        } } }
        compose.onNodeWithContentDescription("Inline query").performClick()
        waitForInput()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        field { it.text.append(", artist:sample") }
        compose.onNodeWithTag("search-options").assertIsDisplayed()
        val results = compose.onNodeWithTag("browsing").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Results must stay visible below search history", results.bottom - results.top >= 100.dp)
        compose.onNodeWithTag("browsing").performTouchInput { click(center) }
        compose.onNodeWithTag("search-collapsed").assertExists()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Inline query").performClick()
        waitForInput()
        field { assertEquals(query.length, it.selectionStart) }
        compose.onNodeWithTag("browsing").performTouchInput { swipeUp() }
        compose.onNodeWithTag("search-collapsed").assertExists()
        compose.runOnIdle { assertEquals("tag:draft, artist:sample", query); assertEquals(0, submitted); assertTrue(list.firstVisibleItemIndex > 0) }
        fieldIfPresentHasNoFocus()
    }

    @Test fun scrollingHistoryHidesOnlyKeyboardAndKeepsDraftAndCursor() {
        var query by mutableStateOf("tag:draft")
        var selected = false
        val history = List(30) { SearchQueryEntry("artist:fixture_$it", setOf("korean")) }
        compose.setContent { AppTheme { Column(Modifier.fillMaxSize()) {
            UnifiedSearchField(query, { query = it }, "History query", {}, {}, history, onRecent = { selected = true })
            androidx.compose.material3.Text("Browsing results", Modifier.weight(1f).testTag("history-browsing"))
        } } }
        compose.onNodeWithContentDescription("History query").performClick()
        waitForInput()
        waitForKeyboard(true)
        field { it.setSelection(4) }
        val before = compose.onNodeWithTag("search-history-list").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("search-history-list").performTouchInput { swipeUp() }
        waitForKeyboard(false)
        compose.onNodeWithTag("search-editor").assertExists()
        compose.onNodeWithTag("search-history-list").assertIsDisplayed()
        val after = compose.onNodeWithTag("search-history-list").getUnclippedBoundsInRoot()
        assertTrue("Dismissing the keyboard must not shrink history", after.bottom - after.top >= before.bottom - before.top)
        field { assertTrue(it.hasFocus()); assertEquals(4, it.selectionStart); assertEquals("tag:draft", it.text.toString()) }
        assertFalse(selected)
        // The same behavior applies to the gallery search because it uses this shared field/layout.
        compose.onNodeWithTag("history-browsing").performTouchInput { click(center) }
        compose.onNodeWithTag("search-collapsed").assertExists()
        assertEquals("tag:draft", query)
    }

    @Test fun expandedHistoryClearsRoundedToolbarBottomAndKeepsResultsVisible() {
        val context = instrumentation.targetContext
        val history = List(30) { SearchQueryEntry("artist:fixture_$it") }
        compose.setContent { AppTheme(fontSizeSp = 24f) { Column(Modifier.fillMaxSize()) {
            BrowserTabStrip(BrowserWorkspace.initial(), {}, {}, remember { BrowserTabPreviewCache(context) },
                titleContent = { UnifiedSearchField("tag:sample", {}, "Padded query", {}, {}, history) })
            androidx.compose.material3.Text("Browsing results", Modifier.weight(1f).testTag("padded-browsing"))
        } } }
        compose.onNodeWithContentDescription("Padded query").performClick()
        waitForInput()
        waitForKeyboard(true)
        val editor = compose.onNodeWithTag("search-editor").getUnclippedBoundsInRoot()
        val options = compose.onNodeWithTag("search-options").getUnclippedBoundsInRoot()
        assertTrue("History card needs space inside the toolbar's rounded bottom", editor.bottom - options.bottom >= 12.dp)
        compose.onNodeWithTag("search-history-list").assertIsDisplayed()
        val results = compose.onNodeWithTag("padded-browsing").getUnclippedBoundsInRoot()
        assertTrue("Browsing must remain visible with the keyboard", results.bottom - results.top >= 80.dp)
    }

    private fun fieldIfPresentHasNoFocus() {
        instrumentation.runOnMainSync { views().filterIsInstance<EditText>().forEach { assertFalse(it.hasFocus()) } }
    }

    @Test fun toolbarFieldExpandsInItsOriginalWindowAndRestoresTabActions() {
        val context = instrumentation.targetContext
        val strings = context.resources
        var query by mutableStateOf("tag:sample")
        compose.setContent { AppTheme {
            BrowserTabStrip(BrowserWorkspace.initial(), {}, {}, remember { BrowserTabPreviewCache(context) },
                titleContent = { UnifiedSearchField(query, { query = it }, "Toolbar query", {}, {}) },
                extraActions = { androidx.compose.material3.Text("Options") })
        } }
        val before = compose.onNodeWithTag("search-collapsed").getUnclippedBoundsInRoot()
        var windowCount = 0
        instrumentation.runOnMainSync { windowCount = WindowInspector.getGlobalWindowViews().size }
        compose.onNodeWithContentDescription("Toolbar query").performClick()
        waitForInput()
        val after = compose.onNodeWithTag("search-editor").getUnclippedBoundsInRoot()
        assertTrue(after.right - after.left > before.right - before.left)
        assertTrue(after.bottom - after.top > (before.bottom - before.top) * 2)
        instrumentation.runOnMainSync { assertEquals(windowCount, WindowInspector.getGlobalWindowViews().size) }
        compose.onNodeWithContentDescription(strings.getString(R.string.ui_new_tab)).assertIsNotDisplayed()
        compose.onNodeWithContentDescription(strings.getString(R.string.ui_return)).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(strings.getString(R.string.ui_new_tab)).assertIsDisplayed()
        val restored = compose.onNodeWithTag("search-collapsed").getUnclippedBoundsInRoot()
        assertEquals((before.right - before.left).value, (restored.right - restored.left).value, .5f)
    }
}
