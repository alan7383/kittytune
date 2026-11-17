package com.alananasss.kittytune.wear.common

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearPlaybackStateTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testSerializationRoundTrip() {
        val targets = listOf(
            TargetDeviceInfo("phone", "Pixel 8", WearConstants.TARGET_TYPE_PHONE, isConnected = true, isSelected = true),
            TargetDeviceInfo("mac-1", "MacBook Pro", WearConstants.TARGET_TYPE_DESKTOP, isConnected = true, isSelected = false),
        )

        val state = WearPlaybackState(
            trackId = 123456L,
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            artworkUrl = "https://example.com/art.jpg",
            isPlaying = true,
            isLoading = false,
            positionMs = 15000L,
            durationMs = 180000L,
            volume = 0.85f,
            targetDevice = "Pixel 8",
            targetType = WearConstants.TARGET_TYPE_PHONE,
            isLiked = true,
            shuffleEnabled = true,
            repeatMode = WearConstants.REPEAT_MODE_ALL,
            availableTargets = targets,
            timestamp = 1700000000000L,
        )

        val serialized = json.encodeToString(state)
        val deserialized = json.decodeFromString<WearPlaybackState>(serialized)

        assertEquals(state, deserialized)
        assertTrue(deserialized.isPhone)
        assertFalse(deserialized.isDesktop)
        assertTrue(deserialized.hasTrack)
    }

    @Test
    fun testProjectedPosition_whenPlaying_advancesMonotonically() {
        val baseTime = 100000L
        val state = WearPlaybackState(
            positionMs = 10000L,
            durationMs = 60000L,
            isPlaying = true,
            timestamp = baseTime,
        )

        // 5 seconds elapsed
        val projected = state.projectedPositionMs(nowMs = baseTime + 5000L)
        assertEquals(15000L, projected)

        // 100 seconds elapsed -> clamped to duration
        val clamped = state.projectedPositionMs(nowMs = baseTime + 100000L)
        assertEquals(60000L, clamped)
    }

    @Test
    fun testProjectedPosition_whenPaused_remainsStatic() {
        val baseTime = 100000L
        val state = WearPlaybackState(
            positionMs = 10000L,
            durationMs = 60000L,
            isPlaying = false,
            timestamp = baseTime,
        )

        // Time elapses while paused -> position should not advance
        val projected = state.projectedPositionMs(nowMs = baseTime + 20000L)
        assertEquals(10000L, projected)
    }

    @Test
    fun testProjectedPosition_whenPausedAndPositionExceedsDuration_clampedToDuration() {
        val baseTime = 100000L
        val state = WearPlaybackState(
            positionMs = 75000L,
            durationMs = 60000L,
            isPlaying = false,
            timestamp = baseTime,
        )

        // Position exceeding duration while paused must be clamped to duration
        val projected = state.projectedPositionMs(nowMs = baseTime + 20000L)
        assertEquals(60000L, projected)
        assertEquals(1.0f, state.progressFraction(nowMs = baseTime + 20000L), 0.001f)
    }

    @Test
    fun testProjectedPosition_negativePosition_clampedToZero() {
        val state = WearPlaybackState(
            positionMs = -5000L,
            durationMs = 60000L,
            isPlaying = false,
        )
        assertEquals(0L, state.projectedPositionMs())
        assertEquals(0.0f, state.progressFraction(), 0.001f)
    }

    @Test
    fun testProgressFraction() {
        val state = WearPlaybackState(
            positionMs = 30000L,
            durationMs = 60000L,
            isPlaying = false,
        )

        assertEquals(0.5f, state.progressFraction(), 0.001f)

        val zeroDurationState = WearPlaybackState(
            positionMs = 0L,
            durationMs = 0L,
        )
        assertEquals(0.0f, zeroDurationState.progressFraction(), 0.001f)
    }
}
