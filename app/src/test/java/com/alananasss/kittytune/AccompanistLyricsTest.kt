package com.alananasss.kittytune

import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AccompanistLyricsTest {
    @Test
    fun testBuildSyncedLyrics() {
        val syllables = listOf(
            KaraokeSyllable(content = "Hel", start = 1000, end = 1500),
            KaraokeSyllable(content = "lo ", start = 1500, end = 2000),
            KaraokeSyllable(content = "World", start = 2000, end = 3000)
        )
        val mainLine = KaraokeLine.MainKaraokeLine(
            syllables = syllables,
            translation = "Bonjour le monde",
            alignment = KaraokeAlignment.Start,
            start = 1000,
            end = 3000
        )
        val syncedLine = SyncedLine(
            content = "Second line",
            translation = null,
            start = 3200,
            end = 6000
        )
        val lyrics = SyncedLyrics(listOf(mainLine, syncedLine))
        assertEquals(2, lyrics.lines.size)
        assertNotNull(lyrics.getCurrentFirstHighlightLineIndexByTime(1200))
        assertEquals(0, lyrics.getCurrentFirstHighlightLineIndexByTime(1200))
        assertEquals(1, lyrics.getCurrentFirstHighlightLineIndexByTime(4000))
    }

    @Test
    fun testBuildSyncedLyricsConversion() {
        val lines = listOf(
            com.alananasss.kittytune.ui.player.lyrics.LyricLine(
                text = "Don&apos;t stop me now",
                startTime = 1000L,
                endTime = 4000L,
                words = listOf(
                    com.alananasss.kittytune.ui.player.lyrics.LyricWord("Don&apos;t", 1000L, 1800L),
                    com.alananasss.kittytune.ui.player.lyrics.LyricWord("stop", 1800L, 2500L),
                    com.alananasss.kittytune.ui.player.lyrics.LyricWord("me", 2500L, 3000L),
                    com.alananasss.kittytune.ui.player.lyrics.LyricWord("now", 3000L, 4000L)
                ),
                translation = "Ne m&apos;arrête pas maintenant"
            ),
            com.alananasss.kittytune.ui.player.lyrics.LyricLine(
                text = "Having a good time",
                startTime = 4200L,
                endTime = 7000L,
                words = emptyList(),
                translation = "Je passe un bon moment"
            )
        )

        val syncedLyrics = com.alananasss.kittytune.ui.player.lyrics.buildSyncedLyrics(lines, isWordSynced = true)
        assertEquals(2, syncedLyrics.lines.size)

        val firstLine = syncedLyrics.lines[0] as KaraokeLine.MainKaraokeLine
        assertEquals("Ne m'arrête pas maintenant", firstLine.translation)
        assertEquals(4, firstLine.syllables.size)
        assertEquals("Don't ", firstLine.syllables[0].content)
        assertEquals("stop ", firstLine.syllables[1].content)
        assertEquals("me ", firstLine.syllables[2].content)
        assertEquals("now", firstLine.syllables[3].content)

        val secondLine = syncedLyrics.lines[1] as SyncedLine
        assertEquals("Having a good time", secondLine.content)
        assertEquals("Je passe un bon moment", secondLine.translation)
    }

    @Test
    fun testBuildSyncedLyricsDuet() {
        val lines = listOf(
            com.alananasss.kittytune.ui.player.lyrics.LyricLine(
                text = "Singer 1 line",
                startTime = 1000L,
                endTime = 3000L,
                singer = com.alananasss.kittytune.ui.player.lyrics.LyricSinger.SINGER_1
            ),
            com.alananasss.kittytune.ui.player.lyrics.LyricLine(
                text = "Singer 2 line",
                startTime = 3500L,
                endTime = 6000L,
                singer = com.alananasss.kittytune.ui.player.lyrics.LyricSinger.SINGER_2
            )
        )

        // With Duet enabled:
        val duetLyrics = com.alananasss.kittytune.ui.player.lyrics.buildSyncedLyrics(lines, isWordSynced = false, isDuetEnabled = true)
        val s1Line = duetLyrics.lines[0] as SyncedLine
        assertEquals("Singer 1 line", s1Line.content)

        val s2Line = duetLyrics.lines[1] as KaraokeLine.MainKaraokeLine
        assertEquals(KaraokeAlignment.End, s2Line.alignment)

        // With Duet disabled:
        val normalLyrics = com.alananasss.kittytune.ui.player.lyrics.buildSyncedLyrics(lines, isWordSynced = false, isDuetEnabled = false)
        val normalS2Line = normalLyrics.lines[1] as SyncedLine
        assertEquals("Singer 2 line", normalS2Line.content)
    }

    @Test
    fun testBuildSyncedLyricsAlignment() {
        val lines = listOf(
            com.alananasss.kittytune.ui.player.lyrics.LyricLine(
                text = "Center text",
                startTime = 1000L,
                endTime = 3000L,
                words = listOf(
                    com.alananasss.kittytune.ui.player.lyrics.LyricWord("Center", 1000L, 2000L),
                    com.alananasss.kittytune.ui.player.lyrics.LyricWord(" text", 2000L, 3000L)
                )
            )
        )

        val centerLyrics = com.alananasss.kittytune.ui.player.lyrics.buildSyncedLyrics(
            lines,
            isWordSynced = true,
            userAlignment = com.alananasss.kittytune.data.local.LyricsAlignment.CENTER
        )
        val centerLine = centerLyrics.lines[0] as KaraokeLine.MainKaraokeLine
        assertEquals(KaraokeAlignment.Unspecified, centerLine.alignment)

        val rightLyrics = com.alananasss.kittytune.ui.player.lyrics.buildSyncedLyrics(
            lines,
            isWordSynced = true,
            userAlignment = com.alananasss.kittytune.data.local.LyricsAlignment.RIGHT
        )
        val rightLine = rightLyrics.lines[0] as KaraokeLine.MainKaraokeLine
        assertEquals(KaraokeAlignment.End, rightLine.alignment)

        val leftLyrics = com.alananasss.kittytune.ui.player.lyrics.buildSyncedLyrics(
            lines,
            isWordSynced = false,
            userAlignment = com.alananasss.kittytune.data.local.LyricsAlignment.LEFT
        )
        val leftLine = leftLyrics.lines[0] as SyncedLine
        assertEquals("Center text", leftLine.content)
    }
}
