package com.alananasss.kittytune.wear

import android.content.Context
import android.os.Build
import android.media.AudioManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.media3.common.C
import androidx.media3.common.Player
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.sync.ConnectManager
import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.common.WearConstants
import com.alananasss.kittytune.wear.common.WearDataMapMapper
import com.alananasss.kittytune.wear.common.WearPlaybackState
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

/**
 * Publishes authoritative playback and volume state from the phone
 * to all connected Wear OS watch companions via Wearable DataClient.
 */
object WearableStatePublisher {

    private const val TAG = "WearableStatePublisher"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null

    /** Monotonic counter used to discard superseded publications. */
    @Volatile
    private var publishGeneration: Long = 0L

    @Volatile
    var activeTargetType: String = WearConstants.TARGET_TYPE_PHONE

    @Volatile
    var activeTargetId: String? = null

    @Volatile
    var activeTargetName: String = WearConstants.TARGET_PHONE_LABEL

    private var desktopObserverJob: Job? = null

    /**
     * Sets the active playback target and optionally publishes state immediately.
     */
    fun setActiveTarget(
        targetType: String,
        targetId: String? = null,
        targetName: String? = null,
        context: Context? = null
    ) {
        activeTargetType = targetType
        activeTargetId = targetId
        if (!targetName.isNullOrBlank()) {
            activeTargetName = targetName
        } else if (targetType == WearConstants.TARGET_TYPE_PHONE) {
            activeTargetName = WearConstants.TARGET_PHONE_LABEL
        } else {
            activeTargetName = "Desktop"
        }
        context?.let { publishCurrentState(it, immediate = true) }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun resetTargetForTesting() {
        activeTargetType = WearConstants.TARGET_TYPE_PHONE
        activeTargetId = null
        activeTargetName = WearConstants.TARGET_PHONE_LABEL
    }

    /**
     * Starts observing ConnectManager peer updates to sync desktop state to wear.
     *
     * Every peer change republishes, not only while a desktop is the active target: the watch needs
     * the target list itself to change when a computer comes online or drops, otherwise its picker
     * keeps offering a device that is gone.
     */
    fun startDesktopObserver(context: Context) {
        if (desktopObserverJob?.isActive == true) return
        val appContext = context.applicationContext
        desktopObserverJob = scope.launch {
            ConnectManager.peers.collect {
                publishCurrentState(appContext, immediate = false)
            }
        }
    }

    fun stopDesktopObserver() {
        desktopObserverJob?.cancel()
        desktopObserverJob = null
    }

    /**
     * Normalizes desktop snapshot volume into 0.0f..1.0f range.
     * Supports standard Float (0.0f..1.0f), integer 0..1000 scale, and IEEE 754 specials.
     */
    internal fun normalizeDesktopVolume(rawVolume: Float?): Float {
        if (rawVolume == null || rawVolume.isNaN()) return 0.5f
        if (rawVolume.isInfinite()) return if (rawVolume > 0f) 1.0f else 0.0f
        return when {
            rawVolume > 1.0f -> (rawVolume / 1000f).coerceIn(0.0f, 1.0f)
            rawVolume < 0.0f -> 0.0f
            else -> rawVolume.coerceIn(0.0f, 1.0f)
        }
    }

    /**
     * Queries current player, audio manager, and target state and publishes
     * an updated [WearPlaybackState] to the Wearable DataClient.
     *
     * Publications are serialised by generation: only the newest request is allowed to write, so a
     * slow immediate publish can never overwrite the newer frame that replaced it.
     *
     * @param immediate If true, bypasses debouncing and publishes right away.
     */
    fun publishCurrentState(context: Context, immediate: Boolean = true) {
        val generation = ++publishGeneration
        debounceJob?.cancel()
        debounceJob = scope.launch {
            if (!immediate) delay(50L)
            // A newer request arrived while this one was waiting: drop this write entirely.
            if (generation != publishGeneration) return@launch
            doPublish(context.applicationContext)
        }
    }

    /**
     * Publishes an explicit idle/stopped state to clear active playback from watch.
     */
    fun publishStoppedState(context: Context) {
        // Stopping phone playback must not clear the selected desktop's state.
        publishCurrentState(context)
    }

    internal fun buildAvailableTargets(): List<TargetDeviceInfo> {
        val availableTargets = mutableListOf<TargetDeviceInfo>()

        // 1. Phone target
        availableTargets.add(
            TargetDeviceInfo(
                id = WearConstants.TARGET_PHONE_ID,
                name = WearConstants.TARGET_PHONE_LABEL,
                type = WearConstants.TARGET_TYPE_PHONE,
                isConnected = true,
                isSelected = activeTargetType == WearConstants.TARGET_TYPE_PHONE
            )
        )

        // 2. Connected Desktop peers from ConnectManager
        val connectedPeers = ConnectManager.peers.value.values.filter { it.connected }
        for (peer in connectedPeers) {
            val isSelected = activeTargetType == WearConstants.TARGET_TYPE_DESKTOP &&
                    (activeTargetId == peer.deviceId || (activeTargetId == null && ConnectManager.selectedDevice.value == peer.deviceId))
            availableTargets.add(
                TargetDeviceInfo(
                    id = peer.deviceId,
                    name = peer.name.ifBlank { "Desktop" },
                    type = WearConstants.TARGET_TYPE_DESKTOP,
                    isConnected = true,
                    isSelected = isSelected
                )
            )
        }

        return availableTargets
    }

    internal fun buildDesktopPlaybackState(
        appContext: Context?,
        availableTargets: List<TargetDeviceInfo>
    ): WearPlaybackState {
        val targetId = activeTargetId
            ?: ConnectManager.selectedDevice.value


        val peerState = targetId?.let { ConnectManager.peers.value[it] }
        val snapshot = peerState?.snapshot
        val currentTrack = snapshot?.queue?.getOrNull(snapshot.currentIndex)

        val trackId = currentTrack?.id ?: 0L
        val title = currentTrack?.title.orEmpty()
        val artist = currentTrack?.displayArtist?.ifBlank { currentTrack.user?.username.orEmpty() }
            ?: currentTrack?.user?.username.orEmpty()
        val album = currentTrack?.publisherMetadata?.albumTitle.orEmpty()
        val artworkUrl = currentTrack?.fullResArtwork ?: currentTrack?.artworkUrl
        val isPlaying = peerState?.connected == true && snapshot?.isPlaying == true
        val isLoading = false
        val positionMs = (peerState?.position() ?: (snapshot?.positionMs ?: 0L)).coerceAtLeast(0L)
        val rawDuration = currentTrack?.actualDurationMs?.takeIf { it > 0L }
            ?: currentTrack?.durationMs?.takeIf { it > 0L }
            ?: 0L
        val durationMs = rawDuration.coerceAtLeast(0L)

        val volumeRatio = normalizeDesktopVolume(snapshot?.volume)

        val targetDeviceLabel = peerState?.name?.ifBlank { activeTargetName.ifBlank { "Desktop" } }
            ?: activeTargetName.ifBlank { "Desktop" }

        val isLiked = snapshot?.isLiked ?: (if (trackId != 0L && LikeRepository.isReady) LikeRepository.isTrackLiked(trackId) else false)

        val shuffleEnabled = snapshot?.shuffleEnabled ?: false
        val repeatMode = when (snapshot?.repeatMode?.lowercase()) {
            "one" -> WearConstants.REPEAT_MODE_ONE
            "all" -> WearConstants.REPEAT_MODE_ALL
            else -> WearConstants.REPEAT_MODE_NONE
        }

        return WearPlaybackState(
            trackId = trackId,
            title = title,
            artist = artist,
            album = album,
            artworkUrl = artworkUrl,
            isPlaying = isPlaying,
            isLoading = isLoading,
            positionMs = positionMs,
            durationMs = durationMs,
            volume = volumeRatio,
            targetDevice = targetDeviceLabel,
            targetType = WearConstants.TARGET_TYPE_DESKTOP,
            targetDeviceId = targetId,
            isLiked = isLiked,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            availableTargets = availableTargets,
            timestamp = System.currentTimeMillis()
        )
    }

    internal fun buildPhonePlaybackState(
        appContext: Context?,
        availableTargets: List<TargetDeviceInfo>
    ): WearPlaybackState {
        val track = MusicManager.currentTrack
        val player = try { MusicManager.player } catch (_: Exception) { null }
        val isPlaying = player?.isPlaying == true
        val isLoading = MusicManager.isLoadingTrack
        val positionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val rawDuration = player?.duration?.takeIf { it > 0L && it != C.TIME_UNSET } ?: track?.actualDurationMs ?: 0L
        val durationMs = rawDuration.coerceAtLeast(0L)

        val audioManager = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val minVol = if (Build.VERSION.SDK_INT >= 28) audioManager?.getStreamMinVolume(AudioManager.STREAM_MUSIC) ?: 0 else 0
        val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val volumeRatio = ((currentVol - minVol).toFloat() / (maxVol - minVol).coerceAtLeast(1)).coerceIn(0f, 1f)

        val isLiked = if (track != null && LikeRepository.isReady) {
            LikeRepository.isTrackLiked(track.id)
        } else false

        val prefs = appContext?.let { runCatching { PlayerPreferences(it) }.getOrNull() }
        val shuffleEnabled = prefs?.getLastShuffleEnabled() ?: false
        val repeatMode = when (player?.repeatMode) {
            Player.REPEAT_MODE_ONE -> WearConstants.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ALL -> WearConstants.REPEAT_MODE_ALL
            else -> WearConstants.REPEAT_MODE_NONE
        }

        return WearPlaybackState(
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
            targetDevice = WearConstants.TARGET_PHONE_LABEL,
            targetType = WearConstants.TARGET_TYPE_PHONE,
            targetDeviceId = WearConstants.TARGET_PHONE_ID,
            isLiked = isLiked,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            availableTargets = availableTargets,
            timestamp = System.currentTimeMillis()
        )
    }

    internal fun buildCurrentPlaybackState(appContext: Context?): WearPlaybackState {
        val availableTargets = buildAvailableTargets()
        return if (activeTargetType == WearConstants.TARGET_TYPE_DESKTOP) {
            buildDesktopPlaybackState(appContext, availableTargets)
        } else {
            buildPhonePlaybackState(appContext, availableTargets)
        }
    }

    /**
     * Advertises the handheld capability so the watch can recognise this phone among its nodes.
     * Idempotent, and safe to call from every PlaybackService start.
     */
    fun ensurePhoneCapability(context: Context) {
        runCatching {
            Wearable.getCapabilityClient(context.applicationContext)
                .addLocalCapability(WearConstants.CAPABILITY_PHONE_APP)
        }.onFailure { Log.w(TAG, "Failed to advertise phone capability", it) }
    }

    private suspend fun doPublish(appContext: Context) {
        try {
            val state = withContext(Dispatchers.Main.immediate) { buildCurrentPlaybackState(appContext) }
            val putDataRequest = WearDataMapMapper.createPutDataRequest(state)
            Wearable.getDataClient(appContext).putDataItem(putDataRequest)
                .addOnFailureListener { Log.w(TAG, "Data Layer rejected playback state", it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to publish wearable state", e)
        }
    }
}
