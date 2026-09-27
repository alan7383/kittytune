package com.alananasss.kittytune

import com.alananasss.kittytune.data.spotify.SpotifyArtistRef
import com.alananasss.kittytune.data.spotify.SpotifyTrack
import com.alananasss.kittytune.data.vk.VkArtist
import com.alananasss.kittytune.data.vk.VkAudioItem
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.TrackPublisherMetadata
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.domain.deduplicateArtistString
import com.alananasss.kittytune.domain.formatDeduplicatedArtists
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistDeduplicationTest {

    @Test
    fun deduplicateArtistString_removesCommaDuplicates() {
        assertEquals("Billie Eilish", deduplicateArtistString("Billie Eilish, Billie Eilish"))
        assertEquals("Billie Eilish", deduplicateArtistString("Billie Eilish, billie eilish"))
        assertEquals("The Weeknd", deduplicateArtistString("The Weeknd, The Weeknd, The Weeknd"))
        assertEquals("Drake, 21 Savage", deduplicateArtistString("Drake, 21 Savage, Drake"))
    }

    @Test
    fun deduplicateArtistString_removesSemicolonAndSlashDuplicates() {
        assertEquals("Kendrick Lamar", deduplicateArtistString("Kendrick Lamar; Kendrick Lamar"))
        assertEquals("Artist A", deduplicateArtistString("Artist A / Artist A"))
        assertEquals("Artist A, Artist B", deduplicateArtistString("Artist A / Artist B / Artist A"))
    }

    @Test
    fun deduplicateArtistString_removesCollaborationDuplicates() {
        assertEquals("Eminem", deduplicateArtistString("Eminem feat. Eminem"))
        assertEquals("Drake", deduplicateArtistString("Drake ft. Drake"))
        assertEquals("Artist", deduplicateArtistString("Artist & Artist"))
        assertEquals("Artist", deduplicateArtistString("Artist + Artist"))
        assertEquals("Artist", deduplicateArtistString("Artist x Artist"))
        assertEquals("Artist", deduplicateArtistString("Artist - Artist"))
    }

    @Test
    fun deduplicateArtistString_preservesLegitimateMultiArtists() {
        assertEquals("Drake, 21 Savage", deduplicateArtistString("Drake, 21 Savage"))
        assertEquals("Simon & Garfunkel", deduplicateArtistString("Simon & Garfunkel"))
        assertEquals("Earth, Wind & Fire", deduplicateArtistString("Earth, Wind & Fire"))
    }

    @Test
    fun deduplicateArtistString_preservesKnownCommaArtists() {
        assertEquals("Tyler, The Creator", deduplicateArtistString("Tyler, The Creator"))
        assertEquals("Tyler, The Creator", deduplicateArtistString("Tyler, The Creator, Tyler, The Creator"))
        assertEquals("Tyler, The Creator, A\$AP Rocky", deduplicateArtistString("Tyler, The Creator, A\$AP Rocky"))
    }

    @Test
    fun deduplicateArtistString_preservesRepeatedWordBands() {
        assertEquals("Duran Duran", deduplicateArtistString("Duran Duran"))
        assertEquals("The The", deduplicateArtistString("The The"))
    }

    @Test
    fun deduplicateArtistString_handlesEmptyAndSingleArtists() {
        assertEquals("", deduplicateArtistString(""))
        assertEquals("", deduplicateArtistString("   "))
        assertEquals("Adele", deduplicateArtistString("Adele"))
    }

    @Test
    fun trackDisplayArtist_deduplicatesListOfSpotifyArtistRefs() {
        val trackWithDuplicateRefs = Track(
            id = 1L,
            title = "Birds of a Feather",
            artworkUrl = null,
            durationMs = 180000L,
            user = null,
            artists = listOf(
                SpotifyArtistRef(id = "1", name = "Billie Eilish"),
                SpotifyArtistRef(id = "2", name = "Billie Eilish")
            )
        )
        assertEquals("Billie Eilish", trackWithDuplicateRefs.displayArtist)

        val trackWithCombinedName = Track(
            id = 2L,
            title = "Birds of a Feather",
            artworkUrl = null,
            durationMs = 180000L,
            user = null,
            artists = listOf(
                SpotifyArtistRef(id = "1", name = "Billie Eilish, Billie Eilish")
            )
        )
        assertEquals("Billie Eilish", trackWithCombinedName.displayArtist)
    }

    @Test
    fun trackDisplayArtist_fallbacksCleanUpDuplicates() {
        val pubTrack = Track(
            id = 3L,
            title = "Blinding Lights",
            artworkUrl = null,
            durationMs = 200000L,
            user = null,
            publisherMetadata = TrackPublisherMetadata(artist = "The Weeknd, The Weeknd")
        )
        assertEquals("The Weeknd", pubTrack.displayArtist)

        val userTrack = Track(
            id = 4L,
            title = "Hotline Bling",
            artworkUrl = null,
            durationMs = 200000L,
            user = User(id = 10L, username = "Drake, Drake", avatarUrl = null)
        )
        assertEquals("Drake", userTrack.displayArtist)
    }

    @Test
    fun spotifyTrack_toTrack_deduplicatesArtists() {
        val spotTrack = SpotifyTrack(
            id = "test_spotify_id",
            name = "Song",
            durationMs = 150000L,
            artists = listOf(
                SpotifyArtistRef(id = "art1", name = "Dua Lipa"),
                SpotifyArtistRef(id = "art1_dup", name = "Dua Lipa")
            )
        )

        assertEquals("Dua Lipa", spotTrack.artistName)
        val converted = spotTrack.toTrack()
        assertEquals("Dua Lipa", converted.displayArtist)
        assertEquals("Dua Lipa", converted.user?.username)
        assertEquals(1, converted.artists?.size)
    }

    @Test
    fun vkAudioItem_toTrack_deduplicatesArtists() {
        val vkItem = VkAudioItem(
            id = 12345L,
            ownerId = 67890L,
            title = "Track Title",
            performer = "Eminem",
            durationSeconds = 240,
            url = "",
            mainArtists = listOf(VkArtist(id = "eminem", name = "Eminem")),
            featArtists = listOf(VkArtist(id = "eminem", name = "Eminem"))
        )

        assertEquals("Eminem", vkItem.displayArtists)
        val converted = vkItem.toTrack()
        assertEquals("Eminem", converted.displayArtist)
        assertEquals("Eminem", converted.user?.username)
        assertEquals(1, converted.artists?.size)
    }
}
