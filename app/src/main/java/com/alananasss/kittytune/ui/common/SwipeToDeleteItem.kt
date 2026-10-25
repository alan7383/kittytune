package com.alananasss.kittytune.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SwipeToDeleteItem(
    key: Any? = Unit,
    enabled: Boolean = true,
    onDelete: () -> Unit,
    shape: Shape = RoundedCornerShape(12.dp),
    horizontalPadding: Dp = 0.dp,
    cardColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    clipContent: Boolean = true,
    endZoneIgnoreWidth: Dp = 56.dp,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .then(if (clipContent) Modifier.clip(shape) else Modifier)
        ) {
            content()
        }
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = rememberAppHaptics()

    val dismissThresholdPx = with(density) { 100.dp.toPx() }

    val offsetX = remember(key) { Animatable(0f) }
    val progress = (-offsetX.value / dismissThresholdPx).coerceIn(0f, 1f)

    val bgColor by animateColorAsState(
        targetValue = if (progress > 0f)
            MaterialTheme.colorScheme.errorContainer.copy(alpha = progress.coerceIn(0.1f, 1f))
        else
            Color.Transparent,
        label = "swipe_bg"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "icon_alpha"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (progress > 0f) (0.75f + 0.35f * progress).coerceIn(0.75f, 1.15f) else 0.75f,
        label = "icon_scale"
    )
    val cardBgColor by animateColorAsState(
        targetValue = if (progress > 0f) cardColor else Color.Transparent,
        animationSpec = tween(120),
        label = "swipe_card_bg"
    )

    val pointerModifier = Modifier.pointerInput(key, endZoneIgnoreWidth) {
        val touchSlop = viewConfiguration.touchSlop
        val endZoneThresholdPx = if (endZoneIgnoreWidth > 0.dp) endZoneIgnoreWidth.toPx() else 0f

        awaitEachGesture {
            val velocityTracker = VelocityTracker()
            val down = awaitFirstDown(requireUnconsumed = false)

            // If the gesture starts in the designated end zone (e.g. reorder handle or options menu),
            // do not intercept or interfere with child gesture detectors.
            if (endZoneThresholdPx > 0f && down.position.x > size.width - endZoneThresholdPx) {
                waitForUpOrCancellation()
                return@awaitEachGesture
            }

            velocityTracker.addPosition(down.uptimeMillis, down.position)

            var isHorizontal: Boolean? = null
            var dragStarted = false
            var thresholdAnnounced = false
            val maxSwipe = size.width.toFloat()

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull() ?: break
                if (!change.pressed) break

                // If a child has consumed the change before horizontal swipe started, stop handling.
                if (change.isConsumed && !dragStarted) {
                    break
                }

                val deltaX = change.position.x - change.previousPosition.x
                val totalDeltaX = change.position.x - down.position.x
                val totalDeltaY = change.position.y - down.position.y

                if (isHorizontal == null) {
                    val absTotalX = abs(totalDeltaX)
                    val absTotalY = abs(totalDeltaY)
                    if (absTotalX > touchSlop || absTotalY > touchSlop) {
                        // Swipe-to-delete is strictly leftward; ignore rightward drags or vertical motion
                        if (absTotalX > absTotalY && (totalDeltaX < -touchSlop || offsetX.value < 0f)) {
                            isHorizontal = true
                        } else {
                            isHorizontal = false
                            break
                        }
                    }
                }

                if (isHorizontal == true) {
                    change.consume()
                    dragStarted = true
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    val newOffset = (offsetX.value + deltaX).coerceIn(-maxSwipe, 0f)
                    coroutineScope.launch { offsetX.snapTo(newOffset) }
                    if (!thresholdAnnounced && -newOffset >= dismissThresholdPx) {
                        thresholdAnnounced = true
                        haptics.perform(HapticKind.THRESHOLD)
                    } else if (thresholdAnnounced && -newOffset < dismissThresholdPx) {
                        thresholdAnnounced = false
                    }
                }
            }

            if (dragStarted) {
                val velocity = velocityTracker.calculateVelocity().x
                coroutineScope.launch {
                    if (-offsetX.value >= dismissThresholdPx || velocity < -1000f) {
                        haptics.perform(HapticKind.CONFIRM)
                        offsetX.animateTo(-size.width.toFloat())
                        onDelete()
                    } else {
                        offsetX.animateTo(0f)
                    }
                }
            } else if (offsetX.value != 0f) {
                coroutineScope.launch { offsetX.animateTo(0f) }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(pointerModifier)
            .padding(horizontal = horizontalPadding)
            .then(if (clipContent) Modifier.clip(shape) else Modifier)
    ) {
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(bgColor)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = iconAlpha),
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .clip(shape)
                .background(cardBgColor)
        ) {
            content()
        }
    }
}
