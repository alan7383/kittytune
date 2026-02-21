    package com.alananasss.kittytune.data
    
    import android.content.Context
    import android.net.Uri
    import android.util.Log
    import androidx.media3.common.AudioAttributes
    import androidx.media3.common.C
    import androidx.media3.common.MediaItem
    import androidx.media3.common.Player
    import androidx.media3.common.PlaybackParameters
    import androidx.media3.datasource.DataSpec
    import androidx.media3.datasource.DefaultDataSource
    import androidx.media3.datasource.ResolvingDataSource
    import androidx.media3.exoplayer.DefaultRenderersFactory
    import androidx.media3.exoplayer.ExoPlayer
    import androidx.media3.exoplayer.audio.AudioSink
    import androidx.media3.exoplayer.audio.DefaultAudioSink
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
    import kotlinx.coroutines.CoroutineScope
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.asStateFlow
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.runBlocking
    import com.alananasss.kittytune.data.local.PlayerPreferences
    import android.os.HandlerThread
    import java.io.IOException
    
    object MusicManager {
        private var _player: ExoPlayer? = null
    
        val player: ExoPlayer
            get() {
                if (_player == null) {
                    throw IllegalStateException("MusicManager not initialized! Call init() first.")
                }
                return _player!!
            }
    
        var currentTrack: Track? = null
    
        // --- SHARED CONTEXT ---
        private val _contextFlow = MutableStateFlow<PlaybackContext?>(null)
        val contextFlow = _contextFlow.asStateFlow()
    
        fun updateContext(context: PlaybackContext?) {
            _contextFlow.value = context
        }
    
        var onTrackChange: ((Track) -> Unit)? = null
    
        private var preloadedTrack: Track? = null
        private val eightDProcessor = EightDAudioProcessor()
        private val fxProcessor = FxAudioProcessor()
        private val reverbProcessor = ReverbAudioProcessor()
        var onNextClick: (() -> Unit)? = null
        var onPreviousClick: (() -> Unit)? = null
        private var rainPlayer: RainPlayer? = null
        private val scope = CoroutineScope(Dispatchers.Main)
    
        fun init(context: Context) {
            if (_player != null) return
    
            rainPlayer = RainPlayer(context.applicationContext)
    
            val prefs = PlayerPreferences(context)
            val lastContext = prefs.getLastContext()
            _contextFlow.value = lastContext
    
            val dataSourceFactory = DefaultDataSource.Factory(context)
    
            val resolvingDataSourceFactory = ResolvingDataSource.Factory(dataSourceFactory, object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    val uri = dataSpec.uri
    
                    if (uri.scheme == "soundtune" && uri.host == "track") {
                        val trackId = uri.lastPathSegment?.toLongOrNull()
    
                        if (trackId != null) {
                            var streamUrl: String? = null
    
                            try {
                                runBlocking(Dispatchers.IO) {
                                    if (trackId < 0) {
                                        try {
                                            val db = AppDatabase.getDatabase(context).downloadDao()
                                            val localTrack = db.getTrack(trackId)
                                            if (localTrack != null && localTrack.localAudioPath.isNotEmpty()) {
                                                streamUrl = localTrack.localAudioPath
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                    else {
                                        try {
                                            var trackToResolve = if (currentTrack?.id == trackId) currentTrack
                                            else if (preloadedTrack?.id == trackId) preloadedTrack
                                            else null
    
                                            val hasMediaInfo = trackToResolve?.media?.transcodings?.isNotEmpty() == true
    
                                            if ((trackToResolve == null || !hasMediaInfo)) {
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
                                                streamUrl = StreamResolver.resolveStream(context, trackToResolve)
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
                                return dataSpec.buildUpon().setUri(Uri.parse(finalUrl)).build()
                            }
                        }
                    }
                    return dataSpec
                }
            })
    
            _player = ExoPlayer.Builder(context.applicationContext)
                .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(resolvingDataSourceFactory))
                .setRenderersFactory(
                    object : DefaultRenderersFactory(context) {
                        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
                            return DefaultAudioSink.Builder(context)
                                .setAudioProcessors(arrayOf(fxProcessor, reverbProcessor, eightDProcessor))
                                .setEnableFloatOutput(enableFloatOutput)
                                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                                .build()
                        }
                    }
                )
                .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()
    
            _player?.addListener(object : Player.Listener {
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
            })
        }
    
        fun applyEffects(state: AudioEffectsState) {
            if (_player == null) return
            val pitch = if (state.isPitchEnabled) state.speed else 1f
            _player?.playbackParameters = PlaybackParameters(state.speed, pitch)
            eightDProcessor.setEnabled(state.is8DEnabled)
            fxProcessor.setEffects(state.isMuffledEnabled, state.isBassBoostEnabled)
            reverbProcessor.setEnabled(state.isReverbEnabled)
            rainPlayer?.setEnabled(state.isRainEnabled)
            rainPlayer?.setVolume(state.rainVolume)
        }
    
        fun releasePlayer() {
            _player?.release()
            _player = null
            rainPlayer?.release()
            rainPlayer = null
        }
    }


