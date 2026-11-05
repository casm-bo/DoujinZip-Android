package com.doujinzip.android.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.data.*
import com.doujinzip.android.model.*
import com.doujinzip.android.network.CompanionClient
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PresentationSyncTest {
    @Test fun lostAcknowledgementIsRecoveredWithoutUploadingLegacyEdits() = verifyRecovery(
        GalleryPresentation("hitomi:42", "Phone edit", LibraryMetadata(hitomiId = "42")))
    @Test fun seriesOverrideIsReplacedByConfirmedPcSnapshot() = verifyRecovery(
        GalleryPresentation(seriesPresentationKey("Fixture series"), "Series edit", LibraryMetadata(tags = listOf("series tag"))))
    @Test fun seriesCoverDocumentIsPulledWithoutUploadingLegacyEdits() = verifyRecovery(
        GalleryPresentation(seriesCoverPresentationKey("Fixture series"), "Fixture series", LibraryMetadata()))
    private fun verifyRecovery(value: GalleryPresentation) = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "presentation-recovery-${System.nanoTime()}.db"
        val server = ServerSocket(0).apply { soTimeout = 5000 }
        val executor = Executors.newSingleThreadExecutor()
        val edits = mutableListOf<String>()
        val remote = value.copy(version = 1, revision = 1)
        val fixture = executor.submit {
            repeat(2) { requestIndex -> server.accept().use { socket ->
                socket.soTimeout = 5000
                val input = socket.getInputStream().buffered()
                fun line(): String = buildString { while (true) { val next = input.read(); if (next < 0 || next == 10) break; if (next != 13) append(next.toChar()) } }
                val first = line()
                val headers = mutableMapOf<String, String>()
                while (true) { val header = line(); if (header.isEmpty()) break; headers[header.substringBefore(':').lowercase()] = header.substringAfter(':').trim() }
                check(headers["authorization"] == "Bearer fixture-token")
                val length = headers["content-length"]?.toInt() ?: 0
                val bytes = ByteArray(length)
                var read = 0
                while (read < length) { val count = input.read(bytes, read, length - read); check(count > 0); read += count }
                val data = if (requestIndex == 0) {
                    check(first.startsWith("POST "))
                    val body = JSONObject(String(bytes, Charsets.UTF_8))
                    check(body.getJSONObject("value").getString("key") == value.key)
                    edits += body.getString("mutationId")
                    JSONObject().put("status", if (requestIndex == 0) "applied" else "already_applied").put("value", remote.toJson())
                } else {
                    check(first.startsWith("GET "))
                    JSONObject().put("values", JSONArray().put(remote.toJson())).put("cursor", 1).put("hasMore", false)
                }
                val output = JSONObject().put("success", true).put("data", data).toString().toByteArray()
                socket.getOutputStream().use { it.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${output.size}\r\nConnection: close\r\n\r\n".toByteArray()); it.write(output) }
            } }
        }
        try {
            LibraryIdentityStore(context, databaseName).use { identity ->
                val store = PresentationStore(identity, "pc")
                val profile = DesktopProfile("pc", "PC", "http://127.0.0.1:${server.localPort}", "fixture-token")
                val client = CompanionClient()
                val first = store.saveLocal(value)
                client.savePresentation(profile, first) // Server saved; the app did not persist its acknowledgement.
                assertTrue(store.get(value.key)!!.dirty)
                PresentationSync(client, store, PresentationCovers(context)).sync(profile)
                assertEquals(listOf(first.mutationId), edits)
                assertFalse(store.get(value.key)!!.dirty)
                assertNull(store.get(value.key)!!.conflict)
                assertEquals(1L, store.cursor(profile.id))
            }
            fixture.get(5, TimeUnit.SECONDS)
        } finally { server.close(); executor.shutdownNow(); context.deleteDatabase(databaseName) }
    }
}
