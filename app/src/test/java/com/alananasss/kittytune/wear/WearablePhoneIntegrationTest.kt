package com.alananasss.kittytune.wear

import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.common.TargetSelectionPayload
import com.alananasss.kittytune.wear.common.WearCodecs
import com.alananasss.kittytune.wear.common.WearConstants
import com.alananasss.kittytune.wear.common.WearDataMapMapper
import com.alananasss.kittytune.wear.common.WearPlaybackState
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class FakeMessageEvent(
    private val path: String,
    private val data: ByteArray = ByteArray(0),
    private val sourceNodeId: String = "wear_watch_node_01",
    private val requestId: Int = 1001
) : MessageEvent {
    override fun getPath(): String = path
    override fun getData(): ByteArray = data
    override fun getSourceNodeId(): String = sourceNodeId
    override fun getRequestId(): Int = requestId
}

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
        assertEquals("/kittytune/playback/like_toggle", WearConstants.ACTION_LIKE_TOGGLE)
        assertEquals("/kittytune/playback/shuffle_toggle", WearConstants.ACTION_SHUFFLE_TOGGLE)
        assertEquals("/kittytune/playback/repeat_cycle", WearConstants.ACTION_REPEAT_CYCLE)
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

        // Null and empty byte array fallback
        assertEquals(0.0f, WearCodecs.decodeVolume(null), 0.001f)
        assertEquals(0.0f, WearCodecs.decodeVolume(ByteArray(0)), 0.001f)

        // UTF-8 string format fallback
        val stringPayload = "0.75".toByteArray(Charsets.UTF_8)
        assertEquals(0.75f, WearCodecs.decodeVolume(stringPayload), 0.001f)
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

        val dataMap = DataMap()
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
    fun testAllFourteenContractDataMapKeys() {
        val state = WearPlaybackState(
            trackId = 88888L,
            title = "Contract Test Track",
            artist = "Contract Artist",
            album = "Contract Album",
            artworkUrl = "https://kittytune.app/cover.jpg",
            isPlaying = true,
            isLoading = false,
            positionMs = 15000L,
            durationMs = 210000L,
            volume = 0.65f,
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
            timestamp = 1700005000000L
        )

        val dataMap = DataMap()
        WearDataMapMapper.writeToDataMap(state, dataMap)

        // Verify all 14 contract keys explicitly
        assertEquals(88888L, dataMap.getLong(WearConstants.KEY_TRACK_ID))
        assertEquals("Contract Test Track", dataMap.getString(WearConstants.KEY_TITLE))
        assertEquals("Contract Artist", dataMap.getString(WearConstants.KEY_ARTIST))
        assertEquals("Contract Album", dataMap.getString(WearConstants.KEY_ALBUM))
        assertEquals("https://kittytune.app/cover.jpg", dataMap.getString(WearConstants.KEY_ARTWORK_URL))
        assertTrue(dataMap.getBoolean(WearConstants.KEY_IS_PLAYING))
        assertFalse(dataMap.getBoolean(WearConstants.KEY_IS_LOADING))
        assertEquals(15000L, dataMap.getLong(WearConstants.KEY_POSITION_MS))
        assertEquals(210000L, dataMap.getLong(WearConstants.KEY_DURATION_MS))
        assertEquals(0.65f, dataMap.getFloat(WearConstants.KEY_VOLUME), 0.001f)
        assertEquals("Phone", dataMap.getString(WearConstants.KEY_TARGET_DEVICE))
        assertEquals(WearConstants.TARGET_TYPE_PHONE, dataMap.getString(WearConstants.KEY_TARGET_TYPE))
        assertTrue(dataMap.getBoolean(WearConstants.KEY_IS_LIKED))
        assertEquals(1700005000000L, dataMap.getLong(WearConstants.KEY_TIMESTAMP))

        // Additional protocol keys
        assertTrue(dataMap.getBoolean(WearConstants.KEY_SHUFFLE_ENABLED))
        val targetsStr = dataMap.getString(WearConstants.KEY_AVAILABLE_TARGETS)
        assertTrue(!targetsStr.isNullOrBlank())
    }

    @Test
    fun testVolumeCalculationScalingAndClamping() {
        // Standard phone stream: min=0, max=15
        val min0 = 0
        val max15 = 15
        fun computeSteps(min: Int, max: Int, ratio: Float): Int {
            return (min + ratio * (max - min)).roundToInt().coerceIn(min, max)
        }

        assertEquals(0, computeSteps(min0, max15, 0.0f))
        assertEquals(8, computeSteps(min0, max15, 0.5f))
        assertEquals(15, computeSteps(min0, max15, 1.0f))
        assertEquals(0, computeSteps(min0, max15, -0.5f))
        assertEquals(15, computeSteps(min0, max15, 1.5f))

        // Fine grained stream: min=0, max=100
        assertEquals(0, computeSteps(0, 100, 0.0f))
        assertEquals(25, computeSteps(0, 100, 0.25f))
        assertEquals(75, computeSteps(0, 100, 0.75f))
        assertEquals(100, computeSteps(0, 100, 1.0f))

        // Stream with non-zero minimum (API 28+): min=1, max=25
        val min1 = 1
        val max25 = 25
        assertEquals(1, computeSteps(min1, max25, 0.0f))
        assertEquals(13, computeSteps(min1, max25, 0.5f))
        assertEquals(25, computeSteps(min1, max25, 1.0f))
        assertEquals(1, computeSteps(min1, max25, -1.0f))
        assertEquals(25, computeSteps(min1, max25, 2.0f))
    }

    @Test
    fun testSmartPreviousThresholdLogic() {
        val threshold = WearableMessageReceiverService.SMART_PREVIOUS_THRESHOLD_MS
        assertEquals(3000L, threshold)

        // At or below 3000ms: skip to previous song
        val posAtBoundary = 3000L
        val shouldSeekStartAtBoundary = posAtBoundary > threshold
        assertFalse(shouldSeekStartAtBoundary)

        val posUnderBoundary = 1500L
        val shouldSeekStartUnder = posUnderBoundary > threshold
        assertFalse(shouldSeekStartUnder)

        val posZero = 0L
        val shouldSeekStartZero = posZero > threshold
        assertFalse(shouldSeekStartZero)

        // Above 3000ms: restart current track (seek to 0)
        val posJustOver = 3001L
        val shouldSeekStartOver = posJustOver > threshold
        assertTrue(shouldSeekStartOver)

        val posMiddle = 60000L
        val shouldSeekStartMiddle = posMiddle > threshold
        assertTrue(shouldSeekStartMiddle)
    }

    @Test
    fun testFakeMessageEventDispatchingAllActions() = runBlocking {
        val service = WearableMessageReceiverService()

        val emptyData = ByteArray(0)
        val allActions = listOf(
            WearConstants.ACTION_PLAY to emptyData,
            WearConstants.ACTION_PAUSE to emptyData,
            WearConstants.ACTION_PLAY_PAUSE to emptyData,
            WearConstants.ACTION_NEXT to emptyData,
            WearConstants.ACTION_PREVIOUS to emptyData,
            WearConstants.ACTION_VOLUME_UP to emptyData,
            WearConstants.ACTION_VOLUME_DOWN to emptyData,
            WearConstants.ACTION_SET_VOLUME to WearCodecs.encodeVolume(0.7f),
            WearConstants.ACTION_TARGET_SELECT to WearCodecs.encodeTarget(
                TargetSelectionPayload(WearConstants.TARGET_TYPE_PHONE, null, "Phone")
            ),
            WearConstants.ACTION_SYNC_REQUEST to emptyData,
            WearConstants.ACTION_LIKE_TOGGLE to emptyData,
            WearConstants.ACTION_SHUFFLE_TOGGLE to emptyData,
            WearConstants.ACTION_REPEAT_CYCLE to emptyData,
            "/kittytune/unknown_action" to emptyData
        )

        for ((action, data) in allActions) {
            val event = FakeMessageEvent(path = action, data = data)
            assertEquals(action, event.path)
            // handleMessage should process gracefully without unhandled exceptions
            service.handleMessage(event.path, event.data)
        }
    }

    @Test
    fun testPublisherStoppedStateStructure() {
        val stoppedState = WearPlaybackState.EMPTY.copy(
            volume = 0.5f,
            targetDevice = WearConstants.TARGET_PHONE_LABEL,
            targetType = WearConstants.TARGET_TYPE_PHONE,
            timestamp = 1700000000L
        )
        assertEquals(0L, stoppedState.trackId)
        assertEquals("", stoppedState.title)
        assertEquals("", stoppedState.artist)
        assertEquals("", stoppedState.album)
        assertNull(stoppedState.artworkUrl)
        assertFalse(stoppedState.isPlaying)
        assertFalse(stoppedState.isLoading)
        assertEquals(0L, stoppedState.positionMs)
        assertEquals(0L, stoppedState.durationMs)
        assertEquals(0.5f, stoppedState.volume, 0.001f)
        assertEquals(WearConstants.TARGET_PHONE_LABEL, stoppedState.targetDevice)
        assertEquals(WearConstants.TARGET_TYPE_PHONE, stoppedState.targetType)
        assertFalse(stoppedState.hasTrack)
    }

    @Test
    fun testClientSidePositionProjectionAndProgressFraction() {
        val basePosition = 30000L
        val duration = 120000L
        val timestamp = 1000L

        // When playing: playhead advances with elapsed time
        val playingState = WearPlaybackState(
            trackId = 1L,
            positionMs = basePosition,
            durationMs = duration,
            isPlaying = true,
            timestamp = timestamp
        )
        val nowMs = timestamp + 10000L // 10 seconds later
        assertEquals(40000L, playingState.projectedPositionMs(nowMs))
        assertEquals(40000f / 120000f, playingState.progressFraction(nowMs), 0.001f)

        // When paused: playhead stays frozen at positionMs
        val pausedState = playingState.copy(isPlaying = false)
        assertEquals(basePosition, pausedState.projectedPositionMs(nowMs))
        assertEquals(basePosition.toFloat() / duration.toFloat(), pausedState.progressFraction(nowMs), 0.001f)

        // When duration is 0 or negative: progress is 0.0f
        val zeroDurationState = playingState.copy(durationMs = 0L)
        assertEquals(0.0f, zeroDurationState.progressFraction(nowMs), 0.001f)
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
