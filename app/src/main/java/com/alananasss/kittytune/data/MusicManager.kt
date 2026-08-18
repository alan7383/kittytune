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
import com.alananasss.kittytune.ui.player.audio.VintageMp3AudioProcessor
import com.alananasss.kittytune.ui.player.audio.VocalRemoverAudioProcessor
import com.alananasss.kittytune.ui.player.audio.VocalBoostAudioProcessor
import com.alananasss.kittytune.ui.player.audio.FlangerAudioProcessor
import com.alananasss.kittytune.ui.player.audio.PartyNextDoorAudioProcessor
import com.alananasss.kittytune.ui.player.audio.SuperWideAudioProcessor
import com.alananasss.kittytune.ui.player.audio.VinylLoFiAudioProcessor
import com.alananasss.kittytune.ui.player.audio.PhaserAudioProcessor
import com.alananasss.kittytune.ui.player.audio.MegaphoneRadioAudioProcessor
import com.alananasss.kittytune.ui.player.audio.RobotVocoderAudioProcessor
import com.alananasss.kittytune.ui.player.audio.ChorusAudioProcessor
import com.alananasss.kittytune.ui.player.audio.UnderwaterAudioProcessor
import com.alananasss.kittytune.ui.player.audio.TranceGateAudioProcessor
import com.alananasss.kittytune.ui.player.audio.PingPongDelayAudioProcessor
import com.alananasss.kittytune.ui.player.audio.ChiptuneAudioProcessor
import com.alananasss.kittytune.ui.player.audio.ShimmerReverbAudioProcessor
import com.alananasss.kittytune.ui.player.audio.RotarySpeakerAudioProcessor
import com.alananasss.kittytune.ui.player.audio.TapeSaturationAudioProcessor
import com.alananasss.kittytune.ui.player.audio.SubOctaverAudioProcessor
import com.alananasss.kittytune.ui.player.audio.EmptyMallAudioProcessor
import com.alananasss.kittytune.ui.player.audio.GramophoneAudioProcessor
import com.alananasss.kittytune.ui.player.audio.ReverseEchoAudioProcessor
import com.alananasss.kittytune.ui.player.audio.StadiumAudioProcessor
import com.alananasss.kittytune.ui.player.audio.CassetteWalkmanAudioProcessor
import com.alananasss.kittytune.ui.player.audio.AsmrVocalAudioProcessor
import com.alananasss.kittytune.ui.player.audio.NightDriveAudioProcessor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private var exoPlayerFactory: ((Int) -> ExoPlayer)? = null
    private var playerListener: Player.Listener? = null

    val player: ExoPlayer
        get() = if (activePlayerIndex == 1) _player1!! else getOrInitPlayer2()

    private fun getOrInitPlayer2(): ExoPlayer {
        if (_player2 == null) {
            _player2 = exoPlayerFactory?.invoke(1)
            _player2?.setSeekParameters(SeekParameters.EXACT)
            playerListener?.let { _player2?.addListener(it) }
        }
        return _player2!!
    }

    var currentTrack: Track? = null

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

    private val _contextFlow = MutableStateFlow<PlaybackContext?>(null)
    val contextFlow = _contextFlow.asStateFlow()

    fun updateContext(context: PlaybackContext?) {
        _contextFlow.value = context
    }

    private val _trackUpdatedFlow = MutableSharedFlow<Track>(extraBufferCapacity = 1)
    val trackUpdatedFlow = _trackUpdatedFlow.asSharedFlow()

    private val _trackDeletedFlow = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val trackDeletedFlow = _trackDeletedFlow.asSharedFlow()

    private val _playlistDeletedFlow = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val playlistDeletedFlow = _playlistDeletedFlow.asSharedFlow()

    fun updateTrackMetadata(updatedTrack: Track) {
        if (currentTrack?.id == updatedTrack.id) {
            currentTrack = updatedTrack
            val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(updatedTrack.title ?: "Unknown")
                .setArtist(updatedTrack.user?.username ?: "Unknown")
                .setArtworkUri(if (updatedTrack.artworkUrl != null) android.net.Uri.parse(updatedTrack.artworkUrl) else null)
                .build()
            try {
                player.currentMediaItem?.let { currentMediaItem ->
                    val updatedMediaItem = currentMediaItem.buildUpon()
                        .setMediaMetadata(mediaMetadata)
                        .build()
                    player.replaceMediaItem(player.currentMediaItemIndex, updatedMediaItem)
                }
            } catch (_: Exception) {}
        }
        _trackUpdatedFlow.tryEmit(updatedTrack)
    }

    fun notifyTrackDeleted(trackId: Long) {
        _trackDeletedFlow.tryEmit(trackId)
    }

    fun notifyPlaylistDeleted(playlistId: Long) {
        _playlistDeletedFlow.tryEmit(playlistId)
    }

    var onTrackChange: ((Track) -> Unit)? = null

    private var preloadedTrack: Track? = null

    private val eightDProcessors = listOf(EightDAudioProcessor(), EightDAudioProcessor())
    private val fxProcessors = listOf(FxAudioProcessor(), FxAudioProcessor())
    private val reverbProcessors = listOf(ReverbAudioProcessor(), ReverbAudioProcessor())
    private val earrapeProcessors = listOf(EarrapeAudioProcessor(), EarrapeAudioProcessor())
    private val monoProcessors = listOf(MonoAudioProcessor(), MonoAudioProcessor())
    private val normalizerProcessors = listOf(R128AudioProcessor(), R128AudioProcessor())
    private val vintageMp3Processors = listOf(VintageMp3AudioProcessor(), VintageMp3AudioProcessor())
    private val vocalRemoverProcessors = listOf(VocalRemoverAudioProcessor(), VocalRemoverAudioProcessor())
    private val vocalBoostProcessors = listOf(VocalBoostAudioProcessor(), VocalBoostAudioProcessor())
    private val flangerProcessors = listOf(FlangerAudioProcessor(), FlangerAudioProcessor())
    private val partyNextDoorProcessors = listOf(PartyNextDoorAudioProcessor(), PartyNextDoorAudioProcessor())
    private val superWideProcessors = listOf(SuperWideAudioProcessor(), SuperWideAudioProcessor())
    private val vinylLoFiProcessors = listOf(VinylLoFiAudioProcessor(), VinylLoFiAudioProcessor())
    private val phaserProcessors = listOf(PhaserAudioProcessor(), PhaserAudioProcessor())
    private val megaphoneProcessors = listOf(MegaphoneRadioAudioProcessor(), MegaphoneRadioAudioProcessor())
    private val robotVocoderProcessors = listOf(RobotVocoderAudioProcessor(), RobotVocoderAudioProcessor())
    private val chorusProcessors = listOf(ChorusAudioProcessor(), ChorusAudioProcessor())
    private val underwaterProcessors = listOf(UnderwaterAudioProcessor(), UnderwaterAudioProcessor())
    private val tranceGateProcessors = listOf(TranceGateAudioProcessor(), TranceGateAudioProcessor())
    private val pingPongDelayProcessors = listOf(PingPongDelayAudioProcessor(), PingPongDelayAudioProcessor())
    private val chiptuneProcessors = listOf(ChiptuneAudioProcessor(), ChiptuneAudioProcessor())
    private val shimmerReverbProcessors = listOf(ShimmerReverbAudioProcessor(), ShimmerReverbAudioProcessor())
    private val rotarySpeakerProcessors = listOf(RotarySpeakerAudioProcessor(), RotarySpeakerAudioProcessor())
    private val tapeSaturationProcessors = listOf(TapeSaturationAudioProcessor(), TapeSaturationAudioProcessor())
    private val subOctaverProcessors = listOf(SubOctaverAudioProcessor(), SubOctaverAudioProcessor())
    private val emptyMallProcessors = listOf(EmptyMallAudioProcessor(), EmptyMallAudioProcessor())
    private val gramophoneProcessors = listOf(GramophoneAudioProcessor(), GramophoneAudioProcessor())
    private val reverseEchoProcessors = listOf(ReverseEchoAudioProcessor(), ReverseEchoAudioProcessor())
    private val stadiumProcessors = listOf(StadiumAudioProcessor(), StadiumAudioProcessor())
    private val cassetteWalkmanProcessors = listOf(CassetteWalkmanAudioProcessor(), CassetteWalkmanAudioProcessor())
    private val asmrVocalProcessors = listOf(AsmrVocalAudioProcessor(), AsmrVocalAudioProcessor())
    private val nightDriveProcessors = listOf(NightDriveAudioProcessor(), NightDriveAudioProcessor())

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
                                .setAudioProcessors(arrayOf(vocalRemoverProcessors[index], vocalBoostProcessors[index], tapeSaturationProcessors[index], subOctaverProcessors[index], chorusProcessors[index], flangerProcessors[index], phaserProcessors[index], rotarySpeakerProcessors[index], robotVocoderProcessors[index], tranceGateProcessors[index], underwaterProcessors[index], partyNextDoorProcessors[index], emptyMallProcessors[index], superWideProcessors[index], pingPongDelayProcessors[index], reverseEchoProcessors[index], fxProcessors[index], reverbProcessors[index], shimmerReverbProcessors[index], eightDProcessors[index], earrapeProcessors[index], monoProcessors[index], normalizerProcessors[index], vinylLoFiProcessors[index], gramophoneProcessors[index], megaphoneProcessors[index], chiptuneProcessors[index], vintageMp3Processors[index]))
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

        this.exoPlayerFactory = createExoPlayer
        _player1 = createExoPlayer(0)
        _player1?.setSeekParameters(SeekParameters.EXACT)

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
        this.playerListener = listener
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

    fun preloadNext(nextTrack: Track, context: Context) {
        preloadedTrack = nextTrack
        val inactivePlayer = if (activePlayerIndex == 1) getOrInitPlayer2() else _player1!!
        inactivePlayer.stop()
        inactivePlayer.clearMediaItems()

        val uri = android.net.Uri.parse("soundtune://track/${nextTrack.id}")

        val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(nextTrack.title ?: "Unknown")
            .setArtist(nextTrack.user?.username ?: "Unknown")
            .setArtworkUri(if (nextTrack.artworkUrl != null) android.net.Uri.parse(nextTrack.artworkUrl) else null)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(if (nextTrack.source == "youtube") "yt_${nextTrack.id}" else "${nextTrack.id}")
            .setMediaMetadata(mediaMetadata)
            .build()

        inactivePlayer.setMediaItem(mediaItem)
        inactivePlayer.prepare()
    }

    fun applyEffects(state: AudioEffectsState) {
        val pitch = if (state.isPitchEnabled) state.speed else 1f
        _player1?.playbackParameters = PlaybackParameters(state.speed, pitch)
        _player2?.playbackParameters = PlaybackParameters(state.speed, pitch)

        eightDProcessors.forEach {
            it.setEnabled(state.is8DEnabled)
            it.setSpeed(state.eightDSpeed)
        }
        fxProcessors.forEach {
            it.setEffects(state.isMuffledEnabled, state.isBassBoostEnabled)
            it.setBassBoostGain(state.bassBoostIntensity)
            it.setMuffledCutoff(state.muffledIntensity)
        }
        reverbProcessors.forEach {
            it.setEnabled(state.isReverbEnabled)
            it.setDecay(state.reverbIntensity)
        }
        earrapeProcessors.forEach {
            it.setEnabled(state.isEarrapeEnabled)
            it.setIntensity(state.earrapeIntensity)
        }
        monoProcessors.forEach {
            it.setEnabled(state.isMonoEnabled)
        }
        normalizerProcessors.forEach {
            it.setParameters(state.isNormalizationEnabled, state.normalizationLevel)
        }
        vintageMp3Processors.forEach {
            it.setEnabled(state.isVintageMp3Enabled)
            it.setCompression(state.vintageMp3Compression)
        }
        vocalRemoverProcessors.forEach {
            it.setEnabled(state.isVocalRemoverEnabled)
            it.setSuppressionLevel(state.vocalRemoverLevel)
        }
        vocalBoostProcessors.forEach {
            it.setEnabled(state.isVocalBoostEnabled)
            it.setIntensity(state.vocalBoostIntensity)
        }
        flangerProcessors.forEach {
            it.setEnabled(state.isFlangerEnabled)
            it.setIntensity(state.flangerIntensity)
            it.setSpeed(state.flangerSpeed)
        }
        partyNextDoorProcessors.forEach {
            it.setEnabled(state.isPartyNextDoorEnabled)
            it.setIsolation(state.partyNextDoorIsolation)
            it.setReverb(state.partyNextDoorReverb)
            it.setBassRumble(state.partyNextDoorBassRumble)
        }
        superWideProcessors.forEach {
            it.setEnabled(state.isSuperWideEnabled)
            it.setWidth(state.superWideWidth)
            it.setDepth(state.superWideDepth)
        }
        vinylLoFiProcessors.forEach {
            it.setEnabled(state.isVinylLoFiEnabled)
            it.setCrackles(state.vinylCrackles)
            it.setFlutter(state.vinylFlutter)
        }
        phaserProcessors.forEach {
            it.setEnabled(state.isPhaserEnabled)
            it.setSpeed(state.phaserSpeed)
            it.setFeedback(state.phaserFeedback)
        }
        megaphoneProcessors.forEach {
            it.setEnabled(state.isMegaphoneEnabled)
            it.setTone(state.megaphoneTone)
            it.setDrive(state.megaphoneDrive)
        }
        robotVocoderProcessors.forEach {
            it.setEnabled(state.isRobotVocoderEnabled)
            it.setFrequency(state.robotFrequency)
            it.setMix(state.robotMix)
        }
        chorusProcessors.forEach {
            it.setEnabled(state.isChorusEnabled)
            it.setRate(state.chorusRate)
            it.setDepth(state.chorusDepth)
        }
        underwaterProcessors.forEach {
            it.setEnabled(state.isUnderwaterEnabled)
            it.setDepth(state.underwaterDepth)
            it.setBubbles(state.underwaterBubbles)
        }
        tranceGateProcessors.forEach {
            it.setEnabled(state.isTranceGateEnabled)
            it.setSpeed(state.tranceGateSpeed)
            it.setPattern(state.tranceGatePattern)
            it.setMix(state.tranceGateMix)
        }
        pingPongDelayProcessors.forEach {
            it.setEnabled(state.isPingPongDelayEnabled)
            it.setDelayTime(state.pingPongDelayTime)
            it.setFeedback(state.pingPongFeedback)
        }
        chiptuneProcessors.forEach {
            it.setEnabled(state.isChiptuneEnabled)
            it.setBits(state.chiptuneBits)
            it.setSampleRateDown(state.chiptuneSampleRate)
        }
        shimmerReverbProcessors.forEach {
            it.setEnabled(state.isShimmerReverbEnabled)
            it.setSize(state.shimmerSize)
            it.setShimmerMix(state.shimmerMix)
        }
        rotarySpeakerProcessors.forEach {
            it.setEnabled(state.isRotarySpeakerEnabled)
            it.setSpeed(state.rotarySpeed)
            it.setDepth(state.rotaryDepth)
        }
        tapeSaturationProcessors.forEach {
            it.setEnabled(state.isTapeSaturationEnabled)
            it.setWarmth(state.tapeWarmth)
            it.setExciter(state.tapeExciter)
        }
        subOctaverProcessors.forEach {
            it.setEnabled(state.isSubOctaverEnabled)
            it.setSubLevel(state.subOctaverLevel)
            it.setSubCutoff(state.subOctaverCutoff)
        }
        emptyMallProcessors.forEach {
            it.setEnabled(state.isEmptyMallEnabled)
            it.setDistance(state.emptyMallDistance)
            it.setGlassReverb(state.emptyMallReverb)
        }
        gramophoneProcessors.forEach {
            it.setEnabled(state.isGramophoneEnabled)
            it.setShellacAge(state.gramophoneAge)
            it.setHornResonance(state.gramophoneHorn)
        }
        reverseEchoProcessors.forEach {
            it.setEnabled(state.isReverseEchoEnabled)
            it.setTime(state.reverseEchoTime)
            it.setFeedback(state.reverseEchoFeedback)
        }
        stadiumProcessors.forEach {
            it.setEnabled(state.isStadiumEnabled)
            it.setStadiumSize(state.stadiumSize)
            it.setAtmosphere(state.stadiumAtmosphere)
        }
        cassetteWalkmanProcessors.forEach {
            it.setEnabled(state.isWalkmanEnabled)
            it.setDrive(state.walkmanDrive)
            it.setTapeHiss(state.walkmanHiss)
        }
        asmrVocalProcessors.forEach {
            it.setEnabled(state.isAsmrVocalEnabled)
            it.setProximity(state.asmrProximity)
            it.setAirSheen(state.asmrAir)
        }
        nightDriveProcessors.forEach {
            it.setEnabled(state.isNightDriveEnabled)
            it.setCabinWidth(state.nightDriveCabin)
            it.setRoadRumble(state.nightDriveRoad)
        }

        rainPlayer?.setEnabled(state.isRainEnabled)
        rainPlayer?.setVolume(state.rainVolume)
        rainPlayer?.setAmbientType(state.ambientType)
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
