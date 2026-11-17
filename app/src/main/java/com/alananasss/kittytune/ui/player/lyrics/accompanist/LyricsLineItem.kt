package com.alananasss.kittytune.ui.player.lyrics.accompanist

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun LyricsLineItem(
    isFocused: Boolean,
    isRightAligned: Boolean,
    isCenterAligned: Boolean = false,
    onLineClicked: () -> Unit,
    onLinePressed: () -> Unit,
    blurRadius: () -> Float,
    modifier: Modifier = Modifier,
    activeAlpha: Float = 1f,
    inactiveAlpha: Float = 0.4f,
    blendMode: BlendMode = BlendMode.SrcOver,
    isInteractive: Boolean = true,
    activeScale: Float = 1.00f,
    content: @Composable () -> Unit
) {
    val inactiveScale = if (activeScale <= 1.02f) 0.98f else (activeScale * 0.92f).coerceAtMost(1.0f)
    val scaleState by animateFloatAsState(
        targetValue = if (isFocused) activeScale else inactiveScale,
        animationSpec = if (isFocused) {
            tween(durationMillis = 600, easing = LinearOutSlowInEasing)
        } else {
            tween(durationMillis = 300, easing = EaseInOut)
        },
        label = "scale"
    )

    val alphaState by animateFloatAsState(
        targetValue = if (isFocused) activeAlpha else inactiveAlpha,
        label = "alpha"
    )

    val originX = when {
        isRightAligned -> 1f
        isCenterAligned -> 0.5f
        else -> 0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isInteractive) Modifier.clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = onLineClicked,
                        onLongClick = onLinePressed
                    )
                else Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scaleState
                    scaleY = scaleState
                    alpha = alphaState
                    transformOrigin = TransformOrigin(originX, 1f)
                    this.blendMode = blendMode
                    compositingStrategy = CompositingStrategy.Offscreen

                    val radius = blurRadius()
                    if (radius > 0f) {
                        renderEffect = BlurEffect(
                            radiusX = radius,
                            radiusY = radius,
                            edgeTreatment = TileMode.Clamp
                        )
                    }
                }
        ) {
            content()
        }
    }
}
