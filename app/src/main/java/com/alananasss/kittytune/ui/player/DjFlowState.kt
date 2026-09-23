/**
 * Developed by Jason-Marshall Fastner, Germany <jasonfastner@protonmail.com>
 * Questions, feedback, or beat-matching debates? Feel free to reach out via email!
 * 
 * Note: Cats always land on their feet, and with this engine, your transitions will too.
 */
package com.alananasss.kittytune.ui.player

import com.alananasss.kittytune.domain.Track

/**
 * Lifecycle phase of an active or pending DJ transition.
 */
enum class TransitionPhase {
    IDLE,
    PREPARING,
    CROSSFADING
}

/**
 * Dynamic DJ transition style adapted to harmonic compatibility, tempo delta, and energy flow.
 */
enum class TransitionStyle(
    val title: String,
    val defaultPhraseBeats: Int,
    val description: String
) {
    HARMONIC_CLUB_BLEND("Harmonic Club Blend", 16, "16 Beats • Sanfte Frequenzüberblendung bei harmonischer Tonart"),
    ENERGY_DROP_CUT("Energy Drop Cut", 8, "8 Beats • Dynamischer Build-Up Cut direkt auf den Drop"),
    POWER_SWAP("Downbeat Power Swap", 4, "4 Beats • Schneller Cut direkt auf Takt 1"),
    EXTENDED_FLOW("Extended 32-Beat Mix", 32, "32 Beats • Tiefe, langsame Club-Verschmelzung"),
    SMART_BASS_CROSS("Bass-Ducked Transition", 16, "16 Beats • Bass-Wechsel ohne Frequenzüberlagerung")
}

/**
 * Evaluated match result for an upcoming track candidate.
 */
data class TrackMatch(
    val track: Track,
    val score: Float,               // Overall match score (0.0f - 100.0f)
    val bpm: Float,                 // Candidate analyzed BPM
    val key: CamelotKey?,           // Candidate analyzed Camelot Key
    val bpmDelta: Float,            // BPM change (candidate - current)
    val bpmPercentDelta: Float,     // Relative percentage delta
    val harmonicDistance: Int,      // Camelot wheel distance
    val isHarmonic: Boolean,        // True when distance <= 1
    val matchDescription: String,  // Human readable match reason
    val mixInPointMs: Long? = null, // Intro cue / drop point
    val mixOutPointMs: Long? = null,// Outro cue point
    val energyLevel: Int = 5,       // Estimated energy level (1 to 10)
    val transitionStyle: TransitionStyle = TransitionStyle.HARMONIC_CLUB_BLEND
)

/**
 * Reactive state of the DJ Flow engine, exposed to UI and animation systems.
 */
data class DjFlowState(
    val isActive: Boolean = false,
    val currentBpm: Float = 0f,
    val targetBpm: Float = 0f,
    val currentKey: CamelotKey? = null,
    val targetKey: CamelotKey? = null,
    val energyMode: EnergyMode = EnergyMode.HOLD,
    val transitionPhase: TransitionPhase = TransitionPhase.IDLE,
    val transitionProgress: Float = 0.0f,
    val suggestedNextTracks: List<Track> = emptyList(),
    val suggestedMatches: List<TrackMatch> = emptyList(),
    val nextTrackMatch: TrackMatch? = null,
    val isLoopExtensionEnabled: Boolean = true,
    val loopBeats: Int = 8,
    val isLoopActive: Boolean = false,
    val loopRangeMs: Pair<Long, Long>? = null,
    val isAutonomousEnabled: Boolean = true,
    val isConstantEnergyEnabled: Boolean = true,
    val isAutoReorderEnabled: Boolean = true,
    val analyzedCandidateCount: Int = 0,
    val totalCandidateCount: Int = 0,
    val plannedTriggerTimeMs: Long? = null,
    val incomingDropPointMs: Long? = null,
    val beatsUntilTransition: Int? = null,
    val barsUntilTransition: Int? = null,
    val timeUntilTransitionMs: Long? = null,
    val transitionStyle: TransitionStyle = TransitionStyle.HARMONIC_CLUB_BLEND,
    val currentBar: Int = 1,
    val beatInBar: Int = 1,
    val beatInPhrase: Int = 1,
    val barInPhrase: Int = 1,
    val isDownbeat: Boolean = false,
    val beatProgress: Float = 0.0f,
    // Infinite DJ Stream & Category Search
    val isInfiniteStreamEnabled: Boolean = true,
    val selectedCategory: String = "Meine Likes",
    val isFetchingInfiniteCandidates: Boolean = false,
    // Real-Time DJ Stem Isolator
    val vocalStemLevel: Float = 1.0f,
    val drumStemLevel: Float = 1.0f,
    val bassStemLevel: Float = 1.0f,
    val isAutoStemDropCutEnabled: Boolean = true,
    val plannedTransitionDurationMs: Long = 8000L,
    /**
     * Everything the crossfade needs and no UI should read. Grouped rather than spread across
     * the state so it is obvious which fields are display data and which are engine plumbing.
     */
    val sync: DjSyncState = DjSyncState(),
)

/**
 * Deck synchronisation parameters, produced by the engine and consumed by the crossfade.
 *
 * Kept out of the display fields deliberately: these change shape whenever the phase-locking
 * strategy changes, and a UI built on them would break with it. Nothing here is meaningful
 * on screen.
 *
 * @param tempoRatio pitch-preserving stretch applied to the incoming deck.
 * @param phaseOffsetMs how far into the current beat the outgoing deck is.
 * @param outgoingGrid beat grid of the playing track; null until analysis lands.
 * @param incomingGrid beat grid of the track the next transition will mix in.
 */
data class DjSyncState(
    val tempoRatio: Float = 1.0f,
    val phaseOffsetMs: Long = 0L,
    val outgoingGrid: com.alananasss.kittytune.audio.automix.BeatPhaseLock.Grid? = null,
    val incomingGrid: com.alananasss.kittytune.audio.automix.BeatPhaseLock.Grid? = null,
)
