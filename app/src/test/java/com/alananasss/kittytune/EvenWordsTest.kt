package com.alananasss.kittytune

import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricWord
import com.alananasss.kittytune.ui.player.lyrics.withEvenWords
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** A line timed only as a whole is given words spread across it, to fill in smoothly (desktop PR #77 port). */
class EvenWordsTest {

    private fun line(text: String, start: Long, end: Long, words: List<LyricWord> = emptyList()) =
        LyricLine(text = text, startTime = start, endTime = end, words = words)

    @Test
    fun `words share the line in proportion to their letters`() {
        val timed = line("aa bbbb", 1_000L, 7_000L).withEvenWords(null)
        assertEquals(listOf("aa ", "bbbb"), timed.words.map { it.text })
        // Two letters of six take a third of the six seconds, the other four letters the rest.
        assertEquals(listOf(1_000L to 3_000L, 3_000L to 7_000L), timed.words.map { it.startTime to it.endTime })
    }

    @Test
    fun `the words put back together are the line, spaces included`() {
        val timed = line("Hello there, my friend", 0L, 8_000L).withEvenWords(null)
        assertEquals("Hello there, my friend", timed.words.joinToString("") { it.text })
    }

    @Test
    fun `a line with no end of its own ends where the next one starts, else after a few seconds`() {
        assertEquals(6_000L, line("one two", 2_000L, 0L).withEvenWords(6_000L).words.last().endTime)
        assertEquals(6_000L, line("one two", 2_000L, 0L).withEvenWords(null).words.last().endTime)
    }

    @Test
    fun `a line that already has words, or none to speak of, is left alone`() {
        val timed = line("a b", 0L, 1_000L, listOf(LyricWord("a ", 0L, 400L), LyricWord("b", 400L, 1_000L)))
        assertSame(timed, timed.withEvenWords(null))
        val blank = line("   ", 0L, 1_000L)
        assertSame(blank, blank.withEvenWords(null))
    }
}
