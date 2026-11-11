package com.alananasss.kittytune.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// The "wavy" play/pause (mini style): while playing, the circle grows nine gentle lobes and slowly
// rotates - a "cookie" - and flattens back into a perfect circle when paused.
//
// The polar formula is r = 1 - indent + indent * cos(sides * angle), evaluated at 120 steps
// per outline, matching the original wavy shape.
data class WavyCookieShape(
    val sides: Int = 9,
    val indent: Float,
    val rotationDegrees: Float = 0f
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: androidx.compose.ui.unit.Density): Outline {
        val path = Path()
        val maxRadiusX = size.width / 2f
        val maxRadiusY = size.height / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val steps = 120
        val rotationRad = rotationDegrees * Math.PI / 180.0
        for (i in 0..steps) {
            val angle = i * Math.PI * 2 / steps
            val bumpAngle = angle - rotationRad
            val r = 1f - indent + indent * cos(sides * bumpAngle)
            val x = cx + maxRadiusX * r * cos(angle)
            val y = cy + maxRadiusY * r * sin(angle)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return Outline.Generic(path)
    }
}

// The cookie drawn as a filled shape, used by the mini player where no icon button chrome
// is wanted.
@Composable
fun MiniWavyPlayPauseButton(
    isPlaying: Boolean,
    wavyEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val hapticFeedback = LocalHapticFeedback.current

    val cookieIndent by animateFloatAsState(
        targetValue = if (isPlaying && wavyEnabled) 0.08f else 0f,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "miniCookieIndent"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "miniRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "miniRotation"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "miniPressScale"
    )

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Only the coloured layer breathes and rotates; the icon above stays upright.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                    rotationZ = if (wavyEnabled) rotation else 0f
                    clip = true
                    shape = WavyCookieShape(sides = 9, indent = cookieIndent)
                }
                .background(containerColor)
        )
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

// Full-size wavy play/pause for the player screens: icon-button look with the cookie shape
// morphing from a circle while playing. Falls back to a plain circle when [wavyEnabled]
// is false so both looks stay available.
@Composable
fun WavyPlayPauseButton(
    isPlaying: Boolean,
    wavyEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: androidx.compose.ui.unit.Dp = 72.dp,
    iconSizeDp: androidx.compose.ui.unit.Dp = 32.dp,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val hapticFeedback = LocalHapticFeedback.current

    val cookieIndent by animateFloatAsState(
        targetValue = if (isPlaying && wavyEnabled) 0.08f else 0f,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "cookieIndent"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "playPauseScale"
    )

    val shape = if (wavyEnabled && cookieIndent > 0f) {
        WavyCookieShape(sides = 9, indent = cookieIndent, rotationDegrees = rotation)
    } else {
        androidx.compose.foundation.shape.CircleShape
    }

    Box(
        modifier = modifier
            .size(sizeDp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .graphicsLayer {
                clip = true
                this.shape = shape
            }
            .background(containerColor, shape)
            .pointerInput(onToggle) {
                detectTapGestures {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(iconSizeDp)
        )
    }
}
