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

    @Test fun settingsKeepsScrollWhenReturningFromDetails() = withModel { model ->
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync { model.rememberMainDestination(MainDestination.Settings.route) }
        compose.setContent { DoujinZipTheme { DoujinZipApp(model) } }
        val backup = context.getString(R.string.ui_about)
        val list = compose.onNode(hasScrollAction())
        list.performScrollToNode(hasText(backup))
        val before = compose.onNodeWithText(backup).getUnclippedBoundsInRoot().top
        compose.onNodeWithText(backup).performClick()
        compose.onNodeWithText("<").performClick()
        assertEquals(before.value, compose.onNodeWithText(backup).assertIsDisplayed().getUnclippedBoundsInRoot().top.value, 1f)
    }
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
            assertTrue(model.activeBrowserPage() is BrowserPage.OnlineGallery)
            assertTrue(model.goBackInBrowserTab())
            assertEquals(root.activeTab.currentPage.key, model.activeBrowserPage().key)
            assertTrue(model.goBackInBrowserTab())
            assertEquals(origin, model.activeBrowserPage())
            assertEquals(root.tabs.size, model.uiState.browserWorkspace.tabs.size)
        }
    }

    @Test fun galleryTabReturnsToSeriesDetailAfterArtistSearch() = withModel { model ->
        val context = instrumentation.targetContext
        val name = "Synthetic navigation series"
        instrumentation.runOnMainSync {
            model.selectLibrarySeries(name)
            model.searchFromFacet("artist:synthetic_navigation_artist")
        }
        compose.setContent { DoujinZipTheme { DoujinZipApp(model) } }
        compose.onNodeWithText(context.getString(R.string.ui_gallery), ignoreCase = true).performClick()
        compose.runOnIdle {
            assertEquals(name, (model.activeBrowserPage() as BrowserPage.LibrarySeries).name)
            assertFalse(model.canReturnFromNewTab(true))
            assertFalse(model.canReturnFromNewTab(false))
            assertTrue(model.goBackInGalleryTab())
            assertTrue(model.activeBrowserPage() is BrowserPage.LibraryHome)
        }
    }

    @Test fun galleryTabBackTraversesDescendantsAndKeepsTheTab() = withModel { model ->
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
            assertEquals(origin.activeTabId, model.uiState.galleryWorkspace.activeTabId)
            assertEquals(opened.tabs.map { it.id }, model.uiState.galleryWorkspace.tabs.map { it.id })
            assertFalse(model.goBackInGalleryTab())
        }
    }

    @Test fun systemBackAtSearchRootKeepsTabAndReturnsToGalleryOpener() = withModel { model ->
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
            assertEquals(2, model.uiState.browserWorkspace.tabs.size)
            assertFalse(model.canReturnFromNewTab(false))
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
