package com.alananasss.kittytune.audio.haptics

import kotlin.math.abs

/**
 * The analysed beat grid of the playing track, used to decide *when* a vibration is musically
 * legitimate.
 *
 * The haptics previously ran their own transient detector on band energy, independent of the
 * analysis engine. That detector cannot tell a kick from a door slam in the sample, it has no
 * idea where the bar starts, and it fires on whatever rises — which is why the buzzing felt
 * unrelated to the music rather than merely mistimed.
 *
 * This does not replace the energy detector; the two answer different questions. The grid knows
 * *when* a beat is due, which it knows exactly and in advance. The audio knows *whether* there
 * is anything there — a breakdown has beats on paper and nothing to feel. Gating one with the
 * other gives timing from the analysis and dynamics from the signal.
 *
 * @param periodMs beat length in the track's own timeline.
 * @param anchorMs a known beat, ideally a classified downbeat.
 * @param beatsPerBar how many beats from one downbeat to the next; 0 when the downbeat was not
 *   trusted, in which case bar position is simply unknown and never claimed.
 */
class BeatHapticGrid(
    val periodMs: Double,
    val anchorMs: Long,
    val beatsPerBar: Int,
) {
    val isUsable: Boolean get() = periodMs > 50.0

    /** How far [positionMs] sits from the nearest beat line, signed, in ms. */
    fun offsetFromBeat(positionMs: Long): Double {
        if (!isUsable) return Double.MAX_VALUE
        val elapsed = (positionMs - anchorMs).toDouble()
        val nearest = Math.round(elapsed / periodMs)
        return elapsed - nearest * periodMs
    }

    /**
     * True when [positionMs] is close enough to a beat line to count as on it.
     *
     * The window scales with tempo rather than being a constant: 60 ms is comfortably inside a
     * beat at 90 BPM and a third of one at 180.
     */
    fun isOnBeat(positionMs: Long, tolerance: Double = periodMs * TOLERANCE_FRACTION): Boolean =
        isUsable && abs(offsetFromBeat(positionMs)) <= tolerance

    /**
     * Which beat of the bar [positionMs] falls on, counted from 1, or null when the downbeat
     * was not trusted. Never guessed — a wrong "this is beat 1" is worse than no answer,
     * because the pulse it drives is the one the listener feels most.
     */
    fun beatInBar(positionMs: Long): Int? {
        if (!isUsable || beatsPerBar <= 1) return null
        val elapsed = (positionMs - anchorMs).toDouble()
        val beatIndex = Math.round(elapsed / periodMs)
        return Math.floorMod(beatIndex, beatsPerBar.toLong()).toInt() + 1
    }

    /** True on beat 1, false on any other beat, null when bar position is unknown. */
    fun isDownbeat(positionMs: Long): Boolean? = beatInBar(positionMs)?.let { it == 1 }

    /**
     * Emphasis for a beat, so the bar has a shape instead of four identical thumps.
     * Falls back to a flat 1.0 when the bar position is unknown.
     */
    fun emphasisAt(positionMs: Long): Float = when (beatInBar(positionMs)) {
        null -> 1.0f
        1 -> DOWNBEAT_EMPHASIS
        3 -> MID_BAR_EMPHASIS
        else -> OFFBEAT_EMPHASIS
    }

    companion object {
        /** A beat counts as hit within this fraction of a beat either side. */
        const val TOLERANCE_FRACTION = 0.18

        /** Beat 1 is what a listener counts from, so it gets the strongest pulse. */
        const val DOWNBEAT_EMPHASIS = 1.0f

        /** Beat 3 is the secondary accent in 4/4. */
        const val MID_BAR_EMPHASIS = 0.85f

        /** Beats 2 and 4 stay present but out of the way. */
        const val OFFBEAT_EMPHASIS = 0.7f
    }
}
