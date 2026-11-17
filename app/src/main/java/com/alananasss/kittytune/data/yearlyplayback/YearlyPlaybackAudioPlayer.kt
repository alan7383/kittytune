package com.alananasss.kittytune.data.yearlyplayback

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles looping background snippet playback for each slide of the SoundCloud Wrapped story.
 * Pauses background playback while active, and cleanly stops upon exit.
 */
class YearlyPlaybackAudioPlayer(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var exoPlayer: ExoPlayer? = null
    private var currentTrackUrn: String? = null
    private var playJob: Job? = null
    private var wasKittyTunePlaying = false

    init {
        try {
            if (MusicManager.player.isPlaying) {
                wasKittyTunePlaying = true
                MusicManager.player.pause()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error checking MusicManager playback state", e)
        }

        try {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = true
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed creating ExoPlayer for YearlyPlayback", e)
        }
    }

    fun playTrackSnippet(trackStub: ApiYearlyTrackStub?) {
        if (trackStub == null) return
        val urn = trackStub.urn ?: return
        if (urn == currentTrackUrn && exoPlayer?.isPlaying == true) {
            return
        }
        currentTrackUrn = urn

        playJob?.cancel()
        playJob = scope.launch {
            try {
                val trackId = urn.substringAfterLast(":").toLongOrNull() ?: return@launch
                val api = RetrofitClient.create(context)
                val track = withContext(Dispatchers.IO) {
                    runCatching { api.getTrackById(trackId) }.getOrNull()
                } ?: return@launch

                val streamUrl = withContext(Dispatchers.IO) {
                    StreamResolver.resolveStream(context, track)
                } ?: return@launch

                withContext(Dispatchers.Main) {
                    val player = exoPlayer ?: return@withContext
                    val mediaItem = MediaItem.fromUri(streamUrl)
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()
                    Log.d(TAG, "Playing background snippet for: ${trackStub.title}")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to play snippet for $urn", e)
            }
        }
    }

    fun pause() {
        try {
            exoPlayer?.pause()
        } catch (e: Throwable) {
            Log.w(TAG, "Error pausing audio", e)
        }
    }

    fun resume() {
        try {
            exoPlayer?.play()
        } catch (e: Throwable) {
            Log.w(TAG, "Error resuming audio", e)
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        currentTrackUrn = null
        try {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
        } catch (e: Throwable) {
            Log.w(TAG, "Error stopping audio player", e)
        }
    }

    fun release() {
        playJob?.cancel()
        playJob = null
        try {
            exoPlayer?.stop()
            exoPlayer?.release()
            exoPlayer = null
        } catch (e: Throwable) {
            Log.w(TAG, "Error releasing audio player", e)
        }
    }

    companion object {
        private const val TAG = "YearlyAudioPlayer"
    }
}
