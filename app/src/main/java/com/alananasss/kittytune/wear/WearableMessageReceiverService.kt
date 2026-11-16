package com.alananasss.kittytune.wear

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.media3.common.Player
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.sync.ConnectManager
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.wear.common.WearCodecs
import com.alananasss.kittytune.wear.common.WearConstants
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Background service receiving discrete playback control messages from Wear OS smartwatches
 * and routing them to [MusicManager] (for phone playback) or [Together] / [ConnectManager] (for desktop playback).
 */
class WearableMessageReceiverService : WearableListenerService() {

    companion object {
        private const val TAG = "WearableMessageReceiver"
        const val SMART_PREVIOUS_THRESHOLD_MS = 3000L
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Unhandled exception in WearableMessageReceiverService", throwable)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        val data = messageEvent.data

        Log.d(TAG, "onMessageReceived: path=$path from ${messageEvent.sourceNodeId}")

        serviceScope.launch {
            handleMessage(path, data)
        }
    }

    internal suspend fun handleMessage(path: String, data: ByteArray?) {
        val appContext = runCatching { applicationContext }.getOrNull()
        if (appContext != null) {
            ensurePlayerInitialized(appContext)
        }

        val isDesktop = WearableStatePublisher.activeTargetType == WearConstants.TARGET_TYPE_DESKTOP
        val audioManager = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        when (path) {
            WearConstants.ACTION_PLAY -> {
                if (isDesktop) {
                    forwardDesktopControl("play")
                } else {
                    try {
                        MusicManager.playWhenReadyRequested = true
                        MusicManager.player.play()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to resume audio playback", e)
                    }
                }
            }

            WearConstants.ACTION_PAUSE -> {
                if (isDesktop) {
                    forwardDesktopControl("pause")
                } else {
                    try {
                        MusicManager.playWhenReadyRequested = false
                        MusicManager.player.pause()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to pause audio playback", e)
                    }
                }
            }

            WearConstants.ACTION_PLAY_PAUSE -> {
                if (isDesktop) {
                    forwardDesktopControl("toggle")
                } else {
                    try {
                        val player = MusicManager.player
                        if (player.isPlaying) {
                            MusicManager.playWhenReadyRequested = false
                            player.pause()
                        } else {
                            MusicManager.playWhenReadyRequested = true
                            player.play()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to toggle play/pause", e)
                    }
                }
            }

            WearConstants.ACTION_NEXT -> {
                if (isDesktop) {
                    forwardDesktopControl("next")
                } else {
                    try {
                        MusicManager.onNextClick?.invoke() ?: run {
                            val player = MusicManager.player
                            if (player.hasNextMediaItem()) {
                                player.seekToNextMediaItem()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to skip to next track", e)
                    }
                }
            }

            WearConstants.ACTION_PREVIOUS -> {
                if (isDesktop) {
                    forwardDesktopControl("previous")
                } else {
                    try {
                        val player = MusicManager.player
                        val currentPos = player.currentPosition
                        if (currentPos > SMART_PREVIOUS_THRESHOLD_MS) {
                            player.seekTo(0L)
                        } else {
                            MusicManager.onPreviousClick?.invoke() ?: run {
                                if (player.hasPreviousMediaItem()) {
                                    player.seekToPreviousMediaItem()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to execute previous track logic", e)
                    }
                }
            }

            WearConstants.ACTION_VOLUME_UP -> {
                if (isDesktop) {
                    forwardDesktopVolumeDelta(0.05f)
                } else {
                    audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                }
            }

            WearConstants.ACTION_VOLUME_DOWN -> {
                if (isDesktop) {
                    forwardDesktopVolumeDelta(-0.05f)
                } else {
                    audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                }
            }

            WearConstants.ACTION_SET_VOLUME -> {
                val volumeRatio = WearCodecs.decodeVolume(data)
                if (isDesktop) {
                    forwardDesktopSetVolume(volumeRatio)
                } else {
                    audioManager?.let { am ->
                        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            am.getStreamMinVolume(AudioManager.STREAM_MUSIC)
                        } else {
                            0
                        }
                        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val safeMax = max.coerceAtLeast(min)
                        val targetIndex = (min + volumeRatio * (safeMax - min)).roundToInt().coerceIn(min, safeMax)
                        runCatching {
                            am.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
                        }.onFailure { e ->
                            Log.e(TAG, "Failed to set audio stream volume to $targetIndex", e)
                        }
                    }
                }
            }

            WearConstants.ACTION_LIKE_TOGGLE -> {
                val currentTrack = MusicManager.currentTrack
                if (currentTrack != null) {
                    runCatching {
                        if (appContext != null && !LikeRepository.isReady) {
                            LikeRepository.init(appContext)
                        }
                        if (LikeRepository.isTrackLiked(currentTrack.id)) {
                            LikeRepository.removeLike(currentTrack.id)
                        } else {
                            LikeRepository.addLike(currentTrack)
                        }
                    }
                }
            }

            WearConstants.ACTION_SHUFFLE_TOGGLE -> {
                MusicManager.onShuffleClick?.invoke()
            }

            WearConstants.ACTION_REPEAT_CYCLE -> {
                try {
                    val player = MusicManager.player
                    val nextMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    player.repeatMode = nextMode
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to cycle repeat mode", e)
                }
            }

            WearConstants.ACTION_TARGET_SELECT -> {
                val payload = WearCodecs.decodeTarget(data)
                if (payload != null) {
                    WearableStatePublisher.activeTargetType = payload.targetType
                    WearableStatePublisher.activeTargetId = payload.peerId
                    WearableStatePublisher.activeTargetName = payload.peerName ?: payload.targetType
                }
            }

            WearConstants.ACTION_SYNC_REQUEST -> {
                // Handled implicitly by publishCurrentState below
            }

            else -> {
                Log.d(TAG, "Ignoring unrecognized Wearable message path: $path")
            }
        }

        if (appContext != null) {
            WearableStatePublisher.publishCurrentState(appContext)
        }
    }

    private fun ensurePlayerInitialized(context: Context) {
        runCatching {
            MusicManager.init(context)
        }
    }

    private fun forwardDesktopControl(action: String) {
        runCatching {
            val position = try { MusicManager.player.currentPosition } catch (_: Throwable) { 0L }
            val activePeerId = WearableStatePublisher.activeTargetId

            if (Together.activeRoom != null) {
                Together.control(action, position)
            } else if (!activePeerId.isNullOrBlank()) {
                ConnectManager.command(activePeerId, action)
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to forward desktop control command: $action", e)
        }
    }

    private fun forwardDesktopVolumeDelta(delta: Float) {
        runCatching {
            val activePeerId = WearableStatePublisher.activeTargetId
            if (!activePeerId.isNullOrBlank()) {
                val peerState = ConnectManager.peers.value[activePeerId]
                val currentVol = peerState?.snapshot?.volume ?: 0.5f
                val newVol = (currentVol + delta).coerceIn(0f, 1f)
                forwardDesktopSetVolume(newVol)
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to forward desktop volume delta: $delta", e)
        }
    }

    private fun forwardDesktopSetVolume(volumeRatio: Float) {
        runCatching {
            val activePeerId = WearableStatePublisher.activeTargetId
            if (!activePeerId.isNullOrBlank()) {
                val safeRatio = if (volumeRatio.isNaN()) 0f else volumeRatio.coerceIn(0f, 1f)
                val targetVal = (safeRatio * 1000).toLong()
                ConnectManager.command(activePeerId, "volume", targetVal)
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to forward desktop volume: $volumeRatio", e)
        }
    }
}
