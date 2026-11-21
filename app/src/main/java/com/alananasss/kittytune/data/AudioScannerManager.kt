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

    // true if kittytune_audio_dsp.so has loaded. Otherwise JNI calls throw UnsatisfiedLinkError
    // (an Error, not an Exception - catch(e: Exception) does NOT catch it and would kill the scanning coroutine).
    private var isNativeAvailable = false

    init {
        try {
            System.loadLibrary("kittytune_audio_dsp")
            isNativeAvailable = true
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
        // Without the native library, LUFS analysis is impossible: bail out right away
        // instead of spinning up MediaExtractor/MediaCodec in vain for every track.
        if (!isNativeAvailable) {
            Log.w(TAG, "Native DSP library unavailable — loudness scanning disabled")
            return
        }
        val dao = AppDatabase.getDatabase(context).downloadDao()
        // Downloads scanned before the player kept its own record: hand their figures over, so they
        // start at the right level like the rest.
        dao.getAllStoredTracksList().forEach { rememberLoudness(it) }
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
                rememberLoudness(updated)
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
            // Split, like the two blocks above: sharing one try meant a throwing stop() - routine
            // for a codec that was configured but never started - skipped the release and leaked
            // the native MediaCodec instance.
            try {
                decoder?.stop()
            } catch (_: Exception) {}
            try {
                decoder?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }

    /** A whole-file scan is the best figure there is; the player uses it for the normalization gain. */
    private fun rememberLoudness(track: LocalTrack) {
        val lufs = track.lufs ?: return
        if (lufs <= -70f) return
        com.alananasss.kittytune.audio.normalization.LoudnessStore.put(
            track.id.toString(),
            com.alananasss.kittytune.audio.normalization.LoudnessStore.Entry(
                lufs, track.truePeak ?: -120f, Float.MAX_VALUE,
                com.alananasss.kittytune.audio.normalization.LoudnessStore.Source.FULL,
            ),
        )
    }

    /**
     * Integrated loudness and true peak of decoded windows of a track, each interleaved with
     * [channels] channels (a mono window is played on both speakers, so the figure compares with a
     * stereo measurement). Null when there is too little.
     */
    fun measureWindows(windows: List<FloatArray>, sampleRate: Int, channels: Int): Pair<Float, Float>? {
        if (windows.isEmpty()) return null
        val handle = nativeCreateAnalyzer(2, sampleRate)
        if (handle == 0L) return null
        try {
            for (window in windows) {
                if (channels >= 2) {
                    nativeAddFramesFloat(handle, window, window.size / 2)
                } else {
                    val stereo = FloatArray(window.size * 2)
                    for (i in window.indices) {
                        stereo[2 * i] = window[i]
                        stereo[2 * i + 1] = window[i]
                    }
                    nativeAddFramesFloat(handle, stereo, window.size)
                }
            }
            val lufs = nativeGetAnalyzedLoudness(handle)
            return if (lufs > -70f) lufs to nativeGetAnalyzedTruePeakDb(handle) else null
        } finally {
            nativeDestroyAnalyzer(handle)
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
