package com.alananasss.kittytune.audio

import androidx.media3.common.audio.AudioProcessor
import com.alananasss.kittytune.audio.usb.UsbAudioManager

// Every processable stage of the playback pipeline
enum class AudioStage(val label: String) {
    NORMALIZER("LUFS normalization"),
    SAFETY_LIMITER("Safety limiter"),
    EQUALIZER("Equalizer"),
    AUTOMIX_DUCK("Automix · duck"),
    DJ_STEM("Automix · DJ stems"),
    MIX_SWEEP("Automix · volume sweep"),
    TRANSITION_SWEEP("Automix · transition sweep"),
    TRANSITION_EFFECTS("Automix · transition FX"),
    SILENCE_SKIP("Silence skip"),
    AI_DETECTION("AI-detection tap"),
    HAPTICS("Haptics tap"),
    LIVE_BEAT("Live-beat tap"),
    PLAYER_FX("Player FX (20+ effects)"),
}

// Playback modes that have a routing row
enum class AudioMode(val label: String) {
    STANDARD("Standard"),
    BIT_PERFECT("Bit-Perfect (Audiophile)"),
}

// AudioRoutingMatrix is the single source of truth for audio DSP routing, replacing ~26 scattered
// if-checks across the codebase that risked leaking audio processing into Bit-Perfect mode. 
// All stages now request permission centrally. In Bit-Perfect mode, the routed stage
// set is strictly empty: the stream reaches the DAC untouched, an invariant enforced by unit tests

object AudioRoutingMatrix {

    // Injection seam for unit tests; production reads the live USB DAC state
    internal var modeProvider: () -> AudioMode = {
        if (UsbAudioManager.shouldBypassPcmEffects) AudioMode.BIT_PERFECT
        else AudioMode.STANDARD
    }

    // THE MATRIX: mode -> stages allowed into the signal path
    private val matrix: Map<AudioMode, Set<AudioStage>> = mapOf(
        AudioMode.STANDARD to AudioStage.entries.toSet(),
        AudioMode.BIT_PERFECT to emptySet(), // the DAC owns the output
    )

    // Stages the Audiophile mode must NEVER route; locked by unit test 
    val BIT_PERFECT_INVARIANTS: Set<AudioStage> = setOf(
        AudioStage.EQUALIZER, AudioStage.SAFETY_LIMITER, AudioStage.NORMALIZER,
        AudioStage.AUTOMIX_DUCK, AudioStage.DJ_STEM, AudioStage.MIX_SWEEP,
        AudioStage.TRANSITION_SWEEP, AudioStage.TRANSITION_EFFECTS,
    )

    fun currentMode(): AudioMode = modeProvider()

    fun isBitPerfectActive(): Boolean = currentMode() == AudioMode.BIT_PERFECT

    // The only question a processor is allowed to ask 
    fun isRouted(stage: AudioStage): Boolean = matrix.getValue(currentMode()).contains(stage)

    fun routedStages(mode: AudioMode): Set<AudioStage> = matrix.getValue(mode)

    // Live table for the diagnostics sheet 
    fun snapshot(): List<Pair<AudioStage, Boolean>> =
        AudioStage.entries.map { it to isRouted(it) }

    // class registry: audit helpers for the chain and diagnostics

    private val classStages: Map<Class<*>, AudioStage> = mapOf(
        com.alananasss.kittytune.audio.normalization.TrackNormalizerProcessor::class.java to AudioStage.NORMALIZER,
        com.alananasss.kittytune.audio.normalization.SafetyLimiterProcessor::class.java to AudioStage.SAFETY_LIMITER,
        com.alananasss.kittytune.ui.player.audio.EqualizerAudioProcessor::class.java to AudioStage.EQUALIZER,
        com.alananasss.kittytune.audio.automix.AutomixDuckAudioProcessor::class.java to AudioStage.AUTOMIX_DUCK,
        com.alananasss.kittytune.audio.automix.DjStemAudioProcessor::class.java to AudioStage.DJ_STEM,
        com.alananasss.kittytune.audio.automix.MixSweepAudioProcessor::class.java to AudioStage.MIX_SWEEP,
        com.alananasss.kittytune.audio.automix.TransitionSweepAudioProcessor::class.java to AudioStage.TRANSITION_SWEEP,
        com.alananasss.kittytune.audio.automix.TransitionEffectsAudioProcessor::class.java to AudioStage.TRANSITION_EFFECTS,
        com.alananasss.kittytune.audio.silence.SilenceDetectorAudioProcessor::class.java to AudioStage.SILENCE_SKIP,
        com.alananasss.kittytune.audio.ai.AiDetectionAudioProcessor::class.java to AudioStage.AI_DETECTION,
        com.alananasss.kittytune.audio.haptics.HapticAudioProcessor::class.java to AudioStage.HAPTICS,
        com.alananasss.kittytune.audio.beat.LiveBeatAudioProcessor::class.java to AudioStage.LIVE_BEAT,
    )

    fun stageOf(processor: AudioProcessor): AudioStage? = classStages[processor.javaClass]
}
