package com.doujinzip.android.network

import com.doujinzip.android.data.PresentationRecord
import com.doujinzip.android.data.toJson
import com.doujinzip.android.model.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PresentationApiTest {
    @Test fun defaultDesktopCoverUsesTheFirstReadablePage() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/library/books") { exchange ->
            val output = """{"success":true,"data":[{"id":42,"pageCount":2,"modifiedAt":100,"coverUrl":"/v1/library/books/42/cover"},{"id":43,"pageCount":0,"coverUrl":"/v1/library/books/43/cover"}]}""".toByteArray()
            exchange.sendResponseHeaders(200, output.size.toLong())
            exchange.responseBody.use { it.write(output) }
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val (readable, coverOnly) = CompanionClient().getLibraryBooks(DesktopProfile("fixture", "Fixture", baseUrl, "fixture-token"))
            assertEquals("$baseUrl/v1/library/books/42/pages/0?v=100", readable.coverUri)
            assertEquals(readable.coverUri, readable.pages.first().uri)
            assertEquals("$baseUrl/v1/library/books/43/cover", coverOnly.coverUri)
            val custom = GalleryPresentation("sync:fixture", "Fixture", LibraryMetadata())
            assertEquals("file:///fixture-custom-cover", readable.withPresentation(custom, "file:///fixture-custom-cover").coverUri)
        } finally {
            server.stop(0)
        }
    }

    @Test fun `metadata retries retain ID and version and binary covers use authentication`() = runBlocking {
        val value = GalleryPresentation("hitomi:42", "Edited", LibraryMetadata(tags = emptyList()), version = 3)
        val edits = mutableListOf<JSONObject>()
        val authorizations = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val bytes = byteArrayOf(1, 2, 3)
        server.createContext("/v1/library/presentations") { exchange ->
            authorizations += exchange.requestHeaders.getFirst("Authorization")
            val data = if (exchange.requestMethod == "GET") JSONObject().put("values", JSONArray().put(value.toJson())).put("cursor", 5).put("hasMore", false)
                else { edits += JSONObject(exchange.requestBody.reader().readText()); JSONObject().put("status", "conflict").put("value", value.toJson()) }
            val output = JSONObject().put("success", true).put("data", data).toString().toByteArray()
            exchange.sendResponseHeaders(200, output.size.toLong()); exchange.responseBody.use { it.write(output) }
        }
        server.createContext("/v1/library/presentation-covers") { exchange ->
            authorizations += exchange.requestHeaders.getFirst("Authorization")
            if (exchange.requestMethod == "PUT") assertArrayEquals(bytes, exchange.requestBody.readBytes())
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val profile = DesktopProfile("pc", "PC", "http://127.0.0.1:${server.address.port}", "secret")
            val client = CompanionClient()
            val record = PresentationRecord(value, dirty = true, mutationId = "stable-id")
            repeat(2) { assertEquals("conflict", client.savePresentation(profile, record).status) }
            assertEquals(listOf("stable-id", "stable-id"), edits.map { it.getString("mutationId") })
            assertTrue(edits.all { it.getLong("baseVersion") == 3L && it.getJSONObject("value").getJSONObject("metadata").getJSONArray("tags").length() == 0 })
            assertEquals(value, client.getPresentations(profile, 0).values.single())
            val hash = "a".repeat(64)
            client.transferCover(profile, hash, bytes)
            assertArrayEquals(bytes, client.transferCover(profile, hash))
            assertTrue(authorizations.all { it == "Bearer secret" })
        } finally { server.stop(0) }
    }
}
