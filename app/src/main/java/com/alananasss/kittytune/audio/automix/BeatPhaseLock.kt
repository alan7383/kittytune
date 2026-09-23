/**
 * Developed by Jason-Marshall Fastner, Germany <jasonfastner@protonmail.com>
 * Questions, feedback, or beat-matching debates? Feel free to reach out via email!
 *
 * Note: Cats always land on their feet, and with this engine, your transitions will too.
 */
package com.alananasss.kittytune.audio.automix

import kotlin.math.abs

/**
 * Keeps two decks on the same beat *phase*, not merely the same tempo.
 *
 * Two tracks can both be exactly 128.0 BPM and still sound wrong: start the incoming deck
 * 15 ms off and you hear an echo rather than one beat, and the low frequencies of the two
 * kicks partially cancel. A tempo match alone never guarantees this — it only guarantees the
 * error stays constant instead of drifting.
 *
 * The error is unavoidable in practice: a compressed-audio seek lands on a frame boundary,
 * buffering delays the start by an unpredictable amount, and the renderer begins output
 * whenever its buffer happens to flush. So the phase is not predicted, it is *measured after
 * the deck is running* and then corrected:
 *
 * 1. While the incoming deck is still silent, correct with one exact seek — inaudible.
 * 2. During the fade, correct residual drift with a speed nudge of at most [MAX_NUDGE],
 *    which is far below the threshold where a pitch change becomes audible.
 */
object BeatPhaseLock {

    /**
     * Largest speed deviation once the incoming deck is at full level, as a fraction of base
     * speed. 1.5 % is about a quarter of a semitone — inaudible, and enough to hold a locked
     * pair together against clock drift.
     */
    const val MAX_NUDGE = 0.015f

    /**
     * Largest speed deviation while the incoming deck is still silent or nearly so.
     *
     * Correction authority is scaled by how audible the deck currently is: a deck at zero
     * volume can be pulled hard without anyone hearing it, and that is exactly the moment a
     * large start-up offset needs to be absorbed. Waiting until it is audible to correct
     * gently means being out of phase for the part of the fade people actually hear.
     */
    const val MAX_NUDGE_SILENT = 0.05f

    /**
     * Speed authority for a deck currently at [audibility] (0 = silent, 1 = full level).
     */
    fun nudgeAuthority(audibility: Float): Float {
        val a = audibility.coerceIn(0f, 1f)
        return MAX_NUDGE_SILENT + (MAX_NUDGE - MAX_NUDGE_SILENT) * a
    }

    /** Below this error the decks count as locked and nothing is touched. */
    const val LOCK_TOLERANCE_MS = 4.0

    /** Above this error a seek is worth it; below it, the speed nudge alone is used. */
    const val SEEK_THRESHOLD_MS = 12.0

    /**
     * A track's beat grid: a known beat time plus the beat length, both in the track's own
     * timeline. [anchorMs] should be a classified downbeat so bar and phrase math stays valid.
     */
    data class Grid(val anchorMs: Long, val periodMs: Double) {
        val isUsable: Boolean get() = periodMs > 1.0

        fun deckAt(positionMs: Long, rate: Float = 1f) = Deck(positionMs, anchorMs, periodMs, rate)

        /**
         * Time of the grid line closest to [positionMs], in either direction.
         *
         * The low-end handover is placed with this rather than with "the next line after the
         * halfway point": at 160 BPM a 16-beat phrase is six seconds, so the next phrase line
         * can easily fall past the end of the fade. Picking the nearest one keeps the swap on a
         * real musical boundary and inside the fade.
         */
        fun nearestLineMs(positionMs: Long, beatsPerLine: Int = 4): Long {
            val lineMs = periodMs * beatsPerLine
            if (lineMs <= 0.0) return positionMs
            val elapsed = (positionMs - anchorMs).toDouble()
            val nearest = Math.round(elapsed / lineMs)
            return (anchorMs + nearest * lineMs).toLong()
        }

        /**
         * The grid line closest to [target] that still lies within [minMs]..[maxMs], or null when
         * the window contains no line at all.
         *
         * Clamping the nearest line into the window instead would put the result on no musical
         * boundary whatsoever while still looking like a grid-aligned value - which is worse than
         * admitting there is no line, because the caller cannot tell the two apart.
         */
        fun nearestLineWithin(target: Long, beatsPerLine: Int, minMs: Long, maxMs: Long): Long? {
            val lineMs = periodMs * beatsPerLine
            if (lineMs <= 0.0 || minMs > maxMs) return null
            // Line indices that fall inside the window, then the one closest to the target.
            // Computed directly rather than by walking out from the target: the target can sit
            // far outside the window, and a bounded walk would give up before reaching it.
            val firstK = Math.ceil((minMs - anchorMs) / lineMs).toLong()
            val lastK = Math.floor((maxMs - anchorMs) / lineMs).toLong()
            if (firstK > lastK) return null
            val k = Math.round((target - anchorMs) / lineMs).coerceIn(firstK, lastK)
            return (anchorMs + k * lineMs).toLong()
        }

        /**
         * Time of the next bar line strictly after [positionMs]. The low-end handover is timed
         * to this: a bass change on a downbeat reads as musical, anywhere else as a dropout.
         */
        fun nextBarLineMs(positionMs: Long, beatsPerBar: Int = 4): Long {
            val barMs = periodMs * beatsPerBar
            if (barMs <= 0.0) return positionMs
            val elapsed = (positionMs - anchorMs).toDouble()
            val nextBar = Math.floor(elapsed / barMs) + 1
            return (anchorMs + nextBar * barMs).toLong()
        }

        companion object {
            /** Null when the BPM is unknown, so callers skip phase locking instead of locking to noise. */
            fun of(anchorMs: Long, bpm: Float): Grid? =
                if (bpm > 1f) Grid(anchorMs, 60_000.0 / bpm) else null
        }
    }

    /**
     * One deck's position on its own beat grid.
     *
     * @param positionMs current playback position in the track's own (unstretched) timeline.
     * @param anchorMs a known beat time in that same timeline — a classified downbeat.
     * @param periodMs beat length in that timeline.
     * @param rate playback speed multiplier; 1.0 when the deck is not tempo-matched.
     */
    data class Deck(
        val positionMs: Long,
        val anchorMs: Long,
        val periodMs: Double,
        val rate: Float = 1f,
    ) {
        val isUsable: Boolean get() = periodMs > 1.0 && rate > 0.01f

        /** How far into the current beat the deck is, in its own timeline. */
        fun beatPhaseMs(): Double {
            val elapsed = (positionMs - anchorMs).toDouble()
            return elapsed - Math.floor(elapsed / periodMs) * periodMs
        }

        /** Wall-clock milliseconds until this deck's next beat line. */
        fun msToNextBeat(): Double = (periodMs - beatPhaseMs()) / rate
    }

    /**
     * Signed phase error in wall-clock milliseconds.
     *
     * Positive means the incoming deck's beat arrives *after* the outgoing deck's — it is
     * running late and has to catch up. The result is wrapped into half a beat either way,
     * so the correction is always the short way round.
     *
     * Returns 0.0 when either grid is unusable, so callers fall through to no correction.
     */
    fun phaseErrorMs(outgoing: Deck, incoming: Deck): Double {
        if (!outgoing.isUsable || !incoming.isUsable) return 0.0
        val beatWallMs = outgoing.periodMs / outgoing.rate
        if (beatWallMs <= 1.0) return 0.0

        val raw = incoming.msToNextBeat() - outgoing.msToNextBeat()
        return wrapToHalfBeat(raw, beatWallMs)
    }

    /** Folds an error into (-beat/2, +beat/2] so corrections take the shortest path. */
    fun wrapToHalfBeat(errorMs: Double, beatWallMs: Double): Double {
        if (beatWallMs <= 0.0) return errorMs
        var e = errorMs - Math.floor(errorMs / beatWallMs) * beatWallMs
        if (e > beatWallMs / 2.0) e -= beatWallMs
        return e
    }

    /**
     * Media-timeline delta to seek the incoming deck by so its beat lines up.
     *
     * A late deck (positive error) must skip forward. The deck's own timeline runs at [Deck.rate]
     * relative to wall clock, so the wall-clock error is scaled back into media milliseconds.
     *
     * Rounded rather than truncated: truncation always biases towards under-correcting, which
     * leaves a residual offset in the same direction on every transition.
     */
    fun correctionSeekMs(errorMs: Double, incoming: Deck): Long =
        Math.round(errorMs * incoming.rate)

    /**
     * Speed multiplier that erases [errorMs] over [windowMs] of playback, clamped to [MAX_NUDGE].
     *
     * A deck running late needs to cover extra ground in the same time, so it speeds up.
     * The clamp means a large error is only partly corrected in one window; the caller
     * re-measures and applies another nudge, which converges without ever being audible.
     */
    fun nudgeSpeed(
        errorMs: Double,
        windowMs: Long,
        baseSpeed: Float,
        maxNudge: Float = MAX_NUDGE,
    ): Float {
        if (windowMs <= 0L || abs(errorMs) < LOCK_TOLERANCE_MS) return baseSpeed
        val limit = maxNudge.coerceIn(0f, MAX_NUDGE_SILENT).toDouble()
        val fraction = (errorMs / windowMs).coerceIn(-limit, limit)
        return (baseSpeed * (1.0 + fraction)).toFloat()
    }

    /** True when the measured error is worth a seek rather than a gradual nudge. */
    fun needsSeek(errorMs: Double): Boolean = abs(errorMs) >= SEEK_THRESHOLD_MS

    /** True when the decks are close enough that no correction should be applied. */
    fun isLocked(errorMs: Double): Boolean = abs(errorMs) < LOCK_TOLERANCE_MS
}
