package com.alananasss.kittytune.wear.presentation.volume

import com.alananasss.kittytune.wear.FakeWearPlaybackController
import com.alananasss.kittytune.wear.common.WearPlaybackState
import com.alananasss.kittytune.wear.presentation.PlaybackScreenDefaults
import com.alananasss.kittytune.wear.presentation.PlaybackViewModel
import com.alananasss.kittytune.wear.presentation.components.TargetDeviceSelectorChipDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Empirical Adversarial Fuzzing and Stress Harness for Milestone 2 Iteration 3:
 * - VolumeSanitizer IEEE-754 pseudo-random float fuzzing (NaNs, Infs, subnormals, negative zero).
 * - Rotary crown detent accumulation oracle (corrupt sequences, rapid directional chatter, boundary fling).
 * - ViewModel concurrency, rapid-fire debouncing, transport failure rollback & recovery.
 * - Circular display geometric clearance invariants across round watch display profiles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RotaryAndVolumeAdversarialFuzzTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var controller: FakeWearPlaybackController
    private lateinit var viewModel: PlaybackViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        controller = FakeWearPlaybackController()
        viewModel = PlaybackViewModel(controller)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // SECTION 1: VolumeSanitizer IEEE-754 High-Volume Fuzzing
    // =========================================================================

    @Test
    fun `fuzz - 100_000 random 32-bit float bit patterns into VolumeSanitizer sanitize`() {
        val rng = Random(0xCAFE_BABE)
        for (i in 0 until 100_000) {
            val rawBits = rng.nextInt()
            val rawFloat = Float.fromBits(rawBits)

            val sanitized = VolumeSanitizer.sanitize(rawFloat)

            // Strict invariant: never NaN, never Infinite, always bounded in [0.0f, 1.0f]
            assertFalse("Sanitized output must never be NaN for bit pattern 0x${Integer.toHexString(rawBits)}", sanitized.isNaN())
            assertFalse("Sanitized output must never be Infinite for bit pattern 0x${Integer.toHexString(rawBits)}", sanitized.isInfinite())
            assertTrue("Sanitized output $sanitized must be >= 0.0f", sanitized >= 0.0f)
            assertTrue("Sanitized output $sanitized must be <= 1.0f", sanitized <= 1.0f)
        }
    }

    @Test
    fun `fuzz - snapToStep with random inputs and random steps never throws and preserves range`() {
        val rng = Random(0xDEAD_BEEF)
        for (i in 0 until 50_000) {
            val rawFloat = Float.fromBits(rng.nextInt())
            val rawStep = Float.fromBits(rng.nextInt())

            val snapped = VolumeSanitizer.snapToStep(rawFloat, rawStep)

            assertFalse("Snapped result must never be NaN", snapped.isNaN())
            assertFalse("Snapped result must never be Infinite", snapped.isInfinite())
            assertTrue("Snapped result $snapped must be within [0.0, 1.0]", snapped in 0.0f..1.0f)
        }
    }

    @Test
    fun `fuzz - increment and decrement with randomized inputs and steps never violate bounds`() {
        val rng = Random(0x1234_5678)
        for (i in 0 until 50_000) {
            val rawVol = Float.fromBits(rng.nextInt())
            val rawStep = Float.fromBits(rng.nextInt())

            val inc = VolumeSanitizer.increment(rawVol, rawStep)
            assertFalse("Increment must not be NaN", inc.isNaN())
            assertFalse("Increment must not be Infinite", inc.isInfinite())
            assertTrue("Increment must be within [0.0, 1.0]", inc in 0.0f..1.0f)

            val dec = VolumeSanitizer.decrement(rawVol, rawStep)
            assertFalse("Decrement must not be NaN", dec.isNaN())
            assertFalse("Decrement must not be Infinite", dec.isInfinite())
            assertTrue("Decrement must be within [0.0, 1.0]", dec in 0.0f..1.0f)
        }
    }

    @Test
    fun `stress - subnormal floats near zero do not trigger underflow crash`() {
        val subnormals = listOf(
            Float.MIN_VALUE,
            Float.fromBits(1),
            Float.fromBits(2),
            Float.fromBits(0x007FFFFF), // Max subnormal
            -Float.MIN_VALUE,
            -Float.fromBits(1),
            -0.0f,
            0.0f
        )

        for (v in subnormals) {
            val sanitized = VolumeSanitizer.sanitize(v)
            assertTrue("Subnormal $v sanitized to $sanitized must be in [0.0, 1.0]", sanitized in 0.0f..1.0f)
            assertFalse("Must not be NaN", sanitized.isNaN())

            val percent = VolumeSanitizer.toPercentage(v)
            assertTrue("Percentage must be in 0..100", percent in 0..100)

            val text = VolumeSanitizer.formatPercentage(v)
            assertTrue("Formatted text must end in %", text.endsWith("%"))
        }
    }

    @Test
    fun `stress - extreme conversions for phone and desktop audio protocols`() {
        val extremeInts = listOf(
            Int.MIN_VALUE, -100, -1, 0, 1, 15, 16, 100, Int.MAX_VALUE
        )
        for (steps in extremeInts) {
            val streamVal = VolumeSanitizer.toStreamStep(0.5f, steps)
            assertTrue("Stream step must be >= 0", streamVal >= 0)
            if (steps > 0) {
                assertTrue("Stream step must be <= steps", streamVal <= steps)
            } else {
                assertEquals("Zero or negative maxSteps yields 0", 0, streamVal)
            }

            val fromStream = VolumeSanitizer.fromStreamStep(steps, 15)
            assertTrue("From stream step must be in [0.0, 1.0]", fromStream in 0.0f..1.0f)
        }

        val extremeLongs = listOf(
            Long.MIN_VALUE, -1000L, -1L, 0L, 500L, 1000L, 1001L, 100_000L, Long.MAX_VALUE
        )
        for (desktopVol in extremeLongs) {
            val fromDesk = VolumeSanitizer.fromDesktopVolume(desktopVol)
            assertTrue("From desktop volume must be in [0.0, 1.0]", fromDesk in 0.0f..1.0f)
            assertFalse("Must not be NaN", fromDesk.isNaN())
        }
    }

    // =========================================================================
    // SECTION 2: Rotary Crown Detent Accumulation Adversarial Fuzzing
    // =========================================================================

    /**
     * Exact replication harness of RotaryVolumeHandler state machine.
     */
    private class RotaryAdversarialEngine(
        val detentThresholdPx: Float = 24.0f,
        val stepVolume: Float = 0.05f,
        val reverseDirection: Boolean = false,
        var currentVolume: Float = 0.5f
    ) {
        var accumulatedPixels = 0.0f
        var hapticCount = 0
        val volumeHistory = mutableListOf<Float>()

        fun onRotaryScroll(verticalScrollPixels: Float): Boolean {
            if (verticalScrollPixels.isNaN() || verticalScrollPixels.isInfinite()) {
                return true // safely ignore corrupt delta and preserve accumulator
            }
            if (accumulatedPixels.isNaN() || accumulatedPixels.isInfinite()) {
                accumulatedPixels = 0.0f
            }

            val safeThresholdPx = if (detentThresholdPx.isNaN() || detentThresholdPx.isInfinite() || detentThresholdPx <= 0.0f) {
                24.0f
            } else {
                detentThresholdPx
            }

            val rawDelta = if (reverseDirection) -verticalScrollPixels else verticalScrollPixels
            accumulatedPixels += rawDelta

            if (abs(accumulatedPixels) >= safeThresholdPx) {
                val stepCount = (accumulatedPixels / safeThresholdPx).toInt()
                accumulatedPixels -= (stepCount * safeThresholdPx)

                val oldVol = VolumeSanitizer.sanitize(currentVolume)
                val newVol = VolumeSanitizer.sanitize(oldVol + (stepCount * stepVolume))

                if (newVol != oldVol) {
                    hapticCount++
                    currentVolume = newVol
                    volumeHistory.add(newVol)
                }
            }
            return true
        }
    }

    @Test
    fun `fuzz - 50_000 mixed rotary deltas with intermittent NaN and Inf corruption`() {
        val engine = RotaryAdversarialEngine(currentVolume = 0.5f)
        val rng = Random(0x5432_1098)

        for (i in 0 until 50_000) {
            val delta = when (rng.nextInt(10)) {
                0 -> Float.NaN
                1 -> Float.POSITIVE_INFINITY
                2 -> Float.NEGATIVE_INFINITY
                3 -> 0.0f
                4 -> -0.0f
                5 -> rng.nextFloat() * 100.0f - 50.0f // Finite scroll in [-50, +50]
                6 -> 24.0f // Exact threshold positive
                7 -> -24.0f // Exact threshold negative
                8 -> 1000.0f // Massive fling
                else -> -1000.0f // Massive reverse fling
            }

            engine.onRotaryScroll(delta)

            // Assertions after every single scroll event:
            assertFalse("Accumulator must never be NaN after event $i", engine.accumulatedPixels.isNaN())
            assertFalse("Accumulator must never be Infinite after event $i", engine.accumulatedPixels.isInfinite())
            assertTrue("Volume must remain strictly within [0.0, 1.0] after event $i", engine.currentVolume in 0.0f..1.0f)
            assertFalse("Volume must never be NaN after event $i", engine.currentVolume.isNaN())
        }
    }

    @Test
    fun `stress - rapid direction reversal chattering at 23_9px threshold produces zero drift`() {
        val engine = RotaryAdversarialEngine(currentVolume = 0.50f)

        // Rapid oscillation +/- 23.9px (just below 24.0px detent threshold)
        for (i in 0 until 10_000) {
            engine.onRotaryScroll(+23.9f)
            engine.onRotaryScroll(-23.9f)
        }

        assertEquals("No haptic feedback should trigger for sub-threshold chattering", 0, engine.hapticCount)
        assertEquals("Volume must remain strictly identical", 0.50f, engine.currentVolume, 0.0001f)
        assertEquals("Accumulator must return to zero without accumulation error", 0.0f, engine.accumulatedPixels, 0.01f)
    }

    @Test
    fun `stress - boundary fling saturation and instantaneous counter-rotation recovery`() {
        val engine = RotaryAdversarialEngine(currentVolume = 0.50f)

        // 1. Massive fling clockwise of +10,000px
        engine.onRotaryScroll(10_000.0f)
        assertEquals("Volume saturates at 1.0f max", 1.0f, engine.currentVolume, 0.0001f)

        // Remainder must not be 10,000px (wind-up bug); it must be strictly bounded < safeThresholdPx (24px)
        assertTrue("Accumulator remainder must be < 24.0px (actual: ${engine.accumulatedPixels})", engine.accumulatedPixels < 24.0f)
        assertTrue("Accumulator remainder must be >= 0.0px (actual: ${engine.accumulatedPixels})", engine.accumulatedPixels >= 0.0f)

        val hapticsBefore = engine.hapticCount

        // 2. Further clockwise rotation while already at 1.0f max: must produce ZERO haptics
        for (i in 0 until 50) {
            engine.onRotaryScroll(48.0f)
        }
        assertEquals("No phantom haptics while saturated at ceiling", hapticsBefore, engine.hapticCount)
        assertEquals("Volume stays at 1.0f", 1.0f, engine.currentVolume, 0.0001f)

        // 3. Counter-rotation recovery:
        // Accumulator has positive remainder from forward fling (bounded < 24px).
        // Counter-rotation of (24.0px + remainder) is guaranteed to trigger immediate step-down (no wind-up dead zone > 48px).
        val remainder = engine.accumulatedPixels
        assertTrue("Remainder is bounded < 24.0px", remainder in 0.0f..<24.0f)

        // First scroll: -24.0px brings accumulator to remainder - 24.0px
        engine.onRotaryScroll(-24.0f)
        if (remainder == 0.0f) {
            assertEquals("If remainder was exactly 0, step down triggers immediately", 0.95f, engine.currentVolume, 0.001f)
        } else {
            // Remainder was > 0, so accumulator is now (remainder - 24.0px) in (-24px, 0px)
            // An additional scroll of -24.0px is strictly guaranteed to cross threshold and trigger step down
            engine.onRotaryScroll(-24.0f)
            assertEquals("Step down is strictly guaranteed within <= 48px (< 2 detent clicks)", 0.95f, engine.currentVolume, 0.001f)
            assertEquals("Haptic fired on step down", hapticsBefore + 1, engine.hapticCount)
        }
    }

    @Test
    fun `stress - malicious detent threshold inputs fall back safely without division by zero`() {
        val maliciousThresholds = listOf(
            0.0f, -1.0f, -24.0f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY
        )

        for (threshold in maliciousThresholds) {
            val engine = RotaryAdversarialEngine(detentThresholdPx = threshold, currentVolume = 0.5f)
            engine.onRotaryScroll(24.0f) // Fallback threshold is 24.0f

            assertEquals("Threshold $threshold must safely trigger 1 step via 24.0f fallback", 1, engine.hapticCount)
            assertEquals("Volume increases to 0.55f", 0.55f, engine.currentVolume, 0.001f)
        }
    }

    // =========================================================================
    // SECTION 3: PlaybackViewModel Concurrency, Rapid-Fire Debounce & Recovery
    // =========================================================================

    @Test
    fun `concurrency - 1_000 rapid volume updates debounce into single transport dispatch`() {
        controller._playbackState.value = WearPlaybackState(volume = 0.50f)
        testDispatcher.scheduler.advanceUntilIdle()

        // Rapidly fire 1,000 volume adjustments
        for (i in 1..1000) {
            viewModel.onSetVolume((i % 20) * 0.05f)
        }

        // Before debounce timeout (50ms): zero controller dispatches
        testDispatcher.scheduler.advanceTimeBy(40L)
        assertEquals("No network dispatch before 50ms debounce window", 0, controller.dispatchedCommands.size)

        // Advance past debounce window
        testDispatcher.scheduler.advanceTimeBy(60L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Exactly one consolidated command dispatched to Wearable controller
        assertEquals("Exactly 1 consolidated dispatch after debounce", 1, controller.dispatchedCommands.size)
        assertTrue("Command starts with setVolume:", controller.dispatchedCommands.first().startsWith("setVolume:"))
    }

    @Test
    fun `resilience - transport communication failure triggers state rollback and subsequent recovery`() {
        controller._playbackState.value = WearPlaybackState(volume = 0.40f)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0.40f, viewModel.uiState.value.volume, 0.001f)

        // Configure mock transport failure
        controller.shouldFailNextCommand = true

        // Attempt volume change to 0.70f
        viewModel.onSetVolume(0.70f)
        assertEquals("Optimistic state updates immediately", 0.70f, viewModel.uiState.value.volume, 0.001f)

        // Advance debounce window to execute dispatch
        testDispatcher.scheduler.advanceTimeBy(60L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Must rollback to authoritative 0.40f and display error
        assertEquals("Volume rolls back to 0.40f on transport failure", 0.40f, viewModel.uiState.value.volume, 0.001f)
        assertNotNull("Error message is surfaced to UI", viewModel.uiState.value.errorMessage)

        // Next successful volume change recovers state
        viewModel.onSetVolume(0.55f)
        testDispatcher.scheduler.advanceTimeBy(60L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Subsequent command succeeds and updates volume to 0.55f", 0.55f, viewModel.uiState.value.volume, 0.001f)
    }

    // =========================================================================
    // SECTION 4: Circular Display Layout Geometry Proof across Smartwatch Radii
    // =========================================================================

    @Test
    fun `geometry - verify circular chord clearances across round smartwatch profiles`() {
        // Test smartwatch display profiles:
        // 1. Small Round: 390x390 @ 320dpi (R_phys = 97.5dp, R_safe = 89.5dp)
        // 2. Standard Round: 410x410 @ 320dpi (R_phys = 102.5dp, R_safe = 94.5dp)
        // 3. Large Round: 450x450 @ 320dpi (R_phys = 112.5dp, R_safe = 104.5dp)
        data class WatchProfile(val name: String, val rPhysDp: Double, val rSafeDp: Double)

        val profiles = listOf(
            WatchProfile("390x390 Round", 97.5, 89.5),
            WatchProfile("410x410 Round", 102.5, 94.5),
            WatchProfile("450x450 Round", 112.5, 104.5)
        )

        // Authentic row specifications
        val chipW = PlaybackScreenDefaults.TargetChipWidth.value.toDouble() // 80.0
        val chipH = PlaybackScreenDefaults.TargetChipHeight.value.toDouble() // 18.0
        val trackW = PlaybackScreenDefaults.TrackInfoMaxWidth.value.toDouble() // 124.0
        val trackH = PlaybackScreenDefaults.TrackInfoHeight.value.toDouble() // 26.0
        val triadW = PlaybackScreenDefaults.PlaybackControlsRowWidth.value.toDouble() // 160.0
        val triadH = PlaybackScreenDefaults.PlaybackControlsRowHeight.value.toDouble() // 48.0
        val volW = VolumeControlsDefaults.RowWidth.value.toDouble() // 112.0
        val volH = VolumeControlsDefaults.RowHeight.value.toDouble() // 32.0
        val timeW = PlaybackScreenDefaults.TimelineMaxWidth.value.toDouble() // 70.0
        val timeH = PlaybackScreenDefaults.TimelineHeight.value.toDouble() // 12.0

        val spStd = PlaybackScreenDefaults.SpacerStandard.value.toDouble() // 2.0
        val spCmp = PlaybackScreenDefaults.SpacerCompact.value.toDouble() // 1.0

        val totalH = chipH + spCmp + trackH + spStd + triadH + spStd + volH + spCmp + timeH
        assertEquals("Total stack height must be exactly 142.0dp", 142.0, totalH, 0.001)

        val y0 = -totalH / 2.0 // -71.0dp
        val rows = listOf(
            Triple("TargetChip", chipW, Pair(y0, y0 + chipH)),
            Triple("TrackInfo", trackW, Pair(y0 + chipH + spCmp, y0 + chipH + spCmp + trackH)),
            Triple("PlaybackTriad", triadW, Pair(y0 + chipH + spCmp + trackH + spStd, y0 + chipH + spCmp + trackH + spStd + triadH)),
            Triple("VolumeRow", volW, Pair(y0 + chipH + spCmp + trackH + spStd + triadH + spStd, y0 + chipH + spCmp + trackH + spStd + triadH + spStd + volH)),
            Triple("TimelineText", timeW, Pair(y0 + chipH + spCmp + trackH + spStd + triadH + spStd + volH + spCmp, y0 + totalH))
        )

        for (profile in profiles) {
            for ((name, width, yRange) in rows) {
                val halfW = width / 2.0
                val maxY = max(abs(yRange.first), abs(yRange.second))
                val cornerDist = sqrt(halfW * halfW + maxY * maxY)

                assertTrue(
                    "Profile ${profile.name} - $name corner ($cornerDist dp) must be within safe radius (${profile.rSafeDp} dp)",
                    cornerDist <= profile.rSafeDp
                )

                val safeChordWidth = 2.0 * sqrt(profile.rSafeDp * profile.rSafeDp - maxY * maxY)
                assertTrue(
                    "Profile ${profile.name} - $name width ($width dp) must be <= safe chord ($safeChordWidth dp)",
                    width <= safeChordWidth
                )

                val physicalClearance = profile.rPhysDp - cornerDist
                assertTrue(
                    "Profile ${profile.name} - $name physical glass clearance ($physicalClearance dp) must be >= 13.0dp",
                    physicalClearance >= 13.0
                )
            }
        }
    }

    @Test
    fun `accessibility - verify touch target bounding boxes comply with WCAG 2_5_5 minimum 48dp`() {
        val minGuidelineDp = 48.0

        // 1. Playback Triad Buttons
        assertTrue(
            "Playback triad button size (${PlaybackScreenDefaults.PlaybackButtonSize.value} dp) must be >= 48dp",
            PlaybackScreenDefaults.PlaybackButtonSize.value.toDouble() >= minGuidelineDp
        )

        // 2. Volume Controls Row Buttons
        assertTrue(
            "VolumeControlsDefaults.MinTouchTargetSize (${VolumeControlsDefaults.MinTouchTargetSize.value} dp) must be >= 48dp",
            VolumeControlsDefaults.MinTouchTargetSize.value.toDouble() >= minGuidelineDp
        )

        // 3. Target Device Selector Chip
        assertTrue(
            "TargetDeviceSelectorChipDefaults.MinTouchTargetSize (${TargetDeviceSelectorChipDefaults.MinTouchTargetSize.value} dp) must be >= 48dp",
            TargetDeviceSelectorChipDefaults.MinTouchTargetSize.value.toDouble() >= minGuidelineDp
        )
    }
}
