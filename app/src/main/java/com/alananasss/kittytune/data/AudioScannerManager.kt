package com.alananasss.kittytune.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LocalTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

object AudioScannerManager {
    private const val TAG = "AudioScannerManager"

    private val scanScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val scanMutex = Mutex()
    private val isRunning = AtomicBoolean(false)
    private var hasPendingWork = AtomicBoolean(false)

    init {
        try {
            System.loadLibrary("kittytune_audio_dsp")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load kittytune_audio_dsp library", e)
        }
    }

    fun scanDownloadedTracks(context: Context) {
        hasPendingWork.set(true)
        if (isRunning.compareAndSet(false, true)) {
            scanScope.launch {
                try {
                    while (hasPendingWork.getAndSet(false)) {
                        scanMutex.withLock {
                            processUnscannedTracks(context.applicationContext)
                        }
                    }
                } finally {
                    isRunning.set(false)
                }
            }
        }
    }

    private suspend fun processUnscannedTracks(context: Context) {
        val dao = AppDatabase.getDatabase(context).downloadDao()
        val tracks = dao.getTracksWithNullLufs()

        for (track in tracks) {
            try {
                scanTrack(context, track, dao)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to scan track ${track.id}: ${e.message} - will retry later")
            }
            // Yield CPU to keep device temperature low and avoid overheating
            delay(100L)
        }
    }

    private suspend fun scanTrack(
        context: Context,
        track: LocalTrack,
        dao: com.alananasss.kittytune.data.local.DownloadDao
    ) = withContext(Dispatchers.IO) {
        val path = resolveAudioPath(track)
        if (path == null) {
            Log.w(TAG, "Cannot resolve path for track ${track.id}, will retry when ready")
            return@withContext
        }

        val file = File(path)
        if (!file.exists() && !path.startsWith("content://")) {
            Log.w(TAG, "File not yet accessible: $path, will retry later")
            return@withContext
        }

        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var nativeHandle = 0L

        try {
            if (path.startsWith("content://")) {
                val uri = android.net.Uri.parse(path)
                extractor.setDataSource(context, uri, emptyMap())
            } else {
                extractor.setDataSource(path)
            }

            var audioTrackIndex = -1
            var channels = 2
            var sampleRate = 44100

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 2)
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100)
                    break
                }
            }

            if (audioTrackIndex < 0) {
                Log.w(TAG, "No audio track found for ${track.id}")
                return@withContext
            }

            extractor.selectTrack(audioTrackIndex)

            val mimeType = extractor.getTrackFormat(audioTrackIndex).getString(MediaFormat.KEY_MIME)
                ?: return@withContext

            decoder = MediaCodec.createDecoderByType(mimeType)
            decoder.configure(extractor.getTrackFormat(audioTrackIndex), null, null, 0)
            decoder.start()

            nativeHandle = nativeCreateAnalyzer(channels, sampleRate)
            if (nativeHandle == 0L) {
                Log.e(TAG, "Failed to create native audio analyzer for track ${track.id}")
                return@withContext
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var eos = false
            var localFloatBuffer = FloatArray(16384 * channels)

            while (!eos) {
                val inIndex = decoder.dequeueInputBuffer(10000)
                if (inIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            val time = extractor.sampleTime
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, time, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                when {
                    outIndex >= 0 -> {
                        val outputBuffer = decoder.getOutputBuffer(outIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            val numFrames = bufferInfo.size / (channels * 2)
                            if (numFrames > 0) {
                                val requiredSize = numFrames * channels
                                if (localFloatBuffer.size < requiredSize) {
                                    localFloatBuffer = FloatArray(requiredSize)
                                }
                                val shortBuf = outputBuffer.duplicate()
                                shortBuf.position(bufferInfo.offset)
                                shortBuf.limit(bufferInfo.offset + bufferInfo.size)
                                for (i in 0 until requiredSize) {
                                    localFloatBuffer[i] = if (shortBuf.remaining() >= 2) {
                                        shortBuf.getShort().toFloat() / 32768f
                                    } else 0f
                                }
                                nativeAddFramesFloat(nativeHandle, localFloatBuffer, numFrames)
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            eos = true
                        }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = decoder.outputFormat
                        channels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                    }
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                }
            }

            val lufs = nativeGetAnalyzedLoudness(nativeHandle)
            val truePeak = nativeGetAnalyzedTruePeakDb(nativeHandle)

            if (lufs > -70f) {
                val updated = track.copy(lufs = lufs, truePeak = truePeak)
                dao.updateTrack(updated)
                Log.d(TAG, "Successfully scanned track ${track.id}: LUFS=${"%.1f".format(lufs)}, TP=${"%.1f".format(truePeak)} dB")
            } else {
                Log.w(TAG, "Track ${track.id} returned invalid LUFS ($lufs), will retry later")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error scanning track ${track.id}: ${e.message}", e)
        } finally {
            try {
                if (nativeHandle != 0L) nativeDestroyAnalyzer(nativeHandle)
            } catch (_: Exception) {}
            try {
                decoder?.stop()
                decoder?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }

    private fun resolveAudioPath(track: LocalTrack): String? {
        return when {
            track.localAudioPath.startsWith("exo_cache://") -> null
            track.localAudioPath.isNotEmpty() -> track.localAudioPath
            else -> null
        }
    }

    private external fun nativeCreateAnalyzer(channels: Int, sampleRate: Int): Long
    private external fun nativeDestroyAnalyzer(handle: Long)
    private external fun nativeAddFramesFloat(handle: Long, samples: FloatArray, numFrames: Int)
    private external fun nativeGetAnalyzedLoudness(handle: Long): Float
    private external fun nativeGetAnalyzedTruePeakDb(handle: Long): Float
}
