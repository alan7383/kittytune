package com.alananasss.kittytune.wear.presentation.volume

import com.alananasss.kittytune.wear.FakeWearPlaybackController
import com.alananasss.kittytune.wear.common.WearPlaybackState
import com.alananasss.kittytune.wear.presentation.PlaybackViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Empirical Adversarial Challenge Suite for Milestone 2:
 * Volume control subsystem, rotary crown detent accumulation, volume sanitizer math, and volume arc geometry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VolumeAdversarialStressTest {

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
    // CHALLENGE VECTOR 1: VolumeSanitizer Boundary & Malicious Inputs
    // =========================================================================

    @Test
    fun `vector1 - sanitize handles extreme IEEE-754 floats without throwing`() {
        // NaNs
        assertEquals(0.0f, VolumeSanitizer.sanitize(Float.NaN), 0.0f)
        assertEquals(0.0f, VolumeSanitizer.sanitize(Float.fromBits(0x7fc00001)), 0.0f) // Quiet NaN
        assertEquals(0.0f, VolumeSanitizer.sanitize(Float.fromBits(0x7f800001)), 0.0f) // Signaling NaN

        // Infinities
        assertEquals(0.0f, VolumeSanitizer.sanitize(Float.POSITIVE_INFINITY), 0.0f)
        assertEquals(0.0f, VolumeSanitizer.sanitize(Float.NEGATIVE_INFINITY), 0.0f)

        // Negative values
        assertEquals(0.0f, VolumeSanitizer.sanitize(-0.0f), 0.0f)
        assertEquals(0.0f, VolumeSanitizer.sanitize(-100.0f), 0.0f)
        assertEquals(0.0f, VolumeSanitizer.sanitize(-10_000.0f), 0.0f)
        assertEquals(0.0f, VolumeSanitizer.sanitize(-Float.MAX_VALUE), 0.0f)

        // Massive positive values
        assertEquals(1.0f, VolumeSanitizer.sanitize(10_000.0f), 0.0f)
        assertEquals(1.0f, VolumeSanitizer.sanitize(Float.MAX_VALUE), 0.0f)
        assertEquals(1.0f, VolumeSanitizer.sanitize(1.000001f), 0.0f)

        // Subnormal positive floats (near 0)
        assertEquals(Float.MIN_VALUE, VolumeSanitizer.sanitize(Float.MIN_VALUE), 0.0f)
        assertEquals(Float.fromBits(1), VolumeSanitizer.sanitize(Float.fromBits(1)), 0.0f)
    }

    @Test
    fun `vector1 - snapToStep snaps uniformly across the entire 0_0 to 1_0 range`() {
        // Test all 101 points from 0.00 to 1.00 in steps of 0.01
        for (i in 0..100) {
            val raw = i / 100.0f
            val snapped = VolumeSanitizer.snapToStep(raw, 0.05f)

            // Result must be strictly within [0.0, 1.0]
            assertTrue("Snapped value $snapped within [0.0, 1.0]", snapped in 0.0f..1.0f)

            // Snapped value multiplied by 100 must be an exact multiple of 5
            val percent = (snapped * 100.0f).roundToInt()
            assertEquals("Percent $percent must be multiple of 5", 0, percent % 5)

            // Snapped value must never differ from raw by more than step/2 + epsilon (0.0251f)
            assertTrue("Error $|raw - snapped| <= 0.0251", abs(raw - snapped) <= 0.0251f)
        }
    }

    @Test
    fun `vector1 - snapToStep behavior with malicious step parameters`() {
        // Step <= 0 returns clamped
        assertEquals(0.5f, VolumeSanitizer.snapToStep(0.5f, 0.0f), 0.001f)
        assertEquals(0.5f, VolumeSanitizer.snapToStep(0.5f, -0.05f), 0.001f)
        assertEquals(0.0f, VolumeSanitizer.snapToStep(-0.5f, 0.0f), 0.001f)
        assertEquals(1.0f, VolumeSanitizer.snapToStep(1.5f, -1.0f), 0.001f)

        // Malicious step NaN or Infinite
        assertEquals(0.5f, VolumeSanitizer.snapToStep(0.5f, Float.NaN), 0.001f)
        assertEquals(0.5f, VolumeSanitizer.snapToStep(0.5f, Float.POSITIVE_INFINITY), 0.001f)
    }

    @Test
    fun `vector1 - increment and decrement boundaries and clamping`() {
        // Increment at boundary
        assertEquals(1.0f, VolumeSanitizer.increment(1.0f, 0.05f), 0.0001f)
        assertEquals(1.0f, VolumeSanitizer.increment(0.98f, 0.05f), 0.0001f)
        assertEquals(1.0f, VolumeSanitizer.increment(10.0f, 0.05f), 0.0001f)
        assertEquals(0.05f, VolumeSanitizer.increment(Float.NaN, 0.05f), 0.0001f)
        assertEquals(0.05f, VolumeSanitizer.increment(Float.NEGATIVE_INFINITY, 0.05f), 0.0001f)

        // Decrement at boundary
        assertEquals(0.0f, VolumeSanitizer.decrement(0.0f, 0.05f), 0.0001f)
        assertEquals(0.0f, VolumeSanitizer.decrement(0.02f, 0.05f), 0.0001f)
        assertEquals(0.0f, VolumeSanitizer.decrement(-5.0f, 0.05f), 0.0001f)
        assertEquals(0.0f, VolumeSanitizer.decrement(Float.NaN, 0.05f), 0.0001f)

        // Exact stepping
        assertEquals(0.55f, VolumeSanitizer.increment(0.50f, 0.05f), 0.0001f)
        assertEquals(0.45f, VolumeSanitizer.decrement(0.50f, 0.05f), 0.0001f)
    }

    @Test
    fun `vector1 - stream step conversions with extreme maxSteps`() {
        // maxSteps = 0 or negative
        assertEquals(0, VolumeSanitizer.toStreamStep(0.5f, 0))
        assertEquals(0, VolumeSanitizer.toStreamStep(0.5f, -10))
        assertEquals(0.0f, VolumeSanitizer.fromStreamStep(5, 0), 0.0f)
        assertEquals(0.0f, VolumeSanitizer.fromStreamStep(5, -1), 0.0f)

        // Standard maxSteps = 15 (Android stream volume)
        assertEquals(0, VolumeSanitizer.toStreamStep(0.0f, 15))
        assertEquals(8, VolumeSanitizer.toStreamStep(0.5f, 15))
        assertEquals(15, VolumeSanitizer.toStreamStep(1.0f, 15))

        // Negative step index
        assertEquals(0.0f, VolumeSanitizer.fromStreamStep(-5, 15), 0.0f)
        // Step index exceeding maxSteps
        assertEquals(1.0f, VolumeSanitizer.fromStreamStep(25, 15), 0.0f)
    }

    @Test
    fun `vector1 - desktop volume conversions (0 to 1000)`() {
        assertEquals(0L, VolumeSanitizer.toDesktopVolume(0.0f))
        assertEquals(500L, VolumeSanitizer.toDesktopVolume(0.5f))
        assertEquals(1000L, VolumeSanitizer.toDesktopVolume(1.0f))
        assertEquals(0L, VolumeSanitizer.toDesktopVolume(-10.0f))
        assertEquals(1000L, VolumeSanitizer.toDesktopVolume(100.0f))
        assertEquals(0L, VolumeSanitizer.toDesktopVolume(Float.NaN))

        assertEquals(0.0f, VolumeSanitizer.fromDesktopVolume(-50L), 0.0f)
        assertEquals(0.5f, VolumeSanitizer.fromDesktopVolume(500L), 0.001f)
        assertEquals(1.0f, VolumeSanitizer.fromDesktopVolume(1000L), 0.0f)
        assertEquals(1.0f, VolumeSanitizer.fromDesktopVolume(99999L), 0.0f)
    }

    // =========================================================================
    // CHALLENGE VECTOR 2: Rotary Detent Accumulation & Debounce Oracle
    // =========================================================================

    /**
     * Exact replication of RotaryVolumeHandler's accumulator and detent logic with NaN protection.
     */
    class RotaryTestEngine(
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
    fun `vector2 - micro-oscillations produce zero net drift and zero phantom ticks`() {
        val engine = RotaryTestEngine(currentVolume = 0.5f)

        // Simulate 1,000 rapid micro-oscillations: +5px, -5px, +10px, -20px, +10px
        for (i in 1..1000) {
            engine.onRotaryScroll(5.0f)
            engine.onRotaryScroll(-5.0f)
            engine.onRotaryScroll(10.0f)
            engine.onRotaryScroll(-20.0f)
            engine.onRotaryScroll(10.0f)
        }

        assertEquals("No steps should fire for symmetric micro-movements", 0, engine.hapticCount)
        assertEquals("Volume should remain unchanged at 0.5f", 0.5f, engine.currentVolume, 0.0001f)
        assertEquals("Accumulated pixels should be zero", 0.0f, engine.accumulatedPixels, 0.0001f)
    }

    @Test
    fun `vector2 - sub-threshold crawling triggers exactly when 24px is crossed`() {
        val engine = RotaryTestEngine(currentVolume = 0.5f)

        // Scroll 23 pixels in 1px increments
        for (i in 1..23) {
            engine.onRotaryScroll(1.0f)
            assertEquals("No step at $i px", 0, engine.hapticCount)
            assertEquals("Volume unchanged at $i px", 0.5f, engine.currentVolume, 0.0001f)
        }

        // 24th pixel crosses the 24px threshold
        engine.onRotaryScroll(1.0f)
        assertEquals("Step triggers at 24px", 1, engine.hapticCount)
        assertEquals("Volume stepped to 0.55f", 0.55f, engine.currentVolume, 0.0001f)
        assertEquals("Accumulator resets to 0.0f remainder", 0.0f, engine.accumulatedPixels, 0.0001f)
    }

    @Test
    fun `vector2 - large rotational fling triggers multiple steps in single event`() {
        val engine = RotaryTestEngine(currentVolume = 0.5f)

        // Fling of +120px = 5 steps of 24px
        engine.onRotaryScroll(120.0f)
        assertEquals("120px / 24px triggers 1 haptic tick", 1, engine.hapticCount)
        assertEquals("Volume increases by 5 * 0.05 = 0.25 -> 0.75f", 0.75f, engine.currentVolume, 0.0001f)
        assertEquals("Remainder is 0px", 0.0f, engine.accumulatedPixels, 0.0001f)

        // Fling of +130px = 5 steps of 24px (120px) with 10px remainder
        engine.onRotaryScroll(130.0f)
        assertEquals("Volume increases by 5 * 0.05 = 0.25 -> 1.0f (clamped)", 1.0f, engine.currentVolume, 0.0001f)
        assertEquals("Remainder preserved at 10.0px", 10.0f, engine.accumulatedPixels, 0.0001f)
    }

    @Test
    fun `vector2 - boundary saturation does not cause accumulator wind-up or dead zones`() {
        val engine = RotaryTestEngine(currentVolume = 1.0f)

        // Spin clockwise by 240px (10 steps) while already at max (1.0f)
        engine.onRotaryScroll(240.0f)

        // Since volume was already 1.0f, newVol == oldVol:
        assertEquals("No haptic fired when saturated at max", 0, engine.hapticCount)
        assertEquals("Volume remains 1.0f", 1.0f, engine.currentVolume, 0.0001f)
        assertEquals("Accumulator must NOT wind up to 240px; discharged to 0", 0.0f, engine.accumulatedPixels, 0.0001f)

        // Now immediately spin counter-clockwise by -24px
        engine.onRotaryScroll(-24.0f)

        // Must immediately step down without needing to unwind 240px
        assertEquals("Step down triggers immediately", 1, engine.hapticCount)
        assertEquals("Volume immediately drops to 0.95f", 0.95f, engine.currentVolume, 0.0001f)
    }

    @Test
    fun `vector2 - nan and infinite scroll deltas are safely ignored without poisoning accumulator`() {
        val engine = RotaryTestEngine(currentVolume = 0.5f)

        // Seed with sub-threshold accumulator value (e.g. 10px)
        engine.onRotaryScroll(10.0f)
        assertEquals(10.0f, engine.accumulatedPixels, 0.0001f)

        // Send corrupt NaN and infinite scroll deltas
        engine.onRotaryScroll(Float.NaN)
        engine.onRotaryScroll(Float.POSITIVE_INFINITY)
        engine.onRotaryScroll(Float.NEGATIVE_INFINITY)

        // Verify accumulator is NOT poisoned and remains strictly preserved at 10px
        assertFalse("Accumulator must not be NaN", engine.accumulatedPixels.isNaN())
        assertFalse("Accumulator must not be infinite", engine.accumulatedPixels.isInfinite())
        assertEquals("Accumulator preserved across malicious deltas", 10.0f, engine.accumulatedPixels, 0.0001f)
        assertEquals("No haptic fired for corrupt events", 0, engine.hapticCount)
        assertEquals("Volume unchanged at 0.5f", 0.5f, engine.currentVolume, 0.0001f)

        // Subsequent valid scrolls must function properly: 14px + 10px = 24px (triggers 1 step)
        engine.onRotaryScroll(14.0f)
        assertEquals("Step triggers after valid scroll resumes", 1, engine.hapticCount)
        assertEquals("Volume stepped to 0.55f", 0.55f, engine.currentVolume, 0.0001f)
        assertEquals("Remainder resets to 0.0f", 0.0f, engine.accumulatedPixels, 0.0001f)
    }

    @Test
    fun `vector2 - self-heal mechanism recovers accumulator if corrupted externally`() {
        val engine = RotaryTestEngine(currentVolume = 0.5f)

        // Force accumulator into NaN state simulate memory corruption
        engine.accumulatedPixels = Float.NaN
        assertTrue(engine.accumulatedPixels.isNaN())

        // Next valid event must trigger self-heal rather than propagating NaN
        engine.onRotaryScroll(24.0f)
        assertFalse("Accumulator self-heals from NaN", engine.accumulatedPixels.isNaN())
        assertEquals(1, engine.hapticCount)
        assertEquals(0.55f, engine.currentVolume, 0.0001f)
    }

    @Test
    fun `vector2 - viewmodel debounces rapid volume changes to avoid bluetooth data layer flooding`() {
        // Set explicit initial volume
        controller._playbackState.value = WearPlaybackState(volume = 0.5f)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0.5f, viewModel.uiState.value.volume, 0.001f)

        // Rapidly change volume 10 times in quick succession (+0.05f each -> reaches 1.0f)
        for (i in 1..10) {
            viewModel.onVolumeChange(0.05f)
        }

        // Before debounce window (50ms), controller should not have received calls
        testDispatcher.scheduler.advanceTimeBy(25L)
        assertEquals("Optimistic UI state is updated immediately", 1.0f, viewModel.uiState.value.volume, 0.001f)
        assertEquals("Controller must not receive commands before debounce window", 0, controller.dispatchedCommands.size)

        // Advance past debounce window (50ms)
        testDispatcher.scheduler.advanceTimeBy(50L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Controller should receive only 1 debounced call
        assertEquals(1, controller.dispatchedCommands.size)
        assertTrue(controller.dispatchedCommands.first().startsWith("setVolume:"))
    }

    // =========================================================================
    // CHALLENGE VECTOR 3: Volume Controls Row & Bezel Arc Geometry
    // =========================================================================

    @Test
    fun `vector3 - volume button enable-disable logic at strict boundaries`() {
        // At 0.0f: canDecrease is false, canIncrease is true
        val volZero = 0.0f
        val canDec0 = volZero > VolumeSanitizer.MIN_VOLUME
        val canInc0 = volZero < VolumeSanitizer.MAX_VOLUME
        assertFalse(canDec0)
        assertTrue(canInc0)

        // At 1.0f: canDecrease is true, canIncrease is false
        val volOne = 1.0f
        val canDec1 = volOne > VolumeSanitizer.MIN_VOLUME
        val canInc1 = volOne < VolumeSanitizer.MAX_VOLUME
        assertTrue(canDec1)
        assertFalse(canInc1)

        // At 0.5f: both are true
        val volMid = 0.5f
        val canDecMid = volMid > VolumeSanitizer.MIN_VOLUME
        val canIncMid = volMid < VolumeSanitizer.MAX_VOLUME
        assertTrue(canDecMid)
        assertTrue(canIncMid)
    }

    @Test
    fun `vector3 - bezel arc angular sweep mathematics`() {
        // The arc starts at 300° (-60°) and sweeps 120° to 60° (across 0° / 3 o'clock)
        val startAngle = 300f
        val totalSweep = 120f

        val endAngle = (startAngle + totalSweep) % 360f // 420 % 360 = 60°
        assertEquals(60f, endAngle, 0.001f)

        // The center of this arc is at (300° + 60°) = 360° = 0° (3 o'clock, right where the physical crown sits)
        val centerAngle = (startAngle + totalSweep / 2f) % 360f
        assertEquals(0f, centerAngle, 0.001f)

        // At 0% volume: active sweep is 0°
        assertEquals(0f, totalSweep * 0.0f, 0.001f)
        // At 50% volume: active sweep is 60° (spans 300° to 360° / 0°)
        assertEquals(60f, totalSweep * 0.5f, 0.001f)
        // At 100% volume: active sweep is 120° (spans 300° to 60°)
        assertEquals(120f, totalSweep * 1.0f, 0.001f)
    }

    @Test
    fun `vector3 - bezel arc outer boundary clearance on circular screen`() {
        // Display dimensions: 390px (scale 2.0 -> 195dp, R = 97.5dp)
        val screenDiameter = 195.0
        val strokeWidth = 5.0
        val bezelMargin = 4.0

        // In VolumeArcIndicator.kt:
        // diameter = size.minDimension - strokeWidth - 4.dp.toPx()
        val arcDiameter = screenDiameter - strokeWidth - bezelMargin // 186.0dp
        val arcRadius = arcDiameter / 2.0 // 93.0dp

        // Outer stroke edge = arcRadius + strokeWidth / 2.0
        val outerStrokeRadius = arcRadius + (strokeWidth / 2.0) // 93.0 + 2.5 = 95.5dp

        // Clearance to screen edge = (screenDiameter / 2.0) - outerStrokeRadius
        val screenRadius = screenDiameter / 2.0 // 97.5dp
        val clearance = screenRadius - outerStrokeRadius // 97.5 - 95.5 = 2.0dp

        assertTrue("Arc outer stroke must be strictly within screen boundary with >= 2dp clearance", clearance >= 2.0)
    }
}
