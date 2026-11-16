/**
 * Developed by Jason-Marshall Fastner, Germany <jasonfastner@protonmail.com>
 * Questions, feedback, or beat-matching debates? Feel free to reach out via email!
 * 
 * Note: Cats always land on their feet, and with this engine, your transitions will too.
 */
package com.alananasss.kittytune.data

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Looper
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
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
import com.alananasss.kittytune.audio.normalization.NormalizingAudioRenderer
import com.alananasss.kittytune.audio.normalization.SafetyLimiterProcessor
import com.alananasss.kittytune.audio.normalization.TrackNormalizerProcessor
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
import com.alananasss.kittytune.ui.player.EqualizerState
import com.alananasss.kittytune.ui.player.audio.EqualizerAudioProcessor
import com.alananasss.kittytune.audio.ai.AiDetectionAudioProcessor
import com.alananasss.kittytune.audio.ai.AiDetectionManager
import com.alananasss.kittytune.audio.silence.SilenceDetectorAudioProcessor
import com.alananasss.kittytune.audio.usb.UsbAudioManager
import com.alananasss.kittytune.audio.usb.BitPerfectContainerAudioProcessor
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.trustedAnchorMs
import android.os.HandlerThread
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object MusicManager {

    private var appContext: Context? = null

    private const val CROSSFADE_REQUEST_TIMEOUT_MS = 5_000L
    private const val CROSSFADE_STALL_TIMEOUT_MS = 10_000L
    private const val DRIFT_CHECK_STEPS = 16
    private const val PHASE_LOCK_READY_TIMEOUT_MS = 3_000L
    private const val PHASE_LOCK_SETTLE_TIMEOUT_MS = 800L
    private const val PHASE_LOCK_MAX_SEEKS = 3

    /** Lowest speed/pitch handed to ExoPlayer during the Melt preset (it rejects values <= 0). */
    private const val MIN_MELT_SPEED = 0.05f

    private var _player1: ExoPlayer? = null
    private var _player2: ExoPlayer? = null
    private var activePlayerIndex = 1

    var lastPlayer: ExoPlayer? = null
    val onPlayerSwappedFlow = MutableStateFlow(0)

    private val _isCrossfadingOut = MutableStateFlow(false)
    val isCrossfadingOutFlow = _isCrossfadingOut.asStateFlow()

    var isCrossfadingOut: Boolean
        get() = _isCrossfadingOut.value
        set(value) {
            _isCrossfadingOut.value = value
            if (!value) {
                crossfadeRequestWatchdog?.cancel()
                crossfadeRequestWatchdog = null
            }
        }

    private var crossfadeRequestWatchdog: Job? = null
    private var fadingPlayer: ExoPlayer? = null

    @Volatile
    var pendingTransitionStyle: com.alananasss.kittytune.audio.automix.MixTransitionPresets.MixTransitionStyle? = null

    @Volatile
    var pendingTransitionDurationMs: Long? = null

    @Volatile
    var pendingTransitionPair: Pair<Long, Long>? = null

    @Volatile
    var pendingPairPlan: com.alananasss.kittytune.audio.automix.TransitionPlan? = null

    private var transitionSeq = 0L
    private var liveTransitions = 0
    private val activeTransitionIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    fun isTransitionRunning(): Boolean = liveTransitions > 0

    private var activeCrossfadeIncoming: ExoPlayer? = null
    private var activeCrossfadeOutgoing: ExoPlayer? = null

    private val bitPerfectBlocksResample: Boolean
        get() = com.alananasss.kittytune.audio.AudioRoutingMatrix.isBitPerfectActive()

    private var lastEffectsState: AudioEffectsState? = null

    fun cancelCrossfade() {
        crossfadeRequestWatchdog?.cancel()
        crossfadeRequestWatchdog = null
        transitionSeq++
        val incoming = activeCrossfadeIncoming
        val outgoing = activeCrossfadeOutgoing ?: fadingPlayer
        activeCrossfadeIncoming = null
        activeCrossfadeOutgoing = null
        try {
            incoming?.volume = 1f
            if (!bitPerfectBlocksResample) {
                lastEffectsState?.let { st ->
                    val pitch = if (st.isPitchEnabled) st.speed else 1f
                    try { incoming?.playbackParameters = PlaybackParameters(st.speed, pitch) } catch (_: Exception) {}
                }
            }
            outgoing?.volume = 0f
            outgoing?.playWhenReady = false
            outgoing?.stop()
            outgoing?.clearMediaItems()
            if (_player2 != null) {
                val inactive = if (activePlayerIndex == 1) _player2 else _player1
                if (inactive != null && inactive != player) {
                    inactive.volume = 0f
                    inactive.playWhenReady = false
                    inactive.stop()
                    inactive.clearMediaItems()
                }
            }
        } catch (_: Exception) {}
        fadingPlayer = null
        liveTransitions = 0
        activeTransitionIds.clear()
        isCrossfadingOut = false
        automixDuckProcessors.forEach { it.resetGain() }
        mixSweepProcessors.forEach { it.resetSweep() }
        setGlobalStemLevels(1.0f, 1.0f, 1.0f)
        com.alananasss.kittytune.audio.automix.AutomixManager.setIsAutomixing(false)
        com.alananasss.kittytune.audio.automix.AutomixManager.clearPlan()
        com.alananasss.kittytune.audio.automix.AutomixManager.setMixProgress(0f)
        updateRainPlayback()
        releasePrebuffered()
    }

    fun beginCrossfadeRequest() {
        isCrossfadingOut = true
        val seqAtRequest = transitionSeq
        crossfadeRequestWatchdog?.cancel()
        crossfadeRequestWatchdog = scope.launch {
            delay(CROSSFADE_REQUEST_TIMEOUT_MS)
            if (transitionSeq == seqAtRequest && liveTransitions == 0 && _isCrossfadingOut.value) {
                Log.w(
                    "MusicManager",
                    "Crossfade request timed out after ${CROSSFADE_REQUEST_TIMEOUT_MS}ms without a transition; clearing latch"
                )
                _isCrossfadingOut.value = false
                val active = try { player } catch (_: Exception) { null }
                if (active != null && (active.playbackState == Player.STATE_ENDED || (active.duration > 0L && active.currentPosition >= active.duration - 200L))) {
                    Log.i("MusicManager", "Active player reached end of track during request timeout; triggering onNextClick recovery")
                    onNextClick?.invoke()
                }
            }
            crossfadeRequestWatchdog = null
        }
    }

    private var exoPlayerFactory: ((Int) -> ExoPlayer)? = null

    val player: ExoPlayer
        get() = if (activePlayerIndex == 1) _player1!! else getOrInitPlayer2()

    private fun getOrInitPlayer2(): ExoPlayer {
        if (_player2 == null) {
            _player2 = exoPlayerFactory?.invoke(1)
            _player2?.setSeekParameters(SeekParameters.EXACT)
            _player2?.addListener(createPlayerListener(1))
            UsbAudioManager.findUsbDac()?.let { _player2?.setPreferredAudioDevice(it) }
            // The second deck is created lazily (first crossfade): give it the saved skip-silence state too.
            applySkipSilenceSettings()
        }
        return _player2!!
    }

    var currentTrack: Track? = null

    val isLoadingTrackFlow = MutableStateFlow(false)
    var isLoadingTrack: Boolean
        get() = isLoadingTrackFlow.value
        set(value) {
            isLoadingTrackFlow.value = value
        }

    val playWhenReadyRequestedFlow = MutableStateFlow(true)
    var playWhenReadyRequested: Boolean
        get() = playWhenReadyRequestedFlow.value
        set(value) {
            playWhenReadyRequestedFlow.value = value
            if (!value) {
                try {
                    _player1?.playWhenReady = false
                    _player1?.pause()
                    _player2?.playWhenReady = false
                    _player2?.pause()
                } catch (_: Exception) {}
            }
        }
    var pendingSeekPositionMs: Long? = null

    private val drmTokenCache = ConcurrentHashMap<Long, String>()

    fun putDrmToken(trackId: Long, token: String) {
        drmTokenCache[trackId] = token
    }

    fun getDrmToken(trackId: Long): String? = drmTokenCache[trackId]

    fun clearDrmToken(trackId: Long) {
        drmTokenCache.remove(trackId)
    }

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var pausedForTransientFocusLoss = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                try {
                    player.pause()
                    fadingPlayer?.pause()
                } catch (_: Exception) {}
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                try {
                    if (player.isPlaying || fadingPlayer?.isPlaying == true) {
                        pausedForTransientFocusLoss = true
                        player.pause()
                        fadingPlayer?.pause()
                    }
                } catch (_: Exception) {}
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedForTransientFocusLoss) {
                    pausedForTransientFocusLoss = false
                    try {
                        player.play()
                        fadingPlayer?.play()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        val req = audioFocusRequest ?: return true
        val res = am.requestAudioFocus(req)
        return res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandonAudioFocus() {
        val am = audioManager ?: return
        val req = audioFocusRequest ?: return
        am.abandonAudioFocusRequest(req)
    }

    fun applySkipSilenceSettings() {
        val ctx = appContext ?: return
        val prefs = PlayerPreferences(ctx)
        val skipSilence = prefs.getSkipSilence()
        val instant = skipSilence && prefs.getInstantSkipSilence()
        instantSilenceSkipEnabled = instant
        try {
            _player1?.skipSilenceEnabled = skipSilence
            _player2?.skipSilenceEnabled = skipSilence
        } catch (_: Exception) {}
        silenceDetectorProcessors.forEach { proc ->
            proc.instantModeEnabled = instant
            if (!instant) proc.resetTracking()
        }
        if (!instant) silenceSkipJob?.cancel()
    }

    private fun handleLongSilenceDetected() {
        if (!instantSilenceSkipEnabled) return
        if (silenceSkipJob?.isActive == true) return
        silenceSkipJob = scope.launch {
            delay(200)
            performInstantSilenceSkip()
        }
    }

    private suspend fun performInstantSilenceSkip() {
        val p = try { player } catch (_: Exception) { return }
        val duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        if (duration <= INSTANT_SILENCE_SKIP_STEP_MS) return
        isSilenceSkipping = true
        try {
            var hops = 0
            val silenceProcessor = silenceDetectorProcessors.getOrNull(if (activePlayerIndex == 1) 0 else 1) ?: return
            while (kotlin.coroutines.coroutineContext.isActive && instantSilenceSkipEnabled && silenceProcessor.isCurrentlySilent()) {
                val current = p.currentPosition
                val target = (current + INSTANT_SILENCE_SKIP_STEP_MS).coerceAtMost(duration - 500)
                if (target <= current) break
                silenceProcessor.resetTracking()
                p.seekTo(target)
                hops++
                if (hops >= INSTANT_SILENCE_SKIP_MAX_HOPS || target >= duration - 500) break
                delay(INSTANT_SILENCE_SKIP_SETTLE_MS)
            }
            if (hops > 0) {
                Log.i("MusicManager", "Instant silence skip: jumped $hops times")
            }
        } finally {
            isSilenceSkipping = false
        }
    }

    private fun registerPlaybackDeviceCallbacks(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                val hasBluetooth = addedDevices?.any {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                } == true
                if (!hasBluetooth) return
                val prefs = PlayerPreferences(context)
                if (!prefs.getResumeOnBluetoothConnect()) return
                try {
                    val p = player
                    if (p.playbackState == Player.STATE_READY && !p.isPlaying) {
                        p.play()
                    }
                } catch (_: Exception) {}
            }
        }
        audioDeviceCallback = callback
        am.registerAudioDeviceCallback(callback, null)

        if (mVolumeChangeReceiver == null) {
            val receiver = object : android.content.BroadcastReceiver() {
                val prefsLocal = PlayerPreferences(context)
                override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
                    handleVolumeChanged(ctx ?: context)
                }
            }
            mVolumeChangeReceiver = receiver
            try {
                context.registerReceiver(
                    receiver,
                    android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION")
                )
            } catch (_: Exception) {}
        }
    }

    private fun handleVolumeChanged(context: Context) {
        val prefs = PlayerPreferences(context)
        if (!prefs.getPauseOnMute()) return
        val am = audioManager ?: return
        try {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val p = player
            if (max > 0 && current == 0) {
                if (p.isPlaying) {
                    wasPlayingBeforeMute = true
                    p.pause()
                }
            } else if (current > 0 && wasPlayingBeforeMute) {
                wasPlayingBeforeMute = false
                if (p.playbackState == Player.STATE_READY) {
                    p.play()
                }
            }
        } catch (_: Exception) {}
    }

    private var mVolumeChangeReceiver: android.content.BroadcastReceiver? = null

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

    val currentLyricsFlow = MutableStateFlow<List<com.alananasss.kittytune.ui.player.lyrics.LyricLine>>(emptyList())
    @Volatile var lyricsOffsetMs: Long = 0L

    val currentAudioFormatFlow = MutableStateFlow<androidx.media3.common.Format?>(null)

    fun updateTrackMetadata(updatedTrack: Track) {
        if (currentTrack?.id == updatedTrack.id) {
            currentTrack = updatedTrack
            val artist = updatedTrack.displayArtist.ifBlank { updatedTrack.user?.username ?: "Unknown" }
            val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(updatedTrack.title ?: "Unknown")
                .setArtist(artist)
                .setSubtitle(artist)
                .setIsPlayable(true)
                .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC)
                .setArtworkUri(if (updatedTrack.artworkUrl != null) android.net.Uri.parse(updatedTrack.artworkUrl) else null)
                .apply {
                    updatedTrack.publisherMetadata?.albumTitle?.takeIf { it.isNotBlank() }?.let { setAlbumTitle(it) }
                }
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

    private val aiDetectionProcessors = listOf(AiDetectionAudioProcessor(), AiDetectionAudioProcessor())
    private val eightDProcessors = listOf(EightDAudioProcessor(), EightDAudioProcessor())
    private val fxProcessors = listOf(FxAudioProcessor(), FxAudioProcessor())
    private val reverbProcessors = listOf(ReverbAudioProcessor(), ReverbAudioProcessor())
    private val earrapeProcessors = listOf(EarrapeAudioProcessor(), EarrapeAudioProcessor())
    private val monoProcessors = listOf(MonoAudioProcessor(), MonoAudioProcessor())
    private val normalizerProcessors = listOf(TrackNormalizerProcessor(), TrackNormalizerProcessor())
    private val safetyLimiterProcessors = listOf(SafetyLimiterProcessor(), SafetyLimiterProcessor())
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
    private val equalizerProcessors = listOf(EqualizerAudioProcessor(), EqualizerAudioProcessor())
    private val automixDuckProcessors = listOf(
        com.alananasss.kittytune.audio.automix.AutomixDuckAudioProcessor(),
        com.alananasss.kittytune.audio.automix.AutomixDuckAudioProcessor()
    )
    private val mixSweepProcessors = listOf(
        com.alananasss.kittytune.audio.automix.MixSweepAudioProcessor(),
        com.alananasss.kittytune.audio.automix.MixSweepAudioProcessor()
    )
    private val djStemProcessors = listOf(
        com.alananasss.kittytune.audio.automix.DjStemAudioProcessor(),
        com.alananasss.kittytune.audio.automix.DjStemAudioProcessor()
    )
    private var hapticProcessors: List<com.alananasss.kittytune.audio.haptics.HapticAudioProcessor>? = null

    private val liveBeatTrackers = List(2) { com.alananasss.kittytune.audio.beat.LiveBeatTracker() }
    private val liveBeatProcessors = liveBeatTrackers.map { com.alananasss.kittytune.audio.beat.LiveBeatAudioProcessor(it) }

    val liveBeat: com.alananasss.kittytune.audio.beat.LiveBeatTracker
        get() = liveBeatTrackers[if (activePlayerIndex == 1) 0 else 1]

    private val silenceDetectorProcessors = listOf(
        SilenceDetectorAudioProcessor { handleLongSilenceDetected() },
        SilenceDetectorAudioProcessor { handleLongSilenceDetected() }
    )

    private var instantSilenceSkipEnabled = false
    private var silenceSkipJob: Job? = null
    private var isSilenceSkipping = false

    private const val INSTANT_SILENCE_SKIP_STEP_MS = 15_000L
    private const val INSTANT_SILENCE_SKIP_SETTLE_MS = 350L
    private const val INSTANT_SILENCE_SKIP_MAX_HOPS = 80

    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var wasPlayingBeforeMute: Boolean = false

    var onNextClick: (() -> Unit)? = null
    var onPreviousClick: (() -> Unit)? = null
    var onShuffleClick: (() -> Unit)? = null
    private var rainPlayer: RainPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun pause() {
        try {
            _player1?.pause()
            _player2?.pause()
        } catch (_: Exception) {}
    }

    fun init(context: Context) {
        if (_player1 != null) return

        appContext = context.applicationContext
        val playerPrefs = PlayerPreferences(appContext!!)

        com.alananasss.kittytune.audio.normalization.LoudnessStore.init(context)
        AiDetectionManager.init(appContext!!)
        com.alananasss.kittytune.audio.automix.AutomixManager.init(context)
        
        UsbAudioManager.init(appContext!!, playerPrefs)
        UsbAudioManager.onUsbDacDisconnectedWhileActive = { pause() }
        UsbAudioManager.onReconfigureRequested = { reconfigureActiveSink() }
        UsbAudioManager.onPreferredDeviceChanged = { dac ->
            _player1?.setPreferredAudioDevice(dac)
            _player2?.setPreferredAudioDevice(dac)
        }

        scope.launch {
            var lastBitPerfect = false
            UsbAudioManager.isEffectsSuspendedByBitPerfect.collect { suspended ->
                if (suspended && !lastBitPerfect) {
                    if (liveTransitions > 0) {
                        Log.i("MusicManager", "Bit-Perfect engaged mid-crossfade: cancelling the automix transition")
                        try { cancelCrossfade() } catch (_: Exception) {}
                    }
                    try {
                        _player1?.playbackParameters = PlaybackParameters(1f, 1f)
                        _player2?.playbackParameters = PlaybackParameters(1f, 1f)
                    } catch (_: Exception) {}
                } else if (!suspended && lastBitPerfect) {
                    lastEffectsState?.let { state ->
                        try { applyEffects(state) } catch (_: Exception) {}
                    }
                }
                lastBitPerfect = suspended
            }
        }

        registerPlaybackDeviceCallbacks(appContext!!)
        applySkipSilenceSettings()
        
        val haptics = listOf(
            com.alananasss.kittytune.audio.haptics.HapticAudioProcessor(appContext!!),
            com.alananasss.kittytune.audio.haptics.HapticAudioProcessor(appContext!!)
        )
        hapticProcessors = haptics

        rainPlayer = RainPlayer(appContext!!)

        aiDetectionProcessors.forEach { it.setPreferences(playerPrefs) }
        val lastContext = playerPrefs.getLastContext()
        _contextFlow.value = lastContext

        val tokenManager = com.alananasss.kittytune.data.TokenManager(appContext!!)
        val token = com.alananasss.kittytune.data.SessionManager.harvestStoredSession(appContext!!) ?: tokenManager.getAccessToken()
        val customUserAgent = "SoundCloud/2025.12.10-release (Android 10; Android)"

        val okHttpClient = com.alananasss.kittytune.data.network.ProxyManager.getOkHttpClient().newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                val urlStr = request.url.toString()
                val builder = request.newBuilder()
                if (urlStr.contains("googlevideo.com") || urlStr.contains("youtube.com")) {
                    val ua = if (org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isAndroidStreamingUrl(urlStr)) {
                        org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getAndroidUserAgent(null)
                    } else if (org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isVisionOsStreamingUrl(urlStr)) {
                        org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getVisionOsUserAgent(null)
                    } else {
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    }
                    builder.header("User-Agent", ua)
                    builder.header("Accept", "*/*")
                    builder.header("Origin", "https://www.youtube.com")
                    builder.header("Referer", "https://www.youtube.com/")
                } else if (request.header("User-Agent") == null) {
                    builder.header("User-Agent", customUserAgent)
                }
                chain.proceed(builder.build())
            }
            .build()

        val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
        val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val deezerAwareDataSourceFactory = com.alananasss.kittytune.audio.providers.deezer.DeezerAudioAwareDataSourceFactory(defaultDataSourceFactory)

        val downloadCache = com.alananasss.kittytune.data.local.ExoCacheManager.getDownloadCache(context)
        val playbackCache = com.alananasss.kittytune.data.local.ExoCacheManager.getPlaybackCache(context)

        val downloadCacheFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(deezerAwareDataSourceFactory)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val playbackCacheFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(playbackCache)
            .setUpstreamDataSourceFactory(deezerAwareDataSourceFactory)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val tieredCacheDataSourceFactory = androidx.media3.datasource.DataSource.Factory {
            val downloadSource = downloadCacheFactory.createDataSource()
            val playbackSource = playbackCacheFactory.createDataSource()
            object : androidx.media3.datasource.DataSource {
                private var currentSource: androidx.media3.datasource.DataSource = playbackSource

                override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
                    downloadSource.addTransferListener(transferListener)
                    playbackSource.addTransferListener(transferListener)
                }

                override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
                    val key = dataSpec.key ?: dataSpec.uri.toString()
                    val isDownloaded = try {
                        downloadCache.keys.contains(key)
                    } catch (_: Exception) {
                        false
                    }
                    currentSource = if (isDownloaded) downloadSource else playbackSource
                    return currentSource.open(dataSpec)
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    return currentSource.read(buffer, offset, length)
                }

                override fun getUri(): android.net.Uri? = currentSource.uri

                override fun close() {
                    currentSource.close()
                }
            }
        }

        val resolvingDataSourceFactory = ResolvingDataSource.Factory(tieredCacheDataSourceFactory, object : ResolvingDataSource.Resolver {
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
                                            val parts = localTrack.localAudioPath.removePrefix("exo_cache://").split("::", limit = 3)
                                            val parsedTrackId = parts[0].toLongOrNull()
                                            val cachedStreamUrl = parts.getOrNull(1)

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
                                        val needsFullTrack = trackToResolve == null ||
                                            !hasMediaInfo ||
                                            trackToResolve.title.isNullOrBlank() ||
                                            trackToResolve.user == null ||
                                            ((trackToResolve.isGoPlus || trackToResolve.isSnipped || (trackToResolve.durationMs != null && trackToResolve.durationMs in 1..45000L)) && trackToResolve.fullDuration == null)

                                        if (needsFullTrack && trackId > 0) {
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

                                            if (resolved?.isDrmProtected == true && resolved.licenseAuthToken != null) {
                                                putDrmToken(trackId, resolved.licenseAuthToken)
                                                Log.d("MusicManager", "DRM token cached for track $trackId")
                                            } else {
                                                clearDrmToken(trackId)
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
                            val uri = if (finalUrl.contains("://")) {
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

        val drmSessionManagerProvider = object : DrmSessionManagerProvider {
            override fun get(mediaItem: MediaItem): DrmSessionManager {
                var finalKeySetId: ByteArray? = null
                var finalToken: String? = null
                val trackId = mediaItem.mediaId.toLongOrNull()

                if (trackId != null) {
                    finalToken = getDrmToken(trackId)

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
                    val manager = DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(
                            androidx.media3.common.C.WIDEVINE_UUID,
                            FrameworkMediaDrm.DEFAULT_PROVIDER
                        )
                        .build(androidx.media3.exoplayer.drm.LocalMediaDrmCallback(ByteArray(0)))
                    manager.setMode(DefaultDrmSessionManager.MODE_PLAYBACK, finalKeySetId)
                    return manager
                } else if (finalToken != null ||
                    (mediaItem.localConfiguration?.uri?.scheme == "soundtune" &&
                        mediaItem.localConfiguration?.uri?.host == "track")) {
                    val callback = SoundCloudDrmCallback(
                        finalToken.orEmpty(),
                        licenseAuthTokenProvider = { trackId?.let { getDrmToken(it) } }
                    )
                    return DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(
                            androidx.media3.common.C.WIDEVINE_UUID,
                            FrameworkMediaDrm.DEFAULT_PROVIDER
                        )
                        .setMultiSession(true)
                        .build(callback)
                }

                return androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider().get(mediaItem)
            }
        }

        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        this.audioManager = am
        this.audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .setAcceptsDelayedFocusGain(true)
            .build()

        val createExoPlayer = { index: Int ->
            ExoPlayer.Builder(context.applicationContext)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(context)
                        .setDataSourceFactory(resolvingDataSourceFactory)
                        .setDrmSessionManagerProvider(drmSessionManagerProvider)
                )
                .setRenderersFactory(
                    object : DefaultRenderersFactory(context) {
                        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
                            val hapticProc = hapticProcessors?.getOrNull(index) ?: com.alananasss.kittytune.audio.haptics.HapticAudioProcessor(context)
                            val duckProc = automixDuckProcessors.getOrNull(index) ?: com.alananasss.kittytune.audio.automix.AutomixDuckAudioProcessor()
                            val stemProc = djStemProcessors.getOrNull(index) ?: com.alananasss.kittytune.audio.automix.DjStemAudioProcessor()
                            val mixSweepProc = mixSweepProcessors.getOrNull(index) ?: com.alananasss.kittytune.audio.automix.MixSweepAudioProcessor()
                            val silenceProc = silenceDetectorProcessors.getOrNull(index) ?: SilenceDetectorAudioProcessor { handleLongSilenceDetected() }
                            val bitPerfectContainerProc = BitPerfectContainerAudioProcessor()

                            val dspList = arrayOf<androidx.media3.common.audio.AudioProcessor>(
                                normalizerProcessors[index], silenceProc, aiDetectionProcessors[index], hapticProc,
                                liveBeatProcessors[index], duckProc, stemProc, mixSweepProc, equalizerProcessors[index],
                                vocalRemoverProcessors[index], vocalBoostProcessors[index], tapeSaturationProcessors[index],
                                subOctaverProcessors[index], chorusProcessors[index], flangerProcessors[index],
                                phaserProcessors[index], rotarySpeakerProcessors[index], robotVocoderProcessors[index],
                                tranceGateProcessors[index], underwaterProcessors[index], partyNextDoorProcessors[index],
                                emptyMallProcessors[index], superWideProcessors[index], pingPongDelayProcessors[index],
                                reverseEchoProcessors[index], stadiumProcessors[index], cassetteWalkmanProcessors[index],
                                asmrVocalProcessors[index], nightDriveProcessors[index], fxProcessors[index],
                                reverbProcessors[index], shimmerReverbProcessors[index], eightDProcessors[index],
                                earrapeProcessors[index], monoProcessors[index], vinylLoFiProcessors[index],
                                gramophoneProcessors[index], megaphoneProcessors[index], chiptuneProcessors[index],
                                vintageMp3Processors[index], safetyLimiterProcessors[index]
                            )

                            return DefaultAudioSink.Builder(context)
                                .setAudioProcessorChain(
                                    com.alananasss.kittytune.audio.usb.BitPerfectAudioProcessorChain(dspList, bitPerfectContainerProc)
                                )
                                .setEnableFloatOutput(enableFloatOutput)
                                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                                .build()
                        }

                        override fun buildAudioRenderers(
                            context: Context,
                            extensionRendererMode: Int,
                            mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
                            enableDecoderFallback: Boolean,
                            audioSink: AudioSink,
                            eventHandler: android.os.Handler,
                            eventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener,
                            out: ArrayList<androidx.media3.exoplayer.Renderer>,
                        ) {
                            val normalizer = normalizerProcessors[index]
                            val codecAdapterFactory = androidx.media3.exoplayer.mediacodec.DefaultMediaCodecAdapterFactory()
                            out.add(
                                NormalizingAudioRenderer(
                                    context, codecAdapterFactory, mediaCodecSelector, enableDecoderFallback,
                                    eventHandler, eventListener, audioSink,
                                ) { mediaId -> normalizer.onTrackStarted(mediaId) }
                            )
                        }

                        override fun buildVideoRenderers(
                            context: Context,
                            extensionRendererMode: Int,
                            mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
                            enableDecoderFallback: Boolean,
                            eventHandler: android.os.Handler,
                            eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
                            allowedVideoJoiningTimeMs: Long,
                            out: ArrayList<androidx.media3.exoplayer.Renderer>,
                        ) {}
                    }
                )
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    false
                )
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()
                .also { it.addAnalyticsListener(createAudioTrackAnalytics(index)) }
        }

        this.exoPlayerFactory = createExoPlayer
        _player1 = createExoPlayer(0)
        _player1?.setSeekParameters(SeekParameters.EXACT)
        _player1?.addListener(createPlayerListener(0))
        UsbAudioManager.findUsbDac()?.let { _player1?.setPreferredAudioDevice(it) }
        // init() applies the saved skip-silence preference before the deck exists; apply it now.
        applySkipSilenceSettings()
    }

    private fun createAudioTrackAnalytics(playerIndex: Int) =
        object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioTrackInitialized(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                audioTrackConfig: AudioSink.AudioTrackConfig
            ) {
                val isActivePlayer = (activePlayerIndex == 1 && playerIndex == 0) ||
                    (activePlayerIndex == 2 && playerIndex == 1)
                if (!isActivePlayer) return
                UsbAudioManager.onAudioTrackInitialized(
                    audioTrackConfig.sampleRate,
                    audioTrackConfig.channelConfig,
                    audioTrackConfig.encoding
                )
            }
        }

    private fun reconfigureActiveSink() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                if (liveTransitions > 0 || isCrossfadingOut) return@post
                val p = player
                if (p.mediaItemCount == 0 || p.playbackState == Player.STATE_IDLE) return@post
                val pos = p.currentPosition
                val resume = p.playWhenReady
                Log.i("MusicManager", "Re-configuring audio sink for Bit-Perfect at ${pos}ms")
                p.stop()
                p.prepare()
                p.seekTo(pos)
                p.playWhenReady = resume
            } catch (e: Exception) {
                Log.w("MusicManager", "Sink reconfiguration failed", e)
            }
        }
    }

    private fun createPlayerListener(playerIndex: Int) = object : Player.Listener {
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            super.onTracksChanged(tracks)
            val isActivePlayer = (activePlayerIndex == 1 && playerIndex == 0) || (activePlayerIndex == 2 && playerIndex == 1)
            if (!isActivePlayer) return
            val audioFormat = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }?.getTrackFormat(0)
            currentAudioFormatFlow.value = audioFormat
            audioFormat?.let {
                // Stable per-track key: our own stop()/prepare() sink rebuild re-fires this
                // callback for the SAME track and must not reset the reconfigure counter.
                val trackKey = (if (playerIndex == 0) _player1 else _player2)
                    ?.currentMediaItem?.mediaId?.takeIf { id -> id.isNotBlank() }
                UsbAudioManager.onPlaybackFormatChanged(
                    it.sampleRate, it.channelCount, it.pcmEncoding, trackKey
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            val isActivePlayer = (activePlayerIndex == 1 && playerIndex == 0) || (activePlayerIndex == 2 && playerIndex == 1)
            if (isActivePlayer && !isPlaying && !isCrossfadingOut) {
                appContext?.let { com.alananasss.kittytune.audio.haptics.PlayerHapticManager.getInstance(it).stopAllHaptics() }
            }
            updateRainPlayback()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            val isActivePlayer = (activePlayerIndex == 1 && playerIndex == 0) || (activePlayerIndex == 2 && playerIndex == 1)
            if (isActivePlayer && (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) && !isCrossfadingOut) {
                appContext?.let { com.alananasss.kittytune.audio.haptics.PlayerHapticManager.getInstance(it).stopAllHaptics() }
            }
            updateRainPlayback()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            updateRainPlayback()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            val isActivePlayer = (activePlayerIndex == 1 && playerIndex == 0) || (activePlayerIndex == 2 && playerIndex == 1)
            if (isActivePlayer) {
                com.alananasss.kittytune.audio.automix.AutomixManager.cancelAllBeatAnalyses()
            } else {
                return
            }
            appContext?.let { com.alananasss.kittytune.audio.haptics.PlayerHapticManager.getInstance(it).stopAllHaptics() }
            aiDetectionProcessors.forEach { it.resetForNewTrack() }
            if (mediaItem == null) {
                AiDetectionManager.resetResult()
                return
            }

            if (isCrossfadingOut && fadingPlayer != null) {
                if (player.currentMediaItem != mediaItem) return
            }

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

            val resolvedUri = mediaItem.localConfiguration?.uri?.toString()
            AiDetectionManager.onTrackChanged(trackId, currentTrack, appContext, resolvedUri)

            currentTrack?.let { track ->
                scope.launch {
                    onTrackChange?.invoke(track)
                }
            }
        }
    }

    fun setRepeatMode(mode: Int) {
        _player1?.repeatMode = mode
        _player2?.repeatMode = mode
    }

    data class PrebufferedTransition(
        val player: ExoPlayer,
        val trackId: Long,
        val plan: com.alananasss.kittytune.audio.automix.AutomixPlan?,
        val basePlaybackParams: PlaybackParameters
    )

    @Volatile
    private var prebuffered: PrebufferedTransition? = null

    fun isPrebuffered(trackId: Long): Boolean {
        val pb = prebuffered ?: return false
        return pb.trackId == trackId && pb.player.playbackState != Player.STATE_IDLE
    }

    fun releasePrebuffered() {
        val pb = prebuffered ?: return
        prebuffered = null
        try {
            pb.player.playWhenReady = false
            pb.player.stop()
            pb.player.clearMediaItems()
        } catch (_: Exception) {}
    }

    fun notifySeek(positionMs: Long) {
        aiDetectionProcessors.forEach { it.notifySeek(positionMs) }
    }

    fun prebufferTransition(
        mediaItem: MediaItem,
        nextTrack: Track,
        automixPlan: com.alananasss.kittytune.audio.automix.AutomixPlan? = null
    ) {
        if (isCrossfadingOut) return
        if (isPrebuffered(nextTrack.id)) return
        if (bitPerfectBlocksResample) return

        val inactivePlayer = if (activePlayerIndex == 1) getOrInitPlayer2() else _player1!!
        try {
            inactivePlayer.playWhenReady = false
            inactivePlayer.stop()
            inactivePlayer.clearMediaItems()
            inactivePlayer.volume = 0f
            inactivePlayer.setMediaItem(mediaItem, automixPlan?.incomingStartMs ?: 0L)

            val baseParams = try { inactivePlayer.playbackParameters } catch (_: Exception) { PlaybackParameters.DEFAULT }
            if (automixPlan != null && !bitPerfectBlocksResample &&
                (automixPlan.tempoRatio != 1f || automixPlan.pitchRatio != 1f)
            ) {
                val adjSpeed = (baseParams.speed * automixPlan.tempoRatio).coerceIn(0.5f, 2.0f)
                val adjPitch = (baseParams.pitch * automixPlan.pitchRatio).coerceIn(0.5f, 2.0f)
                inactivePlayer.playbackParameters = PlaybackParameters(adjSpeed, adjPitch)
            } else {
                inactivePlayer.playbackParameters = baseParams
            }

            inactivePlayer.prepare()
            prebuffered = PrebufferedTransition(inactivePlayer, nextTrack.id, automixPlan, baseParams)
        } catch (e: Exception) {
            Log.w("MusicManager", "Failed to prebuffer transition for track ${nextTrack.id}: ${e.message}")
        }
    }

    private suspend fun awaitRendering(player: ExoPlayer, timeoutMs: Long): Boolean {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            val playing = try { player.isPlaying } catch (_: Exception) { return false }
            if (playing) {
                val before = try { player.currentPosition } catch (_: Exception) { return false }
                delay(30)
                val after = try { player.currentPosition } catch (_: Exception) { return false }
                if (after > before) return true
            } else {
                delay(20)
            }
        }
        return false
    }

    fun crossfadeToMediaItem(
        mediaItem: MediaItem,
        startPositionMs: Long,
        crossfadeDurationMs: Long,
        automixPlan: com.alananasss.kittytune.audio.automix.AutomixPlan? = null,
        isDjConstantEnergy: Boolean = false,
        djFlowActive: Boolean = false,
        djTempoRatio: Float = 1.0f,
        djPhaseOffsetMs: Long = 0L,
        outgoingGrid: com.alananasss.kittytune.audio.automix.BeatPhaseLock.Grid? = null,
        incomingGrid: com.alananasss.kittytune.audio.automix.BeatPhaseLock.Grid? = null,
        transitionStyle: com.alananasss.kittytune.audio.automix.MixTransitionPresets.MixTransitionStyle? = null,
    ) {
        val oldPlayer = player

        val isLoopExtensionEnabled = appContext?.let {
            PlayerPreferences(it).getDjFlowLoopExtension()
        } ?: false

        while (oldPlayer.mediaItemCount > 1) {
            try { oldPlayer.removeMediaItem(1) } catch (_: Exception) {}
        }

        val oldPlayerIndex = if (activePlayerIndex == 1) 0 else 1
        activePlayerIndex = if (activePlayerIndex == 1) 2 else 1
        val newPlayerIndex = if (activePlayerIndex == 1) 0 else 1
        val newPlayer = player
        newPlayer.repeatMode = oldPlayer.repeatMode
        oldPlayer.repeatMode = Player.REPEAT_MODE_OFF

        isCrossfadingOut = true
        crossfadeRequestWatchdog?.cancel()
        crossfadeRequestWatchdog = null
        transitionSeq++
        val myTransition = transitionSeq
        liveTransitions++
        activeTransitionIds.add(myTransition)
        fadingPlayer = oldPlayer
        activeCrossfadeIncoming = newPlayer
        activeCrossfadeOutgoing = oldPlayer

        lastPlayer = oldPlayer
        onPlayerSwappedFlow.value += 1

        val pb = prebuffered
        val targetTrackId = mediaItem.mediaId.removePrefix("yt_").toLongOrNull()
        val outgoingTrackId = currentTrack?.id
        val effectivePlan = (automixPlan ?: pb?.plan)?.takeIf { plan ->
            targetTrackId != null && plan.nextId == targetTrackId.toString() &&
                (outgoingTrackId == null || plan.currentId == outgoingTrackId.toString())
        }
        val stagedPair = pendingTransitionPair
        val stagedHintValid = stagedPair != null && targetTrackId != null &&
            stagedPair.second == targetTrackId &&
            (outgoingTrackId == null || stagedPair.first == outgoingTrackId)
        val basePlaybackParams: PlaybackParameters
        val isAdopted: Boolean
        val effectiveTempoRatio = if (djTempoRatio in 0.90f..1.10f && djTempoRatio != 1.0f) {
            djTempoRatio
        } else {
            effectivePlan?.tempoRatio ?: 1.0f
        }
        val synchronizedStartMs = (startPositionMs + (djPhaseOffsetMs * effectiveTempoRatio).toLong()).coerceAtLeast(0L)

        if (pb != null && pb.player == newPlayer && targetTrackId != null && pb.trackId == targetTrackId) {
            isAdopted = true
            basePlaybackParams = pb.basePlaybackParams
            prebuffered = null
            aiDetectionProcessors.forEach { it.resetForNewTrack() }
            val resolvedUri = newPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
                ?: mediaItem.localConfiguration?.uri?.toString()
            AiDetectionManager.onTrackChanged(targetTrackId, currentTrack ?: preloadedTrack, appContext, resolvedUri)
            val djDrivesStartPosition = djFlowActive && (djPhaseOffsetMs != 0L || effectiveTempoRatio != 1.0f)
            if (djFlowActive && effectiveTempoRatio != 1.0f && !bitPerfectBlocksResample) {
                val adjSpeed = (basePlaybackParams.speed * effectiveTempoRatio).coerceIn(0.85f, 1.15f)
                newPlayer.playbackParameters = PlaybackParameters(adjSpeed, basePlaybackParams.pitch)
            }
            if (djDrivesStartPosition) {
                newPlayer.seekTo(synchronizedStartMs)
            } else if (kotlin.math.abs(newPlayer.currentPosition - startPositionMs) > 100L) {
                newPlayer.seekTo(startPositionMs)
            }
            newPlayer.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }
                ?.getTrackFormat(0)?.let { fmt ->
                    currentAudioFormatFlow.value = fmt
                    UsbAudioManager.onPlaybackFormatChanged(fmt.sampleRate, fmt.channelCount, fmt.pcmEncoding)
                }
        } else {
            isAdopted = false
            releasePrebuffered()
            newPlayer.playWhenReady = playWhenReadyRequested
            newPlayer.setMediaItem(mediaItem, synchronizedStartMs)
            val base = try { newPlayer.playbackParameters } catch (_: Exception) { PlaybackParameters.DEFAULT }
            basePlaybackParams = base
            if (djFlowActive && effectiveTempoRatio != 1.0f && !bitPerfectBlocksResample) {
                val adjSpeed = (base.speed * effectiveTempoRatio).coerceIn(0.85f, 1.15f)
                newPlayer.playbackParameters = PlaybackParameters(adjSpeed, base.pitch)
            } else if (effectivePlan != null && !bitPerfectBlocksResample &&
                (effectivePlan.tempoRatio != 1f || effectivePlan.pitchRatio != 1f)
            ) {
                val adjSpeed = (base.speed * effectivePlan.tempoRatio).coerceIn(0.5f, 2.0f)
                val adjPitch = (base.pitch * effectivePlan.pitchRatio).coerceIn(0.5f, 2.0f)
                newPlayer.playbackParameters = PlaybackParameters(adjSpeed, adjPitch)
            }
            newPlayer.prepare()
        }

        val targetVolume = 1f
        newPlayer.volume = 0f

        if (djFlowActive) {
            setPlayerStemLevels(oldPlayerIndex, vocal = 1.0f, drum = 1.0f, bass = 1.0f)
            setPlayerStemLevels(newPlayerIndex, vocal = 1.0f, drum = 1.0f, bass = 0.0f)
        }

        requestAudioFocus()
        val shouldPlayInitial = playWhenReadyRequested
        newPlayer.playWhenReady = shouldPlayInitial
        if (shouldPlayInitial) {
            newPlayer.play()
        } else {
            newPlayer.pause()
        }

        if (effectivePlan != null) {
            com.alananasss.kittytune.audio.automix.AutomixManager.setIsAutomixing(true)
        }

        val outDuck = automixDuckProcessors.getOrNull(oldPlayerIndex)
        val inDuck = automixDuckProcessors.getOrNull(newPlayerIndex)
        val outSweep = mixSweepProcessors.getOrNull(oldPlayerIndex)
        val inSweep = mixSweepProcessors.getOrNull(newPlayerIndex)

        val preset: com.alananasss.kittytune.audio.automix.MixTransitionPresets.MixTransitionStyle? =
            transitionStyle
                ?: effectivePlan?.transitionStyle
                ?: pendingTransitionStyle.takeIf { stagedHintValid }
        pendingTransitionStyle = null
        pendingTransitionPair = null
        pendingPairPlan = null

        val presetBpm = com.alananasss.kittytune.audio.automix.AutomixManager.automixDebugInfo.value?.outBpm ?: 0f

        outSweep?.resetSweep()
        inSweep?.resetSweep()
        outDuck?.resetGain()
        inDuck?.resetGain()

        var stemBassOnOut = true
        var stemBassOnIn = false
        val heldLimiterJobs = mutableListOf<Job>()
        val heldLimiters = mutableListOf<SafetyLimiterProcessor>()

        val playStateSyncListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isCrossfadingOut && fadingPlayer != null) {
                    try {
                        if (isPlaying) {
                            if (fadingPlayer?.isPlaying == false && fadingPlayer?.playbackState == Player.STATE_READY) {
                                fadingPlayer?.play()
                            }
                        } else if (!newPlayer.playWhenReady) {
                            fadingPlayer?.pause()
                        }
                    } catch (_: Exception) {}
                }
                updateRainPlayback()
            }
        }
        newPlayer.addListener(playStateSyncListener)

        fun equalPowerIn(edge0: Float, edge1: Float, x: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
            return kotlin.math.sin(t * (Math.PI / 2.0).toFloat())
        }
        fun equalPowerOut(edge0: Float, edge1: Float, x: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
            return kotlin.math.cos(t * (Math.PI / 2.0).toFloat())
        }

        scope.launch {
            try {
                if (!isAdopted) {
                    var waitCount = 0
                    while (newPlayer.playbackState == Player.STATE_BUFFERING && waitCount < 50) {
                        delay(100)
                        waitCount++
                    }
                }

                val phaseLock = com.alananasss.kittytune.audio.automix.BeatPhaseLock

                var effOutgoingGrid = outgoingGrid
                var effIncomingGrid = incomingGrid
                if (effectivePlan != null && (effOutgoingGrid == null || effIncomingGrid == null)) {
                    runCatching {
                        val ctx = appContext ?: return@runCatching
                        val dao = com.alananasss.kittytune.data.local.AppDatabase.getDatabase(ctx).beatInfoDao()
                        val (outB, inB) = withContext(Dispatchers.IO) {
                            val b1 = if (effOutgoingGrid == null) dao.getBeatInfo(effectivePlan.currentId) else null
                            val b2 = if (effIncomingGrid == null) dao.getBeatInfo(effectivePlan.nextId) else null
                            b1 to b2
                        }
                        if (effOutgoingGrid == null && outB != null && outB.bpm > 0f && outB.confidence >= 0.3f) {
                            effOutgoingGrid = com.alananasss.kittytune.audio.automix.BeatPhaseLock.Grid.of(outB.trustedAnchorMs, outB.bpm)
                        }
                        if (effIncomingGrid == null && inB != null && inB.bpm > 0f && inB.confidence >= 0.3f) {
                            effIncomingGrid = com.alananasss.kittytune.audio.automix.BeatPhaseLock.Grid.of(inB.trustedAnchorMs, inB.bpm)
                        }
                    }
                }
                val lockBaseSpeed = try {
                    newPlayer.playbackParameters.speed
                } catch (_: Exception) {
                    1f
                }
                val outGrid = effOutgoingGrid
                val inGrid = effIncomingGrid
                val canPhaseLock = (djFlowActive || effectivePlan != null) &&
                    outGrid != null && inGrid != null &&
                    outGrid.isUsable && inGrid.isUsable

                fun measurePhaseErrorMs(): Double {
                    val out = outGrid ?: return 0.0
                    val inc = inGrid ?: return 0.0
                    return try {
                        val outSpeed = oldPlayer.playbackParameters.speed.takeIf { it > 0.01f } ?: 1f
                        phaseLock.phaseErrorMs(
                            outgoing = out.deckAt(oldPlayer.currentPosition, outSpeed),
                            incoming = inc.deckAt(newPlayer.currentPosition, lockBaseSpeed),
                        )
                    } catch (_: Exception) {
                        0.0
                    }
                }

                if (canPhaseLock) {
                    val rendering = awaitRendering(newPlayer, PHASE_LOCK_READY_TIMEOUT_MS)
                    var attempt = 0
                    if (rendering) {
                        while (attempt < PHASE_LOCK_MAX_SEEKS && isActive && transitionSeq == myTransition) {
                            val errorMs = measurePhaseErrorMs()
                            if (!phaseLock.needsSeek(errorMs)) break
                            val inc = inGrid ?: break
                            try {
                                val delta = phaseLock.correctionSeekMs(
                                    errorMs,
                                    inc.deckAt(newPlayer.currentPosition, lockBaseSpeed),
                                )
                                newPlayer.seekTo((newPlayer.currentPosition + delta).coerceAtLeast(0L))
                            } catch (_: Exception) {
                                break
                            }
                            attempt++
                            if (!awaitRendering(newPlayer, PHASE_LOCK_SETTLE_TIMEOUT_MS)) break
                        }
                    }
                }

                val transitionDuration = effectivePlan?.overlapMs
                    ?: pendingTransitionDurationMs.takeIf { stagedHintValid }
                    ?: crossfadeDurationMs
                pendingTransitionDurationMs = null
                pendingTransitionPair = null
                pendingPairPlan = null

                val hardSwitchOnly = com.alananasss.kittytune.audio.AudioRoutingMatrix.isBitPerfectActive()
                val oldDuration = oldPlayer.duration.takeIf { it > 0L && it != C.TIME_UNSET }
                    ?: lastPlayer?.let { it.duration.takeIf { d -> d > 0L && d != C.TIME_UNSET } }
                    ?: currentTrack?.actualDurationMs
                    ?: 0L
                val oldPos = oldPlayer.currentPosition.coerceAtLeast(0L)
                val remainingMs = if (oldDuration > 0L) (oldDuration - oldPos).coerceAtLeast(0L) else transitionDuration

                val actualCrossfadeMs = if (hardSwitchOnly) {
                    0L
                } else if (oldPlayer.isPlaying && remainingMs > 0 && remainingMs < transitionDuration) {
                    remainingMs
                } else if (!oldPlayer.isPlaying || (oldDuration > 0L && remainingMs == 0L)) {
                    0L
                } else {
                    transitionDuration
                }

                if (actualCrossfadeMs <= 0L) {
                    // CRITICAL FIX: mute and clear the old deck first, freeing
                    // the exclusive Bit-Perfect port for the new deck!
                    oldPlayer.playWhenReady = false
                    oldPlayer.stop()
                    oldPlayer.clearMediaItems()

                    newPlayer.volume = targetVolume
                    val shouldPlayNow = playWhenReadyRequested && newPlayer.playWhenReady
                    newPlayer.playWhenReady = shouldPlayNow
                    if (shouldPlayNow) {
                        newPlayer.play()
                    } else {
                        newPlayer.pause()
                    }
                } else {
                    val steps = (actualCrossfadeMs / 15L).toInt().coerceIn(50, 800)
                    val delayMs = (actualCrossfadeMs / steps).coerceAtLeast(5L)

                    val presetBeats = if (presetBpm > 0f) {
                        ((actualCrossfadeMs / 60_000f) * presetBpm).roundToInt().coerceAtLeast(1)
                    } else preset?.defaultPhraseBeats ?: 16

                    val fadeStartMs = oldPlayer.currentPosition
                    val fadeMidMs = fadeStartMs + actualCrossfadeMs / 2
                    val swapWindowStart = fadeStartMs + (actualCrossfadeMs * 15) / 100
                    val swapWindowEnd = fadeStartMs + (actualCrossfadeMs * 85) / 100
                    var swapGrid = "midpoint"
                    val bassSwapAtMs: Long = if (canPhaseLock) {
                        val grid = outGrid
                        if (grid == null) {
                            swapGrid = "midpoint (no beat grid)"
                            fadeMidMs
                        } else {
                            val phrase = grid.nearestLineWithin(
                                fadeMidMs, com.alananasss.kittytune.audio.automix.BeatAnalyzer.BEATS_PER_PHRASE, swapWindowStart, swapWindowEnd
                            )
                            val bar = phrase ?: grid.nearestLineWithin(
                                fadeMidMs, com.alananasss.kittytune.audio.automix.BeatAnalyzer.BEATS_PER_BAR, swapWindowStart, swapWindowEnd
                            )
                            val beat = bar ?: grid.nearestBeat(fadeMidMs)
                            swapGrid = when {
                                phrase != null -> "phrase"
                                bar != null -> "bar"
                                beat != null -> "beat"
                                else -> "midpoint (no line fits the fade)"
                            }
                            beat ?: fadeMidMs
                        }
                    } else {
                        swapGrid = "midpoint (no beat grid)"
                        fadeMidMs
                    }
                    var bassSwapped = false

                    for (i in 0..steps) {
                        if (transitionSeq != myTransition) break
                        if (bitPerfectBlocksResample) break
                        if (!isActive) break

                        var stalledMs = 0L
                        var pausedStalledMs = 0L
                        val maxPausedStallMs = 30_000L
                        while (!newPlayer.isPlaying && isActive && transitionSeq == myTransition) {
                            if (newPlayer.playWhenReady) {
                                if (stalledMs >= CROSSFADE_STALL_TIMEOUT_MS) break
                                stalledMs += 100
                            } else {
                                if (pausedStalledMs >= maxPausedStallMs) break
                                pausedStalledMs += 100
                            }
                            delay(100)
                        }
                        if (transitionSeq != myTransition) break

                        if (djFlowActive && isLoopExtensionEnabled &&
                            oldPlayer.isPlaying && oldPlayer.duration > 4000L &&
                            oldPlayer.currentPosition >= oldPlayer.duration - 400L
                        ) {
                            val loopBack = (oldPlayer.duration - 4000L).coerceAtLeast(0L)
                            try { oldPlayer.seekTo(loopBack) } catch (_: Exception) {}
                        }

                        if (oldPlayer.playbackState == Player.STATE_ENDED || oldPlayer.playbackState == Player.STATE_IDLE) {
                            newPlayer.volume = targetVolume
                            break
                        }

                        val progress = i.toFloat() / steps
                        com.alananasss.kittytune.audio.automix.AutomixManager.setMixProgress(progress)
                        val fadeOut: Float
                        val fadeIn: Float

                        val planFrame = effectivePlan?.exactCurves?.let { exact ->
                            com.alananasss.kittytune.audio.automix.planFrameAt(
                                exact, progress, presetBeats, presetBpm,
                            )
                        }

                        if (planFrame != null) {
                            fadeOut = planFrame.fadeOut
                            fadeIn = planFrame.fadeIn

                            outDuck?.setMix(planFrame.bassDuckOut)
                            inDuck?.setMix(planFrame.bassDuckIn)

                            outSweep?.setMode(
                                planFrame.sweepModeOut
                                    ?: com.alananasss.kittytune.audio.automix.MixSweepAudioProcessor.FilterMode.BYPASS
                            )
                            inSweep?.setMode(
                                planFrame.sweepModeIn
                                    ?: com.alananasss.kittytune.audio.automix.MixSweepAudioProcessor.FilterMode.BYPASS
                            )
                            planFrame.sweepHz?.let { outSweep?.setSweep(it, planFrame.sweepQ) }
                            planFrame.sweepHzIn?.let { inSweep?.setSweep(it, planFrame.sweepQ) }

                            if (planFrame.bassSwapOut != stemBassOnOut) {
                                stemBassOnOut = planFrame.bassSwapOut
                                setPlayerStemLevels(oldPlayerIndex, vocal = 1f, drum = 1f, bass = if (stemBassOnOut) 1f else 0f)
                            }
                            if (planFrame.bassSwapIn != stemBassOnIn) {
                                stemBassOnIn = planFrame.bassSwapIn
                                setPlayerStemLevels(newPlayerIndex, vocal = 1f, drum = 1f, bass = if (stemBassOnIn) 1f else 0f)
                            }
                        } else if (preset != null) {
                            val frame = com.alananasss.kittytune.audio.automix.MixTransitionPresets.frame(
                                preset, progress, presetBeats, presetBpm,
                            )
                            fadeOut = frame.fadeOut
                            fadeIn = frame.fadeIn

                            outDuck?.setMix(frame.bassDuckOut)
                            inDuck?.setMix(frame.bassDuckIn)

                            when (preset) {
                                com.alananasss.kittytune.audio.automix.MixTransitionPresets.MixTransitionStyle.RISE -> {
                                    outSweep?.setMode(com.alananasss.kittytune.audio.automix.MixSweepAudioProcessor.FilterMode.HIGH_PASS)
                                    frame.sweepHz?.let { outSweep?.setSweep(it, frame.sweepQ) }
                                    inSweep?.setMode(com.alananasss.kittytune.audio.automix.MixSweepAudioProcessor.FilterMode.BYPASS)
                                }
                                com.alananasss.kittytune.audio.automix.MixTransitionPresets.MixTransitionStyle.WAVE -> {
                                    outSweep?.setMode(com.alananasss.kittytune.audio.automix.MixSweepAudioProcessor.FilterMode.BAND_PASS)
                                    inSweep?.setMode(com.alananasss.kittytune.audio.automix.MixSweepAudioProcessor.FilterMode.BAND_PASS)
                                    frame.sweepHz?.let { outSweep?.setSweep(it, frame.sweepQ) }
                                    frame.sweepHzIn?.let { inSweep?.setSweep(it, frame.sweepQ) }
                                }
                                else -> {
                                    outSweep?.setMode(com.alananasss.kittytune.audio.automix.MixSweepAudioProcessor.FilterMode.BYPASS)
                                    inSweep?.setMode(com.alananasss.kittytune.audio.automix.MixSweepAudioProcessor.FilterMode.BYPASS)
                                }
                            }

                            if (frame.speedOut != 1f) {
                                try {
                                    // PlaybackParameters requires speed/pitch > 0 (Melt's curve
                                    // ends at exactly 0, which used to throw and be swallowed).
                                    // The deck is already faded out at that point.
                                    val s = frame.speedOut.coerceAtLeast(MIN_MELT_SPEED)
                                    oldPlayer.playbackParameters = PlaybackParameters(s, s)
                                } catch (_: Exception) {}
                            }

                            if (frame.bassSwapOut != stemBassOnOut) {
                                stemBassOnOut = frame.bassSwapOut
                                setPlayerStemLevels(oldPlayerIndex, vocal = 1f, drum = 1f, bass = if (stemBassOnOut) 1f else 0f)
                            }
                            if (frame.bassSwapIn != stemBassOnIn) {
                                stemBassOnIn = frame.bassSwapIn
                                setPlayerStemLevels(newPlayerIndex, vocal = 1f, drum = 1f, bass = if (stemBassOnIn) 1f else 0f)
                            }

                            if (frame.limiterHoldMs > 0) {
                                for (deckIndex in intArrayOf(oldPlayerIndex, newPlayerIndex)) {
                                    val limiter = safetyLimiterProcessors.getOrNull(deckIndex) ?: continue
                                    if (limiter.isLimiterEngaged()) continue
                                    limiter.setEnabled(true)
                                    if (!heldLimiters.contains(limiter)) {
                                        heldLimiters.add(limiter)
                                        heldLimiterJobs.add(scope.launch {
                                            delay(frame.limiterHoldMs.toLong())
                                            limiter.setEnabled(false)
                                        })
                                    }
                                }
                            }
                        } else if (isDjConstantEnergy) {
                            fadeOut = if (progress < 0.4f) 1.0f else equalPowerOut(0.4f, 1f, progress)
                            fadeIn = if (progress > 0.6f) 1.0f else equalPowerIn(0f, 0.6f, progress)
                        } else if (effectivePlan != null) {
                            fadeOut = equalPowerOut(0f, 0.6f, progress)
                            fadeIn = equalPowerIn(0.4f, 1f, progress)
                        } else {
                            fadeOut = kotlin.math.cos(progress * (Math.PI / 2.0).toFloat())
                            fadeIn = kotlin.math.sin(progress * (Math.PI / 2.0).toFloat())
                            outDuck?.setMix(equalPowerIn(0.45f, 1f, progress))
                            inDuck?.setMix(1f - equalPowerIn(0f, 0.55f, progress))
                        }

                        newPlayer.volume = targetVolume * fadeIn
                        oldPlayer.volume = targetVolume * fadeOut

                        if (canPhaseLock && i % DRIFT_CHECK_STEPS == 0 && i > 0) {
                            val driftMs = measurePhaseErrorMs()
                            if (!phaseLock.isLocked(driftMs)) {
                                try {
                                    val nudged = phaseLock.nudgeSpeed(
                                        errorMs = driftMs,
                                        windowMs = delayMs * DRIFT_CHECK_STEPS,
                                        baseSpeed = lockBaseSpeed,
                                        maxNudge = phaseLock.nudgeAuthority(fadeIn),
                                    )
                                    newPlayer.playbackParameters =
                                        PlaybackParameters(nudged, newPlayer.playbackParameters.pitch)
                                } catch (_: Exception) {}
                            } else if (newPlayer.playbackParameters.speed != lockBaseSpeed) {
                                try {
                                    newPlayer.playbackParameters =
                                        PlaybackParameters(lockBaseSpeed, newPlayer.playbackParameters.pitch)
                                } catch (_: Exception) {}
                            }
                        }

                        if (preset == null) {
                            if (isDjConstantEnergy) {
                                outDuck?.setMix(0f)
                                inDuck?.setMix(0f)

                                if (!bassSwapped && oldPlayer.currentPosition >= bassSwapAtMs) {
                                    bassSwapped = true
                                }

                                if (!bassSwapped) {
                                    setPlayerStemLevels(oldPlayerIndex, vocal = 1.0f, drum = 1.0f, bass = 1.0f)
                                    setPlayerStemLevels(newPlayerIndex, vocal = 1.0f, drum = 1.0f, bass = 0.0f)
                                } else {
                                    setPlayerStemLevels(oldPlayerIndex, vocal = 1.0f, drum = 1.0f, bass = 0.0f)
                                    setPlayerStemLevels(newPlayerIndex, vocal = 1.0f, drum = 1.0f, bass = 1.0f)
                                }
                            } else if (effectivePlan != null) {
                                outDuck?.setMix(equalPowerIn(0.45f, 1f, progress))
                                inDuck?.setMix(1f - equalPowerIn(0f, 0.55f, progress))
                            }
                        }

                        delay(delayMs)
                    }
                }
            } finally {
                newPlayer.removeListener(playStateSyncListener)
                val stillOwnsTransition = transitionSeq == myTransition
                try {
                    if (stillOwnsTransition) {
                        newPlayer.volume = targetVolume
                        val shouldPlayEnd = playWhenReadyRequested && newPlayer.playWhenReady
                        newPlayer.playWhenReady = shouldPlayEnd
                        if (shouldPlayEnd) {
                            newPlayer.play()
                        } else {
                            newPlayer.pause()
                        }
                        oldPlayer.volume = 0f
                        oldPlayer.playWhenReady = false
                        oldPlayer.stop()
                        oldPlayer.clearMediaItems()
                    }
                } catch (_: Exception) {}

                try {
                    if (stillOwnsTransition && oldPlayer.playbackParameters != basePlaybackParams) {
                        oldPlayer.playbackParameters = basePlaybackParams
                    }
                } catch (_: Exception) {}

                val wasActiveTransition = activeTransitionIds.remove(myTransition)
                if (wasActiveTransition) {
                    liveTransitions--
                }
                if (liveTransitions <= 0) {
                    liveTransitions = 0
                    fadingPlayer = null
                    isCrossfadingOut = false
                    outDuck?.resetGain()
                    inDuck?.resetGain()
                    outSweep?.resetSweep()
                    inSweep?.resetSweep()
                    heldLimiterJobs.forEach { it.cancel() }
                    heldLimiters.forEach { runCatching { it.setEnabled(false) } }
                    heldLimiters.clear()
                    setGlobalStemLevels(1.0f, 1.0f, 1.0f)
                    com.alananasss.kittytune.audio.automix.AutomixManager.setIsAutomixing(false)
                    com.alananasss.kittytune.audio.automix.AutomixManager.clearPlan()
                    com.alananasss.kittytune.audio.automix.AutomixManager.setMixProgress(0f)
                }

                if (stillOwnsTransition) {
                    if (bitPerfectBlocksResample) {
                        try {
                            newPlayer.playbackParameters = PlaybackParameters(1f, 1f)
                        } catch (_: Exception) {}
                    } else {
                        val currentParams = newPlayer.playbackParameters
                        if (currentParams != basePlaybackParams) {
                            scope.launch {
                                val releaseSchedule =
                                    if (effectivePlan?.tempoSyncEnabled == true) {
                                        com.alananasss.kittytune.audio.automix.MixTransitionPresets.tempoReleaseSchedule(
                                            fromSpeed = currentParams.speed,
                                            toSpeed = basePlaybackParams.speed,
                                            bpm = presetBpm,
                                        )
                                    } else emptyList()
                                if (releaseSchedule.isNotEmpty()) {
                                    var previousAtMs = 0L
                                    val startPitch = currentParams.pitch
                                    val endPitch = basePlaybackParams.pitch
                                    val lastIndex = (releaseSchedule.size - 1).coerceAtLeast(1)
                                    for ((index, step) in releaseSchedule.withIndex()) {
                                        delay((step.atMs - previousAtMs).coerceAtLeast(0L))
                                        previousAtMs = step.atMs
                                        if (!isActive || transitionSeq != myTransition) break
                                        // Walk the harmonic pitch correction home together with the
                                        // tempo, otherwise the rest of the track stays transposed.
                                        val frac = if (releaseSchedule.size == 1) 1f else index.toFloat() / lastIndex
                                        val pitch = startPitch + frac * (endPitch - startPitch)
                                        try {
                                            newPlayer.playbackParameters = PlaybackParameters(step.speed, pitch)
                                        } catch (_: Exception) { break }
                                    }
                                } else {
                                    val rampSteps = 10
                                    val startSpeed = currentParams.speed
                                    val endSpeed = basePlaybackParams.speed
                                    val startPitch = currentParams.pitch
                                    val endPitch = basePlaybackParams.pitch
                                    for (step in 1..rampSteps) {
                                        delay(200)
                                        if (!isActive || transitionSeq != myTransition) break
                                        val frac = step.toFloat() / rampSteps
                                        val curSpeed = startSpeed + frac * (endSpeed - startSpeed)
                                        val curPitch = startPitch + frac * (endPitch - startPitch)
                                        try {
                                            newPlayer.playbackParameters = PlaybackParameters(curSpeed, curPitch)
                                        } catch (_: Exception) { break }
                                    }
                                }
                            }
                        }
                    }
                    fadingPlayer = null
                    activeCrossfadeIncoming = null
                    activeCrossfadeOutgoing = null
                }
            }
        }
    }

    fun preloadNext(nextTrack: Track, context: Context) {
        if (bitPerfectBlocksResample) return // Protects the exclusive DAC from a second deck
        preloadedTrack = nextTrack
        val inactivePlayer = if (activePlayerIndex == 1) getOrInitPlayer2() else _player1!!
        inactivePlayer.playWhenReady = false
        inactivePlayer.stop()
        inactivePlayer.clearMediaItems()

        val uri = android.net.Uri.parse("soundtune://track/${nextTrack.id}")
        val artist = nextTrack.displayArtist.ifBlank { nextTrack.user?.username ?: "Unknown" }
        val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(nextTrack.title ?: "Unknown")
            .setArtist(artist)
            .setSubtitle(artist)
            .setIsPlayable(true)
            .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC)
            .setArtworkUri(if (nextTrack.artworkUrl != null) android.net.Uri.parse(nextTrack.artworkUrl) else null)
            .apply {
                nextTrack.publisherMetadata?.albumTitle?.takeIf { it.isNotBlank() }?.let { setAlbumTitle(it) }
            }
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
        lastEffectsState = state
        if (isTransitionRunning()) {
            // While a transition is running, don't disturb the deck parameters
        } else if (bitPerfectBlocksResample) {
            try {
                _player1?.playbackParameters = PlaybackParameters(1f, 1f)
                _player2?.playbackParameters = PlaybackParameters(1f, 1f)
            } catch (_: Exception) {}
        } else {
            val pitch = if (state.isPitchEnabled) state.speed else 1f
            _player1?.playbackParameters = PlaybackParameters(state.speed, pitch)
            _player2?.playbackParameters = PlaybackParameters(state.speed, pitch)
        }

        appContext?.let { ctx ->
            val hapticMgr = com.alananasss.kittytune.audio.haptics.PlayerHapticManager.getInstance(ctx)
            hapticMgr.playbackSpeedFactor = state.speed
            hapticMgr.bassBoostMultiplier = if (state.isBassBoostEnabled) (1.0f + state.bassBoostIntensity * 0.8f) else 1.0f
        }

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
        safetyLimiterProcessors.forEach {
            it.setEnabled(state.isNormalizationEnabled)
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
        updateRainPlayback()
    }

    fun updateRainPlayback() {
        val action = {
            val isMusicPlaying = try {
                val p1 = _player1
                val p2 = _player2
                val fp = fadingPlayer
                (p1?.isPlaying == true) || (p2?.isPlaying == true) || (fp?.isPlaying == true)
            } catch (_: Exception) {
                false
            }
            rainPlayer?.setMusicPlaying(isMusicPlaying)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            scope.launch { action() }
        }
    }

    fun applyEqualizer(state: EqualizerState) {
        val gains = state.bandGainsDb.toFloatArray()
        equalizerProcessors.forEach {
            it.setParameters(state.isEnabled, state.preampDb, gains)
        }
    }

    fun setGlobalStemLevels(vocal: Float, drum: Float, bass: Float) {
        djStemProcessors.forEach {
            it.setStemLevels(vocal = vocal, drum = drum, bass = bass)
        }
    }

    fun setPlayerStemLevels(playerIndex: Int, vocal: Float, drum: Float, bass: Float) {
        djStemProcessors.getOrNull(playerIndex)?.setStemLevels(vocal = vocal, drum = drum, bass = bass)
    }

    fun triggerStemDropCut(outgoingPlayerIndex: Int = -1) {
        val targetIdx = if (outgoingPlayerIndex >= 0) outgoingPlayerIndex else (activePlayerIndex - 1)
        djStemProcessors.getOrNull(targetIdx)?.triggerDropBassCut()
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
