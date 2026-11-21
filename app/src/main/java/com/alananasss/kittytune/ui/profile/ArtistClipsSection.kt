package com.alananasss.kittytune.ui.profile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.artist.ArtistClips
import com.alananasss.kittytune.data.artist.Clip
import com.alananasss.kittytune.data.local.ClipVideoQuality
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.network.ProxyManager
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.pressScale
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.slider.PlayerSlider
import com.zionhuang.innertube.models.YouTubeClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.util.Locale

private const val CLIP_BROWSER_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"

@Composable
fun ArtistClipsSection(artistName: String, playerViewModel: PlayerViewModel) {
    val clips by produceState(initialValue = emptyList<Clip>(), artistName) {
        value = ArtistClips.clipsFor(artistName)
    }
    if (clips.isEmpty()) return
    var watching by remember { mutableStateOf<Clip?>(null) }

    Column(Modifier.fillMaxWidth()) {
        SectionTitle(title = stringResource(R.string.artist_clips_title))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(clips, key = { it.url }) { clip ->
                ClipCard(clip, onClick = hapticAction(HapticKind.UI) { watching = clip })
            }
        }
    }

    watching?.let { clip ->
        ClipPlayerDialog(clip = clip, playerViewModel = playerViewModel, onDismiss = { watching = null })
    }
}

@Composable
fun HomeClipsShelf(
    playerViewModel: PlayerViewModel,
    clips: List<Clip> = ArtistClips.homeClips.collectAsState().value,
) {
    var watching by remember { mutableStateOf<Clip?>(null) }
    if (clips.isEmpty()) return

    Column(Modifier.fillMaxWidth()) {
        SectionTitle(title = stringResource(R.string.home_clips_title))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(clips, key = { it.url }) { clip ->
                ClipCard(clip, onClick = hapticAction(HapticKind.UI) { watching = clip })
            }
        }
    }

    watching?.let { clip ->
        ClipPlayerDialog(clip = clip, playerViewModel = playerViewModel, onDismiss = { watching = null })
    }
}

@Composable
private fun ClipCard(clip: Clip, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier.width(CLIP_CARD_WIDTH)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .pressScale(interaction)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(
                    interactionSource = interaction,
                    indication = androidx.compose.material3.ripple(),
                    onClick = onClick
                )
        ) {
            AsyncImage(
                model = clip.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                formatClock(clip.durationSec * 1000),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            clip.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (clip.viewCount > 0) {
            Text(
                stringResource(R.string.artist_clip_views, formatCompactNumber(clip.viewCount)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClipPlayerDialog(clip: Clip, playerViewModel: PlayerViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val prefs = remember { PlayerPreferences(context) }
    var clipStreams by remember(clip) { mutableStateOf<ArtistClips.ClipStreams?>(null) }
    // 0 = primary adaptive (up to 4K/2160p), 1 = backup/1080p adaptive, 2 = muxed 360p
    var fallbackStage by remember(clip) { mutableIntStateOf(0) }
    var failed by remember(clip) { mutableStateOf(false) }
    var isFullscreen by remember(clip) { mutableStateOf(false) }
    var isPaused by remember(clip) { mutableStateOf(false) }
    var hasEnded by remember(clip) { mutableStateOf(false) }
    var controlsVisible by remember(clip) { mutableStateOf(true) }
    var positionMs by remember(clip) { mutableLongStateOf(0L) }
    var durationMs by remember(clip) { mutableLongStateOf(0L) }
    var videoAspectRatio by remember(clip) { mutableFloatStateOf(16f / 9f) }
    val quality = remember(clip) { prefs.getClipVideoQuality() }

    LaunchedEffect(clip) {
        fallbackStage = 0
        clipStreams = ArtistClips.streams(clip)
        if (clipStreams?.hasAnything != true) failed = true
    }

    var pendingSyncMs by remember(clip) { mutableLongStateOf(0L) }
    LaunchedEffect(clip) {
        if (playerViewModel.uiCurrentTrack != null) {
            val musicPosition = runCatching { MusicManager.player.currentPosition }.getOrDefault(0L)
            if (musicPosition > 3_000L) pendingSyncMs = musicPosition
        }
    }

    var wasPlaying by remember(clip) { mutableStateOf(false) }
    DisposableEffect(clip) {
        wasPlaying = playerViewModel.isPlaying
        Together.stopListening()
        playerViewModel.pause()
        onDispose {
            if (playerViewModel.uiCurrentTrack != null && positionMs > 0L) {
                val trackDuration = playerViewModel.uiCurrentTrack?.actualDurationMs ?: 0L
                val target = if (trackDuration > 0) positionMs.coerceAtMost(trackDuration - 500L) else positionMs
                runCatching { MusicManager.player.seekTo(target.coerceAtLeast(0L)) }
                playerViewModel.currentPosition = target.coerceAtLeast(0L)
            }
            if (wasPlaying && !playerViewModel.isPlaying) playerViewModel.togglePlayPause()
        }
    }

    val mediaSourceFactory = remember(clip) {
        val okHttpClient = ProxyManager.getOkHttpClient().newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                val builder = req.newBuilder()
                if (url.contains("googlevideo.com") || url.contains("youtube.com")) {
                    val isMobileStream = url.contains("c=VISIONOS") ||
                        url.contains("c=IOS") ||
                        url.contains("c=ANDROID_VR") ||
                        YoutubeParsingHelper.isVisionOsStreamingUrl(url) ||
                        YoutubeParsingHelper.isAndroidStreamingUrl(url)

                    val ua = when {
                        url.contains("c=VISIONOS") -> YouTubeClient.VISIONOS.userAgent
                        url.contains("c=IOS") -> YouTubeClient.IOS.userAgent
                        url.contains("c=ANDROID_VR") -> YouTubeClient.ANDROID_VR.userAgent
                        YoutubeParsingHelper.isVisionOsStreamingUrl(url) ->
                            YoutubeParsingHelper.getVisionOsUserAgent(null)
                        YoutubeParsingHelper.isAndroidStreamingUrl(url) ->
                            YoutubeParsingHelper.getAndroidUserAgent(null)
                        else -> CLIP_BROWSER_UA
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
                chain.proceed(builder.build())
            }
            .build()
        DefaultMediaSourceFactory(DefaultDataSource.Factory(context, OkHttpDataSource.Factory(okHttpClient)))
    }

    val player = remember(clip) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 50_000, 1_000, 3_000)
                    .build()
            )
            .build()
            .apply {
                setAudioAttributes(AudioAttributes.DEFAULT, true)
                playWhenReady = true
            }
    }

    val mediaSource = remember(clipStreams, quality, fallbackStage, mediaSourceFactory) {
        buildClipMediaSource(mediaSourceFactory, clipStreams, quality, stage = fallbackStage)
    }

    DisposableEffect(player, clipStreams, fallbackStage) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    Log.d("ArtistClipsSection", "Playing clip at ${videoSize.width}x${videoSize.height} (stage=$fallbackStage, quality=$quality)")
                    val pixelRatio = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1f
                    videoAspectRatio = (videoSize.width.toFloat() * pixelRatio) / videoSize.height.toFloat()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPaused = !isPlaying && player.playbackState == Player.STATE_READY
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                    if (pendingSyncMs > 0L) {
                        val target = player.duration.takeIf { it > 0 }
                            ?.let { pendingSyncMs.coerceAtMost(it - 500L) } ?: pendingSyncMs
                        player.seekTo(target.coerceAtLeast(0L))
                        pendingSyncMs = 0L
                    }
                    if (player.videoSize.width > 0 && player.videoSize.height > 0) {
                        val vs = player.videoSize
                        val pixelRatio = if (vs.pixelWidthHeightRatio > 0f) vs.pixelWidthHeightRatio else 1f
                        videoAspectRatio = (vs.width.toFloat() * pixelRatio) / vs.height.toFloat()
                    }
                }
                if (playbackState == Player.STATE_ENDED) {
                    hasEnded = true
                    isPaused = true
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("ArtistClipsSection", "Clip playback error (stage=$fallbackStage) for ${clip.url}", error)
                val streams = clipStreams
                when {
                    fallbackStage == 0 && (streams?.hasBackupAdaptive == true || streams?.videoOnly?.any { it.height <= 1080 } == true) -> {
                        Log.w("ArtistClipsSection", "Retrying with backup / <=1080p adaptive streams (stage=1)")
                        fallbackStage = 1
                    }
                    fallbackStage < 2 && streams?.muxedUrl != null -> {
                        Log.w("ArtistClipsSection", "Falling back to muxed stream (stage=2)")
                        fallbackStage = 2
                    }
                    else -> failed = true
                }
            }
        }
        player.addListener(listener)
        if (player.videoSize.width > 0 && player.videoSize.height > 0) {
            val vs = player.videoSize
            val pixelRatio = if (vs.pixelWidthHeightRatio > 0f) vs.pixelWidthHeightRatio else 1f
            videoAspectRatio = (vs.width.toFloat() * pixelRatio) / vs.height.toFloat()
        }
        onDispose {
            player.removeListener(listener)
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    LaunchedEffect(mediaSource) {
        mediaSource?.let { source ->
            failed = false
            player.setMediaSource(source)
            player.prepare()
            player.play()
        }
    }

    LaunchedEffect(player, isPaused, hasEnded) {
        while (true) {
            if (!isPaused && !hasEnded) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                if (playerViewModel.uiCurrentTrack != null) {
                    playerViewModel.currentPosition = positionMs
                }
            }
            delay(250)
        }
    }

    val duration = durationMs.coerceAtLeast(1L)
    var draggedPosition by remember { mutableStateOf<Float?>(null) }
    val sliderStyle = remember { prefs.getPlayerSliderStyle() }

    val onTogglePause = {
        if (hasEnded) {
            player.seekTo(0)
            player.play()
            hasEnded = false
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }
    val onSeek: (Long) -> Unit = { targetMs ->
        player.seekTo(targetMs)
        positionMs = targetMs
        if (hasEnded) {
            hasEnded = false
            player.play()
        }
    }
    val onSeekBy: (Long) -> Unit = { deltaMs ->
        val limit = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        onSeek((player.currentPosition + deltaMs).coerceIn(0L, limit))
    }

    BackHandler(enabled = true) {
        if (isFullscreen) isFullscreen = false else onDismiss()
    }

    Dialog(
        onDismissRequest = {
            if (isFullscreen) isFullscreen = false else onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = !isFullscreen,
        )
    ) {
        val view = LocalView.current

        SideEffect {
            val dialogWindow = findDialogWindow(view)
            if (dialogWindow != null) {
                if (isFullscreen) {
                    dialogWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    dialogWindow.setDimAmount(0f)
                } else {
                    dialogWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    dialogWindow.setDimAmount(0.6f)
                }
            }
        }

        DisposableEffect(isFullscreen, view) {
            val dialogWindow = findDialogWindow(view)
            if (isFullscreen) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                dialogWindow?.let { win ->
                    WindowCompat.setDecorFitsSystemWindows(win, false)
                    val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                }
                activity?.window?.let { actWin ->
                    val insetsController = WindowCompat.getInsetsController(actWin, actWin.decorView)
                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                }
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                dialogWindow?.let { win ->
                    val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
                activity?.window?.let { actWin ->
                    val insetsController = WindowCompat.getInsetsController(actWin, actWin.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }

            onDispose {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                activity?.window?.let { actWin ->
                    val insetsController = WindowCompat.getInsetsController(actWin, actWin.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        Surface(
            shape = if (isFullscreen) RectangleShape else RoundedCornerShape(28.dp),
            color = if (isFullscreen) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = if (isFullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 640.dp)
                    .padding(12.dp)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isFullscreen) Modifier.fillMaxHeight() else Modifier)
                    .padding(if (isFullscreen) 0.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (isFullscreen) 0.dp else 12.dp)
            ) {
                if (!isFullscreen) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = clip.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (clip.uploader.isNotBlank()) {
                                Text(
                                    text = clip.uploader,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.btn_close)
                            )
                        }
                    }
                }

                Box(
                    modifier = (if (isFullscreen) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)))
                        .background(Color.Black)
                        .pointerInput(clip, isFullscreen, failed) {
                            detectTapGestures(
                                onDoubleTap = { isFullscreen = !isFullscreen },
                                onTap = { if (!failed) controlsVisible = !controlsVisible },
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.aspectRatio(videoAspectRatio),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                TextureView(ctx).apply {
                                    player.setVideoTextureView(this)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (clipStreams == null && !failed) {
                            AsyncImage(
                                model = clip.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    if (failed) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.artist_clip_failed),
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            FilledTonalButton(
                                onClick = hapticAction(HapticKind.UI) {
                                    failed = false
                                    fallbackStage = 0
                                    clipStreams = null
                                    scope.launch {
                                        clipStreams = ArtistClips.streams(clip)
                                        if (clipStreams?.hasAnything != true) failed = true
                                    }
                                },
                                shapes = ButtonDefaults.shapes()
                            ) {
                                Text(stringResource(R.string.btn_retry))
                            }
                        }
                    }

                    if (isFullscreen) {
                        IconButton(
                            onClick = hapticAction(HapticKind.UI) { isFullscreen = false },
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.55f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .safeDrawingPadding()
                                .padding(16.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.btn_close)
                            )
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = controlsVisible && !failed,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.matchParentSize(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f))
                                .pointerInput(clip, isFullscreen) {
                                    detectTapGestures(
                                        onDoubleTap = { isFullscreen = !isFullscreen },
                                        onTap = { controlsVisible = false },
                                    )
                                },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(22.dp),
                                modifier = Modifier.align(Alignment.Center),
                            ) {
                                IconButton(
                                    onClick = hapticAction(HapticKind.UI) { onSeekBy(-5_000L) },
                                    enabled = durationMs > 0,
                                    shapes = IconButtonDefaults.shapes(),
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Replay5,
                                        contentDescription = stringResource(R.string.clip_seek_back),
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp),
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.55f),
                                ) {
                                    IconButton(
                                        onClick = hapticAction(HapticKind.UI) { onTogglePause() },
                                        enabled = clipStreams != null && !failed,
                                        shapes = IconButtonDefaults.shapes(),
                                        modifier = Modifier.size(72.dp),
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                hasEnded -> Icons.Rounded.Replay
                                                isPaused -> Icons.Rounded.PlayArrow
                                                else -> Icons.Rounded.Pause
                                            },
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(40.dp),
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = hapticAction(HapticKind.UI) { onSeekBy(5_000L) },
                                    enabled = durationMs > 0,
                                    shapes = IconButtonDefaults.shapes(),
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Forward5,
                                        contentDescription = stringResource(R.string.clip_seek_forward),
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp),
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                    .safeDrawingPadding(),
                            ) {
                                Text(
                                    text = formatClock(draggedPosition?.let { (it * duration).toLong() } ?: positionMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                                PlayerSlider(
                                    value = draggedPosition ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f),
                                    onValueChange = { draggedPosition = it },
                                    onValueChangeFinished = {
                                        draggedPosition?.let { onSeek((it * duration).toLong()) }
                                        draggedPosition = null
                                    },
                                    sliderStyle = sliderStyle,
                                    isPlaying = !isPaused && !hasEnded,
                                    enabled = durationMs > 0,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = formatClock(durationMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                                if (!isFullscreen) {
                                    IconButton(
                                        onClick = hapticAction(HapticKind.UI) { isFullscreen = !isFullscreen },
                                        shapes = IconButtonDefaults.shapes(),
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.Fullscreen,
                                            contentDescription = stringResource(R.string.clip_fullscreen),
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val ArtistClips.ClipStreams.hasBackupAdaptive: Boolean
    get() = backupVideoOnly.any { (it.audioUrl ?: backupAudioUrl ?: audioUrl) != null }

private val ArtistClips.ClipStreams.hasAnything: Boolean
    get() = muxedUrl != null ||
        videoOnly.any { (it.audioUrl ?: audioUrl) != null } ||
        hasBackupAdaptive

private fun buildClipMediaSource(
    factory: DefaultMediaSourceFactory,
    streams: ArtistClips.ClipStreams?,
    quality: ClipVideoQuality,
    stage: Int = 0,
): MediaSource? {
    if (streams == null) return null

    if (stage >= 2) {
        return streams.muxedUrl?.let { factory.createMediaSource(MediaItem.fromUri(it)) }
    }

    val requestedHeight = quality.height ?: ArtistClips.MAX_HEIGHT

    if (stage == 1) {
        // Step down cleanly to <=1080p adaptive before dropping to 360p muxed
        val cappedHeight = minOf(requestedHeight, 1080)
        val backupRendition = streams.backupVideoOnly
            .filter { it.height <= cappedHeight }
            .maxByOrNull { it.height }
            ?: streams.backupVideoOnly.minByOrNull { it.height }
        mergeVideoWithAudio(factory, backupRendition, streams.backupAudioUrl ?: streams.audioUrl)?.let { return it }

        val primary1080 = streams.videoOnly
            .filter { it.height <= cappedHeight }
            .maxByOrNull { it.height }
        mergeVideoWithAudio(factory, primary1080, streams.audioUrl)?.let { return it }

        return streams.muxedUrl?.let { factory.createMediaSource(MediaItem.fromUri(it)) }
    }

    val rendition = streams.videoOnly
        .filter { it.height <= requestedHeight }
        .maxByOrNull { it.height }
        ?: streams.videoOnly.minByOrNull { it.height }

    mergeVideoWithAudio(factory, rendition, streams.audioUrl)?.let { return it }

    if (streams.hasBackupAdaptive) {
        val backupRendition = streams.backupVideoOnly
            .filter { it.height <= requestedHeight }
            .maxByOrNull { it.height }
            ?: streams.backupVideoOnly.minByOrNull { it.height }
        mergeVideoWithAudio(factory, backupRendition, streams.backupAudioUrl ?: streams.audioUrl)?.let { return it }
    }

    return streams.muxedUrl?.let { factory.createMediaSource(MediaItem.fromUri(it)) }
}

private fun mergeVideoWithAudio(
    factory: DefaultMediaSourceFactory,
    rendition: ArtistClips.VideoRendition?,
    fallbackAudioUrl: String?,
): MediaSource? {
    val effectiveAudio = rendition?.audioUrl ?: fallbackAudioUrl
    if (rendition == null || effectiveAudio == null) return null
    return MergingMediaSource(
        factory.createMediaSource(MediaItem.fromUri(rendition.url)),
        factory.createMediaSource(MediaItem.fromUri(effectiveAudio)),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun findDialogWindow(view: View): Window? {
    var parent: ViewParent? = view.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) {
            return parent.window
        }
        parent = parent.parent
    }
    return (view.parent as? DialogWindowProvider)?.window
}

private val CLIP_CARD_WIDTH = 260.dp

private fun formatClock(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val hours = totalSec / 3600
    val minutes = (totalSec / 60) % 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

private fun formatCompactNumber(count: Long): String {
    return when {
        count >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> String.format(Locale.getDefault(), "%,d", count)
    }
}
