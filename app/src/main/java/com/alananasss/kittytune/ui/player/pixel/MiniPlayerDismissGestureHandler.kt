package com.alananasss.kittytune.ui.player.pixel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.alananasss.kittytune.data.local.MiniPlayerSwipeAction
import kotlin.math.abs
import kotlin.math.sign

private enum class MiniDismissDragPhase { IDLE, TENSION, SNAPPING, FREE_DRAG }

/**
 * Swipe gesture handler for MiniPlayer supporting track switching or dismiss.
 * - In CHANGE_TRACK mode: Swipe left to play next, swipe right to play previous with spring bounce-back.
 * - In DISMISS mode: 4 drag phases with commit offscreen (>40% screen width) to dismiss the player.
 */
internal class MiniPlayerDismissGestureHandler(
    private val scope: CoroutineScope,
    private val density: Density,
    private val hapticFeedback: HapticFeedback,
    private val offsetAnimatable: Animatable<Float, AnimationVector1D>,
    private val screenWidthPx: Float,
    private val swipeAction: MiniPlayerSwipeAction = MiniPlayerSwipeAction.CHANGE_TRACK,
    private val onDismiss: () -> Unit = {},
    private val onDismissStarted: () -> Unit = {},
    private val onSwipeNext: () -> Unit = {},
    private val onSwipePrevious: () -> Unit = {}
) {
    private var dragPhase: MiniDismissDragPhase = MiniDismissDragPhase.IDLE
    private var accumulatedDragX: Float = 0f
    private var offsetJob: Job? = null
    private var dragStartTime: Long = 0L
    private var hasTriggeredThresholdHaptic: Boolean = false

    fun onDragStart() {
        dragStartTime = android.os.SystemClock.uptimeMillis()
        dragPhase = MiniDismissDragPhase.TENSION
        accumulatedDragX = 0f
        hasTriggeredThresholdHaptic = false
        offsetJob?.cancel()
        offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            offsetAnimatable.stop()
        }
    }

    fun onHorizontalDrag(dragAmount: Float) {
        accumulatedDragX += dragAmount

        if (swipeAction == MiniPlayerSwipeAction.CHANGE_TRACK) {
            val thresholdPx = 52f * density.density
            val isPastThreshold = abs(accumulatedDragX) >= thresholdPx

            if (isPastThreshold && !hasTriggeredThresholdHaptic) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                hasTriggeredThresholdHaptic = true
            } else if (!isPastThreshold && hasTriggeredThresholdHaptic) {
                hasTriggeredThresholdHaptic = false
            }

            // Smooth rubber-band resistance curve
            val maxDragPx = screenWidthPx * 0.32f
            val absDrag = abs(accumulatedDragX)
            val resistedOffset = if (absDrag <= thresholdPx) {
                absDrag * 0.85f
            } else {
                val excess = absDrag - thresholdPx
                (thresholdPx * 0.85f) + (excess * 0.32f).coerceAtMost(maxDragPx - (thresholdPx * 0.85f))
            }
            val targetOffset = resistedOffset * accumulatedDragX.sign

            offsetJob?.cancel()
            offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                offsetAnimatable.snapTo(targetOffset)
            }
            return
        }

        // DISMISS ACTION
        when (dragPhase) {
            MiniDismissDragPhase.TENSION -> {
                val snapThresholdPx = 100f * density.density
                if (abs(accumulatedDragX) < snapThresholdPx) {
                    val maxTensionOffsetPx = 30f * density.density
                    val dragFraction = (abs(accumulatedDragX) / snapThresholdPx).coerceIn(0f, 1f)
                    val tensionOffset = lerp(0f, maxTensionOffsetPx, dragFraction)
                    offsetJob?.cancel()
                    offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        offsetAnimatable.snapTo(tensionOffset * accumulatedDragX.sign)
                    }
                } else {
                    dragPhase = MiniDismissDragPhase.SNAPPING
                }
            }

            MiniDismissDragPhase.SNAPPING -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                offsetJob?.cancel()
                offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    offsetAnimatable.animateTo(
                        targetValue = accumulatedDragX,
                        animationSpec = spring(
                            dampingRatio = 0.8f,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
                dragPhase = MiniDismissDragPhase.FREE_DRAG
            }

            MiniDismissDragPhase.FREE_DRAG -> {
                offsetJob?.cancel()
                offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    offsetAnimatable.animateTo(
                        targetValue = accumulatedDragX,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessHigh
                        )
                    )
                }
            }

            MiniDismissDragPhase.IDLE -> Unit
        }
    }

    fun onDragEnd() {
        dragPhase = MiniDismissDragPhase.IDLE
        offsetJob?.cancel()

        if (swipeAction == MiniPlayerSwipeAction.CHANGE_TRACK) {
            val elapsed = android.os.SystemClock.uptimeMillis() - dragStartTime
            val thresholdPx = if (elapsed in 1..250 && abs(accumulatedDragX) > 28f * density.density) {
                28f * density.density
            } else {
                52f * density.density
            }

            if (abs(accumulatedDragX) >= thresholdPx) {
                if (accumulatedDragX < 0) {
                    onSwipeNext()
                } else {
                    onSwipePrevious()
                }
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }

            offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                offsetAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            return
        }

        // DISMISS ACTION
        val dismissThreshold = screenWidthPx * 0.4f
        if (abs(accumulatedDragX) > dismissThreshold) {
            onDismissStarted()
            val targetDismissOffset = if (accumulatedDragX < 0) -screenWidthPx else screenWidthPx
            offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                offsetAnimatable.animateTo(
                    targetValue = targetDismissOffset,
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = FastOutSlowInEasing
                    )
                )
                onDismiss()
                offsetAnimatable.snapTo(0f)
            }
        } else {
            offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                offsetAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }
}

@Composable
internal fun rememberMiniPlayerDismissGestureHandler(
    scope: CoroutineScope,
    density: Density,
    hapticFeedback: HapticFeedback,
    offsetAnimatable: Animatable<Float, AnimationVector1D>,
    screenWidthPx: Float,
    swipeAction: MiniPlayerSwipeAction = MiniPlayerSwipeAction.CHANGE_TRACK,
    onDismiss: () -> Unit = {},
    onDismissStarted: () -> Unit = {},
    onSwipeNext: () -> Unit = {},
    onSwipePrevious: () -> Unit = {}
): MiniPlayerDismissGestureHandler {
    val onDismissState = rememberUpdatedState(onDismiss)
    val onDismissStartedState = rememberUpdatedState(onDismissStarted)
    val onSwipeNextState = rememberUpdatedState(onSwipeNext)
    val onSwipePreviousState = rememberUpdatedState(onSwipePrevious)
    return remember(scope, density, hapticFeedback, offsetAnimatable, screenWidthPx, swipeAction) {
        MiniPlayerDismissGestureHandler(
            scope = scope,
            density = density,
            hapticFeedback = hapticFeedback,
            offsetAnimatable = offsetAnimatable,
            screenWidthPx = screenWidthPx,
            swipeAction = swipeAction,
            onDismiss = { onDismissState.value() },
            onDismissStarted = { onDismissStartedState.value() },
            onSwipeNext = { onSwipeNextState.value() },
            onSwipePrevious = { onSwipePreviousState.value() }
        )
    }
}

internal fun Modifier.miniPlayerDismissHorizontalGesture(
    enabled: Boolean,
    handler: MiniPlayerDismissGestureHandler
): Modifier {
    if (!enabled) return this
    return this.pointerInput(enabled, handler) {
        detectHorizontalDragGestures(
            onDragStart = { handler.onDragStart() },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                handler.onHorizontalDrag(dragAmount)
            },
            onDragEnd = { handler.onDragEnd() }
        )
    }
}
