package com.alananasss.kittytune.ui.game

import com.alananasss.kittytune.data.network.ProxyManager
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where a swallowed track should start playing: the loudest ~20 s window of its SoundCloud
 * waveform — the same idea as the official feed playing a track from its best moment — with a
 * "skip the intro" fallback when there is no waveform data (local files, YouTube, etc.).
 */
object TrackHighlights {

    private const val FETCH_TIMEOUT_MS = 2_500L
    private const val WINDOW_MS = 20_000L
    /** Tracks shorter than this have no highlight worth seeking to. */
    private const val MIN_DURATION_MS = 90_000L

    private val cache = object : LinkedHashMap<Long, Long>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Long>?): Boolean = size > 64
    }

    /** Best-moment start position (ms) for [track]; cheap after the first computation. */
    suspend fun startMs(track: Track): Long {
        cache[track.id]?.let { return it }
        val duration = track.actualDurationMs
        val value = if (duration < MIN_DURATION_MS) {
            0L
        } else {
            val fromWaveform = track.waveformUrl?.takeIf { it.isNotBlank() }?.let { url ->
                runCatching { withTimeout(FETCH_TIMEOUT_MS) { fromWaveform(url, duration) } }.getOrNull()
            }
            fromWaveform ?: fallback(duration)
        }
        cache[track.id] = value
        return value
    }

    /** No waveform: skip the intro — hooks almost never live in the first third. */
    private fun fallback(durationMs: Long): Long =
        (durationMs * 0.3).toLong().coerceIn(10_000L, max(10_000L, durationMs - 45_000L))

    /** Sliding-window peak over the SoundCloud waveform JSON (samples span the whole track). */
    private suspend fun fromWaveform(url: String, durationMs: Long): Long = withContext(Dispatchers.IO) {
        val body = ProxyManager.getOkHttpClient()
            .newCall(okhttp3.Request.Builder().url(url).build())
            .execute()
            .use { resp -> resp.body?.string() }
            ?: return@withContext fallback(durationMs)
        val samples = JSONObject(body).getJSONArray("samples")
        val n = samples.length()
        if (n == 0) return@withContext fallback(durationMs)
        val energy = FloatArray(n) { i -> samples.optDouble(i, 0.0).toFloat().coerceAtLeast(0f) }

        val window = max(1, min(n, (WINDOW_MS * n / durationMs.toFloat()).roundToInt()))
        var sum = 0f
        for (i in 0 until window) sum += energy[i]
        var bestSum = sum
        var bestStart = 0
        for (i in window until n) {
            sum += energy[i] - energy[i - window]
            if (sum > bestSum) {
                bestSum = sum
                bestStart = i - window + 1
            }
        }
        // Start a breath before the peak so the hook does not begin mid-note.
        val lead = max(0, (1_500L * n / durationMs.toFloat()).roundToInt())
        val start = max(0, bestStart - lead)
        start * durationMs / n
    }
}
