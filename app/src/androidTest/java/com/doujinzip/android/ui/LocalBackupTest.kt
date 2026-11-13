package com.doujinzip.android.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.doujinzip.android.data.*
import com.doujinzip.android.model.*
import com.doujinzip.android.security.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class LocalBackupTest {
    @Test fun desktopBackupDecisionIsSavedOnlyAfterRestoreAndDigestIgnoresCaptureTime() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        IsolatedContext(base).use { context ->
            val sync = DesktopBackupSync(context)
            val profile = DesktopProfile("fixture", "Fixture", "http://127.0.0.1:1", "fixture")
            val store = LocalBackupStore(context)
            val archive = store.capture(emptySet(), null, "")
            val retimed = archive.copy(data = org.json.JSONObject(archive.data.toString()).put("createdAt", 42))
            assertEquals(backupContentDigest(archive), backupContentDigest(retimed))
            assertNotEquals(backupContentDigest(archive), backupContentDigest(retimed.copy(
                data = org.json.JSONObject(retimed.data.toString()).put("locale", "ko"))))
            assertTrue(sync.enabled)
            sync.enabled = false
            assertFalse(DesktopBackupSync(context).enabled)
            store.stage(archive, sync.decisionKey(profile), "fixture-version")
            assertNull(sync.accepted(profile))
            store.applyPending()
            assertEquals("fixture-version", sync.accepted(profile))
        }
    }
    private class IsolatedContext(base: Context) : ContextWrapper(base), AutoCloseable {
        private val prefix = "backup-test-${System.nanoTime()}-"
        private val directory = File(base.cacheDir, prefix).also { it.mkdirs() }
        private val names = mutableSetOf<String>()
        private val databases = mutableSetOf<String>()
        var failNextCommit = false
        var failCommitCount = 0
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory
        override fun getCacheDir(): File = directory
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            names += name
            val prefs = super.getSharedPreferences(prefix + name, mode)
            if ((!failNextCommit && failCommitCount == 0) || name != "appearance") return prefs
            return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
                if (method.name != "edit") method.invoke(prefs, *(args ?: emptyArray()))
                else {
                    val editor = prefs.edit()
                    lateinit var proxy: Any
                    proxy = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, edit, parameters ->
                        if (edit.name == "commit" && (failNextCommit || failCommitCount > 0)) { failNextCommit = false; if (failCommitCount > 0) failCommitCount--; false }
                        else edit.invoke(editor, *(parameters ?: emptyArray())).let { if (it is SharedPreferences.Editor) proxy else it }
                    }
                    proxy
                }
            } as SharedPreferences
        }
        override fun getDatabasePath(name: String): File = super.getDatabasePath(prefix + name)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase {
            databases += name; return super.openOrCreateDatabase(prefix + name, mode, factory)
        }
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?): SQLiteDatabase {
            databases += name; return super.openOrCreateDatabase(prefix + name, mode, factory, handler)
        }
        override fun close() {
            names.forEach { super.getSharedPreferences(prefix + it, MODE_PRIVATE).edit().clear().commit(); super.deleteSharedPreferences(prefix + it) }
            databases.forEach { super.deleteDatabase(prefix + it) }
            directory.deleteRecursively()
        }
    }

    @Test fun libraryScopeSurvivesUnpairingAndKeepsDifferentDatabasesSeparate() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        IsolatedContext(base).use { context ->
            assertEquals("old-device", BrowserPreferenceStore(context).rememberLibraryScope("library-a", "old-device"))
            assertEquals("old-device", BrowserPreferenceStore(context).rememberLibraryScope("library-a", "new-device"))
            assertEquals("library-b", BrowserPreferenceStore(context).rememberLibraryScope("library-b", "library-b"))
        }
    }

    @Test fun portableBackupRestoresSettingsTabsHistoryAndGalleryIdentityWithoutCredentials() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        IsolatedContext(base).use { source -> IsolatedContext(base).use { target ->
            val browser = BrowserPreferenceStore(source)
            val tab = BrowserTab(history = listOf(BrowserPage.Search(query = "artist:fixture", scrollIndex = 7, scrollOffset = 3, scrollPage = 2, resultSessionId = "synthetic-session")))
            val grouped = BrowserWorkspace(listOf(tab), tab.id).createGroup(setOf(tab.id), "Fixture group")
            val workspace = grouped.archiveGroup(grouped.groups.single().id)
            browser.saveBrowserWorkspace(workspace)
            browser.saveLibraryLocations(listOf(StorageLocation("content://com.android.externalstorage.documents/tree/primary%3AFixture", "Fixture")))
            val id = LibraryIdentityStore(source).use { store -> store.resolve("folder:fixture", "content://com.android.externalstorage.documents/tree/primary%3AFixture/document/primary%3AFixture%2FGallery", ParsedInfoTxt(title = "Fixture"), 1).syncId }
            val bookId = LibraryIdentityStore.stableFileId(id)
            LibraryPreferenceStore(source).apply {
                saveFavoriteIds(setOf(bookId, "sync:server-only")); saveBookProgress(bookId, 4)
                saveReadFilter(LibraryReadFilter.UNREAD); saveFavoritesOnly(true); saveSearchGridColumns(4)
            }
            AppearancePreferenceStore(source).saveBorder(GalleryBorderKind.NORMAL, GalleryBorderStyle(1f, 0xFF123456))
            RecentHistoryStore(source).save(listOf(RecentHistoryEntry("fixture", "Fixture", 10, HistorySource.DEVICE, bookId = bookId, thumbnail = "content://private/fixture")))
            RecentHistoryStore(source).setRecording(false)
            SubscriptionStore(source).use { it.save("", SearchSubscription(name = "Fixture subscription", query = "artist:fixture")) }
            val bitmap = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
            val image = java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            bitmap.recycle()
            val coverHash = BackupFormat.digest(image)
            PresentationCovers(source).put(coverHash, image)
            LibraryIdentityStore(source).use { PresentationStore(it).saveLocal(GalleryPresentation("sync:$id", "Edited fixture", LibraryMetadata(), coverHash = coverHash)) }
            source.getSharedPreferences("app_lock", Context.MODE_PRIVATE).edit().putString("credential", "synthetic-lock").commit()
            source.getSharedPreferences("secure_companion_profiles", Context.MODE_PRIVATE).edit().putString("ciphertext", "synthetic-credential").commit()
            val store = LocalBackupStore(source)
            val archive = store.capture(setOf("sync:server-only"), null, "ko")
            assertFalse(archive.data.toString().contains("synthetic-lock"))
            assertFalse(archive.data.toString().contains("synthetic-credential"))
            assertFalse(archive.data.toString().contains("content://"))
            assertFalse(archive.data.getJSONObject("preferences").toString().contains("server-only"))
            val targetStore = LocalBackupStore(target)
            val malicious = org.json.JSONObject(archive.data.toString())
            malicious.getJSONArray("identities").getJSONObject(0).put("sync_id", "../../escape")
            assertTrue(runCatching { targetStore.stage(BackupArchive(malicious, archive.covers)) }.isFailure)
            targetStore.stage(archive); targetStore.applyPending()
            assertEquals("success", target.getSharedPreferences("backup_runtime", Context.MODE_PRIVATE).getString("result", null))
            assertEquals("Fixture group", BrowserPreferenceStore(target).loadBrowserWorkspace().groups.single().name)
            val group = BrowserPreferenceStore(target).loadBrowserWorkspace().groups.single()
            assertTrue(group.isArchived)
            val search = group.archivedTabs.single().currentPage as BrowserPage.Search
            assertEquals(7, search.scrollIndex); assertEquals(3, search.scrollOffset); assertEquals(2, search.scrollPage)
            assertNull(search.resultSessionId)
            assertEquals(1f, AppearancePreferenceStore(target).loadBorders().normal.widthDp)
            assertEquals(4, LibraryPreferenceStore(target).loadProgress()[bookId])
            assertEquals(LibraryReadFilter.UNREAD, LibraryPreferenceStore(target).loadReadFilter())
            assertEquals(4, LibraryPreferenceStore(target).loadSearchGridColumns())
            assertFalse(RecentHistoryStore(target).isRecording())
            assertEquals("Fixture", RecentHistoryStore(target).load().single().title)
            assertNull(RecentHistoryStore(target).load().single().thumbnail)
            assertTrue(BrowserPreferenceStore(target).loadLibraryLocations().isEmpty())
            assertArrayEquals(image, PresentationCovers(target).bytes(coverHash))
            assertFalse(target.getSharedPreferences("app_lock", Context.MODE_PRIVATE).contains("credential"))
            LibraryIdentityStore(target).use { identity ->
                identity.resolve("folder:new-location", "content://new-location", ParsedInfoTxt(), 1)
                val restored = identity.resolve("folder:new-location", "content://new-location", ParsedInfoTxt(), 2, relativePath = "Gallery")
                assertEquals(id, restored.syncId)
                assertEquals("Edited fixture", PresentationStore(identity).get("sync:$id")?.value?.title)
            }
            SubscriptionStore(target).use { assertEquals("Fixture subscription", it.records("").single().value.name) }
        } }
    }

    @Test fun backupCombinesOnlyIdenticalPresentationSnapshots() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        IsolatedContext(base).use { context ->
            val value = GalleryPresentation("hitomi:42", "Fixture", LibraryMetadata(tags = listOf("fixture")))
            LibraryIdentityStore(context).use { identity ->
                PresentationStore(identity, "previous-pc").receive(value.copy(version = 1))
                PresentationStore(identity, "another-pc").receive(value.copy(version = 3, revision = 2))
                val backup = LocalBackupStore(context)
                val archive = backup.capture(emptySet(), "current-pc", "ko")
                val presentations = archive.data.getJSONArray("presentations")
                assertEquals(1, presentations.length())
                assertEquals(value, presentationFromJson(presentations.getJSONObject(0)))

                PresentationStore(identity, "another-pc").receive(value.copy(title = "Different", version = 4))
                val failure = assertThrows(IllegalArgumentException::class.java) {
                    backup.capture(emptySet(), "current-pc", "ko")
                }
                assertEquals("Conflicting local metadata from multiple PCs", failure.message)
            }
        }
    }

    @Test fun backupCombinesIdenticalSubscriptionsFromPreviousProfiles() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        IsolatedContext(base).use { context ->
            val value = SearchSubscription(id = "fixture", name = "Fixture", query = "artist:fixture")
            SubscriptionStore(context).use { store ->
                store.receive("previous-pc", value.copy(version = 1))
                store.receive("another-pc", value.copy(version = 2))
                val backup = LocalBackupStore(context)
                val archive = backup.capture(emptySet(), "current-pc", "ko")
                val subscriptions = archive.data.getJSONArray("subscriptions")
                assertEquals(1, subscriptions.length())
                assertEquals(value, SearchSubscription.fromJson(subscriptions.getJSONObject(0)))

                store.receive("another-pc", value.copy(enabled = true, version = 3))
                val failure = assertThrows(IllegalArgumentException::class.java) {
                    backup.capture(emptySet(), "current-pc", "ko")
                }
                assertEquals("Duplicate local subscription", failure.message)
            }
        }
    }

    @Test fun backupSkipsOnlyMatchingDeletedSubscriptionConflicts() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        IsolatedContext(base).use { context ->
            val scope = "inactive-fixture"
            val phone = SearchSubscription(id = "fixture", name = "Fixture", query = "artist:fixture", deleted = true)
            SubscriptionStore(context).use { store ->
                store.save(scope, phone)
                store.writableDatabase.execSQL(
                    "UPDATE subscriptions SET conflict=? WHERE scope=? AND id=?",
                    arrayOf(phone.copy(version = 2).json().toString(), scope, phone.id),
                )
                val backup = LocalBackupStore(context)
                val archive = backup.capture(emptySet(), "active-fixture", "ko")
                assertEquals(0, archive.data.getJSONArray("subscriptions").length())
                assertNotNull(store.records(scope).single().conflict)
                store.reconcileMatchingConflicts()
                assertEquals(0, backup.capture(emptySet(), "active-fixture", "ko").data.getJSONArray("subscriptions").length())

                for (remote in listOf(phone.copy(deleted = false), phone.copy(id = "another"), phone.copy(query = "artist:another"))) {
                    store.conflict(scope, phone.id, remote.copy(version = 2))
                    val failure = assertThrows(IllegalArgumentException::class.java) {
                        backup.capture(emptySet(), "active-fixture", "ko")
                    }
                    assertEquals("Resolve subscription conflicts before backup", failure.message)
                }
            }
        }
    }

    @Test fun failedPreferenceCommitRollsBackEarlierDatabaseAndSettingChanges() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        IsolatedContext(base).use { source -> IsolatedContext(base).use { target ->
            AppearancePreferenceStore(source).saveFontSize(24f)
            LibraryIdentityStore(source).use { PresentationStore(it).saveLocal(GalleryPresentation("hitomi:123", "Incoming", LibraryMetadata())) }
            val archive = LocalBackupStore(source).capture(emptySet(), null, "")
            AppearancePreferenceStore(target).saveFontSize(16f)
            LibraryIdentityStore(target).use { PresentationStore(it).saveLocal(GalleryPresentation("hitomi:123", "Previous", LibraryMetadata())) }
            target.getSharedPreferences("app_lock", Context.MODE_PRIVATE).edit().putString("credential", "synthetic-existing-lock").commit()
            LocalBackupStore(target).apply {
                stage(archive); target.failNextCommit = true; applyPending()
            }
            assertEquals("failed", target.getSharedPreferences("backup_runtime", Context.MODE_PRIVATE).getString("result", null))
            assertEquals(16f, AppearancePreferenceStore(target).loadFontSize())
            LibraryIdentityStore(target).use { assertEquals("Previous", PresentationStore(it).get("hitomi:123")?.value?.title) }
            assertEquals("synthetic-existing-lock", target.getSharedPreferences("app_lock", Context.MODE_PRIVATE).getString("credential", null))
            // Interrupt rollback itself, then recover from the durable journal on the next launch.
            val store = LocalBackupStore(target)
            store.stage(archive); target.failCommitCount = 2
            assertTrue(runCatching { store.applyPending() }.isFailure)
            store.applyPending()
            assertEquals(16f, AppearancePreferenceStore(target).loadFontSize())
            LibraryIdentityStore(target).use { assertEquals("Previous", PresentationStore(it).get("hitomi:123")?.value?.title) }
        } }
    }
}
