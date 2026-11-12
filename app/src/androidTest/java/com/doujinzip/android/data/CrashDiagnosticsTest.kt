package com.doujinzip.android.data

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class CrashDiagnosticsTest {
    @Test
    fun savesOnlyLatestCrashAndDelegatesToExistingHandler() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = Files.createTempDirectory(app.cacheDir.toPath(), "crash-diagnostics").toFile()
        val context = object : ContextWrapper(app) {
            override fun getNoBackupFilesDir() = directory
        }
        val original = Thread.getDefaultUncaughtExceptionHandler()
        try {
            assertNull(CrashDiagnostics.latest(context))
            var delegated: Throwable? = null
            Thread.setDefaultUncaughtExceptionHandler { _, error -> delegated = error }
            CrashDiagnostics.install(context)
            val handler = requireNotNull(Thread.getDefaultUncaughtExceptionHandler())
            handler.uncaughtException(Thread.currentThread(), UnsupportedOperationException())
            assertTrue(requireNotNull(CrashDiagnostics.latest(context)).contains("UnsupportedOperationException"))

            val latest = IllegalStateException("Synthetic private message")
            handler.uncaughtException(Thread.currentThread(), latest)
            assertSame(latest, delegated)
            val report = requireNotNull(CrashDiagnostics.latest(context))
            assertTrue(report.contains("IllegalStateException"))
            assertFalse(report.contains("Synthetic private message"))
            assertFalse(report.contains("UnsupportedOperationException"))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
            directory.deleteRecursively()
        }
    }
}
