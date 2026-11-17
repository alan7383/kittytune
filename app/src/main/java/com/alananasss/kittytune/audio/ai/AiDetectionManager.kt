package com.alananasss.kittytune.audio.ai

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.zapret.ZapretManager
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Singleton that manages ArtifactNet LiteRT (TensorFlow Lite) model downloading,
 * multi-point upfront track scanning, background queue pre-analysis, and real-time audio inference.
 *
 * ArtifactNet (4.2M params) detects AI-generated music by extracting forensic
 * residual artifacts from neural audio codecs — generalises across 22+ generators.
 * The full pipeline (STFT → UNet → HPSS → CNN → sigmoid) is executed natively via Google LiteRT.
 *
 * Guarantees a single, immutable verdict per track: once [Status.DONE] is published,
 * [isVerdictFinal] is locked so the score and classification never fluctuate during playback.
 */
object AiDetectionManager {

    private const val TAG = "AiDetectionManager"

    const val CHUNK_SAMPLES = 176_400
    const val TARGET_SR = 44_100

    private const val MODEL_FILENAME = "artifactnet_v94_full.tflite"
    private const val LEGACY_ONNX_FILENAME = "artifactnet_v94_full.onnx"
    private const val LEGACY_DATA_FILENAME = "artifactnet_v94_full.onnx.data"

    private const val URL_MODEL = "https://github.com/alan7383/kittytune/releases/download/v2.68.0/artifactnet_v94_full.tflite"

    const val TOTAL_MODEL_BYTES = 17_460_936L
    const val DEFAULT_THRESHOLD = 0.85f

    fun getEffectiveThreshold(context: Context? = null): Float {
        return runCatching {
            val ctx = context ?: com.alananasss.kittytune.KittyTuneApp.instance
            PlayerPreferences(ctx).aiScoreThreshold
        }.getOrDefault(DEFAULT_THRESHOLD)
    }

    fun getEffectiveWindowSeconds(context: Context? = null): Int {
        return runCatching {
            val ctx = context ?: com.alananasss.kittytune.KittyTuneApp.instance
            PlayerPreferences(ctx).aiDetectionWindow.seconds
        }.getOrDefault(4).coerceIn(1, 4)
    }

    enum class Status { IDLE, ANALYZING, DONE, ERROR }

    data class Result(
        val score: Float,
        val isAi: Boolean,
        val status: Status
    )

    sealed class ModelDownloadState {
        object NotDownloaded : ModelDownloadState()
        data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : ModelDownloadState()
        object Ready : ModelDownloadState()
        data class Error(val message: String) : ModelDownloadState()
    }

    internal data class HlsSegment(
        val url: String,
        val durationMs: Long,
        val startTimeMs: Long
    )

    internal data class HlsPlaylist(
        val initSegmentUrl: String?,
        val segments: List<HlsSegment>,
        val totalDurationMs: Long
    ) {
        fun startIndexFor(positionMs: Long): Int {
            if (segments.isEmpty()) return 0
            if (totalDurationMs > 0 && positionMs >= totalDurationMs) return segments.size
            val match = segments.indexOfFirst { it.startTimeMs + it.durationMs > positionMs }
            return if (match >= 0) match else segments.lastIndex
        }
    }

    private val _result = MutableStateFlow<Result?>(null)
    val result: StateFlow<Result?> = _result.asStateFlow()

    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotDownloaded)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Single-worker lane for model inference so it cannot saturate the shared Default pool. */
    private val inferenceDispatcher = Dispatchers.Default.limitedParallelism(1)
    private var interpreter: Interpreter? = null
    @Volatile private var interpreterLoading = false
    private val engineLock = Any()

    private val resultLock = Any()
    @Volatile private var isVerdictFinal = false
    @Volatile private var isAiConfirmed = false
    @Volatile private var peakScore: Float = 0f
    @Volatile private var hasSeekedDeep = false
    @Volatile private var currentAnalysisEpoch = 0L
    @Volatile private var currentTrackId: Long = 0L
    @Volatile private var currentScanHasResolvedUri: Boolean = false

    private val checkpointScores = mutableListOf<Float>()
    val trackVerdictCache = ConcurrentHashMap<Long, Result>()
    val trackScoreSamplesCache = ConcurrentHashMap<Long, MutableList<Float>>()

    private var analysisJob: Job? = null
    private var upfrontAnalysisJob: Job? = null
    private var queuePreAnalysisJob: Job? = null

    private val httpClient by lazy {
        ZapretManager.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun isVerdictFinal(): Boolean = synchronized(resultLock) { isVerdictFinal }
    fun isAiConfirmed(): Boolean = isAiConfirmed
    fun getPeakScore(): Float = synchronized(resultLock) { peakScore }
    fun isAnalyzingUpfront(): Boolean = upfrontAnalysisJob?.isActive == true
    fun getCachedVerdict(trackId: Long): Result? = trackVerdictCache[trackId]

    fun clearCache() {
        trackVerdictCache.clear()
        trackScoreSamplesCache.clear()
        resetResult()
    }

    private fun getModelsDir(context: Context): File {
        return File(context.filesDir, "models").apply { mkdirs() }
    }

    fun isModelReady(context: Context): Boolean {
        val dir = getModelsDir(context)
        val file = File(dir, MODEL_FILENAME)
        return file.exists() && file.length() > 15_000_000
    }

    fun isModelLoaded(): Boolean = interpreter != null

    fun ensureLoaded() {
        if (interpreter != null || interpreterLoading) return
        val ctx = runCatching { com.alananasss.kittytune.KittyTuneApp.instance }.getOrNull() ?: return
        init(ctx)
    }

    /**
     * Check if model file is on disk and load the interpreter if so.
     */
    fun init(context: Context) {
        if (interpreter != null || interpreterLoading) return
        val ready = isModelReady(context)
        if (!ready) {
            _downloadState.value = ModelDownloadState.NotDownloaded
            return
        }
        interpreterLoading = true
        scope.launch {
            try {
                val modelFile = File(getModelsDir(context), MODEL_FILENAME)
                val options = Interpreter.Options().apply {
                    setNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
                    runCatching { setUseXNNPACK(true) }
                }
                interpreter = Interpreter(modelFile, options)
                _downloadState.value = ModelDownloadState.Ready
                Log.i(TAG, "ArtifactNet (LiteRT) loaded from storage successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load ArtifactNet", e)
                _downloadState.value = ModelDownloadState.Error(e.message ?: "Failed to initialize interpreter")
            } finally {
                interpreterLoading = false
            }
        }
    }

    /**
     * Download the ArtifactNet LiteRT model file (~17.4 MB) from GitHub Releases.
     */
    fun downloadModel(context: Context) {
        if (_downloadState.value is ModelDownloadState.Downloading) return
        _downloadState.value = ModelDownloadState.Downloading(0f, 0L, TOTAL_MODEL_BYTES)

        scope.launch(Dispatchers.IO) {
            val dir = getModelsDir(context)
            try {
                File(dir, LEGACY_ONNX_FILENAME).delete()
                File(dir, LEGACY_DATA_FILENAME).delete()
            } catch (_: Exception) {}

            val modelTmp = File(dir, "$MODEL_FILENAME.tmp")
            val modelFinal = File(dir, MODEL_FILENAME)

            var totalDownloaded = 0L

            try {
                downloadFile(URL_MODEL, modelTmp) { bytesRead ->
                    totalDownloaded += bytesRead
                    val progress = (totalDownloaded.toFloat() / TOTAL_MODEL_BYTES).coerceIn(0f, 1f)
                    _downloadState.value = ModelDownloadState.Downloading(progress, totalDownloaded, TOTAL_MODEL_BYTES)
                }

                modelTmp.renameTo(modelFinal)

                withContext(Dispatchers.Main) {
                    init(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model download failed", e)
                modelTmp.delete()
                _downloadState.value = ModelDownloadState.Error(e.message ?: "Download failed")
            }
        }
    }

    private fun downloadFile(url: String, targetFile: File, onBytesRead: (Long) -> Unit) {
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("HTTP ${response.code} downloading $url")
        }
        val body = response.body
        FileOutputStream(targetFile).use { out ->
            val buf = ByteArray(16384)
            body.byteStream().use { input ->
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    out.write(buf, 0, read)
                    onBytesRead(read.toLong())
                }
            }
            out.flush()
        }
    }

    /**
     * Delete the downloaded model file and release memory.
     */
    fun deleteModel(context: Context) {
        analysisJob?.cancel()
        analysisJob = null
        upfrontAnalysisJob?.cancel()
        upfrontAnalysisJob = null
        queuePreAnalysisJob?.cancel()
        queuePreAnalysisJob = null
        try {
            interpreter?.close()
            interpreter = null
        } catch (_: Exception) {}
        val dir = getModelsDir(context)
        File(dir, MODEL_FILENAME).delete()
        File(dir, LEGACY_ONNX_FILENAME).delete()
        File(dir, LEGACY_DATA_FILENAME).delete()
        _downloadState.value = ModelDownloadState.NotDownloaded
        _result.value = null
        Log.i(TAG, "ArtifactNet model files deleted")
    }

    fun notifySeek(seekPositionSec: Double = 0.0) {
        synchronized(resultLock) {
            if (!isAiConfirmed && !isVerdictFinal) {
                hasSeekedDeep = seekPositionSec >= 10.0
            }
        }
    }

    /**
     * Called when active track changes. Checks cache first for instantaneous verdict,
     * or begins upfront multi-point scan if model is ready.
     */
    fun onTrackChanged(
        trackId: Long,
        track: Track? = null,
        context: Context? = null,
        resolvedUri: String? = null
    ) {
        val modelReady = isModelLoaded() || (context != null && isModelReady(context))

        val hasUsableUri = !resolvedUri.isNullOrBlank() &&
            !resolvedUri.startsWith("soundtune://") &&
            !resolvedUri.startsWith("exo_cache://")

        synchronized(resultLock) {
            val cached = trackVerdictCache[trackId]
            if (cached != null) {
                currentTrackId = trackId
                currentAnalysisEpoch++
                isVerdictFinal = true
                isAiConfirmed = cached.isAi
                peakScore = cached.score
                checkpointScores.clear()
                _result.value = cached
                Log.d(TAG, "Track $trackId already analyzed in cache: $cached")
                return
            }

            // Avoid wiping out an already-computed verdict or restarting an active scan if onMediaItemTransition fires twice
            if (currentTrackId == trackId && trackId != 0L) {
                if (isVerdictFinal || _result.value?.status == Status.DONE) {
                    return
                }
                if (isAnalyzingUpfront() && (currentScanHasResolvedUri || !hasUsableUri)) {
                    return
                }
            }

            currentTrackId = trackId
            currentScanHasResolvedUri = hasUsableUri
            currentAnalysisEpoch++
            isVerdictFinal = false
            isAiConfirmed = false
            peakScore = 0f
            hasSeekedDeep = false
            checkpointScores.clear()
            _result.value = if (modelReady || context == null) {
                Result(0f, false, Status.ANALYZING)
            } else {
                null
            }
        }

        analysisJob?.cancel()
        upfrontAnalysisJob?.cancel()
        // Cancel any background queue pre-analysis so the active track gets immediate access to inferenceDispatcher
        queuePreAnalysisJob?.cancel()

        if (track != null && context != null && isModelReady(context)) {
            analyzeTrackUpfront(context, track, resolvedUri)
        }
    }

    /**
     * Records a progressive sampling checkpoint during playback (fallback when upfront scan cannot run, e.g. DRM).
     * Never flips or changes a verdict once [Status.DONE] is emitted.
     */
    fun recordCheckpointScore(
        score: Float,
        trackId: Long = currentTrackId,
        threshold: Float = getEffectiveThreshold()
    ) {
        if (score.isNaN()) return
        synchronized(resultLock) {
            if (isVerdictFinal) return // Never overwrite a locked verdict

            val clamped = score.coerceIn(0f, 1f)
            checkpointScores.add(clamped)
            if (trackId != 0L) {
                val list = trackScoreSamplesCache.getOrPut(trackId) { mutableListOf() }
                if (!list.contains(clamped)) list.add(clamped)
            }

            val n = checkpointScores.size

            val sortedScores = checkpointScores.sorted()
            val median = sortedScores[n / 2]
            val highCount = checkpointScores.count { it >= threshold }

            // Consecutive AI chunks: 2 adjacent windows both elevated >= threshold
            val hasConsecutiveAi = checkpointScores.zipWithNext().any { (a, b) ->
                a >= threshold && b >= threshold
            }

            // Transition detection: high chunk with elevated neighbor (e.g. intro build-up)
            val hasTransitionAi = checkpointScores.indices.any { i ->
                val s = checkpointScores[i]
                if (s < threshold) return@any false
                val prev = checkpointScores.getOrNull(i - 1) ?: 0f
                val next = checkpointScores.getOrNull(i + 1) ?: 0f
                val neighborMax = maxOf(prev, next)
                neighborMax >= 0.35f && (s + neighborMax) / 2f >= 0.50f
            }

            val isConfirmed = isAiConfirmed ||
                highCount >= 2 ||
                hasConsecutiveAi ||
                hasTransitionAi ||
                clamped >= 0.90f ||
                (n >= 4 && checkpointScores.count { it >= 0.50f } >= (n / 2 + 1))

            if (isConfirmed) {
                isAiConfirmed = true
                isVerdictFinal = true
                val aiScores = checkpointScores.filter { it >= 0.30f }
                val representativeAiScore = if (aiScores.isNotEmpty()) {
                    aiScores.maxOrNull() ?: clamped
                } else clamped
                peakScore = maxOf(peakScore, representativeAiScore)
                val finalRes = Result(peakScore, isAi = true, Status.DONE)
                if (trackId != 0L) {
                    trackVerdictCache[trackId] = finalRes
                }
                _result.value = finalRes
                Log.i(TAG, "AI Confirmed (n=$n, track=$trackId): peak=$peakScore, highCount=$highCount, chunks=$checkpointScores")
            } else {
                val displayScore = maxOf(peakScore, median)
                peakScore = displayScore
                // Wait for at least 2 checkpoints (0..4s and 4..8s) when the first chunk is a quiet intro
                // so the badge never says "Human" at 4s and flips to "AI" at 8s.
                // Once published, lock isVerdictFinal = true so the score never changes!
                val readyToLockHuman = n >= 2 || hasSeekedDeep
                if (readyToLockHuman) {
                    isVerdictFinal = true
                    val doneRes = Result(displayScore, isAi = false, Status.DONE)
                    if (trackId != 0L) {
                        trackVerdictCache[trackId] = doneRes
                    }
                    _result.value = doneRes
                    Log.i(TAG, "Human verdict locked (n=$n, track=$trackId): score=$displayScore, chunks=$checkpointScores")
                } else {
                    Log.d(TAG, "Initial checkpoint ($n, track=$trackId): score=$displayScore, awaiting confirmation window")
                }
            }
        }
    }

    /**
     * Calculates the consensus verdict from multiple sampling points across a track.
     */
    fun calculateVerdictFromScores(scores: List<Float>, threshold: Float): Result {
        val clamped = scores.map { it.coerceIn(0f, 1f) }
        val sorted = clamped.sorted()
        val n = sorted.size
        val median = if (n > 0) sorted[n / 2] else 0f
        val maxScore = sorted.lastOrNull() ?: 0f
        val highCount = clamped.count { it >= threshold }

        // AI confirmation criteria:
        // - At least 2 points >= threshold
        // - OR 1 point >= 0.90 with another point elevated >= 0.35 (vocal transition)
        // - OR any point with extreme forensic confidence >= 0.95
        val isConfirmed = highCount >= 2 ||
            (maxScore >= 0.90f && clamped.count { it >= 0.35f } >= 2) ||
            maxScore >= 0.95f

        return if (isConfirmed) {
            val aiScores = clamped.filter { it >= 0.30f }
            val representativeScore = if (aiScores.isNotEmpty()) aiScores.average().toFloat() else maxScore
            Result(representativeScore, isAi = true, status = Status.DONE)
        } else {
            Result(median, isAi = false, status = Status.DONE)
        }
    }

    /**
     * Upfront multi-point background analysis across the entire track.
     * Samples multiple strategic positions across the track (10%, 35%, 60%, 85%),
     * computes consensus score, and locks the final immutable verdict.
     */
    fun analyzeTrackUpfront(context: Context, track: Track, resolvedUri: String? = null) {
        if (trackVerdictCache.containsKey(track.id)) return
        if (!isModelReady(context)) return
        init(context)

        val epoch: Long
        val targetTrackId = track.id
        synchronized(resultLock) {
            epoch = currentAnalysisEpoch
        }

        upfrontAnalysisJob?.cancel()
        upfrontAnalysisJob = scope.launch(Dispatchers.IO) {
            try {
                var retries = 0
                while (!isModelLoaded() && retries < 25 && isActive) {
                    kotlinx.coroutines.delay(100)
                    retries++
                }
                val interp = interpreter ?: return@launch
                if (!isActive || epoch != currentAnalysisEpoch) return@launch

                val threshold = getEffectiveThreshold(context)
                val ratios = listOf(0.10, 0.35, 0.60, 0.85)
                val chunkScores = sampleTrackPoints(
                    context = context,
                    track = track,
                    resolvedUri = resolvedUri,
                    ratios = ratios,
                    interp = interp,
                    threshold = threshold,
                    checkActive = { isActive && epoch == currentAnalysisEpoch }
                )

                if (!isActive || epoch != currentAnalysisEpoch) return@launch

                if (chunkScores.isNotEmpty()) {
                    val verdict = calculateVerdictFromScores(chunkScores, threshold)
                    trackVerdictCache[targetTrackId] = verdict
                    synchronized(resultLock) {
                        if (!isVerdictFinal && targetTrackId == currentTrackId && epoch == currentAnalysisEpoch) {
                            isVerdictFinal = true
                            isAiConfirmed = verdict.isAi
                            peakScore = verdict.score
                            checkpointScores.clear()
                            checkpointScores.addAll(chunkScores)
                            _result.value = verdict
                            Log.i(TAG, "Upfront scan locked ($targetTrackId, ${chunkScores.size} pts): $verdict, chunks=$chunkScores")
                        }
                    }
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                Log.w(TAG, "Upfront analysis failed for track ${track.id}: ${e.message}")
            }
        }
    }

    /**
     * Pre-analyzes the full audio for the next 1–2 songs in the queue in the background.
     * Caches verdicts ahead of time so songs play with zero AI detection latency or false triggers.
     */
    fun preAnalyzeQueue(context: Context, tracks: List<Track>) {
        if (!isModelReady(context)) return
        init(context)

        queuePreAnalysisJob?.cancel()
        queuePreAnalysisJob = scope.launch(Dispatchers.IO) {
            try {
                // Always yield to the currently playing track's upfront scan so queue pre-analysis
                // never contends for LiteRT CPU, MediaCodec, or network bandwidth.
                kotlinx.coroutines.delay(400)
                upfrontAnalysisJob?.join()

                var retries = 0
                while (!isModelLoaded() && retries < 25 && isActive) {
                    kotlinx.coroutines.delay(100)
                    retries++
                }
                val interp = interpreter ?: return@launch
                if (!isActive) return@launch

                val threshold = getEffectiveThreshold(context)
                val ratios = listOf(0.10, 0.35, 0.60, 0.85)

                for (track in tracks.take(2)) {
                    if (!isActive) break
                    upfrontAnalysisJob?.join()
                    if (trackVerdictCache.containsKey(track.id)) continue

                    val chunkScores = sampleTrackPoints(
                        context = context,
                        track = track,
                        resolvedUri = null,
                        ratios = ratios,
                        interp = interp,
                        threshold = threshold,
                        checkActive = { isActive }
                    )

                    if (chunkScores.isNotEmpty()) {
                        val verdict = calculateVerdictFromScores(chunkScores, threshold)
                        trackVerdictCache[track.id] = verdict
                        Log.i(TAG, "Pre-analyzed queue track ${track.id} (${track.title}): $verdict (chunks=$chunkScores)")

                        synchronized(resultLock) {
                            if (track.id == currentTrackId && !isVerdictFinal) {
                                isVerdictFinal = true
                                isAiConfirmed = verdict.isAi
                                peakScore = verdict.score
                                checkpointScores.clear()
                                checkpointScores.addAll(chunkScores)
                                _result.value = verdict
                            }
                        }
                    }
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                Log.w(TAG, "Queue pre-analysis error: ${e.message}")
            }
        }
    }

    private suspend fun sampleTrackPoints(
        context: Context,
        track: Track,
        resolvedUri: String?,
        ratios: List<Double>,
        interp: Interpreter,
        threshold: Float,
        checkActive: () -> Boolean
    ): List<Float> {
        val windowSeconds = getEffectiveWindowSeconds(context)
        val effectiveRatios = if (windowSeconds == 1) listOf(0.35, 0.85) else ratios

        // Fast Path: use already-resolved URI if provided by MusicManager / PlayerViewModel
        if (!resolvedUri.isNullOrBlank() && !resolvedUri.startsWith("soundtune://") && !resolvedUri.startsWith("exo_cache://")) {
            when {
                resolvedUri.startsWith("content://") -> {
                    val extractor = MediaExtractor()
                    try {
                        extractor.setDataSource(context, Uri.parse(resolvedUri), null)
                        val format = selectAudioTrackFormat(extractor)
                        if (format != null) {
                            val scores = sampleExtractorSource(extractor, format, effectiveRatios, interp, threshold, windowSeconds, true, checkActive)
                            if (scores.isNotEmpty()) return scores
                        }
                    } catch (_: Exception) {
                    } finally {
                        runCatching { extractor.release() }
                    }
                }
                resolvedUri.startsWith("file://") || resolvedUri.startsWith("/") -> {
                    val filePath = if (resolvedUri.startsWith("file://")) {
                        Uri.parse(resolvedUri).path ?: resolvedUri.removePrefix("file://")
                    } else resolvedUri
                    if (File(filePath).exists()) {
                        val extractor = MediaExtractor()
                        try {
                            extractor.setDataSource(filePath)
                            val format = selectAudioTrackFormat(extractor)
                            if (format != null) {
                                val scores = sampleExtractorSource(extractor, format, effectiveRatios, interp, threshold, windowSeconds, true, checkActive)
                                if (scores.isNotEmpty()) return scores
                            }
                        } catch (_: Exception) {
                        } finally {
                            runCatching { extractor.release() }
                        }
                    }
                }
                resolvedUri.contains(".m3u8") -> {
                    val hlsScores = sampleHlsPlaylist(context, resolvedUri, effectiveRatios, interp, threshold, windowSeconds, checkActive)
                    if (hlsScores.isNotEmpty()) return hlsScores
                }
                resolvedUri.startsWith("http://") || resolvedUri.startsWith("https://") -> {
                    val extractor = MediaExtractor()
                    try {
                        val headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        extractor.setDataSource(resolvedUri, headers)
                        val format = selectAudioTrackFormat(extractor)
                        if (format != null) {
                            val scores = sampleExtractorSource(extractor, format, effectiveRatios, interp, threshold, windowSeconds, true, checkActive)
                            if (scores.isNotEmpty()) return scores
                        }
                    } catch (_: Exception) {
                    } finally {
                        runCatching { extractor.release() }
                    }
                }
            }
        }

        // 1. Check local or downloaded audio file in DB
        val localExtractorInfo = resolveLocalExtractorForTrack(context, track)
        if (localExtractorInfo != null) {
            val (extractor, format) = localExtractorInfo
            try {
                val localScores = sampleExtractorSource(
                    extractor = extractor,
                    format = format,
                    ratios = effectiveRatios,
                    interp = interp,
                    threshold = threshold,
                    windowSeconds = windowSeconds,
                    includeBookendsFallback = true,
                    checkActive = checkActive
                )
                if (localScores.isNotEmpty()) return localScores
            } finally {
                runCatching { extractor.release() }
            }
        }

        if (!checkActive()) return emptyList()

        // 2. Resolve remote stream (prefers full-bandwidth 160k HLS / progressive just like KittyTuneDesktop)
        val resolvedStream = StreamResolver.resolveStreamForAiAnalysis(context, track) ?: return emptyList()
        val url = resolvedStream.url
        if (url.isBlank() || resolvedStream.isDrmProtected) return emptyList()

        // 3. Handle HLS (.m3u8) playlists by sampling segments in parallel (mirrors Desktop HlsStreamAdapter)
        if (url.contains(".m3u8")) {
            return sampleHlsPlaylist(
                context = context,
                playlistUrl = url,
                ratios = effectiveRatios,
                interp = interp,
                threshold = threshold,
                windowSeconds = windowSeconds,
                checkActive = checkActive
            )
        }

        // 4. Direct HTTP stream
        val extractor = MediaExtractor()
        try {
            val headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            extractor.setDataSource(url, headers)
            val format = selectAudioTrackFormat(extractor) ?: return emptyList()
            return sampleExtractorSource(
                extractor = extractor,
                format = format,
                ratios = effectiveRatios,
                interp = interp,
                threshold = threshold,
                windowSeconds = windowSeconds,
                includeBookendsFallback = true,
                checkActive = checkActive
            )
        } catch (e: Exception) {
            Log.w(TAG, "Direct stream sampling failed for track ${track.id}: ${e.message}")
            return emptyList()
        } finally {
            runCatching { extractor.release() }
        }
    }

    /**
     * Samples multiple seek positions from [extractor] reusing a SINGLE [MediaCodec] instance
     * via [MediaCodec.flush] for ~5x faster multi-point extraction (<200ms on local files).
     */
    private suspend fun sampleExtractorSource(
        extractor: MediaExtractor,
        format: MediaFormat,
        ratios: List<Double>,
        interp: Interpreter,
        threshold: Float,
        windowSeconds: Int,
        includeBookendsFallback: Boolean,
        checkActive: () -> Boolean
    ): List<Float> {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return emptyList()
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION)
        } else 0L

        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (_: Exception) {
            return emptyList()
        }

        val chunkScores = mutableListOf<Float>()

        try {
            try {
                format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT)
                codec.configure(format, null, null, 0)
            } catch (_: Exception) {
                format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                codec.configure(format, null, null, 0)
            }
            codec.start()

            for (r in ratios) {
                if (!checkActive()) break
                val targetUs = if (durationUs > 16_000_000L) (durationUs * r).toLong() else 0L
                extractor.seekTo(targetUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                runCatching { codec.flush() }

                val floatSamples = decodeFourSecondsWithCodec(
                    extractor = extractor,
                    codec = codec,
                    format = format,
                    discardSeekTransients = targetUs > 0L,
                    windowSeconds = windowSeconds
                )
                if (floatSamples != null && floatSamples.isNotEmpty()) {
                    val score = withContext(inferenceDispatcher) {
                        runDetectionOnMono(interp, floatSamples, TARGET_SR)
                    }
                    if (score != null && !score.isNaN()) {
                        chunkScores.add(score)
                        // In 1-second ultra-fast mode, early-exit as soon as AI is confirmed
                        if (windowSeconds == 1 && calculateVerdictFromScores(chunkScores, threshold).isAi) {
                            break
                        }
                    }
                }
            }

            // Low-bitrate AAC/MP3 local files (e.g. 64 kbps re-encoded m4a) can strip high-frequency
            // spectral bins during loud bass drops in the middle (10%..85%), while preserving clear
            // synthesis residuals in the vocal/melodic intro (~2.6%) and outro (~97%).
            // Require BOTH bookends to be elevated (>= 0.35) so a lone synthetic intro on a human
            // track never triggers a false positive.
            if (includeBookendsFallback &&
                checkActive() &&
                durationUs > 30_000_000L &&
                !calculateVerdictFromScores(chunkScores, threshold).isAi
            ) {
                val bookendScores = mutableListOf<Float>()
                for (r in listOf(0.026, 0.97)) {
                    if (!checkActive()) break
                    val targetUs = (durationUs * r).toLong()
                    extractor.seekTo(targetUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    runCatching { codec.flush() }

                    val floatSamples = decodeFourSecondsWithCodec(
                        extractor = extractor,
                        codec = codec,
                        format = format,
                        discardSeekTransients = targetUs > 0L,
                        windowSeconds = windowSeconds
                    )
                    if (floatSamples != null && floatSamples.isNotEmpty()) {
                        val score = withContext(inferenceDispatcher) {
                            runDetectionOnMono(interp, floatSamples, TARGET_SR)
                        }
                        if (score != null && !score.isNaN()) {
                            bookendScores.add(score)
                        }
                    }
                }
                if (bookendScores.size == 2 && bookendScores.all { it >= 0.35f }) {
                    chunkScores.add(0, bookendScores[0])
                    chunkScores.add(bookendScores[1])
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }

        return chunkScores
    }

    internal fun parseHlsPlaylist(playlistUrl: String, body: String): HlsPlaylist {
        var initUrl: String? = null
        val parsed = mutableListOf<HlsSegment>()
        var currentStartTime = 0L
        var nextDurationMs = 0L

        body.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-MAP:URI=")) {
                var uri = trimmed.substringAfter("URI=\"").substringBefore("\"")
                if (!uri.startsWith("http")) uri = resolveRelativeUrl(playlistUrl, uri)
                initUrl = uri
            } else if (trimmed.startsWith("#EXTINF:")) {
                val durStr = trimmed.substringAfter("#EXTINF:").substringBefore(",")
                val durSec = durStr.toDoubleOrNull() ?: 0.0
                nextDurationMs = (durSec * 1000).toLong()
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                var segUrl = trimmed
                if (!segUrl.startsWith("http")) segUrl = resolveRelativeUrl(playlistUrl, segUrl)
                parsed.add(HlsSegment(segUrl, nextDurationMs, currentStartTime))
                currentStartTime += nextDurationMs
                nextDurationMs = 0L
            }
        }

        return HlsPlaylist(initUrl, parsed, currentStartTime)
    }

    private fun resolveRelativeUrl(base: String, rel: String): String {
        return try {
            URL(URL(base), rel).toString()
        } catch (_: Exception) {
            base.substringBeforeLast('/') + "/" + rel
        }
    }

    private fun fetchUrlBytes(url: String): ByteArray? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body.bytes() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fetches the strategic HLS segments in parallel and decodes [windowSeconds] float PCM from each
     * as soon as each segment completes downloading (streaming pipeline + early exit in 1s mode).
     */
    private suspend fun sampleHlsPlaylist(
        context: Context,
        playlistUrl: String,
        ratios: List<Double>,
        interp: Interpreter,
        threshold: Float,
        windowSeconds: Int,
        checkActive: () -> Boolean
    ): List<Float> {
        val bodyBytes = fetchUrlBytes(playlistUrl) ?: return emptyList()
        val bodyText = String(bodyBytes)
        var playlist = parseHlsPlaylist(playlistUrl, bodyText)

        // If this was a master playlist pointing to child .m3u8 variants, follow the first variant
        if (playlist.segments.isNotEmpty() && playlist.totalDurationMs == 0L &&
            playlist.segments.first().url.contains(".m3u8")
        ) {
            val variantUrl = playlist.segments.first().url
            val variantBytes = fetchUrlBytes(variantUrl) ?: return emptyList()
            playlist = parseHlsPlaylist(variantUrl, String(variantBytes))
        }

        if (playlist.segments.isEmpty()) return emptyList()

        val chunkScores = mutableListOf<Float>()

        coroutineScope {
            val initDeferred = async(Dispatchers.IO) {
                playlist.initSegmentUrl?.let { fetchUrlBytes(it) }
            }
            val segDeferreds = ratios.map { r ->
                async(Dispatchers.IO) {
                    if (!checkActive()) return@async null
                    val startMs = if (playlist.totalDurationMs > 16_000L) {
                        (playlist.totalDurationMs * r).toLong()
                    } else 0L
                    val segIdx = playlist.startIndexFor(startMs)
                    if (segIdx !in playlist.segments.indices) return@async null
                    val first = fetchUrlBytes(playlist.segments[segIdx].url) ?: return@async null
                    val second = if (playlist.segments[segIdx].durationMs in 1..(windowSeconds * 1000L + 500L) &&
                        segIdx + 1 < playlist.segments.size
                    ) {
                        fetchUrlBytes(playlist.segments[segIdx + 1].url)
                    } else null
                    Pair(first, second)
                }
            }

            val initBytes = initDeferred.await()

            for (deferred in segDeferreds) {
                if (!checkActive()) break
                val payload = deferred.await() ?: continue
                val (segBytes, nextSegBytes) = payload

                val tempFile = File.createTempFile("kt_ai_seg_", ".mp4", context.cacheDir)
                try {
                    FileOutputStream(tempFile).use { out ->
                        if (initBytes != null) out.write(initBytes)
                        out.write(segBytes)
                        if (nextSegBytes != null) out.write(nextSegBytes)
                    }

                    val extractor = MediaExtractor()
                    try {
                        extractor.setDataSource(tempFile.absolutePath)
                        val format = selectAudioTrackFormat(extractor) ?: continue
                        val floatSamples = decodeFourSecondsMono(
                            extractor = extractor,
                            format = format,
                            discardSeekTransients = false,
                            windowSeconds = windowSeconds
                        )
                        if (floatSamples != null && floatSamples.isNotEmpty()) {
                            val score = withContext(inferenceDispatcher) {
                                runDetectionOnMono(interp, floatSamples, TARGET_SR)
                            }
                            if (score != null && !score.isNaN()) {
                                chunkScores.add(score)
                                // In 1-second ultra-fast mode, early-exit as soon as AI is confirmed
                                // and cancel any remaining segment downloads immediately!
                                if (windowSeconds == 1 && calculateVerdictFromScores(chunkScores, threshold).isAi) {
                                    segDeferreds.forEach { it.cancel() }
                                    break
                                }
                            }
                        }
                    } finally {
                        runCatching { extractor.release() }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "HLS segment decode error: ${e.message}")
                } finally {
                    runCatching { tempFile.delete() }
                }
            }
        }

        return chunkScores
    }

    private suspend fun resolveLocalExtractorForTrack(context: Context, track: Track): Pair<MediaExtractor, MediaFormat>? {
        val extractor = MediaExtractor()
        try {
            var setDataSource = false

            try {
                val db = AppDatabase.getDatabase(context).downloadDao()
                var localTrack = db.getTrack(track.id)
                if (localTrack == null || localTrack.localAudioPath.isEmpty()) {
                    val fallbackTitle = track.title ?: ""
                    val fallbackArtist = track.user?.username ?: ""
                    if (fallbackTitle.isNotBlank()) {
                        localTrack = db.findDownloadedTrack(fallbackTitle, fallbackArtist)
                    }
                }
                val path = localTrack?.localAudioPath
                if (!path.isNullOrBlank()) {
                    if (path.startsWith("content://")) {
                        extractor.setDataSource(context, Uri.parse(path), null)
                        setDataSource = true
                    } else if (!path.startsWith("exo_cache://") && File(path).exists()) {
                        extractor.setDataSource(path)
                        setDataSource = true
                    }
                }
            } catch (_: Exception) {}

            if (!setDataSource) {
                extractor.release()
                return null
            }

            val audioFormat = selectAudioTrackFormat(extractor)
            if (audioFormat == null) {
                extractor.release()
                return null
            }

            return extractor to audioFormat
        } catch (_: Exception) {
            runCatching { extractor.release() }
            return null
        }
    }

    private fun selectAudioTrackFormat(extractor: MediaExtractor): MediaFormat? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                extractor.selectTrack(i)
                return format
            }
        }
        return null
    }

    private fun decodeFourSecondsMono(
        extractor: MediaExtractor,
        format: MediaFormat,
        discardSeekTransients: Boolean = false,
        windowSeconds: Int = 4
    ): FloatArray? {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        val codec = MediaCodec.createDecoderByType(mime)
        try {
            try {
                format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT)
                codec.configure(format, null, null, 0)
            } catch (_: Exception) {
                format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                codec.configure(format, null, null, 0)
            }
            codec.start()
            return decodeFourSecondsWithCodec(extractor, codec, format, discardSeekTransients, windowSeconds)
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }
    }

    private fun decodeFourSecondsWithCodec(
        extractor: MediaExtractor,
        codec: MediaCodec,
        format: MediaFormat,
        discardSeekTransients: Boolean,
        windowSeconds: Int = 4
    ): FloatArray? {
        val effectiveSec = windowSeconds.coerceIn(1, 4)
        val out = ArrayList<FloatArray>()
        var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else TARGET_SR

        var warmUpSamplesRemaining = if (discardSeekTransients) sampleRate / 3 else 0
        var targetSamples = sampleRate * effectiveSec
        var collectedSamples = 0
        var inputDone = false
        var outputDone = false
        var idleSpins = 0
        val info = MediaCodec.BufferInfo()

        while (!outputDone && collectedSamples < targetSamples && idleSpins < 200) {
            var progressed = false
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(5_000)
                if (inIndex >= 0) {
                    progressed = true
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

            val outIndex = codec.dequeueOutputBuffer(info, 5_000)
            if (outIndex >= 0) {
                progressed = true
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                if (info.size > 0) {
                    val outFormat = codec.outputFormat
                    val channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                    val newSr = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (newSr > 0 && newSr != sampleRate) {
                        sampleRate = newSr
                        targetSamples = sampleRate * effectiveSec
                    }
                    val pcmEncoding = if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    } else AudioFormat.ENCODING_PCM_16BIT

                    val buf = codec.getOutputBuffer(outIndex)!!
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    buf.order(ByteOrder.LITTLE_ENDIAN)

                    val frameMono: FloatArray = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                        val floats = buf.asFloatBuffer()
                        val frames = floats.remaining() / channels
                        val mono = FloatArray(frames)
                        val invCh = 1f / channels
                        for (f in 0 until frames) {
                            var acc = 0f
                            for (c in 0 until channels) acc += floats.get(f * channels + c)
                            mono[f] = acc * invCh
                        }
                        mono
                    } else {
                        val shorts = buf.asShortBuffer()
                        val frames = shorts.remaining() / channels
                        val mono = FloatArray(frames)
                        val invCh = 1f / (channels * 32768f)
                        for (f in 0 until frames) {
                            var acc = 0f
                            for (c in 0 until channels) acc += shorts.get(f * channels + c)
                            mono[f] = acc * invCh
                        }
                        mono
                    }

                    if (warmUpSamplesRemaining > 0) {
                        if (frameMono.size <= warmUpSamplesRemaining) {
                            warmUpSamplesRemaining -= frameMono.size
                        } else {
                            val startIdx = warmUpSamplesRemaining
                            warmUpSamplesRemaining = 0
                            val take = minOf(frameMono.size - startIdx, targetSamples - collectedSamples)
                            if (take > 0) {
                                out.add(frameMono.copyOfRange(startIdx, startIdx + take))
                                collectedSamples += take
                            }
                        }
                    } else {
                        val take = minOf(frameMono.size, targetSamples - collectedSamples)
                        if (take > 0) {
                            out.add(if (take == frameMono.size) frameMono else frameMono.copyOf(take))
                            collectedSamples += take
                        }
                    }
                }
                codec.releaseOutputBuffer(outIndex, false)
            }

            if (progressed) {
                idleSpins = 0
            } else {
                idleSpins++
            }
        }

        if (out.isEmpty()) return null
        val totalFrames = out.sumOf { it.size }
        val fullMono = FloatArray(totalFrames)
        var pos = 0
        for (arr in out) {
            System.arraycopy(arr, 0, fullMono, pos, arr.size)
            pos += arr.size
        }

        return if (sampleRate == TARGET_SR) {
            fullMono
        } else {
            resampleMono(fullMono, sampleRate, TARGET_SR)
        }
    }

    private fun resampleMono(src: FloatArray, srcSr: Int, targetSr: Int): FloatArray {
        if (srcSr == targetSr || src.isEmpty()) return src
        val ratio = srcSr.toDouble() / targetSr
        val outLen = (src.size / ratio).toInt()
        return FloatArray(outLen) { i ->
            val srcPos = i * ratio
            val lo = srcPos.toInt().coerceAtMost(src.size - 1)
            val hi = (lo + 1).coerceAtMost(src.size - 1)
            src[lo] * (1f - (srcPos - lo).toFloat()) + src[hi] * (srcPos - lo).toFloat()
        }
    }

    fun analyzeFloatAsync(monoSamples: FloatArray, sampleRate: Int, trackId: Long = currentTrackId) {
        val session = interpreter ?: run {
            Log.w(TAG, "analyzeFloatAsync called before model ready; skipping")
            return
        }
        if (isVerdictFinal() || isAnalyzingUpfront()) return

        val epoch = currentAnalysisEpoch
        analysisJob = scope.launch(inferenceDispatcher) {
            try {
                if (epoch != currentAnalysisEpoch || isVerdictFinal()) return@launch
                val resampled = if (sampleRate == TARGET_SR) monoSamples else resampleMono(monoSamples, sampleRate, TARGET_SR)
                val score = runDetectionOnMono(session, resampled, TARGET_SR)
                if (isActive && epoch == currentAnalysisEpoch && score != null && !score.isNaN()) {
                    recordCheckpointScore(score, trackId)
                    Log.i(TAG, "ArtifactNet float checkpoint (track $trackId) → P(AI)=${"%.4f".format(score)}")
                }
            } catch (_: CancellationException) {
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OOM during ArtifactNet inference", e)
                System.gc()
            } catch (e: Exception) {
                Log.e(TAG, "Detection failed", e)
            }
        }
    }

    fun analyzeAsync(pcmBytes: ByteArray, sampleRate: Int, channelCount: Int, trackId: Long = currentTrackId) {
        val session = interpreter ?: run {
            Log.w(TAG, "analyzeAsync called before model ready; skipping")
            return
        }
        if (isVerdictFinal() || isAnalyzingUpfront()) return

        val epoch = currentAnalysisEpoch
        analysisJob = scope.launch(inferenceDispatcher) {
            try {
                if (epoch != currentAnalysisEpoch || isVerdictFinal()) return@launch
                val score = runDetection(session, pcmBytes, sampleRate, channelCount)
                if (isActive && epoch == currentAnalysisEpoch && score != null && !score.isNaN()) {
                    recordCheckpointScore(score, trackId)
                    Log.i(TAG, "ArtifactNet checkpoint (track $trackId) → P(AI)=${"%.4f".format(score)}")
                }
            } catch (_: CancellationException) {
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OOM during ArtifactNet inference", e)
                System.gc()
            } catch (e: Exception) {
                Log.e(TAG, "Detection failed", e)
            }
        }
    }

    fun resetResult() {
        analysisJob?.cancel()
        analysisJob = null
        upfrontAnalysisJob?.cancel()
        upfrontAnalysisJob = null
        synchronized(resultLock) {
            currentAnalysisEpoch++
            isVerdictFinal = false
            isAiConfirmed = false
            peakScore = 0f
            hasSeekedDeep = false
            checkpointScores.clear()
            _result.value = null
        }
    }

    private fun runDetection(
        session: Interpreter,
        pcmBytes: ByteArray,
        srcSr: Int,
        channels: Int
    ): Float? {
        val numFrames = pcmBytes.size / (2 * channels)
        if (numFrames == 0) return null

        val buf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        val mono = FloatArray(numFrames)
        if (channels == 1) {
            for (i in 0 until numFrames) {
                mono[i] = buf.short.toFloat() / 32768f
            }
        } else {
            val invChannels = 1f / channels
            for (i in 0 until numFrames) {
                var sum = 0f
                for (ch in 0 until channels) {
                    sum += buf.short.toFloat() / 32768f
                }
                mono[i] = sum * invChannels
            }
        }

        val resampled = if (srcSr == TARGET_SR) mono else resampleMono(mono, srcSr, TARGET_SR)
        return runDetectionOnMono(session, resampled, TARGET_SR)
    }

    private fun runDetectionOnMono(
        session: Interpreter,
        resampled: FloatArray,
        srcSr: Int = TARGET_SR
    ): Float? {
        if (resampled.isEmpty()) return null

        // Check audio energy (RMS) — if lead-in is pure silence (RMS < 0.001), wait for music
        var sumSq = 0f
        var maxPeak = 0f
        for (sample in resampled) {
            sumSq += sample * sample
            val absVal = kotlin.math.abs(sample)
            if (absVal > maxPeak) maxPeak = absVal
        }
        val rms = kotlin.math.sqrt(sumSq / resampled.size)
        if (rms < 0.001f) {
            Log.d(TAG, "Audio chunk is silent (RMS=${"%.5f".format(rms)}), waiting for music content")
            return null
        }

        // Peak normalization: prevent numerical overflow / NaN if float audio overshoots 1.0 (matches Desktop)
        val normalized = if (maxPeak > 1.0f) {
            val scale = 1.0f / maxPeak
            FloatArray(resampled.size) { resampled[it] * scale }
        } else {
            resampled
        }

        val inputSamples: FloatArray
        val numChunks: Int
        if (normalized.size < CHUNK_SAMPLES) {
            val n = normalized.size
            val fadeLen = minOf(882, n / 10)
            if (fadeLen > 1 && n > fadeLen) {
                val loopLen = n - fadeLen
                val loopable = FloatArray(loopLen)
                for (k in 0 until fadeLen) {
                    val alpha = k.toFloat() / fadeLen
                    loopable[k] = alpha * normalized[k] + (1f - alpha) * normalized[n - fadeLen + k]
                }
                for (k in fadeLen until loopLen) {
                    loopable[k] = normalized[k]
                }
                inputSamples = FloatArray(CHUNK_SAMPLES) { i ->
                    loopable[i % loopLen]
                }
            } else {
                inputSamples = FloatArray(CHUNK_SAMPLES) { i ->
                    normalized[i % n]
                }
            }
            numChunks = 1
        } else {
            inputSamples = normalized
            numChunks = minOf(normalized.size / CHUNK_SAMPLES, 4)
        }

        val scores = mutableListOf<Float>()
        val inputBuffer = ByteBuffer.allocateDirect(CHUNK_SAMPLES * 4).order(ByteOrder.nativeOrder())
        val outputBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())

        var offset = 0
        for (c in 0 until numChunks) {
            inputBuffer.clear()
            for (i in offset until offset + CHUNK_SAMPLES) {
                inputBuffer.putFloat(inputSamples[i])
            }
            outputBuffer.clear()
            synchronized(engineLock) {
                session.run(inputBuffer, outputBuffer)
            }
            outputBuffer.rewind()
            var prob = outputBuffer.float

            // Fallback if high-energy compressed master causes STFT/UNet numerical overflow (NaN)
            if (prob.isNaN()) {
                inputBuffer.clear()
                for (i in offset until offset + CHUNK_SAMPLES) {
                    inputBuffer.putFloat(inputSamples[i] * 0.5f)
                }
                outputBuffer.clear()
                synchronized(engineLock) {
                    session.run(inputBuffer, outputBuffer)
                }
                outputBuffer.rewind()
                prob = outputBuffer.float
            }

            if (!prob.isNaN()) {
                scores.add(prob.coerceIn(0f, 1f))
            }
            offset += CHUNK_SAMPLES
        }

        if (scores.isEmpty()) return null

        scores.sort()
        return if (scores.size % 2 == 0) {
            (scores[scores.size / 2 - 1] + scores[scores.size / 2]) / 2f
        } else {
            scores[scores.size / 2]
        }
    }
}
