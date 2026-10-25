package com.alananasss.kittytune

import com.alananasss.kittytune.data.local.ExternalPlaylists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Radios from YouTube Music and Spotify are saved as snapshots. They are not
 * SoundCloud playlists, so every "is this external?" check must keep them out of
 * the SoundCloud refresh/delete paths. Otherwise the next library sync overwrites
 * the saved row with a random SoundCloud playlist or removes it, and the liked
 * radio opens empty.
 */
class ExternalPlaylistsTest {

    @Test
    fun `radio routes are recognised`() {
        assertTrue(ExternalPlaylists.isRadioPermalink("yt_radio:https://youtube.com/watch?v=abc"))
        assertTrue(ExternalPlaylists.isRadioPermalink("spotify_radio:4uLU6hMCjMI75M1A2tKUQC"))
        assertTrue(ExternalPlaylists.isRadioPermalink("station_spotify:4uLU6hMCjMI75M1A2tKUQC"))
        assertFalse(ExternalPlaylists.isRadioPermalink("https://soundcloud.com/artist/sets/song"))
    }

    @Test
    fun `saved radios are external playlists`() {
        assertTrue(ExternalPlaylists.isExternalPermalink("yt_radio:https://youtube.com/watch?v=abc"))
        assertTrue(ExternalPlaylists.isExternalPermalink("https://www.youtube.com/watch?v=abc&list=RDabc"))
        assertTrue(ExternalPlaylists.isExternalPermalink("spotify_radio:4uLU6hMCjMI75M1A2tKUQC"))
    }

    @Test
    fun `a youtube radio is saved under the id its route resolves to`() {
        val url = "https://youtube.com/watch?v=abc"
        assertEquals(
            kotlin.math.abs(url.hashCode().toLong()),
            ExternalPlaylists.radioStableId("yt_radio:$url")
        )
    }

    @Test
    fun `an encoded youtube radio route hashes the decoded url`() {
        val url = "https://www.youtube.com/watch?v=abc&list=RDabc"
        val encoded = java.net.URLEncoder.encode(url, "UTF-8")
        // Library navigation re-encodes the stored route; the hash must not depend
        // on whether the seed was saved raw or percent-encoded.
        assertEquals(
            ExternalPlaylists.radioStableId("yt_radio:$url"),
            ExternalPlaylists.radioStableId("yt_radio:$encoded")
        )
    }

    @Test
    fun `a spotify radio is saved under the seed track id`() {
        val trackId = "4uLU6hMCjMI75M1A2tKUQC"
        assertEquals(
            ExternalPlaylists.spotifyStableId(trackId),
            ExternalPlaylists.radioStableId("spotify_radio:$trackId")
        )
        // Routed through history the seed can already be a full Spotify URI.
        assertEquals(
            ExternalPlaylists.spotifyStableId(trackId),
            ExternalPlaylists.radioStableId("spotify_radio:spotify:track:$trackId")
        )
        // station_spotify is the same radio under a different route name.
        assertEquals(
            ExternalPlaylists.spotifyStableId(trackId),
            ExternalPlaylists.radioStableId("station_spotify:$trackId")
        )
    }

    @Test
    fun `library ids only change for radio routes`() {
        val trackId = "4uLU6hMCjMI75M1A2tKUQC"
        assertEquals(
            ExternalPlaylists.spotifyStableId(trackId),
            ExternalPlaylists.libraryIdFor("spotify_radio:spotify:track:$trackId", 999L)
        )
        assertEquals(42L, ExternalPlaylists.libraryIdFor(null, 42L))
        assertEquals(42L, ExternalPlaylists.libraryIdFor("https://soundcloud.com/artist/sets/song", 42L))
    }

    @Test
    fun `blank radio seeds do not produce an id`() {
        assertNull(ExternalPlaylists.radioStableId("yt_radio:"))
        assertNull(ExternalPlaylists.radioStableId("spotify_radio:"))
    }

    @Test
    fun `a plain soundcloud playlist is still not external`() {
        assertFalse(ExternalPlaylists.isExternalPermalink("https://soundcloud.com/artist/sets/song"))
        assertFalse(ExternalPlaylists.isExternalPermalink(null))
    }
}
