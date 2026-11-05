package com.doujinzip.android.network

import org.junit.Assert.*
import org.junit.Test

class AvailableLanguagesTest {
    @Test fun `preferred languages precede the remaining alphabetical catalog`() {
        val codes = listOf("french", "chinese", "german", "japanese", "english", "korean")
        val entries = codes.mapIndexed { index, code -> "\"$index\":\"$code\"" }.joinToString()
        val languages = parseAvailableLanguages("bitnumber_language={$entries};language_localname={};")
        assertEquals(listOf("korean", "japanese", "english", "chinese", "french", "german"), languages.map { it.code })
        assertEquals(listOf("english", "french"), parseAvailableLanguages("bitnumber_language={\"0\":\"french\",\"1\":\"english\"};language_localname={};").map { it.code })
    }

    @Test fun `all advertised languages use native labels and missing labels fall back`() {
        val languages = parseAvailableLanguages("""
            'use strict';
            var bitnumber_language = {"0":"korean","1":"khmer","2":"textless narrative"},
                language_localname = {"korean":"한국어","all":"(all)","unused":"unused"};
        """.trimIndent())
        assertEquals(setOf("korean", "khmer", "textless_narrative"), languages.map { it.code }.toSet())
        assertEquals("한국어", languages.single { it.code == "korean" }.label)
        assertEquals("Khmer", languages.single { it.code == "khmer" }.label)
        assertEquals("Textless narrative", languages.single { it.code == "textless_narrative" }.label)
    }

    @Test fun `catalog is neither hardcoded nor capped at autocomplete limit`() {
        val codes = ('a'..'z').map { "language_$it" }
        val entries = codes.mapIndexed { index, code -> "\"$index\":\"$code\"" }.joinToString()
        val languages = parseAvailableLanguages("var bitnumber_language = {$entries}, language_localname = {};")
        assertEquals(codes.toSet(), languages.map { it.code }.toSet())
    }

    @Test fun `invalid empty and missing catalogs are rejected`() {
        listOf(
            "<html>unavailable</html>",
            "var bitnumber_language = {}, language_localname = {};",
            "var bitnumber_language = {\"1\":\"korean OR tag:invalid\"}, language_localname = {};",
        ).forEach { script ->
            assertTrue(runCatching { parseAvailableLanguages(script) }.isFailure)
        }
    }
}
