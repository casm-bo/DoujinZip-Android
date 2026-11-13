package com.doujinzip.android.network

import com.doujinzip.android.model.GallerySummary
import com.doujinzip.android.model.GalleryTag
import com.doujinzip.android.model.SearchResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Direct resource protocol, compatible with node-hitomi 9 (no companion server). */
class HitomiClient {
    private val searches = linkedMapOf<String, Pair<Long, List<Long>>>()

    // shortcut: sampling can miss older membership edits; rescan the full feed daily.
    suspend fun subscriptionFingerprint(queries: List<String>): String = withContext(Dispatchers.IO) {
        queries.forEach { com.doujinzip.android.model.validateSubscriptionQuery(it) }
        val terms=queries.flatMap { it.split(Regex("\\s+")) }.filter(String::isNotBlank).map { it.removePrefix("-") }.distinct().sorted()
        val digest=MessageDigest.getInstance("SHA-256")
        for(term in terms) {
            var total=0
            val sample=readResource(hitomiNozomiPath(term),0L..127L,missingIsEmpty=true,onRangeCount={total=it})
            digest.update((term+":"+total+":").toByteArray())
            digest.update(sample)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun subscriptionIds(query: String): List<Long> = withContext(Dispatchers.IO) {
        com.doujinzip.android.model.validateSubscriptionQuery(query)
        val terms = query.split(Regex("\\s+")).filter(String::isNotBlank).distinct()
        var result: MutableSet<Long>? = null
        for (term in terms.filterNot { it.startsWith('-') }) {
            coroutineContext.ensureActive()
            val ids = termIds(term)
            if (result == null) result = ids.toMutableSet() else result.retainAll(ids.toSet())
        }
        val matches = requireNotNull(result)
        for (term in terms.filter { it.startsWith('-') }) matches.removeAll(termIds(term.drop(1)).toSet())
        matches.sortedDescending()
    }

    suspend fun search(searchQuery: String, page: Int = 1): SearchResult = withContext(Dispatchers.IO) {
        require(page > 0)
        val query = searchQuery.trim().lowercase(Locale.ROOT)
        val order = parseHitomiSearchOrder(query)
        if (order.popularity != null) return@withContext searchPopular(listOf(query), page, order.popularity)
        val terms = order.terms
        val directId = terms.firstNotNullOfOrNull { it.removePrefix("id:").toLongOrNull()?.takeIf { id -> id > 0 } }
        if (directId != null) {
            val excluded = terms.filter { it.startsWith('-') }.any { directId in termIds(it.drop(1)) }
            return@withContext SearchResult(if (page == 1 && !excluded) listOf(directId) else emptyList(), false, if (excluded) 0 else 1)
        }
        if (terms.isEmpty() || (terms.size == 1 && ':' in terms[0] && !terms[0].startsWith('-'))) {
            val path = if (terms.isEmpty()) "/n/index-all.nozomi" else hitomiNozomiPath(terms[0])
            var count = 0
            if (order.oldest) {
                readResource(path, 0L..3L, missingIsEmpty = true, onRangeCount = { count = it })
                val range = oldestNozomiRange(count, page)
                    ?: return@withContext SearchResult(emptyList(), false, count)
                val ids = decodeHitomiIds(readResource(path, range, missingIsEmpty = true)).reversed()
                return@withContext SearchResult(ids, page.toLong() * 30 < count, count)
            }
            val start = (page.toLong() - 1) * 30 * 4
            val ids = decodeHitomiIds(readResource(path, start..start + 123, missingIsEmpty = true, onRangeCount = { count = it }))
            return@withContext SearchResult(ids.take(30), page.toLong() * 30 < count, count)
        }
        val cached = synchronized(searches) { searches[query] }
        val ids = if (cached != null && System.currentTimeMillis() - cached.first < 300_000) cached.second else {
            var result: MutableSet<Long>? = null
            val negative = mutableListOf<String>()
            for (term in terms) {
                coroutineContext.ensureActive()
                if (term.startsWith('-')) { negative += term.drop(1); continue }
                val matches = termIds(term)
                if (result == null) result = matches.toMutableSet() else result.retainAll(matches.toSet())
                if (result.isEmpty()) break
            }
            if (result == null) result = nozomi("/n/index-all.nozomi").toMutableSet()
            for (term in negative) result.removeAll(termIds(term).toSet())
            order.sortIds(result).also { found ->
                synchronized(searches) {
                    if (searches.size >= 3) searches.remove(searches.keys.first())
                    searches[query] = System.currentTimeMillis() to found
                }
            }
        }
        val start = ((page.toLong() - 1) * 30).coerceAtMost(ids.size.toLong()).toInt()
        SearchResult(ids.subList(start, minOf(start + 30, ids.size)), start + 30 < ids.size, ids.size)
    }

    internal suspend fun searchPopular(queries: List<String>, page: Int, period: String): SearchResult = withContext(Dispatchers.IO) {
        require(page > 0 && period in setOf("today", "week"))
        val key = queries.joinToString("\n", prefix = "popular/$period\n")
        val cached = synchronized(searches) { searches[key] }
        val ids = if (cached != null && System.currentTimeMillis() - cached.first < 300_000) cached.second else {
            val terms = queries.map { parseHitomiSearchOrder(it).terms }
            val directIds = terms.flatten().mapNotNull { it.removePrefix("id:").toLongOrNull()?.takeIf { id -> id > 0 } }
            val ranking = directIds.ifEmpty { decodeHitomiIds(readResource("/n/popular/$period-all.nozomi")) }
            val matches = terms.flatten().distinct().filter { it.removePrefix("id:").toLongOrNull()?.let { id -> id > 0 } != true }
                .associateWith { termIds(it.removePrefix("-")).toSet() }
            filterPopularityRanking(ranking, terms, matches).also { found ->
                synchronized(searches) {
                    if (searches.size >= 3) searches.remove(searches.keys.first())
                    searches[key] = System.currentTimeMillis() to found
                }
            }
        }
        val start = ((page.toLong() - 1) * 30).coerceAtMost(ids.size.toLong()).toInt()
        SearchResult(ids.subList(start, minOf(start + 30, ids.size)), start + 30 < ids.size, ids.size)
    }

    private suspend fun termIds(term: String): List<Long> {
        if (':' !in term) return titleIds(term)
        return nozomi(hitomiNozomiPath(term))
    }

    private suspend fun nozomi(path: String): List<Long> = decodeHitomiIds(readResource(path, missingIsEmpty = true))

    private suspend fun titleIds(term: String): List<Long> {
        val version = readResource("/galleriesindex/version").toString(Charsets.UTF_8).trim()
        require(version.matches(Regex("[0-9]+"))) { "검색 인덱스 버전이 올바르지 않습니다." }
        val key = MessageDigest.getInstance("SHA-256").digest(term.toByteArray(Charsets.UTF_8)).copyOf(4)
        var address = 0L
        val visited = mutableSetOf<Long>()
        repeat(64) {
            require(address >= 0 && visited.add(address)) { "검색 인덱스가 올바르지 않습니다." }
            val node = parseHitomiIndexNode(readResource("/galleriesindex/galleries.$version.index", address..address + 463))
            val index = node.keys.indexOfFirst { compareHitomiKeys(key, it) <= 0 }.let { if (it < 0) node.keys.size else it }
            if (index < node.keys.size && compareHitomiKeys(key, node.keys[index]) == 0) {
                val (offset, length) = node.data[index]
                require(offset >= 0 && length >= 4 && length <= 64 * 1024 * 1024)
                return decodeHitomiIds(readResource("/galleriesindex/galleries.$version.data", offset + 4..offset + length - 1))
            }
            address = node.children[index]
            if (address == 0L) return emptyList()
        }
        throw IOException("검색 인덱스 탐색 한도를 초과했습니다.")
    }

    suspend fun getGallery(id: Long): GallerySummary = withContext(Dispatchers.IO) { parseDirectGallery(rawGallery(id), id) }

    private suspend fun rawGallery(id: Long): JSONObject {
        require(id > 0)
        val text = readResource("/galleries/$id.js").toString(Charsets.UTF_8)
        require(text.trimStart().startsWith("var galleryinfo")) { "작품 정보 응답이 올바르지 않습니다." }
        return JSONObject(text.substringAfter('=').trim().removeSuffix(";"))
    }

    suspend fun getGalleryPages(id: Long): List<String> = withContext(Dispatchers.IO) {
        val raw = rawGallery(id)
        val context = getImageContext()
        val files = raw.getJSONArray("files")
        List(files.length()) { index -> context.imageUrl(files.getJSONObject(index).getString("hash")) }
    }

    internal suspend fun getImageContext(): HitomiImageContext =
        parseHitomiImageContext(readResource("/gg.js").toString(Charsets.UTF_8))

    private suspend fun readResource(path: String, range: LongRange? = null, missingIsEmpty: Boolean = false, onRangeCount: ((Int) -> Unit)? = null): ByteArray =
        withContext(Dispatchers.IO) {
            coroutineContext.ensureActive()
            val connection = URL("https://ltn.gold-usergeneratedcontent.net$path").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Referer", "https://hitomi.la/")
                range?.let {
                    connection.setRequestProperty("Range", "bytes=${it.first}-${it.last}")
                    connection.setRequestProperty("Accept-Encoding", "identity")
                }
                val status = connection.responseCode
                if (onRangeCount != null && status in listOf(206, 416)) onRangeCount(nozomiResultCount(connection.getHeaderField("Content-Range").orEmpty()))
                if (onRangeCount != null && status == 404) onRangeCount(0)
                if (status in listOf(404, 416) && missingIsEmpty) return@withContext byteArrayOf()
                if (status !in listOf(200, 206)) throw IOException("Hitomi 요청 실패 (HTTP $status)")
                if (range != null && (status != 206 || !connection.getHeaderField("Content-Range").orEmpty().startsWith("bytes ${range.first}-"))) {
                    throw IOException("Hitomi 검색 범위 응답이 올바르지 않습니다.")
                }
                connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 64 * 1024 * 1024) { "Hitomi 응답이 너무 큽니다." }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            } finally { connection.disconnect() }
        }
}

internal fun nozomiResultCount(contentRange: String): Int {
    val bytes = contentRange.substringAfterLast('/').toLongOrNull()
    require(bytes != null && bytes >= 0 && bytes % 4 == 0L && bytes / 4 <= Int.MAX_VALUE) { "검색 결과 전체 건수를 읽을 수 없습니다." }
    return (bytes / 4).toInt()
}

internal fun oldestNozomiRange(count: Int, page: Int): LongRange? {
    require(count >= 0 && page > 0)
    val end = count.toLong() - (page.toLong() - 1) * 30
    if (end <= 0) return null
    return (end - 30).coerceAtLeast(0) * 4..end * 4 - 1
}

internal fun decodeHitomiIds(bytes: ByteArray): List<Long> {
    require(bytes.size % 4 == 0) { "작품 목록 응답이 올바르지 않습니다." }
    val buffer = ByteBuffer.wrap(bytes)
    return List(bytes.size / 4) { buffer.int.toLong() and 0xffffffffL }.filter { it > 0 }
}

internal data class HitomiIndexNode(val keys: List<ByteArray>, val data: List<Pair<Long, Int>>, val children: List<Long>)

internal fun parseHitomiIndexNode(bytes: ByteArray): HitomiIndexNode {
    val buffer = ByteBuffer.wrap(bytes)
    val count = buffer.int.also { require(it in 0..16) }
    val keys = List(count) {
        val size = buffer.int.also { require(it in 1..31) }
        ByteArray(size).also(buffer::get)
    }
    val dataCount = buffer.int.also { require(it == count) }
    val data = List(dataCount) { buffer.long to buffer.int }
    return HitomiIndexNode(keys, data, List(17) { buffer.long })
}

internal fun compareHitomiKeys(a: ByteArray, b: ByteArray): Int {
    for (i in 0 until minOf(a.size, b.size)) {
        val comparison = (a[i].toInt() and 255).compareTo(b[i].toInt() and 255)
        if (comparison != 0) return comparison
    }
    return 0
}

internal data class HitomiImageContext(val cases: Set<Int>, val defaultValue: Int, val prefix: String) {
    private fun shard(hash: String): Int {
        require(hash.matches(Regex("[a-fA-F0-9]{64}"))) { "이미지 해시가 올바르지 않습니다." }
        val code = (hash.takeLast(1) + hash.takeLast(3).take(2)).toInt(16)
        return if ((code in cases) == (defaultValue == 0)) 1 else 0
    }

    fun imageUrl(hash: String): String {
        val suffix = shard(hash) + 1
        val code = (hash.takeLast(1) + hash.takeLast(3).take(2)).toInt(16)
        return "https://w$suffix.gold-usergeneratedcontent.net/$prefix$code/$hash.webp"
    }

    fun thumbnailUrl(hash: String, directory: String): String =
        "https://${if (shard(hash) == 0) "atn" else "btn"}.gold-usergeneratedcontent.net/$directory/${hash.takeLast(1)}/${hash.takeLast(3).take(2)}/$hash.webp"
}

internal fun parseHitomiImageContext(script: String): HitomiImageContext {
    val cases = Regex("case\\s+(\\d+)\\s*:").findAll(script).map { it.groupValues[1].toInt() }.toSet()
    val defaultValue = Regex("var\\s+o\\s*=\\s*([01])\\s*;").find(script)?.groupValues?.get(1)?.toInt()
    val prefix = Regex("\\bb\\s*:\\s*['\"]([0-9]+/)['\"]").find(script)?.groupValues?.get(1)
    require(cases.isNotEmpty() && defaultValue != null && prefix != null) { "이미지 주소 규칙을 읽을 수 없습니다." }
    return HitomiImageContext(cases, defaultValue, prefix)
}

internal fun parseDirectGallery(raw: JSONObject, id: Long): GallerySummary {
    fun names(key: String, field: String): List<String> {
        val array = raw.optJSONArray(key) ?: return emptyList()
        return List(array.length()) { array.getJSONObject(it).optString(field) }.filter(String::isNotBlank)
    }
    val files = raw.getJSONArray("files")
    val hash = files.optJSONObject(0)?.optString("hash").orEmpty()
    val tags = raw.optJSONArray("tags")
    return GallerySummary(
        id = id, title = raw.optString("title", "Gallery #$id"),
        artists = names("artists", "artist"), series = names("parodys", "parody"),
        groups = names("groups", "group"), characters = names("characters", "character"),
        galleryType = raw.optString("type").takeIf(String::isNotBlank),
        tags = List(tags?.length() ?: 0) { index ->
            val tag = tags!!.getJSONObject(index)
            GalleryTag(when { tag.optInt("male") == 1 -> "male"; tag.optInt("female") == 1 -> "female"; else -> "tag" }, tag.getString("tag"))
        },
        thumbnailUrl = hash.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }?.let {
            "https://tn.gold-usergeneratedcontent.net/webpbigtn/${it.takeLast(1)}/${it.takeLast(3).take(2)}/$it.webp"
        },
        pageCount = files.length(), language = raw.optString("language_localname").ifBlank { raw.optString("language") },
        publishedDate = raw.optString("datepublished").ifBlank { raw.optString("date") }.takeIf(String::isNotBlank),
    )
}

internal fun hitomiNozomiPath(term: String): String {
    val type = term.substringBefore(':')
    val value = term.substringAfter(':').replace('_', ' ')
    require(type in setOf("language", "artist", "group", "series", "character", "type", "tag", "male", "female")) {
        "지원하지 않는 검색 필터: $type"
    }
    require(value.isNotBlank()) { "검색 필터 값이 비어 있습니다." }
    val encoded = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    return when (type) {
        "language" -> "/n/index-$encoded.nozomi"
        "male", "female" -> "/n/tag/$type:$encoded-all.nozomi"
        else -> "/n/$type/$encoded-all.nozomi"
    }
}

internal data class HitomiSearchOrder(val terms: List<String>, val oldest: Boolean, val popularity: String? = null) {
    fun sortIds(ids: Collection<Long>): List<Long> = if (oldest) ids.sorted() else ids.sortedDescending()
}

internal fun parseHitomiSearchOrder(query: String): HitomiSearchOrder {
    val tokens = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter(String::isNotBlank)
    val order = tokens.lastOrNull { it in hitomiSortTokens }
    return HitomiSearchOrder(tokens.filterNot { it in hitomiSortTokens }, order == "sort:oldest",
        when (order) { "sort:daily" -> "today"; "sort:weekly" -> "week"; else -> null })
}

internal val hitomiSortTokens = setOf("sort:newest", "sort:oldest", "sort:daily", "sort:weekly")

internal fun filterPopularityRanking(
    ranking: List<Long>, queries: List<List<String>>, matches: Map<String, Set<Long>>,
): List<Long> = ranking.distinct().filter { id ->
    queries.any { terms ->
        val directId = terms.firstNotNullOfOrNull { it.removePrefix("id:").toLongOrNull()?.takeIf { value -> value > 0 } }
        terms.all { term ->
            when {
                term.startsWith("-") -> id !in matches.getValue(term)
                directId != null -> id == directId
                else -> id in matches.getValue(term)
            }
        }
    }
}
