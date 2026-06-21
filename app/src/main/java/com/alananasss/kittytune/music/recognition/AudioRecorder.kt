package com.alananasss.kittytune.music.recognition

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext

class RecordControl {
    @Volatile var shouldStop: Boolean = false
}

/**
 * Records audio in 16-bit PCM mono at 16kHz, which is the required format for Shazam DejaVu signatures.
 */
class AudioRecorder {
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    @SuppressLint("MissingPermission")
    suspend fun recordAudio(
        durationMs: Long,
        control: RecordControl? = null,
        onProgress: ((ByteArray) -> Unit)? = null
    ): ByteArray {
        return withContext(Dispatchers.IO) {
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 4
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord failed to initialize")
            }

            val outStream = ByteArrayOutputStream()
            val buffer = ByteArray(bufferSize)

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
                        outStream.write(buffer, 0, readResult)
                    }

                    if (onProgress != null && (System.currentTimeMillis() - lastCheckTime) >= 3000L) {
                        lastCheckTime = System.currentTimeMillis()
                        onProgress(outStream.toByteArray())
                    }
                }
            } finally {
                audioRecord.stop()
                audioRecord.release()
            }

            outStream.toByteArray()
        }
    }
}
