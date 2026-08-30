package com.alananasss.kittytune.data.stats

/**
 * How much of a track was actually heard (issue #33).
 *
 * The old accounting added 250 ms to a counter on every tick of the progress loop and then wrote a row
 * only when the track ended in one of a few specific ways. Two things went wrong with that, and both
 * show up as "sometimes tracks just don't appear":
 *
 * 1. **A listen that ended any other way was thrown away.** Closing the app, stopping, or loading
 *    something else discarded the counter without writing anything. You could listen to a whole album
 *    and have none of it recorded.
 * 2. **Wall-clock ticks are not listening time.** The counter kept climbing at 2× speed as if the
 *    music were playing at 1×, and a seek backwards over the same chorus counted twice.
 *
 * This is the accounting on its own, with no player and no database attached, so it can be tested
 * directly. It is fed positions and told when playback starts, stops and jumps; it answers how much
 * was heard.
 *
 * Time is measured in *media* milliseconds — how much of the track went past — rather than seconds on
 * the clock. That is the honest measure of listening: at double speed a three-minute track takes ninety
 * seconds of your life but you did hear three minutes of music, and every service counts it that way.
 */
class ListenSessionAccumulator(
    /** Where playback began, in the track. A resume from the middle is not a listen from the start. */
    private val startPositionMs: Long = 0L,
) {

    /** Media milliseconds heard so far. Never decreases. */
    var listenedMs: Long = 0L
        private set

    /** The furthest point reached, for judging whether the track was finished. */
    var furthestPositionMs: Long = startPositionMs
        private set

    private var lastPositionMs: Long = startPositionMs
    private var playing: Boolean = false

    /**
     * Playback advanced to [positionMs].
     *
     * Only the forward distance since the last observation is credited, and only while playing. A jump
     * — a seek, a loop back to the start, the position resetting on a reload — credits nothing: it is
     * movement through the track, not listening. [JUMP_THRESHOLD_MS] is what separates the two.
     */
    fun onPosition(positionMs: Long) {
        val position = positionMs.coerceAtLeast(0L)
        if (playing) {
            val advanced = position - lastPositionMs
            if (advanced in 1..JUMP_THRESHOLD_MS) listenedMs += advanced
        }
        if (position > furthestPositionMs) furthestPositionMs = position
        lastPositionMs = position
    }

    /**
     * Playback started or resumed at [positionMs].
     *
     * The position is taken as the new baseline without crediting anything, so the gap while paused
     * never counts.
     */
    fun onPlaying(positionMs: Long) {
        lastPositionMs = positionMs.coerceAtLeast(0L)
        if (lastPositionMs > furthestPositionMs) furthestPositionMs = lastPositionMs
        playing = true
    }

    /** Playback paused or stopped. Later positions credit nothing until [onPlaying]. */
    fun onPaused() {
        playing = false
    }

    /**
     * The listener jumped deliberately. Same effect as a pause and resume: the distance across the
     * jump is not listening, and what comes after it is.
     */
    fun onSeek(positionMs: Long) {
        lastPositionMs = positionMs.coerceAtLeast(0L)
        if (lastPositionMs > furthestPositionMs) furthestPositionMs = lastPositionMs
    }

    companion object {
        /**
         * The largest forward step still treated as playback rather than a jump.
         *
         * The progress loop reports about four times a second, so a real step is a few hundred
         * milliseconds; two seconds leaves room for a stutter or a slow frame without letting a seek
         * of any real size through.
         */
        const val JUMP_THRESHOLD_MS = 2_000L
    }
}

/**
 * When a session counts as having listened to something (issue #33).
 *
 * The old aggregates decided this by how the track *ended*: only `PLAY_COMPLETE`, `MANUAL_REPLAY` and
 * `REPEAT_ONE_LOOP` rows were counted, so a track played to the last ten seconds and then skipped
 * scored nothing while one that ran out on its own scored a full play. That is why the same listening
 * produced different statistics depending on which button was pressed at the end.
 *
 * Judged on what was heard instead, which is both what people expect and what makes skips and
 * completions comparable.
 */
object ListenRules {

    /** Enough of a listen to count, whatever the track's length. Thirty seconds is the usual line. */
    const val MIN_LISTEN_MS = 30_000L

    /** Or this much of it, for tracks too short to reach [MIN_LISTEN_MS] in a meaningful way. */
    const val MIN_LISTEN_FRACTION = 0.5

    /** How close to the end counts as having finished it — outros and trailing silence. */
    const val COMPLETION_FRACTION = 0.95

    /**
     * @return whether [listenedMs] of a track lasting [trackDurationMs] is a play.
     *
     * A track of unknown length is judged on the absolute threshold alone: guessing a fraction of an
     * unknown duration would either count everything or nothing.
     */
    fun countsAsPlay(listenedMs: Long, trackDurationMs: Long): Boolean {
        if (listenedMs <= 0L) return false
        if (listenedMs >= MIN_LISTEN_MS) return true
        if (trackDurationMs <= 0L) return false
        return listenedMs >= trackDurationMs * MIN_LISTEN_FRACTION
    }

    /**
     * @return whether the track was heard to the end, judged on how far playback reached rather than
     *   on how much was heard: someone who skips the first minute and listens to the rest did finish
     *   it.
     */
    fun isComplete(furthestPositionMs: Long, trackDurationMs: Long): Boolean {
        if (trackDurationMs <= 0L) return false
        return furthestPositionMs >= trackDurationMs * COMPLETION_FRACTION
    }

    /**
     * @return whether this looks like a skip: playback left early, and not much of it was heard.
     *
     * Deliberately not "did not complete". Pausing halfway through and coming back tomorrow is not a
     * skip, and a track that counted as a play was listened to whatever happened at the end.
     */
    fun isSkip(listenedMs: Long, furthestPositionMs: Long, trackDurationMs: Long): Boolean =
        !isComplete(furthestPositionMs, trackDurationMs) &&
            !countsAsPlay(listenedMs, trackDurationMs)
}
