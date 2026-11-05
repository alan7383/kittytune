package com.alananasss.kittytune.wear

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.common.WearConstants
import com.alananasss.kittytune.wear.common.WearDataMapMapper
import com.alananasss.kittytune.wear.common.WearPlaybackState
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Publishes authoritative playback and volume state from the phone
 * to all connected Wear OS watch companions via Wearable DataClient.
 */
object WearableStatePublisher {

    private const val TAG = "WearableStatePublisher"
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    var activeTargetType: String = WearConstants.TARGET_TYPE_PHONE

    @Volatile
    var activeTargetId: String? = null

    @Volatile
    var activeTargetName: String = WearConstants.TARGET_PHONE_LABEL

    /**
     * Queries current player, audio manager, and target state and publishes
     * an updated [WearPlaybackState] to the Wearable DataClient.
     */
    fun publishCurrentState(context: Context) {
        scope.launch {
            try {
                val appContext = context.applicationContext
                val track = MusicManager.currentTrack
                val player = try { MusicManager.player } catch (_: Exception) { null }
                val isPlaying = player?.isPlaying == true
                val isLoading = MusicManager.isLoadingTrack
                val positionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
                val durationMs = (player?.duration?.takeIf { it > 0 } ?: track?.actualDurationMs ?: 0L).coerceAtLeast(0L)

                val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                val maxVol = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
                val volumeRatio = (currentVol.toFloat() / maxVol.toFloat()).coerceIn(0.0f, 1.0f)

                val availableTargets = mutableListOf(
                    TargetDeviceInfo(
                        id = WearConstants.TARGET_PHONE_ID,
                        name = WearConstants.TARGET_PHONE_LABEL,
                        type = WearConstants.TARGET_TYPE_PHONE,
                        isConnected = true,
                        isSelected = activeTargetType == WearConstants.TARGET_TYPE_PHONE
                    )
                )

                // If Together shared playlist/room is active, expose Desktop target
                val activeTogetherRoom = Together.activeRoom
                if (activeTogetherRoom != null) {
                    val desktopName = if (activeTogetherRoom.name.isNotBlank()) activeTogetherRoom.name else "Desktop Together"
                    availableTargets.add(
                        TargetDeviceInfo(
                            id = activeTogetherRoom.code,
                            name = desktopName,
                            type = WearConstants.TARGET_TYPE_DESKTOP,
                            isConnected = Together.isConnected.value,
                            isSelected = activeTargetType == WearConstants.TARGET_TYPE_DESKTOP
                        )
                    )
                }

                val targetDeviceLabel = if (activeTargetType == WearConstants.TARGET_TYPE_DESKTOP) {
                    activeTargetName.ifBlank { "Desktop" }
                } else {
                    WearConstants.TARGET_PHONE_LABEL
                }

                val state = WearPlaybackState(
                    trackId = track?.id ?: 0L,
                    title = track?.title ?: "",
                    artist = track?.displayArtist?.ifBlank { track.user?.username ?: "" } ?: "",
                    album = track?.publisherMetadata?.albumTitle ?: "",
                    artworkUrl = track?.fullResArtwork ?: track?.artworkUrl,
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    volume = volumeRatio,
                    targetDevice = targetDeviceLabel,
                    targetType = activeTargetType,
                    targetDeviceId = activeTargetId,
                    isLiked = false,
                    shuffleEnabled = false,
                    repeatMode = WearConstants.REPEAT_MODE_NONE,
                    availableTargets = availableTargets,
                    timestamp = System.currentTimeMillis()
                )

                val putDataRequest = WearDataMapMapper.createPutDataRequest(state)
                Wearable.getDataClient(appContext).putDataItem(putDataRequest)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to publish wearable state", e)
            }
        }
    }
}
