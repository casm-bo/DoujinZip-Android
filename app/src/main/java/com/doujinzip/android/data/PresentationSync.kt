package com.doujinzip.android.data

import com.doujinzip.android.model.DesktopProfile
import com.doujinzip.android.model.GalleryPresentation
import com.doujinzip.android.network.CompanionClient

/** The caller serializes sync runs. A store belongs to exactly one desktop profile. */
class PresentationSync(private val client: CompanionClient, private val store: PresentationStore, private val covers: PresentationCovers) {
    suspend fun sync(profile: DesktopProfile) {
        // Shared edits use the durable PC-confirmed request path; never upload legacy device edits here.
        var cursor = 0L
        val confirmed = linkedMapOf<String, GalleryPresentation>()
        do {
            val page = client.getPresentations(profile, cursor)
            require(page.cursor >= cursor && (!page.hasMore || page.cursor > cursor)) { "Invalid metadata sync cursor" }
            for (value in page.values) { downloadCover(profile, value); confirmed[value.key] = value }
            cursor = page.cursor
        } while (page.hasMore)
        store.replaceConfirmedSnapshot(confirmed.values.toList(), cursor)
    }
    private suspend fun downloadCover(profile: DesktopProfile, value: GalleryPresentation) {
        value.coverHash?.let { hash -> if (!covers.contains(hash)) covers.put(hash, client.transferCover(profile, hash)) }
    }
}
