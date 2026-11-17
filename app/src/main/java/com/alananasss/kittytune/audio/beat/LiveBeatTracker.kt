package com.alananasss.kittytune.audio.beat

import com.alananasss.kittytune.audio.automix.BeatAnalyzer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Real-time beat tracker, fed with the decoded PCM of the playing track.
 *
 * Why it lands on the beat: every onset is stamped in *media time* (the position in the track),
 * anchored on the exact position Media3 hands to audio processors on each flush ([onFlush]). The
 * beat grid therefore lives in the same timeline as `player.currentPosition`, which already
 * accounts for the output latency: predicting the next audible beat is a subtraction in one
 * timeline, with no cross-thread latency guess (the haptics' weak spot). If a gapless track switch
 * moves the timeline without a flush, [audibleStreamMs] notices the impossible lead and re-anchors.
 *
 * The analysis is the automix [BeatAnalyzer]'s (so both agree), run on a sliding window:
 * 1. STFT (1024 / hop 512), log-magnitude spectral flux = onset strength (full band for the
 *    tempo; kick + snare bodies <= 800 Hz for the phase, since broadband hats sit on offbeats).
 * 2. Every ~1 s, on the last ~8 s: local-mean detrend, autocorrelation tempo with harmonic support
 *    ([BeatAnalyzer.estimateTempoPeriod]), octave resolution with the perceptual tempo prior
 *    ([BeatAnalyzer.resolveTempoOctave]), then a comb search for where the beats fall.
 * 3. Hysteresis against octave flips; "quiet" when onsets and loudness collapse (intro, break,
 *    outro) so the cat can calm down and burst back on the drop.
 *
 * One tracker per player slot (KittyTune has two players for automix crossfades).
 */
class LiveBeatTracker {

    data class Snapshot(
        val bpm: Float,
        val periodMs: Double,
        /** Stream time (ms since the last flush) of a recent beat; media time = origin + this. */
        val beatMs: Double,
        /** Peak prominence of the tempo (same measure as the automix analysis). */
        val confidence: Float,
        val quiet: Boolean,
        val madeAt: Long,
    ) {
        val confident get() = confidence >= MIN_CONFIDENCE && bpm > 0f
    }

    companion object {
        const val MIN_CONFIDENCE = 0.3f
        private const val FFT = 1024
        private const val HOP = 512
        private const val WINDOW_SECONDS = 8.0
        private const val ANALYZE_EVERY_SECONDS = 1.0
        private const val MIN_LEAD_MS = -50.0
        private const val MAX_LEAD_MS = 1_500.0
        private val HANN = FloatArray(FFT) { 0.5f - 0.5f * cos(2.0 * Math.PI * it / FFT).toFloat() }
    }

    @Volatile var snapshot: Snapshot? = null
        private set

    // ---- media time bookkeeping
    @Volatile private var originMs = 0.0
    @Volatile private var writtenMs = 0.0
    @Volatile private var lastLeadMs = 300.0

    // ---- analysis state (audio thread only)
    private var sampleRate = 44_100
    private var frameRate = sampleRate / HOP.toFloat()
    private var windowFrames = 690
    private var analyzeEvery = 86
    private var framesSinceFlush = 0L

    private val ring = FloatArray(FFT)
    private var ringPos = 0
    private var sinceHop = 0
    private var primed = 0
    private val re = FloatArray(FFT)
    private val im = FloatArray(FFT)
    private val prevMag = FloatArray(FFT / 2)
    private var hopSq = 0.0

    private var flux = FloatArray(0)
    /** Kick + snare bodies (<= 800 Hz): where the beats fall. Hats live on the offbeats. */
    private var beatFlux = FloatArray(0)
    private var loud = FloatArray(0)
    private var lowBin = 3
    private var midBin = 18
    private var times = DoubleArray(0)
    private var head = 0
    private var filled = 0
    private var sinceAnalysis = 0

    private var tempoFrames = 0.0
    private var pendingFrames = 0.0
    private var pendingVotes = 0

    fun configure(rate: Int) {
        sampleRate = rate.coerceAtLeast(8_000)
        frameRate = sampleRate / HOP.toFloat()
        windowFrames = (WINDOW_SECONDS * frameRate).roundToInt()
        analyzeEvery = (ANALYZE_EVERY_SECONDS * frameRate).roundToInt()
        flux = FloatArray(windowFrames)
        beatFlux = FloatArray(windowFrames)
        loud = FloatArray(windowFrames)
        val binHz = sampleRate.toFloat() / FFT
        lowBin = (150f / binHz).toInt().coerceAtLeast(1)
        midBin = (800f / binHz).toInt().coerceAtLeast(lowBin + 1)
        times = DoubleArray(windowFrames)
        resetAnalysis()
    }

    /** A seek or new stream: [positionMs] is the media time of the next sample. */
    fun onFlush(positionMs: Double) {
        originMs = positionMs
        writtenMs = positionMs
        framesSinceFlush = 0
        resetAnalysis()
    }

    private fun resetAnalysis() {
        ring.fill(0f); ringPos = 0; sinceHop = 0; primed = 0; hopSq = 0.0
        prevMag.fill(0f)
        head = 0; filled = 0; sinceAnalysis = 0
        tempoFrames = 0.0; pendingFrames = 0.0; pendingVotes = 0
        snapshot = null
    }

    /** One mono sample in [-1, 1], on the audio thread. */
    fun push(sample: Float) {
        ring[ringPos] = sample
        ringPos = (ringPos + 1) % FFT
        hopSq += sample * sample
        framesSinceFlush++
        if (primed < FFT) primed++
        if (++sinceHop >= HOP) {
            sinceHop = 0
            if (primed >= FFT) stftFrame()
            hopSq = 0.0
        }
    }

    fun onBufferDone() {
        writtenMs = originMs + framesSinceFlush * 1000.0 / sampleRate
    }

    private fun stftFrame() {
        for (i in 0 until FFT) {
            re[i] = ring[(ringPos + i) % FFT] * HANN[i]
            im[i] = 0f
        }
        BeatAnalyzer.fft(re, im)
        var sum = 0f
        var low = 0f
        var mid = 0f
        for (b in 0 until FFT / 2) {
            val mag = ln(1f + 10f * sqrt(re[b] * re[b] + im[b] * im[b]))
            val d = mag - prevMag[b]
            prevMag[b] = mag
            if (d > 0) {
                sum += d
                if (b <= lowBin) low += d else if (b <= midBin) mid += d
            }
        }
        if (flux.isEmpty()) return
        flux[head] = sum
        // a kick only lights a few bins; scale it up so it outweighs the broadband hats
        beatFlux[head] = low * (FFT / 2f / (lowBin + 1)) * 0.25f + mid
        loud[head] = (hopSq / HOP).toFloat()
        // centre of the analysed window, in stream time
        times[head] = (framesSinceFlush - FFT / 2.0) * 1000.0 / sampleRate
        head = (head + 1) % windowFrames
        if (filled < windowFrames) filled++
        if (++sinceAnalysis >= analyzeEvery && filled >= windowFrames * 3 / 4) {
            sinceAnalysis = 0
            analyze()
        }
    }

    private fun idx(i: Int) = (head - filled + i + windowFrames) % windowFrames

    private fun analyze() {
        val n = filled
        val raw = FloatArray(n) { flux[idx(it)] }
        // local-mean detrend, as in the automix analysis
        val meanWindow = 8
        val env = FloatArray(n)
        for (i in 0 until n) {
            val lo = max(0, i - meanWindow); val hi = min(n - 1, i + meanWindow)
            var m = 0f
            for (j in lo..hi) m += raw[j]
            env[i] = max(0f, raw[i] - m / (hi - lo + 1))
        }
        val quiet = isQuiet()
        val est = BeatAnalyzer.estimateTempoPeriod(env, frameRate)
        if (est == null) { publishQuiet(quiet); return }
        val (rawPeriod, confidence) = est
        val period0 = BeatAnalyzer.resolveTempoOctave(env, frameRate, rawPeriod).toDouble()

        // hysteresis: keep the tempo unless a new one is seen twice in a row; ignore octave flips
        if (tempoFrames == 0.0) tempoFrames = period0
        else {
            val ratio = period0 / tempoFrames
            when {
                abs(ratio - 1.0) < 0.04 -> tempoFrames = 0.7 * tempoFrames + 0.3 * period0
                abs(ratio - 2.0) < 0.08 || abs(ratio - 0.5) < 0.04 -> Unit
                abs(period0 / max(pendingFrames, 1e-9) - 1.0) < 0.04 -> if (++pendingVotes >= 2) { tempoFrames = period0; pendingVotes = 0 }
                else -> { pendingFrames = period0; pendingVotes = 1 }
            }
        }
        val period = tempoFrames

        // phase: on the kick/snare envelope (full-band flux locks onto offbeat hats)
        val rawBeat = FloatArray(n) { beatFlux[idx(it)] }
        val beatEnv = FloatArray(n)
        for (i in 0 until n) {
            val lo = max(0, i - meanWindow); val hi = min(n - 1, i + meanWindow)
            var m = 0f
            for (j in lo..hi) m += rawBeat[j]
            beatEnv[i] = max(0f, rawBeat[i] - m / (hi - lo + 1))
        }
        // comb search, recent beats weigh more
        var bestPhase = 0.0
        var bestScore = -1.0
        val steps = (period * 2).toInt().coerceAtLeast(1)
        for (p in 0 until steps) {
            val phase = p / 2.0
            var s = 0.0
            var k = 0
            var pos = (n - 1) - phase
            while (pos >= 1) {
                val i0 = pos.toInt(); val f = pos - i0
                val v = beatEnv[i0] * (1 - f) + beatEnv[min(i0 + 1, n - 1)] * f
                s += v * (1.0 - 0.04 * k).coerceAtLeast(0.25)
                pos -= period
                k++
            }
            if (s > bestScore) { bestScore = s; bestPhase = phase }
        }
        val beatPos = ((n - 1) - bestPhase).coerceIn(0.0, (n - 1).toDouble())
        val i0 = beatPos.toInt(); val f = beatPos - i0
        val beatMs = times[idx(i0)] * (1 - f) + times[idx(min(i0 + 1, n - 1))] * f
        val periodMs = period * 1000.0 / frameRate
        snapshot = Snapshot(
            bpm = (60_000.0 / periodMs).toFloat(),
            periodMs = periodMs,
            beatMs = beatMs,
            confidence = confidence,
            quiet = quiet,
            madeAt = System.currentTimeMillis(),
        )
    }

    private fun publishQuiet(quiet: Boolean) {
        snapshot = snapshot?.copy(quiet = quiet, madeAt = System.currentTimeMillis())
    }

    /** Intro, break or outro: the last ~1.5 s are much calmer than the window. */
    private fun isQuiet(): Boolean {
        val n = filled
        val recent = (1.5 * frameRate).roundToInt().coerceIn(1, n)
        var fAll = 0.0; var fRecent = 0.0; var lAll = 0.0; var lRecent = 0.0
        for (i in 0 until n) {
            val fv = flux[idx(i)].toDouble(); val lv = loud[idx(i)].toDouble()
            fAll += fv; lAll += lv
            if (i >= n - recent) { fRecent += fv; lRecent += lv }
        }
        val fluxRatio = (fRecent / recent) / max(fAll / n, 1e-9)
        val loudRatio = (lRecent / recent) / max(lAll / n, 1e-12)
        return fluxRatio < 0.45 || loudRatio < 0.2
    }

    /**
     * Media time heard now, from the player's position. Also checks the anchor: a gapless switch
     * can move the timeline without a flush, which shows up as an impossible output lead.
     */
    fun audibleStreamMs(playerPositionMs: Long): Double {
        val lead = writtenMs - playerPositionMs
        if (lead < MIN_LEAD_MS || lead > MAX_LEAD_MS) {
            val shift = playerPositionMs + lastLeadMs - writtenMs
            originMs += shift
            writtenMs += shift
        } else {
            lastLeadMs = 0.8 * lastLeadMs + 0.2 * lead
        }
        return playerPositionMs.toDouble()
    }

    /**
     * (ms until the next beat the listener will hear, at least [minAheadMs] away; beat index from
     * the estimate's reference beat). Null without a fresh, confident grid.
     */
    fun nextBeat(playerPositionMs: Long, minAheadMs: Long = 0): Pair<Long, Long>? {
        val s = snapshot?.takeIf { it.confident && System.currentTimeMillis() - it.madeAt < 3_000 } ?: return null
        val heard = audibleStreamMs(playerPositionMs)
        val ref = originMs + s.beatMs
        val k = kotlin.math.ceil((heard + minAheadMs - ref) / s.periodMs).toLong()
        return (ref + k * s.periodMs - heard).toLong() to k
    }
}
