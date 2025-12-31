package com.alananasss.kittytune.data

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.ui.player.audio.EightDAudioProcessor
import com.alananasss.kittytune.ui.player.audio.FxAudioProcessor
import com.alananasss.kittytune.ui.player.audio.ReverbAudioProcessor

object MusicManager {
    private var _player: ExoPlayer? = null

    val player: ExoPlayer
        get() {
            if (_player == null) {
                throw IllegalStateException("MusicManager not initialized! Call init() first.")
            }
            return _player!!
        }

    // need to store the current track here so the service can access it
    var currentTrack: Track? = null

    private val eightDProcessor = EightDAudioProcessor()
    private val fxProcessor = FxAudioProcessor()
    private val reverbProcessor = ReverbAudioProcessor()

    var onNextClick: (() -> Unit)? = null
    var onPreviousClick: (() -> Unit)? = null

    fun init(context: Context) {
        if (_player != null) return

        _player = ExoPlayer.Builder(context)
            .setRenderersFactory(
                object : DefaultRenderersFactory(context) {
                    override fun buildAudioSink(
                        context: Context,
                        enableFloatOutput: Boolean,
                        enableAudioTrackPlaybackParams: Boolean
                    ): AudioSink {
                        // hook up our custom processors chain here
                        return DefaultAudioSink.Builder(context)
                            .setAudioProcessors(arrayOf(fxProcessor, reverbProcessor, eightDProcessor))
                            .setEnableFloatOutput(enableFloatOutput)
                            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                            .build()
                    }
                }
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    fun applyEffects(state: AudioEffectsState) {
        if (_player == null) return

        // if pitch is enabled (nightcore style), it follows speed
        val pitch = if (state.isPitchEnabled) state.speed else 1f
        _player?.playbackParameters = PlaybackParameters(state.speed, pitch)

        eightDProcessor.setEnabled(state.is8DEnabled)
        fxProcessor.setEffects(state.isMuffledEnabled, state.isBassBoostEnabled)
        reverbProcessor.setEnabled(state.isReverbEnabled)
    }
}