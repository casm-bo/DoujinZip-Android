package com.doujinzip.android.ui

import com.doujinzip.android.model.ReaderImageSource
import com.doujinzip.android.model.ReaderPosition
import com.doujinzip.android.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.doujinzip.android.model.LibraryBook
import com.doujinzip.android.model.ViewerPreferences

@Composable
fun LocalReaderScreen(
    sessionKey: String,
    book: LibraryBook?,
    initialPage: Int,
    initialOffset: Int,
    isLoading: Boolean,
    error: String?,
    favorite: Boolean,
    preferences: ViewerPreferences,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onProgress: (ReaderPosition) -> Unit,
    nextBookTitle: String?,
    onOpenNextBook: () -> Unit,
    onPreferencesChange: (ViewerPreferences) -> Unit,
    isSeries: Boolean = false,
    previousBookTitle: String? = null,
    onOpenPreviousBook: (() -> Unit)? = null,
    onReadingModeResolved: (com.doujinzip.android.model.ViewerReadingMode) -> Unit = {},
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    HideSystemBars()
    if (!book.hasReadablePages()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isLoading) {
                    CircularProgressIndicator()
                    Text(strings[R.string.ui_preparing_pages], color = Color.White, modifier = Modifier.padding(top = 12.dp))
                } else {
                    Text(error ?: strings[R.string.ui_could_not_open_book], color = Color.White)
                }
                Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) { Text(strings[R.string.ui_return]) }
            }
        }
        return
    }

    val readableBook = requireNotNull(book)
    val context = LocalContext.current
    val pages = remember(readableBook.id, readableBook.pages, readableBook.cloudToken) {
        readableBook.pages.mapIndexed { index, page ->
            ReaderPageModel(
                key = "$index:${page.uri}",
                source = ReaderImageSource(page.uri, readableBook.cloudToken?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()),
                model = libraryImageRequest(context, page.uri, readableBook.cloudToken),
            )
        }
    }
    UnifiedReader(
        readerKey = "$sessionKey:${readableBook.contentVersion ?: readableBook.modifiedAt}",
        workKey = readableBook.metadata.hitomiId?.let { "gallery:$it" } ?: readableBook.syncId?.let { "sync:$it" } ?: "book:${readableBook.id}",
        initialOffset = initialOffset,
        title = readableBook.title,
        pages = pages,
        initialPage = initialPage,
        preferences = preferences,
        onBack = onBack,
        onProgress = onProgress,
        onPreferencesChange = onPreferencesChange,
        onReadingModeResolved = onReadingModeResolved,
        favorite = favorite,
        onToggleFavorite = onToggleFavorite,
        isSeries = isSeries,
        previousBookTitle = previousBookTitle,
        onOpenPreviousBook = onOpenPreviousBook,
        nextBookTitle = nextBookTitle,
        onOpenNextBook = onOpenNextBook,
    )
}

internal fun LibraryBook?.hasReadablePages(): Boolean =
    this != null && pages.isNotEmpty() && pages.all { it.uri.isNotBlank() }
