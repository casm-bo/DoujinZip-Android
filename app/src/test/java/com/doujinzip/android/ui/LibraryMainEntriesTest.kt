package com.doujinzip.android.ui

import com.doujinzip.android.model.*
import org.junit.Assert.*
import org.junit.Test

class LibraryMainEntriesTest {
    private val books = (1..3).map { LibraryBook("$it", "Book $it", locationUri = "device", locationName = "Device", folderUri = "$it", pages = emptyList(), modifiedAt = it.toLong()) }
    @Test fun disabledGroupingShowsEveryEpisodeAsAnIndividualBook() {
        val assignments = books.associate { it.id to CustomSeriesAssignment("Series", it.id.toInt()) }
        val entries = libraryMainEntries(books, assignments, LibrarySort.NEWEST, groupSeries = false)
        assertTrue(entries.all { it is LibraryMainEntry.Book })
        assertEquals(listOf("3", "2", "1"), entries.map { (it as LibraryMainEntry.Book).book.id })
    }
    @Test fun allShowsGroupsAndUnassignedWithoutDuplicatesAndKeepsChapterOrder() {
        val assignments = mapOf("1" to CustomSeriesAssignment("Series", 1), "3" to CustomSeriesAssignment("Series", 0))
        val entries = libraryMainEntries(books, assignments, LibrarySort.NEWEST)
        assertEquals(2, entries.size)
        assertEquals(listOf("3", "1"), (entries.first() as LibraryMainEntry.Series).books.map { it.id })
        assertEquals("2", (entries.last() as LibraryMainEntry.Book).book.id)
        val reassigned = libraryMainEntries(books, assignments - "1", LibrarySort.NEWEST)
        assertEquals(3, reassigned.size)
        val excluded = visibleLibraryBooks(MainUiState(libraryBooks = books, customSeriesByBookId = assignments, libraryExcludeSeries = true))
        assertEquals(listOf("2"), libraryMainEntries(excluded, assignments, LibrarySort.NEWEST).map { (it as LibraryMainEntry.Book).book.id })
    }
    @Test fun emptyAllSingleAllSeriesAndFilteredGroupsRemainValid() {
        assertTrue(libraryMainEntries(emptyList(), emptyMap(), LibrarySort.NEWEST).isEmpty())
        assertEquals(3, libraryMainEntries(books, emptyMap(), LibrarySort.TITLE_ASC).size)
        val assigned = books.associate { it.id to CustomSeriesAssignment("Only series", it.id.toInt()) }
        assertEquals(1, libraryMainEntries(books, assigned, LibrarySort.TITLE_ASC).size)
        assertEquals(listOf("2"), (libraryMainEntries(books.take(2).drop(1), assigned, LibrarySort.OLDEST).single() as LibraryMainEntry.Series).books.map { it.id })
    }
}
