package com.alananasss.kittytune

import com.alananasss.kittytune.data.lyrics.clients.SimpMusicClient
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SimpMusicIntegrationTest {

    @Test
    fun testSimpMusicTvGirlNotAllowedLive() = runBlocking {
        val result = SimpMusicClient.getLyrics(
            title = "Not Allowed",
            artist = "TV Girl",
            duration = 167
        )

        assertTrue("SimpMusic should return lyrics for TV Girl - Not Allowed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val raw = result.getOrThrow()
        assertTrue("Result should contain lyrics", raw.isNotBlank())

        val parsedLines = LyricsUtils.parseLyricsContent(raw, 167_000L)
        assertFalse("Parsed lines must not be empty", parsedLines.isEmpty())
        println("SimpMusic parsed ${parsedLines.size} lines for TV Girl - Not Allowed")
    }

    @Test
    fun testSimpMusicWithNoisySoundCloudTitle() = runBlocking {
        val result = SimpMusicClient.getLyrics(
            title = "TV Girl - Not Allowed (Official Audio)",
            artist = "SoundCloudReuploadAcc",
            duration = 167
        )

        assertTrue("SimpMusic should resolve noisy SoundCloud title: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val raw = result.getOrThrow()
        val parsedLines = LyricsUtils.parseLyricsContent(raw, 167_000L)
        assertFalse("Parsed lines must not be empty", parsedLines.isEmpty())
    }

    @Test
    fun testSimpMusicSearchAndDirectVideoId() = runBlocking {
        val searchResults = SimpMusicClient.search("TV Girl - Not Allowed")
        assertFalse("Search results must not be empty", searchResults.isEmpty())

        val found = searchResults.find { it.videoId == "TPGfJcTycHw" }
        assertNotNull("Should contain known videoId TPGfJcTycHw", found)

        val directResult = SimpMusicClient.getLyrics(videoId = "TPGfJcTycHw", duration = 167)
        assertTrue("Direct videoId fetch should succeed", directResult.isSuccess)
    }
}
