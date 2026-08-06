package com.alananasss.kittytune.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.audio.EightDAudioProcessor
import com.alananasss.kittytune.ui.player.audio.FxAudioProcessor
import com.alananasss.kittytune.ui.player.audio.ReverbAudioProcessor
import com.alananasss.kittytune.ui.player.audio.EarrapeAudioProcessor
import com.alananasss.kittytune.ui.player.audio.MonoAudioProcessor
import com.alananasss.kittytune.ui.player.audio.R128AudioProcessor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import com.alananasss.kittytune.data.local.PlayerPreferences
import android.os.HandlerThread
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object MusicManager {
    private var _player1: ExoPlayer? = null
    private var _player2: ExoPlayer? = null
    private var activePlayerIndex = 1
    
    var lastPlayer: ExoPlayer? = null
    val onPlayerSwappedFlow = MutableStateFlow(0)
    
    @Volatile var isCrossfadingOut = false
    private var fadingPlayer: ExoPlayer? = null

    val player: ExoPlayer
        get() = if (activePlayerIndex == 1) _player1!! else _player2!!

    var currentTrack: Track? = null

    // --- DRM token cache ---
    // Stores the licenseAuthToken per trackId for DRM-protected streams
    private val drmTokenCache = ConcurrentHashMap<Long, String>()

    /** Store a DRM license token for a track */
    fun putDrmToken(trackId: Long, token: String) {
        drmTokenCache[trackId] = token
    }

    /** Retrieve the DRM license token for a track (if any) */
    fun getDrmToken(trackId: Long): String? {
        return drmTokenCache[trackId]
    }

    // --- SHARED CONTEXT ---
    private val _contextFlow = MutableStateFlow<PlaybackContext?>(null)
    val contextFlow = _contextFlow.asStateFlow()

    fun updateContext(context: PlaybackContext?) {
        _contextFlow.value = context
    }

    var onTrackChange: ((Track) -> Unit)? = null

    private var preloadedTrack: Track? = null
    
    private val eightDProcessors = listOf(EightDAudioProcessor(), EightDAudioProcessor())
    private val fxProcessors = listOf(FxAudioProcessor(), FxAudioProcessor())
    private val reverbProcessors = listOf(ReverbAudioProcessor(), ReverbAudioProcessor())
    private val earrapeProcessors = listOf(EarrapeAudioProcessor(), EarrapeAudioProcessor())
    private val monoProcessors = listOf(MonoAudioProcessor(), MonoAudioProcessor())
    private val normalizerProcessors = listOf(R128AudioProcessor(), R128AudioProcessor())

    var onNextClick: (() -> Unit)? = null
    var onPreviousClick: (() -> Unit)? = null
    private var rainPlayer: RainPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun init(context: Context) {
        if (_player1 != null) return

        rainPlayer = RainPlayer(context.applicationContext)

        val prefs = PlayerPreferences(context)
        val lastContext = prefs.getLastContext()
        _contextFlow.value = lastContext

        val tokenManager = com.alananasss.kittytune.data.TokenManager(context.applicationContext)
        val token = com.alananasss.kittytune.data.SessionManager.harvestStoredSession(context.applicationContext) ?: tokenManager.getAccessToken()
        val customUserAgent = "SoundCloud/2025.12.10-release (Android 10; Android)"
        
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(customUserAgent)
            .setAllowCrossProtocolRedirects(true)
        
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val resolvingDataSourceFactory = ResolvingDataSource.Factory(dataSourceFactory, object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                val uri = dataSpec.uri

                if (uri.scheme == "soundtune" && uri.host == "track") {
                    val trackId = uri.lastPathSegment?.toLongOrNull()

                    if (trackId != null) {
                        var streamUrl: String? = null

                        try {
                            runBlocking(Dispatchers.IO) {
                                try {
                                    val db = AppDatabase.getDatabase(context).downloadDao()
                                    val localTrack = db.getTrack(trackId)
                                    if (localTrack != null && localTrack.localAudioPath.isNotEmpty()) {
                                        if (localTrack.localAudioPath.startsWith("exo_cache://")) {
                                            // Format: exo_cache://trackId::streamUrl::licenseAuthToken
                                            val parts = localTrack.localAudioPath.removePrefix("exo_cache://").split("::", limit = 3)
                                            val parsedTrackId = parts[0].toLongOrNull()
                                            val cachedStreamUrl = parts.getOrNull(1)
                                            val token = parts.getOrNull(2)

                                            if (parsedTrackId != null && !cachedStreamUrl.isNullOrEmpty()) {
                                                streamUrl = cachedStreamUrl
                                            }
                                        } else {
                                            val isContentUri = localTrack.localAudioPath.startsWith("content://")
                                            val fileExists = if (isContentUri) true else java.io.File(localTrack.localAudioPath).exists()
                                            if (fileExists) {
                                                streamUrl = localTrack.localAudioPath
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                                if (streamUrl == null) {
                                    try {
                                        var trackToResolve = if (currentTrack?.id == trackId) currentTrack
                                        else if (preloadedTrack?.id == trackId) preloadedTrack
                                        else null

                                        val hasMediaInfo = trackToResolve?.media?.transcodings?.isNotEmpty() == true

                                        if ((trackToResolve == null || !hasMediaInfo) && trackId > 0) {
                                            val api = RetrofitClient.create(context)
                                            val tracks = api.getTracksByIds(trackId.toString())
                                            val fetchedTrack = tracks.firstOrNull()

                                            if (fetchedTrack != null) {
                                                trackToResolve = fetchedTrack
                                                if (currentTrack?.id == trackId) currentTrack = fetchedTrack
                                                preloadedTrack = fetchedTrack
                                            }
                                        }

                                        if (trackToResolve != null) {
                                            preloadedTrack = trackToResolve
                                            val resolved = StreamResolver.resolveStreamWithDrm(context, trackToResolve)
                                            streamUrl = resolved?.url

                                            // Store DRM token if present
                                            if (resolved?.isDrmProtected == true && resolved.licenseAuthToken != null) {
                                                putDrmToken(trackId, resolved.licenseAuthToken)
                                                Log.d("MusicManager", "DRM token cached for track $trackId")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        } catch (t: Throwable) {
                            Log.w("MusicManager", "Resolution cancelled or interrupted: ${t.message}")
                        }

                        val finalUrl = streamUrl
                        if (finalUrl != null) {
                            val uri = if (finalUrl.startsWith("http") || finalUrl.startsWith("content://")) {
                                Uri.parse(finalUrl)
                            } else {
                                Uri.fromFile(java.io.File(finalUrl))
                            }
                            return dataSpec.buildUpon().setUri(uri).build()
                        }
                    }
                }
                return dataSpec
            }
        })

        // DRM session manager provider that handles CENC-protected SoundCloud streams.
        // SoundCloud uses CBCS-encrypted HLS where PSSH data is embedded in the init segments,
        // not in the MediaItem/manifest. We use multiSession so ExoPlayer discovers PSSH
        // from the segments instead of pre-acquiring sessions (which causes MissingSchemeDataException).
        val drmSessionManagerProvider = object : DrmSessionManagerProvider {
            override fun get(mediaItem: MediaItem): DrmSessionManager {
                var finalKeySetId: ByteArray? = null
                var finalToken: String? = null
                val trackId = mediaItem.mediaId.toLongOrNull()

                if (trackId != null) {
                    finalToken = getDrmToken(trackId)

                    // Check if track is downloaded (offline)
                    try {
                        runBlocking(Dispatchers.IO) {
                            val db = AppDatabase.getDatabase(context).downloadDao()
                            val localTrack = db.getTrack(trackId)
                            if (localTrack != null && localTrack.localAudioPath.startsWith("exo_cache://")) {
                                val parts = localTrack.localAudioPath.removePrefix("exo_cache://").split("::", limit = 3)
                                val tokenStr = parts.getOrNull(2)
                                if (!tokenStr.isNullOrEmpty()) {
                                    finalKeySetId = android.util.Base64.decode(tokenStr, android.util.Base64.NO_WRAP)
                                }
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }

                if (finalKeySetId != null) {
                    Log.d("MusicManager", "Using offline Widevine license for track $trackId")
                    val manager = DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(
                            androidx.media3.common.C.WIDEVINE_UUID,
                            FrameworkMediaDrm.DEFAULT_PROVIDER
                        )
                        .build(androidx.media3.exoplayer.drm.LocalMediaDrmCallback(ByteArray(0)))
                    manager.setMode(DefaultDrmSessionManager.MODE_PLAYBACK, finalKeySetId)
                    return manager
                } else if (finalToken != null) {
                    Log.d("MusicManager", "Creating streaming Widevine DRM session for track $trackId")
                    val callback = SoundCloudDrmCallback(finalToken)
                    return DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(
                            androidx.media3.common.C.WIDEVINE_UUID,
                            FrameworkMediaDrm.DEFAULT_PROVIDER
                        )
                        .setMultiSession(true)
                        .build(callback)
                }

                // Fallback to default which handles offline keySetIds automatically!
                return androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider().get(mediaItem)
            }
        }

        val cache = com.alananasss.kittytune.data.local.ExoCacheManager.getCache(context)
        val cacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(resolvingDataSourceFactory)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val createExoPlayer = { index: Int ->
            ExoPlayer.Builder(context.applicationContext)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(context)
                        .setDataSourceFactory(cacheDataSourceFactory)
                        .setDrmSessionManagerProvider(drmSessionManagerProvider)
                )
                .setRenderersFactory(
                    object : DefaultRenderersFactory(context) {
                        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
                            return DefaultAudioSink.Builder(context)
                                .setAudioProcessors(arrayOf(fxProcessors[index], reverbProcessors[index], eightDProcessors[index], earrapeProcessors[index], monoProcessors[index], normalizerProcessors[index]))
                                .setEnableFloatOutput(enableFloatOutput)
                                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                                .build()
                        }
                    }
                )
                .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()
        }

        _player1 = createExoPlayer(0)
        _player2 = createExoPlayer(1)

        _player1?.setSeekParameters(SeekParameters.EXACT)
        _player2?.setSeekParameters(SeekParameters.EXACT)

        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                if (mediaItem == null) return

                val rawId = mediaItem.mediaId
                val cleanIdString = if (rawId.contains(":")) rawId.substringBefore(":") else rawId
                val trackId = cleanIdString.toLongOrNull() ?: rawId.hashCode().toLong()

                if (preloadedTrack != null && preloadedTrack?.id == trackId) {
                    currentTrack = preloadedTrack
                } else if (currentTrack?.id != trackId) {
                    val meta = mediaItem.mediaMetadata
                    val source = if (mediaItem.mediaId.startsWith("yt_") || mediaItem.requestMetadata.mediaUri?.toString()?.contains("youtube") == true) "youtube" else "soundcloud"

                    currentTrack = Track(
                        id = trackId,
                        title = meta.title?.toString() ?: "Unknown",
                        durationMs = 0L,
                        artworkUrl = meta.artworkUri?.toString(),
                        user = User(0, meta.artist?.toString() ?: "Unknown", null),
                        permalinkUrl = "",
                        playbackCount = 0,
                        likesCount = 0,
                        repostsCount = 0,
                        commentCount = 0,
                        source = source
                    )
                }

                currentTrack?.let { track ->
                    scope.launch {
                        onTrackChange?.invoke(track)
                    }
                }
            }
        }
        
        _player1?.addListener(listener)
        _player2?.addListener(listener)
    }

    fun crossfadeToMediaItem(mediaItem: MediaItem, startPositionMs: Long, crossfadeDurationMs: Long) {
        val oldPlayer = player
        activePlayerIndex = if (activePlayerIndex == 1) 2 else 1
        val newPlayer = player
        
        isCrossfadingOut = true
        fadingPlayer = oldPlayer
        
        lastPlayer = oldPlayer
        onPlayerSwappedFlow.value += 1
        
        newPlayer.setMediaItem(mediaItem, startPositionMs)
        newPlayer.prepare()
        
        val targetVolume = 1f
        newPlayer.volume = 0f
        
        if (oldPlayer.playWhenReady) newPlayer.play()
        
        scope.launch {
            // Wait for newPlayer to prepare and begin buffering/rendering audio before volume ramping
            var waitCount = 0
            while (newPlayer.playbackState == Player.STATE_BUFFERING && waitCount < 50) {
                delay(100)
                waitCount++
            }

            var remainingMs = oldPlayer.duration - oldPlayer.currentPosition
            if (remainingMs < 0) remainingMs = 0
            
            val actualCrossfadeMs = if (oldPlayer.isPlaying && remainingMs > 0 && remainingMs < crossfadeDurationMs) {
                remainingMs
            } else if (!oldPlayer.isPlaying || remainingMs == 0L) {
                0L
            } else {
                crossfadeDurationMs
            }
            
            if (actualCrossfadeMs <= 0L) {
                newPlayer.volume = targetVolume
                oldPlayer.stop()
                oldPlayer.clearMediaItems()
                fadingPlayer = null
                isCrossfadingOut = false
            } else {
                val steps = 40
                val delayMs = actualCrossfadeMs / steps
                for (i in 1..steps) {
                    if (fadingPlayer != oldPlayer) break
                    val ratio = i.toFloat() / steps
                    newPlayer.volume = targetVolume * ratio
                    oldPlayer.volume = targetVolume * (1f - ratio)
                    delay(delayMs)
                }
                if (fadingPlayer == oldPlayer) {
                    newPlayer.volume = targetVolume
                    oldPlayer.stop()
                    oldPlayer.clearMediaItems()
                    fadingPlayer = null
                    isCrossfadingOut = false
                }
            }
        }
    }


    fun applyEffects(state: AudioEffectsState) {
        if (_player1 == null) return
        val pitch = if (state.isPitchEnabled) state.speed else 1f
        
        _player1?.playbackParameters = PlaybackParameters(state.speed, pitch)
        _player2?.playbackParameters = PlaybackParameters(state.speed, pitch)
        
        eightDProcessors.forEach { it.setEnabled(state.is8DEnabled); it.setSpeed(state.eightDSpeed) }
        fxProcessors.forEach { 
            it.setEffects(state.isMuffledEnabled, state.isBassBoostEnabled)
            it.setBassBoostGain(state.bassBoostIntensity)
            it.setMuffledCutoff(state.muffledIntensity)
        }
        reverbProcessors.forEach { it.setEnabled(state.isReverbEnabled); it.setDecay(state.reverbIntensity) }
        earrapeProcessors.forEach { it.setEnabled(state.isEarrapeEnabled) }
        monoProcessors.forEach { it.setEnabled(state.isMonoEnabled) }
        normalizerProcessors.forEach { it.setParameters(state.isNormalizationEnabled, state.normalizationLevel) }

        rainPlayer?.setEnabled(state.isRainEnabled)
        rainPlayer?.setVolume(state.rainVolume)
    }

    fun releasePlayer() {
        _player1?.release(); _player1 = null
        _player2?.release(); _player2 = null
        fadingPlayer?.release(); fadingPlayer = null
        rainPlayer?.release()
        rainPlayer = null
        drmTokenCache.clear()
    }
}
