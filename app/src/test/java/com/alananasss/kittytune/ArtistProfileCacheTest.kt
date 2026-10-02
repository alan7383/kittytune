package com.alananasss.kittytune

import com.alananasss.kittytune.data.ArtistProfileCache
import org.junit.Assert.assertEquals
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
}
