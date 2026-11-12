package com.doujinzip.android.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import com.doujinzip.android.BuildConfig
import com.doujinzip.android.data.CrashDiagnostics

internal fun openGitHubIssue(context: Context, state: MainUiState) {
    val body = """
        |## 문제 설명
        |
        |## 재현 방법
        |1.
        |
        |## 진단 정보
        |```text
        |${buildDiagnosticReport(context, state)}
        |```
    """.trimMargin()
    val uri = Uri.parse("https://github.com/casm-bo/doujin-menu-android/issues/new")
        .buildUpon()
        .appendQueryParameter("title", "[Bug] ")
        .appendQueryParameter("body", body)
        .build()
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}

private fun buildDiagnosticReport(context: Context, state: MainUiState): String {
    val orientation = when (context.resources.configuration.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> "landscape"
        Configuration.ORIENTATION_PORTRAIT -> "portrait"
        else -> "unknown"
    }
    return buildDiagnosticReport(
        state = state,
        appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        androidVersion = "${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}",
        device = "${Build.MANUFACTURER} ${Build.MODEL}",
        orientation = orientation,
        latestCrash = CrashDiagnostics.latest(context),
    )
}

internal fun buildDiagnosticReport(
    state: MainUiState,
    appVersion: String,
    androidVersion: String,
    device: String,
    orientation: String,
    latestCrash: String? = null,
): String = buildString {
    appendLine("App: $appVersion")
    appendLine("Android: $androidVersion")
    appendLine("Device: $device")
    appendLine("Orientation: $orientation")
    appendLine("Connection: ${state.desktopConnectionState}")
    appendLine("Saved desktops: ${state.profiles.size}; selected: ${state.selectedProfileId != null}")
    appendLine("Library books: ${state.libraryBooks.size}; locations: ${state.libraryLocations.size}")
    appendLine("Sync pending: ${state.librarySyncPendingCount}; syncing: ${state.isLibrarySyncing}")
    appendLine("Downloads: ${state.downloadQueue.size}")
    appendLine(
        "Errors present: library=${state.libraryScanError != null}, " +
            "sync=${state.librarySyncError != null}, reader=${state.readerErrorsByGalleryId.isNotEmpty()}, " +
            "download=${state.downloadQueueError != null}",
    )
    appendLine("Viewer scale: ${state.viewerPreferences.scale}")
    appendLine("Reading direction: ${state.viewerPreferences.readingDirection}")
    appendLine("Page turn: ${state.viewerPreferences.pageTurnMode}")
    if (latestCrash != null) {
        appendLine("Last crash:")
        appendLine(latestCrash.trimEnd())
    }
}
