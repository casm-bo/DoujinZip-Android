package com.doujinzip.android.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.model.*
import com.doujinzip.android.security.LibraryPreferenceStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LibraryMutationTest {
    @Test fun confirmedSeriesUpdatesVersionMembershipAndOpenPageDuringScan() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        val preferences = LibraryPreferenceStore(context)
        val saved = preferences.loadCustomSeries()
        val removals = preferences.loadSeriesRemovalTimes()
        val store = ViewModelStore()
        lateinit var model: MainViewModel
        try {
            instrumentation.runOnMainSync {
                model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[MainViewModel::class.java]
            }
            model.awaitStarted()
            instrumentation.runOnMainSync {
                val profile = DesktopProfile("fixture", "Fixture", "http://127.0.0.1:9", "fixture-token")
                val book = LibraryBook("A", "Episode", locationUri = "fixture", locationName = "Fixture", folderUri = "",
                    pages = emptyList(), modifiedAt = 1, isCloud = true, syncId = "fixture-A", syncStateVersion = 1)
                val baseline = mapOf("A" to CustomSeriesAssignment("Old", 0))
                MainViewModel::class.java.getDeclaredMethod("setUiState", MainUiState::class.java).apply { isAccessible = true }
                    .invoke(model, model.uiState.copy(profiles = listOf(profile), selectedProfileId = profile.id,
                        libraryBooks = listOf(book), customSeriesByBookId = baseline,
                        galleryWorkspace = BrowserWorkspace.initial(BrowserPage.LibrarySeries("Old")),
                        desktopLibraryReady = true, desktopConnectionState = DesktopConnectionState.CONNECTED, isLibraryScanning = true))
                // An unchanged edit is valid while a background scan runs and sends no request.
                assertTrue(model.commitLibrarySeriesEdit("Old", baseline, listOf("A")))
                val response = JSONObject().put("bookSyncId", "fixture-A").put("status", "applied")
                    .put("name", "New").put("order", 0).put("modifiedAt", 10).put("version", 2)
                val apply = MainViewModel::class.java.getDeclaredMethod("applyConfirmedSeriesResults", JSONArray::class.java).apply { isAccessible = true }
                apply.invoke(model, JSONArray().put(response))
                assertEquals("New", model.uiState.customSeriesByBookId["A"]?.name)
                assertEquals(2L, model.uiState.libraryBooks.single().syncStateVersion)
                assertEquals("New", (model.uiState.galleryWorkspace.activeTab.currentPage as BrowserPage.LibrarySeries).name)
                assertEquals(model.uiState.customSeriesByBookId, preferences.loadCustomSeries())
                apply.invoke(model, JSONArray().put(response.put("name", "Stale").put("version", 1)))
                assertEquals("New", model.uiState.customSeriesByBookId["A"]?.name)
                apply.invoke(model, JSONArray().put(response.put("name", JSONObject.NULL).put("version", 3).put("modifiedAt", 20)))
                assertTrue(model.uiState.customSeriesByBookId.isEmpty())
                assertTrue(model.uiState.galleryWorkspace.activeTab.currentPage is BrowserPage.LibraryHome)
                assertEquals(20L, preferences.loadSeriesRemovalTimes()["A"])
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            preferences.saveCustomSeries(saved)
            preferences.saveSeriesRemovalTimes(removals)
            com.doujinzip.android.data.ServerEditStore(context).let { edits ->
                edits.pending("fixture").forEach { (id, request) -> edits.remove(id, request) }
            }
        }
    }
}
