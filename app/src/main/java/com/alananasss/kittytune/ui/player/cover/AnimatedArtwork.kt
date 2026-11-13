package com.alananasss.kittytune.ui.player.cover

import android.content.Context
import android.graphics.Matrix
import android.view.TextureView
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlin.math.cos

// Constant scale margin of the "flat" gyroscope cover (no-overlay mode): +2% - the middle
// of the configured 1–3% range. Keeps the edges covered at any tilt; visually imperceptible.
private const val FLAT_GYRO_COVER_ZOOM = 1.02f

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
    // "No overlay" mode: the layered shadowbox is replaced by ONE clean artwork at its
    // natural size that tilts with the gyroscope. Toggled from Settings -> Covers ->
    // "Cover without overlay" (shown under the 3D gyroscope switch) - the cover itself
    // stays clean, with no control buttons on top of the artwork.
    val flatCover by prefs.getGyroscopeFlatCoverFlow().collectAsState(initial = prefs.getGyroscopeFlatCover())
    val tilt by rememberGyroscopeTilt(enabled = gyroscopeActive)
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        // Underneath the image rather than as its error state, so it also covers the gap while the
        // cover loads. A local file with no embedded art, a cover-less Spotify track, or a failed
        // load used to leave a bare grey square here.
        ArtworkPlaceholder(Modifier.fillMaxSize())

        if (gyroscopeActive && !flatCover) {
            // Full layered 3D shadowbox: blurred backdrop, midground, cast shadow,
            // popping foreground subject and the specular glint.
            GyroscopeLayeredCoverContent(
                artworkUrl = artworkUrl,
                tilt = tilt,
                contentScale = contentScale,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize()
            )
        } else if (gyroscopeActive && flatCover) {
            // "No overlay": one clean artwork that tilts with the gyroscope like a physical card.
            // On top of the rotation compensation keep a CONSTANT +2% margin (the configured
            // 1–3% range): empty corners never peek out at the edges at any tilt, and the
            // extra zoom is visually indistinguishable.
            val flatShiftPx = with(density) { 6.dp.toPx() }
            AsyncImage(
                model = artworkUrl,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Reads of [tilt] stay INSIDE the lambda: the sensor updates
                        // ~60 times per second, and a deferred layer read invalidates
                        // only this layer instead of recomposing the whole artwork.
                        val rotX = -tilt.tiltY * 9f
                        val rotY = tilt.tiltX * 9f
                        rotationX = rotX
                        rotationY = rotY
                        cameraDistance = 22f * density.density
                        translationX = tilt.tiltX * flatShiftPx
                        translationY = tilt.tiltY * flatShiftPx
                        // +2% on top of the 1/cos edge compensation.
                        scaleX = FLAT_GYRO_COVER_ZOOM / cos(rotY * 0.017453292f)
                        scaleY = FLAT_GYRO_COVER_ZOOM / cos(rotX * 0.017453292f)
                    }
            )
        } else {
            // Base static artwork
            AsyncImage(
                model = artworkUrl,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Animated cover video overlay
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
                                    // The animated canvas rides the SAME clean card tilt as the
                                    // static artwork (rotation + 1/cos edge compensation + the same
                                    // +2% margin), so video and image move as one body.
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
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isVideoReady by remember(canvasUrl) { mutableStateOf(false) }

    val player = remember(canvasUrl) {
        val isAppleStream = canvasUrl.contains("apple.com") || canvasUrl.contains("itunes.apple.com")
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .apply {
                if (isAppleStream) {
                    setDefaultRequestProperties(
                        mapOf(
                            "Origin" to "https://music.apple.com",
                            "Referer" to "https://music.apple.com/",
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                        )
                    )
                }
            }

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

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
                    // This player renders a full-screen background that is blurred by 40dp on
                    // top of being a decorative layer: maximum-bitrate decoding was burning GPU
                    // and memory for detail nobody can see while the main audio player runs.
                    // Capping at 720p keeps the visual result identical under that blur.
                    .setMaxVideoSize(1280, 720)
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

    DisposableEffect(player, lifecycleOwner) {
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
                if (playbackState == Player.STATE_READY && player.videoSize.width > 0) {
                    isVideoReady = true
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("AnimatedArtwork", "Player error for $canvasUrl", error)
            }
        }
        player.addListener(playerListener)
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            player.removeListener(playerListener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    val videoAlpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "canvas_video_alpha"
    )

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    player.setVideoTextureView(this)
                    val listener = object : Player.Listener, View.OnLayoutChangeListener {
                        private var videoWidth = 0
                        private var videoHeight = 0

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
                    tag = listener
                }
            },
            update = { textureView ->
                player.setVideoTextureView(textureView)
            },
            onRelease = { textureView ->
                player.clearVideoTextureView(textureView)
                val listener = textureView.tag as? Player.Listener
                if (listener != null) {
                    player.removeListener(listener)
                }
                if (listener is View.OnLayoutChangeListener) {
                    textureView.removeOnLayoutChangeListener(listener)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .alpha(videoAlpha)
        )
    }
}

/** A themed note on the container colour, shown while the cover loads and when there is none. */
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
