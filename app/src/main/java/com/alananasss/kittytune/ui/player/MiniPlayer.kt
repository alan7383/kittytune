package com.alananasss.kittytune.ui.player

import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
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
import kotlinx.coroutines.flow.collectLatest

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
    modifier: Modifier = Modifier,
    progress: Animatable<Float, AnimationVector1D>? = null,
    showSkip: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
) {
    val track = viewModel.uiCurrentTrack ?: return
    val context = LocalContext.current

    // Smooth playback progress. The docks pass their own already-animated value (rememberDockProgress
    // glides it for the whole bar) — this composable only reads it. Standalone usages without a
    // caller-side value animate internally with the same snap-on-seek / glide behaviour.
    val internalProgress = remember { Animatable(0f) }
    var lastTrackKey by remember { mutableStateOf<Any?>(null) }
    val trackKey: Any = track.id

    LaunchedEffect(trackKey, progress) {
        val isTrackChange = lastTrackKey != null && lastTrackKey != trackKey
        lastTrackKey = trackKey
        if (isTrackChange && progress == null) internalProgress.snapTo(0f)
        // Shared dock progress animates itself; re-animating it here would fight the bar's own
        // collector (and the animated value would keep re-triggering this observer).
        if (progress != null) return@LaunchedEffect

        snapshotFlow {
            viewModel.progressOf(viewModel.uiCurrentPosition.toFloat())
        }.collectLatest { tp ->
            val delta = tp - internalProgress.value
            when {
                delta < -0.01f || delta > 0.05f -> internalProgress.animateTo(
                    tp, tween(150, easing = FastOutSlowInEasing)
                )
                else -> internalProgress.animateTo(tp, tween(1000, easing = LinearEasing))
            }
        }
    }

    // Dominant artwork colour for the progress fill over the standard surface. Extracted off the
    // main thread, same as the modern pill does for its gradient / glow backgrounds.
    val needsArtworkColor = progressFill && !coverAsBackground
    var artworkColors by remember(track.id, needsArtworkColor) { mutableStateOf<List<Color>>(emptyList()) }
    LaunchedEffect(track.id, needsArtworkColor) {
        if (!needsArtworkColor) {
            artworkColors = emptyList()
            return@LaunchedEffect
        }
        val url = track.fullResArtwork ?: track.thumbnailUrl
        if (url.isNullOrBlank()) return@LaunchedEffect
        artworkColors = try {
            val result = Coil.imageLoader(context).execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(96, 96)
                    .allowHardware(false)
                    .build()
            )
            (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { bmp ->
                ArtworkPalette.meshPalette(bmp, count = 2).map { Color(it) }
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    val isLightTheme = MaterialTheme.colorScheme.surface.luminanceCompat() > 0.5f
    val artworkTint = when (val dominant = artworkColors.firstOrNull()) {
        null -> if (isLightTheme) Color(0xFFC7C7CE) else Color(0xFF9A9AA3)
        else -> readableFillTint(dominant, isLightTheme)
    }

    val bg = MaterialTheme.colorScheme.surfaceContainer
    val onBg = MaterialTheme.colorScheme.onSurface
    val onBgVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val overArtwork = coverAsBackground
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
            AsyncImage(
                model = track.fullResArtwork ?: track.thumbnailUrl,
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
                            0f to Color.Black.copy(alpha = 0.55f),
                            0.6f to Color.Black.copy(alpha = 0.42f),
                            1f to Color.Black.copy(alpha = 0.30f),
                        )
                    )
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(bg))
        }

        // ── Progress FILL: the song time painted as a sweeping fill instead of a slider ──
        if (progressFill) {
            BoxWithConstraints(modifier = Modifier.matchParentSize()) {
                val fraction = (progress?.value ?: internalProgress.value).coerceIn(0f, 1f)
                if (fraction > 0.001f) {
                    // Over the standard surface the fill carries the artwork colour (white-grey
                    // fallback); over the artwork background a translucent sweep keeps it visible.
                    val fillTint = if (overArtwork) {
                        if (isLightTheme) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.30f)
                    } else {
                        artworkTint
                    }
                    Box(
                        modifier = Modifier
                            .width(maxWidth * fraction)
                            .fillMaxHeight()
                            .background(fillTint)
                    )
                }
            }
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
                PremiumMarqueeText(
                    text = track.title ?: stringResource(R.string.untitled_track),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = titleColor,
                    edgeGradientWidth = 10.dp,
                    delayMillis = 3000,
                    velocity = 25.dp,
                    modifier = Modifier.weight(1f, fill = false)
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
                    PremiumMarqueeText(
                        text = track.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
                        style = MaterialTheme.typography.bodySmall,
                        color = artistColor,
                        edgeGradientWidth = 8.dp,
                        delayMillis = 3000,
                        velocity = 25.dp
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
            val fraction = (progress?.value ?: internalProgress.value).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
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

/** Icon tint for the play/pause buttons stays readable in both themes. */
private fun Color.luminanceCompat(): Float {
    val argb = toArgb()
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

/**
 * Clamps the artwork's dominant colour so the strip text stays readable:
 * dark enough for white text on a dark theme, light enough for dark text on a light theme.
 */
private fun readableFillTint(color: Color, lightTheme: Boolean): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(color.toArgb(), hsv)
    hsv[1] = hsv[1].coerceAtLeast(0.22f)
    hsv[2] = if (lightTheme) hsv[2].coerceIn(0.62f, 0.88f) else hsv[2].coerceIn(0.20f, 0.52f)
    return Color(AndroidColor.HSVToColor(hsv))
}
