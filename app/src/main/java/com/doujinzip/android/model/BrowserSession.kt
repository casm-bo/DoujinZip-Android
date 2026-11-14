package com.doujinzip.android.model

import java.util.UUID

sealed interface BrowserPage {
    val key: String

    data class LibraryHome(
        override val key: String = UUID.randomUUID().toString(),
        val query: String = "",
        val scrollAnchorKey: String? = null,
        val scrollIndex: Int = 0,
        val scrollOffset: Int = 0,
    ) : BrowserPage

    data class Search(
        override val key: String = UUID.randomUUID().toString(),
        val query: String = "",
        val preferredLanguages: Set<String> = emptySet(),
        val submittedQuery: String = "",
        val submittedQueries: List<String> = emptyList(),
        val resultIds: List<Long> = emptyList(),
        val results: List<GallerySummary> = emptyList(),
        val currentPage: Int = 0,
        val hasNextPage: Boolean = false,
        val scrollAnchorKey: String? = null,
        val scrollIndex: Int = 0,
        val scrollOffset: Int = 0,
        val resultSessionId: String? = null,
        val scrollPage: Int = 1,
    ) : BrowserPage

    data class LibrarySeries(
        val name: String,
        override val key: String = UUID.randomUUID().toString(),
        val episodeList: Boolean = false,
        val previewPosition: ReaderPosition = ReaderPosition(0),
        val episodePosition: ReaderPosition = ReaderPosition(0),
    ) : BrowserPage

    data class OnlineGallery(
        val galleryId: Long,
        val title: String,
        override val key: String = UUID.randomUUID().toString(),
        val previewPosition: ReaderPosition = ReaderPosition(0),
        val returnToSubscriptionNotices: Boolean = false,
    ) : BrowserPage

    data class LibraryBook(
        val bookId: String,
        val title: String,
        override val key: String = UUID.randomUUID().toString(),
        val previewPosition: ReaderPosition = ReaderPosition(0),
    ) : BrowserPage

    data class OnlineReader(
        val galleryId: Long,
        val title: String,
        val startPage: Int,
        override val key: String = UUID.randomUUID().toString(),
        val position: ReaderPosition? = null,
    ) : BrowserPage

    data class LibraryReader(
        val bookId: String,
        val title: String,
        val startPage: Int,
        override val key: String = UUID.randomUUID().toString(),
        val position: ReaderPosition? = null,
    ) : BrowserPage
}

data class ReaderPosition(val page: Int, val offset: Int = 0) {
    init { require(page >= 0 && offset >= 0) }
}

private fun BrowserWorkspace.mapPages(transform: (BrowserPage) -> BrowserPage): BrowserWorkspace {
    fun BrowserTab.update() = copy(history = history.map(transform))
    return copy(tabs = tabs.map { it.update() }, groups = groups.map { group -> group.copy(archivedTabs = group.archivedTabs.map { it.update() }) })
}

internal fun BrowserWorkspace.invalidateSearchResults(): BrowserWorkspace = mapPages { page ->
    if (page is BrowserPage.Search) page.copy(submittedQueries = emptyList(), resultIds = emptyList(), results = emptyList(),
        resultSessionId = null, currentPage = if (page.currentPage > 0) 1 else 0, hasNextPage = false,
        scrollAnchorKey = null, scrollIndex = 0, scrollOffset = 0, scrollPage = 1) else page
}

fun BrowserWorkspace.updateReaderPosition(pageKey: String, position: ReaderPosition): BrowserWorkspace = mapPages { page ->
    when {
        page.key != pageKey -> page
        page is BrowserPage.OnlineReader -> page.copy(position = position)
        page is BrowserPage.LibraryReader -> page.copy(position = position)
        else -> page
    }
}

fun BrowserWorkspace.updatePreviewPosition(pageKey: String, position: ReaderPosition): BrowserWorkspace = mapPages { page ->
    when {
        page.key != pageKey -> page
        page is BrowserPage.OnlineGallery -> page.copy(previewPosition = position)
        page is BrowserPage.LibraryBook -> page.copy(previewPosition = position)
        page is BrowserPage.LibrarySeries -> page.copy(previewPosition = position)
        else -> page
    }
}

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val history: List<BrowserPage> = listOf(BrowserPage.Search()),
    val currentIndex: Int = 0,
    val groupId: String? = null,
    val returnTo: NewTabReturn? = null,
) {
    val currentPage: BrowserPage
        get() = history[currentIndex.coerceIn(history.indices)]

    val canGoBack: Boolean
        get() = currentIndex > 0
}

data class BrowserWorkspace(
    val tabs: List<BrowserTab>,
    val activeTabId: String,
    val groups: List<BrowserTabGroup> = emptyList(),
    val overviewOrder: List<String> = emptyList(),
) {
    val activeTab: BrowserTab
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()

    companion object {
        fun initial(page: BrowserPage = BrowserPage.Search()): BrowserWorkspace {
            val tab = BrowserTab(history = listOf(page))
            return BrowserWorkspace(tabs = listOf(tab), activeTabId = tab.id)
        }
    }
}

fun BrowserWorkspace.selectTab(tabId: String): BrowserWorkspace =
    if (tabs.any { it.id == tabId }) copy(activeTabId = tabId) else this

/** Keep a departing screen bound to its own page while the detail route enters. */
inline fun <reified T : BrowserPage> BrowserWorkspace.currentOrPreviousPage(): T? {
    val tab = activeTab
    for (index in tab.currentIndex.coerceIn(tab.history.indices) downTo 0) {
        val page = tab.history[index]
        if (page is T) return page
    }
    return null
}

val BrowserWorkspace.searchResultSessionIds: Set<String>
    get() = (tabs + groups.flatMap { it.archivedTabs }).flatMap { it.history }
        .filterIsInstance<BrowserPage.Search>().mapNotNull { it.resultSessionId }.toSet()

/** A departing search can finish saving after another tab/page became active. */
fun BrowserWorkspace.updateSearchScroll(
    pageKey: String, sessionId: String?, anchorKey: String?, index: Int, offset: Int, scrollPage: Int = 1,
): BrowserWorkspace {
    fun BrowserTab.saveScroll() = copy(history = history.map { page ->
        if (page is BrowserPage.Search && page.key == pageKey && page.resultSessionId == sessionId) {
            page.copy(scrollAnchorKey = anchorKey, scrollIndex = index.coerceAtLeast(0), scrollOffset = offset.coerceAtLeast(0), scrollPage = scrollPage.coerceAtLeast(1))
        } else page
    })
    return copy(tabs = tabs.map { it.saveScroll() }, groups = groups.map { group ->
        group.copy(archivedTabs = group.archivedTabs.map { it.saveScroll() })
    })
}

fun BrowserWorkspace.updateLibraryScroll(pageKey: String, anchor: String?, index: Int, offset: Int): BrowserWorkspace = mapPages { page ->
    if (page is BrowserPage.LibraryHome && page.key == pageKey) page.copy(scrollAnchorKey = anchor,
        scrollIndex = index.coerceAtLeast(0), scrollOffset = offset.coerceAtLeast(0)) else page
}

fun BrowserWorkspace.selectSearchTab(
    query: String,
    preferredLanguages: Set<String> = emptySet(),
    keepActivePage: Boolean = false,
): BrowserWorkspace? {
    val queryKey = searchTabKey(query, preferredLanguages)
    val archivedTabs = groups.flatMap { it.archivedTabs }
    (listOf(activeTab) + tabs.filterNot { it.id == activeTabId } + archivedTabs).forEach { tab ->
        val candidates = if (keepActivePage && tab.id == activeTabId) listOf(tab.currentIndex)
            else listOf(tab.currentIndex) + tab.history.indices.reversed()
        val index = candidates.firstOrNull { index ->
            val page = tab.history[index] as? BrowserPage.Search
            page != null && searchTabKey(page.query, page.preferredLanguages) == queryKey
        } ?: return@forEach
        val workspace = if (tab in archivedTabs) restoreGroup(requireNotNull(tab.groupId)) else this
        return workspace.copy(
            tabs = workspace.tabs.map { if (it.id == tab.id) it.copy(currentIndex = index) else it },
            activeTabId = tab.id,
        )
    }
    return null
}

fun BrowserWorkspace.openTab(
    page: BrowserPage = BrowserPage.Search(),
    activate: Boolean = true,
    groupId: String? = null,
): BrowserWorkspace {
    val tab = BrowserTab(history = listOf(page), groupId = groupId?.takeIf { id ->
        groups.any { it.id == id && !it.isArchived }
    })
    return copy(tabs = tabs + tab, activeTabId = if (activate) tab.id else activeTabId)
}

data class BackgroundGalleryTab(
    val workspace: BrowserWorkspace,
    val tabId: String?,
    val alreadyOpen: Boolean = false,
    val galleryWorkspace: Boolean = false,
)

fun BrowserWorkspace.openGalleryInBackground(galleryId: Long, title: String): BackgroundGalleryTab {
    tabs.firstOrNull { it.groupId == null &&
        (it.currentPage as? BrowserPage.OnlineGallery)?.galleryId == galleryId }?.let {
        return BackgroundGalleryTab(this, it.id, alreadyOpen = true)
    }
    val next = openTab(BrowserPage.OnlineGallery(galleryId, title), activate = false)
    return BackgroundGalleryTab(next, next.tabs.last().id.takeIf { next != this })
}

fun BrowserWorkspace.closeTab(
    tabId: String,
    fallback: BrowserPage = BrowserPage.Search(),
): BrowserWorkspace {
    val closingIndex = tabs.indexOfFirst { it.id == tabId }
    if (closingIndex < 0) return this
    if (tabs.size == 1) {
        val tab = BrowserTab(history = listOf(fallback))
        return copy(tabs = listOf(tab), activeTabId = tab.id).normalizeGroups()
    }

    val remaining = tabs.filterNot { it.id == tabId }
    if (activeTabId == tabId && tabs[closingIndex].groupId == null) {
        val standalone = overviewItems.filterIsInstance<BrowserOverviewItem.Tab>().map { it.tab }
        val index = standalone.indexOfFirst { it.id == tabId }
        val neighbor = standalone.getOrNull(index - 1) ?: standalone.getOrNull(index + 1)
        val next = neighbor ?: BrowserTab(history = listOf(fallback))
        return copy(tabs = if (neighbor == null) remaining + next else remaining, activeTabId = next.id).normalizeGroups()
    }
    val sameGroupNeighbor = remaining.firstOrNull { it.groupId == tabs[closingIndex].groupId }
    val next = sameGroupNeighbor ?: remaining[closingIndex.coerceAtMost(remaining.lastIndex)]
    return copy(tabs = remaining, activeTabId = if (activeTabId == tabId) next.id else activeTabId)
        .normalizeGroups()
}

fun BrowserWorkspace.pushPage(page: BrowserPage): BrowserWorkspace = updateActiveTab { tab ->
    val history = (tab.history.take(tab.currentIndex + 1) + page).boundedTabHistory()
    tab.copy(history = history, currentIndex = history.lastIndex)
}

private fun List<BrowserPage>.withLibraryReader(page: BrowserPage.LibraryReader): List<BrowserPage> {
    val seriesOrigin = indexOfLast { it is BrowserPage.LibrarySeries }
        .takeIf { it > indexOfLast { entry -> entry is BrowserPage.LibraryHome } }
    val origin = if (seriesOrigin != null) take(seriesOrigin + 1).mapIndexed { index, entry ->
        if (index == seriesOrigin) (entry as BrowserPage.LibrarySeries).copy(episodeList = false) else entry
    } else this
    return (origin + page).boundedTabHistory()
}

internal fun List<BrowserPage>.boundedTabHistory(): List<BrowserPage> =
    if (size <= MAX_TAB_HISTORY) this else take(1) + takeLast(MAX_TAB_HISTORY - 1)

fun BrowserWorkspace.openLibraryReader(page: BrowserPage.LibraryReader): BrowserWorkspace = updateActiveTab { tab ->
    val history = tab.history.take(tab.currentIndex + 1).withLibraryReader(page)
    tab.copy(history = history, currentIndex = history.lastIndex)
}

fun BrowserWorkspace.continueLibraryReader(page: BrowserPage.LibraryReader): BrowserWorkspace = updateActiveTab { tab ->
    val history = tab.history.take(tab.currentIndex).withLibraryReader(page)
    tab.copy(history = history, currentIndex = history.lastIndex)
}

fun BrowserWorkspace.updateLibrarySeries(pageKey: String, transform: (BrowserPage.LibrarySeries) -> BrowserPage.LibrarySeries): BrowserWorkspace = mapPages { page ->
    if (page is BrowserPage.LibrarySeries && page.key == pageKey) transform(page) else page
}

fun BrowserWorkspace.renameSeriesPages(oldName: String, newName: String): BrowserWorkspace = mapPages { page ->
    if (page is BrowserPage.LibrarySeries && page.name == oldName) page.copy(name = newName) else page
}

internal fun BrowserWorkspace.reconcileSeriesPages(
    previous: Map<String, CustomSeriesAssignment>, current: Map<String, CustomSeriesAssignment>,
): BrowserWorkspace {
    val names = current.values.mapTo(hashSetOf()) { it.name }
    val replacements = previous.entries.groupBy { it.value.name }.mapValues { (_, members) ->
        members.mapNotNull { current[it.key]?.name }.distinct().singleOrNull()
    }
    return mapPages { page ->
        if (page !is BrowserPage.LibrarySeries || page.name in names) page
        else replacements[page.name]?.let { page.copy(name = it) } ?: BrowserPage.LibraryHome(key = page.key)
    }
}

fun BrowserWorkspace.goBack(): BrowserWorkspace = updateActiveTab { tab ->
    if (tab.canGoBack) tab.copy(currentIndex = tab.currentIndex - 1) else tab
}

fun BrowserWorkspace.updateActiveSearch(
    transform: (BrowserPage.Search) -> BrowserPage.Search,
): BrowserWorkspace = updateActiveTab { tab ->
    val search = tab.currentPage as? BrowserPage.Search ?: return@updateActiveTab tab
    tab.copy(history = tab.history.toMutableList().apply {
        this[tab.currentIndex] = transform(search)
    })
}

fun BrowserWorkspace.updateActiveLibraryHome(
    transform: (BrowserPage.LibraryHome) -> BrowserPage.LibraryHome,
): BrowserWorkspace = updateActiveTab { tab ->
    val page = tab.currentPage as? BrowserPage.LibraryHome ?: return@updateActiveTab tab
    tab.copy(history = tab.history.toMutableList().apply {
        this[tab.currentIndex] = transform(page)
    })
}

internal fun resolveScrollIndex(
    anchorKey: String?,
    fallbackIndex: Int,
    itemKeys: List<String>,
): Int = anchorKey?.let(itemKeys::indexOf)?.takeIf { it >= 0 }
    ?: fallbackIndex.coerceIn(0, itemKeys.lastIndex.coerceAtLeast(0))

fun BrowserWorkspace.moveTab(tabId: String, offset: Int): BrowserWorkspace {
    val from = tabs.indexOfFirst { it.id == tabId }
    val to = (from + offset).coerceIn(tabs.indices)
    if (from < 0 || from == to) return this
    val reordered = tabs.toMutableList()
    val tab = reordered.removeAt(from)
    reordered.add(to, tab)
    return copy(tabs = reordered)
}

private fun BrowserWorkspace.updateActiveTab(
    transform: (BrowserTab) -> BrowserTab,
): BrowserWorkspace = copy(tabs = tabs.map { tab ->
    if (tab.id == activeTabId) transform(tab) else tab
})

val BrowserPage.label: String
    get() = when (this) {
        is BrowserPage.LibraryHome -> "새 갤러리"
        is BrowserPage.LibrarySeries -> name
        is BrowserPage.Search -> query.ifBlank { "새 검색" }
        is BrowserPage.OnlineGallery -> title.ifBlank { "갤러리 $galleryId" }
        is BrowserPage.LibraryBook -> title.ifBlank { "다운로드 작품" }
        is BrowserPage.OnlineReader -> title.ifBlank { "갤러리 $galleryId" }
        is BrowserPage.LibraryReader -> title.ifBlank { "다운로드 작품" }
    }

internal fun searchTabKey(
    query: String,
    preferredLanguages: Set<String> = emptySet(),
): String = queriesWithPreferredLanguages(query, preferredLanguages)
    .map { effectiveQuery ->
        val terms = searchQueryTokens(effectiveQuery).map(String::lowercase)
        (if (terms.none { it.startsWith("sort:") }) terms + "sort:newest" else terms)
            .distinct().sorted().joinToString("\u0000")
    }
    .distinct().sorted().joinToString("\u0001")

internal fun normalizeSearchSeparators(query: String): String = searchQueryTokens(query).joinToString(" ")

internal fun queriesWithPreferredLanguages(query: String, preferredLanguages: Set<String>): List<String> {
    val normalized = normalizeSearchSeparators(query)
    if (preferredLanguages.isEmpty()) return listOf(normalized.ifBlank { "sort:newest" })
    val base = normalized.replace(Regex("(?i)(^|\\s)-?language:[^\\s]+"), " ")
        .trim().replace(Regex("\\s+"), " ")
    return preferredLanguages.sorted().map { language ->
        listOf(base, "language:$language").filter(String::isNotBlank).joinToString(" ")
    }
}

private const val MAX_TAB_HISTORY = 50
