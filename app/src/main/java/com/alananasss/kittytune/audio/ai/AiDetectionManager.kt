package com.alananasss.kittytune.audio.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import com.alananasss.kittytune.data.zapret.ZapretManager

/**
 * Singleton that manages ArtifactNet LiteRT (TensorFlow Lite) model downloading and on-device inference.
 *
 * ArtifactNet (4.2M params) detects AI-generated music by extracting forensic
 * residual artifacts from neural audio codecs — generalises across 22+ generators.
 * The full pipeline (STFT → UNet → HPSS → CNN → sigmoid) is executed natively via Google LiteRT.
 *
 * Model file is downloaded on-demand (~17.4 MB) into private app storage
 * to keep the APK download size small and respect user storage preferences.
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

    private val _result = MutableStateFlow<Result?>(null)
    val result: StateFlow<Result?> = _result.asStateFlow()

    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotDownloaded)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)
    private var interpreter: Interpreter? = null
    private var analysisJob: Job? = null

    private val httpClient by lazy {
        ZapretManager.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
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

    /**
     * Check if model file is on disk and load the interpreter if so.
     */
    fun init(context: Context) {
        if (interpreter != null) return
        val ready = isModelReady(context)
        if (!ready) {
            _downloadState.value = ModelDownloadState.NotDownloaded
            return
        }
        try {
            val modelFile = File(getModelsDir(context), MODEL_FILENAME)
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(modelFile, options)
            _downloadState.value = ModelDownloadState.Ready
            Log.i(TAG, "ArtifactNet (LiteRT) loaded from storage successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ArtifactNet", e)
            _downloadState.value = ModelDownloadState.Error(e.message ?: "Failed to initialize interpreter")
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
            // Clean up old ONNX files if present to save storage space
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

    fun analyzeAsync(pcmBytes: ByteArray, sampleRate: Int, channelCount: Int) {
        val session = interpreter ?: run {
            Log.w(TAG, "analyzeAsync called before model ready; skipping")
            return
        }
        _result.value = Result(0f, false, Status.ANALYZING)
        analysisJob?.cancel()
        analysisJob = scope.launch(Dispatchers.Default) {
            try {
                val score = runDetection(session, pcmBytes, sampleRate, channelCount)
                if (isActive) {
                    _result.value = Result(score, score > 0.5f, Status.DONE)
                    Log.i(TAG, "ArtifactNet → P(AI)=${"%.4f".format(score)} (${if (score > 0.5f) "AI" else "Human"})")
                }
            } catch (e: CancellationException) {
                // Ignore cancellation when a newer snapshot or reset arrived
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OOM during ArtifactNet inference", e)
                System.gc()
                if (isActive) {
                    _result.value = Result(0f, false, Status.ERROR)
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Detection failed", e)
                    _result.value = Result(0f, false, Status.ERROR)
                }
            }
        }
    }

    fun resetResult() {
        analysisJob?.cancel()
        analysisJob = null
        _result.value = null
    }

    private fun runDetection(
        session: Interpreter,
        pcmBytes: ByteArray,
        srcSr: Int,
        channels: Int
    ): Float {
        val numFrames = pcmBytes.size / (2 * channels)
        if (numFrames == 0) return 0f

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

        val resampled = if (srcSr == TARGET_SR) mono else {
            val ratio = srcSr.toDouble() / TARGET_SR
            val outLen = (mono.size / ratio).toInt()
            FloatArray(outLen) { i ->
                val srcPos = i * ratio
                val lo = srcPos.toInt().coerceAtMost(mono.size - 1)
                val hi = (lo + 1).coerceAtMost(mono.size - 1)
                mono[lo] * (1f - (srcPos - lo).toFloat()) + mono[hi] * (srcPos - lo).toFloat()
            }
        }

        // Check audio energy (RMS) — if lead-in is pure silence (RMS < 0.001), wait for music
        var sumSq = 0f
        for (sample in resampled) {
            sumSq += sample * sample
        }
        val rms = kotlin.math.sqrt(sumSq / resampled.size)
        if (rms < 0.001f) {
            Log.d(TAG, "Audio chunk is silent (RMS=${"%.5f".format(rms)}), waiting for music content")
            return 0f
        }

        val inputSamples: FloatArray
        val numChunks: Int
        if (resampled.size < CHUNK_SAMPLES) {
            val n = resampled.size
            val fadeLen = minOf(882, n / 10)
            if (fadeLen > 1 && n > fadeLen) {
                val loopLen = n - fadeLen
                val loopable = FloatArray(loopLen)
                for (k in 0 until fadeLen) {
                    val alpha = k.toFloat() / fadeLen
                    loopable[k] = alpha * resampled[k] + (1f - alpha) * resampled[n - fadeLen + k]
                }
                for (k in fadeLen until loopLen) {
                    loopable[k] = resampled[k]
                }
                inputSamples = FloatArray(CHUNK_SAMPLES) { i ->
                    loopable[i % loopLen]
                }
            } else {
                inputSamples = FloatArray(CHUNK_SAMPLES) { i ->
                    resampled[i % n]
                }
            }
            numChunks = 1
        } else {
            inputSamples = resampled
            numChunks = resampled.size / CHUNK_SAMPLES
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
            session.run(inputBuffer, outputBuffer)
            outputBuffer.rewind()
            val prob = outputBuffer.float
            if (!prob.isNaN()) {
                scores.add(prob.coerceIn(0f, 1f))
            }
            offset += CHUNK_SAMPLES
        }

        if (scores.isEmpty()) return 0f

        scores.sort()
        return if (scores.size % 2 == 0) {
            (scores[scores.size / 2 - 1] + scores[scores.size / 2]) / 2f
        } else {
            scores[scores.size / 2]
        }
    }
}
