package com.alananasss.kittytune.wear

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.common.WearConstants
import com.alananasss.kittytune.wear.common.WearDataMapMapper
import com.alananasss.kittytune.wear.common.WearPlaybackState
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Publishes authoritative playback and volume state from the phone
 * to all connected Wear OS watch companions via Wearable DataClient.
 */
object WearableStatePublisher {

    private const val TAG = "WearableStatePublisher"
    // Media3's player is created on the main looper. DataClient.putDataItem is asynchronous.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pendingPublish: Job? = null

    @Volatile
    var activeTargetType: String = WearConstants.TARGET_TYPE_PHONE

    @Volatile
    var activeTargetId: String? = null

    @Volatile
    var activeTargetName: String = WearConstants.TARGET_PHONE_LABEL

    /**
     * Queries current player, audio manager, and target state and publishes
     * an updated [WearPlaybackState] to the Wearable DataClient.
     *
     * @param immediate If true, bypasses debouncing and publishes right away.
     */
    fun publishCurrentState(context: Context, immediate: Boolean = true) {
        scope.launch {
            pendingPublish?.cancel()
            pendingPublish = launch {
                if (!immediate) delay(50L)
                doPublish(context.applicationContext)
            }
        }
    }

    /**
     * Publishes an explicit idle/stopped state to clear active playback from watch.
     */
    fun publishStoppedState(context: Context) {
        scope.launch {
            pendingPublish?.cancel()
            try {
                val appContext = context.applicationContext

                val stoppedState = WearPlaybackState.EMPTY.copy(
                    volume = musicVolume(appContext),
                    targetDevice = WearConstants.TARGET_PHONE_LABEL,
                    targetType = WearConstants.TARGET_TYPE_PHONE,
                    timestamp = System.currentTimeMillis()
                )
                val putDataRequest = WearDataMapMapper.createPutDataRequest(stoppedState)
                Wearable.getDataClient(appContext).putDataItem(putDataRequest)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to publish stopped state", e)
            }
        }
    }

    private fun musicVolume(context: Context): Float {
        val audio = context.getSystemService(AudioManager::class.java) ?: return 0f
        val stream = AudioManager.STREAM_MUSIC
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audio.getStreamMinVolume(stream) else 0
        val range = audio.getStreamMaxVolume(stream) - min
        if (range <= 0) return 0f
        return ((audio.getStreamVolume(stream) - min).toFloat() / range).coerceIn(0f, 1f)
    }

    private fun doPublish(appContext: Context) {
        try {
            val track = MusicManager.currentTrack
            val player = try { MusicManager.player } catch (_: Exception) { null }
            val isPlaying = player?.isPlaying == true
            val isLoading = MusicManager.isLoadingTrack
            val positionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
            val rawDuration = player?.duration?.takeIf { it > 0L && it != C.TIME_UNSET } ?: track?.actualDurationMs ?: 0L
            val durationMs = rawDuration.coerceAtLeast(0L)

            // Authoritative like status
            val isLiked = if (track != null && LikeRepository.isReady) {
                LikeRepository.isTrackLiked(track.id)
            } else false

            // Authoritative shuffle and repeat mode
            val prefs = PlayerPreferences(appContext)
            val shuffleEnabled = prefs.getLastShuffleEnabled()
            val repeatMode = when (player?.repeatMode) {
                Player.REPEAT_MODE_ONE -> WearConstants.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ALL -> WearConstants.REPEAT_MODE_ALL
                else -> WearConstants.REPEAT_MODE_NONE
            }

            val availableTargets = mutableListOf(
                TargetDeviceInfo(
                    id = WearConstants.TARGET_PHONE_ID,
                    name = WearConstants.TARGET_PHONE_LABEL,
                    type = WearConstants.TARGET_TYPE_PHONE,
                    isConnected = true,
                    isSelected = activeTargetType == WearConstants.TARGET_TYPE_PHONE
                )
            )

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
                volume = musicVolume(appContext),
                targetDevice = targetDeviceLabel,
                targetType = activeTargetType,
                targetDeviceId = activeTargetId,
                isLiked = isLiked,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
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
