package com.alananasss.kittytune.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.wave.MyWave
import com.alananasss.kittytune.ui.player.PlayerViewModel

/**
 * MyWave on the home screen (ported from desktop, PR #77): a tall card of moving water with a round play
 * button at its centre, the four modes beneath, and what plays now when the wave is on.
 */
@Composable
fun MyWaveCard(playerViewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val isPlaying = playerViewModel.isPlaying
    val isLoading = playerViewModel.isWaveLoading
    val nowPlaying = playerViewModel.currentTrack
    val isActive = playerViewModel.isMyWaveActive

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            WaveBackground(
                playing = isPlaying && isActive,
                colors = listOf(scheme.primary, scheme.tertiary, scheme.secondary),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.wave_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            stringResource(R.string.wave_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Box {
                        FilledIconButton(
                            onClick = { playerViewModel.startMyWave(playerViewModel.waveMode) },
                            modifier = Modifier.size(64.dp),
                        ) {
                            if (isLoading) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    color = scheme.onPrimary,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(28.dp),
                                )
                            } else {
                                Icon(
                                    if (isPlaying && isActive) Icons.Rounded.Pause else Icons.Rounded.AutoAwesome,
                                    stringResource(R.string.wave_title),
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }
                }
                if (isActive && nowPlaying != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .padding(bottom = 18.dp),
                    ) {
                        AsyncImage(
                            model = nowPlaying.artworkUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                nowPlaying.title.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                nowPlaying.displayArtist,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            WaveModeRow(
                selected = playerViewModel.waveMode,
                onSelect = { playerViewModel.startMyWave(it) },
            )
        }
    }
}

@Composable
private fun WaveModeRow(selected: MyWave.WaveMode, onSelect: (MyWave.WaveMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        WaveModeChip(stringResource(R.string.wave_mode_balanced), Icons.Rounded.AutoAwesome, selected == MyWave.WaveMode.BALANCED, {
            onSelect(MyWave.WaveMode.BALANCED)
        }, Modifier.weight(1f))
        WaveModeChip(stringResource(R.string.wave_mode_favorites), Icons.Rounded.Favorite, selected == MyWave.WaveMode.FAVORITES, {
            onSelect(MyWave.WaveMode.FAVORITES)
        }, Modifier.weight(1f))
        WaveModeChip(stringResource(R.string.wave_mode_discover), Icons.Rounded.Explore, selected == MyWave.WaveMode.DISCOVER, {
            onSelect(MyWave.WaveMode.DISCOVER)
        }, Modifier.weight(1f))
        WaveModeChip(stringResource(R.string.wave_mode_popular), Icons.Rounded.TrendingUp, selected == MyWave.WaveMode.POPULAR, {
            onSelect(MyWave.WaveMode.POPULAR)
        }, Modifier.weight(1f))
    }
}

@Composable
private fun WaveModeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainer,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Three waves in the theme's colors, breathing while the wave plays: crests stroked, bodies fading into the
 * card, the whole loop seamless because each wave travels a whole number of turns.
 */
@Composable
private fun WaveBackground(
    playing: Boolean,
    colors: List<Color>,
    content: @Composable () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12_000), RepeatMode.Restart),
        label = "phase",
    )
    val energy by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (playing) 1f else 0.35f,
        animationSpec = tween(900),
        label = "energy",
    )
    val turns = remember { listOf(1f, -1f, 2f) }

    Box {
        Canvas(Modifier.fillMaxWidth().height(132.dp)) {
            val w = size.width
            val h = size.height
            colors.forEachIndexed { i, color ->
                val baseline = h * (0.62f + 0.10f * i)
                val amplitude = h * (0.06f + 0.05f * i) * (0.4f + energy)
                val frequency = 1.2f + 0.45f * i
                val shift = phase * turns[i] + i * 1.7f
                val path = Path()
                var first = true
                    for (x in 0..w.toInt() step 8) {
                    val y = baseline + amplitude * kotlin.math.sin((x / w) * frequency * 2f * Math.PI.toFloat() + shift * 2f * Math.PI.toFloat())
                    if (first) {
                        path.moveTo(x.toFloat(), y)
                        first = false
                    } else {
                        path.lineTo(x.toFloat(), y)
                    }
                }
                drawPath(
                    path,
                    brush = Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f)),
                        startY = baseline - amplitude,
                        endY = h,
                    ),
                )
                drawPath(path, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        content()
    }
}
