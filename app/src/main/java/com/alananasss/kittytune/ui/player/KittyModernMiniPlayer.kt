package com.alananasss.kittytune.ui.player

import kotlinx.coroutines.flow.collectLatest

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import com.alananasss.kittytune.data.local.MiniPlayerBackgroundStyle
import com.alananasss.kittytune.data.local.MiniPlayerSwipeAction
import com.alananasss.kittytune.data.local.MiniPlayerVerticalGesture
import com.alananasss.kittytune.data.local.MiniPlayerWidth
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.player.pixel.miniPlayerDismissHorizontalGesture
import com.alananasss.kittytune.ui.player.pixel.rememberMiniPlayerDismissGestureHandler
import com.alananasss.kittytune.ui.common.rememberWindowSizeInfo
import com.alananasss.kittytune.ui.theme.ArtworkPalette
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

// A modern mini player in the reference-player style, adapted to KittyTune:
//  - a compact pill the height of the modern navigation dock;
//  - circular artwork with a playback progress ring around it;
//  - previous / play-pause / next in a symmetric cluster, play/pause centred
//    between the two skip buttons (both hideable via a setting);
//  - the wavy "cookie" play/pause button;
//  - background styles: solid, gradient, blur, animated glow, live mesh;
//  - width: the usual full-width pill or the narrower centered mini-style pill;
//  - gestures: an optional horizontal track swipe and a swipe-down-to-close.
@Composable
fun KittyModernMiniPlayer(
    viewModel: PlayerViewModel,
    onPlayerClick: () -> Unit,
    modifier: Modifier = Modifier,
    heightDp: Dp = Dp.Unspecified,
    horizontalPadding: Dp = 12.dp,
) {
    val track = viewModel.uiCurrentTrack ?: return
    val context = LocalContext.current
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val playerPrefs = remember { PlayerPreferences(context) }

    // TABLET: the pill is taller (72dp instead of 56dp) so the mini player does
    // not look too thin on a large screen - the artwork and buttons grow
    // with the height. Phones are unaffected: still 56dp there, as before.
    // An explicitly passed height (settings previews etc.) takes priority.
    val windowInfo = rememberWindowSizeInfo()
    val resolvedHeightDp = if (heightDp == Dp.Unspecified) {
        if (windowInfo.isTablet) 72.dp else 56.dp
    } else {
        heightDp
    }

    val swipeAction by playerPrefs.miniPlayerSwipeActionFlow()
        .collectAsState(initial = playerPrefs.getMiniPlayerSwipeAction())
    val swipeTrackEnabled by playerPrefs.miniPlayerSwipeTrackFlow()
        .collectAsState(initial = playerPrefs.getMiniPlayerSwipeTrack())
    val backgroundStyle by playerPrefs.miniPlayerBackgroundStyleFlow()
        .collectAsState(initial = playerPrefs.getMiniPlayerBackgroundStyle())
    val wavyButton by playerPrefs.wavyPlayPauseFlow()
        .collectAsState(initial = playerPrefs.getWavyPlayPauseButton())
    val verticalGesture by playerPrefs.miniPlayerVerticalGestureFlow()
        .collectAsState(initial = playerPrefs.getMiniPlayerVerticalGesture())
    val showTrackButtons by playerPrefs.miniPlayerShowTrackButtonsFlow()
        .collectAsState(initial = playerPrefs.getMiniPlayerShowTrackButtons())
    val widthMode by playerPrefs.miniPlayerWidthFlow()
        .collectAsState(initial = playerPrefs.getMiniPlayerWidth())

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthPx = remember(configuration, density) {
        with(density) { configuration.screenWidthDp.dp.toPx() }
    }
    val offsetAnimatable = remember { Animatable(0f) }
    val offsetYAnimatable = remember { Animatable(0f) }

    // Modern pill: the horizontal swipe only ever switches tracks, and only while the
    // dedicated toggle is on (closing lives on the swipe-down gesture).
    val miniDismissGestureHandler = rememberMiniPlayerDismissGestureHandler(
        scope = coroutineScope,
        density = density,
        hapticFeedback = hapticFeedback,
        offsetAnimatable = offsetAnimatable,
        screenWidthPx = screenWidthPx,
        swipeAction = MiniPlayerSwipeAction.CHANGE_TRACK,
        onDismiss = { viewModel.dismissMiniPlayerAndShowUndo() },
        onDismissStarted = { viewModel.isMiniPlayerDismissing = true },
        onSwipeNext = { viewModel.requestSkipNext() },
        onSwipePrevious = { viewModel.smartPrevious() }
    )

    // Palette for the gradient / glow background styles, extracted off the main thread.
    var paletteColors by remember(track.id) { mutableStateOf<List<Color>>(emptyList()) }
    LaunchedEffect(track.id, backgroundStyle) {
        if (backgroundStyle == MiniPlayerBackgroundStyle.SOLID) return@LaunchedEffect
        val url = track.fullResArtwork ?: track.thumbnailUrl
        if (url.isNullOrBlank()) return@LaunchedEffect
        paletteColors = try {
            val result = coil.Coil.imageLoader(context).execute(
                ImageRequest.Builder(context).data(url).size(96, 96).allowHardware(false).build()
            )
            (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { bmp ->
                ArtworkPalette.meshPalette(bmp, count = 3).map { Color(it) }
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Vertical swipe -> close.
    val closeThresholdPx = with(density) { 96.dp.toPx() }

    val pill: @Composable (Modifier) -> Unit = { pillModifier ->
        Box(
            modifier = pillModifier
                .graphicsLayer {
                    translationX = offsetAnimatable.value
                    translationY = offsetYAnimatable.value
                    val dismissFade = (1f - (abs(offsetAnimatable.value) / screenWidthPx) * 0.35f).coerceIn(0.65f, 1f)
                    val verticalFade = (1f - (offsetYAnimatable.value / closeThresholdPx) * 0.6f).coerceIn(0.4f, 1f)
                    alpha = dismissFade * verticalFade
                }
                .clip(RoundedCornerShape(percent = 50))
                .miniPlayerDismissHorizontalGesture(enabled = swipeTrackEnabled, handler = miniDismissGestureHandler)
                .then(
                    if (verticalGesture == MiniPlayerVerticalGesture.CLOSE) {
                        Modifier.pointerInput(closeThresholdPx) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    coroutineScope.launch {
                                        offsetYAnimatable.snapTo((offsetYAnimatable.value + dragAmount).coerceAtLeast(0f))
                                    }
                                },
                                onDragEnd = {
                                    if (offsetYAnimatable.value > closeThresholdPx * 0.6f) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.isMiniPlayerDismissing = true
                                        coroutineScope.launch {
                                            offsetYAnimatable.animateTo(
                                                targetValue = closeThresholdPx * 3f,
                                                animationSpec = tween(180)
                                            )
                                            viewModel.dismissMiniPlayerAndShowUndo()
                                            offsetYAnimatable.snapTo(0f)
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            offsetYAnimatable.animateTo(
                                                0f,
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable { onPlayerClick() }
        ) {
            MiniPlayerBackgroundLayer(
                style = backgroundStyle,
                artworkUrl = track.thumbnailUrl,
                paletteColors = paletteColors,
                isPlaying = viewModel.uiIsPlaying,
                fallbackColor = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProgressRingThumbnail(
                    viewModel = viewModel,
                    modifier = Modifier.size((resolvedHeightDp - 16.dp))
                )

                Spacer(Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = track.title ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val deviceLabel = viewModel.playbackDeviceLabel
                        if (deviceLabel != null) {
                            Icon(
                                imageVector = Icons.Rounded.Devices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = deviceLabel,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = track.displayArtist.ifBlank { "" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (showTrackButtons) {
                    // Symmetric cluster: equal gaps either side of the play button keep it
                    // centred exactly between the previous and the next skip buttons.
                    Spacer(Modifier.width(10.dp))

                    IconButton(
                        onClick = { viewModel.smartPrevious() },
                        modifier = Modifier.size((resolvedHeightDp - 12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    MiniWavyPlayPauseButton(
                        isPlaying = viewModel.uiIsPlaying,
                        wavyEnabled = wavyButton,
                        onToggle = { viewModel.togglePlayPause() },
                        modifier = Modifier.size((resolvedHeightDp - 16.dp))
                    )

                    Spacer(Modifier.width(4.dp))

                    IconButton(
                        onClick = { viewModel.requestSkipNext() },
                        modifier = Modifier.size((resolvedHeightDp - 12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Spacer(Modifier.width(6.dp))

                    MiniWavyPlayPauseButton(
                        isPlaying = viewModel.uiIsPlaying,
                        wavyEnabled = wavyButton,
                        onToggle = { viewModel.togglePlayPause() },
                        modifier = Modifier.size((resolvedHeightDp - 16.dp))
                    )
                }
            }
        }
    }

    if (widthMode == MiniPlayerWidth.NARROW) {
        // The mini-style compact pill: capped width, centred, floating clear of both
        // screen edges. On tablets the pill is taller (72dp), so the width cap is
        // proportionally larger (440dp ≈ 340dp × 72/56) - the "narrow" pill
        // looks the same as it always did and the track title is not cramped.
        // On phones everything stays as it was: 340dp at 56dp height.
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            pill(
                Modifier
                    .padding(horizontal = horizontalPadding)
                    .widthIn(max = if (windowInfo.isTablet) 440.dp else 340.dp)
                    .height(resolvedHeightDp)
            )
        }
    } else {
        pill(
            modifier
                .padding(horizontal = horizontalPadding)
                .height(resolvedHeightDp)
        )
    }
}

// Circular artwork with the playback progress drawn as a ring around it (mini style).
@Composable
private fun ProgressRingThumbnail(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val track = viewModel.uiCurrentTrack
    val progress = remember { Animatable(0f) }
    LaunchedEffect(viewModel) {
        androidx.compose.runtime.snapshotFlow {
            viewModel.progressOf(viewModel.uiCurrentPosition.toFloat())
        }.collectLatest { target ->
            val delta = target - progress.value
            val spec: androidx.compose.animation.core.TweenSpec<Float> =
                if (abs(delta) > 0.05f) tween(150) else tween(1000, easing = LinearEasing)
            progress.animateTo(target, animationSpec = spec)
        }
    }

    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .drawBehind {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2 + 0.5f
                drawCircle(
                    color = trackColor,
                    radius = (size.minDimension / 2f) - inset,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.value,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - inset * 2f,
                        size.height - inset * 2f
                    ),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            .padding(5.dp)
    ) {
        coil.compose.AsyncImage(
            model = track?.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )
    }
}

// Background layers for the modern mini player.
//
// The animated glow now starts from a base colour wash across the WHOLE pill and only then
// adds two large, softly drifting radial accents - the old version drew two small-radius
// circles that faded out mid-pill and read as floating blobs. The live mesh scales its
// rotating artwork copies 3x (1.5x used to open bare, unmeshed corners on the wide pill)
// and mirrors a second copy counter-rotating for a richer weave.
@Composable
private fun MiniPlayerBackgroundLayer(
    style: MiniPlayerBackgroundStyle,
    artworkUrl: String?,
    paletteColors: List<Color>,
    isPlaying: Boolean,
    fallbackColor: Color
) {
    when (style) {
        MiniPlayerBackgroundStyle.SOLID -> Unit

        MiniPlayerBackgroundStyle.GRADIENT -> {
            if (paletteColors.isNotEmpty()) {
                val base = paletteColors.first()
                val second = paletteColors.getOrElse(1) { base }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    base.copy(alpha = 0.55f),
                                    second.copy(alpha = 0.35f)
                                )
                            )
                        )
                )
            }
        }

        MiniPlayerBackgroundStyle.BLUR -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Box(Modifier.fillMaxSize()) {
                    coil.compose.AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(30.dp)
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    )
                }
            } else if (paletteColors.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    paletteColors.first().copy(alpha = 0.55f),
                                    paletteColors.last().copy(alpha = 0.35f)
                                )
                            )
                        )
                )
            }
        }

        MiniPlayerBackgroundStyle.GLOW -> {
            // The "animated glow" verbatim: a near-black base wash plus two large radial
            // accents whose colours rotate through the palette and whose centres drift on
            // sine paths (Lissajous-like), over a slow 20 s loop. Before the artwork palette
            // arrives (or when it yields nothing) the theme's primary colour stands in.
            val colors = if (paletteColors.isNotEmpty()) {
                paletteColors
            } else {
                listOf(fallbackColor, fallbackColor.copy(alpha = 0.6f))
            }
            val transition = rememberInfiniteTransition(label = "miniGlow")
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 20_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "miniGlowProgress"
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val p = progress
                        val width = size.width
                        val height = size.height

                        fun rotatedColorAt(index: Int): Color {
                            val count = colors.size
                            val idx = index.toFloat() + p * count
                            val a = kotlin.math.floor(idx).toInt() % count
                            val b = (a + 1) % count
                            val frac = idx - kotlin.math.floor(idx)
                            return lerp(colors[a], colors[b], frac)
                        }

                        fun oscillate(min: Float, max: Float, phase: Float): Float {
                            val v = kotlin.math.sin(2f * Math.PI.toFloat() * (p + phase))
                            return min + (max - min) * ((v + 1f) * 0.5f)
                        }

                        val c1 = rotatedColorAt(0)
                        val c2 = rotatedColorAt(1)

                        val o1x = oscillate(0.0f, 1.0f, 0.0f)
                        val o1y = oscillate(0.0f, 0.5f, 0.1f)
                        val o2x = oscillate(1.0f, 0.0f, 0.2f)
                        val o2y = oscillate(0.5f, 1.0f, 0.3f)

                        val b1 = Brush.radialGradient(
                            colors = listOf(c1.copy(alpha = 0.8f), Color.Transparent),
                            center = Offset(width * o1x, height * o1y),
                            radius = width * 1.2f
                        )
                        val b2 = Brush.radialGradient(
                            colors = listOf(c2.copy(alpha = 0.7f), Color.Transparent),
                            center = Offset(width * o2x, height * o2y),
                            radius = width * 1.0f
                        )

                        drawRect(Color(0xFF050505))
                        drawRect(b1)
                        drawRect(b2)
                    }
            )
        }

        MiniPlayerBackgroundStyle.LIVE_MESH -> {
            // The "live mesh" verbatim: ONE artwork copy, saturation boost, 40 dp blur,
            // 60 s rotation, container scaled 1.5x, over a light black scrim.
            val transition = rememberInfiniteTransition(label = "miniMesh")
            val rotation by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 60_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "miniMeshRotation"
            )
            val saturation = remember {
                ColorMatrix().apply { setToSaturation(1.6f) }
            }
            val colorFilter = remember(saturation) { ColorFilter.colorMatrix(saturation) }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.5f
                        scaleY = 1.5f
                    }
            ) {
                coil.compose.AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(40.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
            }
        }
    }
}
