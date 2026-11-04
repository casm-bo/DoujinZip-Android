package com.doujinzip.android.ui

import com.doujinzip.android.R
import com.doujinzip.android.model.ReaderImageSource
import com.doujinzip.android.model.ReaderPosition
import com.doujinzip.android.model.ImageDimensions
import com.doujinzip.android.model.ViewerReadingMode
import com.doujinzip.android.model.detectReadingMode
import com.doujinzip.android.model.webtoonSampleIndices
import com.doujinzip.android.data.ReaderImages
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.doujinzip.android.model.ViewerPageTurnMode
import com.doujinzip.android.model.ViewerPreferences
import com.doujinzip.android.model.ViewerReadingDirection
import com.doujinzip.android.model.ViewerScale
import com.doujinzip.android.model.ViewerTapAction
import com.doujinzip.android.model.ViewerTapZones
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class ReaderPageModel(val key: String, val model: Any, val source: ReaderImageSource)

internal fun initialThumbnailVisibility(preferences: ViewerPreferences): Boolean =
    !preferences.hideThumbnails

@Composable
internal fun UnifiedReader(
    readerKey: String,
    workKey: String,
    initialOffset: Int = 0,
    title: String,
    pages: List<ReaderPageModel>,
    initialPage: Int,
    preferences: ViewerPreferences,
    onBack: () -> Unit,
    onProgress: (ReaderPosition) -> Unit,
    onPreferencesChange: (ViewerPreferences) -> Unit,
    favorite: Boolean? = null,
    onToggleFavorite: (() -> Unit)? = null,
    isSeries: Boolean = false,
    previousBookTitle: String? = null,
    onOpenPreviousBook: (() -> Unit)? = null,
    nextBookTitle: String? = null,
    onOpenNextBook: (() -> Unit)? = null,
    onReadingModeResolved: (ViewerReadingMode) -> Unit = {},
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    ReaderKeepScreenOn(preferences.keepScreenOn)

    val context = LocalContext.current
    val modeStore = remember(context) { com.doujinzip.android.security.LibraryPreferenceStore(context) }
    var overrideName by rememberSaveable(workKey) { mutableStateOf(modeStore.loadReaderMode(workKey)?.name.orEmpty()) }
    val selectedMode = ViewerReadingMode.entries.firstOrNull { it.name == overrideName } ?: preferences.readingMode
    var detectedMode by remember(readerKey) { mutableStateOf<ViewerReadingMode?>(null) }
    var detectedSizes by remember(readerKey) { mutableStateOf(emptyMap<Int, ImageDimensions>()) }
    var currentPage by rememberSaveable(readerKey) { mutableIntStateOf(initialPage.coerceIn(pages.indices)) }
    var currentOffset by rememberSaveable(readerKey) { mutableIntStateOf(initialOffset) }
    var webtoonJump by remember(readerKey) { mutableStateOf<Int?>(null) }
    fun selectMode(mode: ViewerReadingMode?) {
        overrideName = mode?.name.orEmpty(); modeStore.saveReaderMode(workKey, mode)
    }
    LaunchedEffect(readerKey, selectedMode) {
        if (selectedMode == ViewerReadingMode.AUTO && detectedMode == null) {
            val repository = ReaderImages(context)
            val sizes = coroutineScope { webtoonSampleIndices(pages.size).map { index -> async {
                val size = try { repository.probe(pages[index].source) }
                    catch (error: kotlinx.coroutines.CancellationException) { throw error }
                    catch (_: Exception) { null }
                index to size
            } }.awaitAll() }.mapNotNull { (index, size) -> size?.let { index to it } }.toMap()
            detectedSizes = sizes
            detectedMode = detectReadingMode(sizes.values.toList())
        }
    }
    val actualMode = if (selectedMode == ViewerReadingMode.AUTO) detectedMode else selectedMode
    val modeCallback by rememberUpdatedState(onReadingModeResolved)
    LaunchedEffect(workKey, actualMode) { actualMode?.let(modeCallback) }
    val pagerState = androidx.compose.runtime.key(readerKey) {
        rememberPagerState(
            initialPage = currentPage,
            pageCount = { pages.size },
        )
    }
    val scope = rememberCoroutineScope()
    var controlsVisible by remember(readerKey) { mutableStateOf(true) }
    var thumbnailsVisible by remember(readerKey) {
        mutableStateOf(initialThumbnailVisibility(preferences))
    }
    var settingsVisible by remember(readerKey) { mutableStateOf(false) }
    var previousBookDialog by remember(readerKey) { mutableStateOf(false) }
    var nextBookDialog by remember(readerKey) { mutableStateOf(false) }
    var pagerReady by remember(readerKey) { mutableStateOf(false) }
    LaunchedEffect(actualMode) {
        pagerReady = false
        if (actualMode == ViewerReadingMode.PAGE && pagerState.currentPage != currentPage) pagerState.scrollToPage(currentPage)
        pagerReady = actualMode == ViewerReadingMode.PAGE
    }
    LaunchedEffect(pagerState.currentPage, actualMode, pagerReady) {
        if (actualMode == ViewerReadingMode.PAGE && pagerReady) {
            currentPage = pagerState.currentPage; currentOffset = 0
            onProgress(ReaderPosition(currentPage))
        }
    }
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(READER_CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(preferences.hideThumbnails) {
        thumbnailsVisible = initialThumbnailVisibility(preferences)
    }
    BackHandler(enabled = settingsVisible) { settingsVisible = false }

    fun moveTo(page: Int) {
        when {
            page in pages.indices -> if (actualMode == ViewerReadingMode.WEBTOON) webtoonJump = page else scope.launch { pagerState.animateScrollToPage(page) }
            page < 0 && previousBookTitle != null && onOpenPreviousBook != null -> previousBookDialog = true
            page >= pages.size && nextBookTitle != null && onOpenNextBook != null -> nextBookDialog = true
        }
    }

    fun handleTap(action: ViewerTapAction) {
        when (action) {
            ViewerTapAction.PREVIOUS_PAGE -> if (preferences.pageTurnMode != ViewerPageTurnMode.SWIPE_ONLY) {
                moveTo(pagerState.currentPage - 1)
            }
            ViewerTapAction.NEXT_PAGE -> if (preferences.pageTurnMode != ViewerPageTurnMode.SWIPE_ONLY) {
                moveTo(pagerState.currentPage + 1)
            }
            ViewerTapAction.TOGGLE_CONTROLS -> controlsVisible = !controlsVisible
            ViewerTapAction.NONE -> Unit
        }
    }

    fun builtInTapAction(column: Int): ViewerTapAction = when (column) {
        0 -> if (preferences.readingDirection == ViewerReadingDirection.LEFT_TO_RIGHT) {
            ViewerTapAction.PREVIOUS_PAGE
        } else {
            ViewerTapAction.NEXT_PAGE
        }
        2 -> if (preferences.readingDirection == ViewerReadingDirection.LEFT_TO_RIGHT) {
            ViewerTapAction.NEXT_PAGE
        } else {
            ViewerTapAction.PREVIOUS_PAGE
        }
        else -> ViewerTapAction.TOGGLE_CONTROLS
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (actualMode) {
            null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            ViewerReadingMode.WEBTOON -> WebtoonReader(readerKey, pages, ReaderPosition(currentPage, currentOffset), detectedSizes,
                webtoonJump, { webtoonJump = null }, { currentPage = it },
                { position -> currentPage = position.page; currentOffset = position.offset; onProgress(position) },
                { controlsVisible = !controlsVisible }, { selectMode(ViewerReadingMode.PAGE) },
                if (nextBookTitle != null && onOpenNextBook != null) ({ nextBookDialog = true }) else null)
            else -> {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().testTag("reader-pager"),
            beyondViewportPageCount = minOf(2, pages.lastIndex),
            key = { pages[it].key },
            reverseLayout = preferences.readingDirection == ViewerReadingDirection.RIGHT_TO_LEFT,
            userScrollEnabled = preferences.pageTurnMode != ViewerPageTurnMode.TAP_ONLY,
        ) { page ->
            UnifiedZoomableImage(
                model = pages[page].model,
                contentDescription = strings[R.string.ui_pages, (page + 1)],
                contentScale = if (preferences.scale == ViewerScale.FIT_WIDTH) {
                    ContentScale.FillWidth
                } else {
                    ContentScale.Fit
                },
                onTap = { row, column ->
                    handleTap(
                        if (preferences.customTapZonesEnabled) {
                            preferences.tapZones.actionAt(row * 3 + column)
                        } else {
                            builtInTapAction(column)
                        },
                    )
                },
            )
        }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        ) {
            ReaderTopBar(
                title = title,
                pageNumber = if (preferences.showPageNumber) "${currentPage + 1} / ${pages.size}" else null,
                isSeries = isSeries || previousBookTitle != null || nextBookTitle != null,
                previousBookTitle = previousBookTitle,
                nextBookTitle = nextBookTitle,
                onPrevious = onOpenPreviousBook?.let { openPrevious ->
                    { if (currentPage == 0) previousBookDialog = true else openPrevious() }
                },
                onNext = onOpenNextBook,
                favorite = favorite,
                onBack = onBack,
                onToggleFavorite = onToggleFavorite,
                onSettings = { settingsVisible = true },
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && thumbnailsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(280, easing = FastOutSlowInEasing),
            ) + fadeIn(tween(180)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(240, easing = FastOutSlowInEasing),
            ) + fadeOut(tween(160)),
        ) {
            ReaderThumbnailBar(
                pages = pages,
                currentPage = currentPage,
                onPageClick =(::moveTo),
                onSwipeDown = { thumbnailsVisible = false },
            )
        }

        if (controlsVisible && !thumbnailsVisible) {
            ReaderBottomSwipeHandle(
                onSwipeUp = {
                    thumbnailsVisible = true
                    controlsVisible = true
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }


    }

    if (settingsVisible) {
        UnifiedViewerSettingsDialog(
            preferences = preferences.copy(readingMode = selectedMode),
            onChange = { value ->
                if (value.readingMode != selectedMode) selectMode(value.readingMode)
                onPreferencesChange(value.copy(readingMode = preferences.readingMode))
            },
            onResetMode = { selectMode(null) },
            onDismiss = { settingsVisible = false },
        )
    }
    if (previousBookDialog && previousBookTitle != null && onOpenPreviousBook != null) {
        AlertDialog(
            onDismissRequest = { previousBookDialog = false },
            title = { Text(strings[R.string.ui_previous_chapter]) },
            text = { Text(strings[R.string.ui_continue_with, previousBookTitle]) },
            confirmButton = {
                TextButton(onClick = {
                    previousBookDialog = false
                    onOpenPreviousBook()
                }) { Text(strings[R.string.ui_open]) }
            },
            dismissButton = { TextButton(onClick = { previousBookDialog = false }) { Text(strings[R.string.ui_cancel]) } },
        )
    }
    if (nextBookDialog && nextBookTitle != null && onOpenNextBook != null) {
        AlertDialog(
            onDismissRequest = { nextBookDialog = false },
            title = { Text(strings[R.string.ui_next_book]) },
            text = { Text(strings[R.string.ui_continue_with, (nextBookTitle)]) },
            confirmButton = {
                TextButton(onClick = {
                    nextBookDialog = false
                    onOpenNextBook()
                }) { Text(strings[R.string.ui_open]) }
            },
            dismissButton = { TextButton(onClick = { nextBookDialog = false }) { Text(strings[R.string.ui_cancel]) } },
        )
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    pageNumber: String?,
    isSeries: Boolean,
    previousBookTitle: String?,
    nextBookTitle: String?,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    favorite: Boolean?,
    onBack: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    onSettings: () -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.84f))) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹", color = Color.White) }
            Text(title, modifier = Modifier.weight(1f), color = Color.White,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (favorite != null && onToggleFavorite != null) {
                TextButton(onClick = onToggleFavorite) { Text(if (favorite) "♥" else "♡", color = Color.White) }
            }
            TextButton(onClick = onSettings) { Text(strings[R.string.ui_settings], color = Color.White) }
        }
        if (isSeries || pageNumber != null) {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (isSeries) {
                    TextButton(
                        onClick = { onPrevious?.invoke() },
                        enabled = previousBookTitle != null && onPrevious != null,
                        modifier = Modifier.weight(1f).testTag("reader-previous-episode"),
                    ) { Text("‹ ${strings[R.string.ui_previous_chapter]}", color = if (previousBookTitle != null) Color.White else Color.Gray) }
                } else androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                if (pageNumber != null) Text(pageNumber, color = Color.White,
                    modifier = Modifier.testTag("reader-page-number").padding(vertical = 8.dp))
                if (isSeries) {
                    TextButton(
                        onClick = { onNext?.invoke() },
                        enabled = nextBookTitle != null && onNext != null,
                        modifier = Modifier.weight(1f).testTag("reader-next-episode"),
                    ) { Text("${strings[R.string.ui_next_chapter]} ›", color = if (nextBookTitle != null) Color.White else Color.Gray) }
                } else androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReaderThumbnailBar(
    pages: List<ReaderPageModel>,
    currentPage: Int,
    onPageClick: (Int) -> Unit,
    onSwipeDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val listState = rememberLazyListState()
    LaunchedEffect(currentPage) {
        if (currentPage in pages.indices) listState.animateScrollToItem(currentPage)
    }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .height(READER_THUMBNAIL_BAR_HEIGHT)
            .background(Color.Black.copy(alpha = 0.78f))
            .pointerInput(onSwipeDown) {
                val threshold = 48.dp.toPx()
                var draggedY = 0f
                detectVerticalDragGestures(
                    onDragStart = { draggedY = 0f },
                    onVerticalDrag = { change, amount ->
                        draggedY += amount
                        if (draggedY > threshold) change.consume()
                    },
                    onDragEnd = { if (draggedY > threshold) onSwipeDown() },
                    onDragCancel = { draggedY = 0f },
                )
            },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(pages, key = { _, page -> page.key }) { index, page ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = page.model,
                    contentDescription = strings[R.string.ui_go_to_page_abf841, (index + 1)],
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 60.dp, height = 84.dp)
                        .border(
                            width = if (index == currentPage) 3.dp else 1.dp,
                            color = if (index == currentPage) MaterialTheme.colorScheme.primary else Color.Gray,
                        )
                        .clickable { onPageClick(index) },
                )
                Text("${index + 1}", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private val READER_THUMBNAIL_BAR_HEIGHT = 116.dp
private const val READER_CONTROLS_AUTO_HIDE_MS = 3_000L

@Composable
private fun ReaderBottomSwipeHandle(
    onSwipeUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(44.dp)
            .pointerInput(onSwipeUp) {
                val threshold = 48.dp.toPx()
                var draggedY = 0f
                detectVerticalDragGestures(
                    onDragStart = { draggedY = 0f },
                    onVerticalDrag = { change, amount ->
                        draggedY += amount
                        if (draggedY < -threshold) change.consume()
                    },
                    onDragEnd = { if (draggedY < -threshold) onSwipeUp() },
                    onDragCancel = { draggedY = 0f },
                )
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier.padding(bottom = 8.dp).size(width = 52.dp, height = 4.dp)
                .background(Color.White),
        )
    }
}

@Composable
private fun UnifiedZoomableImage(
    model: Any,
    contentDescription: String,
    contentScale: ContentScale,
    onTap: (row: Int, column: Int) -> Unit,
) {
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { _, zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        offset = if (nextScale > 1f) offset + panChange else Offset.Zero
    }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = Modifier.fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .pointerInput(model, scale) {
                detectTapGestures(
                    onTap = { tap ->
                        if (scale == 1f && size.width > 0 && size.height > 0) {
                            val column = (tap.x / (size.width / 3f)).toInt().coerceIn(0, 2)
                            val row = (tap.y / (size.height / 3f)).toInt().coerceIn(0, 2)
                            onTap(row, column)
                        }
                    },
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        offset = Offset.Zero
                    },
                )
            }
            .transformable(transformState, enabled = scale > 1f),
        contentScale = contentScale,
    )
}

@Composable
private fun UnifiedViewerSettingsDialog(
    preferences: ViewerPreferences,
    onChange: (ViewerPreferences) -> Unit,
    onResetMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings[R.string.ui_reader_settings]) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = onResetMode) { Text(strings[R.string.ui_use_app_reader_mode]) }
                ViewerSettingsContent(preferences = preferences, onChange = onChange)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings[R.string.ui_completed]) } },
    )
}

@Composable
internal fun ViewerSettingsContent(
    preferences: ViewerPreferences,
    onChange: (ViewerPreferences) -> Unit,
    modifier: Modifier = Modifier,
    section: Int? = null,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    var tapEditorVisible by remember { mutableStateOf(false) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (section == null || section == 0) {
        SettingsHeading(strings[R.string.ui_reading_mode])
        ViewerReadingMode.entries.forEach { mode ->
            SettingsRadio(strings[when (mode) {
                ViewerReadingMode.AUTO -> R.string.ui_reader_auto
                ViewerReadingMode.PAGE -> R.string.ui_reader_page
                ViewerReadingMode.WEBTOON -> R.string.ui_reader_webtoon
            }], preferences.readingMode == mode) { onChange(preferences.copy(readingMode = mode)) }
        }
        SettingsHeading(strings[R.string.ui_page_scale])
        ViewerScale.entries.forEach { value ->
            SettingsRadio(
                label = if (value == ViewerScale.FIT_SCREEN) strings[R.string.ui_fit_page] else strings[R.string.ui_fit_width],
                selected = preferences.scale == value,
            ) { onChange(preferences.copy(scale = value)) }
        }

        SettingsHeading(strings[R.string.ui_reading_direction])
        ViewerReadingDirection.entries.forEach { value ->
            SettingsRadio(
                label = if (value == ViewerReadingDirection.LEFT_TO_RIGHT) {
                    strings[R.string.ui_left_right]
                } else {
                    strings[R.string.ui_right_left]
                },
                selected = preferences.readingDirection == value,
            ) {
                if (preferences.readingDirection != value) {
                    onChange(
                        preferences.copy(
                            readingDirection = value,
                            tapZones = preferences.tapZones.swapPreviousAndNext(),
                        ),
                    )
                }
            }
        }

        }
        if (section == null || section == 1) {
        SettingsHeading(strings[R.string.ui_page_turning])
        ViewerPageTurnMode.entries.forEach { value ->
            SettingsRadio(
                label = when (value) {
                    ViewerPageTurnMode.SWIPE_AND_TAP -> strings[R.string.ui_swipe_tap]
                    ViewerPageTurnMode.SWIPE_ONLY -> strings[R.string.ui_swipe_only]
                    ViewerPageTurnMode.TAP_ONLY -> strings[R.string.ui_tap_only]
                },
                selected = preferences.pageTurnMode == value,
            ) { onChange(preferences.copy(pageTurnMode = value)) }
        }

        SettingsHeading(strings[R.string.ui_tap_zones])
        ReaderSettingSwitch(strings[R.string.ui_custom_3_3_zones], preferences.customTapZonesEnabled) { enabled ->
            onChange(
                preferences.copy(
                    customTapZonesEnabled = enabled,
                ),
            )
            tapEditorVisible = enabled
        }
        if (preferences.customTapZonesEnabled) {
            Text(
                strings[R.string.ui_place_previous_toggle_next_none_actions_on_screen],
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { tapEditorVisible = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(strings[R.string.ui_edit_tap_zones]) }
        }

        }
        if (section == null || section == 2) {
        ReaderSettingSwitch(strings[R.string.ui_show_page_number], preferences.showPageNumber) {
            onChange(preferences.copy(showPageNumber = it))
        }
        ReaderSettingSwitch(strings[R.string.ui_hide_thumbnails], preferences.hideThumbnails) {
            onChange(preferences.copy(hideThumbnails = it))
        }
        ReaderSettingSwitch(strings[R.string.ui_keep_screen_on], preferences.keepScreenOn) {
            onChange(preferences.copy(keepScreenOn = it))
        }
        }
    }

    if (tapEditorVisible && preferences.customTapZonesEnabled) {
        TouchZoneEditor(
            zones = preferences.tapZones,
            onChange = { zones -> onChange(preferences.copy(tapZones = zones)) },
            onDismiss = { tapEditorVisible = false },
        )
    }
}

@Composable
private fun TouchZoneEditor(
    zones: ViewerTapZones,
    onChange: (ViewerTapZones) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Column(modifier = Modifier.fillMaxSize()) {
                repeat(3) { row ->
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        repeat(3) { column ->
                            val index = row * 3 + column
                            val action = zones.actionAt(index)
                            Box(
                                modifier = Modifier.weight(1f).fillMaxSize()
                                    .background(action.zoneColor().copy(alpha = 0.76f))
                                    .border(1.dp, Color.White.copy(alpha = 0.55f))
                                    .clickable {
                                        onChange(zones.withAction(index, action.nextCustomAction()))
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    strings[action.shortLabelResource()],
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.78f)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    strings[R.string.ui_tap_a_zone_to_cycle_previous_toggle_next_none],
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onDismiss) { Text(strings[R.string.ui_completed], color = Color.White) }
            }
        }
    }
}

internal fun ViewerTapAction.nextCustomAction(): ViewerTapAction = when (this) {
    ViewerTapAction.PREVIOUS_PAGE -> ViewerTapAction.TOGGLE_CONTROLS
    ViewerTapAction.TOGGLE_CONTROLS -> ViewerTapAction.NEXT_PAGE
    ViewerTapAction.NEXT_PAGE -> ViewerTapAction.NONE
    ViewerTapAction.NONE -> ViewerTapAction.PREVIOUS_PAGE
}

private fun ViewerTapAction.zoneColor(): Color = when (this) {
    ViewerTapAction.PREVIOUS_PAGE -> Color(0xFFD32F2F)
    ViewerTapAction.NONE -> Color(0xFF616161)
    ViewerTapAction.NEXT_PAGE -> Color(0xFF00897B)
    ViewerTapAction.TOGGLE_CONTROLS -> Color(0xFF3949AB)
}

private fun ViewerTapAction.shortLabelResource(): Int = when (this) {
    ViewerTapAction.PREVIOUS_PAGE -> R.string.ui_previous
    ViewerTapAction.NEXT_PAGE -> R.string.ui_next
    ViewerTapAction.TOGGLE_CONTROLS -> R.string.ui_toggle_controls
    ViewerTapAction.NONE -> R.string.ui_none
}

@Composable
private fun SettingsHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun SettingsRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun ReaderSettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ReaderKeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
