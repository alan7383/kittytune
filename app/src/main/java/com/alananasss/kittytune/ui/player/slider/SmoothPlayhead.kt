package com.alananasss.kittytune.ui.player.slider

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

/** A report further than this from where the bar is drawn is a seek, not drift; the bar slides there. */
private const val SEEK_THRESHOLD_MS = 1_200L

/** How long the bar takes to slide to a seek's target, wheel notches included. */
private const val SEEK_GLIDE_NANOS = 280_000_000L

/** Share of the drift between the drawn position and a fresh report that is taken up per report. */
private const val DRIFT_CORRECTION = 0.35f

/** The bar never runs further than this past the last report, should the reports stop coming. */
private const val MAX_RUN_AHEAD_MS = 1_000L

/** How often a paused bar checks whether it has something to animate. */
private const val IDLE_CHECK_MS = 64L

/**
 * The playhead as the seek bars draw it, moving every frame instead of in the player's steps (PR #77).
 *
 * The player reports its position four or five times a second, and the engine's own figure moves in
 * buffer-sized steps, so a bar bound straight to it advanced in visible jerks, worst in the wide player.
 * Between reports this runs a clock at the rate the reports have been advancing at (which also covers a
 * changed playback speed), and takes up the difference to each new report a little at a time. A report far
 * from the drawn position is a seek: the bar slides there smoothly.
 *
 * @param reportedMs the player's last reported position.
 * @param isRunning whether the playhead is moving: playing, and neither loading nor being dragged.
 * @param followsInput while dragging or scrubbing: draw [reportedMs] as is, no glide and no prediction.
 * @param trackKey a different track starts over from its own position (the mix glide handles that change).
 */
@Composable
fun rememberSmoothPlayhead(
    reportedMs: Long,
    isRunning: Boolean,
    followsInput: Boolean,
    trackKey: Any?,
): State<Long> {
    val clock = remember { PlayheadClock() }
    val shown = remember { mutableLongStateOf(reportedMs) }
    val reported by rememberUpdatedState(reportedMs)
    val running by rememberUpdatedState(isRunning)
    val direct by rememberUpdatedState(followsInput)
    val key by rememberUpdatedState(trackKey)

    LaunchedEffect(clock) {
        while (isActive) {
            val animate = running || clock.isGliding
            if (!animate) {
                // Still: follow what the player says without asking for frames, unless a seek starts a slide.
                shown.longValue = clock.step(reported, key, direct, running = false, now = System.nanoTime())
                if (!clock.isGliding) delay(IDLE_CHECK_MS)
                continue
            }
            withFrameNanos { now ->
                shown.longValue = clock.step(reported, key, direct, running, now)
            }
        }
    }
    return shown
}

/** The bookkeeping behind [rememberSmoothPlayhead]; plain fields, advanced once per frame. */
internal class PlayheadClock {
    private var lastKey: Any? = UNSET
    private var lastReport = 0L
    private var lastReportNanos = 0L
    private var lastFrameNanos = 0L
    private var drawn = 0.0
    private var rate = 1.0
    private var glideFrom: Double? = null
    private var glideAtNanos = 0L

    val isGliding: Boolean get() = glideFrom != null

    fun step(reported: Long, key: Any?, direct: Boolean, running: Boolean, now: Long): Long {
        val safeReported = reported.coerceAtLeast(0L)
        if (key != lastKey || direct) {
            lastKey = key
            glideFrom = null
            snapTo(safeReported, now)
            return safeReported
        }
        val frameMs = (now - lastFrameNanos).coerceIn(0L, 200_000_000L) / 1_000_000.0
        lastFrameNanos = now
        if (running) drawn += frameMs * rate

        if (safeReported != lastReport) {
            val sinceReportMs = if (lastReportNanos == 0L) 0.0 else (now - lastReportNanos) / 1_000_000.0
            val jumpedBack = safeReported < lastReport - 50
            if (jumpedBack || abs(safeReported - drawn) > SEEK_THRESHOLD_MS) {
                // A seek, a notch, or a jump back: slide there from wherever the bar is.
                glideFrom = drawn
                glideAtNanos = now
            } else {
                if (sinceReportMs in 80.0..1_000.0) {
                    val observed = (safeReported - lastReport) / sinceReportMs
                    if (observed in 0.2..4.5) rate += (observed - rate) * 0.25
                }
                drawn += (safeReported - drawn) * DRIFT_CORRECTION
            }
            lastReport = safeReported
            lastReportNanos = now
        }

        val from = glideFrom
        if (from != null) {
            val t = (now - glideAtNanos).toFloat() / SEEK_GLIDE_NANOS
            val elapsedMs = if (lastReportNanos > 0L) {
                ((now - lastReportNanos).coerceAtLeast(0L) / 1_000_000.0).coerceAtMost(MAX_RUN_AHEAD_MS.toDouble())
            } else 0.0
            val target = safeReported + if (running) elapsedMs * rate else 0.0
            if (t >= 1f) {
                glideFrom = null
                drawn = target
            } else {
                drawn = from + (target - from) * FastOutSlowInEasing.transform(t)
            }
        } else if (running) {
            drawn = drawn.coerceAtMost(safeReported + MAX_RUN_AHEAD_MS * rate)
        } else {
            drawn = safeReported.toDouble()
        }
        return drawn.toLong().coerceAtLeast(0L)
    }

    private fun snapTo(reported: Long, now: Long) {
        drawn = reported.toDouble()
        lastReport = reported
        lastReportNanos = now
        lastFrameNanos = now
    }

    private companion object {
        val UNSET = Any()
    }
}
