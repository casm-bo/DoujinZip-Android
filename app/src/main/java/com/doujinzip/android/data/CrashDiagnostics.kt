package com.doujinzip.android.data

import android.content.Context
import android.util.AtomicFile
import com.doujinzip.android.BuildConfig
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
