package com.doujinzip.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.SubcomposeAsyncImage
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import com.doujinzip.android.R
import com.doujinzip.android.data.PresentationRecord
import com.doujinzip.android.model.*

@Composable
internal fun DetailImage(model: Any?, contentDescription: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val identity = (model as? ImageRequest)?.data ?: model
    var attempt by remember(identity) { mutableIntStateOf(0) }
    key(identity, attempt) {
        SubcomposeAsyncImage(model = model, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale,
            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) } },
            error = {
                LaunchedEffect(identity, attempt) {
                    if (identity != null && attempt < 3) {
                        delay(500L * (1 shl attempt))
                        attempt++
                    }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (identity != null && attempt < 3) CircularProgressIndicator(Modifier.size(28.dp))
                    else IconButton(onClick = { attempt = 0 }, modifier = Modifier.semantics { this.contentDescription = strings[R.string.ui_try_again] }) { ActionIcon(ActionSymbol.REFRESH) }
                }
            })
    }
}

@Composable
internal fun DetailHeader(title: String, metadata: LibraryMetadata, coverModel: Any?, pageCount: Int, information: String? = null,
    onSearchFacet: (String) -> Unit, onSearchLanguage: (String) -> Unit, options: (@Composable () -> Unit)? = null,
    webtoon: Boolean = false) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (coverModel != null) DetailImage(coverModel, title, Modifier.fillMaxWidth().height(390.dp))
        Row(verticalAlignment = Alignment.Top) {
            SelectionContainer(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            options?.invoke()
        }
        FacetSection(strings[R.string.ui_artist], metadata.artists, "artist", onSearchFacet)
        FacetSection(strings[R.string.ui_group], metadata.groups, "group", onSearchFacet)
        FacetSection(strings[R.string.ui_source_series], metadata.series, "series", onSearchFacet)
        FacetSection(strings[R.string.ui_character], metadata.characters, "character", onSearchFacet)
        metadata.galleryType?.let { FacetSection(strings[R.string.ui_type], listOf(it), "type", onSearchFacet) }
        metadata.language?.let { FacetSection(strings[R.string.ui_language], listOf(it), "language", onSearchLanguage, rawValue = true) }
        FacetSection(strings[R.string.ui_tags], metadata.tags, "tag", onSearchFacet)
        if (webtoon) TagPill(TagDisplayInfo(strings[R.string.ui_webtoon_tag], TagStyle.DEFAULT))
        Text(listOfNotNull(metadata.hitomiId?.let { "ID $it" }, strings[R.string.ui_pages, pageCount],
            formatGalleryPublishedDate(metadata.publishedDate)?.let { strings[R.string.ui_uploaded, it] }, information?.takeIf { it.isNotBlank() }).joinToString(" · "),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal data class DetailMenuAction(val label: String, val symbol: ActionSymbol, val onClick: () -> Unit)

@Composable
internal fun PresentationActions(value: GalleryPresentation, record: PresentationRecord?, sources: List<ReaderImageSource>,
    busy: Boolean, syncError: String?, onSave: (GalleryPresentation, Boolean) -> Unit, onCover: (GalleryPresentation, ReaderImageSource) -> Unit,
    onAcceptRemote: () -> Unit, actions: List<DetailMenuAction>, editable: Boolean = true,
    coverFromWorks: Boolean = false, sourceLabels: List<String> = emptyList(), metadataSeed: LibraryMetadata? = null, onResetCover: (() -> Unit)? = null) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var editor by rememberSaveable(value.key) { mutableStateOf(false) }
    var coverPicker by rememberSaveable(value.key) { mutableStateOf(false) }
    val coverLabel = strings[if (coverFromWorks) R.string.ui_cover_from_work else R.string.ui_cover_from_page]
    val latestValue by rememberUpdatedState(record?.value ?: value)
    val external = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onCover(latestValue, ReaderImageSource(it.toString())) }
    }
    Box {
        IconButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.semantics { contentDescription = strings[R.string.ui_detail_options] }) { ActionIcon(ActionSymbol.SETTINGS) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (editable) {
            DropdownMenuItem(text = { Text(strings[R.string.ui_edit_metadata]) }, leadingIcon = { ActionIcon(ActionSymbol.EDIT) }, onClick = { menu = false; editor = true })
            DropdownMenuItem(text = { Text(coverLabel) }, leadingIcon = { ActionIcon(ActionSymbol.PAGE) }, enabled = sources.isNotEmpty(), onClick = { menu = false; coverPicker = true })
            DropdownMenuItem(text = { Text(strings[R.string.ui_cover_from_image]) }, leadingIcon = { ActionIcon(ActionSymbol.FOLDER) }, onClick = { menu = false; external.launch(arrayOf("image/*")) })
            if (onResetCover != null) DropdownMenuItem(text = { Text(strings[R.string.ui_reset_cover]) }, leadingIcon = { ActionIcon(ActionSymbol.REFRESH) }, onClick = { menu = false; onResetCover() })
            if (actions.isNotEmpty()) HorizontalDivider()
            }
            actions.forEach { action -> DropdownMenuItem(text = { Text(action.label) }, leadingIcon = { ActionIcon(action.symbol) }, onClick = { menu = false; action.onClick() }) }
            if (editable && record?.conflict != null) {
                HorizontalDivider()
                DropdownMenuItem(text = { Text(strings[R.string.ui_metadata_conflict]) }, enabled = false, onClick = {})
                DropdownMenuItem(text = { Text(strings[R.string.ui_keep_phone_edit]) }, onClick = { menu = false; editor = true })
                DropdownMenuItem(text = { Text(strings[R.string.ui_use_pc_edit]) }, onClick = { menu = false; onAcceptRemote() })
            } else if (editable && record?.dirty == true) {
                HorizontalDivider()
                DropdownMenuItem(text = { Text(syncError ?: strings[R.string.ui_metadata_pending]) }, enabled = false, onClick = {})
            }
        }
    }
    if (editor) MetadataEditor(latestValue, conflict = record?.conflict, initialMetadata = metadataSeed, onDismiss = { editor = false }, onSave = {
        onSave(it, record?.conflict != null); editor = false
    })
    if (coverPicker) Dialog(onDismissRequest = { coverPicker = false }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.85f)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(coverLabel, Modifier.weight(1f))
                    TextButton(onClick = { coverPicker = false }) { Text(strings[R.string.ui_cancel]) }
                }
                LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(sources) { index, source ->
                        Column(Modifier.clickable { onCover(latestValue, source); coverPicker = false }) {
                            DetailImage(readerSourceRequest(context, source), sourceLabels.getOrNull(index) ?: strings[R.string.ui_page_preview, index + 1], Modifier.fillMaxWidth().height(140.dp), ContentScale.Crop)
                            Text(sourceLabels.getOrNull(index) ?: "${index + 1}", Modifier.align(Alignment.CenterHorizontally))
                        }
                    }
                }
            }
        }
    }
}

internal fun presentationCoverUri(context: android.content.Context, value: GalleryPresentation?): String? = value?.coverHash?.let {
    android.net.Uri.fromFile(com.doujinzip.android.data.PresentationCovers(context).file(it)).toString()
}

@Composable
internal fun MetadataEditor(value: GalleryPresentation, conflict: GalleryPresentation?, onDismiss: () -> Unit, onSave: (GalleryPresentation) -> Unit,
    initialMetadata: LibraryMetadata? = null) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val original = initialMetadata ?: value.metadata
    var fields by rememberSaveable(value.key) { mutableStateOf(listOf(value.title, original.artists.joinToString("\n"), original.groups.joinToString("\n"),
        original.series.joinToString("\n"), original.characters.joinToString("\n"), original.tags.joinToString("\n"), original.galleryType.orEmpty(), original.language.orEmpty())) }
    val labels = listOf(R.string.ui_title, R.string.ui_artist, R.string.ui_group, R.string.ui_source_series, R.string.ui_character, R.string.ui_tags, R.string.ui_type, R.string.ui_language)
    val edited = runCatching { value.copy(title = fields[0].trim(), metadata = original.copy(artists = parseEditedFacets(fields[1]), groups = parseEditedFacets(fields[2]),
        series = parseEditedFacets(fields[3]), characters = parseEditedFacets(fields[4]), tags = parseEditedFacets(fields[5]),
        galleryType = fields[6].trim().takeIf(String::isNotEmpty), language = fields[7].trim().takeIf(String::isNotEmpty))) }.getOrNull()
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).imePadding().padding(16.dp)) {
                Text(strings[R.string.ui_edit_metadata], style = MaterialTheme.typography.titleLarge)
                if (conflict != null) Text(strings[R.string.ui_metadata_conflict], color = MaterialTheme.colorScheme.error)
                Text(strings[R.string.ui_one_facet_per_line], style = MaterialTheme.typography.bodySmall)
                if (edited == null) Text(strings[R.string.ui_metadata_limits], color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(labels.size) { index -> OutlinedTextField(value = fields[index], onValueChange = { input ->
                        fields = fields.toMutableList().also { it[index] = input.take(if (index == 0) 500 else if (index < 6) 60_000 else 200) }
                    }, label = { Text(strings[labels[index]]) }, singleLine = index == 0 || index >= 6, modifier = Modifier.fillMaxWidth()) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(strings[R.string.ui_cancel]) }
                    Button(onClick = { edited?.let(onSave) }, enabled = edited != null) { Text(strings[R.string.ui_save]) }
                }
            }
        }
    }
}

internal fun parseEditedFacets(text: String): List<String> = text.lineSequence().map(String::trim).filter(String::isNotEmpty).distinct().toList()

@Composable
internal fun WorkPresentationActions(viewModel: MainViewModel, value: GalleryPresentation, sources: List<ReaderImageSource>, bookIds: Set<String>) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val state = viewModel.uiState
    val onlineId = value.metadata.hitomiId?.let { "online:$it" }
    val statusIds = bookIds + listOfNotNull(onlineId)
    val favorite = statusIds.any { it in state.libraryFavoriteIds }
    val read = statusIds.any { it in state.libraryReadIds } || value.metadata.hitomiId?.toLongOrNull() in state.viewedGalleryIds
    val hidden = statusIds.any { it in state.libraryHiddenIds }
    var confirmDelete by rememberSaveable(value.key) { mutableStateOf(false) }
    val actions = buildList {
        add(DetailMenuAction(strings[if (favorite) R.string.ui_remove_favorite else R.string.ui_add_favorite], ActionSymbol.STAR) {
            (onlineId ?: bookIds.firstOrNull())?.let(viewModel::toggleLibraryFavorite)
        })
        add(DetailMenuAction(strings[if (read) R.string.ui_mark_unread else R.string.ui_mark_read], ActionSymbol.READ) {
            if (read) viewModel.markLibraryBooksUnread(statusIds) else viewModel.markLibraryBooksRead(statusIds)
            if (bookIds.isEmpty()) value.metadata.hitomiId?.toLongOrNull()?.let { viewModel.markOnlineRead(it, !read) }
        })
        add(DetailMenuAction(strings[if (hidden) R.string.ui_unhide else R.string.ui_hide], ActionSymbol.HIDE) { viewModel.setLibraryBooksHidden(statusIds, !hidden) })
        if (bookIds.isNotEmpty()) add(DetailMenuAction(strings[R.string.ui_delete_gallery], ActionSymbol.DELETE) { confirmDelete = true })
    }
    PresentationActions(value, state.presentations[value.key], sources, state.isBusy, state.presentationSyncError,
        viewModel::saveGalleryPresentation, viewModel::changePresentationCover, { viewModel.acceptRemotePresentation(value.key) }, actions, editable = bookIds.isNotEmpty() && state.desktopLibraryReady, onResetCover = { viewModel.resetPresentationCover(value) })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(strings[R.string.ui_delete_gallery]) },
        text = { Text(strings[R.string.ui_delete_selected_items_desktop_originals_move_to_trash_and_are, bookIds.size]) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.deleteLibraryBooks(bookIds) }) { Text(strings[R.string.ui_delete]) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(strings[R.string.ui_cancel]) } })
}

private fun readerSourceRequest(context: android.content.Context, source: ReaderImageSource): Any = coil3.request.ImageRequest.Builder(context)
    .data(source.uri).apply {
        if (source.headers.isNotEmpty()) httpHeaders(coil3.network.NetworkHeaders.Builder().apply { source.headers.forEach { (name, value) -> set(name, value) } }.build())
    }.build()
