/**
 * Developed by Jason-Marshall Fastner, Germany <jasonfastner@protonmail.com>
 * Questions, feedback, or beat-matching debates? Feel free to reach out via email!
 *
 * Note: Cats always land on their feet, and with this engine, your transitions will too.
 */
package com.alananasss.kittytune.audio.automix

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 4th-order Linkwitz-Riley crossover: one channel of state, a low and a high band.
 *
 * Splitting a band off by subtracting a lowpass from the signal does not work. For a 2nd-order
 * Butterworth lowpass, `x - LP(x)` is not a highpass at all: it leaves -2.8 dB at 80 Hz and
 * actually *boosts* by up to +2 dB above the corner, because the lowpass's phase rotation makes
 * the subtraction constructive there. Killing a track's bass that way leaves most of the kick
 * in place, which is exactly how two decks end up summing to +5 dB in the sub band.
 *
 * Linkwitz-Riley solves this properly. Two identical Butterworth sections in cascade give
 * 24 dB/oct and -6 dB at the corner, and the two bands sum to unity magnitude with a pure
 * allpass phase response — so a crossfade between them has no dip and no bump at any frequency.
 */
class LinkwitzRiley4(sampleRate: Int, crossoverHz: Double) {

    /** RBJ biquad, Direct Form I, with its own two-sample memory. */
    private class Biquad(
        val b0: Float, val b1: Float, val b2: Float,
        val a1: Float, val a2: Float,
    ) {
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        fun process(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x
            y2 = y1; y1 = y
            return y
        }

        fun reset() {
            x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
        }
    }

    private val lp1: Biquad
    private val lp2: Biquad
    private val hp1: Biquad
    private val hp2: Biquad

    init {
        val fs = if (sampleRate > 0) sampleRate.toDouble() else 44100.0
        // Keep the corner below Nyquist with margin; a corner too close to fs/2 makes the
        // bilinear transform's frequency warping blow the coefficients up.
        val f0 = crossoverHz.coerceIn(20.0, fs * 0.45)
        val w0 = 2.0 * PI * f0 / fs
        val cosW = cos(w0)
        val sinW = sin(w0)
        // Butterworth Q. Cascading two of these is what makes it Linkwitz-Riley, not Butterworth.
        val alpha = sinW / (2.0 * 0.70710678)
        val a0 = 1.0 + alpha
        val a1 = (-2.0 * cosW) / a0
        val a2 = (1.0 - alpha) / a0

        val lb0 = ((1.0 - cosW) / 2.0) / a0
        val lb1 = (1.0 - cosW) / a0
        lp1 = Biquad(lb0.toFloat(), lb1.toFloat(), lb0.toFloat(), a1.toFloat(), a2.toFloat())
        lp2 = Biquad(lb0.toFloat(), lb1.toFloat(), lb0.toFloat(), a1.toFloat(), a2.toFloat())

        val hb0 = ((1.0 + cosW) / 2.0) / a0
        val hb1 = (-(1.0 + cosW)) / a0
        hp1 = Biquad(hb0.toFloat(), hb1.toFloat(), hb0.toFloat(), a1.toFloat(), a2.toFloat())
        hp2 = Biquad(hb0.toFloat(), hb1.toFloat(), hb0.toFloat(), a1.toFloat(), a2.toFloat())
    }

    /** Low band of [x]: everything below the crossover, 24 dB/oct. */
    fun low(x: Float): Float = lp2.process(lp1.process(x))

    /** High band of [x]: everything above the crossover, 24 dB/oct. */
    fun high(x: Float): Float = hp2.process(hp1.process(x))

    fun reset() {
        lp1.reset(); lp2.reset(); hp1.reset(); hp2.reset()
    }
}
