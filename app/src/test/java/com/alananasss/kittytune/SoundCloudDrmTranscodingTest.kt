package com.alananasss.kittytune

import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.domain.Format
import com.alananasss.kittytune.domain.Transcoding
import com.alananasss.kittytune.domain.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundCloudDrmTranscodingTest {
    @Test
    fun `subscription metadata does not override account playback permission`() {
        val allowed = Track(
            id = 854420383L, title = "Example", artworkUrl = null,
            durationMs = 135000L, user = null,
            policy = "ALLOW", monetizationModel = "SUB_HIGH_TIER"
        )
        assertFalse(StreamResolver.isBlockedForAccount(allowed))
        assertTrue(StreamResolver.isBlockedForAccount(allowed.copy(policy = "SNIP")))
        assertTrue(StreamResolver.isBlockedForAccount(allowed.copy(policy = "BLOCK")))
    }

    private fun rendition(preset: String, protocol: String, snipped: Boolean = false) =
        Transcoding("https://example.test/$preset/$protocol", preset, Format(protocol, "audio/mp4"), snipped)

    @Test
    fun `encrypted only catalogue selects Widevine in quality order and excludes FairPlay`() {
        val tracks = listOf("aac_96k", "aac_160k", "abr_sq").flatMap { preset ->
            listOf(rendition(preset, "cbc-encrypted-hls"), rendition(preset, "ctr-encrypted-hls"))
        }
        for ((quality, expected) in listOf(
            "HIGH" to listOf("aac_160k", "aac_96k", "abr_sq"),
            "LOW" to listOf("aac_96k", "aac_160k", "abr_sq")
        )) {
            val selected = StreamResolver.buildTranscodingCandidates(tracks, quality, false, true)
            assertEquals(expected, selected.map { it.preset })
            assertTrue(selected.all { it.format?.protocol == "ctr-encrypted-hls" })
        }
    }

    @Test
    fun `analysis requiring clear audio cannot select encrypted renditions`() {
        val tracks = listOf(rendition("aac_160k", "ctr-encrypted-hls"))
        assertTrue(StreamResolver.buildTranscodingCandidates(tracks, "HIGH", false, false).isEmpty())
    }

    @Test
    fun `new Widevine presets are retained but previews are excluded`() {
        val full = rendition("aac_256k", "ctr-encrypted-hls")
        val preview = rendition("aac_160k", "ctr-encrypted-hls", snipped = true)
        assertEquals(listOf(full), StreamResolver.buildTranscodingCandidates(listOf(preview, full), "HIGH", false, true))
    }

    @Test
    fun `clear streams remain preferred when available`() {
        val clear = rendition("aac_160k", "hls")
        val encrypted = rendition("aac_160k", "ctr-encrypted-hls")
        assertEquals(listOf(clear, encrypted), StreamResolver.buildTranscodingCandidates(listOf(encrypted, clear), "HIGH", false, true))
    }
}
