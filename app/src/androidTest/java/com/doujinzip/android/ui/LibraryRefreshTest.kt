package com.doujinzip.android.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.model.DesktopProfile
import com.doujinzip.android.security.SecureProfileStore
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LibraryRefreshTest {
    @Test fun reconnectSharesSuccessfulInFlightScanButManualRefreshStillRuns() = verifyRefresh(changeGeneration = true)
    @Test fun mutationDuringScanQueuesFreshSnapshot() = verifyRefresh(changeGeneration = false)

    private fun verifyRefresh(changeGeneration: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        val profiles = SecureProfileStore(context)
        val saved = profiles.load()
        val server = ServerSocket(0)
        val workers = Executors.newCachedThreadPool()
        val booksRequested = CountDownLatch(1)
        val connected = CountDownLatch(1)
        val releaseBooks = CountDownLatch(1)
        val thirdRequested = CountDownLatch(1)
        val releaseThird = CountDownLatch(1)
        val changedStatus = CountDownLatch(1)
        val serverGeneration = AtomicInteger(1)
        val bookCalls = AtomicInteger()
        val store = ViewModelStore()
        workers.submit { runCatching { while (!server.isClosed) {
            val socket = server.accept()
            workers.submit { runCatching { socket.use {
                val input = it.getInputStream().bufferedReader()
                val path = input.readLine().substringAfter(' ').substringBefore(' ')
                while (!input.readLine().isNullOrEmpty()) { }
                val data: Any = when {
                    path.contains("/library/books") -> {
                        val call = bookCalls.incrementAndGet(); booksRequested.countDown()
                        check(releaseBooks.await(10, TimeUnit.SECONDS))
                        if (call == 3) { thirdRequested.countDown(); check(releaseThird.await(15, TimeUnit.SECONDS)) }
                        JSONArray()
                    }
                    path.contains("/library/presentations") -> JSONObject().put("values", JSONArray()).put("cursor", 0).put("hasMore", false)
                    path.endsWith("/status") -> { connected.countDown(); JSONObject().put("service", "fixture").put("syncGeneration", serverGeneration.get()) }
                    else -> JSONObject().put("path", "C:/Fixture")
                }
                val bytes = JSONObject().put("success", true).put("data", data).toString().toByteArray()
                it.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray() + bytes)
                if (path.endsWith("/status") && (data as JSONObject).optInt("syncGeneration") == 2) changedStatus.countDown()
            } } }
        } } }
        lateinit var model: MainViewModel
        fun waitFor(predicate: () -> Boolean) {
            val deadline = android.os.SystemClock.uptimeMillis() + 10_000
            do {
                var ready = false
                instrumentation.runOnMainSync { ready = predicate() }
                if (ready) return
                Thread.sleep(50)
            } while (android.os.SystemClock.uptimeMillis() < deadline)
            fail("Library refresh did not finish")
        }
        try {
            profiles.save(listOf(DesktopProfile("refresh-fixture-${System.nanoTime()}", "Fixture", "http://127.0.0.1:${server.localPort}", "token")))
            instrumentation.runOnMainSync { model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[MainViewModel::class.java] }
            assertTrue(booksRequested.await(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { model.onAppForegrounded() }
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            waitFor { model.uiState.desktopConnectionState == DesktopConnectionState.CONNECTED }
            releaseBooks.countDown()
            waitFor { !model.uiState.isLibraryScanning }
            Thread.sleep(600)
            assertEquals("Reconnect must not repeat the successful scan", 1, bookCalls.get())
            instrumentation.runOnMainSync { model.refreshLibrary() }
            waitFor { bookCalls.get() == 2 && !model.uiState.isLibraryScanning }
            instrumentation.runOnMainSync { model.refreshLibrary() }
            assertTrue(thirdRequested.await(10, TimeUnit.SECONDS))
            if (changeGeneration) {
                serverGeneration.set(2)
                assertTrue(changedStatus.await(10, TimeUnit.SECONDS))
            } else instrumentation.runOnMainSync {
                MainViewModel::class.java.getDeclaredMethod("removeDeletedLibraryState", Set::class.java).apply { isAccessible = true }
                    .invoke(model, setOf("deleted-fixture"))
            }
            // Allow the localhost status response to reach the main-thread monitor before stopping it.
            Thread.sleep(300)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { model.onAppBackgrounded() }
            releaseThird.countDown()
            waitFor { bookCalls.get() == 4 && !model.uiState.isLibraryScanning }
        } finally {
            releaseBooks.countDown()
            releaseThird.countDown()
            instrumentation.runOnMainSync { store.clear() }
            profiles.save(saved)
            server.close(); workers.shutdownNow()
        }
    }
}
