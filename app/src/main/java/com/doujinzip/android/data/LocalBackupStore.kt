package com.doujinzip.android.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import android.util.AtomicFile
import com.doujinzip.android.security.*
import com.doujinzip.android.model.SearchSubscription
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Portable user data only. Credentials, URI permissions and downloaded books stay on the device. */
internal class LocalBackupStore(private val context: Context) {
    private val preferenceFiles = listOf("appearance", "browser_preferences", "library_preferences", "recent_history", "subscription_options", "updates")
    private val staged = AtomicFile(File(context.filesDir, "pending-local-restore.zip"))
    private val rollback = AtomicFile(File(context.filesDir, "local-restore-rollback.json"))

    fun capture(syncedBookIds: Set<String>, profileId: String?, locale: String): BackupArchive {
        val preferences = JSONObject()
        preferenceFiles.forEach { file ->
            val values = mutableMapOf<String, Any?>().apply { putAll(context.getSharedPreferences(file, Context.MODE_PRIVATE).all.filterKeys { backupPreferenceAllowed(file, it) }) }
            if (file == "browser_preferences") {
                val browser = BrowserPreferenceStore(context)
                values["search_workspace"] = browserWorkspaceToJson(browser.loadBrowserWorkspace())
                values["gallery_workspace"] = browserWorkspaceToJson(browser.loadGalleryWorkspace())
                // Search results are disposable; stale session IDs must not survive a move.
                listOf("search_workspace", "gallery_workspace").forEach { key ->
                    val workspace = browserWorkspaceFromJson(values[key] as String).invalidateBackupSessions()
                    values[key] = browserWorkspaceToJson(workspace)
                }
            }
            if (file == "library_preferences") {
                listOf("favorite_book_ids", "read_book_ids", "hidden_book_ids").forEach { key ->
                    (values[key] as? Set<*>)?.let { values[key] = it.filterNot { id -> id in syncedBookIds }.toSet() }
                }
                listOf("book_progress", "custom_book_series", "series_removal_times", "custom_book_titles").forEach { key ->
                    (values[key] as? String)?.let { raw ->
                        val data = JSONObject(raw); syncedBookIds.forEach(data::remove); values[key] = data.toString()
                    }
                }
                values.keys.filter { it.startsWith("book_page:") && it.removePrefix("book_page:") in syncedBookIds }.toList().forEach(values::remove)
                if (values["active_library_book_id"] in syncedBookIds) values.remove("active_library_book_id")
                val remainingSeries = LibraryPreferenceStore(context).loadCustomSeries().filterKeys { it !in syncedBookIds }.values.map { it.name }.toSet()
                val syncedSeries = LibraryPreferenceStore(context).loadCustomSeries().filterKeys { it in syncedBookIds }.values.map { it.name }.toSet()
                (values["favorite_series_names"] as? Set<*>)?.let { values["favorite_series_names"] = it.filterNot { name -> name in syncedSeries && name !in remainingSeries }.toSet() }
            }
            if (file == "recent_history") {
                // Thumbnails may refer to private local URIs or the previous PC.
                values.keys.filter { it == "entries" || it.startsWith("entry:") }.forEach { key ->
                    val entries = JSONArray(values[key] as String)
                    entries.objects().forEach { it.remove("thumbnail") }
                    values[key] = entries.toString()
                }
            }
            preferences.put(file, encodeBackupPreferences(values))
        }
        val identities = JSONArray()
        val presentations = JSONArray()
        LibraryIdentityStore(context).use { store ->
            val db = store.readableDatabase
            val locations = BrowserPreferenceStore(context).loadLibraryLocations()
            rows(db, "library_identities").objects().forEach { row ->
                if (row.getString("source_key").startsWith("backup:")) { identities.put(row); return@forEach }
                val uri = row.getString("source_uri")
                val documentId = runCatching { DocumentsContract.getDocumentId(Uri.parse(uri)) }.getOrNull() ?: return@forEach
                val root = locations.mapNotNull { runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(it.uri)) }.getOrNull() }
                    .filter { documentId == it || documentId.startsWith("$it/") }.maxByOrNull { it.length } ?: return@forEach
                val kind = row.getString("source_key").substringBefore(':')
                val relative = documentId.removePrefix(root).trim('/').ifEmpty { "." }
                row.put("source_key", "backup:${row.getString("sync_id")}:$kind:$relative")
                    .put("source_uri", "$kind:$relative")
                identities.put(row)
            }
            rows(db, "gallery_presentations").objects().forEach { row ->
                require(row.isNull("conflict")) { "Resolve metadata conflicts before backup" }
                val value = presentationFromJson(JSONObject(row.getString("payload")))
                val belongsToSyncedPc = profileId != null && row.getString("resource_key").startsWith("profile/$profileId/")
                if (!belongsToSyncedPc || row.getInt("dirty") != 0 || value.version == 0L) {
                    presentations.put(value.copy(version = 0, revision = 0).toJson())
                }
            }
        }
        val subscriptions = JSONArray()
        val notices = JSONObject()
        SubscriptionStore(context).use { store ->
            rows(store.readableDatabase, "subscriptions").objects().forEach { row ->
                require(row.isNull("conflict")) { "Resolve subscription conflicts before backup" }
                if (profileId == null || row.getString("scope") != profileId || !row.isNull("mutation")) {
                    subscriptions.put(SearchSubscription.fromJson(JSONObject(row.getString("payload"))).copy(version = 0).json())
                }
            }
            // Notification matches/read receipts are local UI data, not a server subscription definition.
            for (table in listOf("notices", "matches", "reads")) {
                notices.put(table, JSONArray(rows(store.readableDatabase, table).objects().map { it.put("scope", "") }))
            }
        }
        val covers = PresentationCovers(context)
        var imageBytes = 0L
        val images = presentations.objects().mapNotNull { it.optString("coverHash").takeIf { hash -> hash.matches(Regex("[a-f0-9]{64}")) } }
            .distinct().associateWith { hash -> covers.bytes(hash).also { imageBytes += it.size; require(imageBytes <= BACKUP_LIMIT) } }
        return BackupArchive(JSONObject().put("format", "doujin-local-backup").put("version", 1)
            .put("createdAt", System.currentTimeMillis()).put("serverDataExcluded", profileId != null)
            .put("lastSyncAt", BrowserPreferenceStore(context).loadLastDesktopSync()).put("locale", locale)
            .put("preferences", preferences).put("identities", identities).put("presentations", presentations)
            .put("subscriptions", subscriptions).put("notices", notices), images).also(::validate)
    }

    fun validate(archive: BackupArchive) {
        val data = archive.data
        require(data.getString("format") == "doujin-local-backup" && data.getInt("version") == 1)
        val locale = data.getString("locale")
        require(locale.isEmpty() || locale.split(',').all { it.substringBefore('-') in setOf("en", "ko", "ja", "zh") && it.matches(Regex("[a-z]{2}(?:-[A-Za-z0-9]{2,8})*")) })
        data.getBoolean("serverDataExcluded")
        val prefs = data.getJSONObject("preferences")
        require(prefs.keys().asSequence().toSet() == preferenceFiles.toSet())
        preferenceFiles.forEach { file ->
            val values = decodeBackupPreferences(prefs.getJSONObject(file))
            require(values.keys.all { backupPreferenceAllowed(file, it) })
            values.forEach { (key, value) -> require(backupPreferenceTypeValid(key, value)) }
            if (file == "browser_preferences") listOf("search_workspace", "gallery_workspace").forEach { key ->
                values[key]?.let { validateWorkspace(JSONObject(it as String)) }
            }
        }
        LibraryIdentityStore(context).use { store ->
            validateRows(store.readableDatabase, "library_identities", data.getJSONArray("identities"))
            data.getJSONArray("identities").objects().forEach {
                require(it.getString("source_key").startsWith("backup:"))
                require(it.getString("source_uri").startsWith("folder:") || it.getString("source_uri").startsWith("archive:"))
                require(it.getString("sync_id").matches(Regex("[a-zA-Z0-9_-]{1,160}")))
                metadataFromJson(JSONObject(it.getString("metadata_json")))
            }
        }
        val keys = mutableSetOf<String>()
        data.getJSONArray("presentations").objects().forEach { item ->
            val value = presentationFromJson(item)
            require(keys.add(value.key)) { "Conflicting local metadata from multiple PCs" }
            value.coverHash?.let { require(it in archive.covers) }
        }
        val conditions = mutableSetOf<String>()
        data.getJSONArray("subscriptions").objects().forEach {
            val value = SearchSubscription.fromJson(it)
            require(value.deleted || conditions.add(value.conditionKey)) { "Duplicate local subscription" }
        }
        SubscriptionStore(context).use { store -> for (table in listOf("notices", "matches", "reads")) {
            validateRows(store.readableDatabase, table, data.getJSONObject("notices").getJSONArray(table))
            data.getJSONObject("notices").getJSONArray(table).objects().forEach { require(it.getString("scope").isEmpty()) }
        } }
        val covers = PresentationCovers(context)
        archive.covers.forEach { (hash, bytes) -> covers.validate(hash, bytes) }
    }

    fun stage(archive: BackupArchive) {
        validate(archive)
        val output = staged.startWrite()
        try {
            // AtomicFile owns the descriptor; ZipOutputStream must not close it before finishWrite.
            BackupFormat.write(archive, object : java.io.FilterOutputStream(output) { override fun close() { flush() } })
            staged.finishWrite(output)
        } catch (error: Throwable) { staged.failWrite(output); throw error }
    }

    /** Runs before activities/workers use stores. A crash during restore rolls back on next launch. */
    fun applyPending() {
        if (rollback.baseFile.exists() || File(rollback.baseFile.path + ".bak").exists()) {
            restoreJournal(JSONObject(rollback.openRead().bufferedReader().use { it.readText() }))
            rollback.delete(); staged.delete()
            runtime().edit().putString("result", "failed").commit()
            return
        }
        if (!staged.baseFile.exists() && !File(staged.baseFile.path + ".bak").exists()) return
        try {
            val archive = staged.openRead().use(BackupFormat::read); validate(archive)
            val journal = journal()
            val output = rollback.startWrite()
            try { output.write(journal.toString().toByteArray(Charsets.UTF_8)); rollback.finishWrite(output) }
            catch (error: Throwable) { rollback.failWrite(output); throw error }
            apply(archive)
            // Removing the journal is the commit point. Reapplying a remaining stage is idempotent.
            rollback.delete(); staged.delete()
            check(runtime().edit().putString("result", "success").putString("locale", archive.data.getString("locale"))
                .putBoolean("serverDataExcluded", archive.data.getBoolean("serverDataExcluded")).commit())
        } catch (error: Exception) {
            if (rollback.baseFile.exists()) restoreJournal(JSONObject(rollback.openRead().bufferedReader().use { it.readText() }))
            rollback.delete(); staged.delete()
            check(runtime().edit().putString("result", "failed").commit())
        }
    }
    private fun runtime() = context.getSharedPreferences("backup_runtime", Context.MODE_PRIVATE)

    private fun apply(archive: BackupArchive) {
        val data = archive.data
        archive.covers.forEach { (hash, bytes) -> PresentationCovers(context).put(hash, bytes) }
        LibraryIdentityStore(context).use { store ->
            val db = store.writableDatabase
            db.beginTransaction()
            try {
                insertRows(db, "library_identities", data.getJSONArray("identities"))
                val presentations = PresentationStore(store)
                data.getJSONArray("presentations").objects().forEach { presentations.saveLocal(presentationFromJson(it)) }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        SubscriptionStore(context).use { store ->
            val db = store.writableDatabase; db.beginTransaction()
            try {
                data.getJSONArray("subscriptions").objects().forEach { item ->
                    val value = SearchSubscription.fromJson(item)
                    if (store.records("").none { !it.value.deleted && it.value.conditionKey == value.conditionKey }) store.save("", value)
                }
                for (table in listOf("notices", "matches", "reads")) insertRows(db, table, data.getJSONObject("notices").getJSONArray(table))
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        preferenceFiles.forEach { file -> replacePreferences(file, decodeBackupPreferences(data.getJSONObject("preferences").getJSONObject(file)), onlyPortable = true) }
        // Restored local edits must be eligible for upload after a future pairing.
        val library = LibraryPreferenceStore(context)
        val localIds = library.loadFavoriteIds() + library.loadReadIds() + library.loadHiddenIds() + library.loadProgress().keys + library.loadCustomTitles().keys
        library.savePendingBookStateSyncIds(library.loadPendingBookStateSyncIds() + localIds)
        library.savePendingSeriesSyncIds(library.loadPendingSeriesSyncIds() + library.loadCustomSeries().keys + library.loadSeriesRemovalTimes().keys)
    }

    private fun journal(): JSONObject = JSONObject().apply {
        put("preferences", JSONObject().apply { preferenceFiles.forEach { file -> put(file, encodeBackupPreferences(context.getSharedPreferences(file, Context.MODE_PRIVATE).all)) } })
        LibraryIdentityStore(context).use { put("library", dump(it.readableDatabase, listOf("library_identities", "gallery_presentations", "presentation_sync_cursor"))) }
        SubscriptionStore(context).use { put("subscriptions", dump(it.readableDatabase, listOf("subscriptions", "notices", "matches", "reads", "state"))) }
    }
    private fun restoreJournal(data: JSONObject) {
        LibraryIdentityStore(context).use { restoreTables(it.writableDatabase, data.getJSONObject("library")) }
        SubscriptionStore(context).use { restoreTables(it.writableDatabase, data.getJSONObject("subscriptions")) }
        preferenceFiles.forEach { file -> replacePreferences(file, decodeBackupPreferences(data.getJSONObject("preferences").getJSONObject(file)), onlyPortable = false) }
    }
    private fun replacePreferences(file: String, values: Map<String, Any>, onlyPortable: Boolean) {
        val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (onlyPortable) prefs.all.keys.filter { backupPreferenceAllowed(file, it) }.forEach(editor::remove) else editor.clear()
        values.forEach { (key, value) -> when (value) {
            is String -> editor.putString(key, value); is Int -> editor.putInt(key, value); is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value); is Boolean -> editor.putBoolean(key, value)
            is Set<*> -> editor.putStringSet(key, value.map { it as String }.toSet())
        } }
        check(editor.commit()) { "Could not restore preferences" }
    }
    private fun dump(db: SQLiteDatabase, tables: List<String>) = JSONObject().apply { tables.forEach { put(it, rows(db, it)) } }
    private fun restoreTables(db: SQLiteDatabase, data: JSONObject) {
        db.beginTransaction()
        try { data.keys().forEach { table -> db.delete(table, null, null); insertRows(db, table, data.getJSONArray(table)) }; db.setTransactionSuccessful() }
        finally { db.endTransaction() }
    }
    private fun rows(db: SQLiteDatabase, table: String): JSONArray = db.query(table, null, null, null, null, null, null).use { cursor ->
        JSONArray().apply { while (cursor.moveToNext()) put(JSONObject().apply { cursor.columnNames.forEachIndexed { index, name ->
            put(name, when (cursor.getType(index)) { Cursor.FIELD_TYPE_NULL -> JSONObject.NULL; Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index); Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index); else -> cursor.getString(index) })
        } }) }
    }
    private fun validateRows(db: SQLiteDatabase, table: String, rows: JSONArray) {
        val columns = db.query(table, null, "0", null, null, null, null).use { it.columnNames.toSet() }
        val schema = db.rawQuery("PRAGMA table_info($table)", null).use { cursor -> buildMap {
            while (cursor.moveToNext()) put(cursor.getString(1), cursor.getString(2) to (cursor.getInt(3) != 0 || cursor.getInt(5) != 0))
        } }
        require(rows.length() <= 100_000)
        rows.objects().forEach { row ->
            require(row.keys().asSequence().toSet() == columns)
            row.keys().forEach { key ->
                val value = row.get(key); val (type, required) = schema.getValue(key)
                require(if (value == JSONObject.NULL) !required else if (type == "INTEGER") value is Int || value is Long else value is String)
            }
        }
    }
    private fun insertRows(db: SQLiteDatabase, table: String, rows: JSONArray) {
        rows.objects().forEach { row ->
            val values = ContentValues()
            row.keys().forEach { key -> when (val value = row.get(key)) {
                JSONObject.NULL -> values.putNull(key); is Number -> values.put(key, value.toLong()); is String -> values.put(key, value)
                else -> error("Invalid database value")
            } }
            db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
        }
    }
}

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
private fun com.doujinzip.android.model.BrowserWorkspace.invalidateBackupSessions(): com.doujinzip.android.model.BrowserWorkspace {
    fun com.doujinzip.android.model.BrowserTab.clean() = copy(history = history.map { page ->
        if (page is com.doujinzip.android.model.BrowserPage.Search) page.copy(resultSessionId = null,
            resultIds = emptyList(), results = emptyList(), submittedQueries = emptyList(), hasNextPage = false) else page
    })
    return copy(tabs = tabs.map { it.clean() }, groups = groups.map { it.copy(archivedTabs = it.archivedTabs.map { tab -> tab.clean() }) })
}

private fun validateWorkspace(root: JSONObject) {
    require(root.getInt("version") == 2)
    val ids = mutableSetOf<String>()
    fun tabs(array: JSONArray) { array.objects().forEach { tab ->
        require(tab.getString("id").isNotBlank() && ids.add(tab.getString("id")))
        val history = tab.getJSONArray("history"); require(history.length() in 1..50)
        require(tab.getInt("currentIndex") in 0 until history.length())
        history.objects().forEach { page ->
            require(page.getString("type") in setOf("search", "online", "onlineReader", "library", "libraryReader", "libraryHome", "librarySeries"))
            require(page.getString("key").isNotBlank())
            when (page.getString("type")) {
                "online", "onlineReader" -> { require(page.getLong("galleryId") > 0); require(page.get("title") is String) }
                "library", "libraryReader" -> require(page.getString("bookId").isNotBlank())
                "librarySeries" -> require(page.getString("name").isNotBlank())
                "search", "libraryHome" -> require(page.get("query") is String)
            }
        }
    } }
    tabs(root.getJSONArray("tabs")); require(root.getJSONArray("tabs").length() > 0)
    val groups = mutableSetOf<String>()
    root.getJSONArray("groups").objects().forEach { require(it.getString("id").isNotBlank() && groups.add(it.getString("id"))); require(it.getInt("colorIndex") in 0..5); tabs(it.getJSONArray("archivedTabs")) }
    require(root.getString("activeTabId") in root.getJSONArray("tabs").objects().map { it.getString("id") })
}

private fun backupPreferenceTypeValid(key: String, value: Any): Boolean = when {
    key.endsWith("_width") || key == "font_size" -> value is Float
    key.endsWith("_color") -> value is Long
    key in setOf("interval", "sync_interval", "saved_sort", "library_grid_columns", "search_grid_columns") || key.startsWith("book_page:") || key.startsWith("online_page:") -> value is Int
    key in setOf("history_grid", "notifications", "hide_locked", "sync_enabled", "records_v2", "recording", "active_gallery_workspace", "hide_desktop_download_alert", "viewer_page_number", "viewer_hide_thumbnails", "viewer_keep_screen_on", "viewer_custom_tap_zones_enabled", "search_grid_view", "library_favorites_only") -> value is Boolean
    key in setOf("preferred_languages", "custom_languages", "known_filter_tokens", "viewed_gallery_ids", "search_tag_filters", "custom_blocked_tags", "favorite_book_ids", "favorite_series_names", "read_book_ids", "hidden_book_ids") -> value is Set<*>
    else -> value is String
}
