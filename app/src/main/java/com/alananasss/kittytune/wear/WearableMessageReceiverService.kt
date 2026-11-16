package com.alananasss.kittytune.wear

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.media3.common.Player
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.sync.ConnectManager
import com.alananasss.kittytune.wear.common.WearCodecs
import com.alananasss.kittytune.wear.common.WearConstants
import com.alananasss.kittytune.wear.common.WearCommandResult
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

/**
 * Background service receiving discrete playback control messages from Wear OS smartwatches
 * and routing them to [MusicManager] (for phone playback) or [ConnectManager] (for desktop playback).
 */
class WearableMessageReceiverService : WearableListenerService() {

    companion object {
        private const val TAG = "WearableMessageReceiver"
        const val SMART_PREVIOUS_THRESHOLD_MS = 3000L
        private val PHONE_PLAYER_ACTIONS = setOf(
            WearConstants.ACTION_PLAY, WearConstants.ACTION_PAUSE, WearConstants.ACTION_PLAY_PAUSE,
            WearConstants.ACTION_NEXT, WearConstants.ACTION_PREVIOUS,
            WearConstants.ACTION_SHUFFLE_TOGGLE, WearConstants.ACTION_REPEAT_CYCLE
        )
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Unhandled exception in WearableMessageReceiverService", throwable)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private val commandMutex = Mutex()

    override fun onRequest(nodeId: String, path: String, data: ByteArray): Task<ByteArray> {
        val completion = TaskCompletionSource<ByteArray>()
        serviceScope.launch {
            try {
                val reply = commandMutex.withLock {
                    handleMessage(path, data).getOrThrow()
                    WearCommandResult(state = WearableStatePublisher.buildCurrentPlaybackState(applicationContext))
                }
                completion.setResult(reply.encode())
            } catch (e: CancellationException) {
                completion.trySetException(e)
                throw e
            } catch (e: Exception) {
                completion.trySetResult(WearCommandResult(error = e.message ?: "Command failed").encode())
            }
        }
        return completion.task
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        val data = messageEvent.data

        Log.d(TAG, "onMessageReceived: path=$path from ${messageEvent.sourceNodeId}")

        serviceScope.launch {
            try {
                commandMutex.withLock { handleMessage(path, data) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wear command rejected: $path", e)
            }
        }
    }

    internal suspend fun handleMessage(path: String, data: ByteArray?): Result<Unit> = try {
        executeMessage(path, data)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Wear command rejected: $path", e)
        Result.failure(e)
    }

    private suspend fun executeMessage(path: String, data: ByteArray?) {
        val appContext = runCatching { applicationContext }.getOrNull()
        val isDesktop = WearableStatePublisher.activeTargetType == WearConstants.TARGET_TYPE_DESKTOP
        if (appContext != null) {
            ConnectManager.renewWearControlSession()
            WearableStatePublisher.ensurePhoneCapability(appContext)
            WearableStatePublisher.startDesktopObserver(appContext)
            // Desktop discovery and control must work without starting the phone's audio engine.
            if (!isDesktop && path in PHONE_PLAYER_ACTIONS) ensurePlayerInitialized(appContext)
        }

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
                        throw e
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
                        throw e
                    }
                }
            }

            WearConstants.ACTION_PLAY_PAUSE -> {
                if (isDesktop) {
                    val action = if (isDesktopCurrentlyPlaying()) "pause" else "play"
                    forwardDesktopControl(action)
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
                        throw e
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
                        throw e
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
                        throw e
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
                            throw e
                        throw e
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
                        throw e
                }
            }

            WearConstants.ACTION_TARGET_SELECT -> {
                val payload = requireNotNull(WearCodecs.decodeTarget(data)) { "Invalid target" }
                val target = WearableStatePublisher.buildAvailableTargets().firstOrNull {
                    it.type == payload.targetType &&
                        (it.isPhone || it.id == payload.peerId)
                }
                require(target != null && target.isConnected) { "Device is offline" }
                WearableStatePublisher.setActiveTarget(
                    target.type, target.id, target.name, appContext
                )
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

    private fun isDesktopCurrentlyPlaying(): Boolean =
        ConnectManager.peers.value[requireActiveDesktopPeerId()]?.snapshot?.isPlaying == true

    private fun requireActiveDesktopPeerId(): String {
        val id = WearableStatePublisher.activeTargetId ?: ConnectManager.selectedDevice.value
        check(id != null && ConnectManager.peers.value[id]?.connected == true) { "Device is offline" }
        return id
    }

    private suspend fun forwardDesktopControl(action: String) {
        ConnectManager.commandAndAwait(requireActiveDesktopPeerId(), action)
    }

    private suspend fun forwardDesktopVolumeDelta(delta: Float) {
        val peer = ConnectManager.peers.value[requireActiveDesktopPeerId()]
        val current = WearableStatePublisher.normalizeDesktopVolume(peer?.snapshot?.volume)
        forwardDesktopSetVolume((current + delta).coerceIn(0f, 1f))
    }

    private suspend fun forwardDesktopSetVolume(volumeRatio: Float) {
        val safe = if (volumeRatio.isNaN()) 0f else volumeRatio.coerceIn(0f, 1f)
        ConnectManager.commandAndAwait(requireActiveDesktopPeerId(), "volume", (safe * 1000).roundToInt().toLong())
    }
}
