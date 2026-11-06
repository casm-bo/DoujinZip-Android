package com.doujinzip.android.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.model.*
import com.doujinzip.android.security.RecentHistoryStore
import org.junit.Assert.*
import org.junit.Test

class SeriesReadingHistoryTest {
    @Test fun cloudReaderRecordsLatestEpisodeEvenWhenPageHasNotChanged() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        val history = RecentHistoryStore(context)
        val saved = history.load()
        val store = ViewModelStore()
        lateinit var model: MainViewModel
        try {
            instrumentation.runOnMainSync {
                model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[MainViewModel::class.java]
            }
            model.awaitStarted()
            instrumentation.runOnMainSync {
                val books = listOf("First", "Second").map { name -> LibraryBook(name, name,
                    locationUri = "fixture", locationName = "Fixture", folderUri = "",
                    pages = List(4) { LibraryPage("", "$it") }, modifiedAt = 1, isCloud = true, syncId = "fixture-$name") }
                val profile = DesktopProfile("fixture", "Fixture", "http://127.0.0.1:9", "fixture-token")
                MainViewModel::class.java.getDeclaredMethod("setUiState", MainUiState::class.java).apply { isAccessible = true }
                    .invoke(model, model.uiState.copy(profiles = listOf(profile), selectedProfileId = profile.id,
                        libraryBooks = books, libraryProgress = mapOf("First" to 1, "Second" to 2), historyRecording = true,
                        recentHistory = listOf(RecentHistoryEntry("sync:fixture-First", "First", 1, HistorySource.PC, bookId = "First", page = 1))))
                model.updateLibraryProgress("Second", 2)
                assertEquals("Second", model.uiState.recentHistory.first().bookId)
                assertEquals(2, model.uiState.recentHistory.first().page)
                assertEquals("Second", history.load().first().bookId)
                model.updateLibraryProgress("First", 1)
                assertEquals("First", model.uiState.recentHistory.first().bookId)
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            history.save(saved)
        }
    }
}
