package com.alananasss.kittytune.music.recognition

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

class RecordControl {
    @Volatile var shouldStop: Boolean = false
}

/**
 * Records audio in 16-bit PCM mono at 16kHz, which is the required format for Shazam DejaVu signatures.
 * Supports recording from the microphone or from the device internal playback (Android 10+).
 */
class AudioRecorder {
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    @SuppressLint("MissingPermission")
    suspend fun recordAudio(
        durationMs: Long,
        mediaProjection: MediaProjection? = null,
        control: RecordControl? = null,
        onProgress: ((ByteArray) -> Unit)? = null
    ): ByteArray {
        return withContext(Dispatchers.IO) {
            var projectionCallback: MediaProjection.Callback? = null

            val audioRecord = if (mediaProjection != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cb = object : MediaProjection.Callback() {
                    override fun onStop() {
                        control?.shouldStop = true
                    }
                }
                projectionCallback = cb
                mediaProjection.registerCallback(cb, Handler(Looper.getMainLooper()))
                createDeviceAudioRecord(mediaProjection)
            } else {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize * 4
                )
            }

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                throw IllegalStateException("AudioRecord failed to initialize")
            }

            val isFallback48k = audioRecord.sampleRate == 48000 && audioRecord.channelCount == 2
            val captureBufferSize = if (isFallback48k) {
                AudioRecord.getMinBufferSize(48000, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT) * 2
            } else {
                bufferSize
            }
            val buffer = ByteArray(captureBufferSize.coerceAtLeast(bufferSize))
            val outStream = ByteArrayOutputStream()

            try {
                audioRecord.startRecording()
                val startTime = System.currentTimeMillis()
                var lastCheckTime = startTime

                while (coroutineContext.isActive && (System.currentTimeMillis() - startTime) < durationMs) {
                    if (control?.shouldStop == true) {
                        break
                    }
                    val readResult = audioRecord.read(buffer, 0, buffer.size)
                    if (readResult > 0) {
                        if (isFallback48k) {
                            val converted = downsample48kStereoTo16kMono(buffer, readResult)
                            outStream.write(converted, 0, converted.size)
                        } else {
                            outStream.write(buffer, 0, readResult)
                        }
                    }

                    if (onProgress != null && (System.currentTimeMillis() - lastCheckTime) >= 3000L) {
                        lastCheckTime = System.currentTimeMillis()
                        onProgress(outStream.toByteArray())
                    }
                }
            } finally {
                try {
                    audioRecord.stop()
                } catch (_: Exception) {}
                try {
                    audioRecord.release()
                } catch (_: Exception) {}
                if (projectionCallback != null && mediaProjection != null) {
                    try {
                        mediaProjection.unregisterCallback(projectionCallback)
                    } catch (_: Exception) {}
                }
            }

            outStream.toByteArray()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createDeviceAudioRecord(mediaProjection: MediaProjection): AudioRecord {
        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        // 1st attempt: 16000Hz MONO directly
        val primaryRecord = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(captureConfig)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 4)
            .build()

        if (primaryRecord.state == AudioRecord.STATE_INITIALIZED) {
            return primaryRecord
        }

        primaryRecord.release()

        // Fallback: 48000Hz Stereo
        val fallbackBufferSize = AudioRecord.getMinBufferSize(
            48000,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 4
        return AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(captureConfig)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(48000)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(fallbackBufferSize)
            .build()
    }

    private fun downsample48kStereoTo16kMono(stereo48kBytes: ByteArray, length: Int): ByteArray {
        val numFrames = length / 4
        val outSamples = numFrames / 3
        val outBytes = ByteArray(outSamples * 2)
        val bbIn = ByteBuffer.wrap(stereo48kBytes, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        val bbOut = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until outSamples) {
            val left = bbIn.short.toInt()
            val right = bbIn.short.toInt()
            val mono = ((left + right) / 2).coerceIn(-32768, 32767).toShort()
            bbOut.putShort(mono)
            if (bbIn.remaining() >= 8) {
                bbIn.position(bbIn.position() + 8)
            }
        }
        return outBytes
    }
}
