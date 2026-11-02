package com.doujinzip.android.ui

import com.doujinzip.android.R
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import com.doujinzip.android.data.SearchResultIndex
import kotlin.math.roundToInt
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import com.doujinzip.android.security.LibraryPreferenceStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.doujinzip.android.model.GallerySummary
import com.doujinzip.android.model.LibraryBook
import androidx.compose.material3.Switch
import com.doujinzip.android.model.SearchQueryEntry
import com.doujinzip.android.model.resolveScrollIndex
import com.doujinzip.android.model.isSearchPageInRange
import com.doujinzip.android.network.parseHitomiSearchOrder
import com.doujinzip.android.network.FilterSuggestion
import com.doujinzip.android.network.AvailableLanguage
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    state: MainUiState,
    tabHeader: @Composable (@Composable () -> Unit, @Composable () -> Unit) -> Unit,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRecentSearch: (SearchQueryEntry) -> Unit,
    onClearSearchHistory: () -> Unit,
    onToggleLanguage: (String) -> Unit,
    onLoadLanguages: () -> Unit,
    onSelectAnyLanguage: () -> Unit,
    onRemoveCustomLanguage: (String) -> Unit,
    onSelectSuggestion: (FilterSuggestion) -> Unit,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    onLoadPreviousPage: () -> Unit,
    onJumpToPage: (Int) -> Unit,
    onGalleryClick: (Long) -> Unit,
    onGalleryLongClick: (Long) -> Unit,
    onSearchFacet: (String) -> Unit,
    onToggleLibraryFavorite: (String) -> Unit,
    onConnect: () -> Unit,
    pageKey: String,
    initialScrollAnchorKey: String?,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    onScrollChange: (String?, Int, Int, Int) -> Unit,
    onEnsureResults: () -> Unit,
    onVisibleResultsChange: (Int, Int) -> Unit,
    onUnhideGallery: (Long) -> Unit = {},
    onOpenSubscriptions: () -> Unit = {},
    onSubscribeCurrent: () -> Unit = {},
    onToggleTagFilter: (com.doujinzip.android.model.SearchTagFilter) -> Unit = {},
    onCustomBlockedTags: (Set<String>) -> Unit = {},
    onFeedGalleryLoaded: (GallerySummary) -> Unit = {},
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    var expandedPanel by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteLanguage by rememberSaveable { mutableStateOf<String?>(null) }
    val libraryBookByGalleryId = remember(state.libraryBooks, state.libraryHiddenIds) {
        preferredLibraryBooksByGalleryId(state.libraryBooks, state.libraryHiddenIds)
    }
    val resultIds = state.searchResultWindow?.index?.let { index -> remember(index) { index.ids } }
        ?: remember(state.galleries) { state.galleries.map { it.id } }
    val galleryPositions = remember(resultIds) { resultIds.withIndex().associate { "gallery:${it.value}" to it.index } }
    val totalCount = state.searchResultWindow?.index?.totalCount
    val maxPage = state.searchResultWindow?.index?.maxPage
    val galleriesById = remember(state.galleries) { state.galleries.associateBy { it.id } }
    val canRestoreResults = state.searchResultWindow == null && state.currentPage > 0 &&
        !state.isLoadingPage && !state.isRestoringSearch
    val contentKeys = remember(resultIds, totalCount, state.isLoadingPage, state.currentPage, state.hasNextPage, canRestoreResults) { buildList {
        add("search-controls")
        if (resultIds.isEmpty() && !state.isLoadingPage && !canRestoreResults) add("empty-results")
        if (state.isLoadingPage && resultIds.isEmpty()) add("initial-loader")
        if (canRestoreResults) add("restore-results")
        if (resultIds.isNotEmpty() || totalCount != null) {
            add("result-count")
            resultIds.forEach { add("gallery:$it") }
        }
        if (resultIds.isNotEmpty() && (state.hasNextPage || state.isLoadingPage)) {
            add("page-loader-${state.currentPage}")
        }
    } }
    LaunchedEffect(pageKey) { onEnsureResults() }
    val savedAnchorKey = remember(pageKey) { initialScrollAnchorKey }
    val restoredIndex = resolveScrollIndex(savedAnchorKey, initialScrollIndex, contentKeys)
    val listState = remember(pageKey) {
        LazyGridState(
            firstVisibleItemIndex = restoredIndex,
            firstVisibleItemScrollOffset = initialScrollOffset,
        )
    }
    var scrollRestored by remember(pageKey) {
        mutableStateOf(savedAnchorKey == null || savedAnchorKey in contentKeys)
    }
    LaunchedEffect(pageKey, contentKeys, state.isLoadingPage, state.isRestoringSearch) {
        if (!scrollRestored) {
            val anchorIndex = contentKeys.indexOf(savedAnchorKey)
            if (anchorIndex >= 0) {
                listState.scrollToItem(anchorIndex, initialScrollOffset)
                scrollRestored = true
            } else if (!state.isLoadingPage && !state.isRestoringSearch && resultIds.isNotEmpty()) {
                listState.scrollToItem(resolveScrollIndex(null, initialScrollIndex, contentKeys))
                scrollRestored = true
            }
        }
    }
    fun persistScroll() {
        if (state.searchResultWindow?.index?.pages.isNullOrEmpty()) return
        val index = listState.firstVisibleItemIndex
        val anchor = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.key?.toString() ?: return
        onScrollChange(anchor, index, listState.firstVisibleItemScrollOffset, visibleSearchPage(index, state.searchResultWindow.index))
    }
    // Keep per-frame list offsets out of MainUiState; save when the gesture settles.
    LaunchedEffect(pageKey, listState, scrollRestored, state.searchResultWindow?.index) {
        if (!scrollRestored) return@LaunchedEffect
        snapshotFlow { Triple(listState.isScrollInProgress, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .filter { !it.first }.collect { persistScroll() }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(pageKey, listState, scrollRestored, lifecycleOwner, state.searchResultWindow?.index) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && scrollRestored) persistScroll()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (scrollRestored && listState.layoutInfo.totalItemsCount > 0) persistScroll()
        }
    }
    LaunchedEffect(pageKey, listState, scrollRestored, resultIds.size) {
        if (!scrollRestored || resultIds.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo.filter { it.key.toString().startsWith("gallery:") }
            val first = visible.firstOrNull()?.key?.toString()?.let(galleryPositions::get) ?: 0
            val last = visible.lastOrNull()?.key?.toString()?.let(galleryPositions::get) ?: first
            Triple(first, last, listState.isScrollInProgress)
        }.distinctUntilChanged().collect { (first, last, scrolling) ->
            onVisibleResultsChange(first, last)
            val index = state.searchResultWindow?.index
            if (scrolling && index != null && index.firstPage > 1 && index.pageAt(first) == index.firstPage) onLoadPreviousPage()
        }
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(editing, expandedPanel) {
        if (editing && expandedPanel == "languages") onLoadLanguages()
    }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { it }.collect {
            focusManager.clearFocus(); editing = false; expandedPanel = null
        }
    }
    fun submitSearch() {
        focusManager.clearFocus()
        editing = false
        expandedPanel = null
        onSearch()
    }
    fun togglePanel(name: String) {
        val close = editing && expandedPanel == name
        expandedPanel = if (close) null else name
        editing = !close
        focusManager.clearFocus()
    }
    BackHandler(editing) { editing = false; focusManager.clearFocus() }
    var menu by remember { mutableStateOf<String?>(null) }
    val viewPreferences = remember(context) { LibraryPreferenceStore(context) }
    var gridView by rememberSaveable { mutableStateOf(viewPreferences.loadSearchGridView()) }
    var gridColumns by rememberSaveable { mutableIntStateOf(viewPreferences.loadSearchGridColumns()) }
    val feedPreferences=remember(context) { context.getSharedPreferences("subscription_feed", Context.MODE_PRIVATE) }
    val feedOpenKey="open-${state.selectedProfileId.orEmpty()}"
    var showFeed by rememberSaveable(state.selectedProfileId) { mutableStateOf(feedPreferences.getBoolean(feedOpenKey,false)) }
    LaunchedEffect(showFeed,feedOpenKey) { feedPreferences.edit().putBoolean(feedOpenKey,showFeed).apply() }
    val feedClient = remember { com.doujinzip.android.network.HitomiClient() }
    if (showFeed) {
        CompositionLocalProvider(LocalWorkCardSize provides WorkCardSize.fromColumns(gridColumns)) {
            SubscriptionFeedScreen(state, contentPadding, gridView, gridColumns,
                loadIds = { selected -> com.doujinzip.android.data.subscriptionFeedIds(selected, state.searchTagFilters, state.customBlockedTags, feedClient::subscriptionIds) },
                loadGallery = feedClient::getGallery, onLoaded = onFeedGalleryLoaded,
                probeIds = { selected -> feedClient.subscriptionFingerprint(selected.flatMap { it.queries }.map { com.doujinzip.android.model.applySearchTagFilters(it,state.searchTagFilters,state.customBlockedTags) }) },
                onBack = { showFeed = false }, onOpen = onGalleryClick, onLongClick = onGalleryLongClick,
                onFavorite = onToggleLibraryFavorite, onFacet = { showFeed = false; onSearchFacet(it) })
        }
        return
    }
    var pageDialog by rememberSaveable { mutableStateOf(false) }
    var pageInput by rememberSaveable { mutableStateOf("") }
    val searchExpansion = LocalInlineSearch.current ?: remember(pageKey) { InlineSearchState() }
    val canCollapse = resultIds.isNotEmpty() && !editing && !searchExpansion.expanded && menu == null && !pageDialog
    val collapseEnabled by rememberUpdatedState(canCollapse)
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(canScroll = { collapseEnabled })
    LaunchedEffect(pageKey, canCollapse) {
        if (!canCollapse) scrollBehavior.state.heightOffset = 0f
    }
    CompositionLocalProvider(LocalWorkCardSize provides WorkCardSize.fromColumns(gridColumns)) {
    InlineSearchLayout(searchExpansion, contentPadding = contentPadding) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).nestedScroll(scrollBehavior.nestedScrollConnection)) {
        CollapsingToolbar(scrollBehavior) {
        Column {
        tabHeader({
            UnifiedSearchField(state.searchQuery, { expandedPanel = null; onQueryChange(it) }, strings[R.string.ui_search_conditions], { editing = false; expandedPanel = null }, { submitSearch() },
                state.searchQueryHistory, state.filterSuggestions, state.isLoadingFilterSuggestions, onRecentSearch, onClearSearchHistory, onSelectSuggestion)
        }, {
            IconButton(onOpenSubscriptions,modifier=Modifier.semantics { contentDescription=strings[R.string.sub_notifications] }) {
                BadgedBox(badge={if(state.subscriptionNotices.any { !it.read }) Badge()}) { ActionIcon(ActionSymbol.BELL) }
            }
            Box {
                IconButton(onClick = { menu = "root" }, modifier = Modifier.semantics { contentDescription = strings[R.string.ui_search_options] }) { Text("⋯") }
                DropdownMenu(expanded = menu != null, onDismissRequest = { menu = null }) {
                    if (menu == "view") {
                        DropdownMenuItem(text = { Text(strings[R.string.ui_search_options]) }, leadingIcon = { ActionIcon(ActionSymbol.BACK) }, onClick = { menu = "root" })
                        WorkViewOptions(gridView, gridColumns,
                            onMode = { gridView = it; viewPreferences.saveSearchGridView(it); menu = null },
                            onSize = { gridColumns = it; viewPreferences.saveSearchGridColumns(it) })
                    } else {
                        DropdownMenuItem(text = { Text(strings[R.string.sub_current]) }, leadingIcon = { ActionIcon(ActionSymbol.BELL) }, enabled = state.searchQuery.isNotBlank(), onClick = { menu = null; onSubscribeCurrent() })
                        DropdownMenuItem(text = { Text(strings[R.string.ui_go_to_page]) }, leadingIcon = { ActionIcon(ActionSymbol.PAGE) }, enabled = resultIds.isNotEmpty(), onClick = { menu = null; pageDialog = true })
                        DropdownMenuItem(text = { Text(strings[R.string.ui_view]) }, leadingIcon = { ActionIcon(ActionSymbol.GRID) }, onClick = { menu = "view" })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(strings[R.string.ui_refresh]) }, leadingIcon = { ActionIcon(ActionSymbol.REFRESH) }, onClick = { menu = null; onRefresh() })
                        DropdownMenuItem(text = { Text(strings[R.string.ui_clear_search]) }, leadingIcon = { ActionIcon(ActionSymbol.CLEAR) }, onClick = { onQueryChange(""); menu = null; editing = true })
                    }
                }
            }
        })
        if (!searchExpansion.expanded) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { togglePanel("languages") }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                colors = activeFilterButtonColors(state.preferredLanguages.isNotEmpty() || editing && expandedPanel == "languages")) {
                ActionIcon(ActionSymbol.LANGUAGE); Spacer(Modifier.width(4.dp))
                val selected = state.preferredLanguages.singleOrNull()
                Text(selected?.let { code -> state.availableLanguages.firstOrNull { it.code == code }?.label
                    ?: mapOf("korean" to strings[R.string.ui_], "japanese" to "日本語", "english" to "English", "chinese" to "中文")[code]
                    ?: code.replace('_', ' ') } ?: strings[R.string.ui_language])
            }
            OutlinedButton(onClick = { togglePanel("tag-filters") }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                colors = activeFilterButtonColors(state.searchTagFilters.isNotEmpty() || state.customBlockedTags.isNotEmpty() || editing && expandedPanel == "tag-filters")) {
                ActionIcon(ActionSymbol.FILTER); Spacer(Modifier.width(4.dp)); Text(strings[R.string.search_tag_filters])
            }
            OutlinedButton(onClick = { showFeed = true }) {
                ActionIcon(ActionSymbol.NEWSPAPER); Spacer(Modifier.width(4.dp)); Text(strings[R.string.feed_title])
            }
        }
            Spacer(Modifier.width(4.dp))
            SearchSortMenu(state.searchQuery, enabled = !state.isLoadingPage) { oldest ->
                val query = state.searchQuery.replace(Regex("(?i)(^|\\s)sort:(oldest|newest)(?=\\s|$)"), " ").trim()
                onQueryChange(listOf(query, if (oldest) "sort:oldest" else "sort:newest").filter { it.isNotBlank() }.joinToString(" "))
                onSearch()
            }
        }
        AnimatedVisibility(visible = editing && (expandedPanel != null || state.filterSuggestions.isNotEmpty() || state.isLoadingFilterSuggestions), enter = expandVertically(expandFrom = Alignment.Top), exit = shrinkVertically(shrinkTowards = Alignment.Top)) {
            OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            val editorScroll = rememberScrollState()
            Column(Modifier.fillMaxWidth().heightIn(max = 300.dp)
                .then(if (expandedPanel == "languages" || expandedPanel == "history") Modifier else Modifier.verticalScroll(editorScroll)).padding(12.dp)) {
                if (expandedPanel == "history") {
                    RecentSearchHistory(state.searchQueryHistory, { entry ->
                        focusManager.clearFocus(); editing = false; expandedPanel = null; onRecentSearch(entry)
                    }, onClearSearchHistory)
                }
                if (expandedPanel == "tag-filters") {
                    SearchTagFilters(state.searchTagFilters,state.customBlockedTags,onToggleTagFilter,onCustomBlockedTags)
                }

                if (expandedPanel == null && (state.filterSuggestions.isNotEmpty() || state.isLoadingFilterSuggestions)) {
                    state.filterSuggestions.forEach { suggestion ->
                        TextButton(onClick = { onSelectSuggestion(suggestion) }) { Text(suggestion.token) }
                    }
                    if (state.isLoadingFilterSuggestions) Text(strings[R.string.ui_loading_filters])
                }

                    if (expandedPanel == "languages") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(strings[R.string.ui_language], Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { submitSearch() }, enabled = !state.isLoadingPage,
                                modifier = Modifier.semantics { contentDescription = strings[R.string.ui_confirm_languages_and_search] }) {
                                ActionIcon(ActionSymbol.SEARCH)
                                Spacer(Modifier.width(4.dp))
                                Text(strings[R.string.ui_confirm])
                            }
                        }
                        SearchLanguageList(state, state.preferredLanguages, onToggleLanguage, onSelectAnyLanguage,
                            onLoadLanguages, { deleteLanguage = it })
                    }


            }
            }
        }
        }
        }
        }
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (gridView) gridColumns else 1),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item(key = "search-controls", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(0.dp)) }
        if (resultIds.isEmpty() && !state.isLoadingPage && !canRestoreResults) {
            item(key = "empty-results", span = { GridItemSpan(maxLineSpan) }) {
                Text(if (state.currentPage > 0) strings[R.string.ui_no_search_results_try_different_conditions] else strings[R.string.ui_enter_a_query_in_the_search_bar_above_you_can_also_select_lang],
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (state.isLoadingPage && resultIds.isEmpty()) {
            item(key = "initial-loader", span = { GridItemSpan(maxLineSpan) }) { LoadingRow(strings[R.string.ui_loading]) }
        }
        if (canRestoreResults) {
            item(key = "restore-results", span = { GridItemSpan(maxLineSpan) }) {
                TextButton(onClick = onEnsureResults, modifier = Modifier.fillMaxWidth()) { Text(strings[R.string.ui_reload_search_results]) }
            }
        }

        if (resultIds.isNotEmpty() || totalCount != null) {
            item(key = "result-count", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    totalCount?.let { strings[R.string.ui_search_results, (java.text.NumberFormat.getIntegerInstance().format(it))] } ?: strings[R.string.ui_search_results_total_unknown],
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(count = resultIds.size, key = { "gallery:${resultIds[it]}" }, contentType = { "gallery" }) { index ->
                val id = resultIds[index]
                val gallery = galleriesById[id]
                if ("online:$id" in state.libraryHiddenIds) {
                    TextButton(onClick = { onUnhideGallery(id) }) { Text("#$id · ${strings[R.string.ui_unhide]}") }
                } else if (gallery == null) {
                    WorkCard(title = "#$id", artist = "", metadata = strings[R.string.ui_loading],
                        tags = emptyList(), footer = "", grid = gridView, onClick = {}) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                } else {
                    val libraryBook = libraryBookByGalleryId[gallery.id]
                    GalleryCard(
                        gallery = gallery,
                        gridView = gridView,
                        read = gallery.id in state.viewedGalleryIds || libraryBook?.id in state.libraryReadIds || "online:$id" in state.libraryReadIds,
                        libraryBook = libraryBook,
                        favorite = libraryBook?.id?.let { it in state.libraryFavoriteIds } == true || "online:$id" in state.libraryFavoriteIds,
                        onToggleFavorite = { onToggleLibraryFavorite("online:$id") },
                        onClick = { onGalleryClick(gallery.id) },
                        onLongClick = { onGalleryLongClick(gallery.id) },
                        onSearchFacet = onSearchFacet,
                    )
                }
            }
        }

        if (resultIds.isNotEmpty() && (state.hasNextPage || state.isLoadingPage)) {
            item(key = "page-loader-${state.currentPage}", span = { GridItemSpan(maxLineSpan) }) {
                LaunchedEffect(state.currentPage, state.hasNextPage, state.isLoadingPage, state.isRestoringSearch, state.nextSearchPageError, listState.isScrollInProgress) {
                    if (listState.isScrollInProgress && state.hasNextPage && !state.isLoadingPage && !state.isRestoringSearch && state.nextSearchPageError == null) onLoadNextPage()
                }
                if (state.nextSearchPageError != null) {
                    TextButton(onClick = onLoadNextPage, modifier = Modifier.fillMaxWidth()) { Text(strings[R.string.ui_reload_next_page]) }
                } else LoadingRow(
                    if (state.isLoadingPage) strings[R.string.ui_loading_page, (state.currentPage + 1)]
                    else strings[R.string.ui_preparing_next_page],
                )
            }
        }
        }
        DynamicVerticalScrollbar(listState)
        state.searchResultWindow?.index?.takeIf { resultIds.isNotEmpty() }?.let { index ->
            SearchPageIndicator(listState, index, onClick = { pageDialog = true })
        }
        }
    }

    }
    }
    }
    if (pageDialog) AlertDialog(
        onDismissRequest = { pageDialog = false },
        title = { Text(strings[R.string.ui_go_to_page]) },
        text = { OutlinedTextField(value = pageInput, onValueChange = { pageInput = it.filter(Char::isDigit).take(10) }, label = { Text(strings[R.string.ui_page_number]) },
            placeholder = { Text(maxPage?.let { strings[R.string.ui_up_to_page, (java.text.NumberFormat.getIntegerInstance().format(it))] } ?: strings[R.string.ui_enter_target_page]) },
            supportingText = { Text(maxPage?.let { strings[R.string.ui_pages_1, (java.text.NumberFormat.getIntegerInstance().format(it))] } ?: strings[R.string.ui_page_number_of_1_or_higher]) },
            isError = pageInput.isNotEmpty() && !isSearchPageInRange(pageInput.toIntOrNull(), maxPage),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) },
        confirmButton = { TextButton(enabled = isSearchPageInRange(pageInput.toIntOrNull(), maxPage), onClick = { onJumpToPage(pageInput.toInt()); pageDialog = false }) { Text(strings[R.string.ui_go]) } },
        dismissButton = { TextButton(onClick = { pageDialog = false }) { Text(strings[R.string.ui_cancel]) } },
    )

    deleteLanguage?.let { language ->
        AlertDialog(
            onDismissRequest = { deleteLanguage = null },
            title = { Text(strings[R.string.ui_remove_language]) },
            text = { Text(strings[R.string.ui_remove_language_6332b0, (language.replaceFirstChar { it.uppercase() })]) },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveCustomLanguage(language)
                    deleteLanguage = null
                }) { Text(strings[R.string.ui_delete]) }
            },
            dismissButton = {
                TextButton(onClick = { deleteLanguage = null }) { Text(strings[R.string.ui_cancel]) }
            },
        )
    }
}

@Composable
internal fun GalleryCard(
    gallery: GallerySummary,
    gridView: Boolean = false,
    read: Boolean,
    libraryBook: LibraryBook?,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSearchFacet: (String) -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val request = remember(context, gallery.thumbnailUrl, gallery.id) {
        gallery.thumbnailUrl?.let { hitomiImageRequest(context, it, gallery.id, crossfade = false, preview = true) }
    }
    WorkCard(
        title = preferredLocalizedTitle(gallery.title),
        artist = gallery.artists.joinToString().ifBlank { strings[R.string.ui_no_artist_information] },
        metadata = listOfNotNull(gallery.language, gallery.pageCount.takeIf { it > 0 }?.let { strings[R.string.ui_pages, it] }).joinToString(" · "),
        tags = gallery.tags.map { tag -> CardTag(tagDisplayInfo(tag.name, tag.type)) { onSearchFacet(tagSearchFacet(tag.name, tag.type)) } },
        footer = gallery.loadError ?: formatGalleryPublishedDate(gallery.publishedDate)?.let { strings[R.string.ui_uploaded, it] }.orEmpty(),
        footerError = gallery.loadError != null,
        longClickLabel = strings[R.string.ui_open_in_new_tab], grid = gridView, onClick = onClick, onLongClick = onLongClick,
        border = galleryCardBorder(favorite = favorite),
        webtoon = gallery.webtoon == true,
    ) {
        Text("#${gallery.id}", style = MaterialTheme.typography.labelSmall)
        if (request != null) AsyncImage(request, strings[R.string.ui_thumbnail, preferredLocalizedTitle(gallery.title)], Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        LibraryThumbnailBadges(read, libraryBook?.isCloud, favorite, showFavorite = true,
            onToggleFavorite = onToggleFavorite, scale = if (gridView) .75f else .82f)
    }
}

fun hitomiImageRequest(
    context: Context,
    url: String,
    galleryId: Long,
    crossfade: Boolean = true,
    preview: Boolean = false,
): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .httpHeaders(
            NetworkHeaders.Builder()
                .set("Referer", "https://hitomi.la/reader/$galleryId.html")
                .build(),
        )
        .apply {
            if (preview) {
                size(360, 480)
                precision(Precision.INEXACT)
            }
        }
        .crossfade(crossfade)
        .build()

@Composable
fun LoadingRow(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.width(28.dp).height(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(label)
    }
}


internal fun visibleSearchPage(firstVisibleListIndex: Int, index: SearchResultIndex): Int =
    index.pageAt((firstVisibleListIndex - 2).coerceAtLeast(0))

@Composable
private fun BoxScope.SearchPageIndicator(listState: LazyGridState, index: SearchResultIndex, onClick: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val page by remember(listState, index) { derivedStateOf { visibleSearchPage(listState.firstVisibleItemIndex, index) } }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) visible = true
        else { delay(1200); visible = false }
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(120)), exit = fadeOut(tween(180)),
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 16.dp)) {
        Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.semantics { contentDescription = strings[R.string.ui_page_tap_to_jump, (page)] }) {
            Text(strings[R.string.ui_pages, (page)], Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun SearchSortMenu(query: String, enabled: Boolean, onSelect: (Boolean) -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    var expanded by remember { mutableStateOf(false) }
    val oldest = remember(query) { parseHitomiSearchOrder(query).oldest }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp)) {
            ActionIcon(ActionSymbol.SORT); Spacer(Modifier.width(4.dp)); Text(strings[R.string.ui_sort])
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ChoiceMenuItem(strings[R.string.ui_newest_first], !oldest, onClick = { expanded = false; onSelect(false) }, enabled = enabled)
            ChoiceMenuItem(strings[R.string.ui_oldest_first], oldest, onClick = { expanded = false; onSelect(true) }, enabled = enabled)
        }
    }
}
