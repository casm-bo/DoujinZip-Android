package com.doujinzip.android.ui

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class AppLanguageResourcesTest {
    private val root = File("src/main/res")
    private fun strings(folder: String): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(root, "$folder/strings.xml")).getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    @Test fun `subscription sort has three translated registration options`() {
        listOf("values", "values-ko", "values-ja", "values-zh").forEach { folder ->
            assertEquals(folder, 3, strings(folder).getValue("saved_sort_labels").split("|").size)
        }
        assertEquals("최근 등록순|이름순|오래된 등록순", strings("values-ko").getValue("saved_sort_labels"))
    }

    @Test fun `every supported language has all messages with compatible format arguments`() {
        val baseline = strings("values")
        val placeholder = Regex("%\\d+\\\$[sd]")
        listOf("values-ko", "values-ja", "values-zh").forEach { folder ->
            val translated = strings(folder)
            assertEquals(folder, baseline.keys, translated.keys)
            baseline.forEach { (key, value) ->
                assertTrue("$folder/$key is empty", translated.getValue(key).isNotBlank())
                assertEquals("$folder/$key arguments", placeholder.findAll(value).map { it.value }.sorted().toList(),
                    placeholder.findAll(translated.getValue(key)).map { it.value }.sorted().toList())
                // Exercises format syntax, including escaped percent signs and reordered arguments.
                String.format(Locale.ROOT, translated.getValue(key), 1, 2, 3)
            }
        }
        assertEquals("Search", baseline.getValue("ui_search").trim('"'))
        assertEquals("검색", strings("values-ko").getValue("ui_search").trim('"'))
    }

    @Test fun `system language picker exposes the same four translated app locales`() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(root, "xml/locales_config.xml"))
        val nodes = document.getElementsByTagName("locale")
        assertEquals(setOf("en", "ko", "zh", "ja"), (0 until nodes.length).map {
            nodes.item(it).attributes.getNamedItem("android:name").nodeValue
        }.toSet())
    }
}
