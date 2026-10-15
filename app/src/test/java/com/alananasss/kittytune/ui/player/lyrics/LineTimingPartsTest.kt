package com.alananasss.kittytune.ui.player.lyrics

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Backing vocals and word-by-word lighting for lyrics timed by the line only (issue #66). */
class LineTimingPartsTest {

    @Test
    fun bracketsAreTheBackingVocal() {
        assertEquals("I'm falling" to "falling down", splitBackingVocals("I'm falling (falling down)"))
        assertEquals("say it again" to "again, oh", splitBackingVocals("(again) say it (oh) again"))
    }

    @Test
    fun aLineThatIsAllBracketsOrHasNoneStaysWhole() {
        assertEquals("(ooh, ooh)" to null, splitBackingVocals("(ooh, ooh)"))
        assertEquals("no brackets here" to null, splitBackingVocals("no brackets here"))
        assertEquals("empty () pair" to null, splitBackingVocals("empty () pair"))
    }

    @Test
    fun revealedWordsStartInOrderAndAllFinishFast() {
        val words = revealSyllables("one two three four five", start = 10_000, end = 14_000)
        assertEquals(5, words.size)
        assertEquals(listOf("one ", "two ", "three ", "four ", "five"), words.map { it.content })
        assertTrue(words.zipWithNext().all { (a, b) -> b.start > a.start })
        assertEquals(10_000, words.first().start)
        assertTrue("lit within a second: ${words.last().end}", words.last().end <= 10_000 + 1_000)
    }

    @Test
    fun aShortLineIsNeverRevealedPastItsEnd() {
        val words = revealSyllables("a b c d e f g h", start = 0, end = 200)
        assertTrue(words.all { it.start in 0 until 200 && it.end <= 200 && it.end > it.start })
    }

    @Test
    fun instantWordsAreLitAtTheStart() {
        val words = instantSyllables("right now", start = 500, end = 3_000)
        assertTrue(words.all { it.start == 500 && it.end == 501 })
    }

    @Test
    fun aBracketedLineGetsABackingLineAndAPlainOneDoesNot() {
        val lyrics = buildSyncedLyrics(
            lines = listOf(
                LyricLine(text = "I'm falling (falling down)", startTime = 1_000, endTime = 4_000),
                LyricLine(text = "nothing to split", startTime = 5_000, endTime = 8_000),
            ),
            isWordSynced = false,
            splitBacking = true,
        )
        val first = lyrics.lines[0] as KaraokeLine.MainKaraokeLine
        assertEquals("I'm falling", first.syllables.joinToString("") { it.content })
        assertEquals("falling down", first.accompanimentLines!!.single().syllables.joinToString("") { it.content })
        assertTrue(lyrics.lines[1] is SyncedLine)
    }

    @Test
    fun withTheOptionOffTheBracketsStayInTheLine() {
        val lyrics = buildSyncedLyrics(
            lines = listOf(LyricLine(text = "I'm falling (falling down)", startTime = 1_000, endTime = 4_000)),
            isWordSynced = false,
        )
        assertTrue(lyrics.lines.single() is SyncedLine)
    }

    @Test
    fun revealingTurnsEveryLineIntoTimedWords() {
        val lyrics = buildSyncedLyrics(
            lines = listOf(LyricLine(text = "light me up", startTime = 1_000, endTime = 4_000)),
            isWordSynced = false,
            revealWords = true,
        )
        val line = lyrics.lines.single() as KaraokeLine.MainKaraokeLine
        assertEquals(3, line.syllables.size)
        assertNull(line.accompanimentLines)
    }

    private fun words(vararg parts: Pair<String, Long>) =
        parts.map { (text, at) -> LyricWord(word = text, startTime = at, endTime = at + 300) }

    @Test
    fun wordTimedBracketsBecomeBackingGroupsInOrder() {
        val line = words("I'm " to 0L, "falling " to 300L, "(falling " to 600L, "down) " to 900L, "again " to 1200L, "(yeah)" to 1500L)
        val (main, groups) = splitBackingWords(line)
        assertEquals(listOf("I'm ", "falling ", "again "), main.map { it.text })
        assertEquals(listOf(listOf("falling", "down"), listOf("yeah")), groups.map { g -> g.map { it.text } })
        assertEquals(600L, groups.first().first().startTime)
    }

    @Test
    fun aWordTimedLineInBracketsOnlyStaysWhole() {
        val line = words("(ooh " to 0L, "ooh)" to 300L)
        val (main, groups) = splitBackingWords(line)
        assertEquals(line, main)
        assertTrue(groups.isEmpty())
    }

    @Test
    fun backingLineReadsWithCommas() {
        val line = LyricLine(
            text = "I'm falling (falling down) again (yeah)",
            startTime = 0,
            endTime = 2_000,
            words = words("I'm " to 0L, "falling " to 300L, "(falling " to 600L, "down) " to 900L, "again " to 1200L, "(yeah)" to 1500L),
        )
        val (main, backing) = separateBackingVocals(line)
        assertEquals("I'm falling again", main.text)
        assertEquals("falling down, yeah", backing!!.text)
    }
}
