package com.alananasss.kittytune.ui.navigation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.pixel.miniPlayerDismissHorizontalGesture
import com.alananasss.kittytune.ui.player.pixel.rememberMiniPlayerDismissGestureHandler
import kotlin.math.abs

data class KittyTab(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val visible: Boolean = true
)

@Composable
fun KittyUnifiedBottomBar(
    tabs: List<KittyTab>,
    selectedRoute: String?,
    onTabSelected: (KittyTab) -> Unit,
    onFabClick: () -> Unit,
    fabIcon: ImageVector,
    playerViewModel: PlayerViewModel,
    onPlayerClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: String = "modern",
    blurEnabled: Boolean = true
) {
    val track = playerViewModel.currentTrack
    val isPlaying = playerViewModel.isPlaying

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val screenWidthPx = remember(configuration, density) {
        with(density) { configuration.screenWidthDp.dp.toPx() }
    }
    val offsetAnimatable = remember { Animatable(0f) }

    val miniDismissGestureHandler = rememberMiniPlayerDismissGestureHandler(
        scope = coroutineScope,
        density = density,
        hapticFeedback = hapticFeedback,
        offsetAnimatable = offsetAnimatable,
        screenWidthPx = screenWidthPx,
        onDismiss = {
            playerViewModel.dismissMiniPlayerAndShowUndo()
        },
        onDismissStarted = {
            playerViewModel.isMiniPlayerDismissing = true
        }
    )

    if (style == "classic") {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .semantics { }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {})
                }
        ) {
            if (track != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clipToBounds()
                        .systemGestureExclusion()
                        .graphicsLayer {
                            translationX = offsetAnimatable.value
                            alpha = (1f - (abs(offsetAnimatable.value) / (screenWidthPx * 0.85f))).coerceIn(0f, 1f)
                        }
                        .miniPlayerDismissHorizontalGesture(
                            enabled = true,
                            handler = miniDismissGestureHandler
                        )
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .clickable { onPlayerClick() }
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = track.thumbnailUrl,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )

                        Spacer(Modifier.width(12.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = track.title ?: stringResource(R.string.untitled_track),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = { playerViewModel.togglePlayPause() }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(if (isPlaying) R.string.btn_pause else R.string.btn_play),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(onClick = { playerViewModel.requestSkipNext() }) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    val progress = rememberDockProgress(playerViewModel)

                    LinearProgressIndicator(
                        progress = { progress.value },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }
            NavigationBar(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                tabs.filter { it.visible }.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedRoute == tab.route,
                        onClick = { onTabSelected(tab) },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    } else {
        // MODERN STYLE
        Column(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .semantics { }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {})
                }
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (track != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clipToBounds()
                        .systemGestureExclusion()
                        .graphicsLayer {
                            translationX = offsetAnimatable.value
                            alpha = (1f - (abs(offsetAnimatable.value) / (screenWidthPx * 0.85f))).coerceIn(0f, 1f)
                        }
                        .miniPlayerDismissHorizontalGesture(
                            enabled = true,
                            handler = miniDismissGestureHandler
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { onPlayerClick() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = track.thumbnailUrl,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                            Text(
                                text = track.title ?: stringResource(R.string.untitled_track),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = { playerViewModel.togglePlayPause() }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(if (isPlaying) R.string.btn_pause else R.string.btn_play),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(onClick = { playerViewModel.requestSkipNext() }) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    val progress = rememberDockProgress(playerViewModel)

                    LinearProgressIndicator(
                        progress = { progress.value },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                contentAlignment = Alignment.Center
            ) {
                val isCompact = maxWidth < 375.dp
                val tabHorizontalPadding = if (isCompact) 8.dp else 11.dp
                val selectedTabHorizontalPadding = if (isCompact) 12.dp else 14.dp
                val maxLabelWidth = if (isCompact) 80.dp else 110.dp

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 2.dp,
                        shadowElevation = 3.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .height(56.dp)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            tabs.forEach { tab ->
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = tab.visible,
                                    enter = androidx.compose.animation.expandHorizontally(
                                        expandFrom = Alignment.CenterHorizontally,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    ) + androidx.compose.animation.fadeIn(),
                                    exit = androidx.compose.animation.shrinkHorizontally(
                                        shrinkTowards = Alignment.CenterHorizontally,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    ) + androidx.compose.animation.fadeOut()
                                ) {
                                    val isSelected = selectedRoute == tab.route

                                    val shape = CircleShape
                                    val containerColor by animateColorAsState(
                                        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                        label = "containerColor"
                                    )
                                    val contentColor by animateColorAsState(
                                        targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        label = "contentColor"
                                    )

                                    Row(
                                        modifier = Modifier
                                            .clip(shape)
                                            .background(color = containerColor, shape = shape)
                                            .clickable(onClick = { onTabSelected(tab) })
                                            .padding(
                                                horizontal = if (isSelected) selectedTabHorizontalPadding else tabHorizontalPadding,
                                                vertical = 10.dp
                                            )
                                            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.title,
                                            tint = contentColor,
                                            modifier = Modifier.size(24.dp)
                                        )

                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = tab.title,
                                                color = contentColor,
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.widthIn(max = maxLabelWidth)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FloatingToolbarDefaults.VibrantFloatingActionButton(
                        onClick = onFabClick,
                        modifier = Modifier.size(56.dp),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ) {
                        Icon(fabIcon, contentDescription = null)
                    }
                }
            }
        }
    }
}

/**
 * Playback progress for the docks, kept out of the caller's recomposition scope.
 *
 * Reading `currentPosition` in a composable body makes that whole body recompose on every position
 * tick, and the docks are large - the mini player, the navigation bar and every navigation item, in
 * both style branches. The tick runs at 500 ms normally, 200 ms near the end of a track, and 25 Hz
 * while DJ Flow has the player open, so the whole bar was being rebuilt up to 25 times a second to
 * move a 2 dp line.
 *
 * Collecting inside [snapshotFlow] reads the position in a snapshot observer instead, so only the
 * progress value changes. Same approach as the mini player's own bar.
 */
@Composable
internal fun rememberDockProgress(viewModel: com.alananasss.kittytune.ui.player.PlayerViewModel): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(viewModel) {
        snapshotFlow {
            if (viewModel.duration > 0) {
                (viewModel.currentPosition.toFloat() / viewModel.duration.toFloat()).coerceIn(0f, 1f)
            } else 0f
        }.collect { target ->
            // Snap on a track change or a seek, glide in between, so the line does not stutter at
            // the tick rate.
            val delta = target - progress.value
            val spec: androidx.compose.animation.core.TweenSpec<Float> =
                if (kotlin.math.abs(delta) > 0.05f) {
                    androidx.compose.animation.core.tween(150)
                } else {
                    androidx.compose.animation.core.tween(
                        durationMillis = 1000,
                        easing = androidx.compose.animation.core.LinearEasing
                    )
                }
            progress.animateTo(target, animationSpec = spec)
        }
    }
    return progress
}
