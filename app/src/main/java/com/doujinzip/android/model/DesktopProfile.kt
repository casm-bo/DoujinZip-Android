package com.doujinzip.android.model

data class DesktopProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val token: String,
    val deviceId: String = id,
    val libraryId: String? = null,
    val serverId: String? = null,
)

data class CompanionStatus(    val service: String,
    val version: Int,
    val pairingAvailable: Boolean,
    val syncGeneration: Long = 0L,
    val downloadRequestIds: Boolean = false,
    val serverId: String? = null,
    val serverName: String? = null,
)

data class PairingResult(
    val deviceId: String,
    val token: String,
    val libraryId: String? = null,
)

data class SearchResult(
    val galleryIds: List<Long>,
    val hasNextPage: Boolean,
    val totalCount: Int? = null,
    val maxPage: Int? = totalCount?.let { ((it.toLong() + 29) / 30).toInt() },
)

internal fun combineLanguageSearchResults(results: List<SearchResult>): SearchResult = SearchResult(
    galleryIds = results.flatMap { it.galleryIds }.distinct(),
    hasNextPage = results.any { it.hasNextPage },
    // Callers partition the same conditions by distinct languages.
    totalCount = results.takeIf { it.all { result -> result.totalCount != null } }?.sumOf { it.totalCount!! },
    maxPage = results.takeIf { it.all { result -> result.maxPage != null } }?.maxOfOrNull { it.maxPage!! },
)

internal fun isSearchPageInRange(page: Int?, maxPage: Int?): Boolean =
    page != null && page >= 1 && (maxPage == null || page <= maxPage)

data class GalleryTag(
    val type: String,
    val name: String,
    val isNegative: Boolean = false,
)

data class GallerySummary(
    val id: Long,
    val title: String,
    val artists: List<String>,
    val series: List<String>,
    val galleryType: String?,
    val tags: List<GalleryTag>,
    val thumbnailUrl: String?,
    val pageCount: Int,
    val language: String?,
    val publishedDate: String? = null,
    val loadError: String? = null,
    val groups: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val webtoon: Boolean? = null,
    val sourceThumbnailUrl: String? = thumbnailUrl,
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED,
    UNKNOWN,
}

data class DownloadQueueItem(
    val id: Long,
    val galleryId: Long,
    val galleryTitle: String,
    val galleryArtist: String?,
    val thumbnailUrl: String?,
    val status: DownloadStatus,
    val progress: Int,
    val totalFiles: Int,
    val downloadedFiles: Int,
    val downloadSpeed: Long,
    val errorMessage: String?,
    val addedAt: String,
)
