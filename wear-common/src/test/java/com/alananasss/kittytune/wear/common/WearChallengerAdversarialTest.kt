package com.alananasss.kittytune.wear.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

/**
 * Challenger adversarial stress test suite.
 * Probes WearCodecs and PlaybackStatePayload with fuzzing, exhaustive boundary scans,
 * and malicious payloads.
 */
class WearChallengerAdversarialTest {

    // =========================================================================
    // 1. Specific Dispatch Verification Points
    // =========================================================================

    @Test
    fun testDispatchVerificationPoint1_encodeVolumeNanDecodesToZero() {
        val encoded = WearCodecs.encodeVolume(Float.NaN)
        val decoded = WearCodecs.decodeVolume(encoded)
        assertFalse("Volume decoded from encodeVolume(Float.NaN) must not be NaN", decoded.isNaN())
        assertEquals("Volume decoded from encodeVolume(Float.NaN) must be 0.0f", 0.0f, decoded, 0.0f)
    }

    @Test
    fun testDispatchVerificationPoint2_decodeVolumeRawNanBytesDecodesToZero() {
        val rawNanBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat(Float.NaN).array()
        val decoded = WearCodecs.decodeVolume(rawNanBytes)
        assertFalse("decodeVolume(rawNanBytes) must not be NaN", decoded.isNaN())
        assertEquals("decodeVolume(rawNanBytes) must be 0.0f", 0.0f, decoded, 0.0f)
    }

    @Test
    fun testDispatchVerificationPoint3_decodeVolumeAllOnesDecodesToZero() {
        val allOnes = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val decoded = WearCodecs.decodeVolume(allOnes)
        assertFalse("decodeVolume(allOnes) must not be NaN", decoded.isNaN())
        assertEquals("decodeVolume(allOnes) must be 0.0f", 0.0f, decoded, 0.0f)
    }

    @Test
    fun testDispatchVerificationPoint4_pausedStateProjectedPositionClampsToDurationMs() {
        val testCases = listOf(
            Pair(100L, 50L),
            Pair(60_000L, 30_000L),
            Pair(1_000_000L, 100_000L),
            Pair(Long.MAX_VALUE, 180_000L),
            Pair(180_001L, 180_000L),
        )

        for ((pos, dur) in testCases) {
            val pausedState = WearPlaybackState(
                positionMs = pos,
                durationMs = dur,
                isPlaying = false,
                timestamp = 1000L,
            )
            val projected = pausedState.projectedPositionMs(nowMs = 5000L)
            assertEquals("Position $pos exceeding duration $dur when paused must clamp to $dur", dur, projected)

            val defaultNowProjected = pausedState.projectedPositionMs()
            assertEquals("Default nowMs position $pos exceeding duration $dur when paused must clamp to $dur", dur, defaultNowProjected)

            assertEquals("Progress fraction when paused and position exceeds duration must be 1.0f", 1.0f, pausedState.progressFraction(), 0.0001f)
        }
    }

    // =========================================================================
    // 2. Adversarial Fuzzing: 50,000 Randomized NaN Variations
    // =========================================================================

    @Test
    fun testVolumeCodec_50000RandomizedNaNBitPatterns_neverLeakNaN() {
        val rng = Random(42)
        val buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)

        for (i in 0 until 50_000) {
            buffer.clear()
            // IEEE 754 float: Exponent bits 30..23 = 0xFF (all 1s), mantissa != 0
            val signBit = if (rng.nextBoolean()) 1 shl 31 else 0
            val exponent = 0xFF shl 23
            val mantissa = rng.nextInt(1, 1 shl 23) // non-zero mantissa
            val nanIntBits = signBit or exponent or mantissa

            buffer.putInt(nanIntBits)
            val nanBytes = buffer.array()

            val decoded = WearCodecs.decodeVolume(nanBytes)
            assertFalse("Decoded volume for NaN bits 0x${Integer.toHexString(nanIntBits)} must not be NaN", decoded.isNaN())
            assertEquals("Decoded volume for NaN bits 0x${Integer.toHexString(nanIntBits)} must sanitize to 0.0f", 0.0f, decoded, 0.0f)
        }
    }

    // =========================================================================
    // 3. Adversarial Fuzzing: 50,000 Arbitrary Random Byte Payloads
    // =========================================================================

    @Test
    fun testVolumeCodec_50000ArbitraryRandomByteArrays_alwaysBounded() {
        val rng = Random(1337)

        for (i in 0 until 50_000) {
            val length = rng.nextInt(0, 16)
            val randomBytes = ByteArray(length)
            rng.nextBytes(randomBytes)

            val decoded = WearCodecs.decodeVolume(randomBytes)
            assertFalse("Decoded random byte payload must not be NaN", decoded.isNaN())
            assertFalse("Decoded random byte payload must not be infinite", decoded.isInfinite())
            assertTrue("Decoded random byte payload must be >= 0.0f, got $decoded", decoded >= 0.0f)
            assertTrue("Decoded random byte payload must be <= 1.0f, got $decoded", decoded <= 1.0f)
        }
    }

    // =========================================================================
    // 4. Exhaustive String Fuzzing: Malicious String Payloads
    // =========================================================================

    @Test
    fun testVolumeCodec_maliciousStringPayloads() {
        val maliciousStrings = listOf(
            "NaN", "-NaN", "+NaN", "nan", "NAN",
            "Infinity", "-Infinity", "+Infinity", "inf", "-inf",
            "1e5", "1e-5", "1E10", "-1E10",
            "0x1.0p0", "0x7F800000",
            "null", "undefined", "true", "false",
            "", " ", "   \t\n\r  ",
            "0.000000000000000000000000000000000000000000000000000000001",
            "999999999999999999999999999999999999999999999999999999999",
            "-999999999999999999999999999999999999999999999999999999999",
            "+-0.5", "--0.5", "++0.5", ".0.5", "0.5.5", "...",
            "\u0000", "\u00000.5", "0.5\u0000",
            "\uFF10.\uFF15", // Full-width numbers
            "½", "¼", "¾",
        )

        for (str in maliciousStrings) {
            val bytes = str.toByteArray(Charsets.UTF_8)
            val decoded = WearCodecs.decodeVolume(bytes)
            assertFalse("String payload '$str' must not decode to NaN", decoded.isNaN())
            assertFalse("String payload '$str' must not decode to Infinity", decoded.isInfinite())
            assertTrue("String payload '$str' must be in [0.0f, 1.0f], got $decoded", decoded in 0.0f..1.0f)
        }
    }

    // =========================================================================
    // 5. Stress Testing: Playhead Clamping Matrix Under Extreme Conditions
    // =========================================================================

    @Test
    fun testPlayheadClampingMatrix() {
        val positions = listOf(-100_000L, -1L, 0L, 1L, 5000L, 59_999L, 60_000L, 60_001L, 100_000L, Long.MAX_VALUE)
        val durations = listOf(1L, 1000L, 60_000L, 180_000L, 86_400_000L)
        val nowOffsets = listOf(-10_000L, 0L, 1000L, 100_000L, 1_000_000L)

        for (dur in durations) {
            for (pos in positions) {
                // Test Paused State
                val paused = WearPlaybackState(
                    positionMs = pos,
                    durationMs = dur,
                    isPlaying = false,
                    timestamp = 1000L,
                )
                for (offset in nowOffsets) {
                    val proj = paused.projectedPositionMs(nowMs = 1000L + offset)
                    assertTrue("Paused projected position must be >= 0L (pos=$pos, dur=$dur, got=$proj)", proj >= 0L)
                    assertTrue("Paused projected position must be <= duration (pos=$pos, dur=$dur, got=$proj)", proj <= dur)

                    val frac = paused.progressFraction(nowMs = 1000L + offset)
                    assertFalse("Progress fraction must not be NaN", frac.isNaN())
                    assertTrue("Progress fraction must be in 0..1 (got $frac)", frac in 0.0f..1.0f)
                }
            }
        }
    }
}
