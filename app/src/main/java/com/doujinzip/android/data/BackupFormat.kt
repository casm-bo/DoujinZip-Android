package com.doujinzip.android.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal const val BACKUP_LIMIT = 64 * 1024 * 1024

internal fun backupPreferenceAllowed(file: String, key: String): Boolean = when (file) {
    "updates" -> key == "skipped_update"
    "appearance" -> key in setOf("history_grid", "style", "font_size", "normal_width", "normal_color", "series_width", "series_color", "favorite_width", "favorite_color")
    "subscription_options" -> key in setOf("interval", "notifications", "hide_locked", "sync_enabled", "sync_interval")
    "recent_history" -> key == "entries" || key == "records_v2" || key == "recording" || key.startsWith("entry:")
    "browser_preferences" -> key in setOf("search_workspace", "gallery_workspace", "search_favorites", "preferred_languages", "custom_languages", "known_filter_tokens", "viewed_gallery_ids", "theme_mode", "search_tag_filters", "custom_blocked_tags", "search_query_history", "library_query_history", "active_gallery_workspace", "main_destination", "download_destination", "hide_desktop_download_alert")
    "library_preferences" -> key in setOf("favorite_book_ids", "favorite_series_names", "read_book_ids", "book_progress", "online_gallery_progress", "active_library_book_id", "custom_book_series", "series_removal_times", "hidden_book_ids", "custom_book_titles", "reading_mode", "viewer_scale", "viewer_page_number", "viewer_hide_thumbnails", "viewer_keep_screen_on", "viewer_reading_direction", "viewer_page_turn_mode", "viewer_custom_tap_zones_enabled", "viewer_tap_zones", "library_view_mode", "library_grid_columns", "library_sort", "series_destination_sort", "series_episode_sort", "search_grid_view", "search_grid_columns", "library_favorites_only", "library_read_filter", "library_visibility_filter") || key.startsWith("book_page:") || key.startsWith("online_page:") || key.startsWith("reading_mode:")
    else -> false
}

internal fun encodeBackupPreferences(values: Map<String, *>): JSONObject = JSONObject().apply {
    values.forEach { (key, value) ->
        val type = when (value) { is String -> "string"; is Int -> "int"; is Long -> "long"; is Float -> "float"; is Boolean -> "boolean"; is Set<*> -> "set"; else -> error("Unsupported preference") }
        put(key, JSONObject().put("type", type).put("value", if (value is Set<*>) JSONArray(value.toList()) else value))
    }
}

internal fun decodeBackupPreferences(json: JSONObject): Map<String, Any> = json.keys().asSequence().associateWith { key ->
    val item = json.getJSONObject(key)
    when (item.getString("type")) {
        "string" -> item.get("value").also { require(it is String) }
        "int" -> item.getLong("value").also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()
        "long" -> item.getLong("value")
        "float" -> item.getDouble("value").toFloat().also { require(it.isFinite()) }
        "boolean" -> item.get("value").also { require(it is Boolean) }
        "set" -> item.getJSONArray("value").let { array -> (0 until array.length()).map { array.get(it).also { v -> require(v is String) } as String }.toSet() }
        else -> error("Unsupported preference type")
    }
}

internal data class BackupArchive(val data: JSONObject, val covers: Map<String, ByteArray>)
internal object BackupFormat {
    fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun streamDigest(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer); if (count < 0) break
            total += count; require(total <= BACKUP_LIMIT + 1024 * 1024)
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun write(archive: BackupArchive, output: OutputStream) {
        val data = archive.data.toString().toByteArray(Charsets.UTF_8)
        require(data.size.toLong() + archive.covers.values.sumOf { it.size.toLong() } <= BACKUP_LIMIT)
        ZipOutputStream(output).use { zip ->
            val files = linkedMapOf("backup.json" to data)
            archive.covers.forEach { (hash, bytes) -> require(hash == digest(bytes)); files["covers/$hash.image"] = bytes }
            val hashes = JSONObject()
            files.forEach { (name, bytes) -> hashes.put(name, digest(bytes)); zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
            zip.putNextEntry(ZipEntry("checksums.json")); zip.write(hashes.toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
        }
    }
    fun read(input: InputStream): BackupArchive {
        val files = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                require(name == "backup.json" || name == "checksums.json" || name.matches(Regex("covers/[a-f0-9]{64}\\.image")))
                require(name !in files && files.size < 4096 && !entry.isDirectory)
                val buffer = ByteArray(8192)
                val out = ByteArrayOutputStream()
                while (true) { val count = zip.read(buffer); if (count < 0) break; total += count; require(total <= BACKUP_LIMIT); out.write(buffer, 0, count) }
                files[name] = out.toByteArray()
            }
        }
        val hashes = JSONObject(requireNotNull(files.remove("checksums.json")).toString(Charsets.UTF_8))
        require(hashes.keys().asSequence().toSet() == files.keys)
        files.forEach { (name, bytes) -> require(hashes.getString(name) == digest(bytes)) }
        val data = JSONObject(requireNotNull(files.remove("backup.json")).toString(Charsets.UTF_8))
        require(data.getString("format") == "doujin-local-backup" && data.getInt("version") == 1)
        val covers = files.mapKeys { it.key.removePrefix("covers/").removeSuffix(".image") }
        covers.forEach { (hash, bytes) -> require(hash == digest(bytes)) }
        return BackupArchive(data, covers)
    }
}
