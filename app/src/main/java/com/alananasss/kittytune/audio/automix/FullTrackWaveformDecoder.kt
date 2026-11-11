package com.alananasss.kittytune.audio.automix

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.alananasss.kittytune.data.SoundCloudDrmCallback
import com.alananasss.kittytune.data.local.ExoCacheManager
import com.alananasss.kittytune.utils.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

/**
 * Headless full-track decoder that turns a stream into a real, zoomable waveform.
 *
 * [ExoBeatDecoder] decodes short windows for beat analysis; this one walks the WHOLE track in a
 * single pass and never keeps the PCM: an [AudioSink] that folds every decoded frame into ~50
 * peak-per-second buckets as it arrives (a 4-minute song costs ~12 000 floats ≈ 48 KB instead of
 * the ~40 MB the raw samples would). Decoding runs through the same cache-backed, DRM-capable
 * ExoPlayer pipeline the beat analyzer uses, so an already-played track mostly reads from disk.
 */
@UnstableApi
object FullTrackWaveformDecoder {

    private const val TAG = "FullWaveformDecoder"

    // One full-track decode at a time — same reasoning as ExoBeatDecoder's gate
    private val decodeGate = Semaphore(1, true)

    private const val GATE_WAIT_MS = 20_000L

    // Waveform resolution: buckets per second of audio. 20 ms per bucket zooms cleanly
    const val PEAKS_PER_SECOND = 50

    // Never decode more than this much audio (podcast/mix uploads can run for hours)
    const val MAX_DURATION_MS = 15 * 60_000L

    class DecodedWaveform(
        val peaks: FloatArray,
        val durationMs: Long,
        // Always [PEAKS_PER_SECOND] for decoded rows; kept per instance for future formats
        val peaksPerSecond: Int,
        
         // True when the pass genuinely covered the audio: the stream ran to its natural end
        // or the full requested duration was captured. A stalled network or a player error
        //  leaves this false - such a wave must never be cached as the track's waveform
        
        val complete: Boolean = true,
    )

     // Decodes [streamUrl] into a peak array. Synchronous and blocking - call from Dispatchers.IO.
     // @param durationHintMs the expected track length; the decode stops after it (or
     // [MAX_DURATION_MS] when unknown)
     // @param onProgress invoked from the decode thread with 0f..1f as buckets fill
     
    fun decodePeaks(
        context: Context,
        streamUrl: String,
        licenseAuthToken: String? = null,
        durationHintMs: Long = 0L,
        timeoutMs: Long = 90_000L,
        shouldCancel: () -> Boolean = { false },
        onProgress: ((Float) -> Unit)? = null,
    ): DecodedWaveform? {
        if (streamUrl.isBlank() || shouldCancel()) return null

        try {
            val deadline = SystemClock.elapsedRealtime() + GATE_WAIT_MS
            while (!decodeGate.tryAcquire(200, TimeUnit.MILLISECONDS)) {
                if (shouldCancel() || SystemClock.elapsedRealtime() >= deadline) {
                    Log.w(TAG, "Waveform decode gate busy; giving up")
                    return null
                }
            }
        } catch (_: InterruptedException) {
            return null
        }

        try {
            return decodeInternal(
                context = context,
                streamUrl = streamUrl,
                licenseAuthToken = licenseAuthToken,
                durationHintMs = durationHintMs,
                timeoutMs = timeoutMs,
                shouldCancel = shouldCancel,
                onProgress = onProgress,
            )
        } finally {
            decodeGate.release()
        }
    }

    private fun decodeInternal(
        context: Context,
        streamUrl: String,
        licenseAuthToken: String?,
        durationHintMs: Long,
        timeoutMs: Long,
        shouldCancel: () -> Boolean,
        onProgress: ((Float) -> Unit)?,
    ): DecodedWaveform? {
        val targetMs = if (durationHintMs > 0L) durationHintMs.coerceAtMost(MAX_DURATION_MS) else MAX_DURATION_MS
        val targetDurationUs = targetMs * 1_000L

        val latch = CountDownLatch(1)
        val sink = PeakCollectingAudioSink(
            targetDurationUs = targetDurationUs,
            shouldCancel = shouldCancel,
            onProgress = onProgress,
            onComplete = { latch.countDown() },
        )

        val thread = HandlerThread("FullWaveformDecoder").apply { start() }
        val handler = Handler(thread.looper)

        var player: ExoPlayer? = null
        var playerListener: Player.Listener? = null
        var decodeFailed = false

        var offlineKeySetId: ByteArray? = null
        if (!licenseAuthToken.isNullOrEmpty() && !licenseAuthToken.contains(".") && licenseAuthToken.length < 120) {
            try {
                offlineKeySetId = android.util.Base64.decode(licenseAuthToken, android.util.Base64.NO_WRAP)
            } catch (_: Exception) {}
        }

        handler.post {
            try {
                val cache = ExoCacheManager.getCache(context)
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(Config.USER_AGENT)
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15_000)
                    .setReadTimeoutMs(15_000)

                val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpFactory)
                val cacheFactory = CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(defaultDataSourceFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

                val drmProvider = DrmSessionManagerProvider {
                    if (!licenseAuthToken.isNullOrEmpty()) {
                        if (offlineKeySetId != null && offlineKeySetId.isNotEmpty()) {
                            val manager = DefaultDrmSessionManager.Builder()
                                .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                                .build(androidx.media3.exoplayer.drm.LocalMediaDrmCallback(ByteArray(0)))
                            manager.setMode(DefaultDrmSessionManager.MODE_PLAYBACK, offlineKeySetId)
                            manager
                        } else {
                            DefaultDrmSessionManager.Builder()
                                .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                                .setMultiSession(true)
                                .build(SoundCloudDrmCallback(licenseAuthToken))
                        }
                    } else {
                        DrmSessionManager.DRM_UNSUPPORTED
                    }
                }

                val mediaSourceFactory = DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(cacheFactory)
                    .setDrmSessionManagerProvider(drmProvider)

                val renderersFactory = object : DefaultRenderersFactory(context) {
                    override fun buildAudioSink(
                        context: Context,
                        enableFloatOutput: Boolean,
                        enableAudioTrackPlaybackParams: Boolean,
                    ): AudioSink = sink
                }

                val p = ExoPlayer.Builder(context)
                    .setLooper(thread.looper)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .setRenderersFactory(renderersFactory)
                    .build()
                player = p
                p.volume = 0f

                val listener = object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.w(TAG, "Waveform decode error: ${error.message}")
                        decodeFailed = true
                        latch.countDown()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            latch.countDown()
                        }
                    }
                }
                playerListener = listener
                p.addListener(listener)

                val isDrm = !licenseAuthToken.isNullOrEmpty() || streamUrl.contains("cenc") || streamUrl.contains("encrypted")
                val mediaItem = MediaItem.Builder()
                    .setUri(streamUrl)
                    .apply {
                        if (streamUrl.contains(".m3u8") || streamUrl.contains("hls") || isDrm) {
                            setMimeType(MimeTypes.APPLICATION_M3U8)
                        }
                        if (isDrm) {
                            val drmBuilder = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            if (offlineKeySetId != null && offlineKeySetId.isNotEmpty()) {
                                drmBuilder.setKeySetId(offlineKeySetId)
                            }
                            setDrmConfiguration(drmBuilder.build())
                        }
                    }
                    .build()
                p.setMediaItem(mediaItem)
                p.prepare()
                p.play()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start waveform decode", e)
                decodeFailed = true
                latch.countDown()
            }
        }

        val startTime = SystemClock.elapsedRealtime()
        while (latch.count > 0) {
            if (shouldCancel() || decodeFailed || SystemClock.elapsedRealtime() - startTime > timeoutMs) {
                break
            }
            try {
                latch.await(100, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                break
            }
        }

        val captured = sink.getWaveform()

        val releaseLatch = CountDownLatch(1)
        handler.post {
            try {
                playerListener?.let { player?.removeListener(it) }
                player?.stop()
                player?.clearMediaItems()
                player?.release()
            } catch (_: Exception) {
            } finally {
                releaseLatch.countDown()
                handler.postDelayed({
                    try {
                        thread.quitSafely()
                    } catch (_: Exception) {
                    }
                }, 300)
            }
        }
        try {
            releaseLatch.await(1, TimeUnit.SECONDS)
        } catch (_: Exception) {
        }

        val peaks = captured?.peaks
        if (peaks == null || peaks.isEmpty() || captured.durationMs <= 0L) {
            Log.w(TAG, "Waveform decode produced nothing (failed=$decodeFailed)")
            return null
        }
        // A player error must not masquerade as a (shorter) waveform: whatever landed in the
        // sink by that point is a random-length prefix of the track, not a usable wave.
        // But when the prefix covers most of the song, drawing it beats showing
        // "waveform unavailable" - the caller renders it uncached and can retry later.
        if (decodeFailed) {
            val coveredFraction = captured.durationMs.toFloat() / targetMs.toFloat()
            if (coveredFraction < 0.5f && captured.durationMs < 30_000L) {
                Log.w(TAG, "Waveform decode failed after ${captured.durationMs} ms; discarding partial peaks")
                return null
            }
            Log.w(TAG, "Waveform decode failed after ${captured.durationMs} ms of ${targetMs} ms; keeping partial wave")
            return DecodedWaveform(
                peaks = peaks,
                durationMs = captured.durationMs,
                peaksPerSecond = captured.peaksPerSecond,
                complete = false,
            )
        }

        Log.d(TAG, "Decoded waveform: ${peaks.size} buckets over ${captured.durationMs} ms (complete=${captured.complete})")
        return DecodedWaveform(
            peaks = peaks,
            durationMs = captured.durationMs,
            peaksPerSecond = captured.peaksPerSecond,
            complete = captured.complete,
        )
    }
}

 // An [AudioSink] that never touches an [android.media.AudioTrack]: it folds each decoded frame
 // straight into fixed 20 ms peak buckets, so a full song passes through in a few dozen KB.
 // Interface-wise it mirrors [FastCaptureAudioSink] (same unsupported-format claim, so ExoPlayer
 // converts exotic PCM encodings before they reach us)
 
@UnstableApi
private class PeakCollectingAudioSink(
    private val targetDurationUs: Long,
    private val shouldCancel: () -> Boolean,
    private val onProgress: ((Float) -> Unit)?,
    private val onComplete: () -> Unit,
) : AudioSink {

    class Captured(val peaks: FloatArray, val durationMs: Long, val peaksPerSecond: Int, val complete: Boolean)

    private var listener: AudioSink.Listener? = null
    private var sampleRate: Int = 44100
    private var channelCount: Int = 2
    private var pcmEncoding: Int = C.ENCODING_PCM_16BIT

    private var peaks: FloatArray = FloatArray(0)
    private var framesPerBucket: Int = 0
    private var totalFrames: Long = 0L
    private var capturedDurationUs: Long = 0L
    private var lastPositionUs: Long = 0L
    private var isEndedFlag: Boolean = false
    /** ExoPlayer played the source to its natural end — everything the stream had, we took. */
    private var endedNaturally: Boolean = false
    /** We captured the full requested duration — the wave covers everything the hint asked for. */
    private var cutAtTarget: Boolean = false
    private var lastProgressAtUs: Long = -10_000_000L

    @Synchronized
    fun getWaveform(): Captured? {
        if (peaks.isEmpty() || totalFrames <= 0L || sampleRate <= 0) return null
        val durationMs = totalFrames * 1_000L / max(1, sampleRate)
        // Per-track visual normalisation: a quiet master must still read as a wave, and a
        // loud one must not clip into a solid block. Expand slightly (pow 0.75) like DJ decks do.
        var maxPeak = 0f
        for (v in peaks) {
            if (v > maxPeak) maxPeak = v
        }
        val safeMax = if (maxPeak > 0.02f) maxPeak else 1f
        val out = FloatArray(peaks.size) { i ->
            val norm = (peaks[i] / safeMax).coerceIn(0f, 1f)
            val shaped = Math.pow(norm.toDouble(), 0.75).toFloat()
            if (shaped < 0.02f) 0.02f else shaped
        }
        return Captured(
            peaks = out,
            durationMs = durationMs,
            peaksPerSecond = FullTrackWaveformDecoder.PEAKS_PER_SECOND,
            complete = endedNaturally || cutAtTarget,
        )
    }

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
    }

    override fun supportsFormat(format: Format): Boolean =
        format.pcmEncoding == C.ENCODING_PCM_16BIT || format.pcmEncoding == C.ENCODING_PCM_FLOAT

    override fun getFormatSupport(format: Format): Int =
        if (supportsFormat(format)) {
            AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        } else {
            AudioSink.SINK_FORMAT_UNSUPPORTED
        }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long = lastPositionUs

    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?,
    ) {
        sampleRate = if (inputFormat.sampleRate > 0) inputFormat.sampleRate else 44100
        channelCount = if (inputFormat.channelCount > 0) inputFormat.channelCount else 2
        pcmEncoding = if (inputFormat.pcmEncoding != C.ENCODING_INVALID) inputFormat.pcmEncoding else C.ENCODING_PCM_16BIT
        framesPerBucket = max(1, sampleRate / FullTrackWaveformDecoder.PEAKS_PER_SECOND)
        val expectedBuckets = ((targetDurationUs / 1_000_000f) * FullTrackWaveformDecoder.PEAKS_PER_SECOND).toInt() + 16
        if (peaks.size < expectedBuckets) {
            val grown = FloatArray(expectedBuckets)
            peaks.copyInto(grown)
            peaks = grown
        }
    }

    override fun play() {}

    override fun pause() {}

    override fun flush() {}

    override fun reset() {}

    override fun release() {}

    override fun handleDiscontinuity() {}

    // Folds one mono frame magnitude into its 20 ms peak bucket
    private fun foldPeak(value: Float, frameOffset: Int, perBucket: Int) {
        val globalFrame = totalFrames + frameOffset
        val bucket = (globalFrame / perBucket).toInt()
        if (bucket < peaks.size && value > peaks[bucket]) {
            peaks[bucket] = value
        }
    }

    @Synchronized
    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        if (shouldCancel() || capturedDurationUs >= targetDurationUs) {
            buffer.position(buffer.limit())
            if (capturedDurationUs >= targetDurationUs) cutAtTarget = true
            isEndedFlag = true
            onComplete()
            return true
        }

        val remainingBytes = buffer.remaining()
        if (remainingBytes <= 0) return true

        val channels = max(1, channelCount)
        val rate = max(1, sampleRate)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        val perBucket = framesPerBucket
        var frames = 0
        if (pcmEncoding == C.ENCODING_PCM_FLOAT) {
            val fb = buffer.asFloatBuffer()
            frames = fb.remaining() / channels
            for (f in 0 until frames) {
                var acc = 0f
                for (c in 0 until channels) {
                    acc += fb.get(f * channels + c)
                }
                foldPeak(abs(acc / channels), f, perBucket)
            }
        } else {
            val sb = buffer.asShortBuffer()
            frames = sb.remaining() / channels
            for (f in 0 until frames) {
                var acc = 0f
                for (c in 0 until channels) {
                    acc += sb.get(f * channels + c) / 32768f
                }
                foldPeak(abs(acc / channels), f, perBucket)
            }
        }

        totalFrames += frames
        val addedUs = frames * 1_000_000L / rate
        capturedDurationUs += addedUs
        lastPositionUs = presentationTimeUs + addedUs

        buffer.position(buffer.limit())

        if (capturedDurationUs - lastProgressAtUs >= 1_000_000L || capturedDurationUs >= targetDurationUs) {
            lastProgressAtUs = capturedDurationUs
            val progress = if (targetDurationUs > 0L) {
                (capturedDurationUs.toFloat() / targetDurationUs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            onProgress?.invoke(progress)
        }

        if (capturedDurationUs >= targetDurationUs) {
            cutAtTarget = true
            isEndedFlag = true
            onComplete()
        }

        return true
    }

    override fun playToEndOfStream() {
        endedNaturally = true
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
