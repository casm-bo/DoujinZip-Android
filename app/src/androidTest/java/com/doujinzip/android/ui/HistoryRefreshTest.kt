package com.doujinzip.android.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.doujinzip.android.model.*
import com.doujinzip.android.ui.theme.DoujinZipTheme
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HistoryRefreshTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun pullRefreshRetriesMissingMetadataAndFailedCoverWithoutChangingHistory() {
        compose.activityRule.scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val server = ServerSocket(0)
        val requests = AtomicInteger()
        val executor = Executors.newSingleThreadExecutor()
        val image = ByteArrayOutputStream().apply {
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).also {
                it.eraseColor(android.graphics.Color.BLUE)
                it.compress(Bitmap.CompressFormat.PNG, 100, this); it.recycle()
            }
        }.toByteArray()
        executor.submit {
            runCatching { while (!server.isClosed) server.accept().use { socket ->
                socket.soTimeout = 5000
                val input = socket.getInputStream().bufferedReader()
                while (!input.readLine().isNullOrEmpty()) { }
                val success = requests.incrementAndGet() > 1
                val body = if (success) image else byteArrayOf()
                socket.getOutputStream().use {
                    it.write("HTTP/1.1 ${if (success) "200 OK" else "503 Service Unavailable"}\r\nContent-Type: image/png\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    it.write(body)
                }
            } }
        }
        val entry = RecentHistoryEntry("gallery:42", "Synthetic history", 1, HistorySource.SEARCH,
            galleryId=42, thumbnail="http://127.0.0.1:${server.localPort}/cover.png")
        var state by mutableStateOf(MainUiState(recentHistory=listOf(entry)))
        var metadataLoads=0
        var refreshes=0
        try {
            compose.setContent { DoujinZipTheme {
                RecentHistoryScreen(state, PaddingValues(0.dp), { _, _ -> }, {}, {}, {},
                    onLoadMetadata = {
                        metadataLoads++
                        if (metadataLoads > 1) state=state.copy(galleryCache=mapOf(42L to GallerySummary(
                            42, entry.title, artists=emptyList(), series=emptyList(), galleryType=null,
                            thumbnailUrl=entry.thumbnail, pageCount=1, language=null,
                            tags=listOf(GalleryTag("tag", "recovered")))))
                    }, onRefresh = { refreshes++ })
            } }
            compose.waitUntil(10_000) { metadataLoads == 1 && requests.get() == 1 }
            compose.onNode(hasScrollAction() and hasAnyDescendant(hasText(entry.title))).performTouchInput { swipeDown() }
            compose.waitUntil(10_000) { metadataLoads == 2 && requests.get() == 2 }
            compose.onNodeWithText("recovered").assertExists()
            compose.runOnIdle {
                assertEquals(1, refreshes)
                assertEquals(listOf(entry), state.recentHistory)
            }
        } finally { server.close(); executor.shutdownNow() }
    }
}
