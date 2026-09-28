package com.alananasss.kittytune.data.filter

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Reads SoundCloud's waveform: about 1800 peak values across the whole track, scaled to 0.02..1 by
 * WaveformRepository. That is a loudness envelope without any frequency information, so everything here is a weak
 * hint that only counts next to other evidence: an envelope pinned at full scale from start to finish, peaks that
 * barely change from one column to the next (smeared transients), no quieter section anywhere, and a song that
 * stops at full volume instead of ending.
 */
internal object AiWaveformSignals {

    /** Shape checks need a real waveform; SoundCloud's has ~1800 columns. */
    private const val MIN_COLUMNS_FOR_SHAPE = 300
    private const val SEGMENTS = 16

    fun analyze(samples: FloatArray): List<AiSignal> {
        val n = samples.size
        if (n < 16) return emptyList()
        // Intros and outros say nothing about the master, so the shape checks skip the outer 3 %.
        val edge = if (n >= MIN_COLUMNS_FOR_SHAPE) (n * 0.03).toInt() else 0
        val body = samples.copyOfRange(edge, n - edge)

        var sum = 0.0
        var saturated = 0
        var steps = 0.0
        for (i in body.indices) {
            sum += body[i]
            if (body[i] >= 0.95f) saturated++
            if (i > 0) steps += abs(body[i] - body[i - 1])
        }
        val mean = sum / body.size
        var variance = 0.0
        for (value in body) {
            val d = value - mean
            variance += d * d
        }
        val spread = sqrt(variance / body.size)
        val saturation = saturated.toDouble() / body.size
        val sorted = body.sortedArray()
        val p10 = sorted[(sorted.size - 1) * 10 / 100]
        val p50 = sorted[(sorted.size - 1) / 2]
        val p90 = sorted[(sorted.size - 1) * 90 / 100]

        val signals = mutableListOf<AiSignal>()
        val flat = spread < 0.10 || (p90 - p10 < 0.15f && p50 > 0.6f)
        val dynamicsPoints = when {
            flat && saturation > 0.25 -> 18
            flat -> 12
            saturation > 0.25 && spread < 0.14 -> 12
            else -> 0
        }
        if (dynamicsPoints > 0) {
            signals += AiSignal(
                "WAVEFORM_DYNAMICS", dynamicsPoints, AiSignalFamily.WAVEFORM,
                String.format(Locale.US, "spread %.3f, %.0f%% at full scale", spread, saturation * 100)
            )
        }
        if (n < MIN_COLUMNS_FOR_SHAPE) return signals

        if (mean > 0.45) {
            val roughness = steps / (body.size - 1) / mean
            if (roughness < 0.03) {
                signals += AiSignal(
                    "WAVEFORM_SMEARED", 6, AiSignalFamily.WAVEFORM,
                    String.format(Locale.US, "column-to-column change %.3f", roughness)
                )
            }
        }

        // Compare the loudness of the song's sections, leaving out the first and last sixteenth.
        val sectionLevels = (1 until SEGMENTS - 1).map { segment ->
            val from = body.size * segment / SEGMENTS
            val to = body.size * (segment + 1) / SEGMENTS
            var total = 0.0
            for (i in from until to) total += body[i]
            total / (to - from)
        }
        val contrast = sectionLevels.max() - sectionLevels.min()
        if (mean > 0.4 && contrast < 0.08) {
            signals += AiSignal(
                "WAVEFORM_NO_SECTIONS", 5, AiSignalFamily.WAVEFORM,
                String.format(Locale.US, "section contrast %.3f", contrast)
            )
        }

        val tailLength = max(3, (n * 0.015).toInt())
        var tailSum = 0.0
        var tailMin = 1f
        for (i in n - tailLength until n) {
            tailSum += samples[i]
            tailMin = min(tailMin, samples[i])
        }
        if (tailSum / tailLength >= 0.55 && tailMin >= 0.35f && samples[n - 1] >= 0.45f) {
            signals += AiSignal("WAVEFORM_HARD_CUT", 8, AiSignalFamily.WAVEFORM, "ends at full level")
        }
        return signals
    }
}
