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
import coil.compose.AsyncImage

@Composable
fun AnimatedArtwork(
    artworkUrl: String?,
    animatedCoverUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    Box(modifier = modifier) {
        // Base static artwork
        AsyncImage(
            model = artworkUrl,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize()
        )

        // Animated cover video overlay
        if (!animatedCoverUrl.isNullOrBlank()) {
            CanvasVideo(
                canvasUrl = animatedCoverUrl,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize()
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
                    .setForceHighestSupportedBitrate(true)
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
