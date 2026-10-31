package com.doujinzip.android.model

/** Reserved logical sync identity: series edits never replace an episode's presentation. */
fun seriesPresentationKey(name: String): String {
    require(name.isNotBlank())
    val hash = java.security.MessageDigest.getInstance("SHA-256").digest(name.toByteArray(Charsets.UTF_8))
    val hex = "0123456789abcdef"
    return buildString {
        append("sync:series_")
        hash.forEach { append(hex[(it.toInt() ushr 4) and 15]); append(hex[it.toInt() and 15]) }
    }
}

fun seriesCoverPresentationKey(name: String): String = seriesPresentationKey(name).replace("sync:series_", "sync:series_cover_")

data class GalleryPresentation(
    val key: String,
    val title: String,
    val metadata: LibraryMetadata,
    val coverHash: String? = null,
    val webtoon: Boolean? = null,
    val version: Long = 0,
    val revision: Long = 0,
) {
    init {
        require(key.matches(Regex("(?:hitomi:[1-9][0-9]*|sync:[a-zA-Z0-9_-]+)")) && key.length <= 160)
        require(title.isNotBlank() && title.length <= 500)
        require(version >= 0 && revision >= 0)
        require(listOf(metadata.hitomiId, metadata.galleryType, metadata.language, metadata.publishedDate).all { it == null || it.length <= 200 })
        require(coverHash == null || coverHash.matches(Regex("[a-f0-9]{64}")))
        require(listOf(metadata.artists, metadata.groups, metadata.series, metadata.characters, metadata.tags).all { list ->
            list.size <= 300 && list.all { it.isNotBlank() && it.length <= 200 }
        })
    }
}

internal fun GalleryPresentation.hasSameContent(other: GalleryPresentation): Boolean =
    copy(version = 0, revision = 0) == other.copy(version = 0, revision = 0)

fun LibraryBook.presentationKey(): String = metadata.hitomiId?.toLongOrNull()?.takeIf { it > 0 }?.let { "hitomi:$it" }
    ?: "sync:${requireNotNull(syncId) { "Book has no stable identity" }.lowercase()}"

fun LibraryBook.presentation() = GalleryPresentation(presentationKey(), title, metadata, webtoon = webtoon)

fun GallerySummary.presentation() = GalleryPresentation("hitomi:$id", title,
    LibraryMetadata(hitomiId = id.toString(), artists = artists, groups = groups, characters = characters, series = series, galleryType = galleryType,
        tags = tags.map { if (it.type in listOf("female", "male")) "${it.type}:${it.name}" else it.name }, language = language, publishedDate = publishedDate), webtoon = webtoon)

fun LibraryBook.withPresentation(value: GalleryPresentation?, coverUri: String? = null): LibraryBook = if (value == null) this else
    copy(title = value.title, originalTitle = value.title, webtoon = value.webtoon, metadata = value.metadata.copy(publishedDate = value.metadata.publishedDate ?: metadata.publishedDate), coverUriOverride = coverUri ?: coverUriOverride)

fun GallerySummary.withPresentation(value: GalleryPresentation?, coverUri: String? = null): GallerySummary = if (value == null) this else
    copy(title = value.title, webtoon = value.webtoon, artists = value.metadata.artists, groups = value.metadata.groups, characters = value.metadata.characters, series = value.metadata.series,
        galleryType = value.metadata.galleryType, language = value.metadata.language, publishedDate = value.metadata.publishedDate ?: publishedDate,
        tags = value.metadata.tags.map { tag ->
            val namespace = tag.substringBefore(':', "")
            if (namespace in listOf("female", "male")) GalleryTag(namespace, tag.substringAfter(':')) else GalleryTag("tag", tag)
        }, thumbnailUrl = coverUri ?: thumbnailUrl)
