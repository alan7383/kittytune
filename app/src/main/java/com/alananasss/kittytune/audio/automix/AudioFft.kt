package com.alananasss.kittytune.audio.automix

import kotlin.math.cos

/** Shared FFT helpers for the automix analyzers. */
internal object AudioFft {

    /** Periodic Hann window of [size] samples. */
    fun hannWindow(size: Int): FloatArray =
        FloatArray(size) { 0.5f - 0.5f * cos(2.0 * Math.PI * it / size).toFloat() }

    /** In-place iterative radix-2 FFT. Arrays must share a power-of-two length. */
    fun transform(re: FloatArray, im: FloatArray) = BeatAnalyzer.fft(re, im)
}
