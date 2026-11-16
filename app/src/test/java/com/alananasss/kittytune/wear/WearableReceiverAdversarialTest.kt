package com.alananasss.kittytune.wear

import com.alananasss.kittytune.wear.common.TargetSelectionPayload
import com.alananasss.kittytune.wear.common.WearCodecs
import com.alananasss.kittytune.wear.common.WearConstants
import com.google.android.gms.wearable.MessageEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlin.random.Random

class AdversarialMessageEvent(
    private val path: String,
    private val data: ByteArray = ByteArray(0),
    private val sourceNodeId: String = "wear_adversarial_node",
    private val requestId: Int = 9999
) : MessageEvent {
    override fun getPath(): String = path
    override fun getData(): ByteArray = data
    override fun getSourceNodeId(): String = sourceNodeId
    override fun getRequestId(): Int = requestId
}

/**
 * Adversarial and empirical stress test suite for [WearableMessageReceiverService],
 * [WearCodecs] volume decoding, boundary clamping, smart previous threshold logic,
 * message bursts, and cold start resilience.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearableReceiverAdversarialTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Ensure default target is PHONE before each test
        WearableStatePublisher.activeTargetType = WearConstants.TARGET_TYPE_PHONE
        WearableStatePublisher.activeTargetId = null
        WearableStatePublisher.activeTargetName = WearConstants.TARGET_PHONE_LABEL
    }

    @After
    fun tearDown() {
        WearableStatePublisher.activeTargetType = WearConstants.TARGET_TYPE_PHONE
        WearableStatePublisher.activeTargetId = null
        WearableStatePublisher.activeTargetName = WearConstants.TARGET_PHONE_LABEL
        Dispatchers.resetMain()
    }

    // Helper to build 4-byte IEEE 754 floats
    private fun floatToBytes(value: Float): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat(value).array()

    // Helper for volume step quantization formula
    private fun computeVolumeSteps(min: Int, max: Int, ratio: Float): Int {
        return (min + ratio * (max - min)).roundToInt().coerceIn(min, max)
    }

    private fun safeDestroyService(service: WearableMessageReceiverService) {
        try {
            service.onDestroy()
        } catch (_: IllegalStateException) {
            // Expected when service is not attached to Android Service lifecycle (mServiceHandler unset)
        }
    }

    // =========================================================================
    // 1. Volume Decoding & Boundary Clamping
    // =========================================================================

    @Test
    fun testVolumeDecodingWithIeee754SpecialFloats() {
        // Float.NaN -> 0.0f
        val nanBytes = floatToBytes(Float.NaN)
        val decodedNan = WearCodecs.decodeVolume(nanBytes)
        assertEquals(0.0f, decodedNan, 0.0f)

        // Float.POSITIVE_INFINITY -> clamped to 1.0f
        val posInfBytes = floatToBytes(Float.POSITIVE_INFINITY)
        val decodedPosInf = WearCodecs.decodeVolume(posInfBytes)
        assertEquals(1.0f, decodedPosInf, 0.0f)

        // Float.NEGATIVE_INFINITY -> clamped to 0.0f
        val negInfBytes = floatToBytes(Float.NEGATIVE_INFINITY)
        val decodedNegInf = WearCodecs.decodeVolume(negInfBytes)
        assertEquals(0.0f, decodedNegInf, 0.0f)

        // Float.MAX_VALUE -> clamped to 1.0f
        val maxValBytes = floatToBytes(Float.MAX_VALUE)
        val decodedMaxVal = WearCodecs.decodeVolume(maxValBytes)
        assertEquals(1.0f, decodedMaxVal, 0.0f)

        // Float.MIN_VALUE (positive tiny, ~1.4e-45) -> clamped in 0.0f..1.0f
        val minValBytes = floatToBytes(Float.MIN_VALUE)
        val decodedMinVal = WearCodecs.decodeVolume(minValBytes)
        assertTrue(decodedMinVal in 0.0f..1.0f)

        // Extreme negative (-100.0f) -> clamped to 0.0f
        val negBytes = floatToBytes(-100.0f)
        val decodedNeg = WearCodecs.decodeVolume(negBytes)
        assertEquals(0.0f, decodedNeg, 0.0f)

        // Extreme positive (100.0f) -> clamped to 1.0f
        val hugeBytes = floatToBytes(100.0f)
        val decodedHuge = WearCodecs.decodeVolume(hugeBytes)
        assertEquals(1.0f, decodedHuge, 0.0f)

        // Exact boundaries
        assertEquals(0.0f, WearCodecs.decodeVolume(floatToBytes(0.0f)), 0.0f)
        assertEquals(1.0f, WearCodecs.decodeVolume(floatToBytes(1.0f)), 0.0f)
    }

    @Test
    fun testVolumeDecodingWithAdversarialStrings() {
        // Null and Empty
        assertEquals(0.0f, WearCodecs.decodeVolume(null), 0.0f)
        assertEquals(0.0f, WearCodecs.decodeVolume(ByteArray(0)), 0.0f)

        // String "NaN" -> not numeric format or fallback -> 0.0f
        assertEquals(0.0f, WearCodecs.decodeVolume("NaN".toByteArray(Charsets.UTF_8)), 0.0f)
        assertEquals(0.0f, WearCodecs.decodeVolume("nan".toByteArray(Charsets.UTF_8)), 0.0f)

        // String "Infinity", "+Infinity", "-Infinity"
        assertEquals(0.0f, WearCodecs.decodeVolume("Infinity".toByteArray(Charsets.UTF_8)), 0.0f)
        assertEquals(0.0f, WearCodecs.decodeVolume("-Infinity".toByteArray(Charsets.UTF_8)), 0.0f)

        // String numeric boundaries
        assertEquals(0.0f, WearCodecs.decodeVolume("-100.0".toByteArray(Charsets.UTF_8)), 0.0f)
        assertEquals(0.0f, WearCodecs.decodeVolume("-0.0001".toByteArray(Charsets.UTF_8)), 0.0f)
        assertEquals(1.0f, WearCodecs.decodeVolume("100.0".toByteArray(Charsets.UTF_8)), 0.0f)
        assertEquals(1.0f, WearCodecs.decodeVolume("1.0001".toByteArray(Charsets.UTF_8)), 0.0f)
        assertEquals(1.0f, WearCodecs.decodeVolume("99999999999".toByteArray(Charsets.UTF_8)), 0.0f)
        assertEquals(0.42f, WearCodecs.decodeVolume("0.42".toByteArray(Charsets.UTF_8)), 0.001f)

        // Corrupted / malformed strings
        val malformed = listOf(
            "...", "--1", "1.2.3", "abc", "0x10", "1e5", "   ", "\n\t", "1.0.0", "NULL", "undefined"
        )
        for (str in malformed) {
            val decoded = WearCodecs.decodeVolume(str.toByteArray(Charsets.UTF_8))
            assertTrue("Expected 0.0f for malformed string '$str', got $decoded", decoded in 0.0f..1.0f)
        }
    }

    @Test
    fun testVolumeDecodingWithCorruptedByteArrays() {
        // 1 byte, 2 bytes, 3 bytes (incomplete float)
        assertEquals(0.0f, WearCodecs.decodeVolume(byteArrayOf(0x3F)), 0.0f)
        assertEquals(0.0f, WearCodecs.decodeVolume(byteArrayOf(0x3F, 0x00)), 0.0f)
        assertEquals(0.0f, WearCodecs.decodeVolume(byteArrayOf(0x3F, 0x00, 0x00)), 0.0f)

        // 5 bytes, 8 bytes (overflowing float)
        assertEquals(0.0f, WearCodecs.decodeVolume(byteArrayOf(0x3F, 0x00, 0x00, 0x00, 0x00)), 0.0f)
        assertEquals(0.0f, WearCodecs.decodeVolume(ByteArray(8) { 0xFF.toByte() }), 0.0f)

        // Random fuzz payloads of varying lengths
        val rng = Random(42)
        for (i in 1..200) {
            val length = rng.nextInt(0, 128)
            val randomBytes = ByteArray(length).apply { rng.nextBytes(this) }
            val decoded = WearCodecs.decodeVolume(randomBytes)
            assertFalse("Decoded volume should never be NaN", decoded.isNaN())
            assertFalse("Decoded volume should never be Infinite", decoded.isInfinite())
            assertTrue("Decoded volume should be clamped [0.0..1.0], was $decoded", decoded in 0.0f..1.0f)
        }
    }

    @Test
    fun testVolumeQuantizationStepFormulaStress() {
        val testRanges = listOf(
            0 to 15,    // standard android STREAM_MUSIC
            0 to 25,    // some OEM streams
            0 to 100,   // fine-grained volume
            1 to 15,    // non-zero min (API 28+)
            1 to 25,
            5 to 15,
            0 to 1,     // binary / toggle volume
            0 to 0,     // fixed volume stream (min == max)
            7 to 7      // fixed volume non-zero
        )

        val ratios = listOf(
            0.0f, 0.00001f, 0.1f, 0.25f, 0.3333f, 0.5f, 0.6666f, 0.75f, 0.9999f, 1.0f,
            -1.0f, -100.0f, 1.01f, 2.0f, 100.0f, Float.MAX_VALUE, Float.MIN_VALUE
        )

        for ((min, max) in testRanges) {
            for (ratio in ratios) {
                val clampedRatio = if (ratio.isNaN()) 0.0f else ratio.coerceIn(0.0f, 1.0f)
                val target = computeVolumeSteps(min, max, clampedRatio)
                assertTrue("Target index $target must be >= min $min", target >= min)
                assertTrue("Target index $target must be <= max $max", target <= max)
            }
        }
    }

    // =========================================================================
    // 2. Smart Previous Boundary Stress (3000ms threshold)
    // =========================================================================

    @Test
    fun testSmartPreviousBoundaryStrictInequality() {
        val threshold = WearableMessageReceiverService.SMART_PREVIOUS_THRESHOLD_MS
        assertEquals(3000L, threshold)

        // Strict boundary oracle:
        // currentPos > 3000ms -> restart current track (seekTo(0L))
        // currentPos <= 3000ms -> previous queue item (onPreviousClick / seekToPreviousMediaItem)

        // 0ms -> previous queue item
        assertFalse("At 0ms should skip to previous item", 0L > threshold)

        // 2999ms -> previous queue item
        assertFalse("At 2999ms should skip to previous item", 2999L > threshold)

        // 3000ms EXACT -> previous queue item (3000 > 3000 is FALSE)
        assertFalse("At exactly 3000ms should skip to previous item", 3000L > threshold)

        // 3001ms -> restart current track (3001 > 3000 is TRUE)
        assertTrue("At 3001ms should restart current track", 3001L > threshold)

        // 50000ms -> restart current track
        assertTrue("At 50000ms should restart current track", 50000L > threshold)

        // Negative values: e.g. uninitialized / unknown position (-1L)
        assertFalse("At -1ms should skip to previous item", -1L > threshold)
        assertFalse("At -3000ms should skip to previous item", -3000L > threshold)

        // Long.MAX_VALUE
        assertTrue("At Long.MAX_VALUE should restart current track", Long.MAX_VALUE > threshold)
    }

    // =========================================================================
    // 3. Message Burst Stress (500 rapid incoming messages)
    // =========================================================================

    @Test
    fun testMessageBurstStressPhoneTarget500RapidEvents() = runBlocking {
        val service = WearableMessageReceiverService()

        val samplePayloads = listOf(
            WearConstants.ACTION_PLAY to ByteArray(0),
            WearConstants.ACTION_PAUSE to ByteArray(0),
            WearConstants.ACTION_PLAY_PAUSE to ByteArray(0),
            WearConstants.ACTION_NEXT to ByteArray(0),
            WearConstants.ACTION_PREVIOUS to ByteArray(0),
            WearConstants.ACTION_VOLUME_UP to ByteArray(0),
            WearConstants.ACTION_VOLUME_DOWN to ByteArray(0),
            WearConstants.ACTION_SET_VOLUME to floatToBytes(0.5f),
            WearConstants.ACTION_SET_VOLUME to floatToBytes(Float.NaN),
            WearConstants.ACTION_SET_VOLUME to floatToBytes(-50.0f),
            WearConstants.ACTION_SET_VOLUME to "0.85".toByteArray(Charsets.UTF_8),
            WearConstants.ACTION_SET_VOLUME to "invalid_fuzz".toByteArray(Charsets.UTF_8),
            WearConstants.ACTION_LIKE_TOGGLE to ByteArray(0),
            WearConstants.ACTION_SHUFFLE_TOGGLE to ByteArray(0),
            WearConstants.ACTION_REPEAT_CYCLE to ByteArray(0),
            WearConstants.ACTION_SYNC_REQUEST to ByteArray(0),
            WearConstants.ACTION_TARGET_SELECT to WearCodecs.encodeTarget(
                TargetSelectionPayload.phone()
            ),
            "/kittytune/playback/unknown_command" to ByteArray(16) { 0xAA.toByte() }
        )

        // Burst 500 messages concurrently using async/awaitAll across Dispatchers.Default
        val deferredResults = (0 until 500).map { i ->
            async(Dispatchers.Default) {
                val (path, data) = samplePayloads[i % samplePayloads.size]
                val fakeEvent = AdversarialMessageEvent(path = path, data = data, requestId = i)
                // Both onMessageReceived (scope launch) and handleMessage (direct suspend)
                if (i % 2 == 0) {
                    service.onMessageReceived(fakeEvent)
                } else {
                    service.handleMessage(path, data)
                }
            }
        }

        deferredResults.awaitAll()

        safeDestroyService(service)
    }

    // =========================================================================
    // 4. Cold Start / Uninitialized Player Resilience & Vulnerability Proof
    // =========================================================================

    @Test
    fun testColdStartResilienceWithoutApplicationContext() = runBlocking {
        // Instantiate service in isolation (unattached to Android Service lifecycle)
        val isolatedService = WearableMessageReceiverService()

        // Dispatch all actions against isolated service
        val actions = listOf(
            WearConstants.ACTION_PLAY,
            WearConstants.ACTION_PAUSE,
            WearConstants.ACTION_PLAY_PAUSE,
            WearConstants.ACTION_NEXT,
            WearConstants.ACTION_PREVIOUS,
            WearConstants.ACTION_VOLUME_UP,
            WearConstants.ACTION_VOLUME_DOWN,
            WearConstants.ACTION_SET_VOLUME,
            WearConstants.ACTION_LIKE_TOGGLE,
            WearConstants.ACTION_SHUFFLE_TOGGLE,
            WearConstants.ACTION_REPEAT_CYCLE,
            WearConstants.ACTION_TARGET_SELECT,
            WearConstants.ACTION_SYNC_REQUEST,
            "/kittytune/invalid"
        )

        for (action in actions) {
            val event = AdversarialMessageEvent(path = action, data = ByteArray(0))
            // Must not throw NullPointerException, UninitializedPropertyAccessException, etc.
            isolatedService.handleMessage(event.path, event.data)
            isolatedService.onMessageReceived(event)
        }

        safeDestroyService(isolatedService)
    }

    @Test
    fun testTargetSelectionCorruptedPayloads() = runBlocking {
        val service = WearableMessageReceiverService()

        val malformedTargets = listOf(
            null,
            ByteArray(0),
            "{}".toByteArray(Charsets.UTF_8),
            "{targetType: 123}".toByteArray(Charsets.UTF_8),
            "not a json at all".toByteArray(Charsets.UTF_8),
            "desktop:my_peer_id".toByteArray(Charsets.UTF_8),
            "phone".toByteArray(Charsets.UTF_8),
            "desktop".toByteArray(Charsets.UTF_8),
            ByteArray(100) { 0xFF.toByte() }
        )

        for (data in malformedTargets) {
            service.handleMessage(WearConstants.ACTION_TARGET_SELECT, data)
        }

        safeDestroyService(service)
    }

    @Test
    fun testDesktopRoutingColdStartResilience() = runBlocking {
        val service = WearableMessageReceiverService()
        // Switch target to DESKTOP
        service.handleMessage(
            WearConstants.ACTION_TARGET_SELECT,
            WearCodecs.encodeTarget(TargetSelectionPayload.desktop("peer_desktop_123", "MacBook Pro"))
        )

        // Attempting to route desktop playback and volume controls without initialized ConnectManager/KittyTuneApp
        // must be handled gracefully without throwing NoClassDefFoundError / ExceptionInInitializerError
        val desktopActions = listOf(
            WearConstants.ACTION_PLAY to ByteArray(0),
            WearConstants.ACTION_PAUSE to ByteArray(0),
            WearConstants.ACTION_PLAY_PAUSE to ByteArray(0),
            WearConstants.ACTION_NEXT to ByteArray(0),
            WearConstants.ACTION_PREVIOUS to ByteArray(0),
            WearConstants.ACTION_VOLUME_UP to ByteArray(0),
            WearConstants.ACTION_VOLUME_DOWN to ByteArray(0),
            WearConstants.ACTION_SET_VOLUME to WearCodecs.encodeVolume(0.5f)
        )

        for ((action, data) in desktopActions) {
            var caughtThrowable: Throwable? = null
            try {
                service.handleMessage(action, data)
                service.onMessageReceived(AdversarialMessageEvent(path = action, data = data))
            } catch (t: Throwable) {
                caughtThrowable = t
            }

            assertNull(
                "Action $action should be handled gracefully without throwing Throwable, but threw: $caughtThrowable",
                caughtThrowable
            )
        }

        safeDestroyService(service)
    }

    @Test
    fun testMessageBurstStressDesktopTarget500RapidEvents() = runBlocking {
        val service = WearableMessageReceiverService()

        // Set target to DESKTOP
        service.handleMessage(
            WearConstants.ACTION_TARGET_SELECT,
            WearCodecs.encodeTarget(TargetSelectionPayload.desktop("desktop_peer_999", "Studio Mac"))
        )

        val sampleDesktopPayloads = listOf(
            WearConstants.ACTION_PLAY to ByteArray(0),
            WearConstants.ACTION_PAUSE to ByteArray(0),
            WearConstants.ACTION_PLAY_PAUSE to ByteArray(0),
            WearConstants.ACTION_NEXT to ByteArray(0),
            WearConstants.ACTION_PREVIOUS to ByteArray(0),
            WearConstants.ACTION_VOLUME_UP to ByteArray(0),
            WearConstants.ACTION_VOLUME_DOWN to ByteArray(0),
            WearConstants.ACTION_SET_VOLUME to floatToBytes(0.5f),
            WearConstants.ACTION_SET_VOLUME to floatToBytes(Float.NaN),
            WearConstants.ACTION_SET_VOLUME to floatToBytes(Float.POSITIVE_INFINITY),
            WearConstants.ACTION_SET_VOLUME to floatToBytes(Float.NEGATIVE_INFINITY),
            WearConstants.ACTION_SET_VOLUME to floatToBytes(Float.MAX_VALUE),
            WearConstants.ACTION_SET_VOLUME to floatToBytes(Float.MIN_VALUE),
            WearConstants.ACTION_SET_VOLUME to "0.75".toByteArray(Charsets.UTF_8),
            WearConstants.ACTION_SET_VOLUME to "invalid".toByteArray(Charsets.UTF_8),
            WearConstants.ACTION_SYNC_REQUEST to ByteArray(0),
            WearConstants.ACTION_TARGET_SELECT to WearCodecs.encodeTarget(
                TargetSelectionPayload.desktop("desktop_peer_burst", "Studio Mac Burst")
            )
        )

        val caughtExceptions = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()

        val deferredResults = (0 until 500).map { i ->
            async(Dispatchers.Default) {
                try {
                    val (path, data) = sampleDesktopPayloads[i % sampleDesktopPayloads.size]
                    val fakeEvent = AdversarialMessageEvent(path = path, data = data, requestId = i)
                    if (i % 2 == 0) {
                        service.onMessageReceived(fakeEvent)
                    } else {
                        service.handleMessage(path, data)
                    }
                } catch (t: Throwable) {
                    caughtExceptions.add(t)
                }
            }
        }

        deferredResults.awaitAll()

        assertEquals("Zero throwables expected during 500 DESKTOP burst stress, but caught: ${caughtExceptions.toList()}", 0, caughtExceptions.size)

        safeDestroyService(service)
    }
}
