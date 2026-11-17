package com.alananasss.kittytune.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.cover.AnimatedArtwork
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mobile sleep / screensaver focus screen: a clock, what is playing and what has played since it came on,
 * on pure black for OLED power savings.
 *
 * The background is true black and nothing on it is pure white. The content slowly drifts along a
 * Lissajous loop every [DRIFT_STEP_MS] to prevent OLED burn-in.
 *
 * Tap anywhere or back press wakes it.
 */
@Composable
fun AnimatedVisibilityScope.SleepScreen(
    viewModel: PlayerViewModel,
    accent: Color,
    onWake: () -> Unit,
) {
    val track = viewModel.uiCurrentTrack
    LaunchedEffect(viewModel.uiCurrentTrack) {
        if (viewModel.uiCurrentTrack == null) {
            onWake()
        }
    }
    if (track == null) return
    val shownAt = remember { System.currentTimeMillis() }
    var hasWoken by remember { mutableStateOf(false) }
    val wake = {
        if (!hasWoken && System.currentTimeMillis() - shownAt > WAKE_GRACE_MS) {
            hasWoken = true
            onWake()
        }
    }

    BackHandler { wake() }

    // Wall-clock second; the clock and the numbers refresh once a second.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000L - System.currentTimeMillis() % 1_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val drift = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        var step = 0
        while (true) {
            kotlinx.coroutines.delay(DRIFT_STEP_MS)
            step++
            drift.animateTo(step.toFloat(), tween(DRIFT_GLIDE_MS, easing = FastOutSlowInEasing))
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { wake() }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val screenWidth = maxWidth
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 620.dp)
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .graphicsLayer {
                    val t = drift.value
                    translationX = DRIFT_RADIUS_X.toPx() * sin(t * 0.37f)
                    translationY = DRIFT_RADIUS_Y.toPx() * cos(t * 0.23f)
                }
                .animateEnterExit(
                    enter = fadeIn(tween(600, easing = LinearOutSlowInEasing)) +
                        scaleIn(tween(600, easing = LinearOutSlowInEasing), initialScale = 0.96f),
                    exit = fadeOut(tween(200, easing = FastOutLinearInEasing)) +
                        scaleOut(tween(200, easing = FastOutLinearInEasing), targetScale = 1.02f),
                ),
        ) {
            Clock(nowMs, screenWidth)
            Spacer(Modifier.height(36.dp))
            NowPlaying(viewModel, track, accent)
            Spacer(Modifier.height(32.dp))
            SleepStats(viewModel, nowMs)
        }

        WakeHint(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
        )
    }
}

@Composable
private fun Clock(nowMs: Long, screenWidth: androidx.compose.ui.unit.Dp) {
    val now = remember(nowMs / 60_000L) { LocalDateTime.now() }
    val locale = Locale.getDefault()
    val dateFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE, d MMMM", locale) }
    val clockFontSize = if (screenWidth < 360.dp) 72.sp else if (screenWidth < 600.dp) 88.sp else 120.sp
    val clockLetterSpacing = if (screenWidth < 360.dp) (-2).sp else (-4).sp

    Text(
        text = String.format(locale, "%02d:%02d", now.hour, now.minute),
        style = MaterialTheme.typography.displayLarge.copy(fontSize = clockFontSize, letterSpacing = clockLetterSpacing),
        fontWeight = FontWeight.Thin,
        color = SOFT_WHITE,
        textAlign = TextAlign.Center,
    )
    Text(
        text = remember(now.dayOfYear) { now.format(dateFormatter).replaceFirstChar { it.titlecase(locale) } },
        style = MaterialTheme.typography.titleMedium,
        color = SOFT_WHITE.copy(alpha = 0.5f),
        textAlign = TextAlign.Center,
    )
}

/** Cover, title, artist, and hairline progress bar. */
@Composable
private fun NowPlaying(
    viewModel: PlayerViewModel,
    track: Track,
    accent: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 460.dp)) {
        AnimatedContent(
            targetState = track,
            contentKey = { it.id },
            transitionSpec = { fadeIn(tween(600)).togetherWith(fadeOut(tween(400))) },
            label = "screensaverTrackInfo",
        ) { currentTrack ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedArtwork(
                    artworkUrl = currentTrack.fullResArtwork,
                    animatedCoverUrl = null,
                    isPlaying = viewModel.uiIsPlaying,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .alpha(0.85f),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Text(
                        text = currentTrack.title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SOFT_WHITE,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = currentTrack.displayArtist.ifBlank { currentTrack.user?.username.orEmpty() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = SOFT_WHITE.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .width(220.dp)
                .height(2.dp)
                .drawBehind {
                    val fraction = viewModel.progressOf(viewModel.uiCurrentPosition.toFloat())
                    val y = size.height / 2f
                    drawLine(SOFT_WHITE.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), size.height, StrokeCap.Round)
                    drawLine(accent.copy(alpha = 0.7f), Offset(0f, y), Offset(size.width * fraction, y), size.height, StrokeCap.Round)
                },
        )
    }
}

/** Time listened and tracks played since the sleep screen came on. */
@Composable
private fun SleepStats(viewModel: PlayerViewModel, nowMs: Long) {
    val listenedAtStart = remember { viewModel.sessionTotalListenMs }
    val playsAtStart = remember { viewModel.effectiveSessionPlays }
    val listenedMs = remember(nowMs) { (viewModel.sessionTotalListenMs - listenedAtStart).coerceAtLeast(0L) }
    val plays = (viewModel.effectiveSessionPlays - playsAtStart).coerceAtLeast(0)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.screensaver_since_sleep),
            style = MaterialTheme.typography.labelMedium,
            color = SOFT_WHITE.copy(alpha = 0.35f),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            StatItem(Icons.Rounded.Schedule, formatListened(listenedMs), stringResource(R.string.listening_stats_time_listened))
            StatItem(Icons.Rounded.MusicNote, "$plays", stringResource(R.string.listening_stats_unique_tracks))
        }
    }
}

@Composable
private fun StatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = SOFT_WHITE.copy(alpha = 0.45f), modifier = Modifier.size(18.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = SOFT_WHITE.copy(alpha = 0.8f))
            Text(label, style = MaterialTheme.typography.labelSmall, color = SOFT_WHITE.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun formatListened(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> stringResource(R.string.listening_stats_duration_hr_min, hours, minutes)
        minutes > 0 -> stringResource(R.string.listening_stats_duration_min, minutes)
        else -> stringResource(R.string.listening_stats_duration_sec, totalSeconds)
    }
}

/** Fade-out wake hint */
@Composable
private fun WakeHint(modifier: Modifier) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(WAKE_HINT_MS)
        visible = false
    }
    AnimatedVisibility(visible, modifier = modifier, enter = fadeIn(), exit = fadeOut(tween(1200))) {
        Text(
            text = stringResource(R.string.screensaver_tap_to_wake),
            style = MaterialTheme.typography.labelMedium,
            color = SOFT_WHITE.copy(alpha = 0.35f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val SOFT_WHITE = Color(0xFFE6E6E6)
private const val WAKE_GRACE_MS = 400L
private const val WAKE_HINT_MS = 6_000L
private const val DRIFT_STEP_MS = 40_000L
private const val DRIFT_GLIDE_MS = 4_000
private val DRIFT_RADIUS_X = 24.dp
private val DRIFT_RADIUS_Y = 16.dp
