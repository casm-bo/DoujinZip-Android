package com.doujinzip.android.ui

import com.doujinzip.android.R
import com.doujinzip.android.model.presentation
import com.doujinzip.android.model.presentationKey
import com.doujinzip.android.model.seriesPresentationKey
import com.doujinzip.android.model.currentOrPreviousPage
import com.doujinzip.android.model.hasSameContent

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.doujinzip.android.model.BrowserPage
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlin.math.cos
import kotlin.math.sin

internal enum class MainDestination(
    val route: String,
    val labelResource: Int,
    val symbol: String,
) {
    Browser("browser", R.string.ui_search, "⌕"),
    Library("library", R.string.ui_gallery, "▦"),
    History("history", R.string.ui_recent_history, "◷"),
    Downloads("downloads", R.string.ui_downloads, "↓"),
    Settings("settings", R.string.ui_settings, "⚙"),
}

@Composable
fun DoujinZipApp(viewModel: MainViewModel = viewModel()) {
    if (viewModel.uiState.isStarting) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val strings = androidx.compose.ui.platform.LocalResources.current
    var deferredConflicts by remember(viewModel.uiState.selectedProfileId) {
        mutableStateOf(emptySet<com.doujinzip.android.data.PresentationRecord>())
    }
    LaunchedEffect(viewModel.uiState.isLibrarySyncing) {
        if (viewModel.uiState.isLibrarySyncing) deferredConflicts = emptySet()
    }
    if (!viewModel.uiState.isLibrarySyncing) {
        viewModel.uiState.presentations.values.firstOrNull {
            it.conflict != null && !it.value.hasSameContent(it.conflict) && it !in deferredConflicts
        }?.let { record ->
            PresentationConflictDialog(record,
                onChoose = { usePhone -> viewModel.resolvePresentationConflict(record, usePhone) },
                onLater = { deferredConflicts = deferredConflicts + record })
        }
    }
    val navController = rememberNavController()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var backgroundTabNotice by remember { mutableStateOf<Job?>(null) }
    val openGalleryInNewTab: (Long) -> Unit = { galleryId ->
        viewModel.openGalleryBackgroundTab(galleryId)?.let { result ->
            backgroundTabNotice?.cancel()
            backgroundTabNotice = snackbarScope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val action = snackbarHostState.showSnackbar(
                    message = when {
                        result.tabId == null -> strings[R.string.ui_could_not_open_a_new_tab]
                        result.alreadyOpen -> strings[R.string.ui_this_tab_is_already_open]
                        else -> strings[R.string.ui_opened_in_a_new_tab]
                    },
                    actionLabel = result.tabId?.let { strings[R.string.ui_go] },
                    withDismissAction = true,
                    duration = SnackbarDuration.Short,
                )
                if (action == SnackbarResult.ActionPerformed &&
                    (if (result.galleryWorkspace) viewModel.uiState.galleryWorkspace else viewModel.uiState.browserWorkspace).tabs.any { it.id == result.tabId }) {
                    viewModel.tabPreviews.captureNow()
                    if (result.galleryWorkspace) viewModel.selectGalleryTab(requireNotNull(result.tabId))
                    else viewModel.selectBrowserTab(requireNotNull(result.tabId))
                }
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastMainDestination by rememberSaveable { mutableStateOf(viewModel.initialMainDestination()) }
    var searchOpenedFromGallery by rememberSaveable { mutableStateOf(false) }
    var lastShownNewTabNotice by rememberSaveable { mutableStateOf(0) }
    if(viewModel.uiState.showSubscriptions) androidx.compose.ui.window.Dialog(onDismissRequest=viewModel::closeSubscriptions,
        properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false,securePolicy=androidx.compose.ui.window.SecureFlagPolicy.Inherit)) {
        Surface(Modifier.fillMaxSize()) {
            SubscriptionScreen(viewModel.uiState,viewModel::closeSubscriptions,{viewModel.openSubscriptions(it)},
                {viewModel.refreshSubscriptions(true)}, {viewModel.markSubscriptionRead(it)}, viewModel::editSubscription,
                viewModel::saveSubscription,viewModel::resolveSubscription,{ value ->
                    viewModel.closeSubscriptions()
                    lastMainDestination=MainDestination.Browser.route
                    viewModel.searchFavorite(com.doujinzip.android.model.SearchFavorite(value.id,value.name,value.query,value.languages))
                    navController.navigate("main") { popUpTo("main");launchSingleTop=true }
                }, onQueryChange=viewModel::setSubscriptionQuery,onLoadLanguages=viewModel::loadAvailableLanguages,
                )
        }
    }
    val newTabNotice = viewModel.uiState.newTabNotice
    LaunchedEffect(newTabNotice) {
        if (newTabNotice > 0 && newTabNotice != lastShownNewTabNotice) {
            lastShownNewTabNotice = newTabNotice
            backgroundTabNotice?.cancel()
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(strings[R.string.ui_opened_in_a_new_tab], duration = SnackbarDuration.Short)
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val appMessage = viewModel.uiState.message
    val appMessageIsError = viewModel.uiState.isError
    LaunchedEffect(appMessage) {
        if (appMessage != null) {
            snackbarHostState.showSnackbar(
                message = appMessage,
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            viewModel.dismissMessage()
        }
    }

    val browserNavigationRevision = viewModel.uiState.browserNavigationRevision
    var appliedNavigationRevision by rememberSaveable { mutableStateOf(-1L) }
    LaunchedEffect(browserNavigationRevision) {
        val page = viewModel.activeBrowserPage()
        val restoringMain = navController.currentDestination?.route == "main" &&
            (appliedNavigationRevision == browserNavigationRevision || (browserNavigationRevision == 0L && lastMainDestination !in setOf("browser", "library")))
        appliedNavigationRevision = browserNavigationRevision
        if (restoringMain && (page is BrowserPage.Search || page is BrowserPage.LibraryHome)) return@LaunchedEffect
        if (page is BrowserPage.LibraryHome || page is BrowserPage.LibrarySeries || page is BrowserPage.LibraryBook || page is BrowserPage.LibraryReader) searchOpenedFromGallery = false
        when (page) {
            is BrowserPage.LibraryHome -> {
                lastMainDestination = MainDestination.Library.route
                navController.popBackStack("main", inclusive = false)
            }
            is BrowserPage.Search -> {
                if (searchOpenedFromGallery) {
                    when {
                        navController.currentDestination?.route == SEARCH_FROM_GALLERY_ROUTE -> Unit
                        navController.popBackStack(SEARCH_FROM_GALLERY_ROUTE, inclusive = false) -> Unit
                        else -> navController.navigate(SEARCH_FROM_GALLERY_ROUTE) { launchSingleTop = true }
                    }
                } else {
                    lastMainDestination = MainDestination.Browser.route
                    navController.popBackStack("main", inclusive = false)
                }
            }
            is BrowserPage.OnlineGallery -> navController.navigate("gallery/${page.galleryId}") {
                popUpTo("main")
                launchSingleTop = true
            }
            is BrowserPage.LibrarySeries -> navController.navigate("library-series/${Uri.encode(page.name)}") {
                popUpTo("main")
                launchSingleTop = true
            }
            is BrowserPage.LibraryBook -> navController.navigate("library-detail/${Uri.encode(page.bookId)}") {
                popUpTo("main")
                launchSingleTop = true
            }
            is BrowserPage.OnlineReader -> navController.navigate(
                "reader/${page.galleryId}?startPage=${page.startPage}",
            ) {
                popUpTo("main")
                launchSingleTop = true
            }
            is BrowserPage.LibraryReader -> navController.navigate(
                "library-reader/${Uri.encode(page.bookId)}?startPage=${page.startPage}",
            ) {
                popUpTo("main")
                launchSingleTop = true
            }
        }
    }

    val searchBack: () -> Unit = {
        if (!viewModel.goBackInBrowserTab()) navController.popBackStack()
    }
    val galleryBack: () -> Unit = {
        if (!viewModel.goBackInGalleryTab()) navController.popBackStack()
    }

    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val previewIsGallery = when {
        currentRoute == SEARCH_FROM_GALLERY_ROUTE -> false
        currentRoute?.startsWith("library-") == true -> true
        currentRoute?.startsWith("gallery/") == true || currentRoute?.startsWith("reader/") == true -> false
        currentRoute == "main" && lastMainDestination == MainDestination.Library.route -> true
        currentRoute == "main" && lastMainDestination == MainDestination.Browser.route -> false
        else -> null
    }
    val previewWorkspace = if (previewIsGallery == true) viewModel.uiState.galleryWorkspace else viewModel.uiState.browserWorkspace
    val previewKey = previewIsGallery?.let {
        BrowserTabPreviewKey(it, previewWorkspace.activeTabId, previewWorkspace.activeTab.currentPage.key)
    }
    TabSwipeHost {
    BrowserTabPreviewHost(viewModel.tabPreviews, previewKey) {
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "main",
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            enterTransition = { fadeIn(tween(160)) },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { fadeIn(tween(160)) },
            popExitTransition = { ExitTransition.None },
        ) {
            composable("main") {
                MainShell(
                    viewModel = viewModel,
                    initialDestinationRoute = lastMainDestination,
                    onDestinationChanged = { lastMainDestination = it; viewModel.rememberMainDestination(it) },
                    onGalleryClick = {
                        val libraryBookId = viewModel.libraryBookIdForGallery(it)
                        if (libraryBookId != null) {
                            viewModel.openLibraryBookTab(libraryBookId)
                        } else {
                            viewModel.selectGallery(it)
                        }
                    },
                    onGalleryLongClick = openGalleryInNewTab,
                    onLibraryBookClick = viewModel::openLibraryBookTab,
                )
            }
            composable(SEARCH_FROM_GALLERY_ROUTE) {
                MainShell(
                    viewModel = viewModel,
                    initialDestinationRoute = MainDestination.Browser.route,
                    onDestinationChanged = {},
                    onGalleryClick = {
                        val libraryBookId = viewModel.libraryBookIdForGallery(it)
                        if (libraryBookId != null) viewModel.openLibraryBookTab(libraryBookId)
                        else viewModel.selectGallery(it)
                    },
                    onGalleryLongClick = openGalleryInNewTab,
                    onLibraryBookClick = viewModel::openLibraryBookTab,
                    onBackToOrigin = {
                        searchOpenedFromGallery = false
                        navController.popBackStack()
                    },
                )
            }
            composable("library-series/{seriesName}") { entry ->
                val initial = remember(entry.id) { viewModel.uiState.galleryWorkspace.activeTab.currentPage as? BrowserPage.LibrarySeries } ?: return@composable
                val page = (viewModel.uiState.galleryWorkspace.activeTab.currentPage as? BrowserPage.LibrarySeries)?.takeIf { it.key == initial.key } ?: initial
                BackHandler(onBack = galleryBack)
                LibraryTabContent(viewModel, PaddingValues(0.dp), viewModel::openLibraryBookTab, galleryBack,
                    onSearchFacet = { searchOpenedFromGallery = true; viewModel.searchFromFacet(it) },
                    onSearchLanguage = { searchOpenedFromGallery = true; viewModel.searchFromLanguage(it) }, seriesPage = page)
            }
            composable("library-detail/{bookId}") { entry ->
                val bookId = entry.arguments?.getString("bookId") ?: return@composable
                BackHandler(onBack = galleryBack)
                val currentBook = viewModel.uiState.libraryBooks.firstOrNull { it.id == bookId }
                LaunchedEffect(bookId, currentBook?.id) {
                    if (currentBook != null) viewModel.openLibraryBook(bookId)
                }
                val nextBook = currentBook?.let { viewModel.nextLibrarySeriesBook(it.id) }
                val previousBook = currentBook?.let { viewModel.previousLibrarySeriesBook(it.id) }
                val isSeriesBook = currentBook?.id in viewModel.uiState.customSeriesByBookId
                val detail = remember(entry.id) { viewModel.uiState.galleryWorkspace.activeTab.currentPage as? BrowserPage.LibraryBook }
                LibraryDetailScreen(
                    pageKey = detail?.key ?: entry.id,
                    initialPreviewPosition = detail?.previewPosition ?: com.doujinzip.android.model.ReaderPosition(0),
                    onPreviewPositionChange = { viewModel.updatePreviewScroll(detail?.key ?: entry.id, it) },
                    book = currentBook,
                    previousBook = previousBook,
                    nextBook = nextBook,
                    isSeriesBook = isSeriesBook,
                    progress = currentBook?.let { viewModel.uiState.libraryProgress[it.id] } ?: 0,
                    isLoading = viewModel.uiState.isLibraryBookLoading,
                    error = viewModel.uiState.libraryScanError,
                    onBack = galleryBack,
                    onOpenReader = { page -> viewModel.openLibraryReaderTab(bookId, page) },
                    onOpenPreviousBook = {
                        previousBook?.let { viewModel.openLibraryBookTab(it.id) }
                    },
                    onOpenNextBook = {
                        nextBook?.let { viewModel.openLibraryBookTab(it.id) }
                    },
                    onOpenSeriesList = {
                        currentBook?.id?.let { id ->
                            viewModel.uiState.customSeriesByBookId[id]?.name?.let { series ->
                                viewModel.openLibrarySeriesList(series)
                            }
                        }
                    },
                    onSearchFacet = { facet ->
                        searchOpenedFromGallery = true
                        viewModel.searchFromFacet(facet)
                    },
                    onSearchLanguage = { language ->
                        searchOpenedFromGallery = true
                        viewModel.searchFromLanguage(language)
                    },
                    options = {
                        currentBook?.let { book -> runCatching { book.presentation() }.getOrNull()?.let { value ->
                            WorkPresentationActions(viewModel, value,
                                book.pages.filter { it.uri.isNotBlank() }.map { page -> com.doujinzip.android.model.ReaderImageSource(page.uri,
                                    if (book.isCloud && book.cloudToken != null) mapOf("Authorization" to "Bearer ${book.cloudToken}") else emptyMap()) }, setOf(book.id))
                        } }
                    },
                    webtoon = currentBook?.let { book -> runCatching { viewModel.uiState.presentations[book.presentationKey()]?.value?.webtoon == true }.getOrDefault(false) } == true,
                    tabBar = { AppGalleryTabStrip(viewModel, onBack = galleryBack) },
                )
            }
            composable(
                route = "library-reader/{bookId}?startPage={startPage}",
                arguments = listOf(navArgument("startPage") {
                    type = NavType.IntType
                    defaultValue = -1
                }),
            ) { entry ->
                val bookId = entry.arguments?.getString("bookId") ?: return@composable
                BackHandler(onBack = galleryBack)
                val book = viewModel.uiState.libraryBooks.firstOrNull { it.id == bookId }
                LaunchedEffect(bookId, book?.id) {
                    if (book != null) viewModel.openLibraryBook(bookId)
                }
                val nextBook = book?.let { viewModel.nextLibrarySeriesBook(it.id) }
                val previousBook = book?.let { viewModel.previousLibrarySeriesBook(it.id) }
                val requestedPage = entry.arguments?.getInt("startPage") ?: -1
                val session = remember(entry.id) { (viewModel.uiState.galleryWorkspace.activeTab.currentPage as? BrowserPage.LibraryReader)?.takeIf { it.bookId == bookId } }
                val sessionKey = session?.key ?: entry.id
                LocalReaderScreen(
                    sessionKey = sessionKey,
                    initialOffset = session?.position?.offset ?: 0,
                    book = book,
                    initialPage = session?.position?.page ?: if (requestedPage >= 0) requestedPage
                        else book?.let { viewModel.uiState.libraryProgress[it.id] } ?: 0,
                    isLoading = viewModel.uiState.isLibraryBookLoading,
                    error = viewModel.uiState.libraryScanError,
                    favorite = book?.id in viewModel.uiState.libraryFavoriteIds,
                    preferences = viewModel.uiState.viewerPreferences,
                    onBack = galleryBack,
                    onToggleFavorite = { book?.let { viewModel.toggleLibraryFavorite(it.id) } },
                    onProgress = { position ->
                        viewModel.updateReaderSession(sessionKey, position.page, position.offset)
                        book?.let { viewModel.updateLibraryProgress(it.id, position.page) }
                    },
                    isSeries = bookId in viewModel.uiState.customSeriesByBookId,
                    previousBookTitle = previousBook?.title,
                    onOpenPreviousBook = { previousBook?.let { viewModel.openNextLibraryReaderTab(it.id) } },
                    nextBookTitle = nextBook?.title,
                    onOpenNextBook = {
                        nextBook?.let {
                            viewModel.openNextLibraryReaderTab(it.id)
                        }
                    },
                    onPreferencesChange = viewModel::updateViewerPreferences,
                    onReadingModeResolved = { viewModel.classifyLibraryBook(bookId, it) },
                )
            }
            composable("gallery/{galleryId}") { entry ->
                val galleryId = entry.arguments?.getString("galleryId")?.toLongOrNull()
                    ?: return@composable
                LaunchedEffect(galleryId) { viewModel.ensureGalleryLoaded(galleryId) }
                BackHandler(onBack = searchBack)
                val detail = remember(entry.id) { viewModel.uiState.browserWorkspace.activeTab.currentPage as? BrowserPage.OnlineGallery }
                GalleryDetailScreen(
                    pageKey = detail?.key ?: entry.id,
                    initialPreviewPosition = detail?.previewPosition ?: com.doujinzip.android.model.ReaderPosition(0),
                    onPreviewPositionChange = { viewModel.updatePreviewScroll(detail?.key ?: entry.id, it) },
                    gallery = viewModel.uiState.galleryCache[galleryId]
                        ?: viewModel.uiState.galleries.firstOrNull { it.id == galleryId },
                    state = viewModel.uiState,
                    onLoadPreview = viewModel::loadReader,
                    onBack = searchBack,
                    onOpenReader = { page -> viewModel.openOnlineReaderTab(galleryId, page) },
                    onSearchFacet = { facet ->
                        viewModel.searchFromFacet(facet)
                    },
                    onSearchLanguage = { language ->
                        viewModel.searchFromLanguage(language)
                    },
                    onDownload = viewModel::downloadGallery,
                    options = {
                        val gallery = viewModel.uiState.galleryCache[galleryId] ?: viewModel.uiState.galleries.firstOrNull { it.id == galleryId }
                        gallery?.let {
                            val sources = viewModel.uiState.readerPagesByGalleryId[galleryId].orEmpty().map { url ->
                                com.doujinzip.android.model.ReaderImageSource(url, mapOf("Referer" to "https://hitomi.la/reader/$galleryId.html"))
                            }
                            val ids = viewModel.uiState.libraryBooks.filter { it.metadata.hitomiId == galleryId.toString() }.mapTo(linkedSetOf()) { it.id }
                            WorkPresentationActions(viewModel, it.presentation(), sources, ids)
                        }
                    },
                    webtoon = viewModel.uiState.presentations["hitomi:$galleryId"]?.value?.webtoon == true,
                    tabBar = { AppSearchTabStrip(viewModel, onBack = searchBack) },
                )
            }
            composable(
                route = "reader/{galleryId}?startPage={startPage}",
                arguments = listOf(
                    navArgument("startPage") {
                        type = NavType.IntType
                        defaultValue = -1
                    },
                ),
            ) { entry ->
                val galleryId = entry.arguments?.getString("galleryId")?.toLongOrNull()
                    ?: return@composable
                BackHandler(onBack = searchBack)
                val requestedPage = entry.arguments?.getInt("startPage") ?: -1
                val session = remember(entry.id) { (viewModel.uiState.browserWorkspace.activeTab.currentPage as? BrowserPage.OnlineReader)?.takeIf { it.galleryId == galleryId } }
                val sessionKey = session?.key ?: entry.id
                val libraryBookId = viewModel.libraryBookIdForGallery(galleryId)
                val previousBook = libraryBookId?.let(viewModel::previousLibrarySeriesBook)
                val nextBook = libraryBookId?.let(viewModel::nextLibrarySeriesBook)
                ReaderScreen(
                    sessionKey = sessionKey,
                    initialOffset = session?.position?.offset ?: 0,
                    galleryId = galleryId,
                    initialPage = session?.position?.page ?: if (requestedPage >= 0) requestedPage
                        else viewModel.uiState.onlineReaderProgress[galleryId] ?: 0,
                    state = viewModel.uiState,
                    preferences = viewModel.uiState.viewerPreferences,
                    onLoad = viewModel::loadReader,
                    onBack = searchBack,
                    onProgress = { position ->
                        viewModel.updateReaderSession(sessionKey, position.page, position.offset)
                        viewModel.updateOnlineReaderProgress(galleryId, position.page)
                    },
                    isSeries = libraryBookId in viewModel.uiState.customSeriesByBookId,
                    previousBookTitle = previousBook?.title,
                    nextBookTitle = nextBook?.title,
                    onOpenPreviousBook = { previousBook?.let { viewModel.openLibraryReaderTab(it.id, 0) } },
                    onOpenNextBook = { nextBook?.let { viewModel.openLibraryReaderTab(it.id, 0) } },
                    onPreferencesChange = viewModel::updateViewerPreferences,
                    onReadingModeResolved = { viewModel.classifyGallery(galleryId, it) },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 88.dp),
        ) { data ->
            val isError = appMessageIsError && data.visuals.message == appMessage
            Snackbar(
                snackbarData = data,
                containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.inverseSurface,
                contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }

    }

    }

    viewModel.uiState.tabOverviewIsGallery?.let { isGallery ->
        BrowserTabsOverview(
            workspace = if (isGallery) viewModel.uiState.galleryWorkspace else viewModel.uiState.browserWorkspace,
            isGallery = isGallery,
            previews = viewModel.tabPreviews,
            preferredLanguages = viewModel.uiState.preferredLanguages,
            onSelect = { id ->
                snackbarScope.launch {
                    viewModel.tabPreviews.captureNow()
                    viewModel.dismissTabOverview()
                    if (isGallery) viewModel.selectGalleryTab(id) else viewModel.selectBrowserTab(id)
                }
            },
            onCloseTab = { id -> if (isGallery) viewModel.closeGalleryTab(id) else viewModel.closeBrowserTab(id) },
            onUpdate = { viewModel.updateTabWorkspace(it, isGallery) },
            onDismiss = viewModel::dismissTabOverview,
        )
    }

    DownloadPrompts(viewModel) { section ->
        viewModel.requestSettingsSection(section)
        navController.navigate("main") { popUpTo("main"); launchSingleTop = true }
    }

    viewModel.uiState.availableUpdate?.let { release ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissAvailableUpdate(skipVersion = false) },
            title = { Text(strings[R.string.ui_new_version, (release.versionName)]) },
            text = {
                Text(
                    release.notes.ifBlank { strings[R.string.ui_a_new_version_is_available] }.take(600),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)))
                        viewModel.dismissAvailableUpdate(skipVersion = false)
                    },
                ) { Text(strings[R.string.ui_update]) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.dismissAvailableUpdate(skipVersion = true) }) {
                        Text(strings[R.string.ui_skip_this_version])
                    }
                    TextButton(onClick = { viewModel.dismissAvailableUpdate(skipVersion = false) }) {
                        Text(strings[R.string.ui_later])
                    }
                }
            },
        )
    }
}

@Composable
private fun AppSearchTabStrip(viewModel: MainViewModel, onBack: (() -> Unit)? = null, titleContent: (@Composable () -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    BrowserTabStrip(
        workspace = viewModel.uiState.browserWorkspace,
        onNew = viewModel::newBrowserTab,
        onSelect = viewModel::selectBrowserTab,
        onShowOverview = { viewModel.showTabOverview(isGallery = false) },
        previews = viewModel.tabPreviews,
        onBack = onBack,
        titleContent = titleContent,
        extraActions = { actions() },
    )
}

@Composable
private fun AppGalleryTabStrip(viewModel: MainViewModel, onBack: (() -> Unit)? = null, showSync: Boolean = false, titleContent: (@Composable () -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val series = viewModel.uiState.galleryWorkspace.activeTab.currentPage as? BrowserPage.LibrarySeries
    BrowserTabStrip(
        workspace = viewModel.uiState.galleryWorkspace,
        onNew = viewModel::newGalleryTab,
        onSelect = viewModel::selectGalleryTab,
        onShowOverview = { viewModel.showTabOverview(isGallery = true) },
        previews = viewModel.tabPreviews,
        onBack = onBack,
        titleOverride = series?.let { viewModel.uiState.presentations[seriesPresentationKey(it.name)]?.value?.title },
        titleContent = titleContent,
        extraActions = {
            actions()
            if (showSync) IconButton(onClick = viewModel::syncLibraryNow,
                enabled = !viewModel.uiState.isLibrarySyncing && viewModel.uiState.selectedProfileId != null,
                modifier = Modifier.semantics { contentDescription = strings[R.string.ui_sync_library_pending, (viewModel.uiState.librarySyncPendingCount)] }) {
                if (viewModel.uiState.isLibrarySyncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else {
                    val pending = viewModel.uiState.librarySyncPendingCount
                    Text(if (pending > 0) "↻ $pending" else "↻", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    viewModel: MainViewModel,
    initialDestinationRoute: String,
    onDestinationChanged: (String) -> Unit,
    onGalleryClick: (Long) -> Unit,
    onGalleryLongClick: (Long) -> Unit,
    onLibraryBookClick: (String) -> Unit,
    onBackToOrigin: (() -> Unit)? = null,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    var selectedRoute by rememberSaveable(initialDestinationRoute) { mutableStateOf(initialDestinationRoute) }
    val inlineSearch = remember(selectedRoute) { InlineSearchState() }
    val selected = MainDestination.entries.firstOrNull { it.route == selectedRoute }
        ?: MainDestination.Browser
    LaunchedEffect(selectedRoute) {
        onDestinationChanged(selectedRoute)
    }
    val navigate: (MainDestination) -> Unit = { destination ->
        if (destination != selected) {
            viewModel.activateMainWorkspace(destination.route)
            selectedRoute = destination.route
        }
    }
    BackHandler(
        enabled = onBackToOrigin != null || when (selected) {
                MainDestination.Browser -> viewModel.canGoBackInBrowserTab()
                MainDestination.Library -> viewModel.canGoBackInGalleryTab()
                else -> false
            },
        onBack = {
            when {
                selected == MainDestination.Browser && viewModel.canGoBackInBrowserTab() -> viewModel.goBackInBrowserTab()
                selected == MainDestination.Library && viewModel.canGoBackInGalleryTab() -> viewModel.goBackInGalleryTab()
                onBackToOrigin != null -> onBackToOrigin()
                selected == MainDestination.Browser -> viewModel.goBackInBrowserTab()
                else -> viewModel.goBackInGalleryTab()
            }
        },
    )

    LaunchedEffect(viewModel.uiState.settingsSectionRequest) {
        if (viewModel.uiState.settingsSectionRequest != null) selectedRoute = MainDestination.Settings.route
    }
    val content: @Composable (PaddingValues) -> Unit = { padding ->
        MainTabContent(
            destination = selected,
            viewModel = viewModel,
            contentPadding = padding,
            onGalleryClick = onGalleryClick,
            onGalleryLongClick = onGalleryLongClick,
            onLibraryBookClick = onLibraryBookClick,
            onNavigate = navigate,
            onBackToOrigin = onBackToOrigin,
        )
    }
    val topBar: @Composable () -> Unit = {
        Column {
            if (selected != MainDestination.Browser && selected != MainDestination.Library && selected != MainDestination.History) {
                TopAppBar(title = { Text(strings[selected.labelResource]) }, actions = {
                    if (selected == MainDestination.Downloads) TextButton(
                        onClick = viewModel::syncDownloadRequests,
                        enabled = viewModel.uiState.selectedProfileId != null && !viewModel.uiState.isDownloadSyncing,
                    ) { Text(strings[if (viewModel.uiState.isDownloadSyncing) R.string.ui_syncing else R.string.ui_sync_with_pc]) }
                })
                if (selected == MainDestination.Downloads) androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            }
            when (selected) {
                MainDestination.Browser -> Unit
                MainDestination.Library -> Unit
                else -> Unit
            }
        }
    }

    CompositionLocalProvider(LocalInlineSearch provides inlineSearch) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= 700.dp) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(modifier = Modifier.fillMaxHeight()) {
                    MainDestination.entries.forEach { destination ->
                        NavigationRailItem(
                            modifier = Modifier.weight(1f),
                            selected = destination == selected,
                            onClick = { navigate(destination) },
                            icon = { MainDestinationIcon(destination) },
                            label = { Text(strings[destination.labelResource], maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        )
                    }
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    topBar = topBar,
                ) { padding -> content(padding) }
            }
        } else {
            Scaffold(
                topBar = topBar,
                bottomBar = {
                    MainBottomNavigation(selected, navigate)
                },
            ) { padding -> content(padding) }
        }
    }
    }
}

@Composable
internal fun MainBottomNavigation(selected: MainDestination, navigate: (MainDestination) -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val labelHeight = with(androidx.compose.ui.platform.LocalDensity.current) { MaterialTheme.typography.labelMedium.lineHeight.toDp() }
    NavigationBar(modifier = Modifier.heightIn(min = 56.dp + labelHeight * 2)) {
        MainDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selected,
                onClick = { navigate(destination) },
                icon = { MainDestinationIcon(destination) },
                label = { Text(strings[if (destination == MainDestination.History) R.string.ui_history_navigation else destination.labelResource], modifier = Modifier.fillMaxWidth(),
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
            )
        }
    }
}

private const val SEARCH_FROM_GALLERY_ROUTE = "search-from-gallery"

@Composable
private fun MainDestinationIcon(destination: MainDestination) {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(24.dp)) {
        when (destination) {
            MainDestination.Browser -> {
                drawCircle(
                    color,
                    radius = size.width * 0.28f,
                    center = Offset(size.width * 0.43f, size.height * 0.42f),
                    style = Stroke(width = size.width * 0.12f),
                )
                drawLine(
                    color,
                    Offset(size.width * 0.63f, size.height * 0.63f),
                    Offset(size.width * 0.88f, size.height * 0.88f),
                    strokeWidth = size.width * 0.13f,
                )
            }
            MainDestination.Library -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.06f, size.height * 0.22f),
                    size = Size(size.width * 0.88f, size.height * 0.7f),
                    cornerRadius = CornerRadius(size.width * 0.16f),
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.14f, size.height * 0.08f),
                    size = Size(size.width * 0.42f, size.height * 0.3f),
                    cornerRadius = CornerRadius(size.width * 0.1f),
                )
                drawRoundRect(
                    color = color.copy(alpha = 0.42f),
                    topLeft = Offset(size.width * 0.31f, size.height * 0.58f),
                    size = Size(size.width * 0.38f, size.height * 0.09f),
                    cornerRadius = CornerRadius(size.width * 0.04f),
                )
            }
            MainDestination.History -> {
                drawCircle(color, size.width * 0.37f, style = Stroke(width = size.width * 0.1f))
                drawLine(color, Offset(size.width * 0.5f, size.height * 0.25f), Offset(size.width * 0.5f, size.height * 0.52f), size.width * 0.08f)
                drawLine(color, Offset(size.width * 0.5f, size.height * 0.52f), Offset(size.width * 0.7f, size.height * 0.62f), size.width * 0.08f)
            }
            MainDestination.Downloads -> {
                drawLine(color, Offset(size.width * 0.5f, size.height * 0.1f), Offset(size.width * 0.5f, size.height * 0.62f), size.width * 0.12f)
                drawLine(color, Offset(size.width * 0.5f, size.height * 0.62f), Offset(size.width * 0.28f, size.height * 0.4f), size.width * 0.12f)
                drawLine(color, Offset(size.width * 0.5f, size.height * 0.62f), Offset(size.width * 0.72f, size.height * 0.4f), size.width * 0.12f)
                drawRoundRect(
                    color,
                    topLeft = Offset(size.width * 0.12f, size.height * 0.72f),
                    size = Size(size.width * 0.76f, size.height * 0.17f),
                    cornerRadius = CornerRadius(size.width * 0.06f),
                )
            }
            MainDestination.Settings -> {
                val center = Offset(size.width / 2f, size.height / 2f)
                val gear = Path().apply {
                    val angles = floatArrayOf(-22.5f, -13f, -10f, 10f, 13f, 22.5f)
                    val radii = floatArrayOf(0.34f, 0.34f, 0.46f, 0.46f, 0.34f, 0.34f)
                    repeat(8) { tooth ->
                        angles.forEachIndexed { point, degrees ->
                            val angle = (tooth * 45 + degrees) * Math.PI / 180
                            val radius = size.width * radii[point]
                            val x = center.x + cos(angle).toFloat() * radius
                            val y = center.y + sin(angle).toFloat() * radius
                            if (tooth == 0 && point == 0) moveTo(x, y) else lineTo(x, y)
                        }
                    }
                    close()
                    addOval(Rect(center, size.width * 0.16f))
                    fillType = PathFillType.EvenOdd
                }
                drawPath(gear, color)
            }
        }
    }
}

@Composable
private fun MainTabContent(
    destination: MainDestination,
    viewModel: MainViewModel,
    contentPadding: PaddingValues,
    onGalleryClick: (Long) -> Unit,
    onGalleryLongClick: (Long) -> Unit,
    onLibraryBookClick: (String) -> Unit,
    onNavigate: (MainDestination) -> Unit,
    onBackToOrigin: (() -> Unit)? = null,
) {
    var connectionSettingsRequested by rememberSaveable { mutableStateOf(false) }
    val pageStates = rememberSaveableStateHolder()
    val openConnectionSettings: () -> Unit = {
        connectionSettingsRequested = true
        onNavigate(MainDestination.Settings)
    }
    AnimatedContent(
        targetState = destination,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        transitionSpec = {
            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
            val enter = slideInHorizontally(
                animationSpec = tween(200, easing = LinearOutSlowInEasing),
                initialOffsetX = { width -> direction * (width / 10) },
            ) + fadeIn(tween(200))
            (enter togetherWith fadeOut(tween(200))).using(SizeTransform(clip = true))
        },
        label = "mainFragmentTransition",
    ) { current ->
        val pageStateKey = when (current) {
            MainDestination.Browser -> "browser:${viewModel.uiState.browserWorkspace.currentOrPreviousPage<BrowserPage.Search>()?.key}"
            MainDestination.Library -> "library:${viewModel.uiState.galleryWorkspace.currentOrPreviousPage<BrowserPage.LibraryHome>()?.key}"
            else -> current.route
        }
        pageStates.SaveableStateProvider(pageStateKey) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (current) {
        MainDestination.Browser -> {
            val search = viewModel.uiState.browserWorkspace.currentOrPreviousPage<BrowserPage.Search>()
            BrowserScreen(
                state = viewModel.uiState,
                tabHeader = { query, actions -> AppSearchTabStrip(viewModel,
                    titleContent = query, actions = actions) },
                contentPadding = contentPadding,
                onQueryChange = viewModel::setSearchQuery,
                onSearch = viewModel::search,
                onOpenSubscriptions = { viewModel.openSubscriptions() },
                onSubscribeCurrent = viewModel::subscribeCurrentSearch,
                onFeedGalleryLoaded = viewModel::rememberFeedGallery,
                onToggleTagFilter = viewModel::toggleSearchTagFilter,
                onCustomBlockedTags = viewModel::setCustomBlockedTags,
                onRecentSearch = viewModel::repeatSearch,
                onClearSearchHistory = viewModel::clearSearchQueryHistory,
                onToggleLanguage = viewModel::togglePreferredLanguage,
                onLoadLanguages = viewModel::loadAvailableLanguages,
                onSelectAnyLanguage = viewModel::selectAnyLanguage,
                onRemoveCustomLanguage = viewModel::removeCustomLanguage,
                onSelectSuggestion = viewModel::selectFilterSuggestion,
                onRefresh = viewModel::refresh,
                onLoadNextPage = viewModel::loadNextPage,
                onLoadPreviousPage = viewModel::loadPreviousSearchPage,
                onJumpToPage = viewModel::jumpToSearchPage,
                onGalleryClick = onGalleryClick,
                onGalleryLongClick = onGalleryLongClick,
                onSearchFacet = viewModel::searchFromFacet,
                onToggleLibraryFavorite = viewModel::toggleLibraryFavorite,
                onUnhideGallery = { viewModel.setLibraryBooksHidden(setOf("online:$it"), false) },
                onConnect = openConnectionSettings,
                pageKey = "${search?.key.orEmpty()}:${search?.resultSessionId.orEmpty()}:${viewModel.uiState.searchJumpRevision}",
                initialScrollAnchorKey = search?.scrollAnchorKey,
                initialScrollIndex = search?.scrollIndex ?: 0,
                initialScrollOffset = search?.scrollOffset ?: 0,
                onScrollChange = { anchor, index, offset, page ->
                    search?.let { viewModel.updateBrowserScroll(it.key, it.resultSessionId, anchor, index, offset, page) }
                },
                onEnsureResults = viewModel::ensureSearchResults,
                onVisibleResultsChange = viewModel::loadVisibleSearchResults,
            )
        }
        MainDestination.Library -> LibraryTabContent(
            viewModel, contentPadding, onLibraryBookClick,
            onBack = { if (!viewModel.goBackInGalleryTab()) onNavigate(MainDestination.Browser) },
            onSearchFacet = { viewModel.searchFromFacet(it); onNavigate(MainDestination.Browser) },
            onSearchLanguage = { viewModel.searchFromLanguage(it); onNavigate(MainDestination.Browser) },
        )
        MainDestination.History -> RecentHistoryScreen(
            state = viewModel.uiState, contentPadding = contentPadding,
            onOpen = viewModel::openHistory, onRemove = viewModel::removeHistory,
            onClear = viewModel::clearHistory, onRecordingChange = viewModel::setHistoryRecording,
            onLoadMetadata = viewModel::loadHistoryMetadata,
            onRefresh = viewModel::refreshHistory,
        )
        MainDestination.Downloads -> {
            DownloadsScreen(
                state = viewModel.uiState,
                contentPadding = contentPadding,
                onRefresh = viewModel::refreshDownloads,
                onPause = viewModel::pauseDownload,
                onResume = viewModel::resumeDownload,
                onRetry = viewModel::retryDownload,
                onRemove = viewModel::removeDownload,
                onClearCompleted = viewModel::clearCompletedDownloads,
                onConnect = openConnectionSettings,
                onLeave = viewModel::resetDownloadConnectionAttempt,
                onRemoveRequest = viewModel::removeDownloadRequest,
                onLocalDownload = viewModel::startLocalDownload,
                onPauseLocal = viewModel::pauseLocalDownload,
                onSyncRequests = viewModel::syncDownloadRequests,
                onRetryRequest = viewModel::retryDesktopDownloadRequest,
                onSetDownloadLocation = viewModel::setDownloadLocation,
            )
        }
        MainDestination.Settings -> {
            SettingsScreen(
                state = viewModel.uiState,
                contentPadding = contentPadding,
                onHostChange = viewModel::setHost,
                onPortChange = viewModel::setPort,
                onDeviceNameChange = viewModel::setDeviceName,
                onCodeChange = viewModel::setPairingCode,
                onTestConnection = viewModel::testConnection,
                onPair = viewModel::pair,
                onSelectProfile = viewModel::selectProfile,
                onRemoveProfile = viewModel::removeProfile,
                onAddLibraryLocation = viewModel::addLibraryLocation,
                onRemoveLibraryLocation = viewModel::removeLibraryLocation,
                onSetDownloadLocation = viewModel::setDownloadLocation,
                onClearDownloadLocation = viewModel::clearDownloadLocation,
                onViewerPreferencesChange = viewModel::updateViewerPreferences,
                onThemeModeChange = viewModel::setThemeMode,
                onAppStyleChange = viewModel::setAppStyle,
                onFontSizeChange = viewModel::setFontSize,
                onGalleryBorderChange = viewModel::setGalleryBorder,
                onCheckForUpdates = { viewModel.checkForUpdates(manual = true) },
                openConnectionRequested = connectionSettingsRequested,
                onConnectionRequestHandled = { connectionSettingsRequested = false },
                onDownloadDestinationChange = viewModel::setDownloadDestination,
                requestedSection = viewModel.uiState.settingsSectionRequest,
                onSectionRequestHandled = viewModel::consumeSettingsSectionRequest,
                onSubscriptionOptionsChange = viewModel::setSubscriptionOptions,
                onManageSubscriptions = {viewModel.openSubscriptions(1)},
                onSyncNow = viewModel::syncLibraryNow,
                onPrepareBackup = viewModel::prepareLocalBackup,
                initialSectionName = viewModel.settingsSectionName,
                onSectionChanged = { viewModel.settingsSectionName = it },
            )
        }
        }
        }
        }
    }
}

@Composable
private fun LibraryTabContent(
    viewModel: MainViewModel,
    contentPadding: PaddingValues,
    onOpenBook: (String) -> Unit,
    onBack: () -> Unit,
    onSearchFacet: (String) -> Unit,
    onSearchLanguage: (String) -> Unit,
    seriesPage: BrowserPage.LibrarySeries? = null,
) {
            val libraryHome = viewModel.uiState.galleryWorkspace.currentOrPreviousPage<BrowserPage.LibraryHome>()
            LibraryScreen(
                state = viewModel.uiState,
                tabHeader = { query, actions -> if (seriesPage == null) AppGalleryTabStrip(viewModel,
                    titleContent = query, actions = actions) else AppGalleryTabStrip(viewModel) },
                onSync = viewModel::syncLibraryNow,
                contentPadding = contentPadding,
                onQueryChange = viewModel::setLibraryQuery,
                onSubmitSearch = { viewModel.submitLibrarySearch() },
                onRecentSearch = { viewModel.submitLibrarySearch(it) },
                onClearSearchHistory = viewModel::clearLibrarySearchHistory,
                onToggleFavoritesFilter = viewModel::toggleLibraryFavoritesFilter,
                onExcludeSeriesChange = viewModel::setLibraryExcludeSeries,
                onReadFilterChange = viewModel::setLibraryReadFilter,
                onVisibilityFilterChange = viewModel::setLibraryVisibilityFilter,
                onSortChange = viewModel::setLibrarySort,
                onLocationChange = viewModel::toggleLibraryLocation,
                onRefresh = viewModel::refreshLibrary,
                onOpenBook = onOpenBook,
                onToggleFavorite = viewModel::toggleLibraryFavorite,
                onToggleSeriesFavorite = viewModel::toggleLibrarySeriesFavorite,
                onToggleRead = viewModel::toggleLibraryRead,
                onAddFavorites = viewModel::addLibraryFavorites,
                onMarkRead = viewModel::markLibraryBooksRead,
                onMarkUnread = viewModel::markLibraryBooksUnread,
                onAssignSeries = viewModel::assignLibrarySeries,
                onAppendSeries = viewModel::appendLibrarySeries,
                onCommitSeriesEdit = viewModel::commitLibrarySeriesEdit,
                onLoadSeriesPreview = viewModel::loadSeriesBookPreview,
                onSaveSeriesPresentation = viewModel::saveGalleryPresentation,
                onSeriesCover = viewModel::changePresentationCover,
                onAcceptRemoteSeriesPresentation = viewModel::acceptRemotePresentation,
                onSelectSuggestion = viewModel::selectLibrarySuggestion,
                onReadBook = viewModel::openLibraryReaderTab,
                onSearchFacet = onSearchFacet,
                onSearchLanguage = onSearchLanguage,
                onSeriesListChange = viewModel::setLibrarySeriesEpisodeList,
                onSetBooksHidden = viewModel::setLibraryBooksHidden,
                onDeleteBooks = viewModel::deleteLibraryBooks,
                onRenameBook = viewModel::renameLibraryBook,
                onRemoveBooksFromSeries = viewModel::removeLibraryBooksFromSeries,
                onRenameSeries = viewModel::renameLibrarySeries,
                onMergeSeries = viewModel::mergeLibrarySeries,
                onDeleteSeries = viewModel::deleteLibrarySeries,
                onAutoCreateSeries = viewModel::autoCreateLibrarySeries,
                onSeriesModeChange = viewModel::setLibrarySeriesMode,
                onSelectedSeriesChange = viewModel::selectLibrarySeries,
                onBackToSearch = onBack,
                pageKey = seriesPage?.key ?: libraryHome?.key.orEmpty(),
                initialScrollAnchorKey = libraryHome?.scrollAnchorKey,
                initialScrollIndex = libraryHome?.scrollIndex ?: 0,
                initialScrollOffset = libraryHome?.scrollOffset ?: 0,
                onScrollChange = { anchor, index, offset -> viewModel.updateGalleryScroll(libraryHome?.key.orEmpty(), anchor, index, offset) },
                seriesPage = seriesPage,
                onSeriesPreviewPosition = { position -> seriesPage?.let { viewModel.updatePreviewScroll(it.key, position) } },
                onSeriesEpisodePosition = { index, offset -> seriesPage?.let { viewModel.updateSeriesEpisodeScroll(it.key, index, offset) } },
            )
}
