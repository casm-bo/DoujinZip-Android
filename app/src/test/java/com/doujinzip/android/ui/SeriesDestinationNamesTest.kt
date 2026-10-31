package com.doujinzip.android.ui

import com.doujinzip.android.model.*
import org.junit.Assert.*
import org.junit.Test

class SeriesDestinationNamesTest {
    private val books = listOf(
        LibraryBook("a", "Episode title", locationUri="", locationName="", folderUri="", pages=emptyList(), modifiedAt=1,
            metadata=LibraryMetadata(artists=listOf("Alice"), tags=listOf("color"))),
        LibraryBook("b", "Other episode", locationUri="", locationName="", folderUri="", pages=emptyList(), modifiedAt=2,
            metadata=LibraryMetadata(artists=listOf("Bob"))))
    private val state = MainUiState(libraryBooks=books, customSeriesByBookId=mapOf(
        "a" to CustomSeriesAssignment("Alpha series", 0), "b" to CustomSeriesAssignment("Beta series", 0)))

    @Test fun searchMatchesOnlySeriesTitleAndArtistsIgnoringCase() {
        assertEquals(listOf("Alpha series"), seriesDestinationNames(state, "ALPHA alice", LibrarySort.NEWEST))
        assertEquals(listOf("Beta series"), seriesDestinationNames(state, "bob", LibrarySort.NEWEST))
        assertTrue(seriesDestinationNames(state, "Episode", LibrarySort.NEWEST).isEmpty())
        assertTrue(seriesDestinationNames(state, "color", LibrarySort.NEWEST).isEmpty())
    }

    @Test fun sortingUsesNewestByDefaultAndSupportsEveryLibraryField() {
        for (sort in listOf(LibrarySort.NEWEST, LibrarySort.TITLE_DESC, LibrarySort.ARTIST_DESC))
            assertEquals(listOf("Beta series", "Alpha series"), seriesDestinationNames(state, "", sort))
        for (sort in listOf(LibrarySort.OLDEST, LibrarySort.TITLE_ASC, LibrarySort.ARTIST_ASC))
            assertEquals(listOf("Alpha series", "Beta series"), seriesDestinationNames(state, "", sort))
    }
}
