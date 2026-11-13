package com.doujinzip.android.network

import org.junit.Assert.*
import org.junit.Test

class HitomiSearchOrderTest {
    @Test fun `popularity retains source rank across languages and exclusions`() {
        val korean = parseHitomiSearchOrder("artist:fixture language:korean -tag:blocked sort:daily sort:weekly")
        val english = parseHitomiSearchOrder("artist:fixture language:english -tag:blocked sort:weekly")
        assertEquals("week", korean.popularity)
        assertEquals("today", parseHitomiSearchOrder("sort:daily").popularity)
        assertNull(parseHitomiSearchOrder("sort:weekly sort:newest").popularity)
        assertEquals(listOf(7L, 12L), filterPopularityRanking(listOf(7L, 4L, 12L, 7L, 9L),
            listOf(korean.terms, english.terms), mapOf(
                "artist:fixture" to setOf(4L, 7L, 12L),
                "language:korean" to setOf(4L, 12L),
                "language:english" to setOf(7L, 9L),
                "-tag:blocked" to setOf(4L),
            )))
    }
    @Test fun `sort directives are removed from resource terms`() {
        val order = parseHitomiSearchOrder("language:korean sort:oldest -tag:guro")
        assertEquals(listOf("language:korean", "-tag:guro"), order.terms)
        assertEquals(listOf(1L, 3L, 8L), order.sortIds(listOf(8L, 1L, 3L)))
    }
    @Test fun `newest default and last directive win`() {
        assertEquals(listOf(8L, 3L, 1L), parseHitomiSearchOrder("artist:sample").sortIds(listOf(1L, 8L, 3L)))
        assertFalse(parseHitomiSearchOrder("sort:oldest sort:newest").oldest)
        assertTrue(parseHitomiSearchOrder("SORT:OLDEST").oldest)
    }
}
