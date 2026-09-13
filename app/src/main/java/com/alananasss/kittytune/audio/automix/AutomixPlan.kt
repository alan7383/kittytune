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
