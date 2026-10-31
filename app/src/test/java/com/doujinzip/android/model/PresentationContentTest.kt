package com.doujinzip.android.model

import org.junit.Assert.*
import org.junit.Test

class PresentationContentTest {
    @Test fun versionsDoNotChangeContentButEveryEditableFieldDoes() {
        val value = GalleryPresentation("hitomi:42", "Synthetic", LibraryMetadata())
        assertTrue(value.hasSameContent(value.copy(version=2, revision=9)))
        listOf(value.copy(title="Changed"), value.copy(metadata=LibraryMetadata(tags=listOf("tag"))),
            value.copy(webtoon=true), value.copy(coverHash="a".repeat(64))).forEach {
            assertFalse(value.hasSameContent(it))
        }
    }
}
