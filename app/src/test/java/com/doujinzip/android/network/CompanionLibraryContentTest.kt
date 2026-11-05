package com.doujinzip.android.network

import com.doujinzip.android.model.DesktopProfile
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CompanionLibraryContentTest {
    private fun withServer(body: (String) -> String, test: (DesktopProfile) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { request ->
            val bytes = body(request.requestURI.toString()).toByteArray(Charsets.UTF_8)
            request.sendResponseHeaders(200, bytes.size.toLong())
            request.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { test(DesktopProfile("test", "Synthetic desktop", "http://127.0.0.1:${server.address.port}", "synthetic-token")) }
        finally { server.stop(0) }
    }

    @Test fun receivesVersionedPagesAndPreservesServerRevision() = withServer({
        """{"success":true,"snapshot":"generation:1","data":{"books":[{"id":1,"syncId":"test-book","title":"Book","pageCount":2,"modifiedAt":100,"contentVersion":"content-token","contentRevision":8}],"hasMore":false}}"""
    }) { profile -> runBlocking {
        val book = CompanionClient().getLibraryBooks(profile).single()
        assertEquals("content-token", book.contentVersion)
        assertEquals(8L, book.contentRevision)
        assertEquals(2, book.pages.size)
        assertTrue(book.pages.all { it.uri.endsWith("?v=content-token") })
        assertEquals(book.pages.first().uri, book.coverUri)
    } }

    @Test fun legacyDesktopStillLoadsPagesWithTimestampCacheKeys() = withServer({ uri ->
        if (uri.contains("/pages")) """{"success":true,"data":["/v1/library/books/1/pages/0","/v1/library/books/1/pages/1"]}"""
        else """{"success":true,"data":[{"id":1,"title":"Legacy","pageCount":2,"modifiedAt":100}]}"""
    }) { profile -> runBlocking {
        val client = CompanionClient()
        val book = client.getLibraryBooks(profile).single()
        assertNull(book.contentVersion)
        assertEquals("", book.pages[1].uri)
        assertTrue(client.getLibraryBookPages(profile, 1, book.modifiedAt).all { it.endsWith("?v=100") })
    } }

    @Test fun rejectsMixedSnapshotsBeforeReturningPartialLibrary() {
        var requests = 0
        withServer({ uri ->
            requests++
            if (requests == 1) """{"success":true,"snapshot":"generation:1","data":{"books":[{"id":2,"pageCount":1}],"nextCursor":2,"hasMore":true}}"""
            else {
                assertTrue(uri.contains("snapshot=generation%3A1"))
                """{"success":true,"snapshot":"generation:2","data":{"books":[],"hasMore":false}}"""
            }
        }) { profile -> runBlocking {
            try { CompanionClient().getLibraryBooks(profile); fail("Mixed snapshot accepted") }
            catch (_: IllegalArgumentException) { }
        } }
        assertEquals(2, requests)
    }
}
