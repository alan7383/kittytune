/**
 * Developed by Jason-Marshall Fastner, Germany <jasonfastner@protonmail.com>
 * Questions, feedback, or beat-matching debates? Feel free to reach out via email!
 *
 * Note: Cats always land on their feet, and with this engine, your transitions will too.
 */
package com.alananasss.kittytune.audio.automix

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Classifies which beat of the grid is "the One".
 *
 * A tempo estimate alone only says *how often* a beat happens, never *which* one starts the bar.
 * Dropping an incoming track on beat 3 is rhythmically indistinguishable from beat 1 to a
 * peak detector and completely wrong to a listener, so the beat grid is only half the answer.
 *
 * Two phases are recovered here, both relative to the beat grid:
 * - [Downbeat.barPhaseBeats]: which beat index (0..beatsPerBar-1) carries beat 1.
 * - [Downbeat.phrasePhaseBars]: which bar index (0..barsPerPhrase-1) opens a phrase.
 *
 * The evidence is the drum pattern that 4/4 club music is built from:
 * kick on the downbeat, snare/clap on the backbeats (2 and 4), and a spectral shift at
 * phrase boundaries where arrangements change.
 */
object DownbeatTracker {

    /**
     * Onset strength per STFT hop, split into the bands that carry rhythmic information.
     * All arrays share a length and [frameRate].
     */
    class BandOnsets(
        /** < 150 Hz — kick drum body. */
        val low: FloatArray,
        /** 150–800 Hz — snare body, bass notes. */
        val mid: FloatArray,
        /** 2–8 kHz — snare crack, claps, hi-hats. */
        val high: FloatArray,
        /** Full-band detrended flux, used for tempo estimation. */
        val full: FloatArray,
        val frameRate: Float,
    ) {
        val size: Int get() = full.size
    }

    /**
     * @param barPhaseBeats beat offset of the first downbeat, relative to beat 0 of the grid.
     * @param phrasePhaseBars bar offset of the first phrase start, relative to that downbeat.
     * @param confidence 0..1 margin between the winning bar phase and the runner-up.
     *   Below ~0.15 the pattern is ambiguous (e.g. pure four-on-the-floor with no backbeat).
     */
    data class Downbeat(
        val barPhaseBeats: Int,
        val phrasePhaseBars: Int,
        val confidence: Float,
    )

    /** Onsets are sampled over this fraction of a beat period on each side of the grid line. */
    private const val BEAT_TOLERANCE = 0.12f

    /**
     * Relative weights of the four evidence terms. Backbeat placement outranks kick placement
     * because four-on-the-floor makes every beat look like a downbeat to the low band, while a
     * clap on 2 and 4 stays discriminative.
     */
    private const val W_KICK = 1.0f
    private const val W_BACKBEAT = 1.3f
    private const val W_SNARE_ON_ONE = 0.8f
    private const val W_NOVELTY = 0.6f

    fun estimate(
        onsets: BandOnsets,
        periodFrames: Float,
        beatPhaseFrames: Float,
        beatsPerBar: Int = 4,
        barsPerPhrase: Int = 4,
    ): Downbeat {
        if (periodFrames <= 0f || onsets.size <= 0 || beatsPerBar <= 0) {
            return Downbeat(0, 0, 0f)
        }

        val beatFrames = beatGrid(onsets.size, periodFrames, beatPhaseFrames)
        if (beatFrames.size < beatsPerBar * 2) return Downbeat(0, 0, 0f)

        val tolerance = max(1, (periodFrames * BEAT_TOLERANCE).roundToInt())
        val kick = sampleOnsets(onsets.low, beatFrames, tolerance)
        val snare = sampleSnare(onsets.mid, onsets.high, beatFrames, tolerance)
        val novelty = beatNovelty(onsets, beatFrames, periodFrames)

        val barPhase = pickBarPhase(kick, snare, novelty, beatsPerBar)
        val phrasePhase = pickPhrasePhase(
            novelty = novelty,
            kick = kick,
            barPhaseBeats = barPhase.first,
            beatsPerBar = beatsPerBar,
            barsPerPhrase = barsPerPhrase,
        )
        return Downbeat(barPhase.first, phrasePhase, barPhase.second)
    }

    /** Frame index of every beat line that fits inside the analysed window. */
    private fun beatGrid(frameCount: Int, periodFrames: Float, beatPhaseFrames: Float): IntArray {
        val phase = ((beatPhaseFrames % periodFrames) + periodFrames) % periodFrames
        val count = ((frameCount - phase) / periodFrames).toInt()
        if (count <= 0) return IntArray(0)
        return IntArray(count) { (phase + it * periodFrames).roundToInt().coerceIn(0, frameCount - 1) }
    }

    /** Peak onset strength in a tolerance window around each beat line. */
    private fun sampleOnsets(band: FloatArray, beatFrames: IntArray, tolerance: Int): FloatArray {
        val out = FloatArray(beatFrames.size)
        for (i in beatFrames.indices) {
            val center = beatFrames[i]
            val lo = max(0, center - tolerance)
            val hi = min(band.size - 1, center + tolerance)
            var peak = 0f
            for (f in lo..hi) if (band[f] > peak) peak = band[f]
            out[i] = peak
        }
        return out
    }

    /**
     * Snare/clap strength: the mid band carries the drum body, the high band the crack.
     * A kick leaks into mid but not into high, so requiring both suppresses kick false positives.
     */
    private fun sampleSnare(
        mid: FloatArray,
        high: FloatArray,
        beatFrames: IntArray,
        tolerance: Int,
    ): FloatArray {
        val midPeaks = sampleOnsets(mid, beatFrames, tolerance)
        val highPeaks = sampleOnsets(high, beatFrames, tolerance)
        return FloatArray(beatFrames.size) { sqrt(midPeaks[it] * highPeaks[it]) }
    }

    /**
     * How much the spectral balance changes at each beat, measured between the beat interval
     * before and after the line. Arrangement changes — a drop, a break, a new 8-bar section —
     * land on phrase boundaries, so this peaks there.
     */
    private fun beatNovelty(
        onsets: BandOnsets,
        beatFrames: IntArray,
        periodFrames: Float,
    ): FloatArray {
        val span = max(1, periodFrames.roundToInt())
        val out = FloatArray(beatFrames.size)
        for (i in beatFrames.indices) {
            val center = beatFrames[i]
            val beforeLo = max(0, center - span)
            val afterHi = min(onsets.size - 1, center + span)
            if (center - beforeLo < span / 2 || afterHi - center < span / 2) continue

            var diff = 0f
            for (band in arrayOf(onsets.low, onsets.mid, onsets.high)) {
                var before = 0f
                for (f in beforeLo until center) before += band[f]
                before /= max(1, center - beforeLo)
                var after = 0f
                for (f in center..afterHi) after += band[f]
                after /= max(1, afterHi - center + 1)
                diff += abs(after - before)
            }
            out[i] = diff
        }
        return out
    }

    /**
     * Scores every candidate downbeat position against the 4/4 drum template and returns
     * the winner with a confidence margin.
     */
    private fun pickBarPhase(
        kick: FloatArray,
        snare: FloatArray,
        novelty: FloatArray,
        beatsPerBar: Int,
    ): Pair<Int, Float> {
        val kickAt = FloatArray(beatsPerBar)
        val snareAt = FloatArray(beatsPerBar)
        val noveltyAt = FloatArray(beatsPerBar)

        for (c in 0 until beatsPerBar) {
            kickAt[c] = meanAtPhase(kick, c, beatsPerBar)
            snareAt[c] = meanAtPhase(snare, c, beatsPerBar)
            noveltyAt[c] = meanAtPhase(novelty, c, beatsPerBar)
        }

        val zKick = standardize(kickAt)
        val zSnare = standardize(snareAt)
        val zNovelty = standardize(noveltyAt)

        val scores = FloatArray(beatsPerBar)
        for (c in 0 until beatsPerBar) {
            // Backbeats sit halfway through the bar and one beat before the next downbeat.
            // In 4/4 that is beats 2 and 4; the formula generalises to other meters.
            val backbeat = if (beatsPerBar >= 4) {
                0.5f * (zSnare[(c + 1) % beatsPerBar] + zSnare[(c + beatsPerBar - 1) % beatsPerBar])
            } else {
                zSnare[(c + beatsPerBar - 1) % beatsPerBar]
            }
            scores[c] = W_KICK * zKick[c] +
                W_BACKBEAT * backbeat -
                W_SNARE_ON_ONE * zSnare[c] +
                W_NOVELTY * zNovelty[c]
        }

        var best = 0
        for (c in 1 until beatsPerBar) if (scores[c] > scores[best]) best = c
        var runnerUp = Float.NEGATIVE_INFINITY
        for (c in 0 until beatsPerBar) if (c != best && scores[c] > runnerUp) runnerUp = scores[c]

        // Scores are z-scored, so a margin of ~2 is already a decisive win.
        val margin = if (runnerUp.isFinite()) ((scores[best] - runnerUp) / 2f).coerceIn(0f, 1f) else 0f
        return best to margin
    }

    /**
     * Picks which bar opens a phrase. Phrase boundaries carry the arrangement changes, so the
     * bar whose downbeats accumulate the most novelty — and the most kick energy, since breaks
     * end on a phrase line — wins.
     */
    private fun pickPhrasePhase(
        novelty: FloatArray,
        kick: FloatArray,
        barPhaseBeats: Int,
        beatsPerBar: Int,
        barsPerPhrase: Int,
    ): Int {
        if (barsPerPhrase <= 1) return 0

        val downbeatNovelty = ArrayList<Float>()
        val downbeatKick = ArrayList<Float>()
        var beat = barPhaseBeats
        while (beat < novelty.size) {
            downbeatNovelty.add(novelty[beat])
            downbeatKick.add(kick[beat])
            beat += beatsPerBar
        }
        // Fewer than two phrases of material means there is nothing to compare.
        if (downbeatNovelty.size < barsPerPhrase * 2) return 0

        val noveltyAt = FloatArray(barsPerPhrase)
        val kickAt = FloatArray(barsPerPhrase)
        for (p in 0 until barsPerPhrase) {
            noveltyAt[p] = meanAtPhase(downbeatNovelty.toFloatArray(), p, barsPerPhrase)
            kickAt[p] = meanAtPhase(downbeatKick.toFloatArray(), p, barsPerPhrase)
        }

        val zNovelty = standardize(noveltyAt)
        val zKick = standardize(kickAt)

        var best = 0
        var bestScore = Float.NEGATIVE_INFINITY
        for (p in 0 until barsPerPhrase) {
            val score = zNovelty[p] + 0.4f * zKick[p]
            if (score > bestScore) {
                bestScore = score
                best = p
            }
        }
        return best
    }

    /** Mean of every element whose index is congruent to [phase] modulo [modulus]. */
    private fun meanAtPhase(values: FloatArray, phase: Int, modulus: Int): Float {
        var sum = 0f
        var count = 0
        var i = phase
        while (i < values.size) {
            sum += values[i]
            count++
            i += modulus
        }
        return if (count == 0) 0f else sum / count
    }

    /** Zero-mean, unit-variance rescale so the evidence terms can be weighted against each other. */
    private fun standardize(values: FloatArray): FloatArray {
        if (values.isEmpty()) return values
        var mean = 0f
        for (v in values) mean += v
        mean /= values.size
        var variance = 0f
        for (v in values) variance += (v - mean) * (v - mean)
        variance /= values.size
        val sd = sqrt(variance)
        if (sd <= 1e-9f) return FloatArray(values.size)
        return FloatArray(values.size) { (values[it] - mean) / sd }
    }
}
