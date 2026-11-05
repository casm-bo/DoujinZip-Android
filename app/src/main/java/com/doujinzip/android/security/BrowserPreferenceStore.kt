package com.doujinzip.android.security

import android.content.Context
import com.doujinzip.android.model.AppThemeMode
import com.doujinzip.android.model.ReaderPosition
import com.doujinzip.android.model.BrowserPage
import com.doujinzip.android.model.BrowserTab
import com.doujinzip.android.model.BrowserTabGroup
import com.doujinzip.android.model.BrowserWorkspace
import com.doujinzip.android.model.NewTabReturn
import com.doujinzip.android.model.boundedTabHistory
import com.doujinzip.android.model.normalizeGroups
import com.doujinzip.android.model.SearchFavorite
import com.doujinzip.android.model.SearchQueryEntry
import com.doujinzip.android.model.StorageLocation
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class BrowserPreferenceStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    fun loadLastDesktopSync(): Long = preferences.getLong("last_desktop_sync", 0L)
    fun saveLastDesktopSync(value: Long) { preferences.edit().putLong("last_desktop_sync", value).apply() }

    @Synchronized
    fun rememberLibraryScope(libraryId: String, fallback: String): String {
        val key = "library_scope:$libraryId"
        return preferences.getString(key, null) ?: fallback.also {
            preferences.edit().putString(key, it).apply()
        }
    }

    fun loadSearchTagFilters(): Set<com.doujinzip.android.model.SearchTagFilter> =
        com.doujinzip.android.model.SearchTagFilter.entries.filter { it.name in preferences.getStringSet("search_tag_filters", emptySet()).orEmpty() }.toSet()
    fun saveSearchTagFilters(value: Set<com.doujinzip.android.model.SearchTagFilter>) =
        preferences.edit().putStringSet("search_tag_filters", value.map { it.name }.toSet()).apply()
    fun loadCustomBlockedTags(): Set<String> = runCatching {
        com.doujinzip.android.model.parseBlockedSearchTags(preferences.getStringSet("custom_blocked_tags",emptySet()).orEmpty().joinToString(" "))
    }.getOrDefault(emptySet())
    fun saveCustomBlockedTags(value: Set<String>) = preferences.edit().putStringSet("custom_blocked_tags",
        com.doujinzip.android.model.parseBlockedSearchTags(value.joinToString(" "))).apply()

    fun loadDownloadDestination(): com.doujinzip.android.model.DownloadDestination = runCatching {
        com.doujinzip.android.model.DownloadDestination.valueOf(preferences.getString("download_destination", null).orEmpty())
    }.getOrDefault(com.doujinzip.android.model.DownloadDestination.ASK)
    fun saveDownloadDestination(value: com.doujinzip.android.model.DownloadDestination) = preferences.edit().putString("download_destination", value.name).apply()
    fun isDesktopDownloadAlertSuppressed(): Boolean = preferences.getBoolean("hide_desktop_download_alert", false)
    fun suppressDesktopDownloadAlert(value: Boolean) = preferences.edit().putBoolean("hide_desktop_download_alert", value).apply()

    fun loadFavorites(): List<SearchFavorite> {
        val raw = preferences.getString(KEY_FAVORITES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.optJSONObject(index) ?: return@repeat
                    val query = item.optString("query").trim()
                    if (query.isEmpty()) return@repeat
                    add(
                        SearchFavorite(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            name = item.optString("name").ifBlank { query },
                            query = query,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveFavorites(favorites: List<SearchFavorite>) {
        val array = JSONArray()
        favorites.forEach { favorite ->
            array.put(
                JSONObject()
                    .put("id", favorite.id)
                    .put("name", favorite.name)
                    .put("query", favorite.query),
            )
        }
        preferences.edit().putString(KEY_FAVORITES, array.toString()).apply()
    }

    fun loadPreferredLanguages(): Set<String> =
        preferences.getStringSet(KEY_LANGUAGES, emptySet()).orEmpty()
            .mapTo(linkedSetOf()) { it.trim().lowercase() }
            .filterTo(linkedSetOf()) { it.isNotEmpty() }

    fun loadSearchQueryHistory(key: String = "search_query_history"): List<SearchQueryEntry> = runCatching {
        val array = JSONArray(preferences.getString(key, "[]"))
        List(minOf(array.length(), 50)) { index ->
            val item = array.getJSONObject(index)
            SearchQueryEntry(item.getString("query"), item.optJSONArray("languages").stringSet())
        }
    }.getOrDefault(emptyList())

    fun saveSearchQueryHistory(entries: List<SearchQueryEntry>, key: String = "search_query_history") {
        val array = JSONArray(entries.take(50).map { entry ->
            JSONObject().put("query", entry.query).put("languages", JSONArray(entry.languages.toList()))
        })
        preferences.edit().putString(key, array.toString()).apply()
    }

    fun savePreferredLanguages(languages: Set<String>) {
        preferences.edit().putStringSet(KEY_LANGUAGES, languages).apply()
    }

    fun loadCustomLanguages(): Set<String> =
        preferences.getStringSet(KEY_CUSTOM_LANGUAGES, emptySet()).orEmpty()
            .mapTo(linkedSetOf()) { it.trim().lowercase() }
            .filterTo(linkedSetOf()) { it.isNotEmpty() }

    fun saveCustomLanguages(languages: Set<String>) {
        preferences.edit().putStringSet(KEY_CUSTOM_LANGUAGES, languages).apply()
    }

    fun loadKnownFilterTokens(): Set<String> =
        preferences.getStringSet(KEY_KNOWN_FILTERS, emptySet()).orEmpty().toSet()

    fun saveKnownFilterTokens(tokens: Set<String>) {
        preferences.edit().putStringSet(KEY_KNOWN_FILTERS, tokens).apply()
    }

    fun loadViewedGalleryIds(): Set<Long> =
        preferences.getStringSet(KEY_VIEWED_GALLERIES, emptySet()).orEmpty()
            .mapNotNullTo(linkedSetOf(), String::toLongOrNull)

    fun saveViewedGalleryIds(ids: Set<Long>) {
        preferences.edit().putStringSet(
            KEY_VIEWED_GALLERIES,
            ids.mapTo(linkedSetOf(), Long::toString),
        ).apply()
    }

    fun loadThemeMode(): AppThemeMode = runCatching {
        AppThemeMode.valueOf(preferences.getString(KEY_THEME_MODE, null).orEmpty())
    }.getOrDefault(AppThemeMode.SYSTEM)

    fun saveThemeMode(mode: AppThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun loadBrowserWorkspace(): BrowserWorkspace {
        val raw = preferences.getString(KEY_SEARCH_WORKSPACE, null)
            ?: preferences.getString(KEY_BROWSER_WORKSPACE, null)
            ?: return BrowserWorkspace.initial()
        return browserWorkspaceFromJson(raw).searchWorkspace()
    }

    fun saveBrowserWorkspace(workspace: BrowserWorkspace) {
        preferences.edit().putString(KEY_SEARCH_WORKSPACE, browserWorkspaceToJson(workspace)).apply()
    }

    fun loadActiveGalleryWorkspace(): Boolean = preferences.getBoolean("active_gallery_workspace", false)
    fun saveActiveGalleryWorkspace(gallery: Boolean) { preferences.edit().putBoolean("active_gallery_workspace", gallery).apply() }
    fun loadMainDestination(): String = preferences.getString("main_destination", "browser")
        ?.takeIf { it in setOf("browser", "library", "history", "downloads", "settings") } ?: "browser"
    fun saveMainDestination(route: String) { preferences.edit().putString("main_destination", route).apply() }

    fun loadGalleryWorkspace(): BrowserWorkspace {
        val raw = preferences.getString(KEY_GALLERY_WORKSPACE, null)
            ?: preferences.getString(KEY_BROWSER_WORKSPACE, null)
            ?: return BrowserWorkspace.initial(BrowserPage.LibraryHome())
        return browserWorkspaceFromJson(raw).galleryWorkspace()
    }

    fun saveGalleryWorkspace(workspace: BrowserWorkspace) {
        preferences.edit().putString(KEY_GALLERY_WORKSPACE, browserWorkspaceToJson(workspace)).apply()
    }

    fun loadLibraryLocations(): List<StorageLocation> =
        loadStorageLocations(KEY_LIBRARY_LOCATIONS)

    fun saveLibraryLocations(locations: List<StorageLocation>) {
        saveStorageLocations(KEY_LIBRARY_LOCATIONS, locations)
    }

    fun loadDownloadLocation(): StorageLocation? =
        loadStorageLocations(KEY_DOWNLOAD_LOCATION).firstOrNull()

    fun saveDownloadLocation(location: StorageLocation?) {
        saveStorageLocations(KEY_DOWNLOAD_LOCATION, listOfNotNull(location))
    }

    private fun loadStorageLocations(key: String): List<StorageLocation> {
        val raw = preferences.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.optJSONObject(index) ?: return@repeat
                    val uri = item.optString("uri").trim()
                    if (uri.isEmpty()) return@repeat
                    add(
                        StorageLocation(
                            uri = uri,
                            displayName = item.optString("displayName").ifBlank { uri },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveStorageLocations(key: String, locations: List<StorageLocation>) {
        val array = JSONArray()
        locations.forEach { location ->
            array.put(
                JSONObject()
                    .put("uri", location.uri)
                    .put("displayName", location.displayName),
            )
        }
        preferences.edit().putString(key, array.toString()).apply()
    }

    private companion object {
        const val FILE_NAME = "browser_preferences"
        const val KEY_FAVORITES = "search_favorites"
        const val KEY_LANGUAGES = "preferred_languages"
        const val KEY_CUSTOM_LANGUAGES = "custom_languages"
        const val KEY_KNOWN_FILTERS = "known_filter_tokens"
        const val KEY_VIEWED_GALLERIES = "viewed_gallery_ids"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_BROWSER_WORKSPACE = "browser_workspace"
        const val KEY_SEARCH_WORKSPACE = "search_workspace"
        const val KEY_GALLERY_WORKSPACE = "gallery_workspace"
        const val KEY_LIBRARY_LOCATIONS = "library_locations"
        const val KEY_DOWNLOAD_LOCATION = "download_location"
    }
}

internal fun browserWorkspaceToJson(workspace: BrowserWorkspace): String = JSONObject()
    .put("version", 2)
    .put("activeTabId", workspace.activeTabId)
    .put("tabs", workspace.tabs.toJson())
    .put("overviewOrder", JSONArray(workspace.overviewOrder))
    .put("groups", JSONArray().apply {
        workspace.groups.forEach { group ->
            put(JSONObject().put("id", group.id).put("name", group.name)
                .put("colorIndex", group.colorIndex).put("archivedTabs", group.archivedTabs.toJson()))
        }
    })
    .toString()

internal fun browserWorkspaceFromJson(raw: String): BrowserWorkspace = runCatching {
    val root = JSONObject(raw)
    val tabs = root.optJSONArray("tabs").browserTabs().ifEmpty { BrowserWorkspace.initial().tabs }
    val usedIds = tabs.mapTo(mutableSetOf()) { it.id }
    val groupsJson = root.optJSONArray("groups")
    val groups = buildList {
        repeat(groupsJson?.length() ?: 0) { index ->
            val item = groupsJson?.optJSONObject(index) ?: return@repeat
            val id = item.optString("id").takeIf(String::isNotBlank) ?: return@repeat
            add(BrowserTabGroup(
                id = id,
                name = item.optString("name").ifBlank { "새 그룹" },
                colorIndex = item.optInt("colorIndex").coerceIn(0, 5),
                archivedTabs = item.optJSONArray("archivedTabs").browserTabs()
                    .filter { usedIds.add(it.id) }.map { it.copy(groupId = id) },
            ))
        }
    }
    val requestedActiveId = root.optString("activeTabId")
    BrowserWorkspace(
        tabs = tabs,
        activeTabId = requestedActiveId.takeIf { id -> tabs.any { it.id == id } } ?: tabs.first().id,
        groups = groups,
        overviewOrder = root.optJSONArray("overviewOrder").stringList(),
    ).normalizeGroups()
}.getOrElse { BrowserWorkspace.initial() }

private fun List<BrowserTab>.toJson(): JSONArray = JSONArray().apply {
    this@toJson.forEach { tab ->
        put(JSONObject().put("id", tab.id).put("currentIndex", tab.currentIndex)
            .put("returnTo", tab.returnTo?.let { entry ->
                JSONObject().put("isGallery", entry.isGallery).put("originTabId", entry.originTabId)
                    .put("originIsGallery", entry.originIsGallery)
            })
            .put("groupId", tab.groupId).put("history", JSONArray().apply {
                tab.history.forEach { put(it.toJson()) }
            }))
    }
}

private fun JSONArray?.browserTabs(): List<BrowserTab> {
    val tabsJson = this ?: return emptyList()
    return buildList {
        repeat(tabsJson.length()) { index ->
            val item = tabsJson.optJSONObject(index) ?: return@repeat
            val id = item.optString("id").takeIf(String::isNotBlank) ?: return@repeat
            val historyJson = item.optJSONArray("history") ?: return@repeat
            val rawHistory = buildList {
                repeat(historyJson.length()) { pageIndex ->
                    historyJson.optJSONObject(pageIndex)?.toBrowserPage()?.let(::add)
                }
            }
            val history = rawHistory.boundedTabHistory()
            if (history.isEmpty()) return@repeat
            val droppedHistoryCount = rawHistory.size - history.size
            val savedIndex = item.optInt("currentIndex", 0).coerceIn(rawHistory.indices)
            add(BrowserTab(
                id = id,
                history = history,
                currentIndex = (savedIndex - droppedHistoryCount).coerceAtLeast(if (savedIndex == 0) 0 else 1)
                    .coerceIn(history.indices),
                groupId = item.optString("groupId").takeIf { it.isNotBlank() && it != "null" },
                returnTo = item.optJSONObject("returnTo")?.let { entry ->
                    entry.optString("originTabId").takeIf { it.isNotBlank() && it != "null" && it != id }?.let { origin ->
                        NewTabReturn(id, entry.optBoolean("isGallery"), origin, entry.optBoolean("originIsGallery"))
                    }
                },
            ))
        }
    }.distinctBy { it.id }
}

private fun BrowserPage.toJson(): JSONObject = when (this) {
    is BrowserPage.LibrarySeries -> JSONObject()
        .put("type", "librarySeries")
        .put("key", key)
        .put("name", name)
        .put("episodeList", episodeList)
        .put("previewIndex", previewPosition.page)
        .put("previewOffset", previewPosition.offset)
        .put("episodeIndex", episodePosition.page)
        .put("episodeOffset", episodePosition.offset)
    is BrowserPage.LibraryHome -> JSONObject()
        .put("type", "libraryHome")
        .put("query", query)
        .put("key", key)
        .put("scrollAnchorKey", scrollAnchorKey)
        .put("scrollIndex", scrollIndex)
        .put("scrollOffset", scrollOffset)
    is BrowserPage.Search -> JSONObject()
        .put("type", "search")
        .put("key", key)
        .put("query", query)
        .put("preferredLanguages", JSONArray(preferredLanguages.toList()))
        .put("submittedQuery", submittedQuery)
        .put("submittedQueries", JSONArray(submittedQueries))
        .put("resultSessionId", resultSessionId)
        .put("scrollPage", scrollPage)
        .put("currentPage", currentPage)
        .put("scrollAnchorKey", scrollAnchorKey)
        .put("scrollIndex", scrollIndex)
        .put("scrollOffset", scrollOffset)
    is BrowserPage.OnlineGallery -> JSONObject()
        .put("type", "online")
        .put("previewIndex", previewPosition.page).put("previewOffset", previewPosition.offset)
        .put("key", key)
        .put("galleryId", galleryId)
        .put("title", title)
    is BrowserPage.LibraryBook -> JSONObject()
        .put("type", "library")
        .put("previewIndex", previewPosition.page).put("previewOffset", previewPosition.offset)
        .put("key", key)
        .put("bookId", bookId)
        .put("title", title)
    is BrowserPage.OnlineReader -> JSONObject()
        .put("type", "onlineReader")
        .put("key", key)
        .put("galleryId", galleryId)
        .put("title", title)
        .put("startPage", startPage)
        .put("readerPage", position?.page)
        .put("readerOffset", position?.offset)
    is BrowserPage.LibraryReader -> JSONObject()
        .put("type", "libraryReader")
        .put("key", key)
        .put("bookId", bookId)
        .put("title", title)
        .put("startPage", startPage)
        .put("readerPage", position?.page)
        .put("readerOffset", position?.offset)
}

private fun JSONObject.toBrowserPage(): BrowserPage? {
    val key = optString("key").takeIf(String::isNotBlank) ?: UUID.randomUUID().toString()
    return when (optString("type")) {
        "librarySeries" -> optString("name").takeIf(String::isNotBlank)?.let { name ->
            BrowserPage.LibrarySeries(name, key, optBoolean("episodeList"),
                ReaderPosition(optInt("previewIndex").coerceAtLeast(0), optInt("previewOffset").coerceAtLeast(0)),
                ReaderPosition(optInt("episodeIndex").coerceAtLeast(0), optInt("episodeOffset").coerceAtLeast(0)))
        }
        "libraryHome" -> BrowserPage.LibraryHome(
            key = key,
            query = optString("query"),
            scrollAnchorKey = optString("scrollAnchorKey").takeIf(String::isNotBlank),
            scrollIndex = optInt("scrollIndex", 0).coerceAtLeast(0),
            scrollOffset = optInt("scrollOffset", 0).coerceAtLeast(0),
        )
        "search" -> BrowserPage.Search(
            key = key,
            query = optString("query"),
            preferredLanguages = optJSONArray("preferredLanguages").stringSet(),
            submittedQuery = optString("submittedQuery"),
            submittedQueries = optJSONArray("submittedQueries").stringList(),
            resultSessionId = optString("resultSessionId").takeIf { it.isNotBlank() && it != "null" },
            scrollPage = optInt("scrollPage", 1).coerceAtLeast(1),
            currentPage = optInt("currentPage", 0).coerceAtLeast(0),
            scrollAnchorKey = optString("scrollAnchorKey").takeIf(String::isNotBlank),
            scrollIndex = optInt("scrollIndex", 0).coerceAtLeast(0),
            scrollOffset = optInt("scrollOffset", 0).coerceAtLeast(0),
        )
        "online" -> optLong("galleryId").takeIf { it > 0 }?.let { galleryId ->
            BrowserPage.OnlineGallery(galleryId, optString("title"), key, ReaderPosition(optInt("previewIndex").coerceAtLeast(0), optInt("previewOffset").coerceAtLeast(0)))
        }
        "library" -> optString("bookId").takeIf(String::isNotBlank)?.let { bookId ->
            BrowserPage.LibraryBook(bookId, optString("title"), key, ReaderPosition(optInt("previewIndex").coerceAtLeast(0), optInt("previewOffset").coerceAtLeast(0)))
        }
        "onlineReader" -> optLong("galleryId").takeIf { it > 0 }?.let { galleryId ->
            BrowserPage.OnlineReader(
                galleryId = galleryId,
                title = optString("title"),
                startPage = optInt("startPage", 0).coerceAtLeast(0),
                position = if (has("readerPage") && !isNull("readerPage")) ReaderPosition(optInt("readerPage").coerceAtLeast(0), optInt("readerOffset").coerceAtLeast(0)) else null,
                key = key,
            )
        }
        "libraryReader" -> optString("bookId").takeIf(String::isNotBlank)?.let { bookId ->
            BrowserPage.LibraryReader(
                bookId = bookId,
                title = optString("title"),
                startPage = optInt("startPage", 0).coerceAtLeast(0),
                position = if (has("readerPage") && !isNull("readerPage")) ReaderPosition(optInt("readerPage").coerceAtLeast(0), optInt("readerOffset").coerceAtLeast(0)) else null,
                key = key,
            )
        }
        else -> null
    }
}

internal fun BrowserWorkspace.searchWorkspace(): BrowserWorkspace = onlyPages(
    fallback = BrowserPage.Search(),
) { page ->
    page is BrowserPage.Search || page is BrowserPage.OnlineGallery || page is BrowserPage.OnlineReader
}

internal fun BrowserWorkspace.galleryWorkspace(): BrowserWorkspace = onlyPages(
    fallback = BrowserPage.LibraryHome(),
    prependLibraryHome = true,
) { page ->
    page is BrowserPage.LibraryHome || page is BrowserPage.LibrarySeries || page is BrowserPage.LibraryBook || page is BrowserPage.LibraryReader
}

private fun BrowserWorkspace.onlyPages(
    fallback: BrowserPage,
    prependLibraryHome: Boolean = false,
    keep: (BrowserPage) -> Boolean,
): BrowserWorkspace {
    fun filterTabs(source: List<BrowserTab>): List<BrowserTab> = source.mapNotNull { tab ->
        val pages = tab.history.filter(keep)
        if (pages.isEmpty()) return@mapNotNull null
        val keptThroughCurrent = tab.history.take(tab.currentIndex + 1).count(keep)
        val history = if (prependLibraryHome && tab.returnTo == null && pages.first() !is BrowserPage.LibraryHome) {
            listOf(BrowserPage.LibraryHome()) + pages
        } else {
            pages
        }
        val prefix = history.size - pages.size
        tab.copy(
            history = history,
            currentIndex = (keptThroughCurrent - 1 + prefix).coerceIn(history.indices),
        )
    }
    val filteredTabs = filterTabs(tabs).ifEmpty { BrowserWorkspace.initial(fallback).tabs }
    val filteredGroups = groups.mapNotNull { group ->
        if (!group.isArchived) group else {
            val archived = filterTabs(group.archivedTabs)
            if (archived.isEmpty()) null else group.copy(archivedTabs = archived)
        }
    }
    return copy(
        tabs = filteredTabs,
        activeTabId = activeTabId.takeIf { id -> filteredTabs.any { it.id == id } }
            ?: filteredTabs.first().id,
        groups = filteredGroups,
    ).normalizeGroups()
}

private fun JSONArray?.stringList(): List<String> = buildList {
    val array = this@stringList ?: return@buildList
    repeat(array.length()) { index ->
        array.optString(index).takeIf(String::isNotBlank)?.let(::add)
    }
}

private fun JSONArray?.stringSet(): Set<String> = stringList().toCollection(linkedSetOf())
