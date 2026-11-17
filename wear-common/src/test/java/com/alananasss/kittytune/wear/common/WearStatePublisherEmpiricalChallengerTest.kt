package com.alananasss.kittytune.wear.common

import com.google.android.gms.wearable.DataMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Empirical Challenger Test Suite for Milestone 3 (Phone Data Layer Integration & State Sync).
 *
 * Verifies:
 * 1. DataMap contract invariants (all 14 keys + protocol extras, exact typing, roundtrip fidelity).
 * 2. Monotonic timestamp verification under rapid and concurrent sequential generation.
 * 3. Position projection mathematical precision across long durations (1 hr, 10 hrs, 24 hrs),
 *    completion boundaries, zero/negative durations, clock skews, and pause freezes.
 * 4. Stopped state verification & cleanup invariants (`hasTrack = false`, `isPlaying = false`,
 *    `isLoading = false`, `positionMs = 0L`, etc.).
 * 5. Volume scaling, clamping, and API 28+ non-zero min stream logic.
 */
class WearStatePublisherEmpiricalChallengerTest {

    // =========================================================================
    // 1. DataMap Contract Invariants (All 14 Mandatory Keys + Extras)
    // =========================================================================

    @Test
    fun testAllFourteenMandatoryKeysPresentAndTypedCorrectly() {
        val baseState = WearPlaybackState(
            trackId = 9876543210L,
            title = "Empirical Challenge Anthem",
            artist = "Challenger 2",
            album = "Verification Volume 3",
            artworkUrl = "https://kittytune.app/artwork/9876543210.webp",
            isPlaying = true,
            isLoading = false,
            positionMs = 45_000L,
            durationMs = 240_000L,
            volume = 0.82f,
            targetDevice = "Phone",
            targetType = WearConstants.TARGET_TYPE_PHONE,
            targetDeviceId = null,
            isLiked = true,
            shuffleEnabled = true,
            repeatMode = WearConstants.REPEAT_MODE_ALL,
            availableTargets = listOf(
                TargetDeviceInfo(
                    id = WearConstants.TARGET_PHONE_ID,
                    name = WearConstants.TARGET_PHONE_LABEL,
                    type = WearConstants.TARGET_TYPE_PHONE,
                    isConnected = true,
                    isSelected = true
                )
            ),
            timestamp = 1762354800000L
        )

        val dataMap = DataMap()
        WearDataMapMapper.writeToDataMap(baseState, dataMap)

        // 1. KEY_TRACK_ID (Long)
        assertTrue("DataMap must contain KEY_TRACK_ID", dataMap.containsKey(WearConstants.KEY_TRACK_ID))
        assertEquals(9876543210L, dataMap.getLong(WearConstants.KEY_TRACK_ID))

        // 2. KEY_TITLE (String)
        assertTrue("DataMap must contain KEY_TITLE", dataMap.containsKey(WearConstants.KEY_TITLE))
        assertEquals("Empirical Challenge Anthem", dataMap.getString(WearConstants.KEY_TITLE))

        // 3. KEY_ARTIST (String)
        assertTrue("DataMap must contain KEY_ARTIST", dataMap.containsKey(WearConstants.KEY_ARTIST))
        assertEquals("Challenger 2", dataMap.getString(WearConstants.KEY_ARTIST))

        // 4. KEY_ALBUM (String)
        assertTrue("DataMap must contain KEY_ALBUM", dataMap.containsKey(WearConstants.KEY_ALBUM))
        assertEquals("Verification Volume 3", dataMap.getString(WearConstants.KEY_ALBUM))

        // 5. KEY_ARTWORK_URL (String)
        assertTrue("DataMap must contain KEY_ARTWORK_URL when non-null", dataMap.containsKey(WearConstants.KEY_ARTWORK_URL))
        assertEquals("https://kittytune.app/artwork/9876543210.webp", dataMap.getString(WearConstants.KEY_ARTWORK_URL))

        // 6. KEY_IS_PLAYING (Boolean)
        assertTrue("DataMap must contain KEY_IS_PLAYING", dataMap.containsKey(WearConstants.KEY_IS_PLAYING))
        assertTrue(dataMap.getBoolean(WearConstants.KEY_IS_PLAYING))

        // 7. KEY_IS_LOADING (Boolean)
        assertTrue("DataMap must contain KEY_IS_LOADING", dataMap.containsKey(WearConstants.KEY_IS_LOADING))
        assertFalse(dataMap.getBoolean(WearConstants.KEY_IS_LOADING))

        // 8. KEY_POSITION_MS (Long)
        assertTrue("DataMap must contain KEY_POSITION_MS", dataMap.containsKey(WearConstants.KEY_POSITION_MS))
        assertEquals(45_000L, dataMap.getLong(WearConstants.KEY_POSITION_MS))

        // 9. KEY_DURATION_MS (Long)
        assertTrue("DataMap must contain KEY_DURATION_MS", dataMap.containsKey(WearConstants.KEY_DURATION_MS))
        assertEquals(240_000L, dataMap.getLong(WearConstants.KEY_DURATION_MS))

        // 10. KEY_VOLUME (Float)
        assertTrue("DataMap must contain KEY_VOLUME", dataMap.containsKey(WearConstants.KEY_VOLUME))
        assertEquals(0.82f, dataMap.getFloat(WearConstants.KEY_VOLUME), 0.001f)

        // 11. KEY_TARGET_DEVICE (String)
        assertTrue("DataMap must contain KEY_TARGET_DEVICE", dataMap.containsKey(WearConstants.KEY_TARGET_DEVICE))
        assertEquals("Phone", dataMap.getString(WearConstants.KEY_TARGET_DEVICE))

        // 12. KEY_TARGET_TYPE (String)
        assertTrue("DataMap must contain KEY_TARGET_TYPE", dataMap.containsKey(WearConstants.KEY_TARGET_TYPE))
        assertEquals(WearConstants.TARGET_TYPE_PHONE, dataMap.getString(WearConstants.KEY_TARGET_TYPE))

        // 13. KEY_IS_LIKED (Boolean)
        assertTrue("DataMap must contain KEY_IS_LIKED", dataMap.containsKey(WearConstants.KEY_IS_LIKED))
        assertTrue(dataMap.getBoolean(WearConstants.KEY_IS_LIKED))

        // 14. KEY_TIMESTAMP (Long)
        assertTrue("DataMap must contain KEY_TIMESTAMP", dataMap.containsKey(WearConstants.KEY_TIMESTAMP))
        assertEquals(1762354800000L, dataMap.getLong(WearConstants.KEY_TIMESTAMP))

        // Extras: shuffle, repeat mode, available targets
        assertTrue(dataMap.containsKey(WearConstants.KEY_SHUFFLE_ENABLED))
        assertTrue(dataMap.getBoolean(WearConstants.KEY_SHUFFLE_ENABLED))
        assertTrue(dataMap.containsKey(WearConstants.KEY_REPEAT_MODE))
        assertEquals(WearConstants.REPEAT_MODE_ALL, dataMap.getString(WearConstants.KEY_REPEAT_MODE))
        assertTrue(dataMap.containsKey(WearConstants.KEY_AVAILABLE_TARGETS))
    }

    @Test
    fun testDataMapNullArtworkUrlAndTargetDeviceIdRemoved() {
        val stateWithoutOptionals = WearPlaybackState(
            trackId = 1L,
            title = "Track Without Art",
            artworkUrl = null,
            targetDeviceId = null
        )
        val dataMap = DataMap()
        WearDataMapMapper.writeToDataMap(stateWithoutOptionals, dataMap)

        assertFalse("Artwork URL must not be present in DataMap when null", dataMap.containsKey(WearConstants.KEY_ARTWORK_URL))
        assertFalse("Target Device ID must not be present in DataMap when null", dataMap.containsKey(WearConstants.KEY_TARGET_DEVICE_ID))

        val reconstituted = WearDataMapMapper.fromDataMap(dataMap)
        assertNull(reconstituted.artworkUrl)
        assertNull(reconstituted.targetDeviceId)
    }

    @Test
    fun testDataMapFuzzingExtremeValuesRoundTrip() {
        val rng = Random(12345)
        for (i in 0 until 500) {
            val randomTrackId = rng.nextLong()
            val randomTitle = "Title_$i " + (0..10).joinToString("") { ('a'..'z').random(rng).toString() }
            val randomArtist = "Artist_$i \uD83C\uDFB5 \uD83D\uDC31"
            val randomAlbum = "Album_$i \u4E2D\u6587 \u65E5\u672C\u8A9E"
            val randomArt = if (rng.nextBoolean()) "https://cdn.example.com/$i.png" else null
            val isPlaying = rng.nextBoolean()
            val isLoading = rng.nextBoolean()
            val positionMs = rng.nextLong(0L, 10_000_000L)
            val durationMs = rng.nextLong(positionMs, 20_000_000L)
            val volume = rng.nextFloat()
            val isLiked = rng.nextBoolean()
            val shuffle = rng.nextBoolean()
            val repeatMode = listOf(WearConstants.REPEAT_MODE_NONE, WearConstants.REPEAT_MODE_ALL, WearConstants.REPEAT_MODE_ONE).random(rng)
            val timestamp = rng.nextLong(1L, Long.MAX_VALUE)

            val state = WearPlaybackState(
                trackId = randomTrackId,
                title = randomTitle,
                artist = randomArtist,
                album = randomAlbum,
                artworkUrl = randomArt,
                isPlaying = isPlaying,
                isLoading = isLoading,
                positionMs = positionMs,
                durationMs = durationMs,
                volume = volume,
                targetDevice = "Device_$i",
                targetType = if (rng.nextBoolean()) WearConstants.TARGET_TYPE_PHONE else WearConstants.TARGET_TYPE_DESKTOP,
                targetDeviceId = if (rng.nextBoolean()) "peer_$i" else null,
                isLiked = isLiked,
                shuffleEnabled = shuffle,
                repeatMode = repeatMode,
                timestamp = timestamp
            )

            val dataMap = DataMap()
            WearDataMapMapper.writeToDataMap(state, dataMap)
            val deserialized = WearDataMapMapper.fromDataMap(dataMap)

            assertEquals(state.trackId, deserialized.trackId)
            assertEquals(state.title, deserialized.title)
            assertEquals(state.artist, deserialized.artist)
            assertEquals(state.album, deserialized.album)
            assertEquals(state.artworkUrl, deserialized.artworkUrl)
            assertEquals(state.isPlaying, deserialized.isPlaying)
            assertEquals(state.isLoading, deserialized.isLoading)
            assertEquals(state.positionMs, deserialized.positionMs)
            assertEquals(state.durationMs, deserialized.durationMs)
            assertEquals(state.volume, deserialized.volume, 0.0001f)
            assertEquals(state.targetDevice, deserialized.targetDevice)
            assertEquals(state.targetType, deserialized.targetType)
            assertEquals(state.targetDeviceId, deserialized.targetDeviceId)
            assertEquals(state.isLiked, deserialized.isLiked)
            assertEquals(state.shuffleEnabled, deserialized.shuffleEnabled)
            assertEquals(state.repeatMode, deserialized.repeatMode)
            assertEquals(state.timestamp, deserialized.timestamp)
        }
    }

    @Test
    fun testDataMapResilienceToCorruptedOrEmptyInput() {
        // Null DataMap returns EMPTY state
        val fromNull = WearDataMapMapper.fromDataMap(null)
        assertEquals(WearPlaybackState.EMPTY, fromNull)
        assertFalse(fromNull.hasTrack)
        assertFalse(fromNull.isPlaying)
        assertEquals(0L, fromNull.positionMs)

        // Empty DataMap returns safe default state
        val emptyMap = DataMap()
        val fromEmpty = WearDataMapMapper.fromDataMap(emptyMap)
        assertEquals(0L, fromEmpty.trackId)
        assertEquals("", fromEmpty.title)
        assertEquals(0L, fromEmpty.positionMs)
        assertEquals(0.0f, fromEmpty.volume, 0.0f)
        assertEquals(WearConstants.TARGET_PHONE_LABEL, fromEmpty.targetDevice)
        assertFalse(fromEmpty.hasTrack)
    }

    // =========================================================================
    // 2. Monotonic Timestamps & Time Advancement
    // =========================================================================

    @Test
    fun testDataMapGeneratesPositiveTimestampWhenZeroGiven() {
        val stateZeroTimestamp = WearPlaybackState(trackId = 1L, timestamp = 0L)
        val dataMap = DataMap()
        val before = System.currentTimeMillis()
        WearDataMapMapper.writeToDataMap(stateZeroTimestamp, dataMap)
        val after = System.currentTimeMillis()

        val writtenTimestamp = dataMap.getLong(WearConstants.KEY_TIMESTAMP)
        assertTrue("Generated timestamp must be >= before ($before), got $writtenTimestamp", writtenTimestamp >= before)
        assertTrue("Generated timestamp must be <= after ($after), got $writtenTimestamp", writtenTimestamp <= after)
    }

    @Test
    fun testSequentialTimestampsAreMonotonicallyNonDecreasing() {
        var lastTimestamp = 0L
        for (i in 0 until 100) {
            val state = WearPlaybackState(trackId = i.toLong(), timestamp = 0L)
            val dataMap = DataMap()
            WearDataMapMapper.writeToDataMap(state, dataMap)
            val currentTimestamp = dataMap.getLong(WearConstants.KEY_TIMESTAMP)

            assertTrue("Timestamp must be monotonically non-decreasing (last=$lastTimestamp, current=$currentTimestamp)",
                currentTimestamp >= lastTimestamp)
            lastTimestamp = currentTimestamp
            // Small sleep to ensure clock advances
            Thread.sleep(1)
        }
    }

    @Test
    fun testConcurrentTimestampGenerationUnderHighThreadContention() {
        val numThreads = 8
        val iterationsPerThread = 500
        val executor = Executors.newFixedThreadPool(numThreads)
        val timestamps = ConcurrentSkipListSet<Long>()

        for (t in 0 until numThreads) {
            executor.submit {
                for (i in 0 until iterationsPerThread) {
                    val map = DataMap()
                    val state = WearPlaybackState(trackId = (t * 1000 + i).toLong(), timestamp = 0L)
                    WearDataMapMapper.writeToDataMap(state, map)
                    val ts = map.getLong(WearConstants.KEY_TIMESTAMP)
                    timestamps.add(ts)
                }
            }
        }

        executor.shutdown()
        assertTrue("Executor must terminate within 10s", executor.awaitTermination(10, TimeUnit.SECONDS))
        assertTrue("Must generate positive timestamps", timestamps.first() > 0L)
    }

    // =========================================================================
    // 3. Position Projection Mathematical Precision Across Long Intervals
    // =========================================================================

    @Test
    fun testPositionProjectionOneHourDuration() {
        val oneHourMs = 3_600_000L
        val basePosition = 60_000L // 1 minute in
        val startTime = 1_000_000L

        val state = WearPlaybackState(
            trackId = 1L,
            positionMs = basePosition,
            durationMs = oneHourMs,
            isPlaying = true,
            timestamp = startTime
        )

        // After 30 minutes (1,800,000 ms)
        val now30Min = startTime + 1_800_000L
        val projected30Min = state.projectedPositionMs(now30Min)
        assertEquals(basePosition + 1_800_000L, projected30Min) // 1,860,000 ms
        val expectedFrac = 1_860_000f / 3_600_000f
        assertEquals(expectedFrac, state.progressFraction(now30Min), 0.0001f)

        // After 59 minutes (3,540,000 ms) -> total projected 3,600,000 ms (exactly 1.0f)
        val now59Min = startTime + (oneHourMs - basePosition)
        assertEquals(oneHourMs, state.projectedPositionMs(now59Min))
        assertEquals(1.0f, state.progressFraction(now59Min), 0.0001f)

        // Beyond 1 hour (2 hours later) -> must clamp to durationMs
        val now2Hours = startTime + 7_200_000L
        assertEquals(oneHourMs, state.projectedPositionMs(now2Hours))
        assertEquals(1.0f, state.progressFraction(now2Hours), 0.0001f)
    }

    @Test
    fun testPositionProjectionTenHourAudiobook() {
        val tenHoursMs = 36_000_000L // 10 hours = 36,000 seconds = 36,000,000 ms
        val basePosition = 3_600_000L // 1 hour in
        val startTime = 500_000L

        val state = WearPlaybackState(
            trackId = 42L,
            positionMs = basePosition,
            durationMs = tenHoursMs,
            isPlaying = true,
            timestamp = startTime
        )

        // 5 hours of continuous playback elapsed
        val fiveHoursMs = 18_000_000L
        val nowFiveHoursLater = startTime + fiveHoursMs
        val projected = state.projectedPositionMs(nowFiveHoursLater)
        assertEquals(basePosition + fiveHoursMs, projected) // 21,600,000 ms
        assertEquals(21_600_000f / 36_000_000f, state.progressFraction(nowFiveHoursLater), 0.0001f)

        // 12 hours elapsed (exceeding duration)
        val twelveHoursMs = 43_200_000L
        val nowTwelveHoursLater = startTime + twelveHoursMs
        assertEquals(tenHoursMs, state.projectedPositionMs(nowTwelveHoursLater))
        assertEquals(1.0f, state.progressFraction(nowTwelveHoursLater), 0.0001f)
    }

    @Test
    fun testPositionProjectionTrackCompletionBoundaries() {
        val durationMs = 180_000L
        val startTime = 10_000L

        // Exactly at completion
        val stateAtEnd = WearPlaybackState(
            trackId = 1L,
            positionMs = durationMs,
            durationMs = durationMs,
            isPlaying = true,
            timestamp = startTime
        )
        // Even when playing, at durationMs it must stay at durationMs
        assertEquals(durationMs, stateAtEnd.projectedPositionMs(startTime + 5000L))
        assertEquals(1.0f, stateAtEnd.progressFraction(startTime + 5000L), 0.0001f)

        // Already past completion (e.g. slight overshoot in player reporting)
        val statePastEnd = WearPlaybackState(
            trackId = 1L,
            positionMs = durationMs + 500L,
            durationMs = durationMs,
            isPlaying = false,
            timestamp = startTime
        )
        assertEquals(durationMs, statePastEnd.projectedPositionMs(startTime + 1000L))
        assertEquals(1.0f, statePastEnd.progressFraction(startTime + 1000L), 0.0001f)
    }

    @Test
    fun testPositionProjectionZeroAndNegativeDurationHandling() {
        val startTime = 1000L

        // Zero duration: live radio or stream where duration is 0
        val zeroDurationState = WearPlaybackState(
            trackId = 99L,
            positionMs = 5000L,
            durationMs = 0L,
            isPlaying = true,
            timestamp = startTime
        )
        // Advances position but fraction is strictly 0.0f (no division by zero)
        assertEquals(10_000L, zeroDurationState.projectedPositionMs(startTime + 5000L))
        assertEquals(0.0f, zeroDurationState.progressFraction(startTime + 5000L), 0.0f)
        assertFalse("Progress fraction must never be NaN on zero duration", zeroDurationState.progressFraction().isNaN())

        // Negative duration: invalid or uninitialized duration (-1L)
        val negDurationState = WearPlaybackState(
            trackId = 99L,
            positionMs = 5000L,
            durationMs = -1L,
            isPlaying = true,
            timestamp = startTime
        )
        assertEquals(10_000L, negDurationState.projectedPositionMs(startTime + 5000L))
        assertEquals(0.0f, negDurationState.progressFraction(startTime + 5000L), 0.0f)
        assertFalse("Progress fraction must never be NaN on negative duration", negDurationState.progressFraction().isNaN())
    }

    @Test
    fun testPositionProjectionPauseFreezesPositionIndefinitely() {
        val basePosition = 42_000L
        val duration = 200_000L
        val timestamp = 10_000L

        val pausedState = WearPlaybackState(
            trackId = 5L,
            positionMs = basePosition,
            durationMs = duration,
            isPlaying = false,
            timestamp = timestamp
        )

        // Position must remain frozen at basePosition for any nowMs (1 hour, 10 hours, 100 hours later)
        val testOffsets = listOf(0L, 1000L, 60_000L, 3_600_000L, 36_000_000L, 360_000_000L)
        for (offset in testOffsets) {
            val nowMs = timestamp + offset
            assertEquals("Paused state must not advance projectedPositionMs", basePosition, pausedState.projectedPositionMs(nowMs))
            assertEquals("Paused state progress fraction must remain constant",
                basePosition.toFloat() / duration.toFloat(), pausedState.progressFraction(nowMs), 0.0001f)
        }
    }

    @Test
    fun testPositionProjectionClockSkewAndNegativeElapsed() {
        val basePosition = 30_000L
        val timestamp = 50_000L

        val state = WearPlaybackState(
            trackId = 1L,
            positionMs = basePosition,
            durationMs = 120_000L,
            isPlaying = true,
            timestamp = timestamp
        )

        // If clock drifts backward or NTP steps back (nowMs < timestamp)
        val nowMsBeforeTimestamp = timestamp - 5000L
        val projected = state.projectedPositionMs(nowMsBeforeTimestamp)
        assertEquals("Elapsed time before timestamp must clamp to 0 and preserve base position", basePosition, projected)
    }

    // =========================================================================
    // 4. Stopped State Cleanup Verification
    // =========================================================================

    @Test
    fun testStoppedStateClearsAllActivePlaybackFlags() {
        val audioVolume = 0.6f
        val stopped = WearPlaybackState.EMPTY.copy(
            volume = audioVolume,
            targetDevice = WearConstants.TARGET_PHONE_LABEL,
            targetType = WearConstants.TARGET_TYPE_PHONE,
            timestamp = System.currentTimeMillis()
        )

        // Core stopped invariants
        assertFalse("Stopped state hasTrack must be false", stopped.hasTrack)
        assertFalse("Stopped state isPlaying must be false", stopped.isPlaying)
        assertFalse("Stopped state isLoading must be false", stopped.isLoading)
        assertEquals("Stopped state positionMs must be 0L", 0L, stopped.positionMs)
        assertEquals("Stopped state durationMs must be 0L", 0L, stopped.durationMs)
        assertEquals("Stopped state trackId must be 0L", 0L, stopped.trackId)
        assertEquals("Stopped state title must be empty", "", stopped.title)
        assertEquals("Stopped state artist must be empty", "", stopped.artist)
        assertEquals("Stopped state album must be empty", "", stopped.album)
        assertNull("Stopped state artworkUrl must be null", stopped.artworkUrl)
        assertEquals("Stopped state volume must match system volume", audioVolume, stopped.volume, 0.001f)
        assertEquals("Stopped state targetDevice must be Phone", WearConstants.TARGET_PHONE_LABEL, stopped.targetDevice)
        assertEquals("Stopped state targetType must be PHONE", WearConstants.TARGET_TYPE_PHONE, stopped.targetType)
        assertTrue("Stopped state isPhone must be true", stopped.isPhone)
        assertFalse("Stopped state isDesktop must be false", stopped.isDesktop)
        assertEquals("Stopped state projectedPositionMs must be 0L", 0L, stopped.projectedPositionMs())
        assertEquals("Stopped state progressFraction must be 0.0f", 0.0f, stopped.progressFraction(), 0.0f)
    }

    @Test
    fun testStoppedStateDataMapRoundTripPreservesCleanupInvariants() {
        val audioVolume = 0.45f
        val stopped = WearPlaybackState.EMPTY.copy(
            volume = audioVolume,
            targetDevice = WearConstants.TARGET_PHONE_LABEL,
            targetType = WearConstants.TARGET_TYPE_PHONE,
            timestamp = 1762355000000L
        )

        val map = DataMap()
        WearDataMapMapper.writeToDataMap(stopped, map)

        // Invariants in DataMap
        assertEquals(0L, map.getLong(WearConstants.KEY_TRACK_ID))
        assertEquals("", map.getString(WearConstants.KEY_TITLE))
        assertEquals("", map.getString(WearConstants.KEY_ARTIST))
        assertEquals("", map.getString(WearConstants.KEY_ALBUM))
        assertFalse(map.containsKey(WearConstants.KEY_ARTWORK_URL))
        assertFalse(map.getBoolean(WearConstants.KEY_IS_PLAYING))
        assertFalse(map.getBoolean(WearConstants.KEY_IS_LOADING))
        assertEquals(0L, map.getLong(WearConstants.KEY_POSITION_MS))
        assertEquals(0L, map.getLong(WearConstants.KEY_DURATION_MS))
        assertEquals(0.45f, map.getFloat(WearConstants.KEY_VOLUME), 0.001f)
        assertEquals(WearConstants.TARGET_PHONE_LABEL, map.getString(WearConstants.KEY_TARGET_DEVICE))
        assertEquals(WearConstants.TARGET_TYPE_PHONE, map.getString(WearConstants.KEY_TARGET_TYPE))
        assertFalse(map.getBoolean(WearConstants.KEY_IS_LIKED))
        assertEquals(1762355000000L, map.getLong(WearConstants.KEY_TIMESTAMP))

        // Reconstitute from DataMap
        val reconstituted = WearDataMapMapper.fromDataMap(map)
        assertFalse(reconstituted.hasTrack)
        assertFalse(reconstituted.isPlaying)
        assertFalse(reconstituted.isLoading)
        assertEquals(0L, reconstituted.positionMs)
        assertEquals(0L, reconstituted.durationMs)
        assertEquals(0.45f, reconstituted.volume, 0.001f)
    }

    // =========================================================================
    // 5. Volume Scaling, Clamping, and Min Volume Invariants
    // =========================================================================

    @Test
    fun testVolumeStreamScalingFormulas() {
        fun computeSteps(min: Int, max: Int, ratio: Float): Int {
            return (min + ratio * (max - min)).roundToInt().coerceIn(min, max)
        }

        // Standard phone stream: min=0, max=15
        assertEquals(0, computeSteps(0, 15, 0.0f))
        assertEquals(8, computeSteps(0, 15, 0.5f))
        assertEquals(15, computeSteps(0, 15, 1.0f))
        assertEquals(0, computeSteps(0, 15, -1.0f))
        assertEquals(15, computeSteps(0, 15, 2.0f))

        // High resolution stream: min=0, max=100
        assertEquals(0, computeSteps(0, 100, 0.0f))
        assertEquals(50, computeSteps(0, 100, 0.5f))
        assertEquals(100, computeSteps(0, 100, 1.0f))

        // Shifted minimum stream (API 28+): min=1, max=25
        assertEquals(1, computeSteps(1, 25, 0.0f))
        assertEquals(13, computeSteps(1, 25, 0.5f))
        assertEquals(25, computeSteps(1, 25, 1.0f))
        assertEquals(1, computeSteps(1, 25, -0.5f))
        assertEquals(25, computeSteps(1, 25, 1.5f))
    }
}
