package com.doujinzip.android.ui

import com.doujinzip.android.model.*
import org.junit.Assert.*
import org.junit.Test

class BrowserTabsOverviewTest {
    @Test fun activeTabIsLocatedInDisplayedOrderIncludingGroupCards() {
        val tabs = (1..20).map { BrowserTab(id = "tab$it") }
        val workspace = BrowserWorkspace(tabs, "tab18").createGroup(setOf("tab2", "tab3"), "Fixture")
        val items = workspace.overviewItems
        assertEquals("tab:tab18", items[items.activeTabIndex("tab18")].key)
        assertEquals("group:${workspace.groups.single().id}", items[items.activeTabIndex("tab3")].key)
        assertEquals(1, tabs.take(3).map { BrowserOverviewItem.Tab(it) }.activeTabIndex("tab2"))
        assertEquals(-1, items.activeTabIndex("missing"))
    }

    @Test fun mergingGroupsPreservesDestinationStateOrderAndEveryHistory() {
        for (sourceArchived in listOf(false, true)) for (targetArchived in listOf(false, true)) {
            val tabs = (1..5).map { BrowserTab(id = "tab$it", history = listOf(BrowserPage.Search(query = "fixture$it"))) }
            var workspace = BrowserWorkspace(tabs, tabs[0].id)
                .createGroup(setOf("tab1", "tab2"), "Source", 1)
                .createGroup(setOf("tab3", "tab4"), "Target", 4)
            val source = workspace.groups[0].id; val target = workspace.groups[1].id
            if (sourceArchived) workspace = workspace.archiveGroup(source)
            if (targetArchived) workspace = workspace.archiveGroup(target)
            val merged = workspace.mergeGroups(source, target)
            val group = merged.groups.single()
            assertEquals("Target", group.name); assertEquals(4, group.colorIndex)
            assertEquals(targetArchived, group.isArchived)
            val members = if (group.isArchived) group.archivedTabs else merged.tabs.filter { it.groupId == target }
            assertEquals(listOf("tab3", "tab4", "tab1", "tab2"), members.map { it.id })
            assertEquals(tabs.associate { it.id to it.history }, (merged.tabs + group.archivedTabs).associate { it.id to it.history })
            assertTrue(merged.tabs.any { it.id == merged.activeTabId })
            assertEquals(merged, merged.mergeGroups(target, target))
            assertEquals(merged, merged.mergeGroups("missing", target))
        }
    }
    @Test fun mergingIntoArchivedGroupKeepsGalleryFallbackWhenAllOpenTabsMove() {
        val initial = BrowserWorkspace.initial().openTab(BrowserPage.Search(query = "fixture"))
        var workspace = initial.createGroup(setOf(initial.tabs[0].id), "Source").createGroup(setOf(initial.tabs[1].id), "Target")
        val source = workspace.groups[0].id; val target = workspace.groups[1].id
        workspace = workspace.archiveGroup(target)
        val merged = workspace.mergeGroups(source, target, BrowserPage.LibraryHome())
        assertTrue(merged.activeTab.currentPage is BrowserPage.LibraryHome)
        assertEquals(2, merged.groups.single().archivedTabs.size)
    }

    @Test
    fun committingDragOrderRetainsDataLoadedDuringGesture() {
        val original = BrowserWorkspace.initial(BrowserPage.Search(query = "one"))
            .openTab(BrowserPage.Search(query = "two"))
        val draft = original.moveOverviewItem("tab:${original.tabs.first().id}", "tab:${original.tabs.last().id}")
        val latest = original.updateActiveSearch { it.copy(query = "edited during drag", currentPage = 3) }
        val committed = latest.commitTabDragOrder(draft)
        assertEquals(latest.activeTab, committed.activeTab)
        assertEquals(draft.overviewItems.map { it.key }, committed.overviewItems.map { it.key })
    }

    @Test
    fun droppingOnTabCreatesGroupAndDroppingOnGroupKeepsExistingHistory() {
        val original = BrowserWorkspace.initial(BrowserPage.Search(query = "one"))
            .openTab(BrowserPage.Search(query = "two"))
            .openTab(BrowserPage.Search(query = "three"))
        val grouped = original.mergeTabDrop("tab:${original.tabs[0].id}", "tab:${original.tabs[1].id}")
        assertEquals(1, grouped.groups.size)
        assertEquals(2, grouped.overviewItems.size)
        val merged = grouped.mergeTabDrop("tab:${original.tabs[2].id}", "group:${grouped.groups.single().id}")
        assertEquals(1, merged.overviewItems.size)
        assertEquals(original.tabs.map { it.history }, merged.tabs.map { it.history })
        assertEquals(original.activeTabId, merged.activeTabId)
        assertEquals(merged, merged.mergeTabDrop("group:${merged.groups.single().id}", "tab:${original.tabs[0].id}"))
    }

    @Test
    fun cardEdgesReorderAndCenterAllowsGrouping() {
        assertTrue(isTabDropCenter(100f, 140f, 200f, 280f))
        assertFalse(isTabDropCenter(10f, 140f, 200f, 280f))
        assertFalse(isTabDropCenter(100f, 270f, 200f, 280f))
    }

}
