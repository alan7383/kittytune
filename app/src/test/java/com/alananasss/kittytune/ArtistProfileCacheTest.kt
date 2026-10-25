package com.alananasss.kittytune

import com.alananasss.kittytune.data.ArtistProfileCache
import com.alananasss.kittytune.data.CachedArtistProfile
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
