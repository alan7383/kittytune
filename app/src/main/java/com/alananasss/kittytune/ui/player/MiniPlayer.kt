package com.alananasss.kittytune.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.Coil
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.theme.ArtworkPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The redesigned classic mini player strip.
 *
 * Three INDEPENDENT toggles (Settings → Mini player) the user mixes freely:
 *  - [showCover]          — a small artwork sits on the left of the strip; off, the title takes
 *    the full width.
 *  - [coverAsBackground]  — the artwork stretches over the whole strip behind the title, white
 *    text over a dark scrim.
 *  - [progressFill]       — the song time is a fill sweeping across the strip instead of the
 *    familiar 2 dp line along the bottom edge.
 *
 * Tapping anywhere on the strip opens the full player. Gestures (swipe-to-dismiss / track change)
 * and the dismiss animation live at the call sites — this composable only paints the strip.
 */
@Composable
fun ClassicMiniPlayerContent(
    viewModel: PlayerViewModel,
    showCover: Boolean,
    coverAsBackground: Boolean,
    progressFill: Boolean,
    onClick: () -> Unit,
    progress: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier,
    showSkip: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
) {
    val track = viewModel.uiCurrentTrack ?: return
    val context = LocalContext.current

    val artwork = track.fullResArtwork?.takeIf { it.isNotBlank() } ?: track.thumbnailUrl
    val overArtwork = coverAsBackground && artwork.isNotBlank()
    val needsArtworkColor = progressFill && !overArtwork
    var artworkColor by remember(artwork, needsArtworkColor) { mutableStateOf<Color?>(null) }
    LaunchedEffect(artwork, needsArtworkColor) {
        if (!needsArtworkColor || artwork.isBlank()) return@LaunchedEffect
        artworkColor = try {
            val result = Coil.imageLoader(context).execute(
                ImageRequest.Builder(context).data(artwork).size(96, 96).allowHardware(false).build()
            )
            (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { bitmap ->
                withContext(Dispatchers.Default) {
                    ArtworkPalette.meshPalette(bitmap, count = 2).firstOrNull()?.let { Color(it) }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    val scheme = MaterialTheme.colorScheme
    val bg = scheme.surfaceContainer
    val fillTint = if (overArtwork) Color.White.copy(alpha = 0.20f)
        else lerp(bg, artworkColor ?: scheme.primary, 0.32f)
    val onBg = MaterialTheme.colorScheme.onSurface
    val onBgVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val titleColor = if (overArtwork) Color.White else onBg
    val artistColor = if (overArtwork) Color.White.copy(alpha = 0.78f) else onBgVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clipToBounds()
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        // ── Background layers ──
        if (overArtwork) {
            Box(Modifier.fillMaxSize().background(Color(0xFF17171B)))
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Scrim keeps the white text readable over any artwork.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.72f),
                            0.6f to Color.Black.copy(alpha = 0.64f),
                            1f to Color.Black.copy(alpha = 0.62f),
                        )
                    )
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(bg))
        }

        if (progressFill) {
            Box(
                Modifier.matchParentSize().drawBehind {
                    drawRect(fillTint, size = Size(size.width * progress.value.coerceIn(0f, 1f), size.height))
                }
            )
        }

        // ── Content ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            if (showCover) {
                Box(modifier = Modifier.size(48.dp)) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (overArtwork) Color.White.copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text(
                    text = track.title ?: stringResource(R.string.untitled_track),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val deviceLabel = viewModel.playbackDeviceLabel
                if (deviceLabel != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { viewModel.showConnectSheet = true }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Devices,
                                contentDescription = null,
                                tint = if (overArtwork) Color.White else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = deviceLabel,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (overArtwork) Color.White else MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = artistColor
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = track.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
                            style = MaterialTheme.typography.bodySmall,
                            color = artistColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = track.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
                        style = MaterialTheme.typography.bodySmall,
                        color = artistColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(onClick = { viewModel.togglePlayPause() }) {
                Icon(
                    imageVector = if (viewModel.uiIsPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (viewModel.uiIsPlaying) R.string.btn_pause else R.string.btn_play),
                    tint = titleColor
                )
            }

            if (showSkip) {
                IconButton(onClick = { viewModel.requestSkipNext() }) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.menu_play_next),
                        tint = titleColor
                    )
                }
            }
        }

        // ── Bottom edge progress line: only when the fill is switched off ──
        if (!progressFill) {
            LinearProgressIndicator(
                progress = { progress.value.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomCenter),
                color = if (overArtwork) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary,
                trackColor = if (overArtwork) Color.White.copy(alpha = 0.22f) else Color.Transparent
            )
        }
    }
}
