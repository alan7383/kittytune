package com.alananasss.kittytune

import com.alananasss.kittytune.data.cover.AppleMusicCanvasProvider
import com.alananasss.kittytune.data.cover.CanvasIndex
import com.alananasss.kittytune.data.cover.CanvasMatchEntry
import com.alananasss.kittytune.data.cover.CanvasMatchTier
import com.alananasss.kittytune.data.cover.ProviderIsrc
import com.alananasss.kittytune.data.cover.TidalAnimatedCoverProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedCoverResolverTest {

    @Test
    fun testProviderIsrcNormalization() {
        assertEquals("USUM71900012", ProviderIsrc.normalize("US-UM7-19-00012"))
        assertEquals("USUM71900012", ProviderIsrc.normalize("  us.um7.19.00012  "))
        assertNull(ProviderIsrc.normalize("INVALID_ISRC"))
        assertNull(ProviderIsrc.normalize(""))
        assertNull(ProviderIsrc.normalize(null))

        assertTrue(ProviderIsrc.isValid("GB-AYE-06-00960"))
        assertFalse(ProviderIsrc.isValid("not_an_isrc"))

        val firstValid = ProviderIsrc.firstOf("bad", null, "GB-AYE-06-00960", "USUM71900012")
        assertEquals("GBAYE0600960", firstValid)
    }

    @Test
    fun testTidalVideoUrlFormatting() {
        val hash = "e9e126a9-97b6-4f99-b7b6-c242cb6eb31d"
        val expected = "https://resources.tidal.com/videos/e9e126a9/97b6/4f99/b7b6/c242cb6eb31d/1280x1280.mp4"
        assertEquals(expected, TidalAnimatedCoverProvider.formatTidalVideoUrl(hash))

        val directUrl = "https://example.com/custom_video.mp4"
        assertEquals(directUrl, TidalAnimatedCoverProvider.formatTidalVideoUrl(directUrl))
    }

    @Test
    fun testCanvasIndexCaching() {
        val entry = CanvasMatchEntry(
            isrc = "USUM71900012",
            appleCatalogId = "123456",
            title = "Levitating",
            artist = "Dua Lipa",
            album = "Future Nostalgia",
            durationMs = 203000L,
            sourceUrl = "https://example.com/video.m3u8",
            matchTier = CanvasMatchTier.ISRC_EXACT,
            confidence = 100,
            lastMatchedAtMs = System.currentTimeMillis()
        )
        CanvasIndex.put(entry)

        val retrievedByIsrc = CanvasIndex.getByIsrc("USUM71900012")
        assertNotNull(retrievedByIsrc)
        assertEquals("https://example.com/video.m3u8", retrievedByIsrc?.sourceUrl)

        val retrievedBySong = CanvasIndex.getBySongArtist("Levitating", "Dua Lipa")
        assertNotNull(retrievedBySong)
        assertEquals("USUM71900012", retrievedBySong?.isrc)
    }

    @Test
    fun testAppleMusicAmpUrlBuilder() {
        val url = AppleMusicCanvasProvider.buildAmpUrl(
            "https://amp-api.music.apple.com/v1/catalog/us/songs",
            mapOf("filter[isrc]" to "USUM71900012")
        )
        assertTrue(url.contains("filter%5Bisrc%5D=USUM71900012") || url.contains("filter[isrc]=USUM71900012"))
        assertTrue(url.contains("format%5Bresources%5D=map") || url.contains("format[resources]=map"))
        assertTrue(url.contains("fields%5Bsongs%5D") || url.contains("fields[songs]"))
    }

    @Test
    fun testAppleMusicMotionExtractionFromAttributes() {
        val jsonString = """
            {
                "attributes": {
                    "editorialVideo": {
                        "motionDetailSquare": {
                            "video": "https://mvod.itunes.apple.com/square.m3u8"
                        },
                        "motionDetailTall": {
                            "video": "https://mvod.itunes.apple.com/tall.m3u8"
                        }
                    }
                }
            }
        """.trimIndent()
        val item = JSONObject(jsonString)

        val square = AppleMusicCanvasProvider.searchItem(item, AppleMusicCanvasProvider.CanvasAspectPreference.SQUARE)
        assertEquals("https://mvod.itunes.apple.com/square.m3u8", square)

        val tall = AppleMusicCanvasProvider.searchItem(item, AppleMusicCanvasProvider.CanvasAspectPreference.TALL)
        assertEquals("https://mvod.itunes.apple.com/tall.m3u8", tall)
    }

    @Test
    fun testAppleMusicTokenFallback() {
        assertTrue(AppleMusicCanvasProvider.FALLBACK_TOKEN.isNotBlank())
        assertTrue(AppleMusicCanvasProvider.FALLBACK_TOKEN.startsWith("eyJ"))
        val parts = AppleMusicCanvasProvider.FALLBACK_TOKEN.split(".")
        assertEquals(3, parts.size)

        // Verify runBlocking { getToken() } returns non-null valid JWT
        val token = kotlinx.coroutines.runBlocking { AppleMusicCanvasProvider.getToken() }
        assertNotNull(token)
        assertTrue(token.startsWith("eyJ"))
    }

    @Test
    fun testAppleMusicArtistBackgroundProvider() {
        // Blank or invalid artist name returns null without network overhead
        val emptyResult = kotlinx.coroutines.runBlocking {
            com.alananasss.kittytune.data.cover.AppleMusicArtistBackgroundProvider.getByArtistName("")
        }
        assertNull(emptyResult)
    }
}
