package com.alananasss.kittytune.wear.common

/**
 * Shared constants for communication between the Wear OS companion (:wear)
 * and the Android handheld phone (:app) over Google Play Services Wearable Data Layer API.
 */
object WearConstants {

    // =========================================================================
    // DataClient Paths (State Synchronization)
    // =========================================================================

    /**
     * Path for the active playback state synced from phone to watch.
     * Delivered via [com.google.android.gms.wearable.DataClient].
     */
    const val PATH_PLAYBACK_STATE = "/kittytune/playback_state"

    /**
     * Optional path for the list of available target playback devices.
     */
    const val PATH_AVAILABLE_TARGETS = "/kittytune/targets"

    // =========================================================================
    // MessageClient Paths (Discrete Command Dispatch)
    // =========================================================================

    /** Command to resume/play audio playback. */
    const val ACTION_PLAY = "/kittytune/playback/play"

    /** Command to pause audio playback. */
    const val ACTION_PAUSE = "/kittytune/playback/pause"

    /** Command to toggle play/pause state. */
    const val ACTION_PLAY_PAUSE = "/kittytune/playback/play_pause"

    /** Command to skip to the next track. */
    const val ACTION_NEXT = "/kittytune/playback/next"

    /** Command to skip to the previous track (or restart track if > 3s). */
    const val ACTION_PREVIOUS = "/kittytune/playback/previous"

    /** Command to increment volume by one step. */
    const val ACTION_VOLUME_UP = "/kittytune/playback/volume_up"

    /** Command to decrement volume by one step. */
    const val ACTION_VOLUME_DOWN = "/kittytune/playback/volume_down"

    /**
     * Command to set volume to an explicit level.
     * Payload: 4-byte Float (0.0f..1.0f) or UTF-8 Float string.
     */
    const val ACTION_SET_VOLUME = "/kittytune/playback/set_volume"

    /** Command to toggle liked status of current track. */
    const val ACTION_LIKE_TOGGLE = "/kittytune/playback/like_toggle"

    /** Command to toggle shuffle mode. */
    const val ACTION_SHUFFLE_TOGGLE = "/kittytune/playback/shuffle_toggle"

    /** Command to cycle repeat mode (NONE -> ALL -> ONE -> NONE). */
    const val ACTION_REPEAT_CYCLE = "/kittytune/playback/repeat_cycle"

    /** Command from watch requesting immediate broadcast of current state. */
    const val ACTION_SYNC_REQUEST = "/kittytune/playback/sync_request"

    /**
     * Command to switch the active playback target device.
     * Payload: UTF-8 JSON encoded [TargetSelectionPayload] or plain device identifier.
     */
    const val ACTION_TARGET_SELECT = "/kittytune/target/select"

    // =========================================================================
    // DataMap Keys (for PATH_PLAYBACK_STATE)
    // =========================================================================

    /** Current track ID (Long). Default: 0L. */
    const val KEY_TRACK_ID = "track_id"

    /** Current track title (String). */
    const val KEY_TITLE = "title"

    /** Current track artist (String). */
    const val KEY_ARTIST = "artist"

    /** Current track album name (String). */
    const val KEY_ALBUM = "album"

    /** Artwork image URL (String, nullable/optional). */
    const val KEY_ARTWORK_URL = "artwork_url"

    /** True if audio is actively playing, false if paused (Boolean). */
    const val KEY_IS_PLAYING = "is_playing"

    /** True if audio is buffering/loading (Boolean). */
    const val KEY_IS_LOADING = "is_loading"

    /** Playback playhead position in milliseconds (Long). */
    const val KEY_POSITION_MS = "position_ms"

    /** Total track duration in milliseconds (Long). */
    const val KEY_DURATION_MS = "duration_ms"

    /** Normalized volume fraction from 0.0f to 1.0f (Float). */
    const val KEY_VOLUME = "volume"

    /** Human-readable label of target device (e.g. "Phone", "MacBook Pro") (String). */
    const val KEY_TARGET_DEVICE = "target_device"

    /** Type of target device: [TARGET_TYPE_PHONE] or [TARGET_TYPE_DESKTOP] (String). */
    const val KEY_TARGET_TYPE = "target_type"

    /** Remote peer ID when target type is DESKTOP, or empty/null for PHONE (String). */
    const val KEY_TARGET_DEVICE_ID = "target_device_id"

    /** True if the active track is liked (Boolean). */
    const val KEY_IS_LIKED = "is_liked"

    /** True if shuffle is enabled (Boolean). */
    const val KEY_SHUFFLE_ENABLED = "shuffle_enabled"

    /** Repeat mode string: "NONE", "ALL", "ONE" (String). */
    const val KEY_REPEAT_MODE = "repeat_mode"

    /** JSON-serialized list of available [TargetDeviceInfo] items (String). */
    const val KEY_AVAILABLE_TARGETS = "available_targets"

    /**
     * Epoch timestamp in milliseconds when this state was generated (Long).
     * Used both to force Data Layer updates on repeated states and for monotonic
     * playhead projection on the watch.
     */
    const val KEY_TIMESTAMP = "timestamp"

    // =========================================================================
    // Target Device Constants
    // =========================================================================

    const val TARGET_TYPE_PHONE = "PHONE"
    const val TARGET_TYPE_DESKTOP = "DESKTOP"
    const val TARGET_PHONE_ID = "phone"
    const val TARGET_PHONE_LABEL = "Phone"

    // =========================================================================
    // Repeat Modes
    // =========================================================================

    const val REPEAT_MODE_NONE = "NONE"
    const val REPEAT_MODE_ALL = "ALL"
    const val REPEAT_MODE_ONE = "ONE"
}
