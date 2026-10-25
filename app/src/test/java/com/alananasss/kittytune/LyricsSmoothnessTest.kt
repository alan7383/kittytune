package com.alananasss.kittytune

import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricWord
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import com.alananasss.kittytune.ui.player.lyrics.buildSyncedLyrics
import com.alananasss.kittytune.ui.player.slider.PlayheadClock
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSmoothnessTest {

    @Test
    fun testOverlappingWordsAreSanitizedToNonOverlapping() {
        // Line where words have overlapping timestamps in raw source
        val rawWords = listOf(
            LyricWord("Over", startTime = 1000L, endTime = 2000L),
            LyricWord("lapping", startTime = 1500L, endTime = 2500L), // starts before previous ends!
            LyricWord("words", startTime = 2200L, endTime = 3000L)    // starts before previous ends!
        )
        val line = LyricLine(
            text = "Overlapping words",
            startTime = 1000L,
            endTime = 3000L,
            words = rawWords
        )

        val synced = buildSyncedLyrics(listOf(line), isWordSynced = true)
        val mainLine = synced.lines[0] as KaraokeLine.MainKaraokeLine
        val syllables = mainLine.syllables

        assertEquals(3, syllables.size)

        // Verify strictly non-overlapping boundaries:
        for (i in 0 until syllables.size - 1) {
            val curr = syllables[i]
            val next = syllables[i + 1]
            assertTrue(
                "Syllable ${i + 1} start (${next.start}) must be >= Syllable $i end (${curr.end})",
                next.start >= curr.end
            )
            assertTrue(
                "Syllable $i duration must be positive",
                curr.end > curr.start
            )
        }
    }

    @Test
    fun testOutOfOrderWordsAreSortedChronologically() {
        // Words given out of order by the source
        val outOfOrderWords = listOf(
            LyricWord("three", startTime = 3000L, endTime = 4000L),
            LyricWord("one", startTime = 1000L, endTime = 2000L),
            LyricWord("two", startTime = 2000L, endTime = 3000L)
        )
        val line = LyricLine(
            text = "one two three",
            startTime = 1000L,
            endTime = 4000L,
            words = outOfOrderWords
        )

        val synced = buildSyncedLyrics(listOf(line), isWordSynced = true)
        val mainLine = synced.lines[0] as KaraokeLine.MainKaraokeLine
        val syllables = mainLine.syllables

        assertEquals(3, syllables.size)
        assertEquals("one ", syllables[0].content)
        assertEquals("two ", syllables[1].content)
        assertEquals("three", syllables[2].content)
        assertTrue(syllables[0].start <= syllables[1].start)
        assertTrue(syllables[1].start <= syllables[2].start)
    }

    @Test
    fun testLineEndCoversLastSyllableEnd() {
        // Source specifies a line endTime that is shorter than its last word
        val words = listOf(
            LyricWord("First", 1000L, 2000L),
            LyricWord("Last", 2000L, 5000L)
        )
        val line = LyricLine(
            text = "First Last",
            startTime = 1000L,
            endTime = 3000L, // declares 3000L, but last word ends at 5000L
            words = words
        )

        val synced = buildSyncedLyrics(listOf(line), isWordSynced = true)
        val mainLine = synced.lines[0] as KaraokeLine.MainKaraokeLine
        assertTrue(
            "Line end (${mainLine.end}) must be at least the end of the last word (5000)",
            mainLine.end >= 5000
        )
    }

    @Test
    fun testDuplicateTranslationDetection() {
        val original = "Hello darkness my old friend"
        val exactSame = "Hello darkness my old friend"
        val withPunctuation = "Hello darkness, my old friend."
        val withCasing = "hello darkness my old friend"
        val different = "Bonjour obscurité mon vieil ami"

        assertTrue(LyricsUtils.isDuplicateTranslation(original, exactSame))
        assertTrue(LyricsUtils.isDuplicateTranslation(original, withPunctuation))
        assertTrue(LyricsUtils.isDuplicateTranslation(original, withCasing))
        assertFalse(LyricsUtils.isDuplicateTranslation(original, different))
        assertFalse(LyricsUtils.isDuplicateTranslation(original, null))
        assertFalse(LyricsUtils.isDuplicateTranslation(null, different))
    }

    @Test
    fun testPlayheadClockMonotonicityDuringContinuousPlayback() {
        val clock = PlayheadClock()
        val key = "track1"
        var nowNanos = 1_000_000_000L

        // Initial report at 1000ms
        val p0 = clock.step(reported = 1000L, key = key, direct = false, running = true, now = nowNanos)
        assertEquals(1000L, p0)

        // Next frame 16ms later, reported still 1000ms (buffer delay)
        nowNanos += 16_000_000L
        val p1 = clock.step(reported = 1000L, key = key, direct = false, running = true, now = nowNanos)
        assertTrue("Position must advance forward during playback: p1=$p1 >= p0=$p0", p1 >= p0)

        // Next frame 16ms later, reported still 1000ms
        nowNanos += 16_000_000L
        val p2 = clock.step(reported = 1000L, key = key, direct = false, running = true, now = nowNanos)
        assertTrue("Position must advance forward: p2=$p2 >= p1=$p1", p2 >= p1)

        // Reported jumps to 1050ms
        nowNanos += 16_000_000L
        val p3 = clock.step(reported = 1050L, key = key, direct = false, running = true, now = nowNanos)
        assertTrue("Position must not step backward: p3=$p3 >= p2=$p2", p3 >= p2)
    }
}
