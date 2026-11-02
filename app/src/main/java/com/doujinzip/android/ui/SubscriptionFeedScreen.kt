package com.doujinzip.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.doujinzip.android.R
import com.doujinzip.android.model.GallerySummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin

@Composable
internal fun SubscriptionFeedScreen(
    state: MainUiState, padding: PaddingValues, grid: Boolean, columns: Int,
    loadIds: suspend () -> List<Long>, loadGallery: suspend (Long) -> GallerySummary,
    onLoaded: (GallerySummary) -> Unit, onBack: () -> Unit, onOpen: (Long) -> Unit,
    onLongClick: (Long) -> Unit, onFavorite: (String) -> Unit, onFacet: (String) -> Unit,
) {
    val strings=androidx.compose.ui.platform.LocalResources.current
    val scope=rememberCoroutineScope()
    var ids by remember { mutableStateOf(emptyList<Long>()) }
    var galleries by remember { mutableStateOf(emptyList<GallerySummary>()) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var indexReady by remember { mutableStateOf(false) }
    var pageJob by remember { mutableStateOf<Job?>(null) }
    suspend fun nextPage() {
        busy=true; error=null
        try {
            val page=ids.drop(galleries.size).take(25)
            for(id in page) {
                val gallery=state.galleryCache[id] ?: loadGallery(id)
                onLoaded(gallery)
                galleries=galleries+gallery
            }
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(failure: Exception) { error=failure.message ?: strings[R.string.feed_error] }
        finally { busy=false }
    }
    LaunchedEffect(state.selectedProfileId, state.subscriptions.map { it.value }, state.searchTagFilters, state.customBlockedTags, revision) {
        pageJob?.cancelAndJoin()
        ids=emptyList(); galleries=emptyList(); indexReady=false; busy=true; error=null
        try { ids=loadIds(); indexReady=true; nextPage() }
        catch(cancelled: CancellationException) { throw cancelled }
        catch(failure: Exception) { error=failure.message ?: strings[R.string.feed_error] }
        finally { busy=false }
    }
    BackHandler(onBack=onBack)
    val books=remember(state.libraryBooks,state.libraryHiddenIds) { preferredLibraryBooksByGalleryId(state.libraryBooks,state.libraryHiddenIds) }
    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onBack) { ActionIcon(ActionSymbol.BACK); Text(strings[R.string.feed_title]) }
            TextButton({revision++},enabled=!busy) { ActionIcon(ActionSymbol.REFRESH); Text(strings[R.string.ui_refresh]) }
        }
        if(indexReady) Text(strings[R.string.feed_count,ids.size],Modifier.padding(horizontal=16.dp))
        if(error!=null) {
            Text(error.orEmpty(),Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error)
            TextButton({if(indexReady) pageJob=scope.launch { nextPage() } else revision++},enabled=!busy) { Text(strings[R.string.ui_try_again]) }
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(indexReady && ids.isEmpty() && !busy) Text(strings[R.string.feed_empty],Modifier.padding(16.dp))
        LazyVerticalGrid(GridCells.Fixed(if(grid) columns else 1),modifier=Modifier.weight(1f),contentPadding=PaddingValues(16.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            items(galleries,key={it.id}) { item ->
                val gallery=state.galleryCache[item.id] ?: item
                val book=books[gallery.id]
                if(book?.id !in state.libraryHiddenIds && "online:${gallery.id}" !in state.libraryHiddenIds) GalleryCard(gallery,grid,
                    read=gallery.id in state.viewedGalleryIds || book?.id in state.libraryReadIds || "online:${gallery.id}" in state.libraryReadIds,
                    libraryBook=book,favorite=book?.id in state.libraryFavoriteIds || "online:${gallery.id}" in state.libraryFavoriteIds,
                    onToggleFavorite={onLoaded(gallery); onFavorite("online:${gallery.id}")},onClick={onLoaded(gallery); onOpen(gallery.id)},
                    onLongClick={onLoaded(gallery); onLongClick(gallery.id)},onSearchFacet=onFacet)
            }
            if(indexReady && galleries.size<ids.size && error==null) item(span={GridItemSpan(maxLineSpan)}) {
                TextButton({pageJob=scope.launch { nextPage() }},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text(strings[R.string.feed_more]) }
            }
        }
    }
}
