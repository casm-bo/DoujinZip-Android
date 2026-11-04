package com.doujinzip.android.ui

import android.app.Application
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.R
import com.doujinzip.android.security.AppLockStore
import com.doujinzip.android.ui.theme.DoujinZipTheme
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class BackupExportUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun consumingExportRequestDoesNotCancelSuspendedPreparation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AppLockStore(context, "synthetic-backup-ui-lock")
        store.clear()
        val lock = AppLockViewModel(context.applicationContext as Application, store)
        val completed = AtomicBoolean(false)
        compose.setContent { DoujinZipTheme { CompositionLocalProvider(LocalAppLock provides lock) {
            BackupSettings(onPrepare = {
                delay(300)
                completed.set(true)
                throw IOException("Synthetic preparation failure")
            }, onOpenConnection = {})
        } } }
        compose.onNodeWithText(context.getString(R.string.backup_export)).performClick()
        compose.waitUntil(5_000) { completed.get() }
        compose.onAllNodesWithText(context.getString(R.string.backup_prepare_failed)).onFirst().assertIsDisplayed()
        assertTrue(completed.get())
    }

    @Test fun exportWritesPreparedSnapshotWithoutPreparingTwice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val lock = AppLockViewModel(context.applicationContext as Application, AppLockStore(context, "synthetic-backup-ui-lock"))
        val source = java.io.File.createTempFile("synthetic-backup-source", ".zip", context.cacheDir)
        val destination = java.io.File.createTempFile("synthetic-backup-target", ".zip", context.cacheDir)
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                dispatchResult(requestCode, Uri.fromFile(destination))
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        var preparations = 0
        try {
            source.writeText("synthetic backup bytes")
            compose.setContent { DoujinZipTheme {
                CompositionLocalProvider(LocalAppLock provides lock, LocalActivityResultRegistryOwner provides owner) {
                    BackupSettings(onPrepare = { preparations++; source }, onOpenConnection = {})
                }
            } }
            compose.onNodeWithText(context.getString(R.string.backup_export)).performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText(context.getString(R.string.backup_saved)).fetchSemanticsNodes().isNotEmpty() }
            assertEquals("synthetic backup bytes", destination.readText())
            assertEquals(1, preparations)
        } finally {
            source.delete()
            destination.delete()
        }
    }

}
