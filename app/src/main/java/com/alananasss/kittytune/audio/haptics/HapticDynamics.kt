package com.alananasss.kittytune.audio.haptics

import kotlin.math.max


/**
 * Turns raw beat intensity into an amplitude with contrast, so a drop can actually land.
 *
 * A drop only feels like an impact if the phone was not already buzzing at full tilt for the
 * thirty seconds before it. Driving the motor from absolute energy loses exactly that: a
 * build-up is loud, so it vibrates hard, and by the time the drop arrives there is nothing left
 * to give. The listener feels a wall of constant vibration instead of an event.
 *
 * So amplitude follows *excess over the recent past* rather than absolute level. A slow baseline
 * tracks how hard things have been lately; what counts is how far the current beat rises above
 * it. During a build-up the baseline climbs with the music and the excess stays small, so the
 * haptics stay as light ticks. At the drop the energy jumps while the baseline is still lagging,
 * the excess is large, and the full amplitude fires - which is the moment it is worth spending.
 */
class HapticDynamics(
    /** 0 = raw intensity, as before. 1 = pure contrast. */
    @Volatile var contrast: Float = DEFAULT_CONTRAST,
) {

    private var baseline: Float = 0f
    private var hasBaseline: Boolean = false

    /**
     * A second, much slower baseline. Comparing the two is what identifies a build-up: the fast
     * baseline climbs with the music while the slow one lags, and a sustained gap between them
     * means energy has been rising for a while rather than for one bar.
     */
    private var slowBaseline: Float = 0f

    /** What the music has been doing over the last several seconds. */
    enum class Trend { RISING, STEADY, FALLING }

    fun trend(): Trend {
        if (!hasBaseline) return Trend.STEADY
        val delta = baseline - slowBaseline
        return when {
            delta > TREND_THRESHOLD -> Trend.RISING
            delta < -TREND_THRESHOLD -> Trend.FALLING
            else -> Trend.STEADY
        }
    }

    /**
     * @param intensity 0..1 beat strength from the detector.
     * @param elapsedMs since the previous beat, so the baseline follows wall time rather than
     *   however often beats happen to arrive.
     */
    fun shape(intensity: Float, elapsedMs: Long): Float {
        val raw = intensity.coerceIn(0f, 1f)

        if (!hasBaseline) {
            hasBaseline = true
            baseline = raw
            slowBaseline = raw
            // Nothing to compare against yet; a first beat is not evidence of a trend.
            return raw
        }

        val amount = contrast.coerceIn(0f, 1f)

        // What counts is the size of the jump, not where it sits. Dividing by the remaining
        // distance to the ceiling instead would score 0.8 -> 1.0 as a maximal event and an
        // equally large 0.3 -> 0.5 as barely a third of one, so a quietly mastered track could
        // never land a drop.
        val headroom = ((raw - baseline) / REFERENCE_RISE).coerceIn(0f, 1f)

        // Baseline updated after measuring, so a beat is judged against the past, not itself.
        advanceBaseline(raw, elapsedMs)

        val contrasted = raw + (headroom - raw) * amount

        // Contrast must not erase absolute weight. A track with a relentless bass is *always*
        // above its own baseline-adjusted normal, so pure contrast would call every beat
        // unremarkable and keep a genuinely heavy track feeling thin. The floor keeps loudness
        // in the answer: heavy stays heavy, and the drop still has the rest of the range to
        // climb into.
        val floor = raw * ABSOLUTE_FLOOR
        val shaped = max(contrasted, floor)
        return shaped.coerceIn(if (raw > 0f) MIN_AUDIBLE else 0f, 1f)
    }

    /**
     * Asymmetric on purpose: rising slowly lets a long build-up lift the baseline without
     * swallowing the drop, while falling faster means a breakdown restores sensitivity in time
     * for what follows it.
     */
    private fun advanceBaseline(raw: Float, elapsedMs: Long) {
        val tau = if (raw > baseline) RISE_TAU_MS else FALL_TAU_MS
        val alpha = (elapsedMs.toFloat() / tau).coerceIn(0f, 1f)
        baseline += alpha * (raw - baseline)

        val slowAlpha = (elapsedMs.toFloat() / SLOW_TAU_MS).coerceIn(0f, 1f)
        slowBaseline += slowAlpha * (raw - slowBaseline)
    }

    /** A track change is not a continuation of the previous track's dynamics. */
    fun reset() {
        baseline = 0f
        slowBaseline = 0f
        hasBaseline = false
    }

    /** For diagnostics and the settings preview. */
    fun currentBaseline(): Float = baseline

    companion object {
        /**
         * Enough contrast to make drops land without making quiet passages feel dead. Exposed
         * as a setting because this is taste: some want the club in their hand, others want the
         * phone to stay calm on a commute.
         */
        const val DEFAULT_CONTRAST = 0.6f

        /** Roughly four seconds, so a 16-bar build-up lifts it but a single bar does not. */
        const val RISE_TAU_MS = 4_000f

        /** Faster, so sensitivity returns during a breakdown rather than after it. */
        const val FALL_TAU_MS = 1_800f

        /** Below this the motor produces nothing perceptible, so it is wasted current. */
        const val MIN_AUDIBLE = 0.08f

        /**
         * How much of the absolute level survives full contrast.
         *
         * Without this, contrast answers only "is this beat unusual for this track", and a
         * track whose bass never lets up has no unusual beats at all. At this floor a
         * continuously heavy track still drives roughly two thirds amplitude, while a drop
         * reaches the top.
         */
        const val ABSOLUTE_FLOOR = 0.65f

        /**
         * A beat this far above the recent baseline counts as a full-scale event.
         *
         * The detector already reports intensity relative to the track's own peaks, so this can
         * be a fixed figure: it means "a quarter of the range above what has been normal lately",
         * which is a drop in any material.
         */
        const val REFERENCE_RISE = 0.25f

        /** Long enough that only a sustained climb counts as a build-up, not one loud bar. */
        const val SLOW_TAU_MS = 14_000f

        /** How far the fast baseline must lead the slow one before it counts as a trend. */
        const val TREND_THRESHOLD = 0.07f
    }
}
