package com.alananasss.kittytune.data.together

import kotlin.math.abs

internal const val TOGETHER_SYNC_TOLERANCE_MS = 750L

data class TogetherSyncPlayback(
    val trackId: Long,
    val source: String?,
    val positionMs: Long,
    val isPlaying: Boolean,
)

internal enum class TogetherSyncStatus {
    CONNECTING,
    WAITING_FOR_HOST,
    LOCAL_TRACK,
    SYNCING,
    SYNCED,
}

internal fun Together.Room.syncStatus(
    now: Long,
    freshnessMs: Long,
    playback: () -> TogetherSyncPlayback?,
): TogetherSyncStatus {
    val track = nowPlaying
    if (track == null || syncStateHostId == null || syncStateHostId != hostId ||
        now - stateReceivedAt !in 0L..freshnessMs
    ) return TogetherSyncStatus.WAITING_FOR_HOST
    if (track.isLocalOnly) return TogetherSyncStatus.LOCAL_TRACK

    val local = playback() ?: return TogetherSyncStatus.SYNCING
    val estimatedHostPosition = currentPositionMs(now + syncTravelMs)
    return if (local.trackId == track.id && local.source == track.source &&
        local.isPlaying == isPlaying && abs(local.positionMs - estimatedHostPosition) <= TOGETHER_SYNC_TOLERANCE_MS
    ) TogetherSyncStatus.SYNCED else TogetherSyncStatus.SYNCING
}
