package com.alananasss.kittytune.wear.common

import kotlinx.serialization.Serializable

/**
 * Unified, immutable snapshot of playback state shared between phone and Wear OS watch.
 *
 * Designed for transmission over Google Play Services Wearable [com.google.android.gms.wearable.DataClient]
 * under [WearConstants.PATH_PLAYBACK_STATE].
 */
@Serializable
data class WearPlaybackState(
    val trackId: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 0.0f,
    val targetDevice: String = WearConstants.TARGET_PHONE_LABEL,
    val targetType: String = WearConstants.TARGET_TYPE_PHONE,
    val targetDeviceId: String? = null,
    val isLiked: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: String = WearConstants.REPEAT_MODE_NONE,
    val availableTargets: List<TargetDeviceInfo> = emptyList(),
    val timestamp: Long = 0L,
) {
    /**
     * Calculates the estimated current playhead position on the watch without network roundtrips.
     * When playing, advances monotonically based on elapsed wall-clock time since [timestamp].
     * Automatically clamped to `[0, durationMs]` if duration is known.
     */
    fun projectedPositionMs(nowMs: Long = System.currentTimeMillis()): Long {
        val base = positionMs.coerceAtLeast(0L)
        val projected = if (isPlaying && timestamp > 0L) {
            val elapsed = (nowMs - timestamp).coerceAtLeast(0L)
            base + elapsed
        } else {
            base
        }
        return if (durationMs > 0L) projected.coerceAtMost(durationMs) else projected
    }

    /**
     * Returns the normalized progress ratio between 0.0f and 1.0f.
     */
    fun progressFraction(nowMs: Long = System.currentTimeMillis()): Float {
        if (durationMs <= 0L) return 0.0f
        return (projectedPositionMs(nowMs).toFloat() / durationMs.toFloat()).coerceIn(0.0f, 1.0f)
    }

    val isDesktop: Boolean get() = targetType == WearConstants.TARGET_TYPE_DESKTOP
    val isPhone: Boolean get() = targetType == WearConstants.TARGET_TYPE_PHONE
    val hasTrack: Boolean get() = trackId != 0L || title.isNotBlank()

    companion object {
        val EMPTY = WearPlaybackState()
    }
}
