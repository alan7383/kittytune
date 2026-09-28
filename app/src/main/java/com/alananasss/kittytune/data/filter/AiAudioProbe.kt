package com.alananasss.kittytune.data.filter

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.alananasss.kittytune.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Listens to the playing track and judges its audio with [AiFakeprint].
 *
 * The audio comes from the player's own processor chain (AiFingerprintAudioProcessor), so nothing is
 * downloaded twice and every source works, local files included. After [SKIP_SECONDS] (audio of the previous
 * track may still be draining) it keeps [CAPTURE_SECONDS] of mono PCM, then works out the fakeprint off the
 * playback thread and publishes the track id on [analyzed]. Results stay cached, so a replay is judged at once.
 */
@OptIn(UnstableApi::class)
object AiAudioProbe {

    private const val TAG = "AiAudioProbe"
    private const val SKIP_SECONDS = 2
    private const val CAPTURE_SECONDS = 20
    private const val MAX_SAMPLE_RATE = 48_000
    private const val MAX_RESULTS = 500

    /** Quieter than about -60 dBFS is silence, and an empty fakeprint would read as AI (the model's bias). */
    private const val MIN_RMS = 1e-3

    private class Capture(val trackId: Long) {
        // Allocated here rather than on the playback thread, where a 4 MB allocation could stutter the audio.
        val samples = FloatArray(MAX_SAMPLE_RATE * CAPTURE_SECONDS)
        var sampleRate = 0
        var target = 0
        var skipped = 0
        var filled = 0
    }

    @Volatile private var capture: Capture? = null
    @Volatile private var activeProcessor: () -> Int = { 0 }
    @Volatile private var model: AiFakeprint.Model? = null
    @Volatile private var dumpDir: File? = null // TEMP-DIAG
    @Volatile private var lastFormat = "" // TEMP-DIAG

    private val results = object : LinkedHashMap<Long, Float>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Float>?): Boolean = size > MAX_RESULTS
    }
    private val _analyzed = MutableSharedFlow<Long>(extraBufferCapacity = 16)

    /** Ids of tracks whose audio was just judged; read the result with [probability]. */
    val analyzed: SharedFlow<Long> = _analyzed.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** [activeProcessorIndex] tells which of the players' processors carries the audio the listener hears. */
    fun init(context: Context, activeProcessorIndex: () -> Int) {
        activeProcessor = activeProcessorIndex
        if (com.alananasss.kittytune.BuildConfig.DEBUG) dumpDir = File(context.cacheDir, "ai_probe") // TEMP-DIAG
        if (model != null) return
        model = try {
            context.resources.openRawResource(R.raw.ai_music_fakeprint).use { AiFakeprint.Model.read(it) }
        } catch (e: Exception) {
            Log.w(TAG, "AI fingerprint model unavailable: ${e.message}")
            null
        }
    }

    /** The probability that a generator rendered [trackId]'s audio, once it has been heard. */
    fun probability(trackId: Long): Float? = synchronized(results) { results[trackId] }

    /** Starts listening for [trackId], unless its audio is already known or being captured. */
    fun listenTo(trackId: Long) {
        if (trackId <= 0 || model == null || probability(trackId) != null || capture?.trackId == trackId) return
        capture = Capture(trackId)
    }

    fun stopListening() {
        capture = null
    }

    /** Called on the playback thread for every PCM buffer of player [processorIndex]; leaves the buffer as it is. */
    fun onPcm(processorIndex: Int, buffer: ByteBuffer, format: AudioProcessor.AudioFormat) {
        val current = capture ?: return
        if (processorIndex != activeProcessor()) return
        val channels = format.channelCount
        val rate = format.sampleRate
        if (channels <= 0 || rate <= 0) return
        val is16Bit = format.encoding == C.ENCODING_PCM_16BIT
        if (!is16Bit && format.encoding != C.ENCODING_PCM_FLOAT) return

        lastFormat = "$rate Hz, $channels ch, ${if (is16Bit) "pcm16" else "float"}" // TEMP-DIAG
        if (current.sampleRate != rate) {
            current.sampleRate = rate
            current.target = minOf(current.samples.size, rate * CAPTURE_SECONDS)
            current.skipped = 0
            current.filled = 0
        }
        val bytesPerSample = if (is16Bit) 2 else 4
        val frameBytes = bytesPerSample * channels
        val start = buffer.position()
        val frames = buffer.remaining() / frameBytes
        val skip = rate * SKIP_SECONDS
        for (frame in 0 until frames) {
            if (current.skipped < skip) {
                current.skipped++
                continue
            }
            if (current.filled >= current.target) break
            val base = start + frame * frameBytes
            var sum = 0f
            for (channel in 0 until channels) {
                sum += if (is16Bit) {
                    buffer.getShort(base + channel * 2) / 32768f
                } else {
                    buffer.getFloat(base + channel * 4)
                }
            }
            current.samples[current.filled++] = sum / channels
        }
        if (current.filled >= current.target && capture === current) {
            capture = null
            scope.launch { analyze(current) }
        }
    }

    /** A seek or a new item on the active player: what was captured may belong elsewhere, so start over. */
    fun onFlush(processorIndex: Int) {
        val current = capture ?: return
        if (processorIndex != activeProcessor()) return
        current.skipped = 0
        current.filled = 0
    }

    private fun analyze(capture: Capture) {
        val model = model ?: return
        try {
            val started = SystemClock.elapsedRealtime()
            dump(capture) // TEMP-DIAG
            val audio = AiFakeprint.resample(capture.samples.copyOf(capture.filled), capture.sampleRate)
            if (AiFakeprint.rms(audio) < MIN_RMS) {
                Log.d(TAG, "track ${capture.trackId}: too quiet to judge")
                return
            }
            val p = model.probability(AiFakeprint.fakeprint(audio)).toFloat()
            synchronized(results) { results[capture.trackId] = p }
            Log.d(TAG, "track ${capture.trackId}: p=${"%.3f".format(p)} (${SystemClock.elapsedRealtime() - started} ms, $lastFormat)")
            _analyzed.tryEmit(capture.trackId)
        } catch (e: Exception) {
            Log.w(TAG, "track ${capture.trackId}: fingerprint failed: ${e.message}")
        }
    }

    // TEMP-DIAG: keeps the last captures as float32 files so they can be checked against the Python reference.
    private fun dump(capture: Capture) {
        val dir = dumpDir ?: return
        try {
            dir.mkdirs()
            dir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(9)?.forEach { it.delete() }
            val bytes = ByteBuffer.allocate(capture.filled * 4).order(ByteOrder.LITTLE_ENDIAN)
            bytes.asFloatBuffer().put(capture.samples, 0, capture.filled)
            File(dir, "${capture.trackId}_${capture.sampleRate}.f32").writeBytes(bytes.array())
        } catch (e: Exception) {
            Log.w(TAG, "dump failed: ${e.message}")
        }
    }
}
