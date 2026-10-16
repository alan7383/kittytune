package com.alananasss.kittytune.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.view.TextureView
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.artist.ArtistClips
import com.alananasss.kittytune.data.artist.Clip
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.ui.player.PlayerViewModel
import java.util.Locale
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer

/**
 * An artist's music videos, as a row of cards on their page (ported from desktop): tap one and it plays in a
 * dialog over the page, pausing the music — watching something else means leaving a shared playlist, too.
 */
@Composable
fun ArtistClipsSection(artistName: String, playerViewModel: PlayerViewModel) {
    val clips by produceState(initialValue = emptyList<Clip>(), artistName) {
        value = ArtistClips.clipsFor(artistName)
    }
    if (clips.isEmpty()) return
    var playing by remember { mutableStateOf<Clip?>(null) }

    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.artist_clips_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(4.dp))
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(clips, key = { _, c -> c.url }) { _, clip ->
                ClipCard(clip, onClick = { playing = clip })
            }
        }
    }

    playing?.let { clip ->
        ClipPlayerDialog(clip = clip, playerViewModel = playerViewModel, onDismiss = { playing = null })
    }
}

@Composable
private fun ClipCard(clip: Clip, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = clip.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .size(44.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Text(
                formatClock(clip.durationSec * 1000),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            clip.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            String.format(Locale.getDefault(), "%,d ${stringResource(R.string.artist_clip_views)}", clip.viewCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One clip playing over the app: its picture and sound in a dialog, the music paused under it and
 * resumed after, at the volume the music was at. Tap pauses, double-tap fills the screen.
 */
@Composable
private fun ClipPlayerDialog(clip: Clip, playerViewModel: PlayerViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
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

    // Watching something else means leaving a shared playlist, and pausing the music under it.
    var wasPlaying by remember(clip) { mutableStateOf(false) }
    DisposableEffect(clip) {
        wasPlaying = playerViewModel.isPlaying
        Together.stopListening()
        playerViewModel.pause()
        onDispose {
            if (wasPlaying) playerViewModel.togglePlayPause()
        }
    }

    val player = remember(clip) {
        ExoPlayer.Builder(context)
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
                if (playbackState == Player.STATE_READY) durationMs = player.duration.coerceAtLeast(0L)
                if (playbackState == Player.STATE_ENDED) {
                    hasEnded = true
                    isPaused = true
                }
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
            player.setMediaItem(MediaItem.Builder().setUri(url).build())
            player.prepare()
            player.play()
        }
    }

    // The position the slider and the clock show.
    LaunchedEffect(player, isPaused) {
        while (true) {
            if (!isPaused) positionMs = player.currentPosition.coerceAtLeast(0L)
            kotlinx.coroutines.delay(250)
        }
    }

    BackHandler(enabled = true) {
        if (isFullscreen) isFullscreen = false else onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (isFullscreen) 1f else 0.75f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = if (isFullscreen) 0.dp else 20.dp)
                .clip(RoundedCornerShape(if (isFullscreen) 0.dp else 24.dp))
                .background(Color.Black)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .pointerInput(clip) {
                        detectTapGestures(
                            onTap = {
                                if (!failed) {
                                    if (hasEnded) {
                                        player.seekTo(0)
                                        player.play()
                                        hasEnded = false
                                    } else if (player.isPlaying) player.pause() else player.play()
                                }
                            },
                            onDoubleTap = { isFullscreen = !isFullscreen },
                        )
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply { player.setVideoTextureView(this) }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (streamUrl == null && !failed) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.Center)
                    )
                }
                if (failed) {
                    Text(
                        stringResource(R.string.artist_clip_failed),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            if (!isFullscreen) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = {
                            if (hasEnded) {
                                player.seekTo(0)
                                player.play()
                                hasEnded = false
                            } else if (player.isPlaying) player.pause() else player.play()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            if (hasEnded) Icons.Rounded.Replay else Icons.Rounded.PlayArrow,
                            null,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${formatClock(positionMs)} / ${formatClock(durationMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { muted = !muted; player.volume = if (muted) 0f else 1f }) {
                        Icon(
                            if (muted) Icons.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                            null,
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    IconButton(onClick = { isFullscreen = true }) {
                        Icon(Icons.Rounded.Fullscreen, null, tint = Color.White.copy(alpha = 0.85f))
                    }
                }
                Slider(
                    value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                    onValueChange = { fraction ->
                        if (durationMs > 0) {
                            player.seekTo((fraction * durationMs).toLong())
                            positionMs = (fraction * durationMs).toLong()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 10.dp)
                )
            }
        }
    }
}

private fun formatClock(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(Locale.getDefault(), "%d:%02d", m, s)
}
