package com.doujinzip.android.ui

import com.doujinzip.android.model.LibrarySort
import com.doujinzip.android.model.LibraryBook
import com.doujinzip.android.model.LibraryMetadata
import org.junit.Assert.*
import org.junit.Test

class LibrarySortControlTest {
    @Test fun `episode candidates share library sorting without changing draft order`() {
        val books = listOf("B", "A").mapIndexed { index, title -> LibraryBook(title, title,
            locationUri="", locationName="", folderUri="", pages=emptyList(), modifiedAt=index.toLong(),
            metadata=LibraryMetadata(artists=listOf(title))) }
        for (sort in listOf(LibrarySort.NEWEST, LibrarySort.TITLE_ASC, LibrarySort.ARTIST_ASC))
            assertEquals(listOf("A", "B"), sortLibraryBooks(books, sort).map { it.id })
        for (sort in listOf(LibrarySort.OLDEST, LibrarySort.TITLE_DESC, LibrarySort.ARTIST_DESC))
            assertEquals(listOf("B", "A"), sortLibraryBooks(books, sort).map { it.id })
        assertEquals(listOf("B", "A"), books.map { it.id })
    }
    @Test fun `changing field preserves the chosen direction`() {
        assertEquals(LibrarySort.TITLE_DESC, LibrarySort.TITLE_ASC.withDirection(LibrarySort.NEWEST.isAscending()))
        assertEquals(LibrarySort.ARTIST_ASC, LibrarySort.ARTIST_ASC.withDirection(LibrarySort.OLDEST.isAscending()))
        assertEquals(LibrarySort.NEWEST, LibrarySort.OLDEST.withDirection(LibrarySort.ARTIST_DESC.isAscending()))
    }

    @Test fun `direction toggle is reversible for every field`() {
        LibrarySort.entries.filterNot { it == LibrarySort.RANDOM }.forEach { sort ->
            val reversed = sort.withDirection(!sort.isAscending())
            assertNotEquals(sort, reversed)
            assertEquals(sort, reversed.withDirection(!reversed.isAscending()))
        }
        assertEquals(LibrarySort.RANDOM, LibrarySort.RANDOM.withDirection(true))
    }

    @Test fun `random library order stays stable across input order and filtering`() {
        val books = (1..20).map { id -> LibraryBook("$id", "Fixture $id",
            locationUri = "", locationName = "", folderUri = "", pages = emptyList(), modifiedAt = 0) }
        val ordered = sortLibraryBooks(books, LibrarySort.RANDOM)
        assertEquals(books.toSet(), ordered.toSet())
        assertNotEquals(books, ordered)
        assertEquals(ordered, sortLibraryBooks(books.reversed(), LibrarySort.RANDOM))
        assertEquals(ordered.filter { it.id != "2" }, sortLibraryBooks(books.filter { it.id != "2" }, LibrarySort.RANDOM))
    }
}
