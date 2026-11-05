package com.alananasss.kittytune.wear

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.wear.common.WearCodecs
import com.alananasss.kittytune.wear.common.WearConstants
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Background service receiving discrete playback control messages from Wear OS smartwatches
 * and routing them to [MusicManager] (for phone playback) or [Together] (for desktop playback).
 */
class WearableMessageReceiverService : WearableListenerService() {

    companion object {
        private const val TAG = "WearableMessageReceiver"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        val data = messageEvent.data

        Log.d(TAG, "onMessageReceived: path=$path from ${messageEvent.sourceNodeId}")

        serviceScope.launch {
            val isDesktop = WearableStatePublisher.activeTargetType == WearConstants.TARGET_TYPE_DESKTOP
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

            when (path) {
                WearConstants.ACTION_PLAY -> {
                    if (isDesktop) {
                        forwardDesktopControl("play")
                    } else {
                        try {
                            MusicManager.playWhenReadyRequested = true
                            MusicManager.player.play()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to play audio", e)
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
                            Log.e(TAG, "Failed to pause audio", e)
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
                        MusicManager.onNextClick?.invoke()
                    }
                }

                WearConstants.ACTION_PREVIOUS -> {
                    if (isDesktop) {
                        forwardDesktopControl("previous")
                    } else {
                        MusicManager.onPreviousClick?.invoke()
                    }
                }

                WearConstants.ACTION_VOLUME_UP -> {
                    audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                }

                WearConstants.ACTION_VOLUME_DOWN -> {
                    audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                }

                WearConstants.ACTION_SET_VOLUME -> {
                    val volumeRatio = WearCodecs.decodeVolume(data)
                    audioManager?.let { am ->
                        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val targetIndex = (volumeRatio * max).roundToInt().coerceIn(0, max)
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
                    }
                }

                WearConstants.ACTION_LIKE_TOGGLE -> {
                    // Like action handled on phone UI when available
                }

                WearConstants.ACTION_SHUFFLE_TOGGLE -> {
                    MusicManager.onShuffleClick?.invoke()
                }

                WearConstants.ACTION_REPEAT_CYCLE -> {
                    // Repeat mode handled on phone UI when available
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
                    // Just triggers publishCurrentState below
                }
            }

            WearableStatePublisher.publishCurrentState(applicationContext)
        }
    }

    private fun forwardDesktopControl(action: String) {
        val position = try { MusicManager.player.currentPosition } catch (_: Exception) { 0L }
        Together.control(action, position)
    }
}
