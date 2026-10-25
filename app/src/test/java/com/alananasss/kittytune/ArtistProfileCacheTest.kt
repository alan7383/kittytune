package com.alananasss.kittytune

import com.alananasss.kittytune.data.ArtistProfileCache
import com.alananasss.kittytune.data.CachedArtistProfile
import com.alananasss.kittytune.data.sanitized
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.utils.AppUtils
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistProfileCacheTest {

    @Test
    fun normalizeKey_correctlyNormalizesSoundCloudAndSpotifyKeys() {
        assertEquals("sc:12345", ArtistProfileCache.normalizeKey("12345"))
        assertEquals("sc:12345", ArtistProfileCache.normalizeKey("profile:12345"))
        assertEquals("sc:12345", ArtistProfileCache.normalizeKey("sc:12345"))
        assertEquals("spotify:4Z8W4fKeB5YxbusRsdQVPb", ArtistProfileCache.normalizeKey("spotify:artist:4Z8W4fKeB5YxbusRsdQVPb"))
        assertEquals("name:radiohead", ArtistProfileCache.normalizeKey("Radiohead"))
    }

    @Test
    fun cachedArtistProfile_nullUserInJson_deserializesSafelyWithoutNpe() {
        val jsonWithoutUser = """{"isSpotify":true,"popularTracks":[]}"""
        val profile = Gson().fromJson(jsonWithoutUser, CachedArtistProfile::class.java)
        assertNull(profile.user)
        assertNull(profile.user?.username)

        val jsonWithNullUser = """{"user":null,"isSpotify":true}"""
        val profileNull = Gson().fromJson(jsonWithNullUser, CachedArtistProfile::class.java)
        assertNull(profileNull.user)
        assertNull(profileNull.user?.username)
    }

    @Test
    fun cachedArtistProfile_tracksDeserializeAsRealTrackObjects_notLinkedTreeMap() {
        val json = """{
            "user": {"id": 100, "username": "Twenty One Pilots"},
            "popularTracks": [
                {"id": 42, "title": "Stressed Out"},
                {"id": 43, "title": "Ride"}
            ],
            "albums": [
                {"id": 500, "title": "Blurryface"}
            ],
            "similarArtists": [
                {"id": 200, "username": "My Chemical Romance"}
            ]
        }"""
        val profile = AppUtils.gson.fromJson(json, CachedArtistProfile::class.java)
        assertNotNull(profile)
        assertEquals(2, profile.popularTracks.size)
        assertTrue("Element must be concrete Track", profile.popularTracks.first() is Track)
        assertEquals(42L, profile.popularTracks.first().id)
        assertEquals("Stressed Out", profile.popularTracks.first().title)

        assertEquals(1, profile.albums.size)
        assertTrue("Element must be concrete Playlist", profile.albums.first() is Playlist)
        assertEquals(500L, profile.albums.first().id)

        assertEquals(1, profile.similarArtists.size)
        assertTrue("Element must be concrete User", profile.similarArtists.first() is User)
        assertEquals(200L, profile.similarArtists.first().id)
    }

    @Test
    fun cachedArtistProfile_sanitized_cleansContaminatedOrCorruptedElements() {
        @Suppress("UNCHECKED_CAST")
        val corruptTracks = listOf(
            linkedMapOf("id" to 42L, "title" to "Corrupted"),
            Track(43L, "Valid Track", null, null, null)
        ) as List<Track>

        val profile = CachedArtistProfile(
            user = User(1L, "Artist", null),
            popularTracks = corruptTracks
        )
        val safe = profile.sanitized()
        assertEquals(1, safe.popularTracks.size)
        assertTrue(safe.popularTracks.first() is Track)
        assertEquals(43L, safe.popularTracks.first().id)
    }

    @Test
    fun cachedArtistProfile_deserializesBothCamelCaseAndSnakeCase() {
        val snakeCaseJson = """{
            "user": {"id": 100, "username": "Artist"},
            "popular_tracks": [{"id": 1, "title": "Song 1"}],
            "similar_artists": [{"id": 2, "username": "Artist 2"}],
            "is_spotify": true
        }"""
        val profile = AppUtils.gson.fromJson(snakeCaseJson, CachedArtistProfile::class.java)
        assertEquals(1, profile.popularTracks.size)
        assertEquals(1L, profile.popularTracks.first().id)
        assertEquals(1, profile.similarArtists.size)
        assertEquals(2L, profile.similarArtists.first().id)
        assertTrue(profile.isSpotify)
    }
}
