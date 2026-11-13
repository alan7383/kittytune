package com.alananasss.kittytune.audio.automix

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 *  1. Onset envelope - short-time FFT (1024/512 @ native rate), half-wave-rectified
 *     spectral flux capped at 5 kHz (drum transients live there), detrended by a 0.5 s moving
 *     average and peak-normalized. The detrend is what makes quiet intros and loud drops
 *     contribute equally
 *  2. Tempo - normalized autocorrelation over lags of 60–220 BPM, weighted by a
 *     log-normal prior centered at 125 BPM, then folded into the DJ-canonical 70–180 window
 *  3. Phase - comb filter: every candidate offset of the beat period is scored against
 *     the envelope; the winner is where the beats actually are
 *  4. Downbeat - among the first bars, the strongest onset wins beat 1. This is the
 *     "phrase anchor" the transition engine aligns Blend/Slam handovers to
 *
 * The result plugs straight into the automix plan: [BeatGrid.bpm] replaces unknown metadata
 * BPM, [BeatGrid.firstDownbeatMs] gives the phrase grid, [BeatGrid.confidence] lets the
 * planner fall back to safer presets ([MixTransitionStyle.FADE]) when the analysis is unsure.
 *
 * All of this runs OFF the audio thread - launch it on Dispatchers.Default when a track
 * enters the mix queue and cache the result
 */
object BeatGridAnalyzer {

     // @param bpm canonical tempo, octave-folded into [MIN_BPM]..[MAX_BPM] (70..180)
     // @param beatIntervalMs 60_000 / bpm - the grid step in wall-clock time
     // @param firstBeatMs position of the first detected grid beat (phase result)
     // @param firstDownbeatMs position of estimated beat 1 of a bar - the phrase anchor
     //  transitions should align to. Beats land on firstDownbeatMs + k·beatIntervalMs,
     //  bars on firstDownbeatMs + 4k·beatIntervalMs
     //  @param confidence 0..1 - normalized autocorrelation peak, damped when few beats were
     //  available. Below ~0.25 the caller should not trust the grid
     
    data class BeatGrid(
        val bpm: Float,
        val beatIntervalMs: Float,
        val firstBeatMs: Long,
        val firstDownbeatMs: Long,
        val confidence: Float,
    )

    private const val FFT_SIZE = 1024
    private const val HOP_SIZE = 512

    // DJ-canonical tempo window: fold octaves into it
    const val MIN_BPM = 70f
    const val MAX_BPM = 180f

    // Log-normal tempo prior, in "octaves"
    private const val PRIOR_CENTER_BPM = 125f
    private const val PRIOR_WIDTH_OCTAVES = 1.0f

    private const val FLUX_MAX_HZ = 5000f
    private const val EXT_MIN_BPM = 60f
    private const val EXT_MAX_BPM = 220f
    private const val MIN_ANALYSIS_SECONDS = 4

    /**
     * Analyze a mono PCM buffer. Stereo/multichannel input must be collapsed first
     * ([AudioFft.downmixInterleaved]). Returns null when the buffer is too short (< 4 s),
     * silent, or no periodicity survives the prior — a null is a *recommendation* to keep
     * metadata or fall back to analysis-free presets.
     */
    fun analyze(pcm: FloatArray, sampleRate: Int): BeatGrid? {
        if (sampleRate <= 0 || pcm.size < sampleRate * MIN_ANALYSIS_SECONDS) return null

        val env = onsetEnvelope(pcm, sampleRate) ?: return null
        val fps = sampleRate.toFloat() / HOP_SIZE

        // 1. tempo: normalized autocorrelation × log-normal prior
        val minLag = (60f / EXT_MAX_BPM * fps).toInt().coerceAtLeast(2)
        val maxLag = (60f / EXT_MIN_BPM * fps).toInt().coerceAtMost(env.size - 2)
        if (maxLag <= minLag) return null

        var energy = 0f
        for (v in env) energy += v * v
        if (energy <= 1e-9f) return null // silence: no grid to find

        val r = FloatArray(maxLag + 1)
        for (lag in minLag..maxLag) {
            var s = 0f
            val n = env.size - lag
            var t = 0
            while (t < n) {
                s += env[t] * env[t + lag]
                t++
            }
            r[lag] = s / energy
        }

        var bestLag = minLag
        var bestScore = Float.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            val bpm = 60f * fps / lag
            val octaves = ln(bpm / PRIOR_CENTER_BPM) / PRIOR_WIDTH_OCTAVES
            val prior = exp(-0.5f * octaves * octaves)
            val score = r[lag] * prior
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }

        // Sub-frame lag precision via parabolic interpolation of the correlation peak
        val refinedLag = refinePeak(r, bestLag, minLag, maxLag)
        var bpm = 60f * fps / refinedLag
        // Octave-fold into the canonical DJ window (a 2× relative is the same tempo class)
        while (bpm > MAX_BPM) bpm *= 0.5f
        while (bpm < MIN_BPM) bpm *= 2f
        val intervalMs = 60_000f / bpm

        // 2. phase: comb filter across every candidate offset
        val period = 60f * fps / bpm // beat period in envelope frames
        val offsetCount = period.toInt().coerceAtLeast(1)
        var bestOffset = 0f
        var bestComb = Float.NEGATIVE_INFINITY
        var offset = 0
        while (offset < offsetCount) {
            var s = 0f
            var count = 0
            var pos = offset.toFloat()
            while (pos < env.size) {
                s += sampleAt(env, pos)
                count++
                pos += period
            }
            if (count > 0 && s / count > bestComb) {
                bestComb = s / count
                bestOffset = offset.toFloat()
            }
            offset++
        }

        // 3. downbeat: strongest onset among the first bars wins beat 1
        val beatsAvailable = ((env.size - 1f - bestOffset) / period).toInt()
        if (beatsAvailable < 4) return null
        val scanBeats = minOf(16, beatsAvailable)
        var strongest = 0
        var strongestVal = -1f
        for (k in 0 until scanBeats) {
            val v = sampleAt(env, bestOffset + k * period)
            if (v > strongestVal) {
                strongestVal = v
                strongest = k
            }
        }

        val framesToMs = HOP_SIZE * 1000f / sampleRate
        val firstBeatMs = (bestOffset * framesToMs).toLong()
        val firstDownbeatMs = ((bestOffset + strongest * period) * framesToMs).toLong()
        val lengthPenalty = if (beatsAvailable >= 16) 1f else beatsAvailable / 16f
        val confidence = (r[bestLag].coerceIn(0f, 1f) * lengthPenalty)

        return BeatGrid(
            bpm = bpm,
            beatIntervalMs = intervalMs,
            firstBeatMs = firstBeatMs,
            firstDownbeatMs = firstDownbeatMs,
            confidence = confidence,
        )
    }

     // Half-wave-rectified spectral flux, detrended and normalized - the "where do sounds
     // start" curve everything else works on
     
    private fun onsetEnvelope(pcm: FloatArray, sampleRate: Int): FloatArray? {
        val frames = (pcm.size - FFT_SIZE) / HOP_SIZE + 1
        if (frames < 16) return null
        val window = AudioFft.hannWindow(FFT_SIZE)
        val re = FloatArray(FFT_SIZE)
        val im = FloatArray(FFT_SIZE)
        val bins = FFT_SIZE / 2
        val binHz = sampleRate.toFloat() / FFT_SIZE
        val fluxBins = (FLUX_MAX_HZ / binHz).toInt().coerceIn(8, bins)
        val prev = FloatArray(bins)
        val cur = FloatArray(bins)
        val flux = FloatArray(frames)

        for (f in 0 until frames) {
            val off = f * HOP_SIZE
            for (i in 0 until FFT_SIZE) {
                re[i] = pcm[off + i] * window[i]
                im[i] = 0f
            }
            AudioFft.transform(re, im)
            var sum = 0f
            for (b in 0 until fluxBins) {
                val m = sqrt(re[b] * re[b] + im[b] * im[b])
                cur[b] = m
                val d = m - prev[b]
                if (d > 0f) sum += d // only rising energy is an onset
            }
            for (b in 0 until fluxBins) prev[b] = cur[b]
            flux[f] = sum
        }

        // Detrend with a 0.5 s moving average (kills slow loudness drift, keeps transients),
        // rectify, peak-normalize
        val w = (0.5f * sampleRate / HOP_SIZE).toInt().coerceAtLeast(1)
        val env = FloatArray(frames)
        var maxV = 1e-9f
        for (f in 0 until frames) {
            val lo = maxOf(0, f - w)
            val hi = minOf(frames - 1, f + w)
            var mean = 0f
            for (k in lo..hi) mean += flux[k]
            mean /= (hi - lo + 1)
            val v = flux[f] - mean
            env[f] = if (v > 0f) v else 0f
            if (env[f] > maxV) maxV = env[f]
        }
        for (f in 0 until frames) env[f] /= maxV
        return env
    }

    // Parabolic peak interpolation: sub-frame lag precision from an integer grid
    private fun refinePeak(r: FloatArray, peak: Int, minLag: Int, maxLag: Int): Float {
        if (peak <= minLag || peak >= maxLag) return peak.toFloat()
        val a = r[peak - 1]
        val b = r[peak]
        val c = r[peak + 1]
        val denom = a - 2f * b + c
        if (denom == 0f) return peak.toFloat()
        val delta = 0.5f * (a - c) / denom
        return peak + delta.coerceIn(-0.5f, 0.5f)
    }

    // Linear interpolation on the envelope - the comb filter needs float beat positions
    private fun sampleAt(env: FloatArray, pos: Float): Float {
        if (pos <= 0f) return env[0]
        if (pos >= env.size - 1) return env[env.size - 1]
        val i = pos.toInt()
        val frac = pos - i
        return env[i] * (1f - frac) + env[i + 1] * frac
    }
}
