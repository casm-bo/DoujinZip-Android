package com.doujinzip.android.ui

import com.doujinzip.android.R

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.doujinzip.android.model.LibraryBook
import com.doujinzip.android.model.GalleryPresentation
import com.doujinzip.android.model.ReaderImageSource
import com.doujinzip.android.model.seriesPresentationKey
import com.doujinzip.android.model.seriesCoverPresentationKey
import com.doujinzip.android.model.ReaderPosition
import com.doujinzip.android.model.resolveScrollIndex
import com.doujinzip.android.model.CustomSeriesAssignment
import com.doujinzip.android.model.LibraryReadFilter
import com.doujinzip.android.model.LibrarySort
import com.doujinzip.android.model.LibraryVisibilityFilter
import kotlinx.coroutines.flow.distinctUntilChanged
import com.doujinzip.android.security.LibraryPreferenceStore

private enum class LibraryViewMode { GRID, LIST }

private fun thumbnailBadgeScale(columns: Int): Float = when (columns) {
    2 -> 1f
    3 -> 0.72f
    else -> 0.56f
}

private const val LIST_THUMBNAIL_BADGE_SCALE = 0.62f

internal sealed interface LibraryMainEntry {
    val title: String
    val modifiedAt: Long
    data class Book(val book: LibraryBook) : LibraryMainEntry {
        override val title: String = book.title
        override val modifiedAt: Long = book.modifiedAt
    }
    data class Series(val name: String, val books: List<LibraryBook>) : LibraryMainEntry {
        override val title: String = name
        override val modifiedAt: Long = books.maxOfOrNull(LibraryBook::modifiedAt) ?: 0L
    }
}

internal fun libraryMainEntries(
    books: List<LibraryBook>, assignments: Map<String, CustomSeriesAssignment>, sort: LibrarySort, groupSeries: Boolean = true,
): List<LibraryMainEntry> {
    if (!groupSeries) return sortLibraryMainEntries(books.map { LibraryMainEntry.Book(it) }, sort)
    val grouped = books.filter { it.id in assignments }.groupBy { assignments.getValue(it.id).name }
    val entries = books.filter { it.id !in assignments }.map { LibraryMainEntry.Book(it) } +
        grouped.map { (name, members) -> LibraryMainEntry.Series(name, members.sortedWith(
            compareBy<LibraryBook> { assignments.getValue(it.id).order }.thenBy { it.id },
        )) }
    return sortLibraryMainEntries(entries, sort)
}

internal fun sortLibraryMainEntries(entries: List<LibraryMainEntry>, sort: LibrarySort): List<LibraryMainEntry> =
    when (sort) {
        LibrarySort.TITLE_ASC -> entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        LibrarySort.TITLE_DESC -> entries.sortedWith(compareByDescending<LibraryMainEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.title })
        LibrarySort.ARTIST_ASC -> entries.sortedWith(libraryMainEntryArtistComparator(false))
        LibrarySort.ARTIST_DESC -> entries.sortedWith(libraryMainEntryArtistComparator(true))
        LibrarySort.NEWEST -> entries.sortedByDescending { it.modifiedAt }
        LibrarySort.OLDEST -> entries.sortedBy { it.modifiedAt }
    }

private fun LibraryMainEntry.scrollKey(): String = when (this) {
    is LibraryMainEntry.Book -> "book:${book.id}"
    is LibraryMainEntry.Series -> "series:$name"
}

private fun LibraryBook.primaryArtist(): String? =
    metadata.artists.firstOrNull { it.isNotBlank() }?.trim()

private fun compareArtists(left: String?, right: String?, descending: Boolean): Int {
    if (left == null) return if (right == null) 0 else 1
    if (right == null) return -1
    val result = String.CASE_INSENSITIVE_ORDER.compare(left, right)
    return if (descending) -result else result
}

private fun libraryBookArtistComparator(descending: Boolean): Comparator<LibraryBook> =
    Comparator { left, right ->
        compareArtists(left.primaryArtist(), right.primaryArtist(), descending)
            .takeIf { it != 0 }
            ?: String.CASE_INSENSITIVE_ORDER.compare(left.title, right.title)
    }

private fun LibraryMainEntry.primaryArtist(): String? = when (this) {
    is LibraryMainEntry.Book -> book.primaryArtist()
    is LibraryMainEntry.Series -> books.mapNotNull(LibraryBook::primaryArtist)
        .minWithOrNull(String.CASE_INSENSITIVE_ORDER)
}

private fun libraryMainEntryArtistComparator(descending: Boolean): Comparator<LibraryMainEntry> =
    Comparator { left, right ->
        compareArtists(left.primaryArtist(), right.primaryArtist(), descending)
            .takeIf { it != 0 }
            ?: String.CASE_INSENSITIVE_ORDER.compare(left.title, right.title)
    }

private fun seriesGroupArtistComparator(
    descending: Boolean,
): Comparator<Pair<String, List<LibraryBook>>> = Comparator { left, right ->
    val leftArtist = left.second.mapNotNull(LibraryBook::primaryArtist)
        .minWithOrNull(String.CASE_INSENSITIVE_ORDER)
    val rightArtist = right.second.mapNotNull(LibraryBook::primaryArtist)
        .minWithOrNull(String.CASE_INSENSITIVE_ORDER)
    compareArtists(leftArtist, rightArtist, descending)
        .takeIf { it != 0 }
        ?: String.CASE_INSENSITIVE_ORDER.compare(left.first, right.first)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: MainUiState,
    tabHeader: @Composable (@Composable () -> Unit, @Composable () -> Unit) -> Unit,
    onSync: () -> Unit,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onToggleFavoritesFilter: () -> Unit,
    onExcludeSeriesChange: (Boolean) -> Unit,
    onReadFilterChange: (LibraryReadFilter) -> Unit,
    onVisibilityFilterChange: (LibraryVisibilityFilter) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onLocationChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenBook: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleSeriesFavorite: (String) -> Unit,
    onToggleRead: (String) -> Unit,
    onAddFavorites: (Set<String>) -> Unit,
    onMarkRead: (Set<String>) -> Unit,
    onMarkUnread: (Set<String>) -> Unit,
    onAssignSeries: (Set<String>, String) -> Unit,
    onAppendSeries: (Set<String>, String) -> Boolean,
    onSetBooksHidden: (Set<String>, Boolean) -> Unit,
    onDeleteBooks: (Set<String>) -> Unit,
    onRenameBook: (String, String) -> Unit,
    onRemoveBooksFromSeries: (Set<String>) -> Unit,
    onRenameSeries: (String, String) -> Unit,
    onMergeSeries: (List<String>, String) -> Unit,
    onDeleteSeries: (Set<String>) -> Unit,
    onAutoCreateSeries: () -> Unit,
    onSeriesModeChange: (Boolean) -> Unit,
    onSelectedSeriesChange: (String?) -> Unit,
    onBackToSearch: () -> Unit,
    pageKey: String,
    initialScrollAnchorKey: String?,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    onScrollChange: (String?, Int, Int) -> Unit,
    onSubmitSearch: () -> Unit = {},
    onRecentSearch: (String) -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    onCommitSeriesEdit: (String, Map<String, CustomSeriesAssignment>, List<String>) -> Boolean = { _, _, _ -> false },
    onReadBook: (String, Int) -> Unit = { id, _ -> onOpenBook(id) },
    onSearchFacet: (String) -> Unit = {},
    onSearchLanguage: (String) -> Unit = {},
    onSeriesListChange: (Boolean) -> Unit = {},
    onLoadSeriesPreview: (String) -> Unit = {},
    onSelectSuggestion: (com.doujinzip.android.network.FilterSuggestion) -> Unit = {},
    seriesPage: com.doujinzip.android.model.BrowserPage.LibrarySeries? = null,
    onSeriesPreviewPosition: (com.doujinzip.android.model.ReaderPosition) -> Unit = {},
    onSeriesEpisodePosition: (Int, Int) -> Unit = { _, _ -> },
    onSaveSeriesPresentation: (GalleryPresentation, Boolean) -> Unit = { _, _ -> },
    onSeriesCover: (GalleryPresentation, ReaderImageSource) -> Unit = { _, _ -> },
    onAcceptRemoteSeriesPresentation: (String) -> Unit = {},
    onResetSeriesCover: (GalleryPresentation) -> Unit = {},
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val libraryPreferences = remember(context) { LibraryPreferenceStore(context) }
    var viewModeName by rememberSaveable {
        mutableStateOf(libraryPreferences.loadLibraryViewMode())
    }
    var gridColumns by rememberSaveable {
        mutableIntStateOf(libraryPreferences.loadLibraryGridColumns())
    }
    var groupSeries by rememberSaveable { mutableStateOf(libraryPreferences.loadGroupSeries()) }
    val seriesMode = state.librarySeriesMode
    val selectedSeries = seriesPage?.name
    var selectedIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedSeriesNames by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var seriesSelectionMode by rememberSaveable { mutableStateOf(false) }
    var firstSelectedSeriesName by rememberSaveable { mutableStateOf<String?>(null) }
    var existingSeriesDialog by rememberSaveable { mutableStateOf(false) }
    var seriesDialog by rememberSaveable { mutableStateOf(false) }
    var seriesName by rememberSaveable { mutableStateOf("") }
    var editingSeriesName by rememberSaveable { mutableStateOf<String?>(null) }
    val showSeriesList = seriesPage?.episodeList == true
    var pendingSeriesSelection by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    var renameBookDialog by rememberSaveable { mutableStateOf(false) }
    var renameSeriesDialog by rememberSaveable { mutableStateOf(false) }
    var mergeSeriesDialog by rememberSaveable { mutableStateOf(false) }
    var renameInput by rememberSaveable { mutableStateOf("") }
    val viewMode = runCatching { LibraryViewMode.valueOf(viewModeName) }
        .getOrDefault(LibraryViewMode.GRID)
    val books = remember(
        state.libraryBooks,
        state.libraryQuery,
        state.libraryFavoritesOnly,
        state.libraryReadFilter,
        state.libraryVisibilityFilter,
        state.libraryHiddenIds,
        state.librarySort,
        state.selectedLibraryLocationUris,
        state.libraryFavoriteIds,
        state.libraryFavoriteSeriesNames,
        state.libraryReadIds,
        state.libraryExcludeSeries,
        state.customSeriesByBookId,
    ) { visibleLibraryBooks(state) }
    val visibilityBooks = remember(
        state.libraryBooks,
        state.libraryHiddenIds,
        state.libraryVisibilityFilter,
    ) { state.libraryBooksInVisibility() }
    val shownBooks = remember(books, visibilityBooks, selectedSeries, state.customSeriesByBookId) {
        (if (selectedSeries == null) books else visibilityBooks).filterBySeries(selectedSeries, state.customSeriesByBookId)
    }
    val mainEntries = remember(books, state.customSeriesByBookId, state.librarySort, groupSeries) {
        libraryMainEntries(books, state.customSeriesByBookId, state.librarySort, groupSeries)
    }
    val shownEntries = remember(shownBooks) { shownBooks.map { LibraryMainEntry.Book(it) } }
    val pullToRefreshState = rememberPullToRefreshState()
    val mainEntryKeys = remember(mainEntries) { mainEntries.map(LibraryMainEntry::scrollKey) }
    val savedAnchorKey = remember(pageKey) { initialScrollAnchorKey }
    val restoredIndex = resolveScrollIndex(savedAnchorKey, initialScrollIndex, mainEntryKeys)
    val mainGridState = remember(pageKey) { LazyGridState(restoredIndex, initialScrollOffset) }
    val mainListState = remember(pageKey) { LazyListState(restoredIndex, initialScrollOffset) }
    val seriesBooksGridState = remember(pageKey) { LazyGridState(seriesPage?.episodePosition?.page ?: 0, seriesPage?.episodePosition?.offset ?: 0) }
    val seriesBooksListState = remember(pageKey) { LazyListState(seriesPage?.episodePosition?.page ?: 0, seriesPage?.episodePosition?.offset ?: 0) }
    val seriesOverviewGridState = remember(pageKey) { LazyGridState(initialScrollIndex, initialScrollOffset) }
    val seriesOverviewListState = remember(pageKey) { LazyListState(initialScrollIndex, initialScrollOffset) }
    val filterSignature = libraryFilterSignature(state)
    var previousFilterSignature by rememberSaveable { mutableStateOf(filterSignature) }
    var scrollRestored by remember(pageKey) {
        mutableStateOf(savedAnchorKey == null || savedAnchorKey in mainEntryKeys)
    }
    val toggleBookSelection: (String) -> Unit = { id -> selectedIds = selectedIds.toggled(id) }
    val toggleSeriesSelection: (String, Set<String>) -> Unit = { name, ids ->
        selectedIds = if (ids.all { it in selectedIds }) selectedIds - ids
        else selectedIds.also { pendingSeriesSelection = name }
    }
    val longPressBook: (String) -> Unit = { id ->
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        if (!selectionMode) {
            seriesName = state.libraryBooks.firstOrNull { it.id == id }?.title.orEmpty()
        }
        selectionMode = true
        selectedIds = selectedIds + id
    }
    val longPressSeries: (String, String) -> Unit = { name, firstTitle ->
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        if (!selectionMode) seriesName = firstTitle
        selectionMode = true
        pendingSeriesSelection = name
    }

    LaunchedEffect(filterSignature) {
        if (filterSignature != previousFilterSignature) {
            mainGridState.requestScrollToItem(0)
            mainListState.requestScrollToItem(0)
            seriesBooksGridState.requestScrollToItem(0)
            seriesBooksListState.requestScrollToItem(0)
            seriesOverviewGridState.requestScrollToItem(0)
            seriesOverviewListState.requestScrollToItem(0)
            scrollRestored = true
        }
        previousFilterSignature = filterSignature
    }

    SideEffect {
        if (!scrollRestored && mainEntryKeys.isNotEmpty()) {
            val target = resolveScrollIndex(savedAnchorKey, initialScrollIndex, mainEntryKeys)
            // Inactive views have no first layout: enqueue the position without waiting for one.
            mainGridState.requestScrollToItem(target, initialScrollOffset)
            mainListState.requestScrollToItem(target, initialScrollOffset)
            scrollRestored = true
        }
    }

    fun persistScroll() {
        if (selectedSeries != null || !scrollRestored || mainEntryKeys.isEmpty()) return
        if (seriesMode) {
            onScrollChange(null,
                if (viewMode == LibraryViewMode.GRID) seriesOverviewGridState.firstVisibleItemIndex else seriesOverviewListState.firstVisibleItemIndex,
                if (viewMode == LibraryViewMode.GRID) seriesOverviewGridState.firstVisibleItemScrollOffset else seriesOverviewListState.firstVisibleItemScrollOffset)
            return
        }
        val index = if (viewMode == LibraryViewMode.GRID) mainGridState.firstVisibleItemIndex else mainListState.firstVisibleItemIndex
        val offset = if (viewMode == LibraryViewMode.GRID) mainGridState.firstVisibleItemScrollOffset else mainListState.firstVisibleItemScrollOffset
        val anchor = if (viewMode == LibraryViewMode.GRID) mainGridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.key
            else mainListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.key
        if (anchor != null) onScrollChange(anchor.toString(), index, offset)
    }
    LaunchedEffect(pageKey, viewMode, seriesMode, selectedSeries, mainEntryKeys, scrollRestored) {
        if (selectedSeries != null || !scrollRestored) return@LaunchedEffect
        snapshotFlow {
            if (viewMode == LibraryViewMode.GRID) {
                val grid = if (seriesMode) seriesOverviewGridState else mainGridState
                Triple(grid.isScrollInProgress, grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset) to
                    grid.layoutInfo.visibleItemsInfo.firstOrNull { it.index == grid.firstVisibleItemIndex }?.key
            } else {
                val list = if (seriesMode) seriesOverviewListState else mainListState
                Triple(list.isScrollInProgress, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset) to
                    list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == list.firstVisibleItemIndex }?.key
            }
        }.filter { !it.first.first }.collect { persistScroll() }
    }
    DisposableEffect(pageKey, viewMode, seriesMode, selectedSeries, mainEntryKeys, scrollRestored) {
        onDispose { persistScroll() }
    }
    fun persistEpisodes() {
        if (!showSeriesList) return
        onSeriesEpisodePosition(
            if (viewMode == LibraryViewMode.GRID) seriesBooksGridState.firstVisibleItemIndex else seriesBooksListState.firstVisibleItemIndex,
            if (viewMode == LibraryViewMode.GRID) seriesBooksGridState.firstVisibleItemScrollOffset else seriesBooksListState.firstVisibleItemScrollOffset)
    }
    LaunchedEffect(pageKey, showSeriesList, viewMode) {
        if (showSeriesList) snapshotFlow { if (viewMode == LibraryViewMode.GRID) seriesBooksGridState.isScrollInProgress else seriesBooksListState.isScrollInProgress }
            .filter { !it }.collect { persistEpisodes() }
    }
    DisposableEffect(pageKey, showSeriesList, viewMode) { onDispose { persistEpisodes() } }

    BackHandler {
        when {
            selectionMode -> {
                selectionMode = false
                selectedIds = emptySet()
            }
            seriesSelectionMode -> {
                seriesSelectionMode = false
                selectedSeriesNames = emptySet()
                firstSelectedSeriesName = null
            }
            showSeriesList -> onSeriesListChange(false)
            selectedSeries != null -> onSelectedSeriesChange(null)
            else -> onBackToSearch()
        }
    }

    val focusManager = LocalFocusManager.current
    var options by remember { mutableStateOf<String?>(null) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    val searchExpansion = LocalInlineSearch.current ?: remember(pageKey) { InlineSearchState() }
    val canCollapse = selectedSeries == null && books.isNotEmpty() && !searchExpansion.expanded && options == null && !filterMenuOpen && !sortMenuOpen &&
        !selectionMode && !seriesSelectionMode && editingSeriesName == null && !seriesDialog && !existingSeriesDialog
    val collapseEnabled by rememberUpdatedState(canCollapse)
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(canScroll = { collapseEnabled })
    LaunchedEffect(pageKey, canCollapse) { if (!canCollapse) scrollBehavior.state.heightOffset = 0f }
    val changeSeriesMode: (Boolean) -> Unit = {
        onSeriesModeChange(it)
        selectionMode = false
        seriesSelectionMode = false
        selectedIds = emptySet()
        selectedSeriesNames = emptySet()
    }
    if (existingSeriesDialog) {
        SeriesDestinationPicker(state, selectedIds.size, viewMode == LibraryViewMode.LIST, gridColumns, contentPadding,
            onAppend = { onAppendSeries(selectedIds, it) },
            onDone = { existingSeriesDialog = false; selectionMode = false; selectedIds = emptySet() },
            onCancel = { existingSeriesDialog = false })
        return
    }
    editingSeriesName?.let { name ->
        SeriesOptionsEditor(name, state.libraryBooks, state.customSeriesByBookId, contentPadding,
            onSave = { baseline, ids -> onCommitSeriesEdit(name, baseline, ids) },
            onDone = { editingSeriesName = null; selectionMode = false; seriesSelectionMode = false; selectedIds = emptySet(); selectedSeriesNames = emptySet() },
            onCancel = { editingSeriesName = null },
            displayTitle = state.presentations[seriesPresentationKey(name)]?.value?.title ?: name)
        return
    }
    CompositionLocalProvider(LocalWorkCardSize provides WorkCardSize.fromColumns(gridColumns)) {
    PullToRefreshBox(
        isRefreshing = state.isLibraryPullRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                isRefreshing = state.isLibraryPullRefreshing,
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = contentPadding.calculateTopPadding()),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        InlineSearchLayout(searchExpansion, contentPadding = contentPadding) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).nestedScroll(scrollBehavior.nestedScrollConnection)) {
            CollapsingToolbar(scrollBehavior) {
            tabHeader({
                UnifiedSearchField(state.libraryQueryDraft, onQueryChange, strings[R.string.ui_search_gallery], {}, onSubmitSearch,
                    state.libraryQueryHistory, state.libraryFilterSuggestions, state.isLoadingLibrarySuggestions,
                    { onRecentSearch(it.query) }, onClearSearchHistory, onSelectSuggestion)
            }, {
                Box {
                    IconButton(onClick = { options = strings[R.string.ui_gallery_options] }, modifier = Modifier.semantics { contentDescription = strings[R.string.ui_gallery_options] }) { Text("⋯") }
                    DropdownMenu(expanded = options != null, onDismissRequest = { options = null }) {
                        if (options != strings[R.string.ui_gallery_options]) DropdownMenuItem(text = { Text(options.orEmpty()) }, leadingIcon = { ActionIcon(ActionSymbol.BACK) }, onClick = { options = strings[R.string.ui_gallery_options] })
                        when (options) {
                            strings[R.string.ui_view_58d697] -> {
                                DropdownMenuItem(text = { Text(strings[R.string.ui_group_series]) }, leadingIcon = { Checkbox(checked = groupSeries, onCheckedChange = null) }, onClick = {
                                    groupSeries = !groupSeries; libraryPreferences.saveGroupSeries(groupSeries); options = null
                                })
                                WorkViewOptions(viewMode == LibraryViewMode.GRID, gridColumns,
                                    onMode = { grid -> viewModeName = if (grid) "GRID" else "LIST"; libraryPreferences.saveLibraryViewMode(viewModeName); options = null },
                                    onSize = { gridColumns = it; libraryPreferences.saveLibraryGridColumns(it) })
                            }
                            strings[R.string.ui_storage_locations] -> {
                                val locations = state.libraryLocations + state.desktopLibraryLocations
                                if (locations.isEmpty()) DropdownMenuItem(text = { Text(strings[R.string.ui_no_storage_locations]) }, onClick = {}, enabled = false)
                                locations.forEach { location ->
                                    val checked = state.selectedLibraryLocationUris == null || location.uri in state.selectedLibraryLocationUris
                                    DropdownMenuItem(
                                        text = { Text((if (location.isCloud) "PC · " else strings[R.string.ui_device]) + location.displayName) },
                                        leadingIcon = { Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.size(20.dp)) },
                                        trailingIcon = { ActionIcon(if (location.isCloud) ActionSymbol.PC else ActionSymbol.DEVICE) },
                                        modifier = Modifier.semantics {
                                            role = Role.Checkbox
                                            toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                                        },
                                        onClick = { onLocationChange(location.uri) },
                                    )
                                }
                            }
                            else -> {
                                listOf(strings[R.string.ui_view_58d697] to ActionSymbol.GRID, strings[R.string.ui_storage_locations] to ActionSymbol.FOLDER).forEach { (category, symbol) ->
                                    DropdownMenuItem(text = { Text("$category  ›") }, leadingIcon = { ActionIcon(symbol) }, onClick = { options = category })
                                }
                                DropdownMenuItem(text = { Text(if (state.isAutoCreatingSeries) strings[R.string.ui_analyzing] else strings[R.string.ui_auto_generate_series]) }, leadingIcon = { ActionIcon(ActionSymbol.SERIES) }, enabled = !state.isAutoCreatingSeries, onClick = { onAutoCreateSeries(); options = null })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(strings[R.string.ui_refresh]) }, leadingIcon = { ActionIcon(ActionSymbol.REFRESH) }, onClick = { options = null; onRefresh() })
                                DropdownMenuItem(text = { Text(if (state.isLibrarySyncing) strings[R.string.ui_syncing_with_pc] else strings[R.string.ui_pc_sync_pending, (state.librarySyncPendingCount)]) }, leadingIcon = { ActionIcon(ActionSymbol.PC) },
                                    enabled = !state.isLibrarySyncing && state.selectedProfileId != null, onClick = { options = null; onSync() })
                            }
                        }
                    }
                }
            })
            if (!searchExpansion.expanded) {
            if (seriesSelectionMode && seriesMode && selectedSeries == null) {
                LibrarySelectionToolbar(
                    selectedCount = selectedSeriesNames.size,
                    allSelected = seriesNames(books, state.customSeriesByBookId).let {
                        it.isNotEmpty() && selectedSeriesNames.containsAll(it)
                    },
                    onSelectAll = {
                        val all = seriesNames(books, state.customSeriesByBookId)
                        if (selectedSeriesNames.containsAll(all)) {
                            selectedSeriesNames = emptySet()
                            firstSelectedSeriesName = null
                        } else {
                            selectedSeriesNames = all
                            firstSelectedSeriesName = all.firstOrNull()
                        }
                    },
                    onFavorite = null,
                    onRead = null,
                    onSeries = null,
                    onMerge = if (selectedSeriesNames.size >= 2) ({
                        renameInput = firstSelectedSeriesName ?: selectedSeriesNames.first()
                        mergeSeriesDialog = true
                    }) else null,
                    onRename = selectedSeriesNames.singleOrNull()?.let { series ->
                        {
                            renameInput = series
                            renameSeriesDialog = true
                        }
                    },
                    onEditSeries = selectedSeriesNames.singleOrNull()?.let { name -> { editingSeriesName = name } },
                    onDelete = { deleteDialog = true },
                    onClose = {
                        seriesSelectionMode = false
                        selectedSeriesNames = emptySet()
                        firstSelectedSeriesName = null
                    },
                )
            } else if (selectionMode) {
                val allSelectedRead = selectedIds.isNotEmpty() && selectedIds.all { it in state.libraryReadIds }
                val allSelectedHidden = selectedIds.isNotEmpty() && selectedIds.all { it in state.libraryHiddenIds }
                LibrarySelectionToolbar(
                    selectedCount = selectedIds.size,
                    allSelected = shownBooks.isNotEmpty() && selectedIds.containsAll(shownBooks.map { it.id }),
                    onSelectAll = {
                        selectedIds = if (selectedIds.containsAll(shownBooks.map { it.id })) emptySet()
                        else shownBooks.mapTo(linkedSetOf(), LibraryBook::id)
                    },
                    onFavorite = { onAddFavorites(selectedIds) },
                    onRead = { if (allSelectedRead) onMarkUnread(selectedIds) else onMarkRead(selectedIds) },
                    readLabel = if (allSelectedRead) strings[R.string.ui_unread] else strings[R.string.ui_read],
                    onSeries = if (selectedSeries == null) {
                        ({ seriesDialog = true })
                    } else null,
                    seriesLabel = strings[R.string.ui_add_series],
                    onExistingSeries = { existingSeriesDialog = true },
                    onEditSeries = selectedIds.mapNotNull { state.customSeriesByBookId[it]?.name }.distinct().singleOrNull()
                        ?.takeIf { name -> state.customSeriesByBookId.filterValues { it.name == name }.keys == selectedIds }
                        ?.let { name -> { editingSeriesName = name } },
                    onRename = selectedIds.singleOrNull()?.let { id ->
                        {
                            renameInput = state.libraryBooks.firstOrNull { it.id == id }?.title.orEmpty()
                            renameBookDialog = true
                        }
                    },
                    onHide = {
                        onSetBooksHidden(selectedIds, !allSelectedHidden)
                        selectionMode = false
                        selectedIds = emptySet()
                    },
                    hideLabel = if (allSelectedHidden) strings[R.string.ui_unhide] else strings[R.string.ui_hide],
                    onDelete = { deleteDialog = true },
                    onClose = {
                        selectionMode = false
                        selectedIds = emptySet()
                    },
                )
            } else if (selectedSeries == null) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    val modes: @Composable () -> Unit = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(colors = activeFilterChipColors(), selected = !seriesMode && !state.libraryExcludeSeries, onClick = { changeSeriesMode(false) }, leadingIcon = { ActionIcon(ActionSymbol.GRID) }, label = { Text(strings[R.string.ui_all]) })
                            FilterChip(colors = activeFilterChipColors(), selected = seriesMode || state.libraryExcludeSeries, onClick = {
                                if (seriesMode) onExcludeSeriesChange(true) else changeSeriesMode(true)
                            }, leadingIcon = { ActionIcon(if (state.libraryExcludeSeries) ActionSymbol.HIDE else ActionSymbol.SERIES) },
                                label = { Text(if (state.libraryExcludeSeries) strings[R.string.ui_exclude_series] else if (seriesMode) strings[R.string.ui_series_only] else strings[R.string.ui_series]) })
                        }
                    }
                    val tools: @Composable () -> Unit = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            LibraryFilterMenu(state.libraryFavoritesOnly, state.libraryReadFilter, state.libraryVisibilityFilter,
                                onToggleFavoritesFilter, onReadFilterChange, onVisibilityFilterChange, state.libraryExcludeSeries, onExcludeSeriesChange, { filterMenuOpen = it })
                            LibrarySortMenu(state.librarySort, onSortChange, { sortMenuOpen = it })
                        }
                    }
                    GalleryToolsRow(modes, tools)
                }

            }

            }
            if (selectedSeries != null && !selectionMode) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (showSeriesList) onSeriesListChange(false) else onSelectedSeriesChange(null) },
                        modifier = Modifier.semantics { contentDescription = strings[R.string.ui_back] }) { ActionIcon(ActionSymbol.BACK) }
                    Text(state.presentations[seriesPresentationKey(selectedSeries)]?.value?.title ?: selectedSeries,
                        Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (showSeriesList) TextButton(onClick = { onSeriesListChange(false) }) { Text(strings[R.string.ui_series_details]) }

                }
            }

            }
            when {
                selectedSeries != null && !selectionMode && !showSeriesList -> SeriesDetailContent(
                    selectedSeries, state.libraryBooks.filter { state.customSeriesByBookId[it.id]?.name == selectedSeries }, state,
                    onList = { onSeriesListChange(true) }, onEdit = { editingSeriesName = selectedSeries }, onOpenBook = onOpenBook, onReadBook = onReadBook,
                    onRename = { selectedSeriesNames = setOf(selectedSeries); renameInput = selectedSeries; renameSeriesDialog = true },
                    onFavorite = { onToggleSeriesFavorite(selectedSeries) }, onRead = onMarkRead, onUnread = onMarkUnread, onHidden = onSetBooksHidden,
                    onDelete = { onDeleteSeries(setOf(selectedSeries)); onSelectedSeriesChange(null) }, onSearchFacet = onSearchFacet, onSearchLanguage = onSearchLanguage,
                    onLoadPreview = onLoadSeriesPreview,
                    pageKey = pageKey, initialPosition = seriesPage.previewPosition, onPositionChange = onSeriesPreviewPosition,
                    onSavePresentation = onSaveSeriesPresentation, onCover = onSeriesCover, onAcceptRemote = onAcceptRemoteSeriesPresentation, onResetCover = onResetSeriesCover,
                )
                state.isLibraryScanning && state.libraryBooks.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.libraryLocations.isEmpty() && state.desktopLibraryLocations.isEmpty() -> LibraryEmpty(
                    strings[R.string.ui_first_add_a_library_folder_in_settings],
                )
                books.isEmpty() && selectedSeries == null -> LibraryEmpty(strings[R.string.ui_no_books_match_the_conditions])
                seriesMode && selectedSeries == null -> LibrarySeriesOverview(
                    books = books,
                    allBooks = visibilityBooks,
                    customSeries = state.customSeriesByBookId,
                    favoriteSeriesNames = state.libraryFavoriteSeriesNames,
                    readBookIds = state.libraryReadIds,
                    presentations = state.presentations,
                    viewMode = viewMode,
                    columns = gridColumns,
                    sort = state.librarySort,
                    gridState = seriesOverviewGridState,
                    listState = seriesOverviewListState,
                    selectionMode = seriesSelectionMode,
                    selectedSeries = selectedSeriesNames,
                    onOpenSeries = onSelectedSeriesChange,
                    onToggleFavorite = onToggleSeriesFavorite,
                    onToggleSelection = { name ->
                        val wasSelected = name in selectedSeriesNames
                        selectedSeriesNames = selectedSeriesNames.toggled(name)
                        firstSelectedSeriesName = when {
                            !wasSelected && firstSelectedSeriesName == null -> name
                            wasSelected && firstSelectedSeriesName == name -> selectedSeriesNames.firstOrNull()
                            else -> firstSelectedSeriesName
                        }
                    },
                    onLongPress = { name ->
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!seriesSelectionMode) firstSelectedSeriesName = name
                        seriesSelectionMode = true
                        selectedSeriesNames = selectedSeriesNames + name
                    },
                )
                viewMode == LibraryViewMode.GRID -> LibraryGrid(
                    entries = if (selectedSeries != null) shownEntries else mainEntries,
                    state = state,
                    columns = gridColumns,
                    gridState = if (selectedSeries != null) seriesBooksGridState else mainGridState,
                    selectedIds = selectedIds,
                    selectionMode = selectionMode,
                    onOpenBook = onOpenBook,
                    onOpenSeries = onSelectedSeriesChange,
                    onToggleBook = toggleBookSelection,
                    onToggleSeries = toggleSeriesSelection,
                    onLongPressBook = longPressBook,
                    onLongPressSeries = longPressSeries,
                    onToggleFavorite = onToggleFavorite,
                    onToggleSeriesFavorite = onToggleSeriesFavorite,
                    onToggleRead = onToggleRead,
                )
                else -> LibraryList(
                    entries = if (selectedSeries != null) shownEntries else mainEntries,
                    state = state,
                    listState = if (selectedSeries != null) seriesBooksListState else mainListState,
                    selectedIds = selectedIds,
                    selectionMode = selectionMode,
                    onOpenBook = onOpenBook,
                    onOpenSeries = onSelectedSeriesChange,
                    onToggleBook = toggleBookSelection,
                    onToggleSeries = toggleSeriesSelection,
                    onLongPressBook = longPressBook,
                    onLongPressSeries = longPressSeries,
                    onToggleFavorite = onToggleFavorite,
                    onToggleSeriesFavorite = onToggleSeriesFavorite,
                    onToggleRead = onToggleRead,
                )
            }
            state.libraryScanError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
            state.librarySyncError
                ?.takeIf { it != state.libraryScanError }
                ?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
        }
    }

    }
    }
    if (seriesDialog) {
        AlertDialog(
            onDismissRequest = { seriesDialog = false },
            title = { Text(strings[R.string.ui_group_as_series]) },
            text = {
                OutlinedTextField(
                    value = seriesName,
                    onValueChange = { seriesName = it },
                    label = { Text(strings[R.string.ui_series_name]) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAssignSeries(selectedIds, seriesName)
                        selectedIds = emptySet()
                        selectionMode = false
                        seriesName = ""
                        seriesDialog = false
                    },
                    enabled = seriesName.isNotBlank(),
                ) { Text(strings[R.string.ui_save]) }
            },
            dismissButton = { TextButton(onClick = { seriesDialog = false }) { Text(strings[R.string.ui_cancel]) } },
        )
    }
    pendingSeriesSelection?.let { pendingSeries ->
        AlertDialog(
            onDismissRequest = { pendingSeriesSelection = null },
            title = { Text(strings[R.string.ui_select_series]) },
            text = { Text(strings[R.string.ui_selects_all_galleries_in_this_series_saving_as_a_new_series_re]) },
            confirmButton = {
                TextButton(onClick = {
                    val ids = state.customSeriesByBookId
                        .filterValues { it.name == pendingSeries }.keys
                    selectedIds = selectedIds + ids
                    pendingSeriesSelection = null
                }) { Text(strings[R.string.ui_confirm]) }
            },
            dismissButton = {
                TextButton(onClick = { pendingSeriesSelection = null }) { Text(strings[R.string.ui_cancel]) }
            },
        )
    }
    if (deleteDialog) {
        val deletingSeries = seriesSelectionMode && seriesMode && selectedSeries == null
        val removingFromSeries = selectionMode && selectedSeries != null
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = {
                Text(when {
                    deletingSeries -> strings[R.string.ui_delete_series]
                    removingFromSeries -> strings[R.string.ui_remove_from_series]
                    else -> strings[R.string.ui_delete_gallery]
                })
            },
            text = {
                Text(when {
                    deletingSeries -> strings[R.string.ui_delete_selected_series_galleries_will_be_kept, (selectedSeriesNames.size)]
                    removingFromSeries -> strings[R.string.ui_remove_selected_galleries_from_this_series, (selectedIds.size)]
                    else -> strings[R.string.ui_delete_selected_items_desktop_originals_move_to_trash_and_are, (selectedIds.size)]
                })
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        deletingSeries -> onDeleteSeries(selectedSeriesNames)
                        removingFromSeries -> onRemoveBooksFromSeries(selectedIds)
                        else -> onDeleteBooks(selectedIds)
                    }
                    selectedIds = emptySet()
                    selectedSeriesNames = emptySet()
                    selectionMode = false
                    seriesSelectionMode = false
                    deleteDialog = false
                }) { Text(strings[R.string.ui_delete]) }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text(strings[R.string.ui_cancel]) } },
        )
    }
    if (renameBookDialog) {
        RenameDialog(
            title = strings[R.string.ui_rename_gallery],
            value = renameInput,
            onValueChange = { renameInput = it },
            onDismiss = { renameBookDialog = false },
            onConfirm = {
                selectedIds.singleOrNull()?.let { onRenameBook(it, renameInput) }
                renameBookDialog = false
                selectionMode = false
                selectedIds = emptySet()
            },
        )
    }
    if (renameSeriesDialog) {
        val oldName = selectedSeriesNames.singleOrNull()
        RenameDialog(
            title = strings[R.string.ui_rename_series],
            value = renameInput,
            onValueChange = { renameInput = it },
            onDismiss = { renameSeriesDialog = false },
            onConfirm = {
                oldName?.let { onRenameSeries(it, renameInput) }
                renameSeriesDialog = false
                seriesSelectionMode = false
                selectedSeriesNames = emptySet()
            },
        )
    }
    if (mergeSeriesDialog) {
        RenameDialog(
            title = strings[R.string.ui_merge_series],
            value = renameInput,
            onValueChange = { renameInput = it },
            onDismiss = { mergeSeriesDialog = false },
            onConfirm = {
                onMergeSeries(selectedSeriesNames.toList(), renameInput)
                mergeSeriesDialog = false
                seriesSelectionMode = false
                selectedSeriesNames = emptySet()
                firstSelectedSeriesName = null
            },
        )
    }
}

@Composable
private fun RenameDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(strings[R.string.ui_new_name]) },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = value.isNotBlank()) { Text(strings[R.string.ui_change]) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings[R.string.ui_cancel]) } },
    )
}

@Composable
private fun LibrarySelectionToolbar(
    selectedCount: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onFavorite: (() -> Unit)?,
    onRead: (() -> Unit)?,
    onSeries: (() -> Unit)?,
    onExistingSeries: (() -> Unit)? = null,
    onEditSeries: (() -> Unit)? = null,
    onMerge: (() -> Unit)? = null,
    readLabel: String? = null,
    seriesLabel: String? = null,
    onRename: (() -> Unit)?,
    onHide: (() -> Unit)? = null,
    hideLabel: String? = null,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(strings[R.string.ui_selected, (selectedCount)], Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onSelectAll) { ActionIcon(ActionSymbol.SELECT); Text(if (allSelected) strings[R.string.ui_deselect_all] else strings[R.string.ui_select_all]) }
        if (onDelete != null) IconButton(onClick = onDelete, enabled = selectedCount > 0,
            modifier = Modifier.semantics { contentDescription = strings[R.string.ui_delete] }) { ActionIcon(ActionSymbol.DELETE) }
        var menuExpanded by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menuExpanded = true }) { Text("⋯") }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                listOf(
                    Triple(strings[R.string.ui_add_favorite], onFavorite, ActionSymbol.HEART), Triple(readLabel ?: strings[R.string.ui_read], onRead, ActionSymbol.READ),
                    Triple(seriesLabel ?: strings[R.string.ui_series], onSeries, ActionSymbol.SERIES),
                    Triple(strings[R.string.ui_add_to_series], onExistingSeries, ActionSymbol.SERIES), Triple(strings[R.string.ui_merge], onMerge, ActionSymbol.MERGE),
                    Triple(strings[R.string.ui_series_options_edit], onEditSeries, ActionSymbol.EDIT),
                    Triple(strings[R.string.ui_rename], onRename, ActionSymbol.EDIT),
                    Triple(hideLabel ?: strings[R.string.ui_hide], onHide, ActionSymbol.HIDE),
                ).forEach { (label, action, symbol) ->
                    if (action != null) DropdownMenuItem(text = { Text(label) }, leadingIcon = { ActionIcon(symbol) }, enabled = selectedCount > 0,
                        onClick = { menuExpanded = false; action() })
                }
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = strings[R.string.ui_cancel] }) { ActionIcon(ActionSymbol.CLEAR) }
    }
}

@Composable
private fun LibraryFilterMenu(
    favoritesOnly: Boolean,
    readFilter: LibraryReadFilter,
    visibilityFilter: LibraryVisibilityFilter,
    onToggleFavorites: () -> Unit,
    onReadFilterChange: (LibraryReadFilter) -> Unit,
    onVisibilityFilterChange: (LibraryVisibilityFilter) -> Unit,
    excludeSeries: Boolean,
    onExcludeSeriesChange: (Boolean) -> Unit,
    onMenuChange: (Boolean) -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    var expanded by remember { mutableStateOf(false) }
    val active = excludeSeries || favoritesOnly || readFilter != LibraryReadFilter.ALL ||
        visibilityFilter != LibraryVisibilityFilter.VISIBLE
    Box {
        OutlinedButton(onClick = { expanded = true; onMenuChange(true) },
            contentPadding = PaddingValues(horizontal = 8.dp),
            colors = activeFilterButtonColors(active)) {
            ActionIcon(ActionSymbol.FILTER)
            Text(strings[R.string.ui_filter], Modifier.padding(start = 6.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false; onMenuChange(false) }) {
            LibraryFilterItems(favoritesOnly, readFilter, visibilityFilter, onToggleFavorites, onReadFilterChange, onVisibilityFilterChange, excludeSeries, onExcludeSeriesChange)

        }
    }
}

@Composable
private fun LibraryFilterItems(
    favoritesOnly: Boolean,
    readFilter: LibraryReadFilter,
    visibilityFilter: LibraryVisibilityFilter,
    onToggleFavorites: () -> Unit,
    onReadFilterChange: (LibraryReadFilter) -> Unit,
    onVisibilityFilterChange: (LibraryVisibilityFilter) -> Unit,
    excludeSeries: Boolean,
    onExcludeSeriesChange: (Boolean) -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    ChoiceMenuItem(strings[R.string.ui_favorites], favoritesOnly, onToggleFavorites)
    ChoiceMenuItem(strings[R.string.ui_exclude_series], excludeSeries, onClick = { onExcludeSeriesChange(!excludeSeries) })
    HorizontalDivider()
    LibraryReadFilter.entries.forEach { filter ->
        val label = when (filter) {
            LibraryReadFilter.ALL -> strings[R.string.ui_all]
            LibraryReadFilter.UNREAD -> strings[R.string.ui_unread_e216e3]
            LibraryReadFilter.READ -> strings[R.string.ui_read]
        }
        ChoiceMenuItem(label, readFilter == filter, onClick = { onReadFilterChange(filter) })
    }
    HorizontalDivider()
    LibraryVisibilityFilter.entries.forEach { filter ->
        val label = when (filter) {
            LibraryVisibilityFilter.VISIBLE -> strings[R.string.ui_visible_items]
            LibraryVisibilityFilter.HIDDEN -> strings[R.string.ui_hidden_items]
            LibraryVisibilityFilter.ALL -> strings[R.string.ui_visible_hidden]
        }
        ChoiceMenuItem(label, visibilityFilter == filter, onClick = { onVisibilityFilterChange(filter) })
    }
}

internal fun LibrarySort.isAscending(): Boolean = this == LibrarySort.TITLE_ASC || this == LibrarySort.ARTIST_ASC || this == LibrarySort.OLDEST

internal fun LibrarySort.withDirection(ascending: Boolean): LibrarySort = when (this) {
    LibrarySort.TITLE_ASC, LibrarySort.TITLE_DESC -> if (ascending) LibrarySort.TITLE_ASC else LibrarySort.TITLE_DESC
    LibrarySort.ARTIST_ASC, LibrarySort.ARTIST_DESC -> if (ascending) LibrarySort.ARTIST_ASC else LibrarySort.ARTIST_DESC
    LibrarySort.NEWEST, LibrarySort.OLDEST -> if (ascending) LibrarySort.OLDEST else LibrarySort.NEWEST
}

@Composable
internal fun LibrarySortMenu(sort: LibrarySort, onSortChange: (LibrarySort) -> Unit, onMenuChange: (Boolean) -> Unit = {}) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    var expanded by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            OutlinedButton(onClick = { expanded = true; onMenuChange(true) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                ActionIcon(ActionSymbol.SORT)
                Text(strings[R.string.ui_sort], Modifier.padding(start = 4.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false; onMenuChange(false) }) {
                listOf(strings[R.string.ui_title] to LibrarySort.TITLE_ASC, strings[R.string.ui_artist] to LibrarySort.ARTIST_ASC, strings[R.string.ui_modified_date] to LibrarySort.OLDEST).forEach { (label, field) ->
                    val option = field.withDirection(sort.isAscending())
                    ChoiceMenuItem(label, sort == option, onClick = { onSortChange(option); expanded = false; onMenuChange(false) })
                }
            }
        }
        val direction = if (sort.isAscending()) strings[R.string.ui_ascending] else strings[R.string.ui_descending]
        OutlinedIconButton(onClick = { onSortChange(sort.withDirection(!sort.isAscending())) },
            modifier = Modifier.semantics { contentDescription = strings[R.string.ui_toggle_sort_direction, (direction)] }) {
            Text(if (sort.isAscending()) "↑" else "↓", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun LibrarySelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    val selectedColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.outline
    Canvas(modifier = modifier.size(22.dp)) {
        drawCircle(
            color = if (selected) selectedColor else idleColor,
            radius = size.minDimension * 0.4f,
            style = Stroke(width = size.minDimension * 0.12f),
        )
        if (selected) {
            drawCircle(selectedColor, radius = size.minDimension * 0.21f)
        }
    }
}

@Composable
private fun LibraryGrid(
    entries: List<LibraryMainEntry>,
    state: MainUiState,
    columns: Int,
    gridState: LazyGridState,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    onOpenBook: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onToggleBook: (String) -> Unit,
    onToggleSeries: (String, Set<String>) -> Unit,
    onLongPressBook: (String) -> Unit,
    onLongPressSeries: (String, String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleSeriesFavorite: (String) -> Unit,
    onToggleRead: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = LibraryMainEntry::scrollKey) { entry ->
            when (entry) {
                is LibraryMainEntry.Book -> LibraryBookCard(
                    book = entry.book,
                    badgeScale = thumbnailBadgeScale(columns),
                    favorite = entry.book.id in state.libraryFavoriteIds,
                    read = entry.book.id in state.libraryReadIds,
                    progress = state.libraryProgress[entry.book.id] ?: 0,
                    selected = entry.book.id in selectedIds,
                    selectionMode = selectionMode,
                    onOpen = { if (selectionMode) onToggleBook(entry.book.id) else onOpenBook(entry.book.id) },
                    onLongPress = { onLongPressBook(entry.book.id) },
                    onToggleFavorite = { onToggleFavorite(entry.book.id) },
                    onToggleRead = { onToggleRead(entry.book.id) },
                )
                is LibraryMainEntry.Series -> {
                    val ids = entry.books.mapTo(linkedSetOf(), LibraryBook::id)
                    SeriesCard(
                        name = entry.name,
                        books = entry.books,
                        listMode = false,
                        badgeScale = thumbnailBadgeScale(columns),
                        favorite = entry.name in state.libraryFavoriteSeriesNames,
                        read = entry.books.isNotEmpty() && entry.books.all { it.id in state.libraryReadIds },
                        selectionMode = selectionMode,
                        selected = ids.isNotEmpty() && ids.all { it in selectedIds },
                        onClick = { if (selectionMode) onToggleSeries(entry.name, ids) else onOpenSeries(entry.name) },
                        onLongPress = { onLongPressSeries(entry.name, entry.books.firstOrNull()?.title.orEmpty()) },
                        onToggleFavorite = { onToggleSeriesFavorite(entry.name) },
                        presentation = state.presentations[seriesPresentationKey(entry.name)]?.value,
                        coverPresentation = state.presentations[seriesCoverPresentationKey(entry.name)]?.value,
                    )
                }
            }
        }
    }
    DynamicVerticalScrollbar(gridState)
    }
}

@Composable
private fun LibraryList(
    entries: List<LibraryMainEntry>,
    state: MainUiState,
    listState: LazyListState,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    onOpenBook: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onToggleBook: (String) -> Unit,
    onToggleSeries: (String, Set<String>) -> Unit,
    onLongPressBook: (String) -> Unit,
    onLongPressSeries: (String, String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleSeriesFavorite: (String) -> Unit,
    onToggleRead: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listItems(entries, key = LibraryMainEntry::scrollKey) { entry ->
            when (entry) {
                is LibraryMainEntry.Book -> LibraryBookListItem(
                    book = entry.book,
                    favorite = entry.book.id in state.libraryFavoriteIds,
                    read = entry.book.id in state.libraryReadIds,
                    progress = state.libraryProgress[entry.book.id] ?: 0,
                    selected = entry.book.id in selectedIds,
                    selectionMode = selectionMode,
                    onOpen = { if (selectionMode) onToggleBook(entry.book.id) else onOpenBook(entry.book.id) },
                    onLongPress = { onLongPressBook(entry.book.id) },
                    onToggleFavorite = { onToggleFavorite(entry.book.id) },
                    onToggleRead = { onToggleRead(entry.book.id) },
                )
                is LibraryMainEntry.Series -> {
                    val ids = entry.books.mapTo(linkedSetOf(), LibraryBook::id)
                    SeriesCard(
                        name = entry.name,
                        books = entry.books,
                        listMode = true,
                        badgeScale = LIST_THUMBNAIL_BADGE_SCALE,
                        favorite = entry.name in state.libraryFavoriteSeriesNames,
                        read = entry.books.isNotEmpty() && entry.books.all { it.id in state.libraryReadIds },
                        selectionMode = selectionMode,
                        selected = ids.isNotEmpty() && ids.all { it in selectedIds },
                        onClick = { if (selectionMode) onToggleSeries(entry.name, ids) else onOpenSeries(entry.name) },
                        onLongPress = { onLongPressSeries(entry.name, entry.books.firstOrNull()?.title.orEmpty()) },
                        onToggleFavorite = { onToggleSeriesFavorite(entry.name) },
                        presentation = state.presentations[seriesPresentationKey(entry.name)]?.value,
                        coverPresentation = state.presentations[seriesCoverPresentationKey(entry.name)]?.value,
                    )
                }
            }
        }
    }
    DynamicVerticalScrollbar(listState)
    }
}

@Composable
private fun LibraryBookCard(
    book: LibraryBook,
    badgeScale: Float = 1f,
    favorite: Boolean,
    read: Boolean,
    progress: Int,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleRead: () -> Unit,
) {
    LibraryWorkCard(book, progress, grid = true, favorite = favorite, read = read,
        selected = selected, selectionMode = selectionMode, onOpen = onOpen, onLongPress = onLongPress,
        onToggleFavorite = onToggleFavorite, badgeScale = badgeScale)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryBookListItem(
    book: LibraryBook,
    favorite: Boolean,
    read: Boolean,
    progress: Int,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleRead: () -> Unit,
) {
    LibraryWorkCard(book, progress, grid = false, favorite = favorite, read = read,
        selected = selected, selectionMode = selectionMode, onOpen = onOpen, onLongPress = onLongPress,
        onToggleFavorite = onToggleFavorite, badgeScale = LIST_THUMBNAIL_BADGE_SCALE)
}

@Composable
internal fun BoxScope.LibraryThumbnailBadges(
    read: Boolean,
    isCloud: Boolean?,
    favorite: Boolean,
    showFavorite: Boolean = true,
    onToggleFavorite: () -> Unit,
    scale: Float,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    if (read) {
        ThumbnailBadge(strings[R.string.ui_read], Modifier.align(Alignment.TopStart), scale)
    }
    if (isCloud == true) {
        ThumbnailIconBadge(Modifier.align(Alignment.TopEnd), scale) { CloudOutlineIcon(scale = scale) }
    } else if (isCloud == false) {
        ThumbnailBadge(strings[R.string.ui_device_d79c45], Modifier.align(Alignment.TopEnd), scale)
    }
    if (showFavorite) Surface(
        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp * scale),
        shape = RoundedCornerShape(10.dp * scale),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        TextButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(42.dp * scale),
            contentPadding = PaddingValues(0.dp),
        ) {
            FavoriteHeartIcon(favorite = favorite, modifier = Modifier.size(22.dp * scale))
        }
    }
}

@Composable
private fun FavoriteHeartIcon(favorite: Boolean, modifier: Modifier = Modifier) {
    val color = Color(0xFFE53935).copy(alpha = if (favorite) 1f else 0.82f)
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.9f)
            cubicTo(
                size.width * 0.42f, size.height * 0.8f,
                size.width * 0.08f, size.height * 0.59f,
                size.width * 0.08f, size.height * 0.31f,
            )
            cubicTo(
                size.width * 0.08f, size.height * 0.12f,
                size.width * 0.23f, size.height * 0.05f,
                size.width * 0.37f, size.height * 0.09f,
            )
            cubicTo(
                size.width * 0.44f, size.height * 0.11f,
                size.width * 0.48f, size.height * 0.17f,
                size.width * 0.5f, size.height * 0.22f,
            )
            cubicTo(
                size.width * 0.55f, size.height * 0.1f,
                size.width * 0.66f, size.height * 0.06f,
                size.width * 0.76f, size.height * 0.09f,
            )
            cubicTo(
                size.width * 0.91f, size.height * 0.13f,
                size.width * 0.97f, size.height * 0.27f,
                size.width * 0.91f, size.height * 0.44f,
            )
            cubicTo(
                size.width * 0.84f, size.height * 0.63f,
                size.width * 0.61f, size.height * 0.8f,
                size.width * 0.5f, size.height * 0.9f,
            )
            close()
        }
        if (favorite) drawPath(path, color)
        else drawPath(path, color, style = Stroke(width = 3.4.dp.toPx()))
    }
}

@Composable
private fun ThumbnailIconBadge(
    modifier: Modifier,
    scale: Float,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.padding(6.dp * scale),
        shape = RoundedCornerShape(6.dp * scale),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Box(
            modifier = Modifier.width(35.dp * scale).height(27.dp * scale),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
private fun CloudOutlineIcon(modifier: Modifier = Modifier, scale: Float = 1f) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier.size(18.dp * scale)) {
        val path = Path().apply {
            moveTo(size.width * 0.22f, size.height * 0.78f)
            cubicTo(size.width * 0.06f, size.height * 0.78f, 0f, size.height * 0.65f, size.width * 0.04f, size.height * 0.52f)
            cubicTo(size.width * 0.07f, size.height * 0.41f, size.width * 0.16f, size.height * 0.35f, size.width * 0.27f, size.height * 0.36f)
            cubicTo(size.width * 0.33f, size.height * 0.17f, size.width * 0.49f, size.height * 0.09f, size.width * 0.64f, size.height * 0.19f)
            cubicTo(size.width * 0.71f, size.height * 0.23f, size.width * 0.75f, size.height * 0.29f, size.width * 0.77f, size.height * 0.37f)
            cubicTo(size.width * 0.92f, size.height * 0.39f, size.width, size.height * 0.5f, size.width * 0.97f, size.height * 0.63f)
            cubicTo(size.width * 0.95f, size.height * 0.73f, size.width * 0.87f, size.height * 0.78f, size.width * 0.77f, size.height * 0.78f)
            close()
        }
        drawPath(path, color, style = Stroke(width = 2.2.dp.toPx()))
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean, modifier: Modifier = Modifier, scale: Float = 1f) {
    Surface(
        modifier = modifier.size(42.dp * scale).border(
            width = 3.dp * scale,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
            shape = CircleShape,
        ),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.25f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) Text("✓", color = Color.White, fontSize = 25.sp * scale, fontWeight = FontWeight.Bold)
        }
    }
}

private fun selectionColorFilter(enabled: Boolean): ColorFilter? = if (enabled) {
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
} else null

@Composable
private fun ThumbnailBadge(text: String, modifier: Modifier, scale: Float) {
    Surface(
        modifier = modifier.padding(6.dp * scale),
        shape = RoundedCornerShape(6.dp * scale),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp * scale, vertical = 4.dp * scale),
            fontSize = 11.sp * scale,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun LibraryWorkCard(
    book: LibraryBook, progress: Int, grid: Boolean, favorite: Boolean, read: Boolean,
    selected: Boolean, selectionMode: Boolean, onOpen: () -> Unit, onLongPress: () -> Unit,
    onToggleFavorite: () -> Unit, badgeScale: Float,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val model = remember(book.coverUri, book.cloudToken, context) { libraryImageRequest(context, book.coverUri, book.cloudToken) }
    WorkCard(title = book.title, artist = book.metadata.artists.joinToString(),
        metadata = listOfNotNull(book.metadata.language, strings[R.string.ui_pages, book.pages.size]).joinToString(" · "),
        tags = book.metadata.tags.map { CardTag(tagDisplayInfo(it)) },
        footer = if (progress > 0) strings[R.string.ui_from_page, progress + 1] else "",
        grid = grid, onClick = onOpen, onLongClick = onLongPress, selected = selected, webtoon = book.webtoon == true, border = galleryCardBorder(favorite = favorite)) {
        AsyncImage(model, strings[R.string.ui_cover, book.title], Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            colorFilter = selectionColorFilter(selectionMode))
        if (!selectionMode) LibraryThumbnailBadges(read, book.isCloud, favorite, onToggleFavorite = onToggleFavorite, scale = badgeScale)
        else SelectionIndicator(selected, Modifier.align(Alignment.Center), badgeScale)
    }
}

@Composable
private fun LibrarySeriesOverview(
    books: List<LibraryBook>,
    allBooks: List<LibraryBook>,
    customSeries: Map<String, CustomSeriesAssignment>,
    favoriteSeriesNames: Set<String>,
    readBookIds: Set<String>,
    viewMode: LibraryViewMode,
    columns: Int,
    sort: LibrarySort,
    gridState: LazyGridState,
    listState: LazyListState,
    selectionMode: Boolean,
    selectedSeries: Set<String>,
    onOpenSeries: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onLongPress: (String) -> Unit,
    presentations: Map<String, com.doujinzip.android.data.PresentationRecord>,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val groups = remember(books, allBooks, customSeries, sort) {
        val grouped = seriesNames(books, customSeries).map { seriesName ->
            seriesName to allBooks.filter { customSeries[it.id]?.name == seriesName }
                .sortedWith(
                    compareBy<LibraryBook> {
                        customSeries[it.id]?.takeIf { assignment -> assignment.name == seriesName }?.order
                            ?: Int.MAX_VALUE
                    }.thenBy { it.id },
                )
        }
        when (sort) {
            LibrarySort.TITLE_ASC -> grouped.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.first })
            LibrarySort.TITLE_DESC -> grouped.sortedWith(compareByDescending<Pair<String, List<LibraryBook>>, String>(String.CASE_INSENSITIVE_ORDER) { it.first })
            LibrarySort.ARTIST_ASC -> grouped.sortedWith(seriesGroupArtistComparator(descending = false))
            LibrarySort.ARTIST_DESC -> grouped.sortedWith(seriesGroupArtistComparator(descending = true))
            LibrarySort.NEWEST -> grouped.sortedByDescending { (_, seriesBooks) ->
                seriesBooks.maxOfOrNull(LibraryBook::modifiedAt) ?: 0L
            }
            LibrarySort.OLDEST -> grouped.sortedBy { (_, seriesBooks) ->
                seriesBooks.maxOfOrNull(LibraryBook::modifiedAt) ?: 0L
            }
        }
    }
    if (groups.isEmpty()) {
        LibraryEmpty(strings[R.string.ui_no_series_yet_hold_an_item_to_group_it_into_a_series])
        return
    }
    if (viewMode == LibraryViewMode.GRID) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(groups, key = { it.first }) { (name, seriesBooks) ->
                SeriesCard(
                    name = name,
                    books = seriesBooks,
                    listMode = false,
                    badgeScale = thumbnailBadgeScale(columns),
                    favorite = name in favoriteSeriesNames,
                    read = seriesBooks.isNotEmpty() && seriesBooks.all { it.id in readBookIds },
                    selectionMode = selectionMode,
                    selected = name in selectedSeries,
                    onClick = { if (selectionMode) onToggleSelection(name) else onOpenSeries(name) },
                    onLongPress = { onLongPress(name) },
                    onToggleFavorite = { onToggleFavorite(name) },
                    presentation = presentations[seriesPresentationKey(name)]?.value,
                    coverPresentation = presentations[seriesCoverPresentationKey(name)]?.value,
                )
            }
        }
        DynamicVerticalScrollbar(gridState)
        }
    } else {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listItems(groups, key = { it.first }) { (name, seriesBooks) ->
                SeriesCard(
                    name = name,
                    books = seriesBooks,
                    listMode = true,
                    badgeScale = LIST_THUMBNAIL_BADGE_SCALE,
                    favorite = name in favoriteSeriesNames,
                    read = seriesBooks.isNotEmpty() && seriesBooks.all { it.id in readBookIds },
                    selectionMode = selectionMode,
                    selected = name in selectedSeries,
                    onClick = { if (selectionMode) onToggleSelection(name) else onOpenSeries(name) },
                    onLongPress = { onLongPress(name) },
                    onToggleFavorite = { onToggleFavorite(name) },
                    presentation = presentations[seriesPresentationKey(name)]?.value,
                    coverPresentation = presentations[seriesCoverPresentationKey(name)]?.value,
                )
            }
        }
        DynamicVerticalScrollbar(listState)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SeriesCard(
    name: String,
    books: List<LibraryBook>,
    listMode: Boolean,
    badgeScale: Float,
    favorite: Boolean,
    read: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onToggleFavorite: () -> Unit,
    presentation: GalleryPresentation? = null,
    coverPresentation: GalleryPresentation? = null,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val artists = remember(books, presentation) { (presentation?.metadata?.artists ?: books.flatMap { it.metadata.artists }.distinct()).joinToString() }
    val tags = remember(books, presentation) { (presentation?.metadata?.tags ?: books.flatMap { it.metadata.tags }.distinct()).map { CardTag(tagDisplayInfo(it)) } }
    val webtoon = remember(books) { books.any { it.webtoon == true } }
    WorkCard(title = presentation?.title ?: name, artist = artists,
        metadata = strings[R.string.ui_chapters, books.size, ""],
        tags = tags,
        footer = strings[R.string.ui_series], grid = !listMode, onClick = onClick, onLongClick = onLongPress, selected = selected,
        webtoon = webtoon,
        border = galleryCardBorder(series = true, favorite = favorite)) {
        SeriesCover(book = books.firstOrNull { it.coverUri.isNotBlank() }, books = books, context = context, favorite = favorite, read = read,
            onToggleFavorite = onToggleFavorite, selectionMode = selectionMode, selected = selected,
            badgeScale = badgeScale, modifier = Modifier.fillMaxSize(),
            coverUri = presentationCoverUri(context, coverPresentation) ?: presentationCoverUri(context, presentation), title = presentation?.title ?: name)
    }
}

@Composable
private fun SeriesCover(
    book: LibraryBook?,
    books: List<LibraryBook>,
    context: android.content.Context,
    favorite: Boolean,
    read: Boolean,
    onToggleFavorite: () -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    badgeScale: Float,
    modifier: Modifier,
    coverUri: String?,
    title: String,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (book != null || coverUri != null) {
            AsyncImage(
                model = libraryImageRequest(context, coverUri ?: book?.coverUri.orEmpty(), if (coverUri == null) book?.cloudToken else null),
                contentDescription = strings[R.string.ui_series_cover, title],
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = selectionColorFilter(selectionMode),
            )
        }
        if (!selectionMode) {
            SeriesThumbnailBadges(
                read = read,
                source = when {
                    books.all(LibraryBook::isCloud) -> SeriesSource.CLOUD
                    books.none(LibraryBook::isCloud) -> SeriesSource.LOCAL
                    else -> SeriesSource.MIXED
                },
                favorite = favorite,
                onToggleFavorite = onToggleFavorite,
                scale = badgeScale,
            )
        }
        if (selectionMode) SelectionIndicator(selected, Modifier.align(Alignment.Center), badgeScale)
    }
}

private enum class SeriesSource { CLOUD, LOCAL, MIXED }

@Composable
private fun BoxScope.SeriesThumbnailBadges(
    read: Boolean,
    source: SeriesSource,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    scale: Float,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    if (read) ThumbnailBadge(strings[R.string.ui_read], Modifier.align(Alignment.TopStart), scale)
    when (source) {
        SeriesSource.CLOUD ->
            ThumbnailIconBadge(Modifier.align(Alignment.TopEnd), scale) { CloudOutlineIcon(scale = scale) }
        SeriesSource.LOCAL -> ThumbnailBadge(strings[R.string.ui_device_d79c45], Modifier.align(Alignment.TopEnd), scale)
        SeriesSource.MIXED -> ThumbnailBadge(strings[R.string.ui_mixed], Modifier.align(Alignment.TopEnd), scale)
    }
    Surface(
        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp * scale),
        shape = RoundedCornerShape(6.dp * scale),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        SeriesStackIcon(Modifier.padding(6.dp * scale).size(20.dp * scale))
    }
    Surface(
        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp * scale),
        shape = RoundedCornerShape(10.dp * scale),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        TextButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(42.dp * scale),
            contentPadding = PaddingValues(0.dp),
        ) {
            FavoriteHeartIcon(favorite = favorite, modifier = Modifier.size(22.dp * scale))
        }
    }
}

@Composable
private fun SeriesStackIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier) {
        drawRoundRect(
            color = color.copy(alpha = 0.62f),
            topLeft = Offset(size.width * 0.04f, size.height * 0.08f),
            size = Size(size.width * 0.7f, size.height * 0.82f),
            cornerRadius = CornerRadius(size.width * 0.13f),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.26f, size.height * 0.18f),
            size = Size(size.width * 0.7f, size.height * 0.82f),
            cornerRadius = CornerRadius(size.width * 0.13f),
        )
    }
}

@Composable
internal fun LibrarySeriesOrderEditor(
    books: List<LibraryBook>,
    customSeries: Map<String, CustomSeriesAssignment>,
    onReorder: (List<String>) -> Unit,
    selectedIds: Set<String> = emptySet(),
    onSelect: ((String) -> Unit)? = null,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val booksById = remember(books) { books.associateBy(LibraryBook::id) }
    val itemHeights = remember { mutableMapOf<String, Int>() }
    var orderedBooks by remember(books, customSeries) {
        mutableStateOf(
            books.sortedWith(
                compareBy<LibraryBook> { customSeries[it.id]?.order ?: Int.MAX_VALUE }
                    .thenBy(LibraryBook::id),
            ),
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listItems(orderedBooks, key = LibraryBook::id) { book ->
            val movable = customSeries[book.id] != null
            val index = orderedBooks.indexOf(book)
            var accumulatedDrag by remember(book.id) { mutableFloatStateOf(0f) }
            var dragging by remember(book.id) { mutableStateOf(false) }
            var dragStartOrder by remember(book.id) { mutableStateOf<List<String>?>(null) }
            var dragOrder by remember(book.id) { mutableStateOf<List<String>?>(null) }
            val currentOrderedBooks by rememberUpdatedState(orderedBooks)
            val dragScale by animateFloatAsState(
                targetValue = if (dragging) 1.025f else 1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
                label = "seriesReorderScale",
            )
            val settledDragOffset by animateFloatAsState(
                targetValue = if (dragging) accumulatedDrag else 0f,
                animationSpec = tween(160, easing = LinearOutSlowInEasing),
                label = "seriesReorderOffset",
            )
            Card(colors = appCardColors(book.id in selectedIds),
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { itemHeights[book.id] = it.height }
                    .zIndex(if (dragging) 1f else 0f)
                    .animateItem(
                        placementSpec = spring(dampingRatio = 0.78f, stiffness = 430f),
                    )
                    .graphicsLayer {
                        scaleX = dragScale
                        scaleY = dragScale
                        translationY = if (dragging) accumulatedDrag else settledDragOffset
                        shadowElevation = if (dragging) 10.dp.toPx() else 0f
                    }
                    .pointerInput(book.id, movable) {
                    if (movable) {
                        val fallbackHeight = 56.dp.toPx()
                        val itemSpacing = 8.dp.toPx()
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                accumulatedDrag = 0f
                                dragging = true
                                val ids = currentOrderedBooks.map(LibraryBook::id)
                                dragStartOrder = ids
                                dragOrder = ids
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                dragging = false
                                val finalOrder = dragOrder
                                if (finalOrder != null && finalOrder != dragStartOrder) {
                                    onReorder(finalOrder)
                                }
                                dragStartOrder = null
                                dragOrder = null
                            },
                            onDragCancel = {
                                dragging = false
                                dragStartOrder?.let { ids ->
                                    orderedBooks = ids.mapNotNull(booksById::get)
                                }
                                dragStartOrder = null
                                dragOrder = null
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDrag += dragAmount.y
                                var ids = dragOrder ?: currentOrderedBooks.map(LibraryBook::id)
                                var moved = false
                                while (true) {
                                    val from = ids.indexOf(book.id)
                                    val direction = when {
                                        accumulatedDrag > 0f -> 1
                                        accumulatedDrag < 0f -> -1
                                        else -> 0
                                    }
                                    val to = from + direction
                                    if (direction == 0 || from < 0 || to !in ids.indices) break
                                    val neighborId = ids[to]
                                    val crossingDistance = (
                                        (itemHeights[book.id] ?: fallbackHeight.toInt()) +
                                            (itemHeights[neighborId] ?: fallbackHeight.toInt())
                                        ) / 2f + itemSpacing
                                    if (kotlin.math.abs(accumulatedDrag) < crossingDistance) break
                                    val reordered = ids.toMutableList()
                                    reordered.removeAt(from)
                                    reordered.add(to, book.id)
                                    ids = reordered
                                    accumulatedDrag -= direction * crossingDistance
                                    moved = true
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                if (moved) {
                                    dragOrder = ids
                                    orderedBooks = ids.mapNotNull(booksById::get)
                                }
                            },
                        )
                    }
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onSelect != null) androidx.compose.material3.Checkbox(book.id in selectedIds, onCheckedChange = { onSelect(book.id) })
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(book.title, modifier = Modifier.weight(1f))
                    Text(if (movable) "☰" else strings[R.string.ui_pinned], color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun LibraryBook.effectiveSeries(
    customSeries: Map<String, CustomSeriesAssignment>,
): List<String> = customSeries[id]?.name?.let(::listOf).orEmpty()

private fun seriesNames(
    books: List<LibraryBook>,
    customSeries: Map<String, CustomSeriesAssignment>,
): Set<String> = books.flatMapTo(linkedSetOf()) { it.effectiveSeries(customSeries) }

private fun List<LibraryBook>.filterBySeries(
    series: String?,
    customSeries: Map<String, CustomSeriesAssignment>,
): List<LibraryBook> = if (series == null) {
    this
} else {
    filter { series in it.effectiveSeries(customSeries) }
        .sortedWith(
            compareBy<LibraryBook> { customSeries[it.id]?.takeIf { assignment -> assignment.name == series }?.order ?: Int.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
}

private fun Set<String>.toggled(value: String): Set<String> =
    if (value in this) this - value else this + value

@Composable
private fun LibraryEmpty(message: String) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun visibleLibraryBooks(state: MainUiState): List<LibraryBook> {
    val filtered = filterLibraryBooksByQuery(state.libraryBooksInVisibility(), state.libraryQuery, state.customSeriesByBookId).asSequence()
        .filter { !state.libraryExcludeSeries || it.id !in state.customSeriesByBookId }
        .filter {
            when {
                state.selectedLibraryLocationUris != null -> it.locationUri in state.selectedLibraryLocationUris
                else -> true
            }
        }
        .filter { book ->
            !state.libraryFavoritesOnly ||
                book.id in state.libraryFavoriteIds ||
                state.customSeriesByBookId[book.id]?.name in state.libraryFavoriteSeriesNames
        }
        .filter {
            when (state.libraryReadFilter) {
                LibraryReadFilter.ALL -> true
                LibraryReadFilter.UNREAD -> it.id !in state.libraryReadIds
                LibraryReadFilter.READ -> it.id in state.libraryReadIds
            }
        }
        .toList()
    return sortLibraryBooks(filtered, state.librarySort)
}

internal fun sortLibraryBooks(books: List<LibraryBook>, sort: LibrarySort): List<LibraryBook> =
    when (sort) {
        LibrarySort.TITLE_ASC -> books.sortedBy { it.title.lowercase() }
        LibrarySort.TITLE_DESC -> books.sortedByDescending { it.title.lowercase() }
        LibrarySort.ARTIST_ASC -> books.sortedWith(libraryBookArtistComparator(descending = false))
        LibrarySort.ARTIST_DESC -> books.sortedWith(libraryBookArtistComparator(descending = true))
        LibrarySort.NEWEST -> books.sortedByDescending(LibraryBook::modifiedAt)
        LibrarySort.OLDEST -> books.sortedBy(LibraryBook::modifiedAt)
    }

internal fun libraryFilterSignature(state: MainUiState): String = buildString {
    append(state.libraryQuery.length).append(':').append(state.libraryQuery)
    append('|').append(state.libraryFavoritesOnly)
    append('|').append(state.libraryExcludeSeries)
    append('|').append(state.libraryReadFilter.name)
    append('|').append(state.libraryVisibilityFilter.name)
    append('|').append(state.librarySort.name)
    append('|').append(state.selectedLibraryLocationUris?.sorted()?.joinToString("\u0001").orEmpty())
}

private fun MainUiState.libraryBooksInVisibility(): List<LibraryBook> = libraryBooks.filter { book ->
    when (libraryVisibilityFilter) {
        LibraryVisibilityFilter.VISIBLE -> book.id !in libraryHiddenIds
        LibraryVisibilityFilter.HIDDEN -> book.id in libraryHiddenIds
        LibraryVisibilityFilter.ALL -> true
    }
}

internal fun filterLibraryBooksByQuery(books: List<LibraryBook>, query: String, assignments: Map<String, CustomSeriesAssignment> = emptyMap()): List<LibraryBook> {
    val terms = parseLibraryQuery(query)
    return if (terms.isEmpty()) books else books.filter { book -> terms.all { it.matches(book, assignments[book.id]?.name) } }
}

private data class LibraryQueryTerm(val field: String?, val value: String, val excluded: Boolean) {
    fun matches(book: LibraryBook, customSeriesName: String?): Boolean {
        val candidates = when (field) {
            "title", "제목" -> listOf(book.title)
            "artist", "artists", "작가" -> book.metadata.artists
            "tag", "tags", "태그" -> book.metadata.tags
            "male", "female" -> book.metadata.tags.filter { it.startsWith("$field:", ignoreCase = true) }.map { it.substringAfter(':') }
            "series", "시리즈" -> book.metadata.series + listOfNotNull(customSeriesName)
            "group", "groups", "그룹" -> book.metadata.groups
            "character", "characters", "캐릭터" -> book.metadata.characters
            "language", "lang", "언어" -> listOfNotNull(book.metadata.language)
            "type", "종류" -> listOfNotNull(book.metadata.galleryType)
            "id" -> listOfNotNull(book.metadata.hitomiId, book.id)
            else -> listOf(book.title) + book.metadata.artists + book.metadata.tags +
                book.metadata.series + listOfNotNull(customSeriesName) +
                book.metadata.groups + book.metadata.characters +
                listOfNotNull(book.metadata.language, book.metadata.galleryType)
        }
        val found = candidates.any { it.replace('_', ' ').contains(value.replace('_', ' '), ignoreCase = true) }
        return if (excluded) !found else found
    }
}

private fun parseLibraryQuery(query: String): List<LibraryQueryTerm> {
    val tokenRegex = Regex("""(-?)(?:(\w+|[가-힣]+):)?(?:\"([^\"]+)\"|(\S+))""")
    return tokenRegex.findAll(com.doujinzip.android.model.normalizeSearchSeparators(query)).mapNotNull { match ->
        val value = match.groupValues[3].ifBlank { match.groupValues[4] }.trim()
        value.takeIf(String::isNotEmpty)?.let {
            LibraryQueryTerm(
                field = match.groupValues[2].ifBlank { null }?.lowercase(),
                value = it,
                excluded = match.groupValues[1] == "-",
            )
        }
    }.toList()
}
