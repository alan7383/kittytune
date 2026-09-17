package com.alananasss.kittytune.ui.player.pixel

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerActionButtonSlot
import com.alananasss.kittytune.ui.player.RepeatMode
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
fun BottomToggleRow(
    modifier: Modifier = Modifier,
    slots: List<PlayerActionButtonSlot> = listOf(
        PlayerActionButtonSlot.SHUFFLE,
        PlayerActionButtonSlot.REPEAT,
        PlayerActionButtonSlot.LIKE,
        PlayerActionButtonSlot.QUEUE
    ),
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    isFavorite: Boolean,
    isLyricsActive: Boolean = false,
    isFullscreenLyricsActive: Boolean = false,
    isSleepTimerActive: Boolean = false,
    isHapticsActive: Boolean = false,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onQueueClick: () -> Unit = {},
    onEffectsClick: () -> Unit = {},
    onLyricsClick: () -> Unit = {},
    onFullscreenLyricsClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onCommentsClick: () -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
    onHapticsToggle: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    activeColorMain: Color = MaterialTheme.colorScheme.primary,
    activeColorSecondary: Color = MaterialTheme.colorScheme.secondary,
    activeColorTertiary: Color = MaterialTheme.colorScheme.tertiary,
    onActiveColorMain: Color = MaterialTheme.colorScheme.onPrimary,
    onActiveColorSecondary: Color = MaterialTheme.colorScheme.onSecondary,
    onActiveColorTertiary: Color = MaterialTheme.colorScheme.onTertiary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    inactiveContentColor: Color = MaterialTheme.colorScheme.onSurface,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f)
) {
    val rowCorners = 60.dp
    val visibleSlots = slots.filter { it != PlayerActionButtonSlot.NONE }.ifEmpty {
        listOf(PlayerActionButtonSlot.SHUFFLE, PlayerActionButtonSlot.REPEAT, PlayerActionButtonSlot.LIKE)
    }

    Box(
        modifier = modifier.background(
            color = containerColor,
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusBL = rowCorners,
                smoothnessAsPercentTR = 60,
                cornerRadiusBR = rowCorners,
                smoothnessAsPercentBL = 60,
                cornerRadiusTL = rowCorners,
                smoothnessAsPercentBR = 60,
                cornerRadiusTR = rowCorners,
                smoothnessAsPercentTL = 60
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(
                    AbsoluteSmoothCornerShape(
                        cornerRadiusBL = rowCorners,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusBR = rowCorners,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusTL = rowCorners,
                        smoothnessAsPercentBR = 60,
                        cornerRadiusTR = rowCorners,
                        smoothnessAsPercentTL = 60
                    )
                )
                .background(Color.Transparent),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            visibleSlots.forEachIndexed { index, slot ->
                val commonModifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()

                // Distribute active colors tastefully across slots
                val activeBg = when (index % 3) {
                    0 -> activeColorMain
                    1 -> activeColorSecondary
                    else -> activeColorTertiary
                }
                val activeContent = when (index % 3) {
                    0 -> onActiveColorMain
                    1 -> onActiveColorSecondary
                    else -> onActiveColorTertiary
                }

                when (slot) {
                    PlayerActionButtonSlot.SHUFFLE -> {
                        ToggleSegmentButton(
                            modifier = commonModifier,
                            active = isShuffleEnabled,
                            activeColor = activeBg,
                            activeCornerRadius = rowCorners,
                            activeContentColor = activeContent,
                            inactiveColor = inactiveColor,
                            inactiveContentColor = inactiveContentColor,
                            onClick = onShuffleToggle,
                            iconId = R.drawable.rounded_shuffle_24,
                            contentDesc = "Shuffle"
                        )
                    }
                    PlayerActionButtonSlot.REPEAT -> {
                        val repeatActive = repeatMode != RepeatMode.NONE
                        val repeatIcon = when (repeatMode) {
                            RepeatMode.ONE -> R.drawable.rounded_repeat_one_24
                            else -> R.drawable.rounded_repeat_24
                        }
                        ToggleSegmentButton(
                            modifier = commonModifier,
                            active = repeatActive,
                            activeColor = activeBg,
                            activeCornerRadius = rowCorners,
                            activeContentColor = activeContent,
                            inactiveColor = inactiveColor,
                            inactiveContentColor = inactiveContentColor,
                            onClick = onRepeatToggle,
                            iconId = repeatIcon,
                            contentDesc = "Repeat"
                        )
                    }
                    PlayerActionButtonSlot.LIKE -> {
                        ToggleSegmentButton(
                            modifier = commonModifier,
                            active = isFavorite,
                            activeColor = activeBg,
                            activeCornerRadius = rowCorners,
                            activeContentColor = activeContent,
                            inactiveColor = inactiveColor,
                            inactiveContentColor = inactiveContentColor,
                            onClick = onFavoriteToggle,
                            iconId = if (isFavorite) R.drawable.round_favorite_24 else R.drawable.round_favorite_border_24,
                            contentDesc = "Favorite"
                        )
                    }
                    PlayerActionButtonSlot.QUEUE -> {
                        ToggleSegmentButton(
                            modifier = commonModifier,
                            active = false,
                            activeColor = activeBg,
                            activeCornerRadius = rowCorners,
                            activeContentColor = activeContent,
                            inactiveColor = inactiveColor,
                            inactiveContentColor = inactiveContentColor,
                            onClick = onQueueClick,
                            iconVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDesc = "Queue"
                        )
                    }
                    PlayerActionButtonSlot.AUDIO_FX -> {
                        ToggleSegmentButton(
                            modifier = commonModifier,
                            active = false,
                            activeColor = activeBg,
                            activeCornerRadius = rowCorners,
                            activeContentColor = activeContent,
                            inactiveColor = inactiveColor,
                            inactiveContentColor = inactiveContentColor,
                            onClick = onEffectsClick,
                            iconVector = Icons.Rounded.GraphicEq,
                            contentDesc = "Audio Effects"
                        )
                    }
                    PlayerActionButtonSlot.LYRICS -> {
                        ToggleSegmentButton(
                            modifier = commonModifier,
                            active = isLyricsActive,
                            activeColor = activeBg,
                            activeCornerRadius = rowCorners,
                            activeContentColor = activeContent,
                            inactiveColor = inactiveColor,
                            inactiveContentColor = inactiveContentColor,
                            onClick = onLyricsClick,
                            onLongClick = onFullscreenLyricsClick,
                            iconVector = Icons.Rounded.Description,
                            contentDesc = "Lyrics"
                        )
                    }
                    PlayerActionButtonSlot.FULLSCREEN_LYRICS -> {
                        ToggleSegmentButton(
                            modifier = commonModifier,
                            active = isFullscreenLyricsActive,
                            activeColor = activeBg,
                            activeCornerRadius = rowCorners,
                            activeContentColor = activeContent,
                            inactiveColor = inactiveColor,
                            inactiveContentColor = inactiveContentColor,
                            onClick = onFullscreenLyricsClick,
                            iconVector = Icons.Rounded.OpenInFull,
                            contentDesc = "Full Screen Lyrics"
                        )
                    }
                    PlayerActionButtonSlot.SHARE -> {
                        ToggleSegmentButton(
                            modifier = commonModifier,
                            active = false,
                            activeColor = activeBg,
                            activeCornerRadius = rowCorners,
                            activeContentColor = activeContent,
                            inactiveColor = inactiveColor,
                            inactiveContentColor = inactiveContentColor,
                            onClick = onShareClick,
                            iconVector = Icons.Rounded.Share,
                            contentDesc = "Share"
                        )
                    }
                    PlayerActionButtonSlot.COMMENTS -> {
                        ToggleSegmentButton(
                            modifier = commonModifier,
                            active = false,
                            activeColor = activeBg,
                            activeCornerRadius = rowCorners,
                            activeContentColor = activeContent,
                            inactiveColor = inactiveColor,
                            inactiveContentColor = inactiveContentColor,
                            onClick = onCommentsClick,
                            iconVector = Icons.AutoMirrored.Rounded.Comment,
                            contentDesc = "Comments"
                        )
                    }
                    PlayerActionButtonSlot.SLEEP_TIMER -> {
                        ToggleSegmentButton(
                            modifier = commonModifier,
                            active = isSleepTimerActive,
                            activeColor = activeBg,
                            activeCornerRadius = rowCorners,
                            activeContentColor = activeContent,
                            inactiveColor = inactiveColor,
                            inactiveContentColor = inactiveContentColor,
                            onClick = onSleepTimerClick,
                            iconVector = Icons.Rounded.Bedtime,
                            contentDesc = "Sleep Timer"
                        )
                    }
                    PlayerActionButtonSlot.HAPTICS -> {
                        ToggleSegmentButton(
                            modifier = commonModifier,
                            active = isHapticsActive,
                            activeColor = activeBg,
                            activeCornerRadius = rowCorners,
                            activeContentColor = activeContent,
                            inactiveColor = inactiveColor,
                            inactiveContentColor = inactiveContentColor,
                            onClick = onHapticsToggle,
                            iconVector = Icons.Rounded.Vibration,
                            contentDesc = "Haptics"
                        )
                    }
                    PlayerActionButtonSlot.MORE -> {
                        ToggleSegmentButton(
                            modifier = commonModifier,
                            active = false,
                            activeColor = activeBg,
                            activeCornerRadius = rowCorners,
                            activeContentColor = activeContent,
                            inactiveColor = inactiveColor,
                            inactiveContentColor = inactiveContentColor,
                            onClick = onMoreClick,
                            iconVector = Icons.Rounded.MoreVert,
                            contentDesc = "More"
                        )
                    }
                    PlayerActionButtonSlot.NONE -> {}
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToggleSegmentButton(
    modifier: Modifier = Modifier,
    active: Boolean,
    enabled: Boolean = true,
    activeColor: Color,
    inactiveColor: Color,
    activeContentColor: Color,
    inactiveContentColor: Color,
    activeCornerRadius: Dp = 60.dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    iconId: Int? = null,
    iconVector: ImageVector? = null,
    contentDesc: String
) {
    val view = LocalView.current
    val targetBgColor = if (active) activeColor else inactiveColor
    val bgColor by animateColorAsState(
        targetValue = if (enabled) targetBgColor else targetBgColor.copy(alpha = 0.5f),
        animationSpec = tween(durationMillis = 250),
        label = "toggleBg"
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (active) activeCornerRadius else 8.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "toggleCorner"
    )

    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            enabled = enabled,
            onClick = onClick,
            onLongClick = {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onLongClick()
            }
        )
    } else {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor)
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer(alpha = if (enabled) 1f else 0.38f)
                .padding(horizontal = 10.dp)
        ) {
            if (iconId != null) {
                Icon(
                    painter = painterResource(iconId),
                    contentDescription = contentDesc,
                    tint = if (active) activeContentColor else inactiveContentColor,
                    modifier = Modifier.size(24.dp)
                )
            } else if (iconVector != null) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = contentDesc,
                    tint = if (active) activeContentColor else inactiveContentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
