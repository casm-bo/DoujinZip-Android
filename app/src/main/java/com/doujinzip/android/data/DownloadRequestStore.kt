package com.doujinzip.android.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class DownloadTarget { DESKTOP, LOCAL }
enum class RequestStatus { WAITING, SENDING, SENT, RUNNING, PAUSING, PAUSED, FAILED, COMPLETED }

data class DownloadRequest(
    val id: String = UUID.randomUUID().toString(),
    val galleryId: Long,
    val title: String,
    val thumbnailUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val target: DownloadTarget = DownloadTarget.DESKTOP,
    val status: RequestStatus = RequestStatus.WAITING,
    val profileId: String? = null,
    val desktopAttempted: Boolean = false,
    val error: String? = null,
    val downloadedFiles: Int = 0,
    val totalFiles: Int = 0,
    val outputUri: String? = null,
    val locationUri: String? = null,
    val desktopQueueId: Long? = null,
) {
    val isBusy: Boolean get() = status in setOf(RequestStatus.RUNNING, RequestStatus.PAUSING, RequestStatus.SENDING)
    val isFinished: Boolean get() = status == RequestStatus.COMPLETED || status == RequestStatus.SENT
    val canDownloadLocally: Boolean get() = !isBusy && !isFinished && !desktopAttempted
    fun pendingForDesktop(id: String): Boolean = target == DownloadTarget.DESKTOP &&
        status == RequestStatus.WAITING && (profileId == null || profileId == id)
    fun recover(): DownloadRequest = when {
        status == RequestStatus.SENDING -> copy(status = RequestStatus.WAITING)
        target == DownloadTarget.LOCAL && status in setOf(RequestStatus.RUNNING, RequestStatus.PAUSING, RequestStatus.WAITING) ->
            copy(status = RequestStatus.PAUSED, error = "중단된 다운로드입니다. 재개를 눌러 계속하세요.")
        else -> this
    }
}

/** One durable queue shared by UI and the local download service. */
class DownloadRequestStore private constructor(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "download-requests.json"))
    var readError: String? = null
        private set
    private val mutableRequests = MutableStateFlow(load())
    val requests = mutableRequests.asStateFlow()

    private fun load(): List<DownloadRequest> = try {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) emptyList()
        else decodeDownloadRequests(file.openRead().bufferedReader().use { it.readText() }).map { it.recover() }
    } catch (error: Exception) {
        readError = "다운로드 목록을 읽지 못했습니다: ${error.message}"
        emptyList()
    }

    @Synchronized fun update(transform: (List<DownloadRequest>) -> List<DownloadRequest>) {
        check(readError == null) { readError.orEmpty() }
        val next = transform(mutableRequests.value)
        if (next == mutableRequests.value) return
        val output = file.startWrite()
        try {
            output.write(encodeDownloadRequests(next).toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
        mutableRequests.value = next
    }

    fun change(id: String, transform: (DownloadRequest) -> DownloadRequest) = update { list ->
        list.map { if (it.id == id) transform(it) else it }
    }

    companion object {
        @Volatile private var instance: DownloadRequestStore? = null
        fun get(context: Context): DownloadRequestStore = instance ?: synchronized(this) {
            instance ?: DownloadRequestStore(context.applicationContext).also { instance = it }
        }
    }
}

internal fun encodeDownloadRequests(requests: List<DownloadRequest>): String = JSONArray().apply {
    requests.forEach { item -> put(JSONObject()
        .put("id", item.id).put("galleryId", item.galleryId).put("title", item.title)
        .put("thumbnailUrl", item.thumbnailUrl).put("addedAt", item.addedAt)
        .put("target", item.target.name).put("status", item.status.name).put("profileId", item.profileId)
        .put("desktopAttempted", item.desktopAttempted)
        .put("error", item.error).put("downloadedFiles", item.downloadedFiles).put("totalFiles", item.totalFiles)
        .put("outputUri", item.outputUri).put("locationUri", item.locationUri).put("desktopQueueId", item.desktopQueueId)) }
}.toString()

internal fun decodeDownloadRequests(raw: String): List<DownloadRequest> {
    val array = JSONArray(raw)
    val result = List(array.length()) { index ->
        val item = array.getJSONObject(index)
        fun optional(key: String) = item.optString(key).takeIf { it.isNotEmpty() && it != "null" }
        DownloadRequest(
            id = item.getString("id"), galleryId = item.getLong("galleryId"), title = item.getString("title"),
            thumbnailUrl = optional("thumbnailUrl"), addedAt = item.getLong("addedAt"),
            target = DownloadTarget.valueOf(item.getString("target")), status = RequestStatus.valueOf(item.getString("status")),
            profileId = optional("profileId"), desktopAttempted = item.optBoolean("desktopAttempted"),
            error = optional("error"), downloadedFiles = item.optInt("downloadedFiles"),
            totalFiles = item.optInt("totalFiles"), outputUri = optional("outputUri"), locationUri = optional("locationUri"),
            desktopQueueId = item.optLong("desktopQueueId").takeIf { it > 0 },
        ).also { require(it.galleryId > 0 && it.id.isNotBlank() && it.downloadedFiles >= 0 && it.totalFiles >= 0) }
    }
    require(result.distinctBy { it.id }.size == result.size)
    return result
}

internal fun addDownloadRequest(requests: List<DownloadRequest>, request: DownloadRequest): List<DownloadRequest> =
    if (requests.any { it.galleryId == request.galleryId && !it.isFinished }) requests
    else listOf(request) + requests

internal fun List<DownloadRequest>.confirmDesktopCompletions(profileId: String, queue: List<com.doujinzip.android.model.DownloadQueueItem>): List<DownloadRequest> = map { request ->
    val completed = queue.any { item -> item.status == com.doujinzip.android.model.DownloadStatus.COMPLETED &&
        if (request.desktopQueueId != null) item.id == request.desktopQueueId else item.galleryId == request.galleryId }
    if (request.target == DownloadTarget.DESKTOP && request.profileId == profileId && (request.status == RequestStatus.SENT || (request.status == RequestStatus.WAITING && request.desktopAttempted)) && completed)
        request.copy(status = RequestStatus.COMPLETED, error = null) else request
}
