package com.alananasss.kittytune.wear.presentation.volume

import kotlin.math.roundToInt

/**
 * Pure functions for volume validation, clamping, snapping, and cross-platform conversions.
 */
object VolumeSanitizer {

    const val MIN_VOLUME = 0.0f
    const val MAX_VOLUME = 1.0f
    const val DEFAULT_STEP = 0.05f // 5% step (20 discrete intervals)
    const val DESKTOP_MAX_VOLUME = 1000L

    /**
     * Sanitizes raw volume floats.
     * Guarantees result is never NaN, never Infinite, and strictly within [0.0f, 1.0f].
     */
    fun sanitize(volume: Float): Float {
        if (volume.isNaN() || volume.isInfinite()) return MIN_VOLUME
        return volume.coerceIn(MIN_VOLUME, MAX_VOLUME)
    }

    /**
     * Increments volume by [step] with sanitization and snapping.
     */
    fun increment(currentVolume: Float, step: Float = DEFAULT_STEP): Float {
        val sanitized = sanitize(currentVolume)
        return snapToStep((sanitized + step).coerceIn(MIN_VOLUME, MAX_VOLUME), step)
    }

    /**
     * Decrements volume by [step] with sanitization and snapping.
     */
    fun decrement(currentVolume: Float, step: Float = DEFAULT_STEP): Float {
        val sanitized = sanitize(currentVolume)
        return snapToStep((sanitized - step).coerceIn(MIN_VOLUME, MAX_VOLUME), step)
    }

    /**
     * Snaps volume to the nearest discrete step to prevent floating-point accumulation drift.
     */
    fun snapToStep(volume: Float, step: Float = DEFAULT_STEP): Float {
        val clamped = sanitize(volume)
        if (step <= 0.0f) return clamped
        val stepCount = (clamped / step).roundToInt()
        return (stepCount * step).coerceIn(MIN_VOLUME, MAX_VOLUME)
    }

    /**
     * Converts normalized volume [0.0f, 1.0f] to human-readable integer percentage (0..100).
     */
    fun toPercentage(volume: Float): Int {
        return (sanitize(volume) * 100.0f).roundToInt()
    }

    /**
     * Formats normalized volume as percentage string (e.g. "0%", "65%", "100%").
     */
    fun formatPercentage(volume: Float): String {
        return "${toPercentage(volume)}%"
    }

    /**
     * Converts normalized float volume to Phone AudioManager stream step index (e.g. 0..15).
     */
    fun toStreamStep(volume: Float, maxSteps: Int): Int {
        if (maxSteps <= 0) return 0
        return (sanitize(volume) * maxSteps).roundToInt().coerceIn(0, maxSteps)
    }

    /**
     * Converts Phone AudioManager stream step index to normalized float volume.
     */
    fun fromStreamStep(stepIndex: Int, maxSteps: Int): Float {
        if (maxSteps <= 0) return MIN_VOLUME
        return (stepIndex.toFloat() / maxSteps.toFloat()).coerceIn(MIN_VOLUME, MAX_VOLUME)
    }

    /**
     * Converts normalized float volume to Desktop Connect protocol integer (0..1000).
     */
    fun toDesktopVolume(volume: Float): Long {
        return (sanitize(volume) * DESKTOP_MAX_VOLUME).roundToInt().toLong().coerceIn(0L, DESKTOP_MAX_VOLUME)
    }

    /**
     * Converts Desktop Connect protocol integer (0..1000) to normalized float volume.
     */
    fun fromDesktopVolume(desktopVolume: Long): Float {
        return (desktopVolume.coerceIn(0L, DESKTOP_MAX_VOLUME).toFloat() / DESKTOP_MAX_VOLUME.toFloat())
            .coerceIn(MIN_VOLUME, MAX_VOLUME)
    }
}
