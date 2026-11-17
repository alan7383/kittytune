package com.alananasss.kittytune.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Attaches an intuitive, full-screen downward drag-to-dismiss gesture to the player container.
 *
 * Key behaviors:
 * 1. Works across the entire surface of the player (artwork, controls, background, header).
 * 2. Velocity-aware: quick downward flicks/swipes immediately dismiss the player even if the
 *    drag distance is short (matching SoundCloud and Spotify behavior).
 * 3. Directional arbitration: horizontal gestures (queue paging on HorizontalPager, waveform
 *    scrubbing, seeking) are never intercepted or blocked.
 * 4. Yields to vertical scrolling children (e.g. lyrics list) when they consume vertical events.
 * 5. Taps and clicks on buttons, covers, and titles remain completely responsive and unaffected.
 */
fun Modifier.playerDismissGesture(
    dismissTargetY: Float,
    dismissProgress: Animatable<Float, AnimationVector1D>,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
    enabled: Boolean = true
): Modifier = if (!enabled || dismissTargetY <= 0f) this else this.pointerInput(dismissTargetY, enabled) {
    val touchSlop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        scope.launch {
            dismissProgress.stop()
        }
        val velocityTracker = VelocityTracker()
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        var totalDeltaY = 0f
        var totalDeltaX = 0f
        var isVerticalDrag: Boolean? = null
        var isDragging = false
        val initialProgress = dismissProgress.value

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break

            // If a child consumed the event before we claimed vertical drag, yield to child
            if (!isDragging && change.isConsumed) {
                break
            }

            val dx = change.position.x - change.previousPosition.x
            val dy = change.position.y - change.previousPosition.y

            velocityTracker.addPosition(change.uptimeMillis, change.position)
            totalDeltaX += dx
            totalDeltaY += dy

            if (isVerticalDrag == null) {
                val absX = abs(totalDeltaX)
                val absY = abs(totalDeltaY)
                if (absY > touchSlop || absX > touchSlop) {
                    if (absY > absX && totalDeltaY > 0f) {
                        isVerticalDrag = true
                        isDragging = true
                        change.consume()
                        scope.launch {
                            dismissProgress.snapTo(
                                (initialProgress + totalDeltaY / dismissTargetY).coerceIn(0f, 1f)
                            )
                        }
                    } else {
                        isVerticalDrag = false
                        break
                    }
                }
            } else if (isVerticalDrag == true) {
                change.consume()
                val deltaProgress = dy / dismissTargetY
                scope.launch {
                    dismissProgress.snapTo(
                        (dismissProgress.value + deltaProgress).coerceIn(0f, 1f)
                    )
                }
            }
        }

        if (isDragging) {
            val velocityY = velocityTracker.calculateVelocity().y
            if (shouldDismissPlayer(dismissProgress.value, velocityY)) {
                onDismiss()
            } else {
                scope.launch {
                    dismissProgress.animateTo(
                        0f,
                        spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            }
        }
    }
}

/**
 * Pure evaluation function for whether a downward drag/fling should commit dismissal.
 *
 * @param progress Current normalized dismiss progress [0..1]
 * @param velocityY Downward velocity in pixels/second
 */
fun shouldDismissPlayer(progress: Float, velocityY: Float): Boolean {
    return progress > 0.15f || velocityY > 800f
}
