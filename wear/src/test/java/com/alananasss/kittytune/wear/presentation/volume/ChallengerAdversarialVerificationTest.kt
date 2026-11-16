package com.alananasss.kittytune.wear.presentation.volume

import com.alananasss.kittytune.wear.FakeWearPlaybackController
import com.alananasss.kittytune.wear.common.WearPlaybackState
import com.alananasss.kittytune.wear.presentation.PlaybackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * EMPIRICAL CHALLENGER TEST SUITE (Milestone 2 Iteration 3)
 *
 * Adversarial stress harness covering:
 * 1. Rotary crown scroll delta fuzzing (NaN, Infinities, Subnormals, 100k continuous stream, rapid oscillation)
 * 2. VolumeSanitizer boundary limits, malicious steps, monotonicity, idempotence, and exact grid snapping
 * 3. PlaybackViewModel debounced volume dispatch, cancellation, race conditions, exception recovery, and rollback
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChallengerAdversarialVerificationTest {

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
    // HARNESS 1: Rotary Crown Accumulator & Delta Fuzzing
    // =========================================================================

    /**
     * Exact simulation of RotaryVolumeHandler logic for headless empirical challenge testing.
     */
    class RotaryHarness(
        var detentThresholdPx: Float = 24.0f,
        var stepVolume: Float = VolumeSanitizer.DEFAULT_STEP,
        var reverseDirection: Boolean = false,
        var currentVolume: Float = 0.5f
    ) {
        var accumulatedPixels = 0.0f
        var stepCountTotal = 0
        var hapticTicks = 0
        val history = mutableListOf<Float>()

        fun processScroll(verticalScrollPixels: Float): Boolean {
            // Guard against IEEE-754 NaN or Infinite scroll deltas
            if (verticalScrollPixels.isNaN() || verticalScrollPixels.isInfinite()) {
                return true
            }

            // Self-heal accumulator if corrupted
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

                stepCountTotal += stepCount
                if (newVol != oldVol) {
                    hapticTicks++
                    currentVolume = newVol
                    history.add(newVol)
                }
            }
            return true
        }
    }

    @Test
    fun `fuzz - extreme float deltas including NaN, Inf, subnormals, and quiet-signaling NaNs`() {
        val harness = RotaryHarness(currentVolume = 0.5f)

        // Seed sub-threshold accumulator state (15.0px)
        harness.processScroll(15.0f)
        assertEquals(15.0f, harness.accumulatedPixels, 0.0001f)

        // Fuzz with malicious deltas
        val maliciousDeltas = listOf(
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            Float.fromBits(0x7fc00001), // Quiet NaN
            Float.fromBits(0x7f800001), // Signaling NaN
            Float.fromBits(0x7fffffff), // Max NaN
            Float.MIN_VALUE,            // Subnormal positive
            -Float.MIN_VALUE,           // Subnormal negative
            Float.fromBits(1),          // Smallest positive denormal
            -0.0f
        )

        for (malicious in maliciousDeltas) {
            harness.processScroll(malicious)
            // Accumulator must remain strictly valid and uncorrupted
            assertFalse("Accumulator must not be NaN", harness.accumulatedPixels.isNaN())
            assertFalse("Accumulator must not be Infinite", harness.accumulatedPixels.isInfinite())
        }

        // Sub-threshold accumulation of 15.0px must still be preserved!
        assertEquals("Sub-threshold pixels preserved across fuzzing", 15.0f, harness.accumulatedPixels, 0.0001f)
        assertEquals("No phantom haptics triggered", 0, harness.hapticTicks)
        assertEquals("Volume unchanged at 0.5f", 0.5f, harness.currentVolume, 0.0001f)

        // Completing the detent (15 + 9 = 24px) triggers step cleanly
        harness.processScroll(9.0f)
        assertEquals(1, harness.hapticTicks)
        assertEquals(0.55f, harness.currentVolume, 0.0001f)
        assertEquals(0.0f, harness.accumulatedPixels, 0.0001f)
    }

    @Test
    fun `fuzz - rapid sign oscillation chatter over 10000 cycles produces zero net drift`() {
        val harness = RotaryHarness(currentVolume = 0.5f)

        // 10,000 rapid chatter events (+23.9px then -23.9px)
        for (i in 1..10000) {
            harness.processScroll(23.9f)
            harness.processScroll(-23.9f)
        }

        assertEquals("Zero ticks fired for sub-threshold oscillation", 0, harness.hapticTicks)
        assertEquals("Volume remains exactly 0.5f", 0.5f, harness.currentVolume, 0.0001f)
        assertTrue("Accumulator remains near zero", abs(harness.accumulatedPixels) < 0.01f)
    }

    @Test
    fun `fuzz - continuous 100000 pixel scroll stream does not overflow and reverses instantly`() {
        val harness = RotaryHarness(currentVolume = 0.0f)

        // Stream 100,000 pixels in 1px increments
        for (i in 1..100000) {
            harness.processScroll(1.0f)
        }

        // 100,000 / 24 = 4166 steps, remainder = 100,000 - (4166 * 24) = 100,000 - 99,984 = 16px
        assertEquals(4166, harness.stepCountTotal)
        assertEquals(16.0f, harness.accumulatedPixels, 0.001f)
        assertEquals(1.0f, harness.currentVolume, 0.0001f)

        // Now reverse: scroll -16px to cancel remainder
        harness.processScroll(-16.0f)
        assertEquals(0.0f, harness.accumulatedPixels, 0.001f)

        // Then scroll -24px: MUST immediately decrease volume to 0.95f without deadzone
        harness.processScroll(-24.0f)
        assertEquals(0.95f, harness.currentVolume, 0.0001f)
        assertEquals(0.0f, harness.accumulatedPixels, 0.001f)
    }

    @Test
    fun `fuzz - single massive fling of 100000 pixels calculates correct steps and remainder`() {
        val harness = RotaryHarness(currentVolume = 0.2f)

        // 1 single event of +100,000px
        harness.processScroll(100000.0f)

        // 100000 / 24 = 4166 steps. Remainder = 16.0px
        assertEquals(16.0f, harness.accumulatedPixels, 0.001f)
        assertEquals(1.0f, harness.currentVolume, 0.0001f)

        // Immediate reverse by -40.0px (-16px remainder + -24px detent)
        harness.processScroll(-40.0f)
        assertEquals(0.95f, harness.currentVolume, 0.0001f)
        assertEquals(0.0f, harness.accumulatedPixels, 0.001f)
    }

    @Test
    fun `fuzz - detentThresholdPx fallback when threshold is NaN, Infinite, zero, or negative`() {
        val badThresholds = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0.0f, -10.0f, -0.0f)

        for (threshold in badThresholds) {
            val harness = RotaryHarness(detentThresholdPx = threshold, currentVolume = 0.5f)
            // When threshold is invalid, it must safely fall back to 24.0f
            harness.processScroll(24.0f)
            assertEquals("Threshold $threshold must fallback and trigger at 24px", 1, harness.hapticTicks)
            assertEquals(0.55f, harness.currentVolume, 0.0001f)
        }
    }

    // =========================================================================
    // HARNESS 2: VolumeSanitizer Limits, Monotonicity & Snapping Oracles
    // =========================================================================

    @Test
    fun `sanitizer - boundary limits and extreme float clamping`() {
        assertEquals(0.0f, VolumeSanitizer.sanitize(0.0f), 0.0f)
        assertEquals(1.0f, VolumeSanitizer.sanitize(1.0f), 0.0f)
        assertEquals(0.0f, VolumeSanitizer.sanitize(-0.01f), 0.0f)
        assertEquals(1.0f, VolumeSanitizer.sanitize(1.01f), 0.0f)
        assertEquals(0.0f, VolumeSanitizer.sanitize(-Float.MAX_VALUE), 0.0f)
        assertEquals(1.0f, VolumeSanitizer.sanitize(Float.MAX_VALUE), 0.0f)
        assertEquals(0.0f, VolumeSanitizer.sanitize(-0.0f), 0.0f)
        assertEquals(0.0f, VolumeSanitizer.sanitize(Float.NaN), 0.0f)
        assertEquals(0.0f, VolumeSanitizer.sanitize(Float.POSITIVE_INFINITY), 0.0f)
        assertEquals(0.0f, VolumeSanitizer.sanitize(Float.NEGATIVE_INFINITY), 0.0f)
    }

    @Test
    fun `sanitizer - snapToStep with extreme step parameters`() {
        assertEquals(0.5f, VolumeSanitizer.snapToStep(0.5f, 0.05f), 0.0001f)
        assertEquals(0.5f, VolumeSanitizer.snapToStep(0.5f, 0.0f), 0.0001f)
        assertEquals(0.5f, VolumeSanitizer.snapToStep(0.5f, -0.05f), 0.0001f)
        assertEquals(0.5f, VolumeSanitizer.snapToStep(0.5f, Float.NaN), 0.0001f)
        assertEquals(0.5f, VolumeSanitizer.snapToStep(0.5f, Float.POSITIVE_INFINITY), 0.0001f)
        assertEquals(0.5f, VolumeSanitizer.snapToStep(0.5f, Float.NEGATIVE_INFINITY), 0.0001f)
    }

    @Test
    fun `sanitizer - mathematical monotonicity oracle across 1000 random ordered inputs`() {
        val random = Random(42)
        val sampleCount = 1000
        val rawValues = FloatArray(sampleCount) {
            (random.nextFloat() * 20.0f) - 10.0f // [-10.0, 10.0]
        }.sortedArray()

        for (i in 0 until sampleCount - 1) {
            val v1 = rawValues[i]
            val v2 = rawValues[i + 1]

            // Monotonic property: v1 <= v2 ==> f(v1) <= f(v2)
            assertTrue("sanitize monotonic: $v1 <= $v2", VolumeSanitizer.sanitize(v1) <= VolumeSanitizer.sanitize(v2))
            assertTrue("snapToStep monotonic: $v1 <= $v2", VolumeSanitizer.snapToStep(v1, 0.05f) <= VolumeSanitizer.snapToStep(v2, 0.05f))
            assertTrue("increment monotonic: $v1 <= $v2", VolumeSanitizer.increment(v1, 0.05f) <= VolumeSanitizer.increment(v2, 0.05f))
            assertTrue("decrement monotonic: $v1 <= $v2", VolumeSanitizer.decrement(v1, 0.05f) <= VolumeSanitizer.decrement(v2, 0.05f))
        }
    }

    @Test
    fun `sanitizer - snapping idempotence invariant f(f(x)) == f(x)`() {
        val random = Random(1337)
        for (i in 1..500) {
            val raw = (random.nextFloat() * 4.0f) - 1.5f // [-1.5, 2.5]
            val once = VolumeSanitizer.snapToStep(raw, 0.05f)
            val twice = VolumeSanitizer.snapToStep(once, 0.05f)
            assertEquals("snapToStep must be strictly idempotent", once, twice, 0.00001f)
        }
    }

    @Test
    fun `sanitizer - discrete grid invariant on step 0_05 snaps to exact 5 percent intervals`() {
        for (i in 0..1000) {
            val raw = i / 1000.0f // [0.000, 1.000] in steps of 0.001
            val snapped = VolumeSanitizer.snapToStep(raw, 0.05f)

            // Result must be strictly on the grid {0.00, 0.05, 0.10, ..., 1.00}
            val gridIndex = (snapped * 20.0f).roundToInt()
            val reconstructed = gridIndex * 0.05f
            assertEquals("Snapped value must lie exactly on 0.05 grid", reconstructed, snapped, 0.0001f)
            assertTrue("Snapped value must be in [0.0, 1.0]", snapped in 0.0f..1.0f)
        }
    }

    // =========================================================================
    // HARNESS 3: PlaybackViewModel Debounce, Concurrency & Rollback
    // =========================================================================

    @Test
    fun `viewmodel - single failed volume dispatch rolls back without ui drift`() {
        controller._playbackState.value = WearPlaybackState(volume = 0.45f)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0.45f, viewModel.uiState.value.volume, 0.001f)

        // Controller configured to fail
        controller.shouldFailNextCommand = true

        // User sets volume to 0.75f
        viewModel.onSetVolume(0.75f)
        assertEquals("Optimistic volume set to 0.75f immediately", 0.75f, viewModel.uiState.value.volume, 0.001f)

        // Advance debounce window
        testDispatcher.scheduler.advanceTimeBy(60L)
        testDispatcher.scheduler.advanceUntilIdle()

        // UI must roll back to 0.45f
        assertEquals("Volume rolled back to authoritative value", 0.45f, viewModel.uiState.value.volume, 0.001f)
        assertEquals("Volume sync failed", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `viewmodel - rapid 20 crown ticks roll back to baseline on failure`() {
        controller._playbackState.value = WearPlaybackState(volume = 0.20f)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0.20f, viewModel.uiState.value.volume, 0.001f)

        controller.shouldFailNextCommand = true

        // 16 ticks of +0.05f -> reaches 1.0f optimistically
        for (i in 1..16) {
            viewModel.onVolumeChange(0.05f)
        }
        assertEquals(1.0f, viewModel.uiState.value.volume, 0.001f)

        // Before debounce expires: no command sent
        testDispatcher.scheduler.advanceTimeBy(30L)
        assertEquals(0, controller.dispatchedCommands.size)

        // After debounce expires: single command fails
        testDispatcher.scheduler.advanceTimeBy(30L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, controller.dispatchedCommands.size)
        // Must roll back to original baseline 0.20f
        assertEquals("Must roll back to baseline 0.20f", 0.20f, viewModel.uiState.value.volume, 0.001f)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `viewmodel - authoritative backend update received during pending debounce updates rollback baseline`() {
        controller._playbackState.value = WearPlaybackState(volume = 0.30f)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0.30f, viewModel.uiState.value.volume, 0.001f)

        controller.shouldFailNextCommand = true

        // User changes volume to 0.80f optimistically
        viewModel.onSetVolume(0.80f)
        assertEquals(0.80f, viewModel.uiState.value.volume, 0.001f)

        // At t=25ms (mid-debounce), phone pushes a state update with volume = 0.40f
        testDispatcher.scheduler.advanceTimeBy(25L)
        controller._playbackState.value = WearPlaybackState(volume = 0.40f)
        testDispatcher.scheduler.runCurrent()

        // Optimistic UI remains 0.80f because optimistic job is active
        assertEquals("Optimistic volume preserved during pending job", 0.80f, viewModel.uiState.value.volume, 0.001f)

        // At t=60ms (debounce expires), dispatch fails
        testDispatcher.scheduler.advanceTimeBy(35L)
        testDispatcher.scheduler.advanceUntilIdle()

        // UI must roll back to the NEW authoritative baseline 0.40f, NOT the stale 0.30f
        assertEquals("Rolls back to new authoritative volume from backend", 0.40f, viewModel.uiState.value.volume, 0.001f)
    }

    @Test
    fun `viewmodel - rapid overwrite cancels pending job and executes latest volume only`() {
        controller._playbackState.value = WearPlaybackState(volume = 0.50f)
        testDispatcher.scheduler.advanceUntilIdle()

        // Tick 1 at t=0
        viewModel.onSetVolume(0.60f)
        assertEquals(0.60f, viewModel.uiState.value.volume, 0.001f)

        // Advance 30ms (debounce not yet reached)
        testDispatcher.scheduler.advanceTimeBy(30L)

        // Tick 2 at t=30
        viewModel.onSetVolume(0.70f)
        assertEquals(0.70f, viewModel.uiState.value.volume, 0.001f)

        // Advance 30ms (t=60 total, tick 1 was cancelled, tick 2 at 30ms)
        testDispatcher.scheduler.advanceTimeBy(30L)
        assertEquals("No command dispatched yet for tick 2", 0, controller.dispatchedCommands.size)

        // Advance 30ms more (t=90 total, tick 2 expires)
        testDispatcher.scheduler.advanceTimeBy(30L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Exactly one command dispatched: 0.7
        assertEquals(1, controller.dispatchedCommands.size)
        assertEquals("setVolume:0.7", controller.dispatchedCommands.first())
        assertEquals(0.70f, viewModel.uiState.value.volume, 0.001f)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `viewmodel - concurrent volume requests do not crash or corrupt state`() = runTest(testDispatcher) {
        controller._playbackState.value = WearPlaybackState(volume = 0.50f)
        testDispatcher.scheduler.advanceUntilIdle()

        // Launch 50 concurrent volume changes
        val jobs = (1..50).map { step ->
            launch {
                val delta = if (step % 2 == 0) +0.05f else -0.05f
                viewModel.onVolumeChange(delta)
            }
        }
        jobs.forEach { it.join() }

        // Let debounce expire and idle
        testDispatcher.scheduler.advanceTimeBy(100L)
        testDispatcher.scheduler.advanceUntilIdle()

        val finalVolume = viewModel.uiState.value.volume
        assertFalse("Final volume must not be NaN", finalVolume.isNaN())
        assertTrue("Final volume in bounds [0.0, 1.0]", finalVolume in 0.0f..1.0f)
    }
}
