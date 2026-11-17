package com.alananasss.kittytune.ui.player.slider

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.data.local.PlayerSliderStyle
import kotlin.math.PI
import kotlin.math.sin

/**
 * A miniature volume track at 60%, for previewing horizontal and vertical volume sliders in settings.
 */
@Composable
fun VolumeTrackPreview(
    style: PlayerSliderStyle,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    volume: Float = 0.6f,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = if (style == PlayerSliderStyle.SLIM) 4.dp.toPx() else 8.dp.toPx()
            val thumbRadius = 5.dp.toPx()

            if (vertical) {
                val cx = size.width / 2f
                val topY = 4.dp.toPx()
                val bottomY = size.height - 4.dp.toPx()
                val totalH = bottomY - topY
                val filledH = totalH * volume
                val splitY = bottomY - filledH

                // Inactive track (top)
                drawLine(
                    color = inactiveColor,
                    start = Offset(cx, topY),
                    end = Offset(cx, splitY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                // Active track (bottom)
                if (style == PlayerSliderStyle.WAVY || style == PlayerSliderStyle.SQUIGGLY) {
                    val path = Path()
                    val wavelength = if (style == PlayerSliderStyle.SQUIGGLY) 12.dp.toPx() else 24.dp.toPx()
                    val amplitude = 3.dp.toPx()
                    var y = bottomY
                    path.moveTo(cx, y)
                    while (y >= splitY) {
                        val dy = bottomY - y
                        val waveX = cx + amplitude * sin((dy / wavelength) * 2 * PI).toFloat()
                        path.lineTo(waveX, y)
                        y -= 2f
                    }
                    drawPath(path, activeColor, style = Stroke(width = stroke, cap = StrokeCap.Round))
                } else {
                    drawLine(
                        color = activeColor,
                        start = Offset(cx, bottomY),
                        end = Offset(cx, splitY),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }

                // Thumb
                drawCircle(
                    color = activeColor,
                    radius = thumbRadius,
                    center = Offset(cx, splitY)
                )
            } else {
                val cy = size.height / 2f
                val startX = 4.dp.toPx()
                val endX = size.width - 4.dp.toPx()
                val totalW = endX - startX
                val filledW = totalW * volume
                val splitX = startX + filledW

                // Active track (left)
                if (style == PlayerSliderStyle.WAVY || style == PlayerSliderStyle.SQUIGGLY) {
                    val path = Path()
                    val wavelength = if (style == PlayerSliderStyle.SQUIGGLY) 12.dp.toPx() else 24.dp.toPx()
                    val amplitude = 3.dp.toPx()
                    var x = startX
                    path.moveTo(x, cy)
                    while (x <= splitX) {
                        val dx = x - startX
                        val waveY = cy + amplitude * sin((dx / wavelength) * 2 * PI).toFloat()
                        path.lineTo(x, waveY)
                        x += 2f
                    }
                    drawPath(path, activeColor, style = Stroke(width = stroke, cap = StrokeCap.Round))
                } else {
                    drawLine(
                        color = activeColor,
                        start = Offset(startX, cy),
                        end = Offset(splitX, cy),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }

                // Inactive track (right)
                drawLine(
                    color = inactiveColor,
                    start = Offset(splitX, cy),
                    end = Offset(endX, cy),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )

                // Thumb
                if (style == PlayerSliderStyle.BAR) {
                    val barWidth = 4.dp.toPx()
                    val barHeight = 16.dp.toPx()
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(splitX - barWidth / 2f, cy - barHeight / 2f),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                } else if (style != PlayerSliderStyle.SLIM) {
                    drawCircle(
                        color = activeColor,
                        radius = thumbRadius,
                        center = Offset(splitX, cy)
                    )
                }
            }
        }
    }
}
