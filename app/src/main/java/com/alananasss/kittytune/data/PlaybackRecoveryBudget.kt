package com.alananasss.kittytune.data

/** Limits recovery across repeated prepare/error cycles for one playback request. */
internal class PlaybackRecoveryBudget(private val maxRetries: Int = 2) {
    private var trackId: Long? = null
    private var retries = 0

    fun reset() {
        trackId = null
        retries = 0
    }

    fun tryRecover(failedTrackId: Long): Boolean {
        if (trackId != failedTrackId) {
            trackId = failedTrackId
            retries = 0
        }
        if (retries >= maxRetries) return false
        retries++
        return true
    }
}
