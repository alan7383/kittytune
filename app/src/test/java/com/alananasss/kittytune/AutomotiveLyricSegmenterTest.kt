package com.alananasss.kittytune

import com.alananasss.kittytune.data.auto.AutomotiveLyricSegmenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomotiveLyricSegmenterTest {

    @Test
    fun returnsSingleSegmentForShortLine() {
        assertEquals(listOf("A short lyric line"), AutomotiveLyricSegmenter.segmentLine("A short lyric line"))
    }

    @Test
    fun normalizesWhitespace() {
        val result = AutomotiveLyricSegmenter.segmentLine("  first\n\tphrase    second phrase  ")
        assertEquals(listOf("first phrase second phrase"), result)
    }

    @Test
    fun splitsLongLineNearNaturalPunctuation() {
        val text = "I've been waiting for this moment, but you walked away into the dark night"
        val result = AutomotiveLyricSegmenter.segmentLine(text)
        assertTrue(result.size in 2..AutomotiveLyricSegmenter.MAX_SEGMENTS)
        assertEquals(text, result.joinToString(" "))
    }

    @Test
    fun respectsMaxSegments() {
        val text = "One phrase and two phrase and three phrase and four phrase and five phrase and six phrase and seven phrase and eight phrase"
        val result = AutomotiveLyricSegmenter.segmentLine(text)
        assertEquals(AutomotiveLyricSegmenter.MAX_SEGMENTS, result.size)
    }

    @Test
    fun handlesEmojiSafelyWithoutCorruptingBoundaries() {
        val text = "Singing with fire 🔥❤️🔥 in my heart all through the lonely winter night"
        val result = AutomotiveLyricSegmenter.segmentLine(text)
        assertEquals(text, result.joinToString(" "))
    }

    @Test
    fun handlesWideUnicodeCharacters() {
        val text = "これは非常に長い歌詞の行であり、画面に合わせて適切に分割される必要があります"
        val result = AutomotiveLyricSegmenter.segmentLine(text)
        assertTrue(result.size >= 2)
    }

    @Test
    fun generatesTimedSegmentsWithMinDuration() {
        val text = "First readable phrase, followed by another phrase, and a final phrase for the driver"
        val result = AutomotiveLyricSegmenter.timedSegments(text, 1_000L, 9_000L)
        assertTrue(result.size in 2..AutomotiveLyricSegmenter.MAX_SEGMENTS)
        assertEquals(1_000L, result.first().startTimeMs)
        assertEquals(9_000L, result.last().endTimeMs)
        assertTrue(result.all { it.endTimeMs - it.startTimeMs >= AutomotiveLyricSegmenter.MIN_SEGMENT_DURATION_MS })
    }

    @Test
    fun segmentAtSelectsCorrectSegmentByPosition() {
        val text = "First part of the line, second part of the line"
        val segments = AutomotiveLyricSegmenter.timedSegments(text, 0L, 4_000L)
        assertEquals(2, segments.size)
        val transition = segments[1].startTimeMs

        assertEquals(0, AutomotiveLyricSegmenter.segmentAt(segments, transition - 1L))
        assertEquals(1, AutomotiveLyricSegmenter.segmentAt(segments, transition))
        assertEquals(1, AutomotiveLyricSegmenter.segmentAt(segments, 4_000L))
    }
}
