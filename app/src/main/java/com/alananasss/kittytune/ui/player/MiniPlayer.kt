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
    import androidx.compose.material.icons.rounded.Pause
    import androidx.compose.material.icons.rounded.PlayArrow
    import androidx.compose.material.icons.rounded.SkipNext
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.layout.ContentScale
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.unit.dp
    import coil.compose.AsyncImage
    import kotlinx.coroutines.flow.collectLatest
    import com.alananasss.kittytune.R

    @Composable
    fun MiniPlayer(
        viewModel: PlayerViewModel,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val track = viewModel.currentTrack ?: return

        val animatedProgress = remember { Animatable(0f) }
        var lastTrackKey by remember { mutableStateOf<Any?>(null) }
        val trackKey: Any = track.id

        LaunchedEffect(trackKey) {
            val isTrackChange = lastTrackKey != null && lastTrackKey != trackKey
            lastTrackKey = trackKey

            if (isTrackChange) {
                animatedProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 280,
                        easing = FastOutSlowInEasing
                    )
                )
            }

            androidx.compose.runtime.snapshotFlow { 
                if (viewModel.duration > 0) {
                    viewModel.currentPosition.toFloat() / viewModel.duration.toFloat()
                } else 0f 
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
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                AsyncImage(
                    model = track.fullResArtwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    PremiumMarqueeText(
                        text = track.title ?: stringResource(R.string.untitled_track),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        edgeGradientWidth = 10.dp,
                        delayMillis = 3000,
                        velocity = 25.dp
                    )
                    PremiumMarqueeText(
                        text = track.user?.username ?: stringResource(R.string.unknown_artist),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        edgeGradientWidth = 8.dp,
                        delayMillis = 3000,
                        velocity = 25.dp
                    )
                }

                IconButton(onClick = { viewModel.togglePlayPause() }) {
                    Icon(
                        imageVector = if (viewModel.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (viewModel.isPlaying) R.string.btn_pause else R.string.btn_play),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = { viewModel.playNext() }) {
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

