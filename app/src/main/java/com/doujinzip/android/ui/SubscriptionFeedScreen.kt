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
    loadIds: suspend (List<com.doujinzip.android.model.SearchSubscription>) -> List<Long>, loadGallery: suspend (Long) -> GallerySummary,
    onLoaded: (GallerySummary) -> Unit, onBack: () -> Unit, onOpen: (Long) -> Unit,
    onLongClick: (Long) -> Unit, onFavorite: (String) -> Unit, onFacet: (String) -> Unit,
    probeIds: (suspend (List<com.doujinzip.android.model.SearchSubscription>) -> String)? = null,
) {
    val strings=androidx.compose.ui.platform.LocalResources.current
    val context=androidx.compose.ui.platform.LocalContext.current
    val preferences=remember { context.getSharedPreferences("subscription_feed",android.content.Context.MODE_PRIVATE) }
    val scopeKey=state.selectedProfileId.orEmpty()
    var excluded by remember(scopeKey) { mutableStateOf(preferences.getStringSet("excluded-$scopeKey",emptySet()).orEmpty().toSet()) }
    var oldest by remember(scopeKey) { mutableStateOf(preferences.getBoolean("oldest-$scopeKey",false)) }
    var filterMenu by remember { mutableStateOf(false) }
    val subscriptions=state.subscriptions.map { it.value }.filter { it.enabled && !it.deleted }
    val selected=subscriptions.filter { it.id !in excluded }
    val signature=scopeKey+selected.flatMap { it.queries }.distinct().sorted().toString()+state.searchTagFilters.sortedBy { it.name }+state.customBlockedTags.sorted()+oldest
    val session=remember(signature) { java.security.MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) } }
    val cache=remember { com.doujinzip.android.data.SearchResultCache(java.io.File(context.filesDir,"subscription-feed")) }
    val listState=rememberLazyGridState()
    var restored by remember(session) { mutableStateOf(false) }
    LaunchedEffect(session,restored) {
        if(restored) snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (index,offset) ->
            preferences.edit().putInt("$session-index",index).putInt("$session-offset",offset).apply()
        }
    }
    val scope=rememberCoroutineScope()
    var ids by remember { mutableStateOf(emptyList<Long>()) }
    var galleries by remember { mutableStateOf(emptyList<GallerySummary>()) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember(session) { mutableIntStateOf(0) }
    var pendingIds by remember(session) { mutableStateOf(emptyList<Long>()) }
    var indexReady by remember { mutableStateOf(false) }
    var retryIndex by remember { mutableStateOf(false) }
    var pageJob by remember { mutableStateOf<Job?>(null) }
    suspend fun persist(): Boolean {
        val index=com.doujinzip.android.data.SearchResultIndex(session,emptyList(),listOf(galleries.map { it.id },ids.drop(galleries.size)))
        return cache.writePage(index,1,galleries) && cache.writeIndex(index)
    }
    suspend fun nextPage() {
        retryIndex=false; busy=true; error=null
        try {
            val page=ids.drop(galleries.size).take(25)
            for(id in page) {
                val gallery=state.galleryCache[id] ?: loadGallery(id)
                onLoaded(gallery)
                galleries=galleries+gallery
            }
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(failure: Exception) { error=failure.message ?: strings[R.string.feed_error] }
        finally { persist(); busy=false }
    }
    LaunchedEffect(session, revision) {
        pageJob?.cancelAndJoin()
        retryIndex=true; busy=true; error=null
        if(!restored) {
            val saved=cache.readIndex(session)
            val page=saved?.let { cache.readPage(it,1) }
            ids=if(page!=null) saved.ids else emptyList()
            galleries=page.orEmpty(); indexReady=page!=null
            if(galleries.isNotEmpty()) listState.scrollToItem(preferences.getInt("$session-index",0).coerceIn(0,galleries.lastIndex),preferences.getInt("$session-offset",0).coerceAtLeast(0))
            pendingIds=cache.readIndex("$session-pending")?.ids.orEmpty()
            restored=true
        }
        if(!com.doujinzip.android.data.subscriptionFeedRefreshDue(System.currentTimeMillis(),preferences.getLong("$session-success",0),indexReady,revision>0)) {
            busy=false
            return@LaunchedEffect
        }
        try {
            val now=System.currentTimeMillis()
            val probe=try { probeIds?.invoke(selected) }
                catch(cancelled: CancellationException) { throw cancelled }
                catch(_: Exception) { null }
            if(indexReady && !com.doujinzip.android.data.subscriptionFeedFullRefreshDue(now,preferences.getLong("$session-full",0),preferences.getString("$session-probe",null),probe,revision>0)) {
                preferences.edit().putLong("$session-success",now).apply()
                return@LaunchedEffect
            }
            val loadedIds=loadIds(selected)
            val fresh=if(oldest) loadedIds.sorted() else loadedIds.sortedDescending()
            val existing=ids.toHashSet()
            if(indexReady && revision==0 && fresh.any { it !in existing }) {
                if(cache.writeIndex(com.doujinzip.android.data.SearchResultIndex("$session-pending",emptyList(),listOf(fresh)))) {
                    pendingIds=fresh
                    preferences.edit().putLong("$session-success",System.currentTimeMillis()).putLong("$session-full",System.currentTimeMillis()).putString("$session-probe",probe).apply()
                }
                return@LaunchedEffect
            }
            val anchor=galleries.getOrNull(listState.firstVisibleItemIndex)?.id
            val offset=listState.firstVisibleItemScrollOffset
            val previous=(state.galleryCache.values+galleries).associateBy { it.id }
            val count=maxOf(galleries.size,25,fresh.indexOf(anchor)+1)
            val refreshed=mutableListOf<GallerySummary>()
            for(id in fresh.take(count)) refreshed+=previous[id] ?: loadGallery(id).also(onLoaded)
            ids=fresh; galleries=refreshed; indexReady=true
            val anchorIndex=fresh.indexOf(anchor)
            if(anchorIndex>=0) listState.scrollToItem(anchorIndex,offset)
            if(persist()) {
                pendingIds=emptyList()
                cache.writeIndex(com.doujinzip.android.data.SearchResultIndex("$session-pending",emptyList()))
                preferences.edit().putLong("$session-success",System.currentTimeMillis()).putLong("$session-full",System.currentTimeMillis()).putString("$session-probe",probe).apply()
            }
        }
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
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            Box {
                OutlinedButton({filterMenu=true}) { ActionIcon(ActionSymbol.FILTER); Text(strings[R.string.feed_filter]) }
                DropdownMenu(filterMenu,{filterMenu=false}) {
                    DropdownMenuItem(text={Text(strings[R.string.ui_all])},onClick={excluded=emptySet();preferences.edit().putStringSet("excluded-$scopeKey",excluded).apply()})
                    subscriptions.forEach { subscription ->
                        DropdownMenuItem(text={Text(subscription.name)},leadingIcon={Checkbox(subscription.id !in excluded,null)},onClick={
                            excluded=if(subscription.id in excluded) excluded-subscription.id else excluded+subscription.id
                            preferences.edit().putStringSet("excluded-$scopeKey",excluded).apply()
                        })
                    }
                }
            }
            SearchSortMenu(if(oldest) "sort:oldest" else "sort:newest",enabled=!busy) {
                oldest=it;preferences.edit().putBoolean("oldest-$scopeKey",it).apply()
            }
        }
        if(pendingIds.isNotEmpty()) TextButton({revision++},enabled=!busy) { Text(strings[R.string.feed_new_items]) }
        if(indexReady) Text(strings[R.string.feed_count,ids.size],Modifier.padding(horizontal=16.dp))
        if(error!=null) {
            Text(error.orEmpty(),Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error)
            TextButton({if(indexReady && !retryIndex) pageJob=scope.launch { nextPage() } else revision++},enabled=!busy) { Text(strings[R.string.ui_try_again]) }
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(indexReady && ids.isEmpty() && !busy) Text(strings[R.string.feed_empty],Modifier.padding(16.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyVerticalGrid(GridCells.Fixed(if(grid) columns else 1),state=listState,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),
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
        DynamicVerticalScrollbar(listState)
        }
    }
}
