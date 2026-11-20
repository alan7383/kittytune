package com.alananasss.kittytune.ui.player.cover

import android.graphics.Matrix
import android.view.TextureView
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import coil.compose.AsyncImage
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.network.ProxyManager
import com.zionhuang.innertube.models.YouTubeClient
import kotlinx.coroutines.delay
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.util.concurrent.TimeUnit
import kotlin.math.cos

private const val FLAT_GYRO_COVER_ZOOM = 1.02f

private const val BROWSER_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"

@Composable
fun AnimatedArtwork(
    artworkUrl: String?,
    animatedCoverUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    enableGyroscope: Boolean? = null,
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val prefGyroEnabled by prefs.getGyroscopeCoversFlow().collectAsState(initial = prefs.getGyroscopeCoversEnabled())
    val gyroscopeActive = enableGyroscope ?: prefGyroEnabled
    val flatCover by prefs.getGyroscopeFlatCoverFlow().collectAsState(initial = prefs.getGyroscopeFlatCover())
    val tilt by rememberGyroscopeTilt(enabled = gyroscopeActive)
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        ArtworkPlaceholder(Modifier.fillMaxSize())

        if (gyroscopeActive && !flatCover) {
            GyroscopeLayeredCoverContent(
                artworkUrl = artworkUrl,
                tilt = tilt,
                contentScale = contentScale,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize()
            )
        } else if (gyroscopeActive && flatCover) {
            val flatShiftPx = with(density) { 6.dp.toPx() }
            AsyncImage(
                model = artworkUrl,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val rotX = -tilt.tiltY * 9f
                        val rotY = tilt.tiltX * 9f
                        rotationX = rotX
                        rotationY = rotY
                        cameraDistance = 22f * density.density
                        translationX = tilt.tiltX * flatShiftPx
                        translationY = tilt.tiltY * flatShiftPx
                        scaleX = FLAT_GYRO_COVER_ZOOM / cos(rotY * 0.017453292f)
                        scaleY = FLAT_GYRO_COVER_ZOOM / cos(rotX * 0.017453292f)
                    }
            )
        } else {
            AsyncImage(
                model = artworkUrl,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!animatedCoverUrl.isNullOrBlank()) {
            val videoShiftPx = with(density) { 10.dp.toPx() }
            CanvasVideo(
                canvasUrl = animatedCoverUrl,
                isPlaying = isPlaying,
                modifier = Modifier
                    .fillMaxSize()
                    .let { mod ->
                        if (gyroscopeActive) {
                            mod.graphicsLayer {
                                if (flatCover) {
                                    val rotXDeg = -tilt.tiltY * 9f
                                    val rotYDeg = tilt.tiltX * 9f
                                    rotationX = rotXDeg
                                    rotationY = rotYDeg
                                    cameraDistance = 22f * density.density
                                    translationX = tilt.tiltX * videoShiftPx
                                    translationY = tilt.tiltY * videoShiftPx
                                    scaleX = FLAT_GYRO_COVER_ZOOM / cos(rotYDeg * 0.017453292f)
                                    scaleY = FLAT_GYRO_COVER_ZOOM / cos(rotXDeg * 0.017453292f)
                                } else {
                                    scaleX = 1.06f
                                    scaleY = 1.06f
                                    translationX = tilt.tiltX * videoShiftPx
                                    translationY = tilt.tiltY * videoShiftPx
                                }
                            }
                        } else {
                            mod
                        }
                    }
            )
        }
    }
}

@Composable
fun CanvasVideo(
    canvasUrl: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    clipStartMs: Long = 0L,
    clipEndMs: Long = 0L,
    userAgent: String? = null,
    onPlaybackError: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isVideoReady by remember(canvasUrl, clipStartMs, clipEndMs, userAgent) { mutableStateOf(false) }

    val player = remember(canvasUrl, userAgent) {
        val okHttpClient = ProxyManager.getOkHttpClient()
            .newBuilder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                val builder = request.newBuilder()
                when {
                    url.contains("googlevideo.com") || url.contains("youtube.com") -> {
                        val isMobileStream = url.contains("c=VISIONOS") ||
                            url.contains("c=IOS") ||
                            url.contains("c=ANDROID_VR") ||
                            YoutubeParsingHelper.isVisionOsStreamingUrl(url) ||
                            YoutubeParsingHelper.isAndroidStreamingUrl(url)

                        val ua = userAgent ?: when {
                            url.contains("c=VISIONOS") -> YouTubeClient.VISIONOS.userAgent
                            url.contains("c=IOS") -> YouTubeClient.IOS.userAgent
                            url.contains("c=ANDROID_VR") -> YouTubeClient.ANDROID_VR.userAgent
                            YoutubeParsingHelper.isVisionOsStreamingUrl(url) ->
                                YoutubeParsingHelper.getVisionOsUserAgent(null)
                            YoutubeParsingHelper.isAndroidStreamingUrl(url) ->
                                YoutubeParsingHelper.getAndroidUserAgent(null)
                            else -> BROWSER_UA
                        }
                        builder
                            .header("User-Agent", ua)
                            .header("Accept", "*/*")
                        if (!isMobileStream) {
                            builder
                                .header("Origin", "https://www.youtube.com")
                                .header("Referer", "https://www.youtube.com/")
                        }
                    }
                    url.contains("scdn.co") -> {
                        builder
                            .header("User-Agent", userAgent ?: BROWSER_UA)
                            .header("Referer", "https://open.spotify.com/")
                    }
                    url.contains("apple.com") -> {
                        builder
                            .header("User-Agent", BROWSER_UA)
                            .header("Origin", "https://music.apple.com")
                            .header("Referer", "https://music.apple.com/")
                    }
                    url.contains("sndcdn.com") || url.contains("soundcloud.com") -> {
                        builder
                            .header("User-Agent", BROWSER_UA)
                            .header("Referer", "https://soundcloud.com/")
                    }
                }
                chain.proceed(builder.build())
            }
            .build()
        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        // Do NOT attach ClippingConfiguration here: Media3's ClippingMediaSource throws
        // IllegalClippingException on YouTube DASH fragmented MP4 / WebM streams before sidx
        // seek tables settle. Instead we seek to clipStartMs on STATE_READY and loop manually.
        val mediaItem = MediaItem.Builder()
            .setUri(canvasUrl)
            .apply {
                if (canvasUrl.contains(".m3u8")) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(1920, 1920)
                    .setPreferredVideoMimeType(MimeTypes.VIDEO_H264)
                    .build()
            )
        }

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                setAudioAttributes(AudioAttributes.DEFAULT, false)
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                playWhenReady = isPlaying
                setMediaItem(mediaItem)
                prepare()
            }
    }

    LaunchedEffect(player, isPlaying) {
        if (isPlaying) {
            player.play()
        } else {
            player.pause()
        }
    }

    // Safe sample-window loop for YouTube Samples without ClippingMediaSource.
    LaunchedEffect(player, isPlaying, clipStartMs, clipEndMs) {
        if (!isPlaying || clipEndMs <= clipStartMs) return@LaunchedEffect
        while (true) {
            delay(350L)
            if (player.playbackState == Player.STATE_READY && player.isCurrentMediaItemSeekable) {
                val dur = player.duration
                val effectiveEnd = if (dur > 0L) minOf(clipEndMs, dur - 500L) else clipEndMs
                val effectiveStart = if (dur > 0L) {
                    clipStartMs.coerceAtMost((effectiveEnd - 5_000L).coerceAtLeast(0L))
                } else {
                    clipStartMs
                }
                if (effectiveEnd > effectiveStart && player.currentPosition >= effectiveEnd) {
                    player.seekTo(effectiveStart)
                }
            }
        }
    }

    DisposableEffect(player, lifecycleOwner, clipStartMs, clipEndMs) {
        var didInitialSeek = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (isPlaying) player.play()
                Lifecycle.Event.ON_PAUSE -> player.pause()
                else -> Unit
            }
        }
        val playerListener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                android.util.Log.d("AnimatedArtwork", "onRenderedFirstFrame fired for $canvasUrl")
                isVideoReady = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                android.util.Log.d("AnimatedArtwork", "onPlaybackStateChanged: state=$playbackState, isPlaying=${player.isPlaying}")
                if (playbackState == Player.STATE_READY) {
                    if (!didInitialSeek && clipStartMs > 0L && player.isCurrentMediaItemSeekable) {
                        val dur = player.duration
                        val safeStart = if (dur > 0L) {
                            clipStartMs.coerceAtMost((dur - 8_000L).coerceAtLeast(0L))
                        } else {
                            clipStartMs
                        }
                        if (safeStart > 0L) {
                            player.seekTo(safeStart)
                        }
                        didInitialSeek = true
                    }
                    if (player.videoSize.width > 0) {
                        isVideoReady = true
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("AnimatedArtwork", "Player error (${error.errorCodeName}) for $canvasUrl", error)
                isVideoReady = false
                onPlaybackError?.invoke()
            }
        }
        player.addListener(playerListener)
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            player.removeListener(playerListener)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    val videoAlpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "canvas_video_alpha"
    )

    Box(modifier = modifier) {
        key(player) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        player.setVideoTextureView(this)
                        val listener = object : Player.Listener, View.OnLayoutChangeListener {
                            private var videoWidth = player.videoSize.width
                            private var videoHeight = player.videoSize.height

                            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                                videoWidth = videoSize.width
                                videoHeight = videoSize.height
                                applyMatrix()
                            }

                            override fun onLayoutChange(
                                v: View, l: Int, t: Int, r: Int, b: Int,
                                ol: Int, ot: Int, or: Int, ob: Int
                            ) {
                                applyMatrix()
                            }

                            private fun applyMatrix() {
                                if (videoWidth <= 0 || videoHeight <= 0) return
                                val viewWidth = width.toFloat()
                                val viewHeight = height.toFloat()
                                if (viewWidth <= 0 || viewHeight <= 0) return

                                val videoAspect = videoWidth.toFloat() / videoHeight
                                val viewAspect = viewWidth / viewHeight

                                val scaleX: Float
                                val scaleY: Float

                                if (videoAspect > viewAspect) {
                                    scaleX = videoAspect / viewAspect
                                    scaleY = 1f
                                } else {
                                    scaleX = 1f
                                    scaleY = viewAspect / videoAspect
                                }

                                val matrix = Matrix()
                                matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
                                setTransform(matrix)
                            }
                        }
                        addOnLayoutChangeListener(listener)
                        player.addListener(listener)
                        tag = player to listener
                    }
                },
                update = { textureView ->
                    val attached = (textureView.tag as? Pair<*, *>)?.first
                    if (attached !== player) {
                        player.setVideoTextureView(textureView)
                    }
                },
                onRelease = { textureView ->
                    player.clearVideoTextureView(textureView)
                    val listener = (textureView.tag as? Pair<*, *>)?.second
                    if (listener is Player.Listener) {
                        player.removeListener(listener)
                    }
                    if (listener is View.OnLayoutChangeListener) {
                        textureView.removeOnLayoutChangeListener(listener)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = videoAlpha }
            )
        }
    }
}

@Composable
internal fun ArtworkPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val side = minOf(maxWidth, maxHeight) * 0.4f
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(side)
            )
        }
    }
}
