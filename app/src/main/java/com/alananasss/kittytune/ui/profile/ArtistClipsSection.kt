package com.alananasss.kittytune.ui.profile

import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.alananasss.kittytune.data.network.ProxyManager
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.artist.ArtistClips
import com.alananasss.kittytune.data.artist.Clip
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * An artist's music videos, watched right here in a dialog styled after KittyTuneDesktop:
 * tap one to watch, pausing the background music, with easy dismiss, fullscreen, and video controls.
 */
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
            itemsIndexed(clips, key = { _, c -> c.url }) { _, clip ->
                ClipCard(clip, onClick = { watching = clip })
            }
        }
    }

    watching?.let { clip ->
        ClipPlayerDialog(clip = clip, playerViewModel = playerViewModel, onDismiss = { watching = null })
    }
}

@Composable
private fun ClipCard(clip: Clip, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(CLIP_CARD_WIDTH)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
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

/**
 * A clip playing in a dedicated dialog matching KittyTuneDesktop:
 * music pauses during playback and resumes cleanly upon dismiss; title, uploader and close button
 * in the header; fullscreen toggle; audio scrubber and volume toggle.
 */
@Composable
private fun ClipPlayerDialog(clip: Clip, playerViewModel: PlayerViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var streamUrl by remember(clip) { mutableStateOf<String?>(null) }
    var failed by remember(clip) { mutableStateOf(false) }
    var isFullscreen by remember(clip) { mutableStateOf(false) }
    var isPaused by remember(clip) { mutableStateOf(false) }
    var hasEnded by remember(clip) { mutableStateOf(false) }
    var muted by remember(clip) { mutableStateOf(false) }
    var positionMs by remember(clip) { mutableLongStateOf(0L) }
    var durationMs by remember(clip) { mutableLongStateOf(0L) }

    LaunchedEffect(clip) {
        streamUrl = ArtistClips.streamUrl(clip)
        if (streamUrl == null) failed = true
    }

    // Pause current music and stop together listening while watching a clip; resume when leaving
    var wasPlaying by remember(clip) { mutableStateOf(false) }
    DisposableEffect(clip) {
        wasPlaying = playerViewModel.isPlaying
        Together.stopListening()
        playerViewModel.pause()
        onDispose {
            if (wasPlaying && !playerViewModel.isPlaying) playerViewModel.togglePlayPause()
        }
    }

    // Configure ExoPlayer with OkHttpDataSource (supports HTTP/2 and modern TLS) and proper YouTube client headers to prevent HTTP 403
    val player = remember(clip) {
        val okHttpClient = ProxyManager.getOkHttpClient().newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                val builder = req.newBuilder()
                if (url.contains("googlevideo.com") || url.contains("youtube.com")) {
                    val ua = if (YoutubeParsingHelper.isAndroidStreamingUrl(url)) {
                        YoutubeParsingHelper.getAndroidUserAgent(null)
                    } else if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
                        YoutubeParsingHelper.getVisionOsUserAgent(null)
                    } else {
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    }
                    builder.header("User-Agent", ua)
                    builder.header("Accept", "*/*")
                    builder.header("Origin", "https://www.youtube.com")
                    builder.header("Referer", "https://www.youtube.com/")
                }
                chain.proceed(builder.build())
            }
            .build()
        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

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

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPaused = !isPlaying && player.playbackState == Player.STATE_READY
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                }
                if (playbackState == Player.STATE_ENDED) {
                    hasEnded = true
                    isPaused = true
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                failed = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(streamUrl) {
        streamUrl?.let { url ->
            failed = false
            player.setMediaItem(MediaItem.Builder().setUri(url).build())
            player.prepare()
            player.play()
        }
    }

    // Progress updates
    LaunchedEffect(player, isPaused) {
        while (true) {
            if (!isPaused) positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(250)
        }
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
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        )
    ) {
        Surface(
            shape = if (isFullscreen) RectangleShape else RoundedCornerShape(28.dp),
            color = if (isFullscreen) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = (if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth(0.94f))
                .padding(if (isFullscreen) 0.dp else 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(if (isFullscreen) 0.dp else 16.dp),
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
                        IconButton(onClick = onDismiss) {
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
                        .pointerInput(clip, isFullscreen) {
                            detectTapGestures(
                                onDoubleTap = { isFullscreen = !isFullscreen },
                                onTap = {
                                    if (!failed) {
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
                                }
                            )
                        },
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

                    if (streamUrl == null && !failed) {
                        AsyncImage(
                            model = clip.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
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
                                onClick = {
                                    failed = false
                                    streamUrl = null
                                    scope.launch {
                                        streamUrl = ArtistClips.streamUrl(clip)
                                        if (streamUrl == null) failed = true
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.btn_retry))
                            }
                        }
                    }

                    if (isFullscreen) {
                        IconButton(
                            onClick = { isFullscreen = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.btn_close),
                                tint = Color.White
                            )
                        }
                    }
                }

                Box(if (isFullscreen) Modifier.padding(horizontal = 16.dp, vertical = 12.dp) else Modifier) {
                    ClipControls(
                        isPaused = isPaused,
                        hasEnded = hasEnded,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        muted = muted,
                        isFullscreen = isFullscreen,
                        isLoading = streamUrl == null && !failed,
                        onTogglePause = {
                            if (hasEnded) {
                                player.seekTo(0)
                                player.play()
                                hasEnded = false
                            } else if (player.isPlaying) {
                                player.pause()
                            } else {
                                player.play()
                            }
                        },
                        onSeek = { targetMs ->
                            player.seekTo(targetMs)
                            positionMs = targetMs
                        },
                        onToggleMute = {
                            muted = !muted
                            player.volume = if (muted) 0f else 1f
                        },
                        onToggleFullscreen = {
                            isFullscreen = !isFullscreen
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipControls(
    isPaused: Boolean,
    hasEnded: Boolean,
    positionMs: Long,
    durationMs: Long,
    muted: Boolean,
    isFullscreen: Boolean,
    isLoading: Boolean,
    onTogglePause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleMute: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    val duration = durationMs.coerceAtLeast(1L)
    var draggedPosition by remember { mutableStateOf<Float?>(null) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        FilledIconButton(
            onClick = onTogglePause,
            enabled = !isLoading,
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = when {
                    hasEnded -> Icons.Rounded.Replay
                    isPaused -> Icons.Rounded.PlayArrow
                    else -> Icons.Rounded.Pause
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = formatClock(draggedPosition?.let { (it * duration).toLong() } ?: positionMs),
            style = MaterialTheme.typography.labelSmall,
            color = if (isFullscreen) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = draggedPosition ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { draggedPosition = it },
            onValueChangeFinished = {
                draggedPosition?.let { onSeek((it * duration).toLong()) }
                draggedPosition = null
            },
            enabled = durationMs > 0,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatClock(durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = if (isFullscreen) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(
            onClick = onToggleMute,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = null,
                tint = if (isFullscreen) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(
            onClick = onToggleFullscreen,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                contentDescription = null,
                tint = if (isFullscreen) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private const val CLIP_CARD_WIDTH_DP = 240
private val CLIP_CARD_WIDTH = CLIP_CARD_WIDTH_DP.dp

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
