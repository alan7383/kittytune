package com.alananasss.kittytune.ui.player

import androidx.media3.common.Player

/**
 * Decides when a play press cannot simply resume the player (issue #82).
 *
 * After a stream that failed to resolve, the player is stopped (idle) and still holds the item of
 * the track that played before. Calling `play()` then starts nothing, or would start the wrong song,
 * so the current track has to be loaded again.
 */
internal object PlayButtonRecovery {

    /**
     * Whether a press on the play/pause button means "pause".
     *
     * Only true while something is audible or on its way: playing, loading a track the listener
     * asked to hear, or rebuffering with the intent to play. A wish flag left over on a stopped
     * player is not playback, and treating it as such swallowed the press.
     */
    fun shouldPause(
        playerIsPlaying: Boolean,
        playerPlayWhenReady: Boolean,
        playbackState: Int,
        isLoading: Boolean,
        wantsToPlay: Boolean,
    ): Boolean = playerIsPlaying ||
        (isLoading && wantsToPlay) ||
        (playerPlayWhenReady && playbackState == Player.STATE_BUFFERING)

    fun mustReload(playbackState: Int, heldMediaId: String?, currentTrackId: Long): Boolean {
        if (playbackState == Player.STATE_IDLE) return true
        // Only plain numeric ids are compared: other formats are owned by other code paths and
        // would make every press reload a track that is playing fine.
        val heldId = heldMediaId?.toLongOrNull() ?: return false
        return heldId != currentTrackId
    }
}
