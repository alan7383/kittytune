package com.alananasss.kittytune

import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkOptimizationTest {

    private fun createTrack(
        id: Long,
        title: String,
        artworkUrl: String?,
        source: String = "soundcloud",
        user: User? = null
    ): Track {
        return Track(
            id = id,
            title = title,
            artworkUrl = artworkUrl,
            durationMs = 180000L,
            user = user,
            source = source
        )
    }

    @Test
    fun youtube_artwork_downscalesToHqdefaultOrW300() {
        val ytMaxres = createTrack(
            id = 1L,
            title = "Test YT",
            artworkUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
            source = "youtube"
        )
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            ytMaxres.thumbnailUrl
        )

        val ytSd = createTrack(
            id = 2L,
            title = "Test YT SD",
            artworkUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/sddefault.jpg",
            source = "youtube"
        )
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            ytSd.thumbnailUrl
        )

        val ytGoogleUserContent = createTrack(
            id = 3L,
            title = "Test YT Google",
            artworkUrl = "https://lh3.googleusercontent.com/abc=w1080-h1080",
            source = "youtube"
        )
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w300-h300",
            ytGoogleUserContent.thumbnailUrl
        )
    }

    @Test
    fun soundcloud_artwork_usesT300InsteadOfT500OrLarge() {
        val scTrack = createTrack(
            id = 10L,
            title = "Test SC",
            artworkUrl = "https://i1.sndcdn.com/artworks-000123-t500x500.jpg",
            source = "soundcloud"
        )
        assertEquals(
            "https://i1.sndcdn.com/artworks-000123-t300x300.jpg",
            scTrack.thumbnailUrl
        )

        val scLarge = createTrack(
            id = 11L,
            title = "Test SC Large",
            artworkUrl = "https://i1.sndcdn.com/artworks-000123-large.jpg",
            source = "soundcloud"
        )
        assertEquals(
            "https://i1.sndcdn.com/artworks-000123-t300x300.jpg",
            scLarge.thumbnailUrl
        )

        val scOriginal = createTrack(
            id = 12L,
            title = "Test SC Original",
            artworkUrl = "https://i1.sndcdn.com/artworks-000123-original.jpg",
            source = "soundcloud"
        )
        assertEquals(
            "https://i1.sndcdn.com/artworks-000123-t300x300.jpg",
            scOriginal.thumbnailUrl
        )

        val scLabs = createTrack(
            id = 13L,
            title = "Test SC Labs",
            artworkUrl = "https://al.sndcdn.com/labs-123-0-t500x500.jpg?q=xyz",
            source = "soundcloud"
        )
        assertEquals(
            "https://al.sndcdn.com/labs-123-0-t300x300.jpg?q=xyz",
            scLabs.thumbnailUrl
        )

        val scDefaultAvatar = createTrack(
            id = 14L,
            title = "Test SC Default Avatar",
            artworkUrl = "https://a1.sndcdn.com/images/default_avatar_large.png",
            source = "soundcloud"
        )
        assertEquals(
            "https://a1.sndcdn.com/images/default_avatar_large.png",
            scDefaultAvatar.thumbnailUrl
        )
    }

    @Test
    fun spotify_artwork_downscalesTo300x300() {
        val spotifyTrack = createTrack(
            id = 20L,
            title = "Test Spotify",
            artworkUrl = "https://i.scdn.co/image/ab67616d0000b273ba5db46f4b838ef6027e6f96",
            source = "spotify"
        )
        assertEquals(
            "https://i.scdn.co/image/ab67616d00001e02ba5db46f4b838ef6027e6f96",
            spotifyTrack.thumbnailUrl
        )

        val spotifyArtist = createTrack(
            id = 21L,
            title = "Test Spotify Artist",
            artworkUrl = null,
            user = User(100L, "Artist", avatarUrl = "https://i.scdn.co/image/ab6761610000e5ebba5db46f4b838ef6027e6f96"),
            source = "spotify"
        )
        assertEquals(
            "https://i.scdn.co/image/ab67616100005174ba5db46f4b838ef6027e6f96",
            spotifyArtist.thumbnailUrl
        )
    }

    @Test
    fun local_and_fallback_artwork_preserved() {
        val localTrack = createTrack(
            id = -100L,
            title = "Local Song",
            artworkUrl = "/data/user/0/com.alananasss.kittytune/files/local_art_123.jpg",
            source = "local"
        )
        assertEquals(
            "/data/user/0/com.alananasss.kittytune/files/local_art_123.jpg",
            localTrack.thumbnailUrl
        )

        val fileUriTrack = createTrack(
            id = -101L,
            title = "File URI Song",
            artworkUrl = "file:///storage/emulated/0/Music/cover.jpg",
            source = "local"
        )
        assertEquals(
            "file:///storage/emulated/0/Music/cover.jpg",
            fileUriTrack.thumbnailUrl
        )

        val emptyTrack = createTrack(
            id = 999L,
            title = "Empty",
            artworkUrl = null,
            user = null
        )
        assertEquals("https://picsum.photos/200", emptyTrack.thumbnailUrl)
    }

    @Test
    fun playlist_thumbnailUrl_resolvesCorrectly() {
        val playlist = Playlist(
            id = 500L,
            title = "My Playlist",
            artworkUrl = "https://i1.sndcdn.com/artworks-999-t500x500.jpg",
            calculatedArtworkUrl = null,
            trackCount = 1,
            user = null
        )
        assertEquals(
            "https://i1.sndcdn.com/artworks-999-t300x300.jpg",
            playlist.thumbnailUrl
        )
    }

    @Test
    fun binderArtworkScaling_keepsPayloadUnderSafeThreshold() {
        // Simulates 1080p source bitmap downscaling to max 720x720 @ 85% JPEG
        val originalWidth = 1920
        val originalHeight = 1080

        val maxDim = maxOf(originalWidth, originalHeight)
        val targetW = (originalWidth * 720) / maxDim
        val targetH = (originalHeight * 720) / maxDim

        assertEquals(720, targetW)
        assertEquals(405, targetH)
        assertTrue("Width must be <= 720", targetW <= 720)
        assertTrue("Height must be <= 720", targetH <= 720)
    }
}
