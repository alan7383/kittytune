package com.alananasss.kittytune.audio.automix

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// The eight Spotify-Mix-style transition presets, as pure math.
//
// Everything here is sample-rate agnostic and allocation free at process time: the crossfade
// loop in [com.alananasss.kittytune.data.MusicManager] asks for one [TransitionFrame] per fade
// step and translates the numbers onto the decks (volume, duck-processor mix, stem bass swap,
// filter sweep, playback speed, limiter hold). Keeping the math out of the audio objects is what
// makes every curve unit-testable without an ExoPlayer.
//
// Terminology used across the docs below:
//  - `progress` - 0..1 position inside the transition's wall-clock span;
//  - `beatProgress` - progress measured in the transition's own beat grid (0 = beat 1);
//  - "outgoing" (deck A) is the track fading out, "incoming" (deck B) the one fading in.
object MixTransitionPresets {

    // @param title human name shown in the preset selector.
    // @param defaultPhraseBeats the transition length this style sounds best at, in beats.
    // @param description one line under the title in the settings panel.
    enum class MixTransitionStyle(
        val title: String,
        val defaultPhraseBeats: Int,
        val description: String,
    ) {
        AUTO("Auto", 16, "Picks Blend / Rise / Slam / Dissolve from the pair"),
        BLEND("Blend", 16, "Bass crossover swap on the LR4 grid, exponential mids"),
        RISE("Rise", 8, "High-pass sweep up, hard cut on the downbeat"),
        FADE("Fade", 16, "Equal-power musical crossfade"),
        WAVE("Wave", 16, "Anti-phase band-pass wave across both decks"),
        MELT("Melt", 4, "Tape-stop pitch drop into the downbeat"),
        SLAM("Slam", 4, "Straight cut on beat 1, limiter catches the click"),
        DISSOLVE("Dissolve", 8, "Echo tail freezes, new track rises from underneath"),
    }

    // Fallback when nothing about the pair is known: the safe classic.
    val DEFAULT_STYLE = MixTransitionStyle.BLEND

    // One fade step's worth of parameters, all already computed.
    //
    // @param fadeIn loudness of the incoming deck (0..1), applied as player volume.
    // @param fadeOut loudness of the outgoing deck (0..1).
    // @param bassDuckOut how much the outgoing deck's low-shelf duck (LR4-pair at 150 Hz) is
    //   engaged, 0 = dry .. 1 = full duck. Drives [com.alananasss.kittytune.audio.automix.AutomixDuckAudioProcessor].
    // @param bassDuckIn same for the incoming deck.
    // @param bassSwapOut true once the outgoing deck's bass stem must be muted for the rest of
    //   the blend (the "smart bass cross" handover). The bass then lives only on deck B.
    // @param bassSwapIn true once the incoming deck's bass stem must be unmuted.
    // @param sweepHz filter cutoff the sweep processor should run at on the outgoing deck
    //   (null = bypass / flat). Only [MixTransitionStyle.RISE] and [MixTransitionStyle.WAVE] use it.
    // @param sweepHzIn same for the incoming deck (the Wave preset sweeps it in anti-phase).
    // @param sweepQ resonance of the sweep filter, 0.7 (wide) .. 8 (whistle).
    // @param speedOut playback-speed multiplier for the outgoing deck. 1 = normal; Melt drops it
    //   below 1 (speed AND pitch together, like a record slowing down).
    // @param limiterHoldMs how long the safety limiter stays force-enabled from this step
    //   (Slam only; 0 elsewhere).
    // @param echoTailOut how strongly the outgoing deck's "frozen echo tail" envelope is engaged
    //   (Dissolve only), 0..1. Rendered as a ducked, exponentially decaying level on deck A.
    data class TransitionFrame(
        val fadeIn: Float,
        val fadeOut: Float,
        val bassDuckOut: Float = 0f,
        val bassDuckIn: Float = 0f,
        val bassSwapOut: Boolean = false,
        val bassSwapIn: Boolean = false,
        val sweepHz: Float? = null,
        val sweepHzIn: Float? = null,
        val sweepQ: Float = 0.7f,
        val speedOut: Float = 1f,
        val limiterHoldMs: Int = 0,
        val echoTailOut: Float = 0f,
        // Explicit sweep modes for plan-driven frames ([planFrameAt] - the saved Custom
        // seam's own filter legs): which filter each deck runs while [sweepHz]/[sweepHzIn]
        // sweep. Null = the fade loop derives the mode from the preset branch as before.
        val sweepModeOut: com.alananasss.kittytune.audio.automix.MixSweepAudioProcessor.FilterMode? = null,
        val sweepModeIn: com.alananasss.kittytune.audio.automix.MixSweepAudioProcessor.FilterMode? = null,
    )

    // Auto selection (the "Auto" preset itself)

    // How close the two BPMs must be for a smooth blend, as a fraction (4 %).
    const val BLEND_BPM_TOLERANCE = 0.04f

    // A next track this much faster than the playing one earns a build-and-drop instead.
    const val RISE_BPM_JUMP = 0.08f

    // An energy jump this wide is a mood change, not a weave: cut it short.
    const val ENERGY_GAP_SLAM = 0.35f

    // The Auto decision, straight from the DJ rulebook:
    //
    //  - tempos within 4 % **and** keys that fit (same number or one step on the Camelot wheel)
    //    -> [MixTransitionStyle.BLEND]: nothing needs hiding, blend everything (tempo proximity
    //    is octave-normalized - a 2× / 0.5× BPM relative counts as the same tempo);
    //  - the incoming track > 8 % faster -> [MixTransitionStyle.RISE]: the tempo jump reads as a
    //    build-up, so sell it as one;
    //  - clashing keys or a violent energy jump -> the seam has to be short: a hard
    //    [MixTransitionStyle.SLAM] when energy *rises* (the drop excuses the cut), a
    //    [MixTransitionStyle.DISSOLVE] when it falls (the echo tail softens the landing).
    //  - anything else (mild tempo drift, unknown keys) -> [MixTransitionStyle.FADE], the neutral
    //    equal-power crossfade that never embarrasses itself.
    //
    // @param outBpm/inBpm canonical (normalized) BPMs, 0 when unknown.
    // @param keyFit harmonic fit of the two Camelot codes; null when either key is unknown.
    // @param energyGap |energy(out) − energy(in)|, 0..1; null when unknown.
    // internal: the parameter type KeyHarmony.Fit is internal to the app module, and a public
    // signature would leak it (Kotlin EXPOSED_PARAMETER_TYPE). Every caller lives in :app.
    internal fun autoSelect(
        outBpm: Float,
        inBpm: Float,
        keyFit: KeyHarmony.Fit?,
        energyGap: Float?,
    ): MixTransitionStyle {
        val hasTempos = outBpm > 0f && inBpm > 0f
        // Octave-normalized tempo distance: a 2× / 0.5× BPM relative (70 into 140) is the
        // same tempo one octave apart, not a 50 % jump. The ratio is folded into
        // [0.667, 1.5] first, so a perfect-octave pair reads 0 - RISE stays reserved for
        // genuine tempo jumps, and the octave pair earns its BLEND.
        val tempoDiff = if (hasTempos) {
            var ratio = inBpm / outBpm
            while (ratio > 1.5f) ratio /= 2f
            while (ratio < 0.667f) ratio *= 2f
            abs(1f - ratio)
        } else 0f
        val keysCompatible = keyFit == null ||
            keyFit == KeyHarmony.Fit.SAME ||
            keyFit == KeyHarmony.Fit.NEIGHBOUR ||
            keyFit == KeyHarmony.Fit.DIAGONAL ||
            keyFit == KeyHarmony.Fit.LIFT

        return when {
            hasTempos && tempoDiff > RISE_BPM_JUMP -> MixTransitionStyle.RISE
            !keysCompatible -> {
                // Rising energy wants the slam; falling or unknown energy gets the echo tail.
                if (energyGap != null && energyGap >= ENERGY_GAP_SLAM) MixTransitionStyle.SLAM
                else MixTransitionStyle.DISSOLVE
            }
            hasTempos && tempoDiff < BLEND_BPM_TOLERANCE && keysCompatible -> MixTransitionStyle.BLEND
            else -> MixTransitionStyle.FADE
        }
    }

    // Camelot codes ("8A"/"11B") straight into a fit, so callers need no wheel knowledge.
    // internal: returns KeyHarmony.Fit, which is internal - same visibility reasoning as autoSelect.
    internal fun keyFitOf(outCamelot: String?, inCamelot: String?): KeyHarmony.Fit? =
        if (outCamelot == null || inCamelot == null) null else KeyHarmony.between(outCamelot, inCamelot)

    // Per-style frame math

    // The frame at [progress] (0..1) for a transition of [beatsTotal] beats at [bpm]
    // (canonical BPM of the outgoing deck; 0 when unknown, which only affects the beat-grid
    // styles). Unknown tempo degrades Rise/Melt/Slam to their progress-based fallbacks instead
    // of crashing - a transition must never need analysis to be safe.
    fun frame(
        style: MixTransitionStyle,
        progress: Float,
        beatsTotal: Int,
        bpm: Float,
    ): TransitionFrame {
        val p = progress.coerceIn(0f, 1f)
        return when (style) {
            MixTransitionStyle.AUTO -> frame(DEFAULT_STYLE, p, beatsTotal, bpm) // resolved before playback
            MixTransitionStyle.BLEND -> blend(p)
            MixTransitionStyle.RISE -> rise(p, beatsTotal, bpm)
            MixTransitionStyle.FADE -> fade(p)
            MixTransitionStyle.WAVE -> wave(p, beatsTotal)
            MixTransitionStyle.MELT -> melt(p, beatsTotal, bpm)
            MixTransitionStyle.SLAM -> slam(p)
            MixTransitionStyle.DISSOLVE -> dissolve(p, beatsTotal)
        }
    }

    // Blend - the classic harmonic club blend.
    //
    // Levels ride an equal-power pair, so the perceived loudness inside the fade is flat
    // (sin/cos sum to unity for uncorrelated program material). The bass is *not* faded -
    // two kick drums summing in the sub band is the "+5 dB of mud" every DJ dreads - instead
    // the outgoing deck's low shelf ducks progressively (its energy leaves through the
    // Linkwitz-Riley shelf at 150 Hz) while the incoming deck's shelf opens up in mirror image.
    // At the midpoint bar line the stem bass hard-swaps: deck A keeps only mids/highs, deck B
    // owns everything below 160 Hz from that moment. Mid and high content crossfades along the
    // exponential-weighted equal-power curve.
    private fun blend(p: Float): TransitionFrame {
        val fadeIn = equalPowerIn(p)
        val fadeOut = equalPowerOut(p)
        // Duck ramps are offset from the fades so the bass handover is audible but not abrupt:
        // A's low end starts leaving at 30 % and is fully gone by 60 %, B's arrives mirrored.
        val duckOut = smoothstep(0.30f, 0.60f, p)
        val duckIn = 1f - smoothstep(0.40f, 0.70f, p)
        val swapped = p >= 0.5f
        return TransitionFrame(
            fadeIn = fadeIn,
            fadeOut = fadeOut,
            bassDuckOut = duckOut,
            bassDuckIn = duckIn,
            bassSwapOut = swapped,
            bassSwapIn = swapped,
        )
    }

    // Rise - build-up and drop.
    //
    // For the first half of the transition the outgoing deck runs through a resonant high-pass
    // whose cutoff sweeps 40 Hz -> 3 kHz exponentially. Exponential (geometric) because pitch
    // perception is logarithmic: a linear sweep spends 80 % of its time in the last octave and
    // sounds like a stuck filter. Resonance Q grows alongside so the sweeping band whistles
    // thinner and thinner. At the first beat of the next bar (the drop, computed from the beat
    // grid) deck A is cut to silence instantly and deck B enters at full level - the classic
    // "kill the filter, drop the record" move.
    private fun rise(p: Float, beatsTotal: Int, bpm: Float): TransitionFrame {
        val sweepBeats = 8
        val beatFraction = if (bpm > 0f && beatsTotal > 0) {
            (sweepBeats.toFloat() / beatsTotal).coerceIn(0.25f, 0.75f)
        } else 0.5f

        // Before the drop: sweep A up, keep B silent (the drop is what brings B in).
        if (p < beatFraction) {
            val t = p / beatFraction // 0..1 across the sweep
            // Geometric sweep 40 Hz -> 3000 Hz: f(t) = f0 * (f1/f0)^t, one octave every ~14 %
            val cutoff = 40f * (3000f / 40f).pow(t)
            val q = 0.7f + 4.3f * t // resonance grows to ~5 for the thin whistle
            return TransitionFrame(
                fadeIn = 0f,
                fadeOut = 1f,
                sweepHz = cutoff,
                sweepQ = q,
            )
        }

        // The drop: A hard-cut, B full. One frame of limiter hold (50 ms) glues the seam -
        // the incoming downbeat plus the outgoing cut can momentarily sum, and the limiter
        // is what keeps a DC-step click out of the ear.
        return TransitionFrame(
            fadeIn = 1f,
            fadeOut = 0f,
            limiterHoldMs = 50,
        )
    }

    // Fade - the plain musical crossfade on an equal-power curve:
    // in(t) = sin(πt/2), out(t) = cos(πt/2). For uncorrelated stereo programs the sum of the
    // two powers is constant, so the transition is loudness-flat end to end. This is also the
    // shape every other preset falls back on for its level envelope.
    private fun fade(p: Float): TransitionFrame = TransitionFrame(
        fadeIn = equalPowerIn(p),
        fadeOut = equalPowerOut(p),
    )

    // Wave - phase-frequency wave.
    //
    // Both decks run through a band-pass whose center frequency is swept by a common LFO, in
    // counter-phase: while A's window opens on the low band, B's opens on the high band, and
    // they swap on every half-cycle. Musically the pair "takes turns" breathing - one deck
    // muffled low while the other rings bright - over [beatsTotal] beats (2 full LFO cycles so
    // the weave completes a whole number of times). The LFO drives frequency, not gain, so the
    // loudness stays flat and only the timbre shuttles.
    private fun wave(p: Float, beatsTotal: Int): TransitionFrame {
        val fadeIn = equalPowerIn(p)
        val fadeOut = equalPowerOut(p)
        // Two full cycles across the transition. The LFO starts at its extreme, so the very
        // first frame already has the pair at opposite ends of the band - A opens on the high
        // side while B opens on the low side - and they cross in the middle twice per cycle.
        val lfoPhase = 2f * PI.toFloat() * 2f * p
        // Centers sweep geometrically around 866 Hz (the geometric middle of 300..2400),
        // one deck up while the other goes down, ±0.9 octaves each.
        val center = 866f
        val spread = center * 0.9f // ±~0.9 octave-ish span in Hz
        val centerOut = center * 2f.pow(cos(lfoPhase) * 0.9f)      // ±0.9 octaves
        val centerIn = center * 2f.pow(-cos(lfoPhase) * 0.9f)
        // Sweep bounds the center away from the extremes so the band never collapses onto DC
        // or Nyquist: keep 300 Hz .. 2400 Hz regardless of LFO.
        val clampHz: (Float) -> Float = { it.coerceIn(300f, 2400f) }
        // The spread keeps Q wide enough to stay musical at both extremes.
        val q = 1.2f + spread / center * 0.4f
        return TransitionFrame(
            fadeIn = fadeIn,
            fadeOut = fadeOut,
            bassDuckOut = 0.35f * fadeIn, // keep the low end from stacking while the bands weave
            bassDuckIn = 0.35f * fadeOut,
            sweepHz = clampHz(centerOut),
            sweepHzIn = clampHz(centerIn),
            sweepQ = q,
        )
    }

    // Melt - tape-stop / pitch-drop.
    //
    // In the last 4 beats the outgoing deck's playback speed (and with it the pitch - that is
    // what "turntable power-off" means: speed and pitch are one motor) is eased down by a
    // fourth-power curve, the shape a physical platter actually follows once the motor is
    // disengaged (exponential-ish decay dominated by its inertia; a quartic matches it within
    // a couple of percent and stops at a mathematically exact 0). The incoming deck starts
    // exactly on the downbeat of the next bar at full level, so the drop lands on the One.
    //
    // Wall-clock honesty: the deck is slowed by `speed`, so "4 beats remaining" in deck A's
    // musical time is 4·period/speed in wall time - the speed schedule below is expressed
    // against musical progress and the engine feeds it musical progress, which keeps the melt
    // exactly 4 beats long however slow the platter gets
    private fun melt(p: Float, beatsTotal: Int, bpm: Float): TransitionFrame {
        val meltBeats = 4
        val meltStart = if (bpm > 0f && beatsTotal > 0) {
            (1f - meltBeats.toFloat() / beatsTotal).coerceIn(0f, 0.9f)
        } else 0.75f

        if (p < meltStart) {
            return TransitionFrame(
                fadeIn = 0f,
                fadeOut = 1f,
            )
        }

        val t = ((p - meltStart) / (1f - meltStart)).coerceIn(0f, 1f)
        // Quartic ease-out: v(t) = (1 - t)^4. Fast at first (the finger leaves the platter),
        // crawling at the end (bearing friction) - and exactly 0 at t = 1, so the deck stops
        // on the frame, not somewhere in the buffer.
        val speed = (1f - t).pow(4).coerceAtLeast(0f)
        return TransitionFrame(
            fadeIn = if (t >= 1f) 1f else 0f,
            fadeOut = if (t >= 1f) 0f else 1f,
            // Speed AND pitch together: vinyl emulation. `speedOut` is applied as both
            // parameters of PlaybackParameters by the engine.
            speedOut = speed,
        )
    }

    // Slam - the straight cut
    //
    // A is full until the cut point (0.5 - the engine aligns that with beat 1 of a bar), then
    // instantly silent; B mirrors it. No fades: the entire musical justification is the cut
    // landing on the One. What makes a slam clean rather than clicky is the 50 ms of safety
    // limiter across the seam (two full-level decks summing for one buffer can hit +6 dB, and
    // a hard step in a DC-offset waveform is an audible tick) - [TransitionFrame.limiterHoldMs].
    private fun slam(p: Float): TransitionFrame = if (p < 0.5f) {
        TransitionFrame(fadeIn = 0f, fadeOut = 1f)
    } else {
        TransitionFrame(fadeIn = 1f, fadeOut = 0f, limiterHoldMs = 50)
    }

    // Dissolve - the echo tail
    //
    // Deck A never gets faded by a gain curve; instead its "tail" is frozen and left to decay
    // like a delay line: from 0.35 onward its level follows an exponential decay with a
    // half-life of a bar (8 beats), which is exactly how a feedback-delay tail dies (each
    // repeat × feedback < 1). Deck B enters *underneath* the tail from the start - its level
    // reaches full as the tail is 12 dB down, so the new track appears to rise out of the
    // echo of the old one. The high shelf of the tail darkens as it decays (High-Shelf
    // decay): the duck processor's shelf sits at 150 Hz for bass, and the tail envelope
    // below includes a brightness factor that the engine translates onto the shelf drive.
    private fun dissolve(p: Float, beatsTotal: Int): TransitionFrame {
        // Tail begins at 35 % of the transition and dies over the remaining span.
        val tailStart = 0.35f
        val fadeIn = equalPowerIn((p / 0.65f).coerceIn(0f, 1f)) // B arrives under the tail
        return if (p < tailStart) {
            TransitionFrame(fadeIn = fadeIn, fadeOut = 1f)
        } else {
            val t = ((p - tailStart) / (1f - tailStart)).coerceIn(0f, 1f)
            // One half-life per bar of the transition: with beatsTotal spanning 2 bars this
            // gives −12 dB at the cut and −24 dB by the end - the shape of a real tail.
            val halfLives = (beatsTotal.coerceAtLeast(1) / 8f) * t * 2f
            val tail = 2f.pow(-halfLives * 3.32f / 2f) // ~ −10 dB per half-life, softened
            TransitionFrame(
                fadeIn = fadeIn,
                fadeOut = tail.coerceIn(0f, 1f),
                echoTailOut = 1f - t, // engine drives the shelf darker as this falls
                bassDuckOut = smoothstep(0.4f, 0.8f, p), // tail loses its bass first
            )
        }
    }

    // Tempo release (Sonic/ExoPlayer speed ramp-back)

    // One step of the tempo release schedule.
    //
    // @param atMs wall-clock offset from the end of the transition.
    // @param speed the playbackParameters.speed to apply at that moment.
    data class SpeedStep(val atMs: Long, val speed: Float)

    // The schedule that returns the incoming deck from matched tempo to its own tempo after
    // the transition, per the spec: spread over 32 beats, changing by at most 0.5 %/s.
    //
    // Sonic (ExoPlayer's built-in time-stretcher) can follow `playbackParameters.speed`
    // continuously; what would be audible is not the stretch itself but a step in it. So the
    // release is a staircase whose steps are all below the audibility threshold:
    // stepCount = totalChange / (0.5 %/s · stepPeriod).
    //
    // @param fromSpeed speed during the blend (the matched tempo), e.g. 1.06.
    // @param toSpeed the track's own tempo, 1.0.
    // @param bpm canonical BPM for beat math (0 -> fall back to a 4 s ramp).
    // @param beats release length in beats (spec: 32).
    fun tempoReleaseSchedule(
        fromSpeed: Float,
        toSpeed: Float,
        bpm: Float,
        beats: Int = 32,
    ): List<SpeedStep> {
        val start = fromSpeed.coerceIn(0.5f, 2f)
        val end = toSpeed.coerceIn(0.5f, 2f)
        val totalChange = abs(start - end)
        if (totalChange < 0.0005f) return emptyList()

        // Ramp length in wall-clock ms. 32 beats at bpm; unknown bpm degrades to 4 s.
        val rampMs = if (bpm > 0f) (beats * 60_000f / bpm).toLong().coerceIn(2_000L, 30_000L) else 4_000L

        // Max 0.5 % of speed per second -> the whole change needs at least change/0.005 seconds.
        val minMsForRate = (totalChange / 0.005f * 1000f).toLong()
        val effectiveRampMs = maxOf(rampMs, minMsForRate)

        // A step every 250 ms keeps the staircase under Sonic's smoothing and far under hearing.
        val stepPeriodMs = 250L
        val stepCount = (effectiveRampMs / stepPeriodMs).toInt().coerceIn(4, 200)
        val steps = ArrayList<SpeedStep>(stepCount)
        for (i in 1..stepCount) {
            val frac = i.toFloat() / stepCount
            // Linear in speed (Sonic's stretch ratio is what the ear tracks), not in pitch.
            val speed = start + (end - start) * frac
            steps.add(SpeedStep(atMs = (effectiveRampMs * frac).toLong(), speed = speed))
        }
        return steps
    }

    // Shared curve helpers

    // Equal-power fade-in: sin(πt/2).
    fun equalPowerIn(t: Float): Float = sin(t.coerceIn(0f, 1f) * PI.toFloat() / 2f)

    // Equal-power fade-out: cos(πt/2).
    fun equalPowerOut(t: Float): Float = cos(t.coerceIn(0f, 1f) * PI.toFloat() / 2f)

    // Hermite smoothstep, the click-free 0->1 ramp used for all duck envelopes.
    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    // RMS-to-linear loudness match for the incoming deck, so a −14 LUFS track does not jump
    // out of a −9 LUFS one inside the blend. Power domain: gain = 10^((target − actual)/20),
    // clamped to the useful DJ range. 0 dB when either value is unknown.
    fun loudnessMatchGain(targetLufs: Float?, actualLufs: Float?): Float {
        if (targetLufs == null || actualLufs == null) return 1f
        if (targetLufs.isNaN() || actualLufs.isNaN()) return 1f
        val deltaDb = (targetLufs - actualLufs).coerceIn(-10f, 10f)
        return 10f.pow(deltaDb / 20f)
    }

}
