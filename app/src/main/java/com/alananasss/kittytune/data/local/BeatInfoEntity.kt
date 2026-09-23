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
    /** 0..1 confidence that the 16-beat phrase boundary was identified correctly. */
    val phraseConfidence: Float = 0f,
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
        /**
         * Bump whenever the analyzer's output changes meaningfully, to force re-analysis.
         *
         * 2: phrase confidence is recorded separately. Version 1 rows carry a downbeat margin
         * but no phrase margin, and treating a missing margin as "unsure" would demote every
         * one of them to bar quantization - including the ones that were confidently right.
         */
        const val CURRENT_ANALYSIS_VERSION = 2
    }
}

/**
 * How precisely this track's grid may be trusted, and therefore what a cue point is allowed to
 * claim.
 *
 * The downbeat classifier is a hand-tuned template match, not a learned model, and it knows when
 * it is unsure: across 139 analysed tracks the margin was above 0.70 on 47 % and below 0.30 on
 * 20 %. Quantizing a cue point to a 16-beat phrase on the strength of a 0.08 margin is a
 * confident answer built on a guess — it lands the drop somewhere arbitrary while looking
 * exactly as authoritative as a correct one.
 *
 * So the claim shrinks with the evidence. Each step down is still musically valid, just less
 * ambitious, and the beat grid itself is always safe because tempo confidence is measured
 * separately from downbeat confidence.
 */
enum class GridTrust(val quantizeBeats: Int) {
    /** Downbeat and phrase both solid: cue on the 16-beat phrase, where the drop sits. */
    PHRASE(16),

    /** Beat 1 is known but the phrase position is not: cue on the bar. */
    BAR(4),

    /** The downbeat itself is a guess: cue on the beat and claim nothing more. */
    BEAT(1),
}

/** Below this the bar phase is a coin flip rather than a reading. */
const val MIN_DOWNBEAT_CONFIDENCE = 0.30f

/** The phrase phase needs its own evidence; a confident downbeat does not vouch for it. */
const val MIN_PHRASE_CONFIDENCE = 0.25f

val BeatInfoEntity.gridTrust: GridTrust
    get() = when {
        // Rows analysed before confidence was recorded: treat as phrase-safe, which is what
        // they were already being used for. Downgrading them would change behaviour for tracks
        // nobody complained about.
        downbeatConfidence <= 0f && phraseConfidence <= 0f && phraseOffsetMs > 0L -> GridTrust.PHRASE
        downbeatConfidence < MIN_DOWNBEAT_CONFIDENCE -> GridTrust.BEAT
        phraseConfidence < MIN_PHRASE_CONFIDENCE -> GridTrust.BAR
        else -> GridTrust.PHRASE
    }

/**
 * The anchor to quantize against, matching [gridTrust]: the phrase start, the first downbeat,
 * or just the beat grid. Falls back to the plain grid for rows written before the downbeat
 * pass existed.
 */
val BeatInfoEntity.trustedAnchorMs: Long
    get() = when (gridTrust) {
        GridTrust.PHRASE -> if (phraseOffsetMs > 0L || downbeatConfidence > 0f) phraseOffsetMs else firstBeatOffsetMs
        GridTrust.BAR -> if (downbeatOffsetMs > 0L || downbeatConfidence > 0f) downbeatOffsetMs else firstBeatOffsetMs
        GridTrust.BEAT -> firstBeatOffsetMs
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
