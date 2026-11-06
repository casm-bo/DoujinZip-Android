package com.doujinzip.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSessionTest {
    @Test fun closingStandaloneTabUsesPreviousDisplayedTabAndSkipsGroups() {
        val tabs = (1..5).map { BrowserTab(id = "tab$it") }
        val grouped = BrowserWorkspace(tabs, "tab5").createGroup(setOf("tab3", "tab4"), "Fixture")
        assertEquals("tab2", grouped.closeTab("tab5").activeTabId)
        assertEquals("tab2", grouped.selectTab("tab1").closeTab("tab1").activeTabId)
        assertEquals("tab5", grouped.closeTab("tab2").activeTabId)
        val reordered = grouped.copy(overviewOrder = listOf("tab:tab2", "tab:tab1", "tab:tab5"))
        assertEquals("tab1", reordered.closeTab("tab5").activeTabId)
        assertEquals("tab3", grouped.selectTab("tab4").closeTab("tab4").activeTabId)
        val onlyGroups = grouped.copy(tabs = grouped.tabs.filter { it.id !in setOf("tab1", "tab2") })
            .closeTab("tab5", BrowserPage.LibraryHome())
        assertNull(onlyGroups.activeTab.groupId)
        assertTrue(onlyGroups.activeTab.currentPage is BrowserPage.LibraryHome)
        assertEquals(2, onlyGroups.tabs.count { it.groupId != null })
    }

    @Test fun departingPageLookupIgnoresForwardHistoryAndOtherTabs() {
        val first = BrowserPage.LibraryHome(scrollIndex = 20, scrollOffset = 17)
        val second = BrowserPage.LibraryHome(query = "second", scrollIndex = 4)
        val workspace = BrowserWorkspace.initial(first).pushPage(BrowserPage.LibraryBook("book", "Book"))
            .pushPage(second).goBack().openTab(BrowserPage.LibraryHome(query = "other"), activate = false)
        assertEquals(first, workspace.currentOrPreviousPage<BrowserPage.LibraryHome>())
        assertEquals(null, workspace.currentOrPreviousPage<BrowserPage.Search>())
        val search = BrowserPage.Search(query = "tag:sample", scrollIndex = 8)
        assertEquals(search, BrowserWorkspace.initial(search).pushPage(BrowserPage.OnlineGallery(1, "Gallery"))
            .currentOrPreviousPage<BrowserPage.Search>())
    }

    @Test fun nextEpisodeReplacesReaderAndBackReturnsToItsOrigin() {
        val origin = BrowserPage.LibrarySeries("Series", previewPosition = ReaderPosition(14, 27))
        val first = BrowserPage.LibraryReader("first", "Episode 1", 0, position = ReaderPosition(49, 5000))
        val workspace = BrowserWorkspace.initial(origin).pushPage(first)
        val next = BrowserPage.LibraryReader("second", "Episode 2", 0)
        val continued = workspace.continueLibraryReader(next)
        assertEquals(listOf(origin, next), continued.activeTab.history)
        assertEquals(origin, continued.goBack().activeTab.currentPage)
        assertEquals(null, next.position)
        assertEquals(continued, continued.updateReaderPosition(first.key, ReaderPosition(49, 5100)))
        val third = continued.continueLibraryReader(BrowserPage.LibraryReader("third", "Episode 3", 0))
        assertEquals(2, third.activeTab.history.size)
        assertEquals(origin, third.goBack().activeTab.currentPage)
    }

    @Test fun replacingReaderPreservesOtherTabsAndDropsForwardHistory() {
        val origin = BrowserPage.LibraryBook("first", "Episode 1", previewPosition = ReaderPosition(12, 40))
        val workspace = BrowserWorkspace.initial(origin).pushPage(BrowserPage.LibraryReader("first", "Episode 1", 12))
            .pushPage(BrowserPage.LibraryBook("stale", "Old forward page")).goBack()
        val other = workspace.openTab(BrowserPage.Search(query = "untouched"), activate = false)
        val replacement = BrowserPage.LibraryReader("second", "Episode 2", 0)
        val continued = other.continueLibraryReader(replacement)
        assertEquals(listOf(origin, replacement), continued.activeTab.history)
        assertEquals(other.tabs.last(), continued.tabs.last())
        assertEquals(origin, continued.goBack().activeTab.currentPage)
    }

    @Test fun nextEpisodeFromSeriesDropsIntermediateEpisodeDetails() {
        val home = BrowserPage.LibraryHome(query = "saved filter", scrollIndex = 4)
        val series = BrowserPage.LibrarySeries("Series", previewPosition = ReaderPosition(5, 20))
        val previous = BrowserWorkspace.initial(home)
            .pushPage(series)
            .pushPage(BrowserPage.LibraryBook("first", "Episode 1", previewPosition = ReaderPosition(49)))
            .pushPage(BrowserPage.LibraryReader("first", "Episode 1", 49))
        val next = BrowserPage.LibraryReader("second", "Episode 2", 0)
        val continued = previous.continueLibraryReader(next)
        assertEquals(listOf(home, series, next), continued.activeTab.history)
        assertEquals(series, continued.goBack().activeTab.currentPage)
    }

    @Test fun readingFromEpisodeListReturnsToSeriesDetailAndKeepsPreviewPosition() {
        val home = BrowserPage.LibraryHome(query = "tag:hi", scrollIndex = 8, scrollOffset = 13)
        val series = BrowserPage.LibrarySeries("Series", episodeList = true, previewPosition = ReaderPosition(7, 41), episodePosition = ReaderPosition(3, 25))
        val workspace = BrowserWorkspace.initial(home).pushPage(series)
            .pushPage(BrowserPage.LibraryBook("first", "First"))
            .openLibraryReader(BrowserPage.LibraryReader("first", "First", 49))
        assertEquals(listOf(home, series.copy(episodeList = false)), workspace.goBack().activeTab.history.take(2))
        assertEquals(series.copy(episodeList = false), workspace.goBack().activeTab.currentPage)
        assertEquals(home, workspace.goBack().goBack().activeTab.currentPage)
    }

    @Test fun aSeriesInAnotherTabOrBeforeHomeDoesNotStealTheReaderOrigin() {
        val old = BrowserPage.LibrarySeries("Old series")
        val home = BrowserPage.LibraryHome()
        val detail = BrowserPage.LibraryBook("standalone", "Standalone")
        val workspace = BrowserWorkspace.initial(old).pushPage(home).pushPage(detail)
            .openTab(BrowserPage.LibrarySeries("Other tab"), activate = false)
            .openLibraryReader(BrowserPage.LibraryReader("standalone", "Standalone", 0))
            .continueLibraryReader(BrowserPage.LibraryReader("next", "Next", 0))
        assertEquals(detail, workspace.goBack().activeTab.currentPage)
        assertEquals("Other tab", (workspace.tabs.last().currentPage as BrowserPage.LibrarySeries).name)
    }

    @Test
    fun archivedGroupsKeepSearchCacheReferencesUntilExplicitlyDeleted() {
        val original = BrowserWorkspace.initial(BrowserPage.Search(resultSessionId = "saved-search"))
            .let { it.createGroup(setOf(it.activeTabId)) }
        val archived = original.archiveGroup(original.groups.single().id)
        assertEquals(setOf("saved-search"), archived.searchResultSessionIds)
        assertEquals(setOf("saved-search"), archived.restoreGroup(archived.groups.single().id).searchResultSessionIds)
        assertTrue(archived.deleteArchivedGroup(archived.groups.single().id).searchResultSessionIds.isEmpty())
    }

    @Test
    fun backgroundGalleryPreservesSearchAndCanBeSelectedAndClosed() {
        val search = BrowserPage.Search(query = "artist:sample", resultIds = listOf(42),
            currentPage = 3, hasNextPage = true, scrollAnchorKey = "gallery:42", scrollIndex = 12, scrollOffset = 30)
        val original = BrowserWorkspace.initial(search)
        val opened = original.openGalleryInBackground(42, "Example")

        assertEquals(original.activeTabId, opened.workspace.activeTabId)
        assertEquals(original.activeTab, opened.workspace.activeTab)
        assertEquals(2, opened.workspace.tabs.size)
        assertFalse(opened.alreadyOpen)
        val selected = opened.workspace.selectTab(requireNotNull(opened.tabId))
        assertEquals(42L, (selected.activeTab.currentPage as BrowserPage.OnlineGallery).galleryId)
        assertEquals(original, selected.closeTab(selected.activeTabId))
    }

    @Test
    fun backgroundTabsCanExceedTwentyAndDuplicatesAreReused() {
        val original = BrowserWorkspace.initial()
        val opened = original.openGalleryInBackground(42, "Example")
        val full = (1..60).fold(opened.workspace) { workspace, id ->
            workspace.openTab(BrowserPage.OnlineGallery(id.toLong(), "Other"), activate = false)
        }
        val duplicate = full.openGalleryInBackground(42, "Example")
        assertTrue(duplicate.alreadyOpen)
        assertEquals(opened.tabId, duplicate.tabId)
        assertEquals(full, duplicate.workspace)
        val added = full.openGalleryInBackground(99, "New")
        assertEquals(63, added.workspace.tabs.size)
        assertEquals(full.tabs, added.workspace.tabs.dropLast(1))
        assertEquals(added.tabId, added.workspace.tabs.last().id)
        assertEquals(original.activeTabId, added.workspace.activeTabId)
    }

    @Test
    fun foregroundTabsCanExceedTwentyWithoutDroppingHistory() {
        val original = BrowserWorkspace.initial(BrowserPage.Search(query = "first"))
            .pushPage(BrowserPage.OnlineGallery(99, "Original"))
        val expanded = (1..60).fold(original) { workspace, id ->
            workspace.openTab(BrowserPage.Search(query = "search $id"))
        }

        assertEquals(61, expanded.tabs.size)
        assertEquals(original.activeTab, expanded.tabs.first())
        assertEquals(expanded.tabs.last().id, expanded.activeTabId)
    }

    @Test
    fun backRestoresEachSearchSnapshot() {
        val fullColor = BrowserPage.Search(query = "tag:full_color")
        val gallery = BrowserPage.OnlineGallery(1, "Gallery A")
        val multiSeries = BrowserPage.Search(query = "tag:multi_series")
        val workspace = BrowserWorkspace.initial(fullColor)
            .pushPage(gallery)
            .pushPage(multiSeries)

        val detail = workspace.goBack()
        val originalSearch = detail.goBack()

        assertEquals(gallery, detail.activeTab.currentPage)
        assertEquals(fullColor, originalSearch.activeTab.currentPage)
    }

    @Test
    fun tabsKeepIndependentHistory() {
        val first = BrowserWorkspace.initial(BrowserPage.Search(query = "first"))
            .pushPage(BrowserPage.OnlineGallery(1, "One"))
        val firstId = first.activeTabId
        val second = first.openTab(BrowserPage.Search(query = "second"))
            .pushPage(BrowserPage.OnlineGallery(2, "Two"))

        val restoredFirst = second.selectTab(firstId)

        assertEquals(BrowserPage.OnlineGallery::class, restoredFirst.activeTab.currentPage::class)
        assertEquals(1L, (restoredFirst.activeTab.currentPage as BrowserPage.OnlineGallery).galleryId)
        assertTrue(restoredFirst.activeTab.canGoBack)
        assertEquals("first", (restoredFirst.goBack().activeTab.currentPage as BrowserPage.Search).query)
    }

    @Test
    fun closingActiveTabSelectsNeighborAndKeepsOneTab() {
        val first = BrowserWorkspace.initial(BrowserPage.Search(query = "first"))
        val firstId = first.activeTabId
        val second = first.openTab(BrowserPage.Search(query = "second"))
        val afterClose = second.closeTab(second.activeTabId)

        assertEquals(firstId, afterClose.activeTabId)
        assertEquals(1, afterClose.tabs.size)

        val reset = afterClose.closeTab(firstId)
        assertEquals(1, reset.tabs.size)
        assertFalse(reset.activeTab.canGoBack)
        assertTrue(reset.activeTab.currentPage is BrowserPage.Search)
    }

    @Test
    fun pushingAfterBackDropsForwardHistory() {
        val workspace = BrowserWorkspace.initial(BrowserPage.Search(query = "one"))
            .pushPage(BrowserPage.OnlineGallery(1, "One"))
            .pushPage(BrowserPage.Search(query = "two"))
            .goBack()
            .pushPage(BrowserPage.Search(query = "three"))

        assertEquals(3, workspace.activeTab.history.size)
        assertEquals("three", (workspace.activeTab.currentPage as BrowserPage.Search).query)
    }

    @Test
    fun activeTabCanMoveWithoutChangingSelection() {
        val first = BrowserWorkspace.initial(BrowserPage.Search(query = "first"))
        val second = first.openTab(BrowserPage.Search(query = "second"))
        val activeId = second.activeTabId

        val moved = second.moveTab(activeId, -1)

        assertEquals(activeId, moved.activeTabId)
        assertEquals(activeId, moved.tabs.first().id)
    }

    @Test
    fun historyIsBounded() {
        val workspace = (1..60).fold(BrowserWorkspace.initial()) { current, id ->
            current.pushPage(BrowserPage.OnlineGallery(id.toLong(), "Gallery $id"))
        }

        assertEquals(50, workspace.activeTab.history.size)
        assertEquals(60L, (workspace.activeTab.currentPage as BrowserPage.OnlineGallery).galleryId)
    }

    @Test
    fun searchTabIsReusedRegardlessOfQueryOrder() {
        val first = BrowserWorkspace.initial(BrowserPage.Search(query = "tag:full_color artist:sample"))
        val second = first.openTab(BrowserPage.Search(query = "tag:other"))

        val selected = second.selectSearchTab("ARTIST:sample   tag:full_color")

        assertEquals(first.activeTabId, selected?.activeTabId)
        assertEquals("tag:full_color artist:sample", (selected?.activeTab?.currentPage as BrowserPage.Search).query)
    }

    @Test
    fun searchReusesGroupedAndArchivedTabsWithEquivalentConditions() {
        val first = BrowserWorkspace.initial(BrowserPage.Search(query = "tag:sample language:korean"))
        val group = BrowserTabGroup(name = "Saved")
        val grouped = first.copy(groups = listOf(group), tabs = listOf(first.activeTab.copy(groupId = group.id)))
            .openTab(BrowserPage.Search(query = "tag:other"))

        for (workspace in listOf(grouped, grouped.archiveGroup(group.id))) {
            val selected = workspace.selectSearchTab("sort:newest tag:sample", setOf("korean"))
            assertEquals(first.activeTabId, selected?.activeTabId)
            assertEquals(group.id, selected?.activeTab?.groupId)
            assertEquals(2, selected?.tabs?.size)
        }
    }

    @Test
    fun missingSearchCreatesStandaloneTabWithoutBackHistory() {
        val workspace = BrowserWorkspace.initial(BrowserPage.Search(query = "tag:first"))
        val query = "tag:second"
        val selected = workspace.selectSearchTab(query)
            ?: workspace.openTab(BrowserPage.Search(query = query))

        assertEquals(2, selected.tabs.size)
        assertEquals(query, (selected.activeTab.currentPage as BrowserPage.Search).query)
        assertFalse(selected.activeTab.canGoBack)
    }

    @Test
    fun galleryOnlyOpensNewWindowAfterManualTabCreation() {
        val first = BrowserWorkspace.initial(BrowserPage.LibraryHome())
            .pushPage(BrowserPage.LibraryBook("book-a", "Gallery A"))
        val firstId = first.activeTabId
        val second = first.openTab(BrowserPage.LibraryHome())
            .pushPage(BrowserPage.LibraryBook("book-b", "Gallery B"))

        assertEquals(2, second.tabs.size)
        assertEquals("book-a", (second.tabs.first { it.id == firstId }.currentPage as BrowserPage.LibraryBook).bookId)
        assertEquals("book-b", (second.activeTab.currentPage as BrowserPage.LibraryBook).bookId)
        assertTrue(second.activeTab.canGoBack)
    }

    @Test
    fun commaAndSpaceSeparatedSearchesShareOneFormat() {
        assertEquals("tag1 tag2 tag3", normalizeSearchSeparators("tag1,tag2,tag3"))
        assertEquals("tag1 tag2 tag3", normalizeSearchSeparators("tag1, tag2, tag3"))
        assertEquals(
            searchTabKey("tag1 tag2 tag3"),
            searchTabKey("tag3, tag1,tag2"),
        )
    }
}
