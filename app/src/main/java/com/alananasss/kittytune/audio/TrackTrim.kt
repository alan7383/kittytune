package com.alananasss.kittytune.audio

/**
 * Playing only the parts of a track you want, without touching the file (issue #33).
 *
 * SoundCloud is full of re-uploads that exist only because someone wanted a song without its guest verse, or
 * without ninety seconds of intro. The request was to do that in the player instead: remember a few
 * timestamps per track and skip over them on the fly. Nothing is edited, nothing is re-encoded, and removing
 * the trim gives the original back.
 *
 * Two ways to say what you want, because they are the two things people actually mean:
 *
 * - [TrimMode.CUT] — "take *this* out". The segments are holes; everything else plays.
 * - [TrimMode.KEEP] — "play only *this*". The segments are the whole track as far as playback is concerned;
 *   it starts at the first one and ends at the last.
 *
 * They are not redundant. Cutting an intro means writing down where the song really starts *and* trusting
 * nothing else needs removing; keeping a range says it in one span and ends the track early as well.
 *
 * This is the rule on its own, with no player and no database attached, so it can be tested directly — which
 * matters more here than usual, because the failure mode of a wrong answer is a jump that lands back inside
 * the region it jumped out of, forever.
 */
enum class TrimMode { CUT, KEEP }

/**
 * A half-open span of a track, `[startMs, endMs)`.
 *
 * Half-open so that spans can be laid end to end without arguing about the millisecond they share, and so
 * that a jump to `endMs` is by definition outside the span it just left. That is what makes the skip
 * terminate instead of re-triggering on arrival.
 */
data class TrimSegment(val startMs: Long, val endMs: Long) {
    val isValid: Boolean get() = startMs >= 0 && endMs > startMs
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

/** What playback should do at a given moment. */
sealed interface TrimAction {

    /** Nothing to do here. */
    data object Continue : TrimAction

    /** Move to [positionMs] — with a fade, which is the caller's job. */
    data class JumpTo(val positionMs: Long) : TrimAction

    /**
     * The kept part is over; treat the track as finished.
     *
     * Only reachable in [TrimMode.KEEP]. Distinct from a jump because "go to the end" and "the track has
     * ended" behave differently at the end of a queue.
     */
    data object Finished : TrimAction
}

/**
 * A track's trim: a mode and the spans it applies to.
 *
 * Build it with [of], which normalises. Overlapping and out-of-order spans are not user error worth
 * rejecting — dragging two handles across each other is a normal thing to do — so they are merged.
 */
class TrackTrim private constructor(
    val mode: TrimMode,
    /** Sorted, non-overlapping, non-touching, all valid. */
    val segments: List<TrimSegment>,
) {

    val isEmpty: Boolean get() = segments.isEmpty()

    /**
     * Where playback should start.
     *
     * In [TrimMode.KEEP] that is the first kept instant. In [TrimMode.CUT] it is zero unless a cut begins the
     * track, in which case starting at zero would mean starting inside a hole — so the answer is where that
     * hole ends.
     */
    fun startPositionMs(): Long {
        if (segments.isEmpty()) return 0L
        return when (mode) {
            TrimMode.KEEP -> segments.first().startMs
            TrimMode.CUT -> if (segments.first().startMs <= 0L) segments.first().endMs else 0L
        }
    }

    /**
     * @param positionMs where playback is now.
     * @param durationMs the track's real length, or zero when it is not known yet. Without it, [TrimMode.KEEP]
     *   cannot tell "past the last kept span" from "not there yet", so it reports [TrimAction.Finished] on the
     *   span alone and the caller decides.
     */
    fun actionFor(positionMs: Long, durationMs: Long): TrimAction {
        if (segments.isEmpty()) return TrimAction.Continue
        val position = positionMs.coerceAtLeast(0L)

        return when (mode) {
            TrimMode.CUT -> cutAction(position, durationMs)
            TrimMode.KEEP -> keepAction(position, durationMs)
        }
    }

    /**
     * Inside a hole, jump to where it ends — resolving a run of adjacent holes in one hop.
     *
     * Normalisation already merges touching spans, so the loop is belt and braces rather than load-bearing;
     * it is bounded regardless, because a rule that can ask for an unbounded number of jumps would freeze
     * playback rather than skip anything.
     */
    private fun cutAction(positionMs: Long, durationMs: Long): TrimAction {
        var target = positionMs
        var hops = 0
        while (hops < segments.size + 1) {
            val hole = segments.firstOrNull { target >= it.startMs && target < it.endMs }
                ?: return if (hops == 0) TrimAction.Continue else landing(target, durationMs)
            target = hole.endMs
            hops++
        }
        return landing(target, durationMs)
    }

    /**
     * A cut that runs to the end of the track leaves nowhere to land, so the track is over rather than
     * seeking past its own length — which some decoders answer with silence and others with an error.
     */
    private fun landing(target: Long, durationMs: Long): TrimAction =
        if (durationMs > 0 && target >= durationMs) TrimAction.Finished else TrimAction.JumpTo(target)

    private fun keepAction(positionMs: Long, durationMs: Long): TrimAction {
        if (segments.any { positionMs >= it.startMs && positionMs < it.endMs }) return TrimAction.Continue

        // Before the first kept instant, or in a gap between two kept spans: forward to the next one.
        val next = segments.firstOrNull { it.startMs > positionMs }
        if (next != null) return TrimAction.JumpTo(next.startMs)

        // Past the last kept span. There is nothing left that was asked for.
        return TrimAction.Finished
    }

    /** How much of the track actually plays, for a screen that wants to say so. */
    fun playedDurationMs(durationMs: Long): Long {
        if (durationMs <= 0) return 0L
        val kept = segments.sumOf { segment ->
            (minOf(segment.endMs, durationMs) - segment.startMs).coerceAtLeast(0L)
        }
        return when (mode) {
            TrimMode.KEEP -> kept
            TrimMode.CUT -> (durationMs - kept).coerceAtLeast(0L)
        }
    }

    override fun equals(other: Any?): Boolean =
        other is TrackTrim && other.mode == mode && other.segments == segments

    override fun hashCode(): Int = 31 * mode.hashCode() + segments.hashCode()

    override fun toString(): String = "TrackTrim($mode, $segments)"

    companion object {

        /** Nothing trimmed. */
        fun none(): TrackTrim = TrackTrim(TrimMode.CUT, emptyList())

        /**
         * @return [segments] cleaned up: invalid spans dropped, the rest sorted and merged where they
         *   overlap or touch.
         *
         * Merging touching spans matters beyond tidiness. Two holes that meet at the same millisecond would
         * otherwise need two jumps, and the instant between them is a frame of audio nobody asked to hear.
         */
        fun of(mode: TrimMode, segments: List<TrimSegment>): TrackTrim {
            val sorted = segments.filter { it.isValid }.sortedBy { it.startMs }
            val merged = mutableListOf<TrimSegment>()
            for (segment in sorted) {
                val last = merged.lastOrNull()
                if (last != null && segment.startMs <= last.endMs) {
                    merged[merged.lastIndex] = last.copy(endMs = maxOf(last.endMs, segment.endMs))
                } else {
                    merged.add(segment)
                }
            }
            return TrackTrim(mode, merged)
        }
    }
}
