package com.alananasss.kittytune.ui.navigation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.data.local.MiniPlayerSwipeAction
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.lyrics.FloatingLyricChip
import com.alananasss.kittytune.ui.player.pixel.miniPlayerDismissHorizontalGesture
import com.alananasss.kittytune.ui.player.pixel.rememberMiniPlayerDismissGestureHandler
import androidx.compose.ui.platform.LocalContext
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
    blurEnabled: Boolean = true,
    showSkipInMiniPlayer: Boolean = true,
    showLabels: Boolean = true
) {
    val track = playerViewModel.uiCurrentTrack

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current
    val playerPrefs = remember { PlayerPreferences(context) }
    val miniPlayerStyle by playerPrefs.miniPlayerStyleFlow().collectAsState(initial = playerPrefs.getMiniPlayerStyle())
    val miniPlayerShowCover by playerPrefs.miniPlayerClassicShowCoverFlow().collectAsState(initial = playerPrefs.getMiniPlayerClassicShowCover())
    val miniPlayerCoverAsBackground by playerPrefs.miniPlayerClassicCoverAsBackgroundFlow().collectAsState(initial = playerPrefs.getMiniPlayerClassicCoverAsBackground())
    val miniPlayerProgressFill by playerPrefs.miniPlayerClassicProgressFillFlow().collectAsState(initial = playerPrefs.getMiniPlayerClassicProgressFill())
    // Feed tab: the mini player is always hidden there (feed previews fill the screen);
    // every other tab keeps the normal bar.
    val miniHiddenOnFeed = selectedRoute == Screen.Feed.route
    val swipeAction by playerPrefs.miniPlayerSwipeActionFlow().collectAsState(initial = playerPrefs.getMiniPlayerSwipeAction())

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
        swipeAction = swipeAction,
        onDismiss = {
            playerViewModel.dismissMiniPlayerAndShowUndo()
        },
        onDismissStarted = {
            playerViewModel.isMiniPlayerDismissing = true
        },
        onSwipeNext = {
            playerViewModel.requestSkipNext()
        },
        onSwipePrevious = {
            playerViewModel.smartPrevious()
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
            if (track != null && !miniHiddenOnFeed) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    FloatingLyricChip(
                        vm = playerViewModel,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                    if (miniPlayerStyle == com.alananasss.kittytune.data.local.MiniPlayerStyle.MODERN) {
                        com.alananasss.kittytune.ui.player.KittyModernMiniPlayer(
                            viewModel = playerViewModel,
                            onPlayerClick = onPlayerClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clipToBounds()
                        .systemGestureExclusion()
                        .graphicsLayer {
                            translationX = offsetAnimatable.value
                            alpha = if (swipeAction == MiniPlayerSwipeAction.DISMISS) {
                                (1f - (abs(offsetAnimatable.value) / (screenWidthPx * 0.85f))).coerceIn(0f, 1f)
                            } else {
                                (1f - (abs(offsetAnimatable.value) / screenWidthPx) * 0.35f).coerceIn(0.65f, 1f)
                            }
                        }
                        .miniPlayerDismissHorizontalGesture(
                            enabled = true,
                            handler = miniDismissGestureHandler
                        )
                ) {
                    com.alananasss.kittytune.ui.player.ClassicMiniPlayerContent(
                        viewModel = playerViewModel,
                        showCover = miniPlayerShowCover,
                        coverAsBackground = miniPlayerCoverAsBackground,
                        progressFill = miniPlayerProgressFill,
                        onClick = onPlayerClick,
                        progress = rememberDockProgress(playerViewModel),
                        showSkip = showSkipInMiniPlayer,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                }
                    } // else: classic mini player strip
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
                        onClick = {
                            com.alananasss.kittytune.audio.haptics.HapticUtil.performUIHaptic(context)
                            onTabSelected(tab)
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = if (showLabels) ({ Text(tab.title) }) else null,
                        alwaysShowLabel = showLabels,
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
            if (track != null && !miniHiddenOnFeed) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    FloatingLyricChip(
                        vm = playerViewModel,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                    if (miniPlayerStyle == com.alananasss.kittytune.data.local.MiniPlayerStyle.MODERN) {
                        com.alananasss.kittytune.ui.player.KittyModernMiniPlayer(
                            viewModel = playerViewModel,
                            onPlayerClick = onPlayerClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clipToBounds()
                        .systemGestureExclusion()
                        .graphicsLayer {
                            translationX = offsetAnimatable.value
                            alpha = if (swipeAction == MiniPlayerSwipeAction.DISMISS) {
                                (1f - (abs(offsetAnimatable.value) / (screenWidthPx * 0.85f))).coerceIn(0f, 1f)
                            } else {
                                (1f - (abs(offsetAnimatable.value) / screenWidthPx) * 0.35f).coerceIn(0.65f, 1f)
                            }
                        }
                        .miniPlayerDismissHorizontalGesture(
                            enabled = true,
                            handler = miniDismissGestureHandler
                        )
                ) {
                    com.alananasss.kittytune.ui.player.ClassicMiniPlayerContent(
                        viewModel = playerViewModel,
                        showCover = miniPlayerShowCover,
                        coverAsBackground = miniPlayerCoverAsBackground,
                        progressFill = miniPlayerProgressFill,
                        onClick = onPlayerClick,
                        progress = rememberDockProgress(playerViewModel),
                        showSkip = showSkipInMiniPlayer,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
                    } // else: classic mini player card
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
                                    // AyuGram createMaterial3ColorSpring: dampingRatio 1.0f, stiffness 1600f
                                    val containerColor by animateColorAsState(
                                        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                        animationSpec = spring(dampingRatio = 1.0f, stiffness = 1600f),
                                        label = "containerColor"
                                    )
                                    val contentColor by animateColorAsState(
                                        targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        animationSpec = spring(dampingRatio = 1.0f, stiffness = 1600f),
                                        label = "contentColor"
                                    )

                                    // AyuGram ButtonBounce: 60ms cubic press down to ~0.92, overshoot spring release
                                    val tabInteractionSource = remember { MutableInteractionSource() }
                                    val isPressed by tabInteractionSource.collectIsPressedAsState()
                                    val tabBounceScale = remember { Animatable(1f) }

                                    LaunchedEffect(isPressed) {
                                        if (isPressed) {
                                            tabBounceScale.animateTo(
                                                targetValue = 0.92f,
                                                animationSpec = tween(durationMillis = 60, easing = FastOutSlowInEasing)
                                            )
                                        } else {
                                            tabBounceScale.animateTo(
                                                targetValue = 1f,
                                                animationSpec = spring(
                                                    dampingRatio = 0.55f,
                                                    stiffness = 450f
                                                )
                                            )
                                        }
                                    }

                                    val iconScale by animateFloatAsState(
                                        targetValue = if (isSelected) 1.05f else 1.0f,
                                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                                        label = "iconScale"
                                    )

                                    Row(
                                        modifier = Modifier
                                            .graphicsLayer {
                                                scaleX = tabBounceScale.value
                                                scaleY = tabBounceScale.value
                                            }
                                            .clip(shape)
                                            .background(color = containerColor, shape = shape)
                                            .clickable(
                                                interactionSource = tabInteractionSource,
                                                indication = null,
                                                onClick = {
                                                    com.alananasss.kittytune.audio.haptics.HapticUtil.performUIHaptic(context)
                                                    onTabSelected(tab)
                                                }
                                            )
                                            .padding(
                                                horizontal = if (isSelected) selectedTabHorizontalPadding else tabHorizontalPadding,
                                                vertical = 10.dp
                                            )
                                            // AyuGram createMaterial3IndicatorSizeSpring: dampingRatio 0.8f, stiffness 380f
                                            .animateContentSize(animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f)),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.title,
                                            tint = contentColor,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .graphicsLayer {
                                                    scaleX = iconScale
                                                    scaleY = iconScale
                                                }
                                        )

                                        // Labels: the SELECTED pill grows its label (mini dock
                                        // behaviour). With the unified "hide labels" switch on
                                        // even the selected pill stays icons-only.
                                        if (isSelected && showLabels) {
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
                        onClick = {
                            com.alananasss.kittytune.audio.haptics.HapticUtil.performMediumHaptic(context)
                            onFabClick()
                        },
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
            viewModel.progressOf(viewModel.uiCurrentPosition.toFloat())
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
