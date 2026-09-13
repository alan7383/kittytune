package com.alananasss.kittytune

import com.alananasss.kittytune.data.lyrics.clients.BetterLyricsClient
import com.alananasss.kittytune.ui.player.lyrics.LyricSinger
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BetterLyricsIntegrationTest {

    @Test
    fun testBetterLyricsTvGirlNotAllowedLive() = runBlocking {
        val result = BetterLyricsClient.getLyrics(
            title = "Not Allowed",
            artist = "TV Girl",
            album = "Who Really Cares",
            durationSeconds = 167
        )

        assertTrue("BetterLyrics should return success for TV Girl - Not Allowed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val ttml = result.getOrThrow()
        assertTrue("Result should contain TTML XML content", ttml.contains("<tt") && ttml.contains("</tt>"))

        val parsedLines = LyricsUtils.parseLyricsContent(ttml, 167_000L)
        assertFalse("Parsed lines must not be empty", parsedLines.isEmpty())

        println("Parsed ${parsedLines.size} lines from BetterLyrics TV Girl - Not Allowed")

        val hasWordSync = parsedLines.any { it.words.isNotEmpty() }
        assertTrue("BetterLyrics TV Girl - Not Allowed should have word-synced lyrics", hasWordSync)

        val hasSinger1 = parsedLines.any { it.singer == LyricSinger.SINGER_1 || it.agent?.lowercase() == "v1" }
        val hasSinger2 = parsedLines.any { it.singer == LyricSinger.SINGER_2 || it.agent?.lowercase() == "v2" }

        println("Has Singer 1 (v1): $hasSinger1, Has Singer 2 (v2): $hasSinger2")
        assertTrue("TV Girl - Not Allowed should have singer 1 (v1)", hasSinger1)
        assertTrue("TV Girl - Not Allowed should have singer 2 (v2)", hasSinger2)

        val lineWithWords = parsedLines.first { it.words.isNotEmpty() }
        assertTrue("Line start time should be >= 0", lineWithWords.startTime >= 0)
        assertTrue("Line end time should be > line start time", lineWithWords.endTime > lineWithWords.startTime)
        val firstWord = lineWithWords.words.first()
        assertTrue("Word end time should be >= word start time", firstWord.endTime >= firstWord.startTime)
    }

    @Test
    fun testBetterLyricsWithNoisyTitleFallback() = runBlocking {
        // Simulates typical SoundCloud track title with bracket and artist prefix noise
        val result = BetterLyricsClient.getLyrics(
            title = "TV Girl - Not Allowed (Official Audio)",
            artist = "TV Girl",
            album = "",
            durationSeconds = 167
        )

        assertTrue("BetterLyrics should resolve noisy title via smart fallback: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val ttml = result.getOrThrow()
        val parsedLines = LyricsUtils.parseLyricsContent(ttml, 167_000L)
        assertEquals("Should parse 60 lines from resolved TTML", 60, parsedLines.size)
    }

    @Test
    fun testBetterLyricsWithCombinedQueryFromManualSearch() = runBlocking {
        // Simulates query pre-filled in manual search: "Not Allowed TV Girl"
        val result = BetterLyricsClient.getLyrics(
            title = "Not Allowed TV Girl",
            artist = "TV Girl",
            album = "",
            durationSeconds = 167
        )

        assertTrue("BetterLyrics should resolve combined manual search query: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val ttml = result.getOrThrow()
        val parsedLines = LyricsUtils.parseLyricsContent(ttml, 167_000L)
        assertEquals("Should parse 60 lines from resolved TTML", 60, parsedLines.size)
    }

    @Test
    fun testBetterLyricsWithSoundcloudUploaderAndArtistInTitle() = runBlocking {
        val result = BetterLyricsClient.getLyrics(
            title = "TV Girl - Not Allowed (Official Audio)",
            artist = "RandomSoundcloudUploader",
            album = "",
            durationSeconds = 167
        )

        assertTrue("BetterLyrics should extract artist from title and succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val ttml = result.getOrThrow()
        val parsedLines = LyricsUtils.parseLyricsContent(ttml, 167_000L)
        assertEquals("Should parse 60 lines from resolved TTML", 60, parsedLines.size)
    }

    @Test
    fun testFormatLyricWordContents() {
        // Case 1: TTML Blue Hair with syllables ('pret' and 'ty') and embedded spaces
        val line1 = "She asked me if she was pretty"
        val words1 = listOf(
            com.alananasss.kittytune.ui.player.lyrics.LyricWord("She ", 0, 100),
            com.alananasss.kittytune.ui.player.lyrics.LyricWord("asked ", 100, 200),
            com.alananasss.kittytune.ui.player.lyrics.LyricWord("me ", 200, 300),
            com.alananasss.kittytune.ui.player.lyrics.LyricWord("if ", 300, 400),
            com.alananasss.kittytune.ui.player.lyrics.LyricWord("she ", 400, 500),
            com.alananasss.kittytune.ui.player.lyrics.LyricWord("was ", 500, 600),
            com.alananasss.kittytune.ui.player.lyrics.LyricWord("pret", 600, 700),
            com.alananasss.kittytune.ui.player.lyrics.LyricWord("ty", 700, 800)
        )
        val formatted1 = com.alananasss.kittytune.ui.player.lyrics.formatLyricWordContents(line1, words1)
        assertEquals(line1, formatted1.joinToString(""))
        assertEquals("pret", formatted1[6])
        assertEquals("ty", formatted1[7])

        // Case 2: CJK text with no spaces
        val line2 = "你好世界"
        val words2 = listOf(
            com.alananasss.kittytune.ui.player.lyrics.LyricWord("你", 0, 100),
            com.alananasss.kittytune.ui.player.lyrics.LyricWord("好", 100, 200),
            com.alananasss.kittytune.ui.player.lyrics.LyricWord("世", 200, 300),
            com.alananasss.kittytune.ui.player.lyrics.LyricWord("界", 300, 400)
        )
        val formatted2 = com.alananasss.kittytune.ui.player.lyrics.formatLyricWordContents(line2, words2)
        assertEquals(line2, formatted2.joinToString(""))
        assertEquals(listOf("你", "好", "世", "界"), formatted2)

        // Case 3: Stripped spaces provider for whole words
        val line3 = "She asked me if she was pretty"
        val words3 = listOf("She", "asked", "me", "if", "she", "was", "pretty").mapIndexed { i, w ->
            com.alananasss.kittytune.ui.player.lyrics.LyricWord(w, i * 100L, (i + 1) * 100L)
        }
        val formatted3 = com.alananasss.kittytune.ui.player.lyrics.formatLyricWordContents(line3, words3)
        assertEquals(line3, formatted3.joinToString("").trim())

        // Case 4: Stripped spaces provider with syllables (e.g. pret and ty without trailing spaces)
        val line4 = "She asked me if she was pretty"
        val words4 = listOf("She", "asked", "me", "if", "she", "was", "pret", "ty").mapIndexed { i, w ->
            com.alananasss.kittytune.ui.player.lyrics.LyricWord(w, i * 100L, (i + 1) * 100L)
        }
        val formatted4 = com.alananasss.kittytune.ui.player.lyrics.formatLyricWordContents(line4, words4)
        assertEquals(line4, formatted4.joinToString("").trim())
        assertEquals("pret", formatted4[6])
        assertEquals("ty", formatted4[7])
    }
}
