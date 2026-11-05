package com.doujinzip.android.network

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class AvailableLanguage(val code: String, val label: String)

internal fun parseAvailableLanguages(script: String): List<AvailableLanguage> {
    val priority = listOf("korean", "japanese", "english", "chinese")
    fun objectValue(name: String): JSONObject {
        val value = Regex("""\b$name\s*=\s*(\{[^}]*\})""").find(script)?.groupValues?.get(1)
            ?: error("언어 목록 응답 형식이 올바르지 않습니다.")
        return JSONObject(value)
    }
    val languages = objectValue("bitnumber_language")
    val labels = objectValue("language_localname")
    return languages.keys().asSequence().map { languages.getString(it) }.map { raw ->
        val code = raw.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), "_")
        require(code.matches(Regex("[a-z]+(?:_[a-z]+)*")) && code != "all") { "잘못된 언어 코드입니다." }
        AvailableLanguage(code, labels.optString(raw).ifBlank {
            code.replace('_', ' ').replaceFirstChar { it.uppercase() }
        })
    }.distinctBy { it.code }.sortedWith(compareBy<AvailableLanguage> {
        priority.indexOf(it.code).takeIf { index -> index >= 0 } ?: priority.size
    }.thenBy { it.label.lowercase(Locale.ROOT) }).toList()
        .also { require(it.isNotEmpty()) { "서버 언어 목록이 비어 있습니다." } }
}

data class FilterSuggestion(
    val token: String,
    val displayName: String,
)

class HitomiSuggestionClient {
    private val languageMutex = Mutex()
    private var availableLanguages: List<AvailableLanguage>? = null

    suspend fun getAvailableLanguages(): List<AvailableLanguage> = withContext(Dispatchers.IO) {
        languageMutex.withLock {
            availableLanguages ?: run {
                val connection = URL("https://ltn.gold-usergeneratedcontent.net/language_support.js")
                    .openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = CONNECT_TIMEOUT_MS
                    connection.readTimeout = READ_TIMEOUT_MS
                    connection.setRequestProperty("Referer", "https://hitomi.la/")
                    check(connection.responseCode == HttpURLConnection.HTTP_OK) { "언어 목록을 가져오지 못했습니다 (${connection.responseCode})." }
                    val script = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val buffer = CharArray(65_537)
                        var size = 0
                        while (size < buffer.size) {
                            val count = reader.read(buffer, size, buffer.size - size)
                            if (count < 0) break
                            size += count
                        }
                        require(size <= 65_536) { "언어 목록 응답이 너무 큽니다." }
                        String(buffer, 0, size)
                    }
                    parseAvailableLanguages(script).also { availableLanguages = it }
                } finally {
                    connection.disconnect()
                }
            }
        }
    }

    suspend fun getSuggestions(type: String, partial: String): List<FilterSuggestion> =
        withContext(Dispatchers.IO) {
            when (type) {
                "type" -> TYPES.toSuggestions(type, partial)
                "language" -> getAvailableLanguages().map { it.code }.toSuggestions(type, partial)
                in REMOTE_TYPES -> getRemoteSuggestions(type, partial)
                else -> emptyList()
            }
        }

    private fun getRemoteSuggestions(type: String, partial: String): List<FilterSuggestion> {
        val first = partial.firstOrNull()?.lowercaseChar() ?: return emptyList()
        val bucket = if (first.isDigit()) "0-9" else first.toString()
        if (!first.isLetterOrDigit()) return emptyList()
        val listName = when (type) {
            "artist" -> "allartists"
            "series" -> "allseries"
            "character" -> "allcharacters"
            "group" -> "allgroups"
            "tag", "male", "female" -> "alltags"
            else -> return emptyList()
        }
        val connection = URL("https://gold-usergeneratedcontent.net/$listName-$bucket.html")
            .openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "text/html")
            connection.setRequestProperty("Referer", "https://hitomi.la/")
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseLinks(html, type, partial)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseLinks(html: String, type: String, partial: String): List<FilterSuggestion> {
        val normalizedPartial = normalizeValue(partial)
        return HREF_PATTERN.findAll(html)
            .mapNotNull { match ->
                val path = URLDecoder.decode(match.groupValues[1], Charsets.UTF_8.name())
                val value = when (type) {
                    "male", "female" -> path
                        .takeIf { it.startsWith("tag/$type:") }
                        ?.substringAfter("tag/$type:")
                    "tag" -> path
                        .takeIf {
                            it.startsWith("tag/") &&
                                !it.startsWith("tag/male:") &&
                                !it.startsWith("tag/female:")
                        }
                        ?.substringAfter("tag/")
                    else -> path
                        .takeIf { it.startsWith("$type/") }
                        ?.substringAfter("$type/")
                }?.removeSuffix("-all") ?: return@mapNotNull null
                val normalized = normalizeValue(value)
                if (!normalized.startsWith(normalizedPartial)) return@mapNotNull null
                FilterSuggestion(
                    token = "$type:$normalized",
                    displayName = value.replace('_', ' '),
                )
            }
            .distinctBy { it.token }
            .take(MAX_RESULTS)
            .toList()
    }

    private fun List<String>.toSuggestions(type: String, partial: String): List<FilterSuggestion> {
        val normalizedPartial = normalizeValue(partial)
        return asSequence()
            .filter { normalizeValue(it).startsWith(normalizedPartial) }
            .map { FilterSuggestion("$type:${normalizeValue(it)}", it) }
            .take(MAX_RESULTS)
            .toList()
    }

    private fun normalizeValue(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), "_")

    private companion object {
        val REMOTE_TYPES = setOf("artist", "series", "character", "group", "tag", "male", "female")
        val TYPES = listOf("doujinshi", "manga", "artistcg", "gamecg", "imageset", "anime")
        val HREF_PATTERN = Regex("href=[\\\"']/?([^\\\"']+)\\.html[\\\"']", RegexOption.IGNORE_CASE)
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 10_000
        const val MAX_RESULTS = 12
    }
}
