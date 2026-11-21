package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.spotify.SpotifyPlaylist
import com.alananasss.kittytune.data.spotify.SpotifyRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class SpotifyRadioParsingTest {
    @Test
    fun `matching official radios can be identified by title or id`() {
        val named = SpotifyPlaylist("37i9dQZF1DXradio", "Mr. Brightside Radio", ownerName = "Spotify")
        val localized = SpotifyPlaylist("37i9dQZF1E8radio", "Radio de Mr. Brightside", ownerName = "Spotify")
        val wrongSeed = SpotifyPlaylist("37i9dQZF1E4wrong", "Chill Radio", ownerName = "Spotify")

        val picked = SpotifyRepository.officialRadioCandidates(
            listOf(wrongSeed, named, localized),
            " mr. brightside "
        )
        assertEquals(listOf(named, localized), picked)
    }

    @Test
    fun `unrelated editorials and user playlists do not replace a missing radio`() {
        val wrongSeed = SpotifyPlaylist("37i9dQZF1E4other", "Chill Radio", ownerName = "Spotify")
        val editorial = SpotifyPlaylist("37i9dQZF1DXcBWIGoYBM5M", "Today's Top Hits", ownerName = "Spotify")
        val matchingNonRadio = SpotifyPlaylist("37i9dQZF1DXmix", "This Is Radiohead", ownerName = "Spotify")
        val wrongOwner = SpotifyPlaylist("37i9dQZF1E4wrong", "Radiohead Radio", ownerName = "someone")
        val wrongPrefix = SpotifyPlaylist("0abcUserPlaylist", "Radiohead Radio", ownerName = "Spotify")

        val picked = SpotifyRepository.officialRadioCandidates(
            listOf(wrongSeed, editorial, matchingNonRadio, wrongOwner, wrongPrefix),
            "Radiohead"
        )
        assertEquals(emptyList<SpotifyPlaylist>(), picked)
    }
}
