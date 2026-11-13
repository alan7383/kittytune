package com.alananasss.kittytune.audio.automix

/**
 * Transition plan computed from beat, tempo, and key analysis of outgoing and incoming tracks.
 */
data class AutomixPlan(
    val currentId: String,
    val nextId: String,
    /** Position in the outgoing track (ms) where the crossfade transition should trigger. */
    val triggerTimeMs: Long,
    /** Position in the incoming track (ms) where playback begins (first downbeat / intro skipped). */
    val incomingStartMs: Long,
    /** Pitch-preserving tempo ratio to stretch the incoming track to match outgoing tempo. */
    val tempoRatio: Float = 1f,
    /** Harmonic pitch shift factor to align musical keys (minimal circular semitone shift). */
    val pitchRatio: Float = 1f,
    /** Overlap duration (ms) for the DJ crossfade blend (e.g. 16 beats / 4 bars). */
    val overlapMs: Long,
    // How the two tracks meet, one of the Spotify-Mix presets. The engine-level enum, resolved
    // by [com.alananasss.kittytune.audio.automix.MixTransitionPresets.autoSelect] (or taken from
    // the user's Mix panel override) at plan time, so the fade loop never has to decide anything
    // mid-transition.
    val transitionStyle: MixTransitionPresets.MixTransitionStyle =
        MixTransitionPresets.DEFAULT_STYLE,
    // Whether the incoming deck is tempo-stretched (Sonic via playbackParameters.speed) for the
    // blend and released back to its own tempo afterwards. Off when the pair is too far apart
    // to stretch musically (the plan's tempoRatio is 1 anyway).
    val tempoSyncEnabled: Boolean = false,
    // The pair's hand-saved transition plan, present when the seam was shaped in the
    // Edit-transition editor as Custom - the fade loop then drives its envelopes from the
    // plan's exact curves ([planFrameAt]) instead of the engine style's approximation.
    // Null = the engine style owns the shape.
    val exactCurves: TransitionPlan? = null,
)

data class AutomixDebugInfo(
    val status: String,
    val outBpm: Float? = null,
    val outConfidence: Float? = null,
    val outMixOutMs: Long? = null,
    val inBpm: Float? = null,
    val inConfidence: Float? = null,
    val inMixInMs: Long? = null,
    val triggerTimeMs: Long? = null,
    val incomingStartMs: Long? = null,
    val tempoRatio: Float? = null,
    val pitchRatio: Float? = null,
    val overlapMs: Long? = null,
)

data class AutomixPair(
    val currentId: String,
    val nextId: String,
)

data class AutomixPlanResult(
    val plan: AutomixPlan?,
    val pairAnalyzed: Boolean,
)

enum class BeatAnalysisPriority {
    IMMEDIATE,
    LOOKAHEAD
}
