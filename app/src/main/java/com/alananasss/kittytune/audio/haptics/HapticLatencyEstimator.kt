package com.alananasss.kittytune.audio.haptics

import kotlin.math.abs

/**
 * Measures how far the audio pipeline runs ahead of what the listener actually hears.
 *
 * [HapticAudioProcessor] sits in the ExoPlayer processor chain, which is *before* the AudioTrack
 * buffer. It therefore sees a kick drum while that kick is still queued, and firing the vibrator
 * at that moment puts the buzz ahead of the sound by the whole output latency. KittyTune's track
 * is opened with `FLAG_DEEP_BUFFER`, which deliberately trades latency for battery, so the lead
 * is large: at 160 BPM a beat lasts 375 ms, and a 200 ms lead is more than half a beat. That is
 * why the haptics feel unrelated to the music rather than merely a little early.
 *
 * The lead cannot be looked up — it depends on the device, the output route (speaker, Bluetooth,
 * USB) and the buffer the sink negotiated. So it is measured: the processor reports how much
 * audio it has *written*, the player reports how much is *audible*, and the difference is the
 * lead to delay the vibration by.
 */
class HapticLatencyEstimator {

    private companion object {
        /**
         * Plausible range for an Android output path. Below this a reading is noise; above it,
         * something is wrong (a stale position, a seek mid-measurement) and compensating would
         * make the timing worse than not compensating at all.
         */
        const val MIN_PLAUSIBLE_MS = 10L

        /**
         * ExoPlayer's PCM buffer provider targets 250-750 ms and KittyTune opens its track with
         * FLAG_DEEP_BUFFER, so the real lead sits high: ~560 ms measured on a Pixel 10 Pro XL.
         * A ceiling near that figure would reject normal readings whenever they jitter upward,
         * dropping compensation to zero intermittently - worse than a constant offset.
         */
        const val MAX_PLAUSIBLE_MS = 1_200L

        /** Smoothing. Positions arrive at different rates from two threads, so readings jitter. */
        const val SMOOTHING = 0.15f

        /** A reading older than this is not evidence about the present. */
        const val STALE_AFTER_MS = 2_000L
    }

    @Volatile
    private var writtenPositionMs: Long = 0

    @Volatile
    private var audiblePositionMs: Long = 0

    @Volatile
    private var audibleStampMs: Long = 0

    @Volatile
    private var smoothedLeadMs: Float = 0f

    @Volatile
    private var hasReading: Boolean = false

    /** Called from the audio thread: how much audio has been pushed into the sink so far. */
    fun onAudioWritten(positionMs: Long) {
        writtenPositionMs = positionMs
    }

    /**
     * Called from the main thread with the player's own position, which already accounts for the
     * sink's latency — that is exactly what makes the subtraction meaningful.
     */
    fun onAudiblePosition(positionMs: Long, nowMs: Long) {
        audiblePositionMs = positionMs
        audibleStampMs = nowMs
    }

    /** Both sides have to be reset together; a seek invalidates the pairing, not one half of it. */
    fun reset() {
        writtenPositionMs = 0
        audiblePositionMs = 0
        audibleStampMs = 0
        smoothedLeadMs = 0f
        hasReading = false
    }

    /**
     * Resets the position pairing on a seek or track transition without discarding the learned
     * hardware latency estimate, since the physical audio buffer characteristics do not change.
     */
    fun onDiscontinuity(positionMs: Long) {
        writtenPositionMs = positionMs
        audiblePositionMs = positionMs
        audibleStampMs = System.currentTimeMillis()
    }

    /**
     * How long to delay a vibration so it coincides with the sound that caused it.
     *
     * Returns 0 when there is no trustworthy reading — an uncompensated haptic is early, but a
     * haptic delayed by a wrong number is early *and* unpredictable.
     */
    fun leadMs(nowMs: Long): Long {
        val stamp = audibleStampMs
        if (stamp == 0L || nowMs - stamp > STALE_AFTER_MS) return 0L

        // The player position was sampled in the past; the listener has heard more since.
        val audibleNow = audiblePositionMs + (nowMs - stamp)
        val raw = writtenPositionMs - audibleNow
        if (raw < MIN_PLAUSIBLE_MS || raw > MAX_PLAUSIBLE_MS) return 0L

        smoothedLeadMs = if (!hasReading) {
            hasReading = true
            raw.toFloat()
        } else {
            smoothedLeadMs + SMOOTHING * (raw - smoothedLeadMs)
        }
        return smoothedLeadMs.toLong()
    }

    /** For diagnostics: the current estimate without updating it. */
    fun currentLeadMs(): Long = if (hasReading) smoothedLeadMs.toLong() else 0L

    /** True once a plausible measurement has been taken. */
    fun hasEstimate(): Boolean = hasReading && abs(smoothedLeadMs) >= MIN_PLAUSIBLE_MS
}
