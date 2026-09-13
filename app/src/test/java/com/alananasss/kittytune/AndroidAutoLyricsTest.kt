package com.alananasss.kittytune

import com.alananasss.kittytune.data.auto.AndroidAutoLyrics
import com.alananasss.kittytune.data.auto.AutomotiveLyricSegmenter
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidAutoLyricsTest {

    private val sampleLines = listOf(
        LyricLine(text = "First line of song", startTime = 1_000L, endTime = 2_000L),
        LyricLine(text = "Second line of song", startTime = 2_000L, endTime = 3_500L),
        LyricLine(text = "", startTime = 3_500L, endTime = 5_000L, isInstrumental = true),
        LyricLine(text = "Third line after break", startTime = 5_000L, endTime = 7_000L)
    )

    @Test
    fun returnsNullBeforeFirstTimestamp() {
        assertNull(AndroidAutoLyrics.currentLine(sampleLines, 500L))
    }

    @Test
    fun followsCurrentLineAcrossPlayback() {
        val firstLine = AndroidAutoLyrics.currentLine(sampleLines, 1_200L)
        assertNotNull(firstLine)
        assertEquals(0, firstLine?.index)
        assertEquals("First line of song", firstLine?.text)

        val secondLine = AndroidAutoLyrics.currentLine(sampleLines, 2_500L)
        assertNotNull(secondLine)
        assertEquals(1, secondLine?.index)
        assertEquals("Second line of song", secondLine?.text)
    }

    @Test
    fun returnsNullDuringInstrumentalBreak() {
        assertNull(AndroidAutoLyrics.currentLine(sampleLines, 4_000L))
    }

    @Test
    fun followsLineAfterInstrumentalBreak() {
        val thirdLine = AndroidAutoLyrics.currentLine(sampleLines, 5_500L)
        assertNotNull(thirdLine)
        assertEquals(3, thirdLine?.index)
        assertEquals("Third line after break", thirdLine?.text)
    }

    @Test
    fun appliesStoredLyricsOffset() {
        // At 1_600ms + 500ms offset = 2_100ms, which falls into the second line
        val lineWithOffset = AndroidAutoLyrics.currentLine(sampleLines, 1_600L, offsetMs = 500L)
        assertNotNull(lineWithOffset)
        assertEquals(1, lineWithOffset?.index)
        assertEquals("Second line of song", lineWithOffset?.text)
    }

    @Test
    fun segmentsLongLineWithoutLosingContent() {
        val longText = "This is a very long lyric line that will be displayed across multiple balanced segments on the car screen"
        val lines = listOf(
            LyricLine(text = longText, startTime = 0L, endTime = 8_000L),
            LyricLine(text = "Next line", startTime = 8_000L, endTime = 12_000L)
        )

        val firstSegment = AndroidAutoLyrics.currentLine(lines, 0L)
        assertNotNull(firstSegment)
        assertEquals(0, firstSegment?.segmentIndex)
        assertEquals(longText, firstSegment?.segments?.joinToString(" ") { it.text })
        assertEquals(8_000L, firstSegment?.windowEndMs)

        // Mid-way through line should advance to next segment
        val midPosition = firstSegment!!.segments[1].startTimeMs + 10L
        val secondSegment = AndroidAutoLyrics.currentLine(lines, midPosition)
        assertEquals(1, secondSegment?.segmentIndex)
    }

    @Test
    fun usesTrackDurationForLastLine() {
        val lastLine = listOf(LyricLine(text = "Final words echoing away in the distance", startTime = 2_000L, endTime = 10_000L))

        val resultWithDuration = AndroidAutoLyrics.currentLine(lastLine, 2_000L, trackDurationMs = 12_000L)
        assertEquals(10_000L, resultWithDuration?.windowEndMs)

        val fallbackLine = listOf(LyricLine(text = "Final words", startTime = 2_000L, endTime = 0L))
        val resultFallback = AndroidAutoLyrics.currentLine(fallbackLine, 2_000L, trackDurationMs = 12_000L)
        assertEquals(12_000L, resultFallback?.windowEndMs)
    }
}
