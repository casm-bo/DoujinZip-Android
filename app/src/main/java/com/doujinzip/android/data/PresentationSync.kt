package com.doujinzip.android.data

import com.doujinzip.android.model.DesktopProfile
import com.doujinzip.android.model.GalleryPresentation
import com.doujinzip.android.network.CompanionClient

/** The caller serializes sync runs. A store belongs to exactly one desktop profile. */
class PresentationSync(private val client: CompanionClient, private val store: PresentationStore, private val covers: PresentationCovers) {
    suspend fun sync(profile: DesktopProfile) {
        store.adoptDeviceEdits()
        store.reconcileIdenticalConflicts()
        // Recover a lost acknowledgement before treating our own server edit as a conflict.
        for (record in store.pending()) {
            record.value.coverHash?.let { client.transferCover(profile, it, covers.bytes(it)) }
            val result = client.savePresentation(profile, record)
            require(result.value.key == record.value.key)
            downloadCover(profile, result.value)
            when (result.status) {
                "applied", "already_applied" -> store.acknowledge(record.value.key, record.mutationId, result.value)
                "conflict" -> store.conflict(record.value.key, result.value)
                else -> error("Unknown metadata sync result")
            }
        }
        var cursor = store.cursor(profile.id)
        do {
            val page = client.getPresentations(profile, cursor)
            require(page.cursor >= cursor && (!page.hasMore || page.cursor > cursor)) { "Invalid metadata sync cursor" }
            for (value in page.values) { downloadCover(profile, value); store.receive(value) }
            cursor = page.cursor
            store.saveCursor(profile.id, cursor)
        } while (page.hasMore)
    }
    private suspend fun downloadCover(profile: DesktopProfile, value: GalleryPresentation) {
        value.coverHash?.let { hash -> if (!covers.contains(hash)) covers.put(hash, client.transferCover(profile, hash)) }
    }
}
