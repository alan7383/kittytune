package com.alananasss.kittytune.wear.common

import com.google.android.gms.wearable.DataMap
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Empirical and adversarial challenge test suite for :wear-common.
 * Tests edge cases, boundary conditions, malformed byte streams, NaN/Infinity values,
 * clock skews, and unicode/emoji serialization.
 */
class WearCommonAdversarialTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    // =========================================================================
    // 1. Volume Codec Adversarial Tests
    // =========================================================================

    @Test
    fun testVolumeCodec_negativeVolumes_clampedToZero() {
        val negativeValues = listOf(-0.0001f, -0.5f, -1.0f, -100.0f, -Float.MAX_VALUE)
        for (neg in negativeValues) {
            val encoded = WearCodecs.encodeVolume(neg)
            val decoded = WearCodecs.decodeVolume(encoded)
            assertEquals("Encoded negative volume $neg should clamp to 0.0f", 0.0f, decoded, 0.0001f)
        }
    }

    @Test
    fun testVolumeCodec_overflowVolumes_clampedToOne() {
        val overflowValues = listOf(1.0001f, 1.5f, 2.0f, 100.0f, Float.MAX_VALUE)
        for (ov in overflowValues) {
            val encoded = WearCodecs.encodeVolume(ov)
            val decoded = WearCodecs.decodeVolume(encoded)
            assertEquals("Encoded overflow volume $ov should clamp to 1.0f", 1.0f, decoded, 0.0001f)
        }
    }

    @Test
    fun testVolumeCodec_infinities() {
        val posInfEncoded = WearCodecs.encodeVolume(Float.POSITIVE_INFINITY)
        val posInfDecoded = WearCodecs.decodeVolume(posInfEncoded)
        assertEquals(1.0f, posInfDecoded, 0.0001f)

        val negInfEncoded = WearCodecs.encodeVolume(Float.NEGATIVE_INFINITY)
        val negInfDecoded = WearCodecs.decodeVolume(negInfEncoded)
        assertEquals(0.0f, negInfDecoded, 0.0001f)
    }

    @Test
    fun testVolumeCodec_nanInput_sanitizedToZero() {
        // Verification: Passing Float.NaN to encodeVolume must safely coerce to 0.0f
        val nanEncoded = WearCodecs.encodeVolume(Float.NaN)
        val nanDecoded = WearCodecs.decodeVolume(nanEncoded)
        assertFalse("encodeVolume(Float.NaN) decoded must not be NaN", nanDecoded.isNaN())
        assertEquals("encodeVolume(Float.NaN) must sanitize to 0.0f", 0.0f, nanDecoded, 0.0f)

        // Directly passing raw IEEE 754 NaN bytes (0x7fc00000) to decodeVolume must sanitize to 0.0f
        val rawNanBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat(Float.NaN).array()
        val rawNanDecoded = WearCodecs.decodeVolume(rawNanBytes)
        assertFalse("decodeVolume(rawNanBytes) must not be NaN", rawNanDecoded.isNaN())
        assertEquals("decodeVolume(rawNanBytes) must sanitize to 0.0f", 0.0f, rawNanDecoded, 0.0f)
    }

    @Test
    fun testVolumeCodec_malformedByteArrays() {
        // Null and empty
        assertEquals(0.0f, WearCodecs.decodeVolume(null), 0.0f)
        assertEquals(0.0f, WearCodecs.decodeVolume(ByteArray(0)), 0.0f)

        // Non-4 byte lengths that are not valid numeric strings
        assertEquals(0.0f, WearCodecs.decodeVolume(byteArrayOf(0x01)), 0.0f)
        assertEquals(0.0f, WearCodecs.decodeVolume(byteArrayOf(0x01, 0x02)), 0.0f)
        assertEquals(0.0f, WearCodecs.decodeVolume(byteArrayOf(0x01, 0x02, 0x03)), 0.0f)
        assertEquals(0.0f, WearCodecs.decodeVolume(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)), 0.0f)
        assertEquals(0.0f, WearCodecs.decodeVolume(ByteArray(1024) { 0x55.toByte() }), 0.0f)

        // 4 bytes containing all 0xFF (NaN in float representation)
        val allOnes = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val allOnesDecoded = WearCodecs.decodeVolume(allOnes)
        assertFalse("4-byte 0xFF payload must not decode to NaN", allOnesDecoded.isNaN())
        assertEquals("4-byte 0xFF payload must sanitize to 0.0f", 0.0f, allOnesDecoded, 0.0f)
    }

    @Test
    fun testVolumeCodec_utf8NumericStringBoundaryInputs() {
        // String formats with signs and decimals
        assertEquals(0.0f, WearCodecs.decodeVolume("-0.5".toByteArray(Charsets.UTF_8)), 0.0001f)
        assertEquals(1.0f, WearCodecs.decodeVolume("+1.5".toByteArray(Charsets.UTF_8)), 0.0001f)
        assertEquals(0.0f, WearCodecs.decodeVolume("0".toByteArray(Charsets.UTF_8)), 0.0001f)
        assertEquals(1.0f, WearCodecs.decodeVolume("1".toByteArray(Charsets.UTF_8)), 0.0001f)
        assertEquals(0.5f, WearCodecs.decodeVolume("0.5000".toByteArray(Charsets.UTF_8)), 0.0001f)
        assertEquals(0.0f, WearCodecs.decodeVolume("0.0".toByteArray(Charsets.UTF_8)), 0.0001f)
        assertEquals(1.0f, WearCodecs.decodeVolume("1.0".toByteArray(Charsets.UTF_8)), 0.0001f)

        // Invalid numeric strings that pass character filter but fail toFloatOrNull
        assertEquals(0.0f, WearCodecs.decodeVolume("..".toByteArray(Charsets.UTF_8)), 0.0001f)
        assertEquals(0.0f, WearCodecs.decodeVolume("--".toByteArray(Charsets.UTF_8)), 0.0001f)
        assertEquals(0.0f, WearCodecs.decodeVolume(".-.".toByteArray(Charsets.UTF_8)), 0.0001f)
        assertEquals(0.0f, WearCodecs.decodeVolume("   ".toByteArray(Charsets.UTF_8)), 0.0001f)
        assertEquals(0.0f, WearCodecs.decodeVolume("NaN".toByteArray(Charsets.UTF_8)), 0.0001f)
        assertEquals(0.0f, WearCodecs.decodeVolume("Infinity".toByteArray(Charsets.UTF_8)), 0.0001f)
    }

    // =========================================================================
    // 2. Playhead Projection & Clock Skew Adversarial Tests
    // =========================================================================

    @Test
    fun testPlayheadProjection_clockSkewBackward_neverRewindsBelowPositionMs() {
        val baseTime = 1_000_000L
        val state = WearPlaybackState(
            positionMs = 30_000L,
            durationMs = 180_000L,
            isPlaying = true,
            timestamp = baseTime,
        )

        // Device clock skewed backwards: nowMs < timestamp
        val skewedEarlier = state.projectedPositionMs(nowMs = baseTime - 50_000L)
        assertEquals("Clock shifted backwards must not rewind position below snapshot positionMs", 30_000L, skewedEarlier)

        val skewedSlightly = state.projectedPositionMs(nowMs = baseTime - 1L)
        assertEquals(30_000L, skewedSlightly)
    }

    @Test
    fun testPlayheadProjection_pausedState_ignoresClockAdvance() {
        val baseTime = 1_000_000L
        val state = WearPlaybackState(
            positionMs = 25_000L,
            durationMs = 120_000L,
            isPlaying = false,
            timestamp = baseTime,
        )

        // When paused, position remains static even if hours pass
        val afterOneHour = state.projectedPositionMs(nowMs = baseTime + 3_600_000L)
        assertEquals(25_000L, afterOneHour)
        assertEquals(25_000L / 120_000L.toFloat(), state.progressFraction(nowMs = baseTime + 3_600_000L), 0.001f)
    }

    @Test
    fun testPlayheadProjection_zeroAndNegativeDuration() {
        val baseTime = 1_000_000L
        val zeroDuration = WearPlaybackState(
            positionMs = 10_000L,
            durationMs = 0L,
            isPlaying = true,
            timestamp = baseTime,
        )
        // With duration 0, projected position advances without crash
        val projected = zeroDuration.projectedPositionMs(nowMs = baseTime + 5_000L)
        assertEquals(15_000L, projected)
        // progressFraction must return 0.0f safely without division by zero NaN
        assertEquals(0.0f, zeroDuration.progressFraction(nowMs = baseTime + 5_000L), 0.0f)

        val negativeDuration = WearPlaybackState(
            positionMs = 10_000L,
            durationMs = -50_000L,
            isPlaying = true,
            timestamp = baseTime,
        )
        val negProjected = negativeDuration.projectedPositionMs(nowMs = baseTime + 5_000L)
        assertEquals(15_000L, negProjected)
        assertEquals(0.0f, negativeDuration.progressFraction(nowMs = baseTime + 5_000L), 0.0f)
    }

    @Test
    fun testPlayheadProjection_positionGreaterThanDuration_clampedWhenPaused() {
        val baseTime = 1_000_000L
        // Position exceeds duration (e.g. track ended or seeked past end)
        val pausedState = WearPlaybackState(
            positionMs = 70_000L,
            durationMs = 60_000L,
            isPlaying = false,
            timestamp = baseTime,
        )
        // Paused position exceeding durationMs must be clamped to durationMs
        assertEquals("Paused position exceeding durationMs must clamp to durationMs", 60_000L, pausedState.projectedPositionMs(nowMs = baseTime + 1000L))
        assertEquals("Default nowMs also clamps to durationMs", 60_000L, pausedState.projectedPositionMs())

        // When playing, it correctly clamps to durationMs
        val playingState = WearPlaybackState(
            positionMs = 70_000L,
            durationMs = 60_000L,
            isPlaying = true,
            timestamp = baseTime,
        )
        assertEquals(60_000L, playingState.projectedPositionMs(nowMs = baseTime + 1000L))
    }

    @Test
    fun testPlayheadProjection_negativePositionMs() {
        val baseTime = 1_000_000L
        val state = WearPlaybackState(
            positionMs = -5000L,
            durationMs = 60_000L,
            isPlaying = true,
            timestamp = baseTime,
        )
        // Negative position should be coerced to at least 0L
        val projected = state.projectedPositionMs(nowMs = baseTime + 2000L)
        assertEquals(2000L, projected)
    }

    @Test
    fun testPlayheadProjection_uninitializedOrZeroTimestamp() {
        val state = WearPlaybackState(
            positionMs = 15_000L,
            durationMs = 60_000L,
            isPlaying = true,
            timestamp = 0L, // uninitialized
        )
        // Should not project if timestamp is 0 or negative
        assertEquals(15_000L, state.projectedPositionMs(nowMs = 500_000L))

        val negTimeState = state.copy(timestamp = -100L)
        assertEquals(15_000L, negTimeState.projectedPositionMs(nowMs = 500_000L))
    }

    // =========================================================================
    // 3. Serialization Round-Trip with Unicode, Emojis, Special Characters
    // =========================================================================

    @Test
    fun testSerialization_unicodeAndEmojis() {
        val exoticState = WearPlaybackState(
            trackId = 9876543210123L,
            title = "🐱 Kitty Groove 🎵 (Remix) [feat. 🐶 Doggie & 🦊 Fox] - 100% 🐾",
            artist = "米津玄師 (Kenshi Yonezu) & 坂本龍一 / سلام / שלום / Привет / 🎧",
            album = "💖 Kawaii Hits Vol. 99 🌸✨ <Special \"Anniversary\" Edition & Album!>",
            artworkUrl = "https://example.com/art?artist=米津玄師&track=🐱%20Groove&token=xyz#header",
            isPlaying = true,
            isLoading = false,
            positionMs = 45_000L,
            durationMs = 240_000L,
            volume = 0.77f,
            targetDevice = "MacBook Pro (16-inch, 2024) 💻",
            targetType = WearConstants.TARGET_TYPE_DESKTOP,
            targetDeviceId = "mac-peer-🚀-uuid-9999",
            isLiked = true,
            shuffleEnabled = true,
            repeatMode = WearConstants.REPEAT_MODE_ONE,
            availableTargets = listOf(
                TargetDeviceInfo("phone", "Phone 📱", WearConstants.TARGET_TYPE_PHONE, isConnected = true, isSelected = false),
                TargetDeviceInfo("mac-peer-🚀-uuid-9999", "MacBook Pro (16-inch, 2024) 💻", WearConstants.TARGET_TYPE_DESKTOP, isConnected = true, isSelected = true),
                TargetDeviceInfo("win-pc-01", "Gaming Rig 🖥️ (Wired LAN)", WearConstants.TARGET_TYPE_DESKTOP, isConnected = false, isSelected = false),
            ),
            timestamp = 1730000000000L,
        )

        val serializedJson = json.encodeToString(exoticState)
        val deserialized = json.decodeFromString<WearPlaybackState>(serializedJson)

        assertEquals(exoticState, deserialized)
        assertEquals(exoticState.title, deserialized.title)
        assertEquals(exoticState.artist, deserialized.artist)
        assertEquals(exoticState.album, deserialized.album)
        assertEquals(exoticState.targetDevice, deserialized.targetDevice)
        assertEquals(exoticState.availableTargets.size, deserialized.availableTargets.size)
        assertEquals(exoticState.availableTargets[1].name, deserialized.availableTargets[1].name)
    }

    @Test
    fun testSerialization_controlCharactersAndEscapeSequences() {
        val specialState = WearPlaybackState(
            trackId = 1L,
            title = "Line1\nLine2\rLine3\tTabbed \"Quotes\" and \\backslashes\\",
            artist = "Artist with \u0000 null byte, \u001F control and \uFFFF end unicode",
            album = "Album with XML/HTML <tags id=\"123\"> & entities &amp; &lt;",
        )

        val jsonStr = json.encodeToString(specialState)
        val decoded = json.decodeFromString<WearPlaybackState>(jsonStr)

        assertEquals(specialState.title, decoded.title)
        assertEquals(specialState.artist, decoded.artist)
        assertEquals(specialState.album, decoded.album)
    }

    @Test
    fun testSerialization_extremePayloadSize() {
        val hugeTitle = "A".repeat(10_000)
        val hugeArtist = "B".repeat(10_000)
        val hundredTargets = (1..100).map { i ->
            TargetDeviceInfo("device-$i", "Target Device #$i (Zone $i) 🖥️", WearConstants.TARGET_TYPE_DESKTOP)
        }

        val largeState = WearPlaybackState(
            title = hugeTitle,
            artist = hugeArtist,
            availableTargets = hundredTargets,
        )

        val serialized = json.encodeToString(largeState)
        val decoded = json.decodeFromString<WearPlaybackState>(serialized)

        assertEquals(10_000, decoded.title.length)
        assertEquals(10_000, decoded.artist.length)
        assertEquals(100, decoded.availableTargets.size)
    }

    @Test
    fun testTargetSelectionCodec_adversarialInputs() {
        // Null and empty
        assertNull(WearCodecs.decodeTarget(null))
        assertNull(WearCodecs.decodeTarget(ByteArray(0)))
        assertNull(WearCodecs.decodeTarget("   ".toByteArray(Charsets.UTF_8)))

        // JSON with exotic unicode peer names
        val exoticPayload = TargetSelectionPayload(
            targetType = WearConstants.TARGET_TYPE_DESKTOP,
            peerId = "peer-🔑-42",
            peerName = "Studio Laptop 💻 (Floor 2)",
        )
        val encoded = WearCodecs.encodeTarget(exoticPayload)
        val decoded = WearCodecs.decodeTarget(encoded)
        assertNotNull(decoded)
        assertEquals(exoticPayload.targetType, decoded?.targetType)
        assertEquals(exoticPayload.peerId, decoded?.peerId)
        assertEquals(exoticPayload.peerName, decoded?.peerName)

        // Fallback strings
        val phoneFallback = WearCodecs.decodeTarget("PHONE".toByteArray(Charsets.UTF_8))
        assertEquals(WearConstants.TARGET_TYPE_PHONE, phoneFallback?.targetType)

        val desktopColonFallback = WearCodecs.decodeTarget("desktop:office-pc-1".toByteArray(Charsets.UTF_8))
        assertEquals(WearConstants.TARGET_TYPE_DESKTOP, desktopColonFallback?.targetType)
        assertEquals("office-pc-1", desktopColonFallback?.peerId)

        // Arbitrary unknown string fallback
        val unknownFallback = WearCodecs.decodeTarget("custom-device-id".toByteArray(Charsets.UTF_8))
        assertEquals(WearConstants.TARGET_TYPE_PHONE, unknownFallback?.targetType)
        assertEquals("custom-device-id", unknownFallback?.peerId)
    }

    @Test
    fun testTargetsListCodec_adversarialInputs() {
        // Malformed JSON strings
        val malformedDecoded = WearCodecs.decodeTargetsList("{ invalid json [}")
        assertTrue(malformedDecoded.isEmpty())

        val emptyDecoded = WearCodecs.decodeTargetsList("")
        assertTrue(emptyDecoded.isEmpty())

        val nullDecoded = WearCodecs.decodeTargetsList(null)
        assertTrue(nullDecoded.isEmpty())

        val whitespaceDecoded = WearCodecs.decodeTargetsList("   \n\t  ")
        assertTrue(whitespaceDecoded.isEmpty())
    }

    // =========================================================================
    // 4. DataMap Mapper Adversarial & Edge Case Tests
    // =========================================================================

    @Test
    fun testWearDataMapMapper_roundTripWithFullState() {
        val state = WearPlaybackState(
            trackId = 555666777L,
            title = "DataMap Track 🎵",
            artist = "DataMap Artist 🐱",
            album = "DataMap Album 💿",
            artworkUrl = "https://example.com/art.png",
            isPlaying = true,
            isLoading = false,
            positionMs = 12345L,
            durationMs = 99999L,
            volume = 0.65f,
            targetDevice = "MacBook Air 💻",
            targetType = WearConstants.TARGET_TYPE_DESKTOP,
            targetDeviceId = "air-01",
            isLiked = true,
            shuffleEnabled = false,
            repeatMode = WearConstants.REPEAT_MODE_ALL,
            availableTargets = listOf(
                TargetDeviceInfo("phone", "Phone", WearConstants.TARGET_TYPE_PHONE, true, false),
                TargetDeviceInfo("air-01", "MacBook Air 💻", WearConstants.TARGET_TYPE_DESKTOP, true, true),
            ),
            timestamp = 1730005555000L,
        )

        val dataMap = DataMap()
        WearDataMapMapper.writeToDataMap(state, dataMap)
        val reconstructed = WearDataMapMapper.fromDataMap(dataMap)

        assertEquals(state.trackId, reconstructed.trackId)
        assertEquals(state.title, reconstructed.title)
        assertEquals(state.artist, reconstructed.artist)
        assertEquals(state.album, reconstructed.album)
        assertEquals(state.artworkUrl, reconstructed.artworkUrl)
        assertEquals(state.isPlaying, reconstructed.isPlaying)
        assertEquals(state.isLoading, reconstructed.isLoading)
        assertEquals(state.positionMs, reconstructed.positionMs)
        assertEquals(state.durationMs, reconstructed.durationMs)
        assertEquals(state.volume, reconstructed.volume, 0.001f)
        assertEquals(state.targetDevice, reconstructed.targetDevice)
        assertEquals(state.targetType, reconstructed.targetType)
        assertEquals(state.targetDeviceId, reconstructed.targetDeviceId)
        assertEquals(state.isLiked, reconstructed.isLiked)
        assertEquals(state.shuffleEnabled, reconstructed.shuffleEnabled)
        assertEquals(state.repeatMode, reconstructed.repeatMode)
        assertEquals(state.availableTargets.size, reconstructed.availableTargets.size)
        assertEquals(state.timestamp, reconstructed.timestamp)
    }

    @Test
    fun testWearDataMapMapper_nullAndEmptyDataMap() {
        val nullResult = WearDataMapMapper.fromDataMap(null)
        assertEquals(WearPlaybackState.EMPTY, nullResult)

        val emptyDataMap = DataMap()
        val emptyResult = WearDataMapMapper.fromDataMap(emptyDataMap)
        assertEquals(0L, emptyResult.trackId)
        assertEquals("", emptyResult.title)
        assertEquals("", emptyResult.artist)
        assertEquals("", emptyResult.album)
        assertNull(emptyResult.artworkUrl)
        assertFalse(emptyResult.isPlaying)
        assertFalse(emptyResult.isLoading)
        assertEquals(0L, emptyResult.positionMs)
        assertEquals(0L, emptyResult.durationMs)
        assertEquals(0.0f, emptyResult.volume, 0.0f)
        assertEquals(WearConstants.TARGET_PHONE_LABEL, emptyResult.targetDevice)
        assertEquals(WearConstants.TARGET_TYPE_PHONE, emptyResult.targetType)
        assertNull(emptyResult.targetDeviceId)
        assertFalse(emptyResult.isLiked)
        assertFalse(emptyResult.shuffleEnabled)
        assertEquals(WearConstants.REPEAT_MODE_NONE, emptyResult.repeatMode)
        assertTrue(emptyResult.availableTargets.isEmpty())
        assertEquals(0L, emptyResult.timestamp)
    }

    @Test
    fun testWearDataMapMapper_removesNullOptionalKeys() {
        val dataMap = DataMap()
        // First write with optional fields populated
        val stateWithOptionals = WearPlaybackState(
            artworkUrl = "https://example.com/art.jpg",
            targetDeviceId = "desktop-peer-1",
            timestamp = 1000L,
        )
        WearDataMapMapper.writeToDataMap(stateWithOptionals, dataMap)
        assertTrue(dataMap.containsKey(WearConstants.KEY_ARTWORK_URL))
        assertTrue(dataMap.containsKey(WearConstants.KEY_TARGET_DEVICE_ID))

        // Next write with optional fields null
        val stateWithoutOptionals = WearPlaybackState(
            artworkUrl = null,
            targetDeviceId = null,
            timestamp = 2000L,
        )
        WearDataMapMapper.writeToDataMap(stateWithoutOptionals, dataMap)
        assertFalse("Artwork URL key should be removed when null", dataMap.containsKey(WearConstants.KEY_ARTWORK_URL))
        assertFalse("Target Device ID key should be removed when null", dataMap.containsKey(WearConstants.KEY_TARGET_DEVICE_ID))

        val reconstructed = WearDataMapMapper.fromDataMap(dataMap)
        assertNull(reconstructed.artworkUrl)
        assertNull(reconstructed.targetDeviceId)
    }

    @Test
    fun testWearDataMapMapper_autoGeneratesTimestampIfZero() {
        val stateWithZeroTime = WearPlaybackState(timestamp = 0L)
        val dataMap = DataMap()
        val beforeMs = System.currentTimeMillis()
        WearDataMapMapper.writeToDataMap(stateWithZeroTime, dataMap)
        val afterMs = System.currentTimeMillis()

        val writtenTime = dataMap.getLong(WearConstants.KEY_TIMESTAMP)
        assertTrue("Timestamp should be auto-generated when 0L", writtenTime in beforeMs..afterMs)
    }
}
