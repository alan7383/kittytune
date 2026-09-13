package com.alananasss.kittytune.audio.automix

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline BPM + beat-grid analyzer. Classical DSP, no ML:
 * decode -> mono PCM -> STFT -> spectral flux onset envelope ->
 * autocorrelation tempo estimate -> comb-filter phase for beat offset.
 *
 * Beat times of the track are: firstBeatOffsetMs + k * (60000 / bpm).
 */
object BeatAnalyzer {

    data class Result(
        val bpm: Float,
        val firstBeatOffsetMs: Long,
        val confidence: Float,
        val mixInPointMs: Long? = null,
        val mixOutPointMs: Long? = null,
        /** 0=C, 1=C#, ... 11=B. Null when the chroma signal was too weak to call a key. */
        val keyPitchClass: Int? = null,
        val keyIsMinor: Boolean? = null,
    )

    private const val TAG = "BeatAnalyzer"

    private const val FFT_SIZE = 1024
    private const val HOP_SIZE = 512
    private const val MIN_BPM = 60f
    private const val MAX_BPM = 180f

    /** Analysis window: 18s taken from the middle of the track. */
    private const val WINDOW_US = 18_000_000L

    /** Energy-scan windows for dynamic mix points. */
    private const val HEAD_WINDOW_US = 16_000_000L
    private const val TAIL_WINDOW_US = 24_000_000L

    /** Canonical BPM range; octave-fold estimates into it (61.9 -> 123.8, 160 -> 80...). */
    private const val MIN_CANONICAL_BPM = 70f
    private const val MAX_CANONICAL_BPM = 140f
    private const val ENERGY_BLOCK_MS = 500
    private const val MAX_INTRO_SKIP_MS = 20_000L
    private const val MAX_OUTRO_CUT_MS = 45_000L

    /** Hard cap on bytes fetched for analysis. Keeps automix responsive on slow streams. */
    private const val MAX_FETCH_BYTES = 5L * 1024 * 1024

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun analyzeUri(
        context: Context,
        uri: Uri,
        shouldCancel: () -> Boolean = { false },
    ): Result? =
        analyze(shouldCancel) { extractor -> extractor.setDataSource(context, uri, null) }

    fun analyzeFile(
        filePath: String,
        shouldCancel: () -> Boolean = { false },
    ): Result? =
        analyze(shouldCancel) { extractor -> extractor.setDataSource(filePath) }

    /**
     * @param result null when the (possibly partial) data couldn't be analyzed.
     * @param complete true when the copied bytes are known to be the whole stream —
     *   only then is a failed analysis worth negative-caching.
     */
    class CachedAnalysis(val result: Result?, val complete: Boolean)

    /**
     * Analyzes DRM-protected audio streams (e.g. SoundCloud Widevine CENC) using [ExoBeatDecoder].
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun analyzeDrmStream(
        context: Context,
        streamUrl: String,
        licenseAuthToken: String? = null,
        totalDurationMs: Long = 0L,
        shouldCancel: () -> Boolean = { false },
    ): CachedAnalysis? {
        Log.d(TAG, "Starting DRM stream beat analysis: url=$streamUrl, hasToken=${!licenseAuthToken.isNullOrEmpty()}, durationMs=$totalDurationMs")

        // 1. Decode 18s from position 0 for tempo / key / beat grid
        val pcm = ExoBeatDecoder.decodeMono(
            context = context,
            streamUrl = streamUrl,
            licenseAuthToken = licenseAuthToken,
            seekToMs = 0L,
            maxDurationMs = WINDOW_US / 1000,
            shouldCancel = shouldCancel
        )

        if (pcm == null || pcm.samples.size < FFT_SIZE * 8) {
            Log.w(TAG, "ExoBeatDecoder pass failed or had insufficient samples (${pcm?.samples?.size ?: 0})")
            return null
        }

        if (shouldCancel()) return null
        val key = estimateKey(pcm.samples, pcm.sampleRate)

        if (shouldCancel()) return null
        val flux = spectralFlux(pcm.samples)
        val frameRate = pcm.sampleRate.toFloat() / HOP_SIZE

        val (periodFrames, confidence) = estimateTempoPeriod(flux, frameRate) ?: return null
        val phaseFrames = estimateBeatPhase(flux, periodFrames)

        var periodMs = periodFrames / frameRate * 1000f
        var bpm = 60_000f / periodMs
        while (bpm < MIN_CANONICAL_BPM) {
            bpm *= 2f
            periodMs /= 2f
        }
        while (bpm >= MAX_CANONICAL_BPM) {
            bpm /= 2f
            periodMs *= 2f
        }
        val windowStartMs = pcm.actualStartUs / 1000
        val anchorMs = windowStartMs + (phaseFrames / frameRate * 1000f).roundToLong()
        val firstBeatOffsetMs = (anchorMs % periodMs.roundToLong() + periodMs.roundToLong()) % periodMs.roundToLong()

        // 2. Mix-in point from the decoded samples
        val mixInPointMs = detectMixIn(
            energyEnvelope(pcm.samples, pcm.sampleRate),
            firstBeatOffsetMs,
            periodMs
        )

        // 3. Mix-out point from track outro (if duration is known and track is long enough)
        var mixOutPointMs: Long? = null
        if (totalDurationMs > 45_000L && !shouldCancel()) {
            val tailSeekMs = max(0L, totalDurationMs - (TAIL_WINDOW_US / 1000L))
            try {
                val tailPcm = ExoBeatDecoder.decodeMono(
                    context = context,
                    streamUrl = streamUrl,
                    licenseAuthToken = licenseAuthToken,
                    seekToMs = tailSeekMs,
                    maxDurationMs = TAIL_WINDOW_US / 1000L,
                    timeoutMs = 10_000L,
                    shouldCancel = shouldCancel
                )
                if (tailPcm != null && tailPcm.samples.size >= FFT_SIZE * 4) {
                    val tailEnv = energyEnvelope(tailPcm.samples, tailPcm.sampleRate)
                    val tailStartMs = tailPcm.actualStartUs / 1000L
                    mixOutPointMs = detectMixOut(tailEnv, tailStartMs, totalDurationMs)
                    Log.d(TAG, "DRM stream mix-out point detected: $mixOutPointMs ms")
                }
            } catch (e: Exception) {
                Log.d(TAG, "DRM stream outro scan skipped: ${e.message}")
            }
        }

        val result = Result(bpm, firstBeatOffsetMs, confidence, mixInPointMs, mixOutPointMs, key?.first, key?.second)
        return CachedAnalysis(result, complete = true)
    }

    /**
     * Fetches track stream chunks into a temp file and analyzes that.
     * Fetches up to [MAX_FETCH_BYTES] for quick turnaround.
     */
    fun analyzeStream(
        url: String,
        tempDir: File,
        headers: Map<String, String>? = null,
        context: Context? = null,
        licenseAuthToken: String? = null,
        totalDurationMs: Long = 0L,
        shouldCancel: () -> Boolean = { false },
    ): CachedAnalysis? {
        if (context != null && (!licenseAuthToken.isNullOrEmpty() || url.contains("cenc") || url.contains("encrypted-hls"))) {
            Log.d(TAG, "Directly using ExoBeatDecoder for DRM/CENC stream ($url)")
            return analyzeDrmStream(context, url, licenseAuthToken, totalDurationMs, shouldCancel)
        }

        val tempFile = File(tempDir, "beat_${url.hashCode().toLong() and 0xFFFFFFFFL}.tmp")
        try {
            var copied = 0L
            var reachedEnd = false

            val requestBuilder = Request.Builder().url(url)
            headers?.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder.build()

            val call = httpClient.newCall(request)
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.d(TAG, "Stream fetch for $url failed with HTTP ${response.code}")
                        return null
                    }
                    val body = response.body ?: return null
                    val inputStream = body.byteStream()
                    FileOutputStream(tempFile).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (copied < MAX_FETCH_BYTES) {
                            if (shouldCancel()) {
                                Log.d(TAG, "Stream fetch cancelled at $copied bytes (will analyze partial)")
                                break
                            }
                            val read = inputStream.read(buf, 0, buf.size)
                            if (read < 0) {
                                reachedEnd = true
                                break
                            }
                            out.write(buf, 0, read)
                            copied += read
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Stream fetch for $url stopped at $copied bytes (${e.message})")
            }

            Log.d(TAG, "Stream fetch: $copied bytes, complete=$reachedEnd")
            // Check if fetched file is an HLS playlist (.m3u8)
            if (copied in 1..256 * 1024) {
                val header = try {
                    val bytes = tempFile.readBytes().take(200).toByteArray()
                    String(bytes)
                } catch (_: Exception) { "" }

                if (header.startsWith("#EXTM3U")) {
                    Log.d(TAG, "Fetched file is HLS playlist, checking encryption & segments...")
                    val text = try { tempFile.readText() } catch (_: Exception) { "" }
                    if (text.contains("METHOD=SAMPLE-AES") || text.contains("METHOD=AES-128") || text.contains("init.mp4")) {
                        Log.d(TAG, "HLS playlist is DRM encrypted or fMP4, delegating to ExoBeatDecoder...")
                        if (context != null) {
                            return analyzeDrmStream(context, url, licenseAuthToken, totalDurationMs, shouldCancel)
                        }
                        return null
                    }
                    val segmentLine = text.lines().firstOrNull { it.isNotBlank() && !it.startsWith("#") }?.trim()
                    if (!segmentLine.isNullOrEmpty()) {
                        val segmentUrl = if (segmentLine.startsWith("http")) segmentLine else {
                            val base = url.substringBeforeLast('/') + "/"
                            base + segmentLine
                        }
                        Log.d(TAG, "Fetching HLS audio segment: $segmentUrl")
                        try {
                            val segReq = Request.Builder().url(segmentUrl).build()
                            httpClient.newCall(segReq).execute().use { segResp ->
                                if (segResp.isSuccessful) {
                                    val segBody = segResp.body ?: return null
                                    FileOutputStream(tempFile).use { out ->
                                        segBody.byteStream().copyTo(out)
                                    }
                                    copied = tempFile.length()
                                    reachedEnd = true
                                    Log.d(TAG, "Fetched HLS segment: $copied bytes")
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed fetching HLS segment: ${e.message}")
                            return null
                        }
                    }
                }
            }

            if (copied < 64 * 1024) {
                Log.d(TAG, "Insufficient bytes fetched: $copied")
                return null
            }

            // Proceed to analyze regardless of whether the timeout has fired —
            // the DSP (FFT + autocorrelation) takes only a few ms, and the data
            // cost has already been paid.
            // When partial: pass { false } as shouldCancel to analyze() so the already-expired
            // timeout does not abort the codec decode loop (each decodeMono iteration checks it).
            val isPartial = !reachedEnd
            Log.d(TAG, "Stream fetch: $copied bytes, complete=$reachedEnd → analyzing (partial=$isPartial)")
            val analysisCancel: () -> Boolean = if (isPartial) ({ false }) else shouldCancel
            val result = analyze(analysisCancel, isPartialData = isPartial) { extractor ->
                extractor.setDataSource(tempFile.absolutePath)
            }
            return CachedAnalysis(result, reachedEnd)
        } finally {
            try { tempFile.delete() } catch (_: Exception) {}
        }
    }

    private fun analyze(
        shouldCancel: () -> Boolean = { false },
        isPartialData: Boolean = false,
        setSource: (MediaExtractor) -> Unit,
    ): Result? {
        val extractor = MediaExtractor()
        try {
            if (shouldCancel()) return null
            setSource(extractor)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null

            extractor.selectTrack(trackIndex)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            var actualStartUs = 0L
            var pcm: MonoPcm? = null

            if (!isPartialData) {
                // Full data: analyze from the middle of the track for a representative sample.
                val windowStartUs = max(0L, durationUs / 2 - WINDOW_US / 2)
                extractor.seekTo(windowStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                actualStartUs = max(0L, extractor.sampleTime)
                if (shouldCancel()) return null
                pcm = decodeMono(extractor, format, WINDOW_US, shouldCancel)

                // If decoding at the middle returned too few samples (middle past EOF), retry from 0.
                if (pcm == null || pcm.samples.size < FFT_SIZE * 8) {
                    Log.d(TAG, "decodeMono at middle had ${pcm?.samples?.size ?: 0} samples. Retrying from 0...")
                    extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    actualStartUs = max(0L, extractor.sampleTime)
                    pcm = decodeMono(extractor, format, WINDOW_US, shouldCancel)
                }
            } else {
                // Partial data (e.g. YouTube stream timed-out after ~47s): decode from the start.
                // The middle is past the downloaded bytes and the tail is unavailable.
                Log.d(TAG, "Analyzing partial data from position 0 (isPartialData=true)")
                extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                actualStartUs = max(0L, extractor.sampleTime)
                if (shouldCancel()) return null
                pcm = decodeMono(extractor, format, WINDOW_US, shouldCancel)
            }

            if (pcm == null || pcm.samples.size < FFT_SIZE * 8) {
                Log.d(TAG, "Insufficient PCM frames for beat analysis (${pcm?.samples?.size ?: 0})")
                return null
            }

            if (shouldCancel()) return null
            val key = estimateKey(pcm.samples, pcm.sampleRate)

            if (shouldCancel()) return null
            val flux = spectralFlux(pcm.samples)
            val frameRate = pcm.sampleRate.toFloat() / HOP_SIZE

            val (periodFrames, confidence) = estimateTempoPeriod(flux, frameRate) ?: return null
            val phaseFrames = estimateBeatPhase(flux, periodFrames)

            var periodMs = periodFrames / frameRate * 1000f
            var bpm = 60_000f / periodMs
            // Octave-fold into the canonical range: the beat grid stays valid because
            // doubling/halving the period keeps the same phase anchor.
            while (bpm < MIN_CANONICAL_BPM) {
                bpm *= 2f
                periodMs /= 2f
            }
            while (bpm >= MAX_CANONICAL_BPM) {
                bpm /= 2f
                periodMs *= 2f
            }
            val windowStartMs = actualStartUs / 1000
            val anchorMs = windowStartMs + (phaseFrames / frameRate * 1000f).roundToLong()

            // Extrapolate the periodic grid back to the start of the track.
            val firstBeatOffsetMs = (anchorMs % periodMs.roundToLong() + periodMs.roundToLong()) %
                periodMs.roundToLong()

            // Head pass: skip low-energy intros; start the incoming track on the first
            // sustained-energy downbeat instead.
            // Skipped when isPartialData — we only have the start of the file.
            var mixInPointMs: Long? = null
            if (!isPartialData && durationUs > HEAD_WINDOW_US) {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                decodeMono(extractor, format, HEAD_WINDOW_US, shouldCancel)?.let { head ->
                    mixInPointMs = detectMixIn(
                        energyEnvelope(head.samples, head.sampleRate),
                        firstBeatOffsetMs,
                        periodMs,
                    )
                }
            }

            // Tail pass: detect where the body of the song ends (outro starts) so the
            // transition can begin there instead of a fixed distance from the end.
            // Skipped when isPartialData — tail bytes were never downloaded.
            var mixOutPointMs: Long? = null
            val durationMs = durationUs / 1000
            if (!isPartialData && durationUs > TAIL_WINDOW_US) {
                val tailStartUs = durationUs - TAIL_WINDOW_US
                extractor.seekTo(tailStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val tailActualStartMs = max(0L, extractor.sampleTime) / 1000
                decodeMono(extractor, format, TAIL_WINDOW_US, shouldCancel)?.let { tail ->
                    mixOutPointMs = detectMixOut(
                        energyEnvelope(tail.samples, tail.sampleRate),
                        tailActualStartMs,
                        durationMs,
                    )
                }
            }

            return Result(bpm, firstBeatOffsetMs, confidence, mixInPointMs, mixOutPointMs, key?.first, key?.second)
        } catch (e: Exception) {
            Log.w(TAG, "Beat analysis failed: ${e.message}")
            return null
        } finally {
            extractor.release()
        }
    }

    private class MonoPcm(val samples: FloatArray, val sampleRate: Int)

    private fun decodeMono(
        extractor: MediaExtractor,
        format: MediaFormat,
        maxDurationUs: Long,
        shouldCancel: () -> Boolean = { false },
    ): MonoPcm? {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        val codec = MediaCodec.createDecoderByType(mime)
        val out = ArrayList<FloatArray>()
        var sampleRate = 0
        var decodedUs = 0L
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()

            while (!outputDone && decodedUs < maxDurationUs) {
                if (shouldCancel()) return null
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outIndex >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    if (info.size > 0) {
                        val outFormat = codec.outputFormat
                        val channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val pcmEncoding = if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else AudioFormat.ENCODING_PCM_16BIT

                        val buf = codec.getOutputBuffer(outIndex)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN)

                        if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                            val floats = buf.asFloatBuffer()
                            val frames = floats.remaining() / channels
                            val mono = FloatArray(frames)
                            for (f in 0 until frames) {
                                var acc = 0f
                                for (c in 0 until channels) acc += floats.get(f * channels + c)
                                mono[f] = acc / channels
                            }
                            out.add(mono)
                            decodedUs += frames * 1_000_000L / max(1, sampleRate)
                        } else {
                            val shorts = buf.asShortBuffer()
                            val frames = shorts.remaining() / channels
                            val mono = FloatArray(frames)
                            for (f in 0 until frames) {
                                var acc = 0f
                                for (c in 0 until channels) acc += shorts.get(f * channels + c) / 32768f
                                mono[f] = acc / channels
                            }
                            out.add(mono)
                            decodedUs += frames * 1_000_000L / max(1, sampleRate)
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Decode failed: ${e.message}")
            return null
        } finally {
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            codec.release()
        }
        if (sampleRate == 0 || out.isEmpty()) return null

        val total = out.sumOf { it.size }
        val samples = FloatArray(total)
        var pos = 0
        for (chunk in out) {
            chunk.copyInto(samples, pos)
            pos += chunk.size
        }
        return MonoPcm(samples, sampleRate)
    }

    /** RMS energy per ENERGY_BLOCK_MS block. */
    fun energyEnvelope(samples: FloatArray, sampleRate: Int): FloatArray {
        val blockSize = (sampleRate * ENERGY_BLOCK_MS / 1000).coerceAtLeast(1)
        val numBlocks = samples.size / blockSize
        val env = FloatArray(numBlocks)
        for (b in 0 until numBlocks) {
            var sum = 0f
            val offset = b * blockSize
            for (i in 0 until blockSize) {
                val s = samples[offset + i]
                sum += s * s
            }
            env[b] = sqrt(sum / blockSize)
        }
        return env
    }

    private fun percentile(values: FloatArray, p: Float): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * p).roundToInt().coerceIn(0, sorted.size - 1)]
    }

    /**
     * First block where energy reaches and sustains near body level, snapped forward
     * onto the beat grid. Null when the track starts hot (no intro worth skipping).
     */
    fun detectMixIn(env: FloatArray, firstBeatOffsetMs: Long, periodMs: Float): Long? {
        if (env.size < 8) return null
        val ref = percentile(env, 0.75f)
        if (ref <= 0f) return null

        var candidateBlock = -1
        for (i in 0 until env.size - 4) {
            if (env[i] >= 0.55f * ref &&
                env[i + 1] >= 0.4f * ref &&
                env[i + 2] >= 0.4f * ref &&
                env[i + 3] >= 0.4f * ref
            ) {
                candidateBlock = i
                break
            }
        }
        if (candidateBlock <= 0) return null // starts hot; keep default first-downbeat start

        val candidateMs = candidateBlock.toLong() * ENERGY_BLOCK_MS
        if (candidateMs > MAX_INTRO_SKIP_MS) return null

        // Snap forward to the next downbeat.
        val k = kotlin.math.ceil((candidateMs - firstBeatOffsetMs) / periodMs.toDouble()).toLong()
        return (firstBeatOffsetMs + max(0L, k) * periodMs.toDouble()).roundToLong()
    }

    /**
     * Last moment the tail window is still at body loudness; everything after is outro.
     * Null when the track stays loud to the end (no early mix-out warranted).
     */
    fun detectMixOut(env: FloatArray, windowStartMs: Long, durationMs: Long): Long? {
        if (env.size < 8 || durationMs <= 0) return null
        val ref = percentile(env, 0.75f)
        if (ref <= 0f) return null

        var lastLoudBlock = -1
        for (i in env.indices.reversed()) {
            if (env[i] >= 0.5f * ref) {
                lastLoudBlock = i
                break
            }
        }
        if (lastLoudBlock < 0) return null

        val mixOutMs = windowStartMs + (lastLoudBlock + 1).toLong() * ENERGY_BLOCK_MS
        // Loud almost to the end: nothing to cut.
        if (durationMs - mixOutMs < 3_000) return null
        // Never cut more than MAX_OUTRO_CUT_MS.
        return max(mixOutMs, durationMs - MAX_OUTRO_CUT_MS)
    }

    /** Half-wave-rectified spectral flux per hop, log-compressed magnitudes. */
    fun spectralFlux(samples: FloatArray): FloatArray {
        val window = FloatArray(FFT_SIZE) { 0.5f - 0.5f * cos(2.0 * Math.PI * it / FFT_SIZE).toFloat() }
        val numFrames = (samples.size - FFT_SIZE) / HOP_SIZE
        val bins = FFT_SIZE / 2
        val flux = FloatArray(numFrames)
        val prevMag = FloatArray(bins)
        val re = FloatArray(FFT_SIZE)
        val im = FloatArray(FFT_SIZE)

        for (frame in 0 until numFrames) {
            val offset = frame * HOP_SIZE
            for (i in 0 until FFT_SIZE) {
                re[i] = samples[offset + i] * window[i]
                im[i] = 0f
            }
            fft(re, im)
            var sum = 0f
            for (b in 0 until bins) {
                val mag = ln(1f + 10f * sqrt(re[b] * re[b] + im[b] * im[b]))
                val diff = mag - prevMag[b]
                if (diff > 0) sum += diff
                prevMag[b] = mag
            }
            flux[frame] = sum
        }

        // Subtract local mean so autocorrelation sees onsets, not slow dynamics.
        val meanWindow = (0.5f * FFT_SIZE / HOP_SIZE * 8).roundToInt().coerceAtLeast(8)
        val detrended = FloatArray(numFrames)
        for (i in 0 until numFrames) {
            val lo = max(0, i - meanWindow)
            val hi = min(numFrames - 1, i + meanWindow)
            var mean = 0f
            for (j in lo..hi) mean += flux[j]
            mean /= hi - lo + 1
            detrended[i] = max(0f, flux[i] - mean)
        }
        return detrended
    }

    /**
     * Autocorrelation over the beat-period lag range. Returns (periodInFrames, confidence),
     * favoring candidates whose double period is also supported (down-weights half-period picks).
     */
    fun estimateTempoPeriod(flux: FloatArray, frameRate: Float): Pair<Float, Float>? {
        val minLag = (frameRate * 60f / MAX_BPM).roundToInt()
        val maxLag = (frameRate * 60f / MIN_BPM).roundToInt()
        if (flux.size < maxLag * 2) return null

        val ac = FloatArray(maxLag + 1)
        for (lag in minLag..maxLag) {
            var sum = 0f
            for (i in 0 until flux.size - lag) sum += flux[i] * flux[i + lag]
            ac[lag] = sum / (flux.size - lag)
        }

        var mean = 0f
        for (lag in minLag..maxLag) mean += ac[lag]
        mean /= maxLag - minLag + 1
        if (mean <= 0f) return null

        var bestLag = -1
        var bestScore = 0f
        for (lag in minLag..maxLag) {
            if (lag > minLag && lag < maxLag && (ac[lag] < ac[lag - 1] || ac[lag] < ac[lag + 1])) continue
            var score = ac[lag]
            // Reward candidates whose 2x lag also correlates (true beat vs half-beat).
            val doubleLag = lag * 2
            if (doubleLag <= maxLag) score += 0.5f * ac[doubleLag]
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag < 0) return null

        // Parabolic interpolation around the peak for sub-frame period accuracy.
        val refined = if (bestLag in minLag + 1 until maxLag) {
            val y0 = ac[bestLag - 1]
            val y1 = ac[bestLag]
            val y2 = ac[bestLag + 1]
            val denom = y0 - 2 * y1 + y2
            if (denom != 0f) bestLag + 0.5f * (y0 - y2) / denom else bestLag.toFloat()
        } else bestLag.toFloat()

        // Peak prominence over the median autocorrelation: flat (beatless) material
        // scores near 0, a strong periodic pulse scores near 1.
        val sortedAc = ac.copyOfRange(minLag, maxLag + 1).sorted()
        val median = sortedAc[sortedAc.size / 2]
        val confidence = if (ac[bestLag] > 0f) ((ac[bestLag] - median) / ac[bestLag]).coerceIn(0f, 1f) else 0f
        return refined to confidence
    }

    // Krumhansl-Schmuckler key profiles: relative pull of each scale degree on the tonic.
    private val MAJOR_PROFILE = floatArrayOf(6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f)
    private val MINOR_PROFILE = floatArrayOf(6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f)

    /**
     * Chroma vector (12-bin pitch-class energy) correlated against the Krumhansl-Schmuckler
     * major/minor profiles across all 12 rotations. Returns (pitch class 0=C..11=B, isMinor),
     * or null when there isn't enough tonal energy to call a key (e.g. mostly percussive).
     */
    fun estimateKey(samples: FloatArray, sampleRate: Int): Pair<Int, Boolean>? {
        if (samples.size < FFT_SIZE * 4) return null
        val window = FloatArray(FFT_SIZE) { 0.5f - 0.5f * cos(2.0 * Math.PI * it / FFT_SIZE).toFloat() }
        val hop = FFT_SIZE / 2
        val numFrames = (samples.size - FFT_SIZE) / hop
        if (numFrames < 4) return null

        val chroma = FloatArray(12)
        val re = FloatArray(FFT_SIZE)
        val im = FloatArray(FFT_SIZE)
        val binHz = sampleRate.toFloat() / FFT_SIZE
        // Musically relevant range: skip sub-bass rumble and high-frequency noise.
        val minBin = (65f / binHz).toInt().coerceAtLeast(1)
        val maxBin = (2000f / binHz).toInt().coerceAtMost(FFT_SIZE / 2 - 1)

        for (frame in 0 until numFrames) {
            val offset = frame * hop
            for (i in 0 until FFT_SIZE) {
                re[i] = samples[offset + i] * window[i]
                im[i] = 0f
            }
            fft(re, im)
            for (b in minBin..maxBin) {
                val mag = sqrt(re[b] * re[b] + im[b] * im[b])
                if (mag <= 0f) continue
                val freq = b * binHz
                val midi = 69.0 + 12.0 * (ln(freq / 440.0) / ln(2.0))
                val pitchClass = ((midi.roundToInt() % 12) + 12) % 12
                chroma[pitchClass] += mag
            }
        }

        val sum = chroma.sum()
        if (sum <= 0f) return null
        for (i in chroma.indices) chroma[i] /= sum

        var bestScore = Float.NEGATIVE_INFINITY
        var bestPitchClass = 0
        var bestIsMinor = false
        for (tonic in 0 until 12) {
            val majorScore = correlateChroma(chroma, MAJOR_PROFILE, tonic)
            if (majorScore > bestScore) {
                bestScore = majorScore; bestPitchClass = tonic; bestIsMinor = false
            }
            val minorScore = correlateChroma(chroma, MINOR_PROFILE, tonic)
            if (minorScore > bestScore) {
                bestScore = minorScore; bestPitchClass = tonic; bestIsMinor = true
            }
        }
        return bestPitchClass to bestIsMinor
    }

    private fun correlateChroma(chroma: FloatArray, profile: FloatArray, tonic: Int): Float {
        var sum = 0f
        for (degree in 0 until 12) sum += chroma[(degree + tonic) % 12] * profile[degree]
        return sum
    }

    /** Comb filter: phase (in frames) maximizing summed flux at phase + k*period. */
    fun estimateBeatPhase(flux: FloatArray, periodFrames: Float): Float {
        val period = periodFrames.roundToInt().coerceAtLeast(1)
        var bestPhase = 0
        var bestSum = -1f
        for (phase in 0 until period) {
            var sum = 0f
            var i = phase
            while (i < flux.size) {
                sum += flux[i]
                i += period
            }
            if (sum > bestSum) {
                bestSum = sum
                bestPhase = phase
            }
        }
        return bestPhase.toFloat()
    }

    /** In-place iterative radix-2 FFT. Arrays must be a power-of-two length. */
    fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wRe = cos(ang).toFloat()
            val wIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val vIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
