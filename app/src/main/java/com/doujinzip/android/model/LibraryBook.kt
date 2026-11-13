package com.doujinzip.android.model

data class LibraryBook(
    val id: String,
    val title: String,
    val originalTitle: String = title,
    val locationUri: String,
    val locationName: String,
    val folderUri: String,
    val pages: List<LibraryPage>,
    val modifiedAt: Long,
    val metadata: LibraryMetadata = LibraryMetadata(),
    val isCloud: Boolean = false,
    val syncId: String? = null,
    val syncStateVersion: Long = 0L,
    val syncedStateModifiedAt: Long = 0L,
    val syncedFavorite: Boolean = false,
    val syncedRead: Boolean = false,
    val syncedHidden: Boolean = false,
    val syncedProgress: Int = 0,
    val syncedCustomTitle: String? = null,
    val syncedSeriesFavorite: Boolean = false,
    val syncedSeries: CustomSeriesAssignment? = null,
    val syncedSeriesModifiedAt: Long = 0L,
    val hasSyncedSeriesState: Boolean = false,
    val remoteBookId: Long? = null,
    val contentVersion: String? = null,
    val contentRevision: Long = 0L,
    val coverUriOverride: String? = null,
    val cloudToken: String? = null,
    val webtoon: Boolean? = null,
) {
    val coverUri: String get() = coverUriOverride ?: pages.firstOrNull()?.uri.orEmpty()
}

data class LibraryPage(
    val uri: String,
    val name: String,
    val archiveUri: String? = null,
    val archiveEntry: String? = null,
)

data class LibraryMetadata(
    val hitomiId: String? = null,
    val artists: List<String> = emptyList(),
    val groups: List<String> = emptyList(),
    val galleryType: String? = null,
    val series: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val language: String? = null,
    val publishedDate: String? = null,
)

data class CustomSeriesAssignment(
    val name: String,
    val order: Int,
    val modifiedAt: Long = 0L,
)

enum class LibraryReadFilter { ALL, UNREAD, READ }

enum class LibraryVisibilityFilter { VISIBLE, HIDDEN, ALL }

enum class LibrarySort { TITLE_ASC, TITLE_DESC, ARTIST_ASC, ARTIST_DESC, NEWEST, OLDEST, RANDOM }

enum class ViewerScale { FIT_SCREEN, FIT_WIDTH }

enum class ViewerReadingDirection { LEFT_TO_RIGHT, RIGHT_TO_LEFT }

enum class ViewerPageTurnMode { SWIPE_AND_TAP, SWIPE_ONLY, TAP_ONLY }

enum class ViewerTapAction { PREVIOUS_PAGE, NEXT_PAGE, TOGGLE_CONTROLS, NONE }

data class ViewerTapZones(
    val actions: List<ViewerTapAction> = DEFAULT_ACTIONS,
) {
    fun actionAt(index: Int): ViewerTapAction = actions.getOrNull(index) ?: DEFAULT_ACTIONS[index]

    fun withAction(index: Int, action: ViewerTapAction): ViewerTapZones {
        if (index !in 0..8) return this
        val updated = DEFAULT_ACTIONS.mapIndexed { cell, default -> actions.getOrNull(cell) ?: default }.toMutableList()
        updated[index] = action
        return copy(actions = updated)
    }

    fun swapPreviousAndNext(): ViewerTapZones = copy(
        actions = DEFAULT_ACTIONS.mapIndexed { index, default ->
            when (actions.getOrNull(index) ?: default) {
                ViewerTapAction.PREVIOUS_PAGE -> ViewerTapAction.NEXT_PAGE
                ViewerTapAction.NEXT_PAGE -> ViewerTapAction.PREVIOUS_PAGE
                ViewerTapAction.TOGGLE_CONTROLS -> ViewerTapAction.TOGGLE_CONTROLS
                ViewerTapAction.NONE -> ViewerTapAction.NONE
            }
        },
    )

    companion object {
        val DEFAULT_ACTIONS = listOf(
            ViewerTapAction.PREVIOUS_PAGE, ViewerTapAction.NONE, ViewerTapAction.NEXT_PAGE,
            ViewerTapAction.PREVIOUS_PAGE, ViewerTapAction.NONE, ViewerTapAction.NEXT_PAGE,
            ViewerTapAction.PREVIOUS_PAGE, ViewerTapAction.NONE, ViewerTapAction.NEXT_PAGE,
        )
    }
}

data class ViewerPreferences(
    val readingMode: ViewerReadingMode = ViewerReadingMode.AUTO,
    val scale: ViewerScale = ViewerScale.FIT_SCREEN,
    val showPageNumber: Boolean = true,
    val hideThumbnails: Boolean = false,
    val keepScreenOn: Boolean = true,
    val readingDirection: ViewerReadingDirection = ViewerReadingDirection.LEFT_TO_RIGHT,
    val pageTurnMode: ViewerPageTurnMode = ViewerPageTurnMode.SWIPE_AND_TAP,
    val customTapZonesEnabled: Boolean = false,
    val tapZones: ViewerTapZones = ViewerTapZones(),
)
