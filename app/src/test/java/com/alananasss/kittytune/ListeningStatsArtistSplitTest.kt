package com.alananasss.kittytune

import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.local.TopArtistResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningStatsArtistSplitTest {

    @Test
    fun splitArtistNames_correctlySplitsStandardMultiArtistStrings() {
        val cases = mapOf(
            "Drake, 21 Savage" to listOf("Drake", "21 Savage"),
            "Drake, 21 Savage, Metro Boomin" to listOf("Drake", "21 Savage", "Metro Boomin"),
            "Drake & Future" to listOf("Drake", "Future"),
            "Drake feat. Rihanna" to listOf("Drake", "Rihanna"),
            "Drake ft. Lil Baby" to listOf("Drake", "Lil Baby"),
            "Kendrick Lamar" to listOf("Kendrick Lamar")
        )

        for ((input, expected) in cases) {
            val result = ListeningStatsRepository.splitArtistNames(input)
            assertEquals("Failed for input: $input", expected, result)
        }
    }

    @Test
    fun splitArtistNames_preservesKnownArtistsContainingCommasOrAmpersands() {
        val tylerSolo = ListeningStatsRepository.splitArtistNames("Tyler, The Creator")
        assertEquals(listOf("Tyler, The Creator"), tylerSolo)

        val simonSolo = ListeningStatsRepository.splitArtistNames("Simon & Garfunkel")
        assertEquals(listOf("Simon & Garfunkel"), simonSolo)

        val earthSolo = ListeningStatsRepository.splitArtistNames("Earth, Wind & Fire")
        assertEquals(listOf("Earth, Wind & Fire"), earthSolo)

        // Tyler in collaboration
        val tylerCollab = ListeningStatsRepository.splitArtistNames("Tyler, The Creator, A\$AP Rocky")
        assertEquals(listOf("Tyler, The Creator", "A\$AP Rocky"), tylerCollab)
    }

    @Test
    fun aggregateAndSplitTopArtists_creditsEachArtistIndividuallyAndEliminatesCombinedCards() {
        val raw = listOf(
            TopArtistResult(
                artistName = "Drake",
                artworkUrl = "https://example.com/drake.jpg",
                artistId = 100L,
                artistPermalink = "drake",
                source = "soundcloud",
                playCount = 5,
                totalListenMs = 500_000L
            ),
            TopArtistResult(
                artistName = "Drake, 21 Savage",
                artworkUrl = "https://example.com/collab.jpg",
                artistId = 100L,
                artistPermalink = "drake",
                source = "soundcloud",
                playCount = 3,
                totalListenMs = 300_000L
            ),
            TopArtistResult(
                artistName = "21 Savage",
                artworkUrl = "https://example.com/21.jpg",
                artistId = 200L,
                artistPermalink = "21savage",
                source = "soundcloud",
                playCount = 2,
                totalListenMs = 200_000L
            )
        )

        val aggregated = ListeningStatsRepository.aggregateAndSplitTopArtists(raw, limit = 10)

        // There should be only 2 artists in the result: Drake and 21 Savage (no combined "Drake, 21 Savage")
        assertEquals(2, aggregated.size)
        assertFalse(aggregated.any { it.artistName.contains(",") })

        val drake = aggregated.first { it.artistName == "Drake" }
        assertEquals(8, drake.playCount) // 5 + 3
        assertEquals(800_000L, drake.totalListenMs) // 500_000 + 300_000
        assertEquals("https://example.com/drake.jpg", drake.artworkUrl)
        assertEquals(100L, drake.artistId)

        val savage = aggregated.first { it.artistName == "21 Savage" }
        assertEquals(5, savage.playCount) // 2 + 3
        assertEquals(500_000L, savage.totalListenMs) // 200_000 + 300_000
        assertEquals("https://example.com/21.jpg", savage.artworkUrl)
        assertEquals(200L, savage.artistId)

        // Sorted by totalListenMs descending
        assertEquals("Drake", aggregated[0].artistName)
        assertEquals("21 Savage", aggregated[1].artistName)
    }

    @Test
    fun aggregateAndSplitTopArtists_handlesArtistOnlyPresentInCollabs() {
        val raw = listOf(
            TopArtistResult(
                artistName = "Artist A, Artist B",
                artworkUrl = "https://example.com/collab.jpg",
                artistId = 1L,
                artistPermalink = "artistA",
                source = "spotify",
                playCount = 4,
                totalListenMs = 400_000L
            )
        )

        val aggregated = ListeningStatsRepository.aggregateAndSplitTopArtists(raw, limit = 5)
        assertEquals(2, aggregated.size)
        assertTrue(aggregated.any { it.artistName == "Artist A" && it.playCount == 4 })
        assertTrue(aggregated.any { it.artistName == "Artist B" && it.playCount == 4 })
    }
}
