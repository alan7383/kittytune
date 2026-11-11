package com.alananasss.kittytune.audio.automix

import kotlin.math.pow

// The Spotify-Mix data model - the DJ-facing configuration of one track-pair transition.
//
// Everything the "Edit transition" screen shows and edits lives here as a typed choice:
// the overlap length in bars, the seven volume curves, the seven EQ handover modes, the
// seven filter sweeps, and the ten presets they collapse into. (The Camelot wheel itself
// - `CamelotKey` / `CamelotMode`, `parse` and the badge palette - lives in CamelotKey.kt
// and is only consumed here, so the engine, the editor and KeyHarmony share one
// declaration.)
//
// The same file owns the canonical envelope math - [volumeOutAt], [volumeInAt],
// [bassLevelAt], [filterOutFractionAt], [filterInFractionAt] - so the waveform canvas,
// the two-deck preview player and the saved-plan playback all render the exact same
// shape. Before this file existed the canvas drew one set of curves while the engine
// performed another (the editor showed things playback could not do).
//
// [TransitionPlan] (the persistence + engine layer, one serialised line per track pair)
// is bridged losslessly-enough through [TransitionConfig.toPlan] / [TransitionPlan.toConfig]:
// the new modes were added to the persistence enums as well, so a saved pair plan
// round-trips without losing the choice.

// ---------------------------------------------------------------------------
// The transition configuration
// ---------------------------------------------------------------------------
// NOTE: the Camelot wheel (CamelotKey + CamelotMode, `parse`, `code`/`formatted` and
// `badgeColor`) is declared exactly once, in CamelotKey.kt of this package - this file
// and the editor screens only consume it.

// The overlap length, in bars of 4 beats.
enum class TransitionBars(val bars: Int) {
    BARS_2(2),
    BARS_4(4),
    BARS_8(8),
    BARS_16(16);

    val beats: Int get() = bars * 4

    companion object {
        fun fromBars(bars: Int): TransitionBars =
            entries.firstOrNull { it.bars == bars } ?: BARS_4
    }
}

// How the two decks' loudness moves across the overlap.
enum class VolumeCurveMode(val displayName: String) {
    SMOOTH_CROSSFADE("Smooth crossfade"),
    OVERLAP("Overlap"),
    FADE_IN_FADE_OUT("Fade in fade out"),
    CUT_IN_FADE_OUT("Cut in fade out"),
    FADE_IN_CUT_OUT("Fade in cut out"),
    CENTER_CUT("Center cut"),
    FADE_IN_FAST_OUT("Fade in fast out"),
}

// When and how the low end changes hands between the decks.
enum class EqTransitionMode(val displayName: String) {
    CENTER_BASS_SWAP("Center bass swap"),
    END_BASS_SWAP("End bass swap"),
    START_BASS_SWAP("Start bass swap"),
    THREE_BAND_FADE("3-band fade"),
    QUICK_BASS_CUT("Quick bass cut"),
    LONG_BASS_CUT("Long bass cut"),
    NONE("None"),
}

// The filter sweep across the overlap - one leg per deck.
enum class FilterTransitionMode(val displayName: String) {
    LOW_PASS_OUT("Low pass filter out"),
    HIGH_PASS_OUT("High pass filter out"),
    LOW_PASS_IN("Low pass filter in"),
    HIGH_PASS_IN("High pass filter in"),
    HPF_OUT_LPF_IN("High pass filter out / low pass filter in"),
    RISE_SWEEP("Rise sweep"),
    NONE("None"),
}

// The dramatic exit - reverb tails and timed repeats on the outgoing deck, the white line.
// Spotify-Mix's option list, in its order.
enum class EffectTransitionMode(val displayName: String) {
    REVERB_OUT_CENTER("Reverb out center"),
    REVERB_CUT_END("Reverb cut end"),
    REVERB_OUT_END("Reverb out end"),
    DELAY_HALF_CUT_END("Delay ½ cut end"),
    DELAY_THREE_QUARTER_CUT_END("Delay ¾ cut end"),
    ECHO_HALF_CUT_END("Echo ½ cut end"),
    ECHO_HALF_OUT_END("Echo ½ out end"),
    NONE("None"),
}

// The encore - how long a piece of the outgoing deck repeats during the mix, the orange region.
enum class LoopTransitionMode(val beats: Int) {
    NONE(0),
    BEAT_1(1),
    BEAT_2(2),
    BEAT_4(4),
    BEAT_8(8),
    BEAT_16(16),
}

// Whether this pair's two decks are pulled onto one tempo.
enum class TempoMatchMode {
    // The engine's own rule: match when within ±10 BPM, else keep both tempos.
    AUTO,
    // Always stretch the incoming deck to the outgoing tempo (the beatmatch).
    MATCH,
    // Never stretch - both decks keep their own tempo.
    ORIGINAL,
}

// One hand-placed control point of a custom curve: [x] walks the transition window
// (0 = the seam's start, 1 = its end), [y] is the level (0..1) - what that value means
// depends on the control it shapes (loudness, bass ownership, filter openness, wet tail).
data class CurvePoint(val x: Float, val y: Float)

// Which of the editor's curves a set of hand-placed points drives.
enum class CurveOwner {
    // The outgoing deck's loudness (the blue line's leg in band A).
    VOLUME_OUT,
    // The incoming deck's loudness (the blue line's leg in band B).
    VOLUME_IN,
    // The outgoing deck's bass level (the amber line in band A) - 1 = full lows, 0 = handed over.
    EQ_OUT,
    // The incoming deck's bass level (the amber line in band B) - 1 = full lows, 0 = still with A.
    EQ_IN,
    // The outgoing deck's filter leg (the fuchsia line in band A).
    FILTER_OUT,
    // The incoming deck's filter leg (the fuchsia line in band B).
    FILTER_IN,
    // The outgoing deck's effects tail (the white line in band A).
    EFFECT,
}

// The level (0..1) of a hand-placed curve at [x], linearly interpolated between the
// sorted points and held flat past the ends - null when no points were placed, which
// means "fall back to the named mode's curve".
fun curveLevelAt(points: List<CurvePoint>, x: Float): Float? {
    if (points.isEmpty()) return null
    val p = x.coerceIn(0f, 1f)
    val sorted = points.sortedBy { it.x }
    if (sorted.size == 1) return sorted[0].y.coerceIn(0f, 1f)
    if (p <= sorted.first().x) return sorted.first().y.coerceIn(0f, 1f)
    if (p >= sorted.last().x) return sorted.last().y.coerceIn(0f, 1f)
    for (i in 0 until sorted.size - 1) {
        val a = sorted[i]
        val b = sorted[i + 1]
        if (p >= a.x && p <= b.x) {
            val span = (b.x - a.x).coerceAtLeast(1e-4f)
            val t = (p - a.x) / span
            return (a.y + (b.y - a.y) * t).coerceIn(0f, 1f)
        }
    }
    return sorted.last().y.coerceIn(0f, 1f)
}

// Samples [sample] at five evenly spaced stops - the starting line a hand edit reshapes.
fun seedCurve(sample: (Float) -> Float): List<CurvePoint> =
    listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { CurvePoint(it, sample(it).coerceIn(0f, 1f)) }

// The preset ribbon - Spotify-Mix's ten, in its order.
enum class MixPreset(val displayName: String) {
    AUTO("✦ Auto"),
    BLEND("Blend"),
    RISE("Rise"),
    FADE("Fade"),
    WAVE("Wave"),
    MELT("Melt"),
    SLAM("Slam"),
    DISSOLVE("Dissolve"),
    NO_TRANSITION("No transition"),
    CUSTOM("Custom"),
}

// One track pair's transition, fully typed - what the editor edits and the canvas draws.
//
// @param duration the overlap length in bars.
// @param userNudgeOffsetMs the hand-drawn horizontal nudge of the incoming deck, in
//   milliseconds into track B (beat-snapped by the editor's drag gesture).
// @param zoneStartMs where the mix window sits in the outgoing track, in milliseconds
//   into track A - the editor's hand-placed anchor. `-1` means "not placed yet": the
//   canvas and the preview then fall back to the default end-of-track mix point.
data class TransitionConfig(
    val duration: TransitionBars = TransitionBars.BARS_4,
    val volumeCurve: VolumeCurveMode = VolumeCurveMode.OVERLAP,
    val eqMode: EqTransitionMode = EqTransitionMode.CENTER_BASS_SWAP,
    val filterMode: FilterTransitionMode = FilterTransitionMode.NONE,
    val effectMode: EffectTransitionMode = EffectTransitionMode.NONE,
    val loopMode: LoopTransitionMode = LoopTransitionMode.NONE,
    val tempoMatch: TempoMatchMode = TempoMatchMode.AUTO,
    val preset: MixPreset = MixPreset.AUTO,
    val userNudgeOffsetMs: Long = 0L,
    val zoneStartMs: Long = -1L,
    // --- Hand-placed curves (the customization button) - empty list = the named mode's curve.
    val customVolumeOut: List<CurvePoint> = emptyList(),
    val customVolumeIn: List<CurvePoint> = emptyList(),
    val customEqOut: List<CurvePoint> = emptyList(),
    val customEqIn: List<CurvePoint> = emptyList(),
    val customFilterOut: List<CurvePoint> = emptyList(),
    val customFilterIn: List<CurvePoint> = emptyList(),
    val customEffect: List<CurvePoint> = emptyList(),
) {
    val isAuto: Boolean get() = preset == MixPreset.AUTO

    // The named preset's curve triple, or null for Auto/Custom (they don't define one).
    fun presetCurves(preset: MixPreset): Triple<VolumeCurveMode, EqTransitionMode, FilterTransitionMode>? =
        when (preset) {
            MixPreset.FADE -> Triple(VolumeCurveMode.SMOOTH_CROSSFADE, EqTransitionMode.NONE, FilterTransitionMode.NONE)
            MixPreset.RISE -> Triple(VolumeCurveMode.FADE_IN_CUT_OUT, EqTransitionMode.NONE, FilterTransitionMode.RISE_SWEEP)
            MixPreset.BLEND -> Triple(VolumeCurveMode.OVERLAP, EqTransitionMode.CENTER_BASS_SWAP, FilterTransitionMode.NONE)
            MixPreset.WAVE -> Triple(VolumeCurveMode.OVERLAP, EqTransitionMode.THREE_BAND_FADE, FilterTransitionMode.NONE)
            MixPreset.MELT -> Triple(VolumeCurveMode.CUT_IN_FADE_OUT, EqTransitionMode.END_BASS_SWAP, FilterTransitionMode.NONE)
            MixPreset.SLAM -> Triple(VolumeCurveMode.CENTER_CUT, EqTransitionMode.QUICK_BASS_CUT, FilterTransitionMode.NONE)
            MixPreset.DISSOLVE -> Triple(VolumeCurveMode.FADE_IN_FADE_OUT, EqTransitionMode.NONE, FilterTransitionMode.HIGH_PASS_OUT)
            MixPreset.NO_TRANSITION -> Triple(VolumeCurveMode.CENTER_CUT, EqTransitionMode.NONE, FilterTransitionMode.NONE)
            MixPreset.AUTO, MixPreset.CUSTOM -> null
        }

    // Selecting a preset adopts its curves and keeps the bar length. Auto hands the whole
    //  seam back to the engine - the effects and the loop go with it (the beatmatch toggle
    //  is orthogonal and stays, exactly like Spotify's separate beatmatch screen). A named
    //  preset replaces whatever was hand-shaped before: every hand curve goes.
    fun withPresetSelected(preset: MixPreset): TransitionConfig {
        val cleared = copy(
            customVolumeOut = emptyList(), customVolumeIn = emptyList(),
            customEqOut = emptyList(), customEqIn = emptyList(),
            customFilterOut = emptyList(), customFilterIn = emptyList(), customEffect = emptyList(),
        )
        val curves = presetCurves(preset) ?: return if (preset == MixPreset.AUTO) {
            cleared.copy(preset = preset, effectMode = EffectTransitionMode.NONE, loopMode = LoopTransitionMode.NONE)
        } else {
            cleared.copy(preset = preset)
        }
        return cleared.copy(preset = preset, volumeCurve = curves.first, eqMode = curves.second, filterMode = curves.third)
    }

    // A manual curve change from the option sheets. If the resulting triple is exactly a
    // named preset's default the ribbon snaps back to that preset instead of staying
    // Custom - single-parameter edits don't orphan the label.
    fun withCurves(
        volume: VolumeCurveMode = this.volumeCurve,
        eq: EqTransitionMode = this.eqMode,
        filter: FilterTransitionMode = this.filterMode,
    ): TransitionConfig {
        val matched = MixPreset.entries.firstOrNull { candidate ->
            candidate !in setOf(MixPreset.AUTO, MixPreset.CUSTOM) &&
                presetCurves(candidate)?.let { it.first == volume && it.second == eq && it.third == filter } == true
        }
        return copy(
            preset = matched ?: MixPreset.CUSTOM,
            volumeCurve = volume,
            eqMode = eq,
            filterMode = filter,
            // Picking a named mode releases that control's hand shape - the option rows
            // own the curve again until the customization button is pressed once more.
            customVolumeOut = if (volume == this.volumeCurve) customVolumeOut else emptyList(),
            customVolumeIn = if (volume == this.volumeCurve) customVolumeIn else emptyList(),
            customEqOut = if (eq == this.eqMode) customEqOut else emptyList(),
            customEqIn = if (eq == this.eqMode) customEqIn else emptyList(),
            customFilterOut = if (filter == this.filterMode) customFilterOut else emptyList(),
            customFilterIn = if (filter == this.filterMode) customFilterIn else emptyList(),
        )
    }

    // A manual effect choice from the white menu. A non-None effect is a hand edit, so the
    // ribbon goes Custom; turning it back off keeps whatever preset the curves imply. A
    // named tail also releases the hand-drawn wet curve.
    fun withEffect(mode: EffectTransitionMode): TransitionConfig {
        if (effectMode == mode) return this
        val nextPreset =
            if (mode != EffectTransitionMode.NONE && preset != MixPreset.CUSTOM) MixPreset.CUSTOM else preset
        return copy(
            effectMode = mode,
            preset = nextPreset,
            customEffect = if (mode == this.effectMode) customEffect else emptyList(),
        )
    }

    // A manual loop choice from the orange menu - same ribbon rule as the effects.
    fun withLoop(mode: LoopTransitionMode): TransitionConfig {
        if (loopMode == mode) return this
        val nextPreset =
            if (mode != LoopTransitionMode.NONE && preset != MixPreset.CUSTOM) MixPreset.CUSTOM else preset
        return copy(loopMode = mode, preset = nextPreset)
    }

    // The beatmatch toggle - orthogonal to the curve ribbon, it never flips the preset: a
    // pair can run a Blend shape with the tempos left alone.
    fun withTempoMatch(mode: TempoMatchMode): TransitionConfig =
        if (tempoMatch == mode) this else copy(tempoMatch = mode)

    // Hand-placed curves (the customization button)

    // The points currently shaping [owner]'s curve, or an empty list for the named mode.
    fun curvePoints(owner: CurveOwner): List<CurvePoint> = when (owner) {
        CurveOwner.VOLUME_OUT -> customVolumeOut
        CurveOwner.VOLUME_IN -> customVolumeIn
        CurveOwner.EQ_OUT -> customEqOut
        CurveOwner.EQ_IN -> customEqIn
        CurveOwner.FILTER_OUT -> customFilterOut
        CurveOwner.FILTER_IN -> customFilterIn
        CurveOwner.EFFECT -> customEffect
    }

    // Whether [owner]'s curve was hand-placed (its option sheet shows the Custom check).
    fun hasCustomCurve(owner: CurveOwner): Boolean = curvePoints(owner).isNotEmpty()

    val hasCustomVolume: Boolean get() = customVolumeOut.isNotEmpty() || customVolumeIn.isNotEmpty()
    val hasCustomFilter: Boolean get() = customFilterOut.isNotEmpty() || customFilterIn.isNotEmpty()
    val hasCustomEq: Boolean get() = customEqOut.isNotEmpty() || customEqIn.isNotEmpty()
    val hasAnyCustomCurves: Boolean
        get() = customVolumeOut.isNotEmpty() || customVolumeIn.isNotEmpty() || hasCustomEq ||
            customFilterOut.isNotEmpty() || customFilterIn.isNotEmpty() || customEffect.isNotEmpty()

    // Writes one hand curve back. A non-empty shape is a hand edit, so the ribbon goes Custom
    fun withCurvePoints(owner: CurveOwner, points: List<CurvePoint>): TransitionConfig {
        val nextPreset = if (points.isNotEmpty() && preset != MixPreset.CUSTOM) MixPreset.CUSTOM else preset
        return when (owner) {
            CurveOwner.VOLUME_OUT -> copy(customVolumeOut = points, preset = nextPreset)
            CurveOwner.VOLUME_IN -> copy(customVolumeIn = points, preset = nextPreset)
            CurveOwner.EQ_OUT -> copy(customEqOut = points, preset = nextPreset)
            CurveOwner.EQ_IN -> copy(customEqIn = points, preset = nextPreset)
            CurveOwner.FILTER_OUT -> copy(customFilterOut = points, preset = nextPreset)
            CurveOwner.FILTER_IN -> copy(customFilterIn = points, preset = nextPreset)
            CurveOwner.EFFECT -> copy(customEffect = points, preset = nextPreset)
        }
    }

    // Entering the hand edit for [owner]: seed its points from the curve the named mode
    // (or the existing hand shape) currently draws, so the user reshapes what they see.
    fun withCustomEditStarted(owner: CurveOwner): TransitionConfig {
        val seeded = curvePoints(owner).ifEmpty { seededCurve(owner) }
        return withCurvePoints(owner, seeded)
    }

    // The starting line for [owner]'s hand edit, sampled from the mode's own curve. Each
    //  deck gets its own bass leg now: A rides [bassLevelAt] (its lows drop at the swap),
    //  B rides the complement (its lows arrive where A's leave) - NONE keeps both full.
    fun seededCurve(owner: CurveOwner): List<CurvePoint> = when (owner) {
        CurveOwner.VOLUME_OUT -> seedCurve { volumeOutAt(volumeCurve, it) }
        CurveOwner.VOLUME_IN -> seedCurve { volumeInAt(volumeCurve, it) }
        CurveOwner.EQ_OUT -> seedCurve { if (eqMode == EqTransitionMode.NONE) 1f else bassLevelAt(eqMode, it) }
        CurveOwner.EQ_IN -> seedCurve { if (eqMode == EqTransitionMode.NONE) 1f else 1f - bassLevelAt(eqMode, it) }
        CurveOwner.FILTER_OUT -> seedCurve { filterOutFractionAt(filterMode, it) ?: 1f }
        CurveOwner.FILTER_IN -> seedCurve { filterInFractionAt(filterMode, it) ?: 1f }
        CurveOwner.EFFECT -> seedCurve { effectWetAt(effectMode, it) }
    }

    // The effective envelope of [owner] at [x]: the hand shape when present, else the named mode's.
    fun curveLevel(owner: CurveOwner, x: Float): Float = when (owner) {
        CurveOwner.VOLUME_OUT -> curveLevelAt(customVolumeOut, x) ?: volumeOutAt(volumeCurve, x)
        CurveOwner.VOLUME_IN -> curveLevelAt(customVolumeIn, x) ?: volumeInAt(volumeCurve, x)
        CurveOwner.EQ_OUT -> curveLevelAt(customEqOut, x) ?: if (eqMode == EqTransitionMode.NONE) 1f else bassLevelAt(eqMode, x)
        CurveOwner.EQ_IN -> curveLevelAt(customEqIn, x) ?: if (eqMode == EqTransitionMode.NONE) 1f else 1f - bassLevelAt(eqMode, x)
        CurveOwner.FILTER_OUT -> curveLevelAt(customFilterOut, x) ?: (filterOutFractionAt(filterMode, x) ?: 1f)
        CurveOwner.FILTER_IN -> curveLevelAt(customFilterIn, x) ?: (filterInFractionAt(filterMode, x) ?: 1f)
        CurveOwner.EFFECT -> curveLevelAt(customEffect, x) ?: effectWetAt(effectMode, x)
    }
}


// Bridge to the persistence/engine layer (TransitionPlan)

// Maps the editor's model onto the stored/engine plan without losing the choice.
fun TransitionConfig.toPlan(): TransitionPlan = TransitionPlan(
    preset = preset.toTransitionPreset(),
    volume = volumeCurve.toVolumeCurve(),
    eq = eqMode.toEqSwapMode(),
    filter = filterMode.toFilterMode(),
    effect = effectMode,
    loop = loopMode,
    tempoMatch = tempoMatch,
    bars = TransitionDurationBars.fromBars(duration.bars),
    offsetMs = userNudgeOffsetMs,
    zoneStartMs = zoneStartMs,
    customVolumeOut = customVolumeOut,
    customVolumeIn = customVolumeIn,
    customEqOut = customEqOut,
    customEqIn = customEqIn,
    customFilterOut = customFilterOut,
    customFilterIn = customFilterIn,
    customEffect = customEffect,
)

// Restores the editor's model from a stored plan (defaults survive a null plan).
fun TransitionPlan.toConfig(): TransitionConfig = TransitionConfig(
    duration = TransitionBars.fromBars(bars.bars),
    volumeCurve = volume.toVolumeCurveMode(),
    eqMode = eq.toEqTransitionMode(),
    filterMode = filter.toFilterTransitionMode(),
    effectMode = effect,
    loopMode = loop,
    tempoMatch = tempoMatch,
    preset = preset.toMixPreset(),
    userNudgeOffsetMs = offsetMs,
    zoneStartMs = zoneStartMs,
    customVolumeOut = customVolumeOut,
    customVolumeIn = customVolumeIn,
    customEqOut = customEqOut,
    customEqIn = customEqIn,
    customFilterOut = customFilterOut,
    customFilterIn = customFilterIn,
    customEffect = customEffect,
)

private fun MixPreset.toTransitionPreset(): TransitionPreset = when (this) {
    MixPreset.AUTO -> TransitionPreset.AUTO
    MixPreset.BLEND -> TransitionPreset.BLEND
    MixPreset.RISE -> TransitionPreset.RISE
    MixPreset.FADE -> TransitionPreset.FADE
    MixPreset.WAVE -> TransitionPreset.WAVE
    MixPreset.MELT -> TransitionPreset.MELT
    MixPreset.SLAM -> TransitionPreset.SLAM
    MixPreset.DISSOLVE -> TransitionPreset.DISSOLVE
    MixPreset.NO_TRANSITION -> TransitionPreset.NONE
    MixPreset.CUSTOM -> TransitionPreset.CUSTOM
}

private fun TransitionPreset.toMixPreset(): MixPreset = when (this) {
    TransitionPreset.AUTO -> MixPreset.AUTO
    TransitionPreset.BLEND -> MixPreset.BLEND
    TransitionPreset.RISE -> MixPreset.RISE
    TransitionPreset.FADE -> MixPreset.FADE
    TransitionPreset.WAVE -> MixPreset.WAVE
    TransitionPreset.MELT -> MixPreset.MELT
    TransitionPreset.SLAM -> MixPreset.SLAM
    TransitionPreset.DISSOLVE -> MixPreset.DISSOLVE
    TransitionPreset.NONE -> MixPreset.NO_TRANSITION
    TransitionPreset.CUSTOM -> MixPreset.CUSTOM
}

private fun VolumeCurveMode.toVolumeCurve(): VolumeCurve = when (this) {
    VolumeCurveMode.SMOOTH_CROSSFADE -> VolumeCurve.SMOOTH_CROSSFADE
    VolumeCurveMode.OVERLAP -> VolumeCurve.OVERLAP
    VolumeCurveMode.FADE_IN_FADE_OUT -> VolumeCurve.FADE_IN_FADE_OUT
    VolumeCurveMode.CUT_IN_FADE_OUT -> VolumeCurve.CUT_IN_FADE_OUT
    VolumeCurveMode.FADE_IN_CUT_OUT -> VolumeCurve.FADE_IN_CUT_OUT
    VolumeCurveMode.CENTER_CUT -> VolumeCurve.CENTER_CUT
    VolumeCurveMode.FADE_IN_FAST_OUT -> VolumeCurve.FADE_IN_FAST_OUT
}

private fun VolumeCurve.toVolumeCurveMode(): VolumeCurveMode = when (this) {
    VolumeCurve.SMOOTH_CROSSFADE -> VolumeCurveMode.SMOOTH_CROSSFADE
    VolumeCurve.OVERLAP -> VolumeCurveMode.OVERLAP
    VolumeCurve.FADE_IN_FADE_OUT -> VolumeCurveMode.FADE_IN_FADE_OUT
    VolumeCurve.CUT_IN_FADE_OUT -> VolumeCurveMode.CUT_IN_FADE_OUT
    VolumeCurve.FADE_IN_CUT_OUT -> VolumeCurveMode.FADE_IN_CUT_OUT
    VolumeCurve.CENTER_CUT -> VolumeCurveMode.CENTER_CUT
    VolumeCurve.FADE_IN_FAST_OUT -> VolumeCurveMode.FADE_IN_FAST_OUT
}

private fun EqTransitionMode.toEqSwapMode(): EqSwapMode = when (this) {
    EqTransitionMode.CENTER_BASS_SWAP -> EqSwapMode.CENTRE_BASS_SWAP
    EqTransitionMode.END_BASS_SWAP -> EqSwapMode.END_BASS_SWAP
    EqTransitionMode.START_BASS_SWAP -> EqSwapMode.START_BASS_SWAP
    EqTransitionMode.THREE_BAND_FADE -> EqSwapMode.THREE_BAND_FADE
    EqTransitionMode.QUICK_BASS_CUT -> EqSwapMode.QUICK_BASS_CUT
    EqTransitionMode.LONG_BASS_CUT -> EqSwapMode.LONG_BASS_CUT
    EqTransitionMode.NONE -> EqSwapMode.NONE
}

private fun EqSwapMode.toEqTransitionMode(): EqTransitionMode = when (this) {
    EqSwapMode.CENTRE_BASS_SWAP -> EqTransitionMode.CENTER_BASS_SWAP
    EqSwapMode.END_BASS_SWAP -> EqTransitionMode.END_BASS_SWAP
    EqSwapMode.START_BASS_SWAP -> EqTransitionMode.START_BASS_SWAP
    EqSwapMode.THREE_BAND_FADE -> EqTransitionMode.THREE_BAND_FADE
    EqSwapMode.QUICK_BASS_CUT -> EqTransitionMode.QUICK_BASS_CUT
    EqSwapMode.LONG_BASS_CUT -> EqTransitionMode.LONG_BASS_CUT
    EqSwapMode.NONE -> EqTransitionMode.NONE
}

private fun FilterTransitionMode.toFilterMode(): FilterMode = when (this) {
    FilterTransitionMode.LOW_PASS_OUT -> FilterMode.LOW_PASS_OUT
    FilterTransitionMode.HIGH_PASS_OUT -> FilterMode.HIGH_PASS_OUT
    FilterTransitionMode.LOW_PASS_IN -> FilterMode.LOW_PASS_IN
    FilterTransitionMode.HIGH_PASS_IN -> FilterMode.HIGH_PASS_IN
    FilterTransitionMode.HPF_OUT_LPF_IN -> FilterMode.LOW_PASS_OUT_HIGH_PASS_IN
    FilterTransitionMode.RISE_SWEEP -> FilterMode.RISE_SWEEP
    FilterTransitionMode.NONE -> FilterMode.NONE
}

private fun FilterMode.toFilterTransitionMode(): FilterTransitionMode = when (this) {
    FilterMode.LOW_PASS_OUT -> FilterTransitionMode.LOW_PASS_OUT
    FilterMode.HIGH_PASS_OUT -> FilterTransitionMode.HIGH_PASS_OUT
    FilterMode.LOW_PASS_IN -> FilterTransitionMode.LOW_PASS_IN
    FilterMode.HIGH_PASS_IN -> FilterTransitionMode.HIGH_PASS_IN
    FilterMode.LOW_PASS_OUT_HIGH_PASS_IN -> FilterTransitionMode.HPF_OUT_LPF_IN
    FilterMode.HIGH_PASS_OUT_HIGH_PASS_IN -> FilterTransitionMode.HIGH_PASS_OUT
    FilterMode.RISE_SWEEP -> FilterTransitionMode.RISE_SWEEP
    FilterMode.NONE -> FilterTransitionMode.NONE
}

// Canonical envelope math - shared by the canvas, the preview decks and playback

// The centre-cut's anti-click window: the hard swap is softened by a micro-ramp roughly
// 50 ms wide at typical zone lengths. Inaudible as a fade, but it keeps the sample
// discontinuity of two full-scale decks meeting from ever reaching the speaker.
private const val CENTER_CUT_RAMP = 0.005f

// Deck A's loudness (0..1) at [x] (0..1 across the overlap) for [curve].
fun volumeOutAt(curve: VolumeCurveMode, x: Float): Float {
    val p = x.coerceIn(0f, 1f)
    return when (curve) {
        VolumeCurveMode.SMOOTH_CROSSFADE ->
            MixTransitionPresets.equalPowerOut(p) // cos(t·π/2)
        VolumeCurveMode.OVERLAP ->
            1f - MixTransitionPresets.smoothstep(0.5f, 0.92f, p) // holds full level, then yields
        VolumeCurveMode.FADE_IN_FADE_OUT ->
            1f - MixTransitionPresets.smoothstep(0.05f, 0.45f, p) // gone before B rises
        VolumeCurveMode.CUT_IN_FADE_OUT ->
            1f - MixTransitionPresets.smoothstep(0.05f, 0.85f, p)
        VolumeCurveMode.FADE_IN_CUT_OUT ->
            if (p < 0.8f) 1f else (1f - (p - 0.8f) / 0.2f).coerceIn(0f, 1f)
        VolumeCurveMode.CENTER_CUT ->
            when {
                p < 0.5f - CENTER_CUT_RAMP -> 1f
                p > 0.5f + CENTER_CUT_RAMP -> 0f
                else -> 1f - (p - (0.5f - CENTER_CUT_RAMP)) / (2f * CENTER_CUT_RAMP)
            }
        // B snaps in almost immediately, A clears out just as fast - the hard-tear handover.
        VolumeCurveMode.FADE_IN_FAST_OUT ->
            1f - MixTransitionPresets.smoothstep(0.02f, 0.5f, p)
    }
}

// Deck B's loudness (0..1) at [x] for [curve].
fun volumeInAt(curve: VolumeCurveMode, x: Float): Float {
    val p = x.coerceIn(0f, 1f)
    return when (curve) {
        VolumeCurveMode.SMOOTH_CROSSFADE ->
            MixTransitionPresets.equalPowerIn(p) // sin(t·π/2)
        VolumeCurveMode.OVERLAP ->
            MixTransitionPresets.smoothstep(0f, 0.5f, p) // min(1, t×2), smoothed
        VolumeCurveMode.FADE_IN_FADE_OUT ->
            MixTransitionPresets.smoothstep(0.55f, 0.95f, p) // rises after A is gone
        VolumeCurveMode.CUT_IN_FADE_OUT ->
            when {
                p < 0.35f - CENTER_CUT_RAMP -> 0f
                p > 0.35f + CENTER_CUT_RAMP -> 1f
                else -> (p - (0.35f - CENTER_CUT_RAMP)) / (2f * CENTER_CUT_RAMP)
            }
        VolumeCurveMode.FADE_IN_CUT_OUT ->
            MixTransitionPresets.smoothstep(0.05f, 0.8f, p)
        VolumeCurveMode.CENTER_CUT ->
            when {
                p < 0.5f - CENTER_CUT_RAMP -> 0f
                p > 0.5f + CENTER_CUT_RAMP -> 1f
                else -> (p - (0.5f - CENTER_CUT_RAMP)) / (2f * CENTER_CUT_RAMP)
            }
        VolumeCurveMode.FADE_IN_FAST_OUT ->
            MixTransitionPresets.smoothstep(0.0f, 0.4f, p) // fully in before A is half gone
    }
}

// The bass handover level (1 = deck A owns the bass, 0 = deck B owns it) at [x].
// A step for the hard swaps, a smooth ramp for the 3-band fade, flat for NONE.
fun bassLevelAt(mode: EqTransitionMode, x: Float): Float {
    val p = x.coerceIn(0f, 1f)
    return when (mode) {
        EqTransitionMode.CENTER_BASS_SWAP -> if (p < 0.5f) 1f else 0f
        EqTransitionMode.END_BASS_SWAP -> if (p < 0.9f) 1f else 0f
        EqTransitionMode.START_BASS_SWAP -> if (p < 0.05f) 1f else 0f
        EqTransitionMode.THREE_BAND_FADE -> 1f - MixTransitionPresets.smoothstep(0.3f, 0.7f, p)
        // The quick cut: A's lows drop a quarter of the way in - the "cut the bass, drop the new one" move.
        EqTransitionMode.QUICK_BASS_CUT -> if (p < 0.25f) 1f else 0f
        // The long cut: A keeps the lows almost to the very end, then hands them over.
        EqTransitionMode.LONG_BASS_CUT -> if (p < 0.85f) 1f else 0f
        EqTransitionMode.NONE -> 1f
    }
}

// Deck A's filter leg as a 0..1 fraction of the log 20 Hz → 20 kHz span at [x], or null
// when the outgoing deck runs dry in this mode. Fraction 1 = fully open (dry),
// 0 = fully closed.
fun filterOutFractionAt(mode: FilterTransitionMode, x: Float): Float? {
    val p = x.coerceIn(0f, 1f)
    return when (mode) {
        FilterTransitionMode.LOW_PASS_OUT ->
            1f - 0.96f * MixTransitionPresets.smoothstep(0.05f, 0.8f, p) // low-pass closes away
        FilterTransitionMode.HIGH_PASS_OUT -> {
            val sweep = MixTransitionPresets.smoothstep(0.05f, 0.8f, p)
            0.04f + 0.96f * sweep * sweep // high-pass climbs toward the handover
        }
        FilterTransitionMode.HPF_OUT_LPF_IN -> {
            val sweep = MixTransitionPresets.smoothstep(0.05f, 0.8f, p)
            0.04f + 0.96f * sweep * sweep // A high-passes out
        }
        FilterTransitionMode.RISE_SWEEP -> {
            val sweep = MixTransitionPresets.smoothstep(0.05f, 0.75f, p)
            0.04f + 0.96f * sweep * sweep // the build's accelerating climb
        }
        FilterTransitionMode.LOW_PASS_IN,
        FilterTransitionMode.HIGH_PASS_IN,
        FilterTransitionMode.NONE -> null // deck A runs dry; only deck B is filtered
    }
}

// Deck B's filter leg at [x], or null when the incoming deck runs dry. Fraction 1 =
// fully open (dry) by the end of the overlap, so the track always lands unfiltered
fun filterInFractionAt(mode: FilterTransitionMode, x: Float): Float? {
    val p = x.coerceIn(0f, 1f)
    return when (mode) {
        FilterTransitionMode.LOW_PASS_OUT,
        FilterTransitionMode.HIGH_PASS_OUT,
        FilterTransitionMode.NONE -> null // deck B runs dry; only deck A is filtered
        FilterTransitionMode.LOW_PASS_IN -> {
            val sweep = MixTransitionPresets.smoothstep(0.2f, 0.95f, p)
            0.04f + 0.96f * sweep * sweep // low-pass opens up
        }
        FilterTransitionMode.HIGH_PASS_IN -> {
            val sweep = MixTransitionPresets.smoothstep(0.2f, 0.95f, p)
            0.04f + 0.96f * sweep * sweep // high-pass opens up
        }
        FilterTransitionMode.HPF_OUT_LPF_IN -> {
            val sweep = MixTransitionPresets.smoothstep(0.2f, 0.95f, p)
            0.04f + 0.96f * sweep * sweep // B enters behind a low-pass that opens
        }
        FilterTransitionMode.RISE_SWEEP -> {
            val sweep = MixTransitionPresets.smoothstep(0.25f, 0.95f, p)
            0.04f + 0.96f * sweep * sweep // the drop: the low-pass snaps open
        }
    }
}

// Whether a deck's filter leg is a high-pass (vs a low-pass) in [mode]
fun filterLegIsHighPass(mode: FilterTransitionMode, isOutgoingDeck: Boolean): Boolean = when (mode) {
    FilterTransitionMode.LOW_PASS_OUT -> false
    FilterTransitionMode.HIGH_PASS_OUT -> true
    FilterTransitionMode.LOW_PASS_IN -> false
    FilterTransitionMode.HIGH_PASS_IN -> true
    FilterTransitionMode.HPF_OUT_LPF_IN -> isOutgoingDeck // A high-passes out, B low-passes in
    FilterTransitionMode.RISE_SWEEP -> isOutgoingDeck // A climbs on a high-pass, B on a low-pass
    FilterTransitionMode.NONE -> false
}

// Maps a filter fraction (0..1 of the log span) onto a real cutoff in Hz across the
// full 20 Hz -> 20 kHz audio band - what the preview decks' SVF processors sweep
fun filterFractionToHz(fraction: Float): Float {
    val f = fraction.coerceIn(0f, 1f)
    // log2 span: 20 Hz = 2^0 · 20 … 20 kHz = 2^~9.97 · 20
    val minHz = 20f
    val maxHz = 20_000f
    val logMin = 0f
    val logMax = (kotlin.math.ln(maxHz / minHz) / kotlin.math.ln(2f))
    return (minHz * 2f.pow(logMin + (logMax - logMin) * f)).coerceIn(minHz, maxHz)
}

// The outgoing deck's effects wet level (0..1) at [x] - the white line the canvas draws and
// the tail the decks perform. Zero means a dry deck; the envelope mirrors the option names:
// "cut end" pulls the tail back before the seam lands, "out end" swells into it
fun effectWetAt(mode: EffectTransitionMode, x: Float): Float {
    val p = x.coerceIn(0f, 1f)
    return when (mode) {
        EffectTransitionMode.REVERB_OUT_CENTER ->
            MixTransitionPresets.smoothstep(0.05f, 0.35f, p) // the room takes the track at the swap
        EffectTransitionMode.REVERB_CUT_END ->
            MixTransitionPresets.smoothstep(0.10f, 0.55f, p) *
                (1f - MixTransitionPresets.smoothstep(0.85f, 0.95f, p))
        EffectTransitionMode.REVERB_OUT_END ->
            MixTransitionPresets.smoothstep(0.35f, 0.90f, p) // the tail swells into the handover
        EffectTransitionMode.DELAY_HALF_CUT_END,
        EffectTransitionMode.DELAY_THREE_QUARTER_CUT_END ->
            MixTransitionPresets.smoothstep(0.15f, 0.50f, p) *
                (1f - MixTransitionPresets.smoothstep(0.80f, 0.92f, p))
        EffectTransitionMode.ECHO_HALF_CUT_END ->
            MixTransitionPresets.smoothstep(0.10f, 0.45f, p) *
                (1f - MixTransitionPresets.smoothstep(0.75f, 0.90f, p))
        EffectTransitionMode.ECHO_HALF_OUT_END ->
            MixTransitionPresets.smoothstep(0.30f, 0.95f, p) // repeat… repeat… into the new track
        EffectTransitionMode.NONE -> 0f
    }
}

// The repeats' spacing in beats. Zero means the reverb's fixed short tail - not beat-timed,
// the delay/echo modes are
fun effectDelayBeats(mode: EffectTransitionMode): Float = when (mode) {
    EffectTransitionMode.DELAY_THREE_QUARTER_CUT_END -> 0.75f
    EffectTransitionMode.DELAY_HALF_CUT_END,
    EffectTransitionMode.ECHO_HALF_CUT_END,
    EffectTransitionMode.ECHO_HALF_OUT_END -> 0.5f
    else -> 0f
}

// The tail's regrowth: reverb trails stay modest, echo rings longer
fun effectFeedback(mode: EffectTransitionMode): Float = when (mode) {
    EffectTransitionMode.REVERB_OUT_CENTER,
    EffectTransitionMode.REVERB_CUT_END,
    EffectTransitionMode.REVERB_OUT_END -> 0.30f
    EffectTransitionMode.DELAY_HALF_CUT_END,
    EffectTransitionMode.DELAY_THREE_QUARTER_CUT_END -> 0.38f
    EffectTransitionMode.ECHO_HALF_CUT_END,
    EffectTransitionMode.ECHO_HALF_OUT_END -> 0.55f
    EffectTransitionMode.NONE -> 0f
}

// The playback frame for a hand-saved pair plan ([TransitionPlan]): the exact curves the
// editor drew - the hand-placed points when present, otherwise the plan's own named modes -
// instead of the engine style's approximation. This is what makes a saved Custom mix play
// the way the editor previewed it: the loudness pair, the bass handover and the filter legs
// all ride the drawn envelopes, frame by frame.
//
// Beat-quantized engine textures (Rise's grid drop, Melt's speed collapse, Slam's limiter
// hold) stay the named presets' territory - a hand curve plays as drawn.
fun planFrameAt(
    plan: TransitionPlan,
    progress: Float,
    beatsTotal: Int,
    bpm: Float,
): MixTransitionPresets.TransitionFrame {
    val p = progress.coerceIn(0f, 1f)
    // beatsTotal/bpm are part of the frame contract (the style presets quantize against
    // them); a drawn curve is beat-agnostic, so they only ride along for signature parity.
    @Suppress("UNUSED_PARAMETER") val beatsTotalUsed = beatsTotal
    @Suppress("UNUSED_PARAMETER") val bpmUsed = bpm

    val volumeCurve = plan.volume.toVolumeCurveMode()
    val eqMode = plan.eq.toEqTransitionMode()
    // The plan stores the config-side FilterMode; the fraction/leg math below speaks
    // FilterTransitionMode - translate through the file's own mapping, not the reverse one.
    val filterMode = plan.filter.toFilterTransitionMode()

    // Loudness - the hand shapes win over the named modes.
    val fadeOut = curveLevelAt(plan.customVolumeOut, p) ?: volumeOutAt(volumeCurve, p)
    val fadeIn = curveLevelAt(plan.customVolumeIn, p) ?: volumeInAt(volumeCurve, p)

    // Bass handover - same shelf semantics the engine presets use: bassDuckOut is how much
    // of the outgoing deck's low end is cut, bassDuckIn how much of the incoming deck's is.
    // The editor draws TWO independent legs now (the amber line rides each wave like the
    // volume pair): a hand shape on a leg replaces that deck's bass level wholesale, while
    // the named swap modes give deck A [bassLevelAt] and deck B its complement. The drawn
    // shape IS the handover; it rides the duck shelves, so the stem swap flags stay
    // constant "keep both stems live" - edge-triggered by the fade loop, they would
    // otherwise mute the outgoing deck's bass stem exactly where the plan says it owns the lows.
    val bassActive = plan.customEqOut.isNotEmpty() || plan.customEqIn.isNotEmpty() ||
        eqMode != EqTransitionMode.NONE
    val bassLevelOut = curveLevelAt(plan.customEqOut, p)
        ?: if (eqMode == EqTransitionMode.NONE) 1f else bassLevelAt(eqMode, p)
    val bassLevelIn = curveLevelAt(plan.customEqIn, p)
        ?: if (eqMode == EqTransitionMode.NONE) 1f else 1f - bassLevelAt(eqMode, p)

    // Filter legs - fraction 1 = fully open (dry), 0 = fully closed; null = that deck is dry
    val legA = curveLevelAt(plan.customFilterOut, p) ?: filterOutFractionAt(filterMode, p)
    val legB = curveLevelAt(plan.customFilterIn, p) ?: filterInFractionAt(filterMode, p)
    // The engine's biquad owns three shapes: the muffle (low-pass) the SVF preview twin
    // already had, the climbing high-pass, and a bit-transparent bypass for dry legs
    val modeOut = when {
        legA == null -> MixSweepAudioProcessor.FilterMode.BYPASS
        filterLegIsHighPass(filterMode, isOutgoingDeck = true) -> MixSweepAudioProcessor.FilterMode.HIGH_PASS
        else -> MixSweepAudioProcessor.FilterMode.LOW_PASS
    }
    val modeIn = when {
        legB == null -> MixSweepAudioProcessor.FilterMode.BYPASS
        filterLegIsHighPass(filterMode, isOutgoingDeck = false) -> MixSweepAudioProcessor.FilterMode.HIGH_PASS
        else -> MixSweepAudioProcessor.FilterMode.LOW_PASS
    }

    return MixTransitionPresets.TransitionFrame(
        fadeIn = fadeIn,
        fadeOut = fadeOut,
        bassDuckOut = if (bassActive) 1f - bassLevelOut else 0f,
        bassDuckIn = if (bassActive) 1f - bassLevelIn else 0f,
        bassSwapOut = true,
        bassSwapIn = true,
        sweepHz = legA?.let { filterFractionToHz(it) },
        sweepHzIn = legB?.let { filterFractionToHz(it) },
        sweepQ = 0.7f,
        sweepModeOut = modeOut,
        sweepModeIn = modeIn,
    )
}
