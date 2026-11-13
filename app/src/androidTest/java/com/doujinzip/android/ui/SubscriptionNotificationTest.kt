package com.doujinzip.android.ui

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.MainActivity
import com.doujinzip.android.R
import com.doujinzip.android.data.*
import com.doujinzip.android.model.*
import com.doujinzip.android.security.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SubscriptionNotificationTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val galleryId=9876543L
    private fun seed():SearchSubscription {
        val store=SubscriptionStore.get(context)
        val value=SearchSubscription(id="synthetic-notice",name="Synthetic subscription",query="tag:synthetic_example",enabled=false)
        store.save("",value)
        // Seed a completed check without starting any live network work.
        val active=value.copy(enabled=true)
        store.save("",active)
        store.checked("",active,listOf(1),emptyList(),1)
        val gallery=GallerySummary(galleryId,"Synthetic private title",listOf("Synthetic private artist"),emptyList(),null,emptyList(),null,10,"korean")
        store.checked("",active,listOf(galleryId,1),listOf(gallery),System.currentTimeMillis())
        store.save("",value)
        return value
    }
    private fun cleanup() {
        val db=SubscriptionStore.get(context).writableDatabase
        for(table in listOf("notices","matches","reads")) db.delete(table,"scope='' AND gallery=?",arrayOf(galleryId.toString()))
        db.delete("subscriptions","scope='' AND id=?",arrayOf("synthetic-notice"))
        db.delete("state","key=?",arrayOf("notified//$galleryId"))
        context.getSystemService(NotificationManager::class.java).activeNotifications.filter { it.tag?.startsWith("subscription/")==true }.forEach { context.getSystemService(NotificationManager::class.java).cancel(it.tag,it.id) }
    }
    @Test fun postedDetailsAreRedactedOnLockAndPrivacyCanBeDisabled() {
        check(context.packageName.endsWith(".uitest"))
        val lock=AppLockStore(context);val previous=lock.read()
        val options=SubscriptionPreferences(context);val oldOptions=options.load()
        val manager=context.getSystemService(NotificationManager::class.java)
        try {
            if(Build.VERSION.SDK_INT>=33) instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
            options.save(SubscriptionOptions())
            lock.save(LockCredential.create(LockMethod.PIN,"142857"))
            seed()
            lateinit var viewModel:AppLockViewModel
            instrumentation.runOnMainSync { viewModel=AppLockViewModel(context.applicationContext as Application) }
            SubscriptionNotifications.session(context,true)
            SubscriptionNotifications.refresh(context)
            fun notice(title:String):Notification {
                // NotificationManager posts and updates are asynchronous on physical devices.
                compose.waitUntil(5_000) { manager.activeNotifications.any { it.tag=="subscription//$galleryId" && it.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()==title } }
                return manager.activeNotifications.first { it.tag=="subscription//$galleryId" }.notification
            }
            notice("Synthetic private title")
            instrumentation.runOnMainSync { viewModel.onBackground() }
            val masked=notice(context.getString(R.string.sub_generic))
            assertEquals(context.getString(R.string.sub_generic),masked.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertFalse(masked.extras.toString().contains("Synthetic private"))
            assertNull(masked.getLargeIcon())
            options.save(options.load().copy(hideWhenLocked=false))
            SubscriptionNotifications.refresh(context,false)
            notice("Synthetic private title")
            options.save(options.load().copy(notifications=false))
            SubscriptionNotifications.refresh(context)
            compose.waitUntil(5_000) { manager.activeNotifications.none { it.tag?.startsWith("subscription/")==true } }
            assertTrue(SubscriptionStore.get(context).notices("").any { it.galleryId==galleryId })
        } finally {
            cleanup();options.save(oldOptions);if(previous==null) lock.clear() else lock.save(previous)
        }
    }
    @Test fun notificationTargetWaitsForUnlockAndSurvivesActivityRecreation() {
        check(context.packageName.endsWith(".uitest"))
        val lock=AppLockStore(context);val previous=lock.read()
        val browser=BrowserPreferenceStore(context);val oldWorkspace=browser.loadBrowserWorkspace();val oldGallery=browser.loadActiveGalleryWorkspace();val oldDestination=browser.loadMainDestination()
        try {
            lock.save(LockCredential.create(LockMethod.PIN,"142857"));seed()
            val intent=Intent(context,MainActivity::class.java).setComponent(android.content.ComponentName(context.packageName,SubscriptionNotifications.ENTRY_ACTIVITY)).putExtra("subscription_scope","").putExtra("subscription_gallery",galleryId)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                compose.onNodeWithText(context.getString(R.string.ui_app_lock)).assertExists()
                assertFalse(SubscriptionStore.get(context).notices("").first { it.galleryId==galleryId }.read)
                scenario.recreate()
                compose.onNode(hasSetTextAction()).performTextInput("142857")
                compose.onNodeWithText(context.getString(R.string.ui_unlock)).performClick()
                compose.waitUntil(10_000) { SubscriptionStore.get(context).notices("").first { it.galleryId==galleryId }.read }
                compose.waitUntil(10_000) { browser.loadBrowserWorkspace().activeTab.currentPage is BrowserPage.OnlineGallery }
            }
        } finally {
            cleanup();if(previous==null) lock.clear() else lock.save(previous)
            browser.saveBrowserWorkspace(oldWorkspace);browser.saveActiveGalleryWorkspace(oldGallery);browser.saveMainDestination(oldDestination)
        }
    }
    @Test fun backFromArtistSearchReturnsToNoticeGalleryThenNotices() {
        check(context.packageName.endsWith(".uitest"))
        val lock = AppLockStore(context)
        val previousLock = lock.read()
        val profiles = SecureProfileStore(context)
        val previousProfiles = profiles.load()
        val browser = BrowserPreferenceStore(context)
        val previousWorkspace = browser.loadBrowserWorkspace()
        val previousGalleryWorkspace = browser.loadActiveGalleryWorkspace()
        val previousDestination = browser.loadMainDestination()
        try {
            lock.clear()
            profiles.save(emptyList())
            seed()
            val artistQuery = "artist:synthetic_private_artist"
            val workspace = BrowserWorkspace.initial(BrowserPage.Search(query = artistQuery)).openTab()
            browser.saveBrowserWorkspace(workspace)
            browser.saveActiveGalleryWorkspace(false)
            browser.saveMainDestination("browser")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val notifications = context.getString(R.string.sub_notifications)
                val title = "Synthetic private title"
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithContentDescription(notifications).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithContentDescription(notifications).performClick()
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText(title).performClick()
                compose.waitUntil(10_000) {
                    (browser.loadBrowserWorkspace().activeTab.currentPage as? BrowserPage.OnlineGallery)?.galleryId == galleryId
                }
                val noticeTab = hasText("$notifications (", substring = true)
                compose.waitUntil(10_000) {
                    compose.onAllNodes(noticeTab).fetchSemanticsNodes().isEmpty() &&
                        compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("Synthetic private artist").performClick()
                compose.waitUntil(10_000) {
                    val searchWorkspace = browser.loadBrowserWorkspace()
                    searchWorkspace.tabs.size == 3 &&
                        (searchWorkspace.activeTab.currentPage as? BrowserPage.Search)?.query == artistQuery
                }
                compose.waitForIdle()
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitUntil(10_000) {
                    val page = browser.loadBrowserWorkspace().activeTab.currentPage as? BrowserPage.OnlineGallery
                    page?.galleryId == galleryId &&
                        compose.onAllNodesWithText("Synthetic private artist").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNode(noticeTab).assertDoesNotExist()
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.onNode(noticeTab).assertIsDisplayed()
                compose.onNodeWithText(title).assertIsDisplayed()
                assertTrue(SubscriptionStore.get(context).notices("").first { it.galleryId == galleryId }.read)
                assertTrue(browser.loadBrowserWorkspace().activeTab.currentPage is BrowserPage.Search)
                assertEquals(workspace.tabs.map { it.id }, browser.loadBrowserWorkspace().tabs.map { it.id })
            }
        } finally {
            cleanup()
            if (previousLock == null) lock.clear() else lock.save(previousLock)
            profiles.save(previousProfiles)
            browser.saveBrowserWorkspace(previousWorkspace)
            browser.saveActiveGalleryWorkspace(previousGalleryWorkspace)
            browser.saveMainDestination(previousDestination)
        }
    }
    @Test fun changingIntervalUpdatesOneDurableNetworkConstrainedPeriodicJob() {
        val options=SubscriptionPreferences(context);val previous=options.load()
        try {
            val value=seed().copy(enabled=true)
            SubscriptionStore.get(context).save("",value)
            options.save(previous.copy(intervalMinutes=60))
            SubscriptionWorker.schedule(context).result.get(10,java.util.concurrent.TimeUnit.SECONDS)
            val manager=androidx.work.WorkManager.getInstance(context)
            fun work()=manager.getWorkInfosForUniqueWork(SubscriptionWorker.WORK_NAME).get(10,java.util.concurrent.TimeUnit.SECONDS).filter { !it.state.isFinished }.single()
            val id=work().id
            assertEquals(60*60_000L,work().periodicityInfo!!.repeatIntervalMillis)
            assertEquals(androidx.work.NetworkType.CONNECTED,work().constraints.requiredNetworkType)
            for(minutes in listOf(5,10)) {
                context.getSharedPreferences("subscription_options",android.content.Context.MODE_PRIVATE).edit().putInt("interval",minutes).commit()
                SubscriptionWorker.schedule(context).result.get(10,java.util.concurrent.TimeUnit.SECONDS)
                assertEquals(15,SubscriptionPreferences(context).load().intervalMinutes)
                assertEquals(id,work().id)
                assertEquals(15*60_000L,work().periodicityInfo!!.repeatIntervalMillis)
            }
            options.save(previous.copy(intervalMinutes=360))
            SubscriptionWorker.schedule(context).result.get(10,java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(id,work().id)
            assertEquals(360*60_000L,work().periodicityInfo!!.repeatIntervalMillis)
        } finally { cleanup();options.save(previous);SubscriptionWorker.schedule(context).result.get(10,java.util.concurrent.TimeUnit.SECONDS) }
    }
    @Test fun publicLauncherIntentCannotForgeNotificationReadActions() {
        val lock=AppLockStore(context);val previous=lock.read()
        try {
            lock.clear();seed()
            val intent=Intent(context,MainActivity::class.java).putExtra("subscription_scope","").putExtra("subscription_gallery",galleryId)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                lateinit var model:MainViewModel
                scenario.onActivity { model=androidx.lifecycle.ViewModelProvider(it)[MainViewModel::class.java] }
                compose.waitUntil(10_000) { !model.uiState.isStarting }
                compose.runOnIdle { assertFalse(SubscriptionStore.get(context).notices("").first { it.galleryId==galleryId }.read) }
            }
        } finally { cleanup();if(previous==null) lock.clear() else lock.save(previous) }
    }
}
