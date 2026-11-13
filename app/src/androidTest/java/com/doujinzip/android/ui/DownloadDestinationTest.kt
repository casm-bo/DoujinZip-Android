package com.doujinzip.android.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.data.DownloadRequestStore
import com.doujinzip.android.data.DownloadTarget
import com.doujinzip.android.model.*
import com.doujinzip.android.security.BrowserPreferenceStore
import org.junit.Assert.*
import org.junit.Test

class DownloadDestinationTest {
    @Test fun completedRequestsAndOwnedPcBooksBlockStaleDetailDownloadActions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        val requests = DownloadRequestStore.get(context)
        val before = requests.requests.value
        val store = ViewModelStore()
        val gallery = GallerySummary(9_900_003, "Owned fixture", emptyList(), emptyList(), null, emptyList(), null, 1, null)
        val completed = com.doujinzip.android.data.DownloadRequest(galleryId = gallery.id, title = gallery.title,
            target = DownloadTarget.LOCAL, status = com.doujinzip.android.data.RequestStatus.COMPLETED)
        lateinit var model: MainViewModel
        try {
            requests.update { listOf(completed) }
            instrumentation.runOnMainSync {
                model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[MainViewModel::class.java]
            }
            model.awaitStarted()
            instrumentation.runOnMainSync {
                val setState = MainViewModel::class.java.getDeclaredMethod("setUiState", MainUiState::class.java).apply { isAccessible = true }
                setState.invoke(model, model.uiState.copy(downloadDestination = DownloadDestination.DEVICE))
                model.downloadGallery(gallery)
                assertEquals(listOf(completed), requests.requests.value)
                assertNull(model.uiState.pendingDownloadGallery)
                assertTrue(model.uiState.downloadingGalleryIds.isEmpty())
                val retry = completed.copy(id = "paused-fixture", target = DownloadTarget.DESKTOP,
                    status = com.doujinzip.android.data.RequestStatus.PAUSED)
                requests.update { listOf(completed, retry) }
                model.startLocalDownload(retry.id)
                assertEquals(listOf(completed, retry), requests.requests.value)
                assertEquals(context.getString(com.doujinzip.android.R.string.msg_this_gallery_is_already_in_the_device_library), model.uiState.downloadQueueError)
                requests.update { emptyList() }
                val book = LibraryBook("owned", "Fixture", locationUri = "", locationName = "", folderUri = "", pages = emptyList(),
                    modifiedAt = 1, isCloud = true, metadata = LibraryMetadata(hitomiId = gallery.id.toString()))
                setState.invoke(model, model.uiState.copy(libraryBooks = listOf(book), downloadDestination = DownloadDestination.DESKTOP))
                model.downloadGallery(gallery)
                assertTrue(requests.requests.value.isEmpty())
                assertNull(model.uiState.pendingDownloadGallery)
                setState.invoke(model, model.uiState.copy(downloadDestination = DownloadDestination.ASK))
                model.downloadGallery(gallery)
                assertEquals(gallery, model.uiState.pendingDownloadGallery)
                model.chooseDownloadDestination(DownloadDestination.DESKTOP)
                assertTrue(requests.requests.value.isEmpty())
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            requests.update { before }
        }
    }

    @Test fun deviceChoiceNeverQueuesPcAndOfflinePcChoiceIsDurable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        val preferences = BrowserPreferenceStore(context)
        val previous = preferences.loadDownloadDestination()
        val location = preferences.loadDownloadLocation()
        val suppressed = preferences.isDesktopDownloadAlertSuppressed()
        val requests = DownloadRequestStore.get(context)
        val before = requests.requests.value
        val store = ViewModelStore()
        val fixture = GallerySummary(id = 9_900_001, title = "Download fixture", artists = emptyList(), series = emptyList(), galleryType = null,
            tags = emptyList(), thumbnailUrl = null, pageCount = 1, language = null)
        lateinit var model: MainViewModel
        try {
            preferences.saveDownloadDestination(DownloadDestination.ASK)
            preferences.saveDownloadLocation(null)
            preferences.suppressDesktopDownloadAlert(false)
            instrumentation.runOnMainSync {
                model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[MainViewModel::class.java]
            }
            model.awaitStarted()
            instrumentation.runOnMainSync {
                val second = fixture.copy(id = fixture.id + 1)
                model.downloadGalleries(listOf(fixture, second, fixture))
                assertEquals(fixture, model.uiState.pendingDownloadGallery)
                assertEquals(2, model.uiState.pendingDownloadCount)
                assertEquals(before, requests.requests.value)
                model.chooseDownloadDestination(DownloadDestination.DEVICE)
                assertTrue(model.uiState.downloadFolderRequired)
                assertEquals(2, model.uiState.pendingDownloadCount)
                assertEquals(before, requests.requests.value)
                model.cancelDownloadPrompt()
                assertEquals(0, model.uiState.pendingDownloadCount)
                model.setDownloadDestination(DownloadDestination.DESKTOP)
                assertTrue(model.uiState.desktopDownloadConnectionPrompt)
                model.dismissDesktopDownloadConnectionPrompt(true)
                model.downloadGallery(fixture)
            }
            val deadline = android.os.SystemClock.uptimeMillis() + 5_000
            while (requests.requests.value.none { it.galleryId == fixture.id } && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(50)
            val request = requests.requests.value.first { it.galleryId == fixture.id }
            assertEquals(DownloadTarget.DESKTOP, request.target)
            assertTrue(request.pendingForDesktop(model.uiState.selectedProfileId.orEmpty()))
            assertTrue(java.io.File(context.filesDir, "download-requests.json").readText().contains(request.id))
            instrumentation.runOnMainSync { assertFalse(model.uiState.desktopDownloadConnectionPrompt) }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            requests.update { before }
            preferences.saveDownloadDestination(previous); preferences.saveDownloadLocation(location); preferences.suppressDesktopDownloadAlert(suppressed)
        }
    }
}
