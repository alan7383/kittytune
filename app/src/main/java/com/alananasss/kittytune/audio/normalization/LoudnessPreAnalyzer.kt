package com.alananasss.kittytune.audio.normalization

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.alananasss.kittytune.audio.automix.ExoBeatDecoder
import com.alananasss.kittytune.data.AudioScannerManager
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Measures the upcoming tracks of the queue while the current one plays, so each starts at its own
 * level instead of learning it on air — what SoundCloud Desktop does before every track, done ahead
 * of time here because a phone streams rather than holding the whole file.
 *
 * A small queue worker: the next [LOOKAHEAD] tracks, one at a time and in play order, skipping any
 * whose loudness is already on file ([LoudnessStore] is the cache, kept across launches). A queue
 * edit only replaces what is still to do; the track being measured is finished if it is still
 * coming up, and dropped at once if it is not.
 *
 * Three ten-second windows spread over each track (a fifth, half and three quarters of the way in)
 * stand for the whole: an intro or an outro alone would misjudge it. Decoded through the player's
 * own cache, so the bytes fetched are ones playback reads back later, not a second download. The
 * figure is filed as an estimate, and the track's own play replaces it with a measurement.
 */
object LoudnessPreAnalyzer {

    private const val TAG = "LoudnessPreAnalyzer"
    /** The track playing and the three after it. */
    private const val LOOKAHEAD = 4
    private const val WINDOW_MS = 10_000L
    private const val MAX_ATTEMPTS = 2
    private val WINDOW_POSITIONS = floatArrayOf(0.2f, 0.5f, 0.75f)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var plan: List<Track> = emptyList()
    private var worker: Job? = null
    private var inFlightKey: String? = null
    /** Tracks that could not be measured this session, so a dead stream is not retried forever. */
    private val attempts = HashMap<String, Int>()

    /** [upcoming]: the track playing, then the ones after it, in play order. */
    @Synchronized
    fun prepare(context: Context, upcoming: List<Track>) {
        plan = upcoming
            .filter { it.source != "local" }
            .distinctBy { it.id }
            .take(LOOKAHEAD)
        val inFlight = inFlightKey
        if (inFlight != null && plan.none { it.id.toString() == inFlight }) {
            worker?.cancel()
            worker = null
            inFlightKey = null
        }
        if (worker?.isActive == true) return
        if (nextToMeasure() == null) return
        val app = context.applicationContext
        worker = scope.launch { drain(app) }
    }

    /** The first planned track not yet on file and not given up on. Called with the lock held. */
    private fun nextToMeasure(): Track? = plan.firstOrNull { track ->
        val key = track.id.toString()
        LoudnessStore.get(key) == null && (attempts[key] ?: 0) < MAX_ATTEMPTS
    }

    private suspend fun drain(context: Context) {
        val self = coroutineContext[Job]
        while (self?.isActive == true) {
            val track = synchronized(this) {
                nextToMeasure()?.also { inFlightKey = it.id.toString() }
            } ?: break
            val key = track.id.toString()
            val ok = runCatching { analyze(context, track, key) }.getOrDefault(false)
            synchronized(this) {
                inFlightKey = null
                if (!ok && self?.isActive == true) attempts[key] = (attempts[key] ?: 0) + 1
            }
        }
        synchronized(this) { if (worker == self) worker = null }
    }

    @OptIn(UnstableApi::class)
    private suspend fun analyze(context: Context, track: Track, key: String): Boolean {
        val self = coroutineContext[Job]
        val stream = runCatching { StreamResolver.resolveStreamWithDrm(context, track) }.getOrNull() ?: return false
        if (stream.url.isBlank() || self?.isActive != true) return false

        val durationMs = track.actualDurationMs
        val starts = if (durationMs >= 60_000L) {
            WINDOW_POSITIONS.map { (durationMs * it).toLong() }
        } else {
            listOf(0L)
        }
        val windowMs = if (durationMs in 1 until 60_000L) minOf(durationMs, 30_000L) else WINDOW_MS

        val windows = ArrayList<FloatArray>()
        var sampleRate = 0
        var channels = 1
        for (start in starts) {
            if (self?.isActive != true) return false
            val pcm = runCatching {
                ExoBeatDecoder.decodeMono(
                    context = context,
                    streamUrl = stream.url,
                    licenseAuthToken = stream.licenseAuthToken,
                    seekToMs = start,
                    maxDurationMs = windowMs,
                    timeoutMs = 15_000L,
                    shouldCancel = { self?.isActive != true },
                    // Left and right kept apart: a mono mix of a wide track reads up to 3 dB quiet.
                    stereo = true,
                )
            }.getOrNull() ?: continue
            if (pcm.samples.isEmpty()) continue
            if (sampleRate == 0) {
                sampleRate = pcm.sampleRate
                channels = pcm.channels
            }
            if (pcm.sampleRate == sampleRate && pcm.channels == channels) windows += pcm.samples
        }
        if (windows.isEmpty() || sampleRate == 0 || self?.isActive != true) return false

        val measured = AudioScannerManager.measureWindows(windows, sampleRate, channels) ?: return false
        val seconds = windows.sumOf { it.size }.toFloat() / channels / sampleRate
        LoudnessStore.put(key, LoudnessStore.Entry(measured.first, measured.second, seconds, LoudnessStore.Source.ESTIMATE))
        Log.d(TAG, "Estimated ${track.title}: ${"%.1f".format(measured.first)} LUFS, peak ${"%.1f".format(measured.second)} dB over ${seconds.toInt()} s")
        return true
    }
}
