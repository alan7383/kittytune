package com.alananasss.kittytune.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.wave.WaveMode
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlin.math.PI
import kotlin.math.sin

/**
 * My Wave's card, the first thing on the home page (issue #66).
 *
 * The desktop app's card, redrawn for the phone: one press plays music picked for this listener,
 * without choosing anything; the card's waves move slowly while it is idle and swell while the
 * wave plays. Its four modes say what kind of mix it is, and switching one while the wave plays
 * changes what comes next, not the song on now.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MyWaveCard(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val isActive = vm.isMyWaveActive
    val isPlayingWave = isActive && vm.isPlaying
    val energy by animateFloatAsState(if (isPlayingWave) 1f else 0.35f, tween(900, easing = FastOutSlowInEasing), label = "waveEnergy")

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().height(WAVE_CARD_HEIGHT),
    ) {
        Box(Modifier.fillMaxSize()) {
            WaveBackground(
                energy = energy,
                colors = listOf(scheme.primary, scheme.tertiary, scheme.secondary),
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.wave_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.wave_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    WavePlayButton(vm, isPlayingWave)
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isActive) NowInWave(vm)
                    WaveModeRow(selected = vm.waveMode, onSelect = { vm.startMyWave(it) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WavePlayButton(vm: PlayerViewModel, isPlayingWave: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        FilledIconButton(
            onClick = {
                when {
                    vm.isWaveLoading -> Unit
                    vm.isMyWaveActive -> vm.togglePlayPause()
                    else -> vm.startMyWave()
                }
            },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(PLAY_SIZE),
        ) {
            AnimatedContent(
                targetState = when {
                    vm.isWaveLoading -> 0
                    isPlayingWave -> 1
                    else -> 2
                },
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "wavePlayIcon",
            ) { state ->
                when (state) {
                    0 -> androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                    )
                    1 -> Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(32.dp))
                    else -> Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.wave_title),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

/** The song the wave is on, small, so the card shows the wave is playing and what. */
@Composable
private fun NowInWave(vm: PlayerViewModel) {
    val track = vm.currentTrack ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.wave_now_playing),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                listOfNotNull(track.title, track.displayArtist.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WaveModeRow(selected: WaveMode, onSelect: (WaveMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WaveMode.entries.forEach { mode ->
            WaveModeChip(mode, isSelected = mode == selected, onClick = { onSelect(mode) })
        }
    }
}

@Composable
private fun WaveModeChip(mode: WaveMode, isSelected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (isSelected) scheme.secondaryContainer else scheme.surfaceContainerHighest.copy(alpha = 0.7f),
        label = "waveModeContainer",
    )
    val content by animateColorAsState(
        if (isSelected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        label = "waveModeContent",
    )
    val (icon, label) = waveModeVisual(mode)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun waveModeVisual(mode: WaveMode): Pair<ImageVector, String> = when (mode) {
    WaveMode.BALANCED -> Icons.Rounded.AutoAwesome to stringResource(R.string.wave_mode_balanced)
    WaveMode.FAVORITES -> Icons.Rounded.Favorite to stringResource(R.string.wave_mode_favorites)
    WaveMode.DISCOVER -> Icons.Rounded.Explore to stringResource(R.string.wave_mode_discover)
    WaveMode.POPULAR -> Icons.Rounded.TrendingUp to stringResource(R.string.wave_mode_popular)
}

/**
 * Three soft waves in the theme's colours, drifting across the card. [energy] raises them and speeds nothing up:
 * the motion is the same, the waves are taller while music plays.
 */
@Composable
private fun WaveBackground(energy: Float, colors: List<Color>, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "waveDrift")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(WAVE_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase",
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        colors.forEachIndexed { i, color ->
            val baseline = h * (0.62f + i * 0.1f)
            val amplitude = h * (0.06f + 0.05f * i) * (0.4f + energy)
            val frequency = 1.2f + i * 0.45f
            val shift = waveShift(phase, i)
            fun crestY(x: Float) = baseline + amplitude * sin((x / w) * frequency * 2 * PI.toFloat() + shift)
            // The crest alone gets the bright line; outlining the filled shape also drew its sides, two white
            // bars down the card's edges.
            val crest = Path().apply {
                moveTo(0f, crestY(0f))
                var x = 6f
                while (x < w) {
                    lineTo(x, crestY(x))
                    x += 6f
                }
                lineTo(w, crestY(w))
            }
            val body = Path().apply {
                addPath(crest)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = body,
                brush = Brush.verticalGradient(
                    0f to color.copy(alpha = 0.22f + 0.1f * energy),
                    1f to color.copy(alpha = 0.04f),
                    startY = baseline - amplitude,
                    endY = h,
                ),
            )
            drawPath(path = crest, color = color.copy(alpha = 0.25f + 0.25f * energy), style = Stroke(width = 2f))
        }
        // A glow behind the play button. It is placed from the card's edge, where the button is, not from a share of the
        // width: at a wide window the share put it well to the left of the button.
        val glow = Offset(w - 20.dp.toPx() - PLAY_SIZE.toPx() / 2, 18.dp.toPx() + PLAY_SIZE.toPx() / 2)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(colors.first().copy(alpha = 0.18f * (0.5f + energy)), Color.Transparent),
                center = glow,
                radius = h * 0.8f,
            ),
            radius = h * 0.8f,
            center = glow,
        )
    }
}

/**
 * How far wave [index] has drifted at [phase], which runs from 0 to a full turn and starts over.
 *
 * Whole turns of the phase only: a wave that makes a fraction of a turn in that time jumps at the start of each
 * round. Different whole numbers, one of them backwards, keep the waves drifting at their own speeds and the
 * loop seamless.
 */
internal fun waveShift(phase: Float, index: Int): Float = phase * WAVE_TURNS[index % WAVE_TURNS.size] + index * 1.7f

private val WAVE_TURNS = intArrayOf(1, -1, 2)

private val WAVE_CARD_HEIGHT = 210.dp
private val PLAY_SIZE = 64.dp
private const val WAVE_PERIOD_MS = 12_000
