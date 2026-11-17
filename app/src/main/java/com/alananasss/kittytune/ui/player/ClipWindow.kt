package com.alananasss.kittytune.ui.player

/**
 * The part of a song that is played and shown as the song: a trailer's twenty seconds (round 2 of the tester's
 * list, item 6).
 *
 * The engine keeps playing the whole file in the song's own time, so the lyrics, which are written in that time,
 * stay in step. Only what is drawn and what is asked for is in the window's time: the bar runs from 0:00 to its
 * length, and a seek to a place on it is turned into the place in the song.
 */
data class ClipWindow(val startMs: Long, val endMs: Long) {

    val lengthMs: Long get() = (endMs - startMs).coerceAtLeast(1L)

    /** A moment in the song, as a moment of the window: before it is the start, after it the end. */
    fun toShown(songMs: Long): Long = (songMs - startMs).coerceIn(0L, lengthMs)

    /** A moment of the window, as a moment in the song. */
    fun toSong(shownMs: Long): Long = startMs + shownMs.coerceIn(0L, lengthMs)

    companion object {
        /** The window of a trailer for a song [totalMs] long: [lengthMs] from [startShare] of the way in. */
        fun forTrailer(totalMs: Long, lengthMs: Long, startShare: Double): ClipWindow {
            if (totalMs <= lengthMs + SHORT_SONG_SLACK_MS) return ClipWindow(0L, totalMs.coerceAtLeast(1L))
            val start = (totalMs * startShare).toLong().coerceAtMost(totalMs - lengthMs)
            return ClipWindow(start, start + lengthMs)
        }

        /** A song hardly longer than the window plays whole: there is no middle to cut it from. */
        private const val SHORT_SONG_SLACK_MS = 5_000L
    }
}
