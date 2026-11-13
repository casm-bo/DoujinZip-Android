package com.doujinzip.android.ui

import android.app.Application
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.doujinzip.android.R
import com.doujinzip.android.ui.theme.DoujinZipTheme
import com.doujinzip.android.model.*
import com.doujinzip.android.security.*
import java.net.ServerSocket
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule

class StartupRestoreTest {
    @get:Rule val compose = createComposeRule()
    @Test fun interruptedSessionOpensSearchOnceAndKeepsTheReaderTab() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = app.getSharedPreferences("startup-recovery-fixture", 0)
        val browser = BrowserPreferenceStore(object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = fixture
        })
        try {
            fixture.edit().clear().commit()
            val reader = BrowserPage.OnlineReader(42, "Synthetic reader", 7, position = ReaderPosition(7, 15))
            val previous = BrowserWorkspace.initial().openTab(reader)
            browser.saveBrowserWorkspace(previous)
            browser.saveActiveGalleryWorkspace(true)
            browser.saveMainDestination("library")

            assertTrue(browser.recoverSearchAfterExit(100))
            val recovered = browser.loadBrowserWorkspace()
            assertTrue(recovered.activeTab.currentPage is BrowserPage.Search)
            assertEquals(reader, recovered.tabs.first { it.id == previous.activeTabId }.currentPage)
            assertFalse(browser.loadActiveGalleryWorkspace())
            assertEquals("browser", browser.loadMainDestination())
            assertFalse(browser.recoverSearchAfterExit(100))
            assertEquals(recovered, browser.loadBrowserWorkspace())
        } finally {
            fixture.edit().clear().commit()
        }
    }
    @Test fun restoresLargeSavedStateBeforeExposingTheWorkspace() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        val browser = BrowserPreferenceStore(context)
        val library = LibraryPreferenceStore(context)
        val history = RecentHistoryStore(context)
        val profiles = SecureProfileStore(context)
        val previousProfiles = profiles.load()
        val previousProgress = library.loadProgress()
        val previousSeries = library.loadCustomSeries()
        val previousHistory = history.load()
        val previousWorkspace = browser.loadGalleryWorkspace()
        val previousActive = browser.loadActiveGalleryWorkspace()
        val previousLocations = browser.loadLibraryLocations()
        val previousDestination = browser.loadMainDestination()
        val store = ViewModelStore()
        val server = ServerSocket(0) // Hold the refresh until the restored snapshot is checked.
        val progress = (0 until 2_000).associate { "fixture-$it" to it % 100 }
        val series = (0 until 2_000).associate { "fixture-$it" to CustomSeriesAssignment("Series ${it / 10}", it % 10, 1) }
        val recent = listOf(RecentHistoryEntry("startup-book", "Startup book", 100, HistorySource.DEVICE, bookId = "fixture-50", page = 50))
        val home = BrowserPage.LibraryHome(query = "tag:saved", scrollIndex = 37, scrollOffset = 19)
        lateinit var model: MainViewModel
        try {
            profiles.save(listOf(DesktopProfile("startup-fixture", "Startup", "http://127.0.0.1:${server.localPort}", "fixture")))
            browser.saveLibraryLocations(emptyList())
            library.saveProgress(progress); library.saveCustomSeries(series); history.save(recent)
            browser.saveGalleryWorkspace(BrowserWorkspace.initial(home)); browser.saveActiveGalleryWorkspace(true)
            browser.saveMainDestination("library")
            instrumentation.runOnMainSync {
                model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[MainViewModel::class.java]
                assertTrue("Constructor returns before decoding the large saved state", model.uiState.isStarting)
            }
            model.awaitStarted()
            instrumentation.runOnMainSync {
                assertEquals(progress, model.uiState.libraryProgress)
                assertEquals(series, model.uiState.customSeriesByBookId)
                assertEquals(recent, model.uiState.recentHistory)
                assertEquals(home, model.activeBrowserPage())
                assertEquals("tag:saved", model.uiState.libraryQuery)
            }
            compose.setContent { DoujinZipTheme { DoujinZipApp(model) } }
            compose.onNode(isSelected() and hasText(context.getString(R.string.ui_gallery))).assertExists()
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            server.close()
            profiles.save(previousProfiles); library.saveProgress(previousProgress); library.saveCustomSeries(previousSeries)
            history.save(previousHistory); browser.saveGalleryWorkspace(previousWorkspace)
            browser.saveActiveGalleryWorkspace(previousActive); browser.saveLibraryLocations(previousLocations)
            browser.saveMainDestination(previousDestination)
        }
    }
}
