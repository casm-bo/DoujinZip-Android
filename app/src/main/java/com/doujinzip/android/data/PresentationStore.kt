package com.doujinzip.android.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.doujinzip.android.model.GalleryPresentation
import com.doujinzip.android.model.seriesPresentationKey
import com.doujinzip.android.model.seriesCoverPresentationKey
import com.doujinzip.android.model.hasSameContent
import java.util.UUID
import org.json.JSONObject

data class PresentationRecord(val value: GalleryPresentation, val dirty: Boolean = false,
    val mutationId: String = UUID.randomUUID().toString(), val conflict: GalleryPresentation? = null)

/** User edits are separate from scan metadata, including explicit empty lists. */
class PresentationStore(private val identity: LibraryIdentityStore, private val profileId: String? = null) {
    private val prefix get() = profileId?.let { "profile/$it/" }.orEmpty()
    private fun storageKey(key: String) = prefix + key
    private val selection get() = if (profileId == null) "resource_key NOT LIKE 'profile/%'" else "substr(resource_key, 1, ${prefix.length}) = ?"
    private val selectionArgs get() = if (profileId == null) null else arrayOf(prefix)
    fun all(): List<PresentationRecord> = identity.readableDatabase.query("gallery_presentations", arrayOf("payload", "dirty", "mutation_id", "conflict"),
        selection, selectionArgs, null, null, null).use { cursor -> buildList {
        while (cursor.moveToNext()) add(PresentationRecord(presentationFromJson(JSONObject(cursor.getString(0))), cursor.getInt(1) != 0,
            cursor.getString(2), cursor.getString(3)?.let { presentationFromJson(JSONObject(it)) }))
    } }
    fun get(key: String): PresentationRecord? = identity.readableDatabase.query("gallery_presentations", arrayOf("payload", "dirty", "mutation_id", "conflict"),
        "resource_key = ?", arrayOf(storageKey(key)), null, null, null).use { cursor ->
        if (!cursor.moveToFirst()) null else PresentationRecord(presentationFromJson(JSONObject(cursor.getString(0))), cursor.getInt(1) != 0,
            cursor.getString(2), cursor.getString(3)?.let { presentationFromJson(JSONObject(it)) })
    }
    fun saveLocal(value: GalleryPresentation, resolveConflict: Boolean = false): PresentationRecord = transaction {
        val previous = get(value.key)
        val base = if (resolveConflict) previous?.conflict ?: previous?.value else previous?.value
        PresentationRecord(value.copy(version = base?.version ?: value.version, revision = base?.revision ?: value.revision),
            dirty = true, conflict = if (resolveConflict) null else previous?.conflict).also(::put)
    }
    fun archiveOfflineEdits(): Boolean = transaction {
        val db = identity.writableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS legacy_presentation_edits(resource_key TEXT PRIMARY KEY, payload TEXT NOT NULL)")
        val archived = all().filter { it.dirty || it.conflict != null }
        for (record in archived) {
            db.insertWithOnConflict("legacy_presentation_edits", null, ContentValues().apply {
                put("resource_key", storageKey(record.value.key))
                put("payload", JSONObject().put("value", record.value.toJson()).put("conflict", record.conflict?.toJson()).toString())
            }, SQLiteDatabase.CONFLICT_IGNORE)
            db.delete("gallery_presentations", "resource_key = ?", arrayOf(storageKey(record.value.key)))
        }
        if (archived.isNotEmpty()) profileId?.let { saveCursor(it, 0L) }
        archived.isNotEmpty()
    }
    fun pending(): List<PresentationRecord> = all().filter { it.dirty && it.conflict == null }

    fun reconcileIdenticalConflicts() = transaction {
        all().forEach { record -> record.conflict?.let { remote ->
            if (remote.version >= record.value.version && record.value.hasSameContent(remote)) put(PresentationRecord(remote))
        } }
    }

    fun copySeriesOverride(oldName: String, newName: String) = transaction {
        if (oldName == newName) return@transaction
        listOf(seriesPresentationKey(oldName) to seriesPresentationKey(newName),
            seriesCoverPresentationKey(oldName) to seriesCoverPresentationKey(newName)).forEach { (source, destination) ->
            val previous = get(source) ?: return@forEach
            if (get(destination) == null) put(PresentationRecord(previous.value.copy(key = destination, title = newName, version = 0, revision = 0), dirty = true))
            // Keep the old name's snapshot, but do not queue an obsolete rename source.
            if (previous.conflict == null) put(previous.copy(dirty = false))
        }
    }

    fun adoptDeviceEdits() = transaction {
        if (profileId == null) return@transaction
        PresentationStore(identity).all().forEach { record ->
            if (get(record.value.key) == null) {
                put(record.copy(value = record.value.copy(version = 0, revision = 0), dirty = true, conflict = null))
                identity.writableDatabase.delete("gallery_presentations", "resource_key = ?", arrayOf(record.value.key))
            }
        }
    }

    fun replaceConfirmedSnapshot(values: List<GalleryPresentation>, cursor: Long) = transaction {
        require(values.map { it.key }.distinct().size == values.size)
        identity.writableDatabase.delete("gallery_presentations", selection, selectionArgs)
        values.forEach { put(PresentationRecord(it)) }
        profileId?.let { saveCursor(it, cursor) }
    }

    fun receive(value: GalleryPresentation) = transaction {
        val previous = get(value.key)
        when {
            previous == null -> put(PresentationRecord(value))
            value.version <= maxOf(previous.value.version, previous.conflict?.version ?: 0L) -> Unit
            previous.value.hasSameContent(value) -> put(PresentationRecord(value))
            previous.dirty -> put(previous.copy(conflict = value))
            else -> put(PresentationRecord(value))
        }
    }

    /** A late acknowledgement must not erase a newer edit made while the request was in flight. */
    fun acknowledge(key: String, sentMutationId: String, remote: GalleryPresentation) = transaction {
        require(key == remote.key)
        val previous = get(key) ?: return@transaction
        if (remote.version < maxOf(previous.value.version, previous.conflict?.version ?: 0L)) return@transaction
        if (previous.mutationId == sentMutationId) put(PresentationRecord(remote))
        else if (remote.version >= previous.value.version) put(previous.copy(value = previous.value.copy(version = remote.version, revision = remote.revision)))
    }
    fun conflict(key: String, remote: GalleryPresentation) = transaction {
        require(key == remote.key)
        get(key)?.let {
            if (remote.version < maxOf(it.value.version, it.conflict?.version ?: 0L)) return@transaction
            put(if (it.value.hasSameContent(remote)) PresentationRecord(remote) else it.copy(conflict = remote))
        }
    }
    fun acceptRemote(key: String) = transaction { get(key)?.conflict?.let { put(PresentationRecord(it)) } }

    fun resolveConflict(expected: PresentationRecord, usePhone: Boolean) = transaction {
        val current = get(expected.value.key) ?: return@transaction
        if (current != expected || current.conflict == null) return@transaction
        if (usePhone) saveLocal(current.value, resolveConflict = true) else put(PresentationRecord(current.conflict))
    }

    fun cursor(profileId: String): Long = identity.readableDatabase.rawQuery("SELECT revision FROM presentation_sync_cursor WHERE profile_id = ?", arrayOf(profileId))
        .use { if (it.moveToFirst()) it.getLong(0) else 0L }
    fun saveCursor(profileId: String, revision: Long) {
        require(revision >= 0)
        check(identity.writableDatabase.insertWithOnConflict("presentation_sync_cursor", null, ContentValues().apply {
            put("profile_id", profileId); put("revision", revision)
        }, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
    }
    private fun put(record: PresentationRecord) {
        check(identity.writableDatabase.insertWithOnConflict("gallery_presentations", null, ContentValues().apply {
            put("resource_key", storageKey(record.value.key)); put("payload", record.value.toJson().toString())
            put("dirty", if (record.dirty) 1 else 0); put("mutation_id", record.mutationId)
            put("conflict", record.conflict?.toJson()?.toString())
        }, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
    }
    private fun <T> transaction(block: () -> T): T {
        val db = identity.writableDatabase
        db.beginTransaction()
        try { return block().also { db.setTransactionSuccessful() } } finally { db.endTransaction() }
    }
    internal companion object {
        fun createTables(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE gallery_presentations(resource_key TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL, dirty INTEGER NOT NULL, mutation_id TEXT NOT NULL, conflict TEXT)")
            db.execSQL("CREATE TABLE presentation_sync_cursor(profile_id TEXT PRIMARY KEY NOT NULL, revision INTEGER NOT NULL)")
        }
    }
}

internal fun GalleryPresentation.toJson(): JSONObject = JSONObject().put("key", key).put("title", title).put("metadata", metadata.toJson())
    .put("coverHash", coverHash ?: JSONObject.NULL).put("webtoon", webtoon ?: JSONObject.NULL).put("version", version).put("revision", revision)

internal fun presentationFromJson(json: JSONObject) = GalleryPresentation(json.getString("key"), json.getString("title"), metadataFromJson(json.getJSONObject("metadata")),
    coverHash = if (json.isNull("coverHash")) null else json.getString("coverHash"), webtoon = if (json.isNull("webtoon")) null else json.getBoolean("webtoon"),
    version = json.optLong("version"), revision = json.optLong("revision"))
