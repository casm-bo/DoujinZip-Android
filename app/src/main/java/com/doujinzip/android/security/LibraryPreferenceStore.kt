package com.doujinzip.android.security

import android.content.Context
import com.doujinzip.android.model.CustomSeriesAssignment
import com.doujinzip.android.model.LibrarySort
import com.doujinzip.android.model.ViewerReadingMode
import com.doujinzip.android.model.ViewerPreferences
import com.doujinzip.android.model.ViewerPageTurnMode
import com.doujinzip.android.model.ViewerReadingDirection
import com.doujinzip.android.model.ViewerScale
import com.doujinzip.android.model.ViewerTapAction
import com.doujinzip.android.model.ViewerTapZones
import org.json.JSONObject

class LibraryPreferenceStore(context: Context, fileName: String = FILE_NAME) {
    private val preferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    fun archiveOfflineEdits(profileId: String): Boolean {
        val marker = "pc_authority_v1:$profileId"
        if (preferences.getBoolean(marker, false)) return false
        val pending = loadPendingBookStateSyncIds().isNotEmpty() || loadPendingSeriesSyncIds().isNotEmpty()
        val backup = JSONObject()
        preferences.all.forEach { (key, value) ->
            if (!key.startsWith("pc_authority") && !key.startsWith("legacy_offline")) backup.put(key, if (value is Set<*>) org.json.JSONArray(value.toList()) else value)
        }
        check(preferences.edit().putString("legacy_offline:$profileId", backup.toString()).putBoolean(marker, true)
            .putStringSet(KEY_PENDING_BOOK_STATE_SYNC, emptySet()).putStringSet(KEY_PENDING_SERIES_SYNC, emptySet()).commit())
        return pending
    }
    internal fun snapshot(): Map<String, *> = preferences.all
    internal fun matchesSnapshot(snapshot: Map<String, *>): Boolean = preferences.all == snapshot

    fun loadFavoriteIds(): Set<String> = preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()
    fun saveFavoriteIds(ids: Set<String>) = preferences.edit().putStringSet(KEY_FAVORITES, ids).apply()

    fun loadFavoriteSeriesNames(): Set<String> =
        preferences.getStringSet(KEY_SERIES_FAVORITES, emptySet()).orEmpty()

    fun saveFavoriteSeriesNames(names: Set<String>) =
        preferences.edit().putStringSet(KEY_SERIES_FAVORITES, names).apply()

    fun loadReadIds(): Set<String> = preferences.getStringSet(KEY_READ, emptySet()).orEmpty()
    fun saveReadIds(ids: Set<String>) = preferences.edit().putStringSet(KEY_READ, ids).apply()

    fun loadProgress(): Map<String, Int> = readProgress(KEY_PROGRESS, "book_page:")

    private fun readProgress(key: String, prefix: String): Map<String, Int> {
        val snapshot = preferences.all
        val json = runCatching { JSONObject(snapshot[key] as? String ?: "{}") }.getOrElse { JSONObject() }
        return buildMap {
            json.keys().forEach { key -> put(key, json.optInt(key, 0)) }
            snapshot.forEach { (key, value) -> if (key.startsWith(prefix) && value is Int) put(key.removePrefix(prefix), value) }
        }
    }

    fun saveProgress(progress: Map<String, Int>) = replaceProgress(KEY_PROGRESS, "book_page:", progress)

    fun saveBookProgress(bookId: String, page: Int) { preferences.edit().putInt("book_page:$bookId", page).apply() }

    private fun replaceProgress(key: String, prefix: String, progress: Map<String, Int>) {
        val json = JSONObject()
        progress.forEach { (id, page) -> json.put(id, page) }
        val editor = preferences.edit().putString(key, json.toString())
        preferences.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.apply()
    }

    fun loadCustomSeries(): Map<String, CustomSeriesAssignment> {
        val json = runCatching { JSONObject(preferences.getString(KEY_CUSTOM_SERIES, "{}").orEmpty()) }
            .getOrNull() ?: return emptyMap()
        val legacyOrders = mutableMapOf<String, Int>()
        return buildMap<String, CustomSeriesAssignment> {
            json.keys().forEach { id ->
                val value = json.opt(id)
                if (value is JSONObject) {
                    val name = value.takeUnless { it.isNull("name") }
                        ?.optString("name")
                        ?.trim()
                        .orEmpty()
                    if (name.isNotEmpty() && !name.equals("null", ignoreCase = true)) put(
                        id,
                        CustomSeriesAssignment(
                            name = name,
                            order = value.optInt("order", 0),
                            modifiedAt = value.optLong("modifiedAt", 0L),
                        ),
                    )
                } else {
                    val name = value?.toString().orEmpty().trim()
                    if (name.isNotEmpty() && !name.equals("null", ignoreCase = true)) {
                        val order = legacyOrders.getOrDefault(name, 0)
                        put(id, CustomSeriesAssignment(name, order))
                        legacyOrders[name] = order + 1
                    }
                }
            }
        }
    }

    fun saveCustomSeries(series: Map<String, CustomSeriesAssignment>) {
        val json = JSONObject()
        series.forEach { (id, assignment) ->
            json.put(
                id,
                JSONObject()
                    .put("name", assignment.name)
                    .put("order", assignment.order)
                    .put("modifiedAt", assignment.modifiedAt),
            )
        }
        preferences.edit().putString(KEY_CUSTOM_SERIES, json.toString()).apply()
    }

    fun loadSeriesRemovalTimes(): Map<String, Long> {
        val json = runCatching {
            JSONObject(preferences.getString(KEY_SERIES_REMOVAL_TIMES, "{}").orEmpty())
        }.getOrNull() ?: return emptyMap()
        return buildMap {
            json.keys().forEach { id -> put(id, json.optLong(id, 0L)) }
        }
    }

    fun saveSeriesRemovalTimes(times: Map<String, Long>) {
        val json = JSONObject()
        times.forEach { (id, modifiedAt) -> json.put(id, modifiedAt) }
        preferences.edit().putString(KEY_SERIES_REMOVAL_TIMES, json.toString()).apply()
    }

    fun loadPendingSeriesSyncIds(): Set<String> =
        preferences.getStringSet(KEY_PENDING_SERIES_SYNC, emptySet()).orEmpty().toSet()

    fun savePendingSeriesSyncIds(ids: Set<String>) =
        preferences.edit().putStringSet(KEY_PENDING_SERIES_SYNC, ids).apply()

    fun loadPendingBookStateSyncIds(): Set<String> =
        preferences.getStringSet(KEY_PENDING_BOOK_STATE_SYNC, emptySet()).orEmpty().toSet()

    fun savePendingBookStateSyncIds(ids: Set<String>) =
        preferences.edit().putStringSet(KEY_PENDING_BOOK_STATE_SYNC, ids).apply()

    fun loadBookStateModifiedTimes(): Map<String, Long> {
        val json = runCatching {
            JSONObject(preferences.getString(KEY_BOOK_STATE_MODIFIED_TIMES, "{}").orEmpty())
        }.getOrNull() ?: return emptyMap()
        return buildMap {
            json.keys().forEach { id -> put(id, json.optLong(id, 0L)) }
        }
    }

    fun saveBookStateModifiedTimes(times: Map<String, Long>) {
        val json = JSONObject()
        times.forEach { (id, modifiedAt) -> json.put(id, modifiedAt) }
        preferences.edit().putString(KEY_BOOK_STATE_MODIFIED_TIMES, json.toString()).apply()
    }

    fun loadBookStateSyncCursor(profileId: String): Long {
        val json = runCatching {
            JSONObject(preferences.getString(KEY_BOOK_STATE_SYNC_CURSORS, "{}").orEmpty())
        }.getOrNull() ?: return 0L
        return json.optLong(profileId, 0L).coerceAtLeast(0L)
    }

    fun saveBookStateSyncCursor(profileId: String, cursor: Long) {
        val json = runCatching {
            JSONObject(preferences.getString(KEY_BOOK_STATE_SYNC_CURSORS, "{}").orEmpty())
        }.getOrElse { JSONObject() }
        json.put(profileId, cursor.coerceAtLeast(0L))
        preferences.edit().putString(KEY_BOOK_STATE_SYNC_CURSORS, json.toString()).apply()
    }

    fun loadHiddenIds(): Set<String> =
        preferences.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()

    fun saveHiddenIds(ids: Set<String>) =
        preferences.edit().putStringSet(KEY_HIDDEN, ids).apply()

    fun loadCustomTitles(): Map<String, String> {
        val json = runCatching { JSONObject(preferences.getString(KEY_CUSTOM_TITLES, "{}").orEmpty()) }
            .getOrNull() ?: return emptyMap()
        return buildMap {
            json.keys().forEach { id ->
                json.optString(id).trim().takeIf(String::isNotEmpty)?.let { put(id, it) }
            }
        }
    }

    fun saveCustomTitles(titles: Map<String, String>) {
        val json = JSONObject()
        titles.forEach { (id, title) -> json.put(id, title) }
        preferences.edit().putString(KEY_CUSTOM_TITLES, json.toString()).apply()
    }

    fun loadReaderMode(workKey: String): ViewerReadingMode? = runCatching { ViewerReadingMode.valueOf(preferences.getString("reading_mode:$workKey", null).orEmpty()) }.getOrNull()
    fun saveReaderMode(workKey: String, mode: ViewerReadingMode?) {
        val editor = preferences.edit()
        if (mode == null) editor.remove("reading_mode:$workKey") else editor.putString("reading_mode:$workKey", mode.name)
        editor.apply()
    }
    fun loadViewerPreferences() = ViewerPreferences(
        readingMode = enumPreference("reading_mode", ViewerReadingMode.AUTO),
        scale = runCatching {
            ViewerScale.valueOf(preferences.getString(KEY_SCALE, null).orEmpty())
        }.getOrDefault(ViewerScale.FIT_SCREEN),
        showPageNumber = preferences.getBoolean(KEY_PAGE_NUMBER, true),
        hideThumbnails = preferences.getBoolean(KEY_HIDE_THUMBNAILS, false),
        keepScreenOn = preferences.getBoolean(KEY_KEEP_SCREEN_ON, true),
        readingDirection = enumPreference(KEY_READING_DIRECTION, ViewerReadingDirection.LEFT_TO_RIGHT),
        pageTurnMode = enumPreference(KEY_PAGE_TURN_MODE, ViewerPageTurnMode.SWIPE_AND_TAP),
        customTapZonesEnabled = preferences.getBoolean(KEY_CUSTOM_TAP_ZONES_ENABLED, false),
        tapZones = ViewerTapZones(
            preferences.getString(KEY_TAP_ZONES, null)
                ?.split(',')
                ?.mapNotNull { value -> runCatching { ViewerTapAction.valueOf(value) }.getOrNull() }
                ?.takeIf { it.size == 9 }
                ?: ViewerTapZones.DEFAULT_ACTIONS,
        ),
    )

    fun saveViewerPreferences(value: ViewerPreferences) {
        preferences.edit()
            .putString("reading_mode", value.readingMode.name)
            .putString(KEY_SCALE, value.scale.name)
            .putBoolean(KEY_PAGE_NUMBER, value.showPageNumber)
            .putBoolean(KEY_HIDE_THUMBNAILS, value.hideThumbnails)
            .putBoolean(KEY_KEEP_SCREEN_ON, value.keepScreenOn)
            .putString(KEY_READING_DIRECTION, value.readingDirection.name)
            .putString(KEY_PAGE_TURN_MODE, value.pageTurnMode.name)
            .putBoolean(KEY_CUSTOM_TAP_ZONES_ENABLED, value.customTapZonesEnabled)
            .putString(KEY_TAP_ZONES, value.tapZones.actions.joinToString(",") { it.name })
            .apply()
    }

    fun loadOnlineProgress(): Map<Long, Int> = readProgress(KEY_ONLINE_PROGRESS, "online_page:")
        .mapNotNull { (id, page) -> id.toLongOrNull()?.let { it to page } }.toMap()

    fun saveOnlineProgress(progress: Map<Long, Int>) {
        replaceProgress(KEY_ONLINE_PROGRESS, "online_page:", progress.mapKeys { it.key.toString() })
    }

    fun saveOnlinePage(galleryId: Long, page: Int) { preferences.edit().putInt("online_page:$galleryId", page).apply() }

    fun loadActiveBookId(): String? =
        preferences.getString(KEY_ACTIVE_BOOK_ID, null)?.takeIf(String::isNotBlank)

    fun saveActiveBookId(bookId: String?) {
        preferences.edit().apply {
            if (bookId == null) remove(KEY_ACTIVE_BOOK_ID) else putString(KEY_ACTIVE_BOOK_ID, bookId)
        }.apply()
    }

    private inline fun <reified T : Enum<T>> enumPreference(key: String, fallback: T): T =
        runCatching { enumValueOf<T>(preferences.getString(key, null).orEmpty()) }.getOrDefault(fallback)

    fun loadGroupSeries(): Boolean = preferences.getBoolean("library_group_series", true)
    fun saveGroupSeries(value: Boolean) = preferences.edit().putBoolean("library_group_series", value).apply()

    fun loadLibraryViewMode(): String = preferences.getString(KEY_LIBRARY_VIEW_MODE, "GRID") ?: "GRID"

    fun saveLibraryViewMode(value: String) =
        preferences.edit().putString(KEY_LIBRARY_VIEW_MODE, value).apply()

    fun loadLibraryGridColumns(): Int = preferences.getInt(KEY_LIBRARY_GRID_COLUMNS, 2).coerceIn(2, 4)

    fun loadSearchGridView(): Boolean = preferences.getBoolean("search_grid_view", false)
    fun saveSearchGridView(value: Boolean) = preferences.edit().putBoolean("search_grid_view", value).apply()
    fun loadSearchGridColumns(): Int = preferences.getInt("search_grid_columns", 3).coerceIn(2, 4)
    fun saveSearchGridColumns(value: Int) = preferences.edit().putInt("search_grid_columns", value.coerceIn(2, 4)).apply()

    fun saveLibraryGridColumns(value: Int) =
        preferences.edit().putInt(KEY_LIBRARY_GRID_COLUMNS, value.coerceIn(2, 4)).apply()

    fun loadLibrarySort(): LibrarySort = enumPreference(KEY_LIBRARY_SORT, LibrarySort.NEWEST)
    fun loadSeriesDestinationSort(): LibrarySort = enumPreference("series_destination_sort", LibrarySort.NEWEST)
    fun saveSeriesDestinationSort(value: LibrarySort) = preferences.edit().putString("series_destination_sort", value.name).apply()
    fun loadSeriesEpisodeSort(): LibrarySort = enumPreference("series_episode_sort", LibrarySort.NEWEST)
    fun saveSeriesEpisodeSort(value: LibrarySort) = preferences.edit().putString("series_episode_sort", value.name).apply()
    fun loadFavoritesOnly(): Boolean = preferences.getBoolean("library_favorites_only", false)
    fun saveFavoritesOnly(value: Boolean) { preferences.edit().putBoolean("library_favorites_only", value).apply() }
    fun loadReadFilter(): com.doujinzip.android.model.LibraryReadFilter = enumPreference("library_read_filter", com.doujinzip.android.model.LibraryReadFilter.ALL)
    fun saveReadFilter(value: com.doujinzip.android.model.LibraryReadFilter) { preferences.edit().putString("library_read_filter", value.name).apply() }
    fun loadVisibilityFilter(): com.doujinzip.android.model.LibraryVisibilityFilter = enumPreference("library_visibility_filter", com.doujinzip.android.model.LibraryVisibilityFilter.VISIBLE)
    fun saveVisibilityFilter(value: com.doujinzip.android.model.LibraryVisibilityFilter) { preferences.edit().putString("library_visibility_filter", value.name).apply() }

    fun saveLibrarySort(value: LibrarySort) =
        preferences.edit().putString(KEY_LIBRARY_SORT, value.name).apply()

    private companion object {
        const val FILE_NAME = "library_preferences"
        const val KEY_FAVORITES = "favorite_book_ids"
        const val KEY_SERIES_FAVORITES = "favorite_series_names"
        const val KEY_READ = "read_book_ids"
        const val KEY_PROGRESS = "book_progress"
        const val KEY_ONLINE_PROGRESS = "online_gallery_progress"
        const val KEY_ACTIVE_BOOK_ID = "active_library_book_id"
        const val KEY_CUSTOM_SERIES = "custom_book_series"
        const val KEY_SERIES_REMOVAL_TIMES = "series_removal_times"
        const val KEY_PENDING_SERIES_SYNC = "pending_series_sync_ids"
        const val KEY_PENDING_BOOK_STATE_SYNC = "pending_book_state_sync_ids"
        const val KEY_BOOK_STATE_MODIFIED_TIMES = "book_state_modified_times"
        const val KEY_BOOK_STATE_SYNC_CURSORS = "book_state_sync_cursors"
        const val KEY_HIDDEN = "hidden_book_ids"
        const val KEY_CUSTOM_TITLES = "custom_book_titles"
        const val KEY_SCALE = "viewer_scale"
        const val KEY_PAGE_NUMBER = "viewer_page_number"
        const val KEY_HIDE_THUMBNAILS = "viewer_hide_thumbnails"
        const val KEY_KEEP_SCREEN_ON = "viewer_keep_screen_on"
        const val KEY_READING_DIRECTION = "viewer_reading_direction"
        const val KEY_PAGE_TURN_MODE = "viewer_page_turn_mode"
        const val KEY_CUSTOM_TAP_ZONES_ENABLED = "viewer_custom_tap_zones_enabled"
        const val KEY_TAP_ZONES = "viewer_tap_zones"
        const val KEY_LIBRARY_VIEW_MODE = "library_view_mode"
        const val KEY_LIBRARY_GRID_COLUMNS = "library_grid_columns"
        const val KEY_LIBRARY_SORT = "library_sort"
    }
}
