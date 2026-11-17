package com.alananasss.kittytune

import com.alananasss.kittytune.data.vk.VkAudioDecoder
import com.alananasss.kittytune.data.vk.VkAudioItem
import com.alananasss.kittytune.data.vk.VkHashes
import com.alananasss.kittytune.data.vk.VkMore
import com.alananasss.kittytune.data.vk.VkPayload
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The masked-URL vectors below were produced with MeridiusCore's own `ExposeSource` algorithm
 * (`meridius-core/dist/core.js` from meridius-3.4.0.exe) and verified to round-trip there, so they
 * pin the Kotlin port to the reference behaviour.
 */
class VkAudioDecoderTest {

    private val target = "https://cs9-11v4.vkuseraudio.net/p8/6c1a2b3f4d5e6a/index.m3u8"

    @Test
    fun directUrlIsReturnedUnchanged() {
        val directUrl = "https://cs1-23.vkuseraudio.net/p1/sample_track.mp3"
        assertEquals(directUrl, VkAudioDecoder.exposeSource(directUrl))
    }

    @Test
    fun nullAndEmptyInputsAreSafe() {
        assertEquals("", VkAudioDecoder.exposeSource(null))
        assertEquals("", VkAudioDecoder.exposeSource(""))
        assertEquals("", VkAudioDecoder.exposeSource("   "))
    }

    @Test
    fun onlyAudioApiUnavailableCountsAsMasked() {
        assertFalse(VkAudioDecoder.isMaskedUrl("https://example.com/audio.mp3"))
        assertTrue(VkAudioDecoder.isMaskedUrl("https://m.vk.com/audio_api_unavailable.html?extra=123#456"))
        // A plain signed URL is directly playable and must not be treated as masked.
        assertFalse(VkAudioDecoder.isMaskedUrl("https://cs9.vkuseraudio.net/p8/index.m3u8?extra=abc"))
    }

    @Test
    fun decodesReverseOperation() {
        val masked = "https://vk.com/audio_api_unavailable.mp3?extra=" +
            "ohuZBs54zwrUAs9HnMu1zdrMm2iYytfJnI84Cc9Ozw4UB2LKDwfYzxn1A3yUnhyXmsO5C2mVlZPZChrOAa#DG"
        assertEquals(target, VkAudioDecoder.exposeSource(masked))
    }

    @Test
    fun decodesXorOperation() {
        val masked = "https://vk.com/audio_api_unavailable.mp3?extra=" +
            "iZ8/oZHXzgq0ohjMEN09F2u9id44lJKQpI8IjguLlJ9Ko3nKFsH6kNKPEc1/l34UFsPKiIuVlJnLjNG+CW#EaTl"
        assertEquals(target, VkAudioDecoder.exposeSource(masked))
    }

    @Test
    fun decodesSeededShuffleOperation() {
        val masked = "https://vk.com/audio_api_unavailable.mp3?extra=" +
            "AdHOl3j1zNuVBI9PAtjJyMeXlxzZns8Zzw84BMuXmtzOoNGUzs9Rm2rHC3rWy2vKlNbKyw11otz2nhmUna" +
            "#CWSZodq3mq"
        assertEquals(target, VkAudioDecoder.exposeSource(masked))
    }

    @Test
    fun decodesChainedOperations() {
        val masked = "https://vk.com/audio_api_unavailable.mp3?extra=" +
            "Ax8Lz2j+ydv+kwaHFZrLngK+zcr+pZb+mJaWFYiOnwSLz344pZm1ngGIygm8iJCLjYq4yImKixWNowuYoG" +
            "#DGLZcZKXodi3mZqjEaTr"
        assertEquals(target, VkAudioDecoder.exposeSource(masked))
    }

    /** The `i` operation mixes the *session* user id into the shuffle seed. */
    @Test
    fun decodesUserIdSeededShuffle() {
        val masked = "https://vk.com/audio_api_unavailable.mp3?extra=" +
            "AgSUBJHLCZfLzxG2Btv2nc92BMmOm2vVCMqXl3rHnMeVyNuUzJG5Dt0UDxnPl2nWmxnOzdiZAwfKCcOVDa" +
            "#AqSZodq3mq"
        assertEquals(target, VkAudioDecoder.exposeSource(masked, userId = 402154823L))
        // The wrong session user must not yield a plausible-looking URL.
        assertEquals(masked, VkAudioDecoder.exposeSource(masked, userId = 1L))
    }

    @Test
    fun malformedMaskedUrlFallsBackToTheOriginal() {
        val malformed = "https://m.vk.com/audio_api_unavailable.html?extra=invalid#format"
        assertEquals(malformed, VkAudioDecoder.exposeSource(malformed))
    }

    @Test
    fun hashBundleRoundTrips() {
        val raw = "addH/editH/actionH/deleteH/replaceH/urlH"
        val hashes = VkHashes.parse(raw)
        assertEquals("actionH", hashes.action)
        assertEquals("urlH", hashes.url)
        assertEquals(raw, hashes.raw)
    }

    @Test
    fun trackKeepsVkIdentityAndHashes() {
        val item = VkAudioItem(
            id = 123456L,
            ownerId = 78910L,
            url = "https://cs1-23.vkuseraudio.net/p1/track.mp3",
            title = "Test Title",
            performer = "Test Artist",
            durationSeconds = 180,
            hashes = VkHashes.parse("a/b/action/d/e/urlhash"),
            coverUrl = "https://sun9-1.userapi.com/s/v1/ig1/cover.jpg"
        )
        val track = item.toTrack()

        assertEquals("vk", track.source)
        assertEquals(180000L, track.durationMs)
        assertEquals("https://vk.com/audio78910_123456", track.permalinkUrl)
        assertEquals(78910L, track.user?.id)
        // The whole bundle travels with the track so reload_audios can refresh the stream later.
        assertEquals("a/b/action/d/e/urlhash", track.secretToken)
        assertEquals(1, track.media?.transcodings?.size)
    }

    @Test
    fun maskedUrlProducesNoPlayableTranscoding() {
        val item = VkAudioItem(
            id = 1L,
            ownerId = 2L,
            url = "",
            title = "Test",
            performer = "Artist",
            durationSeconds = 10
        )
        assertEquals(0, item.toTrack().media?.transcodings?.size)
    }

    @Test
    fun paginationCursorIsReadFromTheSectionObject() {
        val section = JSONObject(
            """{"sectionId":"audios_all_1234","next_from":"20/abcdef"}"""
        )
        val more = VkMore.from(section)
        assertEquals("audios_all_1234", more?.sectionId)
        assertEquals("20/abcdef", more?.nextFrom)
        assertTrue(more!!.isValid)
    }

    @Test
    fun paginationCursorIsAbsentOnTheLastPage() {
        assertNull(VkMore.from(JSONObject("""{"sectionId":"audios_all_1234"}""")))
    }

    /**
     * `act=section` puts the tracks at `payload[1][1]`, `act=load_section` at `payload[1][0]`.
     * Reading only the first shape made every VK playlist come back empty.
     */
    @Test
    fun trackListIsFoundInBothPayloadShapes() {
        val sectionShape = VkPayload.parse(
            """<!--{"payload":[0,[["<div/>"],{"playlist":{"list":[[1,2],[3,4]]}}]]}"""
        )
        assertEquals(2, sectionShape?.list?.length())

        val loadSectionShape = VkPayload.parse(
            """{"payload":[0,[{"playlist":{"list":[[1,2],[3,4],[5,6]]}},{}]]}"""
        )
        assertEquals(3, loadSectionShape?.list?.length())

        val flatLoadSection = VkPayload.parse("""{"payload":[0,[{"list":[[1,2]]},{}]]}""")
        assertEquals(1, flatLoadSection?.list?.length())
    }

    /** Structured data at `payload[1][0]` must not be handed to the HTML row scraper. */
    @Test
    fun objectPayloadIsNotTreatedAsMarkup() {
        val payload = VkPayload.parse("""{"payload":[0,[{"list":[[1,2]]},{}]]}""")
        assertEquals("", payload?.html)
    }

    @Test
    fun trackCodeTravelsAlongsideTheHashes() {
        val hashes = VkHashes.parse("add/edit/action/del/repl/urlh")
        val encoded = VkHashes.encode(hashes, "abc123")
        assertEquals("add/edit/action/del/repl/urlh|abc123", encoded)
        // Parsing the combined value must still yield clean hashes.
        assertEquals("action", VkHashes.parse(encoded).action)
        assertEquals("urlh", VkHashes.parse(encoded).url)
        assertEquals("abc123", VkHashes.trackCodeOf(encoded))
        assertEquals("", VkHashes.trackCodeOf("add/edit/action/del/repl/urlh"))
    }

    @Test
    fun htmlEntitiesAreFullyDecoded() {
        assertEquals("Rock & Roll", VkAudioItem.unescapeHtml("Rock &amp; Roll"))
        assertEquals("don't stop", VkAudioItem.unescapeHtml("don&#39;t stop"))
        assertEquals("naïve", VkAudioItem.unescapeHtml("na&#239;ve"))
        assertEquals("naïve", VkAudioItem.unescapeHtml("na&#xEF;ve"))
        assertEquals("a\"b", VkAudioItem.unescapeHtml("a&quot;b"))
        // A double-escaped ampersand must not become a quote.
        assertEquals("&#39;", VkAudioItem.unescapeHtml("&amp;#39;"))
    }
}
