package com.alananasss.kittytune.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.hapticClickable
import com.alananasss.kittytune.data.wave.MyWave
import com.alananasss.kittytune.ui.player.PlayerViewModel

/**
 * MyWave on the home screen (ported from desktop, PR #77): an endless personal radio that learns from
 * likes and skips, offering four tailored modes (Balanced, Favorites, Discover, Popular) and smooth
 * playback control.
 */
@Composable
fun MyWaveCard(playerViewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val isPlaying = playerViewModel.isPlaying
    val isLoading = playerViewModel.isWaveLoading
    val nowPlaying = playerViewModel.currentTrack
    val isActive = playerViewModel.isMyWaveActive

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header Row: Title, Subtitle, and Main Action Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = scheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.wave_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.wave_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(16.dp))

                FilledIconButton(
                    onClick = {
                        when {
                            isLoading -> Unit
                            isActive -> playerViewModel.togglePlayPause()
                            else -> playerViewModel.startMyWave(playerViewModel.waveMode)
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                    ),
                    modifier = Modifier.size(52.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = scheme.onPrimary,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying && isActive) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.wave_title),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            // Active Track Display
            AnimatedVisibility(
                visible = isActive && nowPlaying != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                if (nowPlaying != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = scheme.surfaceContainerHighest.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            AsyncImage(
                                model = nowPlaying.artworkUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(scheme.surfaceVariant),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.wave_now_playing),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = nowPlaying.title.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = nowPlaying.displayArtist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // Mode Selection Chips
            WaveModeRow(
                selected = playerViewModel.waveMode,
                onSelect = { playerViewModel.startMyWave(it) },
            )
        }
    }
}

@Composable
private fun WaveModeRow(
    selected: MyWave.WaveMode,
    onSelect: (MyWave.WaveMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WaveModeChip(
            label = stringResource(R.string.wave_mode_balanced),
            icon = Icons.Rounded.AutoAwesome,
            selected = selected == MyWave.WaveMode.BALANCED,
            onClick = hapticAction(HapticKind.UI) { onSelect(MyWave.WaveMode.BALANCED) },
        )
        WaveModeChip(
            label = stringResource(R.string.wave_mode_favorites),
            icon = Icons.Rounded.Favorite,
            selected = selected == MyWave.WaveMode.FAVORITES,
            onClick = hapticAction(HapticKind.UI) { onSelect(MyWave.WaveMode.FAVORITES) },
        )
        WaveModeChip(
            label = stringResource(R.string.wave_mode_discover),
            icon = Icons.Rounded.Explore,
            selected = selected == MyWave.WaveMode.DISCOVER,
            onClick = hapticAction(HapticKind.UI) { onSelect(MyWave.WaveMode.DISCOVER) },
        )
        WaveModeChip(
            label = stringResource(R.string.wave_mode_popular),
            icon = Icons.Rounded.TrendingUp,
            selected = selected == MyWave.WaveMode.POPULAR,
            onClick = hapticAction(HapticKind.UI) { onSelect(MyWave.WaveMode.POPULAR) },
        )
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
    val container by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else scheme.surfaceContainerHighest.copy(alpha = 0.7f),
        label = "waveModeContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        label = "waveModeContent",
    )
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
