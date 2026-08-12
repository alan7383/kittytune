package com.alananasss.kittytune.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LocalTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object AudioScannerManager {
    private val TAG = "AudioScannerManager"
    private var reusableFloatBuffer = FloatArray(16384 * 2)

    init {
        System.loadLibrary("kittytune_audio_dsp")
    }

    fun scanDownloadedTracks(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getDatabase(context).downloadDao()
            val tracks = dao.getTracksWithNullLufs()
            tracks.forEach { track ->
                try {
                    scanTrack(context, track, dao)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to scan track ${track.id}: ${e.message}")
                }
            }
        }
    }

    private suspend fun scanTrack(
        context: Context,
        track: LocalTrack,
        dao: com.alananasss.kittytune.data.local.DownloadDao
    ) = withContext(Dispatchers.IO) {
        val path = resolveAudioPath(track)
        if (path == null) {
            Log.w(TAG, "Cannot resolve path for track ${track.id}")
            return@withContext
        }

        val file = File(path)
        if (!file.exists() && !path.startsWith("content://")) {
            Log.w(TAG, "File not found: $path")
            return@withContext
        }

        val extractor = MediaExtractor()
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

            val decoder = MediaCodec.createDecoderByType(
                extractor.getTrackFormat(audioTrackIndex).getString(MediaFormat.KEY_MIME)!!
            )
            decoder.configure(extractor.getTrackFormat(audioTrackIndex), null, null, 0)
            decoder.start()

            val nativeHandle = nativeCreateAnalyzer(channels, sampleRate)
            if (nativeHandle == 0L) {
                decoder.release()
                extractor.release()
                Log.e(TAG, "Failed to create native analyzer")
                return@withContext
            }

            try {
                val bufferInfo = android.media.MediaCodec.BufferInfo()
                var eos = false

                while (!eos) {
                    val inIndex = decoder.dequeueInputBuffer(10000)
                    if (inIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            val time = extractor.sampleTime
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, time, 0)
                            extractor.advance()
                        }
                    }

                    val outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                    when {
                        outIndex >= 0 -> {
                            val outputBuffer = decoder.getOutputBuffer(outIndex)!!
                            if (bufferInfo.size > 0) {
                                processDecodedBuffer(outputBuffer, bufferInfo, channels, nativeHandle)
                            }
                            decoder.releaseOutputBuffer(outIndex, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                eos = true
                            }
                        }
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFormat = decoder.outputFormat
                            channels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 2)
                            sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100)
                        }
                        outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                    }
                }

                val lufs = nativeGetAnalyzedLoudness(nativeHandle)
                val truePeak = nativeGetAnalyzedTruePeakDb(nativeHandle)

                if (lufs > -70f) {
                    val updated = track.copy(lufs = lufs, truePeak = truePeak)
                    dao.updateTrack(updated)
                    Log.i(TAG, "Scanned track ${track.id}: LUFS=${"%.1f".format(lufs)}, TP=${"%.1f".format(truePeak)} dB")
                } else {
                    Log.w(TAG, "Track ${track.id} returned invalid LUFS")
                }
            } finally {
                nativeDestroyAnalyzer(nativeHandle)
                decoder.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning track ${track.id}: ${e.message}", e)
        } finally {
            extractor.release()
        }
    }

    private fun processDecodedBuffer(
        buffer: ByteBuffer,
        bufferInfo: android.media.MediaCodec.BufferInfo,
        channels: Int,
        nativeHandle: Long
    ) {
        val numFrames = bufferInfo.size / (channels * 2)
        if (numFrames <= 0) return

        val requiredSize = numFrames * channels
        if (reusableFloatBuffer.size < requiredSize) {
            reusableFloatBuffer = FloatArray(requiredSize)
        }

        val shortBuf = buffer.duplicate()
        shortBuf.position(bufferInfo.offset)
        shortBuf.limit(bufferInfo.offset + bufferInfo.size)

        for (i in 0 until requiredSize) {
            reusableFloatBuffer[i] = if (shortBuf.remaining() >= 2) {
                shortBuf.getShort().toFloat() / 32768f
            } else 0f
        }

        nativeAddFramesFloat(nativeHandle, reusableFloatBuffer, numFrames)
    }

    private fun resolveAudioPath(track: LocalTrack): String? {
        return when {
            track.localAudioPath.startsWith("exo_cache://") -> {
                // Cached tracks need to be extracted first to a temp file
                // For now, skip cached tracks - they'll be measured during playback
                null
            }
            track.localAudioPath.isNotEmpty() -> track.localAudioPath
            else -> null
        }
    }

    // --- JNI native methods ---
    private external fun nativeCreateAnalyzer(channels: Int, sampleRate: Int): Long
    private external fun nativeDestroyAnalyzer(handle: Long)
    private external fun nativeAddFramesFloat(handle: Long, samples: FloatArray, numFrames: Int)
    private external fun nativeGetAnalyzedLoudness(handle: Long): Float
    private external fun nativeGetAnalyzedTruePeakDb(handle: Long): Float
}
