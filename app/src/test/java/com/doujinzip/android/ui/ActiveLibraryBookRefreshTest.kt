package com.doujinzip.android.ui

import com.doujinzip.android.model.LibraryBook
import com.doujinzip.android.model.LibraryPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveLibraryBookRefreshTest {
    @Test
    fun refreshPreservesPreparedArchivePagesWhenSourceIsUnchanged() {
        val prepared = archiveBook(
            modifiedAt = 100L,
            pages = listOf(
                LibraryPage("file:///cache/001.jpg", "001.jpg", ARCHIVE_URI, "001.jpg"),
                LibraryPage("file:///cache/002.jpg", "002.jpg", ARCHIVE_URI, "002.jpg"),
            ),
        )
        val refreshed = archiveBook(
            modifiedAt = 100L,
            pages = listOf(
                LibraryPage("file:///cover.jpg", "001.jpg", ARCHIVE_URI, "001.jpg"),
                LibraryPage("", "002.jpg", ARCHIVE_URI, "002.jpg"),
            ),
        )

        val result = refreshed.withPreservedReaderPages(prepared)

        assertTrue(result.hasResolvedReaderPages())
        assertEquals(prepared.pages, result.pages)
    }

    @Test
    fun refreshPreservesPreparedDesktopPagesWhenSourceIsUnchanged() {
        val prepared = cloudBook(
            modifiedAt = 100L,
            pages = listOf(
                LibraryPage("http://desktop/pages/1", "1"),
                LibraryPage("http://desktop/pages/2", "2"),
            ),
        )
        val refreshed = cloudBook(
            modifiedAt = 100L,
            pages = listOf(
                LibraryPage("http://desktop/cover", "1"),
                LibraryPage("", "2"),
            ),
        )

        val result = refreshed.withPreservedReaderPages(prepared.copy(coverUriOverride = "http://desktop/stale-cover"))
        assertEquals(prepared.pages, result.pages)
        assertEquals(refreshed.coverUri, result.coverUri)
    }

    @Test
    fun changedSourceDoesNotReuseStalePreparedPages() {
        val prepared = archiveBook(
            modifiedAt = 100L,
            pages = listOf(
                LibraryPage("file:///cache/001.jpg", "001.jpg", ARCHIVE_URI, "001.jpg"),
                LibraryPage("file:///cache/002.jpg", "002.jpg", ARCHIVE_URI, "002.jpg"),
            ),
        )
        val refreshed = archiveBook(
            modifiedAt = 200L,
            pages = listOf(
                LibraryPage("file:///cover.jpg", "001.jpg", ARCHIVE_URI, "001.jpg"),
                LibraryPage("", "002.jpg", ARCHIVE_URI, "002.jpg"),
            ),
        )

        val result = refreshed.withPreservedReaderPages(prepared)

        assertFalse(result.hasResolvedReaderPages())
        assertEquals(refreshed.pages, result.pages)
    }

    @Test
    fun changedContentVersionInvalidatesPagesEvenWhenCountAndTimestampMatch() {
        val pages = listOf(LibraryPage("http://desktop/page/0?v=old", "1"), LibraryPage("", "2"))
        val prepared = cloudBook(100L, pages.mapIndexed { index, page -> page.copy(uri = "http://desktop/page/$index?v=old") })
            .copy(contentVersion = "old")
        val refreshed = cloudBook(100L, pages).copy(contentVersion = "new")
        assertEquals(refreshed.pages, refreshed.withPreservedReaderPages(prepared).pages)
        assertFalse(refreshed.withPreservedReaderPages(prepared).hasResolvedReaderPages())
        assertFalse(refreshed.withPreservedReaderPages(prepared.copy(contentVersion = null)).hasResolvedReaderPages())
    }

    private fun archiveBook(modifiedAt: Long, pages: List<LibraryPage>) = LibraryBook(
        id = "archive-book",
        title = "Archive",
        locationUri = "content://library",
        locationName = "Library",
        folderUri = ARCHIVE_URI,
        pages = pages,
        modifiedAt = modifiedAt,
    )

    private fun cloudBook(modifiedAt: Long, pages: List<LibraryPage>) = LibraryBook(
        id = "cloud-book",
        title = "Cloud",
        locationUri = "desktop:library",
        locationName = "Desktop",
        folderUri = "C:/library/book.zip",
        pages = pages,
        modifiedAt = modifiedAt,
        isCloud = true,
        remoteBookId = 42L,
        cloudToken = "token",
    )

    private companion object {
        const val ARCHIVE_URI = "content://library/book.zip"
    }
}
