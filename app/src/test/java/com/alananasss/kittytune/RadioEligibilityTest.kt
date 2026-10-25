package com.alananasss.kittytune

import com.alananasss.kittytune.data.local.RadioEligibility
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioEligibilityTest {

    @Test
    fun `every provider is asked for its own radio`() {
        assertEquals(RadioEligibility.Kind.SOUND_CLOUD, RadioEligibility.radioKind(track(source = "soundcloud")))
        assertEquals(RadioEligibility.Kind.SPOTIFY, RadioEligibility.radioKind(track(source = "spotify")))
        assertEquals(RadioEligibility.Kind.YOUTUBE, RadioEligibility.radioKind(track(source = "youtube")))
        assertEquals(RadioEligibility.Kind.TIDAL, RadioEligibility.radioKind(track(source = "tidal")))
        assertEquals(RadioEligibility.Kind.DEEZER, RadioEligibility.radioKind(track(source = "deezer")))
        assertEquals(RadioEligibility.Kind.QOBUZ, RadioEligibility.radioKind(track(source = "qobuz")))
        assertFalse(RadioEligibility.canStartRadio(null))
    }

    @Test
    fun `a provider is recognised by its markers too`() {
        assertEquals(
            RadioEligibility.Kind.DEEZER,
            RadioEligibility.radioKind(track(permalink = "deezer:track:3135556")),
        )
        assertEquals(
            RadioEligibility.Kind.QOBUZ,
            RadioEligibility.radioKind(track(permalinkUrl = "https://open.qobuz.com/track/422042324")),
        )
        assertEquals(
            RadioEligibility.Kind.TIDAL,
            RadioEligibility.radioKind(track(permalinkUrl = "https://tidal.com/browse/track/5966783")),
        )
        assertEquals(
            RadioEligibility.Kind.SPOTIFY,
            RadioEligibility.radioKind(track(userUrn = "spotify:artist:4uLU6hMCjMI75M1A2tKUQC")),
        )
    }

    @Test
    fun `a soundcloud track keeps the soundcloud station`() {
        assertTrue(RadioEligibility.canStartRadio(track(source = "soundcloud")))
        assertEquals(RadioEligibility.Kind.SOUND_CLOUD, RadioEligibility.radioKind(track(source = "soundcloud")))
    }

    private fun track(
        source: String? = null,
        permalink: String? = null,
        permalinkUrl: String? = null,
        userUrn: String? = null,
    ) = Track(
        id = 1L,
        title = "Track",
        artworkUrl = null,
        durationMs = 0L,
        user = User(id = 1L, username = "Artist", avatarUrl = null, urn = userUrn),
        permalink = permalink,
        permalinkUrl = permalinkUrl,
        source = source,
    )
}
