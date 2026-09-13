package com.alananasss.kittytune.audio.automix

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Headless, zero-latency [AudioSink] for offline audio analysis.
 *
 * Captures decoded PCM audio frames directly into memory as mono floats.
 * Bypasses Android [android.media.AudioTrack] entirely, so decoding proceeds
 * at the maximum speed possible (as fast as the hardware decoder can output).
 */
@UnstableApi
class FastCaptureAudioSink(
    private val targetDurationUs: Long,
    private val shouldCancel: () -> Boolean = { false },
    private val onComplete: () -> Unit = {},
) : AudioSink {

    class CapturedPcm(
        val samples: FloatArray,
        val sampleRate: Int,
        val firstSampleTimeUs: Long,
    )

    private var listener: AudioSink.Listener? = null
    private var sampleRate: Int = 44100
    private var channelCount: Int = 2
    private var pcmEncoding: Int = C.ENCODING_PCM_16BIT

    private val chunks = ArrayList<FloatArray>()
    private var totalFrames = 0
    private var capturedDurationUs: Long = 0L
    private var lastPositionUs: Long = 0L
    private var firstSampleTimeUs: Long = -1L
    private var isEndedFlag: Boolean = false

    private var cachedPcm: CapturedPcm? = null

    @Synchronized
    fun getPcm(): CapturedPcm? {
        if (cachedPcm != null) return cachedPcm
        if (chunks.isEmpty() || sampleRate <= 0) return null
        val total = chunks.sumOf { it.size }
        val all = FloatArray(total)
        var pos = 0
        for (c in chunks) {
            c.copyInto(all, pos)
            pos += c.size
        }
        val result = CapturedPcm(all, sampleRate, if (firstSampleTimeUs >= 0L) firstSampleTimeUs else 0L)
        cachedPcm = result
        return result
    }

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
    }

    override fun supportsFormat(format: Format): Boolean =
        androidx.media3.common.util.Util.isEncodingLinearPcm(format.pcmEncoding)

    override fun getFormatSupport(format: Format): Int =
        if (androidx.media3.common.util.Util.isEncodingLinearPcm(format.pcmEncoding)) {
            AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        } else {
            AudioSink.SINK_FORMAT_UNSUPPORTED
        }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long = lastPositionUs

    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?
    ) {
        sampleRate = if (inputFormat.sampleRate > 0) inputFormat.sampleRate else 44100
        channelCount = if (inputFormat.channelCount > 0) inputFormat.channelCount else 2
        pcmEncoding = if (inputFormat.pcmEncoding != C.ENCODING_INVALID) inputFormat.pcmEncoding else C.ENCODING_PCM_16BIT
        android.util.Log.d("FastCaptureAudioSink", "configure: rate=$sampleRate, channels=$channelCount, encoding=$pcmEncoding, inputFormat=$inputFormat")
    }

    override fun play() {
        android.util.Log.d("FastCaptureAudioSink", "play() called")
    }

    override fun pause() {
        android.util.Log.d("FastCaptureAudioSink", "pause() called")
    }

    override fun flush() {
        android.util.Log.d("FastCaptureAudioSink", "flush() called")
    }

    override fun reset() {
        android.util.Log.d("FastCaptureAudioSink", "reset() called (hasCached=${cachedPcm != null})")
        synchronized(this) {
            if (cachedPcm == null && chunks.isNotEmpty()) {
                cachedPcm = getPcm()
            }
            chunks.clear()
            totalFrames = 0
            capturedDurationUs = 0L
            lastPositionUs = 0L
            firstSampleTimeUs = -1L
            isEndedFlag = false
        }
    }

    override fun release() {}

    override fun handleDiscontinuity() {
        android.util.Log.d("FastCaptureAudioSink", "handleDiscontinuity() called")
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {
        android.util.Log.d("FastCaptureAudioSink", "handleBuffer: remaining=${buffer.remaining()}, pts=$presentationTimeUs, capturedUs=$capturedDurationUs / $targetDurationUs")
        if (shouldCancel() || capturedDurationUs >= targetDurationUs) {
            buffer.position(buffer.limit())
            isEndedFlag = true
            android.util.Log.d("FastCaptureAudioSink", "Target reached or cancelled -> onComplete()")
            onComplete()
            return true
        }

        if (firstSampleTimeUs < 0L) {
            firstSampleTimeUs = presentationTimeUs
        }

        val remainingBytes = buffer.remaining()
        if (remainingBytes <= 0) return true

        val channels = max(1, channelCount)
        val rate = max(1, sampleRate)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        val mono: FloatArray
        if (pcmEncoding == C.ENCODING_PCM_FLOAT) {
            val fb = buffer.asFloatBuffer()
            val frames = fb.remaining() / channels
            mono = FloatArray(frames)
            for (f in 0 until frames) {
                var acc = 0f
                for (c in 0 until channels) acc += fb.get(f * channels + c)
                mono[f] = acc / channels
            }
        } else {
            // 16-bit PCM
            val sb = buffer.asShortBuffer()
            val frames = sb.remaining() / channels
            mono = FloatArray(frames)
            for (f in 0 until frames) {
                var acc = 0f
                for (c in 0 until channels) acc += sb.get(f * channels + c) / 32768f
                mono[f] = acc / channels
            }
        }

        if (mono.isNotEmpty()) {
            synchronized(this) {
                chunks.add(mono)
                totalFrames += mono.size
            }
            val framesAdded = mono.size
            val addedUs = framesAdded * 1_000_000L / rate
            capturedDurationUs += addedUs
            lastPositionUs = presentationTimeUs + addedUs
        }

        buffer.position(buffer.limit())

        if (capturedDurationUs >= targetDurationUs) {
            android.util.Log.d("FastCaptureAudioSink", "capturedDurationUs ($capturedDurationUs) >= targetDurationUs ($targetDurationUs) -> onComplete()")
            isEndedFlag = true
            onComplete()
        }

        return true
    }

    override fun playToEndOfStream() {
        android.util.Log.d("FastCaptureAudioSink", "playToEndOfStream() called -> onComplete()")
        isEndedFlag = true
        onComplete()
    }

    override fun isEnded(): Boolean = isEndedFlag

    override fun hasPendingData(): Boolean = false

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {}

    override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {}

    override fun getSkipSilenceEnabled(): Boolean = false

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {}

    override fun getAudioAttributes(): AudioAttributes? = null

    override fun setAudioSessionId(audioSessionId: Int) {}

    override fun setAuxEffectInfo(auxEffectInfo: androidx.media3.common.AuxEffectInfo) {}

    override fun enableTunnelingV21() {}

    override fun disableTunneling() {}

    override fun setVolume(volume: Float) {}

    override fun getAudioTrackBufferSizeUs(): Long = 0L
}
