package com.doujinzip.android.ui

import com.doujinzip.android.model.ReaderPosition
import com.doujinzip.android.R

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.doujinzip.android.model.GallerySummary
import com.doujinzip.android.model.presentation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryDetailScreen(
    pageKey: String,
    initialPreviewPosition: ReaderPosition,
    onPreviewPositionChange: (ReaderPosition) -> Unit,
    gallery: GallerySummary?,
    state: MainUiState,
    onLoadPreview: (Long) -> Unit,
    onBack: () -> Unit,
    onOpenReader: (Int) -> Unit,
    onSearchFacet: (String) -> Unit,
    onSearchLanguage: (String) -> Unit,
    onDownload: (GallerySummary) -> Unit,
    options: (@Composable () -> Unit)? = null,
    webtoon: Boolean = false,
    tabBar: (@Composable () -> Unit)? = null,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (tabBar != null) tabBar() else {
                TopAppBar(
                    title = { Text(strings[R.string.ui_gallery_details]) },
                    navigationIcon = { TextButton(onClick = onBack) { Text("<") } },
                )
            }
        },
    ) { scaffoldPadding ->
        if (gallery == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
                contentAlignment = Alignment.Center,
            ) { Text(strings[R.string.ui_gallery_not_found]) }
            return@Scaffold
        }

        LaunchedEffect(gallery.id) { onLoadPreview(gallery.id) }
        val context = LocalContext.current
        val previewPages = state.readerPagesByGalleryId[gallery.id].orEmpty()

        val gridState = previewGridState(pageKey, initialPreviewPosition, previewPages.size, onPreviewPositionChange)
        Box(Modifier.fillMaxSize().padding(scaffoldPadding)) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        DetailHeader(preferredLocalizedTitle(gallery.title), gallery.presentation().metadata,
                            gallery.thumbnailUrl?.let { hitomiImageRequest(context, it, gallery.id, crossfade = false) },
                            gallery.pageCount, onSearchFacet = onSearchFacet, onSearchLanguage = onSearchLanguage, options = options, webtoon = webtoon)
                        val pendingRequest = state.downloadRequests.any { it.galleryId == gallery.id && !it.isFinished }
                        val queuedDownload = state.downloadQueue.firstOrNull {
                            it.galleryId == gallery.id
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { onOpenReader(0) }, modifier = Modifier.weight(1f)) {
                                Text(strings[R.string.ui_read_fullscreen])
                            }
                            OutlinedButton(
                                onClick = { onDownload(gallery) },
                                enabled = gallery.id !in state.downloadingGalleryIds && queuedDownload == null && !pendingRequest,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    when {
                                        gallery.id in state.downloadingGalleryIds -> strings[R.string.ui_requesting]
                                        pendingRequest -> strings[R.string.ui_already_in_downloads]
                                        queuedDownload != null -> strings[R.string.ui_already_in_download_queue]
                                        else -> strings[R.string.ui_download]
                                    },
                                )
                            }
                        }
                        Text(strings[R.string.ui_gallery_preview], style = MaterialTheme.typography.titleMedium)
                        when {
                            gallery.id in state.readerLoadingGalleryIds -> {
                                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            state.readerErrorsByGalleryId[gallery.id] != null -> {
                                Text(state.readerErrorsByGalleryId.getValue(gallery.id), color = MaterialTheme.colorScheme.error)
                            }
                            previewPages.isEmpty() -> {
                                Text(strings[R.string.ui_no_preview_images])
                            }
                        }
                    }
                }
                itemsIndexed(previewPages, key = { index, url -> "$index-$url" }) { index, url ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onPreviewPositionChange(ReaderPosition(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)); onOpenReader(index) }
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelSmall)
                        DetailImage(
                            model = hitomiImageRequest(
                                context = context,
                                url = url,
                                galleryId = gallery.id,
                                crossfade = false,
                                preview = true,
                            ),
                            contentDescription = strings[R.string.ui_page_preview, (index + 1)],
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            DynamicVerticalScrollbar(gridState)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun CopyableFacetChip(
    text: String,
    clipboardText: String = text,
    tagStyle: TagStyle = TagStyle.DEFAULT,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val (containerColor, contentColor) = tagColors(tagStyle)
    Surface(
        modifier = Modifier.combinedClickable(
            enabled = enabled,
            onClick = onClick,
            onLongClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("gallery metadata", clipboardText))
                Toast.makeText(context, strings[R.string.ui_copied_to_clipboard], Toast.LENGTH_SHORT).show()
            },
        ),
        shape = RoundedCornerShape(if (tagStyle == TagStyle.DEFAULT) 8.dp else 50.dp),
        color = containerColor,
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
        border = if (tagStyle == TagStyle.DEFAULT) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

private fun String.isGalleryFacetSearchable(): Boolean =
    trim().isNotEmpty() && lowercase() !in setOf("n/a", "na", "unknown", "정보 없음")
