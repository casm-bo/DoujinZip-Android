package com.doujinzip.android.data

import java.io.File
import java.net.URI
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

private const val ARCHIVE_INDEX_VERSION = 2

internal data class ArchiveIndex(val pages: List<String>, val info: ParsedInfoTxt, val cover: String)

internal fun archiveFingerprint(source: String, modifiedAt: Long, size: Long): String =
    MessageDigest.getInstance("SHA-256").digest("$source\n$modifiedAt\n$size".toByteArray())
        .joinToString("") { "%02x".format(it) }

/** Missing/unreliable provider timestamps are deliberately never cached. */
internal class ArchiveIndexCache(private val directory: File) {
    fun read(source: String, modifiedAt: Long, size: Long): ArchiveIndex? {
        if (modifiedAt <= 0 || size <= 0) return null
        return runCatching {
            val file = File(directory, "${archiveFingerprint(source, modifiedAt, size)}.json")
            if (!file.isFile || file.length() > 4L * 1024 * 1024) return null
            val json = JSONObject(file.readText())
            require(json.getInt("version") == ARCHIVE_INDEX_VERSION)
            val cover = json.getString("cover")
            if (!File(URI(cover)).let { it.isFile && it.length() > 0 }) return null
            val pages = json.getJSONArray("pages")
            val result = ArchiveIndex(List(pages.length()) { pages.getString(it) },
                ParsedInfoTxt(json.optString("title").takeIf(String::isNotEmpty), json.optString("uuid").takeIf(String::isNotEmpty),
                    metadataFromJson(json.getJSONObject("metadata"))), cover)
            file.setLastModified(System.currentTimeMillis())
            result
        }.getOrNull()
    }

    fun write(source: String, modifiedAt: Long, size: Long, index: ArchiveIndex) {
        if (modifiedAt <= 0 || size <= 0) return
        runCatching {
            directory.mkdirs()
            val destination = File(directory, "${archiveFingerprint(source, modifiedAt, size)}.json")
            val temporary = File(directory, "${destination.name}.tmp")
            try {
                temporary.writeText(JSONObject().put("version", ARCHIVE_INDEX_VERSION).put("pages", JSONArray(index.pages)).put("cover", index.cover)
                    .put("title", index.info.title.orEmpty()).put("uuid", index.info.uuid.orEmpty()).put("metadata", index.info.metadata.toJson()).toString())
                check(temporary.renameTo(destination))
            } finally { temporary.delete() }
        }
    }

    fun trim() {
        val files = directory.listFiles()?.filter { it.extension == "json" }?.sortedBy { it.lastModified() }.orEmpty()
        var bytes = files.sumOf { it.length() }
        for (file in files) {
            if (bytes <= 32L * 1024 * 1024) break
            val length = file.length()
            if (file.delete()) bytes -= length
        }
    }
}
