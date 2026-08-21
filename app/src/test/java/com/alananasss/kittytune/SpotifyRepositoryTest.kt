package com.alananasss.kittytune

import com.alananasss.kittytune.data.spotify.SpotifyArtistRef
import com.alananasss.kittytune.data.spotify.SpotifyPathfinderApi
import com.alananasss.kittytune.data.spotify.SpotifyRepository
import com.alananasss.kittytune.data.spotify.SpotifyTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyRepositoryTest {

    @Test
    fun testTrackConversion() {
        val spotifyTrack = SpotifyTrack(
            id = "4uLU6hMCjMI75M1A2tKUQC",
            name = "Never Gonna Give You Up",
            durationMs = 213000L,
            artists = listOf(SpotifyArtistRef(id = "0gxyHStUsqpMadRV0Di1Qt", name = "Rick Astley")),
            albumName = "Whenever You Need Somebody",
            artworkUrl = "https://i.scdn.co/image/ab67616d0000b2735755e164993798e0c9ef7d7a",
            releaseDate = "1987-11-12",
            explicit = false
        )

        val track = spotifyTrack.toTrack()

        assertEquals("Never Gonna Give You Up", track.title)
        assertEquals("Rick Astley", track.displayArtist)
        assertEquals("spotify", track.source)
        assertEquals(213000L, track.durationMs)
        assertEquals("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC", track.permalinkUrl)
        assertTrue(track.id > 0)
    }

    @Test
    fun testPathfinderQueryUrlGeneration() {
        val searchUrl = SpotifyPathfinderApi.buildSearchUrl("Daft Punk")
        assertTrue(searchUrl.contains(SpotifyPathfinderApi.PATHFINDER_URL))
        assertTrue(searchUrl.contains("operationName=searchDesktop"))
        assertTrue(searchUrl.contains(SpotifyPathfinderApi.Hashes.SEARCH_DESKTOP))

        val trackUrl = SpotifyPathfinderApi.buildTrackUrl("4uLU6hMCjMI75M1A2tKUQC")
        assertTrue(trackUrl.contains("operationName=getTrack"))
        assertTrue(trackUrl.contains(SpotifyPathfinderApi.Hashes.GET_TRACK))

        val playlistUrl = SpotifyPathfinderApi.buildPlaylistUrl("37i9dQZEVXbMDoHDwVN2tF")
        assertTrue(playlistUrl.contains("operationName=fetchPlaylist"))
        assertTrue(playlistUrl.contains(SpotifyPathfinderApi.Hashes.FETCH_PLAYLIST))
    }

    @Test
    fun testArtistConversion() {
        val spotifyArtist = com.alananasss.kittytune.data.spotify.SpotifyArtist(
            id = "0gxyHStUsqpMadRV0Di1Qt",
            name = "Rick Astley",
            avatarUrl = "https://i.scdn.co/image/artist_avatar.jpg",
            verified = true,
            monthlyListeners = 15000000L
        )

        val user = spotifyArtist.toUser()

        assertEquals("Rick Astley", user.username)
        assertEquals("https://i.scdn.co/image/artist_avatar.jpg", user.avatarUrl)
        assertEquals(true, user.verified)
        assertEquals(15000000, user.followersCount)
        assertTrue(user.id > 0)
    }

    @Test
    fun testArtistUrlGeneration() {
        val artistUrl = SpotifyPathfinderApi.buildArtistUrl("0gxyHStUsqpMadRV0Di1Qt")
        assertTrue(artistUrl.contains("operationName=queryArtistOverview"))
        assertTrue(artistUrl.contains(SpotifyPathfinderApi.Hashes.QUERY_ARTIST_OVERVIEW))

        val relatedUrl = SpotifyPathfinderApi.buildArtistRelatedUrl("0gxyHStUsqpMadRV0Di1Qt")
        assertTrue(relatedUrl.contains("operationName=queryArtistRelated"))
        assertTrue(relatedUrl.contains(SpotifyPathfinderApi.Hashes.QUERY_ARTIST_RELATED))
    }

    @Test
    fun testChartsDefinitions() {
        val charts = SpotifyRepository.getCharts()
        assertTrue(charts.isNotEmpty())
        val globalTop50 = charts.firstOrNull { it.key == "top-50-global" }
        assertNotNull(globalTop50)
        assertEquals("37i9dQZEVXbMDoHDwVN2tF", globalTop50?.playlistId)
    }
}
