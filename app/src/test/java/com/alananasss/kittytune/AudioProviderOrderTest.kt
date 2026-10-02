package com.alananasss.kittytune

import com.alananasss.kittytune.audio.providers.AudioProviderOrder
import com.alananasss.kittytune.audio.providers.AudioProviderOrderItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The traffic saver for Hi-Res providers: Qobuz / TIDAL / Deezer can be switched off to save
 * mobile data, while SoundCloud and YouTube Music must stay always available as base sources.
 */
class AudioProviderOrderTest {

    @Test
    fun `only hi-res providers are disableable`() {
        assertTrue(AudioProviderOrderItem.QOBUZ.isDisableable())
        assertTrue(AudioProviderOrderItem.TIDAL.isDisableable())
        assertTrue(AudioProviderOrderItem.DEEZER.isDisableable())
        assertFalse(AudioProviderOrderItem.YOUTUBE_MUSIC.isDisableable())
        assertFalse(AudioProviderOrderItem.SOUNDCLOUD.isDisableable())

        assertEquals(
            setOf(
                AudioProviderOrderItem.QOBUZ,
                AudioProviderOrderItem.TIDAL,
                AudioProviderOrderItem.DEEZER
            ),
            AudioProviderOrder.Disableable
        )
    }

    @Test
    fun `disabled set survives serialization`() {
        val disabled = setOf(AudioProviderOrderItem.TIDAL, AudioProviderOrderItem.QOBUZ)
        val raw = AudioProviderOrder.serializeDisabled(disabled)
        assertEquals(disabled, AudioProviderOrder.deserializeDisabled(raw))
    }

    @Test
    fun `base sources can never be serialized as disabled`() {
        val raw = AudioProviderOrder.serializeDisabled(
            setOf(
                AudioProviderOrderItem.SOUNDCLOUD,
                AudioProviderOrderItem.YOUTUBE_MUSIC
            )
        )
        assertTrue(AudioProviderOrder.deserializeDisabled(raw).isEmpty())
    }

    @Test
    fun `malformed and empty input yields an empty disabled set`() {
        assertTrue(AudioProviderOrder.deserializeDisabled(null).isEmpty())
        assertTrue(AudioProviderOrder.deserializeDisabled("").isEmpty())
        assertTrue(AudioProviderOrder.deserializeDisabled("garbage,,TIDALX").isEmpty())
        assertEquals(
            setOf(AudioProviderOrderItem.DEEZER),
            AudioProviderOrder.deserializeDisabled(" deezer , nope ")
        )
    }

    @Test
    fun `order serialization keeps every playback provider`() {
        val order = listOf(AudioProviderOrderItem.SOUNDCLOUD, AudioProviderOrderItem.DEEZER)
        val restored = AudioProviderOrder.deserialize(AudioProviderOrder.serialize(order))
        assertEquals(AudioProviderOrder.Default.toSet(), restored.toSet())
        assertEquals(
            AudioProviderOrderItem.SOUNDCLOUD,
            restored.first()
        )
    }
}
