package com.doujinzip.android.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.MainActivity
import com.doujinzip.android.R
import com.doujinzip.android.model.BrowserPage
import com.doujinzip.android.model.BrowserWorkspace
import com.doujinzip.android.security.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ActivityLockRestoreTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun saveResultReturnsUnlockedButOrdinaryActivityReturnStillLocks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        val store = AppLockStore(context)
        val previous = store.read()
        lateinit var lock: AppLockViewModel
        try {
            store.save(LockCredential.create(LockMethod.PIN, "142857"))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val unlocked = java.util.concurrent.CountDownLatch(1)
                scenario.onActivity {
                    lock = androidx.lifecycle.ViewModelProvider(it)[AppLockViewModel::class.java]
                    lock.verify("142857") { accepted -> assertTrue(accepted); unlocked.countDown() }
                }
                assertTrue(unlocked.await(5, java.util.concurrent.TimeUnit.SECONDS))
                instrumentation.runOnMainSync { lock.beginBackupSave() }
                scenario.moveToState(Lifecycle.State.CREATED)
                instrumentation.runOnMainSync {
                    assertFalse(lock.locked)
                    lock.finishBackupSave(true)
                }
                scenario.moveToState(Lifecycle.State.RESUMED)
                instrumentation.runOnMainSync {
                    assertFalse(lock.locked)
                    lock.beginBackupSave()
                }
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                instrumentation.runOnMainSync { assertTrue(lock.locked) }
            }
        } finally { if (previous == null) store.clear() else store.save(previous) }
    }

    @Test fun realActivityUnlocksAndRelocksWithoutChangingSecurityOrOpeningPosition() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".uitest"))
        val lock = AppLockStore(context)
        val previous = lock.read()
        val browser = BrowserPreferenceStore(context)
        val savedWorkspace = browser.loadGalleryWorkspace()
        val savedActive = browser.loadActiveGalleryWorkspace()
        val savedDestination = browser.loadMainDestination()
        val series = BrowserPage.LibrarySeries("Lock restore fixture")
        try {
            lock.save(LockCredential.create(LockMethod.PIN, "142857"))
            browser.saveGalleryWorkspace(BrowserWorkspace.initial(BrowserPage.LibraryHome()).let { workspace ->
                workspace.copy(tabs = workspace.tabs.map { it.copy(history = it.history + series, currentIndex = 1) })
            })
            browser.saveActiveGalleryWorkspace(true)
            browser.saveMainDestination("library")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                fun unlock() {
                    compose.onNodeWithText(context.getString(R.string.ui_app_lock)).assertExists()
                    scenario.onActivity { assertTrue(it.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0) }
                    compose.onAllNodesWithText("Lock restore fixture").assertCountEquals(0)
                    compose.onNode(hasSetTextAction()).performTextInput("142857")
                    compose.onNodeWithText(context.getString(R.string.ui_unlock)).performClick()
                    compose.waitUntil(10_000) { compose.onAllNodesWithText(context.getString(R.string.ui_app_lock)).fetchSemanticsNodes().isEmpty() }
                    compose.onAllNodesWithText("Lock restore fixture").onFirst().assertExists()
                    scenario.onActivity { assertTrue(it.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0) }
                }
                unlock()
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                unlock()
                assertTrue(lock.enabled)
                assertEquals(series.key, browser.loadGalleryWorkspace().activeTab.currentPage.key)
            }
        } finally {
            if (previous == null) lock.clear() else lock.save(previous)
            browser.saveGalleryWorkspace(savedWorkspace)
            browser.saveActiveGalleryWorkspace(savedActive)
            browser.saveMainDestination(savedDestination)
        }
    }

    @Test fun localPreferenceChangesInvalidatePreparedRefreshSnapshot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".uitest"))
        val store = LibraryPreferenceStore(context)
        val previous = store.loadProgress()
        try {
            val snapshot = store.snapshot()
            assertTrue(store.matchesSnapshot(snapshot))
            store.saveProgress(previous + ("concurrent-reader" to 49))
            assertFalse(store.matchesSnapshot(snapshot))
        } finally { store.saveProgress(previous) }
    }
}
