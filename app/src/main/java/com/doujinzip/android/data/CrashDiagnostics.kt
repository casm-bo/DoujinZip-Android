package com.doujinzip.android.data

import android.content.Context
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.os.Build
import android.util.AtomicFile
import com.doujinzip.android.BuildConfig
import com.doujinzip.android.security.BrowserPreferenceStore
import java.io.File

internal object CrashDiagnostics {
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler() ?: return
        val file = reportFile(context)
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val output = file.startWrite()
                try {
                    val report = "Crash version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        formatCrashSummary(error)
                    output.write(report.toByteArray(Charsets.UTF_8))
                    file.finishWrite(output)
                } catch (failure: Throwable) {
                    file.failWrite(output)
                }
            } catch (_: Throwable) {
                // Preserve Android's crash handling even when diagnostics cannot be written.
            } finally {
                previous.uncaughtException(thread, error)
            }
        }
    }

    fun latest(context: Context): String? = runCatching {
        reportFile(context).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrNull()

    fun recoverNavigation(context: Context): Boolean {
        val crashTime = reportFile(context).baseFile.lastModified()
        val exitTime = if (Build.VERSION.SDK_INT >= 30) runCatching {
            val lastUpdate = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            context.getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(context.packageName, 0, 0)
                .firstOrNull { it.processName == context.packageName }
                ?.takeIf { exit ->
                    exit.reason == ApplicationExitInfo.REASON_CRASH ||
                        exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                        exit.reason == ApplicationExitInfo.REASON_ANR ||
                        (exit.reason == ApplicationExitInfo.REASON_USER_REQUESTED && exit.timestamp > lastUpdate)
                }?.timestamp ?: 0L
        }.getOrDefault(0L) else 0L
        val interruptedAt = maxOf(crashTime, exitTime)
        if (interruptedAt == 0L) return false
        if (!BrowserPreferenceStore(context).recoverSearchAfterExit(interruptedAt)) return false
        val feed = context.getSharedPreferences("subscription_feed", Context.MODE_PRIVATE)
        feed.edit().apply { feed.all.keys.filter { it.startsWith("open-") }.forEach { remove(it) } }.apply()
        return true
    }

    private fun reportFile(context: Context) = AtomicFile(File(context.noBackupFilesDir, "last-crash.txt"))
}

internal fun formatCrashSummary(error: Throwable): String = buildString {
    // Exception messages, thread names and source paths can contain user data.
    generateSequence(error) { it.cause }.take(3).forEach { cause ->
        appendLine(cause.javaClass.name)
        val frames = cause.stackTrace
        val relevant = (frames.take(2) + frames.filter { it.className.startsWith("com.doujinzip.android.") }.take(3)).distinct()
        for (frame in relevant) {
            appendLine("  ${frame.className}.${frame.methodName}:${frame.lineNumber}")
        }
    }
}
