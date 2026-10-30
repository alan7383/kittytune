    package com.alananasss.kittytune.ui.player

    import androidx.compose.animation.core.Animatable
    import androidx.compose.animation.core.FastOutSlowInEasing
    import androidx.compose.animation.core.LinearEasing
    import androidx.compose.animation.core.tween
    import androidx.compose.foundation.background
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.rounded.Devices
    import androidx.compose.material.icons.rounded.Pause
    import androidx.compose.material.icons.rounded.PlayArrow
    import androidx.compose.material.icons.rounded.SkipNext
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.draw.clipToBounds
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.layout.ContentScale
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextOverflow
    import androidx.compose.ui.unit.dp
    import coil.compose.AsyncImage
    import kotlinx.coroutines.flow.collectLatest
    import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.hapticClickable

    @Composable
    fun MiniPlayer(
        viewModel: PlayerViewModel,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val track = viewModel.uiCurrentTrack ?: return

        val animatedProgress = remember { Animatable(0f) }
        var lastTrackKey by remember { mutableStateOf<Any?>(null) }
        val trackKey: Any = track.id

        LaunchedEffect(trackKey) {
            val isTrackChange = lastTrackKey != null && lastTrackKey != trackKey
            lastTrackKey = trackKey

            if (isTrackChange) {
                animatedProgress.snapTo(0f)
            }

            androidx.compose.runtime.snapshotFlow { 
                viewModel.progressOf(viewModel.uiCurrentPosition.toFloat())
            }.collectLatest { tp ->
                val delta = tp - animatedProgress.value
                when {
                    delta < -0.01f || delta > 0.05f -> {
                        animatedProgress.animateTo(
                            targetValue = tp,
                            animationSpec = tween(
                                durationMillis = 150,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                    else -> {
                        animatedProgress.animateTo(
                            targetValue = tp,
                            animationSpec = tween(
                                durationMillis = 1000,
                                easing = LinearEasing
                            )
                        )
                    }
                }
            }
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(64.dp)
                .clipToBounds()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .hapticClickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                Box(modifier = Modifier.size(48.dp)) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    com.alananasss.kittytune.ui.common.TrackSourceCoverBadge(
                        track = track,
                        resolvedSource = viewModel.currentStreamSource,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PremiumMarqueeText(
                            text = track.title ?: stringResource(R.string.untitled_track),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            edgeGradientWidth = 10.dp,
                            delayMillis = 3000,
                            velocity = 25.dp,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    val deviceLabel = viewModel.playbackDeviceLabel
                    if (deviceLabel != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.hapticClickable { viewModel.showConnectSheet = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Devices,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = deviceLabel,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = track.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        PremiumMarqueeText(
                            text = track.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = { viewModel.requestSkipNext() }) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.menu_play_next),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            LinearProgressIndicator(
                progress = { animatedProgress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        }
    }

