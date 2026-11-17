package com.alananasss.kittytune.ui.yearlyplayback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Material 3 Multi-segmented Step Indicator for Yearly Playback Stories.
 *
 * Implements 100% parity with SoundCloud's decompiled:
 * com.soundcloud.android.yearlyplayback.ui.StepIndicatorKt
 *
 * Each story block has an individual segment.
 * - Completed steps: 100% filled.
 * - Current step: animates smoothly from 0% to 100% over [durationMs].
 * - Upcoming steps: 0% filled (translucent track).
 * - When paused, the progress freezes at its current position.
 */
@Composable
fun StepIndicator(
    totalSteps: Int,
    currentStep: Int,
    durationMs: Int,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    trackColor: Color = Color.White.copy(alpha = 0.25f)
) {
    if (totalSteps <= 0) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (i in 0 until totalSteps) {
            when {
                i < currentStep -> {
                    // Fully completed segment
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(activeColor)
                    )
                }
                i == currentStep -> {
                    // Animating current segment
                    AnimatedStepSegment(
                        durationMs = durationMs,
                        isPaused = isPaused,
                        activeColor = activeColor,
                        trackColor = trackColor,
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> {
                    // Upcoming inactive segment
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(trackColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedStepSegment(
    durationMs: Int,
    isPaused: Boolean,
    activeColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(durationMs, isPaused) {
        if (!isPaused) {
            val remainingTarget = 1f - progress.value
            val remainingTime = (durationMs * remainingTarget).toInt().coerceAtLeast(1)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = remainingTime,
                    easing = LinearEasing
                )
            )
        } else {
            progress.stop()
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .clip(CircleShape)
                .background(activeColor)
        )
    }
}
