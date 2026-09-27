package com.alananasss.kittytune

import com.alananasss.kittytune.audio.haptics.HapticLatencyEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The haptics felt unrelated to the music because the processor analyses audio that is still
 * queued in a deep buffer: at 160 BPM a beat is 375 ms, so a 200 ms lead is more than half a
 * beat. These tests pin down the measurement that closes that gap — and, just as importantly,
 * when it refuses to guess.
 */
class HapticLatencyEstimatorTest {

    private fun estimator(writtenMs: Long, audibleMs: Long, sampledAt: Long = 1_000L) =
        HapticLatencyEstimator().apply {
            onAudioWritten(writtenMs)
            onAudiblePosition(audibleMs, sampledAt)
        }

    @Test
    fun `measures the gap between written and audible audio`() {
        // 200 ms of audio is queued but not yet heard.
        val e = estimator(writtenMs = 10_200, audibleMs = 10_000)
        assertEquals(200L, e.leadMs(1_000L))
    }

    @Test
    fun `accounts for time passing since the player was sampled`() {
        // The position was read 50 ms ago; the listener has heard 50 ms more since, so the
        // remaining lead is smaller than the raw difference.
        val e = estimator(writtenMs = 10_200, audibleMs = 10_000, sampledAt = 1_000L)
        assertEquals(150L, e.leadMs(1_050L))
    }

    @Test
    fun `smooths successive readings`() {
        val e = estimator(writtenMs = 10_200, audibleMs = 10_000)
        val first = e.leadMs(1_000L)
        assertEquals("First reading is taken as-is", 200L, first)

        // A jittery second reading must not yank the estimate across.
        e.onAudioWritten(10_400)
        e.onAudiblePosition(10_000, 1_000L)
        val second = e.leadMs(1_000L)
        assertTrue("Smoothed, not jumped: got $second", second in 201L..260L)
    }

    @Test
    fun `refuses to compensate without a plausible reading`() {
        // Nothing measured yet.
        assertEquals(0L, HapticLatencyEstimator().leadMs(1_000L))

        // Written behind audible - only possible from a stale or mismatched pair.
        assertEquals(0L, estimator(writtenMs = 9_000, audibleMs = 10_000).leadMs(1_000L))

        // Absurdly large: a seek caught mid-measurement. An uncompensated haptic is early;
        // one delayed by a wrong number is early and unpredictable.
        assertEquals(0L, estimator(writtenMs = 20_000, audibleMs = 10_000).leadMs(1_000L))

        // Below the floor, the reading is noise rather than latency.
        assertEquals(0L, estimator(writtenMs = 10_005, audibleMs = 10_000).leadMs(1_000L))
    }

    @Test
    fun `the real deep-buffer lead is accepted, not rejected as implausible`() {
        // Measured on a Pixel 10 Pro XL: ~560 ms. ExoPlayer targets a 250-750 ms PCM buffer and
        // KittyTune opens its track with FLAG_DEEP_BUFFER. A ceiling near 600 would reject this
        // whenever it jittered upward, dropping compensation to zero intermittently.
        val e = estimator(writtenMs = 10_560, audibleMs = 10_000)
        assertEquals(560L, e.leadMs(1_000L))

        // At 160 BPM that is more than a beat and a half - the whole reason the haptics felt
        // unrelated to the music.
        assertTrue("560 ms is over a beat at club tempo", 560 / (60_000.0 / 160.0) > 1.4)
    }

    @Test
    fun `a stale player position is not evidence about now`() {
        val e = estimator(writtenMs = 10_200, audibleMs = 10_000, sampledAt = 1_000L)
        assertEquals("Fresh", 200L, e.leadMs(1_000L))
        assertEquals("Three seconds later it means nothing", 0L, e.leadMs(4_000L))
    }

    @Test
    fun `a seek clears both halves of the pairing`() {
        val e = estimator(writtenMs = 10_200, audibleMs = 10_000)
        assertEquals(200L, e.leadMs(1_000L))

        e.reset()
        assertEquals("No estimate survives a seek", 0L, e.leadMs(1_000L))
        assertTrue(!e.hasEstimate())
    }

    @Test
    fun `the compensation is meaningful at club tempo`() {
        // Why this matters: the lead being corrected is a large fraction of a beat.
        val e = estimator(writtenMs = 10_200, audibleMs = 10_000)
        val lead = e.leadMs(1_000L)
        val beatMs = 60_000.0 / 160.0
        assertTrue(
            "A ${lead} ms lead is over a third of a ${beatMs.toInt()} ms beat",
            lead / beatMs > 0.33,
        )
    }

    @Test
    fun `onDiscontinuity preserves learned latency and realigns position`() {
        val e = estimator(writtenMs = 10_560, audibleMs = 10_000)
        assertEquals(560L, e.leadMs(1_000L))
        assertTrue(e.hasEstimate())

        // Seek forward by 60 seconds
        val now = System.currentTimeMillis()
        e.onDiscontinuity(70_000L)
        assertTrue("Learned hardware latency is preserved across seeks", e.hasEstimate())
        assertEquals(560L, e.currentLeadMs())

        // Audio continues to be written at seeked target
        e.onAudioWritten(70_560L)
        e.onAudiblePosition(70_000L, now)
        assertEquals(560L, e.leadMs(now))
    }
}
