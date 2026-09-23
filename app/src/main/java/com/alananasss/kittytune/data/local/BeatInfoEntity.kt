package com.alananasss.kittytune.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "beat_info")
data class BeatInfoEntity(
    @PrimaryKey val songId: String,
    val bpm: Float,
    val firstBeatOffsetMs: Long,
    val confidence: Float,
    val analyzedAt: Long = System.currentTimeMillis(),
    /** Where the incoming track should start: first sustained-energy downbeat past intro. */
    val mixInPointMs: Long? = null,
    /** Where the outgoing track's body ends (outro begins); transition starts here. */
    val mixOutPointMs: Long? = null,
    /** 0=C, 1=C#, ... 11=B. Null when track's tonal chroma signal was too weak to call a key. */
    val keyPitchClass: Int? = null,
    val keyIsMinor: Boolean? = null,
    /**
     * Time of the first beat 1. [firstBeatOffsetMs] only fixes *a* beat of the grid, which may
     * be beat 2, 3 or 4 — dropping a track there destroys the groove even at a perfect BPM match.
     */
    val downbeatOffsetMs: Long = 0L,
    /** Time of the first 16-beat phrase start. All cue points are quantized against this. */
    val phraseOffsetMs: Long = 0L,
    /** 0..1 confidence that beat 1 was identified correctly. Low on beatless or ambiguous material. */
    val downbeatConfidence: Float = 0f,
    /**
     * Version of the analyzer that produced this row. Rows below [CURRENT_ANALYSIS_VERSION] are
     * re-analyzed: a cached grid from an older analyzer is not wrong so much as incomplete, and
     * silently trusting it would keep every already-played track on the old behaviour forever.
     */
    val analysisVersion: Int = 0,
) {
    /** True when this row was produced by the current analyzer and can be used as-is. */
    val isCurrentAnalysis: Boolean get() = analysisVersion >= CURRENT_ANALYSIS_VERSION

    companion object {
        /** Bump whenever the analyzer's output changes meaningfully, to force re-analysis. */
        const val CURRENT_ANALYSIS_VERSION = 1
    }
}

/**
 * The anchor every cue point must be quantized against: a real phrase start when the downbeat
 * pass produced one, otherwise the plain beat grid so rows analyzed before the downbeat tracker
 * existed still mix — just without phrase alignment.
 */
val BeatInfoEntity.phraseAnchorMs: Long
    get() = if (phraseOffsetMs > 0L || downbeatConfidence > 0f) phraseOffsetMs else firstBeatOffsetMs

/** The anchor for bar-level (4-beat) quantization, with the same legacy fallback. */
val BeatInfoEntity.downbeatAnchorMs: Long
    get() = if (downbeatOffsetMs > 0L || downbeatConfidence > 0f) downbeatOffsetMs else firstBeatOffsetMs
