package com.alananasss.kittytune.wear

import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.common.TargetSelectionPayload
import com.alananasss.kittytune.wear.common.WearCodecs
import com.alananasss.kittytune.wear.common.WearConstants
import com.alananasss.kittytune.wear.common.WearDataMapMapper
import com.alananasss.kittytune.wear.common.WearPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearablePhoneIntegrationTest {

    @Test
    fun testWearConstantsContracts() {
        assertEquals("/kittytune/playback_state", WearConstants.PATH_PLAYBACK_STATE)
        assertEquals("/kittytune/playback/play", WearConstants.ACTION_PLAY)
        assertEquals("/kittytune/playback/pause", WearConstants.ACTION_PAUSE)
        assertEquals("/kittytune/playback/play_pause", WearConstants.ACTION_PLAY_PAUSE)
        assertEquals("/kittytune/playback/next", WearConstants.ACTION_NEXT)
        assertEquals("/kittytune/playback/previous", WearConstants.ACTION_PREVIOUS)
        assertEquals("/kittytune/playback/volume_up", WearConstants.ACTION_VOLUME_UP)
        assertEquals("/kittytune/playback/volume_down", WearConstants.ACTION_VOLUME_DOWN)
        assertEquals("/kittytune/playback/set_volume", WearConstants.ACTION_SET_VOLUME)
        assertEquals("/kittytune/target/select", WearConstants.ACTION_TARGET_SELECT)
        assertEquals("/kittytune/playback/sync_request", WearConstants.ACTION_SYNC_REQUEST)
    }

    @Test
    fun testVolumeCodecBoundariesAndSanitization() {
        // Zero
        val zeroBytes = WearCodecs.encodeVolume(0.0f)
        assertEquals(0.0f, WearCodecs.decodeVolume(zeroBytes), 0.001f)

        // Mid volume
        val halfBytes = WearCodecs.encodeVolume(0.5f)
        assertEquals(0.5f, WearCodecs.decodeVolume(halfBytes), 0.001f)

        // Max volume
        val fullBytes = WearCodecs.encodeVolume(1.0f)
        assertEquals(1.0f, WearCodecs.decodeVolume(fullBytes), 0.001f)

        // Out-of-bounds negative clamped to 0.0f
        val negBytes = WearCodecs.encodeVolume(-0.5f)
        assertEquals(0.0f, WearCodecs.decodeVolume(negBytes), 0.001f)

        // Out-of-bounds > 1.0 clamped to 1.0f
        val highBytes = WearCodecs.encodeVolume(1.8f)
        assertEquals(1.0f, WearCodecs.decodeVolume(highBytes), 0.001f)

        // NaN clamped to 0.0f
        val nanBytes = WearCodecs.encodeVolume(Float.NaN)
        assertEquals(0.0f, WearCodecs.decodeVolume(nanBytes), 0.001f)

        // Positive infinity clamped to 1.0f
        val infBytes = WearCodecs.encodeVolume(Float.POSITIVE_INFINITY)
        assertEquals(1.0f, WearCodecs.decodeVolume(infBytes), 0.001f)

        // Negative infinity clamped to 0.0f
        val negInfBytes = WearCodecs.encodeVolume(Float.NEGATIVE_INFINITY)
        assertEquals(0.0f, WearCodecs.decodeVolume(negInfBytes), 0.001f)
    }

    @Test
    fun testTargetSelectionPayloadEncodingDecoding() {
        // Phone target
        val phonePayload = TargetSelectionPayload(
            targetType = WearConstants.TARGET_TYPE_PHONE,
            peerId = null,
            peerName = WearConstants.TARGET_PHONE_LABEL
        )
        val phoneBytes = WearCodecs.encodeTarget(phonePayload)
        val decodedPhone = WearCodecs.decodeTarget(phoneBytes)
        assertNotNull(decodedPhone)
        assertEquals(WearConstants.TARGET_TYPE_PHONE, decodedPhone?.targetType)
        assertNull(decodedPhone?.peerId)
        assertEquals(WearConstants.TARGET_PHONE_LABEL, decodedPhone?.peerName)

        // Desktop target
        val desktopPayload = TargetSelectionPayload(
            targetType = WearConstants.TARGET_TYPE_DESKTOP,
            peerId = "room-desktop-abc",
            peerName = "MacBook Pro"
        )
        val desktopBytes = WearCodecs.encodeTarget(desktopPayload)
        val decodedDesktop = WearCodecs.decodeTarget(desktopBytes)
        assertNotNull(decodedDesktop)
        assertEquals(WearConstants.TARGET_TYPE_DESKTOP, decodedDesktop?.targetType)
        assertEquals("room-desktop-abc", decodedDesktop?.peerId)
        assertEquals("MacBook Pro", decodedDesktop?.peerName)
    }

    @Test
    fun testDataMapRoundTripWithTargets() {
        val phoneTarget = TargetDeviceInfo(
            id = WearConstants.TARGET_PHONE_ID,
            name = WearConstants.TARGET_PHONE_LABEL,
            type = WearConstants.TARGET_TYPE_PHONE,
            isConnected = true,
            isSelected = true
        )
        val desktopTarget = TargetDeviceInfo(
            id = "room-xyz",
            name = "Desktop Studio",
            type = WearConstants.TARGET_TYPE_DESKTOP,
            isConnected = true,
            isSelected = false
        )

        val originalState = WearPlaybackState(
            trackId = 12345L,
            title = "Chill Beats",
            artist = "Lofi Kitty",
            album = "Lo-Fi Volume 1",
            artworkUrl = "https://example.com/art.jpg",
            isPlaying = true,
            isLoading = false,
            positionMs = 45000L,
            durationMs = 180000L,
            volume = 0.75f,
            targetDevice = WearConstants.TARGET_PHONE_LABEL,
            targetType = WearConstants.TARGET_TYPE_PHONE,
            targetDeviceId = null,
            isLiked = true,
            shuffleEnabled = true,
            repeatMode = WearConstants.REPEAT_MODE_ONE,
            availableTargets = listOf(phoneTarget, desktopTarget),
            timestamp = 1700000000000L
        )

        val dataMap = com.google.android.gms.wearable.DataMap()
        WearDataMapMapper.writeToDataMap(originalState, dataMap)
        val reconstitutedState = WearDataMapMapper.fromDataMap(dataMap)

        assertEquals(originalState.trackId, reconstitutedState.trackId)
        assertEquals(originalState.title, reconstitutedState.title)
        assertEquals(originalState.artist, reconstitutedState.artist)
        assertEquals(originalState.album, reconstitutedState.album)
        assertEquals(originalState.artworkUrl, reconstitutedState.artworkUrl)
        assertTrue(reconstitutedState.isPlaying)
        assertFalse(reconstitutedState.isLoading)
        assertEquals(45000L, reconstitutedState.positionMs)
        assertEquals(180000L, reconstitutedState.durationMs)
        assertEquals(0.75f, reconstitutedState.volume, 0.001f)
        assertEquals(WearConstants.TARGET_PHONE_LABEL, reconstitutedState.targetDevice)
        assertEquals(WearConstants.TARGET_TYPE_PHONE, reconstitutedState.targetType)
        assertEquals(2, reconstitutedState.availableTargets.size)
        assertEquals("Desktop Studio", reconstitutedState.availableTargets[1].name)
        assertEquals(WearConstants.TARGET_TYPE_DESKTOP, reconstitutedState.availableTargets[1].type)
    }

    @Test
    fun testPublisherStateDefaults() {
        val defaultState = WearPlaybackState()
        assertEquals(0L, defaultState.trackId)
        assertEquals("", defaultState.title)
        assertEquals("", defaultState.artist)
        assertFalse(defaultState.isPlaying)
        assertFalse(defaultState.isLoading)
        assertEquals(0.0f, defaultState.volume, 0.001f)
        assertEquals(WearConstants.TARGET_PHONE_LABEL, defaultState.targetDevice)
        assertEquals(WearConstants.TARGET_TYPE_PHONE, defaultState.targetType)
    }
}
