package com.alananasss.kittytune.ui.player.pixel

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

private enum class PlaybackButtonType { NONE, PREVIOUS, PLAY_PAUSE, NEXT }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedPlaybackControls(
    isPlayingProvider: () -> Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    isLoadingProvider: () -> Boolean = { false },
    height: Dp = 90.dp,
    baseWeight: Float = 1f,
    expansionWeight: Float = 1.1f,
    compressionWeight: Float = 0.65f,
    pressAnimationSpec: AnimationSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 380f),
    releaseDelay: Long = 220L,
    playPauseCornerPlaying: Dp = 60.dp,
    playPauseCornerPaused: Dp = 26.dp,
    colorOtherButtons: Color = MaterialTheme.colorScheme.secondaryContainer,
    colorPlayPause: Color = MaterialTheme.colorScheme.primary,
    tintPlayPauseIcon: Color = MaterialTheme.colorScheme.onPrimary,
    tintOtherIcons: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    colorPreviousButton: Color = colorOtherButtons,
    colorNextButton: Color = colorOtherButtons,
    tintPreviousIcon: Color = tintOtherIcons,
    tintNextIcon: Color = tintOtherIcons,
    playPauseIconSize: Dp = 36.dp,
    iconSize: Dp = 32.dp,
) {
    val isPlaying = isPlayingProvider()
    val isLoading = isLoadingProvider()
    var lastClicked by remember { mutableStateOf<PlaybackButtonType?>(null) }
    var clickTrigger by remember { mutableStateOf(0) }
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val motionScheme = remember { MotionScheme.expressive() }
    val defaultSpatialDpSpec = remember { motionScheme.defaultSpatialSpec<Dp>() }

    LaunchedEffect(lastClicked, clickTrigger) {
        if (lastClicked != null) {
            delay(releaseDelay)
            lastClicked = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            fun weightFor(button: PlaybackButtonType): Float = when (lastClicked) {
                button -> expansionWeight
                null -> baseWeight
                else -> compressionWeight
            }

            val prevWeight by animateFloatAsState(
                targetValue = weightFor(PlaybackButtonType.PREVIOUS),
                animationSpec = pressAnimationSpec,
                label = "prevWeight"
            )
            Box(
                modifier = Modifier
                    .weight(prevWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colorPreviousButton)
                    .clickable {
                        lastClicked = PlaybackButtonType.PREVIOUS
                        clickTrigger++
                        coroutineScope.launch {
                            delay(180)
                            onPrevious()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint = tintPreviousIcon,
                    modifier = Modifier.size(iconSize)
                )
            }

            val playWeight by animateFloatAsState(
                targetValue = weightFor(PlaybackButtonType.PLAY_PAUSE),
                animationSpec = pressAnimationSpec,
                label = "playWeight"
            )
            val playCorner by animateDpAsState(
                targetValue = if (!isPlaying) playPauseCornerPlaying else playPauseCornerPaused,
                animationSpec = defaultSpatialDpSpec,
                label = "playCorner"
            )
            Box(
                modifier = Modifier
                    .weight(playWeight)
                    .fillMaxHeight()
                    .graphicsLayer {
                        clip = true
                        shape = AbsoluteSmoothCornerShape(
                            cornerRadiusTL = playCorner,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusBL = playCorner,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusTR = playCorner,
                            smoothnessAsPercentBL = 60,
                            cornerRadiusBR = playCorner,
                            smoothnessAsPercentBR = 60
                        )
                    }
                    .background(colorPlayPause)
                    .clickable {
                        lastClicked = PlaybackButtonType.PLAY_PAUSE
                        clickTrigger++
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPlayPause()
                    },
                contentAlignment = Alignment.Center
            ) {
                MorphingPlayPauseIcon(
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    tint = tintPlayPauseIcon,
                    size = playPauseIconSize,
                    motionScheme = motionScheme
                )
            }

            val nextWeight by animateFloatAsState(
                targetValue = weightFor(PlaybackButtonType.NEXT),
                animationSpec = pressAnimationSpec,
                label = "nextWeight"
            )
            Box(
                modifier = Modifier
                    .weight(nextWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colorNextButton)
                    .clickable {
                        lastClicked = PlaybackButtonType.NEXT
                        clickTrigger++
                        coroutineScope.launch {
                            delay(180)
                            onNext()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = tintNextIcon,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphingPlayPauseIcon(
    isPlaying: Boolean,
    isLoading: Boolean,
    tint: Color,
    size: Dp,
    motionScheme: MotionScheme
) {
    AnimatedContent(
        targetState = Pair(isLoading, isPlaying),
        transitionSpec = {
            val springSpec = spring<Float>(dampingRatio = 0.6f, stiffness = 1000f)
            (scaleIn(initialScale = 0.8f, animationSpec = springSpec) + fadeIn(tween(100)))
                .togetherWith(
                    scaleOut(targetScale = 0.8f, animationSpec = springSpec) + fadeOut(tween(100))
                )
                .using(SizeTransform(clip = false))
        },
        label = "playPauseLoadingCrossfade"
    ) { (loading, playing) ->
        if (loading) {
            LoadingIndicator(
                color = tint,
                modifier = Modifier.size(size)
            )
        } else {
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = tint,
                modifier = Modifier.size(size)
            )
        }
    }
}

