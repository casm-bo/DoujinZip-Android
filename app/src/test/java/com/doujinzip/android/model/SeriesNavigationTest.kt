package com.doujinzip.android.model

import org.junit.Assert.*
import org.junit.Test

class SeriesNavigationTest {
    private val previous = mapOf("A" to CustomSeriesAssignment("Old", 0), "B" to CustomSeriesAssignment("Old", 1))

    @Test fun renamedSeriesUpdatesOpenAndBackHistoryPages() {
        val page = BrowserPage.LibrarySeries("Old", episodeList = true, previewPosition = ReaderPosition(4))
        val workspace = BrowserWorkspace.initial(page).pushPage(BrowserPage.LibraryBook("A", "Episode"))
        val changed = workspace.reconcileSeriesPages(previous, previous.mapValues { it.value.copy(name = "New") })
        assertEquals(page.copy(name = "New"), changed.activeTab.history.first())
        assertEquals(workspace.activeTab.currentPage, changed.activeTab.currentPage)
    }

    @Test fun removedSeriesReturnsHomeButRemainingSeriesKeepsItsPage() {
        val page = BrowserPage.LibrarySeries("Old")
        val workspace = BrowserWorkspace.initial(page)
        assertEquals(BrowserPage.LibraryHome(key = page.key), workspace.reconcileSeriesPages(previous, emptyMap()).activeTab.currentPage)
        assertEquals(page, workspace.reconcileSeriesPages(previous, previous - "A").activeTab.currentPage)
    }

    @Test fun splitSeriesDoesNotChooseAnArbitraryReplacement() {
        val workspace = BrowserWorkspace.initial(BrowserPage.LibrarySeries("Old"))
        val split = mapOf("A" to CustomSeriesAssignment("One", 0), "B" to CustomSeriesAssignment("Two", 0))
        assertTrue(workspace.reconcileSeriesPages(previous, split).activeTab.currentPage is BrowserPage.LibraryHome)
    }
}
