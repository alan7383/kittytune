package com.alananasss.kittytune.data.audio

import com.alananasss.kittytune.ui.player.audio.EqualizerAudioProcessor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Fits imported biquads to the app's fixed bands rather than copying overlapping band gains. */
internal object ParametricEqImport {
    private const val SAMPLE_RATE_HZ = 48_000.0
    private const val MAX_GAIN_DB = 12.0
    private const val MAX_ERROR_DB = 2.5
    private const val MAX_FILTERS = 64
    private const val DERIVATIVE_STEP = 0.15

    private val filterLine = Regex("""^\s*Filter\s+\d+\s*:\s*(ON|OFF)\s*(.*?)\s*$""", RegexOption.IGNORE_CASE)
    private val filterPrefix = Regex("""^\s*Filter\s+\d+\s*:""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
    private val number = """[+-]?(?:\d+(?:\.\d*)?|\.\d+)"""
    private val filterSpec = Regex(
        """(PK|PEQ|LSC|LS|HSC|HS)\s+Fc\s+($number)\s*Hz\s+Gain\s+($number)\s*dB(?:\s+Q\s+($number))?""",
        RegexOption.IGNORE_CASE,
    )
    private val preampLine = Regex("""^\s*Preamp\s*:\s*($number)\s*dB\s*$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))

    fun recognizes(text: String): Boolean =
        !text.contains("GraphicEQ:", ignoreCase = true) && filterPrefix.containsMatchIn(text)

    fun fit(text: String): Pair<List<Float>, Float> {
        val filters = mutableListOf<Filter>()
        for (line in text.lineSequence()) {
            val clean = line.substringBefore('#').trim()
            if (!filterPrefix.containsMatchIn(clean)) continue
            val entry = requireNotNull(filterLine.matchEntire(clean)) { "Invalid ParametricEQ filter" }
            if (entry.groupValues[1].equals("OFF", ignoreCase = true)) continue
            val spec = requireNotNull(filterSpec.matchEntire(entry.groupValues[2])) { "Unsupported ParametricEQ filter" }
            val type = spec.groupValues[1].uppercase()
            val frequency = spec.groupValues[2].toDouble()
            val gain = spec.groupValues[3].toDouble()
            val q = spec.groupValues[4].takeIf(String::isNotEmpty)?.toDouble() ?: 0.70710678
            require(frequency in 10.0..20_000.0 && gain in -48.0..48.0 && q in 0.1..30.0) {
                "ParametricEQ filter is out of range"
            }
            require(filters.size < MAX_FILTERS) { "Too many ParametricEQ filters" }
            filters.add(Filter(type, frequency, q, gain))
        }

        val targetFilters = filters.map { coefficients(it.type, it.frequency, it.q, it.gain) }
        val centers = EqualizerAudioProcessor.BAND_FREQUENCIES
        val fixedQ = EqualizerAudioProcessor.BAND_Q_FACTORS
        val frequencies = ((0..144).map { 20.0 * 1_000.0.pow(it / 144.0) } +
            centers.map(Float::toDouble) + filters.map(Filter::frequency)).distinct()
        val bins = frequencies.map { frequency ->
            val angle = 2.0 * PI * frequency / SAMPLE_RATE_HZ
            Bin(cos(angle), sin(angle), cos(2.0 * angle), sin(2.0 * angle))
        }
        val desired = DoubleArray(bins.size) { index ->
            targetFilters.sumOf { it.response(bins[index]) }
        }
        val gains = DoubleArray(centers.size)
        val actual = DoubleArray(bins.size)
        val baseline = DoubleArray(bins.size)
        val direction = DoubleArray(bins.size)

        // Coordinate descent on the actual cascaded response, in log-spaced frequency bins.
        // Each update changes one band and is accepted only when its fit improves.
        repeat(18) {
            for (band in centers.indices) {
                fun peak(gain: Double) = coefficients("PK", centers[band].toDouble(), fixedQ[band].toDouble(), gain)
                val current = peak(gains[band])
                val plus = peak(gains[band] + DERIVATIVE_STEP)
                val minus = peak(gains[band] - DERIVATIVE_STEP)
                var numerator = 0.0
                var denominator = 0.0
                var oldError = 0.0
                for (index in bins.indices) {
                    val bin = bins[index]
                    baseline[index] = actual[index] - current.response(bin)
                    direction[index] = (plus.response(bin) - minus.response(bin)) / (2.0 * DERIVATIVE_STEP)
                    val error = desired[index] - actual[index]
                    numerator += direction[index] * error
                    denominator += direction[index] * direction[index]
                    oldError += error * error
                }
                if (denominator <= 0.0) continue
                var step = numerator / denominator
                for (attempt in 0 until 3) {
                    val nextGain = (gains[band] + step).coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB)
                    val next = peak(nextGain)
                    var newError = 0.0
                    for (index in bins.indices) {
                        val value = baseline[index] + next.response(bins[index])
                        val error = desired[index] - value
                        newError += error * error
                        direction[index] = value
                    }
                    if (newError + 1e-8 < oldError) {
                        direction.copyInto(actual)
                        gains[band] = nextGain
                        break
                    }
                    step *= 0.5
                }
            }
        }
        require(bins.indices.all { abs(desired[it] - actual[it]) <= MAX_ERROR_DB }) {
            "Profile cannot be mapped to the fixed EQ bands accurately"
        }

        val originalPreamp = preampLine.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val peakDb = (actual.maxOrNull() ?: 0.0).coerceAtLeast(0.0)
        require(peakDb <= MAX_GAIN_DB) { "The EQ would exceed available preamp headroom" }
        return gains.map(Double::toFloat) to minOf(originalPreamp, -peakDb).coerceIn(-MAX_GAIN_DB, 0.0).toFloat()
    }

    private data class Filter(val type: String, val frequency: Double, val q: Double, val gain: Double)
    private data class Bin(val cos: Double, val sin: Double, val cos2: Double, val sin2: Double)

    private data class Biquad(
        val b0: Double, val b1: Double, val b2: Double, val a0: Double, val a1: Double, val a2: Double,
    ) {
        fun response(bin: Bin): Double {
            val br = b0 + b1 * bin.cos + b2 * bin.cos2
            val bi = b1 * bin.sin + b2 * bin.sin2
            val ar = a0 + a1 * bin.cos + a2 * bin.cos2
            val ai = a1 * bin.sin + a2 * bin.sin2
            return 10.0 * log10((br * br + bi * bi) / (ar * ar + ai * ai))
        }
    }

    private fun coefficients(type: String, frequency: Double, q: Double, gain: Double): Biquad {
        val amplitude = 10.0.pow(gain / 40.0)
        val angle = 2.0 * PI * frequency / SAMPLE_RATE_HZ
        val cosine = cos(angle)
        val alpha = sin(angle) / (2.0 * q)
        val sum = amplitude + 1.0
        val difference = amplitude - 1.0
        val shelf = 2.0 * sqrt(amplitude) * alpha
        return when {
            type.startsWith("LS") -> Biquad(
                amplitude * (sum - difference * cosine + shelf),
                2.0 * amplitude * (difference - sum * cosine),
                amplitude * (sum - difference * cosine - shelf),
                sum + difference * cosine + shelf,
                -2.0 * (difference + sum * cosine),
                sum + difference * cosine - shelf,
            )
            type.startsWith("HS") -> Biquad(
                amplitude * (sum + difference * cosine + shelf),
                -2.0 * amplitude * (difference + sum * cosine),
                amplitude * (sum + difference * cosine - shelf),
                sum - difference * cosine + shelf,
                2.0 * (difference - sum * cosine),
                sum - difference * cosine - shelf,
            )
            else -> Biquad(
                1.0 + alpha * amplitude, -2.0 * cosine, 1.0 - alpha * amplitude,
                1.0 + alpha / amplitude, -2.0 * cosine, 1.0 - alpha / amplitude,
            )
        }
    }
}
