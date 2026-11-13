package com.doujinzip.android.data

import android.content.Context
import com.doujinzip.android.model.DesktopProfile
import com.doujinzip.android.network.CompanionApiException
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

internal data class DesktopBackup(val version: String, val modifiedAt: Long)

internal class DesktopBackupSync(private val context: Context) {
    private val preferences = context.getSharedPreferences("backup_runtime", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = preferences.getBoolean("auto_enabled", true)
        set(value) { preferences.edit().putBoolean("auto_enabled", value).apply() }

    fun decisionKey(profile: DesktopProfile) = "auto_decision/${profile.serverId ?: profile.baseUrl}/${profile.deviceId}"
    fun accepted(profile: DesktopProfile) = preferences.getString(decisionKey(profile), null)
    fun digest(profile: DesktopProfile) = preferences.getString("${decisionKey(profile)}/digest", null)
    fun lastCheck(profile: DesktopProfile) = preferences.getLong("${decisionKey(profile)}/checked", 0L)
    fun markChecked(profile: DesktopProfile) {
        preferences.edit().putLong("${decisionKey(profile)}/checked", System.currentTimeMillis()).apply()
    }
    fun checkAgain(profile: DesktopProfile) {
        preferences.edit().remove("${decisionKey(profile)}/checked").apply()
    }
    fun accept(profile: DesktopProfile, version: String, digest: String? = null) {
        check(preferences.edit().putString(decisionKey(profile), version)
            .putString("${decisionKey(profile)}/digest", digest).commit())
    }

    suspend fun inspect(profile: DesktopProfile): DesktopBackup? = request(profile, "HEAD") {
        when (responseCode) {
            204 -> null
            200 -> DesktopBackup(requireNotNull(getHeaderField("ETag")), lastModified)
            else -> throw CompanionApiException("Backup check failed", responseCode)
        }
    }

    suspend fun upload(profile: DesktopProfile, backup: DesktopBackup?, file: File): String = request(profile, "PUT") {
        if (backup == null) setRequestProperty("If-None-Match", "*")
        else setRequestProperty("If-Match", backup.version)
        setRequestProperty("Content-Type", "application/zip")
        doOutput = true
        setFixedLengthStreamingMode(file.length())
        file.inputStream().use { input -> outputStream.use { output ->
            val buffer = ByteArray(8192)
            while (true) {
                coroutineContext.ensureActive()
                val size = input.read(buffer)
                if (size < 0) break
                output.write(buffer, 0, size)
            }
        } }
        if (responseCode != 204) throw CompanionApiException("Backup upload failed", responseCode)
        requireNotNull(getHeaderField("ETag"))
    }

    suspend fun download(profile: DesktopProfile, backup: DesktopBackup): BackupArchive = request(profile, "GET") {
        setRequestProperty("If-Match", backup.version)
        if (responseCode != 200) throw CompanionApiException("Backup download failed", responseCode)
        inputStream.use(BackupFormat::read)
    }

    private suspend fun <T> request(
        profile: DesktopProfile, method: String, block: suspend HttpURLConnection.() -> T,
    ): T = withContext(Dispatchers.IO) {
        val connection = URL("${profile.baseUrl}/v1/android-backup").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5_000
            connection.readTimeout = 15_000
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer ${profile.token}")
            connection.block()
        } finally {
            connection.disconnect()
        }
    }
}

internal fun backupContentDigest(archive: BackupArchive): String {
    fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") { key ->
            "${JSONObject.quote(key)}:${canonical(value.get(key))}"
        }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }
    val data = JSONObject(archive.data.toString()).apply { remove("createdAt") }
    return BackupFormat.digest((canonical(data) + archive.covers.keys.sorted().joinToString()).toByteArray())
}
