package com.doujinzip.android.ui

import com.doujinzip.android.R

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.doujinzip.android.BuildConfig
import com.doujinzip.android.network.toSyncedBookState
import com.doujinzip.android.data.toJson
import com.doujinzip.android.data.matchesDeletedSubscription
import com.doujinzip.android.data.DownloadRequest
import com.doujinzip.android.data.SearchResultCache
import com.doujinzip.android.data.SearchResultIndex
import com.doujinzip.android.data.SearchResultPager
import com.doujinzip.android.data.SearchResultWindow
import com.doujinzip.android.model.RecentHistoryEntry
import com.doujinzip.android.model.HistorySource
import com.doujinzip.android.model.HistoryAction
import com.doujinzip.android.model.recordHistory
import com.doujinzip.android.model.findBook
import com.doujinzip.android.security.RecentHistoryStore
import com.doujinzip.android.model.SearchResult
import com.doujinzip.android.model.GalleryBorderKind
import com.doujinzip.android.model.GalleryBorders
import com.doujinzip.android.model.GalleryBorderStyle
import com.doujinzip.android.security.AppearancePreferenceStore
import com.doujinzip.android.model.SearchQueryEntry
import com.doujinzip.android.model.recordQuery
import com.doujinzip.android.model.combineLanguageSearchResults
import com.doujinzip.android.model.queriesWithPreferredLanguages
import com.doujinzip.android.model.invalidateSearchResults
import com.doujinzip.android.model.isSearchPageInRange
import com.doujinzip.android.data.DownloadRequestStore
import com.doujinzip.android.data.confirmDesktopCompletions
import com.doujinzip.android.data.DownloadTarget
import com.doujinzip.android.data.RequestStatus
import com.doujinzip.android.data.addDownloadRequest
import com.doujinzip.android.data.LibraryScanner
import com.doujinzip.android.data.LibraryArchiveExtractor
import com.doujinzip.android.data.LibraryFileDeleter
import com.doujinzip.android.data.LibraryIdentityStore
import com.doujinzip.android.data.LibraryUploadPreparer
import com.doujinzip.android.model.DesktopProfile
import com.doujinzip.android.model.AppThemeMode
import com.doujinzip.android.model.DownloadQueueItem
import com.doujinzip.android.model.DownloadStatus
import com.doujinzip.android.model.ReaderPosition
import com.doujinzip.android.model.updatePreviewPosition
import com.doujinzip.android.model.updateReaderPosition
import com.doujinzip.android.model.BrowserPage
import com.doujinzip.android.model.BrowserWorkspace
import com.doujinzip.android.model.GallerySummary
import com.doujinzip.android.model.LibraryBook
import com.doujinzip.android.model.CustomSeriesAssignment
import com.doujinzip.android.model.LibraryPage
import com.doujinzip.android.model.LibraryReadFilter
import com.doujinzip.android.model.LibrarySort
import com.doujinzip.android.model.LibraryVisibilityFilter
import com.doujinzip.android.model.SearchFavorite
import com.doujinzip.android.model.StorageLocation
import com.doujinzip.android.model.ViewerPreferences
import com.doujinzip.android.model.closeTab
import com.doujinzip.android.model.goBack
import com.doujinzip.android.model.moveTab
import com.doujinzip.android.model.normalizeSearchSeparators
import com.doujinzip.android.model.searchTokenStart
import com.doujinzip.android.model.applySearchSuggestion
import com.doujinzip.android.model.DownloadDestination
import com.doujinzip.android.model.openTab
import com.doujinzip.android.model.openGalleryInBackground
import com.doujinzip.android.model.BackgroundGalleryTab
import com.doujinzip.android.model.pushPage
import com.doujinzip.android.model.continueLibraryReader
import com.doujinzip.android.model.openLibraryReader
import com.doujinzip.android.model.updateLibrarySeries
import com.doujinzip.android.model.renameSeriesPages
import com.doujinzip.android.model.reconcileSeriesPages
import com.doujinzip.android.data.withoutDeletedBooks
import com.doujinzip.android.model.selectSearchTab
import com.doujinzip.android.model.selectTab
import com.doujinzip.android.model.searchResultSessionIds
import com.doujinzip.android.model.updateLibraryScroll
import com.doujinzip.android.model.updateSearchScroll
import com.doujinzip.android.model.updateActiveSearch
import com.doujinzip.android.model.updateActiveLibraryHome
import com.doujinzip.android.network.CompanionClient
import com.doujinzip.android.data.PresentationStore
import com.doujinzip.android.data.PresentationCovers
import com.doujinzip.android.data.PresentationSync
import com.doujinzip.android.data.PresentationRecord
import com.doujinzip.android.model.GalleryPresentation
import com.doujinzip.android.model.presentationKey
import com.doujinzip.android.model.presentation
import com.doujinzip.android.model.withPresentation
import com.doujinzip.android.network.CompanionApiException
import com.doujinzip.android.network.downloadDefinitelyNotSubmitted
import com.doujinzip.android.network.BookStateSyncResult
import com.doujinzip.android.network.BookStateSyncUpdate
import com.doujinzip.android.network.AppRelease
import com.doujinzip.android.network.SyncedBookState
import com.doujinzip.android.network.EndpointNormalizer
import com.doujinzip.android.network.FilterSuggestion
import com.doujinzip.android.network.HitomiSuggestionClient
import com.doujinzip.android.network.AvailableLanguage
import com.doujinzip.android.network.GitHubReleaseChecker
import com.doujinzip.android.network.SeriesSyncUpdate
import com.doujinzip.android.network.SeriesSyncResult
import com.doujinzip.android.network.isNewerAppVersion
import com.doujinzip.android.security.BrowserPreferenceStore
import com.doujinzip.android.security.LibraryPreferenceStore
import com.doujinzip.android.security.SecureProfileStore
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private data class PreparedLibraryRefresh(
    val aliases: Map<String, String>,
    val hiddenIds: Set<String>,
    val favoriteIds: Set<String>,
    val readIds: Set<String>,
    val progress: Map<String, Int>,
    val pendingBookStateIds: Set<String>,
    val bookStateModifiedTimes: Map<String, Long>,
    val pendingSeriesSyncIds: Set<String>,
    val customSeries: Map<String, CustomSeriesAssignment>,
    val seriesRemovalTimes: Map<String, Long>,
    val localSeries: Map<String, CustomSeriesAssignment>,
    val localSeriesRemovalTimes: Map<String, Long>,
    val customTitles: Map<String, String>,
    val favoriteSeriesNames: Set<String>,
    val allBooks: List<LibraryBook>,
)

data class MainUiState(
    val subscriptions: List<com.doujinzip.android.data.SubscriptionRecord> = emptyList(),
    val subscriptionNotices: List<com.doujinzip.android.data.SubscriptionNotice> = emptyList(),
    val subscriptionOptions: com.doujinzip.android.security.SubscriptionOptions = com.doujinzip.android.security.SubscriptionOptions(),
    val subscriptionsBusy: Boolean = false,
    val subscriptionError: String? = null,
    val subscriptionSyncError: String? = null,
    val showSubscriptions: Boolean = false,
    val subscriptionTab: Int = 0,
    val subscriptionEditor: com.doujinzip.android.model.SearchSubscription? = null,
    val subscriptionSuggestions: List<FilterSuggestion> = emptyList(),
    val isLoadingSubscriptionSuggestions: Boolean = false,
    val isStarting: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val appStyle: com.doujinzip.android.model.AppStyle = com.doujinzip.android.model.AppStyle(),
    val fontSizeSp: Float = com.doujinzip.android.model.DEFAULT_FONT_SIZE_SP,
    val galleryBorders: GalleryBorders = GalleryBorders(),
    val host: String = "",
    val port: String = EndpointNormalizer.DEFAULT_PORT.toString(),
    val pairingCode: String = "",
    val deviceName: String = "",
    val profiles: List<DesktopProfile> = emptyList(),
    val selectedProfileId: String? = null,
    val lastDesktopSyncAt: Long = 0L,
    val browserWorkspace: BrowserWorkspace = BrowserWorkspace.initial(),
    val galleryWorkspace: BrowserWorkspace = BrowserWorkspace.initial(BrowserPage.LibraryHome()),
    val tabOverviewIsGallery: Boolean? = null,
    val browserNavigationRevision: Long = 0,
    val newTabNotice: Int = 0,
    val searchQuery: String = "",
    val searchTagFilters: Set<com.doujinzip.android.model.SearchTagFilter> = emptySet(),
    val customBlockedTags: Set<String> = emptySet(),
    val favoriteName: String = "",
    val searchFavorites: List<SearchFavorite> = emptyList(),
    val searchQueryHistory: List<SearchQueryEntry> = emptyList(),
    val preferredLanguages: Set<String> = emptySet(),
    val customLanguages: Set<String> = emptySet(),
    val availableLanguages: List<AvailableLanguage> = emptyList(),
    val isLoadingLanguages: Boolean = false,
    val languageLoadError: String? = null,
    val filterSuggestions: List<FilterSuggestion> = emptyList(),
    val isLoadingFilterSuggestions: Boolean = false,
    val libraryFilterSuggestions: List<FilterSuggestion> = emptyList(),
    val isLoadingLibrarySuggestions: Boolean = false,
    val knownFilterTokens: Set<String> = emptySet(),
    val viewedGalleryIds: Set<Long> = emptySet(),
    val recentHistory: List<RecentHistoryEntry> = emptyList(),
    val historyRecording: Boolean = true,
    val refreshingGalleryIds: Set<Long> = emptySet(),
    val libraryLocations: List<StorageLocation> = emptyList(),
    val desktopLibraryLocations: List<StorageLocation> = emptyList(),
    val libraryBooks: List<LibraryBook> = emptyList(),
    val presentations: Map<String, PresentationRecord> = emptyMap(),
    val presentationSyncError: String? = null,
    val libraryHiddenIds: Set<String> = emptySet(),
    val activeLibraryBook: LibraryBook? = null,
    val libraryFavoriteIds: Set<String> = emptySet(),
    val libraryFavoriteSeriesNames: Set<String> = emptySet(),
    val libraryReadIds: Set<String> = emptySet(),
    val libraryProgress: Map<String, Int> = emptyMap(),
    val onlineReaderProgress: Map<Long, Int> = emptyMap(),
    val customSeriesByBookId: Map<String, CustomSeriesAssignment> = emptyMap(),
    val libraryQuery: String = "",
    val libraryQueryDraft: String = "",
    val libraryQueryHistory: List<SearchQueryEntry> = emptyList(),
    val libraryFavoritesOnly: Boolean = false,
    val libraryReadFilter: LibraryReadFilter = LibraryReadFilter.ALL,
    val libraryVisibilityFilter: LibraryVisibilityFilter = LibraryVisibilityFilter.VISIBLE,
    val librarySort: LibrarySort = LibrarySort.NEWEST,
    val selectedLibraryLocationUris: Set<String>? = null,
    val librarySeriesMode: Boolean = false,
    val libraryExcludeSeries: Boolean = false,
    val isAutoCreatingSeries: Boolean = false,
    val isLibraryScanning: Boolean = false,
    val isLibraryPullRefreshing: Boolean = false,
    val isLibrarySyncing: Boolean = false,
    val librarySyncPendingCount: Int = 0,
    val libraryLastSyncedAt: Long? = null,
    val librarySyncError: String? = null,
    val libraryScanError: String? = null,
    val isLibraryBookLoading: Boolean = false,
    val seriesPreviewLoadingIds: Set<String> = emptySet(),
    val seriesPreviewErrors: Map<String, String> = emptyMap(),
    val viewerPreferences: ViewerPreferences = ViewerPreferences(),
    val downloadLocation: StorageLocation? = null,
    val downloadDestination: DownloadDestination = DownloadDestination.ASK,
    val pendingDownloadGallery: GallerySummary? = null,
    val downloadFolderRequired: Boolean = false,
    val desktopDownloadConnectionPrompt: Boolean = false,
    val settingsSectionRequest: String? = null,
    val desktopDownloadPath: String? = null,
    val submittedSearchQuery: String = "",
    val submittedSearchQueries: List<String> = emptyList(),
    val galleries: List<GallerySummary> = emptyList(),
    val searchResultWindow: SearchResultWindow? = null,
    val nextSearchPageError: String? = null,
    val activeGallery: GallerySummary? = null,
    val galleryCache: Map<Long, GallerySummary> = emptyMap(),
    val currentPage: Int = 0,
    val hasNextPage: Boolean = false,
    val isLoadingPage: Boolean = false,
    val searchJumpRevision: Int = 0,
    val isRestoringSearch: Boolean = false,
    val isRefreshing: Boolean = false,
    val readerPagesByGalleryId: Map<Long, List<String>> = emptyMap(),
    val readerLoadingGalleryIds: Set<Long> = emptySet(),
    val readerErrorsByGalleryId: Map<Long, String> = emptyMap(),
    val downloadingGalleryIds: Set<Long> = emptySet(),
    val downloadQueue: List<DownloadQueueItem> = emptyList(),
    val downloadRequests: List<DownloadRequest> = emptyList(),
    val isDownloadSyncing: Boolean = false,
    val isDownloadQueueLoading: Boolean = false,
    val downloadQueueError: String? = null,
    val isDownloadConnectionUnavailable: Boolean = false,
    val activeDownloadActionIds: Set<Long> = emptySet(),
    val discoveredServers: List<com.doujinzip.android.network.DiscoveredServer> = emptyList(),
    val isScanningServers: Boolean = false,
    val selectedServerName: String? = null,
    val desktopLibraryReady: Boolean = false,
    val desktopConnectionState: DesktopConnectionState = DesktopConnectionState.IDLE,
    val isBusy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val availableUpdate: AppRelease? = null,
    val isCheckingForUpdates: Boolean = false,
)

enum class DesktopConnectionState { IDLE, CONNECTING, CONNECTED, DISCONNECTED }

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val subscriptionStore = com.doujinzip.android.data.SubscriptionStore.get(application)
    private val subscriptionPreferences = com.doujinzip.android.security.SubscriptionPreferences(application)
    private var subscriptionJob: Job? = null
    private val strings get() = androidx.core.content.ContextCompat.getContextForLanguage(getApplication()).resources
    val tabPreviews = BrowserTabPreviewCache(application)
    private val requestStore = DownloadRequestStore.get(application)
    private val downloadSyncMutex = Mutex()
    private val client = CompanionClient()
    private val suggestionClient = HitomiSuggestionClient()
    private val hitomiClient = com.doujinzip.android.network.HitomiClient()
    private val searchResultCache = SearchResultCache(java.io.File(application.cacheDir, "search-results"))
    private val searchResultPager = SearchResultPager(searchResultCache, ::fetchSearchPage, ::fetchGallerySummaries)
    private val releaseChecker = GitHubReleaseChecker()
    private val updatePreferences = application.getSharedPreferences("updates", Application.MODE_PRIVATE)
    private val profileStore = SecureProfileStore(application)
    private val browserPreferenceStore = BrowserPreferenceStore(application)
    private val appearancePreferenceStore = AppearancePreferenceStore(application)
    private val libraryPreferenceStore = LibraryPreferenceStore(application)
    private val recentHistoryStore = RecentHistoryStore(application)
    private val libraryIdentityStore = LibraryIdentityStore(application)
    private val presentationCovers = PresentationCovers(application)
    private val presentationSyncMutex = Mutex()
    private var presentationReloadRevision = 0L
    private var libraryMutationRevision = 0L
    private fun presentationStore(profileId: String? = uiState.selectedProfileId) = PresentationStore(libraryIdentityStore, profileId)
    private val libraryScanner = LibraryScanner(application, libraryIdentityStore)
    private val libraryArchiveExtractor = LibraryArchiveExtractor(application)
    private val libraryFileDeleter = LibraryFileDeleter(application)
    private val libraryUploadPreparer = LibraryUploadPreparer(application)
    private val suggestionCache = mutableMapOf<String, List<FilterSuggestion>>()
    private var librarySuggestionIndex: Pair<List<LibraryBook>, List<String>>? = null
    private var suggestionJob: Job? = null
    private var libraryQueryJob: Job? = null
    private var downloadAfterFolder: GallerySummary? = null
    private var browserSearchJob: Job? = null
    private var searchWindowJob: Job? = null
    private var requestedSearchPages: IntRange = IntRange.EMPTY
    private var visibleSearchResults: IntRange = 0..0
    private var connectionMonitorJob: Job? = null
    private var downloadRefreshJob: Job? = null
    private var librarySyncDebounceJob: Job? = null
    private var importantLibraryRefreshJob: Job? = null
    private var pendingRefreshIsReconnect = false
    private var successfulDesktopRefreshes = 0L
    private var activeBookPreparationJob: Job? = null
    private var libraryRefreshJob: Job? = null
    private var libraryProfileGeneration = 0L
    private var hasEnteredForeground = false
    private var monitoredProfileId: String? = null
    private var wasDesktopConnected = false
    private var lastDesktopSyncGeneration = 0L
    private var hasDesktopSyncGenerationBaseline = false
    private var notifyOnDownloadReconnect = false
    private val observedCompletedDownloadIds = mutableSetOf<Long>()
    private val pendingHistoryBookIds = mutableSetOf<String>()
    private val seriesSyncMutex = Mutex()
    private val bookStateSyncMutex = Mutex()
    private val bookStateChangeMutex = Mutex()
    private val librarySyncFlushMutex = Mutex()

    var uiState by mutableStateOf(MainUiState(isStarting = true,
        themeMode = browserPreferenceStore.loadThemeMode(),
        galleryBorders = appearancePreferenceStore.loadBorders(),
        fontSizeSp = appearancePreferenceStore.loadFontSize(),
        appStyle = appearancePreferenceStore.loadStyle()))
        private set
    private var navigationPage: BrowserPage = BrowserPage.Search()
    private data class TabLocation(val gallery: Boolean, val tabId: String, val pageKey: String)
    private data class TabReturn(val origin: TabLocation, val destination: TabLocation)
    private val tabReturns = ArrayDeque<TabReturn>()

    private fun readInitialState(): Pair<MainUiState, BrowserPage> {
        val profiles = profileStore.load()
        val subscriptionScope = profiles.firstOrNull()?.id.orEmpty()
        subscriptionStore.migrateFavorites(getApplication(),subscriptionScope)
        subscriptionStore.adoptLocal(subscriptionScope)
        com.doujinzip.android.data.SubscriptionWorker.schedule(getApplication())
        val savedLanguages = browserPreferenceStore.loadPreferredLanguages()
        val loadedWorkspace = browserPreferenceStore.loadBrowserWorkspace()
        val galleryWorkspace = browserPreferenceStore.loadGalleryWorkspace()
        val loadedSearch = loadedWorkspace.activeTab.currentPage as? BrowserPage.Search
        val activeLanguages = if (loadedSearch != null && (loadedSearch.currentPage > 0 || loadedSearch.resultSessionId != null))
            loadedSearch.preferredLanguages else loadedSearch?.preferredLanguages?.takeIf { it.isNotEmpty() } ?: savedLanguages
        val browserWorkspace = loadedWorkspace.updateActiveSearch { search ->
            search.copy(query = if (search.submittedQueries.isNotEmpty()) search.submittedQuery else search.query,
                preferredLanguages = if (search.preferredLanguages.isEmpty()) activeLanguages else search.preferredLanguages)
        }
        val activeSearch = browserWorkspace.activeTab.currentPage as? BrowserPage.Search
        val initial = uiState.copy(
            isStarting = false,
            themeMode = browserPreferenceStore.loadThemeMode(),
            galleryBorders = appearancePreferenceStore.loadBorders(),
            fontSizeSp = appearancePreferenceStore.loadFontSize(),
            appStyle = appearancePreferenceStore.loadStyle(),
            profiles = profiles,
            selectedProfileId = profiles.firstOrNull()?.id,
            lastDesktopSyncAt = browserPreferenceStore.loadLastDesktopSync(),
            browserWorkspace = browserWorkspace,
            galleryWorkspace = galleryWorkspace,
            libraryQuery = (galleryWorkspace.activeTab.currentPage as? BrowserPage.LibraryHome)?.query.orEmpty(),
            libraryQueryDraft = (galleryWorkspace.activeTab.currentPage as? BrowserPage.LibraryHome)?.query.orEmpty(),
            libraryQueryHistory = browserPreferenceStore.loadSearchQueryHistory("library_query_history"),
            searchQuery = activeSearch?.query.orEmpty(),
            searchTagFilters = browserPreferenceStore.loadSearchTagFilters(),
            customBlockedTags = browserPreferenceStore.loadCustomBlockedTags(),
            submittedSearchQuery = activeSearch?.submittedQuery.orEmpty(),
            submittedSearchQueries = activeSearch?.submittedQueries.orEmpty(),
            currentPage = activeSearch?.currentPage ?: 0,
            hasNextPage = activeSearch?.hasNextPage == true,
            searchFavorites = subscriptionStore.records(subscriptionScope).filterNot { it.value.deleted }.map { SearchFavorite(it.value.id,it.value.name,it.value.query,it.value.languages) },
            subscriptionOptions = subscriptionPreferences.load(),
            searchQueryHistory = browserPreferenceStore.loadSearchQueryHistory(),
            preferredLanguages = activeLanguages,
            customLanguages = browserPreferenceStore.loadCustomLanguages(),
            knownFilterTokens = browserPreferenceStore.loadKnownFilterTokens(),
            viewedGalleryIds = browserPreferenceStore.loadViewedGalleryIds(),
            recentHistory = recentHistoryStore.load(),
            historyRecording = recentHistoryStore.isRecording(),
            libraryLocations = browserPreferenceStore.loadLibraryLocations(),
            libraryFavoriteIds = libraryPreferenceStore.loadFavoriteIds(),
            libraryHiddenIds = libraryPreferenceStore.loadHiddenIds(),
            libraryFavoriteSeriesNames = libraryPreferenceStore.loadFavoriteSeriesNames(),
            libraryReadIds = libraryPreferenceStore.loadReadIds(),
            libraryProgress = libraryPreferenceStore.loadProgress(),
            onlineReaderProgress = libraryPreferenceStore.loadOnlineProgress(),
            customSeriesByBookId = libraryPreferenceStore.loadCustomSeries(),
            librarySort = libraryPreferenceStore.loadLibrarySort(),
            libraryFavoritesOnly = libraryPreferenceStore.loadFavoritesOnly(),
            libraryReadFilter = libraryPreferenceStore.loadReadFilter(),
            libraryVisibilityFilter = libraryPreferenceStore.loadVisibilityFilter(),
            librarySyncPendingCount = pendingLibrarySyncCount(),
            viewerPreferences = libraryPreferenceStore.loadViewerPreferences(),
            downloadLocation = browserPreferenceStore.loadDownloadLocation(),
            downloadDestination = browserPreferenceStore.loadDownloadDestination(),
        )
        val page = if (browserPreferenceStore.loadActiveGalleryWorkspace()) galleryWorkspace.activeTab.currentPage
            else browserWorkspace.activeTab.currentPage
        return initial to page
    }

    init {
        viewModelScope.launch {
            val (initial, page) = withContext(Dispatchers.IO) { readInitialState() }
            uiState = initial
            viewModelScope.launch { subscriptionStore.changes.collect { reloadSubscriptions() } }
            navigationPage = page
            viewModelScope.launch { runOperation { reloadPresentations() } }
            viewModelScope.launch {
                requestStore.requests.collect { requests ->
                    val completed = requests.any { request -> request.status == RequestStatus.COMPLETED &&
                        uiState.downloadRequests.none { it.id == request.id && it.status == RequestStatus.COMPLETED } }
                    uiState = uiState.copy(downloadRequests = requests)
                    if (completed) requestImportantLibraryRefresh()
                }
            }
            requestStore.readError?.let { uiState = uiState.copy(downloadQueueError = it) }
            val activeSearch = initial.browserWorkspace.activeTab.currentPage as? BrowserPage.Search
            if (activeSearch?.resultIds.isNullOrEmpty() && (activeSearch?.currentPage ?: 0) > 0) {
                restoreBrowserSearch()
            }
            refreshLibrarySilently()
            checkForUpdates(manual = false)
        }
    }

    private suspend fun reloadSubscriptions() {
        val scope = uiState.selectedProfileId.orEmpty()
        val snapshot = withContext(Dispatchers.IO) { subscriptionStore.records(scope) to subscriptionStore.notices(scope) }
        if(scope != uiState.selectedProfileId.orEmpty()) return
        uiState=uiState.copy(subscriptions=snapshot.first,subscriptionNotices=snapshot.second,
            searchFavorites=snapshot.first.filterNot { it.value.deleted }.map { SearchFavorite(it.value.id,it.value.name,it.value.query,it.value.languages) })
    }
    fun openSubscriptions(tab: Int = 0) {
        uiState=uiState.copy(showSubscriptions=true,subscriptionTab=tab,subscriptionError=null)
        viewModelScope.launch { reloadSubscriptions() }
    }
    fun closeSubscriptions() { editSubscription(null);uiState=uiState.copy(showSubscriptions=false) }
    fun editSubscription(value: com.doujinzip.android.model.SearchSubscription?) {
        suggestionJob?.cancel()
        uiState=uiState.copy(subscriptionEditor=value,subscriptionError=null,subscriptionSuggestions=emptyList(),isLoadingSubscriptionSuggestions=false)
        if(value!=null) { loadAvailableLanguages();refreshFilterSuggestions(value.query,editor=true) }
    }
    fun setSubscriptionQuery(query: String) {
        val value=uiState.subscriptionEditor ?: return
        uiState=uiState.copy(subscriptionEditor=value.copy(query=query))
        refreshFilterSuggestions(query,editor=true)
    }
    fun subscribeCurrentSearch() {
        val query=com.doujinzip.android.network.parseHitomiSearchOrder(normalizeSearchSeparators(uiState.searchQuery)).terms.joinToString(" ")
        openSubscriptions(1)
        editSubscription(com.doujinzip.android.model.SearchSubscription(name=uiState.favoriteName.trim().ifBlank { query },
            query=query,languages=uiState.preferredLanguages,enabled=true))
    }
    fun saveSubscription(value: com.doujinzip.android.model.SearchSubscription) {
        val scope=uiState.selectedProfileId.orEmpty()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { subscriptionStore.save(scope, value, resubscribe = true);com.doujinzip.android.data.SubscriptionWorker.schedule(getApplication()) }
                if(scope == uiState.selectedProfileId.orEmpty()) uiState=uiState.copy(subscriptionEditor=null,favoriteName="",subscriptionError=null)
                reloadSubscriptions(); scheduleLibrarySync(force=true); refreshSubscriptions(syncNow=false)
            } catch(error: Exception) {
                if(error is CancellationException) throw error
                uiState=uiState.copy(subscriptionError=error.message,message=error.message,isError=true)
            }
        }
    }
    fun resolveSubscription(id:String,keepPhone:Boolean) {
        val scope=uiState.selectedProfileId.orEmpty()
        viewModelScope.launch { withContext(Dispatchers.IO) { subscriptionStore.resolve(scope,id,keepPhone) }; reloadSubscriptions(); scheduleLibrarySync(force=true); refreshSubscriptions(syncNow=false) }
    }
    fun refreshSubscriptions(force:Boolean=false,syncNow:Boolean=true) {
        if(subscriptionJob?.isActive == true) return
        val profile=selectedProfile()
        val scope=profile?.id.orEmpty()
        uiState=uiState.copy(subscriptionsBusy=true,subscriptionError=null)
        subscriptionJob=viewModelScope.launch {
            var syncError:String?=null
            var checkError:String?=null
            try {
                syncError=com.doujinzip.android.data.SubscriptionRefresh.run(getApplication(),profile,force,syncNow)
            } catch(error:CancellationException) { throw error }
            catch(error:Exception) { checkError=error.message
            } finally {
                if(scope == uiState.selectedProfileId.orEmpty()) uiState=uiState.copy(subscriptionsBusy=false,subscriptionError=checkError,subscriptionSyncError=syncError)
                reloadSubscriptions()
            }
        }
    }
    private suspend fun syncSubscriptions(profile: DesktopProfile) {
        val error=com.doujinzip.android.data.SubscriptionRefresh.run(getApplication(),profile,forceSync=true,check=false)
        if(selectedProfile()?.id == profile.id) {
            uiState=uiState.copy(subscriptionSyncError=error)
            reloadSubscriptions()
        }
    }
    fun setSubscriptionOptions(options: com.doujinzip.android.security.SubscriptionOptions) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                subscriptionPreferences.save(options)
                com.doujinzip.android.data.SubscriptionWorker.schedule(getApplication())
                com.doujinzip.android.data.SubscriptionNotifications.refresh(getApplication())
            }
            uiState=uiState.copy(subscriptionOptions=options)
        }
    }
    fun markSubscriptionRead(id:Long?=null, returnToSubscriptionNotices: Boolean = false) {
        val scope=uiState.selectedProfileId.orEmpty()
        viewModelScope.launch {
            var gallery:GallerySummary?=null
            withContext(Dispatchers.IO) {
                val ids=id?.let { listOf(it) } ?: subscriptionStore.notices(scope).filterNot { it.read }.map { it.galleryId }
                ids.forEach { subscriptionStore.markRead(scope,it) }
                gallery=id?.let { subscriptionStore.gallery(scope,it) }
                com.doujinzip.android.data.SubscriptionNotifications.refresh(getApplication())
            }
            if(id != null && scope == uiState.selectedProfileId.orEmpty()) {
                closeSubscriptions()
                gallery?.let { uiState=uiState.copy(galleryCache=cacheGalleries(uiState.galleryCache,listOf(it))) }
                selectGallery(id, returnToSubscriptionNotices)
            }
            reloadSubscriptions(); scheduleLibrarySync(force=true)
        }
    }

    fun dismissSubscriptionNotices(ids: Set<Long>) {
        val scope = uiState.selectedProfileId.orEmpty()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    subscriptionStore.dismissNotices(scope, ids)
                    com.doujinzip.android.data.SubscriptionNotifications.refresh(getApplication())
                }
                reloadSubscriptions()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (scope == uiState.selectedProfileId.orEmpty()) {
                    uiState = uiState.copy(subscriptionError = strings[R.string.msg_could_not_delete_items, ids.size])
                }
            }
        }
    }

    fun openSubscriptionNotification(scope:String,galleryId:Long) {
        if(galleryId==0L) { openSubscriptions();return }
        if(galleryId<0 || (scope.isNotEmpty() && uiState.profiles.none { it.id==scope })) return
        if(scope.isNotEmpty()) selectProfile(scope)
        if(scope!=uiState.selectedProfileId.orEmpty()) return
        viewModelScope.launch {
            val exists=withContext(Dispatchers.IO) { subscriptionStore.notices(scope).any { it.galleryId==galleryId } }
            if(exists && scope==uiState.selectedProfileId.orEmpty()) markSubscriptionRead(galleryId)
        }
    }
    fun setHost(value: String) = update { copy(host = value) }

    private fun coverUri(value: GalleryPresentation?): String? = value?.coverHash?.let { hash ->
        presentationCovers.file(hash).takeIf { it.isFile }?.toURI()?.toString()
    }
    private fun presentedBook(book: LibraryBook): LibraryBook {
        return presentedBook(book, uiState.presentations)
    }
    private fun presentedBook(book: LibraryBook, presentations: Map<String, com.doujinzip.android.data.PresentationRecord>): LibraryBook {
        val key = runCatching { book.presentationKey() }.getOrNull() ?: return book
        val value = presentations[key]?.value
        return book.withPresentation(value, coverUri(value))
    }
    private fun presentedGallery(gallery: GallerySummary): GallerySummary {
        val value = uiState.presentations["hitomi:${gallery.id}"]?.value
        return gallery.withPresentation(value, coverUri(value))
    }
    private suspend fun reloadPresentations() {
        val profileId = uiState.selectedProfileId
        val generation = libraryProfileGeneration
        val revision = ++presentationReloadRevision
        val values = withContext(Dispatchers.IO) { presentationStore(profileId).all().associateBy { it.value.key } }
        fun current() = revision == presentationReloadRevision && generation == libraryProfileGeneration && profileId == uiState.selectedProfileId
        while (current()) {
            val base = uiState
            val prepared = withContext(Dispatchers.Default) {
                if (values == base.presentations) return@withContext null
                fun book(value: LibraryBook) = presentedBook(value, values).let { if (it == value) value else it }
                fun gallery(value: GallerySummary): GallerySummary {
                    val presentation = values["hitomi:${value.id}"]?.value
                    return value.withPresentation(presentation, coverUri(presentation)).let { if (it == value) value else it }
                }
                base.copy(presentations = values, libraryBooks = base.libraryBooks.map(::book),
                    activeLibraryBook = base.activeLibraryBook?.let(::book), galleries = base.galleries.map(::gallery),
                    activeGallery = base.activeGallery?.let(::gallery), galleryCache = base.galleryCache.mapValues { gallery(it.value) },
                    librarySyncPendingCount = distinctPendingSyncCount(libraryPreferenceStore.loadPendingSeriesSyncIds(),
                        libraryPreferenceStore.loadPendingBookStateSyncIds(), base.libraryBooks) + values.values.count { it.dirty })
            } ?: return
            if (!current()) return
            val latest = uiState
            if (base.libraryBooks !== latest.libraryBooks || base.activeLibraryBook !== latest.activeLibraryBook ||
                base.galleries !== latest.galleries || base.activeGallery !== latest.activeGallery || base.galleryCache !== latest.galleryCache) continue
            uiState = latest.copy(presentations = values, libraryBooks = prepared.libraryBooks, activeLibraryBook = prepared.activeLibraryBook,
                galleries = prepared.galleries, activeGallery = prepared.activeGallery, galleryCache = prepared.galleryCache,
                librarySyncPendingCount = prepared.librarySyncPendingCount)
            return
        }
    }

    private val serverEditMutex = kotlinx.coroutines.sync.Mutex()
    private val serverEdits by lazy { com.doujinzip.android.data.ServerEditStore(getApplication()) }

    private fun requireDesktopEdit(): DesktopProfile? {
        val profile = selectedProfile()
        if (profile == null || uiState.desktopConnectionState != DesktopConnectionState.CONNECTED || !uiState.desktopLibraryReady || uiState.presentationSyncError != null) {
            uiState = uiState.copy(message = strings[R.string.ui_connect_refresh_before_edit], isError = true)
            return null
        }
        return profile
    }
    private fun submitServerEdit(kind: String, body: org.json.JSONObject) {
        val profile = requireDesktopEdit() ?: return
        val id = java.util.UUID.randomUUID().toString()
        viewModelScope.launch { runOperation {
            withContext(Dispatchers.IO) {
                val arrayKey = when (kind) { "state" -> "mutations"; "series" -> "assignments"; else -> null }
                val array = arrayKey?.let { body.getJSONArray(it) }
                if (array == null || array.length() <= 100) serverEdits.put(profile.id, id, kind, body)
                else (0 until array.length()).toList().chunked(100).forEachIndexed { index, chunk ->
                    serverEdits.put(profile.id, "$id:$index", kind, org.json.JSONObject().put(arrayKey, org.json.JSONArray().apply { chunk.forEach { put(array.getJSONObject(it)) } }))
                }
            }
            try { replayServerEdits(profile) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                uiState = uiState.copy(desktopLibraryReady = false, librarySyncError = error.message)
                throw error
            }
            requestImportantLibraryRefresh()
        } }
    }
    private suspend fun replayServerEdits(profile: DesktopProfile) = serverEditMutex.withLock {
        for ((id, request) in withContext(Dispatchers.IO) { serverEdits.pending(profile.id) }) {
            val response = client.submitServerEdit(profile, request.getString("kind"), request.getJSONObject("body"))
            val results = response.optJSONArray("results")
            val statuses = com.doujinzip.android.data.validateServerEditResponse(request.getString("kind"), request.getJSONObject("body"), response)
            if (profile.id == uiState.selectedProfileId) {
                libraryMutationRevision++
                if (results != null && request.getString("kind") == "series") applyConfirmedSeriesResults(results)
            }
            if (results != null && request.getString("kind") == "state") {
                val states = (0 until results.length()).mapNotNull { results.getJSONObject(it).optJSONObject("state")?.toSyncedBookState() }
                if (profile.id == uiState.selectedProfileId) applySyncedBookStates(states, uiState.libraryBooks)
            }
            val state = results?.optJSONObject(0)?.optJSONObject("state")
            if (state != null && statuses.firstOrNull() in setOf("applied", "duplicate")) withContext(Dispatchers.IO) { serverEdits.advanceProgress(id, request, state) }
            withContext(Dispatchers.IO) { serverEdits.remove(id, request) }
            if (statuses.any { it == "conflict" || it == "not_found" }) uiState = uiState.copy(message = strings[R.string.ui_pc_edit_conflict], isError = true)
        }
    }

    private fun applyConfirmedSeriesResults(results: org.json.JSONArray) {
        val previous = uiState.customSeriesByBookId
        val assignments = previous.toMutableMap()
        val removals = libraryPreferenceStore.loadSeriesRemovalTimes().toMutableMap()
        val bySyncId = (0 until results.length()).map { results.getJSONObject(it) }.associateBy { it.getString("bookSyncId").lowercase() }
        fun updated(book: LibraryBook): LibraryBook {
            val result = book.syncId?.lowercase()?.let(bySyncId::get) ?: return book
            if (result.getString("status") == "not_found") return book
            val version = result.getLong("version")
            if (version < book.syncStateVersion) return book
            val modifiedAt = result.getLong("modifiedAt")
            val assignment = if (result.isNull("name")) null else CustomSeriesAssignment(result.getString("name"), result.getInt("order"), modifiedAt)
            if (assignment == null) { assignments.remove(book.id); removals[book.id] = modifiedAt }
            else { assignments[book.id] = assignment; removals.remove(book.id) }
            return book.copy(syncStateVersion = version, syncedSeries = assignment, syncedSeriesModifiedAt = modifiedAt, hasSyncedSeriesState = true)
        }
        val books = uiState.libraryBooks.map(::updated)
        libraryPreferenceStore.saveCustomSeries(assignments)
        libraryPreferenceStore.saveSeriesRemovalTimes(removals)
        uiState = uiState.copy(libraryBooks = books, customSeriesByBookId = assignments,
            activeLibraryBook = uiState.activeLibraryBook?.let { active -> books.firstOrNull { it.id == active.id } ?: active })
        reconcileLibrarySeriesPages(previous)
    }

    private fun reconcileLibrarySeriesPages(previous: Map<String, CustomSeriesAssignment>) {
        val workspace = uiState.galleryWorkspace.reconcileSeriesPages(previous, uiState.customSeriesByBookId)
        if (workspace == uiState.galleryWorkspace) return
        val page = workspace.tabs.asSequence().flatMap { it.history.asSequence() }.firstOrNull { it.key == navigationPage.key }
        val changedNavigation = page != null && page != navigationPage
        if (changedNavigation) navigationPage = page
        uiState = uiState.copy(galleryWorkspace = workspace,
            browserNavigationRevision = uiState.browserNavigationRevision + if (changedNavigation) 1 else 0)
        browserPreferenceStore.saveGalleryWorkspace(workspace)
    }
    fun saveGalleryPresentation(value: GalleryPresentation, resolveConflict: Boolean = false) {
        if (requireDesktopEdit() == null) return
        val body = org.json.JSONObject().put("mutationId", java.util.UUID.randomUUID().toString()).put("baseVersion", value.version)
            .put("value", value.toJson())
        submitServerEdit("metadata", body)
    }
    fun resolvePresentationConflict(record: com.doujinzip.android.data.PresentationRecord, usePhone: Boolean) {
        if (usePhone) saveGalleryPresentation(record.value.copy(version = record.conflict?.version ?: record.value.version))
        else acceptRemotePresentation(record.value.key)
    }
    fun classifyGallery(id: Long, mode: com.doujinzip.android.model.ViewerReadingMode) {
        uiState.galleryCache[id]?.presentation()?.let { recordReadingMode(it, mode) }
    }
    fun classifyLibraryBook(id: String, mode: com.doujinzip.android.model.ViewerReadingMode) {
        uiState.libraryBooks.firstOrNull { it.id == id }?.let { book -> runCatching { book.presentation() }.getOrNull()?.let { recordReadingMode(it, mode) } }
    }
    private fun recordReadingMode(value: GalleryPresentation, mode: com.doujinzip.android.model.ViewerReadingMode) {
        if (mode == com.doujinzip.android.model.ViewerReadingMode.AUTO) return
        val latest = uiState.presentations[value.key]?.value ?: value
        val webtoon = mode == com.doujinzip.android.model.ViewerReadingMode.WEBTOON
        if (latest.webtoon == webtoon || (latest.webtoon == null && !webtoon)) return
        saveGalleryPresentation(latest.copy(webtoon = webtoon))
    }
    fun acceptRemotePresentation(key: String) {
        val profileId = uiState.selectedProfileId
        viewModelScope.launch { runOperation {
            withContext(Dispatchers.IO) { presentationStore(profileId).acceptRemote(key) }
            if (profileId == uiState.selectedProfileId) reloadPresentations()
        } }
    }
    fun resetPresentationCover(value: GalleryPresentation) {
        if (requireDesktopEdit() == null) return
        val name = uiState.customSeriesByBookId.values.firstOrNull {
            com.doujinzip.android.model.seriesCoverPresentationKey(it.name) == value.key
        }?.name
        val versions = org.json.JSONArray().put(uiState.presentations[value.key]?.value?.version ?: value.version)
        val body = org.json.JSONObject().put("mutationId", java.util.UUID.randomUUID().toString()).put("key", value.key).put("versions", versions)
        if (name != null) {
            body.put("seriesName", name)
            versions.put(uiState.presentations[com.doujinzip.android.model.seriesPresentationKey(name)]?.value?.version ?: 0L)
        }
        submitServerEdit("reset-cover", body)
    }
    fun changePresentationCover(value: GalleryPresentation, source: com.doujinzip.android.model.ReaderImageSource) {
        val profile = requireDesktopEdit() ?: return
        viewModelScope.launch { runOperation {
            val hash = presentationCovers.import(source)
            client.transferCover(profile, hash, presentationCovers.bytes(hash))
            if (profile.id == uiState.selectedProfileId) saveGalleryPresentation(value.copy(coverHash = hash))
        } }
    }
    private suspend fun syncPresentations(profile: DesktopProfile) = presentationSyncMutex.withLock {
        try {
            withContext(Dispatchers.IO) {
                val store = presentationStore(profile.id)
                val archived = store.archiveOfflineEdits() or libraryPreferenceStore.archiveOfflineEdits(profile.id)
                if (archived) uiState = uiState.copy(message = strings[R.string.ui_legacy_edits_saved])
                PresentationSync(client, store, presentationCovers).sync(profile)
            }
            if (uiState.selectedProfileId == profile.id) uiState = uiState.copy(presentationSyncError = null)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (uiState.selectedProfileId == profile.id) {
                uiState = uiState.copy(presentationSyncError = error.message ?: strings[R.string.ui_connect_refresh_before_edit])
            }
        }
        if (uiState.selectedProfileId == profile.id) reloadPresentations()
    }
    fun setPort(value: String) = update { copy(port = value.filter(Char::isDigit).take(5)) }
    fun setPairingCode(value: String) = update {
        copy(pairingCode = value.filter(Char::isDigit).take(PAIRING_CODE_LENGTH))
    }
    fun setDeviceName(value: String) = update { copy(deviceName = value) }
    fun setThemeMode(value: AppThemeMode) {
        browserPreferenceStore.saveThemeMode(value)
        uiState = uiState.copy(themeMode = value)
    }

    fun setAppStyle(style: com.doujinzip.android.model.AppStyle) {
        appearancePreferenceStore.saveStyle(style)
        uiState = uiState.copy(appStyle = style)
    }

    fun setFontSize(size: Float) {
        if (com.doujinzip.android.model.parseAppFontSize(size.toString()) == null) return
        appearancePreferenceStore.saveFontSize(size)
        uiState = uiState.copy(fontSizeSp = size)
    }

    fun setGalleryBorder(kind: GalleryBorderKind, style: GalleryBorderStyle) {
        appearancePreferenceStore.saveBorder(kind, style)
        val borders = uiState.galleryBorders
        uiState = uiState.copy(galleryBorders = when (kind) {
            GalleryBorderKind.NORMAL -> borders.copy(normal = style)
            GalleryBorderKind.SERIES -> borders.copy(series = style)
            GalleryBorderKind.FAVORITE -> borders.copy(favorite = style)
        })
    }
    fun setSearchQuery(value: String) {
        update { copy(searchQuery = value) }
        refreshFilterSuggestions(value)
    }

    fun toggleSearchTagFilter(value: com.doujinzip.android.model.SearchTagFilter) {
        val filters = uiState.searchTagFilters.let { if (value in it) it - value else it + value }
        browserPreferenceStore.saveSearchTagFilters(filters)
        uiState = uiState.copy(searchTagFilters = filters)
        restartFilteredSearch()
    }

    fun setCustomBlockedTags(tags: Set<String>) {
        val normalized=com.doujinzip.android.model.parseBlockedSearchTags(tags.joinToString(" "))
        browserPreferenceStore.saveCustomBlockedTags(normalized)
        uiState=uiState.copy(customBlockedTags=normalized)
        restartFilteredSearch()
    }

    private fun restartFilteredSearch() {
        val workspace = uiState.browserWorkspace.updateActiveSearch {
            it.copy(query = uiState.searchQuery, preferredLanguages = uiState.preferredLanguages)
        }.invalidateSearchResults()
        applyBrowserWorkspace(workspace)
        startBrowserSearch(reset = true)
    }

    fun selectFilterSuggestion(suggestion: FilterSuggestion) {
        setSearchQuery(applySearchSuggestion(uiState.searchQuery, suggestion.token))
    }
    fun selectLibrarySuggestion(suggestion: FilterSuggestion) {
        setLibraryQuery(applySearchSuggestion(uiState.libraryQueryDraft, suggestion.token))
    }
    fun setFavoriteName(value: String) = update { copy(favoriteName = value) }

    fun saveSearchFavorite() {
        val query = uiState.searchQuery.trim()
        if (query.isEmpty()) {
            uiState = uiState.copy(message = strings[R.string.msg_enter_search_conditions_to_save], isError = true)
            return
        }
        saveSubscription(com.doujinzip.android.model.SearchSubscription(name=uiState.favoriteName.trim().ifBlank { query },query=query,languages=uiState.preferredLanguages))
    }

    fun removeSearchFavorite(id: String) {
        uiState.subscriptions.find { it.value.id == id }?.let { saveSubscription(it.value.copy(deleted=true)) }
    }

    fun searchFavorite(favorite: SearchFavorite) {
        uiState = uiState.copy(searchQuery = favorite.query,preferredLanguages=favorite.languages)
        syncActiveSearch()
        search()
    }

    fun loadAvailableLanguages() {
        if (uiState.isLoadingLanguages || uiState.availableLanguages.isNotEmpty()) return
        uiState = uiState.copy(isLoadingLanguages = true, languageLoadError = null)
        viewModelScope.launch {
            try {
                val languages = suggestionClient.getAvailableLanguages()
                uiState = uiState.copy(availableLanguages = languages)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                uiState = uiState.copy(languageLoadError = strings[R.string.msg_could_not_load_languages_check_your_connection_and_ret])
            } finally {
                uiState = uiState.copy(isLoadingLanguages = false)
            }
        }
    }

    fun togglePreferredLanguage(language: String) {
        val normalized = language.trim().lowercase()
        if (normalized.isEmpty()) return
        val languages = uiState.preferredLanguages.toMutableSet().apply {
            if (!add(normalized)) remove(normalized)
        }
        uiState = uiState.copy(preferredLanguages = languages)
    }

    fun selectAnyLanguage() {
        uiState = uiState.copy(preferredLanguages = emptySet())
    }

    fun removeCustomLanguage(language: String) {
        val normalized = language.trim().lowercase()
        val custom = uiState.customLanguages - normalized
        val selected = uiState.preferredLanguages - normalized
        browserPreferenceStore.saveCustomLanguages(custom)
        uiState = uiState.copy(customLanguages = custom, preferredLanguages = selected)
    }

    fun searchFromLanguage(language: String) {
        val normalized = language.trim().lowercase().replace(Regex("\\s+"), "_")
        if (normalized.isEmpty() || normalized == "n/a") return
        val selected = setOf(normalized)
        val query = uiState.searchQuery
            .replace(Regex("(?i)(^|\\s)-?language:[^\\s]+"), " ")
            .trim().replace(Regex("\\s+"), " ")
        openSearchTab(
            query = query,
            preferredLanguages = selected,
        )
    }

    fun searchFromFacet(facet: String) {
        val normalizedFacet = normalizeFilterFacet(facet)
        getApplication<Application>().getSharedPreferences("subscription_feed", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("open-${uiState.selectedProfileId.orEmpty()}", false).apply()
        openSearchTab(normalizedFacet, uiState.preferredLanguages)
    }

    private var serverScanJob: kotlinx.coroutines.Job? = null
    fun scanServers() {
        val previous = serverScanJob
        previous?.cancel()
        serverScanJob = viewModelScope.launch {
            previous?.join()
            uiState = uiState.copy(isScanningServers = true, discoveredServers = emptyList())
            try {
                com.doujinzip.android.network.ServerDiscovery(getApplication(), client).scan { found ->
                    uiState = uiState.copy(discoveredServers = (uiState.discoveredServers.filterNot { it.id == found.id } + found).sortedBy { it.name })
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                uiState = uiState.copy(message = error.message, isError = true)
            } finally { uiState = uiState.copy(isScanningServers = false) }
        }
    }
    fun stopServerScan() { serverScanJob?.cancel() }
    fun selectDiscoveredServer(id: String) {
        val server = uiState.discoveredServers.firstOrNull { it.id == id } ?: return
        stopServerScan()
        val saved = uiState.profiles.singleOrNull()
        if (saved != null) {
            if (saved.serverId != id) { uiState = uiState.copy(message = strings[R.string.connection_disconnect_first], isError = true); return }
            viewModelScope.launch { runOperation {
                val status = client.getStatus(server.baseUrl)
                require(status.serverId == saved.serverId)
                val updated = saved.copy(baseUrl = server.baseUrl)
                client.getLibraryId(updated, com.doujinzip.android.security.pairingIdentity(getApplication()))
                profileStore.save(listOf(updated))
                uiState = uiState.copy(profiles = listOf(updated), desktopLibraryReady = false)
                reloadProfileLibrary()
            } }
        } else {
            val url = java.net.URI(server.baseUrl)
            uiState = uiState.copy(host = url.host, port = url.port.toString(), deviceName = server.name, selectedServerName = server.name, pairingCode = "")
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            runOperation {
                val baseUrl = EndpointNormalizer.normalize(uiState.host, uiState.port)
                val status = client.getStatus(baseUrl)
                notifyOnDownloadReconnect = false
                uiState = uiState.copy(
                    message = strings[R.string.msg_connected_api_v, (status.service), (status.version)] +
                        if (status.pairingAvailable) strings[R.string.msg_pairing_available] else strings[R.string.msg_no_pairing_code],
                    desktopConnectionState = DesktopConnectionState.CONNECTED,
                    isError = false,
                )
            }
        }
    }

    fun pair() {
        viewModelScope.launch {
            runOperation {
                require(uiState.profiles.isEmpty()) { strings[R.string.connection_disconnect_first] }
                val code = uiState.pairingCode
                require(code.length == PAIRING_CODE_LENGTH) { strings[R.string.msg_enter_the_6_digit_pairing_code] }
                val profileName = uiState.deviceName.trim()
                require(profileName.isNotEmpty()) { strings[R.string.msg_enter_a_display_name_for_this_connection] }
                val phoneDeviceName = Build.MODEL.orEmpty().ifBlank { "Android device" }
                val baseUrl = EndpointNormalizer.normalize(uiState.host, uiState.port)
                val status = client.getStatus(baseUrl)
                require(status.pairingAvailable) { strings[R.string.msg_generate_a_new_pairing_code_on_desktop_first] }

                val result = client.pair(baseUrl, code, phoneDeviceName,
                    com.doujinzip.android.security.pairingIdentity(getApplication()))
                notifyOnDownloadReconnect = false
                val profile = DesktopProfile(
                    id = result.libraryId?.let { browserPreferenceStore.rememberLibraryScope(it, it) } ?: result.deviceId,
                    name = profileName,
                    baseUrl = baseUrl,
                    token = result.token,
                    deviceId = result.deviceId,
                    libraryId = result.libraryId,
                    serverId = status.serverId,
                )
                val profiles = listOf(profile)
                profileStore.save(profiles)
                uiState = uiState.copy(
                    profiles = profiles,
                    selectedProfileId = profile.id,
                    pairingCode = "",
                    isDownloadConnectionUnavailable = false,
                    desktopConnectionState = DesktopConnectionState.CONNECTED,
                    message = strings[R.string.msg_paired_with_credential_saved_securely, (profile.name)],
                    isError = false,
                )
                reloadProfileLibrary()
            }
        }
    }

    fun selectProfile(id: String) {
        val profile = uiState.profiles.firstOrNull { it.id == id } ?: return
        if (uiState.selectedProfileId == id && uiState.profiles.size == 1) return
        profileStore.save(listOf(profile))
        uiState = uiState.copy(
            profiles = listOf(profile),
            selectedProfileId = id,
            message = strings[R.string.msg_selected, (profile.name)],
            isError = false,
            downloadQueue = emptyList(),
            downloadQueueError = null,
            isDownloadConnectionUnavailable = false,
            desktopConnectionState = DesktopConnectionState.CONNECTING,
        )
        reloadProfileLibrary()
        restoreBrowserSearch()
    }

    fun removeProfile(id: String) {
        val changed = uiState.selectedProfileId == id
        val profiles = uiState.profiles.filterNot { it.id == id }
        profileStore.save(profiles)
        browserPreferenceStore.saveLastDesktopSync(0)
        uiState = uiState.copy(
            profiles = profiles,
            lastDesktopSyncAt = 0L,
            selectedProfileId = if (uiState.selectedProfileId == id) profiles.firstOrNull()?.id
                else uiState.selectedProfileId,
            downloadQueue = emptyList(),
            downloadQueueError = null,
            isDownloadConnectionUnavailable = false,
            desktopConnectionState = if (profiles.isEmpty()) {
                DesktopConnectionState.IDLE
            } else {
                DesktopConnectionState.CONNECTING
            },
            message = strings[R.string.msg_removed_saved_desktop],
            isError = false,
        )
        if (changed) reloadProfileLibrary()
    }

    private fun reloadProfileLibrary() {
        subscriptionJob?.cancel()
        uiState=uiState.copy(subscriptionsBusy=false,subscriptionSyncError=null)
        uiState = uiState.copy(subscriptions=emptyList(),subscriptionNotices=emptyList(),searchFavorites=emptyList(),subscriptionEditor=null)
        viewModelScope.launch { reloadSubscriptions() }
        viewModelScope.launch { withContext(Dispatchers.IO) {
            com.doujinzip.android.data.SubscriptionWorker.schedule(getApplication())
            com.doujinzip.android.data.SubscriptionNotifications.refresh(getApplication())
        } }
        libraryProfileGeneration++
        librarySuggestionIndex = null
        importantLibraryRefreshJob?.cancel()
        libraryRefreshJob?.cancel()
        activeBookPreparationJob?.cancel()
        uiState = uiState.copy(desktopLibraryReady = false, libraryBooks = emptyList(), activeLibraryBook = null, isLibraryScanning = false,
            isLibraryBookLoading = false, isLibraryPullRefreshing = false, desktopLibraryLocations = emptyList(),
            desktopDownloadPath = null, galleryCache = emptyMap(), galleries = emptyList(), activeGallery = null,
            seriesPreviewLoadingIds = emptySet(), seriesPreviewErrors = emptyMap(), presentations = emptyMap(), presentationSyncError = null)
        viewModelScope.launch { runOperation { reloadPresentations() } }
        refreshLibrarySilently()
    }

    fun search() {
        if (uiState.isLoadingPage) return
        startBrowserSearch(reset = true)
    }

    fun repeatSearch(entry: SearchQueryEntry) {
        if (uiState.isLoadingPage) return
        uiState = uiState.copy(searchQuery = entry.query, preferredLanguages = entry.languages)
        browserPreferenceStore.savePreferredLanguages(entry.languages)
        search()
    }

    fun clearSearchQueryHistory() {
        browserPreferenceStore.saveSearchQueryHistory(emptyList())
        uiState = uiState.copy(searchQueryHistory = emptyList())
    }

    fun openGalleryBackgroundTab(galleryId: Long): BackgroundGalleryTab? {
        libraryBookIdForGallery(galleryId)?.let { bookId ->
            val book = uiState.libraryBooks.first { it.id == bookId }
            val workspace = uiState.galleryWorkspace
            workspace.tabs.firstOrNull { it.groupId == null &&
                (it.currentPage as? BrowserPage.LibraryBook)?.bookId == bookId }?.let {
                return BackgroundGalleryTab(workspace, it.id, alreadyOpen = true, galleryWorkspace = true)
            }
            val next = workspace.openTab(BrowserPage.LibraryBook(bookId, book.title), activate = false)
            val tabId = next.tabs.last().id.takeIf { next != workspace }
            uiState = uiState.copy(galleryWorkspace = next)
            browserPreferenceStore.saveGalleryWorkspace(next)
            return BackgroundGalleryTab(next, tabId, galleryWorkspace = true)
        }
        val gallery = uiState.galleryCache[galleryId]
            ?: uiState.galleries.firstOrNull { it.id == galleryId } ?: return null
        val result = uiState.browserWorkspace.openGalleryInBackground(galleryId, gallery.title)
        if (result.workspace != uiState.browserWorkspace) {
            // Do not navigate or cancel the active search when adding a background tab.
            uiState = uiState.copy(browserWorkspace = result.workspace)
            browserPreferenceStore.saveBrowserWorkspace(result.workspace)
        }
        return result
    }

    fun selectGallery(galleryId: Long, returnToSubscriptionNotices: Boolean = false) {
        val gallery = uiState.galleryCache[galleryId]
            ?: uiState.galleries.firstOrNull { it.id == galleryId }
            ?: return
        val workspace = uiState.browserWorkspace.pushPage(
            BrowserPage.OnlineGallery(galleryId, gallery.title, returnToSubscriptionNotices = returnToSubscriptionNotices),
        )
        uiState = uiState.copy(
            activeGallery = presentedGallery(gallery),
            galleryCache = cacheGalleries(uiState.galleryCache, listOf(gallery)),
        )
        applyBrowserWorkspace(workspace)
    }

    fun openLibraryBookTab(bookId: String) {
        val book = uiState.libraryBooks.firstOrNull { it.id == bookId } ?: return
        openLibraryBook(bookId)
        applyGalleryWorkspace(
            uiState.galleryWorkspace.pushPage(BrowserPage.LibraryBook(bookId, book.title)),
        )
    }

    fun openOnlineReaderTab(galleryId: Long, startPage: Int) {
        val gallery = uiState.galleryCache[galleryId]
            ?: uiState.galleries.firstOrNull { it.id == galleryId }
            ?: return
        applyBrowserWorkspace(
            uiState.browserWorkspace.pushPage(BrowserPage.OnlineReader(
                galleryId = galleryId,
                title = gallery.title,
                startPage = startPage.coerceAtLeast(0),
            )),
        )
    }

    fun openLibraryReaderTab(bookId: String, startPage: Int) {
        val book = uiState.libraryBooks.firstOrNull { it.id == bookId } ?: return
        if (book.isCloud && requireDesktopEdit() == null) return
        applyGalleryWorkspace(
            uiState.galleryWorkspace.openLibraryReader(BrowserPage.LibraryReader(
                bookId = bookId,
                title = book.title,
                startPage = startPage.coerceAtLeast(0),
            )),
        )
    }

    fun openNextLibraryReaderTab(bookId: String, resume: Boolean = false) {
        val workspace = uiState.galleryWorkspace
        val current = workspace.activeTab.currentPage as? BrowserPage.LibraryReader ?: return
        if (current.bookId == bookId) return
        val book = uiState.libraryBooks.firstOrNull { it.id == bookId } ?: return
        openLibraryBook(bookId)
        applyGalleryWorkspace(workspace.continueLibraryReader(BrowserPage.LibraryReader(
            bookId, book.title, if (resume) uiState.libraryProgress[bookId] ?: 0 else 0,
        )))
    }

    fun initialMainDestination(): String = browserPreferenceStore.loadMainDestination()
    fun rememberMainDestination(route: String) = browserPreferenceStore.saveMainDestination(route)
    fun activateMainWorkspace(route: String) {
        tabReturns.clear()
        if (route == "browser" || route == "library") {
            val gallery = route == "library"
            if (gallery) applyGalleryWorkspace(uiState.galleryWorkspace)
            else applyBrowserWorkspace(uiState.browserWorkspace, reload = true)
        }
    }

    fun activeBrowserPage(): BrowserPage =
        (uiState.browserWorkspace.tabs + uiState.galleryWorkspace.tabs).asSequence()
            .flatMap { it.history.asSequence() }.firstOrNull { it.key == navigationPage.key } ?: navigationPage

    fun updatePreviewScroll(pageKey: String, position: ReaderPosition) {
        val browser = uiState.browserWorkspace.updatePreviewPosition(pageKey, position)
        val gallery = uiState.galleryWorkspace.updatePreviewPosition(pageKey, position)
        if (browser != uiState.browserWorkspace) browserPreferenceStore.saveBrowserWorkspace(browser)
        if (gallery != uiState.galleryWorkspace) browserPreferenceStore.saveGalleryWorkspace(gallery)
        if (browser != uiState.browserWorkspace || gallery != uiState.galleryWorkspace) {
            uiState = uiState.copy(browserWorkspace = browser, galleryWorkspace = gallery)
        }
    }

    fun updateReaderSession(pageKey: String, page: Int, offset: Int = 0) {
        val position = ReaderPosition(page.coerceAtLeast(0), offset.coerceAtLeast(0))
        val browser = uiState.browserWorkspace.updateReaderPosition(pageKey, position)
        val gallery = uiState.galleryWorkspace.updateReaderPosition(pageKey, position)
        if (browser != uiState.browserWorkspace) browserPreferenceStore.saveBrowserWorkspace(browser)
        if (gallery != uiState.galleryWorkspace) browserPreferenceStore.saveGalleryWorkspace(gallery)
        if (browser != uiState.browserWorkspace || gallery != uiState.galleryWorkspace) {
            uiState = uiState.copy(browserWorkspace = browser, galleryWorkspace = gallery)
        }
    }

    fun newBrowserTab() {
        val opened = uiState.browserWorkspace.openTab(BrowserPage.Search(preferredLanguages = uiState.preferredLanguages))
        rememberTabReturn(opened, gallery = false)
        notifyNewTab()
        applyBrowserWorkspace(opened)
    }

    fun selectBrowserTab(tabId: String) {
        tabReturns.clear()
        applyBrowserWorkspace(uiState.browserWorkspace.selectTab(tabId), reload = true)
    }

    fun closeBrowserTab(tabId: String) {
        val current = uiState.browserWorkspace
        val closingActiveTab = tabId == current.activeTabId
        val workspace = current.closeTab(tabId, BrowserPage.Search(
            preferredLanguages = uiState.preferredLanguages,
        ))
        if (closingActiveTab) {
            tabReturns.clear()
            applyBrowserWorkspace(workspace, reload = true)
        } else {
            uiState = uiState.copy(browserWorkspace = workspace)
            browserPreferenceStore.saveBrowserWorkspace(workspace)
            removeClosedSearchSessions(current, workspace)
        }
    }

    fun moveBrowserTab(tabId: String, offset: Int) {
        val workspace = uiState.browserWorkspace.moveTab(tabId, offset)
        uiState = uiState.copy(browserWorkspace = workspace)
        browserPreferenceStore.saveBrowserWorkspace(workspace)
    }

    fun canGoBackInBrowserTab(): Boolean = canReturnFromNewTab(false) || uiState.browserWorkspace.activeTab.canGoBack

    fun goBackInBrowserTab(): Boolean {
        if (returnFromNewTab(false)) return true
        if (!canGoBackInBrowserTab()) return false
        val page = uiState.browserWorkspace.activeTab.currentPage
        applyBrowserWorkspace(uiState.browserWorkspace.goBack(), reload = true)
        if (page is BrowserPage.OnlineGallery && page.returnToSubscriptionNotices) openSubscriptions()
        return true
    }

    fun newGalleryTab() {
        val opened = uiState.galleryWorkspace.openTab(BrowserPage.LibraryHome())
        rememberTabReturn(opened, gallery = true)
        notifyNewTab()
        applyGalleryWorkspace(opened)
    }

    fun selectGalleryTab(tabId: String) {
        tabReturns.clear()
        applyGalleryWorkspace(uiState.galleryWorkspace.selectTab(tabId))
    }

    fun closeGalleryTab(tabId: String) {
        val current = uiState.galleryWorkspace
        val closingActiveTab = tabId == current.activeTabId
        val workspace = current.closeTab(tabId, BrowserPage.LibraryHome())
        if (closingActiveTab) {
            tabReturns.clear()
            applyGalleryWorkspace(workspace)
        } else {
            uiState = uiState.copy(galleryWorkspace = workspace)
            browserPreferenceStore.saveGalleryWorkspace(workspace)
        }
    }

    fun moveGalleryTab(tabId: String, offset: Int) {
        val workspace = uiState.galleryWorkspace.moveTab(tabId, offset)
        uiState = uiState.copy(galleryWorkspace = workspace)
        browserPreferenceStore.saveGalleryWorkspace(workspace)
    }

    fun updateTabWorkspace(workspace: BrowserWorkspace, isGallery: Boolean) {
        val previous = if (isGallery) uiState.galleryWorkspace else uiState.browserWorkspace
        if (workspace == previous) return
        if (workspace.activeTabId != previous.activeTabId) {
            tabReturns.clear()
            if (isGallery) applyGalleryWorkspace(workspace) else applyBrowserWorkspace(workspace, reload = true)
        } else if (isGallery) {
            uiState = uiState.copy(galleryWorkspace = workspace)
            browserPreferenceStore.saveGalleryWorkspace(workspace)
        } else {
            uiState = uiState.copy(browserWorkspace = workspace)
            browserPreferenceStore.saveBrowserWorkspace(workspace)
            removeClosedSearchSessions(previous, workspace)
        }
    }

    fun showTabOverview(isGallery: Boolean) {
        uiState = uiState.copy(tabOverviewIsGallery = isGallery)
    }

    fun dismissTabOverview() {
        uiState = uiState.copy(tabOverviewIsGallery = null)
    }

    fun canGoBackInGalleryTab(): Boolean = canReturnFromNewTab(true) || uiState.galleryWorkspace.activeTab.canGoBack

    fun goBackInGalleryTab(): Boolean {
        if (returnFromNewTab(true)) return true
        if (!canGoBackInGalleryTab()) return false
        applyGalleryWorkspace(uiState.galleryWorkspace.goBack())
        return true
    }

    private fun notifyNewTab() {
        uiState = uiState.copy(newTabNotice = uiState.newTabNotice + 1)
    }

    private fun tabWorkspace(gallery: Boolean) = if (gallery) uiState.galleryWorkspace else uiState.browserWorkspace

    private fun tabLocation(workspace: BrowserWorkspace, gallery: Boolean) =
        TabLocation(gallery, workspace.activeTabId, workspace.activeTab.currentPage.key)

    private fun rememberTabReturn(destination: BrowserWorkspace, gallery: Boolean) {
        val originGallery = navigationPage is BrowserPage.LibraryHome || navigationPage is BrowserPage.LibrarySeries ||
            navigationPage is BrowserPage.LibraryBook || navigationPage is BrowserPage.LibraryReader
        val origin = tabLocation(tabWorkspace(originGallery), originGallery)
        val target = tabLocation(destination, gallery)
        // Selecting a search in this tab can rewind its history. Keep the original page reachable.
        if (origin != target) tabReturns.addLast(TabReturn(origin, target))
    }

    private fun workspaceAt(location: TabLocation): BrowserWorkspace? {
        val workspace = tabWorkspace(location.gallery)
        val tab = workspace.tabs.firstOrNull { it.id == location.tabId } ?: return null
        val index = tab.history.indexOfFirst { it.key == location.pageKey }
        if (index < 0) return null
        return workspace.copy(activeTabId = tab.id,
            tabs = workspace.tabs.map { if (it.id == tab.id) it.copy(currentIndex = index) else it })
    }

    fun canReturnFromNewTab(isGallery: Boolean): Boolean =
        tabReturns.lastOrNull()?.let {
            it.destination == tabLocation(tabWorkspace(isGallery), isGallery) && workspaceAt(it.origin) != null
        } == true

    private fun returnFromNewTab(isGallery: Boolean): Boolean {
        val entry = tabReturns.lastOrNull() ?: return false
        if (entry.destination != tabLocation(tabWorkspace(isGallery), isGallery)) return false
        tabReturns.removeLast()
        val origin = workspaceAt(entry.origin) ?: return false
        if (entry.origin.gallery) applyGalleryWorkspace(origin) else applyBrowserWorkspace(origin, reload = true)
        return true
    }

    fun openBackgroundTab(tabId: String, gallery: Boolean) {
        val workspace = tabWorkspace(gallery)
        if (workspace.tabs.none { it.id == tabId }) return
        val selected = workspace.selectTab(tabId)
        rememberTabReturn(selected, gallery)
        if (gallery) applyGalleryWorkspace(selected) else applyBrowserWorkspace(selected, reload = true)
    }

    fun updateBrowserScroll(pageKey: String, sessionId: String?, anchorKey: String?, index: Int, offset: Int, scrollPage: Int) {
        val workspace = uiState.browserWorkspace.updateSearchScroll(pageKey, sessionId, anchorKey, index, offset, scrollPage)
        if (workspace != uiState.browserWorkspace) {
            uiState = uiState.copy(browserWorkspace = workspace)
            browserPreferenceStore.saveBrowserWorkspace(workspace)
        }
    }

    fun updateGalleryScroll(pageKey: String, anchorKey: String?, index: Int, offset: Int) {
        val workspace = uiState.galleryWorkspace.updateLibraryScroll(pageKey, anchorKey, index, offset)
        if (workspace != uiState.galleryWorkspace) {
            uiState = uiState.copy(galleryWorkspace = workspace)
            browserPreferenceStore.saveGalleryWorkspace(workspace)
        }
    }

    private val historyMetadataPermits = kotlinx.coroutines.sync.Semaphore(2)
    fun rememberFeedGallery(gallery: GallerySummary) {
        uiState = uiState.copy(galleryCache = cacheGalleries(uiState.galleryCache, listOf(gallery)))
    }
    suspend fun loadHistoryMetadata(galleryId: Long) {
        historyMetadataPermits.acquire()
        try {
            if (galleryId in uiState.galleryCache) return
            try {
                val gallery = hitomiClient.getGallery(galleryId)
                uiState = uiState.copy(galleryCache = cacheGalleries(uiState.galleryCache, listOf(gallery)))
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: Exception) { /* A missing history thumbnail must not interrupt browsing. */ }
        } finally { historyMetadataPermits.release() }
    }

    fun ensureGalleryLoaded(galleryId: Long) {
        val cached = uiState.galleryCache[galleryId] ?: uiState.galleries.firstOrNull { it.id == galleryId }
        if (cached != null && cached.pageCount > 0) { recordOnlineHistory(cached); return }
        viewModelScope.launch {
            runCatching { hitomiClient.getGallery(galleryId) }.onSuccess { gallery ->
                recordOnlineHistory(gallery)
                uiState = uiState.copy(
                    activeGallery = presentedGallery(gallery),
                    galleryCache = cacheGalleries(uiState.galleryCache, listOf(gallery)),
                )
            }
        }
    }

    fun libraryBookIdForGallery(galleryId: Long): String? =
        preferredLibraryBookForGalleryId(
            uiState.libraryBooks.filterNot { it.id in uiState.libraryHiddenIds },
            galleryId,
        )?.id

    fun startConnectionMonitoring() {
        if (connectionMonitorJob?.isActive == true) return
        connectionMonitorJob = viewModelScope.launch {
            while (true) {
                val profile = selectedProfile()
                if (profile == null) {
                    monitoredProfileId = null
                    wasDesktopConnected = false
                    lastDesktopSyncGeneration = 0L
                    hasDesktopSyncGenerationBaseline = false
                    uiState = uiState.copy(desktopLibraryReady = false, desktopConnectionState = DesktopConnectionState.IDLE)
                    delay(CONNECTION_MONITOR_INTERVAL_MS)
                    continue
                }
                if (monitoredProfileId != profile.id) {
                    monitoredProfileId = profile.id
                    wasDesktopConnected = false
                    lastDesktopSyncGeneration = 0L
                    hasDesktopSyncGenerationBaseline = false
                    uiState = uiState.copy(desktopConnectionState = DesktopConnectionState.CONNECTING)
                }
                val status = runCatching { client.getStatus(profile.baseUrl) }.getOrNull()
                val connected = status != null
                if (connected) {
                    val libraryId = runCatching { client.getLibraryId(profile,
                        com.doujinzip.android.security.pairingIdentity(getApplication())) }.getOrNull()
                    if (selectedProfile() != profile) continue
                    if (libraryId != null && (libraryId != profile.libraryId || (profile.serverId == null && status?.serverId != null))) {
                        val fallback = if (profile.libraryId == null) profile.id else libraryId
                        val scope = browserPreferenceStore.rememberLibraryScope(libraryId, fallback)
                        val updated = profile.copy(id = scope, libraryId = libraryId, serverId = status?.serverId ?: profile.serverId)
                        profileStore.save(listOf(updated))
                        uiState = uiState.copy(profiles = listOf(updated), selectedProfileId = scope)
                        if (scope != profile.id) {
                            reloadProfileLibrary()
                            continue
                        }
                    }
                }
                if (connected && !wasDesktopConnected) {
                    requestReconnectLibraryRefresh()
                    refreshSubscriptions()
                }
                if (status != null) {
                    when {
                        !hasDesktopSyncGenerationBaseline ||
                            status.syncGeneration < lastDesktopSyncGeneration -> {
                            lastDesktopSyncGeneration = status.syncGeneration
                            hasDesktopSyncGenerationBaseline = true
                        }
                        status.syncGeneration > lastDesktopSyncGeneration -> {
                            lastDesktopSyncGeneration = status.syncGeneration
                            requestImportantLibraryRefresh()
                        }
                    }
                    drainBookStateChanges(profile)
                }
                wasDesktopConnected = connected
                if (!connected) uiState = uiState.copy(desktopLibraryReady = false)
                uiState = uiState.copy(
                    desktopConnectionState = if (connected) {
                        DesktopConnectionState.CONNECTED
                    } else {
                        DesktopConnectionState.DISCONNECTED
                    },
                )
                delay(CONNECTION_MONITOR_INTERVAL_MS)
            }
        }
    }

    fun stopConnectionMonitoring() {
        connectionMonitorJob?.cancel()
        connectionMonitorJob = null
    }

    fun loadNextPage() {
        if (uiState.isLoadingPage || !uiState.hasNextPage || uiState.currentPage < 1) return
        startBrowserSearch(reset = false)
    }

    fun jumpToSearchPage(page: Int) {
        if (!isSearchPageInRange(page, uiState.searchResultWindow?.index?.maxPage)) return
        val search = activeSearchPage() ?: return
        val queries = search.submittedQueries.ifEmpty {
            filteredSearchQueries(search.submittedQuery.ifBlank { search.query }, search.preferredLanguages)
        }
        if (queries.isEmpty()) return
        browserSearchJob?.cancel()
        cancelSearchWindow()
        val sessionId = UUID.randomUUID().toString()
        val previousState = uiState
        val previous = uiState.browserWorkspace
        val workspace = previous.updateActiveSearch { it.copy(resultSessionId = sessionId, scrollAnchorKey = null, scrollIndex = 0, scrollOffset = 0, scrollPage = page) }
        uiState = uiState.copy(browserWorkspace = workspace, galleries = emptyList(),
            searchResultWindow = SearchResultWindow(SearchResultIndex(sessionId, queries)),
            isLoadingPage = true, currentPage = 0, hasNextPage = false, nextSearchPageError = null)
        val revision = uiState.browserNavigationRevision
        browserSearchJob = viewModelScope.launch {
            try {
                val loaded = searchResultPager.seek(sessionId, queries, page)
                if (!isSearchSessionCurrent(search.key, sessionId, revision)) return@launch
                val targetOffset = loaded.index.pages.take(page - loaded.index.firstPage).sumOf { it.size }
                val anchor = loaded.index.ids.getOrNull(targetOffset)?.let { "gallery:$it" }
                val ready = uiState.browserWorkspace.updateActiveSearch { it.copy(
                    scrollAnchorKey = anchor, scrollIndex = targetOffset + 2, scrollOffset = 0,
                    scrollPage = page, currentPage = loaded.index.lastPage,
                ) }
                visibleSearchResults = targetOffset..targetOffset
                uiState = uiState.copy(browserWorkspace = ready, searchResultWindow = loaded, galleries = loaded.galleries.map(::presentedGallery),
                    galleryCache = cacheGalleries(uiState.galleryCache, loaded.galleries),
                    currentPage = loaded.index.lastPage, hasNextPage = loaded.index.hasNextPage,
                    searchJumpRevision = uiState.searchJumpRevision + 1)
                syncActiveSearch(persist = true)
                removeClosedSearchSessions(previous, uiState.browserWorkspace)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (isSearchSessionCurrent(search.key, sessionId, revision)) uiState = uiState.copy(
                    browserWorkspace = previousState.browserWorkspace, searchResultWindow = previousState.searchResultWindow,
                    galleries = previousState.galleries, currentPage = previousState.currentPage,
                    hasNextPage = previousState.hasNextPage, isLoadingPage = false,
                    message = error.message ?: strings[R.string.msg_could_not_change_page], isError = true)
            } finally {
                if (isSearchSessionCurrent(search.key, sessionId, revision)) uiState = uiState.copy(isLoadingPage = false)
            }
        }
    }

    fun loadPreviousSearchPage() {
        val window = uiState.searchResultWindow ?: return
        if (window.index.firstPage <= 1 || uiState.isLoadingPage || uiState.isRestoringSearch) return
        val search = activeSearchPage() ?: return
        val revision = uiState.browserNavigationRevision
        cancelSearchWindow()
        browserSearchJob = viewModelScope.launch {
            uiState = uiState.copy(isLoadingPage = true)
            try {
                val loaded = searchResultPager.prepend(window.index)
                if (!isSearchSessionCurrent(search.key, window.index.sessionId, revision)) return@launch
                val added = loaded.index.pages.first().size
                visibleSearchResults = (visibleSearchResults.first + added)..(visibleSearchResults.last + added)
                val pages = loaded.index.window(visibleSearchResults.first, visibleSearchResults.last)
                publishSearchWindow(loaded.copy(residentPages = window.residentPages + loaded.residentPages).retainPages(pages))
                uiState = uiState.copy(galleryCache = cacheGalleries(uiState.galleryCache, loaded.galleries))
                syncActiveSearch(persist = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (isSearchSessionCurrent(search.key, window.index.sessionId, revision)) uiState = uiState.copy(message = error.message, isError = true)
            } finally {
                if (isSearchSessionCurrent(search.key, window.index.sessionId, revision)) uiState = uiState.copy(isLoadingPage = false)
            }
        }
    }

    fun refresh() {
        if (uiState.isLoadingPage || uiState.isRefreshing) return
        val page = activeSearchPage() ?: return
        val searchKey = page.key
        uiState = uiState.copy(searchQuery = if (page.submittedQueries.isNotEmpty()) page.submittedQuery else page.query,
            preferredLanguages = page.preferredLanguages)
        browserSearchJob = viewModelScope.launch {
            uiState = uiState.copy(isRefreshing = true)
            try {
                loadPage(reset = true, preserveResults = true, searchKey = searchKey)
            } finally {
                if (isActiveSearch(searchKey)) uiState = uiState.copy(isRefreshing = false)
            }
        }
    }

    fun loadReader(galleryId: Long) {
        if (galleryId in uiState.readerLoadingGalleryIds) return
        if (uiState.readerPagesByGalleryId[galleryId].isNullOrEmpty().not()) return
        viewModelScope.launch {
            uiState = uiState.copy(
                readerLoadingGalleryIds = uiState.readerLoadingGalleryIds + galleryId,
                readerErrorsByGalleryId = uiState.readerErrorsByGalleryId - galleryId,
            )
            try {
                val pages = hitomiClient.getGalleryPages(galleryId)
                uiState = uiState.copy(
                    readerPagesByGalleryId = uiState.readerPagesByGalleryId + (galleryId to pages),
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    readerErrorsByGalleryId = uiState.readerErrorsByGalleryId +
                        (galleryId to (error.message ?: strings[R.string.msg_could_not_load_pages])),
                )
            } finally {
                uiState = uiState.copy(
                    readerLoadingGalleryIds = uiState.readerLoadingGalleryIds - galleryId,
                )
            }
        }
    }

    fun refreshGalleryDetail(galleryId: Long) {
        if (galleryId in uiState.refreshingGalleryIds || galleryId in uiState.readerLoadingGalleryIds) return
        viewModelScope.launch {
            uiState = uiState.copy(refreshingGalleryIds = uiState.refreshingGalleryIds + galleryId,
                readerErrorsByGalleryId = uiState.readerErrorsByGalleryId - galleryId)
            requestImportantLibraryRefresh()
            refreshDownloads(silent = true)
            try {
                val gallery = hitomiClient.getGallery(galleryId)
                val pages = hitomiClient.getGalleryPages(galleryId)
                uiState = uiState.copy(galleryCache = cacheGalleries(uiState.galleryCache, listOf(gallery)),
                    activeGallery = if (uiState.activeGallery?.id == galleryId) presentedGallery(gallery) else uiState.activeGallery,
                    readerPagesByGalleryId = uiState.readerPagesByGalleryId + (galleryId to pages))
                importantLibraryRefreshJob?.join()
                libraryRefreshJob?.join()
                downloadRefreshJob?.join()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                uiState = uiState.copy(readerErrorsByGalleryId = uiState.readerErrorsByGalleryId +
                    (galleryId to (error.message ?: strings[R.string.msg_could_not_load_pages])))
            } finally {
                uiState = uiState.copy(refreshingGalleryIds = uiState.refreshingGalleryIds - galleryId)
            }
        }
    }

    fun setDownloadDestination(value: DownloadDestination) {
        browserPreferenceStore.saveDownloadDestination(value)
        uiState = uiState.copy(downloadDestination = value)
        if (value == DownloadDestination.DESKTOP) showDesktopDownloadConnectionPrompt()
    }
    private fun showDesktopDownloadConnectionPrompt() {
        if (uiState.desktopConnectionState != DesktopConnectionState.CONNECTED && !browserPreferenceStore.isDesktopDownloadAlertSuppressed())
            uiState = uiState.copy(desktopDownloadConnectionPrompt = true)
    }
    fun dismissDesktopDownloadConnectionPrompt(suppress: Boolean) {
        if (suppress) browserPreferenceStore.suppressDesktopDownloadAlert(true)
        uiState = uiState.copy(desktopDownloadConnectionPrompt = false)
    }
    fun requestSettingsSection(section: String) {
        uiState = uiState.copy(settingsSectionRequest = section, pendingDownloadGallery = null, downloadFolderRequired = false,
            desktopDownloadConnectionPrompt = false)
    }
    var settingsSectionName: String = "HOME"

    fun consumeSettingsSectionRequest() { uiState = uiState.copy(settingsSectionRequest = null) }
    fun cancelDownloadPrompt() {
        downloadAfterFolder = null
        uiState = uiState.copy(pendingDownloadGallery = null, downloadFolderRequired = false)
    }
    fun chooseDownloadDestination(value: DownloadDestination) {
        val gallery = uiState.pendingDownloadGallery ?: return
        uiState = uiState.copy(pendingDownloadGallery = null, downloadFolderRequired = false)
        enqueueGalleryDownload(gallery, value)
    }
    fun downloadGallery(gallery: GallerySummary) {
        if (gallery.id in uiState.downloadingGalleryIds) return
        if (uiState.allDownloadDestinationsOwned(gallery.id, requestStore.requests.value)) {
            uiState = uiState.copy(message = strings[R.string.ui_completed], isError = false)
            return
        }
        if (requestStore.requests.value.any { it.galleryId == gallery.id && (!it.isFinished || it.status == RequestStatus.SENT) }) {
            uiState = uiState.copy(message = strings[R.string.msg_already_in_the_download_list])
            return
        }
        if (uiState.downloadDestination == DownloadDestination.ASK) {
            uiState = uiState.copy(pendingDownloadGallery = gallery, downloadFolderRequired = false)
        } else enqueueGalleryDownload(gallery, uiState.downloadDestination)
    }
    private fun enqueueGalleryDownload(gallery: GallerySummary, destination: DownloadDestination) {
        if (destination == DownloadDestination.ASK || gallery.id in uiState.downloadingGalleryIds) return
        if (requestStore.requests.value.any { it.galleryId == gallery.id && !it.isFinished }) return
        val local = destination == DownloadDestination.DEVICE
        if (uiState.hasDownloadedGallery(gallery.id, destination, requestStore.requests.value)) {
            uiState = uiState.copy(message = strings[R.string.ui_completed], isError = false)
            return
        }
        val location = uiState.downloadLocation
        if (local && location == null) {
            downloadAfterFolder = gallery
            uiState = uiState.copy(pendingDownloadGallery = gallery, downloadFolderRequired = true)
            return
        }
        val profile = if (local) null else selectedProfile()
        val request = DownloadRequest(galleryId = gallery.id, title = gallery.title, thumbnailUrl = gallery.thumbnailUrl,
            target = if (local) DownloadTarget.LOCAL else DownloadTarget.DESKTOP, profileId = profile?.id,
            locationUri = if (local) location?.uri else null)
        uiState = uiState.copy(downloadingGalleryIds = uiState.downloadingGalleryIds + gallery.id)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { requestStore.update { addDownloadRequest(it, request) } }
                if (local) {
                    if (location != null && uiState.libraryLocations.none { it.uri == location.uri }) addLibraryLocation(location.uri, location.displayName)
                    startLocalDownloadService()
                    uiState = uiState.copy(message = strings[R.string.msg_added_to_device_downloads], isError = false)
                } else if (profile != null && uiState.desktopConnectionState == DesktopConnectionState.CONNECTED) {
                    sendPendingDownloadRequests(profile, onlyRequestId = request.id, requireRequestIds = false)
                } else {
                    uiState = uiState.copy(message = strings[R.string.msg_added_to_pc_sync_queue_no_automatic_device_download], isError = false)
                    showDesktopDownloadConnectionPrompt()
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (local) withContext(Dispatchers.IO) { runCatching { requestStore.change(request.id) { it.copy(status = RequestStatus.PAUSED, error = error.message) } } }
                uiState = uiState.copy(message = strings[R.string.msg_download_request_failed, error.message], isError = true)
            } finally { uiState = uiState.copy(downloadingGalleryIds = uiState.downloadingGalleryIds - gallery.id) }
        }
    }
    private fun startLocalDownloadService() {
        val context = getApplication<Application>()
        context.startForegroundService(android.content.Intent(context, com.doujinzip.android.data.LocalDownloadService::class.java))
    }

    fun startLocalDownload(id: String) {
        val request = requestStore.requests.value.firstOrNull { it.id == id } ?: return
        if (!request.canDownloadLocally) return
        if (uiState.hasDownloadedGallery(request.galleryId, DownloadDestination.DEVICE, requestStore.requests.value)) {
            uiState = uiState.copy(downloadQueueError = strings[R.string.msg_this_gallery_is_already_in_the_device_library])
            return
        }
        val location = uiState.downloadLocation ?: run {
            uiState = uiState.copy(downloadQueueError = strings[R.string.msg_choose_a_download_folder_first])
            return
        }
        // Register the chosen folder once; the existing scanner/reader handles completed CBZs.
        if (uiState.libraryLocations.none { it.uri == location.uri }) addLibraryLocation(location.uri, location.displayName)
        viewModelScope.launch {
            try {
                var claimed = false
                withContext(Dispatchers.IO) {
                    requestStore.change(id) {
                        if (!it.canDownloadLocally) it else {
                            claimed = true
                            it.copy(target = DownloadTarget.LOCAL, status = RequestStatus.WAITING,
                                profileId = null, locationUri = location.uri, error = null)
                        }
                    }
                }
                if (!claimed) return@launch
                startLocalDownloadService()
                uiState = uiState.copy(downloadQueueError = null, message = strings[R.string.msg_added_to_device_downloads], isError = false)
            } catch (error: Exception) {
                runCatching { requestStore.change(id) { it.copy(status = RequestStatus.PAUSED, error = error.message) } }
                uiState = uiState.copy(downloadQueueError = error.message)
            }
        }
    }

    fun pauseLocalDownload(id: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { requestStore.change(id) {
                    if (it.target == DownloadTarget.LOCAL && !it.isFinished) it.copy(
                        status = if (it.status in setOf(RequestStatus.RUNNING, RequestStatus.PAUSING)) RequestStatus.PAUSING else RequestStatus.PAUSED,
                    ) else it
                } }
            } catch (error: Exception) { uiState = uiState.copy(downloadQueueError = error.message) }
        }
    }

    fun removeDownloadRequest(id: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val request = requestStore.requests.value.firstOrNull { it.id == id } ?: return@withContext
                    if (request.isBusy) return@withContext
                    requestStore.update { list -> list.filterNot { it.id == id && !it.isBusy } }
                    com.doujinzip.android.data.stagingDirectory(getApplication<Application>().filesDir, id).deleteRecursively()
                    if (!request.isFinished) request.outputUri?.let { uri ->
                        runCatching { android.provider.DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, android.net.Uri.parse(uri)) }
                    }
                }
            } catch (error: Exception) {
                uiState = uiState.copy(downloadQueueError = error.message)
            }
        }
    }

    fun dismissMessage() {
        uiState = uiState.copy(message = null, isError = false)
    }

    fun checkForUpdates(manual: Boolean = true) {
        if (uiState.isCheckingForUpdates) return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            uiState = uiState.copy(isCheckingForUpdates = true)
            try {
                val release = releaseChecker.latestRelease()
                val skipped = updatePreferences.getString(SKIPPED_UPDATE_KEY, null)
                when {
                    isNewerAppVersion(release.versionName, BuildConfig.VERSION_NAME) &&
                        (manual || skipped != release.versionName) -> {
                        uiState = uiState.copy(availableUpdate = release)
                    }
                    manual -> {
                        uiState = uiState.copy(
                            message = strings[R.string.msg_version_is_up_to_date, (BuildConfig.VERSION_NAME)],
                            isError = false,
                        )
                    }
                }
            } catch (error: Exception) {
                if (manual) {
                    uiState = uiState.copy(
                        message = strings[R.string.msg_could_not_check_for_updates, (error.message ?: strings[R.string.msg_network_error])],
                        isError = true,
                    )
                }
            } finally {
                updatePreferences.edit().putLong(LAST_UPDATE_CHECK_KEY, now).apply()
                uiState = uiState.copy(isCheckingForUpdates = false)
            }
        }
    }

    fun dismissAvailableUpdate(skipVersion: Boolean) {
        val version = uiState.availableUpdate?.versionName
        if (skipVersion && version != null) {
            updatePreferences.edit().putString(SKIPPED_UPDATE_KEY, version).apply()
        }
        uiState = uiState.copy(availableUpdate = null)
    }

    fun addLibraryLocation(uri: String, displayName: String) {
        val location = StorageLocation(uri, displayName)
        val locations = (uiState.libraryLocations + location).distinctBy { it.uri }
        browserPreferenceStore.saveLibraryLocations(locations)
        uiState = uiState.copy(libraryLocations = locations)
        refreshLibrarySilently()
    }

    fun removeLibraryLocation(uri: String) {
        val locations = uiState.libraryLocations.filterNot { it.uri == uri }
        browserPreferenceStore.saveLibraryLocations(locations)
        uiState = uiState.copy(
            libraryLocations = locations,
            selectedLibraryLocationUris = uiState.selectedLibraryLocationUris?.filterTo(linkedSetOf()) {
                selected -> locations.any { it.uri == selected } ||
                    uiState.desktopLibraryLocations.any { it.uri == selected }
            },
        )
        refreshLibrarySilently()
    }

    fun refreshLibrary() = refreshLibrary(showPullFeedback = true)

    private fun refreshLibrarySilently() = refreshLibrary(showPullFeedback = false)

    private fun refreshLibrary(showPullFeedback: Boolean) {
        if (uiState.isLibraryScanning) return
        val scannedLocations = uiState.libraryLocations
        val profile = selectedProfile()
        val generation = libraryProfileGeneration
        val mutationRevision = libraryMutationRevision
        fun current() = generation == libraryProfileGeneration && profile?.id == uiState.selectedProfileId
        libraryRefreshJob = viewModelScope.launch {
            uiState = uiState.copy(
                isLibraryScanning = true,
                isLibraryPullRefreshing = showPullFeedback,
                libraryScanError = null,
            )
            try {
            val result = libraryScanner.scan(scannedLocations)
            if (!current()) return@launch
            if (profile != null) {
                runCatching { replayServerEdits(profile) }.onFailure {
                    if (it is CancellationException) throw it
                    uiState = uiState.copy(librarySyncError = it.message)
                }
                syncSubscriptions(profile); syncPresentations(profile)
            }
            val cloudResult = profile?.let { runCatching { client.getLibraryBooks(it) } }
            if (!current()) return@launch
            if (cloudResult?.isSuccess == true) successfulDesktopRefreshes++
            val cloudBooks = when {
                profile == null -> emptyList()
                cloudResult?.isSuccess == true -> cloudResult.getOrThrow()
                else -> uiState.libraryBooks.filter(LibraryBook::isCloud)
            }
            val identityReconciliation = withContext(Dispatchers.IO) {
                libraryIdentityStore.reconcileWithDesktop(result.books, cloudBooks)
            }
            val localBooks = identityReconciliation.books
            val cloudError = cloudResult?.exceptionOrNull()?.let { error ->
                strings[R.string.msg_could_not_load_desktop_library, (error.message ?: strings[R.string.msg_unknown_error])]
            }
            val desktopDownloadPath = profile?.let {
                runCatching { client.getDesktopDownloadPath(it) }.getOrNull()
            }
            if (!current()) return@launch
            val librarySnapshotComplete = result.errors.isEmpty() && (profile == null || cloudResult?.isSuccess == true)
            var prepared: PreparedLibraryRefresh
            while (true) {
                val preferences = libraryPreferenceStore.snapshot()
                val presentations = uiState.presentations
                val previousBooks = uiState.libraryBooks
                prepared = withContext(Dispatchers.Default) {
                    prepareLibraryRefresh(localBooks, cloudBooks, profile, identityReconciliation.idAliases,
                        librarySnapshotComplete, presentations, previousBooks.associateBy(LibraryBook::id))
                }
                if (!current()) return@launch
                if (mutationRevision != libraryMutationRevision) return@launch
                // A user edit during preparation must win over this older scan snapshot.
                if (libraryPreferenceStore.matchesSnapshot(preferences) && presentations === uiState.presentations) break
            }
            val legacyIdAliases = prepared.aliases
            val hiddenIds = prepared.hiddenIds
            val favoriteIds = prepared.favoriteIds
            val readIds = prepared.readIds
            val progress = prepared.progress
            val pendingBookStateIds = prepared.pendingBookStateIds
            val bookStateModifiedTimes = prepared.bookStateModifiedTimes
            val pendingSeriesSyncIds = prepared.pendingSeriesSyncIds
            val customSeries = prepared.customSeries
            val seriesRemovalTimes = prepared.seriesRemovalTimes
            val localSeries = prepared.localSeries
            val localSeriesRemovalTimes = prepared.localSeriesRemovalTimes
            val customTitles = prepared.customTitles
            val favoriteSeriesNames = prepared.favoriteSeriesNames
            val allBooks = prepared.allBooks
            if (
                legacyIdAliases.isNotEmpty() ||
                cloudBooks.any(LibraryBook::hasSyncedSeriesState) ||
                customSeries != localSeries ||
                seriesRemovalTimes != localSeriesRemovalTimes
            ) {
                libraryPreferenceStore.saveHiddenIds(hiddenIds)
                libraryPreferenceStore.saveFavoriteIds(favoriteIds)
                libraryPreferenceStore.saveReadIds(readIds)
                libraryPreferenceStore.saveProgress(progress)
                libraryPreferenceStore.saveCustomSeries(customSeries)
                libraryPreferenceStore.saveSeriesRemovalTimes(seriesRemovalTimes)
                libraryPreferenceStore.savePendingSeriesSyncIds(pendingSeriesSyncIds)
                libraryPreferenceStore.savePendingBookStateSyncIds(pendingBookStateIds)
                libraryPreferenceStore.saveBookStateModifiedTimes(bookStateModifiedTimes)
                libraryPreferenceStore.saveCustomTitles(customTitles)
                libraryPreferenceStore.saveFavoriteSeriesNames(favoriteSeriesNames)
            }
            // Pending IDs can outlive deleted or migrated books. Persist the sanitized queues
            // even when no other library preference changed so an impossible item cannot stay
            // visible as "대기" forever.
            libraryPreferenceStore.savePendingSeriesSyncIds(pendingSeriesSyncIds)
            libraryPreferenceStore.savePendingBookStateSyncIds(pendingBookStateIds)
            libraryPreferenceStore.saveBookStateModifiedTimes(bookStateModifiedTimes)
            val previousActiveBook = uiState.activeLibraryBook
            val savedActiveBookId = previousActiveBook?.id ?: libraryPreferenceStore.loadActiveBookId()
            val refreshedActiveBook = savedActiveBookId?.let { savedId ->
                val activeId = legacyIdAliases[savedId] ?: savedId
                allBooks.firstOrNull { it.id == activeId }
                    ?.withPreservedReaderPages(previousActiveBook)
            }
            if (refreshedActiveBook != null && refreshedActiveBook.id != savedActiveBookId) {
                libraryPreferenceStore.saveActiveBookId(refreshedActiveBook.id)
            } else if (savedActiveBookId != null && refreshedActiveBook == null && librarySnapshotComplete) {
                libraryPreferenceStore.saveActiveBookId(null)
            }
            val readerReadyBooks = refreshedActiveBook?.let { active ->
                allBooks.map { if (it.id == active.id) active else it }
            } ?: allBooks
            val previousSeries = uiState.customSeriesByBookId
            uiState = uiState.copy(
                libraryBooks = readerReadyBooks,
                libraryHiddenIds = hiddenIds,
                libraryFavoriteIds = favoriteIds,
                libraryReadIds = readIds,
                libraryProgress = progress,
                customSeriesByBookId = customSeries,
                libraryFavoriteSeriesNames = favoriteSeriesNames,
                desktopLibraryLocations = cloudBooks.distinctBy(LibraryBook::locationUri).map { book ->
                    StorageLocation(book.locationUri, book.locationName, isCloud = true)
                },
                desktopDownloadPath = desktopDownloadPath,
                desktopLibraryReady = cloudResult?.isSuccess == true,
                activeLibraryBook = refreshedActiveBook,
                isLibraryBookLoading = refreshedActiveBook?.hasResolvedReaderPages() == false,
                isLibraryScanning = false,
                isLibraryPullRefreshing = false,
                libraryScanError = (result.errors + listOfNotNull(cloudError))
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString("\n"),
                librarySyncPendingCount = distinctPendingSyncCount(
                    pendingSeriesSyncIds,
                    pendingBookStateIds,
                    readerReadyBooks,
                ) + uiState.presentations.values.count { it.dirty },
                libraryLastSyncedAt = if (
                    profile != null &&
                    cloudError == null &&
                    pendingSeriesSyncIds.isEmpty() &&
                    pendingBookStateIds.isEmpty()
                ) {
                    System.currentTimeMillis()
                } else uiState.libraryLastSyncedAt,
            )
            if (librarySnapshotComplete) reconcileLibrarySeriesPages(previousSeries)
            refreshedActiveBook
                ?.takeUnless(LibraryBook::hasResolvedReaderPages)
                ?.let(::prepareActiveLibraryBook)
            if (profile != null && (pendingSeriesSyncIds.isNotEmpty() || pendingBookStateIds.isNotEmpty())) {
                flushPendingLibrarySync(profile, readerReadyBooks)
            }
            if (uiState.isLibrarySyncing) {
                val remaining = pendingLibrarySyncCount()
                uiState = uiState.copy(
                    isLibrarySyncing = false,
                    librarySyncPendingCount = remaining,
                    libraryLastSyncedAt = if (remaining == 0) {
                        System.currentTimeMillis()
                    } else uiState.libraryLastSyncedAt,
                    librarySyncError = if (remaining == 0) {
                        null
                    } else {
                        uiState.librarySyncError
                    },
                    message = if (remaining == 0) {
                        strings[R.string.msg_library_sync_complete]
                    } else {
                        strings[R.string.msg_changes_still_need_syncing, (remaining)]
                    },
                    isError = remaining > 0,
                )
            } else if (showPullFeedback) {
                val refreshError = uiState.libraryScanError
                uiState = uiState.copy(
                    message = if (refreshError == null) {
                        strings[R.string.msg_library_refreshed]
                    } else {
                        refreshError
                    },
                    isError = refreshError != null,
                )
            }
            if (uiState.libraryLocations != scannedLocations) refreshLibrarySilently()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (current()) uiState = uiState.copy(libraryScanError = error.message, isLibraryScanning = false, isLibraryPullRefreshing = false)
            } finally {
                if (current()) uiState = uiState.copy(isLibraryScanning = false, isLibraryPullRefreshing = false)
                if (current() && mutationRevision != libraryMutationRevision) requestImportantLibraryRefresh()
            }
        }
    }

    private fun prepareLibraryRefresh(
        localBooks: List<LibraryBook>, cloudBooks: List<LibraryBook>, profile: DesktopProfile?,
        identityAliases: Map<String, String>, librarySnapshotComplete: Boolean,
        presentations: Map<String, com.doujinzip.android.data.PresentationRecord>, previousBooks: Map<String, LibraryBook>,
    ): PreparedLibraryRefresh {
        val legacyDesktopIdAliases = profile?.let { selected ->
            cloudBooks.mapNotNull { book ->
                val remoteId = book.remoteBookId ?: return@mapNotNull null
                val legacyId = "desktop:${selected.id}:$remoteId"
                (legacyId to book.id).takeIf { legacyId != book.id }
            }.toMap()
        }.orEmpty()
        val legacyIdAliases = legacyDesktopIdAliases +
            identityAliases +
            legacyLocalLibraryIdAliases(localBooks)
        val hiddenIds = remapLibraryIds(libraryPreferenceStore.loadHiddenIds(), legacyIdAliases).toMutableSet()
        val favoriteIds = remapLibraryIds(libraryPreferenceStore.loadFavoriteIds(), legacyIdAliases).toMutableSet()
        val readIds = remapLibraryIds(libraryPreferenceStore.loadReadIds(), legacyIdAliases).toMutableSet()
        val progress = remapLibraryIdKeys(libraryPreferenceStore.loadProgress(), legacyIdAliases).toMutableMap()
        var pendingBookStateIds = remapLibraryIds(
            libraryPreferenceStore.loadPendingBookStateSyncIds(),
            legacyIdAliases,
        )
        val bookStateModifiedTimes = remapLibraryIdKeys(
            libraryPreferenceStore.loadBookStateModifiedTimes(),
            legacyIdAliases,
        ).toMutableMap()
        var pendingSeriesSyncIds = remapLibraryIds(
            libraryPreferenceStore.loadPendingSeriesSyncIds(),
            legacyIdAliases,
        )
        val localSeries = remapLibraryIdKeys(
            libraryPreferenceStore.loadCustomSeries(),
            legacyIdAliases,
        )
        val localSeriesRemovalTimes = remapLibraryIdKeys(
            libraryPreferenceStore.loadSeriesRemovalTimes(),
            legacyIdAliases,
        )
        val seriesMerge = mergeSyncedSeries(
            local = localSeries,
            removalTimes = localSeriesRemovalTimes,
            books = cloudBooks,
            pendingIds = pendingSeriesSyncIds,
        )
        val rawBooks = localBooks + cloudBooks
        val knownSyncableIds = rawBooks.asSequence()
            .filter { it.syncId != null }
            .mapTo(hashSetOf(), LibraryBook::id)
        val knownBookIds = rawBooks.mapTo(hashSetOf(), LibraryBook::id)
        val customSeries = if (librarySnapshotComplete) {
            seriesMerge.assignments.filterKeys { it in knownBookIds }
        } else {
            seriesMerge.assignments
        }
        val seriesRemovalTimes = if (librarySnapshotComplete) {
            seriesMerge.removalTimes.filterKeys { it in knownBookIds }
        } else {
            seriesMerge.removalTimes
        }
        pendingSeriesSyncIds += seriesMerge.localWinnerIds.filter { it in knownSyncableIds }
        val customTitles = remapLibraryIdKeys(libraryPreferenceStore.loadCustomTitles(), legacyIdAliases).toMutableMap()
        val favoriteSeriesNames = libraryPreferenceStore.loadFavoriteSeriesNames().toMutableSet()
        if (librarySnapshotComplete) {
            pendingBookStateIds = pendingBookStateIds.filterTo(linkedSetOf()) {
                it in knownSyncableIds
            }
        }
        pendingSeriesSyncIds = sanitizePendingSeriesSyncIds(
            pending = pendingSeriesSyncIds,
            knownSyncableIds = knownSyncableIds,
            librarySnapshotComplete = librarySnapshotComplete,
            assignments = customSeries,
            removalTimes = seriesRemovalTimes,
        )
        val remoteBySyncId = cloudBooks.mapNotNull { remote ->
            remote.syncId?.lowercase()?.let { it to remote }
        }.toMap()
        rawBooks.forEach { book ->
            val remote = book.syncId?.lowercase()?.let(remoteBySyncId::get) ?: return@forEach
            if (book.id in pendingBookStateIds) return@forEach
            hiddenIds.setMembership(book.id, remote.syncedHidden)
            favoriteIds.setMembership(book.id, remote.syncedFavorite)
            readIds.setMembership(book.id, remote.syncedRead)
            progress[book.id] = remote.syncedProgress
            if (remote.syncedCustomTitle == null) customTitles.remove(book.id)
            else customTitles[book.id] = remote.syncedCustomTitle
            bookStateModifiedTimes[book.id] = remote.syncedStateModifiedAt
            remote.syncedSeries?.name?.let { seriesName ->
                favoriteSeriesNames.setMembership(
                    seriesName,
                    remote.syncedSeriesFavorite,
                )
            }
        }
        val allBooks = (localBooks + cloudBooks).map { book ->
                val customTitle = customTitles[book.id]
                if (customTitle != null) book.copy(title = customTitle, originalTitle = customTitle)
                else book.copy(title = preferredLocalizedTitle(book.originalTitle))
            }.map { book ->
                val presented = presentedBook(book, presentations)
                previousBooks[presented.id]?.takeIf { it == presented } ?: presented
            }
        return PreparedLibraryRefresh(legacyIdAliases, hiddenIds, favoriteIds, readIds, progress, pendingBookStateIds, bookStateModifiedTimes, pendingSeriesSyncIds, customSeries, seriesRemovalTimes, localSeries, localSeriesRemovalTimes, customTitles, favoriteSeriesNames, allBooks)
    }

    fun setLibraryQuery(value: String) {
        uiState = uiState.copy(libraryQueryDraft = value)
        refreshFilterSuggestions(value, library = true)
        libraryQueryJob?.cancel()
        libraryQueryJob = viewModelScope.launch {
            delay(250)
            if (uiState.libraryQueryDraft == value) applyLibrarySearch(value, record = false)
        }
    }
    fun submitLibrarySearch(query: String = uiState.libraryQueryDraft) {
        libraryQueryJob?.cancel()
        applyLibrarySearch(query, record = true)
    }
    private fun applyLibrarySearch(query: String, record: Boolean) {
        val applied = normalizeSearchSeparators(query)
        val history = if (record) uiState.libraryQueryHistory.recordQuery(SearchQueryEntry(applied)) else uiState.libraryQueryHistory
        val workspace = uiState.galleryWorkspace.updateActiveLibraryHome { it.copy(query = applied, scrollAnchorKey = null, scrollIndex = 0, scrollOffset = 0) }
        if (record) browserPreferenceStore.saveSearchQueryHistory(history, "library_query_history")
        browserPreferenceStore.saveGalleryWorkspace(workspace)
        uiState = uiState.copy(libraryQuery = applied, libraryQueryDraft = if (record) applied else uiState.libraryQueryDraft,
            libraryQueryHistory = history, galleryWorkspace = workspace)
    }
    fun clearLibrarySearchHistory() {
        browserPreferenceStore.saveSearchQueryHistory(emptyList(), "library_query_history")
        uiState = uiState.copy(libraryQueryHistory = emptyList())
    }
    fun toggleLibraryFavoritesFilter() {
        val value = !uiState.libraryFavoritesOnly
        libraryPreferenceStore.saveFavoritesOnly(value)
        update { copy(libraryFavoritesOnly = value) }
    }
    fun setLibraryReadFilter(value: LibraryReadFilter) {
        libraryPreferenceStore.saveReadFilter(value)
        update { copy(libraryReadFilter = value) }
    }
    fun setLibraryVisibilityFilter(value: LibraryVisibilityFilter) {
        libraryPreferenceStore.saveVisibilityFilter(value)
        update { copy(libraryVisibilityFilter = value) }
    }
    fun setLibrarySort(value: LibrarySort) {
        libraryPreferenceStore.saveLibrarySort(value)
        update { copy(librarySort = value) }
    }
    fun toggleLibraryLocation(uri: String) = update {
        val allUris = (libraryLocations + desktopLibraryLocations).mapTo(linkedSetOf()) { it.uri }
        val current = selectedLibraryLocationUris ?: allUris
        val toggled = current.toMutableSet().apply {
            if (!add(uri)) remove(uri)
        }
        copy(
            selectedLibraryLocationUris = toggled.takeUnless { it.containsAll(allUris) },
        )
    }
    fun setLibrarySeriesMode(enabled: Boolean) = update {
        copy(librarySeriesMode = enabled, libraryExcludeSeries = false)
    }
    fun setLibraryExcludeSeries(enabled: Boolean) = update {
        copy(libraryExcludeSeries = enabled, librarySeriesMode = false)
    }
    fun selectLibrarySeries(name: String?) {
        if (name == null) { goBackInGalleryTab(); return }
        applyGalleryWorkspace(uiState.galleryWorkspace.pushPage(BrowserPage.LibrarySeries(name)))
    }
    fun setLibrarySeriesEpisodeList(show: Boolean) {
        val page = uiState.galleryWorkspace.activeTab.currentPage as? BrowserPage.LibrarySeries ?: return
        applyGalleryWorkspace(uiState.galleryWorkspace.updateLibrarySeries(page.key) { it.copy(episodeList = show) })
    }
    fun openLibrarySeriesList(name: String) {
        applyGalleryWorkspace(uiState.galleryWorkspace.pushPage(BrowserPage.LibrarySeries(name, episodeList = true)))
    }
    fun updateSeriesEpisodeScroll(pageKey: String, index: Int, offset: Int) {
        val workspace = uiState.galleryWorkspace.updateLibrarySeries(pageKey) {
            it.copy(episodePosition = ReaderPosition(index.coerceAtLeast(0), offset.coerceAtLeast(0)))
        }
        if (workspace != uiState.galleryWorkspace) {
            uiState = uiState.copy(galleryWorkspace = workspace)
            browserPreferenceStore.saveGalleryWorkspace(workspace)
        }
    }

    fun commitLibrarySeriesEdit(name: String, baseline: Map<String, CustomSeriesAssignment>, orderedIds: List<String>): Boolean {
        if (requireDesktopEdit() == null) return false
        if (orderedIds.distinct().size != orderedIds.size || orderedIds.any { id -> uiState.libraryBooks.none { it.id == id } }) return false
        val current = uiState.customSeriesByBookId
        fun membership(values: Map<String, CustomSeriesAssignment>) = values.filterValues { it.name == name }.mapValues { it.value.order }
        if (membership(current) != membership(baseline)) return false
        if (orderedIds.any { id -> current[id]?.let { it.name to it.order } != baseline[id]?.let { it.name to it.order } }) return false
        val assignments = com.doujinzip.android.model.replaceSeriesMembers(current, name, orderedIds, System.currentTimeMillis())
        return persistCustomSeries(assignments)
    }

    fun openLibraryBook(bookId: String) {
        val book = uiState.libraryBooks.firstOrNull { it.id == bookId } ?: return
        libraryPreferenceStore.saveActiveBookId(bookId)
        uiState = uiState.copy(
            activeLibraryBook = book,
            isLibraryBookLoading = !book.hasResolvedReaderPages(),
            libraryScanError = null,
        )
        recordLibraryHistory(book)

        prepareActiveLibraryBook(book)
        if (book.metadata.publishedDate == null) book.metadata.hitomiId?.toLongOrNull()?.let { id ->
            viewModelScope.launch {
                try {
                    val gallery = hitomiClient.getGallery(id)
                    gallery.publishedDate?.let { date ->
                        withContext(Dispatchers.IO) { libraryIdentityStore.cachePublishedDate(id.toString(), date) }
                        val updated = uiState.libraryBooks.map { current -> if (current.metadata.hitomiId == id.toString())
                            current.copy(metadata = current.metadata.copy(publishedDate = date)) else current }
                        uiState = uiState.copy(libraryBooks = updated, activeLibraryBook = updated.firstOrNull { it.id == uiState.activeLibraryBook?.id },
                            galleryCache = cacheGalleries(uiState.galleryCache, listOf(gallery)))
                    }
                } catch (error: CancellationException) { throw error } catch (_: Exception) { /* Upload date is optional offline. */ }
            }
        }
    }

    private fun prepareActiveLibraryBook(book: LibraryBook) {
        val generation = libraryProfileGeneration
        if (book.hasResolvedReaderPages()) {
            if (uiState.activeLibraryBook?.id == book.id) {
                uiState = uiState.copy(isLibraryBookLoading = false)
            }
            return
        }
        val preparation: suspend () -> LibraryBook = if (book.isCloud) {
            val profile = selectedProfile()
            val remoteId = book.remoteBookId
            if (profile == null || remoteId == null) {
                if (uiState.activeLibraryBook?.id == book.id) {
                    uiState = uiState.copy(
                        isLibraryBookLoading = false,
                        libraryScanError = strings[R.string.msg_reconnect_to_desktop_and_retry],
                    )
                }
                return
            }
            suspend {
                val urls = client.getLibraryBookPages(profile, remoteId, book.modifiedAt)
                book.copy(
                    pages = urls.mapIndexed { index, url -> LibraryPage(url, "${index + 1}") },
                    coverUriOverride = book.coverUri,
                )
            }
        } else {
            suspend { libraryArchiveExtractor.materialize(book) }
        }
        activeBookPreparationJob?.cancel()
        activeBookPreparationJob = viewModelScope.launch {
            runCatching { preparation() }
                .onSuccess { prepared ->
                    if (generation != libraryProfileGeneration) return@onSuccess
                    val activeMatches = uiState.activeLibraryBook?.id == prepared.id
                    val display = presentedBook(prepared).let { ready -> ready.copy(metadata = ready.metadata.copy(
                        publishedDate = ready.metadata.publishedDate ?: uiState.libraryBooks.firstOrNull { it.id == ready.id }?.metadata?.publishedDate)) }
                    uiState = uiState.copy(
                        activeLibraryBook = if (activeMatches) display else uiState.activeLibraryBook,
                        libraryBooks = uiState.libraryBooks.map {
                            if (it.id == prepared.id) display else it
                        },
                        isLibraryBookLoading = if (activeMatches) false else uiState.isLibraryBookLoading,
                        libraryScanError = if (activeMatches) null else uiState.libraryScanError,
                    )
                }
                .onFailure { error ->
                    if (generation == libraryProfileGeneration && uiState.activeLibraryBook?.id == book.id) {
                        if (book.isCloud) requestImportantLibraryRefresh()
                        uiState = uiState.copy(
                            isLibraryBookLoading = false,
                            libraryScanError = error.message ?: if (book.isCloud) {
                                strings[R.string.msg_could_not_load_desktop_pages]
                            } else {
                                strings[R.string.msg_could_not_open_archive]
                            },
                        )
                    }
                }
        }
    }

    fun loadSeriesBookPreview(bookId: String) {
        val book = uiState.libraryBooks.firstOrNull { it.id == bookId } ?: return
        if (book.hasResolvedReaderPages() || bookId in uiState.seriesPreviewLoadingIds) return
        val profile = selectedProfile()
        val generation = libraryProfileGeneration
        uiState = uiState.copy(seriesPreviewLoadingIds = uiState.seriesPreviewLoadingIds + bookId,
            seriesPreviewErrors = uiState.seriesPreviewErrors - bookId)
        viewModelScope.launch {
            try {
                val prepared = if (book.isCloud) {
                    check(profile != null && book.remoteBookId != null) { strings[R.string.msg_reconnect_to_desktop_and_retry] }
                    val pages = client.getLibraryBookPages(profile, book.remoteBookId, book.modifiedAt)
                    book.copy(pages = pages.mapIndexed { index, url -> LibraryPage(url, "${index + 1}") }, coverUriOverride = book.coverUri)
                } else libraryArchiveExtractor.materialize(book)
                if (generation == libraryProfileGeneration && selectedProfile()?.id == profile?.id) uiState = uiState.copy(libraryBooks = uiState.libraryBooks.map {
                    if (it.id == bookId && it.folderUri == book.folderUri) presentedBook(prepared) else it
                })
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (generation == libraryProfileGeneration && selectedProfile()?.id == profile?.id) uiState = uiState.copy(seriesPreviewErrors = uiState.seriesPreviewErrors +
                    (bookId to (error.message ?: strings[R.string.msg_could_not_open_archive])))
            } finally { if (generation == libraryProfileGeneration) uiState = uiState.copy(seriesPreviewLoadingIds = uiState.seriesPreviewLoadingIds - bookId) }
        }
    }

    private fun submitBookFields(ids: Set<String>, fields: org.json.JSONObject) {
        if (requireDesktopEdit() == null) return
        val onlineIds = ids.filter { it.startsWith("online:") }.mapTo(hashSetOf()) { it.removePrefix("online:") }
        val books = uiState.libraryBooks.filter { it.id in ids || it.metadata.hitomiId in onlineIds }.distinctBy { it.syncId }
        if (books.isEmpty() || books.any { it.syncId == null }) {
            uiState = uiState.copy(message = strings[R.string.ui_connect_refresh_before_edit], isError = true)
            return
        }
        val mutations = org.json.JSONArray()
        books.forEach { book ->
            val patch = org.json.JSONObject(fields.toString()).put("mutationId", java.util.UUID.randomUUID().toString())
                .put("bookSyncId", book.syncId).put("baseVersion", book.syncStateVersion)
            mutations.put(patch)
        }
        submitServerEdit("state", org.json.JSONObject().put("mutations", mutations))
    }
    fun toggleLibraryFavorite(bookId: String) {
        submitBookFields(setOf(bookId), org.json.JSONObject().put("isFavorite", bookId !in uiState.libraryFavoriteIds))
    }
    fun toggleLibrarySeriesFavorite(seriesName: String) {
        submitBookFields(uiState.customSeriesByBookId.filterValues { it.name == seriesName }.keys,
            org.json.JSONObject().put("seriesFavorite", seriesName !in uiState.libraryFavoriteSeriesNames))
    }
    fun toggleLibraryRead(bookId: String) {
        submitBookFields(setOf(bookId), org.json.JSONObject().put("isRead", bookId !in uiState.libraryReadIds))
    }

    fun markOnlineRead(id: Long, read: Boolean) {
        val ids = if (read) uiState.viewedGalleryIds + id else uiState.viewedGalleryIds - id
        browserPreferenceStore.saveViewedGalleryIds(ids)
        uiState = uiState.copy(viewedGalleryIds = ids)
    }

    fun addLibraryFavorites(bookIds: Set<String>) = submitBookFields(bookIds, org.json.JSONObject().put("isFavorite", true))
    fun markLibraryBooksRead(bookIds: Set<String>) = submitBookFields(bookIds, org.json.JSONObject().put("isRead", true))
    fun markLibraryBooksUnread(bookIds: Set<String>) = submitBookFields(bookIds, org.json.JSONObject().put("isRead", false))

    fun assignLibrarySeries(bookIds: Set<String>, seriesName: String) {
        val name = seriesName.trim()
        if (name.isEmpty()) return
        if (!ensureValidSeriesName(name)) return
        val validIds = bookIds.intersect(uiState.libraryBooks.mapTo(hashSetOf()) { it.id })
        val assignments = com.doujinzip.android.model.appendSeriesMembers(uiState.customSeriesByBookId, validIds, name, System.currentTimeMillis())
        persistCustomSeries(assignments)
    }

    fun appendLibrarySeries(bookIds: Set<String>, name: String): Boolean {
        if (uiState.customSeriesByBookId.values.none { it.name == name }) {
            uiState = uiState.copy(message = strings[R.string.ui_series_no_longer_exists], isError = true)
            return false
        }
        assignLibrarySeries(bookIds, name)
        return true
    }

    fun autoCreateLibrarySeries() {
        if (uiState.isAutoCreatingSeries) return
        val candidates = uiState.libraryBooks.filter {
            it.id !in uiState.customSeriesByBookId && it.id !in uiState.libraryHiddenIds
        }
        if (candidates.size < 2) {
            uiState = uiState.copy(
                message = strings[R.string.msg_not_enough_ungrouped_galleries_to_generate_series],
                isError = false,
            )
            return
        }
        uiState = uiState.copy(
            isAutoCreatingSeries = true,
            message = null,
            isError = false,
        )
        viewModelScope.launch {
            try {
                val groups = withContext(Dispatchers.Default) {
                    val parents = IntArray(candidates.size) { it }
                    fun root(index: Int): Int {
                        var value = index
                        while (parents[value] != value) {
                            parents[value] = parents[parents[value]]
                            value = parents[value]
                        }
                        return value
                    }
                    fun join(left: Int, right: Int) {
                        val leftRoot = root(left)
                        val rightRoot = root(right)
                        if (leftRoot != rightRoot) parents[rightRoot] = leftRoot
                    }
                    candidates.indices.forEach { left ->
                        val leftArtists = candidates[left].metadata.artists
                            .mapTo(linkedSetOf(), ::normalizeSeriesText)
                        if (leftArtists.isEmpty()) return@forEach
                        for (right in left + 1 until candidates.size) {
                            val rightArtists = candidates[right].metadata.artists
                                .mapTo(linkedSetOf(), ::normalizeSeriesText)
                            if (leftArtists.intersect(rightArtists).isEmpty()) continue
                            val leftStem = normalizeSeriesText(seriesComparisonStem(candidates[left].title))
                            val rightStem = normalizeSeriesText(seriesComparisonStem(candidates[right].title))
                            if (leftStem.length >= 2 && rightStem.length >= 2 &&
                                (leftStem == rightStem || diceSimilarity(leftStem, rightStem) >= 0.82f)
                            ) join(left, right)
                        }
                    }
                    candidates.indices.groupBy(::root).values
                        .map { indices -> indices.map(candidates::get) }
                        .filter { it.size >= 2 }
                }
                if (groups.isEmpty()) {
                    uiState = uiState.copy(
                        message = strings[R.string.msg_no_galleries_with_the_same_artist_and_similar_titles],
                        isError = false,
                    )
                    return@launch
                }
                val assignments = uiState.customSeriesByBookId.toMutableMap()
                val modifiedAt = System.currentTimeMillis()
                val usedNames = assignments.values.mapTo(linkedSetOf(), CustomSeriesAssignment::name)
                groups.forEach { group ->
                    val baseName = seriesDisplayStem(group.first().title).ifBlank { group.first().title }
                    var name = baseName
                    var suffix = 2
                    while (name in usedNames) name = "$baseName ($suffix)".also { suffix++ }
                    usedNames += name
                    group.sortedWith(compareBy<LibraryBook> { trailingSeriesNumber(it.title) ?: Int.MAX_VALUE }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                        .forEachIndexed { order, book ->
                            assignments[book.id] = CustomSeriesAssignment(name, order, modifiedAt)
                        }
                }
                val indexed = reindexSeriesAssignments(assignments)
                persistCustomSeries(indexed)
                uiState = uiState.copy(
                    message = null,
                    isError = false,
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    message = strings[R.string.msg_auto_generation_failed, (error.message ?: strings[R.string.msg_unknown_error])],
                    isError = true,
                )
            } finally {
                uiState = uiState.copy(isAutoCreatingSeries = false)
            }
        }
    }

    fun setLibraryBooksHidden(bookIds: Set<String>, hidden: Boolean) = submitBookFields(bookIds, org.json.JSONObject().put("isHidden", hidden))

    fun deleteLibraryBooks(bookIds: Set<String>) {
        if (bookIds.isEmpty()) return
        val profile = selectedProfile()
        val selectedBooks = uiState.libraryBooks.filter { it.id in bookIds }
        val remoteBooks = selectedBooks.filter { it.isCloud && it.remoteBookId != null }
        val localBooks = selectedBooks.filterNot(LibraryBook::isCloud)
        if (remoteBooks.isNotEmpty() && profile == null) {
            uiState = uiState.copy(
                message = strings[R.string.msg_connect_to_pc_to_delete_desktop_galleries],
                isError = true,
            )
            return
        }
        viewModelScope.launch {
            val deletedIds = linkedSetOf<String>()
            val failures = mutableListOf<String>()
            remoteBooks.forEach { book ->
                runCatching { client.deleteLibraryBook(profile!!, book.remoteBookId!!) }
                    .onSuccess { deletedIds += book.id }
                    .onFailure { failures += "${book.title}: ${it.message ?: strings[R.string.msg_deletion_failed]}" }
            }
            localBooks.forEach { book ->
                runCatching { libraryFileDeleter.delete(book) }
                    .onSuccess { deletedIds += book.id }
                    .onFailure { failures += "${book.title}: ${it.message ?: strings[R.string.msg_deletion_failed]}" }
            }
            if (deletedIds.isNotEmpty()) {
                runCatching {
                    withContext(Dispatchers.IO) { requestStore.update { it.withoutDeletedBooks(selectedBooks.filter { book -> book.id in deletedIds }, profile?.id) } }
                }.onFailure { failures += it.message ?: strings[R.string.msg_deletion_failed] }
                removeDeletedLibraryState(deletedIds)
            }
            if (remoteBooks.isNotEmpty()) refreshLibrarySilently()
            val details = buildList {
                if (remoteBooks.any { it.id in deletedIds }) {
                    add(strings[R.string.msg_desktop_galleries_moved_to_trash_and_removed_from_the])
                }
                if (localBooks.any { it.id in deletedIds }) {
                    add(strings[R.string.msg_local_gallery_files_permanently_deleted])
                }
                if (failures.isNotEmpty()) add(strings[R.string.msg_could_not_delete_items, (failures.size)])
            }.joinToString(" ")
            uiState = uiState.copy(message = details, isError = failures.isNotEmpty())
        }
    }

    private fun removeDeletedLibraryState(bookIds: Set<String>) {
        libraryMutationRevision++
        val previousSeries = uiState.customSeriesByBookId
        if (uiState.activeLibraryBook?.id in bookIds) {
            libraryPreferenceStore.saveActiveBookId(null)
            activeBookPreparationJob?.cancel()
        }
        val hidden = uiState.libraryHiddenIds - bookIds
        libraryPreferenceStore.saveHiddenIds(hidden)
        val favorites = uiState.libraryFavoriteIds - bookIds
        libraryPreferenceStore.saveFavoriteIds(favorites)
        val read = uiState.libraryReadIds - bookIds
        libraryPreferenceStore.saveReadIds(read)
        val progress = uiState.libraryProgress - bookIds
        libraryPreferenceStore.saveProgress(progress)
        val series = uiState.customSeriesByBookId - bookIds
        libraryPreferenceStore.saveCustomSeries(series)
        libraryPreferenceStore.saveSeriesRemovalTimes(
            libraryPreferenceStore.loadSeriesRemovalTimes() - bookIds,
        )
        libraryPreferenceStore.savePendingSeriesSyncIds(
            libraryPreferenceStore.loadPendingSeriesSyncIds() - bookIds,
        )
        libraryPreferenceStore.savePendingBookStateSyncIds(
            libraryPreferenceStore.loadPendingBookStateSyncIds() - bookIds,
        )
        val titles = libraryPreferenceStore.loadCustomTitles() - bookIds
        libraryPreferenceStore.saveCustomTitles(titles)
        uiState = uiState.copy(
            libraryBooks = uiState.libraryBooks.filterNot { it.id in bookIds },
            libraryHiddenIds = hidden,
            libraryFavoriteIds = favorites,
            libraryReadIds = read,
            libraryProgress = progress,
            customSeriesByBookId = series,
            activeLibraryBook = uiState.activeLibraryBook?.takeUnless { it.id in bookIds },
        )
        reconcileLibrarySeriesPages(previousSeries)
    }

    fun renameLibraryBook(bookId: String, title: String) {
        val normalized = title.trim()
        if (normalized.isEmpty() || normalized.length > 500) return
        uiState.libraryBooks.firstOrNull { it.id == bookId }?.let { book ->
            val base = uiState.presentations[book.presentationKey()]?.value ?: book.presentation()
            saveGalleryPresentation(base.copy(title = normalized))
        }
    }

    fun removeLibraryBooksFromSeries(bookIds: Set<String>) {
        val affectedNames = bookIds.mapNotNullTo(linkedSetOf()) { uiState.customSeriesByBookId[it]?.name }
        val modifiedAt = System.currentTimeMillis()
        val assignments = touchSeriesAssignments(
            reindexSeriesAssignments(uiState.customSeriesByBookId - bookIds),
            affectedNames,
            modifiedAt,
        )
        persistCustomSeries(assignments)
    }

    fun renameLibrarySeries(oldName: String, newName: String) {
        val normalized = newName.trim()
        if (normalized.isEmpty() || oldName == normalized) return
        if (!ensureValidSeriesName(normalized)) return
        if (seriesPresentationConflict(oldName)) {
            uiState = uiState.copy(message = getApplication<Application>().getString(com.doujinzip.android.R.string.ui_metadata_conflict), isError = true)
            return
        }
        val modifiedAt = System.currentTimeMillis()
        val renamed = uiState.customSeriesByBookId.mapValues { (_, assignment) ->
            if (assignment.name == oldName) {
                assignment.copy(name = normalized, modifiedAt = modifiedAt)
            } else assignment
        }
        val assignments = reindexSeriesAssignments(renamed)
        persistCustomSeries(assignments)
    }

    private fun copySeriesPresentation(oldName: String, newName: String) {
        if (oldName == newName) return
        if (uiState.presentations[com.doujinzip.android.model.seriesPresentationKey(oldName)] == null &&
            uiState.presentations[com.doujinzip.android.model.seriesCoverPresentationKey(oldName)] == null) return
        val profileId = uiState.selectedProfileId
        viewModelScope.launch { runOperation {
            withContext(Dispatchers.IO) { presentationStore(profileId).copySeriesOverride(oldName, newName) }
            if (profileId == uiState.selectedProfileId) { reloadPresentations(); scheduleLibrarySync() }
        } }
    }

    private fun seriesPresentationConflict(name: String): Boolean = listOf(com.doujinzip.android.model.seriesPresentationKey(name),
        com.doujinzip.android.model.seriesCoverPresentationKey(name)).any { uiState.presentations[it]?.conflict != null }

    fun mergeLibrarySeries(seriesNames: List<String>, mergedName: String) {
        val names = seriesNames.distinct()
        val name = mergedName.trim()
        if (names.size < 2 || name.isEmpty()) return
        if (!ensureValidSeriesName(name)) return
        if (names.any(::seriesPresentationConflict)) {
            uiState = uiState.copy(message = getApplication<Application>().getString(com.doujinzip.android.R.string.ui_metadata_conflict), isError = true)
            return
        }
        val modifiedAt = System.currentTimeMillis()
        val selectedEntries = names.flatMap { sourceName ->
            uiState.customSeriesByBookId.entries
                .filter { it.value.name == sourceName }
                .sortedWith(compareBy({ it.value.order }, { it.key }))
        }
        if (selectedEntries.isEmpty()) return
        val assignments = uiState.customSeriesByBookId
            .filterValues { it.name !in names }
            .toMutableMap()
        var nextOrder = assignments.values.filter { it.name == name }
            .maxOfOrNull(CustomSeriesAssignment::order)?.plus(1) ?: 0
        selectedEntries.forEach { entry ->
            assignments[entry.key] = CustomSeriesAssignment(name, nextOrder++, modifiedAt)
        }
        val indexed = touchSeriesAssignments(
            reindexSeriesAssignments(assignments),
            setOf(name),
            modifiedAt,
        )
        persistCustomSeries(indexed)
    }

    fun deleteLibrarySeries(names: Set<String>) {
        val assignments = reindexSeriesAssignments(
            uiState.customSeriesByBookId.filterValues { it.name !in names },
        )
        persistCustomSeries(assignments)
    }

    fun nextLibrarySeriesBook(bookId: String): LibraryBook? {
        val assignment = uiState.customSeriesByBookId[bookId] ?: return null
        val nextId = uiState.customSeriesByBookId.entries
            .filter { it.value.name == assignment.name && it.value.order > assignment.order }
            .minByOrNull { it.value.order }?.key ?: return null
        return uiState.libraryBooks.firstOrNull { it.id == nextId }
    }

    fun previousLibrarySeriesBook(bookId: String): LibraryBook? {
        val assignment = uiState.customSeriesByBookId[bookId] ?: return null
        val previousId = uiState.customSeriesByBookId.entries
            .filter { it.value.name == assignment.name && it.value.order < assignment.order }
            .maxByOrNull { it.value.order }?.key ?: return null
        return uiState.libraryBooks.firstOrNull { it.id == previousId }
    }

    private fun reindexSeriesAssignments(
        assignments: Map<String, CustomSeriesAssignment>,
        names: Set<String>? = null,
    ): Map<String, CustomSeriesAssignment> = buildMap {
        if (names != null) {
            assignments.filterValues { it.name !in names }.forEach(::put)
        }
        assignments.entries.groupBy { it.value.name }.forEach { (name, entries) ->
            if (names != null && name !in names) return@forEach
            val modifiedAt = entries.maxOfOrNull { it.value.modifiedAt } ?: 0L
            entries.sortedWith(compareBy({ it.value.order }, { it.key })).forEachIndexed { index, entry ->
                put(entry.key, CustomSeriesAssignment(name, index, modifiedAt))
            }
        }
    }

    private fun touchSeriesAssignments(
        assignments: Map<String, CustomSeriesAssignment>,
        names: Set<String>,
        modifiedAt: Long,
    ): Map<String, CustomSeriesAssignment> = assignments.mapValues { (_, assignment) ->
        if (assignment.name in names) assignment.copy(modifiedAt = modifiedAt) else assignment
    }

    fun updateLibraryProgress(bookId: String, page: Int) {
        val normalizedPage = page.coerceAtLeast(0)
        val book = uiState.libraryBooks.firstOrNull { it.id == bookId }
        if (book?.isCloud == true) {
            val profile = selectedProfile() ?: return
            val syncId = book.syncId ?: return
            recordLibraryHistory(book, normalizedPage)
            if (uiState.libraryProgress[bookId] == normalizedPage) return
            libraryPreferenceStore.saveBookProgress(bookId, normalizedPage)
            uiState = uiState.copy(libraryProgress = uiState.libraryProgress + (bookId to normalizedPage))
            val mutation = org.json.JSONObject().put("mutationId", java.util.UUID.randomUUID().toString())
                .put("bookSyncId", syncId).put("baseVersion", book.syncStateVersion).put("baseCurrentPage", book.syncedProgress).put("baseIsRead", book.syncedRead).put("currentPage", normalizedPage)
            if (reachedLastPage(normalizedPage, book.pages.size)) mutation.put("isRead", true)
            if (uiState.historyRecording) mutation.put("historyEvent", org.json.JSONObject().put("eventId", java.util.UUID.randomUUID().toString()).put("viewedAt", java.time.Instant.now().toString()).put("currentPage", normalizedPage))
            viewModelScope.launch {
                withContext(Dispatchers.IO) { serverEdits.put(profile.id, "progress:${profile.id}:$syncId", "state", org.json.JSONObject().put("mutations", org.json.JSONArray().put(mutation))) }
                scheduleLibrarySync()
            }
            return
        }
        val readIds = if (reachedLastPage(normalizedPage, book?.pages?.size ?: 0)) {
            uiState.libraryReadIds + bookId
        } else {
            uiState.libraryReadIds
        }
        book?.let { recordLibraryHistory(it, normalizedPage) }
        if (uiState.libraryProgress[bookId] == normalizedPage && readIds == uiState.libraryReadIds) return
        val progress = uiState.libraryProgress + (bookId to normalizedPage)
        libraryPreferenceStore.saveBookProgress(bookId, normalizedPage)
        if (readIds != uiState.libraryReadIds) libraryPreferenceStore.saveReadIds(readIds)
        uiState = uiState.copy(libraryProgress = progress, libraryReadIds = readIds)
    }

    private fun ensureValidSeriesName(name: String): Boolean {
        if (name.length <= MAX_SERIES_NAME_LENGTH) return true
        uiState = uiState.copy(
            message = strings[R.string.msg_series_names_must_be_characters_or_fewer, (MAX_SERIES_NAME_LENGTH)],
            isError = true,
        )
        return false
    }

    fun updateOnlineReaderProgress(galleryId: Long, page: Int) {
        val normalizedPage = page.coerceAtLeast(0)
        val pageCount = uiState.galleryCache[galleryId]?.pageCount
            ?: uiState.readerPagesByGalleryId[galleryId]?.size
            ?: 0
        val viewedIds = if (reachedLastPage(normalizedPage, pageCount)) {
            rememberViewedGallery(galleryId)
        } else {
            uiState.viewedGalleryIds
        }
        (uiState.galleryCache[galleryId] ?: uiState.galleries.firstOrNull { it.id == galleryId })?.let { recordOnlineHistory(it, normalizedPage) }
        if (uiState.onlineReaderProgress[galleryId] == normalizedPage && viewedIds == uiState.viewedGalleryIds) return
        val progress = uiState.onlineReaderProgress + (galleryId to normalizedPage)
        libraryPreferenceStore.saveOnlinePage(galleryId, normalizedPage)
        uiState = uiState.copy(onlineReaderProgress = progress, viewedGalleryIds = viewedIds)
    }

    fun refreshHistory() {
        uiState = uiState.copy(recentHistory = recentHistoryStore.load())
    }

    private fun saveHistory(event: RecentHistoryEntry) {
        if (!uiState.historyRecording) return
        val entries = uiState.recentHistory.recordHistory(event)
        recentHistoryStore.save(entries)
        uiState = uiState.copy(recentHistory = entries)
    }

    private fun recordOnlineHistory(gallery: GallerySummary, page: Int? = null) {
        val completed = page != null && reachedLastPage(page, gallery.pageCount)
        saveHistory(RecentHistoryEntry(
            key = "gallery:${gallery.id}", title = gallery.title, viewedAt = System.currentTimeMillis(),
            source = HistorySource.SEARCH, galleryId = gallery.id, artist = gallery.artists.joinToString(),
            thumbnail = gallery.thumbnailUrl, pageCount = gallery.pageCount, page = page,
            action = if (page == null) HistoryAction.DETAIL else if (completed) HistoryAction.COMPLETED else HistoryAction.READING,
            completed = completed,
        ))
    }

    private fun recordLibraryHistory(book: LibraryBook, page: Int? = null) {
        val id = book.metadata.hitomiId?.toLongOrNull()?.takeIf { it > 0 }
        val completed = page != null && reachedLastPage(page, book.pages.size)
        saveHistory(RecentHistoryEntry(
            key = id?.let { "gallery:$it" } ?: book.syncId?.let { "sync:$it" } ?: "book:${book.id}",
            title = book.title, viewedAt = System.currentTimeMillis(),
            source = if (book.isCloud) HistorySource.PC else HistorySource.DEVICE,
            galleryId = id, bookId = book.id, syncId = book.syncId, artist = book.metadata.artists.joinToString(),
            thumbnail = book.coverUri, pageCount = book.pages.size, page = page,
            action = if (page == null) HistoryAction.DETAIL else if (completed) HistoryAction.COMPLETED else HistoryAction.READING,
            completed = completed,
        ))
    }

    fun setHistoryRecording(enabled: Boolean) {
        recentHistoryStore.setRecording(enabled)
        uiState = uiState.copy(historyRecording = enabled)
    }

    fun removeHistory(key: String) {
        val entries = uiState.recentHistory.filterNot { it.key == key }
        recentHistoryStore.save(entries)
        uiState = uiState.copy(recentHistory = entries)
    }

    fun clearHistory() {
        recentHistoryStore.save(emptyList())
        uiState = uiState.copy(recentHistory = emptyList())
    }

    fun openHistory(entry: RecentHistoryEntry, resume: Boolean) {
        if (uiState.isBusy) return
        val book = entry.findBook(uiState.libraryBooks.filterNot { it.id in uiState.libraryHiddenIds })
        if (book != null) {
            openLibraryBookTab(book.id)
            if (resume) openLibraryReaderTab(book.id, (entry.page ?: uiState.libraryProgress[book.id] ?: 0).coerceAtLeast(0))
            return
        }
        val id = entry.galleryId
        if (id == null) {
            uiState = uiState.copy(message = strings[R.string.msg_file_not_found_check_storage_and_pc_connection], isError = true)
            return
        }
        viewModelScope.launch {
            runOperation {
                val gallery = uiState.galleryCache[id] ?: hitomiClient.getGallery(id)
                uiState = uiState.copy(galleryCache = cacheGalleries(uiState.galleryCache, listOf(gallery)))
                selectGallery(id)
                if (resume) openOnlineReaderTab(id, (entry.page ?: uiState.onlineReaderProgress[id] ?: 0)
                    .coerceIn(0, (gallery.pageCount - 1).coerceAtLeast(0)))
            }
        }
    }

    fun updateViewerPreferences(value: ViewerPreferences) {
        libraryPreferenceStore.saveViewerPreferences(value)
        uiState = uiState.copy(viewerPreferences = value)
    }

    fun setDownloadLocation(uri: String, displayName: String) {
        val location = StorageLocation(uri, displayName)
        browserPreferenceStore.saveDownloadLocation(location)
        uiState = uiState.copy(downloadLocation = location)
        downloadAfterFolder?.let { gallery ->
            downloadAfterFolder = null
            uiState = uiState.copy(pendingDownloadGallery = null, downloadFolderRequired = false)
            enqueueGalleryDownload(gallery, DownloadDestination.DEVICE)
        }
    }

    fun clearDownloadLocation() {
        browserPreferenceStore.saveDownloadLocation(null)
        uiState = uiState.copy(downloadLocation = null)
    }

    fun resetDownloadConnectionAttempt() {
        if (uiState.isDownloadConnectionUnavailable) {
            notifyOnDownloadReconnect = true
        }
        uiState = uiState.copy(isDownloadConnectionUnavailable = false)
    }

    fun refreshDownloads(silent: Boolean = false) {
        if (downloadRefreshJob?.isActive == true || uiState.isDownloadConnectionUnavailable) return
        val profile = selectedProfile() ?: run {
            if (!silent) {
                uiState = uiState.copy(downloadQueueError = strings[R.string.msg_no_desktop_connected])
            }
            return
        }
        downloadRefreshJob = viewModelScope.launch {
            if (!silent) {
                uiState = uiState.copy(
                    isDownloadQueueLoading = true,
                    downloadQueueError = null,
                )
            }
            try {
                val downloads = sortDownloadQueueNewest(client.getDownloads(profile))
                withContext(Dispatchers.IO) { requestStore.update { it.confirmDesktopCompletions(profile.id, downloads) } }
                val completedIds = downloads.asSequence()
                    .filter { it.status == DownloadStatus.COMPLETED }
                    .mapTo(hashSetOf(), DownloadQueueItem::id)
                val hasNewCompletion = completedIds.any { it !in observedCompletedDownloadIds }
                observedCompletedDownloadIds.clear()
                observedCompletedDownloadIds += completedIds
                val reconnectMessage = strings[R.string.msg_reconnected_to_pc].takeIf { notifyOnDownloadReconnect }
                uiState = uiState.copy(
                    downloadQueue = downloads,
                    downloadQueueError = null,
                    isDownloadConnectionUnavailable = false,
                    message = reconnectMessage ?: uiState.message,
                    isError = if (reconnectMessage != null) false else uiState.isError,
                )
                notifyOnDownloadReconnect = false
                if (hasNewCompletion) requestImportantLibraryRefresh()
            } catch (error: Exception) {
                if (!silent) {
                    uiState = uiState.copy(
                        downloadQueueError = error.message ?: strings[R.string.msg_could_not_load_downloads],
                        isDownloadConnectionUnavailable = true,
                    )
                }
            } finally {
                if (!silent) uiState = uiState.copy(isDownloadQueueLoading = false)
            }
        }
    }

    fun pauseDownload(queueId: Long) = runDownloadAction(queueId) { profile ->
        client.pauseDownload(profile, queueId)
    }

    fun resumeDownload(queueId: Long) = runDownloadAction(queueId) { profile ->
        client.resumeDownload(profile, queueId)
    }

    fun retryDownload(queueId: Long) = runDownloadAction(queueId) { profile ->
        client.retryDownload(profile, queueId)
    }

    fun removeDownload(queueId: Long) = runDownloadAction(queueId) { profile ->
        val item = uiState.downloadQueue.firstOrNull { it.id == queueId }
        client.removeDownload(profile, queueId)
        withContext(Dispatchers.IO) { requestStore.update { requests ->
            requests.filterNot { request -> request.target == DownloadTarget.DESKTOP && request.profileId == profile.id &&
                !request.isBusy && request.isFinished &&
                (request.desktopQueueId == queueId || request.desktopQueueId == null && request.galleryId == item?.galleryId) }
        } }
    }

    fun clearCompletedDownloads() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { requestStore.update { list -> list.filterNot { it.status == RequestStatus.COMPLETED } } }
        }
        if (selectedProfile() != null && uiState.downloadQueue.any { it.status == DownloadStatus.COMPLETED }) runDownloadAction(null) { profile ->
            client.clearCompletedDownloads(profile)
        }
    }

    private fun runDownloadAction(
        queueId: Long?,
        action: suspend (DesktopProfile) -> Unit,
    ) {
        if (queueId != null && queueId in uiState.activeDownloadActionIds) return
        val profile = selectedProfile() ?: run {
            uiState = uiState.copy(downloadQueueError = strings[R.string.msg_no_desktop_connected])
            return
        }
        viewModelScope.launch {
            if (queueId != null) {
                uiState = uiState.copy(
                    activeDownloadActionIds = uiState.activeDownloadActionIds + queueId,
                )
            }
            try {
                action(profile)
                uiState = uiState.copy(
                    downloadQueue = sortDownloadQueueNewest(client.getDownloads(profile)),
                    downloadQueueError = null,
                )
            } catch (error: Exception) {
                uiState = uiState.copy(
                    downloadQueueError = error.message ?: strings[R.string.msg_download_action_failed],
                )
            } finally {
                if (queueId != null) {
                    uiState = uiState.copy(
                        activeDownloadActionIds = uiState.activeDownloadActionIds - queueId,
                    )
                }
            }
        }
    }

    private fun selectedProfile(): DesktopProfile? =
        uiState.profiles.firstOrNull { it.id == uiState.selectedProfileId }

    private fun activeSearchPage(): BrowserPage.Search? =
        uiState.browserWorkspace.activeTab.currentPage as? BrowserPage.Search

    private fun isActiveSearch(key: String): Boolean = activeSearchPage()?.key == key

    private fun startBrowserSearch(reset: Boolean) {
        val searchKey = activeSearchPage()?.key ?: return
        browserSearchJob?.cancel()
        browserSearchJob = viewModelScope.launch {
            loadPage(reset = reset, searchKey = searchKey)
        }
    }

    fun ensureSearchResults() {
        if (uiState.searchResultWindow == null && !uiState.isRestoringSearch &&
            (activeSearchPage()?.currentPage ?: 0) > 0) restoreBrowserSearch()
    }

    private fun cancelSearchWindow() {
        searchWindowJob?.cancel()
        requestedSearchPages = IntRange.EMPTY
    }

    private fun isSearchSessionCurrent(key: String, sessionId: String, revision: Long): Boolean =
        isActiveSearch(key) && activeSearchPage()?.resultSessionId == sessionId && uiState.browserNavigationRevision == revision

    private fun publishSearchWindow(window: SearchResultWindow) {
        uiState = uiState.copy(searchResultWindow = window, galleries = window.galleries.map(::presentedGallery),
            currentPage = window.index.lastPage, hasNextPage = window.index.hasNextPage)
    }

    private fun restoreBrowserSearch() {
        val search = activeSearchPage() ?: return
        if (search.currentPage <= 0) return
        browserSearchJob?.cancel()
        cancelSearchWindow()
        val sessionId = search.resultSessionId ?: java.util.UUID.randomUUID().toString()
        val revision = uiState.browserNavigationRevision
        val queries = search.submittedQueries.ifEmpty {
            filteredSearchQueries(search.submittedQuery.ifBlank { search.query }, search.preferredLanguages)
        }
        val workspace = uiState.browserWorkspace.updateActiveSearch { it.copy(resultSessionId = sessionId) }
        uiState = uiState.copy(browserWorkspace = workspace, searchResultWindow = null, galleries = emptyList(),
            isRestoringSearch = true, isLoadingPage = true, submittedSearchQueries = queries)
        visibleSearchResults = (search.scrollIndex - 2).coerceAtLeast(0).let { it..it }
        browserPreferenceStore.saveBrowserWorkspace(workspace)
        browserSearchJob = viewModelScope.launch {
            try {
                val index = searchResultPager.restore(sessionId, queries, search.scrollPage)
                if (!isSearchSessionCurrent(search.key, sessionId, revision)) return@launch
                visibleSearchResults = index.ids.indexOf(search.scrollAnchorKey?.removePrefix("gallery:")?.toLongOrNull()).coerceAtLeast(0).let { it..it }
                publishSearchWindow(SearchResultWindow(index))
                syncActiveSearch(persist = true)
                loadVisibleSearchResults(visibleSearchResults.first, visibleSearchResults.last)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (isSearchSessionCurrent(search.key, sessionId, revision)) {
                    uiState = uiState.copy(message = error.message ?: strings[R.string.msg_could_not_restore_search_results], isError = true)
                }
            } finally {
                if (isSearchSessionCurrent(search.key, sessionId, revision)) {
                    uiState = uiState.copy(isLoadingPage = false, isRestoringSearch = false)
                }
            }
        }
    }

    fun loadVisibleSearchResults(firstResult: Int, lastResult: Int) {
        visibleSearchResults = firstResult.coerceAtLeast(0)..lastResult.coerceAtLeast(firstResult).coerceAtLeast(0)
        val window = uiState.searchResultWindow ?: return
        val searchKey = activeSearchPage()?.key ?: return
        if (activeSearchPage()?.resultSessionId != window.index.sessionId) return
        val pages = window.index.window(visibleSearchResults.first, visibleSearchResults.last)
        if (pages.isEmpty()) return
        if (pages == requestedSearchPages && (pages.all { it in window.residentPages } || searchWindowJob?.isActive == true)) return
        cancelSearchWindow()
        requestedSearchPages = pages
        val revision = uiState.browserNavigationRevision
        val trimmed = window.retainPages(pages)
        publishSearchWindow(trimmed)
        searchWindowJob = viewModelScope.launch {
            try {
                searchResultPager.loadWindow(trimmed, pages, window.index.pageAt(visibleSearchResults.first)) { page, galleries ->
                    if (isSearchSessionCurrent(searchKey, window.index.sessionId, revision)) {
                        val latest = uiState.searchResultWindow
                        if (latest != null && page in requestedSearchPages) {
                            publishSearchWindow(latest.copy(residentPages = latest.residentPages + (page to galleries)))
                            uiState = uiState.copy(galleryCache = cacheGalleries(uiState.galleryCache, galleries))
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (isSearchSessionCurrent(searchKey, window.index.sessionId, revision)) {
                    uiState = uiState.copy(message = error.message ?: strings[R.string.msg_could_not_load_search_results], isError = true)
                }
            }
        }
    }

    private suspend fun fetchSearchPage(queries: List<String>, page: Int): SearchResult = coroutineScope {
        val popularity = queries.firstOrNull()?.let { com.doujinzip.android.network.parseHitomiSearchOrder(it).popularity }
        if (popularity != null) return@coroutineScope hitomiClient.searchPopular(queries.distinct(), page, popularity)
        val results = queries.distinct().map { query -> async { hitomiClient.search(query, page) } }.awaitAll()
        combineLanguageSearchResults(results)
    }

    private fun filteredSearchQueries(query: String, languages: Set<String>): List<String> =
        queriesWithPreferredLanguages(query, languages).map { com.doujinzip.android.model.applySearchTagFilters(it, uiState.searchTagFilters, uiState.customBlockedTags) }

    private fun openSearchTab(query: String, preferredLanguages: Set<String>) {
        browserSearchJob?.cancel()
        browserPreferenceStore.savePreferredLanguages(preferredLanguages)
        val current = uiState.browserWorkspace
        val existing = current.selectSearchTab(query, preferredLanguages)
        if (existing != null) {
            val page = existing.activeTab.currentPage as BrowserPage.Search
            rememberTabReturn(existing, gallery = false)
            applyBrowserWorkspace(existing, reload = true)
            if (page.currentPage == 0) startBrowserSearch(reset = true)
            return
        }
        val opened = current.openTab(BrowserPage.Search(query = query, preferredLanguages = preferredLanguages))
        rememberTabReturn(opened, gallery = false)
        notifyNewTab()
        applyBrowserWorkspace(opened)
        startBrowserSearch(reset = true)
    }

    private fun applyBrowserWorkspace(
        workspace: BrowserWorkspace,
        reload: Boolean = false,
    ) {
        val previous = uiState.browserWorkspace
        browserSearchJob?.cancel()
        cancelSearchWindow()
        suggestionJob?.cancel()
        val page = workspace.activeTab.currentPage
        navigationPage = page
        browserPreferenceStore.saveActiveGalleryWorkspace(false)
        val revision = uiState.browserNavigationRevision + 1
        uiState = when (page) {
            is BrowserPage.Search -> uiState.copy(
                browserWorkspace = workspace,
                browserNavigationRevision = revision,
                searchQuery = page.query,
                preferredLanguages = page.preferredLanguages,
                submittedSearchQuery = page.submittedQuery,
                submittedSearchQueries = page.submittedQueries,
                galleries = emptyList(),
                searchResultWindow = null,
                nextSearchPageError = null,
                currentPage = page.currentPage,
                hasNextPage = page.hasNextPage,
                isLoadingPage = false,
                isRestoringSearch = false,
                isRefreshing = false,
                filterSuggestions = emptyList(),
                isLoadingFilterSuggestions = false,
            )
            else -> uiState.copy(
                browserWorkspace = workspace,
                galleries = emptyList(),
                searchResultWindow = null,
                nextSearchPageError = null,
                browserNavigationRevision = revision,
                isLoadingPage = false,
                isRestoringSearch = false,
                isRefreshing = false,
                filterSuggestions = emptyList(),
                isLoadingFilterSuggestions = false,
            )
        }
        browserPreferenceStore.saveBrowserWorkspace(workspace)
        removeClosedSearchSessions(previous, workspace)
        if (reload && page is BrowserPage.Search && page.currentPage > 0) {
            restoreBrowserSearch()
        }
    }

    private fun removeClosedSearchSessions(previous: BrowserWorkspace, next: BrowserWorkspace) {
        val removed = previous.searchResultSessionIds - next.searchResultSessionIds
        if (removed.isNotEmpty()) viewModelScope.launch { searchResultCache.removeSessions(removed) }
    }

    private fun applyGalleryWorkspace(workspace: BrowserWorkspace) {
        libraryQueryJob?.cancel()
        browserSearchJob?.cancel()
        cancelSearchWindow()
        suggestionJob?.cancel()
        navigationPage = workspace.activeTab.currentPage
        browserPreferenceStore.saveActiveGalleryWorkspace(true)
        uiState = uiState.copy(
            galleryWorkspace = workspace,
            libraryQuery = (workspace.activeTab.currentPage as? BrowserPage.LibraryHome)?.query ?: uiState.libraryQuery,
            libraryQueryDraft = (workspace.activeTab.currentPage as? BrowserPage.LibraryHome)?.query ?: uiState.libraryQueryDraft,
            galleries = emptyList(),
            searchResultWindow = null,
            nextSearchPageError = null,
            browserNavigationRevision = uiState.browserNavigationRevision + 1,
            isLoadingPage = false,
            isRestoringSearch = false,
            isRefreshing = false,
            filterSuggestions = emptyList(),
            isLoadingFilterSuggestions = false,
        )
        browserPreferenceStore.saveGalleryWorkspace(workspace)
    }

    private fun syncActiveSearch(persist: Boolean = false) {
        val workspace = uiState.browserWorkspace.updateActiveSearch { search ->
            search.copy(
                submittedQuery = uiState.submittedSearchQuery,
                submittedQueries = uiState.submittedSearchQueries,
                resultIds = emptyList(),
                results = emptyList(),
                currentPage = uiState.currentPage,
                hasNextPage = uiState.hasNextPage,
            )
        }
        if (workspace != uiState.browserWorkspace) {
            uiState = uiState.copy(browserWorkspace = workspace)
        }
        if (persist) browserPreferenceStore.saveBrowserWorkspace(workspace)
    }

    private suspend fun loadPage(
        reset: Boolean,
        preserveResults: Boolean = false,
        searchKey: String,
    ) {
        if (!isActiveSearch(searchKey)) return
        val query = if (reset) uiState.searchQuery.trim() else {
            uiState.submittedSearchQuery.ifBlank {
                if (uiState.submittedSearchQueries.isEmpty()) uiState.searchQuery.trim() else ""
            }
        }
        val searchQueries = if (reset && preserveResults) {
            uiState.submittedSearchQueries.ifEmpty { filteredSearchQueries(query, uiState.preferredLanguages) }
        } else if (reset) {
            filteredSearchQueries(query, uiState.preferredLanguages)
        } else {
            uiState.submittedSearchQueries.ifEmpty {
                filteredSearchQueries(query, uiState.preferredLanguages)
            }
        }
        if (searchQueries.isEmpty()) {
            uiState = uiState.copy(message = strings[R.string.msg_enter_a_search_query], isError = true)
            return
        }
        val index = if (reset) SearchResultIndex(java.util.UUID.randomUUID().toString(), searchQueries)
            else uiState.searchResultWindow?.index ?: return
        if (reset) {
            browserPreferenceStore.savePreferredLanguages(uiState.preferredLanguages)
            if (!preserveResults) {
                val history = uiState.searchQueryHistory.recordQuery(SearchQueryEntry(query, uiState.preferredLanguages))
                browserPreferenceStore.saveSearchQueryHistory(history)
                uiState = uiState.copy(searchQueryHistory = history)
            }
        }
        val revision = uiState.browserNavigationRevision
        if (reset) {
            cancelSearchWindow()
            if (!preserveResults) visibleSearchResults = 0..0
        }
        val previousWorkspace = uiState.browserWorkspace
        val previousResults = uiState.searchResultWindow
        val previousPage = uiState.currentPage
        val previousHasNext = uiState.hasNextPage
        val workspace = previousWorkspace.updateActiveSearch { search ->
            search.copy(query = if (reset) query else search.query,
                preferredLanguages = if (reset) uiState.preferredLanguages else search.preferredLanguages,
                resultSessionId = index.sessionId,
                scrollPage = if (reset && !preserveResults) 1 else search.scrollPage,
                scrollAnchorKey = if (reset && !preserveResults) null else search.scrollAnchorKey,
                scrollIndex = if (reset && !preserveResults) 0 else search.scrollIndex,
                scrollOffset = if (reset && !preserveResults) 0 else search.scrollOffset)
        }
        uiState = uiState.copy(
            browserWorkspace = workspace,
            submittedSearchQuery = if (reset) query else uiState.submittedSearchQuery,
            submittedSearchQueries = if (reset) searchQueries else uiState.submittedSearchQueries,
            galleries = if (reset && !preserveResults) emptyList() else uiState.galleries,
            searchResultWindow = if (reset && !preserveResults) SearchResultWindow(index) else uiState.searchResultWindow,
            currentPage = if (reset) 0 else uiState.currentPage,
            hasNextPage = if (reset) false else uiState.hasNextPage,
            isLoadingPage = true,
            nextSearchPageError = null,
            message = null,
            isError = false,
        )
        syncActiveSearch()

        try {
            val loaded = searchResultPager.append(index)
            if (!isSearchSessionCurrent(searchKey, index.sessionId, revision)) return
            val summaries = loaded.galleries
            val pages = loaded.index.window(visibleSearchResults.first, visibleSearchResults.last)
            val resident = (if (reset) emptyMap() else uiState.searchResultWindow?.residentPages.orEmpty()) + loaded.residentPages
            publishSearchWindow(loaded.copy(residentPages = resident).retainPages(pages))
            val knownFilters = rememberFilterTokens(summaries)
            val failedCount = summaries.count { it.loadError != null }
            uiState = uiState.copy(
                galleryCache = cacheGalleries(uiState.galleryCache, summaries),
                knownFilterTokens = knownFilters,
                message = if (failedCount > 0) {
                    strings[R.string.msg_could_not_load_details_for_galleries, (failedCount)]
                } else {
                    null
                },
                isError = failedCount > 0,
            )
            syncActiveSearch(persist = true)
            if (reset) removeClosedSearchSessions(previousWorkspace, uiState.browserWorkspace)
            loadVisibleSearchResults(visibleSearchResults.first, visibleSearchResults.last)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (isSearchSessionCurrent(searchKey, index.sessionId, revision)) {
                if (preserveResults) uiState = uiState.copy(browserWorkspace = previousWorkspace,
                    searchResultWindow = previousResults, currentPage = previousPage, hasNextPage = previousHasNext, isLoadingPage = false)
                uiState = uiState.copy(
                    message = error.message ?: strings[R.string.msg_could_not_load_search_page],
                    nextSearchPageError = error.message ?: strings[R.string.msg_could_not_load_search_page],
                    isError = true,
                )
                syncActiveSearch(persist = true)
            }
        } finally {
            if (isSearchSessionCurrent(searchKey, index.sessionId, revision)) {
                uiState = uiState.copy(isLoadingPage = false)
                syncActiveSearch()
            }
        }
    }

    private suspend fun fetchGallerySummaries(
        galleryIds: List<Long>,
    ): List<GallerySummary> = coroutineScope {
        val semaphore = Semaphore(GALLERY_DETAIL_CONCURRENCY)
        galleryIds.map { galleryId ->
            async {
                semaphore.withPermit {
                    try { hitomiClient.getGallery(galleryId) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) {
                            GallerySummary(
                                id = galleryId,
                                title = "Gallery #$galleryId",
                                artists = emptyList(),
                                series = emptyList(),
                                galleryType = null,
                                tags = emptyList(),
                                thumbnailUrl = null,
                                pageCount = 0,
                                language = null,
                                loadError = error.message ?: strings[R.string.msg_could_not_load_details],
                            )
                        }
                }
            }
        }.awaitAll()
    }

    private suspend fun runOperation(block: suspend () -> Unit) {
        uiState = uiState.copy(isBusy = true, message = null, isError = false)
        try {
            block()
        } catch (error: Exception) {
            uiState = uiState.copy(
                message = operationErrorMessage(error, strings),
                isError = true,
            )
        } finally {
            uiState = uiState.copy(isBusy = false)
        }
    }

    private inline fun update(transform: MainUiState.() -> MainUiState) {
        uiState = uiState.transform()
    }

    private fun normalizeFilterFacet(facet: String): String {
        val separator = facet.indexOf(':')
        if (separator < 0) return facet.trim().replace(Regex("\\s+"), "_")
        val prefix = facet.substring(0, separator).trim().lowercase()
        val value = facet.substring(separator + 1)
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), "_")
        return "$prefix:$value"
    }

    private fun refreshFilterSuggestions(query: String, library: Boolean = false, editor: Boolean = false) {
        suggestionJob?.cancel()
        fun publish(values: List<FilterSuggestion>, loading: Boolean) {
            uiState = if (editor) uiState.copy(subscriptionSuggestions=values,isLoadingSubscriptionSuggestions=loading)
                else if (library) uiState.copy(libraryFilterSuggestions = values, isLoadingLibrarySuggestions = loading)
                else uiState.copy(filterSuggestions = values, isLoadingFilterSuggestions = loading)
        }
        val rawToken = query.substring(searchTokenStart(query)).removePrefix("-").trim().lowercase()
        if (rawToken.isEmpty()) { publish(emptyList(), false); return }
        val type = rawToken.substringBefore(':', "")
        val partial = rawToken.substringAfter(':', rawToken)
        if ((':' in rawToken && type !in FILTER_TYPES) || partial.isEmpty()) { publish(emptyList(), false); return }
        val untyped = ':' !in rawToken
        val hints = if (untyped) FILTER_TYPES.filter { it.startsWith(partial) }.map { FilterSuggestion("$it:", strings[R.string.msg_filter, it]) } else emptyList()
        var local = if (untyped) localUntypedFilterSuggestions(partial) else localFilterSuggestions(type, partial)
        fun merged(remote: List<FilterSuggestion>) = if (untyped) mergeUntypedSuggestions(hints, local, remote, partial) else mergeSuggestions(local, remote, partial)
        val cacheKey = if (untyped) "untyped:$partial" else "$type:${partial.first().let { if (it.isDigit()) "0-9" else it.toString() }}"
        val generation = libraryProfileGeneration
        fun current() = generation == libraryProfileGeneration && (if (editor) uiState.subscriptionEditor?.query else if (library) uiState.libraryQueryDraft else uiState.searchQuery) == query
        publish(merged(suggestionCache[cacheKey].orEmpty()), true)
        suggestionJob = viewModelScope.launch {
            val books = uiState.libraryBooks
            val cached = librarySuggestionIndex?.takeIf { it.first === books }?.second
            val tokens = withContext(Dispatchers.Default) { cached ?: libraryFilterTokens(books) }
            if (!current()) return@launch
            if (books === uiState.libraryBooks) librarySuggestionIndex = books to tokens
            val libraryTokens = withContext(Dispatchers.Default) {
                tokens.asSequence().filter { if (untyped) it.substringAfter(':').startsWith(partial) else it.startsWith("$type:$partial") }
                    .take(MAX_FILTER_SUGGESTIONS).map { FilterSuggestion(it, it.substringAfter(':').replace('_', ' ')) }.toList()
            }
            if (!current()) return@launch
            local = local + libraryTokens
            suggestionCache[cacheKey]?.let { publish(merged(it), false); return@launch }
            publish(merged(emptyList()), true)
            delay(SUGGESTION_DEBOUNCE_MS)
            val remote = if (untyped) coroutineScope { UNTYPED_SUGGESTION_TYPES.map { facet -> async {
                runCatching { suggestionClient.getSuggestions(facet, partial) }.getOrDefault(emptyList())
            } }.awaitAll().flatten() } else runCatching { suggestionClient.getSuggestions(type, partial) }.getOrDefault(emptyList())
            if (!current()) return@launch
            suggestionCache[cacheKey] = remote
            val known = rememberKnownFilterTokens(remote.mapTo(linkedSetOf()) { it.token })
            if (current()) publish(merged(remote), false)
            if (known != uiState.knownFilterTokens) uiState = uiState.copy(knownFilterTokens = known)
        }
    }

    private fun localFilterSuggestions(type: String, partial: String): List<FilterSuggestion> {
        val favoriteTokens = uiState.searchFavorites.asSequence().flatMap { favorite ->
            favorite.query.split(Regex("\\s+")).asSequence()
        }
        return (uiState.knownFilterTokens.asSequence() + favoriteTokens)
            .map { it.removePrefix("-") }
            .filter { it.startsWith("$type:$partial") }
            .distinct()
            .take(MAX_FILTER_SUGGESTIONS)
            .map { FilterSuggestion(it, it.substringAfter(':').replace('_', ' ')) }
            .toList()
    }

    private fun localUntypedFilterSuggestions(partial: String): List<FilterSuggestion> {
        val favoriteTokens = uiState.searchFavorites.asSequence().flatMap { favorite ->
            favorite.query.split(Regex("\\s+")).asSequence()
        }
        return (uiState.knownFilterTokens.asSequence() + favoriteTokens)
            .map { it.removePrefix("-") }
            .filter { token -> token.substringAfter(':', "").startsWith(partial) }
            .distinct()
            .take(MAX_FILTER_SUGGESTIONS)
            .map { token ->
                FilterSuggestion(token, token.substringAfter(':').replace('_', ' '))
            }
            .toList()
    }

    private fun mergeUntypedSuggestions(
        typeHints: List<FilterSuggestion>,
        local: List<FilterSuggestion>,
        remote: List<FilterSuggestion>,
        partial: String,
    ): List<FilterSuggestion> = (typeHints + local + remote)
        .filter { suggestion ->
            suggestion.token.substringBefore(':').startsWith(partial) ||
                suggestion.token.substringAfter(':', "").startsWith(partial)
        }
        .distinctBy { it.token }
        .take(MAX_FILTER_SUGGESTIONS)

    private fun mergeSuggestions(
        local: List<FilterSuggestion>,
        remote: List<FilterSuggestion>,
        partial: String,
    ): List<FilterSuggestion> = (local + remote)
        .filter { it.token.substringAfter(':').startsWith(partial) }
        .distinctBy { it.token }
        .take(MAX_FILTER_SUGGESTIONS)

    private fun filterValue(value: String): String = normalizeFilterValue(value)

    private fun rememberFilterTokens(galleries: List<GallerySummary>): Set<String> {
        val discovered = galleries.asSequence().flatMap { gallery ->
            sequence {
                gallery.artists.forEach { yield("artist:${filterValue(it)}") }
                gallery.series.forEach { yield("series:${filterValue(it)}") }
                gallery.galleryType?.let { yield("type:${filterValue(it)}") }
                gallery.tags.forEach { yield("${it.type}:${filterValue(it.name)}") }
            }
        }.filter { it.substringAfter(':').isNotBlank() }.toSet()
        return rememberKnownFilterTokens(discovered)
    }

    private fun rememberKnownFilterTokens(discovered: Set<String>): Set<String> {
        if (discovered.isEmpty()) return uiState.knownFilterTokens
        val merged = (uiState.knownFilterTokens + discovered)
            .toList()
            .takeLast(MAX_KNOWN_FILTER_TOKENS)
            .toSet()
        if (merged != uiState.knownFilterTokens) {
            browserPreferenceStore.saveKnownFilterTokens(merged)
        }
        return merged
    }

    private fun rememberViewedGallery(galleryId: Long): Set<Long> {
        if (galleryId in uiState.viewedGalleryIds) return uiState.viewedGalleryIds
        val viewed = (uiState.viewedGalleryIds + galleryId)
            .toList()
            .takeLast(MAX_VIEWED_GALLERIES)
            .toSet()
        browserPreferenceStore.saveViewedGalleryIds(viewed)
        return viewed
    }

    private fun cacheGalleries(
        existing: Map<Long, GallerySummary>,
        incoming: List<GallerySummary>,
    ): Map<Long, GallerySummary> {
        val cache = LinkedHashMap(existing)
        incoming.forEach { gallery ->
            cache.remove(gallery.id)
            cache[gallery.id] = presentedGallery(gallery)
        }
        while (cache.size > MAX_CACHED_GALLERIES) {
            cache.remove(cache.keys.first())
        }
        return cache
    }

    private fun persistCustomSeries(assignments: Map<String, CustomSeriesAssignment>): Boolean {
        if (requireDesktopEdit() == null) return false
        val changed = (uiState.customSeriesByBookId.keys + assignments.keys).filter { !uiState.customSeriesByBookId[it].sameSeriesStateAs(assignments[it]) }
        val patches = org.json.JSONArray()
        for (id in changed) {
            val book = uiState.libraryBooks.firstOrNull { it.id == id } ?: continue
            if (book.syncId == null) { uiState = uiState.copy(message = strings[R.string.ui_connect_refresh_before_edit], isError = true); return false }
            val assignment = assignments[id]
            patches.put(org.json.JSONObject().put("mutationId", java.util.UUID.randomUUID().toString()).put("bookSyncId", book.syncId)
                .put("baseVersion", book.syncStateVersion).put("name", assignment?.name ?: org.json.JSONObject.NULL)
                .put("order", assignment?.order ?: 0).put("modifiedAt", System.currentTimeMillis()))
        }
        if (patches.length() > 0) submitServerEdit("series", org.json.JSONObject().put("assignments", patches))
        return true
    }

    private fun pendingLibrarySyncCount(): Int = (selectedProfile()?.let { serverEdits.pending(it.id).size } ?: 0) + distinctPendingSyncCount(
        libraryPreferenceStore.loadPendingSeriesSyncIds(),
        libraryPreferenceStore.loadPendingBookStateSyncIds(),
        uiState.libraryBooks,
    ) + uiState.presentations.values.count { it.dirty }

    private fun enqueueBookStateSync(changedIds: Set<String>, addHistory: Boolean = false) {
        val syncableIds = uiState.libraryBooks.asSequence()
            .filter { it.id in changedIds && it.syncId != null }
            .mapTo(linkedSetOf(), LibraryBook::id)
        if (syncableIds.isEmpty()) return
        val existingModifiedTimes = libraryPreferenceStore.loadBookStateModifiedTimes()
        val modifiedAt = maxOf(
            System.currentTimeMillis(),
            syncableIds.maxOfOrNull { existingModifiedTimes[it] ?: 0L }?.plus(1) ?: 0L,
        )
        libraryPreferenceStore.saveBookStateModifiedTimes(
            existingModifiedTimes + syncableIds.associateWith { modifiedAt },
        )
        val pending = libraryPreferenceStore.loadPendingBookStateSyncIds() + syncableIds
        libraryPreferenceStore.savePendingBookStateSyncIds(pending)
        if (addHistory) pendingHistoryBookIds += syncableIds
        uiState = uiState.copy(librarySyncPendingCount = pendingLibrarySyncCount())
        scheduleLibrarySync()
    }

    fun onAppForegrounded() {
        viewModelScope.launch { reloadSubscriptions() }
        refreshSubscriptions()
        startConnectionMonitoring()
        if (!hasEnteredForeground) {
            hasEnteredForeground = true
        }
        scheduleLibrarySync(0L)
    }

    fun onAppBackgrounded() {
        syncActiveSearch(persist = true)
        browserPreferenceStore.saveGalleryWorkspace(uiState.galleryWorkspace)
        stopConnectionMonitoring()
        if (pendingRefreshIsReconnect) importantLibraryRefreshJob?.cancel()
        librarySyncDebounceJob?.cancel()
        librarySyncDebounceJob = viewModelScope.launch { flushPendingLibrarySync() }
    }

    private fun requestImportantLibraryRefresh() = requestLibraryRefreshWhenIdle(reconnect = false)

    private fun requestReconnectLibraryRefresh() = requestLibraryRefreshWhenIdle(reconnect = true)

    private fun requestLibraryRefreshWhenIdle(reconnect: Boolean) {
        // A queued server change is stronger than a reconnect check.
        if (reconnect && importantLibraryRefreshJob?.isActive == true) return
        importantLibraryRefreshJob?.cancel()
        pendingRefreshIsReconnect = reconnect
        val generation = libraryProfileGeneration
        val completed = successfulDesktopRefreshes
        val inFlight = libraryRefreshJob
        importantLibraryRefreshJob = viewModelScope.launch {
            inFlight?.join()
            while (uiState.isLibraryScanning || uiState.isLibrarySyncing) {
                delay(IMPORTANT_LIBRARY_REFRESH_RETRY_MS)
            }
            if (generation == libraryProfileGeneration && (!reconnect || successfulDesktopRefreshes == completed)) {
                refreshLibrarySilently()
            }
        }
    }

    private fun scheduleLibrarySync(delayMs: Long = LIBRARY_SYNC_DEBOUNCE_MS, force: Boolean = false) {
        uiState = uiState.copy(librarySyncPendingCount = pendingLibrarySyncCount())
        if (!force && pendingLibrarySyncCount() == 0) return
        librarySyncDebounceJob?.cancel()
        librarySyncDebounceJob = viewModelScope.launch {
            delay(delayMs)
            try { flushPendingLibrarySync() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                uiState = uiState.copy(desktopLibraryReady = false, librarySyncError = error.message)
            }
        }
    }

    private suspend fun flushPendingLibrarySync(profileOverride: DesktopProfile? = null, booksOverride: List<LibraryBook>? = null) = librarySyncFlushMutex.withLock {
        val profile = profileOverride ?: selectedProfile() ?: return@withLock
        if (uiState.desktopConnectionState != DesktopConnectionState.CONNECTED) return@withLock
        replayServerEdits(profile)
        syncSubscriptions(profile)
        syncPresentations(profile)
    }

    private data class BookStateSyncAttempt(
        val update: BookStateSyncUpdate,
        val bookIds: Set<String>,
        val expectedModifiedAtByBookId: Map<String, Long>,
    )

    private suspend fun syncBookStateUpdates(
        profile: DesktopProfile,
        books: List<LibraryBook>,
        ids: Set<String>,
        addHistory: Boolean = false,
        uploadLocalBooks: Boolean = true,
    ) = bookStateSyncMutex.withLock {
        if (uploadLocalBooks) uploadMissingLocalBooks(profile, books, ids)
        val customTitles = libraryPreferenceStore.loadCustomTitles()
        val presentations = withContext(Dispatchers.IO) { presentationStore(profile.id).all().associateBy { it.value.key } }
        val modifiedTimes = libraryPreferenceStore.loadBookStateModifiedTimes()
        val versionBySyncId = latestSyncStateVersions(books)
        val booksBySyncId = books
            .filter { it.syncId != null }
            .groupBy { it.syncId!!.lowercase() }
        val attempts = books.asSequence()
            .filter { it.id in ids }
            // Legacy title fields must not bypass an unresolved presentation conflict.
            .filter { book -> runCatching { presentations[book.presentationKey()]?.dirty != true }.getOrDefault(true) }
            .mapNotNull { book ->
                val syncId = book.syncId ?: return@mapNotNull null
                val relatedBooks = booksBySyncId[syncId.lowercase()].orEmpty()
                val seriesName = uiState.customSeriesByBookId[book.id]?.name
                val modifiedAt = modifiedTimes[book.id] ?: 0L
                val mutationId = stableBookStateMutationId(
                    profileId = profile.id,
                    syncId = syncId,
                    modifiedAt = modifiedAt,
                )
                BookStateSyncAttempt(
                    update = BookStateSyncUpdate(
                        mutationId = mutationId,
                        bookSyncId = syncId,
                        baseVersion = versionBySyncId[syncId.lowercase()] ?: book.syncStateVersion,
                        modifiedAt = modifiedAt,
                        currentPage = uiState.libraryProgress[book.id] ?: 0,
                        isFavorite = book.id in uiState.libraryFavoriteIds,
                        isRead = book.id in uiState.libraryReadIds,
                        isHidden = book.id in uiState.libraryHiddenIds,
                        customTitle = runCatching { presentations[book.presentationKey()]?.value?.title }.getOrNull() ?: customTitles[book.id],
                        seriesFavorite = seriesName in uiState.libraryFavoriteSeriesNames,
                        historyEventId = mutationId.takeIf { addHistory },
                        historyViewedAt = if (addHistory) {
                            Instant.ofEpochMilli(modifiedAt).toString()
                        } else {
                            null
                        },
                    ),
                    bookIds = relatedBooks.mapTo(linkedSetOf(), LibraryBook::id),
                    expectedModifiedAtByBookId = relatedBooks.associate { related ->
                        related.id to (modifiedTimes[related.id] ?: 0L)
                    },
                )
            }
            .groupBy { it.update.bookSyncId.lowercase() }
            .map { (_, candidates) ->
                val newest = candidates.maxBy { it.update.modifiedAt }
                newest.copy(
                    bookIds = candidates.flatMapTo(linkedSetOf(), BookStateSyncAttempt::bookIds),
                    expectedModifiedAtByBookId = candidates
                        .flatMap { it.expectedModifiedAtByBookId.entries }
                        .associate { it.toPair() },
                )
            }
            .toList()
        if (attempts.isEmpty()) return@withLock emptySet()

        val completedBookIds = linkedSetOf<String>()
        val failed = mutableListOf<BookStateSyncUpdate>()
        val failureMessages = linkedSetOf<String>()
        var conflictCount = 0
        attempts.chunked(MAX_BOOK_STATE_SYNC_MUTATIONS).forEach { batch ->
            val attemptsByMutationId = batch.associateBy { it.update.mutationId }
            val expectedModifiedAtByBookId = batch
                .flatMap { it.expectedModifiedAtByBookId.entries }
                .associate { it.toPair() }
            var pending = batch.map(BookStateSyncAttempt::update)
            var lastError: Throwable? = null
            for (attempt in 0..BOOK_STATE_SYNC_MAX_RETRIES) {
                val request = runCatching { client.saveBookStates(profile, pending) }
                lastError = request.exceptionOrNull() ?: lastError
                val results = request.getOrNull()
                if (results != null) {
                    val completedResults = results.filter(BookStateSyncResult::isCompleted)
                    conflictCount += completedResults.count { it.status == "conflict" }
                    val completedMutationIds = completedResults
                        .mapTo(hashSetOf(), BookStateSyncResult::mutationId)
                    completedBookIds += completedMutationIds.flatMap { mutationId ->
                        attemptsByMutationId[mutationId]?.bookIds.orEmpty()
                    }
                    applySyncedBookStates(
                        remoteStates = completedResults.mapNotNull(BookStateSyncResult::state),
                        books = books,
                        expectedModifiedAtByBookId = expectedModifiedAtByBookId,
                    )
                    pending = pending.filterNot { it.mutationId in completedMutationIds }
                    if (pending.isEmpty()) break
                }
                if (attempt < BOOK_STATE_SYNC_MAX_RETRIES) {
                    delay(BOOK_STATE_SYNC_RETRY_BASE_DELAY_MS shl attempt)
                }
            }
            if (pending.isNotEmpty()) {
                failed += pending
                lastError?.message?.takeIf(String::isNotBlank)?.let(failureMessages::add)
            }
        }

        val failureDetails = failureMessages.joinToString("\n").takeIf(String::isNotBlank)
        val failureMessage = strings[R.string.msg_could_not_sync_book_states, (failed.size)]
        uiState = uiState.copy(
            librarySyncPendingCount = pendingLibrarySyncCount(),
            librarySyncError = if (failed.isEmpty()) uiState.librarySyncError
            else combinedSyncError(uiState.librarySyncError, failureDetails ?: failureMessage),
            message = when {
                failed.isNotEmpty() -> buildString {
                    append(failureMessage)
                    failureDetails?.lineSequence()?.firstOrNull()?.let { append(" ").append(it) }
                }
                conflictCount > 0 -> strings[R.string.msg_applied_changes_from_another_device, (conflictCount)]
                else -> uiState.message
            },
            isError = failed.isNotEmpty(),
        )
        if (pendingLibrarySyncCount() > 0) scheduleLibrarySync()
        completedBookIds
    }

    private suspend fun drainBookStateChanges(profile: DesktopProfile) =
        bookStateChangeMutex.withLock {
            var cursor = libraryPreferenceStore.loadBookStateSyncCursor(profile.id)
            do {
                val page = runCatching { client.getBookStateChanges(profile, cursor) }
                    .getOrNull() ?: return@withLock
                val remoteStates = page.changes.asSequence()
                    .filterNot { it.deviceId == profile.deviceId }
                    .map { it.state }
                    .toList()
                applySyncedBookStates(remoteStates, uiState.libraryBooks)
                cursor = page.cursor.coerceAtLeast(cursor)
                libraryPreferenceStore.saveBookStateSyncCursor(profile.id, cursor)
            } while (page.hasMore)
        }

    private fun applySyncedBookStates(
        remoteStates: List<SyncedBookState>,
        books: List<LibraryBook>,
        expectedModifiedAtByBookId: Map<String, Long>? = null,
    ): Set<String> {
        if (remoteStates.isEmpty()) return emptySet()

        val favoriteIds = libraryPreferenceStore.loadFavoriteIds().toMutableSet()
        val readIds = libraryPreferenceStore.loadReadIds().toMutableSet()
        val hiddenIds = libraryPreferenceStore.loadHiddenIds().toMutableSet()
        val progress = libraryPreferenceStore.loadProgress().toMutableMap()
        val customTitles = libraryPreferenceStore.loadCustomTitles().toMutableMap()
        val favoriteSeriesNames = libraryPreferenceStore.loadFavoriteSeriesNames().toMutableSet()
        val modifiedTimes = libraryPreferenceStore.loadBookStateModifiedTimes().toMutableMap()
        val pendingIds = libraryPreferenceStore.loadPendingBookStateSyncIds().toMutableSet()
        val booksBySyncId = books.groupBy { it.syncId?.lowercase() }
        val receivedStatesByBookId = mutableMapOf<String, SyncedBookState>()
        val acceptedStatesByBookId = mutableMapOf<String, SyncedBookState>()
        remoteStates.forEach { state ->
            booksBySyncId[state.bookSyncId.lowercase()].orEmpty().forEach { book ->
                receivedStatesByBookId[book.id] = state
                val localModifiedAt = modifiedTimes[book.id] ?: 0L
                if (
                    !shouldAcceptBookStateResponse(
                        bookId = book.id,
                        localModifiedAt = localModifiedAt,
                        isPending = book.id in pendingIds,
                        expectedModifiedAtByBookId = expectedModifiedAtByBookId,
                    )
                ) {
                    pendingIds.add(book.id)
                    return@forEach
                }
                pendingIds.remove(book.id)
                acceptedStatesByBookId[book.id] = state
                favoriteIds.setMembership(book.id, state.isFavorite)
                readIds.setMembership(book.id, state.isRead)
                hiddenIds.setMembership(book.id, state.isHidden)
                progress[book.id] = state.currentPage
                if (state.customTitle == null) customTitles.remove(book.id)
                else customTitles[book.id] = state.customTitle
                modifiedTimes[book.id] = state.modifiedAt
                uiState.customSeriesByBookId[book.id]?.name?.let { seriesName ->
                    favoriteSeriesNames.setMembership(
                        seriesName,
                        state.seriesFavorite,
                    )
                }
            }
        }
        libraryPreferenceStore.saveFavoriteIds(favoriteIds)
        libraryPreferenceStore.saveReadIds(readIds)
        libraryPreferenceStore.saveHiddenIds(hiddenIds)
        libraryPreferenceStore.saveProgress(progress)
        libraryPreferenceStore.saveCustomTitles(customTitles)
        libraryPreferenceStore.saveFavoriteSeriesNames(favoriteSeriesNames)
        libraryPreferenceStore.saveBookStateModifiedTimes(modifiedTimes)
        libraryPreferenceStore.savePendingBookStateSyncIds(pendingIds)
        fun LibraryBook.withSyncedState(): LibraryBook {
            val state = receivedStatesByBookId[id] ?: return this
            val accepted = id in acceptedStatesByBookId
            return copy(
                title = if (accepted) state.customTitle ?: title else title,
                syncStateVersion = state.version,
                syncedStateModifiedAt = state.modifiedAt,
                syncedFavorite = state.isFavorite,
                syncedRead = state.isRead,
                syncedHidden = state.isHidden,
                syncedProgress = state.currentPage,
                syncedCustomTitle = state.customTitle,
                syncedSeriesFavorite = state.seriesFavorite,
            )
        }
        uiState = uiState.copy(
            libraryBooks = uiState.libraryBooks.map { presentedBook(it.withSyncedState()) },
            activeLibraryBook = uiState.activeLibraryBook?.withSyncedState()?.let(::presentedBook),
            libraryFavoriteIds = favoriteIds,
            libraryReadIds = readIds,
            libraryHiddenIds = hiddenIds,
            libraryProgress = progress,
            libraryFavoriteSeriesNames = favoriteSeriesNames,
            librarySyncPendingCount = pendingLibrarySyncCount(),
        )
        if (pendingIds.isNotEmpty()) scheduleLibrarySync()
        return acceptedStatesByBookId.keys
    }

    fun retryDesktopDownloadRequest(id: String) {
        val profile = selectedProfile() ?: return
        if (uiState.isDownloadSyncing) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { requestStore.change(id) { request ->
                if (request.target == DownloadTarget.DESKTOP && !request.isBusy && request.status != RequestStatus.COMPLETED && (request.profileId == null || request.profileId == profile.id))
                    request.copy(status = RequestStatus.WAITING, error = null) else request
            } }
            sendPendingDownloadRequests(profile, onlyRequestId = id)
        }
    }

    fun syncDownloadRequests() {
        val profile = selectedProfile() ?: run {
            uiState = uiState.copy(downloadQueueError = strings[R.string.msg_connect_to_desktop_first])
            return
        }
        if (uiState.isDownloadSyncing) return
        viewModelScope.launch { sendPendingDownloadRequests(profile) }
    }

    private suspend fun sendPendingDownloadRequests(
        profile: DesktopProfile,
        onlyRequestId: String? = null,
        requireRequestIds: Boolean = true,
    ): List<String> = downloadSyncMutex.withLock {
        val pending = requestStore.requests.value.filter {
            it.pendingForDesktop(profile.id) && (onlyRequestId == null || it.id == onlyRequestId)
        }
        uiState = uiState.copy(isDownloadSyncing = true, downloadQueueError = null)
        val errors = mutableListOf<String>()
        try {
            val downloads = sortDownloadQueueNewest(client.getDownloads(profile))
            withContext(Dispatchers.IO) { requestStore.update { it.confirmDesktopCompletions(profile.id, downloads) } }
            if (selectedProfile()?.id == profile.id) uiState = uiState.copy(downloadQueue = downloads)
            if (pending.isEmpty()) return@withLock emptyList()
            val status = client.getStatus(profile.baseUrl)
            check(!requireRequestIds || status.downloadRequestIds) {
                strings[R.string.msg_update_desktop_to_a_version_supporting_request_ids_pen]
            }
            for (request in pending) {
                var claimed = false
                withContext(Dispatchers.IO) {
                    requestStore.change(request.id) { current ->
                        if (!current.pendingForDesktop(profile.id)) current else {
                            claimed = true
                            current.copy(profileId = profile.id, status = RequestStatus.SENDING, desktopAttempted = true, error = null)
                        }
                    }
                }
                if (!claimed) continue
                try {
                    val item = client.requestDownload(profile, request.galleryId, request.id)
                    withContext(Dispatchers.IO) { requestStore.change(request.id) { it.copy(status = if (item.status == DownloadStatus.COMPLETED) RequestStatus.COMPLETED else RequestStatus.SENT, desktopQueueId = item.id, error = null) } }
                    if (selectedProfile()?.id == profile.id) uiState = uiState.copy(
                        downloadQueue = sortDownloadQueueNewest((uiState.downloadQueue + item).distinctBy { it.id }),
                    )
                } catch (error: Exception) {
                    withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                        requestStore.change(request.id) { it.copy(status = RequestStatus.WAITING,
                            desktopAttempted = request.desktopAttempted || !downloadDefinitelyNotSubmitted(error), error = error.message) }
                    }
                    if (error is CancellationException) throw error
                    errors += "${request.title}: ${error.message}"
                    // Keep later requests untouched when the server becomes unavailable.
                    break
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            errors += error.message ?: strings[R.string.msg_download_request_sync_failed]
        } finally {
            uiState = uiState.copy(isDownloadSyncing = false,
                downloadQueueError = errors.takeIf { it.isNotEmpty() }?.joinToString("\n"))
        }
        if (errors.isEmpty()) uiState = uiState.copy(message = strings[R.string.msg_pending_requests_sent_to_pc_download_queue], isError = false)
        errors
    }

    suspend fun prepareLocalBackup(locale: String): java.io.File {
        require(uiState.profiles.size <= 1) { strings[R.string.connection_choose_one] }
        require(!uiState.isLibrarySyncing) { strings[R.string.backup_wait] }
        libraryRefreshJob?.join()
        val profile = selectedProfile()
        var syncedIds = emptySet<String>()
        if (profile != null) {
            uiState = uiState.copy(librarySyncError = null)
            // A public status response alone does not prove this pairing can access the library.
            withContext(Dispatchers.IO) { client.getLibraryBooks(profile) }
            refreshLibrarySilently()
            libraryRefreshJob?.join()
            check(uiState.libraryScanError == null) { uiState.libraryScanError.orEmpty() }
            val uploadErrors = uploadMissingLocalBooks(profile, uiState.libraryBooks, uiState.libraryBooks.mapTo(mutableSetOf()) { it.id })
            check(uploadErrors.isEmpty()) { uploadErrors.joinToString("\n") }
            flushPendingLibrarySync()
            val error = com.doujinzip.android.data.SubscriptionRefresh.run(getApplication(), profile, forceSync = true, check = false)
            check(error == null) { error.orEmpty() }
            check(uiState.librarySyncError == null && uiState.presentationSyncError == null && pendingLibrarySyncCount() == 0) {
                uiState.librarySyncError ?: uiState.presentationSyncError ?: strings[R.string.backup_pending]
            }
            check(withContext(Dispatchers.IO) {
                subscriptionStore.records(profile.id).none {
                    (it.mutationId != null || it.conflict != null) && !it.value.matchesDeletedSubscription(it.conflict)
                }
            }) { strings[R.string.backup_pending] }
            syncedIds = withContext(Dispatchers.IO) { client.getLibraryBooks(profile).filter { it.syncStateVersion > 0 && it.hasSyncedSeriesState }.mapTo(mutableSetOf()) { it.id } }
            val now = System.currentTimeMillis()
            browserPreferenceStore.saveLastDesktopSync(now)
            uiState = uiState.copy(lastDesktopSyncAt = now, desktopConnectionState = DesktopConnectionState.CONNECTED)
        }
        librarySyncDebounceJob?.cancel()
        return presentationSyncMutex.withLock {
            com.doujinzip.android.data.SubscriptionRefresh.snapshot {
                withContext(Dispatchers.IO) {
                    val store = com.doujinzip.android.data.LocalBackupStore(getApplication())
                    val archive = store.capture(syncedIds, profile?.id, locale)
                    val target = java.io.File.createTempFile("local-backup-", ".zip", getApplication<Application>().cacheDir)
                    try {
                        target.outputStream().use { com.doujinzip.android.data.BackupFormat.write(archive, it) }
                        target.inputStream().use { store.validate(com.doujinzip.android.data.BackupFormat.read(it)) }
                        target
                    } catch (error: Throwable) { target.delete(); throw error }
                }
            }
        }
    }

    fun syncLibraryNow() {
        val profile = selectedProfile()
        if (profile == null || uiState.isLibrarySyncing) {
            if (profile == null) uiState = uiState.copy(librarySyncError = strings[R.string.msg_connect_to_desktop_first],
                message = strings[R.string.msg_connect_to_desktop_first], isError = true)
            return
        }
        viewModelScope.launch {
            librarySyncDebounceJob?.cancel()
            uiState = uiState.copy(isLibrarySyncing = true, librarySyncError = null)
            try {
                val downloadErrors = sendPendingDownloadRequests(profile)
                flushPendingLibrarySync(profile, uiState.libraryBooks)
                val errors = downloadErrors + listOfNotNull(uiState.subscriptionSyncError)
                if (errors.isNotEmpty()) uiState = uiState.copy(librarySyncError = errors.joinToString("\n"),
                    message = strings[R.string.msg_some_items_could_not_sync_check_the_pending_list], isError = true)
                if (errors.isEmpty() && uiState.librarySyncError == null && uiState.presentationSyncError == null && pendingLibrarySyncCount() == 0) {
                    val now = System.currentTimeMillis()
                    browserPreferenceStore.saveLastDesktopSync(now)
                    uiState = uiState.copy(lastDesktopSyncAt = now)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                uiState = uiState.copy(librarySyncError = error.message, message = error.message, isError = true)
            } finally {
                uiState = uiState.copy(isLibrarySyncing = false)
                refreshLibrarySilently()
            }
        }
    }

    private suspend fun syncSeriesUpdates(
        profile: DesktopProfile,
        books: List<LibraryBook>,
        assignments: Map<String, CustomSeriesAssignment>,
        removalTimes: Map<String, Long>,
        ids: Set<String>,
        uploadLocalBooks: Boolean = true,
    ) = seriesSyncMutex.withLock {
        val uploadFailures = if (uploadLocalBooks) {
            uploadMissingLocalBooks(profile, books, ids)
        } else {
            emptyList()
        }
        if (uploadFailures.isNotEmpty()) {
            uiState = uiState.copy(
                librarySyncError = uploadFailures.joinToString("\n"),
                message = strings[R.string.msg_could_not_transfer_local_files_before_saving_series, (uploadFailures.size)],
                isError = true,
            )
        }
        val updates = books.asSequence()
            .filter { it.id in ids }
            .mapNotNull { book ->
                val syncId = book.syncId ?: return@mapNotNull null
                val assignment = assignments[book.id]
                val modifiedAt = assignment?.modifiedAt ?: removalTimes[book.id] ?: return@mapNotNull null
                SeriesSyncUpdate(
                    bookSyncId = syncId,
                    assignment = assignment,
                    modifiedAt = modifiedAt,
                )
            }
            .groupBy { it.bookSyncId.lowercase() }
            .map { (_, candidates) -> candidates.maxBy(SeriesSyncUpdate::modifiedAt) }
            .toList()
        if (updates.isEmpty()) return@withLock

        val failed = mutableListOf<SeriesSyncUpdate>()
        val failureMessages = linkedSetOf<String>()
        updates.chunked(MAX_SERIES_SYNC_ASSIGNMENTS).forEach { batch ->
            val outcome = syncSeriesBatchResilient(profile, batch)
            failed += outcome.failed
            outcome.errorMessage?.let(failureMessages::add)
            settleSeriesPendingBatch(
                books = books,
                attempted = batch,
                failed = outcome.failed,
                remoteWinnerSyncIds = outcome.remoteWinnerSyncIds,
            )
        }
        if (failed.isNotEmpty()) {
            val details = failureMessages.joinToString("\n").takeIf(String::isNotBlank)
            uiState = uiState.copy(
                librarySyncError = combinedSyncError(
                    uiState.librarySyncError,
                    details ?: strings[R.string.msg_could_not_save_series_items_on_desktop, (failed.size)],
                ),
                message = buildString {
                    append(strings[R.string.msg_could_not_save_series_items_on_desktop, (failed.size)])
                    if (details != null) append(" ").append(details.lineSequence().first())
                },
                isError = true,
            )
        }
        uiState = uiState.copy(
            librarySyncPendingCount = pendingLibrarySyncCount(),
        )
    }

    private fun settleSeriesPendingBatch(
        books: List<LibraryBook>,
        attempted: List<SeriesSyncUpdate>,
        failed: List<SeriesSyncUpdate>,
        remoteWinnerSyncIds: Set<String>,
    ) {
        val attemptedBySyncId = attempted.associateBy { it.bookSyncId.lowercase() }
        val failedSyncIds = failed.mapTo(hashSetOf()) { it.bookSyncId.lowercase() }
        val currentAssignments = libraryPreferenceStore.loadCustomSeries()
        val currentRemovals = libraryPreferenceStore.loadSeriesRemovalTimes()
        val completedIds = books.asSequence().mapNotNull { book ->
            val syncId = book.syncId?.lowercase() ?: return@mapNotNull null
            val update = attemptedBySyncId[syncId] ?: return@mapNotNull null
            if (syncId in failedSyncIds) return@mapNotNull null
            val currentAssignment = currentAssignments[book.id]
            val stillSameLocalRevision = if (update.assignment == null) {
                currentAssignment == null && currentRemovals[book.id] == update.modifiedAt
            } else {
                currentAssignment?.modifiedAt == update.modifiedAt &&
                    currentAssignment.sameSeriesStateAs(update.assignment)
            }
            book.id.takeIf { stillSameLocalRevision || syncId in remoteWinnerSyncIds }
        }.toSet()
        libraryPreferenceStore.savePendingSeriesSyncIds(
            libraryPreferenceStore.loadPendingSeriesSyncIds() - completedIds,
        )
        uiState = uiState.copy(librarySyncPendingCount = pendingLibrarySyncCount())
    }

    private suspend fun uploadMissingLocalBooks(
        profile: DesktopProfile,
        books: List<LibraryBook>,
        ids: Set<String>? = null,
    ): List<String> {
        val remoteSyncIds = books.asSequence()
            .filter(LibraryBook::isCloud)
            .mapNotNull { it.syncId?.lowercase() }
            .toHashSet()
        val targets = books.asSequence()
            .filterNot(LibraryBook::isCloud)
            .filter { ids == null || it.id in ids }
            .filter { book -> book.syncId?.lowercase()?.let { it !in remoteSyncIds } == true }
            .distinctBy { it.syncId?.lowercase() }
            .toList()
        val failures = mutableListOf<String>()
        val desktopApiVersion = if (targets.any(LibraryBook::usesExistingArchive)) {
            runCatching { client.getStatus(profile.baseUrl).version }.getOrNull()
        } else {
            null
        }
        targets.forEach { book ->
            if (
                book.usesExistingArchive() &&
                (desktopApiVersion ?: 0) < SAFE_ARCHIVE_IMPORT_API_VERSION
            ) {
                failures += if (desktopApiVersion == null) {
                    strings[R.string.msg_could_not_check_desktop_compatibility, (book.title)]
                } else {
                    strings[R.string.msg_update_desktop_for_safe_transfer, (book.title)]
                }
                return@forEach
            }
            val prepared = runCatching { libraryUploadPreparer.prepare(book) }.getOrElse { error ->
                failures += "${book.title}: ${error.message ?: strings[R.string.msg_upload_preparation_failed]}"
                return@forEach
            }
            var uploaded = false
            var lastError: Throwable? = null
            for (attempt in 0..FILE_UPLOAD_MAX_RETRIES) {
                val result = runCatching {
                    client.uploadLibraryArchive(
                        profile = profile,
                        archive = prepared.file,
                        fileName = prepared.fileName,
                        syncId = prepared.syncId,
                    )
                }
                if (result.isSuccess) {
                    uploaded = true
                    break
                }
                lastError = result.exceptionOrNull()
                if (attempt < FILE_UPLOAD_MAX_RETRIES) {
                    delay(FILE_UPLOAD_RETRY_BASE_DELAY_MS shl attempt)
                }
            }
            prepared.file.delete()
            if (!uploaded) {
                failures += "${book.title}: ${lastError?.message ?: strings[R.string.msg_file_upload_failed]}"
            }
        }
        return failures
    }

    private data class SeriesBatchOutcome(
        val failed: List<SeriesSyncUpdate>,
        val remoteWinnerSyncIds: Set<String>,
        val errorMessage: String? = null,
        val rejectedByClientError: Boolean = false,
    )

    private suspend fun syncSeriesBatchResilient(
        profile: DesktopProfile,
        updates: List<SeriesSyncUpdate>,
    ): SeriesBatchOutcome {
        val outcome = syncSeriesBatchWithRetry(profile, updates)
        if (!outcome.rejectedByClientError || updates.size == 1) return outcome
        val midpoint = updates.size / 2
        val left = syncSeriesBatchResilient(profile, updates.subList(0, midpoint))
        val right = syncSeriesBatchResilient(profile, updates.subList(midpoint, updates.size))
        return SeriesBatchOutcome(
            failed = left.failed + right.failed,
            remoteWinnerSyncIds = left.remoteWinnerSyncIds + right.remoteWinnerSyncIds,
            errorMessage = listOfNotNull(left.errorMessage, right.errorMessage)
                .distinct()
                .joinToString("\n")
                .takeIf(String::isNotBlank),
            rejectedByClientError = left.rejectedByClientError || right.rejectedByClientError,
        )
    }

    private suspend fun syncSeriesBatchWithRetry(
        profile: DesktopProfile,
        updates: List<SeriesSyncUpdate>,
    ): SeriesBatchOutcome {
        var pending = updates
        val remoteWinnerSyncIds = linkedSetOf<String>()
        var lastError: Throwable? = null
        repeat(SERIES_SYNC_MAX_RETRIES + 1) { attempt ->
            val saveResult = runCatching { client.saveLibrarySeries(profile, pending) }
            lastError = saveResult.exceptionOrNull() ?: lastError
            saveResult.getOrNull()?.let { results ->
                remoteWinnerSyncIds += applySeriesSyncConflicts(results)
                val bySyncId = results.associateBy { it.bookSyncId.lowercase() }
                pending = pending.filter { update ->
                    when (bySyncId[update.bookSyncId.lowercase()]?.status) {
                        "applied", "already_applied", "conflict" -> false
                        else -> true
                    }
                }
                if (pending.isEmpty()) {
                    return SeriesBatchOutcome(emptyList(), remoteWinnerSyncIds)
                }
            }

            val apiError = saveResult.exceptionOrNull() as? CompanionApiException
            if (apiError?.statusCode?.let { it in 400..499 } == true) {
                return SeriesBatchOutcome(
                    failed = pending,
                    remoteWinnerSyncIds = remoteWinnerSyncIds,
                    errorMessage = apiError.message,
                    rejectedByClientError = true,
                )
            }

            val remoteBooks = if (saveResult.isFailure) {
                runCatching { client.getLibraryBooks(profile) }.getOrNull()
            } else null
            if (remoteBooks != null) {
                pending = pendingSeriesSyncUpdates(pending, remoteBooks)
                if (pending.isEmpty()) {
                    return SeriesBatchOutcome(emptyList(), remoteWinnerSyncIds)
                }
            }

            if (attempt < SERIES_SYNC_MAX_RETRIES) {
                delay(SERIES_SYNC_RETRY_BASE_DELAY_MS shl attempt)
            }
        }
        return SeriesBatchOutcome(
            failed = pending,
            remoteWinnerSyncIds = remoteWinnerSyncIds,
            errorMessage = lastError?.message,
        )
    }

    private fun applySeriesSyncConflicts(results: List<SeriesSyncResult>): Set<String> {
        val conflicts = results.filter { it.status == "conflict" }
        if (conflicts.isEmpty()) return emptySet()
        val assignments = uiState.customSeriesByBookId.toMutableMap()
        val removals = libraryPreferenceStore.loadSeriesRemovalTimes().toMutableMap()
        val booksBySyncId = uiState.libraryBooks.groupBy { it.syncId?.lowercase() }
        val acceptedSyncIds = linkedSetOf<String>()
        conflicts.forEach { result ->
            val syncId = result.bookSyncId.lowercase()
            val matchingBooks = booksBySyncId[syncId].orEmpty()
            val newestLocalRevision = matchingBooks.maxOfOrNull { book ->
                maxOf(assignments[book.id]?.modifiedAt ?: 0L, removals[book.id] ?: 0L)
            } ?: 0L
            if (newestLocalRevision > result.modifiedAt) return@forEach
            acceptedSyncIds += syncId
            matchingBooks.forEach { book ->
                if (result.name == null) {
                    assignments.remove(book.id)
                    removals[book.id] = result.modifiedAt
                } else {
                    assignments[book.id] = CustomSeriesAssignment(
                        name = result.name,
                        order = result.order.coerceAtLeast(0),
                        modifiedAt = result.modifiedAt,
                    )
                    removals.remove(book.id)
                }
            }
        }
        libraryPreferenceStore.saveCustomSeries(assignments)
        libraryPreferenceStore.saveSeriesRemovalTimes(removals)
        if (acceptedSyncIds.isNotEmpty()) {
            uiState = uiState.copy(
                customSeriesByBookId = assignments,
                message = strings[R.string.msg_applied_desktop_state_to_series_changed_on_another_dev, (acceptedSyncIds.size)],
                isError = false,
            )
        }
        return acceptedSyncIds
    }

    private companion object {
        const val PAIRING_CODE_LENGTH = 6
        const val GALLERY_DETAIL_CONCURRENCY = 4
        const val SUGGESTION_DEBOUNCE_MS = 200L
        const val CONNECTION_MONITOR_INTERVAL_MS = 2_000L
        const val MAX_FILTER_SUGGESTIONS = 12
        const val MAX_CACHED_GALLERIES = 200
        const val MAX_KNOWN_FILTER_TOKENS = 20_000
        const val MAX_VIEWED_GALLERIES = 50_000
        const val MAX_SERIES_SYNC_ASSIGNMENTS = 25
        const val MAX_BOOK_STATE_SYNC_MUTATIONS = 50
        const val SAFE_ARCHIVE_IMPORT_API_VERSION = 2
        const val MAX_SERIES_NAME_LENGTH = 500
        const val LIBRARY_SYNC_DEBOUNCE_MS = 15_000L
        const val LIBRARY_SYNC_SCAN_RETRY_MS = 1_000L
        const val IMPORTANT_LIBRARY_REFRESH_RETRY_MS = 250L
        const val SERIES_SYNC_MAX_RETRIES = 3
        const val SERIES_SYNC_RETRY_BASE_DELAY_MS = 500L
        const val FILE_UPLOAD_MAX_RETRIES = 3
        const val FILE_UPLOAD_RETRY_BASE_DELAY_MS = 750L
        const val BOOK_STATE_SYNC_MAX_RETRIES = 3
        const val BOOK_STATE_SYNC_RETRY_BASE_DELAY_MS = 500L
        const val LAST_UPDATE_CHECK_KEY = "last_update_check"
        const val SKIPPED_UPDATE_KEY = "skipped_update"
        val FILTER_TYPES = listOf(
            "artist", "group", "type", "language", "series", "character", "male", "female", "tag",
        )
        val UNTYPED_SUGGESTION_TYPES = listOf("tag", "language", "type")
    }
}

private fun BookStateSyncResult.isCompleted(): Boolean =
    state != null &&
        when (status) {
            "applied", "duplicate", "conflict" -> true
            else -> false
        }

private fun LibraryBook.usesExistingArchive(): Boolean = pages.firstOrNull()?.archiveUri != null

private fun combinedSyncError(current: String?, next: String): String =
    listOfNotNull(current, next).distinct().joinToString("\n")

internal fun distinctPendingSyncCount(
    pendingSeriesIds: Set<String>,
    pendingBookStateIds: Set<String>,
    books: List<LibraryBook> = emptyList(),
): Int {
    val syncIdByBookId = books.mapNotNull { book ->
        book.syncId?.lowercase()?.let { book.id to it }
    }.toMap()
    return (pendingSeriesIds + pendingBookStateIds)
        .mapTo(hashSetOf()) { id -> syncIdByBookId[id] ?: id }
        .size
}

internal fun latestSyncStateVersions(books: List<LibraryBook>): Map<String, Long> =
    books.asSequence()
        .mapNotNull { book ->
            book.syncId?.lowercase()?.let { syncId -> syncId to book.syncStateVersion }
        }
        .groupingBy(Pair<String, Long>::first)
        .fold(0L) { highest, (_, version) -> maxOf(highest, version) }

internal fun stableBookStateMutationId(
    profileId: String,
    syncId: String,
    modifiedAt: Long,
): String = UUID.nameUUIDFromBytes(
    "$profileId\u0000${syncId.lowercase()}\u0000$modifiedAt".toByteArray(Charsets.UTF_8),
).toString()

internal fun shouldAcceptBookStateResponse(
    bookId: String,
    localModifiedAt: Long,
    isPending: Boolean,
    expectedModifiedAtByBookId: Map<String, Long>?,
): Boolean {
    if (expectedModifiedAtByBookId != null) {
        return expectedModifiedAtByBookId[bookId] == localModifiedAt
    }
    return !isPending
}

internal fun sortDownloadQueueNewest(items: List<DownloadQueueItem>): List<DownloadQueueItem> =
    items.sortedWith(
        compareByDescending<DownloadQueueItem> { it.addedAt }
            .thenByDescending(DownloadQueueItem::id),
    )

internal fun preferredLibraryBookForGalleryId(
    books: List<LibraryBook>,
    galleryId: Long,
): LibraryBook? = books.asSequence()
    .filter { it.metadata.hitomiId?.toLongOrNull() == galleryId }
    .sortedBy(LibraryBook::isCloud)
    .firstOrNull()

internal fun preferredLibraryBooksByGalleryId(books: List<LibraryBook>, hiddenIds: Set<String>): Map<Long, LibraryBook> =
    LinkedHashMap<Long, LibraryBook>().apply {
        for (book in books) {
            if (book.id in hiddenIds) continue
            val id = book.metadata.hitomiId?.toLongOrNull() ?: continue
            val previous = get(id)
            if (previous == null || (previous.isCloud && !book.isCloud)) put(id, book)
        }
    }

internal fun remapLibraryIds(
    ids: Set<String>,
    aliases: Map<String, String>,
): Set<String> = ids.mapTo(linkedSetOf()) { aliases[it] ?: it }

internal fun <T> remapLibraryIdKeys(
    values: Map<String, T>,
    aliases: Map<String, String>,
): Map<String, T> {
    if (aliases.isEmpty()) return values
    return LinkedHashMap<String, T>(values.size).apply {
        values.forEach { (id, value) -> if (id !in aliases) put(id, value) }
        values.forEach { (id, value) -> aliases[id]?.let { putIfAbsent(it, value) } }
    }
}

internal fun legacyLocalLibraryIdAliases(
    books: List<LibraryBook>,
): Map<String, String> = books.asSequence()
    .filterNot(LibraryBook::isCloud)
    .filter { it.folderUri.isNotBlank() && it.folderUri != it.id }
    .associate { it.folderUri to it.id }

internal fun LibraryBook.hasResolvedReaderPages(): Boolean =
    pages.isNotEmpty() && pages.all { it.uri.isNotBlank() }

internal fun LibraryBook.withPreservedReaderPages(previous: LibraryBook?): LibraryBook {
    if (hasResolvedReaderPages() || previous?.hasResolvedReaderPages() != true) return this
    val sameSource = if (isCloud) {
        previous.isCloud && remoteBookId == previous.remoteBookId
    } else {
        !previous.isCloud && folderUri == previous.folderUri
    }
    val unchanged = sameSource &&
        pages.size == previous.pages.size &&
        modifiedAt == previous.modifiedAt &&
        contentVersion == previous.contentVersion
    if (!unchanged) return this
    return copy(
        pages = previous.pages,
        coverUriOverride = coverUri.takeIf(String::isNotBlank),
    )
}

internal fun pendingSeriesSyncUpdates(
    updates: List<SeriesSyncUpdate>,
    remoteBooks: List<LibraryBook>,
): List<SeriesSyncUpdate> {
    val remoteBySyncId = remoteBooks.mapNotNull { book ->
        book.syncId?.lowercase()?.let { it to book }
    }.toMap()
    return updates.filterNot { update ->
        val remote = remoteBySyncId[update.bookSyncId.lowercase()] ?: return@filterNot false
        remote.hasSyncedSeriesState &&
            remote.syncedSeries.sameSeriesStateAs(update.assignment)
    }
}

private fun CustomSeriesAssignment?.sameSeriesStateAs(
    expected: CustomSeriesAssignment?,
): Boolean = when {
    this == null || expected == null -> this == null && expected == null
    else -> name == expected.name && order == expected.order
}

private fun MutableSet<String>.setMembership(value: String, included: Boolean) {
    if (included) add(value) else remove(value)
}

internal data class SeriesMergeResult(
    val assignments: Map<String, CustomSeriesAssignment>,
    val removalTimes: Map<String, Long>,
    val localWinnerIds: Set<String>,
)

internal fun sanitizePendingSeriesSyncIds(
    pending: Set<String>,
    knownSyncableIds: Set<String>,
    librarySnapshotComplete: Boolean,
    assignments: Map<String, CustomSeriesAssignment>,
    removalTimes: Map<String, Long>,
): Set<String> = pending.filterTo(linkedSetOf()) { id ->
    (!librarySnapshotComplete || id in knownSyncableIds) &&
        (id in assignments || id in removalTimes)
}

internal fun mergeSyncedSeries(
    local: Map<String, CustomSeriesAssignment>,
    removalTimes: Map<String, Long>,
    books: List<LibraryBook>,
    pendingIds: Set<String> = emptySet(),
): SeriesMergeResult {
    val merged = local.toMutableMap()
    val mergedRemovalTimes = removalTimes.toMutableMap()
    val localWinnerIds = linkedSetOf<String>()
    books.filter(LibraryBook::hasSyncedSeriesState).forEach { book ->
        if (book.id in pendingIds) {
            localWinnerIds += book.id
        } else {
            merged.remove(book.id)
            mergedRemovalTimes.remove(book.id)
            book.syncedSeries?.let { merged[book.id] = it }
                ?: run { mergedRemovalTimes[book.id] = book.syncedSeriesModifiedAt }
        }
    }
    return SeriesMergeResult(merged, mergedRemovalTimes, localWinnerIds)
}

private fun operationErrorMessage(error: Exception, strings: android.content.res.Resources): String {
    val causes = generateSequence(error as Throwable?) { it.cause }.toList()
    return when {
        causes.any { it is SocketTimeoutException } ->
            strings[R.string.msg_connection_timed_out_check_vpn_local_network_permissio]
        causes.any { it is ConnectException || it is NoRouteToHostException } ->
            strings[R.string.msg_cannot_connect_to_pc_check_vpn_and_network_settings_an]
        causes.any { it is UnknownHostException } ->
            strings[R.string.msg_pc_address_not_found_check_the_ip_host_and_network_set]
        else -> error.message ?: strings[R.string.msg_an_unknown_error_occurred]
    }
}

internal fun reachedLastPage(page: Int, pageCount: Int): Boolean =
    pageCount > 0 && page >= pageCount - 1

private fun seriesDisplayStem(title: String): String = title
    .replace(Regex("^\\s*\\[[^]]+]\\s*"), "")
    .replace(
        Regex(
            "(?i)\\s*(?:[-_ ]*(?:vol(?:ume)?|ch(?:apter)?|episode|ep|part|권|화|장)\\.?\\s*)?#?" +
                "[0-9０-９]+(?:\\.[0-9]+)?\\s*$",
        ),
        "",
    )
    .trim(' ', '-', '_', '.', ':')

private fun seriesComparisonStem(title: String): String = seriesDisplayStem(title)
    .replace(Regex("(?i)\\b(?:vol(?:ume)?|ch(?:apter)?|episode|ep|part)\\.?\\b"), " ")
    .replace(Regex("(?:전|중|후|상|하)편|완결|특별편|외전"), " ")
    .replace(Regex("[0-9０-９]+"), " ")
    .trim()

private fun normalizeSeriesText(value: String): String = value.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), "")

private fun trailingSeriesNumber(value: String): Int? = Regex("([0-9]+)\\D*$")
    .find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()

private fun diceSimilarity(left: String, right: String): Float {
    if (left == right) return 1f
    if (left.length < 2 || right.length < 2) return 0f
    val leftPairs = left.windowed(2).groupingBy { it }.eachCount().toMutableMap()
    var overlap = 0
    right.windowed(2).forEach { pair ->
        val count = leftPairs[pair] ?: 0
        if (count > 0) {
            overlap++
            leftPairs[pair] = count - 1
        }
    }
    return (2f * overlap) / (left.length - 1 + right.length - 1)
}
