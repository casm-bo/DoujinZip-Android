package com.doujinzip.android.ui

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.R
import com.doujinzip.android.data.LibraryIdentityStore
import com.doujinzip.android.model.*
import com.doujinzip.android.security.BrowserPreferenceStore
import com.doujinzip.android.security.SecureProfileStore
import com.doujinzip.android.ui.theme.DoujinZipTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NewTabNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun repeatedFacetReturnsToExistingSearchTab() = withModel { model ->
        instrumentation.runOnMainSync {
            val origin = model.activeBrowserPage()
            model.searchFromFacet("fixture:artist")
            val root = model.uiState.browserWorkspace
            val parentId = root.activeTabId
            assertTrue(model.canReturnFromNewTab(false))
            model.updateTabWorkspace(root.pushPage(BrowserPage.OnlineGallery(42, "Fixture")), false)
            assertFalse(model.canReturnFromNewTab(false))
            model.searchFromFacet("fixture:artist")
            assertEquals(parentId, model.uiState.browserWorkspace.activeTabId)
            assertEquals(root.tabs.size, model.uiState.browserWorkspace.tabs.size)
            assertEquals(root.activeTab.currentPage.key, model.activeBrowserPage().key)
            assertTrue(model.goBackInBrowserTab())
            assertEquals(origin, model.activeBrowserPage())
        }
    }

    @Test fun galleryTabReturnsToSeriesDetailAfterArtistSearch() = withModel { model ->
        val context = instrumentation.targetContext
        val name = "Synthetic navigation series"
        val artist = "Synthetic navigation artist"
        val key = seriesPresentationKey(name)
        try {
            instrumentation.runOnMainSync {
                model.saveGalleryPresentation(GalleryPresentation(key, name, LibraryMetadata(artists = listOf(artist))))
                model.selectLibrarySeries(name)
            }
            compose.setContent { DoujinZipTheme { DoujinZipApp(model) } }
            compose.waitUntil(5_000) { compose.onAllNodesWithText(artist).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(artist).performClick()
            compose.onNodeWithText(context.getString(R.string.ui_gallery), ignoreCase = true).performClick()
            compose.onNodeWithText(artist).assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(name, (model.activeBrowserPage() as BrowserPage.LibrarySeries).name)
            }
        } finally {
            LibraryIdentityStore(context).use {
                it.writableDatabase.delete("gallery_presentations", "resource_key = ?", arrayOf(key))
            }
        }
    }

    @Test fun galleryTabBackTraversesDescendantsBeforeClosingAtHome() = withModel { model ->
        instrumentation.runOnMainSync {
            val origin = model.uiState.galleryWorkspace
            model.newGalleryTab()
            val opened = model.uiState.galleryWorkspace
            model.updateTabWorkspace(opened.pushPage(BrowserPage.LibraryBook("fixture", "Fixture"))
                .pushPage(BrowserPage.LibraryReader("fixture", "Fixture", 0)), true)
            assertTrue(model.goBackInGalleryTab())
            assertTrue(model.activeBrowserPage() is BrowserPage.LibraryBook)
            assertEquals(opened.activeTabId, model.uiState.galleryWorkspace.activeTabId)
            assertTrue(model.goBackInGalleryTab())
            assertTrue(model.activeBrowserPage() is BrowserPage.LibraryHome)
            assertEquals(opened.activeTabId, model.uiState.galleryWorkspace.activeTabId)
            assertTrue(model.goBackInGalleryTab())
            assertEquals(origin, model.uiState.galleryWorkspace)
        }
    }

    @Test fun systemBackAtSearchRootClosesTabAndReturnsToGalleryOpener() = withModel { model ->
        lateinit var back: OnBackPressedDispatcher
        lateinit var origin: BrowserPage
        instrumentation.runOnMainSync {
            origin = model.activeBrowserPage()
            model.searchFromFacet("fixture:artist")
        }
        compose.setContent { DoujinZipTheme {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            DoujinZipApp(model)
        } }
        compose.runOnIdle { back.onBackPressed() }
        compose.runOnIdle {
            assertEquals(origin, model.activeBrowserPage())
            assertEquals(1, model.uiState.browserWorkspace.tabs.size)
        }
    }

    private fun withModel(check: (MainViewModel) -> Unit) {
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        val preferences = BrowserPreferenceStore(context)
        val profiles = SecureProfileStore(context)
        val oldProfiles = profiles.load()
        val oldBrowser = preferences.loadBrowserWorkspace()
        val oldGallery = preferences.loadGalleryWorkspace()
        val oldActive = preferences.loadActiveGalleryWorkspace()
        val oldDestination = preferences.loadMainDestination()
        val oldLanguages = preferences.loadPreferredLanguages()
        val oldLocations = preferences.loadLibraryLocations()
        val oldHistory = preferences.loadSearchQueryHistory()
        val store = ViewModelStore()
        lateinit var model: MainViewModel
        try {
            profiles.save(emptyList())
            preferences.savePreferredLanguages(emptySet())
            preferences.saveLibraryLocations(emptyList())
            preferences.saveBrowserWorkspace(BrowserWorkspace.initial())
            preferences.saveGalleryWorkspace(BrowserWorkspace.initial(BrowserPage.LibraryHome()))
            preferences.saveActiveGalleryWorkspace(true)
            preferences.saveMainDestination(MainDestination.Browser.route)
            instrumentation.runOnMainSync {
                model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[MainViewModel::class.java]
            }
            model.awaitStarted()
            check(model)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            profiles.save(oldProfiles)
            preferences.saveBrowserWorkspace(oldBrowser)
            preferences.saveGalleryWorkspace(oldGallery)
            preferences.saveActiveGalleryWorkspace(oldActive)
            preferences.saveMainDestination(oldDestination)
            preferences.savePreferredLanguages(oldLanguages)
            preferences.saveLibraryLocations(oldLocations)
            preferences.saveSearchQueryHistory(oldHistory)
        }
    }
}
