package com.doujinzip.android.data

import com.doujinzip.android.model.LibraryMetadata
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ArchiveIndexCacheTest {
    @Test fun cachedIndexRequiresTheSameSourceVersionAndAnExistingCover() {
        val root = Files.createTempDirectory("archive-index-test").toFile()
        try {
            val cover = root.resolve("cover.jpg").apply { writeText("image") }
            val cache = ArchiveIndexCache(root.resolve("indexes"))
            val index = ArchiveIndex(listOf("1.jpg", "2.jpg"), ParsedInfoTxt("Title", "uuid", LibraryMetadata(tags = listOf("female:sample"))), cover.toURI().toString())
            cache.write("content://archive/1", 10, 100, index)
            assertEquals(index, cache.read("content://archive/1", 10, 100))
            assertNull(cache.read("content://archive/1", 11, 100))
            assertNull(cache.read("content://archive/1", 10, 101))
            assertNull(cache.read("content://archive/2", 10, 100))
            assertNull(cache.read("content://archive/1", 0, 100))
            val storedIndex = root.resolve("indexes").listFiles()!!.single()
            val current = storedIndex.readText()
            storedIndex.writeText(org.json.JSONObject(current).put("version", 1).toString())
            assertNull(cache.read("content://archive/1", 10, 100))
            storedIndex.writeText(current)
            cover.delete()
            assertNull(cache.read("content://archive/1", 10, 100))
        } finally { root.deleteRecursively() }
    }
}
