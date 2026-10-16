package com.alananasss.kittytune.data

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * How a track's lyrics are shifted against its audio: by one offset, or by two that drift apart.
 *
 * One offset is enough when the lyrics were timed against the same recording and only start late or early.
 * When they were timed against another version (a different master, a radio edit, a slightly different speed)
 * they drift: right at the start, seconds off by the end. Two points fix that. The offset is
 * [offsetMs] at [anchorMs] in the track and [endOffsetMs] at [endAtMs], straight in between and carried on the
 * same way before and after.
 *
 * Times are in the audio's own timeline, offsets are added to the playhead to get the lyrics' time, as with the
 * single offset before.
 */
data class LyricsSync(
    val offsetMs: Long = 0L,
    val anchorMs: Long = 0L,
    val endAtMs: Long? = null,
    val endOffsetMs: Long? = null,
) {
    val isTwoPoint: Boolean get() = endAtMs != null && endOffsetMs != null

    val isNone: Boolean get() = !isTwoPoint && offsetMs == 0L

    /** How many milliseconds the offset changes per millisecond of audio; 0 with a single offset. */
    private val slope: Double
        get() {
            val endAt = endAtMs ?: return 0.0
            val endOffset = endOffsetMs ?: return 0.0
            return (endOffset - offsetMs).toDouble() / (endAt - anchorMs)
        }

    /** The offset to add at [positionMs] of the audio. */
    fun offsetAt(positionMs: Long): Long =
        if (!isTwoPoint) offsetMs else offsetMs + (slope * (positionMs - anchorMs)).roundToLong()

    /** Where in the audio the lyrics reach [lyricTimeMs]: the playhead `t` with `t + offsetAt(t) = lyricTimeMs`. */
    fun audioPositionFor(lyricTimeMs: Long): Long {
        if (!isTwoPoint) return lyricTimeMs - offsetMs
        val s = slope
        return ((lyricTimeMs - offsetMs + s * anchorMs) / (1.0 + s)).roundToLong()
    }

    /**
     * Nudged by [deltaMs] at [positionMs]: the single offset, or with two points the one nearer the playhead,
     * since that is the part of the song being listened to and fixed.
     */
    fun adjustedAt(positionMs: Long, deltaMs: Long): LyricsSync {
        val endAt = endAtMs
        val endOffset = endOffsetMs
        if (endAt == null || endOffset == null) return copy(offsetMs = clamp(offsetMs + deltaMs))
        return if (abs(positionMs - anchorMs) <= abs(positionMs - endAt)) {
            copy(offsetMs = clamp(offsetMs + deltaMs))
        } else {
            copy(endOffsetMs = clamp(endOffset + deltaMs))
        }
    }

    /** The first point moved to [positionMs], keeping the offset the lyrics have there now. */
    fun withStartAt(positionMs: Long): LyricsSync {
        val here = offsetAt(positionMs)
        val endAt = endAtMs
        // A start at or after the end would leave no span to draw a line through; the end goes.
        if (endAt != null && positionMs >= endAt - MIN_SPAN_MS) return LyricsSync(offsetMs = here, anchorMs = positionMs)
        return copy(offsetMs = here, anchorMs = positionMs)
    }

    /**
     * A second point at [positionMs] with the offset the lyrics have there now, or null when it is too close to
     * the first one, or would make the lyrics run at a speed no recording differs by.
     */
    fun withEndAt(positionMs: Long): LyricsSync? {
        if (positionMs - anchorMs < MIN_SPAN_MS) return null
        val candidate = copy(endAtMs = positionMs, endOffsetMs = offsetAt(positionMs))
        return candidate.takeIf { abs(it.slope) <= MAX_SLOPE }
    }

    /** Back to one offset, the one the lyrics have at [positionMs]. */
    fun singleAt(positionMs: Long): LyricsSync = LyricsSync(offsetMs = offsetAt(positionMs))

    private fun clamp(offset: Long) = offset.coerceIn(LyricsOffsetRepository.MIN_OFFSET_MS, LyricsOffsetRepository.MAX_OFFSET_MS)

    companion object {
        val NONE = LyricsSync()

        /** The two points have to be at least this far apart for the line between them to mean anything. */
        const val MIN_SPAN_MS = 20_000L

        /** A 20 % speed difference is already two different songs; anything steeper is a mis-click. */
        const val MAX_SLOPE = 0.2
    }
}
