package com.alananasss.kittytune.wear.presentation.volume

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay

/**
 * Curved volume arc rendered along the right bezel edge (next to physical rotary crown)
 * with smooth progress animation.
 */
@Composable
fun VolumeBezelArc(
    volume: Float,
    isInteracting: Boolean = false,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color(0x33FFFFFF)
) {
    val sanitized = VolumeSanitizer.sanitize(volume)
    val animatedProgress by animateFloatAsState(
        targetValue = sanitized,
        animationSpec = tween(durationMillis = 150),
        label = "VolumeArcProgress"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidth = 5.dp.toPx()
        val diameter = size.minDimension - strokeWidth - 4.dp.toPx()
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)

        // Arc span along right bezel: 300° (-60°) to 60°, total sweep = 120°
        val startAngle = 300f
        val totalSweep = 120f
        val activeSweep = totalSweep * animatedProgress

        // Background Track Arc
        drawArc(
            color = trackColor,
            startAngle = startAngle,
            sweepAngle = totalSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Foreground Active Arc
        if (activeSweep > 0f) {
            drawArc(
                color = activeColor,
                startAngle = startAngle,
                sweepAngle = activeSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Ephemeral HUD Badge displayed during rotary adjustments, auto-dismissing after [autoDismissMs].
 */
@Composable
fun EphemeralVolumeHud(
    volume: Float,
    triggerKey: Any,
    modifier: Modifier = Modifier,
    autoDismissMs: Long = 2000L
) {
    var visible by remember { mutableStateOf(false) }
    val percentage = VolumeSanitizer.toPercentage(volume)

    LaunchedEffect(triggerKey) {
        if (triggerKey != 0L && triggerKey != 0) {
            visible = true
            delay(autoDismissMs)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xCC1A1A1A), shape = RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when {
                    percentage == 0 -> Icons.AutoMirrored.Filled.VolumeMute
                    percentage < 50 -> Icons.AutoMirrored.Filled.VolumeDown
                    else -> Icons.AutoMirrored.Filled.VolumeUp
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
            }
        }
    }
}
