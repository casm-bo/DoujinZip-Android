package com.doujinzip.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.doujinzip.android.R
import com.doujinzip.android.model.*
import org.json.JSONObject
import kotlinx.coroutines.flow.filter

@Composable
internal fun SeriesDetailContent(name: String, books: List<LibraryBook>, state: MainUiState, onList: () -> Unit, onEdit: () -> Unit,
    onOpenBook: (String) -> Unit, onReadBook: (String, Int) -> Unit, onRename: () -> Unit, onFavorite: () -> Unit,
    onRead: (Set<String>) -> Unit, onUnread: (Set<String>) -> Unit, onHidden: (Set<String>, Boolean) -> Unit, onDelete: () -> Unit,
    onSearchFacet: (String) -> Unit, onSearchLanguage: (String) -> Unit, onLoadPreview: (String) -> Unit = {},
    pageKey: String = name, initialPosition: ReaderPosition = ReaderPosition(0), onPositionChange: (ReaderPosition) -> Unit = {},
    onSavePresentation: (GalleryPresentation, Boolean) -> Unit = { _, _ -> },
    onCover: (GalleryPresentation, ReaderImageSource) -> Unit = { _, _ -> }, onAcceptRemote: (String) -> Unit = {}) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val sorted = remember(books, state.customSeriesByBookId) { books.sortedWith(compareBy<LibraryBook> { state.customSeriesByBookId[it.id]?.order ?: Int.MAX_VALUE }.thenBy { it.id }) }
    val key = remember(name) { seriesPresentationKey(name) }
    val coverKey = remember(name) { seriesCoverPresentationKey(name) }
    val metadataRecord = state.presentations[key]
    val coverRecord = state.presentations[coverKey]
    val aggregate = remember(sorted) { unionSeriesMetadata(sorted) }
    val presentation = metadataRecord?.value ?: remember(key, name) { GalleryPresentation(key, name, LibraryMetadata()) }
    val record = remember(metadataRecord, coverRecord, presentation) {
        metadataRecord?.copy(dirty = metadataRecord.dirty || coverRecord?.dirty == true)
            ?: com.doujinzip.android.data.PresentationRecord(presentation, dirty = coverRecord?.dirty == true)
    }
    val metadata = metadataRecord?.value?.metadata ?: aggregate
    val coverValue = coverRecord?.value ?: remember(coverKey, name) { GalleryPresentation(coverKey, name, LibraryMetadata()) }
    val coverUri = presentationCoverUri(context, coverRecord?.value) ?: presentationCoverUri(context, presentation)
    val coverBooks = remember(sorted) { sorted.filter { it.coverUri.isNotBlank() } }
    val sources = remember(coverBooks) { coverBooks.map { ReaderImageSource(it.coverUri,
        if (it.isCloud && it.cloudToken != null) mapOf("Authorization" to "Bearer ${it.cloudToken}") else emptyMap()) } }
    val ids = sorted.mapTo(linkedSetOf()) { it.id }
    val favorite = name in state.libraryFavoriteSeriesNames
    val read = ids.isNotEmpty() && ids.all { it in state.libraryReadIds }
    val hidden = ids.isNotEmpty() && ids.all { it in state.libraryHiddenIds }
    var delete by rememberSaveable(name) { mutableStateOf(false) }
    val list = remember(pageKey) { LazyListState(initialPosition.page, initialPosition.offset) }
    val savePosition by rememberUpdatedState(onPositionChange)
    fun persistPosition() { if (books.isNotEmpty()) savePosition(ReaderPosition(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)) }
    LaunchedEffect(pageKey, list, books.isNotEmpty()) {
        snapshotFlow { list.isScrollInProgress }.filter { !it }.collect { persistPosition() }
    }
    DisposableEffect(pageKey, list, books.isNotEmpty()) { onDispose { persistPosition() } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                DetailHeader(presentation.title, metadata, coverUri?.let { libraryImageRequest(context, it, null) } ?: coverBooks.firstOrNull()?.let { libraryImageRequest(context, it.coverUri, it.cloudToken) },
                    sorted.sumOf { it.pages.size.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), information = strings[R.string.ui_series_episode_count, sorted.size],
                    onSearchFacet = onSearchFacet, onSearchLanguage = onSearchLanguage, webtoon = sorted.any { it.webtoon == true }, options = {
                        PresentationActions(presentation, record, sources, state.isBusy, state.presentationSyncError,
                            onSavePresentation, { _, source -> onCover(coverValue, source) }, { onAcceptRemote(key) },
                                (if (coverRecord?.conflict != null) listOf(
                                    DetailMenuAction("${strings[R.string.ui_cover_from_work]} · ${strings[R.string.ui_keep_phone_edit]}", ActionSymbol.EDIT) { onSavePresentation(coverRecord.value, true) },
                                    DetailMenuAction("${strings[R.string.ui_cover_from_work]} · ${strings[R.string.ui_use_pc_edit]}", ActionSymbol.PC) { onAcceptRemote(coverKey) }) else emptyList()) +
                                listOf(DetailMenuAction(strings[R.string.ui_series_options_edit], ActionSymbol.SORT, onEdit),
                                    DetailMenuAction(strings[R.string.ui_rename_series], ActionSymbol.EDIT, onRename),
                                    DetailMenuAction(strings[if (favorite) R.string.ui_remove_favorite else R.string.ui_add_favorite], ActionSymbol.STAR, onFavorite),
                                    DetailMenuAction(strings[if (read) R.string.ui_mark_unread else R.string.ui_mark_read], ActionSymbol.READ) { if (read) onUnread(ids) else onRead(ids) },
                                    DetailMenuAction(strings[if (hidden) R.string.ui_unhide else R.string.ui_hide], ActionSymbol.HIDE) { onHidden(ids, !hidden) },
                                    DetailMenuAction(strings[R.string.ui_delete_series], ActionSymbol.DELETE) { delete = true }),
                            coverFromWorks = true, sourceLabels = coverBooks.map { it.title }, metadataSeed = if (metadataRecord == null) aggregate else null)
                    })
            }
            item {
                val lastRead = state.recentHistory.firstNotNullOfOrNull { entry -> entry.page?.let { page ->
                    entry.findBook(sorted)?.let { it to page }
                } } ?: sorted.firstOrNull { (state.libraryProgress[it.id] ?: 0) > 0 }?.let { it to state.libraryProgress.getValue(it.id) }
                lastRead?.let { (book, page) ->
                    Button(onClick = { onReadBook(book.id, page.coerceIn(0, (book.pages.size - 1).coerceAtLeast(0))) }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        ActionIcon(ActionSymbol.PLAY); Text(strings[R.string.ui_continue_reading], Modifier.padding(start = 6.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { sorted.firstOrNull()?.let { onReadBook(it.id, 0) } }, enabled = sorted.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(strings[R.string.ui_read_from_beginning]) }
                    OutlinedButton(onClick = onList, modifier = Modifier.weight(1f)) { ActionIcon(ActionSymbol.LIST); Text(strings[R.string.ui_list], Modifier.padding(start = 6.dp)) }
                }
            }
            items(sorted.chunked(3), key = { it.first().id }) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { book -> Column(Modifier.weight(1f).clickable { onOpenBook(book.id) }) {
                        DetailImage(libraryImageRequest(context, book.coverUri, book.cloudToken), book.title, Modifier.fillMaxWidth().height(150.dp))
                        Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                    } }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            item { Text(strings[R.string.ui_gallery_preview], style = MaterialTheme.typography.titleMedium) }
            sorted.forEach { book ->
                item(key = "preview:${book.id}") {
                    LaunchedEffect(book.id) { onLoadPreview(book.id) }
                    Text(book.title, style = MaterialTheme.typography.titleSmall)
                    state.seriesPreviewErrors[book.id]?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { onLoadPreview(book.id) }) { ActionIcon(ActionSymbol.REFRESH); Text(strings[R.string.ui_try_again]) }
                    }
                }
                itemsIndexed(book.pages.chunked(3), key = { index, _ -> "preview:${book.id}:$index" }) { rowIndex, row ->
                    LaunchedEffect(book.id) { if (row.any { it.uri.isBlank() }) onLoadPreview(book.id) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEachIndexed { column, page -> Box(Modifier.weight(1f).height(160.dp).semantics { contentDescription = page.name }.clickable(enabled = page.uri.isNotBlank()) {
                            onReadBook(book.id, rowIndex * 3 + column)
                        }, contentAlignment = Alignment.Center) {
                            if (page.uri.isBlank()) CircularProgressIndicator(Modifier.size(28.dp))
                            else DetailImage(libraryImageRequest(context, page.uri, book.cloudToken), null, Modifier.fillMaxSize())
                        } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        DynamicVerticalScrollbar(list)
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text(strings[R.string.ui_delete_series]) },
        text = { Text(strings[R.string.ui_delete_selected_series_galleries_will_be_kept, 1]) },
        confirmButton = { TextButton(onClick = { delete = false; onDelete() }) { Text(strings[R.string.ui_delete]) } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text(strings[R.string.ui_cancel]) } })
}

@Composable
internal fun SeriesOptionsEditor(name: String, books: List<LibraryBook>, assignments: Map<String, CustomSeriesAssignment>, contentPadding: PaddingValues,
    onSave: (Map<String, CustomSeriesAssignment>, List<String>) -> Boolean, onDone: () -> Unit, onCancel: () -> Unit,
    displayTitle: String = name) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val preferences = remember(context) { com.doujinzip.android.security.LibraryPreferenceStore(context) }
    var sort by remember { mutableStateOf(preferences.loadSeriesEpisodeSort()) }
    val keyboard = LocalSoftwareKeyboardController.current
    val baselineJson = rememberSaveable(name) { JSONObject().apply { assignments.forEach { (id, entry) ->
        put(id, JSONObject().put("name", entry.name).put("order", entry.order).put("modifiedAt", entry.modifiedAt))
    } }.toString() }
    val baseline = remember(baselineJson) { JSONObject(baselineJson).let { json -> json.keys().asSequence().associateWith { id ->
        val entry = json.getJSONObject(id); CustomSeriesAssignment(entry.getString("name"), entry.getInt("order"), entry.getLong("modifiedAt"))
    } } }
    var order by rememberSaveable(name) { mutableStateOf(baseline.entries.filter { it.value.name == name }.sortedWith(compareBy({ it.value.order }, { it.key })).map { it.key }) }
    var selected by rememberSaveable(name) { mutableStateOf(emptySet<String>()) }
    var adding by rememberSaveable(name) { mutableStateOf(false) }
    var chosen by rememberSaveable(name) { mutableStateOf(emptySet<String>()) }
    var query by rememberSaveable(name) { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val byId = remember(books) { books.associateBy { it.id } }
    BackHandler { if (adding) { adding = false; chosen = emptySet() } else onCancel() }
    Column(Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding)
        .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).imePadding()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(displayTitle, Modifier.weight(1f).padding(end = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { if (adding) { adding = false; chosen = emptySet() } else onCancel() }) {
                        Text(strings[if (adding) R.string.ui_return else R.string.ui_cancel])
                    }
                    Button(onClick = {
                        if (adding) { order = order + chosen.filter { it !in order }; chosen = emptySet(); adding = false }
                        else { failed = !onSave(baseline, order); if (!failed) onDone() }
                    }, enabled = if (adding) chosen.isNotEmpty() else order.all { it in byId }) {
                        Text(strings[if (adding) R.string.ui_confirm else R.string.ui_save])
                    }
                }
                if (!adding) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { query = ""; adding = true }, modifier = Modifier.weight(1f)) {
                        ActionIcon(ActionSymbol.ADD); Text(strings[R.string.ui_add_episodes], Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(onClick = { order = order.filterNot { it in selected }; selected = emptySet() }, enabled = selected.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        ActionIcon(ActionSymbol.DELETE); Text(strings[R.string.ui_exclude_episodes], Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        if (failed) Text(strings[R.string.ui_series_edit_conflict], Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
        if (adding) {
            val candidates = remember(books, order, query, assignments, sort) {
                sortLibraryBooks(filterLibraryBooksByQuery(books.filter { it.id !in order }, query, assignments), sort)
            }
            Column(Modifier.weight(1f)) {
                OutlinedTextField(query, { query = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    placeholder = { Text(strings[R.string.ui_search_gallery]) },
                    leadingIcon = { ActionIcon(ActionSymbol.SEARCH) },
                    trailingIcon = if (query.isEmpty()) null else ({ IconButton(onClick = { query = "" },
                        modifier = Modifier.semantics { contentDescription = strings[R.string.ui_clear_search] }) {
                        ActionIcon(ActionSymbol.CLEAR)
                    } }),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                    LibrarySortMenu(sort, { sort = it; preferences.saveSeriesEpisodeSort(it) })
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(candidates, key = { it.id }) { book ->
                LibraryWorkCard(book, 0, grid = false, favorite = false, read = false,
                    selected = book.id in chosen, selectionMode = true,
                    onOpen = { chosen = if (book.id in chosen) chosen - book.id else chosen + book.id }, onLongPress = {},
                    onToggleFavorite = {}, badgeScale = .82f)
            }
                }
            }
        } else if (order.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(strings[R.string.ui_empty_series_keeps_files], Modifier.padding(16.dp)) }
        else LibrarySeriesOrderEditor(order.mapNotNull(byId::get), order.mapIndexed { index, id -> id to CustomSeriesAssignment(name, index) }.toMap(),
            onReorder = { order = it }, selectedIds = selected, onSelect = { selected = if (it in selected) selected - it else selected + it })
    }
}
