package com.alananasss.kittytune.audio.automix

import android.content.Context

// The transition-editor's data model - the Spotify-Mix "Edit transition" sheet, typed.
//
// The engine ([MixTransitionPresets]) owns the sample math; this file owns the *choice*:
// which preset, which volume shape, when the bass changes hands, whether a filter sweeps,
// and how many bars the overlap lasts. A [TransitionPlan] is what the playlist connector
// stores per track pair, what the editor edits, and what [TransitionStore.applyToEngine]
// translates into the engine override so playback actually performs what was drawn.

// The presets shown in the editor's horizontal ribbon - the Spotify-Mix ten, in its order.
enum class TransitionPreset {
    // Every curve was hand-set and matches no named preset.
    CUSTOM,
    // The engine's per-pair decision; the editor previews the resolved shape.
    AUTO,
    FADE,
    RISE,
    BLEND,
    WAVE,
    MELT,
    SLAM,
    // The echo tail freezes and the new track rises from underneath (engine texture).
    DISSOLVE,
    // No transition: the outgoing track ends, the incoming one starts.
    NONE,
}

// How the two decks' loudness moves across the overlap.
enum class VolumeCurve {
    // Equal-power weave - `V_A = cos(t·π/2)`, `V_B = sin(t·π/2)`; perceived loudness stays flat.
    SMOOTH_CROSSFADE,
    // Both decks hold full level through the first half - the literal "overlap".
    OVERLAP,
    // Sequential fade: the outgoing deck fades out first, the incoming one rises after it.
    FADE_IN_FADE_OUT,
    // The incoming deck cuts in partway through while the outgoing one fades out.
    CUT_IN_FADE_OUT,
    // The incoming deck fades in; the outgoing one is cut at the end of the overlap.
    FADE_IN_CUT_OUT,
    // A straight swap at the midpoint, with a 50 ms anti-click micro-ramp.
    CENTER_CUT,
    // The incoming deck snaps in almost immediately; the outgoing one clears out just as fast.
    FADE_IN_FAST_OUT;

    companion object {
        // Old persisted names -> the current enum, so saved pair plans survive the rename.
        fun legacyValueOf(name: String?): VolumeCurve? = when (name) {
            "CUT" -> CENTER_CUT
            "FADE_OUT_CUT_IN" -> CUT_IN_FADE_OUT
            else -> entries.firstOrNull { it.name == name }
        }
    }
}

// When the low end changes hands between the decks.
enum class EqSwapMode {
    // The bass hard-swaps on the centre bar line (the classic club blend).
    CENTRE_BASS_SWAP,
    // Deck A keeps its bass almost to the end, then hands it over.
    END_BASS_SWAP,
    // The bass moves to deck B immediately at the start of the overlap.
    START_BASS_SWAP,
    // No hard swap: low/mid/high fade across on their own ramps.
    THREE_BAND_FADE,
    // A quarter of the way in, deck A's lows drop dead - the quick bass cut.
    QUICK_BASS_CUT,
    // Deck A holds its lows almost to the very end - the long bass cut.
    LONG_BASS_CUT,
    // Bass rides the volume curves; no separate handover.
    NONE,
}

// The filter sweep across the overlap, if any - one leg per deck.
enum class FilterMode {
    // Deck A low-passes out (muffles away) while deck B enters behind a low-pass that opens.
    LOW_PASS_OUT_HIGH_PASS_IN,
    // Deck A high-passes out (the cutoff climbs away) while deck B enters behind one that opens.
    HIGH_PASS_OUT_HIGH_PASS_IN,
    // The build: deck A's high-pass climbs, deck B's low-pass opens with an accelerating rise into the drop.
    RISE_SWEEP,
    // Only deck A low-passes out; the incoming deck enters dry.
    LOW_PASS_OUT,
    // Only deck A high-passes out; the incoming deck enters dry.
    HIGH_PASS_OUT,
    // Only deck B enters behind a low-pass that opens; the outgoing deck runs dry.
    LOW_PASS_IN,
    // Only deck B enters behind a high-pass that opens; the outgoing deck runs dry.
    HIGH_PASS_IN,
    // Both decks run dry.
    NONE;

    companion object {
        // Old persisted names -> the current enum, so saved pair plans survive the rename.
        fun legacyValueOf(name: String?): FilterMode? = when (name) {
            "HIGH_PASS_FILTER_OUT", "LOW_PASS_FILTER_IN" -> LOW_PASS_OUT_HIGH_PASS_IN
            else -> entries.firstOrNull { it.name == name }
        }
    }
}

// The overlap length, in bars of 4 beats.
enum class TransitionDurationBars(val bars: Int, val beats: Int) {
    BARS_2(2, 8),
    BARS_4(4, 16),
    BARS_8(8, 32),
    BARS_16(16, 64);

    companion object {
        fun fromBars(bars: Int): TransitionDurationBars =
            entries.firstOrNull { it.bars == bars } ?: BARS_4
    }
}

// One track pair's transition, fully typed.
//
// @param preset the ribbon selection. When [AUTO], the three curve fields hold the *preview*
//   resolution and are re-derived per pair by [resolveAuto]; when a named preset, they hold that
//   preset's defaults; when [CUSTOM], whatever the user last picked.
// @param offsetMs where the incoming deck enters, in milliseconds into track B. The editor's
//   drag gesture writes it (snapped to the beat grid); `0` means "at the plan's own start".
data class TransitionPlan(
    val preset: TransitionPreset = TransitionPreset.AUTO,
    val volume: VolumeCurve = VolumeCurve.OVERLAP,
    val eq: EqSwapMode = EqSwapMode.CENTRE_BASS_SWAP,
    val filter: FilterMode = FilterMode.NONE,
    // The dramatic exit - reverb/echo tail on the outgoing deck (the white control).
    val effect: EffectTransitionMode = EffectTransitionMode.NONE,
    // The encore - the outgoing deck's repeated piece during the mix (the orange control).
    val loop: LoopTransitionMode = LoopTransitionMode.NONE,
    // Whether the two decks are pulled onto one tempo for this pair.
    val tempoMatch: TempoMatchMode = TempoMatchMode.AUTO,
    val bars: TransitionDurationBars = TransitionDurationBars.BARS_4,
    val offsetMs: Long = 0L,
    // Where the editor's mix window sits in the outgoing track (ms into A); -1 = auto.
    val zoneStartMs: Long = -1L,
    // --- Hand-placed curves (the customization button): empty = the named mode's curve.
    val customVolumeOut: List<CurvePoint> = emptyList(),
    val customVolumeIn: List<CurvePoint> = emptyList(),
    val customEqOut: List<CurvePoint> = emptyList(),
    val customEqIn: List<CurvePoint> = emptyList(),
    val customFilterOut: List<CurvePoint> = emptyList(),
    val customFilterIn: List<CurvePoint> = emptyList(),
    val customEffect: List<CurvePoint> = emptyList(),
) {
    val isAuto: Boolean get() = preset == TransitionPreset.AUTO

    // The named preset's curve triple, or null for Auto/Custom (they don't define one).
    fun presetCurves(preset: TransitionPreset): Triple<VolumeCurve, EqSwapMode, FilterMode>? =
        when (preset) {
            TransitionPreset.FADE -> Triple(VolumeCurve.SMOOTH_CROSSFADE, EqSwapMode.NONE, FilterMode.NONE)
            TransitionPreset.RISE -> Triple(VolumeCurve.FADE_IN_CUT_OUT, EqSwapMode.NONE, FilterMode.RISE_SWEEP)
            TransitionPreset.BLEND -> Triple(VolumeCurve.OVERLAP, EqSwapMode.CENTRE_BASS_SWAP, FilterMode.NONE)
            TransitionPreset.WAVE -> Triple(VolumeCurve.OVERLAP, EqSwapMode.THREE_BAND_FADE, FilterMode.NONE)
            TransitionPreset.MELT -> Triple(VolumeCurve.CUT_IN_FADE_OUT, EqSwapMode.LONG_BASS_CUT, FilterMode.NONE)
            TransitionPreset.SLAM -> Triple(VolumeCurve.CENTER_CUT, EqSwapMode.QUICK_BASS_CUT, FilterMode.NONE)
            TransitionPreset.DISSOLVE -> Triple(VolumeCurve.FADE_IN_FADE_OUT, EqSwapMode.NONE, FilterMode.HIGH_PASS_OUT)
            // "No transition" differs from Slam by playing NO overlap: the shortest honest
            // rendering is the hard cut - engine-side it shares SLAM's renderer, but the
            // overlap length collapses (computeAutomixPlan clamps a NONE pair to the
            // engine minimum) so the incoming deck simply takes over.
            TransitionPreset.NONE -> Triple(VolumeCurve.CENTER_CUT, EqSwapMode.NONE, FilterMode.NONE)
            TransitionPreset.AUTO, TransitionPreset.CUSTOM -> null
        }

    // Selecting a preset: adopt its curves, keep the bar length. Auto keeps the current curves
    // as the (re-resolvable) preview shape and hands the effects/loop back to the engine.
    fun withPresetSelected(preset: TransitionPreset): TransitionPlan {
        // A named preset replaces whatever was hand-shaped before: every hand curve goes.
        val cleared = copy(
            customVolumeOut = emptyList(), customVolumeIn = emptyList(),
            customEqOut = emptyList(), customEqIn = emptyList(),
            customFilterOut = emptyList(), customFilterIn = emptyList(), customEffect = emptyList(),
        )
        val curves = presetCurves(preset) ?: return if (preset == TransitionPreset.AUTO) {
            cleared.copy(preset = preset, effect = EffectTransitionMode.NONE, loop = LoopTransitionMode.NONE)
        } else {
            cleared.copy(preset = preset)
        }
        return cleared.copy(preset = preset, volume = curves.first, eq = curves.second, filter = curves.third)
    }

    // A manual curve change from the option sheets. If the resulting triple is exactly some
    // named preset's default, the ribbon snaps back to that preset instead of staying Custom -
    // the same behaviour Spotify's editor has, so single-parameter edits don't orphan the label.
    fun withCurves(volume: VolumeCurve = this.volume, eq: EqSwapMode = this.eq, filter: FilterMode = this.filter): TransitionPlan {
        val matched = TransitionPreset.entries.firstOrNull { candidate ->
            candidate != TransitionPreset.AUTO && candidate != TransitionPreset.CUSTOM &&
                presetCurves(candidate)?.let { it.first == volume && it.second == eq && it.third == filter } == true
        }
        return copy(
            preset = matched ?: TransitionPreset.CUSTOM,
            volume = volume,
            eq = eq,
            filter = filter,
            // Picking a named mode releases that control's hand shape - the option rows
            // own the curve again until the customization button is pressed once more.
            customVolumeOut = if (volume == this.volume) customVolumeOut else emptyList(),
            customVolumeIn = if (volume == this.volume) customVolumeIn else emptyList(),
            customEqOut = if (eq == this.eq) customEqOut else emptyList(),
            customEqIn = if (eq == this.eq) customEqIn else emptyList(),
            customFilterOut = if (filter == this.filter) customFilterOut else emptyList(),
            customFilterIn = if (filter == this.filter) customFilterIn else emptyList(),
        )
    }

    // Auto's per-pair resolution, straight from the DJ rulebook in [MixTransitionPresets].
    // Unknown tempo/keys degrade the same way the engine does - this must never throw.
    fun resolveAuto(outBpm: Float, inBpm: Float, outCamelot: String?, inCamelot: String?): TransitionPlan {
        if (!isAuto) return this
        val style = MixTransitionPresets.autoSelect(
            outBpm = outBpm,
            inBpm = inBpm,
            keyFit = MixTransitionPresets.keyFitOf(outCamelot, inCamelot),
            energyGap = null,
        )
        val uiPreset = when (style) {
            MixTransitionPresets.MixTransitionStyle.BLEND -> TransitionPreset.BLEND
            MixTransitionPresets.MixTransitionStyle.RISE -> TransitionPreset.RISE
            MixTransitionPresets.MixTransitionStyle.FADE -> TransitionPreset.FADE
            MixTransitionPresets.MixTransitionStyle.WAVE -> TransitionPreset.WAVE
            MixTransitionPresets.MixTransitionStyle.MELT -> TransitionPreset.MELT
            MixTransitionPresets.MixTransitionStyle.SLAM -> TransitionPreset.SLAM
            MixTransitionPresets.MixTransitionStyle.DISSOLVE -> TransitionPreset.DISSOLVE
            MixTransitionPresets.MixTransitionStyle.AUTO -> TransitionPreset.BLEND
        }
        return copy(preset = uiPreset).let { resolved ->
            resolved.copy(
                volume = resolved.presetCurves(resolved.preset)?.first ?: resolved.volume,
                eq = resolved.presetCurves(resolved.preset)?.second ?: resolved.eq,
                filter = resolved.presetCurves(resolved.preset)?.third ?: resolved.filter,
            )
        }
    }

    // The engine style playback should perform for this plan, or null to leave the engine on
    // Auto. `No transition` maps to the straight cut - the engine's shortest honest rendering
    // of "just end it". Dissolve keeps its own engine texture (the echo tail). Custom maps by
    // its dominant curve.
    fun toEngineStyle(): MixTransitionPresets.MixTransitionStyle? = when (preset) {
        TransitionPreset.AUTO -> null
        TransitionPreset.FADE -> MixTransitionPresets.MixTransitionStyle.FADE
        TransitionPreset.RISE -> MixTransitionPresets.MixTransitionStyle.RISE
        TransitionPreset.BLEND -> MixTransitionPresets.MixTransitionStyle.BLEND
        TransitionPreset.WAVE -> MixTransitionPresets.MixTransitionStyle.WAVE
        TransitionPreset.MELT -> MixTransitionPresets.MixTransitionStyle.MELT
        TransitionPreset.SLAM -> MixTransitionPresets.MixTransitionStyle.SLAM
        TransitionPreset.DISSOLVE -> MixTransitionPresets.MixTransitionStyle.DISSOLVE
        TransitionPreset.NONE -> MixTransitionPresets.MixTransitionStyle.SLAM
        TransitionPreset.CUSTOM -> when {
            filter != FilterMode.NONE -> MixTransitionPresets.MixTransitionStyle.RISE
            eq == EqSwapMode.QUICK_BASS_CUT -> MixTransitionPresets.MixTransitionStyle.SLAM
            eq == EqSwapMode.LONG_BASS_CUT -> MixTransitionPresets.MixTransitionStyle.BLEND
            volume == VolumeCurve.CENTER_CUT -> MixTransitionPresets.MixTransitionStyle.SLAM
            volume == VolumeCurve.FADE_IN_FAST_OUT -> MixTransitionPresets.MixTransitionStyle.SLAM
            eq == EqSwapMode.CENTRE_BASS_SWAP && volume == VolumeCurve.OVERLAP ->
                MixTransitionPresets.MixTransitionStyle.BLEND
            else -> MixTransitionPresets.MixTransitionStyle.FADE
        }
    }

    // Persistence - one compact line per pair, no JSON dependency.

    fun serialize(): String =
        listOf(
            preset.name,
            volume.name,
            eq.name,
            filter.name,
            effect.name,
            loop.name,
            tempoMatch.name,
            bars.name,
            offsetMs.toString(),
            zoneStartMs.toString(),
            customVolumeOut.serializePoints(),
            customVolumeIn.serializePoints(),
            customEqOut.serializePoints(),
            customEqIn.serializePoints(),
            customFilterOut.serializePoints(),
            customFilterIn.serializePoints(),
            customEffect.serializePoints(),
        ).joinToString("|")

    companion object {

        // Packs hand points as "x/y;x/y" with millisecond-precision integers - no locale dots.
        private fun List<CurvePoint>.serializePoints(): String =
            joinToString(";") { "${(it.x * 1000).toInt()}/${(it.y * 1000).toInt()}" }

        private fun parsePoints(raw: String?): List<CurvePoint> =
            raw?.takeIf { it.isNotBlank() }?.split(";")?.mapNotNull { token ->
                val xy = token.split("/")
                val x = xy.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val y = xy.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                CurvePoint(x / 1000f, y / 1000f)
            }?.takeIf { it.isNotEmpty() } ?: emptyList()

        fun deserialize(raw: String?): TransitionPlan? = runCatching {
            val parts = raw?.split("|") ?: return null
            if (parts.size !in 5..17) return null
            // The line grew three times: the original 6-part form kept `bars` at index 4; the
            // 9-part form carries effect/loop/tempoMatch between the filter and the bars;
            // the 17-part form splits the bass handover into two independent legs (the EQ
            // line rides each wave like the volume pair now). A saved plan from any era
            // must round-trip - detect the legacy form by its BARS_* token at index 4
            // before reading any of the new fields.
            val isLegacyLine = parts.getOrNull(4)?.startsWith("BARS_") == true
            // Lines saved before the split carried ONE shared bass curve ("deck A owns the
            // lows", token 12): deck B played its mirror image. Migrate it by complementing
            // every point, so an old plan both looks and sounds exactly as it did.
            val hasSplitEq = parts.size >= 17
            val legacySharedEq = parsePoints(parts.getOrNull(12))
            TransitionPlan(
                preset = TransitionPreset.valueOf(parts[0]),
                volume = VolumeCurve.legacyValueOf(parts[1]) ?: return null,
                eq = EqSwapMode.valueOf(parts[2]),
                filter = FilterMode.legacyValueOf(parts[3]) ?: return null,
                effect = parts.getOrNull(4)
                    ?.takeIf { !isLegacyLine }
                    ?.let { runCatching { EffectTransitionMode.valueOf(it) }.getOrNull() }
                    ?: EffectTransitionMode.NONE,
                loop = parts.getOrNull(5)
                    ?.takeIf { !isLegacyLine }
                    ?.let { runCatching { LoopTransitionMode.valueOf(it) }.getOrNull() }
                    ?: LoopTransitionMode.NONE,
                tempoMatch = parts.getOrNull(6)
                    ?.takeIf { !isLegacyLine }
                    ?.let { runCatching { TempoMatchMode.valueOf(it) }.getOrNull() }
                    ?: TempoMatchMode.AUTO,
                bars = if (isLegacyLine) {
                    runCatching { TransitionDurationBars.valueOf(parts[4]) }.getOrNull()
                        ?: TransitionDurationBars.BARS_4
                } else {
                    parts.getOrNull(7)
                        ?.let { runCatching { TransitionDurationBars.valueOf(it) }.getOrNull() }
                        ?: TransitionDurationBars.BARS_4
                },
                offsetMs = if (isLegacyLine) {
                    parts.getOrNull(5)?.toLongOrNull() ?: 0L
                } else {
                    parts.getOrNull(8)?.toLongOrNull() ?: 0L
                },
                // The 10th token (index 9) is the editor's window anchor; lines saved
                // before it existed simply keep the "auto" sentinel.
                zoneStartMs = parts.getOrNull(9)?.toLongOrNull() ?: -1L,
                // Tokens 11..17 are the hand-placed curves; older lines keep them empty.
                customVolumeOut = parsePoints(parts.getOrNull(10)),
                customVolumeIn = parsePoints(parts.getOrNull(11)),
                customEqOut = if (hasSplitEq) parsePoints(parts.getOrNull(12)) else legacySharedEq,
                customEqIn = if (hasSplitEq) {
                    parsePoints(parts.getOrNull(13))
                } else {
                    legacySharedEq.map { CurvePoint(it.x, 1f - it.y) }
                },
                customFilterOut = parsePoints(parts.getOrNull(if (hasSplitEq) 14 else 13)),
                customFilterIn = parsePoints(parts.getOrNull(if (hasSplitEq) 15 else 14)),
                customEffect = parsePoints(parts.getOrNull(if (hasSplitEq) 16 else 15)),
            )
        }.getOrNull()
    }
}

// Per-pair persistence and the engine bridge.
//
// Plans are keyed by `playlist : outgoing id : incoming id` in their own preferences file -
// one tiny XML, one read per connector render, no Room migration for a cosmetic cache. The
// engine consumes a plan through [applyToEngine], which writes the same global style/length
// settings the Mix panel always used: per-pair *storage* is exact, per-pair *playback* rides
// the engine's existing override path, so the audio path stays untouched and testable.
object TransitionStore {

    private const val PREFS_NAME = "mix_transition_plans"
    private const val KEY_PREFIX = "plan_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun key(playlistKey: String?, fromId: Long, toId: Long): String =
        "$KEY_PREFIX${playlistKey ?: "global"}_${fromId}_$toId"

    // The saved plan for this pair, or null when the connector has never been edited.
    fun load(context: Context, playlistKey: String?, fromId: Long, toId: Long): TransitionPlan? {
        val raw = prefs(context).getString(key(playlistKey, fromId, toId), null) ?: return null
        return TransitionPlan.deserialize(raw)
    }

    // The pair's plan under ANY of the route keys it may have been saved with.
    //
    // The editor saves under the detail screen's route id, while playback looks the plan
    // up under whatever navigation id the queue is playing from - and those do not always
    // agree (`12345` vs `system_playlist:…` vs `playlist_detail:12345`). Every candidate
    // key is tried in order; the most specific (the exact route) wins over the global
    // pair key.
    fun loadAny(context: Context, playlistKey: String?, fromId: Long, toId: Long): TransitionPlan? {
        candidateKeys(playlistKey).forEach { candidate ->
            val plan = load(context, candidate, fromId, toId)
            if (plan != null) return plan
        }
        return null
    }

    fun save(context: Context, playlistKey: String?, fromId: Long, toId: Long, plan: TransitionPlan) {
        prefs(context).edit()
            .putString(key(playlistKey, fromId, toId), plan.serialize())
            .apply()
    }

    // Writes the plan under every route key the pair can be played from: the exact
    // playlist key, the media-session `playlist_detail:<id>` form, the bare numeric id,
    // and the playlist-agnostic global pair key. A manual mix saved in the editor is then
    // audible no matter which route the queue came from - the "can't listen to
    // the mix I made manually" report.
    fun saveAll(context: Context, playlistKey: String?, numericPlaylistId: Long, fromId: Long, toId: Long, plan: TransitionPlan) {
        val p = prefs(context).edit()
        candidateKeys(playlistKey, numericPlaylistId).forEach { candidate ->
            p.putString(key(candidate, fromId, toId), plan.serialize())
        }
        p.apply()
    }

    // All route keys worth trying for this playlist, most specific first.
    private fun candidateKeys(playlistKey: String?, numericPlaylistId: Long = 0L): List<String?> =
        buildList {
            add(playlistKey?.takeIf { it.isNotBlank() })
            if (numericPlaylistId > 0L) {
                add("playlist_detail:$numericPlaylistId")
                add(numericPlaylistId.toString())
            }
            playlistKey?.takeIf { it.isNotBlank() }?.let { key ->
                add("playlist_detail:$key")
            }
            add("global")
        }

    fun clear(context: Context, playlistKey: String?) {
        val p = prefs(context)
        val keys = p.all.keys.filter { it.startsWith(KEY_PREFIX) && it.contains("_${playlistKey ?: "global"}_") }
        if (keys.isEmpty()) return
        p.edit().apply { keys.forEach { remove(it) } }.apply()
    }

    // Makes the running engine perform [plan]'s shape: the preset becomes the transition-style
    // override (Auto restores the engine's own decision) and the bar length becomes the
    // transition-length preference, clamped to the engine's supported range.
    fun applyToEngine(context: Context, plan: TransitionPlan) {
        val style = plan.toEngineStyle()
        val appPrefs = com.alananasss.kittytune.data.local.PlayerPreferences(context)
        appPrefs.setMixTransitionStyle(style?.name ?: "AUTO")
        appPrefs.setMixTransitionBeats(plan.bars.beats.coerceIn(4, 32))
        AutomixManager.setTransitionStyleOverride(style)
    }
}
