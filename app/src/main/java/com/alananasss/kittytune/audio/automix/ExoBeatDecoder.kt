package com.alananasss.kittytune.audio.automix

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Headless ExoPlayer decoder that decodes Widevine DRM-protected streams
 * (such as SoundCloud CENC HLS) directly into mono PCM samples for [BeatAnalyzer].
 */
@UnstableApi
object ExoBeatDecoder {

    private const val TAG = "ExoBeatDecoder"

    class DecodedMonoPcm(
        val samples: FloatArray,
        val sampleRate: Int,
        val actualStartUs: Long,
    )

    fun decodeMono(
        context: Context,
        streamUrl: String,
        licenseAuthToken: String? = null,
        seekToMs: Long = 0L,
        maxDurationMs: Long = 18_000L,
        timeoutMs: Long = 25_000L,
        shouldCancel: () -> Boolean = { false },
    ): DecodedMonoPcm? {
        if (streamUrl.isBlank() || shouldCancel()) return null

        val latch = CountDownLatch(1)
        val targetDurationUs = maxDurationMs * 1_000L

        val sink = FastCaptureAudioSink(
            targetDurationUs = targetDurationUs,
            shouldCancel = shouldCancel,
            onComplete = { latch.countDown() }
        )

        val thread = HandlerThread("ExoBeatDecoder_${System.currentTimeMillis()}").apply { start() }
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
                            Log.d(TAG, "Configuring offline Widevine DRM session for beat analysis")
                            val manager = DefaultDrmSessionManager.Builder()
                                .setUuidAndExoMediaDrmProvider(
                                    C.WIDEVINE_UUID,
                                    FrameworkMediaDrm.DEFAULT_PROVIDER
                                )
                                .build(androidx.media3.exoplayer.drm.LocalMediaDrmCallback(ByteArray(0)))
                            manager.setMode(DefaultDrmSessionManager.MODE_PLAYBACK, offlineKeySetId)
                            manager
                        } else {
                            Log.d(TAG, "Configuring streaming Widevine DRM session for beat analysis")
                            val callback = SoundCloudDrmCallback(licenseAuthToken)
                            DefaultDrmSessionManager.Builder()
                                .setUuidAndExoMediaDrmProvider(
                                    C.WIDEVINE_UUID,
                                    FrameworkMediaDrm.DEFAULT_PROVIDER
                                )
                                .setMultiSession(true)
                                .build(callback)
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
                        enableAudioTrackPlaybackParams: Boolean
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
                        Log.w(TAG, "ExoBeatDecoder error: ${error.message}", error)
                        decodeFailed = true
                        latch.countDown()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        Log.d(TAG, "ExoBeatDecoder onPlaybackStateChanged: state=$playbackState (ENDED=${Player.STATE_ENDED}, READY=${Player.STATE_READY}, BUFFERING=${Player.STATE_BUFFERING})")
                        if (playbackState == Player.STATE_ENDED) {
                            Log.d(TAG, "STATE_ENDED -> latch.countDown()")
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
                            setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
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
                if (seekToMs > 0) {
                    p.seekTo(seekToMs)
                }
                p.prepare()
                p.play()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start ExoBeatDecoder", e)
                decodeFailed = true
                latch.countDown()
            }
        }

        // Wait on the calling thread
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

        // Retrieve captured PCM before releasing player
        val captured = sink.getPcm()

        // Cleanup on player thread
        val releaseLatch = CountDownLatch(1)
        handler.post {
            try {
                playerListener?.let { player?.removeListener(it) }
                player?.stop()
                player?.clearMediaItems()
                player?.release()
            } catch (_: Exception) {}
            finally {
                releaseLatch.countDown()
                // Quitting after a brief delay ensures all asynchronous release callbacks posted by
                // ExoPlayer internal threads to this looper drain cleanly without triggering
                // "sending message to a Handler on a dead thread".
                handler.postDelayed({
                    try {
                        thread.quitSafely()
                    } catch (_: Exception) {}
                }, 300)
            }
        }

        try {
            releaseLatch.await(1, TimeUnit.SECONDS)
        } catch (_: Exception) {}
        if (captured == null || captured.samples.isEmpty()) {
            Log.w(TAG, "ExoBeatDecoder produced no samples (cancelled=${shouldCancel()}, failed=$decodeFailed)")
            return null
        }

        Log.d(TAG, "ExoBeatDecoder decoded ${captured.samples.size} samples (${captured.samples.size.toFloat() / captured.sampleRate}s) at ${captured.sampleRate}Hz")
        return DecodedMonoPcm(
            samples = captured.samples,
            sampleRate = captured.sampleRate,
            actualStartUs = max(0L, captured.firstSampleTimeUs)
        )
    }
}
