package com.alananasss.kittytune.ui.game

import android.content.Context
import com.alananasss.kittytune.data.WaveformRepository
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** Picks a start position from the loudest 20-second stretch of a track's waveform. */
internal object TrackHighlights {
    private const val MIN_DURATION_MS = 90_000L
    private const val WINDOW_MS = 20_000L
    private const val LEAD_IN_MS = 1_500L

    suspend fun startMs(context: Context, track: Track): Long {
        val duration = track.actualDurationMs
        if (duration < MIN_DURATION_MS) return 0L
        val samples = WaveformRepository.getCachedWaveform(track.id)
            ?: withTimeoutOrNull(2_500L) {
                // Use the existing metadata waveform cache. Prefetch never decodes the audio.
                WaveformRepository.prefetchWaveform(context, track)
                WaveformRepository.getCachedWaveform(track.id)
            }
        return startMs(duration, samples)
    }

    internal fun startMs(durationMs: Long, samples: FloatArray?): Long {
        if (durationMs < MIN_DURATION_MS) return 0L

        if (samples == null || samples.isEmpty() || samples.none { it > 0f }) {
            return (durationMs * 0.3).toLong().coerceIn(10_000L, durationMs - 45_000L)
        }

        val window = (WINDOW_MS * samples.size / durationMs.toDouble()).roundToInt().coerceIn(1, samples.size)
        var sum = 0.0
        for (index in 0 until window) sum += samples[index]
        var bestSum = sum
        var bestOffset = 0
        for (index in window until samples.size) {
            sum += samples[index] - samples[index - window]
            if (sum > bestSum) {
                bestSum = sum
                bestOffset = index - window + 1
            }
        }
        return (bestOffset * durationMs / samples.size - LEAD_IN_MS).coerceAtLeast(0L)
    }
}
