package com.alananasss.kittytune.e2e.harness

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Authoritative contracts and constants for Wear OS companion,
 * Data Layer protocol, Connect protocol, and volume/rotary specifications.
 * Derived from PROJECT.md, ORIGINAL_REQUEST.md, and Explorers 1-3.
 */
object E2EContracts {
    // Module and Package Constraints
    const val EXPECTED_APPLICATION_ID = "com.alananasss.kittytune"
    const val WEAR_PACKAGE_NAMESPACE = "com.alananasss.kittytune.wear"
    const val COMPILE_SDK = 37
    const val MIN_SDK_PHONE = 26
    const val MIN_SDK_WEAR = 30
    const val WEAR_STANDALONE_METADATA = "com.google.android.wearable.standalone"
    const val WEAR_WATCH_FEATURE = "android.hardware.type.watch"

    // DataClient Paths & Keys
    const val PATH_PLAYBACK_STATE = "/kittytune/playback_state"
    const val KEY_TRACK_ID = "track_id"
    const val KEY_TITLE = "title"
    const val KEY_ARTIST = "artist"
    const val KEY_ALBUM = "album"
    const val KEY_ARTWORK_URL = "artwork_url"
    const val KEY_IS_PLAYING = "is_playing"
    const val KEY_IS_LOADING = "is_loading"
    const val KEY_POSITION_MS = "position_ms"
    const val KEY_DURATION_MS = "duration_ms"
    const val KEY_VOLUME = "volume"
    const val KEY_TARGET_DEVICE = "target_device"
    const val KEY_TARGET_TYPE = "target_type"
    const val KEY_IS_LIKED = "is_liked"
    const val KEY_TIMESTAMP = "timestamp"

    // MessageClient Command Paths
    const val PATH_PLAY = "/kittytune/playback/play"
    const val PATH_PAUSE = "/kittytune/playback/pause"
    const val PATH_PLAY_PAUSE = "/kittytune/playback/play_pause"
    const val PATH_NEXT = "/kittytune/playback/next"
    const val PATH_PREVIOUS = "/kittytune/playback/previous"
    const val PATH_VOLUME_UP = "/kittytune/playback/volume_up"
    const val PATH_VOLUME_DOWN = "/kittytune/playback/volume_down"
    const val PATH_SET_VOLUME = "/kittytune/playback/set_volume"
    const val PATH_SYNC_REQUEST = "/kittytune/playback/sync_request"
    const val PATH_TARGET_SELECT = "/kittytune/target/select"

    // Target Types
    const val TARGET_TYPE_PHONE = "PHONE"
    const val TARGET_TYPE_DESKTOP = "DESKTOP"

    // Volume Constants
    const val VOLUME_MIN = 0.0f
    const val VOLUME_MAX = 1.0f
    const val VOLUME_STEP = 0.05f // 5% per button tap / crown tick
    const val DESKTOP_VOLUME_MAX = 1000L // Connect protocol uses 0..1000

    // Connect & Together Protocol
    const val CONNECT_LAN_PORT = 47654
    const val CONNECT_RELAY_PORT = 8787
    const val ACTION_PLAY = "play"
    const val ACTION_PAUSE = "pause"
    const val ACTION_NEXT = "next"
    const val ACTION_PREVIOUS = "previous"
    const val ACTION_VOLUME = "volume"

    // Smart Previous Threshold (ms)
    const val SMART_PREVIOUS_THRESHOLD_MS = 3000L

    /**
     * Encodes a float volume (0.0..1.0) into a 4-byte array (IEEE 754 Big Endian).
     */
    fun encodeVolumePayload(volume: Float): ByteArray {
        val clamped = volume.coerceIn(VOLUME_MIN, VOLUME_MAX)
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat(clamped).array()
    }

    /**
     * Decodes a 4-byte array into a float volume.
     * Also supports string-encoded floats ("0.75").
     */
    fun decodeVolumePayload(bytes: ByteArray): Float {
        if (bytes.isEmpty()) return 0.0f
        val text = bytes.toString(Charsets.UTF_8).trim()
        val textFloat = text.toFloatOrNull()
        if (textFloat != null && !textFloat.isNaN() && !textFloat.isInfinite()) {
            return textFloat.coerceIn(VOLUME_MIN, VOLUME_MAX)
        }
        if (bytes.size == 4) {
            val f = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).float
            if (!f.isNaN() && !f.isInfinite()) {
                return f.coerceIn(VOLUME_MIN, VOLUME_MAX)
            }
        }
        return 0.0f
    }

    /**
     * Computes the volume adjustment given a rotary crown scroll delta.
     * Positive delta: clockwise roll -> volume increase.
     * Negative delta: counter-clockwise roll -> volume decrease.
     */
    fun computeRotaryVolume(currentVolume: Float, scrollDelta: Float, sensitivity: Float = 0.05f): Float {
        val deltaVolume = scrollDelta * sensitivity
        return (currentVolume + deltaVolume).coerceIn(VOLUME_MIN, VOLUME_MAX)
    }

    /**
     * Converts normalized float volume (0.0..1.0) to Desktop Connect integer (0..1000).
     */
    fun floatToDesktopVolume(volume: Float): Long {
        return (volume.coerceIn(VOLUME_MIN, VOLUME_MAX) * DESKTOP_VOLUME_MAX).toLong()
    }

    /**
     * Converts Desktop Connect integer (0..1000) to normalized float volume (0.0..1.0).
     */
    fun desktopVolumeToFloat(volume: Long): Float {
        return (volume.coerceIn(0L, DESKTOP_VOLUME_MAX).toFloat() / DESKTOP_VOLUME_MAX)
    }

    /**
     * Converts normalized float volume to Phone AudioManager stream steps (e.g. 0..15).
     */
    fun floatToStreamSteps(volume: Float, maxSteps: Int): Int {
        if (maxSteps <= 0) return 0
        return Math.round(volume.coerceIn(VOLUME_MIN, VOLUME_MAX) * maxSteps)
    }

    /**
     * Decides whether 'previous' command should seek to track start or skip to prior song.
     */
    fun resolvePreviousAction(positionMs: Long): PreviousBehavior {
        return if (positionMs > SMART_PREVIOUS_THRESHOLD_MS) {
            PreviousBehavior.SEEK_TO_START
        } else {
            PreviousBehavior.SKIP_TO_PREVIOUS_TRACK
        }
    }

    enum class PreviousBehavior {
        SEEK_TO_START,
        SKIP_TO_PREVIOUS_TRACK
    }
}
